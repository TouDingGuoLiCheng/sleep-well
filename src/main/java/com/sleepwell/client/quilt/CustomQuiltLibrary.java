package com.sleepwell.client.quilt;

import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The player's own quilt images, read from {@code config/sleep_well/quilts}.
 * <p>
 * A custom quilt is not a registry entry: the picture lives on the player's disk and its id is
 * {@code custom:<file name>}. The server only ever sees that id, so the same id has to mean the same picture on
 * every client - which is what the upload step is for. Until that step lands, the file simply has to be present
 * locally.
 */
public final class CustomQuiltLibrary {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/custom");
    public static final String ID_PREFIX = "custom:";
    /** Bigger than this and the editor's preview gets slow and the upload would be wasteful. */
    /**
     * Ten megabytes per picture: a 7 MB photo already worked before the cap existed, and the real guard against a
     * memory blow-up is the pixel count below, not the file size.
     */
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_SIDE = 1024;
    /** Anything past this many pixels is refused outright rather than decoded. */
    public static final long MAX_PIXELS = 200_000_000L;
    /** Formats ImageIO can decode on this JVM; jpg matters most, it is what a phone photo is. */
    private static final java.util.List<String> EXTENSIONS = java.util.List.of(".png", ".jpg", ".jpeg", ".bmp", ".gif");

    private static final Map<String, Path> FILES = new LinkedHashMap<>();

    private CustomQuiltLibrary() {
    }

    public static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve("sleep_well").resolve("quilts");
    }

    /** Re-reads the folder. Cheap enough to call whenever the screen opens. */
    public static void rescan() {
        FILES.clear();
        Path folder = folder();
        try {
            Files.createDirectories(folder);
        } catch (IOException failure) {
            LOGGER.warn("Could not create {}", folder, failure);
            return;
        }
        try (Stream<Path> stream = Files.list(folder)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return EXTENSIONS.stream().anyMatch(name::endsWith);
                    })
                    .filter(path -> {
                        try {
                            long size = Files.size(path);
                            if (size > MAX_BYTES) {
                                LOGGER.warn("Skipping {}: {} bytes is over the {} byte limit",
                                        path.getFileName(), size, MAX_BYTES);
                                return false;
                            }
                            return true;
                        } catch (IOException failure) {
                            return false;
                        }
                    })
                    .sorted()
                    .forEach(path -> FILES.put(nameOf(path), path));
        } catch (IOException failure) {
            LOGGER.warn("Could not read {}", folder, failure);
        }
    }

    public static List<String> names() {
        return new ArrayList<>(FILES.keySet());
    }

    public static boolean has(String name) {
        return FILES.containsKey(name);
    }

    public static Path path(String name) {
        return FILES.get(name);
    }

    /** The file name without extension, which is what the id carries. */
    public static String nameOf(Path path) {
        String file = path.getFileName().toString();
        int dot = file.lastIndexOf('.');
        return dot <= 0 ? file : file.substring(0, dot);
    }

    /**
     * Builds the runtime quilt for one file at one set of numbers. Not registered anywhere: two maids may wear the
     * same picture with different numbers, so a shared registry entry would be wrong.
     */
    public static QuiltStyle style(String name, QuiltParams params) {
        Path path = FILES.get(name);
        if (path == null) {
            return null;
        }
        QuiltParams safe = params == null ? QuiltParams.DEFAULT : params;
        return QuiltStyle.runtime(ID_PREFIX + name, name, path, safe);
    }

    /** Resolves a stored id back to a style, or null when this client does not have that picture. */
    public static QuiltStyle styleById(String id, QuiltParams params) {
        if (id == null || !id.startsWith(ID_PREFIX)) {
            return null;
        }
        return style(id.substring(ID_PREFIX.length()), params);
    }

    public static boolean isCustom(String id) {
        return id != null && id.startsWith(ID_PREFIX);
    }
}
