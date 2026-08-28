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
 * L'Aurore : des rideaux de lumiere qui prennent tout le ciel.
 *
 * Deux erreurs successives valent d'etre notees, parce qu'elles se ressemblent
 * de loin et ne se soignent pas pareil.
 *
 * La premiere version la faisait en particules -- trois par tick dans un volume
 * de quatre-vingts blocs de cote, une pour mille metres cubes. Invisible par
 * arithmetique. Et meme en multipliant le nombre, des points epars ne font pas
 * un rideau : il y manque la continuite, qui est ce qu'on reconnait dans une
 * aurore. D'ou la geometrie.
 *
 * La seconde etait un bug de REPERE. Dans RenderLevelStageEvent, la pile de
 * transformations est relative a la CAMERA : un sommet en (x, y, z) atterrit a
 * (camX + x, camY + y, camZ + z). En y passant des coordonnees du monde, on
 * dessinait a peu pres au double de la position du joueur -- des centaines de
 * blocs plus loin, et a l'altitude 62 plutot qu'au-dessus de lui. Il en restait
 * un lisere lointain, ce qui ressemblait a un probleme d'echelle alors que
 * c'etait un probleme de place.
 *
 * Tout est donc en coordonnees RELATIVES A LA CAMERA ici, et rien d'autre. Le
 * monde n'intervient que dans la phase des ondulations, pour que les rideaux
 * derivent au-dessus du paysage au lieu de coller au joueur.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class AuroraRenderer {

    /** Assez pour couvrir le ciel d'un bord a l'autre du champ de vision. */
    private static final int RIBBONS = 9;

    /** Segments par ruban : c'est ce qui rend l'ondulation lisse. */
    private static final int SEGMENTS = 44;

    /** Longueur d'un ruban. Il doit sortir du champ des deux cotes. */
    private static final double LENGTH = 420.0;

    /** Ecart entre deux rubans, perpendiculairement. */
    private static final double SPACING = 34.0;

    /** Hauteur du bord inferieur AU-DESSUS DE LA CAMERA. */
    private static final double BASE = 42.0;

    /** 0 -> 1 : l'aurore s'allume et s'eteint, elle ne claque pas. */
    private static float intensity;

    private AuroraRenderer() {
    }

    /** Pour l'ambiance sonore et lumineuse, qui suit le meme fondu. */
    public static float intensity() {
        return intensity;
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        boolean on = WeatherClient.current() == Weather.AURORE;
        intensity = Math.max(0.0F, Math.min(1.0F, intensity + (on ? 0.010F : -0.014F)));
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
        // AUCUNE translation : on est deja dans le repere de la camera, et
        // c'est precisement ce que la version precedente avait manque
        Matrix4f matrix = pose.last().pose();

        for (int r = 0; r < RIBBONS; r++) {
            drawRibbon(matrix, consumer, eye, time, r);
        }

        pose.popPose();
        buffer.endBatch(RenderType.lightning());
    }

    /**
     * Un rideau : une bande verticale qui serpente au-dessus de la tete.
     *
     * Les rubans sont legerement EVENTES plutot que paralleles. Parallelles,
     * ils se voyaient de face comme un ciel raye et, vus dans l'axe, comme une
     * seule ligne -- on tombait sur l'un ou l'autre selon l'orientation. Evente,
     * l'ensemble se lit de partout.
     */
    private static void drawRibbon(Matrix4f matrix, VertexConsumer consumer,
                                   Vec3 eye, double time, int index) {
        double centered = index - (RIBBONS - 1) / 2.0;
        // l'eventail : douze degres d'ecart d'un ruban au suivant
        double heading = Math.toRadians(centered * 12.0);
        double dirX = Math.cos(heading);
        double dirZ = Math.sin(heading);
        // la perpendiculaire, qui porte l'ecartement des rubans
        double perpX = -dirZ;
        double perpZ = dirX;

        double phase = index * 2.1;
        double speed = 0.00045 + index * 0.00007;
        double height = 44.0 + (index % 4) * 9.0;
        double step = LENGTH / SEGMENTS;
        double spread = centered * SPACING;

        for (int i = 0; i < SEGMENTS; i++) {
            double t0 = -LENGTH / 2 + i * step;
            double t1 = t0 + step;

            double[] a = point(eye, t0, spread, dirX, dirZ, perpX, perpZ, time, phase, speed);
            double[] b = point(eye, t1, spread, dirX, dirZ, perpX, perpZ, time, phase, speed);

            // les extremites s'effacent : un rideau qui s'arrete net se voit
            float fade0 = edgeFade(i) * intensity;
            float fade1 = edgeFade(i + 1) * intensity;

            float[] c0 = hue(eye.x + a[0], time, phase);
            float[] c1 = hue(eye.x + b[0], time, phase);

            quad(matrix, consumer, a, b, height, c0, c1, fade0, fade1);
            quad(matrix, consumer, b, a, height, c1, c0, fade1, fade0);
        }
    }

    /**
     * Un point du bord inferieur, en coordonnees relatives a la camera.
     *
     * La position du monde n'entre que dans la PHASE : c'est ce qui fait
     * deriver les rideaux au-dessus du paysage au lieu de les coller au joueur,
     * sans les eloigner de lui.
     */
    private static double[] point(Vec3 eye, double along, double spread,
                                  double dirX, double dirZ, double perpX, double perpZ,
                                  double time, double phase, double speed) {
        double worldAlong = eye.x * dirX + eye.z * dirZ + along;
        double swing = Math.sin(worldAlong * 0.012 + time * speed * 20 + phase) * 15.0
                + Math.sin(worldAlong * 0.031 - time * speed * 11 + phase) * 6.0;
        double lift = Math.sin(worldAlong * 0.008 + phase) * 8.0;
        double offset = spread + swing;
        return new double[]{
                along * dirX + offset * perpX,
                BASE + lift,
                along * dirZ + offset * perpZ,
        };
    }

    /** Le fondu aux deux bouts du rideau, en cloche. */
    private static float edgeFade(int i) {
        float t = i / (float) SEGMENTS;
        return (float) Math.sin(t * Math.PI);
    }

    /**
     * La couleur, prise sur la position et le temps.
     *
     * La teinte derive le long du rideau sur une DEMI-roue seulement, du vert
     * au violet en passant par le cyan -- les couleurs d'une vraie aurore, et
     * la signature prismatique du mode. La roue entiere ferait sapin de Noel.
     */
    private static float[] hue(double along, double time, double phase) {
        float h = (float) (((along * 0.0016 + time * 0.00035 + phase * 0.1) % 1.0 + 1.0) % 1.0);
        int rgb = Color.HSBtoRGB(h * 0.5F + 0.25F, 0.70F, 1.0F);
        return new float[]{((rgb >> 16) & 0xFF) / 255F,
                ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F};
    }

    private static void quad(Matrix4f matrix, VertexConsumer consumer,
                             double[] a, double[] b, double height,
                             float[] ca, float[] cb, float alphaA, float alphaB) {
        // le bas porte la lumiere, le haut s'evanouit : c'est ce contraste qui
        // fait lire une aurore plutot qu'un simple voile colore
        float lowA = 0.62F * alphaA;
        float lowB = 0.62F * alphaB;
        consumer.addVertex(matrix, (float) a[0], (float) a[1], (float) a[2])
                .setColor(ca[0], ca[1], ca[2], lowA);
        consumer.addVertex(matrix, (float) b[0], (float) b[1], (float) b[2])
                .setColor(cb[0], cb[1], cb[2], lowB);
        consumer.addVertex(matrix, (float) b[0], (float) (b[1] + height), (float) b[2])
                .setColor(cb[0], cb[1], cb[2], 0.0F);
        consumer.addVertex(matrix, (float) a[0], (float) (a[1] + height), (float) a[2])
                .setColor(ca[0], ca[1], ca[2], 0.0F);
    }
}
