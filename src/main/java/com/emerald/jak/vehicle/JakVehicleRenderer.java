package com.emerald.jak.vehicle;

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
 * Dessine une voiture de Haven a partir de ses triangles cuits.
 *
 * Les types de rendu d'entite de Minecraft sont en QUADS : chaque groupe de
 * quatre sommets est coupe en deux triangles (0, 1, 2) et (2, 3, 0)
 * (RenderSystem.sharedSequentialQuad). On emet donc chaque triangle comme un
 * quad dont le quatrieme sommet repete le troisieme : le second triangle,
 * (2, 2, 0), n'a aucune surface et ne dessine rien.
 *
 * Tous les materiaux des voitures et des motos sont en decoupe et double face
 * dans le glb (alphaMode MASK, doubleSided) : entityCutoutNoCull. Un triangle
 * marque BLEND passerait en entityTranslucent ; aucun des six vehicules n'en a
 * (mesure, tools/jak_vehicle.py).
 */
public class JakVehicleRenderer extends EntityRenderer<JakVehicleEntity> {

    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(
            EmeraldWeaponsMod.MODID, "textures/entity/jak_vehicles/atlas.png");

    public JakVehicleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(JakVehicleEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        JakVehicleModel model = JakVehicleModels.get(entity.model());
        if (model != null) {
            poseStack.pushPose();
            // Lacet Minecraft y : l'entite regarde (-sin y, 0, cos y). La rotation
            // YP d'un angle a envoie l'avant du modele (0, 0, 1) sur (sin a, 0, cos a) :
            // il faut donc a = -y. Le vanilla fait 180 - y pour le bateau, dont le
            // modele regarde -z ; le notre regarde +z.
            poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getViewYRot(partialTick)));
            // Roulis leger dans les virages, autour de l'axe avant du modele (+z),
            // APRES le lacet : la voiture penche sur son propre axe. Un roulis
            // negatif (virage a gauche) abaisse la gauche du modele, +x.
            poseStack.mulPose(Axis.ZP.rotationDegrees(entity.roll(partialTick)));
            PoseStack.Pose pose = poseStack.last();
            emit(model, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS)), packedLight, false);
            if (model.hasBlend) {
                emit(model, pose, buffers.getBuffer(RenderType.entityTranslucent(ATLAS)), packedLight, true);
            }
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void emit(JakVehicleModel model, PoseStack.Pose pose, VertexConsumer out,
                             int light, boolean blend) {
        float[] p = model.positions;
        float[] uv = model.uvs;
        float[] n = model.normals;
        int[] color = model.colors;
        for (int t = 0; t < model.triangles; t++) {
            if (((model.flags[t] & JakVehicleModel.FLAG_BLEND) != 0) != blend) {
                continue;
            }
            for (int corner = 0; corner < 4; corner++) {
                int s = t * 3 + Math.min(corner, 2);   // le 4e sommet repete le 3e
                int i3 = s * 3;
                int i2 = s * 2;
                out.addVertex(pose, p[i3], p[i3 + 1], p[i3 + 2])
                        .setColor(color[s])
                        .setUv(uv[i2], uv[i2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n[i3], n[i3 + 1], n[i3 + 2]);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(JakVehicleEntity entity) {
        return ATLAS;
    }
}
