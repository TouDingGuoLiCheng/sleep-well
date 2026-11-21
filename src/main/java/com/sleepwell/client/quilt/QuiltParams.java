package com.sleepwell.client.quilt;

import net.minecraft.nbt.CompoundTag;

/**
 * The numbers a player may turn on a quilt of their own.
 * <p>
 * A built-in quilt is one fixed set of these, baked into a constant. A custom quilt is the same numbers with a
 * player's image in place of our artwork, edited in the quilt screen - so everything the editor changes is here,
 * and nothing the renderer reads lives outside it.
 * <p>
 * The picture itself is never stretched: the frame it is cut to has the cloth's own aspect, and the player moves
 * that frame around the image. {@code cropW == 0} means "as much of the picture as fits without distortion".
 */
public final class QuiltParams {
    /** Repeat the image across the cloth, or stretch it once over the whole sheet. */
    public final boolean tiled;
    public final float repeatU;
    public final float repeatV;
    /** A third wider than a plain sheet. */
    public final boolean wideAcross;
    /** Visible thickness, underside and rim brightness. */
    public final float shell;
    public final float underShade;
    public final float rimShade;
    /** How far the cloth floats above her, and how hard it resists bending, 0..1. */
    public final float loft;
    public final float body;
    /** Let the bed's dye tint the cloth. */
    public final boolean dyeTinted;
    /** Centre of the cut frame in image space, both 0..1. */
    public final float cropX;
    public final float cropY;
    /** How much of the picture's width the frame spans; 0 means "fit without distortion". */
    public final float cropW;

    public QuiltParams(boolean tiled, float repeatU, float repeatV, boolean wideAcross, float shell,
                       float underShade, float rimShade, float loft, float body, boolean dyeTinted) {
        this(tiled, repeatU, repeatV, wideAcross, shell, underShade, rimShade, loft, body, dyeTinted,
                0.5F, 0.5F, 0.0F);
    }

    public QuiltParams(boolean tiled, float repeatU, float repeatV, boolean wideAcross, float shell,
                       float underShade, float rimShade, float loft, float body, boolean dyeTinted,
                       float cropX, float cropY, float cropW) {
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
        this.cropX = clamp(cropX, 0.0F, 1.0F);
        this.cropY = clamp(cropY, 0.0F, 1.0F);
        this.cropW = clamp(cropW, 0.0F, 1.0F);
    }

    /** A plain sheet: exactly what the default quilt is, so an untouched custom quilt still looks sane. */
    public static final QuiltParams DEFAULT = new QuiltParams(false, 1.0F, 1.0F, false,
            0.038F, 0.76F, 0.66F, 0.35F, 0.50F, true);

    public QuiltParams withTiled(boolean tiled) {
        return new QuiltParams(tiled, this.repeatU, this.repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, this.dyeTinted,
                this.cropX, this.cropY, this.cropW);
    }

    public QuiltParams withRepeat(float repeatU, float repeatV) {
        return new QuiltParams(this.tiled, repeatU, repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, this.dyeTinted,
                this.cropX, this.cropY, this.cropW);
    }

    public QuiltParams withWide(boolean wideAcross) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, this.dyeTinted,
                this.cropX, this.cropY, this.cropW);
    }

    public QuiltParams withTint(boolean dyeTinted) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, dyeTinted,
                this.cropX, this.cropY, this.cropW);
    }

    public QuiltParams withLoft(float loft) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, loft, this.body, this.dyeTinted,
                this.cropX, this.cropY, this.cropW);
    }

    public QuiltParams withBody(float body) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, body, this.dyeTinted,
                this.cropX, this.cropY, this.cropW);
    }

    public QuiltParams withShell(float shell) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, this.wideAcross, shell,
                this.underShade, this.rimShade, this.loft, this.body, this.dyeTinted,
                this.cropX, this.cropY, this.cropW);
    }

    public QuiltParams withHand(float shell, float underShade, float rimShade, float loft, float body) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, this.wideAcross, shell,
                underShade, rimShade, loft, body, this.dyeTinted, this.cropX, this.cropY, this.cropW);
    }

    /** Moves the cut frame; values outside the picture are clamped by the constructor. */
    public QuiltParams withCrop(float cropX, float cropY, float cropW) {
        return new QuiltParams(this.tiled, this.repeatU, this.repeatV, this.wideAcross, this.shell,
                this.underShade, this.rimShade, this.loft, this.body, this.dyeTinted,
                cropX, cropY, cropW);
    }

    /**
     * The frame the picture is cut to, as width over height.
     * <p>
     * This is the cloth's own shape, measured from the sheet {@code PhysicalQuilt} actually solves: it spans
     * 1.92 blocks across the bed (over both sides, not just the mattress) and 1.43 blocks along it, so the top
     * face is 1.343:1, wider than it is long. A sheet cut to any other shape can only be painted on that face by
     * squashing it, which is exactly what a portrait crop did: it reached the bed roughly twice as wide as the
     * frame the player had dragged. Each repeat divides the cloth, so unequal repeat counts change one cell.
     */
    public static float frameAspect(QuiltParams params) {
        // 1.92 / 1.43, and a widened quilt takes a third more across without getting any longer.
        float cloth = params.wideAcross ? 1.790F : 1.343F;
        return cloth * params.repeatV / params.repeatU;
    }

    /**
     * How much of the picture's width the frame takes.
     * <p>
     * Never more than keeps the frame inside the picture: if the cloth changed shape (wider quilt, different
     * repeat count) while a wide crop was stored, honouring that width would need a frame taller than the
     * picture, and clamping the height afterwards would squash the picture instead of re-cutting it.
     */
    public float effectiveCropW(float imageWidth, float imageHeight) {
        float widestThatFits = Math.min(1.0F, frameAspect(this) * imageHeight / imageWidth);
        if (this.cropW > 0.01F) {
            return Math.min(this.cropW, widestThatFits);
        }
        return widestThatFits;
    }

    public float effectiveCropH(float imageWidth, float imageHeight) {
        float cropWidth = effectiveCropW(imageWidth, imageHeight);
        return Math.min(1.0F, cropWidth * imageWidth / (frameAspect(this) * imageHeight));
    }

    /** Left edge of the frame, 0..1 across the picture, kept inside it. */
    public float cropLeft(float imageWidth, float imageHeight) {
        float width = effectiveCropW(imageWidth, imageHeight);
        return Math.max(0.0F, Math.min(1.0F - width, this.cropX - width * 0.5F));
    }

    public float cropTop(float imageWidth, float imageHeight) {
        float height = effectiveCropH(imageWidth, imageHeight);
        return Math.max(0.0F, Math.min(1.0F - height, this.cropY - height * 0.5F));
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
        tag.putFloat("CropX", this.cropX);
        tag.putFloat("CropY", this.cropY);
        tag.putFloat("CropW", this.cropW);
        return tag;
    }

    public static QuiltParams load(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return DEFAULT;
        }
        return new QuiltParams(tag.getBoolean("Tiled"), tag.getFloat("RepeatU"), tag.getFloat("RepeatV"),
                tag.getBoolean("Wide"), tag.getFloat("Shell"), tag.getFloat("Under"), tag.getFloat("Rim"),
                tag.getFloat("Loft"), tag.getFloat("Body"), tag.getBoolean("Tint"),
                tag.contains("CropX") ? tag.getFloat("CropX") : 0.5F,
                tag.contains("CropY") ? tag.getFloat("CropY") : 0.5F,
                tag.contains("CropW") ? tag.getFloat("CropW") : 0.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
