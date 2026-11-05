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
    private static String drawDiagnostics = "";
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
        // AFTER_ENTITIES hands us a camera-relative space: the pose contains the camera rotation only,
        // entities are drawn at (world - camera). Our boxes are in world space, so bring them over.
        Matrix4f matrix = new Matrix4f(pose.last().pose())
                .translate((float) -camera.x, (float) -camera.y, (float) -camera.z);
        int boxes = 0;
        int missing = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        org.joml.Vector3f firstBoxWorld = null;
        org.joml.Vector3f firstBoxView = null;
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
            // A yellow beacon on every sleeping maid: if the boxes stay invisible but this shows up,
            // the coordinates are fine and the line render type is being swallowed instead.
            marker(consumer, matrix, maid);
            for (QuiltBox box : set.boxes) {
                float r = box.hard ? 1.0F : 0.30F;
                float g = box.hard ? 0.25F : 0.60F;
                float b = box.hard ? 0.25F : 1.0F;
                for (int[] edge : EDGES) {
                    line(consumer, matrix, box, edge[0], r, g, b);
                    line(consumer, matrix, box, edge[1], r, g, b);
                }
                for (int i = 0; i < 8; i++) {
                    org.joml.Vector3f view = matrix.transformPosition(
                            new org.joml.Vector3f(box.x(i), box.y(i), box.z(i)));
                    minX = Math.min(minX, view.x);
                    minY = Math.min(minY, view.y);
                    minZ = Math.min(minZ, view.z);
                    maxX = Math.max(maxX, view.x);
                    maxY = Math.max(maxY, view.y);
                    maxZ = Math.max(maxZ, view.z);
                    if (firstBoxWorld == null) {
                        firstBoxWorld = new org.joml.Vector3f(box.x(i), box.y(i), box.z(i));
                        firstBoxView = new org.joml.Vector3f(view);
                    }
                }
            }
        }
        buffers.endBatch(RenderType.lines());
        int tick = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
        // Fold the draw-time numbers into the chat/log status line: this block is proven to run, so the
        // numbers cannot go missing the way a separate log statement did.
        drawDiagnostics = String.format(java.util.Locale.ROOT,
                " cam=%.2f,%.2f,%.2f viewBox=[%.2f,%.2f,%.2f..%.2f,%.2f,%.2f] firstWorld=%s firstView=%s camRot=%s",
                camera.x, camera.y, camera.z, minX, minY, minZ, maxX, maxY, maxZ,
                firstBoxWorld == null ? "-" : firstBoxWorld.toString(),
                firstBoxView == null ? "-" : firstBoxView.toString(),
                pose.last().pose().toString().replace("Matrix4f[", "").replace("]", ""));
        if ((ANNOUNCED.compareAndSet(false, true) || tick % 200 == 0) && tick > 0) {
            String renderer = maids.isEmpty() ? "?" : Minecraft.getInstance().getEntityRenderDispatcher()
                    .getRenderer(maids.get(0)).getClass().getSimpleName();
            report("停在=" + com.sleepwell.client.quilt.diag.QuiltTrace.last()
                    + " boxes=" + boxes + " missing=" + missing
                    + " mixin=" + MaidColliderCapture.mixinState()
                    + " captured=" + MaidColliderCapture.captures()
                    + " " + MaidColliderCapture.layerState()
                    + " renderer=" + renderer
                    + drawDiagnostics
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

    /** Yellow beacon above a sleeping maid: tells "wrong coordinates" apart from "lines not drawn". */
    private static void marker(VertexConsumer consumer, Matrix4f matrix, EntityMaid maid) {
        float x = (float) maid.getX();
        float y = (float) maid.getY();
        float z = (float) maid.getZ();
        vertex(consumer, matrix, x, y + 0.05F, z, 1.0F, 1.0F, 0.0F);
        vertex(consumer, matrix, x, y + 3.0F, z, 1.0F, 1.0F, 0.0F);
        vertex(consumer, matrix, x - 0.6F, y + 0.05F, z, 1.0F, 1.0F, 0.0F);
        vertex(consumer, matrix, x + 0.6F, y + 0.05F, z, 1.0F, 1.0F, 0.0F);
        vertex(consumer, matrix, x, y + 0.05F, z - 0.6F, 1.0F, 1.0F, 0.0F);
        vertex(consumer, matrix, x, y + 0.05F, z + 0.6F, 1.0F, 1.0F, 0.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float r, float g, float b) {
        consumer.vertex(matrix, x, y, z).color(r, g, b, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, QuiltBox box, int corner,
                             float r, float g, float b) {
        consumer.vertex(matrix, box.x(corner), box.y(corner), box.z(corner))
                .color(r, g, b, 1.0F)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}