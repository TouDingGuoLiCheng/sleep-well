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
    /**
     * How much sleep counts as "a full night's worth" when shortening 困倦: ten in-game hours. Sleeping this much
     * inside the window leaves only a quarter of the usual 困倦.
     */
    private static final int FULL_NIGHT_TICKS = 10_000;
    /** Stacking has to stop somewhere, or a long peaceful world turns into a permanent buff. */
    private static final int MAX_STACK_TICKS = 20 * 60 * 20;
    /** The most 困倦 can be shortened to: even a perfect night leaves something to shake off. */
    private static final float DROWSY_SHORTEN_MAX = 0.75F;

    /** One 困倦 decay step per real second. */
    private static final int DROWSY_DECAY_INTERVAL = 20;
    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();
    private static final AtomicInteger LOGS = new AtomicInteger();

    private SleepEffectsHandler() {
    }

    /**
     * Everyone sleeping through the night does not tick away: the clock jumps forward by thousands of ticks in one
     * server tick. Counting ticks one by one therefore saw a maid who slept the whole night as "slept almost
     * nothing", because only the handful of ticks before the jump had been added up - and the morning handed her
     * 困倦 while she had just slept eight hours. This is also how players are detected: the event fires when the
     * bed vote succeeds, which works the same in single player and with a dozen players, without watching anyone.
     */
    @SubscribeEvent
    public static void onSleepFinished(net.minecraftforge.event.level.SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long jump = Math.max(0L, event.getNewTime() - level.getDayTime());
        if (jump <= 0L) {
            return;
        }
        int credited = 0;
        int refused = 0;
        long capped = Math.min(Integer.MAX_VALUE, jump);
        for (Track track : TRACKS.values()) {
            // Whoever is in bed while the night is being skipped slept through the rest of it. No window, no
            // minimum, no "did she stay down" test: being in a loaded chunk and asleep at that moment is the
            // whole qualification, exactly as the owner asked.
            if (!track.sleeping) {
                refused++;
                continue;
            }
            track.windowSleepTicks += (int) capped;
            track.sleptInWindow = true;
            // "How long she slept" is measured from when she lay down, so the skipped night belongs inside it.
            track.sleepStart -= jump;
            track.skippedNight = true;
            track.wokeInWindow = false;
            credited++;
        }
        LOGGER.info("[睡个好觉] 玩家睡过整夜：时间跳过 {} ticks，床上睡着的 {} 只记作睡了这一觉，没上床的 {} 只不记",
                jump, credited, refused);

        // The skipped ticks were ticks she spent asleep, so 困倦 has to come off her for them exactly as it does
        // one tick at a time. Without this a maid who slept the whole night through the skip woke up still under
        // the full debuff, which reads as "sleeping does nothing about drowsy".
        if (SleepWellConfig.DROWSY_SLEEP_DECAY_ENABLED.get()) {
            int decayed = 0;
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof EntityMaid maid && maid.isSleeping() && decayDrowsyBy(maid, jump)) {
                    decayed++;
                }
            }
            if (decayed > 0) {
                LOGGER.info("[睡个好觉] 跳过的 {} ticks 里，{} 只女仆的困倦照常消退", jump, decayed);
            }
        }
    }

    /**
     * The same decay as {@link #decayDrowsy}, applied to a whole block of ticks at once. True when she actually had
     * 困倦 to lose, so the caller can report how many maids were touched.
     */
    private static boolean decayDrowsyBy(EntityMaid maid, long skippedTicks) {
        MobEffectInstance drowsy = maid.getEffect(SleepWellEffects.DROWSY.get());
        if (drowsy == null || drowsy.isInfiniteDuration()) {
            return false;
        }
        long steps = skippedTicks / DROWSY_DECAY_INTERVAL;
        if (steps <= 0L) {
            return false;
        }
        long removed = steps * (long) SleepWellConfig.DROWSY_SLEEP_DECAY_SECONDS.get() * 20L;
        int left = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, drowsy.getDuration() - removed));
        int amplifier = drowsy.getAmplifier();
        boolean ambient = drowsy.isAmbient();
        boolean visible = drowsy.isVisible();
        boolean icon = drowsy.showIcon();
        maid.removeEffect(SleepWellEffects.DROWSY.get());
        if (left > 0) {
            maid.addEffect(new MobEffectInstance(SleepWellEffects.DROWSY.get(), left, amplifier, ambient,
                    visible, icon));
        }
        return true;
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
        /** Set when she got up at any point inside the window: kept for the log, no longer a bar to the skip. */
        private boolean wokeInWindow;
        /** She was in bed when the players skipped the night, so this sleep counts whatever the window says. */
        private boolean skippedNight;
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        Track track = TRACKS.computeIfAbsent(maid.getUUID(), key -> new Track());
        long now = level.getGameTime();
        // A nap forces the rest schedule onto her brain, so asking the schedule alone would let a ten-minute
        // afternoon nap open - and then close - her night window. That is what handed 困倦 to maids who had
        // just taken a nap. The nap is not a night: it neither opens nor closes the window.
        boolean napping = com.sleepwell.liein.LieInHandler.isNapping(maid);
        boolean rest = !napping && maid.getScheduleDetail() == Activity.REST;
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
                track.wokeInWindow = false;
            }
        } else if (!napping && track.inWindow && ++track.outOfWindow >= WINDOW_CLOSE_GRACE) {
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
        if (track.inWindow) {
            // A hole in the night: the skip at dawn must not patch over it.
            track.wokeInWindow = true;
        }
        track.regenTimer = 0;
        track.drowsyTimer = 0;
        long slept = now - track.sleepStart;
        boolean napSleep = napping;
        // A skipped night is a whole night: she was in bed for it, so the morning owes her 干劲 whatever the
        // window counters managed to add up before the clock jumped.
        // A nap that ran into the night skip settles like any other night sleep: she was in bed while the clock
        // jumped, so the morning pays her 干劲. Only a nap that ended on its own, in the middle of the day, is
        // settled as "neither paid nor punished".
        boolean enough = track.skippedNight || (!napSleep && track.sleptInWindow
                && track.windowSleepTicks >= MIN_WINDOW_SLEEP_TICKS
                && slept >= MIN_SLEEP_TICKS);
        // A nap is not a lie-in, even though it never opens the window: no extra minutes for it.
        boolean lieIn = !napSleep && !track.inWindow;
        if (enough && SleepWellConfig.VIGOR_ENABLED.get()) {
            track.judged = true;
            applyVigor(maid, lieIn);
        } else if (!enough && !napSleep && !track.skippedNight && track.closedWhileAsleep
                && SleepWellConfig.DROWSY_ENABLED.get()) {
            // Slept through most of the window but still too little: same verdict as never lying down.
            track.judged = true;
            applyDrowsy(maid, track);
        }
        log(maid, "起床", track, slept, enough, lieIn);
        track.windowSleepTicks = 0;
        track.sleptInWindow = false;
        track.closedWhileAsleep = false;
        track.skippedNight = false;
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

    /**
     * Puts an effect on her and <em>adds</em> the time to whatever is left of it, instead of restarting it.
     * <p>
     * Vanilla's {@code addEffect} only keeps the better of the two, so a short new buff onto a long running one
     * did nothing and a long new one threw the rest away. Stacking is what the owner asked for: two good nights in
     * a row should feel like two good nights.
     */
    private static void addStacked(EntityMaid maid, net.minecraft.world.effect.MobEffect effect, int ticks,
                                   int amplifier) {
        MobEffectInstance current = maid.getEffect(effect);
        int left = current == null ? 0 : Math.max(0, current.getDuration());
        int total = Math.min(MAX_STACK_TICKS, left + ticks);
        int level = Math.max(amplifier, current == null ? 0 : current.getAmplifier());
        if (current != null) {
            maid.removeEffect(effect);
        }
        maid.addEffect(new MobEffectInstance(effect, total, level, false, true, true));
    }

    private static void applyVigor(EntityMaid maid, boolean lieIn) {
        int seconds = SleepWellConfig.VIGOR_SECONDS.get()
                + (lieIn ? SleepWellConfig.VIGOR_LIE_IN_EXTRA_SECONDS.get() : 0);
        addStacked(maid, SleepWellEffects.VIGOR.get(), seconds * 20, 0);
        com.sleepwell.advancement.Achievements.award(maid,
                com.sleepwell.advancement.Achievements.VIGOR, 1);
    }

    /**
     * 困倦 duration by how much she actually slept: a maid who dozed for a few minutes gets the full debuff, one
     * who spent most of the window in bed shakes most of it off. The night that was skipped by the players counts
     * as slept time too, so a maid who slept through it wakes up nearly clear.
     */
    private static int drowsySeconds(Track track) {
        int base = SleepWellConfig.DROWSY_SECONDS.get();
        float share = Math.min(1.0F, Math.max(0.0F, track.windowSleepTicks / (float) FULL_NIGHT_TICKS));
        return Math.max(1, Math.round(base * (1.0F - DROWSY_SHORTEN_MAX * share)));
    }

    private static void applyDrowsy(EntityMaid maid, Track track) {
        int seconds = drowsySeconds(track);
        LOGGER.info("[睡个好觉] [困倦] maid={} 本次睡眠={}ticks 时长={}s（基准 {}s）",
                maid.getUUID(), track.windowSleepTicks, seconds, SleepWellConfig.DROWSY_SECONDS.get());
        addStacked(maid, SleepWellEffects.DROWSY.get(), seconds * 20, 0);
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
