package com.sleepwell.config;

import com.sleepwell.fire.CampfireFleePolicy;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/**
 * Common config for Sleep Well.
 * <p>
 * Values are read via {@code .get()} at use time, so a config edit applies after Forge reloads the
 * config file (re-enter the world / restart the server).
 */
public final class SleepWellConfig {
    public static final ForgeConfigSpec SPEC;

    /** Enable the sleep-stuck fallback teleport for kappa-compass-configured maids. */
    public static final ForgeConfigSpec.BooleanValue SLEEP_FALLBACK_ENABLED;
    /** Seconds of no progress towards the sleep point before the fallback fires (2..60). */
    public static final ForgeConfigSpec.IntValue SLEEP_FALLBACK_SECONDS;
    /** Fallback teleports allowed per nap (0..5). */
    public static final ForgeConfigSpec.IntValue SLEEP_FALLBACK_MAX_TELEPORTS;

    /** Enable "a maid burned by a campfire runs away from it". */
    public static final ForgeConfigSpec.BooleanValue CAMPFIRE_FLEE_ENABLED;
    /** How far the safe-spot search reaches, in blocks (2..12). */
    public static final ForgeConfigSpec.IntValue CAMPFIRE_FLEE_RADIUS;

    /** Default for the per-maid "do not work in winter" switch. */
    public static final ForgeConfigSpec.BooleanValue WINTER_SKIP_DEFAULT_ENABLED;
    /** TLM task ids that count as "work" and are skipped in winter. */
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WINTER_SKIP_TASKS;

    /** Master switch for "sleeps in instead of getting up at the scheduled wake-up". */
    public static final ForgeConfigSpec.BooleanValue LIE_IN_ENABLED;
    /** Chance to stay in bed when the schedule says it is time to get up. */
    public static final ForgeConfigSpec.IntValue LIE_IN_CHANCE_PERCENT;
    /** Same chance in winter (needs Serene Seasons; without it the plain number is used). */
    public static final ForgeConfigSpec.IntValue LIE_IN_WINTER_CHANCE_PERCENT;
    /** Shortest / longest lie-in, in in-game hours. */
    public static final ForgeConfigSpec.IntValue LIE_IN_MIN_HOURS;
    public static final ForgeConfigSpec.IntValue LIE_IN_MAX_HOURS;

    /** Master switch for "walks back to bed on her own during the day". */
    public static final ForgeConfigSpec.BooleanValue NAP_ENABLED;
    /** Chance per check interval that an awake, idle maid heads back to bed. */
    public static final ForgeConfigSpec.IntValue NAP_CHANCE_PERCENT;
    public static final ForgeConfigSpec.IntValue NAP_WINTER_CHANCE_PERCENT;
    /** Minutes of in-game time between two nap rolls. */
    public static final ForgeConfigSpec.IntValue NAP_CHECK_MINUTES;
    public static final ForgeConfigSpec.IntValue NAP_MIN_HOURS;
    public static final ForgeConfigSpec.IntValue NAP_MAX_HOURS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment(
                "Sleep Well: fallback teleport for maids that cannot reach their scheduled sleep point.",
                "睡个好觉：到不了排班睡觉点的女仆兜底传送。"
        );

        builder.push("sleepFallback");
        SLEEP_FALLBACK_ENABLED = builder
                .comment(
                        "Fallback teleport when a maid with a kappa-compass sleep point cannot reach it (stuck on ladders, fences, drops).",
                        "Vanilla only teleports a maid that is outside (restrict radius + 4) and not already walking to the center,",
                        "so a maid stuck inside her sleep area would retry forever. Vanilla landing is tried once, then a mod-side search.",
                        "当河童罗盘设置过睡觉点的女仆到不了睡觉点时兜底传送（卡梯子/栅栏/落差）。",
                        "原版只在据点半径+4以外且未在走向中心时传送，圈内卡死会永远重试。",
                        "先试一次原版安全落点，失败再用本模组的落点搜索。"
                )
                .define("sleepFallbackEnabled", true);
        SLEEP_FALLBACK_SECONDS = builder
                .comment(
                        "Seconds without progress towards the sleep point before the fallback fires.",
                        "距离睡觉点多久没有进展就开始兜底传送（秒）。"
                )
                .defineInRange("sleepFallbackSeconds", 5, 2, 60);
        SLEEP_FALLBACK_MAX_TELEPORTS = builder
                .comment(
                        "Fallback teleports allowed per nap. 0 disables them without touching the switch.",
                        "每次入睡最多兜底传送几次。0 等于关闭（开关不用动）。"
                )
                .defineInRange("sleepFallbackMaxTeleports", 2, 0, 5);
        builder.pop();

        builder.comment(
                "A maid that gets burned by a campfire runs a few blocks away from it.",
                "女仆被篝火烫到就跑开。"
        );
        builder.push("campfireFlee");
        CAMPFIRE_FLEE_ENABLED = builder
                .comment(
                        "Whether a maid burned by a campfire tries to run away from it.",
                        "Vanilla campfires deal plain fire damage while she stands in them, so she keeps burning",
                        "until something moves her. This makes her pick a safe standing spot 3..radius blocks away",
                        "and walk there (navigated, not teleported), for up to 5 seconds per burn.",
                        "女仆被篝火烫到后是否尝试跑开。",
                        "原版篝火对她只造成普通火焰伤害：站在里面会一直掉血，没人管就不动。",
                        "开启后她会挑一个 3..半径 格外的安全落点走过去（是走路，不是传送），每次最多 5 秒。"
                )
                .define("campfireFleeEnabled", true);
        CAMPFIRE_FLEE_RADIUS = builder
                .comment(
                        "How far the search for a safe standing spot reaches.",
                        "寻找安全落点的搜索半径（格）。"
                )
                .defineInRange("campfireFleeRadius", CampfireFleePolicy.DEFAULT_RADIUS,
                        CampfireFleePolicy.MIN_RADIUS, CampfireFleePolicy.MAX_RADIUS);
        builder.pop();

        builder.comment(
                "Winter work skip (needs Serene Seasons): a maid parked on the idle task instead of working.",
                "冬天跳过工作（需要静谧四季）：女仆改挂空闲任务，不去工位。"
        );
        builder.push("winterSkip");
        WINTER_SKIP_DEFAULT_ENABLED = builder
                .comment(
                        "Default value of the per-maid \"skip work in winter\" switch for newly met maids.",
                        "新女仆\"冬天不工作\"开关的默认值。"
                )
                .define("winterSkipDefaultEnabled", true);
        WINTER_SKIP_TASKS = builder
                .comment(
                        "TLM task ids treated as work. Bare names also match (farm == touhou_little_maid:farm).",
                        "算作\"工作\"的车万女仆任务 id，写裸名也行（farm 等同 touhou_little_maid:farm）。",
                        "Defaults: farm, cocoa, melon, sugar_cane, grass, snow, feed_animal, milk, shears, fishing, torch, honey, berries_farm",
                        "默认：种地、可可、西瓜、甘蔗、割草、扫雪、繁育动物、挤奶、剪羊毛、钓鱼、放火把、采蜂蜜、采浆果"
                )
                .defineList("winterSkipTasks",
                        List.of("farm", "cocoa", "melon", "sugar_cane", "grass", "snow",
                                "feed_animal", "milk", "shears", "fishing", "torch", "honey", "berries_farm"),
                        entry -> entry instanceof String);
        builder.pop();

        builder.comment(
                "Sleeping in and daytime naps: a maid stays in bed past her scheduled wake-up,",
                "and an idle maid now and then walks back to bed on her own.",
                "赖床与补觉：女仆会在排班起床时继续睡，空闲时也会自己回床补一觉。"
        );
        builder.push("lieIn");
        LIE_IN_ENABLED = builder
                .comment(
                        "Whether a maid may stay in bed when her schedule says it is time to get up.",
                        "At that moment the chance below is rolled once; on a hit she keeps sleeping for a",
                        "random 2..5 in-game hours. Works for every task, winter is only a boost.",
                        "排班起床时是否可能继续赖床。",
                        "那一刻掷一次下面的概率：中了就再睡 2~5 游戏内小时。所有任务都适用，冬天只是提高概率。"
                )
                .define("lieInEnabled", true);
        LIE_IN_CHANCE_PERCENT = builder
                .comment("Chance to lie in, in percent.", "赖床概率（百分比）。")
                .defineInRange("lieInChancePercent", 30, 0, 100);
        LIE_IN_WINTER_CHANCE_PERCENT = builder
                .comment(
                        "Lie-in chance in winter. Needs Serene Seasons installed; without it the plain chance is used.",
                        "冬季赖床概率。需要安装静谧四季；没装就用上面的普通概率。"
                )
                .defineInRange("lieInWinterChancePercent", 50, 0, 100);
        LIE_IN_MIN_HOURS = builder
                .comment("Shortest lie-in, in in-game hours (1 hour = 1000 ticks).", "最短赖床时长（游戏内小时，1 小时 = 1000 tick）。")
                .defineInRange("lieInMinHours", 2, 1, 12);
        LIE_IN_MAX_HOURS = builder
                .comment("Longest lie-in, in in-game hours.", "最长赖床时长（游戏内小时）。")
                .defineInRange("lieInMaxHours", 5, 1, 12);
        NAP_ENABLED = builder
                .comment(
                        "Whether an awake maid parked on the idle task may walk back to bed on her own.",
                        "She rolls every napCheckMinutes while idle; on a hit the rest activity's bed task",
                        "walks her to the bed and she sleeps for napMinHours..napMaxHours.",
                        "空闲任务的女仆是否会自己回床补觉。",
                        "空闲时每 napCheckMinutes 分钟掷一次：中了就交给原版休息行为走回床，睡 napMinHours~napMaxHours 小时。"
                )
                .define("napEnabled", true);
        NAP_CHANCE_PERCENT = builder
                .comment("Chance per check interval, in percent.", "每次判定的补觉概率（百分比）。")
                .defineInRange("napChancePercent", 5, 0, 100);
        NAP_WINTER_CHANCE_PERCENT = builder
                .comment(
                        "Nap chance in winter. Needs Serene Seasons installed; without it the plain chance is used.",
                        "冬季补觉概率。需要安装静谧四季；没装就用普通概率。"
                )
                .defineInRange("napWinterChancePercent", 10, 0, 100);
        NAP_CHECK_MINUTES = builder
                .comment("Minutes of in-game time between two nap rolls.", "两次补觉判定之间隔多少游戏内分钟。")
                .defineInRange("napCheckMinutes", 30, 5, 120);
        NAP_MIN_HOURS = builder
                .comment("Shortest nap, in in-game hours.", "最短补觉时长（游戏内小时）。")
                .defineInRange("napMinHours", 2, 1, 12);
        NAP_MAX_HOURS = builder
                .comment("Longest nap, in in-game hours.", "最长补觉时长（游戏内小时）。")
                .defineInRange("napMaxHours", 3, 1, 12);
        builder.pop();

        SPEC = builder.build();
    }

    private SleepWellConfig() {
    }
}
