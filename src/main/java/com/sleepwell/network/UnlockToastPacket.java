package com.sleepwell.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** "Unlocked a new quilt face: 西瓜被", shown as the game's own toast so it never blocks the cursor. */
public final class UnlockToastPacket {
    private final String target;

    public UnlockToastPacket(String target) {
        this.target = target == null ? "" : target;
    }

    public static void encode(UnlockToastPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.target, 64);
    }

    public static UnlockToastPacket decode(FriendlyByteBuf buf) {
        return new UnlockToastPacket(buf.readUtf(64));
    }

    public static void handle(UnlockToastPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.sleepwell.client.ClientUnlocks.toast(packet.target)));
        context.setPacketHandled(true);
    }
}
