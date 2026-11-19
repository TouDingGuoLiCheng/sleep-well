package com.sleepwell.sleep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepFallbackPolicyTest {
    private static boolean eligible(boolean enabled, boolean resting, boolean sleeping, boolean inVehicle,
                                    boolean configured, boolean hasSleepPos, boolean sameDimension,
                                    double distSqr, int teleportsThisNap, int maxTeleports,
                                    int ticksSinceLastTeleport) {
        return SleepFallbackPolicy.eligible(enabled, resting, sleeping, inVehicle, configured, hasSleepPos,
                sameDimension, distSqr, teleportsThisNap, maxTeleports, ticksSinceLastTeleport);
    }

    @Test
    void baselineIsEligible() {
        assertTrue(eligible(true, true, false, false, true, true, true, 400.0D, 0, 2, 1000));
    }

    @Test
    void disabledIsNotEligible() {
        assertFalse(eligible(false, true, false, false, true, true, true, 400.0D, 0, 2, 1000));
    }

    @Test
    void notRestingIsNotEligible() {
        assertFalse(eligible(true, false, false, false, true, true, true, 400.0D, 0, 2, 1000));
    }

    @Test
    void sleepingIsNotEligible() {
        assertFalse(eligible(true, true, true, false, true, true, true, 400.0D, 0, 2, 1000));
    }

    @Test
    void inVehicleIsNotEligible() {
        assertFalse(eligible(true, true, false, true, true, true, true, 400.0D, 0, 2, 1000));
    }

    @Test
    void unconfiguredCompassIsNotEligible() {
        assertFalse(eligible(true, true, false, false, false, true, true, 400.0D, 0, 2, 1000));
    }

    @Test
    void missingSleepPosIsNotEligible() {
        assertFalse(eligible(true, true, false, false, true, false, true, 400.0D, 0, 2, 1000));
    }

    @Test
    void otherDimensionIsNotEligible() {
        assertFalse(eligible(true, true, false, false, true, true, false, 400.0D, 0, 2, 1000));
    }

    @Test
    void closeEnoughIsNotEligible() {
        assertFalse(eligible(true, true, false, false, true, true, true, SleepFallbackPolicy.MIN_DIST_SQR, 0, 2, 1000));
        assertTrue(eligible(true, true, false, false, true, true, true, SleepFallbackPolicy.MIN_DIST_SQR + 0.01D, 0, 2, 1000));
    }

    @Test
    void budgetExhaustedIsNotEligible() {
        assertFalse(eligible(true, true, false, false, true, true, true, 400.0D, 2, 2, 1000));
        assertTrue(eligible(true, true, false, false, true, true, true, 400.0D, 1, 2, 1000));
    }

    @Test
    void zeroBudgetIsNotEligible() {
        assertFalse(eligible(true, true, false, false, true, true, true, 400.0D, 0, 0, 1000));
    }

    @Test
    void cooldownBlocksImmediatelyAfterTeleport() {
        assertFalse(eligible(true, true, false, false, true, true, true, 400.0D, 1, 2, SleepFallbackPolicy.TELEPORT_COOLDOWN_TICKS - 1));
        assertTrue(eligible(true, true, false, false, true, true, true, 400.0D, 1, 2, SleepFallbackPolicy.TELEPORT_COOLDOWN_TICKS));
    }

    @Test
    void stuckNeedsTheWholeWindow() {
        assertFalse(SleepFallbackPolicy.stuck(4, 5));
        assertTrue(SleepFallbackPolicy.stuck(5, 5));
        assertTrue(SleepFallbackPolicy.stuck(6, 5));
        assertFalse(SleepFallbackPolicy.stuck(5, 0));
    }

    @Test
    void secondsMapToSamples() {
        assertEquals(5, SleepFallbackPolicy.samplesNeeded(SleepFallbackPolicy.DEFAULT_SECONDS));
        assertEquals(2, SleepFallbackPolicy.samplesNeeded(2));
        assertEquals(60, SleepFallbackPolicy.samplesNeeded(60));
        assertEquals(2, SleepFallbackPolicy.samplesNeeded(0));
        assertEquals(60, SleepFallbackPolicy.samplesNeeded(1000));
    }

    @Test
    void secondsAndBudgetAreClamped() {
        assertEquals(SleepFallbackPolicy.MIN_SECONDS, SleepFallbackPolicy.clampSeconds(-5));
        assertEquals(SleepFallbackPolicy.MAX_SECONDS, SleepFallbackPolicy.clampSeconds(999));
        assertEquals(SleepFallbackPolicy.DEFAULT_SECONDS, SleepFallbackPolicy.clampSeconds(SleepFallbackPolicy.DEFAULT_SECONDS));
        assertEquals(0, SleepFallbackPolicy.clampMaxTeleports(-1));
        assertEquals(SleepFallbackPolicy.MAX_TELEPORTS_CAP, SleepFallbackPolicy.clampMaxTeleports(99));
    }
}
