package com.sleepwell.liein;

/**
 * Pure rules for "a maid sleeps in, and naps on her own".
 * <p>
 * Two rolls feed the state machine: one at the moment she is supposed to wake up, one every
 * few in-game minutes while she is awake and parked on the idle task. Winter only raises the
 * odds (and only when Serene Seasons is installed — the caller passes {@code winter == false}
 * otherwise), it is never a gate.
 */
public final class LieInPolicy {
    /** One in-game hour is 1000 ticks. */
    public static final int TICKS_PER_GAME_HOUR = 1000;

    /** A nap that never reaches a bed is given up after this long. */
    public static final long NAP_GIVE_UP_TICKS = 600L;

    private LieInPolicy() {
    }

    /** Winter swaps in the boosted number; without Serene Seasons the caller passes {@code winter == false}. */
    public static int chancePercent(boolean winter, int basePercent, int winterPercent) {
        return clampPercent(winter ? winterPercent : basePercent);
    }

    public static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    /** {@code roll} is expected in {@code [0, 100)}; a 0 % chance never fires. */
    public static boolean roll(int percent, int roll) {
        return percent > 0 && roll >= 0 && roll < clampPercent(percent);
    }

    /** Random duration inside {@code [minHours, maxHours]}, in ticks. */
    public static long durationTicks(int minHours, int maxHours, int roll) {
        int lo = Math.max(1, Math.min(minHours, maxHours));
        int hi = Math.max(lo, Math.max(minHours, maxHours));
        int span = hi - lo + 1;
        int hours = lo + Math.floorMod(roll, span);
        return (long) hours * TICKS_PER_GAME_HOUR;
    }

    /** How often the awake idle maid re-rolls for a nap. */
    public static long napCheckIntervalTicks(int minutes) {
        int clamped = Math.max(1, Math.min(120, minutes));
        // Divide last so 30 in-game minutes is exactly 500 ticks instead of 480.
        return Math.max(1L, (long) clamped * TICKS_PER_GAME_HOUR / 60L);
    }

    public static boolean expired(long now, long until) {
        return now >= until;
    }

    /** The per-maid "idle task" lookup, by task uid. */
    public static boolean idleMode(String taskUid, String idleTaskUid) {
        return taskUid != null && !taskUid.isEmpty() && taskUid.equals(idleTaskUid);
    }

    /**
     * The wake-up roll: she is in bed, still asleep, and the schedule has already moved her off
     * {@code Activity.REST} — the exact moment {@code MaidClearSleepTask} would get her up.
     */
    public static boolean needsWakeRoll(boolean active, boolean alreadyRolledToday, boolean sleeping, boolean resting) {
        return !active && !alreadyRolledToday && sleeping && !resting;
    }

    public static boolean napDue(boolean enabled, boolean active, boolean idleMode, boolean sleeping,
                                 boolean resting, long now, long nextCheck) {
        return enabled && !active && idleMode && !sleeping && !resting && now >= nextCheck;
    }

    /** A nap that has not reached a bed within the grace window is abandoned. */
    public static boolean napGaveUp(boolean napping, boolean sleeping, long now, long started) {
        return napping && !sleeping && now - started >= NAP_GIVE_UP_TICKS;
    }
}
