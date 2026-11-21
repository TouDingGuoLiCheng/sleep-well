package com.sleepwell.sleep;

/**
 * The arithmetic behind "sleeping wears 困倦 off faster", kept pure so it can be tested without a server.
 * <p>
 * Vanilla already takes one tick off the effect every tick, so a second of sleep costs 20 ticks on its own.
 * The configured number is the <em>total</em> per second, and this class only works out how much extra has to
 * come off on top of vanilla's own tick-down.
 */
public final class DrowsySleepDecay {
    /** One real second is 20 game ticks. */
    public static final int TICKS_PER_SECOND = 20;

    private DrowsySleepDecay() {
    }

    /**
     * Extra ticks to remove once per second of sleep so that the total comes out at secondsPerSecond seconds.
     * A rate of one second per second is plain vanilla ticking, so it adds nothing.
     */
    public static int bonusTicksPerSecond(int secondsPerSecond) {
        if (secondsPerSecond <= 1) {
            return 0;
        }
        return secondsPerSecond * TICKS_PER_SECOND - TICKS_PER_SECOND;
    }

    /** Remaining ticks after one decay step; zero or less means the effect is gone. */
    public static int remainingTicks(int remainingTicks, int secondsPerSecond) {
        return remainingTicks - bonusTicksPerSecond(secondsPerSecond);
    }
}
