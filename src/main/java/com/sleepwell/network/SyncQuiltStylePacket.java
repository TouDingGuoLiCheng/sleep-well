package com.sleepwell.network;

import com.sleepwell.client.ClientQuiltStyles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server tells the client which quilt a maid wears.
 * <p>
 * The settings capability is plain {@code INBTSerializable}, so it never crosses the wire; without this packet
 * the renderer (client side) would keep reading the default and switching quilts would look like a no-op.
 */
public final class SyncQuiltStylePacket {
    private final int entityId;
    private final String styleId;

    public SyncQuiltStylePacket(int entityId, String styleId) {
        this.entityId = entityId;
        this.styleId = styleId == null ? "" : styleId;
    }

    public static void encode(SyncQuiltStylePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeUtf(packet.styleId, 64);
    }

    public static SyncQuiltStylePacket decode(FriendlyByteBuf buf) {
        return new SyncQuiltStylePacket(buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(SyncQuiltStylePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientQuiltStyles.put(packet.entityId, packet.styleId)));
        context.setPacketHandled(true);
    }
}
