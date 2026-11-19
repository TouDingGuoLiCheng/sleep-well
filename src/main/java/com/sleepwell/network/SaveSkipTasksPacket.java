package com.sleepwell.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.winter.SkipTaskSelection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The whole winter skip list, sent whenever a box is ticked on the gear overlay.
 * <p>
 * The list lives in the common config and is global, so the maid is only used to check that the sender
 * is allowed to open her page at all.
 */
public final class SaveSkipTasksPacket {
    /** Plenty of room for every task of every addon; anything bigger is a broken client. */
    private static final int MAX_TASKS = 512;

    private final int entityId;
    private final List<String> tasks;

    public SaveSkipTasksPacket(int entityId, List<String> tasks) {
        this.entityId = entityId;
        this.tasks = List.copyOf(tasks);
    }

    public static void encode(SaveSkipTasksPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeVarInt(packet.tasks.size());
        for (String task : packet.tasks) {
            buf.writeUtf(task, 128);
        }
    }

    public static SaveSkipTasksPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int size = Math.min(buf.readVarInt(), MAX_TASKS);
        List<String> tasks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            tasks.add(buf.readUtf(128));
        }
        return new SaveSkipTasksPacket(entityId, tasks);
    }

    public static void handle(SaveSkipTasksPacket packet, Supplier<NetworkEvent.Context> supplier) {
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
            SleepWellConfig.WINTER_SKIP_TASKS.set(SkipTaskSelection.normalize(packet.tasks));
            SleepWellConfig.SPEC.save();
        });
    }
}
