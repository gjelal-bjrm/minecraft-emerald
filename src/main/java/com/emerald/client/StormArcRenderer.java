package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.StormStrikePayload;
import com.emerald.particles.ModParticles;
import com.emerald.weather.Weather;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Les arcs de l'Orage : l'electricite qui COURT AU SOL.
 *
 * C'est l'identite de l'Orage, par opposition a la Nuit : la Nuit fait tomber
 * des eclairs colores du ciel ; l'Orage fait ramper la charge au sol, sur le
 * metal, autour des corps. Ici l'electricite saute d'un point du sol a un
 * autre, a quelques blocs du joueur, en un trait brise qui ne dure qu'un
 * quart de seconde. Et elle sait ou la foudre va frapper : les arcs
 * CONVERGENT vers le point, de plus en plus vite, jusqu'a ce que la decharge
 * monte du sol -- c'est l'annonce, sans cercle ni carillon. A l'impact, ils
 * eclatent en etoile ; et celui qui porte la Surcharge en a qui lui courent
 * autour du corps.
 *
 * Les arcs d'ambiance sont TOUT CLIENT : ils n'ont aucun effet de jeu, et les
 * synchroniser ne servirait qu'a les retarder. Seuls les points de frappe et
 * les porteurs de Surcharge viennent du serveur (StormStrikePayload), parce
 * que ce sont des faits de jeu que tous doivent voir au meme endroit.
 *
 * Le trait est un RUBAN face camera le long d'une ligne brisee, en lightning,
 * additif -- c'est de la lumiere, et elle blanchit ce qu'elle traverse. Ce
 * rendu elimine les faces arriere, et un arc n'a pas de bon cote : chaque
 * ruban est ecrit dans les deux sens.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class StormArcRenderer {

    private record Arc(double[][] points, int life, int maxLife, double seed) {
    }

    /** Une frappe annoncee : ou, et dans combien de ticks. */
    private static final class Warn {
        final double x;
        final double y;
        final double z;
        int ticks;

        Warn(double x, double y, double z, int ticks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticks = ticks;
        }
    }

    private static final List<Arc> arcs = new ArrayList<>();
    private static final List<Warn> warns = new ArrayList<>();

    /** Nombre de segments d'un arc. */
    private static final int SEGMENTS = 9;
    /** Distance au joueur ou un arc d'ambiance peut naitre, en blocs. */
    private static final double NEAR = 5.0;
    private static final double FAR = 14.0;
    /** Longueur d'un arc d'ambiance, en blocs. */
    private static final double MIN_LEN = 2.0;
    private static final double MAX_LEN = 5.5;

    private StormArcRenderer() {
    }

    // ------------------------------------------------------------ du serveur

    public static void accept(StormStrikePayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        switch (payload.kind()) {
            case StormStrikePayload.WARN ->
                    warns.add(new Warn(payload.x(), payload.y(), payload.z(), payload.ticks()));
            case StormStrikePayload.IMPACT -> burst(level, random, payload.x(), payload.y(), payload.z());
            case StormStrikePayload.CRACKLE -> crackle(level, random, payload.x(), payload.y(), payload.z());
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ naissance

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) {
            arcs.clear();
            warns.clear();
            return;
        }
        // vieillissement
        List<Arc> aged = new ArrayList<>(arcs.size());
        for (Arc arc : arcs) {
            if (arc.life() + 1 < arc.maxLife()) {
                aged.add(new Arc(arc.points(), arc.life() + 1, arc.maxLife(), arc.seed()));
            }
        }
        arcs.clear();
        arcs.addAll(aged);

        if (WeatherClient.current() != Weather.ORAGE) {
            warns.clear();
            return;
        }
        RandomSource random = level.random;
        tickWarns(level, random);
        // un arc d'ambiance toutes les une a trois secondes environ
        if (random.nextInt(40) == 0) {
            spawn(level, player, random);
        }
    }

    /**
     * Les frappes annoncees : les arcs convergent vers le point -- un toutes
     * les sept ticks d'abord, puis a chaque tick dans la derniere seconde --
     * et le point lui-meme gresille de plus en plus. On lit OU ca va tomber
     * et QUAND, sans qu'on ait eu a dessiner un cercle.
     */
    private static void tickWarns(ClientLevel level, RandomSource random) {
        Iterator<Warn> it = warns.iterator();
        while (it.hasNext()) {
            Warn w = it.next();
            if (--w.ticks <= 0) {
                it.remove();
                continue;
            }
            int every = Math.max(1, w.ticks / 7);
            if (random.nextInt(every) == 0) {
                towards(level, random, w.x, w.y, w.z);
            }
            int sparks = w.ticks < 10 ? 3 : (w.ticks < 30 ? 1 : 0);
            for (int i = 0; i < sparks; i++) {
                level.addParticle(ModParticles.STATIC_SPARK.get(),
                        w.x + (random.nextDouble() - 0.5) * 0.8, w.y + random.nextDouble() * 0.6,
                        w.z + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
            }
        }
    }

    /** L'arc d'ambiance : entre deux points du sol, a quelques blocs du joueur. */
    private static void spawn(ClientLevel level, LocalPlayer player, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = NEAR + random.nextDouble() * (FAR - NEAR);
        double ax = player.getX() + Math.cos(angle) * dist;
        double az = player.getZ() + Math.sin(angle) * dist;
        double len = MIN_LEN + random.nextDouble() * (MAX_LEN - MIN_LEN);
        double dir = random.nextDouble() * Math.PI * 2.0;
        double bx = ax + Math.cos(dir) * len;
        double bz = az + Math.sin(dir) * len;
        arc(level, random, ax, ground(level, ax, az) + 0.08, az,
                bx, ground(level, bx, bz) + 0.08, bz, 0.35F);
    }

    /** Un arc qui converge vers le point de frappe. */
    private static void towards(ClientLevel level, RandomSource random,
                                double tx, double ty, double tz) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = 2.5 + random.nextDouble() * 3.5;
        double ax = tx + Math.cos(angle) * dist;
        double az = tz + Math.sin(angle) * dist;
        arc(level, random, ax, ground(level, ax, az) + 0.08, az, tx, ty + 0.08, tz, 0.3F);
    }

    /** L'impact : des arcs qui eclatent en etoile depuis le point. */
    private static void burst(ClientLevel level, RandomSource random, double x, double y, double z) {
        int n = 4 + random.nextInt(3);
        for (int i = 0; i < n; i++) {
            double angle = i / (double) n * Math.PI * 2.0 + random.nextDouble() * 0.6;
            double dist = 2.0 + random.nextDouble() * 3.0;
            double bx = x + Math.cos(angle) * dist;
            double bz = z + Math.sin(angle) * dist;
            arc(level, random, x, y + 0.1, z, bx, ground(level, bx, bz) + 0.08, bz, 0.0F);
        }
    }

    /** La Surcharge : un ou deux petits arcs qui courent autour du corps. */
    private static void crackle(ClientLevel level, RandomSource random, double x, double y, double z) {
        int n = 1 + random.nextInt(2);
        for (int i = 0; i < n; i++) {
            double a1 = random.nextDouble() * Math.PI * 2.0;
            double a2 = a1 + 1.2 + random.nextDouble() * 2.0;
            double r1 = 0.45 + random.nextDouble() * 0.35;
            double r2 = 0.45 + random.nextDouble() * 0.35;
            arc(level, random,
                    x + Math.cos(a1) * r1, y + 0.2 + random.nextDouble() * 1.4, z + Math.sin(a1) * r1,
                    x + Math.cos(a2) * r2, y + 0.2 + random.nextDouble() * 1.4, z + Math.sin(a2) * r2,
                    0.12F);
        }
    }

    /**
     * Le trait lui-meme : une ligne brisee entre A et B, decalee au hasard
     * perpendiculairement a sa course, des etincelles aux deux bouts, et un
     * claquement -- proportionne a ce que l'arc represente.
     */
    private static void arc(ClientLevel level, RandomSource random,
                            double ax, double ay, double az, double bx, double by, double bz,
                            float volume) {
        double dir = Math.atan2(bz - az, bx - ax);
        double len = Math.hypot(bx - ax, bz - az);
        double amp = Math.min(0.9, 0.15 + len * 0.15);
        double px = -Math.sin(dir);
        double pz = Math.cos(dir);
        double[][] pts = new double[SEGMENTS + 1][3];
        for (int i = 0; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            double wobble = (i == 0 || i == SEGMENTS) ? 0.0 : (random.nextDouble() - 0.5) * amp;
            pts[i][0] = ax + (bx - ax) * t + px * wobble;
            pts[i][1] = ay + (by - ay) * t + Math.abs(wobble) * 0.35;
            pts[i][2] = az + (bz - az) * t + pz * wobble;
        }
        arcs.add(new Arc(pts, 0, 4 + random.nextInt(4), random.nextDouble()));

        for (int i = 0; i < 5; i++) {
            level.addParticle(ModParticles.STATIC_SPARK.get(),
                    ax + (random.nextDouble() - 0.5) * 0.4, ay + random.nextDouble() * 0.3,
                    az + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
            level.addParticle(ModParticles.STATIC_SPARK.get(),
                    bx + (random.nextDouble() - 0.5) * 0.4, by + random.nextDouble() * 0.3,
                    bz + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
        }
        if (volume > 0.0F) {
            level.playLocalSound(ax, ay, az, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
                    volume, 1.6F + random.nextFloat() * 0.5F, false);
        }
    }

    private static double ground(ClientLevel level, double x, double z) {
        BlockPos pos = new BlockPos((int) Math.floor(x), 0, (int) Math.floor(z));
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    // -------------------------------------------------------------- rendu

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || arcs.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        draw(event.getPoseStack(), event.getCamera(), partial);
    }

    private static void draw(PoseStack pose, Camera camera, float partial) {
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();
        Vec3 cam = camera.getPosition();
        Vec3 look = new Vec3(camera.getLookVector());
        VertexConsumer vc = buffer.getBuffer(RenderType.lightning());

        for (Arc arc : arcs) {
            float t = (arc.life() + partial) / arc.maxLife();
            // un arc nait plein et meurt en un souffle ; il vacille entre-temps
            float flicker = 0.7F + 0.3F * (float) Math.sin(t * 40.0 + arc.seed() * 10.0);
            float alpha = (1.0F - t) * flicker;
            float width = (0.06F + 0.05F * (1.0F - t)) * flicker;

            for (int i = 0; i + 1 < arc.points().length; i++) {
                double[] p0 = arc.points()[i];
                double[] p1 = arc.points()[i + 1];
                Vec3 a = new Vec3(p0[0] - cam.x, p0[1] - cam.y, p0[2] - cam.z);
                Vec3 b = new Vec3(p1[0] - cam.x, p1[1] - cam.y, p1[2] - cam.z);
                // le ruban fait face a la camera : sa largeur est perpendiculaire
                // a la fois au segment et au regard
                Vec3 side = b.subtract(a).cross(look).normalize().scale(width);
                // coeur blanc, et un voile violet plus large autour
                ribbon(matrix, vc, a, b, side.scale(2.2), 0.62F, 0.40F, 1.0F, alpha * 0.35F);
                ribbon(matrix, vc, a, b, side, 1.0F, 0.96F, 1.0F, alpha);
            }
        }
        buffer.endBatch(RenderType.lightning());
    }

    private static void ribbon(Matrix4f m, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 side,
                               float r, float g, float bl, float alpha) {
        vertex(m, vc, a.subtract(side), r, g, bl, alpha);
        vertex(m, vc, a.add(side), r, g, bl, alpha);
        vertex(m, vc, b.add(side), r, g, bl, alpha);
        vertex(m, vc, b.subtract(side), r, g, bl, alpha);
        // et dans l'autre sens : le rendu lightning elimine les faces arriere,
        // or un arc n'a pas de bon cote -- sans cela, la moitie des arcs,
        // ceux qui couraient vers la droite de l'ecran, etaient invisibles
        vertex(m, vc, b.subtract(side), r, g, bl, alpha);
        vertex(m, vc, b.add(side), r, g, bl, alpha);
        vertex(m, vc, a.add(side), r, g, bl, alpha);
        vertex(m, vc, a.subtract(side), r, g, bl, alpha);
    }

    private static void vertex(Matrix4f m, VertexConsumer vc, Vec3 p, float r, float g, float b,
                               float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }
}
