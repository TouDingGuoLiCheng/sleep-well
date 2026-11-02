package com.sleepwell.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.gui.SleepWellContainer;
import com.sleepwell.maid.MaidCapabilities;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.winter.SeasonBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/** Client asks the server to open the Sleep Well page for a maid. */
public final class OpenSettingsPacket {
    private final int entityId;

    public OpenSettingsPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenSettingsPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
    }

    public static OpenSettingsPacket decode(FriendlyByteBuf buf) {
        return new OpenSettingsPacket(buf.readVarInt());
    }

    public static void handle(OpenSettingsPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        if (!(player.level().getEntity(packet.entityId) instanceof EntityMaid maid)) {
            return;
        }
        if (!maid.isOwnedBy(player) && !player.hasPermissions(2)) {
            return;
        }
        boolean sleepFallback = SleepWellConfig.SLEEP_FALLBACK_ENABLED.get();
        boolean campfireFlee = SleepWellConfig.CAMPFIRE_FLEE_ENABLED.get();
        boolean winterSkip = MaidCapabilities.of(maid).winterSkip();
        boolean sereneSeasons = SeasonBridge.sereneSeasonsLoaded();
        context.enqueueWork(() -> NetworkHooks.openScreen(player,
                SleepWellContainer.create(packet.entityId, sleepFallback, campfireFlee, winterSkip, sereneSeasons),
                buf -> SleepWellContainer.writeData(buf, packet.entityId,
                        sleepFallback, campfireFlee, winterSkip, sereneSeasons)));
    }
}
