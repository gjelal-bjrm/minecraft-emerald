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
 *
 * EN PREMIERE PERSONNE, A BORD D'UNE VOITURE, ELLE EST DESSINEE DE NIVEAU (cahier §98, le
 * joueur : « dans les gros vehicules, le capot bouche la vue »). La camera ne penche pas avec
 * la voiture : penchee vers le poids du conducteur, la coque de cara lui montait devant les
 * yeux (premieres photos). De niveau, la camera n'a plus qu'a monter un peu, la ou le capot
 * cache encore la route (JakVehicleClient.cockpit). Les autres joueurs, la troisieme personne
 * et les motos voient toujours la voiture pencher.
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
            // L'equilibre (VehicleAttitude), APRES le lacet et autour du centre de masse du
            // jeu (cm-offset-joint, sur l'axe du modele) : la voiture penche sur ses propres
            // axes. XP d'un angle a plonge le nez (+z vers -y) : le tangage, nez en haut
            // positif, passe donc en -a. ZP leve la gauche (+x) : un roulis negatif (virage
            // a gauche) abaisse la gauche du modele.
            poseStack.pushPose();
            if (!JakVehicleClient.inCockpit(entity)) {
                PivotTilt.apply(poseStack, entity.spec().balance.cmZ(), entity.pitch(partialTick),
                        entity.roll(partialTick));
            }
            PoseStack.Pose pose = poseStack.last();
            // l'epave est noircie au quart de ses couleurs (hvehicle.gc:1172), comme dans le jeu
            boolean wreck = entity.wrecked();
            emit(model, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS)), packedLight, false, wreck);
            if (model.hasBlend) {
                emit(model, pose, buffers.getBuffer(RenderType.entityTranslucent(ATLAS)), packedLight, true, wreck);
            }
            poseStack.popPose();
            poseStack.popPose();
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static void emit(JakVehicleModel model, PoseStack.Pose pose, VertexConsumer out,
                             int light, boolean blend, boolean wreck) {
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
                        .setColor(wreck ? darken(color[s]) : color[s])
                        .setUv(uv[i2], uv[i2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, n[i3], n[i3 + 1], n[i3 + 2]);
            }
        }
    }

    /** Une couleur ARGB au quart de sa lumiere, opacite gardee. */
    private static int darken(int argb) {
        return (argb & 0xFF000000) | (((argb >> 16) & 0xFF) / 4 << 16) | (((argb >> 8) & 0xFF) / 4 << 8) | ((argb & 0xFF) / 4);
    }

    /** L'inclinaison d'un vehicule autour de son centre de masse, repere deja tourne du lacet. */
    static final class PivotTilt {
        private PivotTilt() {
        }

        static void apply(PoseStack poseStack, double cmZ, float pitchDegrees, float rollDegrees) {
            poseStack.translate(0.0, 0.0, cmZ);
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitchDegrees));
            poseStack.mulPose(Axis.ZP.rotationDegrees(rollDegrees));
            poseStack.translate(0.0, 0.0, -cmZ);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(JakVehicleEntity entity) {
        return ATLAS;
    }
}
