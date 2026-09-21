package com.emerald.client;

import com.emerald.block.HavenGunRackBlock;
import com.emerald.block.entity.HavenGunRackBlockEntity;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.gun.MorphGunItemRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Le Morph Gun pose sur le ratelier du QG : la forme Scatter Gun du modele de Jak 3,
 * couchee sur le berceau, vue de son flanc gauche.
 */
public class HavenGunRackRenderer implements BlockEntityRenderer<HavenGunRackBlockEntity> {

    /** Longueur de l'arme posee, en blocs. */
    private static final float LENGTH = 0.8F;
    /** Le centre de l'arme, au-dessus du berceau (12/16 de haut ; verifie sur les photos de la vitrine). */
    private static final float CRADLE_Y = 0.86F;

    @Nullable
    private static MorphGunItemRenderer gun;

    public HavenGunRackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HavenGunRackBlockEntity rack, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        BlockState state = rack.getBlockState();
        if (!(state.getBlock() instanceof HavenGunRackBlock)) {
            return;
        }
        if (gun == null) {
            gun = new MorphGunItemRenderer();
        }
        Direction facing = state.getValue(HavenGunRackBlock.FACING);
        // le modele est dessine face au nord ; l'etat de bloc le tourne de (lacet + 180) dans le sens horaire
        float turn = (facing.toYRot() + 180.0F) % 360.0F;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-turn));
        // sur le berceau, au milieu du bloc
        poseStack.translate(0.0F, CRADLE_Y, 0.0F);
        // canon (+z du modele) vers +x : on voit le flanc gauche de l'arme, canon vers la gauche
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        gun.renderPlaced(GunForm.RED_1, LENGTH, poseStack, buffers, light, overlay);
        poseStack.popPose();
    }
}
