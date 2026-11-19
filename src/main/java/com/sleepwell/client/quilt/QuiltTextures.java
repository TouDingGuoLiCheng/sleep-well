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
    private static final int SHEET_H = 256;
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    /** Pairs whose images could not be read, so a missing file does not retry every frame. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    /** Source pixel sizes, so the GUI can draw a 32x32 gingham and a 192x256 scene at their own shapes. */
    private static final Map<String, int[]> SIZES = new ConcurrentHashMap<>();

    private QuiltTextures() {
    }

    /** The texture this quilt should be drawn with: the face alone, or face plus band. */
    public static ResourceLocation texture(QuiltStyle style, String borderId) {
        QuiltBorder border = QuiltBorder.of(borderId);
        if (border.isNone()) {
            return style.texture();
        }
        String key = style.id + "|" + border.id;
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
        NativeImage face = read(style.texture());
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
        NativeImage image = read(style.texture());
        int[] size = image == null ? new int[]{192, 256} : new int[]{image.getWidth(), image.getHeight()};
        close(image);
        if (!border.isNone()) {
            // A composited cover is always one canonical sheet plus the band's outward reach on each side.
            size = new int[]{SHEET_W + QuiltBorder.REACH * 2, SHEET_H + QuiltBorder.REACH * 2};
        }
        SIZES.put(key, size);
        return size;
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
