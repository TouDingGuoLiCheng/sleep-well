package com.sleepwell.unlock;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What one player has unlocked, which quilt faces they have actually slept under, and the little event
 * flags the unlock table needs (bone-mealed cherry, bred sheep, tamed cat).
 * <p>
 * Everything the unlock table asks for is stored here or read from vanilla statistics, so a save keeps its
 * progress across a restart without any side files.
 */
public final class PlayerUnlocks implements INBTSerializable<CompoundTag> {
    private static final String TAG_UNLOCKED = "Unlocked";
    private static final String TAG_WORN = "Worn";
    private static final String TAG_SEASONS = "Seasons";
    private static final String TAG_FLAGS = "Flags";
    private static final String TAG_SEASON_FALLBACK = "SeasonFallbackDone";

    private final Set<String> unlocked = new LinkedHashSet<>();
    private final Set<String> worn = new LinkedHashSet<>();
    private final Set<String> seasons = new LinkedHashSet<>();
    private final Set<String> flags = new LinkedHashSet<>();
    private boolean seasonFallbackDone;

    public Set<String> unlocked() {
        return this.unlocked;
    }

    /** Marks a target unlocked; returns true when it was new, which is what fires the toast. */
    public boolean unlock(String target) {
        return target != null && !target.isEmpty() && this.unlocked.add(target);
    }

    public Set<String> worn() {
        return this.worn;
    }

    public boolean markWorn(String quiltId) {
        return quiltId != null && !quiltId.isEmpty() && this.worn.add(quiltId);
    }

    public Set<String> seasons() {
        return this.seasons;
    }

    public boolean markSeason(String season) {
        return season != null && !season.isEmpty() && this.seasons.add(season);
    }

    public boolean flag(String flag) {
        return this.flags.contains(flag);
    }

    public boolean markFlag(String flag) {
        return flag != null && !flag.isEmpty() && this.flags.add(flag);
    }

    public boolean seasonFallbackDone() {
        return this.seasonFallbackDone;
    }

    public void setSeasonFallbackDone() {
        this.seasonFallbackDone = true;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_UNLOCKED, strings(this.unlocked));
        tag.put(TAG_WORN, strings(this.worn));
        tag.put(TAG_SEASONS, strings(this.seasons));
        tag.put(TAG_FLAGS, strings(this.flags));
        tag.putBoolean(TAG_SEASON_FALLBACK, this.seasonFallbackDone);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        this.unlocked.clear();
        this.worn.clear();
        this.seasons.clear();
        this.flags.clear();
        read(tag, TAG_UNLOCKED, this.unlocked);
        read(tag, TAG_WORN, this.worn);
        read(tag, TAG_SEASONS, this.seasons);
        read(tag, TAG_FLAGS, this.flags);
        this.seasonFallbackDone = tag.getBoolean(TAG_SEASON_FALLBACK);
    }

    private static ListTag strings(Set<String> values) {
        ListTag list = new ListTag();
        for (String value : values) {
            list.add(net.minecraft.nbt.StringTag.valueOf(value));
        }
        return list;
    }

    private static void read(CompoundTag tag, String key, Set<String> into) {
        Tag list = tag.get(key);
        if (!(list instanceof ListTag entries)) {
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) instanceof net.minecraft.nbt.StringTag text) {
                into.add(text.getAsString());
            }
        }
    }
}
