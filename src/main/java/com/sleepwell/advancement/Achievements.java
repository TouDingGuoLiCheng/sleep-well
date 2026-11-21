package com.sleepwell.advancement;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;

/** The kind strings the advancement JSONs listen for, and the one call that fires them. */
public final class Achievements {
    public static final String ROOT = "root_sleep";
    public static final String SLEEP_COUNT = "sleep_count";
    public static final String CHANGE_STYLE = "style_changed";
    public static final String ALL_FACES = "all_faces";
    public static final String DROWSY = "drowsy";
    public static final String VIGOR = "vigor";
    public static final String IRON_SLEEP = "iron_sleep";
    public static final String WINTER_SLEEP = "winter_sleep";
    public static final String FLUFFY = "fluffy";
    public static final String LIE_IN = "lie_in";
    public static final String NAP = "nap";
    public static final String MAID_SQUAD = "maid_squad";
    public static final String SOFT_HAND = "soft_hand";

    private Achievements() {
    }

    public static void award(ServerPlayer player, String kind, int value) {
        if (player != null) {
            SleepWellCriteria.TRIGGER.trigger(player, kind, value);
        }
    }

    /** Awards to her owner, when that owner is online. */
    public static void award(EntityMaid maid, String kind, int value) {
        if (maid != null && maid.getOwner() instanceof ServerPlayer player) {
            award(player, kind, value);
        }
    }
}
