package com.sleepwell;

import com.sleepwell.config.SleepWellConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Sleep Well — fallback teleport so maids with a kappa-compass sleep point actually get there.
 */
@Mod(SleepWellMod.MOD_ID)
public final class SleepWellMod {
    public static final String MOD_ID = "sleep_well";

    public SleepWellMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SleepWellConfig.SPEC);
    }
}
