package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the maid's own bedrock model bones and turns them into a coarse silhouette: for a number of slices
 * along her body, how wide she is there.
 * <p>
 * TLM models are ordinary bedrock bone trees ({@code modelMap} of {@code BedrockPart}s), so walking the bones
 * gives her real proportions - head, shoulders, skirt, legs - instead of a hard coded human profile.
 */
public final class MaidSilhouette {
    public static final int BINS = 24;
    private static final float PIXELS = 16.0F;

    private MaidSilhouette() {
    }

    /** @return {height in blocks, radius per slice}, or null when the model is not a bedrock one. */
    public static float[] sample(EntityMaid maid) {
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
            float[] heights = new float[BINS];
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
                int bin = bin(y, 2.0F);
                if (bin >= 0) {
                    radius[bin] = Math.max(radius[bin], lateral);
                    heights[bin] = Math.max(heights[bin], y);
                }
            }
            if (maxY < 0.5F) {
                return null;
            }
            // Fill the gaps between bones so the cloth does not jump between slices.
            for (int i = 1; i < BINS; i++) {
                radius[i] = Math.max(radius[i], radius[i - 1] * 0.92F);
            }
            for (int i = BINS - 2; i >= 0; i--) {
                radius[i] = Math.max(radius[i], radius[i + 1] * 0.92F);
            }
            float[] out = new float[BINS + 1];
            out[0] = maxY;
            System.arraycopy(radius, 0, out, 1, BINS);
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Radius at a height given in blocks along her body, from {@link #sample}. */
    public static float radiusAt(float[] profile, float bodyY) {
        float height = Math.max(0.5F, profile[0]);
        float t = Math.max(0.0F, Math.min(0.999F, bodyY / height));
        int bin = (int) (t * BINS);
        return profile[1 + Math.min(BINS - 1, bin)];
    }

    private static int bin(float y, float span) {
        int bin = (int) Math.floor(y / span);
        return bin < 0 || bin >= BINS ? -1 : bin;
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
            Field field = part.getClass().getField(name);
            return field.getFloat(part);
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }
}
