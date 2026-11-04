package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.SimpleBedrockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.util.Map;

/**
 * The quilt that covers a maid sleeping in a maid bed.
 * <p>
 * It is drawn as part of the maid bed block renderer, so it inherits the bed's own transform and colour: no
 * need to mirror the maid's sleeping pose, and no drifting when she rolls over or a second maid joins her.
 * The model is authored in the same bedrock space as the bed itself (mattress top is y = 8.2 px).
 */
public final class QuiltRenderer {
    private static final ResourceLocation MODEL = new ResourceLocation("sleep_well", "models/bedrock/quilt.json");
    private static final Map<DyeColor, ResourceLocation> TEXTURES = Map.of(
            DyeColor.WHITE, texture("white"),
            DyeColor.BLACK, texture("black"),
            DyeColor.YELLOW, texture("yellow"),
            DyeColor.BLUE, texture("blue"),
            DyeColor.GREEN, texture("green"),
            DyeColor.PURPLE, texture("purple"),
            DyeColor.PINK, texture("pink"));

    private static SimpleBedrockModel<Entity> model;
    private static boolean broken;

    private QuiltRenderer() {
    }

    private static ResourceLocation texture(String color) {
        return new ResourceLocation("sleep_well", "textures/quilt/" + color + ".png");
    }

    public static void render(BlockState state, DyeColor color, PoseStack pose, MultiBufferSource buffer,
                              int packedLight, int packedOverlay) {
        if (state == null || !state.getValue(BlockMaidBed.OCCUPIED)) {
            return;
        }
        SimpleBedrockModel<Entity> quilt = model();
        if (quilt == null) {
            return;
        }
        ResourceLocation texture = TEXTURES.getOrDefault(color, TEXTURES.get(DyeColor.WHITE));
        int rotation = state.getValue(HorizontalDirectionalBlock.FACING).get2DDataValue();
        pose.pushPose();
        // Exactly the transform TLM's own bed renderer uses, so the quilt lands on the mattress.
        pose.rotateAround(Axis.YP.rotationDegrees(rotation * 90.0F), 0.5F, 0.0F, 0.5F);
        pose.translate(0.5D, 1.5D, -0.5D);
        pose.scale(-1.0F, -1.0F, 1.0F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutout(texture));
        quilt.renderToBuffer(pose, consumer, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
    }

    private static SimpleBedrockModel<Entity> model() {
        if (model != null || broken) {
            return model;
        }
        try (InputStream input = Minecraft.getInstance().getResourceManager().open(MODEL)) {
            model = new SimpleBedrockModel<>(input);
        } catch (Exception exception) {
            broken = true;
            System.err.println("[sleep_well] quilt model failed to load: " + exception);
        }
        return model;
    }
}
