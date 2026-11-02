package com.sleepwell;

import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.gui.SleepWellContainer;
import com.sleepwell.network.SleepWellNetwork;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Sleep Well — fallback teleport so maids with a kappa-compass sleep point actually get there,
 * campfire flee, and the optional winter work skip, all behind one maid GUI page.
 */
@Mod(SleepWellMod.MOD_ID)
public final class SleepWellMod {
    public static final String MOD_ID = "sleep_well";

    public SleepWellMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SleepWellConfig.SPEC);
        SleepWellContainer.MENUS.register(FMLJavaModLoadingContext.get().getModEventBus());
        SleepWellNetwork.init();
    }
}
