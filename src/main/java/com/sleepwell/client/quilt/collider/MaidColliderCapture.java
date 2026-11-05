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

    /** Bone name fragments that get real cloth collision. Everything else only repels. */
    private static final String[] HARD_BITS = {
            "body", "chest", "head", "arm", "hand", "leg", "foot", "breast", "waist", "torso", "hip", "shoulder"
    };

    private static final Map<UUID, ColliderSet> SETS = new HashMap<>();
    private static final Set<String> MISSES = new HashSet<>();
    private static volatile int hookRuns;
    private static volatile int captures;
    private static final java.util.concurrent.atomic.AtomicInteger DIRECT_LOGS = new java.util.concurrent.atomic.AtomicInteger();
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
            if (animatable == null) {
                animatable = animatableViaCapability(maid);
            }
            Object model = animatable == null ? null : Reflect.call(animatable, "getGeoModel");
            if (model == null && animatable != null) {
                model = Reflect.call(animatable, "getCurrentModel");
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
            SETS.put(maid.getUUID(), new ColliderSet(boxes, bounds(boxes), stamp(boxes), maid.tickCount, false));
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

    /** Called from {@code RenderLivingEvent.Post}, i.e. while the sleeping pose is still on the model. */
    public static void capture(EntityMaid maid, PoseStack poseStack, float partialTick) {
        hookRuns++;
        try {
            doCapture(maid, poseStack, partialTick);
        } catch (Throwable error) {
            miss("capture:" + error.getClass().getSimpleName(), error.toString());
        }
    }

    /**
     * Mixin-free fallback: read her model at {@code RenderLevelStageEvent} time, where the bones still hold
     * the last animated (sleeping) pose, and rebuild the entity transform ourselves.
     * <p>
     * Vanilla's sleeping transform is {@code T(0,-eye,0) · RY(-bedDir) · RZ(-90)} inside the dispatcher's
     * {@code RY(180 - bodyYaw)}, so it can be reconstructed without hooking anything. Throttled because a
     * reflection walk per frame per maid is not free.
     */
    public static ColliderSet captureDirect(EntityMaid maid, net.minecraft.world.phys.Vec3 camera, float partialTick) {
        ColliderSet cached = SETS.get(maid.getUUID());
        if (cached != null && maid.tickCount - cached.tick < 5) {
            return cached;
        }
        try {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(maid);
            if (renderer == null) {
                miss("direct:renderer", "dispatcher returned null");
                return null;
            }
            Object gecko = Reflect.field(renderer, "geckoEntityMaidRenderer");
            if (gecko == null) {
                miss("direct:geckoField", dumpFields(renderer));
                return null;
            }
            Object animatable = Reflect.call(gecko, "getAnimatableEntity", maid);
            if (animatable == null) {
                // The renderer route can come back empty (capability not yet resolved, or a signature change);
                // the capability itself is public and gives the same GeckoMaidEntity.
                animatable = animatableViaCapability(maid);
            }
            if (animatable == null) {
                miss("direct:animatable", gecko.getClass().getName() + " -> " + dumpMethods(gecko, "getAnimatableEntity"));
                return null;
            }
            Object model = Reflect.call(animatable, "getGeoModel");
            if (model == null) {
                miss("direct:geoModel", animatable.getClass().getName() + " -> " + dumpMethods(animatable, "getGeoModel"));
                return null;
            }
            List<?> roots = Reflect.list(model, "topLevelBones");
            if (roots == null || roots.isEmpty()) {
                miss("direct:roots", model.getClass().getName());
                return null;
            }
            Matrix4f entity = entityMatrix(maid, camera, partialTick);
            List<QuiltBox> boxes = new ArrayList<>();
            for (Object root : roots) {
                walkBone(root, new Matrix4f(entity), boxes, 0);
            }
            if (boxes.isEmpty()) {
                miss("direct:empty", "bones walked but no cubes");
                return null;
            }
            ColliderSet set = new ColliderSet(boxes, bounds(boxes), stamp(boxes), maid.tickCount, true);
            SETS.put(maid.getUUID(), set);
            if (DIRECT_LOGS.getAndIncrement() == 0) {
                float[] b = set.bounds;
                LOGGER.info("[sleep_well] direct capture boxes={} center=({},{},{}) maidY={} renderer={}",
                        boxes.size(), (b[0] + b[3]) / 2.0F, (b[1] + b[4]) / 2.0F, (b[2] + b[5]) / 2.0F,
                        maid.getY(), renderer.getClass().getSimpleName());
            }
            return set;
        } catch (Throwable error) {
            miss("direct:" + error.getClass().getSimpleName(), error.toString());
            return null;
        }
    }

    /** Rebuilds the transform the renderer would have used: dispatcher translate/yaw plus sleeping rotation. */
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

    private static void doCapture(EntityMaid maid, PoseStack poseStack, float partialTick) {
        Matrix4f entity = new Matrix4f(poseStack.last().pose());
        selfCheck(maid, entity, partialTick);

        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(maid);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> living)) {
            miss("renderer", renderer == null ? "null" : renderer.getClass().getName());
            return;
        }
        List<QuiltBox> boxes = new ArrayList<>();
        boolean gecko = captureGecko(maid, renderer, entity, boxes);
        if (!gecko) {
            captureBedrock(living, entity, boxes);
        }
        if (boxes.isEmpty()) {
            miss("empty", "no cubes captured for " + living.getModel().getClass().getSimpleName());
            return;
        }
        captures++;
        SETS.put(maid.getUUID(), new ColliderSet(boxes, bounds(boxes), stamp(boxes), maid.tickCount, false));
    }

    // --- gecko -------------------------------------------------------------

    private static boolean captureGecko(EntityMaid maid, Object renderer, Matrix4f entity, List<QuiltBox> boxes) {
        Object geckoRenderer = Reflect.field(renderer, "geckoEntityMaidRenderer");
        if (geckoRenderer == null) {
            return false;
        }
        Object animatable = Reflect.call(geckoRenderer, "getAnimatableEntity", maid);
        Object model = animatable == null ? null : Reflect.call(animatable, "getGeoModel");
        if (model == null) {
            miss("geckoModel", "getGeoModel returned null");
            return false;
        }
        List<?> roots = Reflect.list(model, "topLevelBones");
        if (roots == null || roots.isEmpty()) {
            miss("geckoRoots", model.getClass().getName());
            return false;
        }
        for (Object root : roots) {
            walkBone(root, new Matrix4f(entity), boxes, 0);
        }
        return true;
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

        Matrix4f boneMatrix = new Matrix4f(parent)
                .translate(posX, posY, posZ)
                .translate(pivotX, pivotY, pivotZ)
                .rotateXYZ(rotX, rotY, rotZ)
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
            float x = (origin.x + dx.x * a + dy.x * b + dz.x * c) / PIXELS;
            float y = (origin.y + dx.y * a + dy.y * b + dz.y * c) / PIXELS;
            float z = (origin.z + dx.z * a + dy.z * b + dz.z * c) / PIXELS;
            Vector3f world = new Vector3f(x, y, z).mulPosition(matrix);
            box.setCorner(corner, world.x, world.y, world.z);
        }
        box.radius = Math.max(QuiltBox.MIN_RADIUS, Math.min(MAX_RADIUS, box.shortestEdge() * 0.5F));
        boxes.add(box);
    }

    // --- bedrock (legacy models) -------------------------------------------

    private static void captureBedrock(LivingEntityRenderer<?, ?> living, Matrix4f entity, List<QuiltBox> boxes) {
        Map<?, ?> parts = null;
        Class<?> type = living.getModel().getClass();
        while (type != null && parts == null) {
            for (java.lang.reflect.Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())
                        || !field.getName().toLowerCase(Locale.ROOT).contains("modelmap")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(living.getModel()) instanceof Map<?, ?> map && !map.isEmpty()) {
                        parts = map;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        if (parts == null) {
            miss("bedrockParts", living.getModel().getClass().getName());
            return;
        }
        Set<Object> children = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Object part : parts.values()) {
            List<?> kids = Reflect.list(part, "children");
            if (kids != null) {
                children.addAll(kids);
            }
        }
        for (Map.Entry<?, ?> entry : parts.entrySet()) {
            if (!children.contains(entry.getValue())) {
                walkBedrockPart(String.valueOf(entry.getKey()), entry.getValue(), new Matrix4f(entity), boxes, 0);
            }
        }
    }

    private static void walkBedrockPart(String name, Object part, Matrix4f parent, List<QuiltBox> boxes, int depth) {
        if (part == null || depth > 32) {
            return;
        }
        // BedrockPart keeps these as public fields, not getters, and `visible` false means "not drawn".
        if (Reflect.field(part, "visible") instanceof Boolean shown && !shown) {
            return;
        }
        Matrix4f matrix = new Matrix4f(parent).translate(
                orZero(Reflect.floatField(part, "x")) / PIXELS,
                orZero(Reflect.floatField(part, "y")) / PIXELS,
                orZero(Reflect.floatField(part, "z")) / PIXELS);
        // One coarse box per part: the legacy path is a fallback, not the main shape.
        QuiltBox box = new QuiltBox(name);
        box.hard = isHard(name);
        box.radius = 0.05F;
        for (int corner = 0; corner < 8; corner++) {
            float a = (corner & 1) != 0 ? 1.0F : 0.0F;
            float b = (corner & 2) != 0 ? 1.0F : 0.0F;
            float c = (corner & 4) != 0 ? 1.0F : 0.0F;
            Vector3f world = new Vector3f((a - 0.5F) * 0.12F, b * 0.12F, (c - 0.5F) * 0.12F).mulPosition(matrix);
            box.setCorner(corner, world.x, world.y, world.z);
        }
        boxes.add(box);
        List<?> kids = Reflect.list(part, "children");
        if (kids != null) {
            for (Object child : kids) {
                walkBedrockPart(name + "/?", child, matrix, boxes, depth + 1);
            }
        }
    }

    // --- helpers -----------------------------------------------------------

    /** Verifies the PoseStack still carries the entity transform; logs once when it does not. */
    private static void selfCheck(EntityMaid maid, Matrix4f entity, float partialTick) {
        Vector3f origin = new Vector3f(0.0F, 0.0F, 0.0F).mulPosition(entity);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 expected = maid.getPosition(partialTick).subtract(camera);
        double distance = Math.sqrt(
                (origin.x - expected.x) * (origin.x - expected.x)
                        + (origin.y - expected.y) * (origin.y - expected.y)
                        + (origin.z - expected.z) * (origin.z - expected.z));
        if (distance > 2.0D) {
            miss("matrix", String.format(Locale.ROOT, "pose stack origin off by %.2f blocks "
                    + "(got %.2f/%.2f/%.2f expected %.2f/%.2f/%.2f)",
                    distance, origin.x, origin.y, origin.z, expected.x, expected.y, expected.z));
        }
    }

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

    /** Lists the overloads for a method name plus the exception each one threw, if any. */
    private static String dumpMethods(Object target, String name) {
        StringBuilder builder = new StringBuilder();
        for (java.lang.reflect.Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            builder.append(java.util.Arrays.toString(method.getParameterTypes())).append("; ");
        }
        return builder.length() == 0 ? "no method " + name : builder.toString();
    }

    private static void miss(String key, String detail) {
        if (MISSES.add(key)) {
            LOGGER.info("[sleep_well] collider miss {}: {}", key, detail);
        }
    }
}