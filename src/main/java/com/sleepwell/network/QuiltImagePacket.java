package com.sleepwell.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** One chunk of a picture on its way to a client. */
public final class QuiltImagePacket {
    private final String hash;
    private final int index;
    private final int total;
    private final byte[] chunk;

    public QuiltImagePacket(String hash, int index, int total, byte[] chunk) {
        this.hash = hash == null ? "" : hash;
        this.index = index;
        this.total = total;
        this.chunk = chunk == null ? new byte[0] : chunk;
    }

    public static void encode(QuiltImagePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.hash, 32);
        buf.writeVarInt(packet.index);
        buf.writeVarInt(packet.total);
        buf.writeByteArray(packet.chunk);
    }

    public static QuiltImagePacket decode(FriendlyByteBuf buf) {
        return new QuiltImagePacket(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(),
                buf.readByteArray(QuiltImageStore.CHUNK + 1024));
    }

    public static void handle(QuiltImagePacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.sleepwell.client.QuiltImageCache.receiveChunk(
                        packet.hash, packet.index, packet.total, packet.chunk)));
        context.setPacketHandled(true);
    }
}
