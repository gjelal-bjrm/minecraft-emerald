package com.emerald.client;

import com.emerald.haven.quest.HavenOrbEntity;
import com.emerald.jak.gun.GunRenderers;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * L'ORBE PRECURSEUR cache (HavenOrbEntity) : une sphere doree qui flotte et respire, dans une
 * couronne de lumiere -- on la voit de loin, dans une ruelle sombre comme au fond de l'eau.
 *
 * DESSINE, ET NON REPRIS DU JEU : Jak 3 n'a pas d'orbe dans ce qui est extrait. Ses
 * collectables sont l'oeuf de Ndi Madman (rose) et la gemme a tete de mort (couchee, plate) ;
 * vus en photo le 22 sept., ni l'un ni l'autre ne passe pour « la gemme orange ronde » que le
 * joueur voulait. L'orbe est donc une texture a nous (tools/haven_orb_texture.py), posee face
 * a la camera : une sphere se regarde de partout pareil, un panneau suffit.
 */
public class HavenOrbRenderer extends EntityRenderer<HavenOrbEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final ResourceLocation ORB = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/entity/haven_orbe.png");
    /** Un demi-bloc : assez gros pour se voir au bout d'une rue, assez petit pour se cacher. */
    private static final float SIZE = 0.55F;

    public HavenOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.2F;
    }

    @Override
    public void render(HavenOrbEntity orb, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        double t = orb.tickCount + partial;
        float bob = (float) (0.09 * Math.sin(t * 0.08 + orb.index()));
        float pulse = 0.85F + 0.15F * (float) Math.sin(t * 0.13 + orb.index());

        // la couronne, derriere
        pose.pushPose();
        pose.translate(0.0F, 0.45F + bob, 0.0F);
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose halo = pose.last();
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GunRenderers.HALO));
        float ring = SIZE * (2.1F + 0.2F * pulse);
        int alpha = (int) (130 * pulse);
        corner(halo, glow, -ring / 2, -ring / 2, 0.0F, 1.0F, 255, 190, 70, alpha);
        corner(halo, glow, ring / 2, -ring / 2, 1.0F, 1.0F, 255, 190, 70, alpha);
        corner(halo, glow, ring / 2, ring / 2, 1.0F, 0.0F, 255, 190, 70, alpha);
        corner(halo, glow, -ring / 2, ring / 2, 0.0F, 0.0F, 255, 190, 70, alpha);

        // l'orbe, devant, a pleine lumiere
        pose.translate(0.0F, 0.0F, -0.01F);
        PoseStack.Pose face = pose.last();
        VertexConsumer body = buffers.getBuffer(RenderType.entityTranslucentEmissive(ORB));
        float s = SIZE * pulse;
        corner(face, body, -s / 2, -s / 2, 0.0F, 1.0F, 255, 255, 255, 255);
        corner(face, body, s / 2, -s / 2, 1.0F, 1.0F, 255, 255, 255, 255);
        corner(face, body, s / 2, s / 2, 1.0F, 0.0F, 255, 255, 255, 255);
        corner(face, body, -s / 2, s / 2, 0.0F, 0.0F, 255, 255, 255, 255);
        pose.popPose();
        super.render(orb, yaw, partial, pose, buffers, light);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer out, float x, float y, float u, float v,
                               int r, int g, int b, int a) {
        out.addVertex(pose, x, y, 0.0F).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(HavenOrbEntity entity) {
        return ORB;
    }
}
