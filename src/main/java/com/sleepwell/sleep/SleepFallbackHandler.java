package com.sleepwell.sleep;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sleep-stage fallback teleport.
 * <p>
 * Vanilla {@code SchedulePos.tick} teleports a maid only when she is outside {@code (restrictRadius + 4)}
 * <b>and</b> is not already walking to the restrict center, so a maid stuck on a ladder inside her sleep
 * area retries forever. Here we watch the distance to the kappa-compass sleep point; once it stops
 * improving for the configured window, we move her: vanilla safe landing first (one attempt), our own
 * search around the sleep point second, otherwise nothing.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class SleepFallbackHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/sleepFallback");
    /** Per-maid progress state; dropped as soon as the maid is removed or the switch is off. */
    private static final Map<UUID, SleepFallbackTracker> TRACKERS = new ConcurrentHashMap<>();

    private SleepFallbackHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = maid.getUUID();
        if (maid.isRemoved() || !maid.isAlive()) {
            TRACKERS.remove(id);
            return;
        }
        if (!SleepWellConfig.SLEEP_FALLBACK_ENABLED.get()) {
            TRACKERS.remove(id);
            return;
        }
        int maxTeleports = SleepFallbackPolicy.clampMaxTeleports(SleepWellConfig.SLEEP_FALLBACK_MAX_TELEPORTS.get());
        if (maxTeleports <= 0) {
            TRACKERS.remove(id);
            return;
        }

        boolean resting = maid.getScheduleDetail() == Activity.REST;
        boolean sleeping = maid.isSleeping();
        SchedulePos schedulePos = maid.getSchedulePos();
        BlockPos sleepPos = schedulePos.getSleepPos();
        boolean configured = schedulePos.isConfigured() && sleepPos != null;
        boolean sameDimension = configured && level.dimension().location().equals(schedulePos.getDimension());
        boolean inVehicle = maid.isPassenger() || maid.isVehicle();
        double distSqr = configured && sameDimension
                ? Vec3.atCenterOf(sleepPos).distanceToSqr(maid.position())
                : Double.MAX_VALUE;

        SleepFallbackTracker tracker = TRACKERS.computeIfAbsent(id, key -> new SleepFallbackTracker());
        tracker.tick(resting, distSqr);
        if (sleeping) {
            tracker.onFellAsleep();
            return;
        }

        int samplesNeeded = SleepFallbackPolicy.samplesNeeded(SleepWellConfig.SLEEP_FALLBACK_SECONDS.get());
        if (!SleepFallbackPolicy.eligible(true, resting, false, inVehicle, configured, configured, sameDimension,
                distSqr, tracker.teleportsThisNap(), maxTeleports, tracker.ticksSinceLastTeleport())) {
            return;
        }
        if (!SleepFallbackPolicy.stuck(tracker.stalledSamples(), samplesNeeded)) {
            return;
        }
        if (!teleport(level, maid, sleepPos)) {
            return;
        }
        tracker.onTeleported();
    }

    /**
     * Vanilla safe landing first, exactly once; then our own search around the sleep point.
     *
     * @return whether the maid was moved
     */
    private static boolean teleport(ServerLevel level, EntityMaid maid, BlockPos sleepPos) {
        if (SleepLandingSpot.teleportVanillaOnce(maid)) {
            clearPathing(maid);
            if (!maid.getRestrictCenter().equals(sleepPos)) {
                LOGGER.debug("Sleep fallback: moved maid {} to restrict center {} (sleep point {} differs; home mode off?)",
                        maid.getUUID(), maid.getRestrictCenter(), sleepPos);
            } else {
                LOGGER.debug("Sleep fallback: moved maid {} to vanilla landing near {}", maid.getUUID(), sleepPos);
            }
            return true;
        }
        Optional<Vec3> landing = SleepLandingSpot.find(level, maid, sleepPos);
        if (landing.isEmpty()) {
            LOGGER.debug("Sleep fallback: no safe landing for maid {} around {}", maid.getUUID(), sleepPos);
            return false;
        }
        Vec3 target = landing.get();
        maid.teleportTo(target.x, target.y, target.z);
        maid.setDeltaMovement(0.0D, 0.0D, 0.0D);
        clearPathing(maid);
        LOGGER.debug("Sleep fallback: moved maid {} to searched landing {}", maid.getUUID(), target);
        return true;
    }

    /** Let {@code MaidBedTask} re-issue its walk target / bed snap on the next check. */
    private static void clearPathing(EntityMaid maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }
}
