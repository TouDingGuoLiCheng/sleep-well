package com.sleepwell.fire;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.sleep.SleepLandingSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Landing spots for the campfire flee.
 * <p>
 * Candidates are the standing spots of a disc around the maid, nearest first, but never closer than
 * {@link CampfireFleePolicy#MIN_FLEE_DIST} — running one block off the fire looks like nothing happened.
 * A candidate is only taken when its whole 3x3x3 neighbourhood is free of campfires, fire, magma and lava,
 * so she does not simply run into the next fire.
 */
public final class CampfireFleeSpot {
    private CampfireFleeSpot() {
    }

    /** Nearest safe standing spot within {@code radius} blocks, or empty when there is none. */
    public static Optional<Vec3> find(ServerLevel level, EntityMaid maid, int radius) {
        int r = Math.max(CampfireFleePolicy.MIN_RADIUS, radius);
        BlockPos origin = maid.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        double minSqr = CampfireFleePolicy.MIN_FLEE_DIST * CampfireFleePolicy.MIN_FLEE_DIST;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double distSqr = (double) dx * dx + (double) dz * dz;
                if (distSqr > (double) r * r || distSqr < minSqr) {
                    continue;
                }
                candidates.add(origin.offset(dx, 0, dz));
            }
        }
        candidates.sort(Comparator.comparingDouble(
                pos -> (double) (pos.getX() - origin.getX()) * (pos.getX() - origin.getX())
                        + (double) (pos.getZ() - origin.getZ()) * (pos.getZ() - origin.getZ())));
        for (BlockPos horizontal : candidates) {
            for (int dy : new int[]{0, 1, -1}) {
                BlockPos feet = SleepLandingSpot.groundBelow(level, horizontal.offset(0, dy, 0));
                if (feet == null || !SleepLandingSpot.canStandAt(level, maid, feet)) {
                    continue;
                }
                if (!isFreeOfFire(level, feet)) {
                    continue;
                }
                return Optional.of(new Vec3(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D));
            }
        }
        return Optional.empty();
    }

    /** The spot itself and its 3x3x3 neighbourhood must be free of everything that burns or hurts. */
    static boolean isFreeOfFire(ServerLevel level, BlockPos feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (isHazard(level, feet.offset(dx, dy, dz))) {
                        return false;
                    }
                }
            }
        }
        return !isHazard(level, feet) && !isHazard(level, feet.below());
    }

    static boolean isHazard(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CampfireBlock || state.getBlock() instanceof BaseFireBlock) {
            return true;
        }
        if (state.getBlock() == Blocks.MAGMA_BLOCK) {
            return true;
        }
        return state.getFluidState().is(FluidTags.LAVA);
    }
}
