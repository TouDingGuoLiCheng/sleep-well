package com.sleepwell.client.quilt;

import com.sleepwell.client.quilt.collider.ColliderSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Solved quilts on disk, so a restart does not have to solve them again.
 * <p>
 * Keyed by model rather than by maid: every instance of the same model lies down in the same shape, so one
 * solved sheet serves all of them. The stored shape is in bed-local coordinates, which makes it valid for any
 * bed the maid sleeps in.
 * <p>
 * A cache entry carries the footprint it was solved from (box count plus bounding size). A model update or a
 * different model pack changes those numbers, the entry is rejected, and the quilt is solved again.
 */
public final class QuiltCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/quiltCache");
    private static final String DIR_NAME = "sleep_well/quilt_cache";
    /**
     * Footprint tolerance. Absolute 0.12 blocks plus 25 % of the measured size: a pose that shifts by a few
     * centimetres must not throw away a solved sheet, but a genuinely different body (a model swap, a size
     * change) still has to be re-solved.
     */
    private static final float SIZE_EPS = 0.12F;
    private static final float SIZE_REL = 0.25F;
    private static final int BOX_EPS = 12;

    private QuiltCache() {
    }

    /** The three local coordinates of every particle, plus the footprint they belong to. */
    public static final class Frame {
        public float[] along;
        public float[] across;
        public float[] height;
    }

    private static Path dir() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve(DIR_NAME);
    }

    /** 1.20.1 has no float array tag, so the bits travel as ints. */
    private static int[] bits(float[] values) {
        int[] out = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = Float.floatToRawIntBits(values[i]);
        }
        return out;
    }

    private static float[] floats(int[] values) {
        float[] out = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = Float.intBitsToFloat(values[i]);
        }
        return out;
    }

    private static String nameFor(String modelKey) {
        return modelKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    /** One axis of the footprint: within a fixed slack, or within a quarter of the larger of the two. */
    private static boolean close(float stored, float current) {
        float slack = SIZE_EPS + SIZE_REL * Math.max(Math.abs(stored), Math.abs(current));
        return Math.abs(stored - current) <= slack;
    }

    /** Loads a frame for this model and footprint, or null when there is none worth trusting. */
    public static Frame load(String modelKey, ColliderSet set, int cols, int rows) {
        if (modelKey == null || set == null) {
            return null;
        }
        Path file = dir().resolve(nameFor(modelKey) + ".nbt");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            CompoundTag tag;
            try (java.io.InputStream in = Files.newInputStream(file)) {
                tag = NbtIo.readCompressed(in);
            }
            if (tag.getInt("cols") != cols || tag.getInt("rows") != rows) {
                return null;
            }
            float dx = set.bounds[3] - set.bounds[0];
            float dy = set.bounds[4] - set.bounds[1];
            float dz = set.bounds[5] - set.bounds[2];
            boolean same = Math.abs(tag.getInt("boxes") - set.boxes.size()) <= BOX_EPS
                    && close(tag.getFloat("dx"), dx)
                    && close(tag.getFloat("dy"), dy)
                    && close(tag.getFloat("dz"), dz);
            if (!same) {
                LOGGER.info("[被子] 缓存不匹配，重新解算 model={} 盒子 {}->{} 尺寸 {}x{}x{}->{}x{}x{}",
                        modelKey, tag.getInt("boxes"), set.boxes.size(), tag.getFloat("dx"),
                        tag.getFloat("dy"), tag.getFloat("dz"), dx, dy, dz);
                return null;
            }
            float[] along = floats(tag.getIntArray("along"));
            float[] across = floats(tag.getIntArray("across"));
            float[] height = floats(tag.getIntArray("height"));
            if (along.length != cols * rows || across.length != cols * rows || height.length != cols * rows) {
                return null;
            }
            Frame frame = new Frame();
            frame.along = along;
            frame.across = across;
            frame.height = height;
            return frame;
        } catch (Exception exception) {
            LOGGER.warn("[被子] 读取缓存失败 model={}：{}", modelKey, exception.toString());
            return null;
        }
    }

    /** Writes the frame this solve produced. Failures are logged and ignored: the quilt still works. */
    public static void save(String modelKey, ColliderSet set, int cols, int rows,
                            float[] along, float[] across, float[] height) {
        if (modelKey == null || set == null || along == null) {
            return;
        }
        try {
            CompoundTag tag = new CompoundTag();
            tag.putInt("cols", cols);
            tag.putInt("rows", rows);
            tag.putInt("boxes", set.boxes.size());
            tag.putFloat("dx", set.bounds[3] - set.bounds[0]);
            tag.putFloat("dy", set.bounds[4] - set.bounds[1]);
            tag.putFloat("dz", set.bounds[5] - set.bounds[2]);
            tag.putIntArray("along", bits(along));
            tag.putIntArray("across", bits(across));
            tag.putIntArray("height", bits(height));
            Files.createDirectories(dir());
            try (java.io.OutputStream out = Files.newOutputStream(dir().resolve(nameFor(modelKey) + ".nbt"))) {
                NbtIo.writeCompressed(tag, out);
            }
        } catch (Exception exception) {
            LOGGER.warn("[被子] 写入缓存失败 model={}：{}", modelKey, exception.toString());
        }
    }
}
