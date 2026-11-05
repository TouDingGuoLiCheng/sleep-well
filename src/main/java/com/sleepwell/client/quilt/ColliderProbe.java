package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * M0 probe: walks from a maid to her bones and cubes, logging every hop.
 * <p>
 * The point of this class is not to render anything - it is to answer one question with certainty:
 * <em>where do the post-animation bone transforms and the cube corners live, in both of TLM's model
 * paths?</em> Every hop logs either the field/method it used or the reason it could not, so one game
 * session with {@code -Dsleepwell.quiltProbe=true} tells us whether the plan's M1 can be written the
 * cheap way or needs the gecko mixin fallback.
 * <p>
 * Temporary: M1 replaces this with the real {@code collider/} package and this class is deleted.
 */
public final class ColliderProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Set with -Dsleepwell.quiltProbe=true. Off by default: this is a one-session diagnostic. */
    private static final boolean ENABLED = Boolean.getBoolean("sleepwell.quiltProbe");
    private static final Set<String> DONE = new HashSet<>();

    private ColliderProbe() {
    }

    /** Called while her model still carries the sleeping pose, once per maid/renderer pair. */
    public static void maybeDump(EntityMaid maid) {
        if (!ENABLED) {
            return;
        }
        try {
            Object renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(maid);
            if (renderer == null) {
                return;
            }
            String key = renderer.getClass().getSimpleName() + "/" + maid.getType().toShortString();
            if (!DONE.add(key)) {
                return;
            }
            dump(maid, key, renderer);
        } catch (Throwable error) {
            LOGGER.warn("[sleep_well] quilt probe failed: {}", error.toString());
        }
    }

    private static void dump(EntityMaid maid, String key, Object renderer) {
        LOGGER.info("[sleep_well] probe start model={} maidsSleeping={} sleeping={}", key,
                maid.getClass().getSimpleName(), maid.isSleeping());
        Object model = null;
        if (renderer instanceof LivingEntityRenderer<?, ?> living) {
            model = living.getModel();
            LOGGER.info("[sleep_well] probe renderer={} model={}", renderer.getClass().getName(),
                    model.getClass().getName());
        }
        // --- gecko path -----------------------------------------------------
        Object geckoRenderer = fieldValue(renderer, "geckoEntityMaidRenderer");
        LOGGER.info("[sleep_well] probe geckoRenderer={}", nameOf(geckoRenderer));
        Object animated = null;
        if (geckoRenderer != null) {
            animated = invoke(geckoRenderer, "getAnimatableEntity", maid);
            LOGGER.info("[sleep_well] probe animatableEntity={}", nameOf(animated));
        }
        Object animatedModel = animated == null ? null : invoke(animated, "getGeoModel");
        LOGGER.info("[sleep_well] probe animatedModel={}", nameOf(animatedModel));
        if (animatedModel != null) {
            int[] counts = new int[3];
            walkAnimatedBones(animatedModel, 0, counts, new IdentityHashMap<>());
            LOGGER.info("[sleep_well] probe gecko bones={} cubes={} hidden={}", counts[0], counts[1], counts[2]);
        }
        // --- bedrock path ---------------------------------------------------
        Map<?, ?> parts = model == null ? null : bedrockParts(model);
        if (parts != null && !parts.isEmpty()) {
            int[] counts = new int[3];
            String sample = walkBedrockParts(parts, counts);
            LOGGER.info("[sleep_well] probe bedrock parts={} cubes={} hidden={} childrenSample={}",
                    counts[0], counts[1], counts[2], sample);
        }
        LOGGER.info("[sleep_well] probe done model={} -> next hop is M1 collider capture", key);
    }

    private static void walkAnimatedBones(Object model, int depth, int[] counts, Map<Object, Boolean> seen) {
        List<?> roots = listValue(model, "topLevelBones");
        if (roots == null) {
            LOGGER.info("[sleep_well] probe no topLevelBones on {}", model.getClass().getName());
            return;
        }
        if (depth == 0) {
            LOGGER.info("[sleep_well] probe gecko roots={} bones={}", roots.size(), bonesOf(model).size());
        }
        for (Object bone : roots) {
            walkAnimatedBone(bone, depth, counts, seen);
        }
    }

    private static void walkAnimatedBone(Object bone, int depth, int[] counts, Map<Object, Boolean> seen) {
        if (bone == null || seen.put(bone, Boolean.TRUE) != null) {
            return;
        }
        counts[0]++;
        boolean hidden = boolValue(bone, "isHidden") || boolValue(bone, "areCubesHidden");
        if (hidden) {
            counts[2]++;
        }
        Object geoBone = invoke(bone, "geoBone");
        Object mesh = geoBone == null ? null : invoke(geoBone, "cubes");
        int cubes = mesh == null ? 0 : intValue(mesh, "cubeCount");
        counts[1] += cubes;
        if (counts[0] <= 6 || cubes > 0 && counts[0] % 20 == 0) {
            LOGGER.info("[sleep_well] probe bone d{} name={} pos=({},{},{}) rot=({},{},{}) scale=({},{},{}) "
                            + "hidden={} cubes={} pivot={}", depth, invoke(bone, "getName"),
                    floatValue(bone, "getPositionX"), floatValue(bone, "getPositionY"), floatValue(bone, "getPositionZ"),
                    floatValue(bone, "getRotationX"), floatValue(bone, "getRotationY"), floatValue(bone, "getRotationZ"),
                    floatValue(bone, "getScaleX"), floatValue(bone, "getScaleY"), floatValue(bone, "getScaleZ"),
                    hidden, cubes, pivotOf(bone));
        }
        List<?> children = listValue(bone, "children");
        if (children != null) {
            for (Object child : children) {
                walkAnimatedBone(child, depth + 1, counts, seen);
            }
        }
    }

    private static String walkBedrockParts(Map<?, ?> parts, int[] counts) {
        StringBuilder sample = new StringBuilder();
        Set<Object> children = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object part : parts.values()) {
            List<?> kids = listValue(part, "children");
            if (kids != null) {
                children.addAll(kids);
            }
        }
        for (Map.Entry<?, ?> entry : parts.entrySet()) {
            if (!children.contains(entry.getValue())) {
                walkBedrockPart(entry.getKey(), entry.getValue(), 0, counts, sample);
            }
        }
        return sample.toString();
    }

    private static void walkBedrockPart(Object partName, Object part, int depth, int[] counts, StringBuilder sample) {
        if (part == null) {
            return;
        }
        counts[0]++;
        boolean visible = !boolValue(part, "isEmpty");
        Object cubes = fieldValue(part, "cubes");
        int cubeCount = cubes instanceof List<?> list ? list.size() : 0;
        counts[1] += cubeCount;
        if (!visible) {
            counts[2]++;
        }
        if (depth <= 2 && sample.length() < 300) {
            sample.append(partName).append(':').append(cubeCount).append(' ');
        }
        List<?> kids = listValue(part, "children");
        if (kids != null) {
            for (Object child : kids) {
                walkBedrockPart("?", child, depth + 1, counts, sample);
            }
        }
    }

    private static Object pivotOf(Object bone) {
        Object geoBone = invoke(bone, "geoBone");
        Object pivot = geoBone == null ? null : invoke(geoBone, "pivot");
        return pivot == null ? "null" : String.valueOf(pivot);
    }

    private static Map<?, ?> bonesOf(Object model) {
        Map<?, ?> bones = mapValue(model, "bones");
        return bones == null ? Map.of() : bones;
    }

    private static Map<?, ?> bedrockParts(Object model) {
        Class<?> type = model.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())
                        || !field.getName().toLowerCase().contains("modelmap")) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    if (field.get(model) instanceof Map<?, ?> map && !map.isEmpty()) {
                        return map;
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    // --- tiny reflection helpers that never throw -------------------------

    private static Object fieldValue(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<?> listValue(Object target, String name) {
        return fieldValue(target, name) instanceof List<?> list ? list : null;
    }

    private static Map<?, ?> mapValue(Object target, String name) {
        return fieldValue(target, name) instanceof Map<?, ?> map ? map : null;
    }

    private static boolean boolValue(Object target, String name) {
        Object value = fieldValue(target, name);
        return value instanceof Boolean flag && flag;
    }

    private static int intValue(Object target, String name) {
        Object value = fieldValue(target, name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        Method[] methods = target.getClass().getMethods();
        for (Method method : methods) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static float floatValue(Object target, String getter) {
        Object value = invoke(target, getter);
        return value instanceof Number number ? number.floatValue() : Float.NaN;
    }

    private static String nameOf(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }
}
