package com.emerald.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;

/**
 * Les outils de dessin des portes dessinees par le jeu : la porte precurseur de Haven
 * (HavenGateRenderer) et les portails d'Arcencium (ArcPortalRenderer). Des quadrilateres,
 * des disques debout ou couches, des cylindres de lumiere, des boites.
 */
final class PortalMesh {

    static final int FULL = LightTexture.FULL_BRIGHT;
    static final int SEGMENTS = 40;

    private PortalMesh() {
    }

    static int argb(float a, float r, float g, float b) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (Mth.clamp(r, 0, 1) * 255) << 16)
                | ((int) (Mth.clamp(g, 0, 1) * 255) << 8) | (int) (Mth.clamp(b, 0, 1) * 255);
    }

    /** Une couleur de l'arc-en-ciel (teinte de 0 a 1), opaque. */
    static int rainbow(float hue, float alpha) {
        int rgb = Mth.hsvToRgb(hue - Mth.floor(hue), 0.85F, 1.0F);
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** La meme couleur, alpha change. */
    static int withAlpha(int color, float alpha) {
        return ((int) (Mth.clamp(alpha, 0, 1) * 255) << 24) | (color & 0xFFFFFF);
    }

    static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, int color, float u, float v,
                       int overlay, int light, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }

    static void quad(PoseStack.Pose p, VertexConsumer vc, int overlay, int light, int color,
                     float nx, float ny, float nz,
                     float x0, float y0, float z0, float u0, float v0,
                     float x1, float y1, float z1, float u1, float v1,
                     float x2, float y2, float z2, float u2, float v2,
                     float x3, float y3, float z3, float u3, float v3) {
        vertex(p, vc, x0, y0, z0, color, u0, v0, overlay, light, nx, ny, nz);
        vertex(p, vc, x1, y1, z1, color, u1, v1, overlay, light, nx, ny, nz);
        vertex(p, vc, x2, y2, z2, color, u2, v2, overlay, light, nx, ny, nz);
        vertex(p, vc, x3, y3, z3, color, u3, v3, overlay, light, nx, ny, nz);
    }

    /** Un disque debout (dans le plan xy), l'image tournee de « angle » ; « scale » l'agrandit. */
    static void disc(PoseStack.Pose p, VertexConsumer vc, int overlay, float cx, float cy, float z, float radius,
                     float angle, float scale, int color) {
        disc(p, vc, overlay, cx, cy, z, radius, angle, scale, color, false);
    }

    /** Un disque debout (plan xy), ou couche au sol (plan xz) si « flat ». */
    static void disc(PoseStack.Pose p, VertexConsumer vc, int overlay, float cx, float cy, float z, float radius,
                     float angle, float scale, int color, boolean flat) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float tu0 = 0.5F + 0.5F / scale * Mth.cos(a0 + angle), tv0 = 0.5F + 0.5F / scale * Mth.sin(a0 + angle);
            float tu1 = 0.5F + 0.5F / scale * Mth.cos(a1 + angle), tv1 = 0.5F + 0.5F / scale * Mth.sin(a1 + angle);
            if (flat) {
                quad(p, vc, overlay, FULL, color, 0, 1, 0,
                        cx, cy, z, 0.5F, 0.5F,
                        cx + radius * c1, cy, z + radius * s1, tu1, tv1,
                        cx + radius * c0, cy, z + radius * s0, tu0, tv0,
                        cx, cy, z, 0.5F, 0.5F);
            } else {
                quad(p, vc, overlay, FULL, color, 0, 0, 1,
                        cx, cy, z, 0.5F, 0.5F,
                        cx + radius * c0, cy + radius * s0, z, tu0, tv0,
                        cx + radius * c1, cy + radius * s1, z, tu1, tv1,
                        cx, cy, z, 0.5F, 0.5F);
            }
        }
    }

    /** Un cylindre de lumiere, de y0 a y1, la texture qui defile ; opaque en bas, efface en haut. */
    static void column(PoseStack.Pose p, VertexConsumer vc, int overlay, float radius, float y0, float y1,
                       float scroll, float alpha) {
        column(p, vc, overlay, radius, y0, y1, scroll, alpha, 0xFFFFFF);
    }

    /** Le meme cylindre, teinte. */
    static void column(PoseStack.Pose p, VertexConsumer vc, int overlay, float radius, float y0, float y1,
                       float scroll, float alpha, int rgb) {
        int bottom = withAlpha(rgb, alpha);
        int top = withAlpha(rgb, 0.0F);
        int n = 24;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n;
            float a1 = Mth.TWO_PI * (i + 1) / n;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = 4.0F * i / n, u1 = 4.0F * (i + 1) / n;
            float mc = (c0 + c1) * 0.5F, ms = (s0 + s1) * 0.5F;
            vertex(p, vc, radius * c0, y0, radius * s0, bottom, u0, 1.0F + scroll, overlay, FULL, mc, 0, ms);
            vertex(p, vc, radius * c1, y0, radius * s1, bottom, u1, 1.0F + scroll, overlay, FULL, mc, 0, ms);
            vertex(p, vc, radius * c1, y1, radius * s1, top, u1, scroll, overlay, FULL, mc, 0, ms);
            vertex(p, vc, radius * c0, y1, radius * s0, top, u0, scroll, overlay, FULL, mc, 0, ms);
        }
    }

    /** Une boite dont la bande de texture se repete « repeat » fois sur la longueur (x). */
    static void lintel(PoseStack.Pose p, VertexConsumer vc, int overlay, int light,
                       float x0, float y0, float z0, float x1, float y1, float z1, float repeat) {
        int c = 0xFFFFFFFF;
        quad(p, vc, overlay, light, c, 0, 0, 1,
                x0, y0, z1, 0, 1, x1, y0, z1, repeat, 1, x1, y1, z1, repeat, 0, x0, y1, z1, 0, 0);
        quad(p, vc, overlay, light, c, 0, 0, -1,
                x1, y0, z0, 0, 1, x0, y0, z0, repeat, 1, x0, y1, z0, repeat, 0, x1, y1, z0, 0, 0);
        quad(p, vc, overlay, light, c, 0, 1, 0,
                x0, y1, z1, 0, 0.22F, x1, y1, z1, repeat, 0.22F, x1, y1, z0, repeat, 0.0F, x0, y1, z0, 0, 0.0F);
        quad(p, vc, overlay, light, c, 0, -1, 0,
                x0, y0, z0, 0, 1.0F, x1, y0, z0, repeat, 1.0F, x1, y0, z1, repeat, 0.78F, x0, y0, z1, 0, 0.78F);
        quad(p, vc, overlay, light, c, -1, 0, 0,
                x0, y0, z0, 0, 1, x0, y0, z1, 0.4F, 1, x0, y1, z1, 0.4F, 0, x0, y1, z0, 0, 0);
        quad(p, vc, overlay, light, c, 1, 0, 0,
                x1, y0, z1, 0, 1, x1, y0, z0, 0.4F, 1, x1, y1, z0, 0.4F, 0, x1, y1, z1, 0, 0);
    }

    static void box(PoseStack.Pose p, VertexConsumer vc, int overlay, int light,
                    float x0, float y0, float z0, float x1, float y1, float z1) {
        boxTinted(p, vc, overlay, light, 0xFFFFFFFF, x0, y0, z0, x1, y1, z1);
    }

    /** Une boite ; chaque face prend toute la texture. */
    static void boxTinted(PoseStack.Pose p, VertexConsumer vc, int overlay, int light, int color,
                          float x0, float y0, float z0, float x1, float y1, float z1) {
        // avant (+z), arriere (-z)
        quad(p, vc, overlay, light, color, 0, 0, 1,
                x0, y0, z1, 0, 1, x1, y0, z1, 1, 1, x1, y1, z1, 1, 0, x0, y1, z1, 0, 0);
        quad(p, vc, overlay, light, color, 0, 0, -1,
                x1, y0, z0, 0, 1, x0, y0, z0, 1, 1, x0, y1, z0, 1, 0, x1, y1, z0, 0, 0);
        // gauche (-x), droite (+x)
        quad(p, vc, overlay, light, color, -1, 0, 0,
                x0, y0, z0, 0, 1, x0, y0, z1, 1, 1, x0, y1, z1, 1, 0, x0, y1, z0, 0, 0);
        quad(p, vc, overlay, light, color, 1, 0, 0,
                x1, y0, z1, 0, 1, x1, y0, z0, 1, 1, x1, y1, z0, 1, 0, x1, y1, z1, 0, 0);
        // dessus, dessous
        quad(p, vc, overlay, light, color, 0, 1, 0,
                x0, y1, z1, 0, 1, x1, y1, z1, 1, 1, x1, y1, z0, 1, 0, x0, y1, z0, 0, 0);
        quad(p, vc, overlay, light, color, 0, -1, 0,
                x0, y0, z0, 0, 1, x1, y0, z0, 1, 1, x1, y0, z1, 1, 0, x0, y0, z1, 0, 0);
    }
}
