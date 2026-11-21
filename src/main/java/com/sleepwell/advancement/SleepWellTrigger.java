package com.sleepwell.advancement;

import com.google.gson.JsonObject;
import com.sleepwell.SleepWellMod;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/**
 * One criterion for every Sleep Well achievement: a kind and a number.
 * <p>
 * The kinds are plain strings ({@code root_sleep}, {@code sleep_count}, {@code iron_sleep}, ...), so the
 * achievement JSON decides what it listens for and the code only has to say what happened.
 */
public final class SleepWellTrigger extends SimpleCriterionTrigger<SleepWellTrigger.Instance> {
    public static final ResourceLocation ID = new ResourceLocation(SleepWellMod.MOD_ID, "sleepwell");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate predicate,
                                      DeserializationContext context) {
        String kind = GsonHelper.getAsString(json, "kind", "");
        int value = GsonHelper.getAsInt(json, "value", 1);
        return new Instance(predicate, kind, value);
    }

    /** Fires every listener whose kind matches and whose threshold has been reached. */
    public void trigger(ServerPlayer player, String kind, int value) {
        trigger(player, instance -> instance.matches(kind, value));
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final String kind;
        private final int value;

        public Instance(ContextAwarePredicate predicate, String kind, int value) {
            super(SleepWellTrigger.ID, predicate);
            this.kind = kind == null ? "" : kind;
            this.value = value;
        }

        public boolean matches(String kind, int value) {
            return this.kind.equals(kind) && value >= this.value;
        }
    }
}
