package com.sleepwell.client;

import com.sleepwell.SleepWellMod;
import com.sleepwell.gui.SleepWellContainer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only screen registration. */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SleepWellClientSetup {
    private SleepWellClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(SleepWellContainer.TYPE.get(), SleepWellSettingsScreen::new));
        // Client command for the quilt diagnostics: "/swquilt wireframe on" needs no restart.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(QuiltDebugCommand::onRegisterCommands);
    }
}
