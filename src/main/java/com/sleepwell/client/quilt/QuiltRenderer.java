package com.sleepwell.client.quilt;

import com.github.tartaricacid.touhoulittlemaid.block.BlockMaidBed;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityMaidBed;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
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
import net.minecraftforge.client.event.RenderLivingEvent;
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
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    /** Where the cloth starts, measured from the outer edge of the pillow end. */
    private static final float HEAD_GAP = 0.15F;
    private static final float FOOT_GAP = 0.02F;
    /** Half the mattress width: the bed is one block wide, so 0.40 keeps the cloth on the mattress. */
    private static final float HALF_WIDTH = 0.50F;
    /** How far the sides hang past the mattress edge, and how far down. */
    /** How far the cloth hangs straight down over the mattress edge. */
    private static final float HEM_DROP = 0.20F;
    /** One repeat of the quilt texture covers this many blocks, so the checks stay square. */
    private static final float TILE_BLOCKS = 1.0F;
    private static final float SKIRT_OUT = 0.18F;
    private static final float SKIRT_DROP = 0.46F;
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

    private static int STAGE_LOGS;
    private static int SLEEP_LOGS;
    private static final Map<DyeColor, ResourceLocation> TEXTURES = new EnumMap<>(DyeColor.class);

    static {
        for (DyeColor color : BlockMaidBed.AVAILABLE_COLOR) {
            TEXTURES.put(color, new ResourceLocation(SleepWellMod.MOD_ID,
                    "textures/quilt/" + color.getSerializedName() + ".png"));
        }
    }

    private QuiltRenderer() {
    }

    /** Fires while her model still carries her animated sleeping pose, so the bones are the right ones. */
    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRenderLiving(RenderLivingEvent.Post event) {
        if (event.getEntity() instanceof EntityMaid maid && maid.isSleeping()) {
            MaidSilhouette.capture(maid);
        }
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
        if (STAGE_LOGS++ < 3) {
            LOGGER.info("[sleep_well] quilt stage running, camera=" + camera);
        }
        List<EntityMaid> sleeping = level.getEntitiesOfClass(EntityMaid.class,
                new net.minecraft.world.phys.AABB(camera.x - 64.0D, camera.y - 64.0D, camera.z - 64.0D,
                        camera.x + 64.0D, camera.y + 64.0D, camera.z + 64.0D),
                EntityMaid::isSleeping);
        if (SLEEP_LOGS++ < 3) {
            LOGGER.info("[sleep_well] sleeping maids near camera=" + sleeping.size());
        }
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
        float bedBottom = (float) (headCenter.y - top);

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
        // Her feet often hang off the end of the bed, so the cloth is allowed past the mattress edge and
        // simply drapes off it.
        float uEnd = Math.min(bedEnd + 1.50F, uMax + FOOT_MARGIN + 0.45F);
        if (uEnd - uStart < 1.0F) {
            uStart = Math.max(HEAD_GAP, uStart - 0.25F);
            uEnd = Math.min(bedEnd + 1.45F, uEnd + 0.25F);
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
        // The bed's own model bounds: the only reliable answer to how wide the frame is and where its top is.
        net.minecraft.world.phys.AABB bedBox = BedGeometry.bounds(bed.head());
        float frameHalf = bedBox == null ? 0.5F
                : (float) Math.max(bedBox.getXsize(), bedBox.getZsize()) * 0.5F;
        float bedTop = bedBox == null ? (float) top : (float) Math.max(top, bedBox.maxY);
        // Wide enough that the model cannot poke out of the sides, and never narrower than the frame.
        float halfWidth = Math.max(Math.min(HALF_WIDTH, Math.max(0.42F, wMax + 0.12F)),
                Math.min(1.0F, frameHalf + 0.02F));
        // How many texture repeats the cloth is wide, so the checks stay square rather than stretched.
        float widthTiles = (2.0F * halfWidth) / TILE_BLOCKS;
        float skirtOut = Math.max(SKIRT_OUT, Math.max(0.0F, frameHalf - halfWidth) + 0.14F);
        // Kept small on purpose: a long curtain looks like a wall, not like cloth.
        float skirtDrop = (float) Math.min(0.22F, Math.max(0.12F, bedTop - bedBottom * 0.5F));
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
            // Thick quilt (up to half a block) with a gentle in-and-out following her bones.
            float cover = halfWidth;
            // Never sink below her own body: clothLift is how high her hitbox reaches above the mattress.
            float body = Math.min(0.50F, Math.max(0.30F, Math.max(profile * 1.6F + 0.18F, clothLift + 0.06F)));
            // The feet end drapes down to the mattress instead of ending in a vertical wall; the head end
            // fades too, but it is mostly hidden by the pillow gap anyway.
            // Fades measured in blocks from the two ends, not as a fraction, so adding cloth at the foot
            // does not change how the rest of the quilt looks.
            // Stay at full height until the very end, so her feet are covered rather than poking out of a
            // cloth that already sank to the mattress.
            float fade = Math.min(1.0F, Math.min(u / 0.35F, (uEnd + 0.12F - u) / 0.25F));
            body *= Math.max(0.0F, fade);
            for (int j = 0; j <= acrossSteps; j++) {
                float s = -1.0F + 2.0F * j / acrossSteps;
                float distance = Math.abs(s) * halfWidth;
                float lift = 0.0F;
                if (distance < cover && cover > 1.0E-3F) {
                    double ratio = distance / cover;
                    // Flat topped dome: it stays almost full height across most of the width and only
                    // drops near the very edge, so limbs lying off centre stay under the cloth.
                    lift = body * (float) Math.pow(0.5D + 0.5D * Math.cos(Math.PI * ratio), 0.30D);
                }
                points[i][j] = new Vec3(
                        headCenter.x + along.x * u + across.x * (s * halfWidth),
                        headCenter.y + LIE_FLAT + lift,
                        headCenter.z + along.z * u + across.z * (s * halfWidth));
                shades[i][j] = 0.72F + 0.28F * Math.min(1.0F, lift / 0.20F);
            }
        }
        for (int i = 0; i < alongSteps; i++) {
            float uTex0 = (uStart + (uEnd - uStart) * i / alongSteps) / TILE_BLOCKS;
            float uTex1 = (uStart + (uEnd - uStart) * (i + 1) / alongSteps) / TILE_BLOCKS;
            for (int j = 0; j < acrossSteps; j++) {
                // Texture repeats every block, so a check is a quarter of the quilt width.
                float v0 = (float) j / acrossSteps * widthTiles;
                float v1 = (float) (j + 1) / acrossSteps * widthTiles;
                quad(consumer, pose, points[i][j], points[i + 1][j], points[i + 1][j + 1], points[i][j + 1],
                        uTex0, uTex1, v0, v1, light, shades[i][j], shades[i][j + 1]);
            }
        }
        // The sides simply hang down: no rolled hem, no flap sticking out sideways.
        for (int side : new int[]{0, acrossSteps}) {
            for (int i = 0; i < alongSteps; i++) {
                Vec3 topA = points[i][side];
                Vec3 topB = points[i + 1][side];
                Vec3 lowA = topA.add(0.0D, -HEM_DROP, 0.0D);
                Vec3 lowB = topB.add(0.0D, -HEM_DROP, 0.0D);
                float uTex0 = (uStart + (uEnd - uStart) * i / alongSteps) / TILE_BLOCKS;
                float uTex1 = (uStart + (uEnd - uStart) * (i + 1) / alongSteps) / TILE_BLOCKS;
                float drop = HEM_DROP / TILE_BLOCKS;
                if (side == 0) {
                    quad(consumer, pose, topB, topA, lowA, lowB, uTex1, uTex0, 0.0F, drop, light, 0.60F, 0.60F);
                } else {
                    quad(consumer, pose, topA, topB, lowB, lowA, uTex0, uTex1, 0.0F, drop, light, 0.60F, 0.60F);
                }
            }
        }

        buffers.endBatch(RenderType.entityCutoutNoCull(texture));
    }

    private static void quad(VertexConsumer consumer, PoseStack pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             float u0, float u1, float v0, float v1, int light, float shadeA, float shadeB) {
        vertex(consumer, pose, a, u0, v0, light, shadeA);
        vertex(consumer, pose, b, u1, v0, light, shadeA);
        vertex(consumer, pose, c, u1, v1, light, shadeB);
        vertex(consumer, pose, d, u0, v1, light, shadeB);
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
