package com.sleepwell.client;

import com.sleepwell.client.quilt.QuiltStyle;
import com.sleepwell.unlock.UnlockRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The client's mirror of its own unlock state: which faces are open and how far the locked ones have come.
 * The wall reads it, and the toast is the one the server asked for.
 */
public final class ClientUnlocks {
    private static final Set<String> UNLOCKED = new HashSet<>();
    private static final Map<String, int[]> PROGRESS = new HashMap<>();

    private ClientUnlocks() {
    }

    public static void receive(List<String> unlocked, List<UnlockRules.Progress> progress) {
        UNLOCKED.clear();
        UNLOCKED.addAll(unlocked);
        PROGRESS.clear();
        for (UnlockRules.Progress row : progress) {
            PROGRESS.put(row.target(), new int[]{row.progress(), row.goal()});
        }
    }

    /** True when the target is open; targets with no rule are always open. */
    public static boolean isUnlocked(String target) {
        return !PROGRESS.containsKey(target) || UNLOCKED.contains(target);
    }

    /** {progress, goal}, or null when the target has no rule. */
    public static int[] progressOf(String target) {
        return PROGRESS.get(target);
    }

    public static void toast(String target) {
        SystemToast.add(Minecraft.getInstance().getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                Component.translatable("toast.sleep_well.unlocked"),
                Component.literal(QuiltStyle.of(target).displayName));
    }
}
