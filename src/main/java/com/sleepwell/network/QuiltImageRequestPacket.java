package com.sleepwell.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A client asking for the picture behind a {@code custom:<hash>} quilt it has never seen. */
public final class QuiltImageRequestPacket {
    private final String hash;

    public QuiltImageRequestPacket(String hash) {
        this.hash = hash == null ? "" : hash;
    }

    public static void encode(QuiltImageRequestPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.hash, 32);
    }

    public static QuiltImageRequestPacket decode(FriendlyByteBuf buf) {
        return new QuiltImageRequestPacket(buf.readUtf(32));
    }

    public static void handle(QuiltImageRequestPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> QuiltImageStore.sendTo(player, packet.hash));
        context.setPacketHandled(true);
    }
}
