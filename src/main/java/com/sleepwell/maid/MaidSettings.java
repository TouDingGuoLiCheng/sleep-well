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
    public static final String TAG_QUILT_BORDER = "QuiltBorder";
    public static final String TAG_QUILT_PARAMS = "QuiltParams";
    public static final String TAG_SLEEP_COUNT = "SleepCount";
    public static final String DEFAULT_QUILT = "solid";

    private boolean winterSkip;
    private boolean lieIn = true;
    /** How many times this maid has gone to sleep and woken up again; read by the achievement page. */
    private int sleepCount;
    private String savedTaskId = "";
    private String quiltStyle = DEFAULT_QUILT;
    private String quiltBorder = "";
    private com.sleepwell.client.quilt.QuiltParams quiltParams = com.sleepwell.client.quilt.QuiltParams.DEFAULT;

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

    /** One complete sleep. The count is per maid, not per player. */
    public int sleepCount() {
        return this.sleepCount;
    }

    public int incrementSleepCount() {
        return ++this.sleepCount;
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

    /** The numbers a player turned on her own quilt; only read when the style id is {@code custom:...}. */
    public com.sleepwell.client.quilt.QuiltParams quiltParams() {
        return quiltParams == null ? com.sleepwell.client.quilt.QuiltParams.DEFAULT : quiltParams;
    }

    public void setQuiltParams(com.sleepwell.client.quilt.QuiltParams quiltParams) {
        this.quiltParams = quiltParams == null ? com.sleepwell.client.quilt.QuiltParams.DEFAULT : quiltParams;
    }

    /** Which border is sewn around her quilt; empty means none, which is what every old save has. */
    public String quiltBorder() {
        return quiltBorder == null ? "" : quiltBorder;
    }

    public void setQuiltBorder(String quiltBorder) {
        this.quiltBorder = quiltBorder == null ? "" : quiltBorder;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_WINTER_SKIP, winterSkip);
        tag.putString(TAG_SAVED_TASK, savedTaskId);
        tag.putBoolean(TAG_LIE_IN, lieIn);
        tag.putInt(TAG_SLEEP_COUNT, sleepCount);
        tag.putString(TAG_QUILT_STYLE, quiltStyle());
        tag.putString(TAG_QUILT_BORDER, quiltBorder());
        tag.put(TAG_QUILT_PARAMS, quiltParams().save());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        winterSkip = !tag.contains(TAG_WINTER_SKIP) || tag.getBoolean(TAG_WINTER_SKIP);
        savedTaskId = tag.getString(TAG_SAVED_TASK);
        // Missing tag = a maid from before this switch existed: she defaults to allowed.
        lieIn = !tag.contains(TAG_LIE_IN) || tag.getBoolean(TAG_LIE_IN);
        sleepCount = tag.getInt(TAG_SLEEP_COUNT);
        quiltStyle = tag.contains(TAG_QUILT_STYLE) ? tag.getString(TAG_QUILT_STYLE) : DEFAULT_QUILT;
        // Missing tag = a maid from before borders existed: no border, no visual change.
        quiltBorder = tag.contains(TAG_QUILT_BORDER) ? tag.getString(TAG_QUILT_BORDER) : "";
        quiltParams = com.sleepwell.client.quilt.QuiltParams.load(tag.getCompound(TAG_QUILT_PARAMS));
    }
}
