package com.emerald.client;

import com.emerald.block.entity.OathBladeBlockEntity;
import com.emerald.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
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
        pose.pushPose();
        // la garde domine le socle, la pointe s'enfonce dedans : c'est ce
        // chevauchement qui fait lire « plantee » plutot que « posee »
        // dans les limites du bloc : le rayon d'interaction ne teste la forme
        // d'un bloc que lorsqu'il traverse SON cube. Une lame dessinee plus haut
        // se visait dans le vide et ne repondait plus au clic.
        pose.translate(0.5, 0.80, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(35.0F));
        // 135 et non -45 : a -45 la lame se dresse bien, mais pointe en l'air.
        // Un demi-tour de plus retourne la garde en haut et la pointe en terre.
        pose.mulPose(Axis.ZP.rotationDegrees(135.0F));
        pose.mulPose(Axis.XP.rotationDegrees(4.0F));
        pose.scale(1.45F, 1.45F, 1.45F);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                this.blade, ItemDisplayContext.FIXED, 0xF000F0, packedOverlay,
                pose, buffer, entity.getLevel(), 0);
        pose.popPose();
    }
}
