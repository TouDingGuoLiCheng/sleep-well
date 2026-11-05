package com.sleepwell.client.quilt.collider;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Debug view for the captured outline: every cube as a wire box, red for body (hard collision) and blue
 * for the rest (soft repulsion).
 * <p>
 * This is the acceptance test for M1: if the boxes wrap her sleeping pose exactly, the capture is right and
 * the cloth solver can be written against it. Enable with {@code -Dsleepwell.quiltWireframe=true}.
 */
public final class QuiltWireframe {
    private static final boolean ENABLED = Boolean.getBoolean("sleepwell.quiltWireframe");
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7},
            {0, 2}, {1, 3}, {4, 6}, {5, 7},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private QuiltWireframe() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void draw(PoseStack pose, MultiBufferSource.BufferSource buffers, List<EntityMaid> maids) {
        if (!ENABLED || maids.isEmpty()) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        Matrix4f matrix = pose.last().pose();
        for (EntityMaid maid : maids) {
            ColliderSet set = MaidColliderCapture.get(maid);
            if (set == null) {
                continue;
            }
            for (QuiltBox box : set.boxes) {
                float r = box.hard ? 1.0F : 0.30F;
                float g = box.hard ? 0.25F : 0.60F;
                float b = box.hard ? 0.25F : 1.0F;
                for (int[] edge : EDGES) {
                    line(consumer, matrix, box, edge[0], r, g, b);
                    line(consumer, matrix, box, edge[1], r, g, b);
                }
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, QuiltBox box, int corner,
                             float r, float g, float b) {
        consumer.vertex(matrix, box.x(corner), box.y(corner), box.z(corner))
                .color(r, g, b, 1.0F)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
