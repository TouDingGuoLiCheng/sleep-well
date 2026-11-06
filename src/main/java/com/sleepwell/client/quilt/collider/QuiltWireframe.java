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
        // Measured: the AFTER_ENTITIES pose already maps world space into view space (it carries the
        // camera rotation *and* the camera translation). Subtracting the camera a second time lifted
        // every box ~116 blocks into the sky, which is why nothing was ever on screen.
        Matrix4f matrix = new Matrix4f(pose.last().pose());
        // A debug overlay must stay visible through the bed the maid is lying in, otherwise "buried"
        // and "misplaced" look identical on screen.
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        int boxes = 0;
        int missing = 0;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        org.joml.Vector3f firstBoxWorld = null;
        org.joml.Vector3f firstBoxView = null;
        StringBuilder maidList = new StringBuilder();
        EntityMaid nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (EntityMaid maid : maids) {
            double d = maid.position().distanceTo(camera);
            if (d < nearestDistance) {
                nearestDistance = d;
                nearest = maid;
            }
            ColliderSet set = MaidColliderCapture.get(maid);
            if (set == null) {
                set = MaidColliderCapture.captureDirect(maid, camera, partialTick);
            }
            // Beacon first: it is drawn from her own position, so it stays visible no matter what the
            // captured boxes contain.
            marker(consumer, matrix, maid);
            if (set == null || !set.fromLayer) {
                // Sets built outside her render pass carry the wrong space; drawing them is what put
                // boxes in the sky. Only the render-layer capture is trusted.
                missing++;
                continue;
            }
            boxes += set.boxes.size();
            if (maidList.length() < 420) {
                maidList.append(String.format(java.util.Locale.ROOT, " [n=%d %.1f,%.1f,%.1f min=%.1f,%.1f,%.1f]",
                        set.boxes.size(), maid.getX(), maid.getY(), maid.getZ(),
                        set.bounds[0], set.bounds[1], set.bounds[2]));
            }
            for (QuiltBox box : set.boxes) {
                float r = box.hard ? 1.0F : 0.30F;
                float g = box.hard ? 0.25F : 0.60F;
                float b = box.hard ? 0.25F : 1.0F;
                for (int[] edge : EDGES) {
                    line(consumer, matrix, box, edge[0], r, g, b, 0.0F, 0.0F, 0.0F);
                    line(consumer, matrix, box, edge[1], r, g, b, 0.0F, 0.0F, 0.0F);
                    // Green copy two blocks aside, in open air: the shape can be judged even when the
                    // original sits buried inside the bed.
                    line(consumer, matrix, box, edge[0], 0.35F, 1.0F, 0.35F, 2.0F, 0.0F, 0.0F);
                    line(consumer, matrix, box, edge[1], 0.35F, 1.0F, 0.35F, 2.0F, 0.0F, 0.0F);
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
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        int tick = Minecraft.getInstance().player == null ? 0 : Minecraft.getInstance().player.tickCount;
        // Fold the draw-time numbers into the chat/log status line: this block is proven to run, so the
        // numbers cannot go missing the way a separate log statement did.
        String nearInfo = "-";
        if (nearest != null) {
            ColliderSet nearSet = MaidColliderCapture.get(nearest);
            float[] nb = nearSet == null ? null : nearSet.bounds;
            nearInfo = String.format(java.util.Locale.ROOT,
                    "%.2f@%.2f,%.2f,%.2f n=%d min=%s", nearestDistance, nearest.getX(), nearest.getY(),
                    nearest.getZ(), nearSet == null ? -1 : nearSet.boxes.size(),
                    nb == null ? "-" : String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", nb[0], nb[1], nb[2]));
        }
        drawDiagnostics = " maids=" + maidList + " near=" + nearInfo + String.format(java.util.Locale.ROOT,
                " cam=%.2f,%.2f,%.2f viewBox=[%.2f,%.2f,%.2f..%.2f,%.2f,%.2f] firstWorld=%s firstView=%s",
                camera.x, camera.y, camera.z, minX, minY, minZ, maxX, maxY, maxZ,
                firstBoxWorld == null ? "-" : firstBoxWorld.toString(),
                firstBoxView == null ? "-" : firstBoxView.toString());
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
        // and a green beacon where the copied outline sits
        vertex(consumer, matrix, x + 2.0F, y + 0.05F, z, 0.35F, 1.0F, 0.35F);
        vertex(consumer, matrix, x + 2.0F, y + 2.0F, z, 0.35F, 1.0F, 0.35F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float r, float g, float b) {
        consumer.vertex(matrix, x, y, z).color(r, g, b, 1.0F).normal(0.0F, 1.0F, 0.0F).endVertex();
    }

    private static void line(VertexConsumer consumer, Matrix4f matrix, QuiltBox box, int corner,
                             float r, float g, float b, float dx, float dy, float dz) {
        consumer.vertex(matrix, box.x(corner) + dx, box.y(corner) + dy, box.z(corner) + dz)
                .color(r, g, b, 1.0F)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}