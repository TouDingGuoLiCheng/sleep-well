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
            // The cut frame is part of what this quilt looks like, so it goes into the map before cutting.
            CUSTOM_PARAMS.put(style.file, style.custom == null ? QuiltParams.DEFAULT : style.custom);
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
    /**
     * The player's picture, cut to the frame the cloth actually has and scaled to the sheet - never squashed.
     * <p>
     * Doing the cut here rather than in UVs is what keeps the tiled mode honest: a frame cut out of the source can
     * be repeated by the ordinary UV maths, while an offset inside a repeating texture cannot.
     */
    private static NativeImage readFile(java.nio.file.Path file) {
        try {
            java.awt.image.BufferedImage source = picture(file);
            if (source == null) {
                return null;
            }
            java.awt.image.BufferedImage cut = cut(source, file);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(cut, "png", bytes);
            return NativeImage.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
        } catch (Exception failure) {
            return null;
        }
    }

    /** Cuts the frame {@code params} describes out of the picture, keeping the cloth's aspect. */
    private static java.awt.image.BufferedImage cut(java.awt.image.BufferedImage source, java.nio.file.Path file) {
        QuiltParams params = CUSTOM_PARAMS.getOrDefault(file, QuiltParams.DEFAULT);
        float width = source.getWidth();
        float height = source.getHeight();
        float frameAspect = QuiltParams.frameAspect(params);
        int left = Math.round(params.cropLeft(width, height) * width);
        int top = Math.round(params.cropTop(width, height) * height);
        int cutW = Math.max(1, Math.round(params.effectiveCropW(width, height) * width));
        int cutH = Math.max(1, Math.round(params.effectiveCropH(width, height) * height));
        int outW;
        int outH;
        if (frameAspect >= 1.0F) {
            outW = MAX_CUSTOM_SIDE;
            outH = Math.max(1, Math.round(MAX_CUSTOM_SIDE / frameAspect));
        } else {
            outH = MAX_CUSTOM_SIDE;
            outW = Math.max(1, Math.round(MAX_CUSTOM_SIDE * frameAspect));
        }
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(outW, outH,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = out.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, outW, outH, left, top, left + cutW, top + cutH, null);
        graphics.dispose();
        return out;
    }

    /** Decoded pictures, kept small: re-cutting for a dragged frame must not re-read a 10 MB file. */
    private static final Map<java.nio.file.Path, java.awt.image.BufferedImage> PICTURES =
            new java.util.concurrent.ConcurrentHashMap<>();
    /** The numbers each file is currently cut with; a plain map so the texture code stays readable. */
    private static final Map<java.nio.file.Path, QuiltParams> CUSTOM_PARAMS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static java.awt.image.BufferedImage picture(java.nio.file.Path file) {
        java.awt.image.BufferedImage cached = PICTURES.get(file);
        if (cached != null) {
            return cached;
        }
        java.awt.image.BufferedImage decoded = decode(file);
        if (decoded == null) {
            return null;
        }
        if (PICTURES.size() > 8) {
            PICTURES.clear();
        }
        PICTURES.put(file, decoded);
        return decoded;
    }

    /** Pixel size of a player's picture, so the editor can lay its frame over the real image. */
    public static int[] pictureSize(java.nio.file.Path file) {
        java.awt.image.BufferedImage picture = picture(file);
        return picture == null ? new int[]{1, 1} : new int[]{picture.getWidth(), picture.getHeight()};
    }

    /** The uncut picture as a texture, for the editor's drag surface. */
    public static ResourceLocation sourceTexture(java.nio.file.Path file) {
        String key = "source|" + file;
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            java.awt.image.BufferedImage picture = picture(file);
            if (picture == null) {
                return null;
            }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(picture, "png", bytes);
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
            ResourceLocation id = new ResourceLocation(SleepWellMod.MOD_ID,
                    "textures/quilt/source_" + Math.abs(key.hashCode()));
            DynamicTexture texture = new DynamicTexture(image);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            CACHE.put(key, id);
            return id;
        } catch (Exception failure) {
            return null;
        }
    }

    /**
     * Decodes without ever holding the full bitmap of a huge photo.
     * <p>
     * The header is read first: an image past {@link CustomQuiltLibrary#MAX_PIXELS} is refused, and anything much
     * larger than the sheet is decoded already subsampled, which is what keeps a 10 MB 4000x3000 photo from
     * costing a quarter of a gigabyte on the way in.
     */
    private static java.awt.image.BufferedImage decode(java.nio.file.Path file) {
        try (javax.imageio.stream.ImageInputStream stream =
                     javax.imageio.ImageIO.createImageInputStream(file.toFile())) {
            if (stream == null) {
                return null;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > CustomQuiltLibrary.MAX_PIXELS) {
                    return null;
                }
                int step = Math.max(1, (int) Math.ceil(Math.max(width, height) / (double) MAX_CUSTOM_SIDE));
                javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
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
        QuiltParams params = style.custom == null ? QuiltParams.DEFAULT : style.custom;
        String key = style.id + "|file|" + Math.round(params.cropX * 64) + "," + Math.round(params.cropY * 64)
                + "," + Math.round(params.cropW * 64) + "," + Math.round(QuiltParams.frameAspect(params) * 16);
        ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(key)) {
            return style.texture();
        }
        CUSTOM_PARAMS.put(style.file, params);
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
