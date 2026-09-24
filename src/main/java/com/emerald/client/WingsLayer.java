package com.emerald.client;

import com.emerald.specialization.Specialization;
import com.emerald.specialization.WingSkin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Les ailes de specialisation, dans le dos du joueur.
 *
 * Chaque aile est une peinture attachee au torse -- elle suit le corps quand
 * il se penche -- posee derriere l'omoplate, ouverte vers l'arriere et
 * l'exterieur, qui bat lentement et plus vite quand le joueur court ou vole.
 * La texture est une aile DROITE peinte, racine en bas a gauche (voir
 * WingSkin) ; l'aile gauche est la meme texture en miroir.
 *
 * ELLES VIVENT (cahier §96, le joueur : « j'ai l'impression qu'elles ne sont pas
 * vivantes, que c'est juste du papier qu'on a sur le dos »). Une aile n'est plus
 * un carton qui pivote d'un bloc : elle PLIE le long de son envergure -- une
 * vague part de l'omoplate et la pointe la suit un quart de battement plus tard,
 * comme un vrai battement --, elle est un peu BOMBEE de haut en bas, et les
 * plumes du bout FRISSONNENT, plus fort en vol, dans le vent. Voir Shape.
 *
 * La taille suit le palier : de deux moignons a +1 a l'envergure pleine a
 * +15, puis encore un cinquieme de plus jusqu'a +20 (cahier §96 : « les ailes
 * +20 seraient environ 20 % plus grandes »).
 *
 * A +20, deux choses de plus, choisies par le joueur : les REFLETS D'ARCENCIUM
 * SUR LE BORD DES AILES -- les sept couleurs glissent le long du lisere, une
 * bande plus claire le parcourt, et il luit la nuit ; la peinture garde ses
 * couleurs -- et, en vol d'elytre, la TRAINEE du bout des ailes (WingsTrail).
 * La poussee d'envol, la troisieme, est dans ArtifactInputClient.
 *
 * TOUTES LES AILES SONT DE LA MATIERE. Elles se rendent d'abord en decoupe
 * opaque, eclairees par le monde : c'est ce qui leur donne une ombre sous un
 * pack de shaders et un corps quand on les regarde. Les apparences de LUMIERE
 * recoivent PAR-DESSUS une seconde passe emissive, translucide, qui les fait
 * luire la nuit sans les rendre fantomatiques -- le joueur l'a dit : « chaque
 * aile doit etre cent pour cent materialisee », et les ailes emissives seules
 * etaient des vitres sans ombre.
 */
public class WingsLayer<T extends AbstractClientPlayer, M extends PlayerModel<T>> extends RenderLayer<T, M> {

    /** Envergure d'une aile a +15, en blocs. */
    private static final float FULL_SIZE = 3.4F;
    /** Ce que les ailes gagnent encore de +15 a +20 : un cinquieme. */
    private static final float GROWTH = 0.2F;
    /** Les bandes des reflets, de la racine a la pointe : une couleur chacune (cahier §93 C). */
    private static final int SHEEN_STRIPS = 40;
    /** La souplesse d'une aile : colonnes de la racine a la pointe (les bandes des reflets y tombent juste). */
    private static final int COLUMNS = 10;
    /** ... et rangees de haut en bas, pour le bombe. */
    private static final int ROWS = 6;
    /** Le bombe de haut en bas, en part de l'envergure : les bords reviennent vers le corps. */
    private static final float CAMBER = 0.06F;
    /** Le retard de la pointe sur l'omoplate, en radians : un quart de battement. */
    private static final float LAG = 1.6F;
    /** Opacite de la passe de lueur : assez pour luire la nuit, pas assez pour blanchir le jour. */
    private static final int GLOW_ALPHA = 150;

    public WingsLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** L'envergure d'une aile pour un palier. */
    public static float sizeFor(int level) {
        float t = Math.min(15, level) / 15.0F;
        float full = FULL_SIZE * (0.12F + 0.88F * (float) Math.pow(t, 0.85));
        return full * (1.0F + GROWTH * Mth.clamp(level - 15, 0, 5) / 5.0F);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, T player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                       float headPitch) {
        int level = WingsClient.level(player);
        if (level <= 0 || player.isInvisible()) {
            return;
        }
        WingSkin skin = WingsClient.skin(player);
        float size = sizeFor(level);
        // le battement : lent au repos, plus ample et plus rapide en mouvement
        boolean moving = player.isFallFlying() || limbSwingAmount > 0.15F
                || WingsFlightClient.gliding(player);
        // LE SOUVERAIN ASTRAL BAT PLUS LENT ET PLUS AMPLE : un battement de
        // rapace, pas de moineau. C'est la premiere chose qui le distingue de
        // loin, avant meme la couleur.
        boolean astral = skin == WingSkin.SOUVERAIN_ASTRAL;
        float tempo = astral ? 0.045F : 0.07F;
        float reach = astral ? 11.0F : 7.0F;
        float beat = astral ? 0.22F : 0.32F;
        float flap = Mth.sin(ageInTicks * tempo) * reach
                + (moving ? Mth.sin(ageInTicks * beat) * 12.0F : 0.0F);
        float lift = Mth.sin(ageInTicks * tempo + 1.2F) * (astral ? 4.5F : 3.0F);
        // la vague qui plie l'aile suit les memes ondes que le battement, en retard vers la pointe
        Bend bend = new Bend(0.6F * reach * Mth.DEG_TO_RAD, tempo,
                moving ? 6.0F * Mth.DEG_TO_RAD : 0.0F, beat);
        float ripple = moving ? 0.02F : 0.012F;
        if (player.isFallFlying()) {
            // LE VOL D'ELYTRE : les ailes s'ouvrent en grand et planent, sans battre -- une
            // ondulation lente, comme un rapace qui tient le vent ; le bout des plumes y frissonne
            flap = -17.0F + Mth.sin(ageInTicks * 0.09F) * 2.5F;
            lift = 3.0F + Mth.sin(ageInTicks * 0.09F + 1.0F) * 1.5F;
            bend = new Bend(3.0F * Mth.DEG_TO_RAD, 0.09F, 0.0F, 0.0F);
            ripple = 0.035F;
        }
        Shape[] shapes = {new Shape(-1, skin, size, bend, ageInTicks, ripple),
                new Shape(1, skin, size, bend, ageInTicks, ripple)};
        // LA LUEUR VIT. Une passe emissive fixe se remarque une fois puis
        // disparait du regard ; l'astrale scintille -- deux ondes qui ne
        // battent jamais a l'unisson, comme des etoiles qui vacillent.
        int glowAlpha = GLOW_ALPHA;
        if (astral) {
            float twinkle = 0.62F + 0.22F * Mth.sin(ageInTicks * 0.19F)
                    + 0.16F * Mth.sin(ageInTicks * 0.53F + 2.1F);
            glowAlpha = (int) (255 * Mth.clamp(twinkle, 0.3F, 1.0F));
        }

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0.0F, 0.0F, 0.15F);            // juste derriere le dos

        // DEUX PASSES SEPAREES, ET JAMAIS DEUX TAMPONS EN MAIN.
        //
        // On tenait le tampon de la matiere ET celui de la lueur en meme temps,
        // puis on ecrivait dans les deux en alternance. Dans le MONDE cela
        // passe : les types de rendu des entites y ont chacun leur tampon
        // reserve. Dans l'INVENTAIRE, non -- la source n'en garde qu'un, et
        // demander le second TERMINE le premier. La premiere ecriture suivante
        // tombait alors sur « Not building! », et le jeu plantait des qu'on
        // ouvrait son sac avec des ailes emissives dans le dos.
        //
        // On fait donc la matiere en entier, puis la lueur en entier, en
        // reprenant le tampon a chaque fois.
        drawWings(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(skin.texture())),
                shapes, light, flap, lift, 1.0F, 255, 0.0F);
        if (skin.emissive) {
            drawWings(pose, buffer.getBuffer(RenderType.entityTranslucentEmissive(skin.texture())),
                    shapes, LightTexture.FULL_BRIGHT, flap, lift, skin.tint, glowAlpha, -0.004F);
        }
        if (level >= Specialization.MAX) {
            net.minecraft.resources.ResourceLocation mask = WingMasks.of(skin);
            if (mask != null) {
                drawSheen(pose, buffer.getBuffer(RenderType.entityTranslucentEmissive(mask)), shapes, flap, lift,
                        ageInTicks);
            }
            if (player.isFallFlying() && WingsTrail.inWorld()) {
                recordTips(pose, player, shapes, flap, lift, player.level().getGameTime() + partialTick);
            }
        }
        pose.popPose();
    }

    /** Les transformations d'une aile, de l'omoplate a son plan (les memes pour chaque passe). */
    private static void wingFrame(PoseStack pose, int side, float flap, float lift, float depth) {
        // l'omoplate : dans l'espace du modele, +y descend, -x est la droite du joueur
        pose.translate(side * 0.14F, 0.17F, depth);
        pose.mulPose(Axis.YP.rotationDegrees(side * (-24.0F - flap)));
        pose.mulPose(Axis.ZP.rotationDegrees(-side * (6.0F + lift)));   // les pointes montent
    }

    /**
     * La vague qui plie une aile : l'onde du repos et celle du mouvement, chacune de son amplitude
     * a la pointe (radians) et de sa vitesse (radians par tique, celles du battement).
     */
    private record Bend(float rest, float restSpeed, float move, float moveSpeed) {
        /** L'inclinaison de l'aile a cette part de son envergure (0 a l'omoplate, 1 a la pointe). */
        float at(float sigma, float time) {
            float weight = (float) Math.pow(Math.max(0.0F, sigma), 1.2);
            return weight * (this.rest * Mth.sin(time * this.restSpeed - LAG * sigma)
                    + this.move * Mth.sin(time * this.moveSpeed - LAG * sigma));
        }
    }

    /**
     * LA FORME D'UNE AILE A CETTE IMAGE, dans le repere de l'omoplate. Le long de l'envergure, une
     * chaine de colonnes : la partie de la peinture en deca de la racine reste droite, au-dela
     * chaque tronçon s'incline de la vague (Bend) -- la pointe suit en retard. De haut en bas, le
     * bombe ; au bout, le frisson des plumes, une petite onde qui court vers l'exterieur. La
     * matiere, la lueur, les reflets et la trainee lisent tous la meme.
     */
    private static final class Shape {
        private final int side;
        private final float size;
        private final float rootV;
        private final float time;
        private final float ripple;
        private final float[] x = new float[COLUMNS + 1];
        private final float[] z = new float[COLUMNS + 1];
        private final float[] angle = new float[COLUMNS + 1];

        Shape(int side, WingSkin skin, float size, Bend bend, float time, float ripple) {
            this.side = side;
            this.size = size;
            this.rootV = skin.rootV;
            this.time = time;
            this.ripple = ripple;
            float span = Math.max(1.0e-3F, (1.0F - skin.rootU) * size);
            float px = 0.0F;
            float pz = 0.0F;
            float ps = 0.0F;
            for (int i = 0; i <= COLUMNS; i++) {
                float s = (i / (float) COLUMNS - skin.rootU) * size;
                if (s <= 0.0F) {
                    this.x[i] = side * s;
                    continue;
                }
                float ds = (s - ps) / 4.0F;
                for (int k = 0; k < 4; k++) {
                    float a = bend.at((ps + ds * (k + 0.5F)) / span, time);
                    px += ds * Mth.cos(a);
                    pz += ds * Mth.sin(a);
                }
                ps = s;
                this.x[i] = side * px;
                this.z[i] = pz;
                this.angle[i] = bend.at(s / span, time);
            }
        }

        /** Un point de la peinture (u, v), dans le repere de l'omoplate. */
        Vector3f point(float u, float v, Vector3f out) {
            float f = Mth.clamp(u, 0.0F, 1.0F) * COLUMNS;
            int i = Math.min((int) f, COLUMNS - 1);
            float t = f - i;
            float dv = v - this.rootV;
            float depth = Mth.lerp(t, this.z[i], this.z[i + 1])
                    - CAMBER * this.size * dv * dv
                    + this.ripple * this.size * u * u * Mth.sin(this.time * 0.3F - 6.0F * u - 2.5F * v);
            return out.set(Mth.lerp(t, this.x[i], this.x[i + 1]), dv * this.size, depth);
        }

        /** L'inclinaison de l'aile a cette abscisse de la peinture : pour la normale. */
        float angle(float u) {
            float f = Mth.clamp(u, 0.0F, 1.0F) * COLUMNS;
            int i = Math.min((int) f, COLUMNS - 1);
            return Mth.lerp(f - i, this.angle[i], this.angle[i + 1]);
        }
    }

    /** Les deux ailes, en une passe : un seul tampon, du debut a la fin. */
    private static void drawWings(PoseStack pose, VertexConsumer vc, Shape[] shapes, int light,
                                  float flap, float lift, float tint, int alpha, float depth) {
        int c = (int) (255 * tint);
        Vector3f p = new Vector3f();
        for (Shape shape : shapes) {
            pose.pushPose();
            wingFrame(pose, shape.side, flap, lift, depth);
            PoseStack.Pose last = pose.last();
            // la texture se lit toujours racine a gauche, pointe a droite : le miroir de l'aile
            // gauche est dans la forme (x change de signe), pas dans la texture
            for (int i = 0; i < COLUMNS; i++) {
                float u0 = i / (float) COLUMNS;
                float u1 = (i + 1) / (float) COLUMNS;
                for (int j = 0; j < ROWS; j++) {
                    float v0 = j / (float) ROWS;
                    float v1 = (j + 1) / (float) ROWS;
                    vertex(vc, last, shape, u0, v1, light, c, c, c, alpha, p);
                    vertex(vc, last, shape, u1, v1, light, c, c, c, alpha, p);
                    vertex(vc, last, shape, u1, v0, light, c, c, c, alpha, p);
                    vertex(vc, last, shape, u0, v0, light, c, c, c, alpha, p);
                }
            }
            pose.popPose();
        }
    }

    /**
     * LES REFLETS D'ARCENCIUM des ailes +20, SUR LEUR BORD : sur le lisere de l'apparence
     * (WingMasks), les sept couleurs de la racine a la pointe, qui glissent vers la pointe ; une
     * bande plus claire le parcourt toutes les quatre secondes. Emissifs : ils luisent la nuit. Une
     * couleur et une opacite par bande. (Sur toute l'aile, ils denaturaient la peinture : le joueur
     * les a voulus sur les bordures seulement.)
     *
     * DES DEUX COTES DE L'AILE. Un decalage vers le corps (celui de la lueur des apparences de
     * lumiere) ne se voit que de face : de dos -- la vue de tout le monde en troisieme personne --
     * la matiere le cachait, et seuls ses bords depassaient (premieres photos). Les reflets se
     * posent donc devant et derriere la matiere ; le test de profondeur ne garde que celui qui
     * regarde la camera.
     */
    private static void drawSheen(PoseStack pose, VertexConsumer vc, Shape[] shapes, float flap, float lift,
                                  float ageInTicks) {
        float band = (ageInTicks / 80.0F) % 1.0F * 1.6F - 0.3F;
        Vector3f p = new Vector3f();
        for (int face = 0; face < 2; face++) {
            for (Shape shape : shapes) {
                pose.pushPose();
                wingFrame(pose, shape.side, flap, lift, face == 0 ? 0.007F : -0.007F);
                PoseStack.Pose last = pose.last();
                for (int k = 0; k < SHEEN_STRIPS; k++) {
                    float u0 = k / (float) SHEEN_STRIPS;
                    float u1 = (k + 1) / (float) SHEEN_STRIPS;
                    float mid = (u0 + u1) * 0.5F;
                    // le lisere porte l'arc-en-ciel de la racine a la pointe, une bande plus claire y passe
                    float hue = ((mid * 0.7F - ageInTicks * 0.004F) % 1.0F + 1.0F) % 1.0F;
                    int rgb = java.awt.Color.HSBtoRGB(hue, 0.55F, 1.0F);
                    float glow = 0.35F + 0.45F * Math.max(0.0F, 1.0F - Math.abs(mid - band) / 0.18F);
                    int alpha = (int) (255 * glow);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    for (int j = 0; j < ROWS; j++) {
                        float v0 = j / (float) ROWS;
                        float v1 = (j + 1) / (float) ROWS;
                        vertex(vc, last, shape, u0, v1, LightTexture.FULL_BRIGHT, r, g, b, alpha, p);
                        vertex(vc, last, shape, u1, v1, LightTexture.FULL_BRIGHT, r, g, b, alpha, p);
                        vertex(vc, last, shape, u1, v0, LightTexture.FULL_BRIGHT, r, g, b, alpha, p);
                        vertex(vc, last, shape, u0, v0, LightTexture.FULL_BRIGHT, r, g, b, alpha, p);
                    }
                }
                pose.popPose();
            }
        }
    }

    /**
     * Le bout des deux ailes, dans le monde, pour la trainee (WingsTrail) : le bord de la pointe,
     * au tiers haut de la peinture, la ou la forme le met. La pile de poses d'une entite part de la
     * camera : il suffit de lui rajouter sa position. Un point a plus de huit blocs du joueur vient
     * d'une autre passe (une ombre de shader) : on l'ecarte.
     */
    private static void recordTips(PoseStack pose, AbstractClientPlayer player, Shape[] shapes, float flap,
                                   float lift, double time) {
        net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer
                .getMainCamera().getPosition();
        net.minecraft.world.phys.Vec3[] tips = new net.minecraft.world.phys.Vec3[2];
        Vector3f p = new Vector3f();
        for (int k = 0; k < 2; k++) {
            Shape shape = shapes[k];
            pose.pushPose();
            wingFrame(pose, shape.side, flap, lift, 0.0F);
            shape.point(1.0F, 0.3F, p);
            pose.last().pose().transformPosition(p);
            tips[k] = new net.minecraft.world.phys.Vec3(p.x() + cam.x, p.y() + cam.y, p.z() + cam.z);
            pose.popPose();
        }
        if (tips[0].distanceToSqr(player.position()) < 64.0 && tips[1].distanceToSqr(player.position()) < 64.0) {
            WingsTrail.record(player.getUUID(), tips[0], tips[1], time);
        }
    }

    /** Un sommet de la forme, sa normale tournee comme l'aile a cet endroit. */
    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, Shape shape, float u, float v, int light,
                               int r, int g, int b, int alpha, Vector3f scratch) {
        shape.point(u, v, scratch);
        float a = shape.angle(u);
        vc.addVertex(pose.pose(), scratch.x(), scratch.y(), scratch.z())
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, -shape.side * Mth.sin(a), 0.0F, Mth.cos(a));
    }
}
