package com.sleepwell.config;

import net.minecraftforge.common.ForgeConfigSpec;

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

        SPEC = builder.build();
    }

    private SleepWellConfig() {
    }
}
