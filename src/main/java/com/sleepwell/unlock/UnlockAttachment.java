package com.sleepwell.unlock;

import com.sleepwell.SleepWellMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Attaches the unlock capability to every player and carries it through death and respawn. */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class UnlockAttachment {
    private static final ResourceLocation KEY = new ResourceLocation(SleepWellMod.MOD_ID, "player_unlocks");

    private UnlockAttachment() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new UnlockProvider(new PlayerUnlocks()));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        event.getOriginal().reviveCaps();
        CompoundTag saved = event.getOriginal().getCapability(UnlockCapabilities.PLAYER_UNLOCKS)
                .map(PlayerUnlocks::serializeNBT).orElse(null);
        if (saved != null) {
            event.getEntity().getCapability(UnlockCapabilities.PLAYER_UNLOCKS)
                    .ifPresent(unlocks -> unlocks.deserializeNBT(saved));
        }
        event.getOriginal().invalidateCaps();
    }
}
