package com.sleepwell.sleep;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Why did she get up?
 * <p>
 * A maid that keeps climbing out of bed and lying back down looks identical from the outside no matter what
 * woke her: the schedule, her own task, a teleport, a stray arrow. This handler writes one line per
 * sleep-to-awake transition with everything that could have caused it - the activity the schedule had her on,
 * the task she is running, her distance to her owner, whether her position jumped (a teleport, because she
 * cannot walk while asleep) and any damage she took while in bed.
 * <p>
 * Diagnostics only: it changes no behaviour, and it logs on transitions rather than per tick.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class SleepWakeDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_well/wake");
    /** She cannot walk while asleep, so a jump this large is something else moving her. */
    private static final double TELEPORT_JUMP = 1.5D;
    /** Two wakes this close together are the "gets up over and over" symptom, not a normal morning. */
    private static final long REPEAT_WINDOW = 1200L;
    private static final Map<UUID, Track> TRACKS = new ConcurrentHashMap<>();

    private SleepWakeDiagnostics() {
    }

    /** Per maid, the little that has to be remembered between ticks. */
    private static final class Track {
        private boolean sleeping;
        private long sleptFrom;
        private long lastWake = -1L;
        private int wakesInWindow;
        private String damage = "";
        private Vec3 lastPos = Vec3.ZERO;
        private boolean teleported;
    }

    /** Damage taken while in bed: the one cause we can name outright instead of inferring. */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide()) {
            return;
        }
        Track track = TRACKS.get(maid.getUUID());
        if (track == null || !track.sleeping) {
            return;
        }
        track.damage = event.getSource().getMsgId() + " (" + String.format(java.util.Locale.ROOT, "%.1f", event.getAmount()) + ")";
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !(maid.level() instanceof ServerLevel level)) {
            return;
        }
        Track track = TRACKS.computeIfAbsent(maid.getUUID(), key -> new Track());
        long now = level.getGameTime();
        boolean sleeping = maid.isSleeping();
        if (track.sleeping && maid.position().distanceTo(track.lastPos) > TELEPORT_JUMP) {
            track.teleported = true;
        }
        track.lastPos = maid.position();

        if (sleeping && !track.sleeping) {
            if (track.lastWake > 0L && now - track.lastWake <= REPEAT_WINDOW) {
                LOGGER.info("[诊断] 重新躺下 maid={} 距上次起身 {} 秒", name(maid), (now - track.lastWake) / 20L);
            }
            track.sleeping = true;
            track.sleptFrom = now;
            track.teleported = false;
            track.damage = "";
            return;
        }
        if (sleeping || !track.sleeping) {
            return;
        }

        track.sleeping = false;
        track.wakesInWindow = track.lastWake > 0L && now - track.lastWake <= REPEAT_WINDOW
                ? track.wakesInWindow + 1 : 1;
        track.lastWake = now;
        LOGGER.info("[诊断] 入睡 maid={} 日程={} 任务={} 主人距离={}",
                name(maid), maid.getScheduleDetail().getName(), taskUid(maid), ownerDistance(maid));
        LOGGER.info("[诊断] 起身 maid={} 睡了={}秒 日程={} 任务={} 主人距离={} 传送={} 受伤={} 窗口内第{}次",
                name(maid), (now - track.sleptFrom) / 20L, maid.getScheduleDetail().getName(), taskUid(maid),
                ownerDistance(maid), track.teleported, track.damage.isEmpty() ? "无" : track.damage,
                track.wakesInWindow);
    }

    private static String name(EntityMaid maid) {
        String uuid = maid.getUUID().toString();
        return maid.getDisplayName().getString() + "/" + uuid.substring(0, 8);
    }

    private static String taskUid(EntityMaid maid) {
        IMaidTask task = maid.getTask();
        return task == null ? "无" : task.getUid().toString();
    }

    private static String ownerDistance(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        if (owner == null) {
            return "无主人";
        }
        return String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(maid.distanceToSqr(owner)));
    }
}
