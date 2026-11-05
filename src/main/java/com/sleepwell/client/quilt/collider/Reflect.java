package com.sleepwell.client.quilt.collider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Reflection helpers for reading TLM's model objects.
 * <p>
 * Everything here swallows failures on purpose: this code runs on the render thread, and a renamed field
 * in a TLM update must degrade to "no colliders this frame", never to a crash. Callers log the miss once.
 */
public final class Reflect {
    private Reflect() {
    }

    public static Object field(Object target, String name) {
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

    public static Object anyField(Object target, String... names) {
        for (String name : names) {
            Object value = field(target, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    public static List<?> list(Object target, String name) {
        return field(target, name) instanceof List<?> list ? list : null;
    }

    public static Map<?, ?> map(Object target, String name) {
        return field(target, name) instanceof Map<?, ?> map ? map : null;
    }

    public static boolean flag(Object target, String name) {
        return field(target, name) instanceof Boolean value && value;
    }

    public static int integer(Object target, String name) {
        return field(target, name) instanceof Number value ? value.intValue() : 0;
    }

    public static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    public static float floatField(Object target, String name) {
        return field(target, name) instanceof Number number ? number.floatValue() : Float.NaN;
    }

    public static float number(Object target, String getter) {
        Object value = call(target, getter);
        return value instanceof Number number ? number.floatValue() : Float.NaN;
    }
}
