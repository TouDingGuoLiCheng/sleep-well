package com.sleepwell.quilt;

import com.sleepwell.network.QuiltCatalogPacket;
import com.sleepwell.network.SleepWellNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The world's shelf of named quilts, kept next to the pictures it refers to.
 * <p>
 * A quilt is a picture plus a name plus its look; the shelf is what the wall shows, so every player sees the same
 * quilts under the same names, and picking one carries its whole look to the maid.
 */
public final class QuiltCatalog {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/catalog");
    public static final int MAX_ENTRIES = 64;

    private QuiltCatalog() {
    }

    private static Path file(MinecraftServer server) {
        return com.sleepwell.network.QuiltImageStore.folder(server).resolve("catalog.dat");
    }

    public static List<SavedQuilt> all(ServerLevel level) {
        Path path = file(level.getServer());
        List<SavedQuilt> quilts = new ArrayList<>();
        if (!Files.isRegularFile(path)) {
            return quilts;
        }
        try (InputStream in = Files.newInputStream(path)) {
            CompoundTag root = NbtIo.readCompressed(in);
            ListTag list = root.getList("Quilts", 10);
            for (int i = 0; i < list.size(); i++) {
                SavedQuilt quilt = SavedQuilt.load(list.getCompound(i));
                if (com.sleepwell.network.QuiltImageStore.validHash(quilt.hash())) {
                    quilts.add(quilt);
                }
            }
        } catch (Exception failure) {
            LOGGER.warn("Could not read the quilt shelf", failure);
        }
        return quilts;
    }

    /** Adds or renames one quilt; the picture's hash is the identity, so saving twice renames instead of piling up. */
    public static void put(ServerLevel level, SavedQuilt quilt) {
        if (!com.sleepwell.network.QuiltImageStore.validHash(quilt.hash())) {
            return;
        }
        List<SavedQuilt> quilts = all(level);
        quilts.removeIf(known -> known.hash().equals(quilt.hash()));
        quilts.add(new SavedQuilt(quilt.hash(), SavedQuilt.clean(quilt.name()), quilt.params()));
        while (quilts.size() > MAX_ENTRIES) {
            quilts.remove(0);
        }
        Path path = file(level.getServer());
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (SavedQuilt each : quilts) {
            list.add(each.save());
        }
        root.put("Quilts", list);
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                NbtIo.writeCompressed(root, out);
            }
        } catch (Exception failure) {
            LOGGER.warn("Could not save the quilt shelf", failure);
        }
    }

    public static void sendTo(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level) {
            SleepWellNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new QuiltCatalogPacket(all(level)));
        }
    }

    public static void broadcast(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendTo(player);
        }
    }

    static Path folderOf(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("sleep_well_quilts");
    }
}
