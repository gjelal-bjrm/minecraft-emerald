package com.emerald.client;

import com.emerald.item.UpgradeGlow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Le halo de l'arme, vu par les AUTRES.
 *
 * Meme routine que la premiere personne, autre chemin : ici on est un calque
 * de rendu greffe sur le modele du porteur, et l'on se place dans sa main par
 * les memes transformations que le calque vanilla de l'objet tenu. Une aura
 * de +10 est faite pour etre vue par l'equipe -- et par le joueur qui croise
 * un zombie dont l'epee brille en rouge.
 *
 * Le calque est generique sur le modele : il se greffe sur les joueurs comme
 * sur toute creature humanoide armee, puisque le bestiaire recoit lui aussi
 * des armes ameliorees.
 */
public class UpgradeHandLayer<T extends LivingEntity, M extends EntityModel<T> & ArmedModel>
        extends RenderLayer<T, M> {

    public UpgradeHandLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack main = entity.getMainHandItem();
        if (!UpgradeHaloRenderer.shows(main)) {
            return;
        }
        HumanoidArm arm = entity.getMainArm();
        renderArm(pose, buffer, light, entity, main, arm);
    }

    /**
     * Les transformations exactes de ItemInHandLayer : on se place dans la
     * main, on retourne l'objet dans le repere du bras, et on le decale d'un
     * seizieme de bloc vers l'exterieur.
     */
    private void renderArm(PoseStack pose, MultiBufferSource buffer, int light, T entity,
                           ItemStack stack, HumanoidArm arm) {
        pose.pushPose();
        this.getParentModel().translateToHand(arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        boolean left = arm == HumanoidArm.LEFT;
        pose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
        // Better Combat et playerAnimator deplacent l'objet APRES ces
        // transformations, par l'os « rightItem » de l'animation : sans le
        // rejouer, la lame etait en travers de la poitrine et son halo a la hanche.
        com.emerald.client.compat.AnimatedHand.apply(entity, arm, pose);
        UpgradeHaloRenderer.renderHalo(entity, stack,
                left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                left, pose, buffer, light);
        pose.popPose();
    }
}
