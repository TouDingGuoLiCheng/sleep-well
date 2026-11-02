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

    private boolean winterSkip;
    private String savedTaskId = "";

    public MaidSettings(boolean winterSkip) {
        this.winterSkip = winterSkip;
    }

    /** Skip her current work task while it is winter. */
    public boolean winterSkip() {
        return winterSkip;
    }

    public void setWinterSkip(boolean winterSkip) {
        this.winterSkip = winterSkip;
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
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        winterSkip = !tag.contains(TAG_WINTER_SKIP) || tag.getBoolean(TAG_WINTER_SKIP);
        savedTaskId = tag.getString(TAG_SAVED_TASK);
    }
}
