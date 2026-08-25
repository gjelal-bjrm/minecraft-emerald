package com.emerald.client;

import com.emerald.block.entity.OathBladeBlockEntity;
import com.emerald.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dessine la Lame du Serment plantee dans le sol.
 *
 * On rend l'OBJET lui-meme, avec sa texture animee, plutot qu'un modele de bloc
 * imite : c'est la seule facon d'avoir exactement la lame que le joueur
 * emportera, et elle continue de scintiller comme dans la main.
 *
 * La rotation de 45 degres sur Z redresse l'epee : le modele « handheld » la
 * pose en diagonale, du coin bas-gauche au coin haut-droit. Sans cette
 * correction, elle serait plantee de travers.
 */
public class OathBladeRenderer implements BlockEntityRenderer<OathBladeBlockEntity> {

    /** Hauteur du faisceau, en blocs. De quoi le voir depuis n'importe ou dans le village. */
    private static final int BEAM_HEIGHT = 320;

    /** Au-dela, le faisceau cesserait d'etre dessine : il doit porter tres loin. */
    private static final int VIEW_DISTANCE = 512;

    private final ItemStack blade = new ItemStack(ModItems.OATH_BLADE.get());

    public OathBladeRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Le faisceau doit rester visible meme quand le bloc sort du champ, sinon il
     * disparaitrait des qu'on regarde ailleurs qu'a ses pieds.
     */
    @Override
    public boolean shouldRenderOffScreen(OathBladeBlockEntity entity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public void render(OathBladeBlockEntity entity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        renderBeam(entity, partialTick, pose, buffer);
        pose.pushPose();
        // la garde domine le socle, la pointe s'enfonce dedans : c'est ce
        // chevauchement qui fait lire « plantee » plutot que « posee »
        pose.translate(0.5, 1.05, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(35.0F));
        // 135 et non -45 : a -45 la lame se dresse bien, mais pointe en l'air.
        // Un demi-tour de plus retourne la garde en haut et la pointe en terre.
        pose.mulPose(Axis.ZP.rotationDegrees(135.0F));
        pose.mulPose(Axis.XP.rotationDegrees(4.0F));
        pose.scale(1.7F, 1.7F, 1.7F);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                this.blade, ItemDisplayContext.FIXED, 0xF000F0, packedOverlay,
                pose, buffer, entity.getLevel(), 0);
        pose.popPose();
    }

    /**
     * Le faisceau qui signale la lame.
     *
     * On reutilise le rendu de la balise vanilla : c'est le seul element du jeu
     * qui porte a plusieurs centaines de blocs, la ou les particules s'arretent
     * a trente. Sans lui, la lame reste introuvable des qu'on s'ecarte de la
     * place -- et le declencheur de la partie ne doit jamais se chercher.
     *
     * Sa teinte suit le cycle prismatique, comme les fissures de l'armure.
     */
    private void renderBeam(OathBladeBlockEntity entity, float partialTick,
                            PoseStack pose, MultiBufferSource buffer) {
        if (entity.getLevel() == null) {
            return;
        }
        long time = entity.getLevel().getGameTime();
        int color = java.awt.Color.HSBtoRGB((time % 200L) / 200.0F, 0.55F, 1.0F) & 0xFFFFFF;
        BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION,
                partialTick, 1.0F, time, 0, BEAM_HEIGHT, color, 0.16F, 0.24F);
    }
}
