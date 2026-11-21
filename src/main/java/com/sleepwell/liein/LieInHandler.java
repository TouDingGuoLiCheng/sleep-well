package com.sleepwell.liein;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.maid.MaidBubble;
import com.sleepwell.maid.MaidCapabilities;
import com.sleepwell.winter.SeasonBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lie-in and daytime naps.
 * <p>
 * The whole trick is one mixin ({@code EntityMaidScheduleMixin}): while a maid is in this state,
 * {@code EntityMaid#getScheduleDetail()} reports {@code Activity.REST}. TLM's own
 * {@code MaidUpdateActivityFromSchedule} then keeps her brain on the rest activity, so
 * {@code MaidClearSleepTask} (which wakes her once {@code Activity.WORK} is active) never fires and
 * {@code MaidBedTask} walks her back to bed all by itself.
 * <p>
 * Naps only ever start when a free maid bed is already within {@link MaidBedFinder#RADIUS} blocks, so
 * she never gets dragged to a "sleep spot" that has nothing to lie down on; while a nap is running the
 * schedule teleports are suppressed as well, so she is not thrown around mid-walk.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class LieInHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/lieIn");
    private static final long DAY_LENGTH = 24000L;
    /** Guards against a stale entry keeping a maid in bed forever. */
    private static final long MAX_STATE_TICKS = 24000L;
    private static final Map<UUID, LieInState> STATES = new ConcurrentHashMap<>();
    /**
     * A schedule whose only keyframe is {@code REST} at 0: while a lie-in or a nap runs, the brain runs on this
     * one. Swapping the brain's schedule (instead of only faking {@code getScheduleDetail()}) is what keeps the
     * rest activity's own tasks — including {@code MaidBedTask}, the only thing that actually puts her to bed —
     * in charge.
     */
    private static final Schedule FORCED_REST = new ScheduleBuilder(Schedule.EMPTY).changeActivityAt(0, Activity.REST).build();

    private LieInHandler() {
    }

    /** Used by the mixin: she is in the middle of a lie-in or a nap, so report REST. */
    public static boolean forcesRest(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return false;
        }
        LieInState state = STATES.get(maid.getUUID());
        return state != null && state.active();
    }

    public static boolean isNapping(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return false;
        }
        LieInState state = STATES.get(maid.getUUID());
        return state != null && state.kind() == LieInState.Kind.NAP;
    }

    /**
     * A nap towards a bed she can already see must not be fought over: the schedule teleports would drag her
     * back to the idle spot while she is walking. A nap towards her configured sleep area is the opposite —
     * there the normal pathing (and the fallback teleport) is exactly what gets her there.
     */
    public static boolean suppressesScheduleChase(EntityMaid maid) {
        if (maid == null || maid.level().isClientSide()) {
            return false;
        }
        LieInState state = STATES.get(maid.getUUID());
        return state != null && state.kind() == LieInState.Kind.NAP && !state.configuredSleepArea();
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof EntityMaid maid)
                || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = maid.getUUID();
        if (maid.isRemoved() || !maid.isAlive()) {
            STATES.remove(id);
            return;
        }
        // Her own switch first: "allowed" is the default, the page can turn it off per maid.
        if (!MaidCapabilities.of(maid).lieIn()) {
            release(maid, STATES.remove(id), "switch off");
            return;
        }
        if (!lieInEnabled() && !napEnabled()) {
            release(maid, STATES.remove(id), "config off");
            return;
        }

        long now = level.getGameTime();
        LieInState state = STATES.computeIfAbsent(id, key -> new LieInState());
        String idleUid = idleTaskUid();
        String taskUid = taskUid(maid);
        Activity activity = maid.getScheduleDetail();
        // While a nap runs the brain is held on our own REST schedule, so the schedule she reports is the one
        // we forced, not hers. Reading it as "the night has arrived" released every nap on its second tick,
        // which is the maid who climbs into bed and stands straight back up.
        boolean forcedRest = maid.getBrain().getSchedule() == FORCED_REST;
        boolean resting = !forcedRest && activity == Activity.REST;
        logTransition(id, state, activity, maid.isSleeping());

        if (state.active()) {
            // Night sleep belongs to the schedule, never to a nap: if REST arrives while she is napping,
            // hand her over so she sleeps through the night instead of waking up "refreshed".
            if (state.kind() == LieInState.Kind.NAP && resting) {
                release(maid, state, "schedule rest");
                return;
            }
            holdBrain(maid, state);
            MaidBubble.tick(maid, state);
            keepOrEnd(maid, state, now, taskUid);
            return;
        }

        if (state.until() > 0L && now - state.started() > MAX_STATE_TICKS) {
            // Dropped without restoring, this left her brain stuck on our own REST schedule for the rest of
            // the session: every later nap then looked like "the night has arrived" and ended on its next tick.
            release(maid, state, "stale state");
        }

        long day = level.getDayTime() / DAY_LENGTH;
        boolean winter = SeasonBridge.isWinter(level);

        // Rest time never rolls: she is only allowed to sleep in once the schedule has left REST.
        if (lieInEnabled() && LieInPolicy.needsWakeRoll(false, state.rolledToday(day), maid.isSleeping(), resting)) {
            int chance = LieInPolicy.chancePercent(winter,
                    SleepWellConfig.LIE_IN_CHANCE_PERCENT.get(), SleepWellConfig.LIE_IN_WINTER_CHANCE_PERCENT.get());
            int roll = maid.getRandom().nextInt(100);
            boolean hit = LieInPolicy.roll(chance, roll);
            state.markRolled(day);
            LOGGER.info("Lie-in roll: maid={} day={} activity={} sleeping={} winter={} chance={}% roll={} hit={}",
                    id, day, activity.getName(), maid.isSleeping(), winter, chance, roll, hit);
            if (hit) {
                long ticks = LieInPolicy.durationTicks(SleepWellConfig.LIE_IN_MIN_HOURS.get(),
                        SleepWellConfig.LIE_IN_MAX_HOURS.get(), maid.getRandom().nextInt(1000));
                state.begin(LieInState.Kind.LIE_IN, now, ticks, taskUid);
                com.sleepwell.advancement.Achievements.award(maid,
                        com.sleepwell.advancement.Achievements.LIE_IN, 1);
                LOGGER.info("Lie-in: maid {} stays in bed for {} ticks (until {})", id, ticks, now + ticks);
            }
            return;
        }

        boolean idle = LieInPolicy.idleMode(taskUid, idleUid);
        // Naps are a work-time thing only. Rolling them during REST is exactly what woke maids up at
        // nightfall after they had already napped in the afternoon.
        if (napEnabled() && idle && !maid.isSleeping() && activity == Activity.WORK) {
            if (state.nextNapCheck() == 0L) {
                state.setNextNapCheck(now + LieInPolicy.napCheckIntervalTicks(SleepWellConfig.NAP_CHECK_MINUTES.get()));
                return;
            }
            if (!LieInPolicy.napDue(true, false, true, false, false, now, state.nextNapCheck())) {
                return;
            }
            state.setNextNapCheck(now + LieInPolicy.napCheckIntervalTicks(SleepWellConfig.NAP_CHECK_MINUTES.get()));
            boolean configuredArea = configuredSleepArea(maid, level);
            // A kappa-compass sleep area is a destination in its own right: she paths there and lies down.
            // Without one she may only nap towards a bed she can see, otherwise she would just stand at the
            // sleep spot while the schedule shoves her back and forth.
            if (!configuredArea && MaidBedFinder.find(maid).isEmpty()) {
                return;
            }
            int chance = LieInPolicy.chancePercent(winter,
                    SleepWellConfig.NAP_CHANCE_PERCENT.get(), SleepWellConfig.NAP_WINTER_CHANCE_PERCENT.get());
            int napRoll = maid.getRandom().nextInt(100);
            boolean napHit = LieInPolicy.roll(chance, napRoll);
            LOGGER.info("Nap roll: maid={} winter={} chance={}% roll={} hit={}", id, winter, chance, napRoll, napHit);
            if (napHit) {
                long ticks = LieInPolicy.durationTicks(SleepWellConfig.NAP_MIN_HOURS.get(),
                        SleepWellConfig.NAP_MAX_HOURS.get(), maid.getRandom().nextInt(1000));
                state.begin(LieInState.Kind.NAP, now, ticks, taskUid, configuredArea);
                com.sleepwell.advancement.Achievements.award(maid,
                        com.sleepwell.advancement.Achievements.NAP, 1);
                // A nap that ends after dawn must not chain straight into a lie-in.
                state.markRolled(day);
                LOGGER.info("Nap: maid {} heads to her {} for {} ticks (until {})", id,
                        configuredArea ? "configured sleep area" : "nearest maid bed", ticks, now + ticks);
            }
        }
    }

    private static void keepOrEnd(EntityMaid maid, LieInState state, long now, String taskUid) {
        boolean kindLieIn = state.kind() == LieInState.Kind.LIE_IN;
        boolean napping = state.kind() == LieInState.Kind.NAP;
        boolean taskChanged = !state.taskUid().equals(taskUid);
        boolean expired = LieInPolicy.expired(now, state.until());
        long giveUpTicks = LieInPolicy.napGiveUpTicks(state.configuredSleepArea());
        boolean gaveUp = LieInPolicy.napGaveUp(napping, maid.isSleeping(), now, state.started(), giveUpTicks);
        boolean lieInLostBed = kindLieIn && !maid.isSleeping() && now - state.started() > 40L;

        if (!taskChanged && !expired && !gaveUp && !lieInLostBed) {
            return;
        }
        MaidBubble.forget(maid.getUUID());
        release(maid, state, "back up");
        LOGGER.info("Lie-in: maid {} back up (task changed={}, expired={}, gaveUp={}, lostBed={})",
                maid.getUUID(), taskChanged, expired, gaveUp, lieInLostBed);
    }

    /** One log line when the schedule takes her out of REST: that is the moment the roll looks for. */
    private static void logTransition(UUID id, LieInState state, Activity activity, boolean sleeping) {
        String name = activity == null ? "" : activity.getName();
        String previous = state.lastActivity();
        if (name.equals(previous)) {
            return;
        }
        if (Activity.REST.getName().equals(previous)) {
            LOGGER.info("Maid {}: schedule REST -> {} (the wake-up moment), sleeping={}", id, name, sleeping);
        }
        state.setLastActivity(name);
    }

    /** Being hurt wakes her right away. */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        LieInState state = STATES.get(maid.getUUID());
        if (state != null && state.active()) {
            release(maid, state, "hurt");
        }
    }

    /** The owner calling her (right-click) ends it too. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        if (player == null || !maid.isOwnedBy(player)) {
            return;
        }
        LieInState state = STATES.get(maid.getUUID());
        if (state != null && state.active()) {
            release(maid, state, "called");
        }
    }

    /** Result of {@link #triggerNapNow(EntityMaid)} so the command can say what happened. */
    public enum NapResult {
        STARTED,
        ALREADY_SLEEPING,
        SWITCH_OFF,
        NO_BED
    }

    /**
     * Force a nap right now, used by {@code /sleepwell nap}.
     * <p>
     * Same path as the random nap: the brain is held on the rest schedule and TLM's own bed task walks her
     * there. Differences: no probability roll, and she does not have to be on the idle task.
     */
    public static NapResult triggerNapNow(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return NapResult.NO_BED;
        }
        LieInState state = STATES.computeIfAbsent(maid.getUUID(), key -> new LieInState());
        if (state.active() || maid.isSleeping()) {
            return NapResult.ALREADY_SLEEPING;
        }
        if (!MaidCapabilities.of(maid).lieIn() || !napEnabled()) {
            return NapResult.SWITCH_OFF;
        }
        boolean configuredArea = configuredSleepArea(maid, level);
        if (!configuredArea && MaidBedFinder.find(maid).isEmpty()) {
            return NapResult.NO_BED;
        }
        long now = level.getGameTime();
        long ticks = LieInPolicy.durationTicks(SleepWellConfig.NAP_MIN_HOURS.get(),
                SleepWellConfig.NAP_MAX_HOURS.get(), maid.getRandom().nextInt(1000));
        state.begin(LieInState.Kind.NAP, now, ticks, taskUid(maid), configuredArea);
        state.markRolled(level.getDayTime() / DAY_LENGTH);
        holdBrain(maid, state);
        LOGGER.info("Nap: maid {} sent to her {} by command for {} ticks",
                maid.getUUID(), configuredArea ? "configured sleep area" : "nearest maid bed", ticks);
        return NapResult.STARTED;
    }

    /** Keep the brain on the forced-rest schedule for as long as the state lasts. */
    private static void holdBrain(EntityMaid maid, LieInState state) {
        Brain<?> brain = maid.getBrain();
        Schedule current = brain.getSchedule();
        if (state.originalSchedule() == null && current != FORCED_REST) {
            state.setOriginalSchedule(current);
        }
        if (current != FORCED_REST) {
            brain.setSchedule(FORCED_REST);
            LOGGER.info("Lie-in: maid {} brain schedule -> REST (was {})", maid.getUUID(), current);
        }
    }

    /** Put her own schedule back before the state is dropped, otherwise she stays idle forever. */
    private static void release(EntityMaid maid, LieInState state, String reason) {
        if (state == null) {
            return;
        }
        Schedule original = state.originalSchedule();
        if (original == null && maid.getBrain().getSchedule() == FORCED_REST) {
            // Nothing recorded, and she is still on our schedule: say so, because guessing a replacement
            // would silently rewrite her real schedule.
            LOGGER.warn("Lie-in: maid {} dropped while held on REST with no original schedule ({})",
                    maid.getUUID(), reason);
        }
        if (original != null) {
            maid.getBrain().setSchedule(original);
            LOGGER.info("Lie-in: maid {} brain schedule restored ({})", maid.getUUID(), reason);
        }
        state.clear();
    }

    /** A kappa-compass sleep point in this dimension: she has somewhere to go, bed check not needed. */
    private static boolean configuredSleepArea(EntityMaid maid, ServerLevel level) {
        SchedulePos pos = maid.getSchedulePos();
        return pos != null && pos.isConfigured() && pos.getSleepPos() != null
                && level.dimension().location().equals(pos.getDimension());
    }

    private static boolean lieInEnabled() {
        return SleepWellConfig.LIE_IN_ENABLED.get();
    }

    private static boolean napEnabled() {
        return SleepWellConfig.NAP_ENABLED.get() && SleepWellConfig.LIE_IN_ENABLED.get();
    }

    private static String taskUid(EntityMaid maid) {
        IMaidTask task = maid.getTask();
        return task == null ? "" : task.getUid().toString();
    }

    private static String idleTaskUid() {
        return TaskManager.getIdleTask().getUid().toString();
    }
}
