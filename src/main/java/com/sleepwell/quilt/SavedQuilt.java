package com.sleepwell.quilt;

import com.sleepwell.client.quilt.QuiltParams;
import net.minecraft.nbt.CompoundTag;

/**
 * One named quilt the player saved from the editor: which picture, what to call it, and the look it had.
 * <p>
 * The name is stored beside the picture rather than on the maid, so a quilt is a thing a player owns and can lay
 * on any maid; the maid only remembers which one it is wearing.
 */
public record SavedQuilt(String hash, String name, QuiltParams params) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Hash", this.hash);
        tag.putString("Name", this.name);
        tag.put("Params", (this.params == null ? QuiltParams.DEFAULT : this.params).save());
        return tag;
    }

    public static SavedQuilt load(CompoundTag tag) {
        String hash = tag.getString("Hash");
        String name = tag.getString("Name");
        QuiltParams params = tag.contains("Params", 10)
                ? QuiltParams.load(tag.getCompound("Params")) : QuiltParams.DEFAULT;
        return new SavedQuilt(hash, name == null || name.isBlank() ? clean("自定义被子") : clean(name), params);
    }

    /** Names travel to other players' screens, so control characters and absurd lengths stop at the door. */
    public static String clean(String name) {
        StringBuilder out = new StringBuilder();
        String trimmed = name == null ? "" : name.trim();
        for (int i = 0; i < trimmed.length() && out.length() < 32; i++) {
            char c = trimmed.charAt(i);
            if (!Character.isISOControl(c)) {
                out.append(c);
            }
        }
        return out.toString().isBlank() ? "自定义被子" : out.toString();
    }
}
