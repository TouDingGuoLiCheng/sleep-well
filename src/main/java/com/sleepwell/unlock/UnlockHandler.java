package com.sleepwell.unlock;

import com.sleepwell.SleepWellMod;
import com.sleepwell.network.SleepWellNetwork;
import com.sleepwell.network.SyncUnlocksPacket;
import com.sleepwell.network.UnlockToastPacket;
import com.sleepwell.winter.SeasonBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates the unlock table and tells the player what just opened.
 * <p>
 * The table is short, so it runs once a second per player and again right after the four events that can
 * change it. A target that flips writes to the player's capability, syncs the whole set to that client and
 * pops a vanilla-style toast; nothing interrupts what the player was doing.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class UnlockHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/unlock");

    private UnlockHandler() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        seasonFallback(player);
        evaluate(player);
        sync(player);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getGameTime() % 20L != 0L) {
            return;
        }
        evaluate(player);
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        if (event.isCanceled() || !event.getBlock().is(Blocks.CHERRY_SAPLING)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        grantFlag(player, "cherry");
    }

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (event.getChild() instanceof Sheep && event.getCausedByPlayer() instanceof ServerPlayer player) {
            grantFlag(player, "sheep");
        }
    }

    @SubscribeEvent
    public static void onTame(AnimalTameEvent event) {
        if (event.getAnimal() instanceof Cat && event.getTamer() instanceof ServerPlayer player) {
            grantFlag(player, "cat");
        }
    }

    /** Records a one-off event and gives it an immediate chance to open something. */
    public static void grantFlag(ServerPlayer player, String flag) {
        if (UnlockCapabilities.of(player).markFlag(flag)) {
            evaluate(player);
        }
    }

    /**
     * Runs the table. Season progress is recorded here too, because "has been spring" is a fact about the
     * past that no vanilla statistic can answer.
     */
    public static void evaluate(ServerPlayer player) {
        PlayerUnlocks unlocks = UnlockCapabilities.of(player);
        boolean changed = false;
        if (SeasonBridge.sereneSeasonsLoaded()) {
            String season = SeasonBridge.season(player.level());
            if (season != null && unlocks.markSeason(season)) {
                changed = true;
            }
        }
        for (UnlockRules.Rule rule : UnlockRules.all()) {
            if (unlocks.unlocked().contains(rule.target())) {
                continue;
            }
            if (!rule.condition().satisfied(player)) {
                continue;
            }
            unlocks.unlock(rule.target());
            SleepWellNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new UnlockToastPacket(rule.target()));
            LOGGER.info("Unlocked {} for {}", rule.target(), player.getGameProfile().getName());
            changed = true;
        }
        if (changed) {
            sync(player);
        }
    }

    /** Pushes the whole set plus every locked row's progress, so the wall can draw "12/20" without asking. */
    public static void sync(ServerPlayer player) {
        List<String> unlocked = new ArrayList<>(UnlockCapabilities.of(player).unlocked());
        List<UnlockRules.Progress> progress = UnlockRules.progress(player);
        SleepWellNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncUnlocksPacket(unlocked, progress));
    }

    /**
     * Without Serene Seasons the four seasonal quilts can never unlock, so they are handed out silently on the
     * first join instead of sitting behind an impossible condition. No toast: nothing was earned.
     */
    private static void seasonFallback(ServerPlayer player) {
        PlayerUnlocks unlocks = UnlockCapabilities.of(player);
        if (unlocks.seasonFallbackDone()) {
            return;
        }
        unlocks.setSeasonFallbackDone();
        if (SeasonBridge.sereneSeasonsLoaded()) {
            return;
        }
        for (String target : List.of("spring_cherry", "summer_sunflower", "autumn", "winter")) {
            unlocks.unlock(target);
        }
        for (String season : List.of("spring", "summer", "autumn", "winter")) {
            unlocks.markSeason(season);
        }
        sync(player);
    }
}
