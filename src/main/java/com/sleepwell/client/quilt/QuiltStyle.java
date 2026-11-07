package com.sleepwell.client.quilt;

import net.minecraft.resources.ResourceLocation;

/**
 * One kind of quilt, as data rather than as constants buried in the renderer.
 * <p>
 * Everything that makes one quilt look different from another lives here: its texture, how many times that
 * texture repeats across the cloth, the visible thickness of the shell and how the underside and rim are shaded.
 * <p>
 * A quilt carries its own colours. The bed's dye no longer tints anything: a spring quilt has to look like
 * spring on a black bed too, so each quilt owns one texture and repeats it.
 * <p>
 * Adding a quilt is therefore a matter of declaring another {@link QuiltStyle} - the renderer and the solver
 * do not know any of the numbers.
 */
public final class QuiltStyle {
    /** How the texture is placed on the cloth. */
    public enum Tiling {
        /** Repeat the pattern: the artwork is a seamless tile, laid down per block. */
        TILED,
        /** Draw the artwork once across the whole sheet, stretched to the cloth's shape. */
        WHOLE
    }

    /** Stable id, used by config and by the resource path. */
    public final String id;
    /** Name shown to players. */
    public final String displayName;
    /** Repeat the pattern (seamless artwork) or stretch it once over the whole sheet. */
    public final Tiling tiling;
    /** How many times the texture repeats across the cloth, which is what keeps a pattern square. */
    public final float repeatU;
    public final float repeatV;
    /** Draw the quilt one third wider than a plain one: the sheet is enlarged, not the pattern. */
    public final boolean wideAcross;
    /** Visible thickness: the underside sits this far below the solved surface. */
    public final float shell;
    /** Brightness of the underside, relative to the top surface. */
    public final float underShade;
    /** Brightness of the rim that joins top and underside. */
    public final float rimShade;
    /** Texture height used by the rim strips. */
    public final float rimV;

    private QuiltStyle(String id, String displayName, Tiling tiling, float repeatU, float repeatV,
                       boolean wideAcross, float shell, float underShade, float rimShade, float rimV) {
        this.id = id;
        this.displayName = displayName;
        this.tiling = tiling;
        this.repeatU = repeatU;
        this.repeatV = repeatV;
        this.wideAcross = wideAcross;
        this.shell = shell;
        this.underShade = underShade;
        this.rimShade = rimShade;
        this.rimV = rimV;
    }

    // Declared before the quilts themselves: the constant below registers into this map while the class is
    // still initialising, so a later field would still be null at that moment.
    /** Every quilt that exists, in display order. Adding a quilt means one more line here. */
    private static final java.util.Map<String, QuiltStyle> REGISTRY = new java.util.LinkedHashMap<>();

    /** The default quilt, and the one every maid wears until she picks another. */
    public static final QuiltStyle THICK_GINGHAM = register(new QuiltStyle("thick_gingham", "厚实格子被",
            Tiling.TILED, 4.0F / 3.0F, 1.0F, true, 0.045F, 0.72F, 0.62F, 0.16F));

    /**
     * Spring: a thin, narrow cover in off-white with a fine green check and scattered petals - the one you
     * pull over yourself on a night that has stopped being cold but is not yet warm.
     */
    public static final QuiltStyle SPRING_BLOSSOM = register(new QuiltStyle("spring_blossom", "春之被",
            Tiling.TILED, 4.0F, 4.0F, false, 0.028F, 0.82F, 0.72F, 0.16F));

    private static QuiltStyle register(QuiltStyle style) {
        REGISTRY.put(style.id, style);
        return style;
    }

    /** The quilt with this id, or the default one when the id is unknown (an old save, a removed quilt). */
    public static QuiltStyle of(String id) {
        QuiltStyle found = id == null ? null : REGISTRY.get(id);
        return found == null ? THICK_GINGHAM : found;
    }

    /** Every quilt, default first: the GUI wall renders this list. */
    public static java.util.List<QuiltStyle> all() {
        return java.util.List.copyOf(REGISTRY.values());
    }

    public boolean isDefault() {
        return THICK_GINGHAM.id.equals(id);
    }

    /** How many texture repeats span the cloth across, and along. */
    public float tileU() {
        return repeatU;
    }

    public float tileV() {
        return repeatV;
    }

    /** Every quilt owns exactly one texture; the bed's dye is no longer involved. */
    public ResourceLocation texture() {
        return new ResourceLocation("sleep_well", "textures/quilt/" + id + ".png");
    }
}
