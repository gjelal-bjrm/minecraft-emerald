package com.emerald.client;

import com.emerald.haven.quest.HavenOrbEntity;
import com.emerald.jak.gun.GunRenderers;
import com.emerald.jak.gun.JakGunModel;
import com.emerald.jak.gun.MorphGunItemRenderer;
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
 * L'ORBE PRECURSEUR cache (HavenOrbEntity) : l'OEUF ROSE de Jak 3 -- « collectables-skill »,
 * l'oeuf de Ndi Madman --, cuit dans l'atlas du Morph Gun par tools/jak_gun.py.
 *
 * C'est le choix du joueur (22 sept.) : « pour les collectables, je voulais justement que tu
 * reprennes l'oeuf rose du jeu original ». Il tourne sur lui-meme, flotte, et porte une
 * couronne de lumiere pour se voir de loin, dans une ruelle sombre comme au fond de l'eau ;
 * a pleine lumiere, parce qu'un collectable qu'on ne distingue pas ne se ramasse jamais.
 */
public class HavenOrbRenderer extends EntityRenderer<HavenOrbEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;
    /** L'oeuf du jeu fait un metre de haut : dans la ville, il en fait un demi-bloc. */
    private static final float SCALE = 0.45F;

    public HavenOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.16F;
    }

    @Override
    public void render(HavenOrbEntity orb, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        JakGunModel model = JakGunModel.get("haven_orb");
        double t = orb.tickCount + partial;
        float bob = (float) (0.09 * Math.sin(t * 0.08 + orb.index()));
        float pulse = 0.85F + 0.15F * (float) Math.sin(t * 0.13 + orb.index());

        // la couronne, derriere l'oeuf
        pose.pushPose();
        pose.translate(0.0F, 0.35F + bob, 0.0F);
        pose.pushPose();
        pose.mulPose(this.entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose halo = pose.last();
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(GunRenderers.HALO));
        float ring = 1.15F * pulse;
        int alpha = (int) (130 * pulse);
        corner(halo, glow, -ring / 2, -ring / 2, 0.0F, 1.0F, alpha);
        corner(halo, glow, ring / 2, -ring / 2, 1.0F, 1.0F, alpha);
        corner(halo, glow, ring / 2, ring / 2, 1.0F, 0.0F, alpha);
        corner(halo, glow, -ring / 2, ring / 2, 0.0F, 0.0F, alpha);
        pose.popPose();

        // l'oeuf, qui tourne sur lui-meme
        pose.translate(0.0F, -0.22F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees((float) (t * 2.5) + orb.index() * 37.0F));
        pose.scale(SCALE, SCALE, SCALE);
        if (model != null) {
            PoseStack.Pose last = pose.last();
            VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(MorphGunItemRenderer.ATLAS));
            for (int tri = 0; tri < model.triangles; tri++) {
                for (int corner = 0; corner < 4; corner++) {
                    int s = tri * 3 + Math.min(corner, 2);
                    out.addVertex(last, model.positions[s * 3], model.positions[s * 3 + 1], model.positions[s * 3 + 2])
                            .setColor(model.colors[s])
                            .setUv(model.uvs[s * 2], model.uvs[s * 2 + 1])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(FULL_BRIGHT)
                            .setNormal(last, model.normals[s * 3], model.normals[s * 3 + 1], model.normals[s * 3 + 2]);
                }
            }
        }
        pose.popPose();
        super.render(orb, yaw, partial, pose, buffers, light);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer out, float x, float y, float u, float v, int a) {
        out.addVertex(pose, x, y, 0.0F).setColor(255, 205, 175, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(HavenOrbEntity entity) {
        return MorphGunItemRenderer.ATLAS;
    }
}
