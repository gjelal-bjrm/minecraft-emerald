package com.emerald.client;

import com.emerald.item.UpgradeGlow;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * L'aura de l'ARMURE amelioree : une coque AUTOUR du corps, pas une lueur dessus.
 *
 * LA PREMIERE VERSION EFFACAIT L'ARMURE. Elle posait une lueur additive sur la
 * piece, a cinquante-cinq pour cent ; du blanc additif a cette force blanchit
 * tout ce qu'il recouvre, et un plastron +10 devenait un bloc blanc. Le joueur
 * a dit la regle qui manquait : une amelioration AJOUTE quelque chose, elle ne
 * remplace jamais l'apparence de base.
 *
 * D'ou la coque : un maillage gonfle bien au-dela de l'armure, dessine tres
 * translucide. Vu de FRONT, sa face ne teinte l'armure que d'un cinquieme --
 * la piece reste elle-meme, juste rechauffee de sa couleur. Vu A LA
 * SILHOUETTE, on voit ses flancs, qui sont hors du corps : la, contre le decor,
 * la couleur fait un lisere. C'est ce lisere qu'on lit comme une aura, et il
 * ne couvre rien puisqu'il est a cote.
 *
 * Elle RESPIRE, et chaque piece a son propre rythme -- casque, plastron,
 * jambieres et bottes ne battent jamais ensemble. Quatre pieces a l'unisson
 * font un clignotement ; quatre rythmes decales font une lumiere qui court sur
 * le corps.
 */
public class UpgradeArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {

    /** Une texture blanche, pleine : la couleur et l'opacite viennent du sommet. */
    private static final ResourceLocation SHELL = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/models/armor/upgrade_glow_1.png");

    /**
     * Ce qu'une piece de +10 atteint au sommet de sa respiration.
     *
     * Un peu plus d'un quart -- et ce chiffre AGIT, ce qui n'etait pas le cas
     * avant : sans premultiplication, il aurait pu valoir un centieme sans que
     * la piece cesse d'etre blanche. C'est le seuil en dessous duquel l'armure
     * reste lisible sous la coque ; au-dessus, le blanc du +10 recommence a
     * manger le dessin.
     */
    private static final float PEAK = 0.28F;

    private final HumanoidModel<T> shell;

    public UpgradeArmorLayer(RenderLayerParent<T, M> parent, HumanoidModel<T> shell) {
        super(parent);
        this.shell = shell;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        float time = entity.tickCount + partialTick;
        renderSlot(pose, buffer, entity, EquipmentSlot.FEET, time, 0.0F);
        renderSlot(pose, buffer, entity, EquipmentSlot.LEGS, time, 1.6F);
        renderSlot(pose, buffer, entity, EquipmentSlot.CHEST, time, 3.2F);
        renderSlot(pose, buffer, entity, EquipmentSlot.HEAD, time, 4.8F);
    }

    private void renderSlot(PoseStack pose, MultiBufferSource buffer, T entity,
                            EquipmentSlot slot, float time, float phase) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) {
            return;
        }
        UpgradeGlow.Aura aura = UpgradeGlow.of(stack);
        if (aura.intensity() <= 0.0F) {
            return;
        }
        HumanoidModel<T> model = this.shell;
        this.getParentModel().copyPropertiesTo(model);
        setPartVisibility(model, slot);

        // La respiration : lente, decalee par piece, plus ample sur une aura
        // large -- un +8 ne se contente pas d'etre plus fort qu'un +5, il bat
        // plus fort.
        float depth = aura.large() ? 0.30F : 0.18F;
        float breath = (1.0F - depth) + depth * Mth.sin(time * 0.09F + phase);
        float alpha = PEAK * aura.intensity() * breath;

        // PREMULTIPLIE, et c'est toute la correction. Le type de rendu des yeux
        // melange en additif ONE/ONE : l'alpha n'entre pas dans l'equation,
        // image = image + couleur. Passer un alpha faible ne dimmait donc rien
        // -- la coque ajoutait l'aura PLEINE, et du blanc plein sur n'importe
        // quoi donne du blanc. On attenue la couleur elle-meme, et l'alpha
        // reste a fond puisqu'il n'a aucun effet.
        int colour = FastColor.ARGB32.color(255,
                (int) (aura.red() * alpha * 255), (int) (aura.green() * alpha * 255),
                (int) (aura.blue() * alpha * 255));
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(SHELL));
        model.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, colour);
    }

    /** Meme decoupage que HumanoidArmorLayer : chaque piece n'eclaire que ses parties. */
    private static <T extends LivingEntity> void setPartVisibility(HumanoidModel<T> model,
                                                                   EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }
}
