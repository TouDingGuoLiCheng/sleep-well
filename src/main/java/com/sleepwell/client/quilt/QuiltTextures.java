package com.sleepwell.client.quilt;

import com.sleepwell.SleepWellMod;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the finished cover once per (face, border) pair: the face image laid in the middle, the border band
 * composited over it, registered as an ordinary texture so the renderer keeps drawing with a plain
 * {@code entityCutoutNoCull}.
 * <p>
 * The band laps {@link QuiltBorder#OVERLAP} pixels of the face, which is what makes the cover look sewn rather
 * than glued; the artwork underneath that overlap is simply covered.
 */
public final class QuiltTextures {
    /** The finished sheet every composited cover is laid out on: the size the whole-sheet art uses. */
    private static final int SHEET_W = 192;
    /** A custom picture keeps this many pixels on its longest side: enough for a close-up, cheap in memory. */
    private static final int MAX_CUSTOM_SIDE = 768;
    private static final int SHEET_H = 256;
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    /** Pairs whose images could not be read, so a missing file does not retry every frame. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    /** Preview textures are thrown away as the player drags a slider; keep only the recent ones alive. */
    private static final java.util.Deque<ResourceLocation> PREVIEWS = new java.util.ArrayDeque<>();
    private static final int MAX_PREVIEWS = 12;
    /** Source pixel sizes, so the GUI can draw a 32x32 gingham and a 192x256 scene at their own shapes. */
    private static final Map<String, int[]> SIZES = new ConcurrentHashMap<>();

    private QuiltTextures() {
    }

    /** The texture this quilt should be drawn with: the face alone, or face plus band. */
    public static ResourceLocation texture(QuiltStyle style, String borderId) {
        QuiltBorder border = QuiltBorder.of(borderId);
        if (border.isNone()) {
            if (style.file != null) {
                return fileTile(style);
            }
            return style.print == null ? style.texture() : printTile(style);
        }
        // A player's numbers decide how the picture lies on the sheet, so they belong in the cache key.
        String key = style.id + "|" + border.id + (style.file == null ? ""
                : "@" + style.repeatU + "x" + style.repeatV);
        ResourceLocation built = CACHE.get(key);
        if (built != null) {
            return built;
        }
        if (FAILED.contains(key)) {
            return style.texture();
        }
        ResourceLocation composite = build(style, border);
        if (composite == null) {
            FAILED.add(key);
            return style.texture();
        }
        CACHE.put(key, composite);
        return composite;
    }

    private static ResourceLocation build(QuiltStyle style, QuiltBorder border) {
        NativeImage face = faceOf(style);
        NativeImage band = read(border.texture());
        if (face == null || band == null) {
            close(face);
            close(band);
            return null;
        }
        NativeImage out = null;
        try {
            // Everything is laid out on one canonical sheet first. A gingham that tiles 2x2 is flattened here, so
            // the band frames a finished cloth instead of repeating with the pattern.
            int width = SHEET_W + QuiltBorder.REACH * 2;
            int height = SHEET_H + QuiltBorder.REACH * 2;
            out = new NativeImage(width, height, true);
            for (int y = 0; y < SHEET_H; y++) {
                for (int x = 0; x < SHEET_W; x++) {
                    out.setPixelRGBA(x + QuiltBorder.REACH, y + QuiltBorder.REACH, sampleTiled(face, style, x, y));
                }
            }
            // The band is drawn last and only where it is opaque: its middle is punched out. It is sampled with a
            // nearest-neighbour ratio because the art is authored at 220x284 while a solid quilt's face is 32x32:
            // copying pixel for pixel left a sliver of band in one corner and the cover looked missing.
            for (int y = 0; y < height; y++) {
                int bandY = Math.min((int) ((long) y * band.getHeight() / height), band.getHeight() - 1);
                for (int x = 0; x < width; x++) {
                    int bandX = Math.min((int) ((long) x * band.getWidth() / width), band.getWidth() - 1);
                    int pixel = band.getPixelRGBA(bandX, bandY);
                    if (((pixel >>> 24) & 0xFF) != 0) {
                        out.setPixelRGBA(x, y, pixel);
                    }
                }
            }
            ResourceLocation id = new ResourceLocation(SleepWellMod.MOD_ID,
                    "textures/quilt/composite_" + style.id + "_" + border.id);
            DynamicTexture texture = new DynamicTexture(out);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            out = null; // the DynamicTexture owns the image now
            return id;
        } catch (Throwable failure) {
            return null;
        } finally {
            close(face);
            close(band);
            close(out);
        }
    }

    /** Pixel size of the texture {@link #texture} would hand back, for drawing it at the right shape. */
    public static int[] dimensions(QuiltStyle style, String borderId) {
        QuiltBorder border = QuiltBorder.of(borderId);
        String key = style.id + "|" + border.id;
        int[] known = SIZES.get(key);
        if (known != null) {
            return known;
        }
        NativeImage image = faceOf(style);
        int[] size = image == null ? new int[]{192, 256} : new int[]{image.getWidth(), image.getHeight()};
        close(image);
        if (!border.isNone()) {
            // A composited cover is always one canonical sheet plus the band's outward reach on each side.
            size = new int[]{SHEET_W + QuiltBorder.REACH * 2, SHEET_H + QuiltBorder.REACH * 2};
        }
        SIZES.put(key, size);
        return size;
    }

    /**
     * The editor's preview: the sheet this quilt would show, sampled with exactly the maths the renderer uses, so
     * what the player tweaks in the editor is what she gets on the bed.
     */
    public static ResourceLocation preview(QuiltStyle style) {
        String key = "preview|" + style.id + "|" + style.repeatU + "x" + style.repeatV;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        NativeImage face = faceOf(style);
        if (face == null) {
            return style.texture();
        }
        NativeImage out = null;
        try {
            out = new NativeImage(SHEET_W, SHEET_H, true);
            for (int y = 0; y < SHEET_H; y++) {
                for (int x = 0; x < SHEET_W; x++) {
                    out.setPixelRGBA(x, y, sampleTiled(face, style, x, y));
                }
            }
            ResourceLocation id = new ResourceLocation(SleepWellMod.MOD_ID,
                    "textures/quilt/preview_" + Math.abs(key.hashCode()));
            DynamicTexture texture = new DynamicTexture(out);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            out = null; // the DynamicTexture owns the image now
            CACHE.put(key, id);
            synchronized (PREVIEWS) {
                PREVIEWS.addLast(id);
                while (PREVIEWS.size() > MAX_PREVIEWS) {
                    ResourceLocation oldest = PREVIEWS.removeFirst();
                    CACHE.values().remove(oldest);
                    Minecraft.getInstance().getTextureManager().release(oldest);
                }
            }
            return id;
        } catch (Throwable failure) {
            return style.texture();
        } finally {
            close(face);
            close(out);
        }
    }

    /**
     * The finished print tile: our cloth with the vanilla sprite stamped across it in a grid. Built here rather
     * than shipped, which is what keeps Mojang's art out of the jar and lets a resource pack restyle the quilt.
     */
    private static ResourceLocation printTile(QuiltStyle style) {
        String key = style.id + "|print";
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(key)) {
            return style.texture();
        }
        NativeImage tile = buildPrint(style);
        if (tile == null) {
            FAILED.add(key);
            return style.texture();
        }
        try {
            ResourceLocation id = new ResourceLocation(SleepWellMod.MOD_ID, "textures/quilt/print_" + style.id);
            DynamicTexture texture = new DynamicTexture(tile);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            tile = null; // the DynamicTexture owns the image now
            CACHE.put(key, id);
            return id;
        } catch (Throwable failure) {
            FAILED.add(key);
            return style.texture();
        } finally {
            close(tile);
        }
    }

    /** Our cloth with the sprite stamped over it; null when either image is missing, so the drawn sheet wins. */
    private static NativeImage buildPrint(QuiltStyle style) {
        QuiltStyle.Print print = style.print;
        NativeImage cloth = read(style.clothTexture());
        NativeImage sprite = read(new ResourceLocation(print.sprite));
        if (cloth == null || sprite == null) {
            close(cloth);
            close(sprite);
            return null;
        }
        try {
            for (int gy = 0; gy < print.grid; gy++) {
                for (int gx = 0; gx < print.grid; gx++) {
                    for (int y = 0; y < print.cell; y++) {
                        int dy = gy * print.cell + y;
                        if (dy >= cloth.getHeight()) {
                            break;
                        }
                        int sy = print.sy + Math.min(print.sh - 1, y * print.sh / print.cell);
                        for (int x = 0; x < print.cell; x++) {
                            int dx = gx * print.cell + x;
                            if (dx >= cloth.getWidth()) {
                                break;
                            }
                            int sx = print.sx + Math.min(print.sw - 1, x * print.sw / print.cell);
                            int pixel = sprite.getPixelRGBA(sx, sy);
                            // Item art is cut out on transparent corners: the cloth has to show through there.
                            if (((pixel >>> 24) & 0xFF) != 0) {
                                cloth.setPixelRGBA(dx, dy, pixel);
                            }
                        }
                    }
                }
            }
            return cloth;
        } finally {
            close(sprite);
        }
    }

    /** The sheet this quilt draws from: a drawn file, or a print built on the spot. */
    private static NativeImage faceOf(QuiltStyle style) {
        if (style.file != null) {
            return readFile(style.file);
        }
        return style.print == null ? read(style.texture()) : buildPrint(style);
    }

    /**
     * The picture straight off the player's disk, in any format ImageIO understands - a phone jpg included.
     * <p>
     * Decoding goes through {@link java.awt.image.BufferedImage} because it is the only reader that handles jpg;
     * the result is re-encoded as png bytes so {@code NativeImage.read} keeps doing the channel handling. Images
     * larger than {@link #MAX_CUSTOM_SIDE} are halved with a smooth filter rather than a nearest sample, which is
     * what turned photos into a mosaic.
     */
    private static NativeImage readFile(java.nio.file.Path file) {
        try {
            java.awt.image.BufferedImage picture = javax.imageio.ImageIO.read(file.toFile());
            if (picture == null) {
                return null;
            }
            picture = downscale(picture, MAX_CUSTOM_SIDE);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(picture, "png", bytes);
            return NativeImage.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        } catch (Exception failure) {
            return null;
        }
    }

    /** Longest side kept at most {@code max}, halving until it fits so the filter keeps detail. */
    private static java.awt.image.BufferedImage downscale(java.awt.image.BufferedImage source, int max) {
        if (source.getWidth() <= max && source.getHeight() <= max) {
            return source;
        }
        double factor = Math.min((double) max / source.getWidth(), (double) max / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return out;
    }

    /** A player's image registered as a texture, cached once per quilt id. */
    private static ResourceLocation fileTile(QuiltStyle style) {
        String key = style.id + "|file";
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(key)) {
            return style.texture();
        }
        NativeImage image = readFile(style.file);
        if (image == null) {
            FAILED.add(key);
            return style.texture();
        }
        try {
            ResourceLocation id = new ResourceLocation(SleepWellMod.MOD_ID,
                    "textures/quilt/custom_" + Math.abs(style.id.hashCode()));
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            image = null; // the DynamicTexture owns the image now
            CACHE.put(key, id);
            return id;
        } catch (Throwable failure) {
            FAILED.add(key);
            return style.texture();
        } finally {
            close(image);
        }
    }

    /** One pixel of the flattened sheet: the face sampled where the quilt's own repeat count would land. */
    private static int sampleTiled(com.mojang.blaze3d.platform.NativeImage face, QuiltStyle style, int x, int y) {
        double u = (double) x / SHEET_W * style.repeatU;
        double v = (double) y / SHEET_H * style.repeatV;
        u -= Math.floor(u);
        v -= Math.floor(v);
        int sx = Math.min((int) (u * face.getWidth()), face.getWidth() - 1);
        int sy = Math.min((int) (v * face.getHeight()), face.getHeight() - 1);
        return face.getPixelRGBA(sx, sy);
    }

    private static NativeImage read(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) {
                return null;
            }
            try (InputStream stream = resource.get().open()) {
                return NativeImage.read(stream);
            }
        } catch (Exception failure) {
            return null;
        }
    }

    private static void close(NativeImage image) {
        if (image != null) {
            image.close();
        }
    }

    /** Drops the composites; the textures themselves stay registered until the resource reload replaces them. */
    public static void clear() {
        CACHE.clear();
        FAILED.clear();
    }
}
