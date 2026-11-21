package com.sleepwell.client.quilt;

import net.minecraft.nbt.CompoundTag;

/**
 * The numbers a player may turn on a quilt of their own.
 * <p>
 * A built-in quilt is one fixed set of these, baked into a constant. A custom quilt is the same numbers with a
 * player's image in place of our artwork, edited in the quilt screen - so everything the editor changes is here,
 * and nothing the renderer reads lives outside it.
 */
public final class QuiltParams {
    /** Repeat the image across the cloth, or stretch it once over the whole sheet. */
    public final boolean tiled;
    public final float repeatU;
    public final float repeatV;
    /** A third wider than a plain sheet. */
    public final boolean wideAcross;
    /** Visible thickness, underside and rim brightness, 0..1. */
    public final float shell;
    public final float underShade;
    public final float rimShade;
    /** How far the cloth floats above her, and how hard it resists bending, 0..1. */
    public final float loft;
    public final float body;
    /** Let the bed's dye tint the cloth. */
    public final boolean dyeTinted;

    public QuiltParams(boolean tiled, float repeatU, float repeatV, boolean wideAcross, float shell,
                       float underShade, float rimShade, float loft, float body, boolean dyeTinted) {
        this.tiled = tiled;
        this.repeatU = clamp(repeatU, 0.25F, 8.0F);
        this.repeatV = clamp(repeatV, 0.25F, 8.0F);
        this.wideAcross = wideAcross;
        this.shell = clamp(shell, 0.0F, 0.12F);
        this.underShade = clamp(underShade, 0.3F, 1.0F);
        this.rimShade = clamp(rimShade, 0.3F, 1.0F);
        this.loft = clamp(loft, 0.0F, 1.0F);
        this.body = clamp(body, 0.0F, 1.0F);
        this.dyeTinted = dyeTinted;
    }

    /** A plain sheet: exactly what the default quilt is, so an untouched custom quilt still looks sane. */
    public static final QuiltParams DEFAULT = new QuiltParams(false, 1.0F, 1.0F, false,
            0.038F, 0.76F, 0.66F, 0.35F, 0.50F, true);

    public QuiltParams withTiled(boolean tiled) {
        return new QuiltParams(tiled, this.repeatU, this.repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, this.dyeTinted);
    }

    public QuiltParams withRepeat(float repeatU, float repeatV) {
        return new QuiltParams(this.tiled, repeatU, repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, this.dyeTinted);
    }

    public QuiltParams withWide(boolean wideAcross) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, this.dyeTinted);
    }

    public QuiltParams withTint(boolean dyeTinted) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, dyeTinted);
    }

    /** Every hand pulled from a built-in quilt: picking 羽绒被 here means picking its numbers. */
    public static QuiltParams of(QuiltStyle style) {
        return new QuiltParams(style.tiles(), style.repeatU, style.repeatV, style.wideAcross, style.shell,
                style.underShade, style.rimShade, style.loft, style.body, style.dyeTinted);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Tiled", this.tiled);
        tag.putFloat("RepeatU", this.repeatU);
        tag.putFloat("RepeatV", this.repeatV);
        tag.putBoolean("Wide", this.wideAcross);
        tag.putFloat("Shell", this.shell);
        tag.putFloat("Under", this.underShade);
        tag.putFloat("Rim", this.rimShade);
        tag.putFloat("Loft", this.loft);
        tag.putFloat("Body", this.body);
        tag.putBoolean("Tint", this.dyeTinted);
        return tag;
    }

    public static QuiltParams load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return DEFAULT;
        }
        return new QuiltParams(tag.getBoolean("Tiled"), tag.getFloat("RepeatU"), tag.getFloat("RepeatV"),
                tag.getBoolean("Wide"), tag.getFloat("Shell"), tag.getFloat("Under"), tag.getFloat("Rim"),
                tag.getFloat("Loft"), tag.getFloat("Body"), tag.getBoolean("Tint"));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
