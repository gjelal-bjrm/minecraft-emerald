package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * LA TRAINEE DES AILES +20 (cahier §96, choix du joueur) : en vol d'elytre, deux fins rubans de
 * lumiere aux couleurs de l'Arcencium suivent le bout des ailes et s'effacent en une seconde.
 *
 * Le bout des ailes vient de WingsLayer, au moment ou il les dessine DANS LE MONDE -- pas dans
 * l'inventaire, ni dans une passe d'ombre d'un shader : entre le ciel et la fin du monde, et a
 * moins de huit blocs du joueur. C'est la seule facon d'avoir le vrai bout, battement compris.
 * Chaque image en ajoute un point ; les rubans se dessinent apres les particules, face a la
 * camera, UN QUAD D'UNE COULEUR par troncon (sous un shader, un quad a deux couleurs se coupe en
 * deux triangles, cahier §93 C).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class WingsTrail {

    /** Duree de vie d'un point, en tiques. */
    private static final double LIFE = 20.0;
    private static final double HALF_WIDTH = 0.07;
    /** Un point de plus seulement quand un des bouts a bouge d'autant. */
    private static final double MIN_STEP = 0.1;
    /** Plus loin d'une image a l'autre, le bout a saute (un teleport, un demi-tour) : le ruban se coupe. */
    private static final double MAX_JUMP = 1.5;
    private static final Map<UUID, Deque<Point>> TRAILS = new HashMap<>();
    /** Le monde est en train de se dessiner (entre le ciel et la fin). */
    private static boolean world;

    /** @param cut le ruban ne relie pas ce point au precedent */
    private record Point(Vec3 right, Vec3 left, double time, boolean cut) {
    }

    private WingsTrail() {
    }

    static boolean inWorld() {
        return world;
    }

    /** Le bout des deux ailes, dans le monde, a cette image (WingsLayer). */
    static void record(UUID player, Vec3 right, Vec3 left, double time) {
        Deque<Point> trail = TRAILS.computeIfAbsent(player, key -> new ArrayDeque<>());
        Point last = trail.peekLast();
        if (last != null && last.right.distanceToSqr(right) < MIN_STEP * MIN_STEP
                && last.left.distanceToSqr(left) < MIN_STEP * MIN_STEP) {
            return;
        }
        boolean cut = last == null || last.right.distanceToSqr(right) > MAX_JUMP * MAX_JUMP
                || last.left.distanceToSqr(left) > MAX_JUMP * MAX_JUMP;
        trail.addLast(new Point(right, left, time, cut));
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            world = true;
            return;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            world = false;
            return;
        }
        if (stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES || TRAILS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAILS.clear();
            return;
        }
        double now = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();
        RenderType type = ModRenderTypes.beam();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(type);
        Iterator<Map.Entry<UUID, Deque<Point>>> entries = TRAILS.entrySet().iterator();
        while (entries.hasNext()) {
            Deque<Point> trail = entries.next().getValue();
            while (!trail.isEmpty() && now - trail.peekFirst().time > LIFE) {
                trail.pollFirst();
            }
            if (trail.isEmpty()) {
                entries.remove();
                continue;
            }
            Point previous = null;
            int index = 0;
            for (Point point : trail) {
                if (previous != null && !point.cut) {
                    float fade = (float) Math.max(0.0, 1.0 - (now - point.time) / LIFE);
                    float alpha = 0.85F * fade;
                    float hue = (float) ((index * 0.035 + now * 0.01) % 1.0);
                    int rgb = Color.HSBtoRGB(hue, 0.75F, 1.0F);
                    ribbon(vc, matrix, cam, previous.right, point.right, rgb, alpha);
                    ribbon(vc, matrix, cam, previous.left, point.left, rgb, alpha);
                }
                previous = point;
                index++;
            }
        }
        buffers.endBatch(type);
    }

    /** Un troncon de ruban, de a a b, tourne vers la camera, d'une seule couleur. */
    private static void ribbon(VertexConsumer vc, Matrix4f matrix, Vec3 cam, Vec3 a, Vec3 b, int rgb, float alpha) {
        Vec3 along = b.subtract(a);
        Vec3 toCam = cam.subtract(a.add(b).scale(0.5));
        Vec3 side = along.cross(toCam);
        if (side.lengthSqr() < 1.0e-8) {
            return;
        }
        side = side.normalize().scale(HALF_WIDTH);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int bl = rgb & 0xFF;
        int al = (int) (alpha * 255.0F);
        vertex(vc, matrix, a.subtract(side).subtract(cam), r, g, bl, al);
        vertex(vc, matrix, b.subtract(side).subtract(cam), r, g, bl, al);
        vertex(vc, matrix, b.add(side).subtract(cam), r, g, bl, al);
        vertex(vc, matrix, a.add(side).subtract(cam), r, g, bl, al);
    }

    private static void vertex(VertexConsumer vc, Matrix4f matrix, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }
}
