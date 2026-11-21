package com.sleepwell.network;

import com.sleepwell.quilt.SavedQuilt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The world's whole shelf of named quilts, sent on join and again whenever anyone saves one. */
public final class QuiltCatalogPacket {
    private final List<SavedQuilt> quilts;

    public QuiltCatalogPacket(List<SavedQuilt> quilts) {
        this.quilts = quilts == null ? List.of() : List.copyOf(quilts);
    }

    public static void encode(QuiltCatalogPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.quilts.size());
        for (SavedQuilt quilt : packet.quilts) {
            buf.writeUtf(quilt.hash(), 32);
            buf.writeUtf(quilt.name(), 64);
            buf.writeNbt(quilt.params().save());
        }
    }

    public static QuiltCatalogPacket decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<SavedQuilt> quilts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String hash = buf.readUtf(32);
            String name = buf.readUtf(64);
            CompoundTag params = buf.readNbt();
            quilts.add(new SavedQuilt(hash, name, com.sleepwell.client.quilt.QuiltParams.load(
                    params == null ? new CompoundTag() : params)));
        }
        return new QuiltCatalogPacket(quilts);
    }

    public static void handle(QuiltCatalogPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.sleepwell.client.ClientQuiltCatalog.receive(packet.quilts)));
        context.setPacketHandled(true);
    }
}
