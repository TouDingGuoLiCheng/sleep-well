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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns sleeping into something with consequences.
 * <p>
 * Judged on the maid's own schedule ({@code getScheduleDetail() == REST}), so a night-shift maid sleeping at noon
 * is judged exactly like a day-shift maid sleeping at night.
 * <ul>
 *   <li><b>干劲</b> - she slept at least an in-game hour inside that window and then woke up. Waking up after the
 *       window already ended (a lie-in) still counts and lasts {@link SleepWellConfig#VIGOR_LIE_IN_EXTRA_SECONDS}
 *       longer.</li>
 *   <li><b>困倦</b> - the window ended with her awake and she had either never lain down or slept too little.</li>
 *   <li><b>sleep regen</b> - 1 HP every 3 s while asleep.</li>
 *   <li><b>困倦 decay</b> - while she is asleep, 困倦 loses {@link SleepWellConfig#DROWSY_SLEEP_DECAY_SECONDS}
 *       seconds per second, so sleeping actually washes the debuff off.</li>
 * </ul>
 * A nap outside the window neither pays nor punishes: the counters are cleared when she wakes.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class SleepEffectsHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/effects");
    /** One in-game hour (1000 ticks) is what counts as "she actually slept". */
    private static final int MIN_SLEEP_TICKS = 1000;
    /** The whole window has to add up to this much sleep, or the night did not happen. */
    private static final int MIN_WINDOW_SLEEP_TICKS = 1000;
    /**
     * Ticks of "not REST" before the window closes. A maid that is asleep can briefly report another activity
     * while the brain switches tasks, and closing the window on that single tick is what made a full night look
     * like a skipped one.
     */
    private static final int WINDOW_CLOSE_GRACE = 40;
    private static final int REGEN_INTERVAL = 60;
    private static final float REGEN_AMOUNT = 1.0F;
    /** One 困倦 decay step per real second. */
    private static final int DROWSY_DECAY_INTERVAL = 20;
    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();
    private static final AtomicInteger LOGS = new AtomicInteger();

    private SleepEffectsHandler() {
    }

    /** Per maid, the little that has to survive between ticks. */
    private static final class Track {
        private boolean sleeping;
        private long sleepStart;
        private boolean inWindow;
        private int windowSleepTicks;
        private boolean sleptInWindow;
        private int outOfWindow;
        private int regenTimer;
        /** True when the window closed while she was still asleep: that is a lie-in, judged when she wakes. */
        private boolean closedWhileAsleep;
        /**
         * A window pays out once. Without this, waking up inside the last grace ticks granted 干劲, then the
         * window closed on an awake maid with the counters already cleared and handed out 困倦 as well.
         */
        private boolean judged;
        /** Ticks of sleep accumulated towards the next 困倦 decay step. */
        private int drowsyTimer;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        Track track = TRACKS.computeIfAbsent(maid.getUUID(), key -> new Track());
        long now = level.getGameTime();
        boolean rest = maid.getScheduleDetail() == Activity.REST;
        boolean sleeping = maid.isSleeping();

        // 1) Window bookkeeping. Opening on REST, closing only after the grace period.
        if (rest) {
            track.outOfWindow = 0;
            if (!track.inWindow) {
                track.inWindow = true;
                track.windowSleepTicks = 0;
                track.sleptInWindow = false;
                track.closedWhileAsleep = false;
                track.judged = false;
            }
        } else if (track.inWindow && ++track.outOfWindow >= WINDOW_CLOSE_GRACE) {
            track.inWindow = false;
            if (sleeping) {
                // Still in bed after her window ended: a lie-in. Judge it when she gets up.
                track.closedWhileAsleep = true;
            } else {
                judgeDrowsy(maid, track);
                track.windowSleepTicks = 0;
                track.sleptInWindow = false;
            }
        }

        // 2) Sleep bookkeeping. Any sleep inside the window counts, whatever the activity says right now.
        if (sleeping) {
            if (!track.sleeping) {
                track.sleeping = true;
                track.sleepStart = now;
            }
            if (track.inWindow) {
                track.sleptInWindow = true;
                track.windowSleepTicks++;
            }
            if (SleepWellConfig.SLEEP_REGEN_ENABLED.get() && ++track.regenTimer >= REGEN_INTERVAL) {
                track.regenTimer = 0;
                if (maid.getHealth() < maid.getMaxHealth()) {
                    maid.heal(REGEN_AMOUNT);
                }
            }
            if (SleepWellConfig.DROWSY_SLEEP_DECAY_ENABLED.get()
                    && ++track.drowsyTimer >= DROWSY_DECAY_INTERVAL) {
                track.drowsyTimer = 0;
                decayDrowsy(maid);
            }
            return;
        }
        if (!track.sleeping) {
            return;
        }

        // 3) She just got up.
        track.sleeping = false;
        track.regenTimer = 0;
        track.drowsyTimer = 0;
        long slept = now - track.sleepStart;
        boolean enough = track.sleptInWindow
                && track.windowSleepTicks >= MIN_WINDOW_SLEEP_TICKS
                && slept >= MIN_SLEEP_TICKS;
        boolean lieIn = !track.inWindow;
        if (enough && SleepWellConfig.VIGOR_ENABLED.get()) {
            track.judged = true;
            applyVigor(maid, lieIn);
        } else if (!enough && track.closedWhileAsleep && SleepWellConfig.DROWSY_ENABLED.get()) {
            // Slept through most of the window but still too little: same verdict as never lying down.
            track.judged = true;
            applyDrowsy(maid, track);
        }
        log(maid, "起床", track, slept, enough, lieIn);
        track.windowSleepTicks = 0;
        track.sleptInWindow = false;
        track.closedWhileAsleep = false;
    }

    /**
     * Shortens 困倦 by one step. 1.20.1 has no {@code setDuration}, so the effect is swapped for a shorter copy
     * that keeps her amplifier and visibility; the swap costs one remove/add per second of sleep at most.
     */
    private static void decayDrowsy(EntityMaid maid) {
        MobEffectInstance drowsy = maid.getEffect(SleepWellEffects.DROWSY.get());
        if (drowsy == null || drowsy.isInfiniteDuration()) {
            return;
        }
        int left = DrowsySleepDecay.remainingTicks(drowsy.getDuration(),
                SleepWellConfig.DROWSY_SLEEP_DECAY_SECONDS.get());
        int amplifier = drowsy.getAmplifier();
        boolean ambient = drowsy.isAmbient();
        boolean visible = drowsy.isVisible();
        boolean icon = drowsy.showIcon();
        maid.removeEffect(SleepWellEffects.DROWSY.get());
        if (left <= 0) {
            LOGGER.debug("[睡个好觉] 困倦在睡眠中消退完毕 maid={}", maid.getUUID());
            return;
        }
        maid.addEffect(new MobEffectInstance(SleepWellEffects.DROWSY.get(), left, amplifier, ambient, visible, icon));
    }

    private static void applyVigor(EntityMaid maid, boolean lieIn) {
        int seconds = SleepWellConfig.VIGOR_SECONDS.get()
                + (lieIn ? SleepWellConfig.VIGOR_LIE_IN_EXTRA_SECONDS.get() : 0);
        maid.addEffect(new MobEffectInstance(SleepWellEffects.VIGOR.get(), seconds * 20, 0, false, true, true));
        com.sleepwell.advancement.Achievements.award(maid,
                com.sleepwell.advancement.Achievements.VIGOR, 1);
    }

    private static void applyDrowsy(EntityMaid maid, Track track) {
        maid.addEffect(new MobEffectInstance(SleepWellEffects.DROWSY.get(),
                SleepWellConfig.DROWSY_SECONDS.get() * 20, 0, false, true, true));
        com.sleepwell.advancement.Achievements.award(maid,
                com.sleepwell.advancement.Achievements.DROWSY, 1);
        log(maid, "困倦", track, 0L, false, false);
    }

    private static void judgeDrowsy(EntityMaid maid, Track track) {
        if (track.judged || !SleepWellConfig.DROWSY_ENABLED.get()) {
            return;
        }
        track.judged = true;
        if (!track.sleptInWindow || track.windowSleepTicks < MIN_WINDOW_SLEEP_TICKS) {
            applyDrowsy(maid, track);
        }
    }

    /** One line per event for the first few events, DEBUG afterwards: enough to verify, not enough to spam. */
    private static void log(EntityMaid maid, String what, Track track, long slept, boolean enough, boolean lieIn) {
        boolean verbose = LOGS.getAndIncrement() < 40;
        if (!verbose && !LOGGER.isDebugEnabled()) {
            return;
        }
        String detail = track == null ? ""
                : " 窗口内睡眠=" + track.windowSleepTicks + "ticks 躺下过=" + track.sleptInWindow
                + " 窗口开=" + track.inWindow + " 窗口末仍未起=" + track.closedWhileAsleep;
        String line = "[" + what + "] maid=" + maid.getUUID() + " 本次睡眠=" + slept + "ticks"
                + " 判定=" + (enough ? "够" : "不够") + " 赖床=" + lieIn + detail;
        if (verbose) {
            LOGGER.info("[睡个好觉] {}", line);
        } else {
            LOGGER.debug("[睡个好觉] {}", line);
        }
    }
}
