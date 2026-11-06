package com.sleepwell.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sleepwell.SleepWellMod;
import com.sleepwell.liein.LieInHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

/** {@code /sleepwell nap}: send the nearest maid to bed right away. Built for testing. */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID)
public final class SleepWellCommands {
    private static final double SEARCH_RADIUS = 32.0D;

    private SleepWellCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal("sleepwell")
                        .then(Commands.literal("nap")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> napNearest(context.getSource())));
        // The quilt diagnostics read client-side state, so they are only offered where that state exists.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            root.then(com.sleepwell.client.QuiltDebugCommand.wireframeNode())
                    .then(com.sleepwell.client.QuiltDebugCommand.statusNode());
        }
        event.getDispatcher().register(root);
    }

    private static int napNearest(CommandSourceStack source) {
        Player player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.translatable("command.sleep_well.nap.no_player"));
            return 0;
        }
        List<EntityMaid> maids = player.level().getEntitiesOfClass(EntityMaid.class,
                new AABB(player.blockPosition()).inflate(SEARCH_RADIUS));
        EntityMaid nearest = maids.stream()
                .min(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)))
                .orElse(null);
        if (nearest == null) {
            source.sendFailure(Component.translatable("command.sleep_well.nap.no_maid"));
            return 0;
        }
        return switch (LieInHandler.triggerNapNow(nearest)) {
            case STARTED -> {
                source.sendSuccess(() -> Component.translatable("command.sleep_well.nap.started",
                        nearest.getDisplayName()), false);
                yield 1;
            }
            case ALREADY_SLEEPING -> {
                source.sendFailure(Component.translatable("command.sleep_well.nap.already", nearest.getDisplayName()));
                yield 0;
            }
            case SWITCH_OFF -> {
                source.sendFailure(Component.translatable("command.sleep_well.nap.switch_off", nearest.getDisplayName()));
                yield 0;
            }
            case NO_BED -> {
                source.sendFailure(Component.translatable("command.sleep_well.nap.no_bed", nearest.getDisplayName()));
                yield 0;
            }
        };
    }
}
