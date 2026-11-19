package com.sleepwell.winter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkipTaskSelectionTest {
    @Test
    void bareNameDropsTheNamespace() {
        assertEquals("farm", SkipTaskSelection.bare("touhou_little_maid:farm"));
        assertEquals("farm", SkipTaskSelection.bare("farm"));
        assertEquals("", SkipTaskSelection.bare(null));
    }

    @Test
    void normalizeTrimsDropsDuplicatesAndSorts() {
        List<String> out = SkipTaskSelection.normalize(
                List.of(" melon ", "touhou_little_maid:melon", "farm", "", "berries_farm"));
        assertEquals(List.of("berries_farm", "farm", "melon"), out);
    }

    @Test
    void normalizeKeepsTheFirstSpelling() {
        assertEquals(List.of("touhou_little_maid:farm"), SkipTaskSelection.normalize(
                List.of("touhou_little_maid:farm", "farm")));
    }

    @Test
    void containsComparesByBareName() {
        assertTrue(SkipTaskSelection.contains(List.of("farm"), "touhou_little_maid:farm"));
        assertFalse(SkipTaskSelection.contains(List.of("farm"), "touhou_little_maid:melon"));
        assertFalse(SkipTaskSelection.contains(List.of(), "farm"));
    }

    @Test
    void toggleAddsWhenMissingAndRemovesWhenPresent() {
        assertEquals(List.of("farm", "melon"), SkipTaskSelection.toggle(List.of("melon"), "farm"));
        assertEquals(List.of("melon"), SkipTaskSelection.toggle(List.of("farm", "melon"), "touhou_little_maid:farm"));
        assertEquals(List.of(), SkipTaskSelection.toggle(List.of("melon"), "melon"));
    }

    @Test
    void togglingTwiceRestoresTheOriginalList() {
        List<String> once = SkipTaskSelection.toggle(List.of("farm", "melon"), "berries_farm");
        assertEquals(List.of("berries_farm", "farm", "melon"), once);
        assertEquals(List.of("farm", "melon"), SkipTaskSelection.toggle(once, "berries_farm"));
    }

    @Test
    void emptyInputIsSafe() {
        assertTrue(SkipTaskSelection.normalize(null).isEmpty());
        assertTrue(SkipTaskSelection.toggle(null, " ").isEmpty());
    }
}
