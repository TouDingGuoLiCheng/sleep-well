package com.sleepwell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidClearSleepTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.liein.LieInHandler;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Second half of the lie-in hook.
 * <p>
 * {@code MaidClearSleepTask} is what actually gets her out of bed: as soon as the brain is on
 * {@code Activity.WORK} it calls {@code stopSleeping()}. Forcing the brain schedule to rest covers that,
 * but the activity can already be {@code WORK} on the one tick a lie-in starts, so the wake-up itself is
 * cancelled as well. {@code start} is TLM's own method (not a vanilla override), hence {@code remap = false}.
 */
@Mixin(MaidClearSleepTask.class)
public abstract class MaidClearSleepTaskMixin {
    @Inject(method = "start", at = @At("HEAD"), cancellable = true, remap = false)
    private void sleepwell$keepSleeping(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        if (LieInHandler.forcesRest(maid)) {
            ci.cancel();
        }
    }
}
