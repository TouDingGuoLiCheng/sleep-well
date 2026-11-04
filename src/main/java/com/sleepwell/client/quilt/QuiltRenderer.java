package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * The quilt over a sleeping maid, drawn as a curved mesh instead of stacked boxes.
 * <p>
 * Placement no longer goes through TLM's bedrock model space: it is built from the two blocks the bed
 * occupies. The renderer's pose sits on this bed block's corner, so everything is computed in block-local
 * coordinates — the quilt always lands on the mattress no matter which way the bed faces.
 */
public final class QuiltRenderer {
    private static final Map<DyeColor, ResourceLocation> TEXTURES = Map.of(
            DyeColor.WHITE, texture("white"),
            DyeColor.BLACK, texture("black"),
            DyeColor.YELLOW, texture("yellow"),
            DyeColor.BLUE, texture("blue"),
            DyeColor.GREEN, texture("green"),
            DyeColor.PURPLE, texture("purple"),
            DyeColor.PINK, texture("pink"));

    /** How many strips the curved top is split into. */
    private static final int SEGMENTS = 16;
    /** Half width of the duvet (the mattress is 1 block wide). */
    private static final float HALF_WIDTH = 0.45F;
    /** Height of the bulge over the mattress, in blocks. */
    private static final float BULGE = 0.30F;
    /** How much the duvet hangs down over the sides. */
    private static final float SKIRT = 0.10F;
    /** Left free at the pillow end, so her head stays outside the duvet. */
    private static final float HEAD_GAP = 0.35F;

    private static boolean LOGGED;

    private QuiltRenderer() {
    }

    private static ResourceLocation texture(String color) {
        return new ResourceLocation("sleep_well", "textures/quilt/" + color + ".png");
    }

    public static void render(TileEntityMaidBed bed, BlockState state, PoseStack pose, MultiBufferSource buffer,
                              int packedLight, int packedOverlay) {
        if (state == null || !state.getValue(BlockMaidBed.OCCUPIED)) {
            return;
        }
        Direction axis = findAxis(bed, state);
        if (axis == null) {
            return;
        }
        float top = mattressTop(bed, state);
        ResourceLocation texture = TEXTURES.getOrDefault(bed.getColor(), TEXTURES.get(DyeColor.WHITE));
        // No culling: the mesh is hand built and its winding would otherwise hide the top face entirely.
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        if (!LOGGED) {
            LOGGED = true;
            System.out.println("[sleep_well] quilt render: axis=" + axis + " top=" + top + " color=" + bed.getColor()
                    + " state=" + state);
        }
        PoseStack.Pose last = pose.last();

        Direction side = axis.getClockWise();
        float uStart = HEAD_GAP;
        float uEnd = 2.0F;
        // The far end is measured from this block's corner along the axis.
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = i / (float) SEGMENTS;
            float t1 = (i + 1) / (float) SEGMENTS;
            float u0 = Mth.lerp(t0, uStart, uEnd);
            float u1 = Mth.lerp(t1, uStart, uEnd);
            float h0 = height(t0, 1.0F);
            float h1 = height(t1, 1.0F);
            for (int w = 0; w < 2; w++) {
                float w0 = w == 0 ? -HALF_WIDTH : 0.0F;
                float w1 = w == 0 ? 0.0F : HALF_WIDTH;
                float c0 = w == 0 ? 0.84F : 1.0F;
                float c1 = w == 0 ? 1.0F : 0.84F;
                // top surface: two quads per strip (left half / right half), so the cross section is round
                quad(consumer, last, axis, side, u0, u1, w0, w1, top, h0, h1, c0, c1, packedLight, packedOverlay);
            }
        }
        // hanging cloth on both long sides
        skirt(consumer, last, axis, side, uStart, uEnd, -HALF_WIDTH, top, packedLight, packedOverlay);
        skirt(consumer, last, axis, side, uStart, uEnd, HALF_WIDTH, top, packedLight, packedOverlay);
        // foot end cap
        endCap(consumer, last, axis, side, uEnd, uStart, top, packedLight, packedOverlay);
        // head end cap
        endCap(consumer, last, axis, side, uStart, uEnd, top, packedLight, packedOverlay);
    }

    /** Height of the duvet above the mattress for a strip position {@code t} (0 = head, 1 = foot). */
    private static float height(float t, float across) {
        float along = Mth.sin(t * Mth.PI);
        float roundness = 1.0F - 0.35F * across * across;
        return BULGE * along * 0.85F + BULGE * roundness * along * 0.25F;
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, Direction axis, Direction side,
                             float u0, float u1, float w0, float w1, float top, float h0, float h1,
                             float c0, float c1, int light, int overlay) {
        float shade = 0.9F;
        float[] p00 = point(axis, side, u0, w0, top + h0 * 0.75F);
        float[] p01 = point(axis, side, u0, w1, top + h1 * 0.75F);
        float[] p10 = point(axis, side, u1, w0, top + h0 * 0.75F);
        float[] p11 = point(axis, side, u1, w1, top + h1 * 0.75F);
        float[] n = normal(axis, side);
        vertex(consumer, pose, p00, 0.0F, 0.0F, c0 * shade, n, light, overlay);
        vertex(consumer, pose, p10, 1.0F, 0.0F, c0 * shade, n, light, overlay);
        vertex(consumer, pose, p11, 1.0F, 1.0F, c1 * shade, n, light, overlay);
        vertex(consumer, pose, p01, 0.0F, 1.0F, c1 * shade, n, light, overlay);
    }

    private static void skirt(VertexConsumer consumer, PoseStack.Pose pose, Direction axis, Direction side,
                              float u0, float u1, float w, float top, int light, int overlay) {
        float[] a = point(axis, side, u0, w, top - SKIRT);
        float[] b = point(axis, side, u1, w, top - SKIRT);
        float[] c = point(axis, side, u1, w, top);
        float[] d = point(axis, side, u0, w, top);
        float shade = w < 0 ? 0.62F : 0.7F;
        float[] n = normal(axis, side);
        float nx = w < 0 ? -n[0] : n[0];
        float nz = w < 0 ? -n[2] : n[2];
        vertex(consumer, pose, a, 0.0F, 0.0F, shade, new float[]{nx, 0.0F, nz}, light, overlay);
        vertex(consumer, pose, d, 0.0F, 1.0F, shade, new float[]{nx, 0.0F, nz}, light, overlay);
        vertex(consumer, pose, c, 1.0F, 1.0F, shade, new float[]{nx, 0.0F, nz}, light, overlay);
        vertex(consumer, pose, b, 1.0F, 0.0F, shade, new float[]{nx, 0.0F, nz}, light, overlay);
    }

    private static void endCap(VertexConsumer consumer, PoseStack.Pose pose, Direction axis, Direction side,
                               float u, float uOther, float top, int light, int overlay) {
        float dir = u < uOther ? -1.0F : 1.0F;
        float[] a = point(axis, side, u, -HALF_WIDTH, top - SKIRT);
        float[] b = point(axis, side, u, HALF_WIDTH, top - SKIRT);
        float[] c = point(axis, side, u, HALF_WIDTH, top);
        float[] d = point(axis, side, u, -HALF_WIDTH, top);
        float[] n = {axis.getStepX() * dir, 0.0F, axis.getStepZ() * dir};
        vertex(consumer, pose, a, 0.0F, 0.0F, 0.58F, n, light, overlay);
        vertex(consumer, pose, b, 1.0F, 0.0F, 0.58F, n, light, overlay);
        vertex(consumer, pose, c, 1.0F, 1.0F, 0.66F, n, light, overlay);
        vertex(consumer, pose, d, 0.0F, 1.0F, 0.66F, n, light, overlay);
    }

    private static float[] point(Direction axis, Direction side, float u, float w, float y) {
        float x = axis.getStepX() * u + side.getStepX() * (0.5F + w);
        float z = axis.getStepZ() * u + side.getStepZ() * (0.5F + w);
        return new float[]{x, y, z};
    }

    private static float[] normal(Direction axis, Direction side) {
        return new float[]{0.0F, 1.0F, 0.0F};
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float[] p, float u, float v,
                               float shade, float[] normal, int light, int overlay) {
        consumer.vertex(pose.pose(), p[0], p[1], p[2])
                .color(shade, shade, shade, 1.0F)
                .uv(u, v)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(pose.normal(), normal[0], normal[1], normal[2])
                .endVertex();
    }

    /** The neighbour block of the same bed, i.e. the direction the mattress runs. */
    private static Direction findAxis(TileEntityMaidBed bed, BlockState state) {
        if (bed.getLevel() == null) {
            return null;
        }
        BlockPos pos = bed.getBlockPos();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (bed.getLevel().getBlockState(pos.relative(direction)).getBlock() instanceof BlockMaidBed) {
                return direction;
            }
        }
        // The two halves are always adjacent; if the neighbour is not loaded yet, fall back to the facing.
        return state.getValue(HorizontalDirectionalBlock.FACING).getOpposite();
    }

    /** Mattress top from the block's own collision shape, so it follows TLM if the bed changes. */
    private static float mattressTop(TileEntityMaidBed bed, BlockState state) {
        VoxelShape shape = state.getShape(bed.getLevel(), bed.getBlockPos());
        float top = (float) shape.max(Direction.Axis.Y);
        // Shapes that cover the whole block (or none at all) are no use here: fall back to the mattress height.
        if (top <= 0.05F || top > 0.95F) {
            return 0.5625F;
        }
        return top;
    }
}
