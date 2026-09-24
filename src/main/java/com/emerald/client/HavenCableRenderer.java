package com.emerald.client;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenCables;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * LES CABLES DU PORT, DESSINES EN CHAINES CONTINUES (cahier §94).
 *
 * Posees en blocs, les chaines suivaient le cable en escalier, un bout horizontal decale d'un
 * bloc du suivant a chaque marche : « on dirait qu'elles flottent dans le vide » (le joueur).
 * Ici chaque cable entier (HavenCables.lines, recousu d'apres le decor de Jak 3) est dessine
 * comme le bloc de chaine de Minecraft l'est -- deux plans croises de trois pixels, a la
 * texture de la chaine --, mais LE LONG DE SA VRAIE LIGNE, en biais s'il le faut : les maillons
 * se suivent d'une tour a l'autre sans une marche. La texture se repete a chaque bloc de long.
 *
 * Un cable ne se dessine que si le bloc temoin d'un de ses bouts (l'attache dans la tour) est
 * la chez le client : la ville est posee, et son troncon est charge. Au-dela de {@link #SEEN}
 * blocs, une chaine de trois pixels ne fait plus un pixel : on ne la dessine pas.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class HavenCableRenderer {

    private static final ResourceLocation CHAIN = ResourceLocation.withDefaultNamespace("block/chain");
    /** La demi-largeur d'un plan : un pixel et demi, comme le bloc de chaine. */
    private static final float HALF = 1.5F / 16.0F;
    /** La largeur d'un plan dans la texture : trois pixels sur seize. */
    private static final float STRIP = 3.0F / 16.0F;
    private static final double SEEN = 256.0;
    private static final double COS45 = Math.sqrt(0.5);

    private HavenCableRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !Haven.is(level)) {
            return;
        }
        List<HavenCables.Line> lines = HavenCables.lines();
        if (lines.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        BlockPos origin = Haven.ORIGIN;
        TextureAtlasSprite sprite = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(CHAIN);
        RenderType type = RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(type);
        PoseStack.Pose pose = event.getPoseStack().last();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (HavenCables.Line line : lines) {
            if (!anchored(level, origin, line, probe)) {
                continue;
            }
            List<Vec3> points = line.points();
            for (int i = 0; i + 1 < points.size(); i++) {
                Vec3 a = points.get(i).add(origin.getX(), origin.getY(), origin.getZ());
                Vec3 b = points.get(i + 1).add(origin.getX(), origin.getY(), origin.getZ());
                segment(level, vc, pose.pose(), cam, a, b, sprite, probe);
            }
        }
        buffers.endBatch(type);
    }

    /** Le bloc temoin d'un des bouts est-il la ? (la ville est posee, et ce troncon est charge chez nous) */
    private static boolean anchored(ClientLevel level, BlockPos origin, HavenCables.Line line,
                                    BlockPos.MutableBlockPos probe) {
        for (BlockPos witness : new BlockPos[]{line.startWitness(), line.endWitness()}) {
            if (witness != null && !level.getBlockState(probe.setWithOffset(origin, witness)).isAir()) {
                return true;
            }
        }
        return false;
    }

    /** Un troncon droit de la chaine, de a a b (monde), en morceaux d'un bloc au plus pour y repeter la texture. */
    private static void segment(ClientLevel level, VertexConsumer vc, Matrix4f matrix, Vec3 cam, Vec3 a, Vec3 b,
                                TextureAtlasSprite sprite, BlockPos.MutableBlockPos probe) {
        Vec3 d = b.subtract(a);
        double length = d.length();
        if (length < 1.0e-4) {
            return;
        }
        Vec3 axis = d.scale(1.0 / length);
        // deux plans qui contiennent l'axe, croises a angle droit et tournes de 45 degres, comme
        // ceux du bloc de chaine
        Vec3 ref = Math.abs(axis.y) < 0.95 ? new Vec3(0.0, 1.0, 0.0) : new Vec3(1.0, 0.0, 0.0);
        Vec3 side = axis.cross(ref).normalize();
        Vec3 up = side.cross(axis).normalize();
        Vec3 w1 = side.add(up).scale(COS45 * HALF);
        Vec3 w2 = side.subtract(up).scale(COS45 * HALF);
        int pieces = (int) Math.ceil(length);
        for (int k = 0; k < pieces; k++) {
            double t0 = k;
            double t1 = Math.min(length, k + 1.0);
            Vec3 p0 = a.add(axis.scale(t0));
            Vec3 p1 = a.add(axis.scale(t1));
            Vec3 mid = p0.add(p1).scale(0.5);
            if (mid.distanceToSqr(cam) > SEEN * SEEN) {
                continue;
            }
            int light = LevelRenderer.getLightColor(level, probe.set(mid.x, mid.y, mid.z));
            float v1 = (float) (t1 - t0);
            plane(vc, matrix, cam, p0, p1, w1, sprite, 0.0F, v1, light);
            plane(vc, matrix, cam, p0, p1, w2, sprite, STRIP, v1, light);
        }
    }

    /** Un plan de la chaine : les maillons de la bande [u0, u0 + 3 px] de la texture, sur v1 bloc de long. */
    private static void plane(VertexConsumer vc, Matrix4f matrix, Vec3 cam, Vec3 p0, Vec3 p1, Vec3 w,
                              TextureAtlasSprite sprite, float u0, float v1, int light) {
        float uA = sprite.getU(u0);
        float uB = sprite.getU(u0 + STRIP);
        float vA = sprite.getV(0.0F);
        float vB = sprite.getV(v1);
        vertex(vc, matrix, cam, p0.subtract(w), uA, vA, light);
        vertex(vc, matrix, cam, p0.add(w), uB, vA, light);
        vertex(vc, matrix, cam, p1.add(w), uB, vB, light);
        vertex(vc, matrix, cam, p1.subtract(w), uA, vB, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, Vec3 cam, Vec3 p, float u, float v, int light) {
        // la normale vers le haut, comme le bloc de chaine (« shade » a faux) : un eclairage egal
        vc.addVertex(matrix, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z))
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 1.0F, 0.0F);
    }
}
