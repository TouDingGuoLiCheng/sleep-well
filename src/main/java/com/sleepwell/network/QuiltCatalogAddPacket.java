package com.sleepwell.network;

import com.sleepwell.quilt.QuiltCatalog;
import com.sleepwell.quilt.SavedQuilt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A client saving a named quilt to the world's shelf. */
public final class QuiltCatalogAddPacket {
    private final String hash;
    private final String name;
    private final CompoundTag params;

    public QuiltCatalogAddPacket(String hash, String name, CompoundTag params) {
        this.hash = hash == null ? "" : hash;
        this.name = SavedQuilt.clean(name);
        this.params = params == null ? new CompoundTag() : params;
    }

    public static void encode(QuiltCatalogAddPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.hash, 32);
        buf.writeUtf(packet.name, 64);
        buf.writeNbt(packet.params);
    }

    public static QuiltCatalogAddPacket decode(FriendlyByteBuf buf) {
        String hash = buf.readUtf(32);
        String name = buf.readUtf(64);
        CompoundTag params = buf.readNbt();
        return new QuiltCatalogAddPacket(hash, name, params);
    }

    public static void handle(QuiltCatalogAddPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player == null) {
            return;
        }
        context.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) {
                return;
            }
            QuiltCatalog.put(level, new SavedQuilt(packet.hash, packet.name,
                    com.sleepwell.client.quilt.QuiltParams.load(packet.params)));
            QuiltCatalog.broadcast(level.getServer());
        });
        context.setPacketHandled(true);
    }
}
