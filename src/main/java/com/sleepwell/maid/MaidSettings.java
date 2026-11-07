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
    public static final String TAG_QUILT_STYLE = "QuiltStyle";
    public static final String DEFAULT_QUILT = "thick_gingham";

    private boolean winterSkip;
    private boolean lieIn = true;
    private String savedTaskId = "";
    private String quiltStyle = DEFAULT_QUILT;

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

    /** Which quilt covers her; an id from {@code QuiltStyle}. */
    public String quiltStyle() {
        return quiltStyle == null || quiltStyle.isEmpty() ? DEFAULT_QUILT : quiltStyle;
    }

    public void setQuiltStyle(String quiltStyle) {
        this.quiltStyle = quiltStyle == null || quiltStyle.isEmpty() ? DEFAULT_QUILT : quiltStyle;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_WINTER_SKIP, winterSkip);
        tag.putString(TAG_SAVED_TASK, savedTaskId);
        tag.putBoolean(TAG_LIE_IN, lieIn);
        tag.putString(TAG_QUILT_STYLE, quiltStyle());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        winterSkip = !tag.contains(TAG_WINTER_SKIP) || tag.getBoolean(TAG_WINTER_SKIP);
        savedTaskId = tag.getString(TAG_SAVED_TASK);
        // Missing tag = a maid from before this switch existed: she defaults to allowed.
        lieIn = !tag.contains(TAG_LIE_IN) || tag.getBoolean(TAG_LIE_IN);
        quiltStyle = tag.contains(TAG_QUILT_STYLE) ? tag.getString(TAG_QUILT_STYLE) : DEFAULT_QUILT;
    }
}
