package com.sleepwell.sleep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepFallbackTrackerTest {
    private static final int STEP = SleepFallbackPolicy.SAMPLE_INTERVAL_TICKS;

    private static void sampleAt(SleepFallbackTracker tracker, double distSqr) {
        for (int i = 0; i < STEP; i++) {
            tracker.tick(true, distSqr);
        }
    }

    @Test
    void noSampleBeforeTheInterval() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        for (int i = 0; i < STEP - 1; i++) {
            tracker.tick(true, 100.0D);
        }
        assertEquals(0, tracker.stalledSamples());
    }

    @Test
    void steadyProgressNeverStalls() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        double dist = 400.0D;
        for (int i = 0; i < 20; i++) {
            sampleAt(tracker, dist);
            dist -= 4.0D;
        }
        assertEquals(0, tracker.stalledSamples());
    }

    @Test
    void circlingCountsUpUntilStuck() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        for (int i = 0; i < 10; i++) {
            sampleAt(tracker, i % 2 == 0 ? 100.0D : 100.4D);
        }
        // First sample only seeds the window, so 10 samples leave 9 stalled ones.
        assertEquals(9, tracker.stalledSamples());
        assertTrue(SleepFallbackPolicy.stuck(tracker.stalledSamples(),
                SleepFallbackPolicy.samplesNeeded(SleepFallbackPolicy.DEFAULT_SECONDS)));
    }

    @Test
    void progressResetsTheCounter() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        sampleAt(tracker, 100.0D);
        sampleAt(tracker, 100.0D);
        sampleAt(tracker, 100.0D);
        assertEquals(2, tracker.stalledSamples());
        sampleAt(tracker, 90.0D);
        assertEquals(0, tracker.stalledSamples());
    }

    @Test
    void tinyImprovementIsNotProgress() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        sampleAt(tracker, 100.0D);
        sampleAt(tracker, 99.5D);
        assertEquals(1, tracker.stalledSamples());
    }

    @Test
    void teleportSpendsBudgetAndRestartsCooldown() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        for (int i = 0; i < 30; i++) {
            tracker.tick(true, 100.0D);
        }
        assertEquals(30, tracker.ticksSinceLastTeleport());
        sampleAt(tracker, 100.0D);
        tracker.onTeleported();
        assertEquals(1, tracker.teleportsThisNap());
        assertEquals(0, tracker.ticksSinceLastTeleport());
        assertEquals(0, tracker.stalledSamples());
    }

    @Test
    void fallingAsleepRefreshesTheBudget() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        tracker.onTeleported();
        assertEquals(1, tracker.teleportsThisNap());
        tracker.onFellAsleep();
        assertEquals(0, tracker.teleportsThisNap());
    }

    @Test
    void leavingTheRestWindowRefreshesTheBudget() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        tracker.onTeleported();
        tracker.onTeleported();
        assertEquals(2, tracker.teleportsThisNap());
        for (int i = 0; i < STEP; i++) {
            tracker.tick(false, 100.0D);
        }
        assertEquals(0, tracker.teleportsThisNap());
        assertEquals(0, tracker.stalledSamples());
    }

    @Test
    void outsideTheRestWindowNoSamplingHappens() {
        SleepFallbackTracker tracker = new SleepFallbackTracker();
        for (int i = 0; i < STEP * 4; i++) {
            tracker.tick(false, 100.0D);
        }
        assertEquals(0, tracker.stalledSamples());
        for (int i = 0; i < STEP * 6; i++) {
            tracker.tick(true, 100.0D);
        }
        assertEquals(5, tracker.stalledSamples());
    }
}
