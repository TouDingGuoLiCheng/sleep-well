package com.sleepwell.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sleepwell.client.quilt.collider.MaidColliderCapture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capture point for the sleeping outline.
 * <p>
 * {@code RenderLivingEvent.Post} is not enough: TLM's maid renderer delegates to this gecko renderer
 * instead of going through vanilla's {@code LivingEntityRenderer#render}, so the Forge event may never
 * fire for a maid. Here the pose stack already carries entity translation, sleeping rotation and scale,
 * and the animation has been applied - exactly the frame we need.
 * <p>
 * {@code require = 0}: if a TLM update changes this signature the game still starts, it just logs that
 * the capture point went missing instead of refusing to launch.
 */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer", remap = false)
public abstract class GeoRendererCaptureMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0, remap = false)
    @SuppressWarnings("rawtypes")
    private void sleepwell$captureSleepingMaid(LivingEntity entity, AnimatableEntity animatable,
                                                float entityYaw, float partialTick, PoseStack poseStack,
                                                MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity instanceof EntityMaid maid && maid.isSleeping()) {
            MaidColliderCapture.capture(maid, poseStack, partialTick);
        }
    }
}
