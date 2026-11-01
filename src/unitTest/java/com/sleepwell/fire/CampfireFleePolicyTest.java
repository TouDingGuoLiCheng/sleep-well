package com.sleepwell.fire;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampfireFleePolicyTest {
    private static boolean eligible(boolean enabled, boolean alive, boolean sleeping, boolean riding,
                                    boolean fireDamage, boolean standingOnCampfire) {
        return CampfireFleePolicy.eligible(enabled, alive, sleeping, riding, fireDamage, standingOnCampfire);
    }

    @Test
    void burnedByCampfireIsEligible() {
        assertTrue(eligible(true, true, false, false, true, true));
    }

    @Test
    void disabledIsNotEligible() {
        assertFalse(eligible(false, true, false, false, true, true));
    }

    @Test
    void deadIsNotEligible() {
        assertFalse(eligible(true, false, false, false, true, true));
    }

    @Test
    void sleepingIsNotEligible() {
        assertFalse(eligible(true, true, true, false, true, true));
    }

    @Test
    void ridingIsNotEligible() {
        assertFalse(eligible(true, true, false, true, true, true));
    }

    @Test
    void nonFireDamageIsNotEligible() {
        assertFalse(eligible(true, true, false, false, false, true));
    }

    @Test
    void fireDamageWithoutACampfireIsNotEligible() {
        assertFalse(eligible(true, true, false, false, true, false));
    }

    @Test
    void campfireIsOnlyCampfireDamageWhenBothFactsHold() {
        assertTrue(CampfireFleePolicy.isCampfireDamage(true, true));
        assertFalse(CampfireFleePolicy.isCampfireDamage(true, false));
        assertFalse(CampfireFleePolicy.isCampfireDamage(false, true));
    }

    @Test
    void radiusIsClamped() {
        assertEquals(CampfireFleePolicy.MIN_RADIUS, CampfireFleePolicy.clampRadius(-5));
        assertEquals(CampfireFleePolicy.MAX_RADIUS, CampfireFleePolicy.clampRadius(99));
        assertEquals(CampfireFleePolicy.DEFAULT_RADIUS, CampfireFleePolicy.clampRadius(CampfireFleePolicy.DEFAULT_RADIUS));
    }

    @Test
    void arrivalAndRetargetThresholds() {
        assertTrue(CampfireFleePolicy.arrived(0.0D));
        assertTrue(CampfireFleePolicy.arrived(CampfireFleePolicy.ARRIVED_SQR));
        assertFalse(CampfireFleePolicy.arrived(CampfireFleePolicy.ARRIVED_SQR + 0.5D));
        assertFalse(CampfireFleePolicy.needsRetarget(CampfireFleePolicy.RETARGET_TICKS - 1));
        assertTrue(CampfireFleePolicy.needsRetarget(CampfireFleePolicy.RETARGET_TICKS));
    }
}
