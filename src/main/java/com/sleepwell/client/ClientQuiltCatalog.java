package com.sleepwell.client;

import com.sleepwell.quilt.SavedQuilt;

import java.util.ArrayList;
import java.util.List;

/** This client's copy of the world's quilt shelf: what the wall lists under the built-in faces. */
public final class ClientQuiltCatalog {
    private static volatile List<SavedQuilt> entries = List.of();

    private ClientQuiltCatalog() {
    }

    public static List<SavedQuilt> all() {
        return entries;
    }

    public static void receive(List<SavedQuilt> quilts) {
        entries = List.copyOf(quilts == null ? List.<SavedQuilt>of() : quilts);
    }

    public static String nameOf(String hash) {
        for (SavedQuilt quilt : entries) {
            if (quilt.hash().equals(hash)) {
                return quilt.name();
            }
        }
        return "";
    }

    /** Forgets a picture that turned out not to exist, so a broken entry cannot be picked over and over. */
    public static void dropMissing() {
        List<SavedQuilt> alive = new ArrayList<>();
        for (SavedQuilt quilt : entries) {
            if (QuiltImageCache.has(quilt.hash())) {
                alive.add(quilt);
            }
        }
        if (alive.size() != entries.size()) {
            entries = List.copyOf(alive);
        }
    }
}
