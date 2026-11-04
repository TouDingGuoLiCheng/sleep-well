package com.sleepwell.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.tileentity.TileEntityMaidBedRenderer;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sleepwell.client.quilt.QuiltRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the quilt right after TLM drew the bed itself (client only).
 * <p>
 * The bed renderer pushes and pops its own pose around the model, so at TAIL the stack is back at the block
 * origin and {@link QuiltRenderer} can apply the very same transform the bed used.
 */
@Mixin(TileEntityMaidBedRenderer.class)
public abstract class TileEntityMaidBedRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void sleepwell$drawQuilt(TileEntityMaidBed bed, float partialTick, PoseStack pose,
                                     MultiBufferSource buffer, int packedLight, int packedOverlay,
                                     CallbackInfo ci) {
        // HEAD, not TAIL: TLM pushes its own translate/scale for the bed model, so at TAIL the pose is no
        // longer the block origin. At HEAD it is, and the quilt can be built in plain block coordinates.
        BlockState state = bed.getBlockState();
        QuiltRenderer.render(bed, state, pose, buffer, packedLight, packedOverlay);
    }
}
