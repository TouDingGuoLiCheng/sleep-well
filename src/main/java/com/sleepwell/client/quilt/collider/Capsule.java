package com.sleepwell.client.quilt.collider;

import org.joml.Vector3f;

/**
 * One segment of a capsule chain: the solids the cloth has to slide over. Segments are produced per bone,
 * so they follow the pose instead of describing a body-shaped blob.
 */
public final class Capsule {
    public final Vector3f a;
    public final Vector3f b;
    public final float radius;
    /** Hard solids push the cloth out; soft ones only repel it, so hair and tails never punch through. */
    public final boolean hard;
    public final String name;

    public Capsule(Vector3f a, Vector3f b, float radius, boolean hard, String name) {
        this.a = a;
        this.b = b;
        this.radius = radius;
        this.hard = hard;
        this.name = name;
    }

    public float length() {
        return a.distance(b);
    }
}
