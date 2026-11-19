package com.sleepwell.client.quilt;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;

/**
 * Reads the real footprint of a maid bed straight out of TLM's own bedrock model.
 * <p>
 * The bed is a bedrock model like the maid, and its model keeps a {@code renderBoundingBox}; that box is the
 * only trustworthy answer to "how wide is the frame" and "how tall is the mattress", which is what the quilt
 * has to line up with.
 */
public final class BedGeometry {
    private static final float PIXELS = 16.0F;

    private BedGeometry() {
    }

    /** @return the bed's model bounds in blocks, or null when the model cannot be read. */
    public static AABB bounds(BlockPos pos) {
        try {
            BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(pos);
            if (blockEntity == null) {
                return null;
            }
            BlockEntityRenderer<?> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher()
                    .getRenderer(blockEntity);
            if (renderer == null) {
                return null;
            }
            Object model = findModel(renderer);
            if (model == null) {
                return null;
            }
            AABB box = findBounds(model);
            if (box == null) {
                return null;
            }
            // Bedrock models work in pixels; normalise to blocks when the numbers clearly are pixels.
            float scale = box.getXsize() > 4.0D || box.getYsize() > 4.0D ? 1.0F / PIXELS : 1.0F;
            return new AABB(box.minX * scale, box.minY * scale, box.minZ * scale,
                    box.maxX * scale, box.maxY * scale, box.maxZ * scale);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findModel(Object renderer) throws IllegalAccessException {
        Class<?> type = renderer.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getType().getName();
                if (name.contains("bedrock") || name.contains("Bedrock") || name.contains("Model")) {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (value != null) {
                        return value;
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static AABB findBounds(Object model) throws IllegalAccessException {
        Class<?> type = model.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (AABB.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(model);
                    if (value instanceof AABB box) {
                        return box;
                    }
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
