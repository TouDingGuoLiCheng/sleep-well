package com.sleepwell.liein;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LieInPolicyTest {

    @Test
    void winterSwapsInTheBoostedChance() {
        assertEquals(30, LieInPolicy.chancePercent(false, 30, 50));
        assertEquals(50, LieInPolicy.chancePercent(true, 30, 50));
        assertEquals(5, LieInPolicy.chancePercent(false, 5, 10));
        assertEquals(10, LieInPolicy.chancePercent(true, 5, 10));
    }

    @Test
    void chanceIsClamped() {
        assertEquals(0, LieInPolicy.chancePercent(false, -5, 50));
        assertEquals(100, LieInPolicy.chancePercent(true, 30, 400));
    }

    @Test
    void rollOnlyFiresInsideThePercentWindow() {
        assertFalse(LieInPolicy.roll(0, 0));
        assertTrue(LieInPolicy.roll(30, 0));
        assertTrue(LieInPolicy.roll(30, 29));
        assertFalse(LieInPolicy.roll(30, 30));
        assertFalse(LieInPolicy.roll(30, 99));
        assertTrue(LieInPolicy.roll(100, 99));
    }

    @Test
    void durationStaysInsideTheConfiguredHours() {
        for (int roll = 0; roll < 200; roll++) {
            long lieIn = LieInPolicy.durationTicks(2, 5, roll);
            assertTrue(lieIn >= 2000L && lieIn <= 5000L, "lie-in out of range: " + lieIn);
            long nap = LieInPolicy.durationTicks(1, 3, roll);
            assertTrue(nap >= 1000L && nap <= 3000L, "nap out of range: " + nap);
        }
        assertEquals(2000L, LieInPolicy.durationTicks(2, 5, 0));
        assertEquals(5000L, LieInPolicy.durationTicks(2, 5, 3));
        assertEquals(2000L, LieInPolicy.durationTicks(2, 5, 4));
        // swapped bounds are tolerated rather than producing a zero-length sleep
        assertEquals(2000L, LieInPolicy.durationTicks(5, 2, 0));
    }

    @Test
    void thirtyInGameMinutesIsFiveHundredTicks() {
        assertEquals(500L, LieInPolicy.napCheckIntervalTicks(30));
        assertEquals(16L, LieInPolicy.napCheckIntervalTicks(1));
        assertEquals(2000L, LieInPolicy.napCheckIntervalTicks(120));
        assertEquals(2000L, LieInPolicy.napCheckIntervalTicks(500));
    }

    @Test
    void expiryIsInclusive() {
        assertFalse(LieInPolicy.expired(99L, 100L));
        assertTrue(LieInPolicy.expired(100L, 100L));
        assertTrue(LieInPolicy.expired(101L, 100L));
    }

    @Test
    void idleModeComparesTheTaskUid() {
        assertTrue(LieInPolicy.idleMode("touhou_little_maid:idle", "touhou_little_maid:idle"));
        assertFalse(LieInPolicy.idleMode("touhou_little_maid:farm", "touhou_little_maid:idle"));
        assertFalse(LieInPolicy.idleMode("", "touhou_little_maid:idle"));
        assertFalse(LieInPolicy.idleMode(null, "touhou_little_maid:idle"));
    }

    @Test
    void wakeRollHappensOncePerDayWhileSleepingOffRest() {
        assertTrue(LieInPolicy.needsWakeRoll(false, false, true, false));
        assertFalse(LieInPolicy.needsWakeRoll(true, false, true, false));
        assertFalse(LieInPolicy.needsWakeRoll(false, true, true, false));
        assertFalse(LieInPolicy.needsWakeRoll(false, false, false, false));
        assertFalse(LieInPolicy.needsWakeRoll(false, false, true, true));
    }

    @Test
    void napNeedsAnAwakeIdleMaidAndADueCheck() {
        assertTrue(LieInPolicy.napDue(true, false, true, false, false, 100L, 100L));
        assertFalse(LieInPolicy.napDue(false, false, true, false, false, 100L, 100L));
        assertFalse(LieInPolicy.napDue(true, true, true, false, false, 100L, 100L));
        assertFalse(LieInPolicy.napDue(true, false, false, false, false, 100L, 100L));
        assertFalse(LieInPolicy.napDue(true, false, true, true, false, 100L, 100L));
        assertFalse(LieInPolicy.napDue(true, false, true, false, true, 100L, 100L));
        assertFalse(LieInPolicy.napDue(true, false, true, false, false, 99L, 100L));
    }

    @Test
    void napIsDroppedWhenNoBedIsReached() {
        assertFalse(LieInPolicy.napGaveUp(true, true, 1000L, 0L));
        assertFalse(LieInPolicy.napGaveUp(true, false, LieInPolicy.NAP_GIVE_UP_TICKS - 1, 0L));
        assertTrue(LieInPolicy.napGaveUp(true, false, LieInPolicy.NAP_GIVE_UP_TICKS, 0L));
        assertFalse(LieInPolicy.napGaveUp(false, false, 100000L, 0L));
    }
}
