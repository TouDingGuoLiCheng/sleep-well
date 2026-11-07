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
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    /** Pairs whose images could not be read, so a missing file does not retry every frame. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

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
            int width = face.getWidth() + QuiltBorder.BAND * 2;
            int height = face.getHeight() + QuiltBorder.BAND * 2;
            out = new NativeImage(width, height, true);
            int originX = QuiltBorder.REACH;
            int originY = QuiltBorder.REACH;
            for (int y = 0; y < face.getHeight(); y++) {
                for (int x = 0; x < face.getWidth(); x++) {
                    out.setPixelRGBA(x + originX, y + originY, face.getPixelRGBA(x, y));
                }
            }
            // The band is drawn last and only where it is opaque: its middle is punched out.
            for (int y = 0; y < height && y < band.getHeight(); y++) {
                for (int x = 0; x < width && x < band.getWidth(); x++) {
                    int pixel = band.getPixelRGBA(x, y);
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
