package com.sleepwell.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sleepwell.client.quilt.collider.MaidColliderCapture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets one maid render pass so the per-bone hook knows which maid the bones belong to.
 * <p>
 * Both the SRG name ({@code m_7392_}, what the released jar carries) and {@code render} (dev mappings) are
 * listed; {@code require = 0} keeps a mapping mismatch from breaking startup.
 */
@Mixin(EntityMaidRenderer.class)
public abstract class MaidRendererCaptureMixin {
    @Inject(method = {"m_7392_", "render"}, at = @At("HEAD"), require = 0, remap = false)
    private void sleepwell$capture(Mob entity, float entityYaw, float partialTick, PoseStack poseStack,
                                   MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity instanceof EntityMaid maid) {
            MaidColliderCapture.beginRenderPass(maid);
            MaidColliderCapture.captureDuringRender(maid);
        }
    }

    /** Closes the part pass: the model has now been drawn, so every part matrix has been seen. */
    @Inject(method = {"m_7392_", "render"}, at = @At("TAIL"), require = 0, remap = false)
    private void sleepwell$close(Mob entity, float entityYaw, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity instanceof EntityMaid maid) {
            MaidColliderCapture.endRenderPass(maid);
        }
    }
}
