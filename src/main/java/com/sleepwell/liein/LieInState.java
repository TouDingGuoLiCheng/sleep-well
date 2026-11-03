package com.sleepwell.liein;

/**
 * Per-maid runtime state for the lie-in / nap feature.
 * <p>
 * Deliberately not persisted: reloading the world drops the state and the maid simply wakes up,
 * which is safer than reviving a stale "sleep until tick 193000" from an earlier session.
 */
public final class LieInState {
    public enum Kind {
        /** Stayed in bed past the moment she should have got up. */
        LIE_IN,
        /** Walked back to bed on her own during the day. */
        NAP
    }

    private Kind kind;
    private long until;
    private long started;
    private String taskUid = "";
    private boolean configuredSleepArea;
    private long rolledDay = Long.MIN_VALUE;
    private long nextNapCheck;
    private String lastActivity = "";

    public boolean active() {
        return kind != null;
    }

    public Kind kind() {
        return kind;
    }

    public long until() {
        return until;
    }

    public long started() {
        return started;
    }

    /** Task she had when the state began; a task change means the player wants her back at work. */
    public String taskUid() {
        return taskUid;
    }

    public void begin(Kind kind, long now, long durationTicks, String taskUid) {
        begin(kind, now, durationTicks, taskUid, false);
    }

    public void begin(Kind kind, long now, long durationTicks, String taskUid, boolean configuredSleepArea) {
        this.kind = kind;
        this.started = now;
        this.until = now + durationTicks;
        this.taskUid = taskUid == null ? "" : taskUid;
        this.configuredSleepArea = configuredSleepArea;
    }

    /** True when the nap walks her to the kappa-compass sleep area instead of a bed she can see. */
    public boolean configuredSleepArea() {
        return configuredSleepArea;
    }

    public void clear() {
        kind = null;
        until = 0L;
        started = 0L;
        taskUid = "";
        configuredSleepArea = false;
    }

    public boolean rolledToday(long day) {
        return rolledDay == day;
    }

    public void markRolled(long day) {
        rolledDay = day;
    }

    public long nextNapCheck() {
        return nextNapCheck;
    }

    public void setNextNapCheck(long nextNapCheck) {
        this.nextNapCheck = nextNapCheck;
    }

    /** Name of the last schedule activity seen, so a REST -> awake transition can be logged once. */
    public String lastActivity() {
        return lastActivity;
    }

    public void setLastActivity(String lastActivity) {
        this.lastActivity = lastActivity == null ? "" : lastActivity;
    }
}
