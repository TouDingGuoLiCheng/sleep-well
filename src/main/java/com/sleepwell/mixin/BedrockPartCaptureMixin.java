package com.sleepwell.mixin;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sleepwell.client.quilt.collider.MaidColliderCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports every {@code BedrockPart} exactly where it is drawn.
 * <p>
 * Non-gecko maid models (the vanilla-style packs) are {@code BedrockPart} trees, and the matrix inside the
 * render call is the only exact statement of where a part's cubes land - reconstructing it from the parts'
 * fields meant guessing at vanilla's sleeping transform, which is why those maids came out with no outline at
 * all. Reading the matrix the game actually used removes that guesswork.
 * <p>
 * {@code BedrockPart} has two {@code render} overloads, so each handler names its own descriptor: mixin keys
 * handlers by name, and two handlers sharing a name is what made the last build fail to launch.
 * <p>
 * The hook is a no-op unless a sleeping maid render pass is open, so normal movement costs one boolean.
 */
@Mixin(BedrockPart.class)
public abstract class BedrockPartCaptureMixin {
    private static final String FLAT =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V";
    private static final String TINT =
            "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V";

    @Inject(method = FLAT, at = @At("HEAD"), require = 0, remap = false)
    private void sleepwell$partRenderFlat(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                                          CallbackInfo ci) {
        capture(poseStack);
    }

    @Inject(method = TINT, at = @At("HEAD"), require = 0, remap = false)
    private void sleepwell$partRenderTint(PoseStack poseStack, VertexConsumer consumer, int light, int overlay,
                                          float red, float green, float blue, float alpha, CallbackInfo ci) {
        capture(poseStack);
    }

    private void capture(PoseStack poseStack) {
        if (!MaidColliderCapture.wantsParts()) {
            return;
        }
        BedrockPart self = (BedrockPart) (Object) this;
        if (!self.visible) {
            return;
        }
        // A copy of the stack: translateAndRotate mutates it, and the render call must not see our edits.
        PoseStack copy = new PoseStack();
        copy.last().pose().set(poseStack.last().pose());
        self.translateAndRotate(copy);
        MaidColliderCapture.partRendered(self, copy.last().pose());
    }
}
