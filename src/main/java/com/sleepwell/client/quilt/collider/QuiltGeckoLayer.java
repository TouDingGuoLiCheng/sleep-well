package com.sleepwell.client.quilt.collider;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.NotNull;

/**
 * 挂在女仆 gecko 渲染器上的采集层。
 * <p>
 * 这一层在女仆自身的渲染流程里被调用：此时 {@code PoseStack} 已经含完整变换（含睡觉姿态），
 * 动画实体也已完成 {@code updateModel()}，骨骼里是这一帧真实的姿势。之前所有绕路（渲染事件、
 * 接口 mixin、外层 {@code EntityMaidRenderer#getModel()}）拿到的都是空壳，这一层才是答案。
 */
public final class QuiltGeckoLayer extends GeoLayerRenderer<EntityMaid, GeckoEntityMaidRenderer<EntityMaid>> {
    public QuiltGeckoLayer(GeckoEntityMaidRenderer<EntityMaid> renderer) {
        super(renderer);
    }

    @Override
    public @NotNull GeoLayerRenderer<EntityMaid, GeckoEntityMaidRenderer<EntityMaid>> copy(
            GeckoEntityMaidRenderer<EntityMaid> renderer) {
        return new QuiltGeckoLayer(renderer);
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int light, @NotNull EntityMaid maid,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        MaidColliderCapture.captureFromLayer(maid, getGeoEntity(maid), poseStack.last().pose());
    }
}
