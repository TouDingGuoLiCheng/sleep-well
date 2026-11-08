package com.sleepwell.client.quilt.collider;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Samples the maid's sleeping outline from the render pass, per frame.
 * <p>
 * Why the render pass: only there is her model in the <em>sleeping</em> pose. Every model pack ships its own
 * {@code sleep} animation, so the rest pose (what any other moment gives you) is the wrong shape and, worse,
 * it is the same shape for every maid - which is exactly the bug the old quilt had.
 * <p>
 * Two paths, because TLM 1.5 ships two model systems:
 * <ul>
 *   <li><b>gecko</b>: {@code geckoEntityMaidRenderer} → {@code getAnimatableEntity} → {@code getGeoModel()}
 *       → {@code topLevelBones} of an {@code AnimatedGeoModel}. Bone transforms are post-animation;
 *       cubes come from {@code GeoMesh} as origin + three edge vectors.</li>
 *   <li><b>bedrock</b>: {@code modelMap} parts, read by their public x/y/z fields. Coarse on purpose -
 *       this path is the legacy models only.</li>
 * </ul>
 * Nothing here throws: a missed reflection hop yields "no colliders", never a broken render thread.
 */
public final class MaidColliderCapture {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float PIXELS = 16.0F;
    private static final float MAX_RADIUS = 0.10F;
    /** Thickness given to genuinely flat panels (skirts, capes) so they still collide as a sheet. */
    private static final float PLATE = 0.01F;

    /** Bone name fragments that get real cloth collision. Everything else only repels. */
    private static final String[] HARD_BITS = {
            "body", "chest", "head", "arm", "hand", "leg", "foot", "breast", "waist", "torso", "hip", "shoulder"
    };

    private static final Map<UUID, ColliderSet> SETS = new HashMap<>();
    /** Animated model per maid: it is only handed out while she renders, so it is kept once seen. */
    private static final Map<UUID, Object> MODELS = new HashMap<>();
    private static final Set<String> MISSES = new HashSet<>();
    private static volatile String LAST_MISS = "";
    private static volatile int hookRuns;
    private static volatile int captures;
    private static volatile int renderRuns;

    /**
     * Called from the maid renderer's HEAD: her model is alive here, so the outline can be rebuilt with the
     * transform we compute ourselves. This is the path that does not depend on any bone-level mixin.
     */
    public static void captureDuringRender(EntityMaid maid) {
        if (!maid.isSleeping()) {
            return;
        }
        renderRuns++;
        try {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(maid);
            Object gecko = renderer == null ? null : Reflect.field(renderer, "geckoEntityMaidRenderer");
            Object animatable = gecko == null ? null : Reflect.call(gecko, "getAnimatableEntity", maid);
            String via = "renderer";
            if (animatable == null) {
                animatable = animatableViaCapability(maid);
                via = "capability";
            }
            Object model = MODELS.get(maid.getUUID());
            if (model == null && animatable != null) {
                model = Reflect.call(animatable, "getGeoModel");
            }
            if (model == null && animatable != null) {
                Object updated = Reflect.call(animatable, "updateModel");
                model = Reflect.call(animatable, "getCurrentModel");
            }
            if (model != null) {
                MODELS.put(maid.getUUID(), model);
            }
            if (model == null) {
                miss("render:model", "animatable=" + describe(animatable) + " renderer=" + describe(renderer));
                return;
            }
            List<?> roots = Reflect.list(model, "topLevelBones");
            if (roots == null || roots.isEmpty()) {
                miss("render:roots", model.getClass().getName());
                return;
            }
            net.minecraft.world.phys.Vec3 camera =
                    Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Matrix4f entity = entityMatrix(maid, camera, 0.0F);
            List<QuiltBox> boxes = new ArrayList<>();
            for (Object root : roots) {
                walkBone(root, new Matrix4f(entity), boxes, 0);
            }
            if (boxes.isEmpty()) {
                miss("render:empty", "bones walked but no cubes");
                return;
            }
            captures++;
            store(maid.getUUID(), new ColliderSet(boxes, bounds(boxes), stamp(boxes), maid.tickCount, false));
            if (RENDER_LOGS.getAndIncrement() == 0) {
                LOGGER.info("[sleep_well] render capture boxes={} model={}",
                        boxes.size(), model.getClass().getSimpleName());
            }
        } catch (Throwable error) {
            miss("render:" + error.getClass().getSimpleName(), error.toString());
        }
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static final java.util.concurrent.atomic.AtomicInteger RENDER_LOGS =
            new java.util.concurrent.atomic.AtomicInteger();

    /** How many render passes reached the capture: 0 means the renderer mixin did not apply. */
    public static String mixinState() {
        return String.valueOf(renderRuns);
    }

    private MaidColliderCapture() {
    }

    // --- bedrock parts: captured while the model is actually drawn ---------------------------------------

    /**
     * Model packs that are not gecko (the vanilla-style maids) draw {@code BedrockPart} trees, and the part's
     * own matrix is the only exact statement of where its cubes end up. The part render hook feeds this list
     * while one maid render pass is open; the pass closes at the renderer's TAIL.
     * <p>
     * Gecko models never touch this: they are drawn as bones, not {@code BedrockPart}s, so the Wine Fox models
     * keep going through the layer capture exactly as before.
     */
    private static List<QuiltBox> partBoxes;
    private static java.util.Set<Object> partSeen;
    private static int partPasses;

    /** Opens a capture pass for one maid render. */
    public static void beginRenderPass(EntityMaid maid) {
        if (maid.isSleeping()) {
            partBoxes = new ArrayList<>();
            partSeen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        } else {
            partBoxes = null;
            partSeen = null;
        }
    }

    /** True while a sleeping maid is being drawn: the part hook only pays for itself then. */
    public static boolean wantsParts() {
        return partBoxes != null;
    }

    /** One {@code BedrockPart} was drawn: record the cubes its matrix places. */
    public static void partRendered(Object part, Matrix4f matrix) {
        List<QuiltBox> out = partBoxes;
        if (out == null || part == null) {
            return;
        }
        if (partSeen != null && !partSeen.add(part)) {
            return;
        }
        Object cubes = Reflect.field(part, "cubes");
        if (!(cubes instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        for (Object cube : list) {
            appendBedrockCube(cube, matrix, "part", out);
        }
    }

    /** Closes the pass; a full set replaces whatever the reflection paths had produced. */
    public static void endRenderPass(EntityMaid maid) {
        List<QuiltBox> boxes = partBoxes;
        partBoxes = null;
        partSeen = null;
        if (boxes == null || boxes.size() < 8) {
            return;
        }
        // The part matrices already produce camera-relative coordinates with the camera translation baked in,
        // and the level batch applies the camera rotation itself when it uploads. Rotating here as well is what
        // made the outline swing around with the view instead of staying on her: R applied twice.
        ColliderSet set = new ColliderSet(boxes, bounds(boxes), stamp(boxes), maid.tickCount, true);
        set.fromLayer = true;
        set.viewSpace = true;
        set.captureFrame = frameCounter();
        set.viewToWorld = null;
        set.pendingWorldMatrix = true;
        set.capsules = Capsules.fit(boxes);
        // Publish through the guard: a model-specific outline captured earlier this frame outranks this one.
        store(maid.getUUID(), set);
        captures++;
        // DEBUG on purpose: the capture runs every frame per sleeping maid, and at INFO this one line was
        // most of the log file. Turn it up when the quilt's footprint looks wrong.
        if (partPasses++ % 200 == 0) {
            float[] b = set.bounds;
            LOGGER.debug("[被子] 模型采集 maid={} boxes={} size={}x{}x{}", maid.getUUID(), boxes.size(),
                    String.format(java.util.Locale.ROOT, "%.2f", b[3] - b[0]),
                    String.format(java.util.Locale.ROOT, "%.2f", b[4] - b[1]),
                    String.format(java.util.Locale.ROOT, "%.2f", b[5] - b[2]));
        }
    }

    /** One bedrock cube: origin plus three sizes in model pixels, then through the part's matrix. */
    private static void appendBedrockCube(Object cube, Matrix4f matrix, String name, List<QuiltBox> boxes) {
        float x = Reflect.floatField(cube, "x");
        float y = Reflect.floatField(cube, "y");
        float z = Reflect.floatField(cube, "z");
        float w = Reflect.floatField(cube, "width");
        float h = Reflect.floatField(cube, "height");
        float d = Reflect.floatField(cube, "depth");
        if (w <= 0.0F && h <= 0.0F && d <= 0.0F) {
            return;
        }
        // TLM's bedrock loader hands cubes out already divided by 16 (a blockbench 8 px cube arrives as 0.5),
        // so dividing again shrank every box to a centimetre and the wireframe collapsed to a dot. Packs that
        // really are in pixels still show up with large numbers, hence the guard instead of a bare 1/16.
        float unit = (w > 2.0F || h > 2.0F || d > 2.0F) ? 1.0F / PIXELS : 1.0F;
        // Skirts, coat tails and capes ship as zero-thickness panels: dropping them left whole garments out of
        // the outline. A flat sheet still collides like cloth, it just needs a plate thin enough to act flat.
        float thickness = PLATE * unit;
        float bw = Math.max(w * unit, thickness);
        float bh = Math.max(h * unit, thickness);
        float bd = Math.max(d * unit, thickness);
        float bx = x * unit;
        float by = y * unit;
        float bz = z * unit;
        QuiltBox box = new QuiltBox(name);
        for (int corner = 0; corner < 8; corner++) {
            float a = (corner & 1) != 0 ? 1.0F : 0.0F;
            float b = (corner & 2) != 0 ? 1.0F : 0.0F;
            float c = (corner & 4) != 0 ? 1.0F : 0.0F;
            Vector3f local = new Vector3f(bx + bw * a, by + bh * b, bz + bd * c);
            Vector3f world = local.mulPosition(matrix);
            box.setCorner(corner, world.x, world.y, world.z);
        }
        box.radius = Math.max(QuiltBox.MIN_RADIUS, Math.min(MAX_RADIUS, box.shortestEdge() * 0.5F));
        boxes.add(box);
    }


    private static Matrix4f entityMatrix(EntityMaid maid, net.minecraft.world.phys.Vec3 camera, float partialTick) {
        net.minecraft.world.phys.Vec3 position = maid.getPosition(partialTick).subtract(camera);
        float yaw = net.minecraft.util.Mth.rotLerp(partialTick, maid.yBodyRotO, maid.yBodyRot);
        Matrix4f matrix = new Matrix4f()
                .translate((float) position.x, (float) position.y, (float) position.z)
                .rotateY((float) Math.toRadians(180.0F - yaw));
        net.minecraft.core.Direction bed = maid.getBedOrientation();
        if (maid.isSleeping() && bed != null) {
            float eye = maid.getEyeHeight(net.minecraft.world.entity.Pose.STANDING) - 0.1F;
            matrix.translate(0.0F, -eye, 0.0F);
            matrix.rotateY((float) Math.toRadians(-bed.toYRot()));
            matrix.rotateZ((float) Math.toRadians(-90.0F));
        }
        return matrix;
    }

    /** How many times the render hook was entered - 0 means the mixin never fired. */
    public static int hookRuns() {
        return hookRuns;
    }

    /** How many times a non-empty outline was produced. */
    public static int captures() {
        return captures;
    }

    /** One-line diagnosis for the in-game message. */
    public static String misses() {
        return MISSES.toString();
    }

    /** Last captured outline, or null when she has never been rendered while asleep. */
    public static ColliderSet get(EntityMaid maid) {
        return SETS.get(maid.getUUID());
    }

    public static void forget(UUID id) {
        SETS.remove(id);
    }

    // --- gecko -------------------------------------------------------------

    // --- gecko render layer: the only hook that sees the real, posed geometry ---------------------------------

    private static final java.util.Set<Object> LAYERED =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static int layerRuns;
    private static int layerCalls;
    private static String layerMiss = "-";
    private static float[] layerBounds;

    /**
     * The render-layer capture is the only trustworthy source, so a set built elsewhere must never
     * replace a fresh one: that overwrite is what kept pushing the wireframe into the sky.
     */
    private static void store(UUID id, ColliderSet set) {
        ColliderSet existing = SETS.get(id);
        if (existing != null && existing.layerSource && !set.layerSource && set.tick - existing.tick <= 3) {
            return;
        }
        SETS.put(id, set);
    }

    /**
     * Makes sure the capture layer is attached. The wireframe used to be the only caller, so switching the
     * debug view off silently stopped all capture and the quilt had no collision set at all.
     */
    public static void ensureLayer(EntityMaid maid) {
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(maid);
        if (renderer != null) {
            installLayer(renderer);
        }
    }

    /** Registers the capture layer on a maid renderer (idempotent, per renderer instance). */
    static void installLayer(Object renderer) {
        Object geo = Reflect.field(renderer, "geckoEntityMaidRenderer");
        if (geo == null || LAYERED.contains(geo)) {
            return;
        }
        try {
            Object layer = new QuiltGeckoLayer(
                    (com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer<
                            com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid>) geo);
            Reflect.call(geo, "addGeoLayerRenderer", layer);
            LAYERED.add(geo);
            LOGGER.info("[被子] 已挂载 gecko 采集层 -> {}", geo.getClass().getSimpleName());
        } catch (Throwable error) {
            LOGGER.warn("[被子] 挂载采集层失败: {}", error.toString());
        }
    }

    static String layerState() {
        return "layer=" + layerCalls + "/" + layerRuns + (layerBounds == null ? ""
                : String.format(java.util.Locale.ROOT, " size=%.2fx%.2fx%.2f",
                layerBounds[0], layerBounds[1], layerBounds[2])) + (layerMiss.equals("-") ? "" : " layerMiss=" + layerMiss);
    }

    /** Called from the maid's own render pass: pose is model-to-view space, already including the sleep pose. */
    /** Bumped once per rendered frame by the level stage, so a set can be proven fresh. */
    private static long FRAME;

    public static long frameCounter() {
        return FRAME;
    }

    /** Called from the level stage at the start of every frame. */
    public static void nextFrame() {
        FRAME++;
    }

    /**
     * Converts every set captured earlier in <em>this</em> frame into world space, using this frame's pose.
     * <p>
     * Must be called from the level stage while the camera pose of the frame is known. The cloth used to
     * invert a <em>newer</em> pose at solve time, so a capture taken one frame earlier was converted with a
     * camera that had already moved - and the error was frozen into the quilt for the whole sleep.
     */
    public static void resolveWorldMatrices(Matrix4f framePose) {
        Matrix4f inverse = new Matrix4f(framePose).invert();
        for (ColliderSet set : SETS.values()) {
            if (set.pendingWorldMatrix) {
                set.viewToWorld = new Matrix4f(inverse);
                set.pendingWorldMatrix = false;
            }
        }
    }

    static void captureFromLayer(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
                                 Object geoEntity, Matrix4f pose) {
        layerRuns++;
        if (maid == null || !maid.isSleeping()) {
            return;
        }
        Object model = geoEntity == null ? null : Reflect.call(geoEntity, "getCurrentModel");
        if (model == null) {
            model = Reflect.field(geoEntity, "currentModel");
        }
        if (model == null) {
            layerMiss = "noCurrentModel";
            return;
        }
        List<?> roots = Reflect.list(model, "topLevelBones");
        if (roots == null || roots.isEmpty()) {
            layerMiss = "noRootBones";
            return;
        }
        net.minecraft.client.Camera camera = net.minecraft.client.Minecraft.getInstance()
                .gameRenderer.getMainCamera();
        org.joml.Vector3f cameraPos = new org.joml.Vector3f((float) camera.getPosition().x,
                (float) camera.getPosition().y, (float) camera.getPosition().z);
        // Use the exact matrix TLM uses to draw her: the layer's PoseStack maps model space straight into
        // the space the line batch is drawn in. Copying it verbatim means the outline cannot drift away
        // from the model, no matter what the bed looks like or where a model's origin sits.
        Matrix4f viewMatrix = new Matrix4f(pose);
        List<QuiltBox> boxes = new ArrayList<>();
        for (Object root : roots) {
            walkBone(root, new Matrix4f(viewMatrix), boxes, 0);
        }
        if (boxes.isEmpty()) {
            layerMiss = "noCubes";
            return;
        }
        // No centring, no bed probing: the matrix already places every cube exactly where she is drawn.
        layerCalls++;
        layerMiss = "-";
        ColliderSet set = new ColliderSet(boxes, bounds(boxes), stamp(boxes), maid.tickCount, true);
        set.fromLayer = true;
        set.viewSpace = true;
        set.captureFrame = frameCounter();
        // Converted at the end of this very frame; see resolveWorldMatrices.
        set.viewToWorld = null;
        set.pendingWorldMatrix = true;
        set.layerSource = true;
        set.capsules = Capsules.fit(boxes);
        if (layerCalls % 600 == 1) {
            LOGGER.info("[被子] " + Capsules.summary(set.capsules));
        }
        SETS.put(maid.getUUID(), set);
        float[] b = set.bounds;
        layerBounds = new float[]{b[3] - b[0], b[4] - b[1], b[5] - b[2]};
        if (layerCalls % 600 == 1) {
            // Same bones, model space only: separates "intrinsic size" from "our transform".
            List<QuiltBox> raw = new ArrayList<>();
            for (Object root : roots) {
                walkBone(root, new Matrix4f(), raw, 0);
            }
            float[] rb = bounds(raw);
            LOGGER.info("[被子] model size={} min={} yaw={} bedDir={} posedSize={}",
                    String.format(java.util.Locale.ROOT, "%.2fx%.2fx%.2f", rb[3] - rb[0], rb[4] - rb[1], rb[5] - rb[2]),
                    String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", rb[0], rb[1], rb[2]),
                    maid.yBodyRot, maid.getBedOrientation(),
                    String.format(java.util.Locale.ROOT, "%.2fx%.2fx%.2f", b[3] - b[0], b[4] - b[1], b[5] - b[2]));
            LOGGER.info("[被子] layer boxes={} size={} min={} maid={} pose={} bed={}", boxes.size(),
                    String.format(java.util.Locale.ROOT, "%.2fx%.2fx%.2f", b[3] - b[0], b[4] - b[1], b[5] - b[2]),
                    String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", b[0], b[1], b[2]),
                    String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", maid.getX(), maid.getY(), maid.getZ()),
                    maid.getPose(),
                    maid.getBedOrientation());
        }
    }

    private static void walkBone(Object bone, Matrix4f parent, List<QuiltBox> boxes, int depth) {
        if (bone == null || depth > 64) {
            return;
        }
        if (Reflect.flag(bone, "isHidden")) {
            return;
        }
        // Cubes hidden but children still drawn: keep walking, just do not emit this bone's cubes.
        boolean cubesHidden = Reflect.flag(bone, "areCubesHidden");
        String name = String.valueOf(Reflect.call(bone, "getName"));
        Object geoBone = Reflect.call(bone, "geoBone");

        float posX = orZero(Reflect.number(bone, "getPositionX")) / PIXELS;
        float posY = orZero(Reflect.number(bone, "getPositionY")) / PIXELS;
        float posZ = orZero(Reflect.number(bone, "getPositionZ")) / PIXELS;
        float rotX = orZero(Reflect.number(bone, "getRotationX"));
        float rotY = orZero(Reflect.number(bone, "getRotationY"));
        float rotZ = orZero(Reflect.number(bone, "getRotationZ"));
        float scaleX = defaultOne(Reflect.number(bone, "getScaleX"));
        float scaleY = defaultOne(Reflect.number(bone, "getScaleY"));
        float scaleZ = defaultOne(Reflect.number(bone, "getScaleZ"));
        float pivotX = orZero(Reflect.number(bone, "getPivotX")) / PIXELS;
        float pivotY = orZero(Reflect.number(bone, "getPivotY")) / PIXELS;
        float pivotZ = orZero(Reflect.number(bone, "getPivotZ")) / PIXELS;
        if (pivotX == 0.0F && pivotY == 0.0F && pivotZ == 0.0F && geoBone != null) {
            if (Reflect.call(geoBone, "pivot") instanceof Vector3f pivot) {
                pivotX = pivot.x / PIXELS;
                pivotY = pivot.y / PIXELS;
                pivotZ = pivot.z / PIXELS;
            }
        }

        // TLM mirrors the model on X: `translateMatrixToBone` negates positionX before scaling by 1/16.
        Matrix4f boneMatrix = new Matrix4f(parent)
                .translate(-posX, posY, posZ)
                .translate(pivotX, pivotY, pivotZ)
                // TLM rotates with Quaternionf.rotateZYX(z, y, x): Z first, then Y, then X.
                .rotateZYX(rotZ, rotY, rotX)
                .scale(scaleX, scaleY, scaleZ)
                .translate(-pivotX, -pivotY, -pivotZ);

        Object mesh = cubesHidden || geoBone == null ? null : Reflect.call(geoBone, "cubes");
        if (mesh != null) {
            int count = Reflect.integer(mesh, "cubeCount");
            for (int i = 0; i < count; i++) {
                appendCube(mesh, i, name, boneMatrix, boxes);
            }
        }
        List<?> children = Reflect.list(bone, "children");
        if (children != null) {
            for (Object child : children) {
                walkBone(child, boneMatrix, boxes, depth + 1);
            }
        }
    }

    private static void appendCube(Object mesh, int index, String name, Matrix4f matrix, List<QuiltBox> boxes) {
        Vector3f origin = callVector(mesh, "position", index);
        Vector3f dx = callVector(mesh, "dx", index);
        Vector3f dy = callVector(mesh, "dy", index);
        Vector3f dz = callVector(mesh, "dz", index);
        if (origin == null || dx == null || dy == null || dz == null) {
            return;
        }
        QuiltBox box = new QuiltBox(name);
        box.hard = isHard(name);
        for (int corner = 0; corner < 8; corner++) {
            float a = (corner & 1) != 0 ? 1.0F : 0.0F;
            float b = (corner & 2) != 0 ? 1.0F : 0.0F;
            float c = (corner & 4) != 0 ? 1.0F : 0.0F;
            // Cube vectors are already in blocks: the renderer feeds them straight into mulPosition.
            float x = origin.x + dx.x * a + dy.x * b + dz.x * c;
            float y = origin.y + dx.y * a + dy.y * b + dz.y * c;
            float z = origin.z + dx.z * a + dy.z * b + dz.z * c;
            Vector3f world = new Vector3f(x, y, z).mulPosition(matrix);
            box.setCorner(corner, world.x, world.y, world.z);
        }
        box.radius = Math.max(QuiltBox.MIN_RADIUS, Math.min(MAX_RADIUS, box.shortestEdge() * 0.5F));
        boxes.add(box);
    }

    // --- bedrock (legacy models) -------------------------------------------

    /** Emits cubes for one part and recurses into its children with the same matrix chain. */
    private static void appendPartTree(Object part, Matrix4f matrix, List<QuiltBox> boxes, int depth) {
        if (part == null || depth > 32) {
            return;
        }
        if (Reflect.field(part, "visible") instanceof Boolean shown && !shown) {
            return;
        }
        Object cubes = Reflect.field(part, "cubes");
        if (cubes instanceof Iterable<?> list) {
            for (Object cube : list) {
                appendBedrockCube(cube, "part", matrix, boxes);
            }
        }
        List<?> kids = Reflect.list(part, "children");
        if (kids != null) {
            for (Object child : kids) {
                // Only the child's own transform here: the ancestors are already in `matrix`.
                appendPartTree(child, applyLocal(matrix, child), boxes, depth + 1);
            }
        }
    }


    /** Compact one-line field dump: enough to see which field actually holds the geometry. */
    static String dumpFields(Object target, int max) {
        if (target == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder(target.getClass().getSimpleName()).append('{');
        Class<?> type = target.getClass();
        int count = 0;
        while (type != null && type != Object.class && count < max) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || count >= max) {
                    continue;
                }
                count++;
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(target);
                } catch (Throwable error) {
                    value = "?";
                }
                String text;
                if (value instanceof java.util.Collection<?> collection) {
                    text = "size=" + collection.size();
                } else if (value instanceof Map<?, ?> map) {
                    text = "size=" + map.size();
                } else if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                    text = String.valueOf(value);
                } else {
                    text = value == null ? "null" : value.getClass().getSimpleName();
                }
                out.append(field.getName()).append(':').append(text).append(' ');
            }
            type = type.getSuperclass();
        }
        return out.append('}').toString();
    }

    private static Matrix4f applyLocal(Matrix4f parent, Object node) {
        return new Matrix4f(parent)
                .translate(orZero(Reflect.floatField(node, "x")) / PIXELS,
                        orZero(Reflect.floatField(node, "y")) / PIXELS,
                        orZero(Reflect.floatField(node, "z")) / PIXELS)
                .rotateXYZ((float) Math.toRadians(orZero(Reflect.floatField(node, "xRot"))),
                        (float) Math.toRadians(orZero(Reflect.floatField(node, "yRot"))),
                        (float) Math.toRadians(orZero(Reflect.floatField(node, "zRot"))))
                .scale(defaultOne(Reflect.floatField(node, "xScale")),
                        defaultOne(Reflect.floatField(node, "yScale")),
                        defaultOne(Reflect.floatField(node, "zScale")));
    }

    private static Map<?, ?> bedrockParts(Object model) {
        Map<?, ?> fallback = null;
        Class<?> type = model.getClass();
        while (type != null) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())
                        || !field.getName().toLowerCase(Locale.ROOT).contains("modelmap")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(model) instanceof Map<?, ?> map && !map.isEmpty()) {
                        // BedrockModel also carries modelMapWrapper, whose values are animation wrappers
                        // around BedrockPart; the plain modelMap is the one with real parts.
                        if ("modelMap".equals(field.getName())) {
                            return map;
                        }
                        if (fallback == null) {
                            fallback = map;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return fallback;
    }

    /** Legacy (bedrock-format) models: part transforms are public fields, cube sizes are in pixels. */
    private static void walkBedrockPart(String name, Object part, Matrix4f parent, List<QuiltBox> boxes, int depth) {
        if (part == null || depth > 32) {
            return;
        }
        if (Reflect.field(part, "visible") instanceof Boolean shown && !shown) {
            return;
        }
        float px = orZero(Reflect.floatField(part, "x")) / PIXELS;
        float py = orZero(Reflect.floatField(part, "y")) / PIXELS;
        float pz = orZero(Reflect.floatField(part, "z")) / PIXELS;
        float rx = (float) Math.toRadians(orZero(Reflect.floatField(part, "xRot")));
        float ry = (float) Math.toRadians(orZero(Reflect.floatField(part, "yRot")));
        float rz = (float) Math.toRadians(orZero(Reflect.floatField(part, "zRot")));
        float sx = defaultOne(Reflect.floatField(part, "xScale"));
        float sy = defaultOne(Reflect.floatField(part, "yScale"));
        float sz = defaultOne(Reflect.floatField(part, "zScale"));
        Matrix4f matrix = new Matrix4f(parent).translate(px, py, pz).rotateXYZ(rx, ry, rz).scale(sx, sy, sz);
        Object cubes = Reflect.field(part, "cubes");
        if (cubes instanceof Iterable<?> list) {
            for (Object cube : list) {
                appendBedrockCube(cube, name, matrix, boxes);
            }
        }
        List<?> kids = Reflect.list(part, "children");
        if (kids != null) {
            for (Object child : kids) {
                walkBedrockPart(name, child, matrix, boxes, depth + 1);
            }
        }
    }

    /** A bedrock cube is origin + size in pixels; BedrockCubeBox keeps them as protected floats. */
    private static void appendBedrockCube(Object cube, String name, Matrix4f matrix, List<QuiltBox> boxes) {
        float x = Reflect.floatField(cube, "x");
        float y = Reflect.floatField(cube, "y");
        float z = Reflect.floatField(cube, "z");
        float w = Reflect.floatField(cube, "width");
        float h = Reflect.floatField(cube, "height");
        float d = Reflect.floatField(cube, "depth");
        if (Float.isNaN(x) || Float.isNaN(w) || w <= 0.0F) {
            return;
        }
        QuiltBox box = new QuiltBox(name);
        box.hard = isHard(name);
        for (int corner = 0; corner < 8; corner++) {
            float a = (corner & 1) != 0 ? 1.0F : 0.0F;
            float b = (corner & 2) != 0 ? 1.0F : 0.0F;
            float c = (corner & 4) != 0 ? 1.0F : 0.0F;
            Vector3f world = new Vector3f((x + w * a) / PIXELS, (y + h * b) / PIXELS, (z + d * c) / PIXELS)
                    .mulPosition(matrix);
            box.setCorner(corner, world.x, world.y, world.z);
        }
        box.radius = Math.max(QuiltBox.MIN_RADIUS, Math.min(MAX_RADIUS, box.shortestEdge() * 0.5F));
        boxes.add(box);
    }

    // --- helpers -----------------------------------------------------------

    private static boolean isHard(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String bit : HARD_BITS) {
            if (lower.contains(bit)) {
                return true;
            }
        }
        return false;
    }

    private static Vector3f callVector(Object target, String name, int index) {
        return Reflect.call(target, name, index) instanceof Vector3f vector ? vector : null;
    }

    private static float orZero(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0.0F : value;
    }

    private static float defaultOne(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 1.0F : value;
    }

    private static float[] bounds(List<QuiltBox> boxes) {
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (QuiltBox box : boxes) {
            for (int i = 0; i < 8; i++) {
                bounds[0] = Math.min(bounds[0], box.x(i));
                bounds[1] = Math.min(bounds[1], box.y(i));
                bounds[2] = Math.min(bounds[2], box.z(i));
                bounds[3] = Math.max(bounds[3], box.x(i));
                bounds[4] = Math.max(bounds[4], box.y(i));
                bounds[5] = Math.max(bounds[5], box.z(i));
            }
        }
        return bounds;
    }

    private static long stamp(List<QuiltBox> boxes) {
        long stamp = boxes.size() * 31L;
        for (int i = 0; i < Math.min(boxes.size(), 16); i++) {
            QuiltBox box = boxes.get(i);
            stamp = stamp * 31L + (long) (box.x(0) * 100.0F) + (long) (box.y(0) * 100.0F) * 17L
                    + (long) (box.z(0) * 100.0F) * 31L;
        }
        return stamp;
    }

    /**
     * {@code GeckoMaidEntityCapabilityProvider.CAP} is a public static capability; going through it skips the
     * renderer entirely and works even before the first frame of her model has been drawn.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object animatableViaCapability(EntityMaid maid) {
        try {
            Class<?> provider = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.capability.GeckoMaidEntityCapabilityProvider");
            Object token = provider.getField("CAP").get(null);
            if (!(token instanceof net.minecraftforge.common.capabilities.Capability capability)) {
                miss("cap:token", "CAP is not a Capability");
                return null;
            }
            net.minecraftforge.common.util.LazyOptional lazy = maid.getCapability(capability);
            Object value = lazy != null && lazy.isPresent() ? lazy.orElse(null) : null;
            miss("cap:value", value == null ? "capability absent" : value.getClass().getSimpleName());
            return value;
        } catch (Throwable error) {
            miss("cap:" + error.getClass().getSimpleName(), error.toString());
            return null;
        }
    }

    /** Field name/type/null-ness dump, used once when a reflection hop lands on nothing. */
    private static String dumpFields(Object target) {
        StringBuilder builder = new StringBuilder(target.getClass().getSimpleName()).append('{');
        Class<?> type = target.getClass();
        int shown = 0;
        while (type != null && shown < 20) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (shown++ >= 20) {
                    break;
                }
                builder.append(field.getName()).append(':').append(field.getType().getSimpleName());
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    builder.append(value == null ? "=null" : "=" + value.getClass().getSimpleName());
                } catch (Throwable ignored) {
                    builder.append("=?");
                }
                builder.append(' ');
            }
            type = type.getSuperclass();
        }
        return builder.append('}').toString();
    }

    private static void miss(String key, String detail) {
        LAST_MISS = key + ":" + detail;
        if (MISSES.add(key)) {
            LOGGER.info("[sleep_well] collider miss {}: {}", key, detail);
        }
    }

    /** Last miss, trimmed for the chat line. */
    public static String lastMiss() {
        String text = LAST_MISS;
        return text.length() > 110 ? text.substring(0, 110) + "…" : text;
    }

    /** Size and membership of TLM's gecko model cache - tells "no model" apart from "wrong location". */
    private static String geoCache(Object location) {
        try {
            Class<?> cacheClass = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache");
            Object instance = cacheClass.getMethod("getInstance").invoke(null);
            Object models = cacheClass.getMethod("getGeoModels").invoke(instance);
            if (models instanceof Map<?, ?> map) {
                return "size=" + map.size() + " has=" + (location != null && map.containsKey(location))
                        + " key=" + location;
            }
        } catch (Throwable error) {
            return "cache error " + error;
        }
        return "no cache";
    }
}