package com.sleepwell.sleep;

/**
 * Per-maid state for the sleep fallback. Pure Java, no Minecraft types: the handler feeds the
 * distance in every tick and asks whether progress stalled.
 * <p>
 * A nap is one rest window. The teleport budget resets when the rest window ends and also when the
 * maid actually falls asleep, so a second nap in the same night gets a fresh budget.
 */
public final class SleepFallbackTracker {
    private int ticksSinceSample;
    private int ticksSinceLastTeleport;
    private int stalledSamples;
    private int teleportsThisNap;
    private double bestDistSqr = Double.MAX_VALUE;
    private boolean sampled;
    private boolean resting;

    /** Advance one server tick. Sampling only happens inside the rest window. */
    public void tick(boolean restingNow, double distSqr) {
        ticksSinceLastTeleport++;
        if (!restingNow) {
            // Outside the rest window the nap is over: the next one starts with a fresh budget.
            resting = false;
            clearWindow();
            teleportsThisNap = 0;
            return;
        }
        if (!resting) {
            resting = true;
            clearWindow();
        }
        ticksSinceSample++;
        if (ticksSinceSample < SleepFallbackPolicy.SAMPLE_INTERVAL_TICKS) {
            return;
        }
        ticksSinceSample = 0;
        sample(distSqr);
    }

    private void sample(double distSqr) {
        if (!sampled) {
            sampled = true;
            bestDistSqr = distSqr;
            stalledSamples = 0;
            return;
        }
        if (bestDistSqr - distSqr >= SleepFallbackPolicy.MIN_PROGRESS_SQR) {
            bestDistSqr = distSqr;
            stalledSamples = 0;
            return;
        }
        stalledSamples++;
    }

    /** Call right after a fallback teleport landed. */
    public void onTeleported() {
        teleportsThisNap++;
        ticksSinceLastTeleport = 0;
        clearWindow();
    }

    /** Call when the maid actually falls asleep: the next nap starts with a fresh budget. */
    public void onFellAsleep() {
        teleportsThisNap = 0;
        clearWindow();
    }

    public void clearWindow() {
        ticksSinceSample = 0;
        stalledSamples = 0;
        sampled = false;
        bestDistSqr = Double.MAX_VALUE;
    }

    public int stalledSamples() {
        return stalledSamples;
    }

    public int teleportsThisNap() {
        return teleportsThisNap;
    }

    public int ticksSinceLastTeleport() {
        return ticksSinceLastTeleport;
    }
}
