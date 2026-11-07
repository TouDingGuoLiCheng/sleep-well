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
    /**
     * True when captured from the gecko render layer, which sees the model's real bones. A bedrock-part
     * capture is in-her-render-pass too, but it describes the plain body underneath, so it must never
     * replace a model-specific outline that was just captured.
     */
    public boolean layerSource;
    /** Corner coordinates are already in the space the line batch draws in (TLM's own render matrix). */
    public boolean viewSpace;
    /** Capsule chain fitted from {@link #boxes}: the collision form the cloth actually solves against. */
    public java.util.List<Capsule> capsules = java.util.List.of();
    /** Maps the captured view-space corners back to world space; filled at the end of the capturing frame. */
    public org.joml.Matrix4f viewToWorld;
    /** Set while this frame's pose has not been applied yet; see MaidColliderCapture.resolveWorldMatrices. */
    public boolean pendingWorldMatrix;
    /**
     * The render frame this set was captured in. View-space corners are only valid together with the pose
     * of that very frame; using a stale set with a newer camera shifts the whole body.
     */
    public long captureFrame;

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
