package com.sleepwell.winter;

import net.minecraft.world.level.Level;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

/**
 * The only class that touches Serene Seasons classes.
 * <p>
 * Loaded lazily and only when the mod is present, so the mod keeps working (and loading) without it.
 */
final class SereneSeasonsBridge {
    private SereneSeasonsBridge() {
    }

    static boolean isWinter(Level level) {
        return SeasonHelper.getSeasonState(level).getSeason() == Season.WINTER;
    }
}
