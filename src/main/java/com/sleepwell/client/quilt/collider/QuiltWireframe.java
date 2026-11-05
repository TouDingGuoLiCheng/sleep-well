package com.sleepwell.client.quilt.collider;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Debug view for the captured outline: every cube as a wire box, red for body (hard collision) and blue
 * for the rest (soft repulsion).
 * <p>
 * This is the acceptance test for M1: if the boxes wrap her sleeping pose exactly, the capture is right and
 * the cloth solver can be written against it.
 * <p>
 * <b>On by default</b> while this branch is under construction; {@code -Dsleepwell.quiltWireframe=false}
 * turns it off. The first frame reports itself both in the log and in the chat bar, so "nothing on screen"
 * can be told apart from "the switch never reached the JVM" without hunting for a log file.
 */
public final class QuiltWireframe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("sleepwell.quiltWireframe"));
    private static final AtomicBoolean ANNOUNCED = new AtomicBoolean();
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

    public static void draw(PoseStack pose, MultiBufferSource.BufferSource buffers, List<EntityMaid> maids,
                            net.minecraft.world.phys.Vec3 camera, float partialTick) {
        com.sleepwell.client.quilt.diag.QuiltTrace.step("11.wireframe", "enabled=" + ENABLED + " maids=" + maids.size());
        if (!ENABLED || maids.isEmpty()) {
            if (ANNOUNCED.compareAndSet(false, true)) {
                report("flag=" + ENABLED + " sleeping=" + maids.size()
                        + " hook=" + MaidColliderCapture.hookRuns());
            }
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        Matrix4f matrix = pose.last().pose();
        int boxes = 0;
        int missing = 0;
        for (EntityMaid maid : maids) {
            ColliderSet set = MaidColliderCapture.get(maid);
            if (set == null) {
                set = MaidColliderCapture.captureDirect(maid, camera, partialTick);
            }
            if (set == null) {
                missing++;
                continue;
            }
            boxes += set.boxes.size();
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
        if (ANNOUNCED.compareAndSet(false, true)) {
            String renderer = maids.isEmpty() ? "?" : Minecraft.getInstance().getEntityRenderDispatcher()
                    .getRenderer(maids.get(0)).getClass().getSimpleName();
            report("停在=" + com.sleepwell.client.quilt.diag.QuiltTrace.last()
                    + " boxes=" + boxes + " missing=" + missing
                    + " mixin=" + MaidColliderCapture.mixinState()
                    + " captured=" + MaidColliderCapture.captures()
                    + " renderer=" + renderer
                    + " miss=" + MaidColliderCapture.misses()
                    + " | " + MaidColliderCapture.lastMiss());
            LOGGER.info("[sleep_well] trace summary {}", com.sleepwell.client.quilt.diag.QuiltTrace.summary());
        }
    }

    /** Log plus one chat line: the chat line is what a player actually notices during a test run. */
    private static void report(String status) {
        LOGGER.info("[sleep_well] quilt wireframe {}", status);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(
                    Component.literal("[被子] 线框 " + status).withStyle(ChatFormatting.AQUA), false);
        }
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, QuiltBox box, int corner,
                             float r, float g, float b) {
        consumer.vertex(matrix, box.x(corner), box.y(corner), box.z(corner))
                .color(r, g, b, 1.0F)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
