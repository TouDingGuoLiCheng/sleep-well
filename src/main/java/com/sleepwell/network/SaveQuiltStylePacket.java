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
    private final com.sleepwell.client.quilt.QuiltParams params;

    public SaveQuiltStylePacket(int entityId, String styleId) {
        this(entityId, styleId, com.sleepwell.client.quilt.QuiltParams.DEFAULT);
    }

    public SaveQuiltStylePacket(int entityId, String styleId, com.sleepwell.client.quilt.QuiltParams params) {
        this.entityId = entityId;
        this.styleId = styleId == null ? "" : styleId;
        this.params = params == null ? com.sleepwell.client.quilt.QuiltParams.DEFAULT : params;
    }

    public static void encode(SaveQuiltStylePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeUtf(packet.styleId, 64);
        buf.writeNbt(packet.params.save());
    }

    public static SaveQuiltStylePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String styleId = buf.readUtf(64);
        return new SaveQuiltStylePacket(entityId, styleId, com.sleepwell.client.quilt.QuiltParams.load(buf.readNbt()));
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
            // A custom id names a player's own picture, which this side cannot resolve - only its shape is checked.
            // File names may be Chinese, so the check is "no separators and not absurdly long", not ASCII-only.
            boolean custom = packet.styleId.startsWith("custom:")
                    && packet.styleId.length() <= 64
                    && packet.styleId.indexOf('/') < 0
                    && packet.styleId.indexOf('\\') < 0;
            String id = custom ? packet.styleId : QuiltStyle.of(packet.styleId).id;
            boolean locked = SleepWellConfig.QUILT_LOCK_ALL.get() && !"solid".equals(id);
            if (locked) {
                return;
            }
            MaidCapabilities.of(maid).setQuiltStyle(id);
            MaidCapabilities.of(maid).setQuiltParams(packet.params);
            // The capability does not sync itself, so push the new value to everyone who can see her.
            SleepWellNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY.with(() -> maid),
                    new SyncQuiltStylePacket(maid.getId(), id,
                            MaidCapabilities.of(maid).quiltBorder(), packet.params));
        });
    }
}
