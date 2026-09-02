package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.RiftSyncPayload;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Les failles de la Dechirure : de la GEOMETRIE, pas des particules.
 *
 * Une faille faite de particules de portail disait « j'ai devine qu'il y a
 * quelque chose ici » ; elle ne ressemblait a rien de precis. Une faille est
 * une DECHIRURE dans l'image : une fente noire, dechiquetee, bordee d'une
 * lueur qui palpite. Cela se dessine, cela ne se saupoudre pas.
 *
 * Deux couches, avec deux types de rendu differents, et il le faut :
 *
 *   - le COEUR est noir et translucide. Il passe par debugQuads, un rendu
 *     position-couleur en fondu classique : un noir additif n'ajouterait rien
 *     et serait invisible ;
 *   - le BORD est violet-rose et lumineux. Il passe par lightning, additif,
 *     exactement comme les rideaux de l'Aurore : c'est de la lumiere.
 *
 * Le trace est dechiquete par un bruit SEEDE sur la position : chaque faille a
 * sa propre forme, stable d'une image a l'autre, qui frissonne lentement avec
 * le temps. Une forme tiree au hasard a chaque image tremblerait comme un
 * defaut d'affichage.
 *
 * Tout est en coordonnees RELATIVES A LA CAMERA -- la lecon de l'Aurore.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class RiftRenderer {

    /** Une faille telle que le serveur la decrit. */
    public record Rift(double x, double y, double z, int life, int maxLife) {
    }

    private static List<Rift> rifts = List.of();
    private static long stamp;

    /** Points le long de la fente, de bas en haut. */
    private static final int POINTS = 14;
    /** Demi-hauteur de la fente, en blocs. */
    private static final double HALF_HEIGHT = 2.4;
    /** Largeur du coeur, et de la lueur qui le borde. */
    private static final double CORE_WIDTH = 0.34;
    private static final double GLOW_WIDTH = 0.62;

    private RiftRenderer() {
    }

    public static void accept(RiftSyncPayload payload) {
        List<Rift> next = new ArrayList<>(payload.rifts().size());
        for (RiftSyncPayload.Entry e : payload.rifts()) {
            next.add(new Rift(e.x(), e.y(), e.z(), e.life(), e.maxLife()));
        }
        rifts = next;
        Minecraft mc = Minecraft.getInstance();
        stamp = mc.level == null ? 0 : mc.level.getGameTime();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || rifts.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // une synchro trop vieille veut dire que la meteo a cesse : on n'affiche
        // pas des failles fantomes pendant que le serveur se tait
        if (mc.level.getGameTime() - stamp > 60) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double time = mc.level.getGameTime() + partial;
        draw(event.getPoseStack(), event.getCamera(), time);
    }

    private static void draw(PoseStack pose, Camera camera, double time) {
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();
        Vec3 cam = camera.getPosition();
        // la fente fait face a la camera sur son axe horizontal : on la voit
        // toujours de face, comme une entaille dans le regard lui-meme
        Vec3 look = new Vec3(camera.getLookVector());
        Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize();

        for (Rift rift : rifts) {
            float in = Math.min(1.0F, rift.life() / 20.0F);
            float out = Math.min(1.0F, Math.max(0.0F, (rift.maxLife() - rift.life()) / 20.0F));
            float fade = in * out;
            double[][] spine = spine(rift, cam, side, time);
            // la lueur d'abord, puis le noir par-dessus : il ne reste qu'un lisere
            VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
            ribbon(matrix, glow, spine, side, GLOW_WIDTH, 0.92F, 0.42F, 1.0F, 0.55F * fade, time);
            buffer.endBatch(RenderType.lightning());
            VertexConsumer core = buffer.getBuffer(RenderType.debugQuads());
            ribbon(matrix, core, spine, side, CORE_WIDTH, 0.02F, 0.0F, 0.05F, 0.92F * fade, time);
            buffer.endBatch(RenderType.debugQuads());
        }
    }

    /**
     * La colonne vertebrale de la fente, DEJA relative a la camera : des points
     * empiles, decales lateralement par un bruit seede sur la position et
     * lentement animes par le temps.
     */
    private static double[][] spine(Rift rift, Vec3 cam, Vec3 side, double time) {
        double[][] pts = new double[POINTS][3];
        long seed = Double.doubleToLongBits(rift.x()) * 31 + Double.doubleToLongBits(rift.z()) * 17;
        for (int i = 0; i < POINTS; i++) {
            double t = i / (double) (POINTS - 1);
            double n = hash(seed, i);
            double jitter = (n - 0.5) * 0.9 + Math.sin(time * 0.05 + i * 1.7 + n * 6.0) * 0.12;
            pts[i][0] = rift.x() - cam.x + side.x * jitter;
            pts[i][1] = rift.y() + (t * 2.0 - 1.0) * HALF_HEIGHT - cam.y;
            pts[i][2] = rift.z() - cam.z + side.z * jitter;
        }
        return pts;
    }

    private static double hash(long seed, int i) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /**
     * Un ruban vertical le long de la colonne, de largeur variable : la fente
     * est plus etroite a ses deux bouts, comme une dechirure qui s'arrete.
     */
    private static void ribbon(Matrix4f matrix, VertexConsumer vc, double[][] spine, Vec3 side,
                               double width, float r, float g, float b, float a, double time) {
        for (int i = 0; i + 1 < spine.length; i++) {
            float t0 = i / (float) (spine.length - 1);
            float t1 = (i + 1) / (float) (spine.length - 1);
            double w0 = width * taper(t0) * (1.0 + 0.10 * Math.sin(time * 0.20 + i));
            double w1 = width * taper(t1) * (1.0 + 0.10 * Math.sin(time * 0.20 + i + 1));
            double[] p0 = spine[i];
            double[] p1 = spine[i + 1];
            vertex(matrix, vc, p0[0] - side.x * w0, p0[1], p0[2] - side.z * w0, r, g, b, a);
            vertex(matrix, vc, p0[0] + side.x * w0, p0[1], p0[2] + side.z * w0, r, g, b, a);
            vertex(matrix, vc, p1[0] + side.x * w1, p1[1], p1[2] + side.z * w1, r, g, b, a);
            vertex(matrix, vc, p1[0] - side.x * w1, p1[1], p1[2] - side.z * w1, r, g, b, a);
        }
    }

    private static float taper(float t) {
        return (float) Math.sin(t * Math.PI) * 0.7F + 0.3F;
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vc, double x, double y, double z,
                               float r, float g, float b, float a) {
        vc.addVertex(matrix, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }
}
