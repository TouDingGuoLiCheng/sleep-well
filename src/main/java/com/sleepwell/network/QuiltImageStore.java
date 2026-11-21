package com.sleepwell.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The world's own shelf of player pictures.
 * <p>
 * Pictures are stored once per world under their content hash, so two maids wearing the same image cost one file,
 * and a client that already has the bytes simply never asks. Nothing from a player's picture is shipped inside the
 * mod jar - it lives in the save, like a map or a book.
 */
public final class QuiltImageStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/upload");
    public static final int CHUNK = 30_000;
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final int MAX_IMAGES = 64;
    private static final Map<String, ByteArrayOutputStream> INCOMING = new ConcurrentHashMap<>();

    private QuiltImageStore() {
    }

    public static Path folder(net.minecraft.server.MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("sleep_well_quilts");
    }

    /** Content hashes are what an id names, so only hex of the right length is allowed through. */
    public static boolean validHash(String hash) {
        return hash != null && hash.length() == 16 && hash.chars().allMatch(c -> "0123456789abcdef".indexOf(c) >= 0);
    }

    public static String hash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] sum = digest.digest(bytes);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                out.append(String.format(java.util.Locale.ROOT, "%02x", sum[i]));
            }
            return out.toString();
        } catch (Exception failure) {
            return "";
        }
    }

    /** One chunk in; true once the picture is on disk. */
    public static boolean acceptChunk(net.minecraft.server.level.ServerLevel level, String hash,
                                      int index, int total, byte[] chunk) {
        if (!validHash(hash) || total <= 0 || index < 0 || index >= total || chunk.length == 0) {
            return false;
        }
        ByteArrayOutputStream buffer = INCOMING.computeIfAbsent(hash, key -> new ByteArrayOutputStream());
        synchronized (buffer) {
            if (buffer.size() + chunk.length > MAX_BYTES) {
                INCOMING.remove(hash);
                LOGGER.warn("Refused a picture {}: over {} bytes", hash, MAX_BYTES);
                return false;
            }
            buffer.writeBytes(chunk);
            if (index + 1 < total) {
                return false;
            }
            byte[] bytes = buffer.toByteArray();
            INCOMING.remove(hash);
            return write(level, hash, bytes);
        }
    }

    private static boolean write(net.minecraft.server.level.ServerLevel level, String hash, byte[] bytes) {
        // A png header or nothing: this file ends up being decoded by every client that meets the maid.
        if (bytes.length < 8 || bytes[0] != (byte) 0x89 || bytes[1] != 'P' || bytes[2] != 'N'
                || bytes[3] != 'G') {
            LOGGER.warn("Refused a picture {}: not a png", hash);
            return false;
        }
        Path folder = folder(level.getServer());
        Path target = folder.resolve(hash + ".png");
        try {
            Files.createDirectories(folder);
            if (Files.isRegularFile(target)) {
                return true;
            }
            if (Files.list(folder).filter(Files::isRegularFile).count() >= MAX_IMAGES) {
                LOGGER.warn("Refused a picture {}: the world already holds {} pictures", hash, MAX_IMAGES);
                return false;
            }
            Files.write(target, bytes);
            return true;
        } catch (IOException failure) {
            LOGGER.warn("Could not store picture {}", hash, failure);
            return false;
        }
    }

    public static Optional<byte[]> read(net.minecraft.server.level.ServerLevel level, String hash) {
        if (!validHash(hash)) {
            return Optional.empty();
        }
        Path file = folder(level.getServer()).resolve(hash + ".png");
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException failure) {
            return Optional.empty();
        }
    }

    /** Hands a picture to one player, in chunks the channel will accept. */
    public static void sendTo(net.minecraft.server.level.ServerPlayer player, String hash) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        byte[] bytes = read(level, hash).orElse(null);
        if (bytes == null) {
            return;
        }
        int total = (bytes.length + CHUNK - 1) / CHUNK;
        for (int i = 0; i < total; i++) {
            int from = i * CHUNK;
            int to = Math.min(bytes.length, from + CHUNK);
            SleepWellNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                    new QuiltImagePacket(hash, i, total, Arrays.copyOfRange(bytes, from, to)));
        }
    }
}
