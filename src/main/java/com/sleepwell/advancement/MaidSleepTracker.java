package com.sleepwell.advancement;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import com.sleepwell.maid.MaidCapabilities;
import com.sleepwell.unlock.PlayerUnlocks;
import com.sleepwell.unlock.UnlockCapabilities;
import com.sleepwell.unlock.UnlockHandler;
import com.sleepwell.winter.SeasonBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches each maid's sleep so the achievement page can count it: one sleep is one lie-down followed by
 * one getting up, whatever time of day it was.
 * <p>
 * On falling asleep it also records the quilt face the player has now seen, the season and how many maids
 * are asleep around them; on waking it counts the night (a maid's own counter) and measures how long it lasted.
 * That is everything the fourteen achievements need, and it leaves the wake diagnostics alone.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class MaidSleepTracker {
    /** Eight in-game hours: what "straight through the night" means for the 雷打不动 achievement. */
    private static final long IRON_SLEEP_TICKS = 8000L;
    /** How far "the maids around me" reaches for 女仆们，集合！. */
    private static final double SQUAD_RADIUS = 32.0D;
    /** Every built-in face, so wearing all of them can be recognised without hard-coding the registry size. */
    private static final int ALL_FACES = 16;

    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();

    private MaidSleepTracker() {
    }

    private static final class Track {
        private boolean sleeping;
        private long since;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        Track track = TRACKS.computeIfAbsent(maid.getUUID(), key -> new Track());
        if (track.sleeping && !maid.isSleeping()) {
            TRACKS.remove(maid.getUUID());
            onWokeUp(maid, track, level);
            return;
        }
        if (maid.isSleeping() && !track.sleeping) {
            track.sleeping = true;
            track.since = level.getGameTime();
            onSleepStart(maid, level);
        }
    }

    private static void onSleepStart(EntityMaid maid, ServerLevel level) {
        if (!(maid.getOwner() instanceof ServerPlayer player)) {
            return;
        }
        Achievements.award(player, Achievements.ROOT, 1);
        PlayerUnlocks unlocks = UnlockCapabilities.of(player);

        String quilt = MaidCapabilities.of(maid).quiltStyle();
        if (!quilt.startsWith("custom:") && unlocks.markWorn(quilt)
                && unlocks.worn().stream().filter(id -> !id.startsWith("custom:")).count() >= ALL_FACES) {
            Achievements.award(player, Achievements.ALL_FACES, 1);
        }
        if (SeasonBridge.isWinter(level)) {
            Achievements.award(player, Achievements.WINTER_SLEEP, 1);
        }
        int squad = level.getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(SQUAD_RADIUS), EntityMaid::isSleeping).size();
        if (squad >= 10) {
            Achievements.award(player, Achievements.MAID_SQUAD, squad);
        }
        UnlockHandler.evaluate(player);
    }

    private static void onWokeUp(EntityMaid maid, Track track, ServerLevel level) {
        long slept = level.getGameTime() - track.since;
        if (slept <= 0L) {
            return;
        }
        if (maid.getOwner() instanceof ServerPlayer player) {
            int count = MaidCapabilities.of(maid).incrementSleepCount();
            Achievements.award(player, Achievements.SLEEP_COUNT, count);
            if (slept >= IRON_SLEEP_TICKS) {
                Achievements.award(player, Achievements.IRON_SLEEP, 1);
            }
            UnlockHandler.evaluate(player);
        }
    }
}
