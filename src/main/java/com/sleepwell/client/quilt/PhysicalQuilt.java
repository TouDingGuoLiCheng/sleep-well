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
    /** Fewer sweeps while settling: the sheet only has to look right, not converge to machine precision. */
    private static final int WARMUP_ITERATIONS = 3;
    private static final float GRAVITY = -9.8F;
    private static final float DAMPING = 0.985F;
    private static final float DAMPING_CALM = 0.86F;
    /** Set per frame from the measured average speed; not thread state, just a cheap settle detector. */
    private static boolean CALM;
    private static final float THICKNESS = 0.012F;
    /** Upper bound on how far one particle may move per substep, in blocks. */
    private static final float MAX_STEP = 0.05F;
    /** Broadphase cell. Her cubes are small, so a particle only needs to look at its own cell. */
    private static final float CELL = 0.5F;
    /** Fraction of tangential speed kept when the cloth rubs against something; 0 = glued. */
    private static final float FRICTION = 0.18F;
    /**
     * How far the cloth floats above her skin: this is the duvet's own padding. Without it the sheet lies
     * straight on a body that is only 0.23 blocks tall, which reads as a flat bed sheet rather than a quilt.
     */
    private static final float DUET_PADDING = 0.09F;
    /** Below this average per-substep motion the sheet is considered settled and gets extra damping. */
    private static final float CALM_SPEED = 0.0016F;
    /** One solve, done properly: the sheet is settled once and then frozen (R6). */
    private static final int WARMUP = 300;
    /** How often the frozen quilt is re-checked against her current pose. */
    private static final int RECHECK_TICKS = 100;
    /** How many rows at the pillow end are held in place. */
    private static final int PINNED_ROWS = 1;
    private static final float PIN_STRENGTH = 0.12F;
    /** Half a bed's width, plus the overhang that hangs down the sides. */
    private static final float HALF_WIDTH = 0.72F;
    /** How far short of the pillow end the quilt starts, in blocks (measured from the bed centre). */
    private static final float HEAD_INSET = 0.35F;
    /** How far past the bed centre it reaches toward the foot. */
    private static final float FOOT_REACH = 1.08F;
    /** Hard caps for the footprint-driven sizing, so one long tail cannot turn the quilt into a tent. */
    private static final float MAX_HALF_WIDTH = 1.05F;
    private static final float MAX_FOOT_REACH = 1.75F;
    private static final float TILE = 1.0F;

    private static final Map<UUID, Cloth> CLOTHS = new HashMap<>();
    private static int FRAMES;
    private static int TICK_LOGS = 300;
    /** Bounded: the skip path can fire every frame and would flood the log. */
    private static int SKIP_LOGS = 5;
    /** At most one new quilt is solved per frame, which keeps the one-time cost off the frame budget. */
    private static boolean SOLVED_THIS_FRAME;

    /** Anything that changes her rendered geometry must invalidate the cache. */
    private static String modelKey(EntityMaid maid) {
        Object id = com.sleepwell.client.quilt.collider.Reflect.call(maid, "getModelId");
        return id == null ? "unknown" : id.toString();
    }

    /** Called once per frame by the level stage: the one-solve-per-frame budget starts over here. */
    public static void beginFrame() {
        SOLVED_THIS_FRAME = false;
    }

    /** Cheap signature of the current collision geometry: total extent, enough to spot a different pose. */
    private static float fingerprint(ColliderSet set) {
        float[] bounds = set.bounds;
        return (bounds[3] - bounds[0]) + (bounds[4] - bounds[1]) + (bounds[5] - bounds[2]);
    }

    private static String shortId(EntityMaid maid) {
        String id = maid.getUUID().toString();
        return id.substring(0, 8);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String fmt(Vec3 value) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", value.x, value.y, value.z);
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private PhysicalQuilt() {
    }

    public static void clear() {
        CLOTHS.clear();
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, QuiltRenderer.Bed bed,
                              EntityMaid maid) {
        ColliderSet set = MaidColliderCapture.get(maid);
        if (set == null || set.boxes.isEmpty()) {
            // Silence here is what made "the quilt vanished" take a log hunt to explain.
            if (SKIP_LOGS-- > 0) {
                LOGGER.warn("[被子] 布料跳过 maid={} set={} boxes={}", shortId(maid),
                        set == null ? "null" : "ok", set == null ? -1 : set.boxes.size());
            }
            return;
        }
        // Entities are drawn before AFTER_ENTITIES, so a set captured this frame carries the counter of the
        // previous one. Anything older than that is genuinely stale.
        if (MaidColliderCapture.frameCounter() - set.captureFrame > 1) {
            // The set comes from an earlier frame, so its view-space corners no longer match the camera;
            // transforming them now is what put the collision boxes 1.5 blocks above the bed. Keep the
            // frozen sheet as it is and wait for a set captured this very frame.
            Cloth existing = CLOTHS.get(maid.getUUID());
            if (existing != null && existing.initialised) {
                existing.draw(pose, buffers, bed.color());
            } else if (SKIP_LOGS-- > 0) {
                LOGGER.info("[被子] 碰撞集不是本帧的 maid={} setFrame={} now={}（等本帧采集）", shortId(maid),
                        set.captureFrame, MaidColliderCapture.frameCounter());
            }
            return;
        }
        if (set.boxes.size() < 100 && SKIP_LOGS-- > 0) {
            LOGGER.warn("[被子] 碰撞盒偏少 maid={} boxes={}（采集可能漏骨骼，被子会穿透）", shortId(maid),
                    set.boxes.size());
        }
        Vec3 head = Vec3.atLowerCornerOf(bed.head()).add(0.5D, 0.0D, 0.5D);
        Vec3 foot = Vec3.atLowerCornerOf(bed.foot()).add(0.5D, 0.0D, 0.5D);
        Vec3 along = new Vec3(foot.x - head.x, 0.0D, foot.z - head.z).normalize();
        Vec3 across = new Vec3(-along.z, 0.0D, along.x);
        double top = bedTop(bed);
        // Bed centre, used to place the sheet before the first solve. `top` is already a world height.
        Vec3 middle = new Vec3((head.x + foot.x) * 0.5D, top, (head.z + foot.z) * 0.5D);

        // The captured corners live in the line batch's space. The level PoseStack maps world into exactly
        // that space, so its inverse is the only correct way back to world coordinates.
        Matrix4f viewToWorld = new Matrix4f(pose.last().pose()).invert();
        String modelKey = modelKey(maid);
        Cloth cloth = CLOTHS.computeIfAbsent(maid.getUUID(), id -> new Cloth());
        if (cloth.initialised && !modelKey.equals(cloth.modelKey)) {
            // Different model (or skin/model pack) means different geometry: the cached sheet no longer fits.
            LOGGER.info("[被子] 布料失效重算 maid={} 原因=模型变化 {} -> {}", shortId(maid), cloth.modelKey, modelKey);
            cloth.initialised = false;
        }
        if (cloth.initialised && cloth.bedKey != null && !cloth.bedKey.equals(bed.head())) {
            LOGGER.info("[被子] 布料失效重算 maid={} 原因=床位置变化", shortId(maid));
            cloth.initialised = false;
        }
        cloth.modelKey = modelKey;
        cloth.bedKey = bed.head();
        cloth.anchor = middle;
        cloth.along = along;
        cloth.across = across;
        cloth.bedTop = top;
        cloth.bedHead = new Vec3(head.x, top, head.z);
        cloth.bedFoot = new Vec3(foot.x, top, foot.z);
        if (!cloth.initialised && SOLVED_THIS_FRAME) {
            // Several maids coming into view at once used to stack ~400 ms of solving into one frame.
            return;
        }
        if (!cloth.initialised) {
            SOLVED_THIS_FRAME = true;
            cloth.refreshBoxes(set, viewToWorld);
            cloth.reset();
            LOGGER.info("[被子] 布料新建 maid={} model={} grid={}x{} anchor={} bedTop={} boxes={}",
                    shortId(maid), modelKey, COLS, ROWS, fmt(cloth.anchor), fmt(cloth.bedTop), set.boxes.size());
            long start = System.nanoTime();
            cloth.settle(set, viewToWorld);
            cloth.initialised = true;
            cloth.frozenAt = maid.tickCount;
            cloth.fingerprint = fingerprint(set);
            LOGGER.info("[被子] 布料解算完成并冻结 maid={} steps={} 耗时={}ms yRange={}..{} 沿床覆盖={} 逐行={}",
                    shortId(maid), WARMUP, String.format(Locale.ROOT, "%.1f",
                            (System.nanoTime() - start) / 1.0E6), fmt(cloth.minY()), fmt(cloth.maxY()),
                    cloth.alongSpan(), cloth.rowHeights());
        }
        // No periodic re-solve: the fingerprint check caused 300-step rebuilds whenever her idle animation
        // nudged the geometry, which is exactly the stutter that got the cloth rolled back. The cache now
        // only dies on a real change (different model, different bed).
        cloth.draw(pose, buffers, bed.color());
        if (--TICK_LOGS <= 0) {
            TICK_LOGS = 300;
            float minY = cloth.minY();
            float maxY = cloth.maxY();
            float drift = (float) Math.abs(cloth.centroid().y - (cloth.bedTop + 0.15D));
            LOGGER.info("[被子] 布料 frame={} p={} boxes={} yRange={}..{} 床面={} 质心高度差={} 悬浮={} 沿床覆盖={} 静止={}",
                    FRAMES, cloth.count, set.boxes.size(), fmt(minY), fmt(maxY), fmt(cloth.bedTop),
                    fmt(drift), drift > 1.5F ? "是" : "否", cloth.alongSpan(), CALM ? "是" : "否");
            if (drift > 1.5F) {
                LOGGER.warn("[被子] 布料整体离床 {} 格（床面 {}，质心 {}）：坐标换算或碰撞盒方向有问题",
                        fmt(drift), fmt(cloth.bedTop), fmt(cloth.centroid().y));
            }
            if (Float.isNaN(minY) || Float.isNaN(maxY)) {
                LOGGER.error("[被子] 布料出现 NaN，重置 maid={}", shortId(maid));
                cloth.initialised = false;
            }
        }
    }

    /**
     * World height of the mattress surface. The block's shape only reports its local top (0..1), so the
     * bed's own Y has to be added: using the local value alone pinned the whole sheet near world zero,
     * which is how the quilt "disappeared" while sitting at y = 0.57.
     */
    private static double bedTop(QuiltRenderer.Bed bed) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return bed.head().getY() + 0.5125D;
        }
        BlockState headState = level.getBlockState(bed.head());
        BlockState footState = level.getBlockState(bed.foot());
        double a = headState.getShape(level, bed.head()).max(Direction.Axis.Y);
        double b = footState.getShape(level, bed.foot()).max(Direction.Axis.Y);
        double local = Math.min(a, b);
        if (local <= 0.0D || local > 0.60D) {
            local = 0.5125D;
        }
        return bed.head().getY() + local;
    }

    /** One sheet: positions, previous positions and the constraint list. */
    private static final class Cloth {
        private final Vector3f[] now = new Vector3f[COLS * ROWS];
        private final Vector3f[] prev = new Vector3f[COLS * ROWS];
        private final List<int[]> constraints = new ArrayList<>();
        private final List<float[]> rests = new ArrayList<>();
        private final int count = COLS * ROWS;
        private final float[] pinX = new float[COLS * ROWS];
        private final float[] pinY = new float[COLS * ROWS];
        private final float[] pinZ = new float[COLS * ROWS];
        private boolean initialised;
        private String modelKey = "";
        private BlockPos bedKey;
        private int frozenAt;
        private float fingerprint;
        private Vec3 anchor = Vec3.ZERO;
        private Vec3 along = Vec3.ZERO;
        private Vec3 across = Vec3.ZERO;
        private double bedTop;
        private Vec3 bedHead = Vec3.ZERO;
        private Vec3 bedFoot = Vec3.ZERO;
        /** World-space AABBs of the captured cubes, rebuilt every frame from the latest pose. */
        private float[] boxMin = new float[0];
        private float[] boxMax = new float[0];
        /** Box indices bucketed by cell, rebuilt once per settle. Turns 408 tests per particle into a few. */
        private final java.util.HashMap<Long, int[]> grid = new java.util.HashMap<>();

        private void reset() {
            // Size the sheet from what she actually occupies: a tail or a long pair of legs that sticks out
            // past the bed would otherwise sit outside the cloth and stay uncovered.
            float halfWidth = HALF_WIDTH;
            float footReach = FOOT_REACH;
            float headInset = HEAD_INSET;
            int n = boxMin.length / 3;
            if (n > 0) {
                float maxAcross = 0.0F;
                float minAlong = -HEAD_INSET;
                float maxAlong = FOOT_REACH;
                for (int i = 0; i < n; i++) {
                    int base = i * 3;
                    for (int corner = 0; corner < 8; corner++) {
                        float x = (corner & 1) == 0 ? boxMin[base] : boxMax[base];
                        float z = (corner & 2) == 0 ? boxMin[base + 2] : boxMax[base + 2];
                        double alongOffset = (x - anchor.x) * along.x + (z - anchor.z) * along.z;
                        double acrossOffset = (x - anchor.x) * across.x + (z - anchor.z) * across.z;
                        maxAcross = (float) Math.max(maxAcross, Math.abs(acrossOffset));
                        minAlong = (float) Math.min(minAlong, alongOffset);
                        maxAlong = (float) Math.max(maxAlong, alongOffset);
                    }
                }
                halfWidth = Math.min(MAX_HALF_WIDTH, Math.max(halfWidth, maxAcross + 0.10F));
                footReach = Math.min(MAX_FOOT_REACH, Math.max(footReach, maxAlong + 0.12F));
                // Never let the head end creep onto the pillow: an inset of zero is the limit.
                headInset = Math.max(0.0F, Math.min(headInset, -minAlong + 0.10F));
            }
            LOGGER.info("[被子] 覆盖范围 半宽={} 枕端={} 脚端={}（盒数={}）", fmt(halfWidth), fmt(headInset),
                    fmt(footReach), n);
            // Drop it from above her tallest point: starting level with the mattress put the sheet inside her
            // body and inside any tail, and the collision then pushed those particles downwards.
            double startY = Math.max(bedTop + 0.14D, boxMaxY() + 0.12D);
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    float u = COLS == 1 ? 0.0F : (float) col / (COLS - 1);
                    float v = ROWS == 1 ? 0.0F : (float) row / (ROWS - 1);
                    double offsetAcross = (u - 0.5) * 2.0 * halfWidth;
                    // Along: asymmetric on purpose. A quilt starts below the pillow, so the head end is
                    // inset while the foot end still drapes past the mattress.
                    double offsetAlong = -headInset + (footReach + headInset) * v;
                    Vec3 p = new Vec3(anchor.x + across.x * offsetAcross + along.x * offsetAlong, startY,
                            anchor.z + across.z * offsetAcross + along.z * offsetAlong);
                    int index = row * COLS + col;
                    now[index] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
                    prev[index] = new Vector3f((float) p.x, (float) p.y, (float) p.z);
                    pinX[index] = (float) p.x;
                    pinY[index] = (float) p.y;
                    pinZ[index] = (float) p.z;
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

        private void settle(ColliderSet set, Matrix4f viewToWorld) {
            refreshBoxes(set, viewToWorld);
            refreshBoxes(set, viewToWorld);
            LOGGER.info("[被子] 碰撞校验 布质心={} 盒质心={} 间距={} 盒数={} 盒Y范围={}..{}",
                    fmt(centroid()), fmt(boxCentroid()), fmt(centroid().distanceTo(boxCentroid())),
                    boxMin.length / 3, fmt(boxMinY()), fmt(boxMaxY()));
            for (int i = 0; i < WARMUP; i++) {
                // No refresh and no grid rebuild inside the loop: the pose cannot change mid-settle.
                step(set, viewToWorld, true);
            }
        }

        private void step(ColliderSet set, Matrix4f viewToWorld) {
            step(set, viewToWorld, false);
        }

        private void step(ColliderSet set, Matrix4f viewToWorld, boolean warmup) {
            if (!warmup) {
                refreshBoxes(set, viewToWorld);
            }
            float sub = DT / SUBSTEPS;
            // While settling the sheet still has to fall, so the calm-down damping must not apply yet.
            float damping = (!warmup && CALM) ? DAMPING_CALM : DAMPING;
            for (int s = 0; s < SUBSTEPS; s++) {
                for (int i = 0; i < count; i++) {
                    Vector3f p = now[i];
                    Vector3f o = prev[i];
                    float vx = (p.x - o.x) * damping;
                    float vy = (p.y - o.y) * damping;
                    float vz = (p.z - o.z) * damping;
                    o.set(p);
                    p.x += vx;
                    p.y += vy + GRAVITY * sub * sub;
                    p.z += vz;
                    // Cap the per-substep displacement: without this a single deep overlap can inject
                    // enough energy to fling the sheet around.
                    float mx = p.x - o.x, my = p.y - o.y, mz = p.z - o.z;
                    float dist = (float) Math.sqrt(mx * mx + my * my + mz * mz);
                    if (dist > MAX_STEP) {
                        float k = MAX_STEP / dist;
                        p.set(o.x + mx * k, o.y + my * k, o.z + mz * k);
                    }
                    if (Math.abs(p.y - (float) bedTop) > 4.0F || p.distanceSquared(o) > 4.0F) {
                        // Runaway particle: put it back on the bed instead of letting one bad push fling
                        // the whole sheet out of the world.
                        p.set((float) anchor.x, (float) (bedTop + 0.4D), (float) anchor.z);
                        o.set(p);
                    }
                }
                int iterations = warmup ? WARMUP_ITERATIONS : ITERATIONS;
                for (int iter = 0; iter < iterations; iter++) {
                    for (int c = 0; c < constraints.size(); c++) {
                        int[] pair = constraints.get(c);
                        float[] rest = rests.get(c);
                        project(pair[0], pair[1], rest[0], rest[1]);
                    }
                    collide();
                    pinHeadEnd();
                }
            }
            if (!warmup) {
                FRAMES++;
                float speed = 0.0F;
                for (int i = 0; i < count; i++) {
                    speed += now[i].distance(prev[i]);
                }
                CALM = speed / count / SUBSTEPS < CALM_SPEED;
            }
        }

        /** Rows at the pillow end stay put, the way a quilt is tucked under the pillow. */
        private void pinHeadEnd() {
            for (int row = 0; row < PINNED_ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int index = row * COLS + col;
                    Vector3f p = now[index];
                    p.x += (pinX[index] - p.x) * PIN_STRENGTH;
                    p.y += (pinY[index] - p.y) * PIN_STRENGTH;
                    p.z += (pinZ[index] - p.z) * PIN_STRENGTH;
                }
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
                Vector3f cameFrom = prev[i];
                int[] candidates = grid.get(key(cell(p.x), cell(p.y), cell(p.z)));
                if (candidates != null) {
                    collideWith(p, candidates, cameFrom);
                }
                // Same check one cell up: a particle resting exactly on a cell boundary must still see the
                // cubes just above it.
                int[] above = grid.get(key(cell(p.x), cell(p.y) + 1, cell(p.z)));
                if (above != null) {
                    collideWith(p, above, cameFrom);
                }
                // Mattress: only inside the bed footprint, so the overhang may still fall down the sides.
                if (p.y < bedTop + THICKNESS && insideBed(p)) {
                    p.y = (float) (bedTop + THICKNESS);
                    Vector3f previous = prev[i];
                    previous.x = p.x + (previous.x - p.x) * FRICTION;
                    previous.z = p.z + (previous.z - p.z) * FRICTION;
                }
            }
        }

        private void collideWith(Vector3f p, int[] candidates, Vector3f cameFrom) {
            for (int box : candidates) {
                int base = box * 3;
                float minX = boxMin[base] - DUET_PADDING, minY = boxMin[base + 1] - THICKNESS,
                        minZ = boxMin[base + 2] - DUET_PADDING;
                float maxX = boxMax[base] + DUET_PADDING, maxY = boxMax[base + 1] + DUET_PADDING,
                        maxZ = boxMax[base + 2] + DUET_PADDING;
                if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY || p.z < minZ || p.z > maxZ) {
                    continue;
                }
                // Inside: escape through the nearest face.
                float dxMin = p.x - minX, dxMax = maxX - p.x;
                float dyMin = p.y - minY, dyMax = maxY - p.y;
                float dzMin = p.z - minZ, dzMax = maxZ - p.z;
                // A particle that arrived from above must leave through the top face. Escaping through the
                // nearest face instead dropped the sheet inside tails and folded it under her body.
                if (cameFrom.y >= maxY - 1.0E-4F) {
                    p.y = maxY;
                    continue;
                }
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
        }

        private boolean insideBed(Vector3f p) {
            Vec3 local = new Vec3(p.x - anchor.x, 0.0D, p.z - anchor.z);
            double alongOffset = local.dot(along);
            double acrossOffset = local.dot(across);
            return Math.abs(alongOffset) <= 1.05D && Math.abs(acrossOffset) <= 0.52D;
        }

        private void refreshBoxes(ColliderSet set, Matrix4f viewToWorld) {
            int n = set.boxes.size();
            if (boxMin.length != n * 3) {
                boxMin = new float[n * 3];
                boxMax = new float[n * 3];
            }
            Matrix4f toWorld = viewToWorld;
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
            buildGrid(n);
        }

        private void buildGrid(int n) {
            grid.clear();
            java.util.HashMap<Long, java.util.ArrayList<Integer>> buckets = new java.util.HashMap<>();
            for (int i = 0; i < n; i++) {
                int base = i * 3;
                int x0 = cell(boxMin[base]), x1 = cell(boxMax[base]);
                int y0 = cell(boxMin[base + 1]), y1 = cell(boxMax[base + 1]);
                int z0 = cell(boxMin[base + 2]), z1 = cell(boxMax[base + 2]);
                for (int x = x0; x <= x1; x++) {
                    for (int y = y0; y <= y1; y++) {
                        for (int z = z0; z <= z1; z++) {
                            buckets.computeIfAbsent(key(x, y, z), k -> new java.util.ArrayList<>()).add(i);
                        }
                    }
                }
            }
            for (java.util.Map.Entry<Long, java.util.ArrayList<Integer>> entry : buckets.entrySet()) {
                int[] values = new int[entry.getValue().size()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = entry.getValue().get(i);
                }
                grid.put(entry.getKey(), values);
            }
        }

        private static int cell(float value) {
            return (int) Math.floor(value / CELL);
        }

        private static long key(int x, int y, int z) {
            return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
        }

        private float boxMinY() {
            float min = Float.MAX_VALUE;
            for (int i = 0; i < boxMin.length / 3; i++) {
                min = Math.min(min, boxMin[i * 3 + 1]);
            }
            return min;
        }

        private float boxMaxY() {
            float max = -Float.MAX_VALUE;
            for (int i = 0; i < boxMin.length / 3; i++) {
                max = Math.max(max, boxMax[i * 3 + 1]);
            }
            return max;
        }

        /** Height of the middle particle of every fourth row: shows at a glance whether the sheet sagged. */
        private String rowHeights() {
            StringBuilder out = new StringBuilder();
            for (int row = 0; row < ROWS; row += 4) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(fmt(now[row * COLS + COLS / 2].y));
            }
            return out.toString();
        }

        private Vec3 boxCentroid() {
            int n = boxMin.length / 3;
            if (n == 0) {
                return Vec3.ZERO;
            }
            double x = 0, y = 0, z = 0;
            for (int i = 0; i < n; i++) {
                int base = i * 3;
                x += (boxMin[base] + boxMax[base]) * 0.5D;
                y += (boxMin[base + 1] + boxMax[base + 1]) * 0.5D;
                z += (boxMin[base + 2] + boxMax[base + 2]) * 0.5D;
            }
            return new Vec3(x / n, y / n, z / n);
        }

        /** How far the sheet spans along the bed, measured from the bed centre, in blocks. */
        private String alongSpan() {
            float min = Float.MAX_VALUE;
            float max = -Float.MAX_VALUE;
            for (Vector3f p : now) {
                double offset = (p.x - anchor.x) * along.x + (p.z - anchor.z) * along.z;
                min = (float) Math.min(min, offset);
                max = (float) Math.max(max, offset);
            }
            return String.format(Locale.ROOT, "%.2f..%.2f", min, max);
        }

        private Vec3 centroid() {
            double x = 0, y = 0, z = 0;
            for (Vector3f p : now) {
                x += p.x;
                y += p.y;
                z += p.z;
            }
            return new Vec3(x / count, y / count, z / count);
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
