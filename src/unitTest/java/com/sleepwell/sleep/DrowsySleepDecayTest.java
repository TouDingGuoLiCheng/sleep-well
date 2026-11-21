package com.sleepwell.sleep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrowsySleepDecayTest {

    @Test
    void bonusOnlyTopsUpOverVanillaTicking() {
        assertEquals(0, DrowsySleepDecay.bonusTicksPerSecond(0));
        assertEquals(0, DrowsySleepDecay.bonusTicksPerSecond(1));
        assertEquals(180, DrowsySleepDecay.bonusTicksPerSecond(10));
        assertEquals(1180, DrowsySleepDecay.bonusTicksPerSecond(60));
    }

    @Test
    void oneStepTakesTheWholeSecondsOff() {
        int drowsy = 600 * DrowsySleepDecay.TICKS_PER_SECOND;
        assertEquals(drowsy - 180, DrowsySleepDecay.remainingTicks(drowsy, 10));
        assertEquals(drowsy, DrowsySleepDecay.remainingTicks(drowsy, 1));
    }

    /** Sleeping a minute clears the default 10-minute 困倦 exactly: 10 s of timer per 1 s of sleep. */
    @Test
    void aMinuteOfSleepClearsTheDefaultDrowsy() {
        int remaining = 600 * DrowsySleepDecay.TICKS_PER_SECOND;
        for (int second = 0; second < 60; second++) {
            remaining = DrowsySleepDecay.remainingTicks(remaining, 10) - DrowsySleepDecay.TICKS_PER_SECOND;
        }
        assertEquals(0, remaining);
        assertTrue(DrowsySleepDecay.remainingTicks(20, 10) <= 0,
                "a nearly spent effect should come out expired, not barely alive");
    }
}
