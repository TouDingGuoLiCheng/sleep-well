package com.sleepwell.sleep;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Landing spots for the sleep fallback.
 * <p>
 * Vanilla first, exactly once: {@code TeleportHelper.teleportToRestrictCenter} already does the
 * "restrict center +-3, then drop to the ground" dance. {@code SchedulePos.teleport} wraps that call
 * in a 10-attempt loop, which we deliberately do <b>not</b> reuse: ten random spots are ten chances
 * to drop a maid into a wall or onto a roof. One attempt, then our own search around the sleep point.
 */
public final class SleepLandingSpot {
    /** Horizontal search radius around the sleep point, in blocks. */
    static final int HORIZONTAL_SPREAD = 2;
    /** How far below the sleep point we are willing to look for solid ground. */
    static final int MAX_DROP = 4;

    private SleepLandingSpot() {
    }

    /** Vanilla safe landing, single attempt. Returns whether the maid was moved. */
    public static boolean teleportVanillaOnce(EntityMaid maid) {
        return TeleportHelper.teleportToRestrictCenter(maid);
    }

    /**
     * Fallback search: nearest free standing spot around {@code sleepPos}.
     * Returns the entity position to teleport to (block center, feet level) or empty when nothing is safe.
     */
    public static Optional<Vec3> find(ServerLevel level, EntityMaid maid, BlockPos sleepPos) {
        for (BlockPos horizontal : horizontalCandidates(sleepPos)) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos feet = groundBelow(level, horizontal.offset(0, dy, 0));
                if (feet != null && canStandAt(level, maid, feet)) {
                    return Optional.of(new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D));
                }
            }
        }
        return Optional.empty();
    }

    /** Sleep point first, then a deterministic nearest-first spiral of its neighbourhood. */
    static List<BlockPos> horizontalCandidates(BlockPos sleepPos) {
        List<BlockPos> candidates = new ArrayList<>((2 * HORIZONTAL_SPREAD + 1) * (2 * HORIZONTAL_SPREAD + 1));
        for (int dx = -HORIZONTAL_SPREAD; dx <= HORIZONTAL_SPREAD; dx++) {
            for (int dz = -HORIZONTAL_SPREAD; dz <= HORIZONTAL_SPREAD; dz++) {
                candidates.add(sleepPos.offset(dx, 0, dz));
            }
        }
        candidates.sort(Comparator.comparingInt(pos -> Math.abs(pos.getX() - sleepPos.getX()) + Math.abs(pos.getZ() - sleepPos.getZ())));
        return candidates;
    }

    /**
     * Walks down from {@code start} while the block is not something a maid can stand in, until solid
     * ground is reached. Returns null when nothing within {@link #MAX_DROP} blocks qualifies.
     */
    public static BlockPos groundBelow(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        for (int drop = 0; drop <= MAX_DROP; drop++) {
            if (cursor.getY() <= level.getMinBuildHeight()) {
                return null;
            }
            if (hasSupport(level, cursor) && level.isEmptyBlock(cursor)) {
                return cursor.immutable();
            }
            if (!level.isEmptyBlock(cursor) && !hasSupport(level, cursor)) {
                // Solid, but not standing ground we can use (e.g. a wall): nothing below will help.
                return null;
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }

    public static boolean canStandAt(ServerLevel level, EntityMaid maid, BlockPos feet) {
        if (!level.isEmptyBlock(feet) || !level.isEmptyBlock(feet.above())) {
            return false;
        }
        if (!hasSupport(level, feet)) {
            return false;
        }
        AABB box = maid.getBoundingBox().move(
                feet.getX() + 0.5D - maid.getX(),
                feet.getY() - maid.getY(),
                feet.getZ() + 0.5D - maid.getZ());
        return level.noCollision(maid, box);
    }

    private static boolean hasSupport(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        if (below.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        return below.isSolid();
    }
}
