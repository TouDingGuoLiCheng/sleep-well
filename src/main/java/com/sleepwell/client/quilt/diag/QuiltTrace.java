package com.sleepwell.client.quilt.diag;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered, one-shot trace of the whole outline pipeline.
 * <p>
 * Every hop reports once into the log with a step number and what it produced; repeated hits are counted
 * instead of repeated. {@link #last()} answers the only question that matters when nothing shows up:
 * <em>how far did it get?</em> The answer is also pushed to the chat bar, because a player testing the mod
 * is not going to open latest.log mid-session.
 */
public final class QuiltTrace {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Integer> COUNTS = new LinkedHashMap<>();
    private static final Map<String, String> DETAILS = new LinkedHashMap<>();
    private static volatile String lastStep = "none";
    private static volatile String lastDetail = "";

    private QuiltTrace() {
    }

    /** Records a step. First hit logs; later hits only bump a counter. */
    public static synchronized void step(String key, String detail) {
        lastStep = key;
        lastDetail = detail == null ? "" : detail;
        Integer count = COUNTS.get(key);
        if (count == null) {
            COUNTS.put(key, 1);
            DETAILS.put(key, lastDetail);
            LOGGER.info("[sleep_well] trace #{} {} | {}", COUNTS.size(), key, lastDetail);
        } else {
            COUNTS.put(key, count + 1);
        }
    }

    /** A repeat counter that should not become the "last step" on its own. */
    public static synchronized void beat(String key) {
        COUNTS.merge(key, 1, Integer::sum);
    }

    public static String last() {
        return lastStep;
    }

    public static String lastDetail() {
        return lastDetail;
    }

    /** {@code step=count step=count ...} for the chat report. */
    public static synchronized String summary() {
        StringBuilder builder = new StringBuilder();
        COUNTS.forEach((key, count) -> builder.append(key).append('=').append(count).append(' '));
        return builder.toString().trim();
    }

    public static synchronized void reset() {
        COUNTS.clear();
        DETAILS.clear();
        lastStep = "none";
        lastDetail = "";
    }
}
