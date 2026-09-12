package com.emerald.client;

import com.emerald.item.UpgradeGlow;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Le HALO de l'arme amelioree : sa propre silhouette, redessinee en lumiere.
 *
 * C'est ce qui fait la difference entre une aura et un nuage de points. On
 * rend l'arme une seconde fois -- et une troisieme -- legerement agrandie,
 * dans une couleur pleine, additive et insensible a la lumiere ambiante. Le
 * halo epouse donc exactement la forme de la lame : une lame courbe a un halo
 * courbe, un sceptre a un halo de sceptre. Aucune particule ne sait faire
 * cela.
 *
 * DEUX COUCHES, et non une. La premiere, serree et dense, est le CORPS de la
 * lueur ; la seconde, plus large et plus diffuse, en est le VOILE. Une seule
 * couche donne un contour dur qui ressemble a une erreur de rendu ; deux
 * donnent un degrade, et c'est le degrade qu'on lit comme de la lumiere.
 *
 * LE HALO RESPIRE. Son opacite suit une sinusoide lente, differente pour les
 * deux couches, de sorte qu'elles ne battent jamais a l'unisson. Une lueur
 * fixe se remarque une fois puis disparait du regard ; une lueur qui respire
 * reste vivante dans le coin de l'oeil.
 *
 * Il se rend a la premiere personne (ce que voit le porteur) ET a la
 * troisieme (ce que voient les autres), par deux chemins differents mais une
 * seule routine : {@link #renderHalo}.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class UpgradeHaloRenderer {

    /**
     * Les deux couches : agrandissement, et part de l'intensite qu'elles portent.
     *
     * RECALIBREES le 6 septembre : le premier contour a 1,07 et 0,42 ne se
     * voyait pas (« tres peu visible ») ; il est deux fois plus large et deux
     * fois plus dense. La couche large du +8 etait a 0,09 : invisible par
     * construction. Elle passe a 0,30 et s'ecarte a 1,45.
     */
    private static final float[] SCALE = {1.12F, 1.30F};
    private static final float[] ALPHA = {0.45F, 0.22F};
    /** Une troisieme couche, tres large, reservee aux auras LONGUES (+8 et plus). */
    private static final float LARGE_SCALE = 1.50F;
    private static final float LARGE_ALPHA = 0.18F;

    // --- la JAUGE du cran, gravee sur la lame
    /**
     * Le cran se lit sur la lame comme une jauge : de +1 a +7, la couleur du
     * palier monte du bas de la lame vers la pointe, un septieme par cran, avec
     * un trait blanc net a la frontiere.
     *
     * J'AVAIS VOULU DES ENCOCHES, une par cran, qu'on compte. La mesure les a
     * refusees : une epee tient sur seize pixels, sa lame sur onze ; sept
     * traits separes n'y tiennent pas, et sur la capture on ne voyait qu'une
     * lame blanchie. La jauge dit la meme chose -- combien de crans -- avec ce
     * que les pixels permettent, et elle se lit a toute distance.
     */
    private static final float NOTCH_FIRST = 0.30F;
    private static final float NOTCH_SPAN = 0.65F;
    /** Demi-largeur du trait de frontiere, en fraction de la lame. */
    private static final float NOTCH_WIDTH = 0.035F;
    /**
     * La couleur de la jauge des quatre premiers crans : un BLEU FROID, pas le
     * blanc du halo. La mesure l'a impose : blanche, la jauge se confondait
     * avec une lame de fer et l'on ne voyait pas ou elle s'arretait. Le halo
     * garde son blanc ; la jauge, elle, doit se detacher de la lame.
     */
    private static final int GAUGE_COOL = 0x3F8CFF;
    /** La jauge se dessine a fleur de lame, juste au-dessus de sa surface. */
    private static final float NOTCH_SCALE = 1.03F;
    /** Force de la couleur sur la part remplie, et sur la lame entiere au +8. */
    private static final float GAUGE_ALPHA = 0.90F;
    private static final float FILL_ALPHA = 0.60F;
    private static final float SWEEP_WIDTH = 0.16F;
    private static final float SWEEP_SPEED = 1.0F / 30.0F;

    // --- les ETINCELLES qui tombent de l'arme
    /** Par tique et par cran, la chance qu'une etincelle se detache : 0,12 x cran. */
    private static final float SPARK_PER_LEVEL = 0.12F;
    /** Pendant un coup, au +8 et au-dela : autant d'etincelles de plus, le long de la lame. */
    private static final int TRAIL_SPARKS = 6;
    /** Ce qu'on a deja tire cette tique, par porteur et par point de vue. */
    private static final java.util.Map<Integer, Integer> sparkTick = new java.util.HashMap<>();

    // --- la VAGUE des raretes 7 et 8
    /** A partir de ce rang, la vague existe. Legendaire et Phenomenal. */
    private static final int WAVE_FROM = 7;
    /** Un cycle garde -> pointe dure vingt-quatre ticks, un peu plus d'une seconde. */
    private static final float WAVE_SPEED = 1.0F / 24.0F;
    /** Largeur de la bande, en fraction de la lame ; le Phenomenal a une bande plus large. */
    private static final float[] WAVE_WIDTH = {0.13F, 0.18F};
    /** Force de la bande a son sommet, prise dans la couleur du rang. */
    private static final float[] WAVE_STRENGTH = {0.55F, 0.78F};
    /** Ou la vague se dessine : juste hors du corps du halo, sous son voile. */
    private static final float WAVE_SCALE = 1.11F;

    private UpgradeHaloRenderer() {
    }

    /** Vrai si l'arme a quelque chose a montrer : un cran, ou un rang qui fait la vague. */
    public static boolean shows(ItemStack stack) {
        return UpgradeGlow.glows(stack)
                || com.emerald.item.GearRarity.of(stack).rank() >= WAVE_FROM;
    }

    // ------------------------------------------------ premiere personne

    /**
     * Le halo dans la main du joueur lui-meme.
     *
     * On rejoue ici les transformations que le jeu applique a l'objet tenu --
     * equipement, balancement, position du bras -- parce que l'evenement se
     * declenche AVANT qu'elles ne soient posees. Sans les rejouer, le halo
     * flotterait au milieu de l'ecran pendant que l'arme bougerait en bas a
     * droite. Ce sont les formules exactes de ItemInHandRenderer, pour le cas
     * d'une arme tenue sans etre en cours d'utilisation.
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        if (!shows(stack)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isUsingItem()) {
            return;                       // un arc bande a ses propres poses : on s'abstient
        }
        HumanoidArm arm = event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? mc.player.getMainArm() : mc.player.getMainArm().getOpposite();
        boolean left = arm == HumanoidArm.LEFT;
        int side = left ? -1 : 1;
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        // applyItemArmTransform
        pose.translate(side * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        // applyItemArmAttackTransform
        float f = Mth.sin(swing * swing * (float) Math.PI);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45.0F + f * -20.0F)));
        float f1 = Mth.sin(Mth.sqrt(swing) * (float) Math.PI);
        pose.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * -45.0F));

        renderHalo(mc.player, stack,
                left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                        : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                left, pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    // ------------------------------------------------------- la routine

    /**
     * Dessine les couches du halo, l'arme etant deja placee dans la main.
     *
     * L'agrandissement se fait autour du CENTRE de l'objet, via renderScaled.
     * J'avais d'abord agrandi depuis l'origine de la main en jugeant la derive
     * « en dessous de ce que l'oeil distingue » : c'etait faux. Sur une lame
     * longue, la pointe derivait d'un tiers de sa distance a la main et les
     * couches s'y deployaient en eventail -- une tache blanche au bout de
     * l'epee, visible au premier coup d'oeil. Ce qui est vrai pres de la main
     * ne l'est pas a l'autre bout de l'objet.
     */
    public static void renderHalo(LivingEntity holder, ItemStack stack, ItemDisplayContext context,
                                  boolean leftHand, PoseStack pose, MultiBufferSource buffer,
                                  int light) {
        Minecraft mc = Minecraft.getInstance();
        float time = (holder.tickCount + mc.getTimer().getGameTimeDeltaPartialTick(true));

        // La vague ne depend pas du cran : une arme Legendaire +0 la porte deja.
        int rank = com.emerald.item.GearRarity.of(stack).rank();
        if (rank >= WAVE_FROM) {
            renderWave(holder, stack, context, leftHand, pose, buffer, light, rank, time);
        }

        UpgradeGlow.Aura aura = UpgradeGlow.of(stack);
        if (aura.intensity() <= 0.0F) {
            return;
        }

        int layers = aura.layers();
        for (int i = 0; i < layers; i++) {
            float scale = i < 2 ? SCALE[i] : LARGE_SCALE;
            float base = i < 2 ? ALPHA[i] : LARGE_ALPHA;
            // chaque couche respire a son rythme : jamais a l'unisson
            float breath = 0.82F + 0.18F * Mth.sin(time * (0.11F + 0.04F * i) + i * 2.1F);
            float alpha = base * aura.intensity() * breath;

            renderScaled(holder, stack, context, leftHand, pose,
                    new GlowSource(buffer, aura.tint(time), alpha), light, scale);
        }

        int level = com.emerald.item.Upgrade.of(stack);
        renderNotches(holder, stack, context, leftHand, pose, buffer, light, aura, level, time);
        sparks(holder, stack, context, leftHand, pose, aura, level);
    }

    // ------------------------------------------------------- les encoches

    /**
     * LES ENCOCHES : un trait de lumiere par cran, qu'on compte du regard.
     *
     * « Il faudrait qu'on voie clairement que l'arme a ete amelioree. » Une
     * lueur plus ou moins forte ne se compte pas ; des traits, si. De +1 a +7,
     * chaque cran grave un trait de plus le long de la lame, du bas vers la
     * pointe, dans la couleur du palier. Au +8 et au-dela, la lame ENTIERE
     * prend la couleur -- or, turquoise, puis la teinte tournante du
     * prismatique -- et une lumiere blanche la balaie de la garde a la pointe.
     *
     * Meme technique que la vague des raretes : chaque face du modele est
     * teintee d'apres sa hauteur dans la lame. Cela vaut pour TOUTES les armes
     * sans une texture de plus : une epee en fer, le Glaive, le sceptre, l'arc.
     */
    private static void renderNotches(LivingEntity holder, ItemStack stack, ItemDisplayContext context,
                                      boolean leftHand, PoseStack pose, MultiBufferSource buffer,
                                      int light, UpgradeGlow.Aura aura, int level, float time) {
        if (level <= 0) {
            return;
        }
        float[] tint = aura.tint(time);
        int colour = level <= 4 ? GAUGE_COOL
                : ((int) (tint[0] * 255) << 16) | ((int) (tint[1] * 255) << 8) | (int) (tint[2] * 255);
        float sweep = (time * SWEEP_SPEED) % 1.0F;
        renderScaled(holder, stack, context, leftHand, pose,
                new NotchSource(buffer, colour, Math.min(level, 7), level >= 8, sweep), light,
                NOTCH_SCALE);
    }

    private record NotchSource(MultiBufferSource inner, int colour, int notches, boolean filled,
                               float sweep) implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType ignored) {
            // en fondu alpha, comme la vague : le trait RECOUVRE la lame, quel
            // que soit le halo dessous
            return new Notched(this.inner.getBuffer(
                    RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS)),
                    this.colour, this.notches, this.filled, this.sweep);
        }
    }

    /** Le consommateur des encoches : la force de chaque face vient de sa hauteur dans la lame. */
    private record Notched(VertexConsumer inner, int colour, int notches, boolean filled, float sweep)
            implements VertexConsumer {

        private static final int STRIDE = 8;

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float r, float g, float b,
                                float a, int light, int overlay, boolean readAlpha) {
            int[] v = quad.getVertices();
            float y = 0.0F;
            for (int i = 0; i < 4; i++) {
                y += Float.intBitsToFloat(v[i * STRIDE + 1]);
            }
            y *= 0.25F;

            float w;
            float cr = ((this.colour >> 16) & 0xFF) / 255.0F;
            float cg = ((this.colour >> 8) & 0xFF) / 255.0F;
            float cb = (this.colour & 0xFF) / 255.0F;
            if (this.filled) {
                // la lame entiere, et le balayage blanc par-dessus
                float d = y - this.sweep;
                float s = (float) Math.exp(-(d * d) / (2.0F * SWEEP_WIDTH * SWEEP_WIDTH));
                w = FILL_ALPHA + (1.0F - FILL_ALPHA) * s;
                cr += (1.0F - cr) * s;
                cg += (1.0F - cg) * s;
                cb += (1.0F - cb) * s;
            } else {
                // la jauge DESCEND DE LA POINTE : au +1 la pointe seule est
                // coloree, au +7 la lame entiere. Partie de la poignee, la
                // mesure montrait un +1 invisible -- un septieme de lame dans
                // l'ombre de la main. La pointe, elle, est toujours en vue.
                float edge = NOTCH_FIRST + NOTCH_SPAN * (1.0F - this.notches / 7.0F);
                float d = y - edge;
                float line = (float) Math.exp(-(d * d) / (2.0F * NOTCH_WIDTH * NOTCH_WIDTH));
                if (y >= edge) {
                    w = GAUGE_ALPHA + (1.0F - GAUGE_ALPHA) * line;
                } else if (line > 0.08F) {
                    w = line;
                } else {
                    return;                     // sous la jauge : la lame nue
                }
                // le trait de frontiere est SOMBRE : blanc, il se perdait dans le halo
                cr *= 1.0F - 0.85F * line;
                cg *= 1.0F - 0.85F * line;
                cb *= 1.0F - 0.85F * line;
            }
            this.inner.putBulkData(pose, quad, cr, cg, cb, w, light, overlay, readAlpha);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this.inner.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.inner.setColor(r, g, b, a);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this.inner.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this.inner.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this.inner.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this.inner.setNormal(x, y, z);
        }
    }

    // ------------------------------------------------------ les etincelles

    /**
     * LES ETINCELLES qui tombent de l'arme -- posees PAR LE RENDU, donc a la
     * vraie place de la lame.
     *
     * Le serveur ne sait pas ou est la main (voir UpgradeAuraEvents) ; le rendu,
     * lui, tient la matrice exacte de l'objet. On prend un point au hasard le
     * long de la lame dans le repere de l'objet, on le passe par la pose, et
     * l'on retrouve le monde : a la troisieme personne la pose est relative a
     * la camera dans l'orientation du monde ; a la premiere elle est dans le
     * repere de la vue, et il faut lui rendre la rotation de la camera.
     *
     * Le nombre suit le cran (0,12 par cran et par tique : une toutes les huit
     * tiques au +1, presque une par tique au +7), la couleur suit le palier, et
     * au +8 chaque coup trace une trainee de six etincelles le long de la lame.
     */
    private static void sparks(LivingEntity holder, ItemStack stack, ItemDisplayContext context,
                               boolean leftHand, PoseStack pose, UpgradeGlow.Aura aura, int level) {
        Minecraft mc = Minecraft.getInstance();
        if (level <= 0 || mc.level == null || mc.isPaused()) {
            return;
        }
        // une fois par tique et par point de vue, pas une fois par image
        int key = holder.getId() * 2 + (context.firstPerson() ? 1 : 0);
        Integer last = sparkTick.get(key);
        if (last != null && last == holder.tickCount) {
            return;
        }
        sparkTick.put(key, holder.tickCount);

        java.util.Random random = new java.util.Random(holder.tickCount * 31L + key);
        int count = random.nextFloat() < SPARK_PER_LEVEL * level ? 1 : 0;
        boolean swinging = holder.swinging && holder.swingTime > 0 && holder.swingTime < 5;
        if (level >= 8 && swinging) {
            count += TRAIL_SPARKS;
        }
        if (count == 0) {
            return;
        }

        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        net.minecraft.world.phys.Vec3 eye = camera.getPosition();
        float[] tint = aura.tint(holder.tickCount);
        net.minecraft.core.particles.DustParticleOptions dust =
                new net.minecraft.core.particles.DustParticleOptions(
                        new org.joml.Vector3f(tint[0], tint[1], tint[2]), level >= 8 ? 1.3F : 0.9F);

        // A LA PREMIERE PERSONNE, la pose de la main est dans le repere de la
        // vue : on passe le point par la matrice de l'objet, puis par la
        // rotation de la camera, et l'on est dans le monde. Mesure a lacet 90 :
        // c'est la rotation directe qui pose le point devant et a droite.
        //
        // A LA TROISIEME PERSONNE, la pose de la couche est reecrite par les
        // mods d'animation et par le rendu du corps en vue subjective : le
        // point retombait a trois ou quatre metres du porteur, quelle que soit
        // la rotation essayee. On prend alors la main DANS LE REPERE DU CORPS
        // (yBodyRot), a hauteur de hanche, et la lame qui en part vers l'avant :
        // une approximation, mais qui suit le porteur ou qu'il regarde.
        org.joml.Matrix4f matrix = null;
        if (context.firstPerson()) {
            net.minecraft.client.resources.model.BakedModel model =
                    mc.getItemRenderer().getModel(stack, holder.level(), holder, 0);
            pose.pushPose();
            net.neoforged.neoforge.client.ClientHooks.handleCameraTransforms(pose, model, context, leftHand);
            pose.translate(-0.5F, -0.5F, -0.5F);
            matrix = new org.joml.Matrix4f(pose.last().pose());
            pose.popPose();
        }
        for (int i = 0; i < count; i++) {
            float along = random.nextFloat();
            double wx;
            double wy;
            double wz;
            if (matrix != null) {
                float y = NOTCH_FIRST + along * (1.0F - NOTCH_FIRST);
                org.joml.Vector4f p = new org.joml.Vector4f(0.5F, y, 0.5F, 1.0F).mul(matrix);
                org.joml.Vector3f at = new org.joml.Vector3f(p.x, p.y, p.z).rotate(camera.rotation());
                wx = eye.x + at.x;
                wy = eye.y + at.y - 0.12F;
                wz = eye.z + at.z;
            } else {
                double yaw = Math.toRadians(holder.yBodyRot);
                double side = leftHand ? -0.38 : 0.38;
                // droite du corps, puis un peu devant, la lame montant vers l'avant
                double fx = -Math.sin(yaw), fz = Math.cos(yaw);        // devant
                double rx = -fz, rz = fx;                              // a droite
                double forward = 0.25 + along * 0.55;
                wx = holder.getX() + rx * side + fx * forward;
                wy = holder.getY() + 0.85 + along * 0.35;
                wz = holder.getZ() + rz * side + fz * forward;
            }
            mc.level.addParticle(dust, wx, wy, wz,
                    (random.nextFloat() - 0.5F) * 0.02F, -0.02F - random.nextFloat() * 0.03F,
                    (random.nextFloat() - 0.5F) * 0.02F);
        }
    }

    /**
     * Rend l'objet agrandi AUTOUR DE SON CENTRE.
     *
     * C'est le coeur de la correction du bout de lame. Agrandir la pose AVANT
     * le rendu d'objet agrandit depuis l'origine de la main : un point de
     * l'objet a la distance d se retrouve a d x echelle, et le decalage croit
     * avec la distance. Sur une lame longue, la pointe -- le point le plus
     * eloigne de la main -- derivait d'un tiers de sa distance sur la couche la
     * plus large, et les trois couches s'y deployaient en eventail : c'est la
     * tache blanche qu'on voyait au bout de l'epee.
     *
     * On rejoue donc les trois etapes du rendu d'objet -- transformation
     * d'affichage, recentrage d'un demi-bloc, faces -- pour glisser l'echelle
     * ENTRE la deuxieme et la troisieme, la ou le cube unite de l'objet est
     * connu et son centre a (0,5, 0,5, 0,5). Ainsi chaque couche grossit sur
     * place, et la pointe reste une pointe.
     */
    private static void renderScaled(LivingEntity holder, ItemStack stack,
                                     ItemDisplayContext context, boolean leftHand,
                                     PoseStack pose, MultiBufferSource buffer, int light,
                                     float scale) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.renderer.entity.ItemRenderer renderer = mc.getItemRenderer();
        net.minecraft.client.resources.model.BakedModel model =
                renderer.getModel(stack, holder.level(), holder, 0);

        pose.pushPose();
        model = net.neoforged.neoforge.client.ClientHooks.handleCameraTransforms(
                pose, model, context, leftHand);
        pose.translate(-0.5F, -0.5F, -0.5F);
        // l'echelle, autour du centre du cube unite
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(scale, scale, scale);
        pose.translate(-0.5F, -0.5F, -0.5F);

        // Le type de rendu demande n'a pas d'importance : nos tampons rendent
        // tout dans leur propre type. On passe donc n'importe lequel.
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        for (net.minecraft.client.resources.model.BakedModel pass
                : model.getRenderPasses(stack, true)) {
            renderer.renderModelLists(pass, stack, light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, vc);
        }
        pose.popPose();
    }

    // -------------------------------------------------------- la vague

    /**
     * La VAGUE des raretes 7 et 8 : une bande de lumiere qui part de la garde
     * et remonte jusqu'a la pointe, sans fin, comme si la lame vibrait.
     *
     * C'est un effet de RANG et non de cran, dans la couleur du rang -- orange
     * pour le Legendaire, rouge pour le Phenomenal. Il se superpose au halo
     * sans le modifier : c'est une passe de plus, pas une retouche.
     *
     * LA BANDE SE CALCULE PAR FACE, d'apres la hauteur de chaque face dans le
     * modele de l'objet. Une arme tenue est dessinee garde en bas et pointe en
     * haut dans son propre repere ; la hauteur d'une face dit donc ou elle se
     * trouve le long de la lame, et c'est la seule information qu'il faut. Une
     * gaussienne centree sur la position courante de la bande allume chaque
     * face selon sa distance a celle-ci.
     *
     * La gaussienne est ASYMETRIQUE : plus large derriere la bande que devant.
     * C'est la traine, et c'est elle qui donne le sens du mouvement -- une
     * bande symetrique qui monte se lit aussi bien comme une bande qui descend.
     *
     * La vibration est un tremblement rapide de la force, bien plus vite que
     * la montee : la bande ne glisse pas, elle fremit en montant.
     */
    private static void renderWave(LivingEntity holder, ItemStack stack, ItemDisplayContext context,
                                   boolean leftHand, PoseStack pose, MultiBufferSource buffer,
                                   int light, int rank, float time) {
        int tier = Math.min(1, rank - WAVE_FROM);           // 0 = Legendaire, 1 = Phenomenal
        int colour = com.emerald.item.GearRarity.values()[rank].colour();
        float band = (time * WAVE_SPEED) % 1.0F;
        float tremor = 0.72F + 0.28F * Mth.sin(time * 0.95F);
        float strength = WAVE_STRENGTH[tier] * tremor;

        renderScaled(holder, stack, context, leftHand, pose,
                new WaveSource(buffer, colour, band, WAVE_WIDTH[tier], strength), light,
                WAVE_SCALE);
    }

    private record WaveSource(MultiBufferSource inner, int colour, float band,
                              float width, float strength) implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType ignored) {
            // EN FONDU ALPHA, PAS EN ADDITIF -- et c'est ce qui la rend visible.
            // Le halo +10 sature la lame en blanc pur ; une vague additive y
            // ajoute de l'orange a du blanc, ce qui donne du blanc, et l'on ne
            // voit rien. Le fondu alpha RECOUVRE au lieu d'ajouter : la bande
            // passe par-dessus n'importe quel halo, quel que soit son cran.
            return new Waved(this.inner.getBuffer(
                    RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS)),
                    this.colour, this.band, this.width, this.strength);
        }
    }

    /**
     * Le consommateur de la vague : chaque face recoit la couleur du rang,
     * dosee par sa distance a la bande.
     *
     * La force passe par l'ALPHA, en fondu classique : la vague recouvre la
     * lame au lieu de s'y ajouter. C'est ce qui lui permet de rester visible
     * sur un halo +10, qui sature la lame en blanc -- de l'orange ajoute a du
     * blanc ne se voit pas, de l'orange pose dessus, si. Le halo, lui, reste
     * tel que le joueur l'a valide.
     */
    private record Waved(VertexConsumer inner, int colour, float band, float width, float strength)
            implements VertexConsumer {

        /** Huit entiers par sommet dans une face cuite ; la hauteur est le deuxieme. */
        private static final int STRIDE = 8;

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float r, float g, float b,
                                float a, int light, int overlay, boolean readAlpha) {
            int[] v = quad.getVertices();
            float y = 0.0F;
            for (int i = 0; i < 4; i++) {
                y += Float.intBitsToFloat(v[i * STRIDE + 1]);
            }
            y *= 0.25F;

            float d = y - this.band;
            // derriere la bande (d < 0), la traine est deux fois plus large
            float sigma = d < 0.0F ? this.width * 2.2F : this.width;
            float w = (float) Math.exp(-(d * d) / (2.0F * sigma * sigma)) * this.strength;

            // En fondu alpha, c'est l'ALPHA qui porte la force : la couleur
            // reste pleine, et la bande recouvre d'autant plus qu'elle est
            // proche. (En additif, c'etait l'inverse ; voir le halo.)
            float cr = ((this.colour >> 16) & 0xFF) / 255.0F;
            float cg = ((this.colour >> 8) & 0xFF) / 255.0F;
            float cb = (this.colour & 0xFF) / 255.0F;
            this.inner.putBulkData(pose, quad, cr, cg, cb, w, light, overlay, readAlpha);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this.inner.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.inner.setColor(r, g, b, a);
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this.inner.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this.inner.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this.inner.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this.inner.setNormal(x, y, z);
        }
    }

    // --------------------------------------------- le tampon de lumiere

    /**
     * Un tampon qui rend tout ce qu'on lui donne en lumiere teintee.
     *
     * IL A LONGTEMPS UTILISE {@code RenderType.eyes}, ET C'ETAIT DEUX FOIS FAUX.
     *
     * D'abord, {@code eyes} melange en ADDITIF PUR -- blendFunc(ONE, ONE) --
     * et son shader ne multiplie jamais la couleur par l'alpha. L'intensite du
     * halo ne voyage QUE dans l'alpha des sommets : les trois valeurs qui la
     * dosent, 0,45, 0,22 et 0,18, n'avaient donc aucun effet, et les trois
     * couches s'ajoutaient a pleine force par-dessus l'arme.
     *
     * Ensuite, et c'est ce qui faisait le plan geant : ce shader ne rejette
     * aucun fragment transparent. Or le modele d'un objet tenu n'est pas la
     * silhouette de l'arme -- c'est une BOITE dont les faces avant et arriere
     * sont des carres pleins de seize sur seize, que seule la transparence de
     * la texture decoupe. Sans rejet, le carre entier etait peint dans la
     * couleur du cran ; a la premiere personne, agrandi d'une fois et demie, il
     * couvre la hauteur de l'ecran. C'est le rectangle colore signale.
     *
     * On passe donc au type qu'utilisent deja la vague et les encoches, qui
     * n'ont jamais pose de probleme : il respecte l'alpha, et il rejette les
     * fragments sous un dixieme d'opacite, ce qui redonne au halo la forme de
     * l'arme. Le halo y perd un peu de son eclat additif ; il y gagne d'exister.
     */
    private record GlowSource(MultiBufferSource inner, float[] tint, float alpha)
            implements MultiBufferSource {

        @Override
        public VertexConsumer getBuffer(RenderType ignored) {
            return new Tinted(this.inner.getBuffer(
                    RenderType.entityTranslucentEmissive(TextureAtlas.LOCATION_BLOCKS)),
                    this.tint, this.alpha);
        }
    }

    /**
     * Le consommateur qui impose la teinte.
     *
     * Le rendu d'objet passe par UN SEUL point pour ecrire ses faces, putBulkData
     * avec une couleur : c'est la qu'on substitue la notre. Tout le reste est
     * delegue tel quel.
     */
    private record Tinted(VertexConsumer inner, float[] tint, float alpha)
            implements VertexConsumer {

        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float r, float g, float b,
                                float a, int light, int overlay, boolean readAlpha) {
            this.inner.putBulkData(pose, quad, this.tint[0], this.tint[1],
                    this.tint[2], this.alpha, light, overlay, readAlpha);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this.inner.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.inner.setColor((int) (this.tint[0] * 255), (int) (this.tint[1] * 255),
                    (int) (this.tint[2] * 255), (int) (this.alpha * 255));
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this.inner.setUv(u, v);
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this.inner.setUv1(u, v);
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this.inner.setUv2(u, v);
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this.inner.setNormal(x, y, z);
        }
    }
}
