package com.sleepwell.winter;

import java.util.Collection;
import java.util.List;

/**
 * Pure rules for "do not send her to her work point in winter".
 * <p>
 * Task ids are compared both as full resource locations and as bare paths, so a config value of
 * {@code farm} also matches {@code touhou_little_maid:farm}.
 */
public final class WinterSkipPolicy {
    /** Default skip list: work tasks that need her outside. {@code berries_farm} is Maidsoul Kitchen's berry task. */
    public static final List<String> DEFAULT_SKIP_TASKS = List.of(
            "farm", "cocoa", "melon", "sugar_cane", "grass", "snow",
            "feed_animal", "milk", "shears", "fishing", "torch", "honey", "berries_farm");

    private WinterSkipPolicy() {
    }

    public static boolean shouldSkip(boolean winter, boolean enabled, String taskUid, Collection<? extends String> skipTasks) {
        if (!winter || !enabled || taskUid == null || taskUid.isEmpty()) {
            return false;
        }
        return matches(taskUid, skipTasks);
    }

    /** Swap back when winter is over, the switch is off, or the player changed her task in the meantime. */
    public static boolean shouldRestore(boolean skipped, boolean winter, boolean enabled, String currentTaskUid, String idleTaskUid) {
        if (!skipped) {
            return false;
        }
        if (!winter || !enabled) {
            return true;
        }
        return currentTaskUid == null || !currentTaskUid.equals(idleTaskUid);
    }

    public static boolean matches(String taskUid, Collection<? extends String> skipTasks) {
        if (skipTasks == null) {
            return false;
        }
        String bare = bare(taskUid);
        for (String entry : skipTasks) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String candidate = entry.trim();
            if (candidate.equals(taskUid) || bare(candidate).equals(bare)) {
                return true;
            }
        }
        return false;
    }

    static String bare(String uid) {
        int colon = uid.indexOf(':');
        return colon >= 0 ? uid.substring(colon + 1) : uid;
    }
}
