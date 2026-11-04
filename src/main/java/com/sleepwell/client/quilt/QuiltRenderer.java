package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.sleepwell.SleepWellMod;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the quilt in plain world space, after the entities.
 * <p>
 * Both the bed renderer and the maid renderer hand out a PoseStack that already carries somebody else's
 * translate/rotate/scale, which is what kept throwing the mesh off the bed. At this stage the only transform
 * on the stack is the camera, so "bed block corner" is a number we fully control.
 */
@Mod.EventBusSubscriber(modid = SleepWellMod.MOD_ID, value = Dist.CLIENT)
public final class QuiltRenderer {
    /** Where the cloth starts, measured from the outer edge of the pillow end. */
    private static final float HEAD_GAP = 0.15F;
    private static final float FOOT_GAP = 0.15F;
    /** Half the mattress width: the bed is one block wide, so 0.40 keeps the cloth on the mattress. */
    private static final float HALF_WIDTH = 0.48F;
    /** Roughly how far the cloth sits above the mattress where it is not lifted by her body. */
    private static final float LIE_FLAT = 0.014F;
    private static final float BULGE = 0.16F;
    /**
     * Height multiplier along the bed, from the pillow end (0) to the feet end (1): chest, waist, hips,
     * knees, shins. This is what makes her outline visible under the cloth.
     */
    private static final float[][] PROFILE = {
            {0.00F, 0.45F},
            {0.10F, 0.85F},
            {0.22F, 1.05F},
            {0.36F, 0.72F},
            {0.52F, 0.98F},
            {0.68F, 0.60F},
            {0.84F, 0.70F},
            {1.00F, 0.45F}
    };
    /** Extra lift right over the maid, so the cloth drapes over her instead of floating flat. */
    private static final float BODY_BUMP = 0.07F;
    private static final float BODY_RADIUS = 0.45F;
    /** How far past her hitbox the cloth reaches. The feet reach far beyond the hitbox, so that side is longer. */
    private static final float HEAD_MARGIN = 0.45F;
    private static final float FOOT_MARGIN = 0.95F;
    private static final float SKIRT = 0.16F;
    private static final int SEGMENTS = 12;
    private static final int WIDTH_SEGMENTS = 6;
    private static final float FALLBACK_TOP = 0.5125F;

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
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        List<EntityMaid> sleeping = level.getEntitiesOfClass(EntityMaid.class,
                new net.minecraft.world.phys.AABB(camera.x - 64.0D, camera.y - 64.0D, camera.z - 64.0D,
                        camera.x + 64.0D, camera.y + 64.0D, camera.z + 64.0D),
                EntityMaid::isSleeping);
        if (sleeping.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (EntityMaid maid : sleeping) {
            Bed bed = findBed(level, maid.blockPosition());
            if (bed != null) {
                draw(pose, buffers, level, bed, maid);
            }
        }
        pose.popPose();
    }

    private static Bed findBed(ClientLevel level, BlockPos pos) {
        BlockPos[] candidates = {pos, pos.above(), pos.below()};
        for (BlockPos candidate : candidates) {
            BlockState state = level.getBlockState(candidate);
            if (!(state.getBlock() instanceof BlockMaidBed)) {
                continue;
            }
            BedPart part = state.getValue(BlockMaidBed.PART);
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            Direction toOther = part == BedPart.HEAD ? facing.getOpposite() : facing;
            BlockPos other = candidate.relative(toOther);
            if (!(level.getBlockState(other).getBlock() instanceof BlockMaidBed)) {
                continue;
            }
            BlockPos head = part == BedPart.HEAD ? candidate : other;
            BlockPos foot = part == BedPart.HEAD ? other : candidate;
            DyeColor color = DyeColor.PINK;
            if (level.getBlockEntity(head) instanceof TileEntityMaidBed tile) {
                color = tile.getColor();
            } else if (level.getBlockEntity(foot) instanceof TileEntityMaidBed tile) {
                color = tile.getColor();
            }
            return new Bed(head, foot, color);
        }
        return null;
    }

    private static void draw(PoseStack pose, MultiBufferSource.BufferSource buffers, ClientLevel level,
                             Bed bed, EntityMaid maid) {
        BlockState headState = level.getBlockState(bed.head());
        BlockState footState = level.getBlockState(bed.foot());
        // MIN of the two halves: the head half carries the headboard and would otherwise lift the whole quilt
        // to headboard height, which read as a tent floating above the bed.
        double top = Math.min(headState.getShape(level, bed.head()).max(net.minecraft.core.Direction.Axis.Y),
                footState.getShape(level, bed.foot()).max(net.minecraft.core.Direction.Axis.Y));
        if (top <= 0.0D || top > 0.60D) {
            top = FALLBACK_TOP;
        }
        Vec3 headCenter = Vec3.atLowerCornerOf(bed.head()).add(0.5D, top, 0.5D);
        Vec3 footCenter = Vec3.atLowerCornerOf(bed.foot()).add(0.5D, top, 0.5D);
        Vec3 along = new Vec3(footCenter.x - headCenter.x, 0.0D, footCenter.z - headCenter.z).normalize();
        Vec3 across = new Vec3(-along.z, 0.0D, along.x);
        float length = (float) headCenter.distanceTo(footCenter);
        float bedEnd = length + 1.0F - FOOT_GAP;

        // Reach: follow the maid's own footprint along the bed so that a long body still gets covered and a
        // small one is not buried under cloth.
        net.minecraft.world.phys.AABB box = maid.getBoundingBox();
        float uMin = Float.MAX_VALUE;
        float uMax = -Float.MAX_VALUE;
        float wMax = 0.0F;
        double[] xs = {box.minX, box.maxX};
        double[] zs = {box.minZ, box.maxZ};
        for (double x : xs) {
            for (double z : zs) {
                double dx = x - headCenter.x;
                double dz = z - headCenter.z;
                float u = (float) (dx * along.x + dz * along.z);
                float w = Math.abs((float) (dx * across.x + dz * across.z));
                uMin = Math.min(uMin, u);
                uMax = Math.max(uMax, u);
                wMax = Math.max(wMax, w);
            }
        }
        // Cover her whole footprint, then widen the reach so no part of the model ends up outside the cloth.
        float uStart = Math.max(HEAD_GAP, uMin - HEAD_MARGIN);
        float uEnd = Math.min(bedEnd, uMax + FOOT_MARGIN);
        if (uEnd - uStart < 1.0F) {
            uStart = Math.max(HEAD_GAP, uStart - 0.25F);
            uEnd = Math.min(bedEnd, uEnd + 0.25F);
        }
        float bodyCenter = (uStart + uEnd) * 0.5F;
        // Her own bones, when TLM's model exposes them; the hard coded profile is only the fallback.
        float[] silhouette = MaidSilhouette.sample(maid);
        float[] profileBySlice = new float[SEGMENTS + 1];
        for (int i = 0; i <= SEGMENTS; i++) {
            float t = (float) i / SEGMENTS;
            if (silhouette != null) {
                float bodyY = silhouette[0] * (1.0F - t);
                profileBySlice[i] = MaidSilhouette.radiusAt(silhouette, bodyY);
            } else {
                profileBySlice[i] = profileAt(t) * 0.22F;
            }
        }
        // Wide enough that the model cannot poke out of the sides.
        float halfWidth = Math.min(HALF_WIDTH, Math.max(0.42F, wMax + 0.12F));
        // Sit just above her, but never a full block up: a sleeping hitbox may still be the standing one.
        float clothLift = (float) Math.min(0.42D, Math.max(0.14D, box.maxY - headCenter.y + 0.06D));
        float skirt = clothLift + 0.06F;

        int light = LevelRenderer.getLightColor(level, bed.head());
        ResourceLocation texture = TEXTURES.getOrDefault(bed.color(),
                new ResourceLocation(SleepWellMod.MOD_ID, "textures/quilt/pink.png"));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));

        int alongSteps = SEGMENTS;
        int acrossSteps = WIDTH_SEGMENTS;
        Vec3[][] points = new Vec3[alongSteps + 1][acrossSteps + 1];
        float[][] shades = new float[alongSteps + 1][acrossSteps + 1];
        for (int i = 0; i <= alongSteps; i++) {
            float t = (float) i / alongSteps;
            float u = uStart + (uEnd - uStart) * t;
            float profile = profileBySlice[Math.min(profileBySlice.length - 1, Math.round(t * SEGMENTS))];
            // How wide and how tall she is at this slice, straight from her bones. The floors keep the cloth
            // visibly above the mattress even when every bone sits close to her centre line.
            float cover = Math.min(halfWidth, Math.max(0.24F, profile * 1.8F + 0.12F));
            float body = Math.min(0.30F, Math.max(0.16F, profile * 1.3F + 0.08F));
            // The feet end drapes down to the mattress instead of ending in a vertical wall; the head end
            // fades too, but it is mostly hidden by the pillow gap anyway.
            float fade = Math.min(1.0F, Math.min(t / 0.10F, (1.0F - t) / 0.26F));
            body *= Math.max(0.0F, fade);
            for (int j = 0; j <= acrossSteps; j++) {
                float s = -1.0F + 2.0F * j / acrossSteps;
                float distance = Math.abs(s) * halfWidth;
                float lift = 0.0F;
                if (distance < cover && cover > 1.0E-3F) {
                    double ratio = distance / cover;
                    lift = body * (float) Math.pow(0.5D + 0.5D * Math.cos(Math.PI * ratio), 0.75D);
                }
                points[i][j] = new Vec3(
                        headCenter.x + along.x * u + across.x * (s * halfWidth),
                        headCenter.y + LIE_FLAT + lift,
                        headCenter.z + along.z * u + across.z * (s * halfWidth));
                shades[i][j] = 0.72F + 0.28F * Math.min(1.0F, lift / 0.20F);
            }
        }
        for (int i = 0; i < alongSteps; i++) {
            for (int j = 0; j < acrossSteps; j++) {
                float v0 = (float) j / acrossSteps;
                float v1 = (float) (j + 1) / acrossSteps;
                quad(consumer, pose, points[i][j], points[i + 1][j], points[i + 1][j + 1], points[i][j + 1],
                        v0, v1, light, shades[i][j], shades[i][j + 1]);
            }
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(texture));
    }

    private static void quad(VertexConsumer consumer, PoseStack pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float v0, float v1, int light, float shadeA, float shadeB) {
        vertex(consumer, pose, a, 0.0F, v0, light, shadeA);
        vertex(consumer, pose, b, 1.0F, v0, light, shadeA);
        vertex(consumer, pose, c, 1.0F, v1, light, shadeB);
        vertex(consumer, pose, d, 0.0F, v1, light, shadeB);
    }

    private static void vertex(VertexConsumer consumer, PoseStack pose, Vec3 position, float u, float v,
                               int light, float shade) {
        PoseStack.Pose entry = pose.last();
        consumer.vertex(entry.pose(), (float) position.x, (float) position.y, (float) position.z)
                .color(shade, shade, shade, 1.0F)
                .uv(u, v)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(entry.normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static float profileAt(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        float factor = PROFILE[PROFILE.length - 1][1];
        for (int i = 0; i < PROFILE.length - 1; i++) {
            float low = PROFILE[i][0];
            float high = PROFILE[i + 1][0];
            if (clamped <= high) {
                float local = (clamped - low) / (high - low);
                factor = PROFILE[i][1] + (PROFILE[i + 1][1] - PROFILE[i][1]) * local;
                break;
            }
        }
        return factor;
    }

    private record Bed(BlockPos head, BlockPos foot, DyeColor color) {
    }
}
