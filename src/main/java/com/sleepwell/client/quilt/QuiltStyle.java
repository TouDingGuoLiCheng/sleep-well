package com.sleepwell.client.quilt;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/**
 * One kind of quilt, as data rather than as constants buried in the renderer.
 * <p>
 * Everything that makes one quilt look different from another lives here: which texture it uses, how large
 * that texture is, whether the pattern tiles across the cloth or is drawn once over the whole sheet, the
 * visible thickness of the shell and how its underside and rim are shaded.
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
    public final Tiling tiling;
    /** Draw the quilt one third wider than the texture was authored for (see TILE_U). */
    public final boolean wideAcross;
    /** Visible thickness: the underside sits this far below the solved surface. */
    public final float shell;
    /** Brightness of the underside, relative to the top surface. */
    public final float underShade;
    /** Brightness of the rim that joins top and underside. */
    public final float rimShade;
    /** Texture height used by the rim strips. */
    public final float rimV;

    private QuiltStyle(String id, String displayName, Tiling tiling, boolean wideAcross, float shell,
                       float underShade, float rimShade, float rimV) {
        this.id = id;
        this.displayName = displayName;
        this.tiling = tiling;
        this.wideAcross = wideAcross;
        this.shell = shell;
        this.underShade = underShade;
        this.rimShade = rimShade;
        this.rimV = rimV;
    }

    /** The default quilt, and the one every maid wears until she picks another. */
    public static final QuiltStyle THICK_GINGHAM = register(new QuiltStyle("thick_gingham", "厚实格子被",
            Tiling.TILED, true, 0.045F, 0.72F, 0.62F, 0.16F));

    /** Every quilt that exists, in display order. Adding a quilt means one more line here. */
    private static final java.util.Map<String, QuiltStyle> REGISTRY = new java.util.LinkedHashMap<>();

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

    /** Width multiplier of the UV range: wider cloth, same check size. */
    public float tileU() {
        return tiling == Tiling.WHOLE ? 1.0F : (wideAcross ? 4.0F / 3.0F : 1.0F);
    }

    public float tileV() {
        return 1.0F;
    }

    /** Texture for one bed colour. Whole-image quilts ignore the colour and resolve by id. */
    public ResourceLocation texture(DyeColor color) {
        if (tiling == Tiling.WHOLE) {
            return new ResourceLocation("sleep_well", "textures/quilt/" + id + ".png");
        }
        return new ResourceLocation("sleep_well", "textures/quilt/" + id + "/" + color.getSerializedName() + ".png");
    }
}
