package com.sleepwell.maid;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Per-maid settings for Sleep Well. Stored on the maid through a capability, so it follows her
 * (dimension changes, save reload) instead of a global config file.
 */
public final class MaidSettings implements INBTSerializable<CompoundTag> {
    public static final String TAG_WINTER_SKIP = "WinterSkip";
    public static final String TAG_SAVED_TASK = "SavedTask";
    public static final String TAG_LIE_IN = "LieIn";

    private boolean winterSkip;
    private boolean lieIn = true;
    private String savedTaskId = "";

    public MaidSettings(boolean winterSkip) {
        this(winterSkip, true);
    }

    public MaidSettings(boolean winterSkip, boolean lieIn) {
        this.winterSkip = winterSkip;
        this.lieIn = lieIn;
    }

    /** Skip her current work task while it is winter. */
    public boolean winterSkip() {
        return winterSkip;
    }

    public void setWinterSkip(boolean winterSkip) {
        this.winterSkip = winterSkip;
    }

    /** Whether this maid may sleep in and take daytime naps. */
    public boolean lieIn() {
        return lieIn;
    }

    public void setLieIn(boolean lieIn) {
        this.lieIn = lieIn;
    }

    /** Task uid she had before being swapped to the idle task; empty when not swapped. */
    public String savedTaskId() {
        return savedTaskId;
    }

    public void setSavedTaskId(String savedTaskId) {
        this.savedTaskId = savedTaskId == null ? "" : savedTaskId;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_WINTER_SKIP, winterSkip);
        tag.putString(TAG_SAVED_TASK, savedTaskId);
        tag.putBoolean(TAG_LIE_IN, lieIn);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        winterSkip = !tag.contains(TAG_WINTER_SKIP) || tag.getBoolean(TAG_WINTER_SKIP);
        savedTaskId = tag.getString(TAG_SAVED_TASK);
        // Missing tag = a maid from before this switch existed: she defaults to allowed.
        lieIn = !tag.contains(TAG_LIE_IN) || tag.getBoolean(TAG_LIE_IN);
    }
}
