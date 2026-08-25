package com.emerald.client;

import com.emerald.block.ModBlocks;
import com.emerald.block.entity.ArcenciumChestBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Dessine le coffre en trois dimensions dans l'inventaire et dans la main.
 *
 * Un coffre n'a pas de modele de bloc utilisable : sa geometrie vit dans le
 * renderer de block entity. Sans ce relais, l'objet apparaitrait comme un cube
 * sans texture. C'est exactement le mecanisme qu'emploie le coffre vanilla.
 */
public class ArcenciumChestItemRenderer extends BlockEntityWithoutLevelRenderer {

    /** Cree a la premiere utilisation : trop tot, les blocs ne sont pas encore la. */
    private ArcenciumChestBlockEntity dummy;

    public ArcenciumChestItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        if (this.dummy == null) {
            this.dummy = new ArcenciumChestBlockEntity(BlockPos.ZERO,
                    ModBlocks.ARCENCIUM_CHEST.get().defaultBlockState());
        }
        Minecraft.getInstance().getBlockEntityRenderDispatcher()
                .renderItem(this.dummy, pose, buffer, light, overlay);
    }
}
