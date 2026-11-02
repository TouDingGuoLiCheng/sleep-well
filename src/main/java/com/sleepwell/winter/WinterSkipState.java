package com.sleepwell.winter;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Maids that are currently parked on the idle task because it is winter. */
public final class WinterSkipState {
    private static final Set<UUID> SKIPPING = ConcurrentHashMap.newKeySet();

    private WinterSkipState() {
    }

    public static boolean isSkipping(UUID id) {
        return SKIPPING.contains(id);
    }

    public static boolean isSkipping(EntityMaid maid) {
        return maid != null && SKIPPING.contains(maid.getUUID());
    }

    public static void mark(UUID id) {
        SKIPPING.add(id);
    }

    public static void clear(UUID id) {
        SKIPPING.remove(id);
    }
}
