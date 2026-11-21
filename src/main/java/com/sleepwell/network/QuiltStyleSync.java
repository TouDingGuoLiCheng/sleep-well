package com.sleepwell.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import com.sleepwell.maid.MaidCapabilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Re-sends the quilt a maid wears whenever a player starts tracking her: joining the world, or walking back
 * into range, both start a fresh client-side view that has no idea what the server has stored.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class QuiltStyleSync {
    private QuiltStyleSync() {
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof EntityMaid maid) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String id = MaidCapabilities.of(maid).quiltStyle();
        String border = MaidCapabilities.of(maid).quiltBorder();
        SleepWellNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new SyncQuiltStylePacket(maid.getId(), id, border, MaidCapabilities.of(maid).quiltParams()));
    }
}
