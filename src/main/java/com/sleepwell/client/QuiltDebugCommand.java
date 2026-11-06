package com.sleepwell.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.brigadier.context.CommandContext;
import com.sleepwell.client.quilt.collider.ColliderSet;
import com.sleepwell.client.quilt.collider.MaidColliderCapture;
import com.sleepwell.client.quilt.collider.QuiltWireframe;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * In-game switches for the quilt diagnostics, so a test needs no JVM argument and no restart.
 * <p>
 * {@code /swquilt wireframe on} shows the captured boxes (blue = body, dark = soft parts) and the capsule
 * chains; {@code /swquilt status} reports how complete the capture is for every sleeping maid nearby, which
 * is what tells "the quilt is wrong" apart from "her boxes were never captured".
 */
public final class QuiltDebugCommand {
    private QuiltDebugCommand() {
    }

    /** {@code /sleepwell wireframe on|off}: hangs off the mod's own command instead of its own root. */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> wireframeNode() {
        return Commands.literal("wireframe")
                .then(Commands.literal("on").executes(context -> wireframe(context, true)))
                .then(Commands.literal("off").executes(context -> wireframe(context, false)))
                .executes(QuiltDebugCommand::help);
    }

    /** {@code /sleepwell status}: capture report for every sleeping maid nearby. */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> statusNode() {
        return Commands.literal("status").executes(QuiltDebugCommand::status);
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        say(context, "/sleepwell wireframe on|off  — 显示/隐藏采集线框（蓝=身体，暗=软部件）", ChatFormatting.AQUA);
        say(context, "/sleepwell status            — 报告附近睡觉女仆的盒子数与范围", ChatFormatting.AQUA);
        return 1;
    }

    private static int wireframe(CommandContext<CommandSourceStack> context, boolean value) {
        QuiltWireframe.setEnabled(value);
        say(context, "[被子] 线框已" + (value ? "打开" : "关闭"), value ? ChatFormatting.GREEN : ChatFormatting.GRAY);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Vec3 camera = source.getPosition();
        List<EntityMaid> maids = source.getLevel().getEntitiesOfClass(EntityMaid.class,
                new AABB(camera.x - 48.0D, camera.y - 48.0D, camera.z - 48.0D,
                        camera.x + 48.0D, camera.y + 48.0D, camera.z + 48.0D),
                EntityMaid::isSleeping);
        if (maids.isEmpty()) {
            say(context, "[被子] 附近没有睡着的女仆", ChatFormatting.YELLOW);
            return 0;
        }
        say(context, "[被子] 附近睡着 " + maids.size() + " 只：", ChatFormatting.AQUA);
        for (EntityMaid maid : maids) {
            ColliderSet set = MaidColliderCapture.get(maid);
            if (set == null || set.boxes.isEmpty()) {
                say(context, "  " + shortId(maid) + " 盒子=无（采集失败）", ChatFormatting.RED);
                continue;
            }
            float[] bounds = set.bounds;
            say(context, String.format(Locale.ROOT,
                            "  %s 盒子=%d 尺寸=%.2fx%.2fx%.2f 范围Y=%.2f..%.2f 帧龄=%d",
                            shortId(maid), set.boxes.size(), bounds[3] - bounds[0], bounds[4] - bounds[1],
                            bounds[5] - bounds[2], bounds[1], bounds[4],
                            MaidColliderCapture.frameCounter() - set.captureFrame),
                    set.boxes.size() < 100 ? ChatFormatting.RED : ChatFormatting.WHITE);
        }
        return maids.size();
    }

    private static String shortId(EntityMaid maid) {
        return maid.getUUID().toString().substring(0, 8);
    }

    private static void say(CommandContext<CommandSourceStack> context, String message, ChatFormatting color) {
        context.getSource().sendSuccess(() -> Component.literal(message).withStyle(color), false);
    }
}
