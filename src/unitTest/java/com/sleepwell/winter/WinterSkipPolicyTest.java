package com.sleepwell.winter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WinterSkipPolicyTest {
    private static final List<String> SKIP = List.of("farm", "feed_animal");

    @Test
    void winterWorkTaskIsSkipped() {
        assertTrue(WinterSkipPolicy.shouldSkip(true, true, "touhou_little_maid:farm", SKIP));
    }

    @Test
    void notWinterIsNotSkipped() {
        assertFalse(WinterSkipPolicy.shouldSkip(false, true, "touhou_little_maid:farm", SKIP));
    }

    @Test
    void disabledSwitchIsNotSkipped() {
        assertFalse(WinterSkipPolicy.shouldSkip(true, false, "touhou_little_maid:farm", SKIP));
    }

    @Test
    void taskOutsideTheListIsNotSkipped() {
        assertFalse(WinterSkipPolicy.shouldSkip(true, true, "touhou_little_maid:idle", SKIP));
    }

    @Test
    void namespacesAreOptional() {
        assertTrue(WinterSkipPolicy.matches("touhou_little_maid:farm", List.of("farm")));
        assertTrue(WinterSkipPolicy.matches("farm", List.of("touhou_little_maid:farm")));
        assertFalse(WinterSkipPolicy.matches("touhou_little_maid:melon", List.of("farm")));
    }

    @Test
    void defaultListCoversBerryPicking() {
        assertTrue(WinterSkipPolicy.shouldSkip(true, true,
                "maidsoulkitchen:berries_farm", WinterSkipPolicy.DEFAULT_SKIP_TASKS));
    }

    @Test
    void restoreWhenWinterEnds() {
        assertTrue(WinterSkipPolicy.shouldRestore(true, false, true,
                "touhou_little_maid:idle", "touhou_little_maid:idle"));
    }

    @Test
    void restoreWhenSwitchTurnsOff() {
        assertTrue(WinterSkipPolicy.shouldRestore(true, true, false,
                "touhou_little_maid:idle", "touhou_little_maid:idle"));
    }

    @Test
    void restoreWhenThePlayerPickedAnotherTask() {
        assertTrue(WinterSkipPolicy.shouldRestore(true, true, true,
                "touhou_little_maid:attack", "touhou_little_maid:idle"));
    }

    @Test
    void staySkippedWhileWinterLasts() {
        assertFalse(WinterSkipPolicy.shouldRestore(true, true, true,
                "touhou_little_maid:idle", "touhou_little_maid:idle"));
        assertFalse(WinterSkipPolicy.shouldRestore(false, true, true,
                "touhou_little_maid:idle", "touhou_little_maid:idle"));
    }
}
