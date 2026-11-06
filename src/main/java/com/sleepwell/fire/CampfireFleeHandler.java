package com.sleepwell.fire;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Campfire flee: a maid that gets burned by a campfire runs a few blocks away from it instead of standing in
 * the flames until she dies.
 * <p>
 * Trigger: {@link LivingDamageEvent} on an {@code EntityMaid} with a fire-typed source while a lit campfire is
 * in her feet column (vanilla campfires deal plain {@code inFire} damage, so the block has to confirm it).
 * Response: pick a safe standing spot 3..{@code campfireFleeRadius} blocks away, then keep re-issuing that walk
 * target for up to 5 seconds — the maid's own task may overwrite it every tick, so we push it back every tick
 * and also drive the navigation directly.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class CampfireFleeHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/campfireFlee");
    /** A bit above a walking maid so the escape reads as running. */
    private static final float FLEE_SPEED = 1.3F;
    /** Let the vanilla walk-target sink stop on the neighbouring block instead of the exact spot. */
    private static final int CLOSE_ENOUGH = 1;
    /** Per-maid flee state; dropped when the flee ends or the maid disappears. */
    private static final Map<UUID, Flee> FLEES = new ConcurrentHashMap<>();
    /** Debug lines used to be invisible (log level is INFO), which is why "she stands in the fire" took a
     *  code read to even confirm the handler ran. These counters keep the report honest and quiet. */
    private static int DAMAGE_LOGS;
    private static int DRIVE_LOGS;

    private CampfireFleeHandler() {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        boolean enabled = SleepWellConfig.CAMPFIRE_FLEE_ENABLED.get();
        boolean fireDamage = event.getSource().is(DamageTypeTags.IS_FIRE);
        boolean standingOnCampfire = onLitCampfire(level, maid.blockPosition());
        if (DAMAGE_LOGS++ < 6) {
            LOGGER.info("[篝火] 受伤事件 maid={} 伤害={} 火属性={} 站在篝火上={} 开关={} 睡={} 载具={} 位置={}",
                    maid.getUUID().toString().substring(0, 8), event.getAmount(), fireDamage, standingOnCampfire,
                    enabled, maid.isSleeping(), maid.isPassenger() || maid.isVehicle(), maid.blockPosition());
        }
        if (!CampfireFleePolicy.eligible(enabled, maid.isAlive(), maid.isSleeping(),
                maid.isPassenger() || maid.isVehicle(), fireDamage, standingOnCampfire)) {
            if (!enabled) {
                FLEES.remove(maid.getUUID());
            }
            return;
        }
        int radius = CampfireFleePolicy.clampRadius(SleepWellConfig.CAMPFIRE_FLEE_RADIUS.get());
        Flee flee = FLEES.computeIfAbsent(maid.getUUID(), key -> new Flee(maid, radius));
        flee.radius = radius;
        flee.ticksLeft = CampfireFleePolicy.FLEE_TICKS;
        flee.ticksOnTarget = 0;
        flee.stuckTicks = 0;
        flee.startX = maid.getX();
        flee.startY = maid.getY();
        flee.startZ = maid.getZ();
        flee.target = CampfireFleeSpot.find(level, maid, radius).orElse(null);
        if (flee.target == null) {
            FLEES.remove(maid.getUUID());
            LOGGER.warn("[篝火] 找不到安全落点 maid={} 位置={} 半径={}", shortId(maid), maid.blockPosition(), radius);
            return;
        }
        if (DRIVE_LOGS++ < 6) {
            LOGGER.info("[篝火] 开始逃跑 maid={} 从={} 跑向={} 半径={}", shortId(maid), maid.blockPosition(),
                    new BlockPos((int) Math.floor(flee.target.x), (int) Math.floor(flee.target.y),
                            (int) Math.floor(flee.target.z)), radius);
        }
        drive(maid, flee.target);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        Flee flee = FLEES.get(maid.getUUID());
        if (flee == null) {
            return;
        }
        if (maid.isRemoved() || !maid.isAlive() || maid.isSleeping()
                || !(maid.level() instanceof ServerLevel level)
                || !SleepWellConfig.CAMPFIRE_FLEE_ENABLED.get()
                || flee.target == null) {
            stop(maid);
            return;
        }
        if (CampfireFleePolicy.arrived(maid.position().distanceToSqr(flee.target))) {
            stop(maid);
            return;
        }
        // The brain's MoveToTargetSink owns the navigator and rewrites the walk target later in the same tick,
        // so the path we issue can be ignored outright. If she has not actually moved, carrying her out is the
        // only thing that keeps her out of the flames.
        if (maid.position().distanceToSqr(flee.startX, flee.startY, flee.startZ) < 0.25D) {
            if (++flee.stuckTicks > CampfireFleePolicy.STUCK_TICKS) {
                LOGGER.warn("[篝火] 寻路无效，直接把女仆移出火堆 maid={} 从={} 到={}", shortId(maid),
                        maid.blockPosition(), flee.target);
                maid.teleportTo(flee.target.x, flee.target.y, flee.target.z);
                maid.getNavigation().stop();
                stop(maid);
                return;
            }
        } else {
            flee.stuckTicks = 0;
        }
        if (--flee.ticksLeft <= 0) {
            stop(maid);
            return;
        }
        if (CampfireFleePolicy.needsRetarget(++flee.ticksOnTarget)) {
            flee.ticksOnTarget = 0;
            Vec3 next = CampfireFleeSpot.find(level, maid, flee.radius).orElse(null);
            if (next == null) {
                stop(maid);
                return;
            }
            flee.target = next;
        }
        drive(maid, flee.target);
    }

    private static String shortId(EntityMaid maid) {
        return maid.getUUID().toString().substring(0, 8);
    }

    /** Push the walk target back and steer the navigation directly, so a busy task cannot keep her in the fire. */
    private static void drive(EntityMaid maid, Vec3 target) {
        maid.getNavigation().moveTo(target.x, target.y, target.z, FLEE_SPEED);
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(BlockPos.containing(target)), FLEE_SPEED, CLOSE_ENOUGH));
        maid.getLookControl().setLookAt(target.x, target.y, target.z);
    }

    private static void stop(EntityMaid maid) {
        FLEES.remove(maid.getUUID());
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    /** A lit campfire in the maid's feet column: the block she is inside, or the one right under her. */
    static boolean onLitCampfire(ServerLevel level, BlockPos feet) {
        return isLitCampfire(level, feet) || isLitCampfire(level, feet.below());
    }

    private static boolean isLitCampfire(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CampfireBlock;
    }

    /** Mutable per-maid flee state. */
    private static final class Flee {
        private final EntityMaid maid;
        private int radius;
        private int ticksLeft;
        private int ticksOnTarget;
        private int stuckTicks;
        private double startX;
        private double startY;
        private double startZ;
        private Vec3 target;

        private Flee(EntityMaid maid, int radius) {
            this.maid = maid;
            this.radius = radius;
        }
    }
}
