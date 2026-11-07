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
    public static final int PAGE_SETTINGS = 0;
    public static final int PAGE_QUILT = 1;

    private final int entityId;
    private final int page;

    public OpenSettingsPacket(int entityId) {
        this(entityId, PAGE_SETTINGS);
    }

    public OpenSettingsPacket(int entityId, int page) {
        this.entityId = entityId;
        this.page = page;
    }

    public static void encode(OpenSettingsPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.page);
    }

    public static OpenSettingsPacket decode(FriendlyByteBuf buf) {
        return new OpenSettingsPacket(buf.readVarInt(), buf.readByte());
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
        boolean lieIn = MaidCapabilities.of(maid).lieIn();
        boolean sereneSeasons = SeasonBridge.sereneSeasonsLoaded();
        String quiltStyle = MaidCapabilities.of(maid).quiltStyle();
        boolean quiltLocked = SleepWellConfig.QUILT_LOCK_ALL.get();
        int page = packet.page == PAGE_QUILT ? PAGE_QUILT : PAGE_SETTINGS;
        context.enqueueWork(() -> NetworkHooks.openScreen(player,
                SleepWellContainer.create(packet.entityId, sleepFallback, campfireFlee, winterSkip, lieIn,
                        sereneSeasons, page, quiltStyle, quiltLocked),
                buf -> SleepWellContainer.writeData(buf, packet.entityId, sleepFallback, campfireFlee, winterSkip,
                        lieIn, sereneSeasons, page, quiltStyle, quiltLocked)));
    }
}
