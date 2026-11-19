package com.sleepwell.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Attaches the settings capability to every maid. */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class MaidCapabilityAttachment {
    private static final ResourceLocation KEY = new ResourceLocation(SleepWellMod.MOD_ID, "maid_settings");

    private MaidCapabilityAttachment() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityMaid) {
            event.addCapability(KEY,
                    new MaidSettingsProvider(new MaidSettings(SleepWellConfig.WINTER_SKIP_DEFAULT_ENABLED.get())));
        }
    }
}
