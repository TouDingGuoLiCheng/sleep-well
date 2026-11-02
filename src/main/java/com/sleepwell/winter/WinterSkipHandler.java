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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

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
    /** One tick per in-game day (06:00, dayTime % 24000 == 0) is the only moment we look at the season. */
    private static final long DAY_LENGTH = 24000L;
    /** Season state per dimension, cached for the current in-game day so a herd of maids costs one lookup. */
    private static final Map<ResourceKey<Level>, SeasonCache> SEASON_CACHE = new ConcurrentHashMap<>();

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
        // Once per in-game day at 06:00 instead of every second: the season cannot change inside a day,
        // and a work task chosen at dawn is what the player actually sees.
        if (level.getDayTime() % DAY_LENGTH != 0L) {
            return;
        }

        MaidSettings settings = MaidCapabilities.of(maid);
        String idleUid = idleTaskUid();
        String currentUid = taskUid(maid);
        boolean winter = winterOf(level);
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

    /** Season lookup, shared by every maid of the same dimension within one in-game day. */
    private static boolean winterOf(ServerLevel level) {
        long day = level.getDayTime() / DAY_LENGTH;
        SeasonCache cached = SEASON_CACHE.get(level.dimension());
        if (cached != null && cached.day == day) {
            return cached.winter;
        }
        boolean winter = SeasonBridge.isWinter(level);
        SEASON_CACHE.put(level.dimension(), new SeasonCache(day, winter));
        return winter;
    }

    /** Called from {@code SchedulePosMixin}: keep her "home" on the idle spot instead of the work point. */
    public static boolean shouldSuppressWorkPoint(EntityMaid maid) {
        return maid != null && WinterSkipState.isSkipping(maid);
    }

    private record SeasonCache(long day, boolean winter) {
    }
}
