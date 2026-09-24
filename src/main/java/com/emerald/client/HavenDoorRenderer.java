package com.emerald.client;

import com.emerald.block.entity.HavenDoorBlockEntity;
import com.emerald.haven.door.HavenDoorKind;
import com.emerald.jak.vehicle.JakVehicleModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

/**
 * Dessine une porte de Jak 3 (cahier §95) : le modele cuit par tools/jak_door.py, chaque
 * triangle deplace par la matrice de son os au pas d'animation de la porte -- les battants
 * coulissent dans le mur, les barres du sas glissent et tournent, comme dans le jeu. Entre deux
 * pas, la matrice est interpolee.
 *
 * Reperes : l'origine du modele au milieu de la porte, au sol ; +x le long de sa largeur, +z
 * sa face. On le pose au centre que l'entite garde, tourne de son lacet (comme les voitures),
 * et la petite porte est le modele du Hip Hog reduit a un bloc sur deux.
 */
public class HavenDoorRenderer implements BlockEntityRenderer<HavenDoorBlockEntity> {

    public HavenDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HavenDoorBlockEntity door, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        HavenDoorKind kind = door.kind();
        HavenDoorModels.DoorModel model = HavenDoorModels.get(kind.model);
        if (model == null) {
            return;
        }
        float progress = door.shown(partialTick);
        float at = progress * model.steps();
        int step = Math.min((int) Math.floor(at), model.steps() - 1);
        float blend = at - step;
        JakVehicleModel mesh = model.mesh();
        float[][] bones = new float[mesh.boneNames.length][];
        for (int b = 0; b < bones.length; b++) {
            float[][] frames = model.boneFrames()[b];
            if (frames != null) {
                bones[b] = lerp(frames[step], frames[Math.min(step + 1, frames.length - 1)], blend);
            }
        }
        pose.pushPose();
        pose.translate(door.centerX(), door.centerY(), door.centerZ());
        pose.mulPose(Axis.YP.rotationDegrees(-door.yaw()));
        pose.scale(kind.scaleX, kind.scaleY, 1.0F);
        PoseStack.Pose last = pose.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(HavenDoorModels.ATLAS));
        float[] p = mesh.positions;
        float[] uv = mesh.uvs;
        float[] n = mesh.normals;
        int[] color = mesh.colors;
        for (int t = 0; t < mesh.triangles; t++) {
            float[] m = bones[mesh.bones[t]];
            for (int corner = 0; corner < 4; corner++) {
                int s = t * 3 + Math.min(corner, 2);   // le quatrieme sommet repete le troisieme
                int i3 = s * 3;
                int i2 = s * 2;
                float x = p[i3];
                float y = p[i3 + 1];
                float z = p[i3 + 2];
                float nx = n[i3];
                float ny = n[i3 + 1];
                float nz = n[i3 + 2];
                if (m != null) {
                    float tx = m[0] * x + m[4] * y + m[8] * z + m[12];
                    float ty = m[1] * x + m[5] * y + m[9] * z + m[13];
                    float tz = m[2] * x + m[6] * y + m[10] * z + m[14];
                    float mx = m[0] * nx + m[4] * ny + m[8] * nz;
                    float my = m[1] * nx + m[5] * ny + m[9] * nz;
                    float mz = m[2] * nx + m[6] * ny + m[10] * nz;
                    x = tx;
                    y = ty;
                    z = tz;
                    nx = mx;
                    ny = my;
                    nz = mz;
                }
                vc.addVertex(last, x, y, z)
                        .setColor(color[s])
                        .setUv(uv[i2], uv[i2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(last, nx, ny, nz);
            }
        }
        pose.popPose();
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        float[] out = new float[16];
        for (int i = 0; i < 16; i++) {
            out[i] = a[i] + (b[i] - a[i]) * t;
        }
        return out;
    }

    @Override
    public boolean shouldRenderOffScreen(HavenDoorBlockEntity door) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(HavenDoorBlockEntity door) {
        HavenDoorKind kind = door.kind();
        double reach = kind.width / 2.0 + 5.0;
        return new AABB(door.getBlockPos()).inflate(reach, 1.0, reach).expandTowards(0.0, kind.height + 1.0, 0.0);
    }
}
