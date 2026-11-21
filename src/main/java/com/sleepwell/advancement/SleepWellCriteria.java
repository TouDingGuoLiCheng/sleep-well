package com.sleepwell.advancement;

import com.sleepwell.SleepWellMod;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Registers the one custom criterion the achievement page uses. */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SleepWellCriteria {
    public static final SleepWellTrigger TRIGGER = new SleepWellTrigger();

    private SleepWellCriteria() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CriteriaTriggers.register(TRIGGER));
    }
}
