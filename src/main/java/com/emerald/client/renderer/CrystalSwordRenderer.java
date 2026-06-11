package com.emerald.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

public class CrystalSwordRenderer {

    private static final ResourceLocation[] CRYSTAL_TEXTURES = new ResourceLocation[] {
            ResourceLocation.parse("emeraldweapons;particle/crystal_green.png"),
            ResourceLocation.parse("emeraldweapons;particle/crystal_red.png"),
            ResourceLocation.parse("emeraldweapons;particle/crystal_orange.png"),
            ResourceLocation.parse("emeraldweapons;particle/crystal_pink.png"),
            ResourceLocation.parse("emeraldweapons;particle/crystal_yellow.png")
    };

    public static void render(ItemStack itemStack, ItemDisplayContext context, PoseStack poseStack,
                              MultiBufferSource buffer, int light, float partialTicks) {

        // Render de l’item normal
        Minecraft.getInstance().getItemRenderer().renderStatic(
                itemStack, context, light, OverlayTexture.NO_OVERLAY, poseStack, buffer, null, 0
        );

        // Ajout des cristaux en orbite
        float time = (Minecraft.getInstance().level.getGameTime() + partialTicks) % 360;

        for (int i = 0; i < CRYSTAL_TEXTURES.length; i++) {
            poseStack.pushPose();

            float angle = time + (120 * i);
            float radius = 0.3f;
            float x = Mth.cos(angle * 0.05f) * radius;
            float y = 0.2f + Mth.sin(angle * 0.1f) * 0.05f;
            float z = Mth.sin(angle * 0.05f) * radius;

            poseStack.translate(x, y, z);
            poseStack.scale(0.2f, 0.2f, 0.2f);
            poseStack.mulPose(Axis.YP.rotationDegrees(angle));

            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            itemRenderer.renderStatic(
                    new ItemStack(net.minecraft.world.item.Items.DIAMOND),
                    ItemDisplayContext.FIXED,
                    light,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    buffer,
                    null,
                    0
            );

            poseStack.popPose();
        }
    }
}