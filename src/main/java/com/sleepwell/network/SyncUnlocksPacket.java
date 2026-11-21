package com.sleepwell.network;

import com.sleepwell.unlock.UnlockRules;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The player's whole unlock state, plus how far every still-locked row is. */
public final class SyncUnlocksPacket {
    private final List<String> unlocked;
    private final List<UnlockRules.Progress> progress;

    public SyncUnlocksPacket(List<String> unlocked, List<UnlockRules.Progress> progress) {
        this.unlocked = unlocked == null ? List.of() : List.copyOf(unlocked);
        this.progress = progress == null ? List.of() : List.copyOf(progress);
    }

    public static void encode(SyncUnlocksPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.unlocked.size());
        for (String target : packet.unlocked) {
            buf.writeUtf(target, 64);
        }
        buf.writeVarInt(packet.progress.size());
        for (UnlockRules.Progress row : packet.progress) {
            buf.writeUtf(row.target(), 64);
            buf.writeVarInt(row.progress());
            buf.writeVarInt(row.goal());
        }
    }

    public static SyncUnlocksPacket decode(FriendlyByteBuf buf) {
        int unlockedSize = buf.readVarInt();
        List<String> unlocked = new ArrayList<>(unlockedSize);
        for (int i = 0; i < unlockedSize; i++) {
            unlocked.add(buf.readUtf(64));
        }
        int progressSize = buf.readVarInt();
        List<UnlockRules.Progress> progress = new ArrayList<>(progressSize);
        for (int i = 0; i < progressSize; i++) {
            progress.add(new UnlockRules.Progress(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
        }
        return new SyncUnlocksPacket(unlocked, progress);
    }

    public static void handle(SyncUnlocksPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.sleepwell.client.ClientUnlocks.receive(packet.unlocked, packet.progress)));
        context.setPacketHandled(true);
    }
}
