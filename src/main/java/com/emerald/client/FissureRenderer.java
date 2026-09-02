package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.FissureSyncPayload;
import com.emerald.weather.FissureShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Les fissures de la Pluie de Meteores, cote image.
 *
 * Le trou est REEL : c'est le serveur qui creuse (voir WeatherEffects). Ici on
 * ne dessine que ce qu'un trou ne montre pas de lui-meme :
 *
 *   - la FENTE, un trait sombre et fin qui court sur le sol et se propage
 *     depuis le centre : c'est l'annonce, une seconde et demie avant que le
 *     sol ne cede, et ce qui reste aux deux bouts la ou rien ne s'ouvre --
 *     une vraie fissure finit en cheveu ;
 *   - au fond des GRANDES seulement, une lueur sourde et rouge qui respire :
 *     la roche chauffe. Une craquelure de deux blocs n'a pas de magma.
 *
 * Le trait suit le relief point par point ; une fois le sol effondre, le
 * relief est le fond du trou, et le trait y descend de lui-meme. Le trait est
 * en debugQuads (translucide, deux faces) ; la lueur en lightning (additif),
 * ecrite dans les deux sens parce que ce rendu elimine les faces arriere et
 * qu'un fond de trou n'a pas de bon cote.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class FissureRenderer {

    private record Fissure(double x, double z, float dir, float length, float width, int life,
                           int maxLife) {
    }

    private static List<Fissure> fissures = List.of();
    private static long stamp;

    /** Ticks de fondu a la fin. */
    private static final float CLOSE_TICKS = 40.0F;
    /** A partir de cette largeur, le fond luit. */
    private static final float DEEP_WIDTH = 2.4F;

    private FissureRenderer() {
    }

    public static void accept(FissureSyncPayload payload) {
        List<Fissure> next = new ArrayList<>(payload.fissures().size());
        for (FissureSyncPayload.Entry e : payload.fissures()) {
            next.add(new Fissure(e.x(), e.z(), e.dir(), e.length(), e.width(), e.life(), e.maxLife()));
        }
        fissures = next;
        Minecraft mc = Minecraft.getInstance();
        stamp = mc.level == null ? 0 : mc.level.getGameTime();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || fissures.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // une synchro trop vieille veut dire que le serveur s'est tu
        if (mc.level.getGameTime() - stamp > 60) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double time = mc.level.getGameTime() + partial;
        // la fissure a vieilli depuis la derniere synchro : on la fait vieillir
        // ici aussi, sinon la propagation avancerait par a-coups
        double aged = mc.level.getGameTime() - stamp + partial;
        draw(mc.level, event.getPoseStack(), event.getCamera(), time, aged);
    }

    private static void draw(ClientLevel level, PoseStack pose, Camera camera, double time,
                             double aged) {
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();
        Vec3 cam = camera.getPosition();

        for (Fissure f : fissures) {
            double life = f.life() + aged;
            float close = (float) Math.max(0.0, Math.min(1.0, (f.maxLife() - life) / CLOSE_TICKS));
            if (close <= 0.0F) {
                continue;
            }
            boolean collapsed = life >= FissureShape.COLLAPSE_AT;
            float reach = collapsed ? 1.0F : (float) Math.min(1.0, life / FissureShape.OPEN_TICKS);
            boolean deep = f.width() >= DEEP_WIDTH;
            float breath = 0.70F + 0.30F * (float) Math.sin(time * 0.08 + f.x());

            for (FissureShape.Line line : FissureShape.lines(f.x(), f.z(), f.dir(), f.length(),
                    f.width(), 0)) {
                Vec3 side = new Vec3(-Math.sin(line.dir()), 0.0, Math.cos(line.dir()));
                double[][] spine = spine(level, line, cam);

                // la fente : un trait, pas une bande -- a peine plus large pour les grandes
                double hair = 0.05 + 0.03 * line.width();
                VertexConsumer core = buffer.getBuffer(RenderType.debugQuads());
                ribbon(matrix, core, spine, line, side, hair, reach,
                        0.05F, 0.03F, 0.02F, 0.85F * close, false);
                buffer.endBatch(RenderType.debugQuads());

                if (collapsed && deep) {
                    // le fond chauffe, au rythme de l'effondrement
                    float glowReach = (float) Math.min(1.0,
                            (life - FissureShape.COLLAPSE_AT) / FissureShape.CARVE_TICKS);
                    VertexConsumer glow = buffer.getBuffer(RenderType.lightning());
                    ribbon(matrix, glow, spine, line, side, line.width() * 0.22, glowReach,
                            0.85F, 0.22F, 0.04F, 0.45F * close * breath, true);
                    buffer.endBatch(RenderType.lightning());
                }
            }
        }
    }

    /** La ligne, DEJA relative a la camera, posee sur le sol point par point. */
    private static double[][] spine(ClientLevel level, FissureShape.Line line, Vec3 cam) {
        double[][] pts = new double[FissureShape.POINTS][3];
        for (int i = 0; i < FissureShape.POINTS; i++) {
            double[] p = line.point(i);
            pts[i][0] = p[0] - cam.x;
            pts[i][1] = ground(level, p[0], p[1]) + 0.03 - cam.y;
            pts[i][2] = p[1] - cam.z;
        }
        return pts;
    }

    private static double ground(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(x), (int) Math.floor(z));
    }

    /**
     * Un ruban a plat le long de la ligne. Il se PROPAGE : au-dela de reach,
     * rien n'est encore dessine, et la pointe qui avance s'effile.
     */
    private static void ribbon(Matrix4f matrix, VertexConsumer vc, double[][] spine,
                               FissureShape.Line line, Vec3 side, double width, float reach,
                               float r, float g, float b, float a, boolean bothSides) {
        for (int i = 0; i + 1 < spine.length; i++) {
            double d0 = line.progress(i);
            double d1 = line.progress(i + 1);
            if (Math.min(d0, d1) > reach) {
                continue;
            }
            double w0 = width * line.taper(i) * tip(reach, d0);
            double w1 = width * line.taper(i + 1) * tip(reach, d1);
            double[] p0 = spine[i];
            double[] p1 = spine[i + 1];
            quad(matrix, vc, p0, p1, side, w0, w1, r, g, b, a, false);
            if (bothSides) {
                quad(matrix, vc, p0, p1, side, w0, w1, r, g, b, a, true);
            }
        }
    }

    private static double tip(float reach, double d) {
        return Math.max(0.05, Math.min(1.0, (reach - d) / 0.12));
    }

    private static void quad(Matrix4f m, VertexConsumer vc, double[] p0, double[] p1, Vec3 side,
                             double w0, double w1, float r, float g, float b, float a, boolean flip) {
        if (!flip) {
            vertex(m, vc, p0[0] - side.x * w0, p0[1], p0[2] - side.z * w0, r, g, b, a);
            vertex(m, vc, p0[0] + side.x * w0, p0[1], p0[2] + side.z * w0, r, g, b, a);
            vertex(m, vc, p1[0] + side.x * w1, p1[1], p1[2] + side.z * w1, r, g, b, a);
            vertex(m, vc, p1[0] - side.x * w1, p1[1], p1[2] - side.z * w1, r, g, b, a);
        } else {
            vertex(m, vc, p1[0] - side.x * w1, p1[1], p1[2] - side.z * w1, r, g, b, a);
            vertex(m, vc, p1[0] + side.x * w1, p1[1], p1[2] + side.z * w1, r, g, b, a);
            vertex(m, vc, p0[0] + side.x * w0, p0[1], p0[2] + side.z * w0, r, g, b, a);
            vertex(m, vc, p0[0] - side.x * w0, p0[1], p0[2] - side.z * w0, r, g, b, a);
        }
    }

    private static void vertex(Matrix4f matrix, VertexConsumer vc, double x, double y, double z,
                               float r, float g, float b, float a) {
        vc.addVertex(matrix, (float) x, (float) y, (float) z).setColor(r, g, b, a);
    }
}
