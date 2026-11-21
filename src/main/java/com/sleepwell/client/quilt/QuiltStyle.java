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
    /** Non-null when the cloth is printed at runtime with a vanilla sprite instead of our own artwork. */
    public final Print print;
    /** Set only for a quilt built from a player's own image file; null for every built-in sheet. */
    public final java.nio.file.Path file;
    /** The numbers this custom quilt was built with, crop included; null for built-in sheets. */
    public QuiltParams custom;

    /**
     * A print: a vanilla sprite stamped across a cloth of ours. The sprite's bytes stay in the player's own jar,
     * so nothing of Mojang's ships with this mod - and a resource pack restyles the quilt along with everything
     * else on screen.
     */
    public static final class Print {
        /** The sprite, without a namespace: vanilla's own art lives under {@code minecraft}. */
        public final String sprite;
        /** The crop inside that sprite, in source pixels. */
        public final int sx;
        public final int sy;
        public final int sw;
        public final int sh;
        /** Footprint of one motif on the finished tile, in pixels. */
        public final int cell;
        /** How many motifs fit across one tile, so {@code grid * cell} fills the sheet exactly. */
        public final int grid;
        /** Our cloth under {@code textures/quilt/}; the sprite is stamped over it. */
        public final String clothId;

        public Print(String sprite, int sx, int sy, int sw, int sh, int cell, int grid, String clothId) {
            this.sprite = sprite;
            this.sx = sx;
            this.sy = sy;
            this.sw = sw;
            this.sh = sh;
            this.cell = cell;
            this.grid = grid;
            this.clothId = clothId;
        }
    }

    private QuiltStyle(String id, String displayName, String textureId, boolean dyeTinted, Tiling tiling,
                       float repeatU, float repeatV, boolean wideAcross, float shell, float underShade,
                       float rimShade, float rimV, float loft, float body) {
        this(id, displayName, textureId, dyeTinted, tiling, repeatU, repeatV, wideAcross, shell, underShade,
                rimShade, rimV, loft, body, null);
    }

    private QuiltStyle(String id, String displayName, String textureId, boolean dyeTinted, Tiling tiling,
                       float repeatU, float repeatV, boolean wideAcross, float shell, float underShade,
                       float rimShade, float rimV, float loft, float body, Print print) {
        this(id, displayName, textureId, dyeTinted, tiling, repeatU, repeatV, wideAcross, shell, underShade,
                rimShade, rimV, loft, body, print, null);
    }

    private QuiltStyle(String id, String displayName, String textureId, boolean dyeTinted, Tiling tiling,
                       float repeatU, float repeatV, boolean wideAcross, float shell, float underShade,
                       float rimShade, float rimV, float loft, float body, Print print, java.nio.file.Path file) {
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
        this.print = print;
        this.file = file;
    }

    /**
     * A quilt built from a player's own image and that player's numbers. Never registered: two maids may wear the
     * same picture with different numbers, so a shared registry entry would be wrong.
     */
    public static QuiltStyle runtime(String id, String displayName, java.nio.file.Path file, QuiltParams params) {
        // Repeat count is what the geometry multiplies the UV by, so "whole sheet" has to mean exactly 1 here.
        float repeatU = params.tiled ? params.repeatU : 1.0F;
        float repeatV = params.tiled ? params.repeatV : 1.0F;
        QuiltStyle style = new QuiltStyle(id, displayName, null, params.dyeTinted,
                params.tiled ? Tiling.TILED : Tiling.WHOLE, repeatU, repeatV,
                params.wideAcross, params.shell, params.underShade, params.rimShade, 0.16F,
                params.loft, params.body, null, file);
        style.custom = params;
        return style;
    }

    /** True for a player's own sheet, whose picture is read from disk instead of the jar. */
    public boolean isCustom() {
        return this.file != null;
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
     * 西瓜被: a thin summer print - round slices packed into a repeating cloth. Watermelon on a hot night, so it
     * borrows the summer quilt's hand: barely any padding, light and cool.
     */
    public static final QuiltStyle WATERMELON = register(printed("watermelon", "西瓜被", "watermelon", SUMMER_COOL,
            new Print("textures/item/melon_slice.png", 0, 0, 16, 16, 16, 4, "watermelon_cloth")));

    /**
     * 蛋糕被: a sweet print on the down quilt's hand - puffed up and soft, so it reads as a cake you can flop onto
     * rather than a picture of one.
     */
    public static final QuiltStyle CAKE = register(printed("cake", "蛋糕被", "cake", DOWN,
            new Print("textures/item/cake.png", 0, 0, 16, 16, 16, 4, "cake_cloth")));

    /**
     * 绵羊被: the fluffiest and the widest sheet here - a woolly print over a nearly down-soft body, plus the
     * extra third of width so it hangs down both sides instead of stopping at the mattress. The widened sheet
     * would stretch the print, so the repeat is raised to 16/9: the wool cells stay square (1.5 blocks each, as
     * on every other tiled quilt) while the cloth itself is a third wider.
     */
    public static final QuiltStyle SHEEP = register(new QuiltStyle("sheep", "绵羊被",
            "sheep", false, Tiling.TILED, 16.0F / 9.0F, 1.0F, true, 0.062F, 0.74F, 0.64F, 0.16F, 0.90F, 0.30F));

    /**
     * 猫咪被: a cotton-weight print - medium padding, enough body to crease, which is what a cat has already done
     * to every quilt it has ever slept on.
     */
    public static final QuiltStyle CAT = register(new QuiltStyle("cat", "猫咪被",
            "cat", false, Tiling.TILED, 4.0F / 3.0F, 1.0F, false, 0.048F, 0.74F, 0.64F, 0.16F, 0.50F, 0.60F));

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

    /**
     * A quilt whose cloth is ours but whose print is read from vanilla at runtime, keeping one hand and one
     * fallback sheet: if the sprite cannot be read, the drawn artwork under {@code fallbackTextureId} is used.
     */
    private static QuiltStyle printed(String id, String displayName, String fallbackTextureId, QuiltStyle hand,
                                      Print print) {
        return new QuiltStyle(id, displayName, fallbackTextureId, hand.dyeTinted, hand.tiling,
                hand.repeatU, hand.repeatV, hand.wideAcross, hand.shell, hand.underShade,
                hand.rimShade, hand.rimV, hand.loft, hand.body, print);
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

    /** Our cloth that a print is stamped onto, under {@code textures/quilt/}. */
    public ResourceLocation clothTexture() {
        return new ResourceLocation("sleep_well", "textures/quilt/" + print.clothId + ".png");
    }

    /** Every quilt owns exactly one texture; the bed's dye is no longer involved. */
    public ResourceLocation texture() {
        if (this.file != null) {
            // A player's image is not a resource; the renderer loads the file and registers it dynamically.
            return new ResourceLocation("sleep_well", "textures/quilt/custom_" + this.id.replace(':', '_'));
        }
        return new ResourceLocation("sleep_well", "textures/quilt/" + textureId + ".png");
    }
}
