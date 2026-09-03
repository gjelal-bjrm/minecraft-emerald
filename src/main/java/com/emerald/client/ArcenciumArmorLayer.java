package com.emerald.client;

import com.emerald.item.ModArmorMaterials;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Fait pulser les fissures de l'armure d'Arcencium sur le joueur.
 *
 * POURQUOI un calque maison. Les textures d'armure portee ne passent pas par
 * un atlas : le systeme d'animation .mcmeta, qui anime nos icones d'inventaire,
 * n'a aucun effet sur elles. On dessine donc une seconde fois le modele
 * d'armure, avec pour seule texture le reseau de fissures sur fond transparent,
 * en choisissant l'image du tick courant. Douze images pre-decalees en teinte
 * font defiler l'arc-en-ciel le long du corps.
 *
 * Le rendu passe par RenderType.eyes : additif et insensible a la lumiere,
 * c'est ce qui fait que les fissures brillent vraiment dans le noir au lieu
 * d'etre une simple decalcomanie coloree.
 */
public class ArcenciumArmorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    /** Doit rester egal au NFRAMES de tools/armor_textures.py. */
    private static final int FRAMES = 12;

    /** Ticks par image : meme cadence que l'animation des icones. */
    private static final int TICKS_PER_FRAME = 3;

    private static final ResourceLocation[][] CRACKS = new ResourceLocation[2][FRAMES];
    /** La gravure : les jointures des plaques, blanches sur fond transparent (tools/armor_engraving.py). */
    private static final ResourceLocation[] ENGRAVING = {
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                    "textures/models/armor/arcencium_engraving_1.png"),
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                    "textures/models/armor/arcencium_engraving_2.png")};

    /** La place de chaque piece sur le corps, pour l'onde de la gravure. */
    private static float heightOf(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 0.0F;
            case LEGS -> 0.3F;
            case CHEST -> 0.65F;
            default -> 1.0F;
        };
    }

    static {
        for (int layer = 1; layer <= 2; layer++) {
            for (int f = 0; f < FRAMES; f++) {
                CRACKS[layer - 1][f] = ResourceLocation.fromNamespaceAndPath(
                        EmeraldWeaponsMod.MODID,
                        String.format("textures/models/armor/arcencium_cracks_%d_%02d.png", layer, f));
            }
        }
    }

    private final HumanoidModel<AbstractClientPlayer> innerModel;
    private final HumanoidModel<AbstractClientPlayer> outerModel;

    public ArcenciumArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                               HumanoidModel<AbstractClientPlayer> innerModel,
                               HumanoidModel<AbstractClientPlayer> outerModel) {
        super(parent);
        this.innerModel = innerModel;
        this.outerModel = outerModel;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        long time = player.level().getGameTime();
        int frame = (int) ((time / TICKS_PER_FRAME) % FRAMES);

        renderSlot(pose, buffer, player, EquipmentSlot.FEET, frame);
        renderSlot(pose, buffer, player, EquipmentSlot.LEGS, frame);
        renderSlot(pose, buffer, player, EquipmentSlot.CHEST, frame);
        renderSlot(pose, buffer, player, EquipmentSlot.HEAD, frame);
    }

    private void renderSlot(PoseStack pose, MultiBufferSource buffer,
                            AbstractClientPlayer player, EquipmentSlot slot, int frame) {
        ItemStack stack = player.getItemBySlot(slot);
        if (!(stack.getItem() instanceof ArmorItem armor)
                || !armor.getMaterial().equals(ModArmorMaterials.ARCENCIUM)
                || armor.getEquipmentSlot() != slot) {
            return;
        }
        boolean inner = slot == EquipmentSlot.LEGS;
        HumanoidModel<AbstractClientPlayer> model = inner ? this.innerModel : this.outerModel;

        this.getParentModel().copyPropertiesTo(model);
        setPartVisibility(model, slot);

        ResourceLocation texture = CRACKS[inner ? 1 : 0][frame];
        VertexConsumer vc = buffer.getBuffer(RenderType.eyes(texture));
        model.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY);
        renderEngraving(pose, buffer, player, stack, slot, model, inner);
    }

    /**
     * LA GRAVURE S'ALLUME AVEC LE CRAN.
     *
     * L'armure d'Arcencium est la notre : elle merite mieux qu'un contour.
     * Ses jointures de plaques, relevees dans sa propre texture, s'eclairent
     * de la couleur du cran -- faiblement en blanc de +1 a +4, franchement en
     * couleur de +5 a +7, pleinement et au passage de l'onde a partir de +8.
     * Une amelioration AJOUTE : les lignes sont posees sur la piece, la piece
     * reste dessous.
     */
    private void renderEngraving(PoseStack pose, MultiBufferSource buffer, AbstractClientPlayer player,
                                 ItemStack stack, EquipmentSlot slot,
                                 HumanoidModel<AbstractClientPlayer> model, boolean inner) {
        com.emerald.item.UpgradeGlow.Aura aura = com.emerald.item.UpgradeGlow.of(stack);
        if (aura.intensity() <= 0.0F) {
            return;
        }
        float time = player.tickCount + net.minecraft.client.Minecraft.getInstance()
                .getTimer().getGameTimeDeltaPartialTick(true);
        float[] tint = aura.tint(time);
        float alpha = 0.30F + 0.65F * aura.intensity();
        if (aura.large()) {
            alpha = Math.min(1.0F, alpha * (0.7F + 0.6F
                    * com.emerald.item.UpgradeGlow.pulse(heightOf(slot), time)));
        }
        int colour = net.minecraft.util.FastColor.ARGB32.color((int) (alpha * 255),
                (int) (tint[0] * 255), (int) (tint[1] * 255), (int) (tint[2] * 255));
        VertexConsumer vc = buffer.getBuffer(
                RenderType.entityTranslucentEmissive(ENGRAVING[inner ? 1 : 0]));
        model.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, colour);
    }

    /** Meme decoupage que HumanoidArmorLayer : chaque piece n'eclaire que ses parties. */
    private static void setPartVisibility(HumanoidModel<AbstractClientPlayer> model, EquipmentSlot slot) {
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
