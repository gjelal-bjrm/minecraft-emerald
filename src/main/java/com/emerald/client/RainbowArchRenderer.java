package com.emerald.client;

import com.emerald.game.GameState;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * L'Arc-en-ciel : un arc dans le ciel, au-dessus de l'arene, visible de
 * partout.
 *
 * Il se dessine comme le voile de ciel : sur la coupole autour de la camera,
 * a la distance du brouillard, apres la meteo -- donc sous un shader aussi.
 * Ce n'est pas un objet pose dans le monde : c'est une DIRECTION. De loin
 * l'arc est etroit et bas, il grandit a mesure qu'on approche, et passe
 * au-dessus de la tete quand on est dessous. C'est ainsi qu'on trouve
 * l'arene sans boussole : on marche vers l'arc.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class RainbowArchRenderer {

    private static final int BANDS = 7;
    private static final int STEPS = 28;
    /** Epaisseur d'une bande de couleur, en elevation. */
    private static final double BAND = Math.toRadians(2.0);
    private static final float ALPHA = 0.30F;

    private RainbowArchRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        long packed = GameHudClient.finalePos();
        int status = GameHudClient.statusOrdinal();
        // l'arc tient tant que la partie n'est pas jugee ; hors partie il ne
        // peut venir que de la commande d'essai, et c'est justement pour le voir
        if (packed == 0L || status == GameState.Status.WON.ordinal()
                || status == GameState.Status.LOST.ordinal()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BlockPos arena = BlockPos.of(packed);
        double dx = arena.getX() + 0.5 - mc.player.getX();
        double dz = arena.getZ() + 0.5 - mc.player.getZ();
        double distance = Math.hypot(dx, dz);
        double azimuth = Math.atan2(dz, dx);
        // l'ouverture de l'arc : un pont de 160 blocs de large vu a cette distance
        double span = Math.atan2(160.0, Math.max(40.0, distance));
        double height = Math.max(Math.toRadians(14.0), Math.min(Math.toRadians(62.0), span * 0.9));
        double radius = mc.gameRenderer.getRenderDistance() * 0.9;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        float shimmer = 0.85F + 0.15F * (float) Math.sin((mc.level.getGameTime() + partial) * 0.05);
        draw(event.getPoseStack(), azimuth, span, height, radius, ALPHA * shimmer);
    }

    private static void draw(PoseStack pose, double azimuth, double span, double height,
                             double radius, float alpha) {
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        for (int band = 0; band < BANDS; band++) {
            // rouge dehors, violet dedans
            int rgb = java.awt.Color.HSBtoRGB(band / (float) BANDS * 0.78F, 0.8F, 1.0F);
            float r = ((rgb >> 16) & 0xFF) / 255F;
            float g = ((rgb >> 8) & 0xFF) / 255F;
            float b = (rgb & 0xFF) / 255F;
            double outer = height - band * BAND;
            double inner = outer - BAND;
            for (int i = 0; i < STEPS; i++) {
                double t0 = Math.PI * i / STEPS;
                double t1 = Math.PI * (i + 1) / STEPS;
                // les pieds de l'arc s'effacent : il ne touche pas le sol
                float a0 = alpha * (float) Math.sin(t0);
                float a1 = alpha * (float) Math.sin(t1);
                vertex(matrix, vc, elevation(outer, t0), azimuth + span * Math.cos(t0), radius, r, g, b, a0);
                vertex(matrix, vc, elevation(outer, t1), azimuth + span * Math.cos(t1), radius, r, g, b, a1);
                vertex(matrix, vc, elevation(inner, t1), azimuth + span * Math.cos(t1), radius, r, g, b, a1);
                vertex(matrix, vc, elevation(inner, t0), azimuth + span * Math.cos(t0), radius, r, g, b, a0);
                // l'autre face, pour la voir aussi de derriere la coupole
                vertex(matrix, vc, elevation(inner, t0), azimuth + span * Math.cos(t0), radius, r, g, b, a0);
                vertex(matrix, vc, elevation(inner, t1), azimuth + span * Math.cos(t1), radius, r, g, b, a1);
                vertex(matrix, vc, elevation(outer, t1), azimuth + span * Math.cos(t1), radius, r, g, b, a1);
                vertex(matrix, vc, elevation(outer, t0), azimuth + span * Math.cos(t0), radius, r, g, b, a0);
            }
        }
        buffer.endBatch(RenderType.debugQuads());
    }

    /** L'arc culmine au milieu et retombe vers ses pieds, un peu sous l'horizon. */
    private static double elevation(double peak, double t) {
        return -Math.toRadians(4.0) + (peak + Math.toRadians(4.0)) * Math.sin(t);
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vc, double elevation, double azimuth,
                               double radius, float r, float g, float b, float a) {
        float x = (float) (Math.cos(elevation) * Math.cos(azimuth) * radius);
        float y = (float) (Math.sin(elevation) * radius);
        float z = (float) (Math.cos(elevation) * Math.sin(azimuth) * radius);
        vc.addVertex(matrix, x, y, z).setColor(r, g, b, a);
    }
}
