package com.sleepwell.liein;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.winter.SeasonBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
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
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class LieInHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/lieIn");
    private static final long DAY_LENGTH = 24000L;
    /** Guards against a stale entry keeping a maid in bed forever. */
    private static final long MAX_STATE_TICKS = 24000L;
    private static final Map<UUID, LieInState> STATES = new ConcurrentHashMap<>();

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
        if (!lieInEnabled() && !napEnabled()) {
            STATES.remove(id);
            return;
        }

        long now = level.getGameTime();
        LieInState state = STATES.computeIfAbsent(id, key -> new LieInState());
        String idleUid = idleTaskUid();
        String taskUid = taskUid(maid);
        boolean resting = maid.getScheduleDetail() == Activity.REST;

        if (state.active()) {
            keepOrEnd(maid, state, now, taskUid);
            return;
        }

        if (state.until() > 0L && now - state.started() > MAX_STATE_TICKS) {
            state.clear();
        }

        long day = level.getDayTime() / DAY_LENGTH;
        boolean winter = SeasonBridge.isWinter(level);

        if (lieInEnabled() && LieInPolicy.needsWakeRoll(false, state.rolledToday(day), maid.isSleeping(), resting)) {
            state.markRolled(day);
            int chance = LieInPolicy.chancePercent(winter,
                    SleepWellConfig.LIE_IN_CHANCE_PERCENT.get(), SleepWellConfig.LIE_IN_WINTER_CHANCE_PERCENT.get());
            if (LieInPolicy.roll(chance, maid.getRandom().nextInt(100))) {
                long ticks = LieInPolicy.durationTicks(SleepWellConfig.LIE_IN_MIN_HOURS.get(),
                        SleepWellConfig.LIE_IN_MAX_HOURS.get(), maid.getRandom().nextInt(1000));
                state.begin(LieInState.Kind.LIE_IN, now, ticks, taskUid);
                LOGGER.debug("Lie-in: maid {} stays in bed for {} ticks", id, ticks);
            }
            return;
        }

        boolean idle = LieInPolicy.idleMode(taskUid, idleUid);
        if (napEnabled() && idle && !maid.isSleeping() && !resting) {
            if (state.nextNapCheck() == 0L) {
                state.setNextNapCheck(now + LieInPolicy.napCheckIntervalTicks(SleepWellConfig.NAP_CHECK_MINUTES.get()));
                return;
            }
            boolean due = LieInPolicy.napDue(true, false, true, false, false, now, state.nextNapCheck());
            if (!due) {
                return;
            }
            state.setNextNapCheck(now + LieInPolicy.napCheckIntervalTicks(SleepWellConfig.NAP_CHECK_MINUTES.get()));
            int chance = LieInPolicy.chancePercent(winter,
                    SleepWellConfig.NAP_CHANCE_PERCENT.get(), SleepWellConfig.NAP_WINTER_CHANCE_PERCENT.get());
            if (LieInPolicy.roll(chance, maid.getRandom().nextInt(100))) {
                long ticks = LieInPolicy.durationTicks(SleepWellConfig.NAP_MIN_HOURS.get(),
                        SleepWellConfig.NAP_MAX_HOURS.get(), maid.getRandom().nextInt(1000));
                state.begin(LieInState.Kind.NAP, now, ticks, taskUid);
                LOGGER.debug("Nap: maid {} heads back to bed for {} ticks", id, ticks);
            }
        }
    }

    private static void keepOrEnd(EntityMaid maid, LieInState state, long now, String taskUid) {
        boolean kindLieIn = state.kind() == LieInState.Kind.LIE_IN;
        boolean napping = state.kind() == LieInState.Kind.NAP;
        boolean taskChanged = !state.taskUid().equals(taskUid);
        boolean expired = LieInPolicy.expired(now, state.until());
        boolean gaveUp = LieInPolicy.napGaveUp(napping, maid.isSleeping(), now, state.started());
        boolean lieInLostBed = kindLieIn && !maid.isSleeping() && now - state.started() > 40L;

        if (!taskChanged && !expired && !gaveUp && !lieInLostBed) {
            return;
        }
        state.clear();
        LOGGER.debug("Lie-in: maid {} back up (task changed={}, expired={}, gaveUp={}, lostBed={})",
                maid.getUUID(), taskChanged, expired, gaveUp, lieInLostBed);
    }

    /** Being hurt wakes her right away. */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        LieInState state = STATES.get(maid.getUUID());
        if (state != null && state.active()) {
            state.clear();
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
            state.clear();
        }
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
