package com.sleepwell.client.quilt.collider;

/**
 * One cube of the maid's sleeping model, in world space.
 * <p>
 * Stored as eight corners rather than a centre + size because the cube is followed through a bone
 * rotation: a rotated box is not axis aligned, and the cloth solver needs the real edges to build a
 * capsule out of them.
 */
public final class QuiltBox {
    /** 8 corners × 3 floats, world space. */
    public final float[] corners = new float[24];
    /** Capsule radius derived from the shortest edge; never below {@link #MIN_RADIUS}. */
    public float radius;
    /** Bone name, debug only. */
    public final String name;
    /** Body parts get real cloth collision; hair, tails and trinkets only repel. */
    public boolean hard;

    public static final float MIN_RADIUS = 0.035F;

    public QuiltBox(String name) {
        this.name = name;
    }

    public void setCorner(int index, float x, float y, float z) {
        int base = index * 3;
        corners[base] = x;
        corners[base + 1] = y;
        corners[base + 2] = z;
    }

    public float x(int index) {
        return corners[index * 3];
    }

    public float y(int index) {
        return corners[index * 3 + 1];
    }

    public float z(int index) {
        return corners[index * 3 + 2];
    }

    /** Longest edge, used as the capsule axis. */
    public float longestEdge() {
        float longest = 0.0F;
        // Edges 0-1, 0-2, 0-4 of a box whose corner order is (0,0,0)(1,0,0)(1,1,0)(0,1,0)(0,0,1)...
        longest = Math.max(longest, distance(0, 1));
        longest = Math.max(longest, distance(0, 2));
        longest = Math.max(longest, distance(0, 4));
        longest = Math.max(longest, distance(1, 3));
        return longest;
    }

    public float shortestEdge() {
        float shortest = Math.min(Math.min(distance(0, 1), distance(0, 2)), distance(0, 4));
        return shortest <= 0.0F ? 0.0F : shortest;
    }

    private float distance(int a, int b) {
        float dx = x(a) - x(b);
        float dy = y(a) - y(b);
        float dz = z(a) - z(b);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
