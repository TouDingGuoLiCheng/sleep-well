package com.sleepwell.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One chunk of a player's picture on its way to the server.
 * <p>
 * Chunked because a single payload that big would be refused by the channel, and because the same class carries
 * the server's answer: the picture is stored once per world under its content hash, so every client that meets a
 * maid wearing it can ask for the same bytes.
 */
public final class QuiltImageUploadPacket {
    private final String hash;
    private final int index;
    private final int total;
    private final byte[] chunk;

    public QuiltImageUploadPacket(String hash, int index, int total, byte[] chunk) {
        this.hash = hash == null ? "" : hash;
        this.index = index;
        this.total = total;
        this.chunk = chunk == null ? new byte[0] : chunk;
    }

    public static void encode(QuiltImageUploadPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.hash, 32);
        buf.writeVarInt(packet.index);
        buf.writeVarInt(packet.total);
        buf.writeByteArray(packet.chunk);
    }

    public static QuiltImageUploadPacket decode(FriendlyByteBuf buf) {
        return new QuiltImageUploadPacket(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(),
                buf.readByteArray(QuiltImageStore.CHUNK + 1024));
    }

    public static void handle(QuiltImageUploadPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            // Any player may add a picture: the size, count and png checks are what guard the world, not rank.
            QuiltImageStore.acceptChunk(level, packet.hash, packet.index, packet.total, packet.chunk);
        });
        context.setPacketHandled(true);
    }
}
