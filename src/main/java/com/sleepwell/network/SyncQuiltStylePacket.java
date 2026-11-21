package com.sleepwell.network;

import com.sleepwell.client.ClientQuiltStyles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server tells the client which quilt a maid wears, and which border is sewn around it.
 * <p>
 * The settings capability is plain {@code INBTSerializable}, so it never crosses the wire; without this packet
 * the renderer (client side) would keep reading the default and switching quilts would look like a no-op.
 */
public final class SyncQuiltStylePacket {
    private final int entityId;
    private final String styleId;
    private final String borderId;
    private final com.sleepwell.client.quilt.QuiltParams params;

    public SyncQuiltStylePacket(int entityId, String styleId, String borderId) {
        this(entityId, styleId, borderId, com.sleepwell.client.quilt.QuiltParams.DEFAULT);
    }

    public SyncQuiltStylePacket(int entityId, String styleId, String borderId,
                                com.sleepwell.client.quilt.QuiltParams params) {
        this.entityId = entityId;
        this.styleId = styleId == null ? "" : styleId;
        this.borderId = borderId == null ? "" : borderId;
        this.params = params == null ? com.sleepwell.client.quilt.QuiltParams.DEFAULT : params;
    }

    public static void encode(SyncQuiltStylePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeUtf(packet.styleId, 64);
        buf.writeUtf(packet.borderId, 32);
        buf.writeNbt(packet.params.save());
    }

    public static SyncQuiltStylePacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        String styleId = buf.readUtf(64);
        String borderId = buf.readUtf(32);
        return new SyncQuiltStylePacket(entityId, styleId, borderId,
                com.sleepwell.client.quilt.QuiltParams.load(buf.readNbt()));
    }

    public static void handle(SyncQuiltStylePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientQuiltStyles.put(packet.entityId, packet.styleId, packet.borderId,
                        packet.params)));
        context.setPacketHandled(true);
    }
}
