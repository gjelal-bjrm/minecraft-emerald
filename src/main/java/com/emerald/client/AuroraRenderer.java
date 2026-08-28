package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.Weather;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * L'Aurore, dessinee comme une VRAIE aurore : de la geometrie dans le ciel.
 *
 * La premiere version la faisait en particules, comme les cinq autres meteos.
 * Elle en posait trois par tick dans un volume de quatre-vingts blocs de cote,
 * soit environ une particule pour mille metres cubes : invisible par
 * construction. Et meme en multipliant le nombre, des points epars ne font pas
 * un rideau -- il y manque la continuite, qui est justement ce qu'on reconnait
 * dans une aurore.
 *
 * On dessine donc des rubans continus, en melange additif comme les eclairs :
 * quelques bandes qui ondulent d'un horizon a l'autre, lumineuses en bas et
 * evanescentes en haut. C'est le seul effet du mode qui ne soit pas fait de
 * particules, et c'est voulu -- l'Aurore ne doit ressembler a aucune autre.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class AuroraRenderer {

    /** Nombre de rubans. Au-dela, le ciel se remplit et l'effet se banalise. */
    private static final int RIBBONS = 5;

    /** Segments par ruban : c'est ce qui rend l'ondulation lisse. */
    private static final int SEGMENTS = 48;

    /** Longueur d'un ruban, en blocs. Il doit sortir du champ des deux cotes. */
    private static final double LENGTH = 460.0;

    /** Hauteur du bord inferieur, au-dessus de la camera. */
    private static final double BASE = 62.0;

    /** 0 -> 1 : l'aurore s'allume et s'eteint, elle ne claque pas. */
    private static float intensity;

    private AuroraRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        boolean on = WeatherClient.current() == Weather.AURORE;
        intensity = Math.max(0.0F, Math.min(1.0F, intensity + (on ? 0.008F : -0.012F)));
        if (intensity <= 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = mc.level.getGameTime() + partial;
        draw(event.getPoseStack(), event.getCamera(), time);
    }

    private static void draw(PoseStack pose, Camera camera, double time) {
        Vec3 eye = camera.getPosition();
        MultiBufferSource.BufferSource buffer =
                Minecraft.getInstance().renderBuffers().bufferSource();
        // le melange additif des eclairs : la lumiere s'AJOUTE au ciel au lieu
        // de le masquer, ce qui est exactement ce que fait une aurore
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());

        pose.pushPose();
        // l'aurore est un objet du ciel : elle suit la camera a l'horizontale,
        // comme un phenomene assez lointain pour n'avoir pas de parallaxe, mais
        // son ondulation reste calee sur les coordonnees du monde
        pose.translate(0.0, -eye.y, 0.0);
        Matrix4f matrix = pose.last().pose();

        for (int r = 0; r < RIBBONS; r++) {
            drawRibbon(matrix, consumer, eye, time, r);
        }

        pose.popPose();
        buffer.endBatch(RenderType.lightning());
    }

    /**
     * Un ruban : une bande verticale qui serpente le long de l'axe X.
     *
     * Chaque segment est un quadrilatere dont le bas est vif et le haut
     * transparent. On l'emet dans les deux sens d'enroulement, faute de quoi il
     * disparait des qu'on le regarde par derriere.
     */
    private static void drawRibbon(Matrix4f matrix, VertexConsumer consumer,
                                   Vec3 eye, double time, int index) {
        // chaque ruban a sa distance, sa vitesse et sa phase : sans cela les
        // cinq bandes ondulent ensemble et l'oeil y voit un motif, pas un ciel
        double offset = (index - (RIBBONS - 1) / 2.0) * 46.0;
        double phase = index * 2.1;
        double speed = 0.00042 + index * 0.00009;
        double height = 34.0 + index * 5.0;
        double step = LENGTH / SEGMENTS;

        for (int i = 0; i < SEGMENTS; i++) {
            double x0 = eye.x - LENGTH / 2 + i * step;
            double x1 = x0 + step;

            double z0 = eye.z + offset + wave(x0, time, phase, speed);
            double z1 = eye.z + offset + wave(x1, time, phase, speed);
            double b0 = BASE + Math.sin(x0 * 0.008 + phase) * 7.0;
            double b1 = BASE + Math.sin(x1 * 0.008 + phase) * 7.0;

            // les extremites s'effacent : un ruban qui s'arrete net se voit
            float fade0 = edgeFade(i) * intensity;
            float fade1 = edgeFade(i + 1) * intensity;

            float[] c0 = hue(x0, time, phase);
            float[] c1 = hue(x1, time, phase);

            quad(matrix, consumer, x0, b0, z0, x1, b1, z1, height, c0, c1, fade0, fade1);
            quad(matrix, consumer, x1, b1, z1, x0, b0, z0, height, c1, c0, fade1, fade0);
        }
    }

    /** L'ondulation horizontale : deux sinus de periodes differentes. */
    private static double wave(double x, double time, double phase, double speed) {
        return Math.sin(x * 0.012 + time * speed * 20 + phase) * 16.0
                + Math.sin(x * 0.031 - time * speed * 11 + phase) * 6.0;
    }

    /** Le fondu aux deux bouts du ruban, en cloche. */
    private static float edgeFade(int i) {
        float t = i / (float) SEGMENTS;
        return (float) Math.sin(t * Math.PI);
    }

    /**
     * La couleur, prise sur la position et le temps.
     *
     * La teinte derive lentement le long du ruban : on retrouve la signature
     * prismatique du mode sans tomber dans le degrade arc-en-ciel complet, qui
     * ferait sapin de Noel. Une demi-roue suffit.
     */
    private static float[] hue(double x, double time, double phase) {
        float h = (float) (((x * 0.0016 + time * 0.00035 + phase * 0.1) % 1.0 + 1.0) % 1.0);
        int rgb = Color.HSBtoRGB(h * 0.5F + 0.25F, 0.72F, 1.0F);
        return new float[]{((rgb >> 16) & 0xFF) / 255F,
                ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F};
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer,
                             double xa, double ya, double za,
                             double xb, double yb, double zb,
                             double height, float[] ca, float[] cb,
                             float alphaA, float alphaB) {
        // le bas porte la lumiere, le haut s'evanouit : c'est ce contraste qui
        // fait lire une aurore plutot qu'un simple voile colore
        float lowA = 0.42F * alphaA;
        float lowB = 0.42F * alphaB;
        consumer.addVertex(matrix, (float) xa, (float) ya, (float) za)
                .setColor(ca[0], ca[1], ca[2], lowA);
        consumer.addVertex(matrix, (float) xb, (float) yb, (float) zb)
                .setColor(cb[0], cb[1], cb[2], lowB);
        consumer.addVertex(matrix, (float) xb, (float) (yb + height), (float) zb)
                .setColor(cb[0], cb[1], cb[2], 0.0F);
        consumer.addVertex(matrix, (float) xa, (float) (ya + height), (float) za)
                .setColor(ca[0], ca[1], ca[2], 0.0F);
    }
}
