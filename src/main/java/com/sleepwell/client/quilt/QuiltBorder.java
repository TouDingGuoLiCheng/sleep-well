package com.sleepwell.client.quilt;

import com.sleepwell.SleepWellMod;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The optional edging around a quilt: a printed band that sits outside the face and laps a few pixels of it.
 * <p>
 * A border is a separate image the size of the finished cover (face plus the band on every side), with the
 * middle punched out. The renderer lays the face down first and the border over it, which is why nothing here
 * has to know how the face is drawn.
 */
public final class QuiltBorder {
    /** Where the band ends, measured from the face edge: it covers this much of the artwork ... */
    public static final int OVERLAP = 6;
    /** ... and reaches this far past it. */
    public static final int REACH = 14;
    /** Total band, and how much bigger the finished cover is than the face. */
    public static final int BAND = OVERLAP + REACH;

    /** Declared before the borders themselves, because the constants below register while this class initialises. */
    private static final Map<String, QuiltBorder> REGISTRY = new LinkedHashMap<>();

    /** No border: the artwork covers the whole sheet, exactly as it did before borders existed. */
    public static final QuiltBorder NONE = register(new QuiltBorder("", "无"));
    public static final QuiltBorder WHITE = register(new QuiltBorder("white", "白边"));
    public static final QuiltBorder BLACK = register(new QuiltBorder("black", "黑边"));
    public static final QuiltBorder STRIPED = register(new QuiltBorder("striped", "条纹边"));
    public static final QuiltBorder VELVET = register(new QuiltBorder("velvet", "绒边"));

    /** Empty for {@link #NONE}. */
    public final String id;
    public final String displayName;

    private QuiltBorder(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    private static QuiltBorder register(QuiltBorder border) {
        REGISTRY.put(border.id, border);
        return border;
    }

    /** The border with this id; an unknown or empty id means no border, so old saves stay borderless. */
    public static QuiltBorder of(String id) {
        QuiltBorder found = id == null ? null : REGISTRY.get(id);
        return found == null ? NONE : found;
    }

    /** Every border, none first: the GUI wall renders this list. */
    public static List<QuiltBorder> all() {
        return List.copyOf(REGISTRY.values());
    }

    public boolean isNone() {
        return id.isEmpty();
    }

    /** The band image under {@code textures/quilt/}, or null for {@link #NONE}. */
    public ResourceLocation texture() {
        return isNone() ? null
                : new ResourceLocation(SleepWellMod.MOD_ID, "textures/quilt/border_" + id + ".png");
    }
}
