package com.sleepwell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Names whoever put the maid out of bed.
 * <p>
 * "She climbed in and got straight out again" looks the same from every angle: TLM's own task clearing the sleep
 * task, the schedule flipping away from rest, a vanilla bed check, one of our teleports. This hook prints the
 * call stack at {@code LivingEntity#stopSleeping}, so the cause is read rather than guessed.
 * <p>
 * Diagnostic only: {@code require = 0} keeps a mapping change from breaking the launch, and returning early for
 * everything that is not a maid keeps the cost to one instanceof.
 */
@Mixin(LivingEntity.class)
public abstract class MaidStopSleepingTraceMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/wake");
    /** Deep enough to leave vanilla and reach the behaviour that decided to wake her. */
    private static final int FRAMES = 12;

    @Inject(method = "stopSleeping", at = @At("HEAD"), require = 0)
    private void sleepwell$traceWake(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }
        StackTraceElement[] trace = new Throwable().getStackTrace();
        StringBuilder out = new StringBuilder("[诊断] 起床调用栈 maid=")
                .append(maid.getUUID().toString(), 0, 8)
                .append(" 日程=").append(maid.getScheduleDetail().getName());
        for (int i = 1; i < trace.length && i <= FRAMES; i++) {
            StackTraceElement frame = trace[i];
            out.append("\n    ").append(frame.getClassName()).append('.').append(frame.getMethodName())
                    .append('(').append(frame.getFileName()).append(':').append(frame.getLineNumber()).append(')');
        }
        LOGGER.info(out.toString());
    }
}
