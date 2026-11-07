package com.sleepwell.network;

import com.sleepwell.SleepWellMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** One channel for the maid GUI page. */
public final class SleepWellNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SleepWellMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private SleepWellNetwork() {
    }

    public static void init() {
        int id = 0;
        CHANNEL.messageBuilder(OpenSettingsPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenSettingsPacket::encode)
                .decoder(OpenSettingsPacket::decode)
                .consumerMainThread(OpenSettingsPacket::handle)
                .add();
        CHANNEL.messageBuilder(SaveSettingPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveSettingPacket::encode)
                .decoder(SaveSettingPacket::decode)
                .consumerMainThread(SaveSettingPacket::handle)
                .add();
        CHANNEL.messageBuilder(SaveSkipTasksPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveSkipTasksPacket::encode)
                .decoder(SaveSkipTasksPacket::decode)
                .consumerMainThread(SaveSkipTasksPacket::handle)
                .add();
        CHANNEL.messageBuilder(SaveQuiltStylePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveQuiltStylePacket::encode)
                .decoder(SaveQuiltStylePacket::decode)
                .consumerMainThread(SaveQuiltStylePacket::handle)
                .add();
        CHANNEL.messageBuilder(SyncQuiltStylePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncQuiltStylePacket::encode)
                .decoder(SyncQuiltStylePacket::decode)
                .consumerMainThread(SyncQuiltStylePacket::handle)
                .add();
    }
}
