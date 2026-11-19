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
    private static final float MAX_SEGMENT = 0.45F;
    private static final float MIN_RADIUS = 0.03F;
    /** A limb is a few pixels thick; anything fatter than this is a fitting bug, not anatomy. */
    private static final float MAX_RADIUS = 0.24F;

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
        int count = boxes.size();
        float[] centerX = new float[count], centerY = new float[count], centerZ = new float[count];
        float[] half = new float[count];
        float meanX = 0, meanY = 0, meanZ = 0;
        for (int i = 0; i < count; i++) {
            QuiltBox box = boxes.get(i);
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int corner = 0; corner < 8; corner++) {
                minX = Math.min(minX, box.x(corner));
                minY = Math.min(minY, box.y(corner));
                minZ = Math.min(minZ, box.z(corner));
                maxX = Math.max(maxX, box.x(corner));
                maxY = Math.max(maxY, box.y(corner));
                maxZ = Math.max(maxZ, box.z(corner));
            }
            centerX[i] = (minX + maxX) * 0.5F;
            centerY[i] = (minY + maxY) * 0.5F;
            centerZ[i] = (minZ + maxZ) * 0.5F;
            // A cube's own half-size is the honest local thickness; the group aggregate is only used to
            // find the direction.
            half[i] = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) * 0.5F;
            meanX += centerX[i];
            meanY += centerY[i];
            meanZ += centerZ[i];
        }
        meanX /= count;
        meanY /= count;
        meanZ /= count;
        // Dominant direction from the cube centres: works for diagonally posed limbs, where a world-axis
        // bounding box produced those fat wandering rings.
        double cxx = 0, cxy = 0, cxz = 0, cyy = 0, cyz = 0, czz = 0;
        for (int i = 0; i < count; i++) {
            double dx = centerX[i] - meanX, dy = centerY[i] - meanY, dz = centerZ[i] - meanZ;
            cxx += dx * dx;
            cxy += dx * dy;
            cxz += dx * dz;
            cyy += dy * dy;
            cyz += dy * dz;
            czz += dz * dz;
        }
        org.joml.Vector3f axis = new org.joml.Vector3f(0, 1, 0);
        if (count > 2 && (cxx + cyy + czz) > 1.0E-7) {
            axis.set(1, 0, 0);
            for (int iter = 0; iter < 24; iter++) {
                float x = (float) (cxx * axis.x + cxy * axis.y + cxz * axis.z);
                float y = (float) (cxy * axis.x + cyy * axis.y + cyz * axis.z);
                float z = (float) (cxz * axis.x + cyz * axis.y + czz * axis.z);
                axis.set(x, y, z);
                if (axis.lengthSquared() < 1.0E-9F) {
                    axis.set(0, 1, 0);
                    break;
                }
                axis.normalize();
            }
        }
        float tMin = Float.MAX_VALUE, tMax = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float t = (centerX[i] - meanX) * axis.x + (centerY[i] - meanY) * axis.y
                    + (centerZ[i] - meanZ) * axis.z;
            tMin = Math.min(tMin, t);
            tMax = Math.max(tMax, t);
        }
        // 90th percentile of the perpendicular offsets plus the local cube half-size: robust against a
        // single wide cube dragging the whole limb fat.
        float[] spread = new float[count];
        for (int i = 0; i < count; i++) {
            float ox = centerX[i] - meanX, oy = centerY[i] - meanY, oz = centerZ[i] - meanZ;
            float t = ox * axis.x + oy * axis.y + oz * axis.z;
            float px = ox - axis.x * t, py = oy - axis.y * t, pz = oz - axis.z * t;
            spread[i] = (float) Math.sqrt(px * px + py * py + pz * pz) + half[i];
        }
        java.util.Arrays.sort(spread);
        float radius = spread[(int) Math.min(count - 1, Math.floor(count * 0.9))];
        radius = Math.min(MAX_RADIUS, Math.max(MIN_RADIUS, radius));
        float length = tMax - tMin;
        if (length < 0.04F) {
            // Roundish blob: one sphere-like segment is enough.
            length = 0.04F;
        }
        int segments = Math.max(1, (int) Math.ceil(length / MAX_SEGMENT));
        float step = length / segments;
        boolean hard = !isSoft(bone);
        org.joml.Vector3f base = new org.joml.Vector3f(meanX, meanY, meanZ).fma(tMin, axis);
        for (int i = 0; i < segments; i++) {
            Vector3f a = new Vector3f(base).fma(step * i, axis);
            Vector3f b = new Vector3f(base).fma(step * (i + 1), axis);
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