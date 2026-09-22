package com.emerald.client;

import com.emerald.haven.quest.HavenTargetEntity;
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
import net.minecraft.util.Mth;

/**
 * Les cibles du stand de tir de Jak 3 (HavenTargetEntity) : le modele en carton de la variante,
 * cuit dans l'atlas du Morph Gun (tools/jak_gun.py, niveau lgunnorm), debout sur ses pieds,
 * face au tireur, a la hauteur d'un joueur (un metre soixante-dix). Pleine lumiere : la salle
 * des armes est sombre, et une cible doit se voir. Elle se dresse en un quart de seconde en
 * apparaissant, comme les cibles du jeu qui basculent sur leur charniere.
 */
public class HavenTargetRenderer extends EntityRenderer<HavenTargetEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;
    /** La hauteur d'une cible, en blocs. */
    private static final float HEIGHT = 1.7F;
    private static final float RISE_TICKS = 5.0F;

    public HavenTargetRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
    }

    @Override
    public void render(HavenTargetEntity target, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        JakGunModel model = JakGunModel.get(HavenTargetEntity.MODELS[target.variant()]);
        if (model != null && model.maxY > 0.0F) {
            float scale = HEIGHT / model.maxY;
            float rise = Mth.clamp((target.tickCount + partial) / RISE_TICKS, 0.0F, 1.0F);
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, target.yRotO, target.getYRot())));
            // la cible bascule de l'horizontale a la verticale sur sa charniere, au sol
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F * (1.0F - rise)));
            pose.scale(scale, scale, scale);
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
            pose.popPose();
        }
        super.render(target, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HavenTargetEntity entity) {
        return MorphGunItemRenderer.ATLAS;
    }
}
