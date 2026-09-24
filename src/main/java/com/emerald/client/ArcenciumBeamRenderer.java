package com.emerald.client;

import com.emerald.game.GameState;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * LE FAISCEAU D'ARCENCIUM : la colonne de lumiere qui marque l'arene du boss (cahier §93).
 *
 * L'arc-en-ciel d'abord (« pas beau, ni realiste »), puis le Vortex, une aurore en spirale :
 * « il n'y a rien d'impressionnant, de jour on ne le voit meme pas, et de nuit ce n'est
 * vraiment pas incroyable [...] on ne sait pas qu'il faut aller la-bas tellement c'est peu
 * visible » (le joueur, 24 sept.). Une lumiere qui S'AJOUTE au ciel ne se voit pas sur un ciel
 * de jour deja clair. Choix du joueur parmi quatre nouveaux concepts : LE FAISCEAU.
 *
 * Tout y est OPAQUE ou presque, en couleurs pleines, en fondu alpha et non en additif -- c'est
 * ce qui le fait voir de jour :
 * <ul>
 * <li>LE COEUR : une colonne blanche de dix blocs de large dans un halo de dix-huit, du sol de
 *     l'arene jusqu'a neuf cents blocs, qui s'efface dans ses deux cents
 *     derniers ; des bandes plus claires la remontent a vingt-quatre blocs par seconde ;</li>
 * <li>LA SPIRALE : sept rubans des couleurs de l'Arcencium, de quatre blocs de haut, enroules
 *     autour a treize blocs de l'axe, un tour tous les quatre-vingt-seize blocs, qui tournent --
 *     les couleurs montent le long de la colonne ;</li>
 * <li>LA COURONNE : a la hauteur des nuages (230 blocs au-dessus du sol de l'arene), les memes
 *     couleurs en anneaux fondus, du violet dedans au rouge dehors, de 20 a 170 blocs de l'axe,
 *     et toutes les cinq secondes une onde blanche qui s'en echappe jusqu'a 420 blocs.</li>
 * </ul>
 *
 * PRES DE L'ARENE, LE FAISCEAU S'EFFACE AU SOL : il marque le chemin, il ne doit pas cacher le
 * combat. Au-dela de 250 blocs il est entier ; en dessous il palit jusqu'a moitie, et son pied
 * s'efface sur soixante-dix blocs de haut.
 *
 * DE LOIN, IL GARDE SA LARGEUR A L'ECRAN. A seize cents blocs, dix-huit blocs de large ne
 * faisaient plus qu'un trait sur l'horizon (premieres photos) : la colonne et sa spirale
 * s'elargissent avec la distance, pour ne jamais paraitre plus etroites qu'un demi-degre. De
 * pres, rien ne change.
 *
 * UN QUAD, UNE COULEUR. Sous un shader, un triangle ne fond pas les couleurs de ses sommets : il
 * prend celle d'un seul. Un quad plus clair d'un cote que de l'autre se coupait ainsi en un
 * triangle clair et un triangle sombre -- les dents de scie du Vortex, et celles de la couronne
 * aux deuxiemes photos. Chaque quad est donc d'une couleur et d'une opacite uniformes (la
 * moyenne de ses coins), et les degrades se font en decoupant plus fin : des etages de douze
 * blocs pour la colonne, cinq anneaux par couleur pour la couronne.
 *
 * VISIBLE DE PARTOUT. Le plan lointain de Minecraft est a quatre fois la distance de rendu :
 * tout point plus loin que 0,9 fois ce plan est ramene sur la sphere de ce rayon autour de la
 * camera, dans sa direction -- l'image est la meme, et rien n'est coupe. Le nuanceur
 * position-couleur n'applique pas de brouillard : a deux mille blocs, les couleurs restent
 * pleines. Seulement dans le monde normal.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class ArcenciumBeamRenderer {

    private static final double HEIGHT = 900.0;
    private static final double TOP_FADE = 200.0;
    private static final double CORE = 5.0;
    private static final double HALO = 9.0;
    private static final int FACETS = 32;
    private static final double STEP = 12.0;

    private static final double SPIRAL = 13.0;
    private static final double RIBBON = 4.0;
    private static final double TURN = 96.0;
    private static final int PER_TURN = 48;
    /** Un tour de spirale en quatre secondes : les couleurs montent. */
    private static final double SPIN = 2.0 * Math.PI / 80.0;

    private static final double RING_Y = 230.0;
    private static final double RING_IN = 20.0;
    private static final double RING_OUT = 170.0;
    private static final int RING_SEGMENTS = 96;
    /** Les anneaux de la couronne entre deux couleurs : le degrade se fait par petites marches. */
    private static final int RING_STEPS = 5;
    private static final int RIPPLE_TICKS = 100;
    private static final double RIPPLE_MAX = 420.0;
    /** Le coeur ne parait jamais plus etroit que cela, en radians (un demi-degre de large). */
    private static final double MIN_ANGLE = 0.0045;

    /** Les sept couleurs de l'Arcencium, du rouge au violet. */
    private static final float[][] COLORS = {
            {1.00F, 0.23F, 0.23F},
            {1.00F, 0.55F, 0.10F},
            {1.00F, 0.88F, 0.10F},
            {0.23F, 0.88F, 0.35F},
            {0.18F, 0.55F, 1.00F},
            {0.36F, 0.29F, 1.00F},
            {0.70F, 0.29F, 1.00F}};
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float[] HALO_TINT = {0.85F, 0.95F, 1.0F};

    private ArcenciumBeamRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        long packed = GameHudClient.finalePos();
        int status = GameHudClient.statusOrdinal();
        // le Faisceau tient tant que la partie n'est pas jugee ; hors partie il ne peut venir que
        // de la commande d'essai, et c'est justement pour le voir
        if (packed == 0L || status == GameState.Status.WON.ordinal()
                || status == GameState.Status.LOST.ordinal()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        BlockPos arena = BlockPos.of(packed);
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double time = mc.level.getGameTime() + partial;
        double ax = arena.getX() + 0.5;
        double ay = arena.getY();
        double az = arena.getZ() + 0.5;
        double away = Math.hypot(ax - cam.x, az - cam.z);
        // pres de l'arene : plus pale, et le pied efface pour ne pas cacher le combat
        float near = (float) clamp((away - 90.0) / 160.0, 0.5, 1.0);
        // de loin, la colonne et sa spirale s'elargissent : jamais plus etroites qu'un demi-degre
        double widen = Math.max(1.0, away * MIN_ANGLE / CORE);
        double baseFade = away < 250.0 ? 70.0 : 40.0;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f matrix = event.getPoseStack().last().pose();
        double dome = mc.gameRenderer.getDepthFar() * 0.9;

        RenderType beam = ModRenderTypes.beam();
        Sky sky = new Sky(matrix, buffers.getBuffer(beam), cam, dome);
        column(sky, ax, ay, az, HALO * widen, HALO_TINT, 0.30F * near, time, baseFade);
        column(sky, ax, ay, az, CORE * widen, WHITE, 0.90F * near, time, baseFade);
        spiral(sky, ax, ay, az, SPIRAL * widen, RIBBON * widen, time, near, baseFade);
        crown(sky, ax, ay, az, time, near);
        buffers.endBatch(beam);
    }

    /** La colonne : un cylindre de rayon r, par etages de douze blocs, fondu en bas et en haut, des bandes qui montent. */
    private static void column(Sky sky, double ax, double ay, double az, double r, float[] rgb, float alpha,
                               double time, double baseFade) {
        for (double h = 0.0; h < HEIGHT; h += STEP) {
            double h1 = Math.min(HEIGHT, h + STEP);
            float a0 = alpha * heightFade(h, baseFade) * pulse(h, time);
            float a1 = alpha * heightFade(h1, baseFade) * pulse(h1, time);
            if (a0 <= 0.003F && a1 <= 0.003F) {
                continue;
            }
            for (int f = 0; f < FACETS; f++) {
                double t0 = 2.0 * Math.PI * f / FACETS;
                double t1 = 2.0 * Math.PI * (f + 1) / FACETS;
                double x0 = ax + Math.cos(t0) * r;
                double z0 = az + Math.sin(t0) * r;
                double x1 = ax + Math.cos(t1) * r;
                double z1 = az + Math.sin(t1) * r;
                sky.quad(x0, ay + h, z0, x1, ay + h, z1, x1, ay + h1, z1, x0, ay + h1, z0, rgb, a0, a0, a1, a1);
            }
        }
    }

    /** Les sept rubans enroules autour de la colonne, qui tournent. */
    private static void spiral(Sky sky, double ax, double ay, double az, double radius, double ribbon, double time,
                               float near, double baseFade) {
        double spin = time * SPIN;
        double dh = TURN / PER_TURN;
        for (int j = 0; j < COLORS.length; j++) {
            double phase = j * 2.0 * Math.PI / COLORS.length + spin;
            for (double h = 0.0; h < HEIGHT - TOP_FADE * 0.5; h += dh) {
                double h1 = h + dh;
                double t0 = phase + 2.0 * Math.PI * h / TURN;
                double t1 = phase + 2.0 * Math.PI * h1 / TURN;
                float a0 = 0.88F * near * heightFade(h, baseFade);
                float a1 = 0.88F * near * heightFade(h1, baseFade);
                if (a0 <= 0.003F && a1 <= 0.003F) {
                    continue;
                }
                double x0 = ax + Math.cos(t0) * radius;
                double z0 = az + Math.sin(t0) * radius;
                double x1 = ax + Math.cos(t1) * radius;
                double z1 = az + Math.sin(t1) * radius;
                sky.quad(x0, ay + h, z0, x1, ay + h1, z1, x1, ay + h1 + ribbon, z1, x0, ay + h + ribbon, z0,
                        COLORS[j], a0, a1, a1, a0);
            }
        }
    }

    /**
     * La couronne a la hauteur des nuages, et ses ondes. Les sept couleurs se fondent d'un anneau
     * au suivant, du violet dedans au rouge dehors, et le bord s'efface : aux premieres photos,
     * des bandes tranchees et un bord net en faisaient une cible de tir a l'arc.
     */
    private static void crown(Sky sky, double ax, double ay, double az, double time, float near) {
        double y = ay + RING_Y;
        double band = (RING_OUT - RING_IN) / (COLORS.length - 1);
        double turn = time * 2.0 * Math.PI / 1200.0;
        float[] rgb = new float[3];
        for (int k = 0; k + 1 < COLORS.length; k++) {
            float[] inner = COLORS[COLORS.length - 1 - k];
            float[] outer = COLORS[COLORS.length - 2 - k];
            for (int q = 0; q < RING_STEPS; q++) {
                // un anneau de la couleur et de l'opacite de son milieu
                double f = (k + (q + 0.5) / RING_STEPS) / (COLORS.length - 1);
                double mix = (q + 0.5) / RING_STEPS;
                for (int c = 0; c < 3; c++) {
                    rgb[c] = (float) (inner[c] + (outer[c] - inner[c]) * mix);
                }
                double r0 = RING_IN + (k + q / (double) RING_STEPS) * band;
                double r1 = r0 + band / RING_STEPS;
                float base = crownAlpha(f) * near;
                for (int s = 0; s < RING_SEGMENTS; s++) {
                    double t0 = 2.0 * Math.PI * s / RING_SEGMENTS;
                    double t1 = 2.0 * Math.PI * (s + 1) / RING_SEGMENTS;
                    // trois lobes plus clairs qui tournent : la couronne tourne, et cela se voit
                    float a = base * (float) (0.75 + 0.25 * Math.sin(3.0 * (t0 + t1) * 0.5 + turn));
                    sky.quad(ax + Math.cos(t0) * r0, y, az + Math.sin(t0) * r0,
                            ax + Math.cos(t1) * r0, y, az + Math.sin(t1) * r0,
                            ax + Math.cos(t1) * r1, y, az + Math.sin(t1) * r1,
                            ax + Math.cos(t0) * r1, y, az + Math.sin(t0) * r1, rgb, a, a, a, a);
                }
            }
        }
        // deux ondes a la fois, une toutes les cinq secondes
        for (int w = 0; w < 2; w++) {
            double age = ((time / RIPPLE_TICKS) + w * 0.5) % 1.0;
            double r = RING_IN + age * (RIPPLE_MAX - RING_IN);
            double width = 6.0 + age * 14.0;
            float a = (float) (0.80 * Math.pow(1.0 - age, 1.5)) * near;
            for (int s = 0; s < RING_SEGMENTS; s++) {
                double t0 = 2.0 * Math.PI * s / RING_SEGMENTS;
                double t1 = 2.0 * Math.PI * (s + 1) / RING_SEGMENTS;
                sky.quad(ax + Math.cos(t0) * r, y + 1.0, az + Math.sin(t0) * r,
                        ax + Math.cos(t1) * r, y + 1.0, az + Math.sin(t1) * r,
                        ax + Math.cos(t1) * (r + width), y + 1.0, az + Math.sin(t1) * (r + width),
                        ax + Math.cos(t0) * (r + width), y + 1.0, az + Math.sin(t0) * (r + width), WHITE, a, a, a, a);
            }
        }
    }

    /** L'opacite de la couronne a une fraction de son rayon : pleine au milieu, eteinte au bord et contre le coeur. */
    private static float crownAlpha(double f) {
        return (float) (0.62 * smooth(clamp(f / 0.15, 0.0, 1.0)) * (1.0 - smooth(clamp((f - 0.55) / 0.45, 0.0, 1.0))));
    }

    /** Le fondu d'une hauteur : le pied s'allume sur baseFade blocs, la cime s'eteint sur TOP_FADE. */
    private static float heightFade(double h, double baseFade) {
        double foot = smooth(clamp(h / baseFade, 0.0, 1.0));
        double top = 1.0 - smooth(clamp((h - (HEIGHT - TOP_FADE)) / TOP_FADE, 0.0, 1.0));
        return (float) (foot * top);
    }

    /** Des bandes plus claires qui remontent la colonne, une tous les soixante blocs. */
    private static float pulse(double h, double time) {
        return (float) (0.86 + 0.14 * Math.sin((h - time * 1.2) * 2.0 * Math.PI / 60.0));
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** Les points du monde ramenes sur la sphere lointaine s'ils sont plus loin qu'elle, puis traces. */
    private static final class Sky {
        private final Matrix4f matrix;
        private final VertexConsumer vc;
        private final Vec3 cam;
        private final double dome;

        Sky(Matrix4f matrix, VertexConsumer vc, Vec3 cam, double dome) {
            this.matrix = matrix;
            this.vc = vc;
            this.cam = cam;
            this.dome = dome;
        }

        /**
         * Un quad de quatre points du monde, d'UNE couleur et d'UNE opacite : la moyenne de celles
         * de ses coins (voir « un quad, une couleur »).
         */
        void quad(double x0, double y0, double z0, double x1, double y1, double z1,
                  double x2, double y2, double z2, double x3, double y3, double z3,
                  float[] rgb, float a0, float a1, float a2, float a3) {
            float a = (a0 + a1 + a2 + a3) * 0.25F;
            vertex(x0, y0, z0, rgb, a);
            vertex(x1, y1, z1, rgb, a);
            vertex(x2, y2, z2, rgb, a);
            vertex(x3, y3, z3, rgb, a);
        }

        private void vertex(double wx, double wy, double wz, float[] rgb, float alpha) {
            double x = wx - this.cam.x;
            double y = wy - this.cam.y;
            double z = wz - this.cam.z;
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d > this.dome) {
                double k = this.dome / d;
                x *= k;
                y *= k;
                z *= k;
            }
            this.vc.addVertex(this.matrix, (float) x, (float) y, (float) z)
                    .setColor(rgb[0], rgb[1], rgb[2], Math.max(0.0F, Math.min(1.0F, alpha)));
        }
    }
}
