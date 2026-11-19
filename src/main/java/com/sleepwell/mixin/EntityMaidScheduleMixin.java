package com.sleepwell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.liein.LieInHandler;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lie-in / nap, the only part that needs to touch TLM internals.
 * <p>
 * {@code getScheduleDetail()} is what TLM's own {@code MaidUpdateActivityFromSchedule} behaviour
 * copies into the brain every tick, and {@code MaidClearSleepTask} gets her out of bed as soon as
 * {@code Activity.WORK} is active. While a maid is lying in or napping we report {@code REST}
 * instead, so the work activity is never entered: she keeps sleeping, and for a nap the rest
 * activity's {@code MaidBedTask} walks her to the bed on its own.
 */
@Mixin(EntityMaid.class)
public abstract class EntityMaidScheduleMixin {
    @Inject(method = "getScheduleDetail", at = @At("HEAD"), cancellable = true, remap = false)
    private void sleepwell$lieInResting(CallbackInfoReturnable<Activity> cir) {
        if (LieInHandler.forcesRest((EntityMaid) (Object) this)) {
            cir.setReturnValue(Activity.REST);
        }
    }
}
