package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sleepwell.SleepWellMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * The quilt is drawn in the maid's own model space, as the last thing after her model.
 * <p>
 * That is the whole point: her sleeping rotation is already baked into the PoseStack, so the cloth simply
 * follows whatever pose she is in, and it is sized from her own body instead of from a guess about the bed.
 * Nothing of the model can end up outside the quilt, because the quilt is a shell around her.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID, value = Dist.CLIENT)
public final class QuiltRenderer {
    /** Radius of the shell around her body axis, in blocks. Grows with her hitbox width. */
    private static final float MIN_RADIUS = 0.30F;
    private static final float RADIUS_PAD = 0.10F;
    /** How far up her body the cloth runs: from just above the feet to the chest, leaving the pillow free. */
    private static final float FROM_FEET = 0.08F;
    private static final float TO_CHEST = 1.42F;
    /** How far around her the cloth wraps, in degrees, measured from "up". */
    private static final float WRAP_DEGREES = 100.0F;
    /**
     * Body profile along her own model axis: the cloth rises over the legs, dips at the knees and the waist,
     * bulges over the hips and the chest, so her outline shows through instead of a plain tube.
     * First column is the height in model blocks, second a multiplier on the base radius.
     */
    private static final float[][] PROFILE = {
            {0.00F, 0.90F},
            {0.42F, 0.97F},
            {0.56F, 0.84F},
            {0.72F, 1.00F},
            {0.86F, 1.08F},
            {1.00F, 0.89F},
            {1.14F, 1.05F},
            {1.30F, 0.98F},
            {1.46F, 0.86F}
    };
    private static final int ALONG_STEPS = 18;
    private static final int AROUND_STEPS = 10;

    private static final Map<DyeColor, ResourceLocation> TEXTURES = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : BlockMaidBed.AVAILABLE_COLOR) {
            TEXTURES.put(color, new ResourceLocation(SleepWellMod.MOD_ID,
                    "textures/quilt/" + color.getSerializedName() + ".png"));
        }
    }

    private QuiltRenderer() {
    }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRenderLivingPost(RenderLivingEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || !maid.isSleeping()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        DyeColor color = colorOf(maid);
        VertexConsumer consumer = event.getMultiBufferSource()
                .getBuffer(RenderType.entityCutoutNoCull(TEXTURES.getOrDefault(color,
                        new ResourceLocation(SleepWellMod.MOD_ID, "textures/quilt/pink.png"))));
        int light = event.getPackedLight();

        // Where "up" points, expressed in her model space. Her sleeping rotation is inside the pose already,
        // so this is all that is needed to know which side of her the blanket belongs on.
        Matrix3f worldToModel = new Matrix3f(pose.last().pose()).invert();
        Vector3f up = worldToModel.transform(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        Vector3f axis = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f side = new Vector3f(up).cross(axis);
        if (side.lengthSquared() < 1.0E-6F) {
            return;
        }
        side.normalize();

        float radius = Math.max(MIN_RADIUS, (float) maid.getBbWidth() * 0.5F);
        float wrap = (float) Math.toRadians(WRAP_DEGREES);

        PoseStack.Pose entry = pose.last();
        Vector3f scratch = new Vector3f();
        for (int i = 0; i < ALONG_STEPS; i++) {
            float t0 = (float) i / ALONG_STEPS;
            float t1 = (float) (i + 1) / ALONG_STEPS;
            float y0 = FROM_FEET + (TO_CHEST - FROM_FEET) * t0;
            float y1 = FROM_FEET + (TO_CHEST - FROM_FEET) * t1;
            float r0 = radiusAt(y0, radius);
            float r1 = radiusAt(y1, radius);
            for (int j = 0; j < AROUND_STEPS; j++) {
                float a0 = -wrap + 2.0F * wrap * j / AROUND_STEPS;
                float a1 = -wrap + 2.0F * wrap * (j + 1) / AROUND_STEPS;
                float shade0 = 0.72F + 0.28F * (float) Math.cos(a0 * 0.7D);
                float shade1 = 0.72F + 0.28F * (float) Math.cos(a1 * 0.7D);
                vertex(consumer, entry, scratch, up, side, r0, y0, a0, shade0, light);
                vertex(consumer, entry, scratch, up, side, r1, y1, a0, shade0, light);
                vertex(consumer, entry, scratch, up, side, r1, y1, a1, shade1, light);
                vertex(consumer, entry, scratch, up, side, r0, y0, a1, shade1, light);
            }
        }
        // Little flap hanging off the two edges, so the cloth does not read as a painted band.
        for (int j : new int[]{0, AROUND_STEPS}) {
            float a = -wrap + 2.0F * wrap * j / AROUND_STEPS;
            float sx = (float) Math.cos(a);
            float sy = (float) Math.sin(a);
            for (int i = 0; i < ALONG_STEPS; i++) {
                float y0 = FROM_FEET + (TO_CHEST - FROM_FEET) * i / ALONG_STEPS;
                float y1 = FROM_FEET + (TO_CHEST - FROM_FEET) * (i + 1) / ALONG_STEPS;
                addPoint(scratch, up, side, radiusAt(y0, radius), y0, sx, sy);
                vertex(consumer, entry, scratch, 0.0F, 0.0F, 0.70F, light);
                addPoint(scratch, up, side, radiusAt(y0 - 0.10F, radius) * 0.94F, y0 - 0.12F, sx, sy);
                vertex(consumer, entry, scratch, 0.0F, 0.5F, 0.70F, light);
                addPoint(scratch, up, side, radiusAt(y1 - 0.10F, radius) * 0.94F, y1 - 0.12F, sx, sy);
                vertex(consumer, entry, scratch, 1.0F, 0.5F, 0.70F, light);
                addPoint(scratch, up, side, radiusAt(y1, radius), y1, sx, sy);
                vertex(consumer, entry, scratch, 1.0F, 0.0F, 0.70F, light);
            }
        }
    }

    private static DyeColor colorOf(EntityMaid maid) {
        BlockPos pos = maid.blockPosition();
        for (BlockPos candidate : new BlockPos[]{pos, pos.below()}) {
            if (Minecraft.getInstance().level == null) {
                break;
            }
            BlockState state = Minecraft.getInstance().level.getBlockState(candidate);
            if (state.getBlock() instanceof BlockMaidBed
                    && Minecraft.getInstance().level.getBlockEntity(candidate) instanceof TileEntityMaidBed bed) {
                return bed.getColor();
            }
        }
        return DyeColor.PINK;
    }

    /** Radius of the shell at a given height along her body, from the profile above. */
    private static float radiusAt(float y, float base) {
        float factor = PROFILE[PROFILE.length - 1][1];
        for (int i = 0; i < PROFILE.length - 1; i++) {
            float low = PROFILE[i][0];
            float high = PROFILE[i + 1][0];
            if (y <= high) {
                float t = Math.max(0.0F, Math.min(1.0F, (y - low) / (high - low)));
                factor = PROFILE[i][1] + (PROFILE[i + 1][1] - PROFILE[i][1]) * t;
                break;
            }
        }
        return base * factor + RADIUS_PAD * 0.5F;
    }

    private static void addPoint(Vector3f out, Vector3f up, Vector3f side, float radius, float y,
                                 float cos, float sin) {
        float flat = sin * 0.90F;
        out.set(up).mul(radius * cos).add(side.x * radius * flat, side.y * radius * flat, side.z * radius * flat);
        out.y += y;
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose entry, Vector3f scratch, Vector3f up,
                               Vector3f side, float radius, float y, float angle, float shade, int light) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        addPoint(scratch, up, side, radius, y, cos, sin);
        vertex(consumer, entry, scratch, (float) (angle / (2.0D * Math.PI)) + 0.5F, y, shade, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose entry, Vector3f position,
                               float u, float v, float shade, int light) {
        consumer.vertex(entry.pose(), position.x, position.y, position.z)
                .color(shade, shade, shade, 1.0F)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(entry.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
