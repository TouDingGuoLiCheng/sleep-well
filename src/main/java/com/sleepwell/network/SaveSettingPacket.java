package com.sleepwell.network;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.maid.MaidCapabilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One switch changed on the page.
 * <p>
 * {@code key 0} and {@code key 1} are the two global switches: they live in the common config, so the page
 * writes the config value and saves the file. {@code key 2} (winter skip) and {@code key 3} (lie-in / naps)
 * are per-maid switches and go into her capability.
 */
public final class SaveSettingPacket {
    public static final byte KEY_SLEEP_FALLBACK = 0;
    public static final byte KEY_CAMPFIRE_FLEE = 1;
    public static final byte KEY_WINTER_SKIP = 2;
    public static final byte KEY_LIE_IN = 3;

    private final int entityId;
    private final byte key;
    private final boolean value;

    public SaveSettingPacket(int entityId, byte key, boolean value) {
        this.entityId = entityId;
        this.key = key;
        this.value = value;
    }

    public static void encode(SaveSettingPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeByte(packet.key);
        buf.writeBoolean(packet.value);
    }

    public static SaveSettingPacket decode(FriendlyByteBuf buf) {
        return new SaveSettingPacket(buf.readVarInt(), buf.readByte(), buf.readBoolean());
    }

    public static void handle(SaveSettingPacket packet, Supplier<NetworkEvent.Context> supplier) {
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
            switch (packet.key) {
                case KEY_SLEEP_FALLBACK -> {
                    SleepWellConfig.SLEEP_FALLBACK_ENABLED.set(packet.value);
                    SleepWellConfig.SPEC.save();
                }
                case KEY_CAMPFIRE_FLEE -> {
                    SleepWellConfig.CAMPFIRE_FLEE_ENABLED.set(packet.value);
                    SleepWellConfig.SPEC.save();
                }
                case KEY_WINTER_SKIP -> MaidCapabilities.of(maid).setWinterSkip(packet.value);
                case KEY_LIE_IN -> MaidCapabilities.of(maid).setLieIn(packet.value);
                default -> {
                }
            }
        });
    }
}
