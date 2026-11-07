package com.sleepwell.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.client.quilt.QuiltStyle;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.maid.MaidCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player picked a quilt for one maid.
 * <p>
 * The id is validated server-side against the real registry and the lock switch, so a modified client cannot
 * hand her a quilt that does not exist or one that is meant to be locked.
 */
public final class SaveQuiltStylePacket {
    private final int entityId;
    private final String styleId;

    public SaveQuiltStylePacket(int entityId, String styleId) {
        this.entityId = entityId;
        this.styleId = styleId == null ? "" : styleId;
    }

    public static void encode(SaveQuiltStylePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeUtf(packet.styleId, 64);
    }

    public static SaveQuiltStylePacket decode(FriendlyByteBuf buf) {
        return new SaveQuiltStylePacket(buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(SaveQuiltStylePacket packet, Supplier<NetworkEvent.Context> supplier) {
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
        context.enqueueWork(() -> {
            QuiltStyle style = QuiltStyle.of(packet.styleId);
            boolean locked = SleepWellConfig.QUILT_LOCK_ALL.get() && !style.isDefault();
            if (locked) {
                return;
            }
            MaidCapabilities.of(maid).setQuiltStyle(style.id);
            // The capability does not sync itself, so push the new value to everyone who can see her.
            SleepWellNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> maid),
                    new SyncQuiltStylePacket(maid.getId(), style.id));
        });
    }
}
