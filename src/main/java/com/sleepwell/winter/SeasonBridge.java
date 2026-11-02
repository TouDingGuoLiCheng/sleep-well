package com.sleepwell.winter;

import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/** Soft dependency on Serene Seasons: no Serene Seasons means "never winter". */
public final class SeasonBridge {
    public static final String SERENE_SEASONS_MOD_ID = "sereneseasons";

    private static Boolean loaded;

    private SeasonBridge() {
    }

    public static boolean sereneSeasonsLoaded() {
        if (loaded == null) {
            loaded = ModList.get().isLoaded(SERENE_SEASONS_MOD_ID);
        }
        return loaded;
    }

    public static boolean isWinter(Level level) {
        if (level == null || !sereneSeasonsLoaded()) {
            return false;
        }
        try {
            return SereneSeasonsBridge.isWinter(level);
        } catch (Throwable ignored) {
            // A broken/removed season provider must never take the maid tick down with it.
            return false;
        }
    }
}
