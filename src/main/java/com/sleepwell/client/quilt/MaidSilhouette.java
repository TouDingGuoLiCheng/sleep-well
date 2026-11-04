package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The maid's own bones, sampled while she is actually being rendered.
 * <p>
 * Reading the bones at any other moment only gives the rest pose, and every maid's rest pose is the same -
 * that is why every quilt used to look identical. The render event fires after the animation has been applied,
 * so the sampled bones are the ones of her sleeping pose.
 */
public final class MaidSilhouette {
    public static final int BINS = 24;
    private static final float PIXELS = 16.0F;
    private static final int REFRESH_TICKS = 10;

    private static final Map<UUID, float[]> CACHE = new HashMap<>();
    private static final Map<UUID, Integer> STAMP = new HashMap<>();
    private static final Map<UUID, Integer> PRINTS = new HashMap<>();

    private MaidSilhouette() {
    }

    /** Called from the render event: the model holds her animated pose at this point. */
    public static void capture(EntityMaid maid) {
        UUID id = maid.getUUID();
        Integer last = STAMP.get(id);
        if (last != null && maid.tickCount - last < REFRESH_TICKS) {
            return;
        }
        float[] profile = compute(maid);
        if (profile != null) {
            CACHE.put(id, profile);
            STAMP.put(id, maid.tickCount);
        }
    }

    /** @return {height in blocks, radius per slice}, or null when nothing could be sampled. */
    public static float[] sample(EntityMaid maid) {
        float[] cached = CACHE.get(maid.getUUID());
        if (cached != null) {
            return cached;
        }
        float[] profile = compute(maid);
        if (profile != null) {
            CACHE.put(maid.getUUID(), profile);
        }
        return profile;
    }

    public static void forget(UUID id) {
        CACHE.remove(id);
        STAMP.remove(id);
    }

    private static float[] compute(EntityMaid maid) {
        try {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(maid);
            if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
                return null;
            }
            EntityModel<?> model = living.getModel();
            Map<?, ?> parts = bedrockParts(model);
            if (parts == null || parts.isEmpty()) {
                return null;
            }
            float[] radius = new float[BINS];
            float maxY = 0.0F;
            for (Object value : parts.values()) {
                if (value == null) {
                    continue;
                }
                float x = read(value, "x") / PIXELS;
                float y = read(value, "y") / PIXELS;
                float z = read(value, "z") / PIXELS;
                float lateral = Math.max(Math.abs(x), Math.abs(z)) + 0.10F;
                maxY = Math.max(maxY, y);
                int bin = (int) Math.floor(y / 2.0F);
                if (bin >= 0 && bin < BINS) {
                    radius[bin] = Math.max(radius[bin], lateral);
                }
            }
            if (maxY < 0.5F) {
                return null;
            }
            for (int i = 1; i < BINS; i++) {
                radius[i] = Math.max(radius[i], radius[i - 1] * 0.90F);
            }
            for (int i = BINS - 2; i >= 0; i--) {
                radius[i] = Math.max(radius[i], radius[i + 1] * 0.90F);
            }
            float[] out = new float[BINS + 1];
            out[0] = maxY;
            System.arraycopy(radius, 0, out, 1, BINS);
            debug(maid, model, parts, out);
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Temporary: show what the sampler actually sees, twice per maid. */
    private static void debug(EntityMaid maid, Object model, Map<?, ?> parts, float[] profile) {
        java.util.UUID id = maid.getUUID();
        int printed = PRINTS.getOrDefault(id, 0);
        if (printed >= 2) {
            return;
        }
        PRINTS.put(id, printed + 1);
        System.out.println("[sleep_well] silhouette maid=" + id + " model=" + model.getClass().getName()
                + " parts=" + parts.size());
        int shown = 0;
        for (Map.Entry<?, ?> entry : parts.entrySet()) {
            if (shown++ >= 10) {
                break;
            }
            Object part = entry.getValue();
            System.out.println("[sleep_well]   bone " + entry.getKey()
                    + " x=" + read(part, "x") + " y=" + read(part, "y") + " z=" + read(part, "z")
                    + " ox=" + read(part, "offsetX") + " oy=" + read(part, "offsetY")
                    + " oz=" + read(part, "offsetZ"));
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < BINS; i++) {
            builder.append(String.format("%.2f ", profile[1 + i]));
        }
        System.out.println("[sleep_well]   height=" + String.format("%.2f", profile[0]) + " radius=" + builder);
    }

    public static float radiusAt(float[] profile, float bodyY) {
        float height = Math.max(0.5F, profile[0]);
        float t = Math.max(0.0F, Math.min(0.999F, bodyY / height));
        int bin = (int) (t * BINS);
        return profile[1 + Math.min(BINS - 1, bin)];
    }

    private static Map<?, ?> bedrockParts(EntityModel<?> model) throws IllegalAccessException {
        Class<?> type = model.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(field.getType()) && field.getName().toLowerCase().contains("modelmap")) {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    return value instanceof Map<?, ?> map ? map : null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static float read(Object part, String name) {
        try {
            return part.getClass().getField(name).getFloat(part);
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }
}
