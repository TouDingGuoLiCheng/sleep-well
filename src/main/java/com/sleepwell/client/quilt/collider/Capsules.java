package com.sleepwell.client.quilt.collider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.joml.Vector3f;

/** Turns the per-cube boxes of one pose into capsule chains, grouped by bone. */
public final class Capsules {
    /** Longest segment the cloth steps over; longer bones are split so a capsule never spans a whole leg. */
    private static final float MAX_SEGMENT = 0.6F;
    private static final float MIN_RADIUS = 0.03F;
    private static final float MAX_RADIUS = 0.55F;

    private static final String[] SOFT = {
            "hair", "tail", "ear", "wing", "ribbon", "bow", "skirt", "dress", "cloth", "cape",
            "sleeve", "hat", "flower", "braid", "ponytail", "accessor", "acc_", "pad", "frill"
    };

    private Capsules() {
    }

    public static List<Capsule> fit(List<QuiltBox> boxes) {
        Map<String, List<QuiltBox>> byBone = new LinkedHashMap<>();
        for (QuiltBox box : boxes) {
            byBone.computeIfAbsent(box.name == null ? "?" : box.name, key -> new ArrayList<>()).add(box);
        }
        List<Capsule> out = new ArrayList<>(byBone.size() * 2);
        for (Map.Entry<String, List<QuiltBox>> entry : byBone.entrySet()) {
            fitBone(entry.getKey(), entry.getValue(), out);
        }
        return out;
    }

    private static void fitBone(String bone, List<QuiltBox> boxes, List<Capsule> out) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (QuiltBox box : boxes) {
            for (int corner = 0; corner < 8; corner++) {
                minX = Math.min(minX, box.x(corner));
                minY = Math.min(minY, box.y(corner));
                minZ = Math.min(minZ, box.z(corner));
                maxX = Math.max(maxX, box.x(corner));
                maxY = Math.max(maxY, box.y(corner));
                maxZ = Math.max(maxZ, box.z(corner));
            }
        }
        float dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
        Vector3f axis;
        float length;
        if (dy >= dx && dy >= dz) {
            axis = new Vector3f(0, 1, 0);
            length = dy;
        } else if (dx >= dz) {
            axis = new Vector3f(1, 0, 0);
            length = dx;
        } else {
            axis = new Vector3f(0, 0, 1);
            length = dz;
        }
        // Radius so the capsule swallows every cube of the bone, not just its axis.
        float across;
        if (dx == length) {
            across = (float) Math.sqrt(dy * dy + dz * dz) * 0.5F;
        } else if (dy == length) {
            across = (float) Math.sqrt(dx * dx + dz * dz) * 0.5F;
        } else {
            across = (float) Math.sqrt(dx * dx + dy * dy) * 0.5F;
        }
        float radius = Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, across));
        Vector3f center = new Vector3f((minX + maxX) * 0.5F, (minY + maxY) * 0.5F, (minZ + maxZ) * 0.5F);
        float half = Math.max(length * 0.5F, 0.001F);
        int segments = Math.max(1, (int) Math.ceil(length / MAX_SEGMENT));
        float step = length / segments;
        boolean hard = !isSoft(bone);
        for (int i = 0; i < segments; i++) {
            float t0 = -half + step * i;
            float t1 = t0 + step;
            Vector3f a = new Vector3f(center).fma(t0, axis);
            Vector3f b = new Vector3f(center).fma(t1, axis);
            out.add(new Capsule(a, b, radius, hard, bone));
        }
    }

    public static boolean isSoft(String bone) {
        if (bone == null) {
            return false;
        }
        String lower = bone.toLowerCase(Locale.ROOT);
        for (String key : SOFT) {
            if (lower.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public static String summary(List<Capsule> capsules) {
        if (capsules.isEmpty()) {
            return "capsules=0";
        }
        int hard = 0;
        float radiusSum = 0, maxRadius = 0, lengthSum = 0;
        for (Capsule capsule : capsules) {
            if (capsule.hard) {
                hard++;
            }
            radiusSum += capsule.radius;
            maxRadius = Math.max(maxRadius, capsule.radius);
            lengthSum += capsule.length();
        }
        return String.format(Locale.ROOT, "capsules=%d hard=%d soft=%d rAvg=%.3f rMax=%.3f lenAvg=%.3f",
                capsules.size(), hard, capsules.size() - hard, radiusSum / capsules.size(), maxRadius,
                lengthSum / capsules.size());
    }
}
