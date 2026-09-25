package com.emerald.client;

import com.emerald.block.HavenWindowBlock;
import com.emerald.block.entity.HavenWindowBlockEntity;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'iris d'une vitre de Jak 3 (cahier §101) : la part de l'ellipse qui revient a cette vitre. La
 * fenetre entiere, moins son cadre, est un rectangle ; l'ouverture est une ellipse centree sur lui,
 * qui le contient tout entier grande ouverte (racine de 2 fois ses demi-axes) et se resserre jusqu'a rien.
 * L'acier sombre du sas couvre ce qui est hors de l'ellipse, PIXEL PAR PIXEL -- seize par bloc,
 * comme les textures --, et un lisere de metal clair borde le verre qui reste. Dessine DEVANT le
 * verre, des deux cotes, en retrait dans l'epaisseur du cadre : au milieu du verre, sa teinte et ses
 * reflets passaient dessus, et l'acier ressemblait a du verre depoli (photo).
 */
public class HavenWindowRenderer implements BlockEntityRenderer<HavenWindowBlockEntity> {

    /** L'acier de l'iris (seize lignes du haut) et son lisere (seize du bas) : tools/haven_window.py. */
    private static final ResourceLocation IRIS = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
            "textures/entity/haven_window_iris.png");
    /** Le cadre, deux pixels, que l'iris laisse voir autour de lui. */
    private static final float FRAME = 2.0F / 16.0F;
    private static final float PIXEL = 1.0F / 16.0F;
    private static final float WIDE_OPEN = (float) Math.sqrt(2.0);
    /** Les deux plans de l'iris : entre le verre (7,5 a 8,5 pixels) et le dessus du cadre (6,5 et 9,5). */
    private static final float[] DEPTHS = {7.0F / 16.0F, 9.0F / 16.0F};

    public HavenWindowRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HavenWindowBlockEntity pane, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (pane.getLevel() == null) {
            return;
        }
        float openness = pane.openness(pane.getLevel().getGameTime(), partialTick);
        if (openness >= 0.999F) {
            return;
        }
        BlockState state = pane.getBlockState();
        boolean alongX = state.getValue(HavenWindowBlock.AXIS) == Direction.Axis.X;
        int bu = HavenWindowBlockEntity.u(pane.getBlockPos(), state);
        int bv = pane.getBlockPos().getY();
        // le rectangle de la fenetre moins son cadre, en coordonnees de son plan
        float left = pane.u0() + FRAME;
        float right = pane.u1() + 1.0F - FRAME;
        float bottom = pane.v0() + FRAME;
        float top = pane.v1() + 1.0F - FRAME;
        float cu = (left + right) / 2.0F;
        float cv = (bottom + top) / 2.0F;
        float k = openness * WIDE_OPEN;
        float a = (right - left) / 2.0F * k;
        float b = (top - bottom) / 2.0F * k;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(IRIS));
        PoseStack.Pose last = pose.last();
        float nx = alongX ? 0.0F : 1.0F;
        float nz = alongX ? 1.0F : 0.0F;
        for (int row = 0; row < 16; row++) {
            float v = bv + (row + 0.5F) * PIXEL;
            if (v < bottom || v > top) {
                continue;                                 // le cadre du haut ou du bas de la fenetre
            }
            // une ligne : des suites de pixels d'acier, lisere a part
            int runStart = -1;
            boolean runRim = false;
            for (int col = 0; col <= 16; col++) {
                boolean iris = false;
                boolean rim = false;
                if (col < 16) {
                    float u = bu + (col + 0.5F) * PIXEL;
                    if (u > left && u < right && !glass(u, v, cu, cv, a, b)) {
                        iris = true;
                        rim = glass(u - PIXEL, v, cu, cv, a, b) || glass(u + PIXEL, v, cu, cv, a, b)
                                || glass(u, v - PIXEL, cu, cv, a, b) || glass(u, v + PIXEL, cu, cv, a, b);
                    }
                }
                if (runStart >= 0 && (!iris || rim != runRim)) {
                    for (float depth : DEPTHS) {
                        quad(vc, last, alongX, depth, runStart * PIXEL, col * PIXEL, row, runRim, nx, nz, light);
                    }
                    runStart = -1;
                }
                if (iris && runStart < 0) {
                    runStart = col;
                    runRim = rim;
                }
            }
        }
    }

    /** Ce point du plan est-il dans l'ouverture ? */
    private static boolean glass(float u, float v, float cu, float cv, float a, float b) {
        if (a <= 0.0F || b <= 0.0F) {
            return false;
        }
        float du = (u - cu) / a;
        float dv = (v - cv) / b;
        return du * du + dv * dv <= 1.0F;
    }

    /** Une suite de pixels d'une ligne, a cette profondeur, de s0 a s1 le long de la vitre. */
    private static void quad(VertexConsumer vc, PoseStack.Pose last, boolean alongX, float depth, float s0, float s1,
                             int row, boolean rim, float nx, float nz, int light) {
        float y0 = row * PIXEL;
        float y1 = y0 + PIXEL;
        float tv0 = (15 - row) / 32.0F + (rim ? 0.5F : 0.0F);
        float tv1 = tv0 + 1.0F / 32.0F;
        vertex(vc, last, alongX, depth, s0, y0, s0, tv1, nx, nz, light);
        vertex(vc, last, alongX, depth, s1, y0, s1, tv1, nx, nz, light);
        vertex(vc, last, alongX, depth, s1, y1, s1, tv0, nx, nz, light);
        vertex(vc, last, alongX, depth, s0, y1, s0, tv0, nx, nz, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose last, boolean alongX, float depth, float s, float y,
                               float tu, float tv, float nx, float nz, int light) {
        vc.addVertex(last, alongX ? s : depth, y, alongX ? depth : s)
                .setColor(255, 255, 255, 255)
                .setUv(tu, tv)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(last, nx, 0.0F, nz);
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
