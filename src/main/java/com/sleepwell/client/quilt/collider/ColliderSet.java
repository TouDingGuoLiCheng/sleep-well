package com.sleepwell.client.quilt.collider;

import java.util.List;

/**
 * The maid's full sleeping outline for one frame: every cube that was actually drawn, in world space.
 */
public final class ColliderSet {
    public final List<QuiltBox> boxes;
    /** minX, minY, minZ, maxX, maxY, maxZ (world). */
    public final float[] bounds;
    /** Cheap "did she move/reshape" stamp: count plus the first corners. */
    public final long stamp;
    /** Tick the capture was made on; -1 when never captured. */
    public final int tick;

    public ColliderSet(List<QuiltBox> boxes, float[] bounds, long stamp, int tick) {
        this.boxes = boxes;
        this.bounds = bounds;
        this.stamp = stamp;
        this.tick = tick;
    }

    public int hardCount() {
        int hard = 0;
        for (QuiltBox box : boxes) {
            if (box.hard) {
                hard++;
            }
        }
        return hard;
    }
}
