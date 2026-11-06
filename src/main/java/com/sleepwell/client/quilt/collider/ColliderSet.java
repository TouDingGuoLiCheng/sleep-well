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
    /** True when built without the render hook, from the model's last animated pose. */
    public final boolean direct;
    /** True when produced inside the maid's own render pass: that is the only trustworthy source. */
    public boolean fromLayer;

    public ColliderSet(List<QuiltBox> boxes, float[] bounds, long stamp, int tick, boolean direct) {
        this.boxes = boxes;
        this.bounds = bounds;
        this.stamp = stamp;
        this.tick = tick;
        this.direct = direct;
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
