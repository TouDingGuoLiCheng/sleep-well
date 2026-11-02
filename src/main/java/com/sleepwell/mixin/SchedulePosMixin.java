package com.sleepwell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.config.subconfig.MaidConfig;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.SchedulePos;
import com.sleepwell.winter.WinterSkipHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Winter work skip, the only part that needs to touch TLM internals.
 * <p>
 * {@code SchedulePos#restrictTo(EntityMaid)} swaps the maid's restrict centre to the work point during
 * {@code Activity.WORK}; that centre is what the 40-tick teleport pulls her back to. While she is parked
 * for winter we cancel that branch and point her at the idle spot instead, so there is no work-point
 * teleport. The {@code IDLE} and {@code REST} branches are not touched at all.
 */
@Mixin(SchedulePos.class)
public abstract class SchedulePosMixin {
    @Inject(method = "restrictTo", at = @At("HEAD"), cancellable = true, remap = false)
    private void sleepwell$suppressWinterWorkPoint(EntityMaid maid, CallbackInfo ci) {
        if (maid.getScheduleDetail() != Activity.WORK || !WinterSkipHandler.shouldSuppressWorkPoint(maid)) {
            return;
        }
        SchedulePos self = (SchedulePos) (Object) this;
        // Rest spot, never the work point: idle when there is one, otherwise her sleep spot.
        BlockPos rest = self.getIdlePos() != null ? self.getIdlePos() : self.getSleepPos();
        if (rest != null) {
            // Same range TLM uses for the idle branch, so she is not yanked around by a stale radius.
            maid.restrictTo(rest, MaidConfig.MAID_IDLE_RANGE.get());
        }
        ci.cancel();
    }
}
