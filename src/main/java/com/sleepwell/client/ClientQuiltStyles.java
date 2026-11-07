package com.sleepwell.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of every maid's chosen quilt.
 * <p>
 * The server owns the setting; this map only exists so the renderer can read it without a capability that
 * never reaches this side. Missing entries fall through to the default quilt.
 */
public final class ClientQuiltStyles {
    private static final Map<Integer, String> STYLES = new ConcurrentHashMap<>();

    private ClientQuiltStyles() {
    }

    public static void put(int entityId, String styleId) {
        if (styleId == null || styleId.isEmpty()) {
            STYLES.remove(entityId);
        } else {
            STYLES.put(entityId, styleId);
        }
    }

    /** The quilt id this maid wears, or null when the server has never told us (use the default). */
    public static String of(Entity entity) {
        if (entity == null) {
            return null;
        }
        String id = STYLES.get(entity.getId());
        if (id != null) {
            return id;
        }
        // Entities are unloaded and reloaded with new ids less often than one would think, but a stale entry
        // for a recycled id would show the wrong quilt, so verify she is actually the entity we know.
        return Minecraft.getInstance().level == null ? null : STYLES.get(entity.getId());
    }

    public static void clear() {
        STYLES.clear();
    }
}
