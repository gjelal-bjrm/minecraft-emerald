package com.emerald.jak.board;

import com.emerald.jak.gun.JakGunModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Le JET-Board en objet -- dans sa case, dans la main, au sol : le modele de Jak 3, centre et mis
 * a la taille de la case selon son plus long cote (la planche fait 2,5 m). L'orientation est dans
 * le modele d'objet (models/item/jet_board.json).
 */
public final class JetBoardItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** Le plus long cote de la planche, dans la case : un peu moins d'un bloc. */
    private static final float LENGTH = 0.95F;

    public JetBoardItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                             int light, int overlay) {
        JakGunModel model = JakGunModel.get(JetBoardRenderer.MODEL);
        if (model == null) {
            return;
        }
        float longest = Math.max(model.maxX - model.minX, Math.max(model.maxY - model.minY, model.maxZ - model.minZ));
        float scale = longest > 0.0F ? LENGTH / longest : 1.0F;
        pose.pushPose();
        // ItemRenderer vient de reculer d'un demi-bloc apres le display : on se remet au centre
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(scale, scale, scale);
        pose.translate(-(model.minX + model.maxX) / 2.0F, -(model.minY + model.maxY) / 2.0F,
                -(model.minZ + model.maxZ) / 2.0F);
        JetBoardRenderer.emit(model, pose.last(), buffers, light, overlay);
        pose.popPose();
    }
}
