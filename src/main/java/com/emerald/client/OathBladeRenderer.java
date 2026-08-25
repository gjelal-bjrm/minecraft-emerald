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

    private final ItemStack blade = new ItemStack(ModItems.OATH_BLADE.get());

    public OathBladeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OathBladeBlockEntity entity, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        pose.pushPose();
        // la garde repose un peu au-dessus du sol, la lame s'enfonce dedans
        pose.translate(0.5, 0.62, 0.5);
        // une inclinaison legere, pour qu'elle paraisse fichee et non posee
        pose.mulPose(Axis.YP.rotationDegrees(35.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        pose.mulPose(Axis.XP.rotationDegrees(6.0F));
        pose.scale(1.55F, 1.55F, 1.55F);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                this.blade, ItemDisplayContext.FIXED, 0xF000F0, packedOverlay,
                pose, buffer, entity.getLevel(), 0);
        pose.popPose();
    }
}
