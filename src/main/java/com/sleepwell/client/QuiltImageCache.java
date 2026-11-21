package com.sleepwell.client;

import com.sleepwell.SleepWellMod;
import com.sleepwell.client.quilt.QuiltTextures;
import com.sleepwell.network.QuiltImageRequestPacket;
import com.sleepwell.network.QuiltImageStore;
import com.sleepwell.network.QuiltImageUploadPacket;
import com.sleepwell.network.SleepWellNetwork;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This client's copy of every picture a maid might wear.
 * <p>
 * Sending is normalised first: the picture is decoded, subsampled to the sheet size and re-encoded as png, so the
 * bytes that travel are the same bytes every other client will hash and cache. The hash is the id, which is why
 * two players using the same picture share one file on the server and one request on the wire.
 */
public final class QuiltImageCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/upload");
    private static final Map<String, ByteArrayOutputStream> INCOMING = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> HASHES = new ConcurrentHashMap<>();

    private QuiltImageCache() {
    }

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("sleep_well").resolve("cache");
    }

    public static Path file(String hash) {
        return folder().resolve(hash + ".png");
    }

    public static boolean has(String hash) {
        return QuiltImageStore.validHash(hash) && Files.isRegularFile(file(hash));
    }

    /** Asks the server once per hash; a picture that never arrives simply falls back to the default quilt. */
    public static void request(String hash) {
        if (!QuiltImageStore.validHash(hash) || has(hash) || !REQUESTED.add(hash)) {
            return;
        }
        SleepWellNetwork.CHANNEL.sendToServer(new QuiltImageRequestPacket(hash));
    }

    public static void receiveChunk(String hash, int index, int total, byte[] chunk) {
        if (!QuiltImageStore.validHash(hash) || total <= 0 || index < 0 || index >= total) {
            return;
        }
        ByteArrayOutputStream buffer = INCOMING.computeIfAbsent(hash, key -> new ByteArrayOutputStream());
        byte[] bytes;
        synchronized (buffer) {
            buffer.writeBytes(chunk);
            if (index + 1 < total) {
                return;
            }
            bytes = buffer.toByteArray();
            INCOMING.remove(hash);
        }
        REQUESTED.remove(hash);
        try {
            Files.createDirectories(folder());
            Files.write(file(hash), bytes);
            // The texture may already have failed to load once, so the renderer has to look again.
            QuiltTextures.clear();
        } catch (Exception failure) {
            LOGGER.warn("Could not cache picture {}", hash, failure);
        }
    }

    /** The normalised png bytes for a file: what travels, what gets hashed, and what the cache stores. */
    public static byte[] normalised(Path source) {
        return QuiltTextures.normalisedPng(source);
    }

    /** Hash of a local file, computed once per file version. */
    public static String hashOf(Path source) {
        try {
            String key = source + "|" + Files.size(source) + "|" + Files.getLastModifiedTime(source).toMillis();
            String known = HASHES.get(key);
            if (known != null) {
                return known;
            }
            byte[] bytes = normalised(source);
            if (bytes == null || bytes.length == 0 || bytes.length > QuiltImageStore.MAX_BYTES) {
                return "";
            }
            String hash = QuiltImageStore.hash(bytes);
            HASHES.put(key, hash);
            return hash;
        } catch (Exception failure) {
            return "";
        }
    }

    /** Uploads a picture and returns the hash every client can name it by, or an empty string on failure. */
    public static String publish(Path source) {
        byte[] bytes = normalised(source);
        if (bytes == null || bytes.length == 0 || bytes.length > QuiltImageStore.MAX_BYTES) {
            return "";
        }
        String hash = QuiltImageStore.hash(bytes);
        try {
            Files.createDirectories(folder());
            Files.write(file(hash), bytes);
        } catch (Exception failure) {
            LOGGER.warn("Could not cache picture {}", hash, failure);
        }
        int total = (bytes.length + QuiltImageStore.CHUNK - 1) / QuiltImageStore.CHUNK;
        for (int i = 0; i < total; i++) {
            int from = i * QuiltImageStore.CHUNK;
            int to = Math.min(bytes.length, from + QuiltImageStore.CHUNK);
            SleepWellNetwork.CHANNEL.sendToServer(
                    new QuiltImageUploadPacket(hash, i, total, Arrays.copyOfRange(bytes, from, to)));
        }
        return hash;
    }
}
