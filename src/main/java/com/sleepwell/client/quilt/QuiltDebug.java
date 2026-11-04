package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/** Temporary diagnostics: what is a sleeping maid actually lying on? */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public final class QuiltDebug {
    private static int ticker;

    private QuiltDebug() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticker % 100 != 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Player player = minecraft.player;
        List<EntityMaid> maids = minecraft.level.getEntitiesOfClass(EntityMaid.class,
                player.getBoundingBox().inflate(24.0D));
        for (EntityMaid maid : maids) {
            if (!maid.isSleeping()) {
                continue;
            }
            BlockPos pos = maid.blockPosition();
            BlockState here = minecraft.level.getBlockState(pos);
            BlockState below = minecraft.level.getBlockState(pos.below());
            System.out.println("[sleep_well] sleeping maid " + maid.getUUID() + " at " + pos
                    + " here=" + here + " below=" + below + " pose=" + maid.getPose());
        }
    }
}
