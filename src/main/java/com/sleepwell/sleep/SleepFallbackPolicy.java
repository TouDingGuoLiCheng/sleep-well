package com.sleepwell.sleep;

/**
 * Pure rules for the sleep-stuck fallback teleport.
 * <p>
 * Vanilla {@code SchedulePos.tick} only teleports a maid when she is further than
 * {@code (restrictRadius + 4)} from her restrict center <b>and</b> is not already walking towards
 * that center. A maid stuck on a ladder / fence / drop inside her sleep area therefore keeps
 * receiving the same walk target forever. This policy decides when to step in instead.
 * <p>
 * All numbers are in vanilla units: distances are squared block distances, times are ticks.
 */
public final class SleepFallbackPolicy {
    /** Distance is sampled every this many ticks. */
    public static final int SAMPLE_INTERVAL_TICKS = 20;
    /** Default "no progress" window in seconds (100 ticks = 5 s). */
    public static final int DEFAULT_SECONDS = 5;
    public static final int MIN_SECONDS = 2;
    public static final int MAX_SECONDS = 60;
    /** Fallback teleports allowed per nap. */
    public static final int DEFAULT_MAX_TELEPORTS = 2;
    public static final int MAX_TELEPORTS_CAP = 5;
    /**
     * Never step in closer than 3 blocks (squared). {@code MaidBedTask} already snaps onto the bed
     * within 2 blocks, so anything nearer is not a pathing failure we should paper over.
     */
    public static final double MIN_DIST_SQR = 9.0D;
    /** One sample counts as progress when the best distance improved by at least 1 block (squared). */
    public static final double MIN_PROGRESS_SQR = 1.0D;
    /** Minimum gap between two fallback teleports. */
    public static final int TELEPORT_COOLDOWN_TICKS = 200;

    private SleepFallbackPolicy() {
    }

    public static int clampSeconds(int seconds) {
        return Math.min(MAX_SECONDS, Math.max(MIN_SECONDS, seconds));
    }

    public static int clampMaxTeleports(int maxTeleports) {
        return Math.min(MAX_TELEPORTS_CAP, Math.max(0, maxTeleports));
    }

    /** Configured seconds of no progress -> number of consecutive stalled samples needed. */
    public static int samplesNeeded(int seconds) {
        return Math.max(1, clampSeconds(seconds) * 20 / SAMPLE_INTERVAL_TICKS);
    }

    /**
     * Hard gates: everything that can be judged without history.
     *
     * @param enabled                config switch
     * @param resting                maid's schedule activity is {@code Activity.REST}
     * @param sleeping               maid is already asleep
     * @param inVehicle              maid rides or carries a vehicle/entity
     * @param configured             {@code SchedulePos.isConfigured()} (sleep point set by kappa compass)
     * @param hasSleepPos            {@code SchedulePos.getSleepPos() != null}
     * @param sameDimension          sleep point lives in the maid's dimension
     * @param distSqr                squared distance from the maid to the sleep point
     * @param teleportsThisNap       fallback teleports already spent this nap
     * @param maxTeleports           configured per-nap budget
     * @param ticksSinceLastTeleport ticks since the last fallback teleport
     */
    public static boolean eligible(boolean enabled, boolean resting, boolean sleeping, boolean inVehicle,
                                   boolean configured, boolean hasSleepPos, boolean sameDimension,
                                   double distSqr, int teleportsThisNap, int maxTeleports,
                                   int ticksSinceLastTeleport) {
        if (!enabled || !resting || sleeping || inVehicle) {
            return false;
        }
        if (!configured || !hasSleepPos || !sameDimension) {
            return false;
        }
        if (!(distSqr > MIN_DIST_SQR)) {
            return false;
        }
        if (maxTeleports <= 0 || teleportsThisNap >= maxTeleports) {
            return false;
        }
        return ticksSinceLastTeleport >= TELEPORT_COOLDOWN_TICKS;
    }

    /** History gate: the distance has stopped improving for long enough. */
    public static boolean stuck(int stalledSamples, int samplesNeeded) {
        return samplesNeeded > 0 && stalledSamples >= samplesNeeded;
    }
}
