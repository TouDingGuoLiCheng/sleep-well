package com.sleepwell.liein;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * "Is there a bed she could actually lie down on right now?"
 * <p>
 * Forcing the rest activity only gets her to the rest spot; lying down is TLM's {@code MaidBedTask}
 * finding a maid bed. Without one she just stands there, so naps are only rolled when this finds a
 * free bed nearby — no bed, no nap, and no being teleported across the base for nothing.
 */
public final class MaidBedFinder {
    /** Blocks searched around her in every direction horizontally. */
    public static final int RADIUS = 16;
    private static final int VERTICAL = 4;

    private MaidBedFinder() {
    }

    public static Optional<BlockPos> find(EntityMaid maid) {
        if (!(maid.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        BlockPos center = maid.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -VERTICAL; dy <= VERTICAL; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!(state.getBlock() instanceof BlockMaidBed) || state.getValue(BlockMaidBed.OCCUPIED)) {
                        continue;
                    }
                    return Optional.of(cursor.immutable());
                }
            }
        }
        return Optional.empty();
    }
}
