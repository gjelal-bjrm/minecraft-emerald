package com.emerald.client;

import com.emerald.item.UpgradeGlow;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Le LISERE de l'armure amelioree : un trait de lumiere sur la silhouette.
 *
 * DEUX VERSIONS ONT ECHOUE AVANT CELLE-CI. La premiere posait une lueur
 * additive sur la piece : du blanc a moitie effacait le dessin. La seconde
 * gonflait une coque translucide autour du corps qui « respirait » : en 3D,
 * cela se lisait comme du cellophane colore, et le joueur l'a dit -- pas beau
 * du tout. Une aura de sprite 2D, celle de NosTale, ne se traduit pas en
 * maillage gonfle.
 *
 * Ici on ne dessine que le CONTOUR (voir {@link ModRenderTypes#rim}) : la
 * coque n'expose que ses faces arriere, et il n'en survit qu'un trait au bord
 * de la silhouette. La piece reste elle-meme ; le trait est a cote.
 *
 * L'echelle se lit en deux questions. L'EPAISSEUR d'abord -- trois coques,
 * fine, moyenne, large -- puis la COULEUR. Au second tour (+8 et plus), deux
 * traits : un fin et net contre la piece, un large et doux au-dela ; et la
 * PULSATION, une onde qui monte des pieds a la tete, comme la vague de la
 * lame. Le +10 tourne dans toutes les teintes.
 */
public class UpgradeArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {

    /** Une texture blanche, pleine : la couleur et l'opacite viennent du sommet. */
    private static final ResourceLocation SHELL = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/models/armor/upgrade_glow_1.png");

    /** Opacite du trait net a pleine intensite ; le voile large est plus discret. */
    private static final float LINE_ALPHA = 0.85F;
    private static final float VEIL_ALPHA = 0.30F;

    /** La place de chaque piece sur le corps, de zero (pieds) a un (tete) : pour l'onde. */
    private static final float[] HEIGHT = {0.0F, 0.3F, 0.65F, 1.0F};
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    /** Les trois coques : fine, moyenne, large. */
    private final HumanoidModel<T>[] shells;

    public UpgradeArmorLayer(RenderLayerParent<T, M> parent, HumanoidModel<T>[] shells) {
        super(parent);
        this.shells = shells;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        float time = entity.tickCount + partialTick;
        for (int i = 0; i < SLOTS.length; i++) {
            renderSlot(pose, buffer, entity, SLOTS[i], HEIGHT[i], time);
        }
    }

    private void renderSlot(PoseStack pose, MultiBufferSource buffer, T entity,
                            EquipmentSlot slot, float height, float time) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) {
            return;
        }
        UpgradeGlow.Aura aura = UpgradeGlow.of(stack);
        if (aura.intensity() <= 0.0F || aura.width() <= 0) {
            return;
        }
        float[] tint = aura.tint(time);
        // la pulsation n'existe qu'au second tour : elle ajoute a l'opacite au passage de l'onde
        float pulse = aura.large() ? UpgradeGlow.pulse(height, time) : 0.0F;

        if (aura.large()) {
            // le voile large et doux, derriere le trait
            drawShell(pose, buffer, entity, slot, this.shells[2], tint,
                    VEIL_ALPHA * aura.intensity() * (1.0F + 0.8F * pulse));
            // le trait net, sur la coque moyenne
            drawShell(pose, buffer, entity, slot, this.shells[1], tint,
                    LINE_ALPHA * aura.intensity() * (0.85F + 0.15F * pulse));
        } else {
            drawShell(pose, buffer, entity, slot, this.shells[aura.width() - 1], tint,
                    LINE_ALPHA * aura.intensity());
        }
    }

    private void drawShell(PoseStack pose, MultiBufferSource buffer, T entity, EquipmentSlot slot,
                           HumanoidModel<T> model, float[] tint, float alpha) {
        this.getParentModel().copyPropertiesTo(model);
        setPartVisibility(model, slot);
        int colour = FastColor.ARGB32.color((int) (Math.min(1.0F, alpha) * 255),
                (int) (tint[0] * 255), (int) (tint[1] * 255), (int) (tint[2] * 255));
        // les faces retournees : seules les faces arriere de la coque survivent,
        // et il n'en reste qu'un trait au bord de la silhouette
        VertexConsumer vc = ModRenderTypes.flipped(buffer.getBuffer(ModRenderTypes.rim(SHELL)));
        model.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, colour);
        ModRenderTypes.finish(vc);
    }

    /** Meme decoupage que HumanoidArmorLayer : chaque piece n'eclaire que ses parties. */
    static <T extends LivingEntity> void setPartVisibility(HumanoidModel<T> model,
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
