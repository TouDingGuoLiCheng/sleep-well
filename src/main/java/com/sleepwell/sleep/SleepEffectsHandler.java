package com.sleepwell.sleep;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.effect.SleepWellEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns sleeping into something with consequences.
 * <p>
 * Four things happen here, all on the server and all driven by the maid's own schedule rather than the world
 * clock, because a night-shift maid sleeps at noon and must be judged on her own window:
 * <ul>
 *   <li><b>干劲</b> - slept at least {@link #MIN_SLEEP_TICKS} inside her rest window, then woke up.</li>
 *   <li><b>困倦</b> - her rest window ended and she either never lay down or slept less than
 *       {@link #MIN_WINDOW_SLEEP_TICKS} in it. Applied the moment work time starts.</li>
 *   <li><b>sleep regen</b> - 1 HP every 3 s while asleep.</li>
 * </ul>
 * A nap outside her window gives nothing and is never punished: it is not her sleep.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class SleepEffectsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/effects");
    /** One in-game hour (1000 ticks) of sleep is what counts as "she actually slept". */
    private static final int MIN_SLEEP_TICKS = 1000;
    /** The whole rest window has to add up to this much sleep, or the night did not happen. */
    private static final int MIN_WINDOW_SLEEP_TICKS = 1000;
    private static final int REGEN_INTERVAL = 60;
    private static final float REGEN_AMOUNT = 1.0F;
    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();

    private SleepEffectsHandler() {
    }

    /** Per maid, the little that has to survive between ticks. */
    private static final class Track {
        private boolean sleeping;
        private long sleepStart;
        private boolean inWindow;
        private int windowSleepTicks;
        private boolean sleptInWindow;
        private int regenTimer;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        Track track = TRACKS.computeIfAbsent(maid.getUUID(), key -> new Track());
        long now = level.getGameTime();
        boolean rest = maid.getScheduleDetail() == Activity.REST;

        if (rest && !track.inWindow) {
            track.inWindow = true;
            track.windowSleepTicks = 0;
            track.sleptInWindow = false;
        } else if (!rest && track.inWindow) {
            track.inWindow = false;
            boolean skipped = !track.sleptInWindow || track.windowSleepTicks < MIN_WINDOW_SLEEP_TICKS;
            if (skipped && SleepWellConfig.DROWSY_ENABLED.get()) {
                applyDrowsy(maid, track);
            }
            track.windowSleepTicks = 0;
            track.sleptInWindow = false;
        }

        boolean sleeping = maid.isSleeping();
        if (sleeping) {
            if (!track.sleeping) {
                track.sleeping = true;
                track.sleepStart = now;
            }
            if (rest) {
                track.sleptInWindow = true;
                track.windowSleepTicks++;
            }
            if (SleepWellConfig.SLEEP_REGEN_ENABLED.get() && ++track.regenTimer >= REGEN_INTERVAL) {
                track.regenTimer = 0;
                if (maid.getHealth() < maid.getMaxHealth()) {
                    maid.heal(REGEN_AMOUNT);
                }
            }
        } else if (track.sleeping) {
            track.sleeping = false;
            track.regenTimer = 0;
            long slept = now - track.sleepStart;
            // Only a sleep inside her own window earns 干劲; an afternoon nap does not.
            if (slept >= MIN_SLEEP_TICKS && track.sleptInWindow && SleepWellConfig.VIGOR_ENABLED.get()) {
                applyVigor(maid);
            }
        }
    }

    private static void applyVigor(EntityMaid maid) {
        int ticks = SleepWellConfig.VIGOR_SECONDS.get() * 20;
        maid.addEffect(new MobEffectInstance(SleepWellEffects.VIGOR.get(), ticks, 0, false, true, true));
        LOGGER.debug("[效果] 干劲 maid={} {}s", maid.getUUID(), SleepWellConfig.VIGOR_SECONDS.get());
    }

    private static void applyDrowsy(EntityMaid maid, Track track) {
        int ticks = SleepWellConfig.DROWSY_SECONDS.get() * 20;
        maid.addEffect(new MobEffectInstance(SleepWellEffects.DROWSY.get(), ticks, 0, false, true, true));
        LOGGER.debug("[效果] 困倦 maid={} {}s 窗口内睡眠={}ticks 躺下过={}",
                maid.getUUID(), SleepWellConfig.DROWSY_SECONDS.get(), track.windowSleepTicks, track.sleptInWindow);
    }
}
