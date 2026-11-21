package com.sleepwell.unlock;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The built-in unlock table (form A): quilts that are not listed here are open from the start, and the ones
 * that are listed say in code what has to happen before they can be worn.
 * <p>
 * A rule only knows its target id and a condition; the hint text lives in the language file under
 * {@code unlock.sleep_well.<target>}, and the progress numbers come from the condition itself.
 */
public final class UnlockRules {
    /** One condition, queried both by the server (to unlock) and by the GUI (to show "12/20"). */
    public interface Condition {
        boolean satisfied(ServerPlayer player);

        int progress(ServerPlayer player);

        int goal();
    }

    /** A target plus what it waits for. */
    public record Rule(String target, Condition condition) {
    }

    /** One row of the wall's hover text: what it is, where the player is, and how far is far. */
    public record Progress(String target, int progress, int goal) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("thick_gingham", maidCount(3)),
            new Rule("spring_blossom", flag("cherry")),
            new Rule("spring_cherry", season("spring")),
            new Rule("summer_sunflower", season("summer")),
            new Rule("autumn", season("autumn")),
            new Rule("winter", season("winter")),
            new Rule("watermelon", stat(Stats.ITEM_USED, Items.MELON_SLICE, 3)),
            new Rule("cake", stat(Stats.ITEM_CRAFTED, Items.CAKE, 1)),
            new Rule("sheep", flag("sheep")),
            new Rule("cat", flag("cat"))
    );

    private UnlockRules() {
    }

    public static List<Rule> all() {
        return RULES;
    }

    public static Rule byTarget(String target) {
        for (Rule rule : RULES) {
            if (rule.target().equals(target)) {
                return rule;
            }
        }
        return null;
    }

    public static boolean hasRule(String target) {
        return byTarget(target) != null;
    }

    /** True when nothing gates this target, or the player already unlocked it. */
    public static boolean isUnlocked(ServerPlayer player, String target) {
        Rule rule = byTarget(target);
        return rule == null || UnlockCapabilities.of(player).unlocked().contains(target);
    }

    public static List<Progress> progress(ServerPlayer player) {
        return RULES.stream()
                .map(rule -> new Progress(rule.target(), rule.condition().progress(player),
                        rule.condition().goal()))
                .toList();
    }

    /** A vanilla statistic has to reach {@code goal}. */
    public static Condition stat(StatType<Item> type, Item item, int goal) {
        return new Condition() {
            @Override
            public boolean satisfied(ServerPlayer player) {
                return progress(player) >= goal;
            }

            @Override
            public int progress(ServerPlayer player) {
                return Math.min(goal, player.getStats().getValue(type, item));
            }

            @Override
            public int goal() {
                return goal;
            }
        };
    }

    /** Maids owned by this player that are within 32 blocks of them (TLM has no offline roster). */
    public static Condition maidCount(int goal) {
        return new Condition() {
            @Override
            public boolean satisfied(ServerPlayer player) {
                return progress(player) >= goal;
            }

            @Override
            public int progress(ServerPlayer player) {
                int count = player.level().getEntitiesOfClass(EntityMaid.class,
                        player.getBoundingBox().inflate(32.0D), maid -> maid.isOwnedBy(player)).size();
                return Math.min(goal, count);
            }

            @Override
            public int goal() {
                return goal;
            }
        };
    }

    /** An event flag the mod sets when the right thing happens (bone meal, sheep, cat). */
    public static Condition flag(String flag) {
        return new Condition() {
            @Override
            public boolean satisfied(ServerPlayer player) {
                return UnlockCapabilities.of(player).flag(flag);
            }

            @Override
            public int progress(ServerPlayer player) {
                return satisfied(player) ? 1 : 0;
            }

            @Override
            public int goal() {
                return 1;
            }
        };
    }

    /** A season the player has already slept into (recorded on every evaluation). */
    public static Condition season(String season) {
        return new Condition() {
            @Override
            public boolean satisfied(ServerPlayer player) {
                return UnlockCapabilities.of(player).seasons().contains(season);
            }

            @Override
            public int progress(ServerPlayer player) {
                return satisfied(player) ? 1 : 0;
            }

            @Override
            public int goal() {
                return 1;
            }
        };
    }
}
