package com.sleepwell.winter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Pure helpers for the "which work tasks count as work in winter" list.
 * <p>
 * Entries are compared by bare name ({@code farm} == {@code touhou_little_maid:farm}), matching
 * {@link WinterSkipPolicy}. Entries a keeper typed in full stay as typed; only new entries are stored bare.
 */
public final class SkipTaskSelection {
    private SkipTaskSelection() {
    }

    public static String bare(String uid) {
        if (uid == null) {
            return "";
        }
        String trimmed = uid.trim();
        int colon = trimmed.indexOf(':');
        return colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
    }

    /** Trimmed, deduplicated by bare name (first spelling wins) and sorted for a stable config file. */
    public static List<String> normalize(Collection<? extends String> uids) {
        List<String> out = new ArrayList<>();
        if (uids == null) {
            return out;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String entry : uids) {
            String bare = bare(entry);
            if (bare.isEmpty() || !seen.add(bare)) {
                continue;
            }
            out.add(entry == null ? bare : entry.trim());
        }
        out.sort(Comparator.comparing(SkipTaskSelection::bare));
        return out;
    }

    public static boolean contains(Collection<? extends String> uids, String uid) {
        String target = bare(uid);
        if (uids == null || target.isEmpty()) {
            return false;
        }
        for (String entry : uids) {
            if (bare(entry).equals(target)) {
                return true;
            }
        }
        return false;
    }

    /** Adds the task when it is missing, removes it otherwise; always returns a normalized list. */
    public static List<String> toggle(Collection<? extends String> uids, String uid) {
        String target = bare(uid);
        List<String> out = new ArrayList<>();
        boolean removed = false;
        if (uids != null) {
            for (String entry : uids) {
                if (!removed && bare(entry).equals(target)) {
                    removed = true;
                    continue;
                }
                out.add(entry);
            }
        }
        if (!removed && !target.isEmpty()) {
            out.add(target);
        }
        return normalize(out);
    }
}
