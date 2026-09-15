package com.emerald.jak.gun;

import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Comment l'arme se tient : la matrice qui va du repere de l'objet (celui que
 * recoit BlockEntityWithoutLevelRenderer.renderByItem, apres les reglages
 * « display » de models/item/morph_gun.json) au repere du modele de Jak.
 *
 * CODE COMMUN, SANS CLASSE CLIENTE : le banc d'essai du serveur rejoue cette
 * matrice, avec les formules vanilla de la pose d'arbalete et du calque de
 * main, pour ecrire les sommets poses que tools/.. dessine hors ligne.
 *
 * LE PARTAGE DES ROLES. L'ECHELLE vit dans morph_gun.json : 1/0,9375 en
 * troisieme personne (le modele du joueur est dessine a 0,9375, PlayerRenderer.scale :
 * sans compenser, 1 m de Jak ferait 0,94 bloc), 0,65 en premiere personne
 * (cadrage reduit choisi par le joueur sur premiere-personne.png). L'ORIENTATION
 * et la PRISE vivent ici, parce qu'elles dependent de la famille : le point
 * « main droite » de la famille (GunForm.rightHand) est pose sur la main, canon
 * le long du regard.
 *
 * TROISIEME PERSONNE. Le calque de main (ItemInHandLayer.renderArmWithItem)
 * tourne de X -90 puis Y 180 au bout du bras. Pour un bras leve droit devant, ce
 * repere a +x a droite du joueur, +y en haut, +z vers l'arriere ; le modele de
 * Jak a +x a SA gauche et le canon en +z : une rotation de 180 autour de Y.
 * Mais la pose d'arbalete (AnimationUtils.animateCrossbowHold) ne leve pas le
 * bras droit devant : lacet -0,3 rad vers le corps, tangage -PI/2 + 0,1, plus
 * le balancement moyen de bobModelPart (roulis +0,05). On compense cet ecart,
 * constant par rapport au regard : C = L^-1 x Bras^-1 x Ideal x L, avec
 * L = X(-90) Y(180), Bras = ZYX(0,05 ; -0,3 ; -PI/2 + 0,1), Ideal = X(-PI/2).
 * L'arme suit alors le regard (le bras suit head.xRot et head.yRot) ; l'ecart
 * restant selon le tangage est mesure par le banc. En main gauche (inventaire
 * plein), la pose est ITEM : bras a -PI/10, meme compensation.
 *
 * PREMIERE PERSONNE. Le point « main droite » est pose en (0,56 ; -0,52 - 0,6 x
 * equipement ; -0,72), le point d'ItemInHandRenderer.applyItemArmTransform, par
 * applyForgeHandTransform (MorphGunClient) ; ici seulement Y 180 : canon droit
 * devant, comme sur la planche que le joueur a validee.
 *
 * INVENTAIRE ET AUTRES. L'arme entiere tient dans la case : trois quarts (Y 90
 * canon a droite, X 25 pour voir le dessus, Z 35 canon vers le haut), centree et
 * mise a l'echelle sur SES SOMMETS VISIBLES projetes, qui remplissent 0,92 de la
 * case. (Une premiere version cadrait la boite de la forme : ses coins projetes
 * debordent l'arme, et l'icone ne remplissait que 0,52 a 0,76 de la case -- mesure
 * du banc d'essai.) Le cadrage se calcule une fois par forme (MorphGunItemRenderer).
 */
public final class GunHold {

    /** Echelle du display en troisieme personne (morph_gun.json) : 1 / 0,9375. */
    public static final float THIRD_PERSON_SCALE = 1.0F / 0.9375F;
    /** Echelle du display en premiere personne (morph_gun.json). */
    public static final float FIRST_PERSON_SCALE = 0.65F;
    /** Le point de la main en premiere personne (ItemInHandRenderer.applyItemArmTransform). */
    public static final float FIRST_X = 0.56F;
    public static final float FIRST_Y = -0.52F;
    public static final float FIRST_Z = -0.72F;
    public static final float FIRST_EQUIP_DROP = -0.6F;

    /** Balancement moyen des bras (AnimationUtils.bobModelPart : cos x 0,05 + 0,05 sur le roulis). */
    public static final float BOB_ROLL = 0.05F;
    /** La pose d'arbalete du bras droit, sans regard (AnimationUtils.animateCrossbowHold). */
    public static final float CROSSBOW_YAW = -0.3F;
    public static final float CROSSBOW_PITCH = (float) (-Math.PI / 2.0) + 0.1F;
    /** La pose ITEM du bras gauche au repos (HumanoidModel.poseLeftArm). */
    public static final float ITEM_PITCH = (float) (-Math.PI / 10.0);

    public static final float GUI_FILL = 0.92F;
    public static final float GUI_YAW = (float) Math.toRadians(90.0);
    public static final float GUI_TILT = (float) Math.toRadians(25.0);
    public static final float GUI_ROLL = (float) Math.toRadians(35.0);

    private static final float PI = (float) Math.PI;
    private static final Matrix4f RIGHT_CROSSBOW = compensation(BOB_ROLL, CROSSBOW_YAW, CROSSBOW_PITCH);
    private static final Matrix4f LEFT_ITEM = compensation(-BOB_ROLL, 0.0F, ITEM_PITCH);

    private GunHold() {
    }

    /** C = L^-1 x Bras^-1 x Ideal x L (voir l'en-tete). */
    static Matrix4f compensation(float roll, float yaw, float pitch) {
        Matrix4f layer = new Matrix4f().rotateX(-PI / 2.0F).rotateY(PI);
        Matrix4f arm = new Matrix4f().rotation(new Quaternionf().rotationZYX(roll, yaw, pitch));
        Matrix4f ideal = new Matrix4f().rotationX(-PI / 2.0F);
        return new Matrix4f(layer).invert().mul(arm.invert()).mul(ideal).mul(layer);
    }

    /**
     * La matrice objet -> arme pour un contexte d'affichage.
     *
     * @param grip la forme dont la prise compte (la forme cible pendant une transformation)
     * @param fit  le cadrage d'inventaire de cette forme ({@link #fit})
     */
    public static Matrix4f hold(ItemDisplayContext context, GunForm grip, Matrix4f fit, Matrix4f out) {
        out.identity();
        switch (context) {
            case THIRD_PERSON_RIGHT_HAND -> out.mul(RIGHT_CROSSBOW);
            case THIRD_PERSON_LEFT_HAND -> out.mul(LEFT_ITEM);
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
            }
            default -> {
                return out.set(fit);
            }
        }
        double[] hand = grip.rightHand();
        return out.rotateY(PI).translate((float) -hand[0], (float) -hand[1], (float) -hand[2]);
    }

    /**
     * Le cadrage d'inventaire d'une arme posee : trois quarts, centre et echelle
     * pris sur ses sommets visibles projetes (voir l'en-tete).
     */
    public static Matrix4f fit(GunPose pose, Matrix4f out) {
        Matrix4f turn = new Matrix4f().rotateZ(GUI_ROLL).rotateX(GUI_TILT).rotateY(GUI_YAW);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        double[] p = new double[3];
        Vector3f v = new Vector3f();
        for (int t = 0; t < pose.model.triangles; t++) {
            if (!pose.visible(t)) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                pose.vertex(t * 3 + k, p);
                turn.transformPosition(v.set((float) p[0], (float) p[1], (float) p[2]));
                minX = Math.min(minX, v.x);
                maxX = Math.max(maxX, v.x);
                minY = Math.min(minY, v.y);
                maxY = Math.max(maxY, v.y);
                minZ = Math.min(minZ, v.z);
                maxZ = Math.max(maxZ, v.z);
            }
        }
        float extent = Math.max(maxX - minX, maxY - minY);
        if (!(extent > 1.0e-6F)) {
            return out.identity();
        }
        float k = GUI_FILL / extent;
        return out.identity().scale(k)
                .translate(-(minX + maxX) * 0.5F, -(minY + maxY) * 0.5F, -(minZ + maxZ) * 0.5F).mul(turn);
    }
}
