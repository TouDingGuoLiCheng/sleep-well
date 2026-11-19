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
    /** Texture file under {@code textures/quilt/}; solid quilts use the plain white sheet. */
    public final String textureId;
    /** True when the bed's dye tints this quilt: a gingham quilt follows the bed, the themed ones do not. */
    public final boolean dyeTinted;
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
    /**
     * Loft, 0..1: how far the cloth floats above her and how round the folds read. A down quilt is 1, a
     * silk one is low enough to lie on her like a second skin.
     */
    public final float loft;
    /**
     * Body, 0..1: how hard the cloth resists bending. An old cotton quilt folds in hard creases, a silk or
     * down one drapes over every limb.
     */
    public final float body;

    private QuiltStyle(String id, String displayName, String textureId, boolean dyeTinted, Tiling tiling,
                       float repeatU, float repeatV, boolean wideAcross, float shell, float underShade,
                       float rimShade, float rimV, float loft, float body) {
        this.id = id;
        this.displayName = displayName;
        this.textureId = textureId;
        this.dyeTinted = dyeTinted;
        this.tiling = tiling;
        this.repeatU = repeatU;
        this.repeatV = repeatV;
        this.wideAcross = wideAcross;
        this.shell = shell;
        this.underShade = underShade;
        this.rimShade = rimShade;
        this.rimV = rimV;
        this.loft = loft;
        this.body = body;
    }

    // Declared before the quilts themselves: the constant below registers into this map while the class is
    // still initialising, so a later field would still be null at that moment.
    /** Every quilt that exists, in display order. Adding a quilt means one more line here. */
    private static final java.util.Map<String, QuiltStyle> REGISTRY = new java.util.LinkedHashMap<>();

    /**
     * The default quilt: a plain sheet that takes the bed's dye, so a maid on a blue bed is covered in blue
     * and nobody has to pick a colour to get a matching quilt.
     */
    public static final QuiltStyle SOLID = register(new QuiltStyle("solid", "素色被",
            "solid_white", true, Tiling.WHOLE, 1.0F, 1.0F, false, 0.038F, 0.76F, 0.66F, 0.16F, 0.35F, 0.50F));

    /**
     * 棉被: the old fluffed cotton quilt - a wide, heavy, hard-folding cover with a warm printed face. It is the
     * stiffest sheet in the mod on purpose: it should press down and crease instead of draping.
     */
    public static final QuiltStyle COTTON = register(new QuiltStyle("cotton", "棉被",
            "cotton", false, Tiling.TILED, 4.0F / 3.0F, 1.0F, true, 0.055F, 0.70F, 0.58F, 0.16F, 0.35F, 0.95F));

    /**
     * 化纤被: the cheap one - mid thickness, a flat synthetic sheen and diamond stitching. Neither heavy nor
     * clingy: it just covers.
     */
    public static final QuiltStyle SYNTHETIC = register(new QuiltStyle("synthetic", "化纤被",
            "synthetic", false, Tiling.TILED, 4.0F / 3.0F, 1.0F, false, 0.040F, 0.80F, 0.70F, 0.16F, 0.55F, 0.60F));

    /**
     * 羽绒被: the loftiest sheet here - cassette-quilted squares that bulge, high padding and almost no bending
     * strength, so it settles into every hollow of the person under it instead of bridging over them.
     */
    public static final QuiltStyle DOWN = register(new QuiltStyle("down", "羽绒被",
            "down", false, Tiling.TILED, 4.0F / 3.0F, 1.0F, true, 0.066F, 0.76F, 0.66F, 0.16F, 1.00F, 0.25F));

    /** 蚕丝被: thin and floppy - low loft, minimal stiffness, so it lies on her like a second skin. */
    public static final QuiltStyle SILK = register(new QuiltStyle("silk", "蚕丝被",
            "silk", false, Tiling.TILED, 4.0F / 3.0F, 1.0F, false, 0.024F, 0.88F, 0.80F, 0.16F, 0.30F, 0.15F));

    /** 夏凉被: the thinnest sheet in the mod, a light printed cotton for a warm night. */
    public static final QuiltStyle SUMMER_COOL = register(new QuiltStyle("summer_cool", "夏凉被",
            "summer_cool", false, Tiling.TILED, 4.0F / 3.0F, 1.0F, false, 0.020F, 0.90F, 0.82F, 0.16F, 0.15F, 0.20F));

    /** The original gingham cloth, with the PixelLab artwork and its own pink-and-cream colours, whatever bed it is on. */
    public static final QuiltStyle THICK_GINGHAM = register(new QuiltStyle("thick_gingham", "厚实格子被",
            "thick_gingham", false, Tiling.TILED, 4.0F / 3.0F, 1.0F, true, 0.045F, 0.72F, 0.62F, 0.16F, 0.45F, 0.65F));

    /**
     * Spring: a thin, narrow cover in off-white with a fine green check and scattered petals - the one you
     * pull over yourself on a night that has stopped being cold but is not yet warm.
     */
    public static final QuiltStyle SPRING_BLOSSOM = register(new QuiltStyle("spring_blossom", "樱草细格被",
            "spring_blossom", false, Tiling.TILED, 2.0F, 2.0F, false, 0.028F, 0.82F, 0.72F, 0.16F, 0.20F, 0.35F));

    /**
     * Spring: a printed scene - blue sky, a warm sun, cherry boughs and a flower meadow. Like the
     * summer quilt it is laid once across the whole sheet instead of tiling.
     */
    public static final QuiltStyle SPRING_CHERRY = register(new QuiltStyle("spring_cherry", "春之被",
            "spring_cherry", false, Tiling.WHOLE, 1.0F, 1.0F, false, 0.038F, 0.76F, 0.66F, 0.16F, 0.33F, 0.50F));

    /**
     * Summer: a printed scene rather than a repeating cloth - blue sky, big cumulus and a sunflower
     * field. The artwork is laid once across the whole sheet, so there is nothing to tile and the
     * drawing keeps its own composition.
     */
    public static final QuiltStyle SUMMER_SUNFLOWER = register(new QuiltStyle("summer_sunflower", "夏之被",
            "summer_sunflower", false, Tiling.WHOLE, 1.0F, 1.0F, false, 0.038F, 0.76F, 0.66F, 0.16F, 0.33F, 0.50F));

    /**
     * Autumn: a printed scene chosen by the artwork itself - low evening light through a forest,
     * warm and quiet. Whole-sheet like the spring and summer quilts.
     */
    public static final QuiltStyle AUTUMN = register(new QuiltStyle("autumn", "秋之被",
            "autumn", false, Tiling.WHOLE, 1.0F, 1.0F, false, 0.038F, 0.76F, 0.66F, 0.16F, 0.35F, 0.55F));

    /**
     * Winter: a printed scene - snow, pines, moonlight and falling flakes. Whole-sheet like the
     * other seasonal quilts.
     */
    public static final QuiltStyle WINTER = register(new QuiltStyle("winter", "冬之被",
            "winter", false, Tiling.WHOLE, 1.0F, 1.0F, false, 0.038F, 0.76F, 0.66F, 0.16F, 0.50F, 0.70F));

    /**
     * How far the cloth floats above her skin, in blocks: the duvet's own padding. 0.35 loft is the flat sheet
     * this mod shipped with; a down quilt sits three times higher, a silk one practically on her.
     */
    public float padding() {
        return 0.008F + 0.042F * loft;
    }

    /** Multiplier on the long-range (bending) constraints. Body 0.5 reproduces the pre-parameter sheet exactly. */
    public float bendScale() {
        return 0.35F + 1.30F * body;
    }

    /** Cache-key part: two quilts with a different hand must not share one solved sheet. */
    public String clothKey() {
        return String.format(java.util.Locale.ROOT, "%s-%.3f-%.3f", id, loft, body);
    }

    /** True when the artwork repeats across the cloth instead of covering it once. */
    public boolean tiles() {
        return repeatU != 1.0F || repeatV != 1.0F;
    }

    private static QuiltStyle register(QuiltStyle style) {
        REGISTRY.put(style.id, style);
        return style;
    }

    /** The quilt with this id, or the default one when the id is unknown (an old save, a removed quilt). */
    public static QuiltStyle of(String id) {
        QuiltStyle found = id == null ? null : REGISTRY.get(id);
        return found == null ? SOLID : found;
    }

    /** Every quilt, default first: the GUI wall renders this list. */
    public static java.util.List<QuiltStyle> all() {
        return java.util.List.copyOf(REGISTRY.values());
    }

    public boolean isDefault() {
        return SOLID.id.equals(id);
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
        return new ResourceLocation("sleep_well", "textures/quilt/" + textureId + ".png");
    }
}
