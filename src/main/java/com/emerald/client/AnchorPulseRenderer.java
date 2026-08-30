package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.AnchorPulsePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;

import java.awt.Color;

/**
 * La pulsation qui montre l'ancre A TRAVERS les murs.
 *
 * Les particules ne savent pas faire cela : elles passent le test de
 * profondeur, donc la maconnerie les cache. On dessine donc de la geometrie
 * avec {@code textBackgroundSeeThrough}, le seul type de rendu simple du jeu
 * qui combine NO_DEPTH_TEST, la transparence et un format sans texture. C'est
 * un type prevu pour le fond des etiquettes de nom -- exactement le meme
 * besoin : etre lu de partout, meme derriere un mur.
 *
 * La colonne pulse en s'estompant sur ses dix secondes, plutot que de rester
 * allumee : une aide qui ne s'eteint jamais cesse d'etre une aide et devient
 * un element de decor qu'on ne regarde plus.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class AnchorPulseRenderer {

    /**
     * Un repere en cours, et sa duree.
     *
     * Il y en a PLUSIEURS : l'ancre en signale un, mais l'indice des sceaux en
     * allume trois d'un coup. Une seule position suffisait tant qu'on ne
     * montrait que l'ancre.
     */
    private record Marker(int x, int y, int z, int height, int total, int left) {
    }

    private static final java.util.List<Marker> markers = new java.util.ArrayList<>();

    private AnchorPulseRenderer() {
    }

    public static void accept(AnchorPulsePayload payload) {
        markers.removeIf(m -> m.x() == payload.x() && m.y() == payload.y()
                && m.z() == payload.z());
        markers.add(new Marker(payload.x(), payload.y(), payload.z(),
                payload.height(), Math.max(1, payload.ticks()), payload.ticks()));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        markers.replaceAll(m -> new Marker(m.x(), m.y(), m.z(), m.height(),
                m.total(), m.left() - 1));
        markers.removeIf(m -> m.left() <= 0);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER
                || markers.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // deux battements par seconde, sous une enveloppe qui s'eteint
        float beat = 0.55F + 0.45F * (float) Math.sin(
                (mc.level.getGameTime() + event.getPartialTick()
                        .getGameTimeDeltaPartialTick(false)) * 0.35);
        for (Marker marker : markers) {
            float life = marker.left() / (float) marker.total();
            draw(event.getPoseStack(), event.getCamera(), life * beat,
                    mc.level.getGameTime(), marker);
        }
    }

    private static void draw(PoseStack pose, Camera camera, float alpha, long time,
                             Marker marker) {
        Vec3 eye = camera.getPosition();
        MultiBufferSource.BufferSource buffer =
                Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.textBackgroundSeeThrough();
        VertexConsumer consumer = buffer.getBuffer(type);

        pose.pushPose();
        // le repere est relatif a la camera : on retranche sa position pour
        // parler en coordonnees du monde (la lecon de l'Aurore)
        pose.translate(marker.x() + 0.5 - eye.x, marker.y() - eye.y,
                marker.z() + 0.5 - eye.z);
        Matrix4f matrix = pose.last().pose();

        float hue = (float) ((time * 0.008) % 1.0);
        int rgb = Color.HSBtoRGB(hue, 0.55F, 1.0F);
        float red = ((rgb >> 16) & 0xFF) / 255F;
        float green = ((rgb >> 8) & 0xFF) / 255F;
        float blue = (rgb & 0xFF) / 255F;

        // deux lames croisees : la colonne se lit sous tous les angles
        int height = marker.height();
        column(matrix, consumer, 0.9F, red, green, blue, alpha, true, height);
        column(matrix, consumer, 0.9F, red, green, blue, alpha, false, height);
        // un socle plus large, qui marque le pied
        column(matrix, consumer, 2.4F, red, green, blue, alpha * 0.5F, true, height);
        column(matrix, consumer, 2.4F, red, green, blue, alpha * 0.5F, false, height);

        pose.popPose();
        buffer.endBatch(type);
    }

    /**
     * Une lame verticale, transparente vers le haut.
     *
     * Le format demande une coordonnee de lumiere : on la met au maximum, la
     * colonne devant briller autant dans une salle noire qu'en plein jour.
     */
    private static void column(Matrix4f matrix, VertexConsumer consumer, float width,
                               float red, float green, float blue, float alpha,
                               boolean alongX, int height) {
        float w = width / 2.0F;
        float x0 = alongX ? -w : 0.0F;
        float z0 = alongX ? 0.0F : -w;
        float x1 = alongX ? w : 0.0F;
        float z1 = alongX ? 0.0F : w;
        int light = 0xF000F0;

        consumer.addVertex(matrix, x0, 0.0F, z0)
                .setColor(red, green, blue, alpha).setLight(light);
        consumer.addVertex(matrix, x1, 0.0F, z1)
                .setColor(red, green, blue, alpha).setLight(light);
        consumer.addVertex(matrix, x1, height, z1)
                .setColor(red, green, blue, 0.0F).setLight(light);
        consumer.addVertex(matrix, x0, height, z0)
                .setColor(red, green, blue, 0.0F).setLight(light);
    }
}
