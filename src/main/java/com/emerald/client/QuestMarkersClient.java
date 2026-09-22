package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.QuestMarkersPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Les reperes des quetes de Haven (QuestMarkersPayload) : colonnes de lumiere, anneaux,
 * cercles au sol. Dessines comme les colonnes des ancres (AnchorPulseRenderer) : a pleine
 * lumiere, vus a travers les murs, en coordonnees du monde (le repere de la camera retranche).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class QuestMarkersClient {

    private static volatile List<QuestMarkersPayload.Marker> markers = List.of();
    private static final int SEGMENTS = 48;

    private QuestMarkersClient() {
    }

    public static void accept(QuestMarkersPayload payload) {
        markers = payload.markers();
    }

    /** Pour les photos et le banc : ce que le client a recu. */
    public static List<QuestMarkersPayload.Marker> markers() {
        return markers;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        markers = List.of();
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        List<QuestMarkersPayload.Marker> list = markers;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || list.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.textBackgroundSeeThrough();
        VertexConsumer consumer = buffer.getBuffer(type);
        PoseStack pose = event.getPoseStack();
        for (QuestMarkersPayload.Marker marker : list) {
            pose.pushPose();
            pose.translate(marker.x() - eye.x, marker.y() - eye.y, marker.z() - eye.z);
            Matrix4f matrix = pose.last().pose();
            float r = ((marker.argb() >> 16) & 0xFF) / 255F;
            float g = ((marker.argb() >> 8) & 0xFF) / 255F;
            float b = (marker.argb() & 0xFF) / 255F;
            float a = ((marker.argb() >>> 24) & 0xFF) / 255F;
            float beat = 0.75F + 0.25F * Mth.sin(time * 0.25F);
            switch (marker.kind()) {
                case QuestMarkersPayload.RING -> ring(matrix, consumer, marker.yaw(), marker.size(), r, g, b, a * beat);
                case QuestMarkersPayload.ZONE -> zone(matrix, consumer, marker.size(), r, g, b, a * beat, time);
                default -> beacon(matrix, consumer, marker.size(), r, g, b, a * beat);
            }
            pose.popPose();
        }
        buffer.endBatch(type);
    }

    /** Une colonne : deux lames croisees, transparentes vers le haut, et un socle plus large. */
    private static void beacon(Matrix4f m, VertexConsumer c, float height, float r, float g, float b, float a) {
        blade(m, c, 0.8F, height, r, g, b, a, true);
        blade(m, c, 0.8F, height, r, g, b, a, false);
        blade(m, c, 2.2F, height * 0.35F, r, g, b, a * 0.5F, true);
        blade(m, c, 2.2F, height * 0.35F, r, g, b, a * 0.5F, false);
    }

    private static void blade(Matrix4f m, VertexConsumer c, float width, float height, float r, float g, float b,
                              float a, boolean alongX) {
        float w = width / 2.0F;
        float x0 = alongX ? -w : 0.0F;
        float z0 = alongX ? 0.0F : -w;
        float x1 = alongX ? w : 0.0F;
        float z1 = alongX ? 0.0F : w;
        quad(m, c, x0, 0, z0, x1, 0, z1, x1, height, z1, x0, height, z0, r, g, b, a, 0.0F);
    }

    /** Un anneau vertical de rayon {@code radius}, tourne selon le lacet : a traverser. */
    private static void ring(Matrix4f m, VertexConsumer c, float yaw, float radius, float r, float g, float b, float a) {
        float rad = (float) Math.toRadians(yaw);
        // le plan de l'anneau est perpendiculaire a la direction de passage (lacet)
        float ax = Mth.cos(rad);
        float az = Mth.sin(rad);
        float thick = Math.max(0.35F, radius * 0.12F);
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            float c0 = Mth.cos(t0);
            float s0 = Mth.sin(t0);
            float c1 = Mth.cos(t1);
            float s1 = Mth.sin(t1);
            float ro = radius;
            float ri = radius - thick;
            quad(m, c,
                    ax * c0 * ro, s0 * ro, az * c0 * ro,
                    ax * c1 * ro, s1 * ro, az * c1 * ro,
                    ax * c1 * ri, s1 * ri, az * c1 * ri,
                    ax * c0 * ri, s0 * ri, az * c0 * ri,
                    r, g, b, a, a);
        }
    }

    /** Un cercle au sol qui tourne doucement, et une lame basse sur son pourtour. */
    private static void zone(Matrix4f m, VertexConsumer c, float radius, float r, float g, float b, float a, float time) {
        float thick = 0.4F;
        float spin = time * 0.01F;
        for (int i = 0; i < SEGMENTS; i++) {
            if (i % 4 == 3) {
                continue;             // des tirets : le sol reste lisible
            }
            float t0 = (float) (i * Math.PI * 2.0 / SEGMENTS) + spin;
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS) + spin;
            float c0 = Mth.cos(t0);
            float s0 = Mth.sin(t0);
            float c1 = Mth.cos(t1);
            float s1 = Mth.sin(t1);
            quad(m, c,
                    c0 * radius, 0.08F, s0 * radius,
                    c1 * radius, 0.08F, s1 * radius,
                    c1 * (radius - thick), 0.08F, s1 * (radius - thick),
                    c0 * (radius - thick), 0.08F, s0 * (radius - thick),
                    r, g, b, a, a);
            quad(m, c,
                    c0 * radius, 0.0F, s0 * radius,
                    c1 * radius, 0.0F, s1 * radius,
                    c1 * radius, 1.2F, s1 * radius,
                    c0 * radius, 1.2F, s0 * radius,
                    r, g, b, a * 0.6F, 0.0F);
        }
    }

    /** Un quadrilatere a deux faces ; alpha du bas (points 1-2) et du haut (points 3-4). */
    private static void quad(Matrix4f m, VertexConsumer c,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float r, float g, float b, float aLow, float aHigh) {
        int light = 0xF000F0;
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, aLow).setLight(light);
        c.addVertex(m, x2, y2, z2).setColor(r, g, b, aLow).setLight(light);
        c.addVertex(m, x3, y3, z3).setColor(r, g, b, aHigh).setLight(light);
        c.addVertex(m, x4, y4, z4).setColor(r, g, b, aHigh).setLight(light);
        c.addVertex(m, x4, y4, z4).setColor(r, g, b, aHigh).setLight(light);
        c.addVertex(m, x3, y3, z3).setColor(r, g, b, aHigh).setLight(light);
        c.addVertex(m, x2, y2, z2).setColor(r, g, b, aLow).setLight(light);
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, aLow).setLight(light);
    }
}
