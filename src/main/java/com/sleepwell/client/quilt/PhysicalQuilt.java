package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.sleepwell.client.quilt.collider.ColliderSet;
import com.sleepwell.client.quilt.collider.MaidColliderCapture;
import com.sleepwell.client.quilt.collider.QuiltBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The quilt as an actual cloth: a particle grid solved with position based dynamics, colliding against the
 * exact per-cube OBBs captured from her sleeping pose and against the bed it lies on.
 * <p>
 * Gravity, distance constraints and collision are all solved every frame, so indentations under her body and
 * the sag over the bed edge are consequences of the simulation rather than hand-authored curves.
 */
public final class PhysicalQuilt {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Grid resolution: across the bed first, then along it. */
    private static final int COLS = 15;
    private static final int ROWS = 19;
    private static final float DT = 1.0F / 60.0F;
    private static final int SUBSTEPS = 4;
    private static final int ITERATIONS = 6;
    private static final float GRAVITY = -9.8F;
    private static final float DAMPING = 0.985F;
    private static final float THICKNESS = 0.012F;
    private static final int WARMUP = 90;
    /** Half a bed's width, plus the overhang that hangs down the sides. */
    private static final float HALF_WIDTH = 0.78F;
    private static final float TILE = 1.0F;

    private static final Map<UUID, Cloth> CLOTHS = new HashMap<>();
    private static int FRAMES;
    private static int TICK_LOGS;

    private PhysicalQuilt() {
    }

    public static void clear() {
        CLOTHS.clear();
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, QuiltRenderer.Bed bed,
                              EntityMaid maid) {
        ColliderSet set = MaidColliderCapture.get(maid);
        if (set == null || set.boxes.isEmpty()) {
            return;
        }
        Vec3 head = Vec3.atLowerCornerOf(bed.head()).add(0.5D, 0.0D, 0.5D);
        Vec3 foot = Vec3.atLowerCornerOf(bed.foot()).add(0.5D, 0.0D, 0.5D);
        Vec3 along = new Vec3(foot.x - head.x, 0.0D, foot.z - head.z).normalize();
        Vec3 across = new Vec3(-along.z, 0.0D, along.x);
        double top = bedTop(bed);
        // Bed centre, used to place the sheet before the first solve.
        Vec3 middle = head.add(foot).scale(0.5D).add(0.0D, top, 0.0D);

        Cloth cloth = CLOTHS.computeIfAbsent(maid.getUUID(), id -> new Cloth());
        cloth.anchor = middle;
        cloth.along = along;
        cloth.across = across;
        cloth.bedTop = top;
        cloth.bedHead = head.add(0.0D, top, 0.0D);
        cloth.bedFoot = foot.add(0.0D, top, 0.0D);
        if (!cloth.initialised) {
            cloth.reset();
            cloth.settle(set);
            cloth.initialised = true;
        } else {
            cloth.step(set);
        }
        cloth.draw(pose, buffers, bed.color());
        if (--TICK_LOGS <= 0) {
            TICK_LOGS = 300;
            LOGGER.info("[被子] cloth p={} boxes={} yRange={}..{}", cloth.count, set.boxes.size(),
                    String.format(Locale.ROOT, "%.2f", cloth.minY()), String.format(Locale.ROOT, "%.2f", cloth.maxY()));
        }
    }

    private static double bedTop(QuiltRenderer.Bed bed) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return 0.5D;
        }
        BlockState headState = level.getBlockState(bed.head());
        BlockState footState = level.getBlockState(bed.foot());
        double a = headState.getShape(level, bed.head()).max(Direction.Axis.Y);
        double b = footState.getShape(level, bed.foot()).max(Direction.Axis.Y);
        double top = Math.min(a, b);
        return (top <= 0.0D || top > 0.60D) ? 0.5125D : top;
    }

    /** One sheet: positions, previous positions and the constraint list. */
    private static final class Cloth {
        private final Vector3f[] now = new Vector3f[COLS * ROWS];
        private final Vector3f[] prev = new Vector3f[COLS * ROWS];
        private final List<int[]> constraints = new ArrayList<>();
        private final List<float[]> rests = new ArrayList<>();
        private final int count = COLS * ROWS;
        private boolean initialised;
        private Vec3 anchor = Vec3.ZERO;
        private Vec3 along = Vec3.ZERO;
        private Vec3 across = Vec3.ZERO;
        private double bedTop;
        private Vec3 bedHead = Vec3.ZERO;
        private Vec3 bedFoot = Vec3.ZERO;
        /** World-space AABBs of the captured cubes, rebuilt every frame from the latest pose. */
        private float[] boxMin = new float[0];
        private float[] boxMax = new float[0];

        private void reset() {
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    float u = COLS == 1 ? 0.0F : (float) col / (COLS - 1);
                    float v = ROWS == 1 ? 0.0F : (float) row / (ROWS - 1);
                    double offsetAcross = (u - 0.5) * 2.0 * HALF_WIDTH;
                    // Along: from head end to foot end, the bed itself is two blocks.
                    double offsetAlong = (v - 0.5) * 2.0 * 1.05;
                    Vec3 p = anchor.add(across.scale(offsetAcross)).add(along.scale(offsetAlong))
                            .add(0.0D, 0.42D, 0.0D);
                    int index = row * COLS + col;
                    now[index] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
                    prev[index] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
                }
            }
            constraints.clear();
            rests.clear();
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int a = row * COLS + col;
                    if (col + 1 < COLS) {
                        add(a, a + 1, 1.0F);
                    }
                    if (row + 1 < ROWS) {
                        add(a, a + COLS, 1.0F);
                    }
                    if (col + 1 < COLS && row + 1 < ROWS) {
                        add(a, a + COLS + 1, 0.6F);
                        add(a + 1, a + COLS, 0.6F);
                    }
                    if (col + 2 < COLS) {
                        add(a, a + 2, 0.18F);
                    }
                    if (row + 2 < ROWS) {
                        add(a, a + COLS * 2, 0.12F);
                    }
                }
            }
        }

        private void add(int a, int b, float stiffness) {
            float rest = now[a].distance(now[b]);
            constraints.add(new int[]{a, b});
            rests.add(new float[]{rest, stiffness});
        }

        private void settle(ColliderSet set) {
            refreshBoxes(set);
            for (int i = 0; i < WARMUP; i++) {
                step(set, true);
            }
        }

        private void step(ColliderSet set) {
            step(set, false);
        }

        private void step(ColliderSet set, boolean warmup) {
            refreshBoxes(set);
            float sub = DT / SUBSTEPS;
            for (int s = 0; s < SUBSTEPS; s++) {
                for (int i = 0; i < count; i++) {
                    Vector3f p = now[i];
                    Vector3f o = prev[i];
                    float vx = (p.x - o.x) * DAMPING;
                    float vy = (p.y - o.y) * DAMPING;
                    float vz = (p.z - o.z) * DAMPING;
                    o.set(p);
                    p.x += vx;
                    p.y += vy + GRAVITY * sub * sub;
                    p.z += vz;
                }
                for (int iter = 0; iter < ITERATIONS; iter++) {
                    for (int c = 0; c < constraints.size(); c++) {
                        int[] pair = constraints.get(c);
                        float[] rest = rests.get(c);
                        project(pair[0], pair[1], rest[0], rest[1]);
                    }
                    collide();
                }
            }
            if (!warmup) {
                FRAMES++;
            }
        }

        private void project(int ia, int ib, float rest, float stiffness) {
            Vector3f a = now[ia];
            Vector3f b = now[ib];
            float dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 1.0E-5F) {
                return;
            }
            float diff = (d - rest) / d * 0.5F * stiffness;
            a.x += dx * diff;
            a.y += dy * diff;
            a.z += dz * diff;
            b.x -= dx * diff;
            b.y -= dy * diff;
            b.z -= dz * diff;
        }

        /** Pushes every particle out of the captured cubes and above the mattress. */
        private void collide() {
            for (int i = 0; i < count; i++) {
                Vector3f p = now[i];
                for (int box = 0, n = boxMin.length / 3; box < n; box++) {
                    int base = box * 3;
                    float minX = boxMin[base] - THICKNESS, minY = boxMin[base + 1] - THICKNESS,
                            minZ = boxMin[base + 2] - THICKNESS;
                    float maxX = boxMax[base] + THICKNESS, maxY = boxMax[base + 1] + THICKNESS,
                            maxZ = boxMax[base + 2] + THICKNESS;
                    if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY || p.z < minZ || p.z > maxZ) {
                        continue;
                    }
                    // Inside: escape through the nearest face.
                    float dxMin = p.x - minX, dxMax = maxX - p.x;
                    float dyMin = p.y - minY, dyMax = maxY - p.y;
                    float dzMin = p.z - minZ, dzMax = maxZ - p.z;
                    float best = Math.min(Math.min(dxMin, dxMax), Math.min(Math.min(dyMin, dyMax),
                            Math.min(dzMin, dzMax)));
                    if (best == dxMin) {
                        p.x = minX;
                    } else if (best == dxMax) {
                        p.x = maxX;
                    } else if (best == dyMin) {
                        p.y = minY;
                    } else if (best == dyMax) {
                        p.y = maxY;
                    } else if (best == dzMin) {
                        p.z = minZ;
                    } else {
                        p.z = maxZ;
                    }
                }
                // Mattress: only inside the bed footprint, so the overhang may still fall down the sides.
                if (p.y < bedTop + THICKNESS && insideBed(p)) {
                    p.y = (float) (bedTop + THICKNESS);
                }
            }
        }

        private boolean insideBed(Vector3f p) {
            Vec3 local = new Vec3(p.x - anchor.x, 0.0D, p.z - anchor.z);
            double alongOffset = local.dot(along);
            double acrossOffset = local.dot(across);
            return Math.abs(alongOffset) <= 1.05D && Math.abs(acrossOffset) <= 0.52D;
        }

        private void refreshBoxes(ColliderSet set) {
            int n = set.boxes.size();
            if (boxMin.length != n * 3) {
                boxMin = new float[n * 3];
                boxMax = new float[n * 3];
            }
            Matrix4f toWorld = set.viewToWorld;
            for (int i = 0; i < n; i++) {
                QuiltBox box = set.boxes.get(i);
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
                float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
                for (int corner = 0; corner < 8; corner++) {
                    Vector3f v = toWorld.transformPosition(
                            new Vector3f(box.x(corner), box.y(corner), box.z(corner)));
                    minX = Math.min(minX, v.x);
                    minY = Math.min(minY, v.y);
                    minZ = Math.min(minZ, v.z);
                    maxX = Math.max(maxX, v.x);
                    maxY = Math.max(maxY, v.y);
                    maxZ = Math.max(maxZ, v.z);
                }
                int base = i * 3;
                boxMin[base] = minX;
                boxMin[base + 1] = minY;
                boxMin[base + 2] = minZ;
                boxMax[base] = maxX;
                boxMax[base + 1] = maxY;
                boxMax[base + 2] = maxZ;
            }
        }

        private float minY() {
            float min = Float.MAX_VALUE;
            for (Vector3f p : now) {
                min = Math.min(min, p.y);
            }
            return min;
        }

        private float maxY() {
            float max = -Float.MAX_VALUE;
            for (Vector3f p : now) {
                max = Math.max(max, p.y);
            }
            return max;
        }

        private void draw(PoseStack pose, MultiBufferSource.BufferSource buffers, DyeColor color) {
            VertexConsumer consumer = buffers.getBuffer(
                    RenderType.entityCutoutNoCull(QuiltRenderer.textureFor(color)));
            PoseStack.Pose entry = pose.last();
            int light = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
            for (int row = 0; row + 1 < ROWS; row++) {
                for (int col = 0; col + 1 < COLS; col++) {
                    int a = row * COLS + col;
                    int b = a + 1;
                    int c = a + COLS + 1;
                    int d = a + COLS;
                    float u0 = (float) col / (COLS - 1) * TILE;
                    float u1 = (float) (col + 1) / (COLS - 1) * TILE;
                    float v0 = (float) row / (ROWS - 1) * TILE;
                    float v1 = (float) (row + 1) / (ROWS - 1) * TILE;
                    // Shade by height so the sheet reads as a surface instead of a flat decal.
                    float shade = 0.75F + 0.25F * Math.min(1.0F, Math.max(0.0F,
                            (now[a].y - (float) bedTop) / 0.35F));
                    vertex(consumer, entry, now[a], u0, v0, light, shade);
                    vertex(consumer, entry, now[b], u1, v0, light, shade);
                    vertex(consumer, entry, now[c], u1, v1, light, shade);
                    vertex(consumer, entry, now[d], u0, v1, light, shade);
                }
            }
            buffers.endBatch(RenderType.entityCutoutNoCull(QuiltRenderer.textureFor(color)));
        }

        private static void vertex(VertexConsumer consumer, PoseStack.Pose entry, Vector3f p, float u, float v,
                                   int light, float shade) {
            consumer.vertex(entry.pose(), p.x, p.y, p.z)
                    .color(shade, shade, shade, 1.0F)
                    .uv(u, v)
                    .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(entry.normal(), 0.0F, 1.0F, 0.0F)
                    .endVertex();
        }
    }
}
