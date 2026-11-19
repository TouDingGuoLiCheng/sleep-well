package com.sleepwell.maid;

import com.sleepwell.SleepWellMod;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the capability itself on the mod bus. */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MaidCapabilityRegistration {
    private MaidCapabilityRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(MaidSettings.class);
    }
}
