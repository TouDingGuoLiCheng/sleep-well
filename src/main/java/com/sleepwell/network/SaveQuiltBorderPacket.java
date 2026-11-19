package com.sleepwell.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.client.quilt.QuiltBorder;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.maid.MaidCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player picked a border for one maid's quilt, or cleared it.
 * <p>
 * Like the quilt itself, the id is validated server-side against the real registry and the lock switch, so a
 * modified client cannot sew on a border that does not exist.
 */
public final class SaveQuiltBorderPacket {
    private final int entityId;
    private final String borderId;

    public SaveQuiltBorderPacket(int entityId, String borderId) {
        this.entityId = entityId;
        this.borderId = borderId == null ? "" : borderId;
    }

    public static void encode(SaveQuiltBorderPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeUtf(packet.borderId, 32);
    }

    public static SaveQuiltBorderPacket decode(FriendlyByteBuf buf) {
        return new SaveQuiltBorderPacket(buf.readVarInt(), buf.readUtf(32));
    }

    public static void handle(SaveQuiltBorderPacket packet, Supplier<NetworkEvent.Context> supplier) {
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
            QuiltBorder border = QuiltBorder.of(packet.borderId);
            boolean locked = SleepWellConfig.QUILT_LOCK_ALL.get() && !border.isNone();
            if (locked) {
                return;
            }
            MaidCapabilities.of(maid).setQuiltBorder(border.id);
            SleepWellNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> maid),
                    new SyncQuiltStylePacket(maid.getId(), maidQuilt(maid), border.id));
        });
    }

    /** The quilt she wears right now, so the sync carries both halves of the choice. */
    private static String maidQuilt(EntityMaid maid) {
        return MaidCapabilities.of(maid).quiltStyle();
    }
}
