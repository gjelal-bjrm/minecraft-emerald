package com.emerald.jak.board;

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
 * Dessine le JET-Board de Jak 3 (board-lod0, cuit par tools/jak_gun.py dans l'atlas du Morph Gun)
 * sous les pieds du joueur : tourne comme sa course, penche dans les virages et suit sa pente.
 * Chaque triangle en quad dont le quatrieme sommet repete le troisieme, comme les vehicules.
 */
public class JetBoardRenderer extends EntityRenderer<JetBoardEntity> {

    public static final String MODEL = "jet_board";

    public JetBoardRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.6F;
    }

    @Override
    public void render(JetBoardEntity board, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        JakGunModel model = JakGunModel.get(MODEL);
        if (model != null) {
            pose.pushPose();
            // le modele regarde +z, comme les vehicules : le lacet Minecraft y s'applique en -y
            pose.mulPose(Axis.YP.rotationDegrees(-board.getViewYRot(partial)));
            pose.mulPose(Axis.XP.rotationDegrees(board.pitch(partial)));
            pose.mulPose(Axis.ZP.rotationDegrees(board.roll(partial)));
            emit(model, pose.last(), buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        super.render(board, yaw, partial, pose, buffers, light);
    }

    /** Les triangles de la planche, au repos, dans l'atlas du Morph Gun. */
    static void emit(JakGunModel model, PoseStack.Pose pose, MultiBufferSource buffers, int light, int overlay) {
        emit(model, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(MorphGunItemRenderer.ATLAS)), light, overlay,
                false);
        if (model.hasBlend) {
            emit(model, pose, buffers.getBuffer(RenderType.entityTranslucent(MorphGunItemRenderer.ATLAS)), light, overlay,
                    true);
        }
    }

    private static void emit(JakGunModel model, PoseStack.Pose pose, VertexConsumer out, int light, int overlay,
                             boolean blend) {
        for (int tri = 0; tri < model.triangles; tri++) {
            if (((model.flags[tri] & JakGunModel.FLAG_BLEND) != 0) != blend) {
                continue;
            }
            boolean quad = (model.flags[tri] & JakGunModel.FLAG_QUAD) != 0;
            for (int corner = 0; corner < 4; corner++) {
                int s = tri * 3 + Math.min(corner, 2);
                float x = model.positions[s * 3];
                float y = model.positions[s * 3 + 1];
                float z = model.positions[s * 3 + 2];
                if (quad && corner == 3) {
                    // un rectangle des cubes (cahier §110) : le quatrieme coin, c0 + c2 - c1
                    int c = tri * 9;
                    x = model.positions[c] + model.positions[c + 6] - model.positions[c + 3];
                    y = model.positions[c + 1] + model.positions[c + 7] - model.positions[c + 4];
                    z = model.positions[c + 2] + model.positions[c + 8] - model.positions[c + 5];
                }
                out.addVertex(pose, x, y, z)
                        .setColor(model.colors[s])
                        .setUv(model.uvs[s * 2], model.uvs[s * 2 + 1])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, model.normals[s * 3], model.normals[s * 3 + 1], model.normals[s * 3 + 2]);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(JetBoardEntity board) {
        return MorphGunItemRenderer.ATLAS;
    }
}
