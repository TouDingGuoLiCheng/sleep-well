package com.sleepwell.winter;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.sleepwell.SleepWellMod;
import com.sleepwell.config.SleepWellConfig;
import com.sleepwell.maid.MaidCapabilities;
import com.sleepwell.maid.MaidSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Winter work skip.
 * <p>
 * While it is winter and the maid's switch is on, she is parked on TLM's own idle task (so she does not
 * follow her owner and does not keep farming), and {@code SchedulePosMixin} keeps her restrict centre on
 * the idle spot instead of the work point, which is what suppresses the work-point teleport. Rest and
 * sleep positions are untouched: those are separate activities and separate positions.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class WinterSkipHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/winterSkip");
    /** Season checks are cheap but not free; one look per second is plenty. */
    private static final int CHECK_INTERVAL_TICKS = 20;

    private WinterSkipHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = maid.getUUID();
        if (maid.isRemoved() || !maid.isAlive()) {
            WinterSkipState.clear(id);
            return;
        }
        if (maid.tickCount % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        MaidSettings settings = MaidCapabilities.of(maid);
        String idleUid = idleTaskUid();
        String currentUid = taskUid(maid);
        boolean winter = SeasonBridge.isWinter(level);
        boolean enabled = settings.winterSkip();

        if (WinterSkipState.isSkipping(id)) {
            if (WinterSkipPolicy.shouldRestore(true, winter, enabled, currentUid, idleUid)) {
                restore(maid, settings, id);
            }
            return;
        }
        if (!WinterSkipPolicy.shouldSkip(winter, enabled, currentUid, SleepWellConfig.WINTER_SKIP_TASKS.get())) {
            return;
        }
        startSkip(maid, settings, id, currentUid);
    }

    private static void startSkip(EntityMaid maid, MaidSettings settings, UUID id, String currentUid) {
        settings.setSavedTaskId(currentUid);
        WinterSkipState.mark(id);
        maid.setTask(TaskManager.getIdleTask());
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        LOGGER.debug("Winter skip: maid {} parked on the idle task (was {})", id, currentUid);
    }

    private static void restore(EntityMaid maid, MaidSettings settings, UUID id) {
        String saved = settings.savedTaskId();
        settings.setSavedTaskId("");
        WinterSkipState.clear(id);
        // The player may have picked a different task while she was parked; never fight that.
        if (saved.isEmpty() || !idleTaskUid().equals(taskUid(maid))) {
            return;
        }
        ResourceLocation location = ResourceLocation.tryParse(saved);
        if (location == null) {
            return;
        }
        TaskManager.findTask(location).ifPresent(task -> {
            maid.setTask(task);
            LOGGER.debug("Winter skip: maid {} back to task {}", id, saved);
        });
    }

    private static String taskUid(EntityMaid maid) {
        IMaidTask task = maid.getTask();
        return task == null ? "" : task.getUid().toString();
    }

    private static String idleTaskUid() {
        return TaskManager.getIdleTask().getUid().toString();
    }

    /** Called from {@code SchedulePosMixin}: keep her "home" on the idle spot instead of the work point. */
    public static boolean shouldSuppressWorkPoint(EntityMaid maid) {
        return maid != null && WinterSkipState.isSkipping(maid);
    }
}
