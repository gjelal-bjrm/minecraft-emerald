package com.emerald.client;

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

/**
 * Les ailes de specialisation, dans le dos du joueur.
 *
 * Chaque aile est un plan texture attache au torse -- il suit le corps quand
 * il se penche -- pose derriere l'omoplate, ouvert vers l'arriere et
 * l'exterieur, qui bat lentement et plus vite quand le joueur court ou vole.
 * La texture est une aile DROITE peinte, racine en bas a gauche (voir
 * WingSkin) ; l'aile gauche est la meme texture en miroir.
 *
 * La taille suit le palier : de deux moignons a +1 a l'envergure pleine a
 * +15 ; au-dela, les ailes ne changent plus, ce sont les animations autour
 * du corps qui s'ajoutent (a venir).
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
    /** Ou est la racine dans la texture : 12 % du bord gauche, 78 % du haut. */
    private static final float ROOT_U = 0.12F;
    private static final float ROOT_V = 0.78F;

    public WingsLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** L'envergure d'une aile pour un palier. */
    public static float sizeFor(int level) {
        float t = Math.min(15, level) / 15.0F;
        return FULL_SIZE * (0.12F + 0.88F * (float) Math.pow(t, 0.85));
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
        boolean moving = player.isFallFlying() || limbSwingAmount > 0.15F;
        float flap = Mth.sin(ageInTicks * 0.07F) * 7.0F
                + (moving ? Mth.sin(ageInTicks * 0.32F) * 12.0F : 0.0F);
        float lift = Mth.sin(ageInTicks * 0.07F + 1.2F) * 3.0F;

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0.0F, 0.0F, 0.15F);            // juste derriere le dos
        // la matiere : decoupe opaque, lumiere du monde -- et donc une ombre
        VertexConsumer body = buffer.getBuffer(RenderType.entityCutoutNoCull(skin.texture()));
        // la lueur des apparences de lumiere, posee par-dessus, un peu en avant
        VertexConsumer glow = skin.emissive
                ? buffer.getBuffer(RenderType.entityTranslucentEmissive(skin.texture())) : null;
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            // l'omoplate : dans l'espace du modele, +y descend, -x est la droite du joueur
            pose.translate(side * 0.14F, 0.17F, 0.0F);
            pose.mulPose(Axis.YP.rotationDegrees(side * (-24.0F - flap)));
            pose.mulPose(Axis.ZP.rotationDegrees(-side * (6.0F + lift)));   // les pointes montent
            quad(pose, body, side, size, light, 1.0F, 255);
            if (glow != null) {
                pose.translate(0.0F, 0.0F, -0.004F);
                quad(pose, glow, side, size, LightTexture.FULL_BRIGHT, skin.tint, GLOW_ALPHA);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    /**
     * Le plan d'une aile. La texture est l'aile DROITE ; la droite du joueur
     * est -x dans l'espace du modele, on y pose la texture telle quelle, et
     * on la miroite pour la gauche.
     */
    /** Opacite de la passe de lueur : assez pour luire la nuit, pas assez pour blanchir le jour. */
    private static final int GLOW_ALPHA = 150;

    private static void quad(PoseStack pose, VertexConsumer vc, int side, float size, int light,
                             float tint, int alpha) {
        float x0 = side * (-ROOT_U * size);          // bord de la racine
        float x1 = side * ((1.0F - ROOT_U) * size);  // bord de la pointe
        float yTop = -(ROOT_V * size);               // le haut de la texture (y monte vers le negatif)
        float yBot = (1.0F - ROOT_V) * size;
        // le miroir est deja fait par la geometrie (x0 pres du corps, x1 dehors,
        // des deux cotes) : la texture se lit toujours racine a gauche, pointe
        // a droite. La retourner en plus donnait une aile a l'envers.
        float uRoot = 0.0F;
        float uTip = 1.0F;
        PoseStack.Pose last = pose.last();
        int c = (int) (255 * tint);
        vertex(vc, last, x0, yBot, uRoot, 1.0F, light, c, alpha);
        vertex(vc, last, x1, yBot, uTip, 1.0F, light, c, alpha);
        vertex(vc, last, x1, yTop, uTip, 0.0F, light, c, alpha);
        vertex(vc, last, x0, yTop, uRoot, 0.0F, light, c, alpha);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float u, float v,
                               int light, int c, int alpha) {
        vc.addVertex(pose.pose(), x, y, 0.0F)
                .setColor(c, c, c, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
