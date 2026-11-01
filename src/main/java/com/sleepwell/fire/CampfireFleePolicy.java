package com.sleepwell.fire;

/**
 * Pure rules for "a maid burned by a campfire tries to run away".
 * <p>
 * Campfire damage in vanilla is a plain {@code inFire} hit dealt by {@code CampfireBlock#stepOn}, so the damage
 * source alone cannot tell a campfire apart from a fire block or lava. The caller therefore has to look at the
 * block the maid is standing in as well; both facts are passed in here so the decision stays testable.
 * <p>
 * All numbers are in vanilla units: distances are squared block distances, times are ticks.
 */
public final class CampfireFleePolicy {
    /** How long one flee lasts (100 ticks = 5 s), refreshed by every new campfire burn. */
    public static final int FLEE_TICKS = 100;
    /** Pick a new landing spot when the current one is not reached within this many ticks. */
    public static final int RETARGET_TICKS = 40;
    /** Close enough to the landing spot (squared): 1.5 blocks. */
    public static final double ARRIVED_SQR = 2.25D;
    /** Never pick a spot closer than 3 blocks, otherwise she just shuffles one block off the fire. */
    public static final double MIN_FLEE_DIST = 3.0D;
    /** Landing spot search radius, in blocks. */
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 12;
    public static final int DEFAULT_RADIUS = 6;

    private CampfireFleePolicy() {
    }

    public static int clampRadius(int radius) {
        return Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, radius));
    }

    /**
     * Was this hit a campfire burn? Vanilla campfires hurt with {@code inFire}, and the maid has to be standing
     * in (or on top of) the lit campfire block for it to be the campfire's doing rather than plain fire.
     */
    public static boolean isCampfireDamage(boolean fireDamage, boolean standingOnCampfire) {
        return fireDamage && standingOnCampfire;
    }

    /**
     * Hard gates for starting a flee.
     *
     * @param enabled            config switch
     * @param alive              maid is alive
     * @param sleeping           maid is asleep (let the nap win; she is in a bed, not on a fire)
     * @param riding             maid rides or carries a vehicle/entity
     * @param fireDamage         damage source is fire-typed
     * @param standingOnCampfire a lit campfire is in her feet column
     */
    public static boolean eligible(boolean enabled, boolean alive, boolean sleeping, boolean riding,
                                   boolean fireDamage, boolean standingOnCampfire) {
        if (!enabled || !alive || sleeping || riding) {
            return false;
        }
        return isCampfireDamage(fireDamage, standingOnCampfire);
    }

    /** Reached the landing spot (or overshot it). */
    public static boolean arrived(double distSqr) {
        return distSqr <= ARRIVED_SQR;
    }

    /** Given up on the current spot and look for another one. */
    public static boolean needsRetarget(int ticksOnTarget) {
        return ticksOnTarget >= RETARGET_TICKS;
    }
}
