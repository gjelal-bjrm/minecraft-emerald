package com.emerald.client;

import com.emerald.block.HavenGateBlock;
import com.emerald.block.entity.HavenGateBlockEntity;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * La porte precurseur de la victoire, dessinee aux textures du warp gate de Jak 3
 * (tools/haven_porte_textures.py). Trois modeles, un par STYLE du bloc :
 *
 *   ANNEAU  -- la porte de la victoire, choisie par le joueur : ronde, debout, 3,7 blocs
 *              (agrandie de 20 % apres les photos) : bande de metal brun a glyphes
 *              sur ses deux faces, glyphes allumes en cyan, et dedans un voile de lumiere
 *              bleue qui tourne ;
 *   PORTAIL -- le plateau du warp gate, rond, de trois blocs, quatre montants, et une
 *              colonne de lumiere a circuits qui monte a cinq blocs : se voit de loin ;
 *   ARCHE   -- deux piliers, un linteau a glyphes, un voile d'eau lumineuse qui coule.
 *
 * Reperes : l'origine au milieu du bloc, au sol ; +z regarde FACING (la face avant).
 *
 * UNE TEXTURE A LA FOIS : les types de rendu d'entite partagent un meme tampon, et en
 * demander un autre termine le precedent (MultiBufferSource.BufferSource.getBuffer).
 * Ecrire ensuite dans l'ancien ferait tomber le jeu (« Not building! ») : chaque piece
 * est donc ecrite en entier, texture par texture, avant de passer a la suivante.
 *
 * LES LUEURS ECRIVENT LA PROFONDEUR (entityTranslucent, a pleine lumiere) : un voile en
 * entityTranslucentEmissive, qui ne l'ecrit pas, disparaissait partout ou le ciel etait
 * derriere lui -- Distant Horizons repeint ce qui reste « au loin » dans le tampon de
 * profondeur, avec ou sans shaders (photos du 21 sept. au soir). Seuls les glyphes, poses
 * sur le metal, et les tourbillons, poses sur une lueur, gardent le rendu emissif.
 */
public class HavenGateRenderer implements BlockEntityRenderer<HavenGateBlockEntity> {

    private static final ResourceLocation METAL = tex("metal");
    private static final ResourceLocation GLYPHES = tex("glyphes");
    private static final ResourceLocation PILIER = tex("pilier");
    private static final ResourceLocation CIRCUIT = tex("circuit");
    private static final ResourceLocation PLAQUE = tex("plaque");
    private static final ResourceLocation BORD = tex("bord");
    private static final ResourceLocation LUMIERE = tex("lumiere");
    private static final ResourceLocation VOILE = tex("voile");

    private static final int FULL = LightTexture.FULL_BRIGHT;
    private static final int SEGMENTS = 40;
    /**
     * L'anneau, choisi par le joueur pour la victoire, « un peu petit, il faudrait
     * l'agrandir de 20 % » (photos du 21 sept.) : 3,7 blocs de haut et de large.
     */
    private static final float RING_SCALE = 1.2F;

    public HavenGateRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "textures/block/haven_porte/" + name + ".png");
    }

    @Override
    public void render(HavenGateBlockEntity gate, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = gate.getBlockState();
        if (!(state.getBlock() instanceof HavenGateBlock) || gate.getLevel() == null) {
            return;
        }
        float time = ((gate.getLevel().getGameTime() % 72000L) + partialTick) / 20.0F;
        Direction facing = state.getValue(HavenGateBlock.FACING);
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        switch (state.getValue(HavenGateBlock.STYLE)) {
            case ANNEAU -> ring(pose, buffers, light, overlay, time);
            case PORTAIL -> pad(pose, buffers, light, overlay, time);
            case ARCHE -> arch(pose, buffers, light, overlay, time);
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(HavenGateBlockEntity gate) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(HavenGateBlockEntity gate) {
        return new AABB(gate.getBlockPos()).inflate(2.5, 0.0, 2.5).expandTowards(0.0, 5.5, 0.0);
    }

    // ================================================================ l'anneau

    private static void ring(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time) {
        float cy = 1.6F * RING_SCALE;
        float outer = 1.55F * RING_SCALE;
        float inner = 1.18F * RING_SCALE;
        float depth = 0.22F * RING_SCALE;
        PoseStack.Pose p = pose.last();
        // la bande a glyphes fait huit fois le tour ; u suit l'anneau, v va de l'interieur a l'exterieur
        float repeat = 8.0F;
        VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = repeat * i / SEGMENTS, u1 = repeat * (i + 1) / SEGMENTS;
            for (int side = -1; side <= 1; side += 2) {
                float z = depth * side;
                quad(p, metal, overlay, light, 0xFFFFFFFF, 0, 0, side,
                        inner * c0, cy + inner * s0, z, u0, 0.9F,
                        outer * c0, cy + outer * s0, z, u0, 0.1F,
                        outer * c1, cy + outer * s1, z, u1, 0.1F,
                        inner * c1, cy + inner * s1, z, u1, 0.9F);
            }
        }
        // tranches exterieure et interieure
        VertexConsumer bord = buffers.getBuffer(RenderType.entityCutoutNoCull(BORD));
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = repeat * i / SEGMENTS, u1 = repeat * (i + 1) / SEGMENTS;
            float mc = (c0 + c1) * 0.5F, ms = (s0 + s1) * 0.5F;
            quad(p, bord, overlay, light, 0xFFFFFFFF, mc, ms, 0,
                    outer * c0, cy + outer * s0, -depth, u0, 0.0F,
                    outer * c1, cy + outer * s1, -depth, u1, 0.0F,
                    outer * c1, cy + outer * s1, depth, u1, 0.45F,
                    outer * c0, cy + outer * s0, depth, u0, 0.45F);
            quad(p, bord, overlay, light, 0xFFFFFFFF, -mc, -ms, 0,
                    inner * c0, cy + inner * s0, -depth, u0, 0.0F,
                    inner * c0, cy + inner * s0, depth, u0, 0.45F,
                    inner * c1, cy + inner * s1, depth, u1, 0.45F,
                    inner * c1, cy + inner * s1, -depth, u1, 0.0F);
        }
        // les glyphes s'allument, en pulsant
        int glow = argb(0.55F + 0.45F * Mth.sin(time * 2.2F), 1.0F, 1.0F, 1.0F);
        VertexConsumer glyphs = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLYPHES));
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = repeat * i / SEGMENTS, u1 = repeat * (i + 1) / SEGMENTS;
            for (int side = -1; side <= 1; side += 2) {
                float z = (depth + 0.004F) * side;
                quad(p, glyphs, overlay, FULL, glow, 0, 0, side,
                        inner * c0, cy + inner * s0, z, u0, 0.9F,
                        outer * c0, cy + outer * s0, z, u0, 0.1F,
                        outer * c1, cy + outer * s1, z, u1, 0.1F,
                        inner * c1, cy + inner * s1, z, u1, 0.9F);
            }
        }
        // le voile : une lueur qui ecrit la profondeur, et par-dessus, des deux cotes, l'eau qui tourne
        disc(p, buffers.getBuffer(RenderType.entityTranslucent(LUMIERE)), overlay, 0, cy, 0.0F, inner,
                time * 0.6F, 1.0F, argb(0.9F, 1.0F, 1.0F, 1.0F));
        VertexConsumer swirl = buffers.getBuffer(RenderType.entityTranslucentEmissive(VOILE));
        disc(p, swirl, overlay, 0, cy, 0.012F, inner, -time * 0.9F, 1.6F, argb(0.6F, 0.8F, 1.0F, 1.0F));
        disc(p, swirl, overlay, 0, cy, -0.012F, inner, time * 0.7F, 1.6F, argb(0.6F, 0.8F, 1.0F, 1.0F));
        // deux pieds pour le poser au sol
        VertexConsumer plaque = buffers.getBuffer(RenderType.entityCutoutNoCull(PLAQUE));
        float k = RING_SCALE;
        box(p, plaque, overlay, light, -0.95F * k, 0.0F, -0.4F * k, -0.35F * k, 0.18F * k, 0.4F * k);
        box(p, plaque, overlay, light, 0.35F * k, 0.0F, -0.4F * k, 0.95F * k, 0.18F * k, 0.4F * k);
    }

    // ================================================================ le portail

    private static void pad(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time) {
        PoseStack.Pose p = pose.last();
        float radius = 1.5F;
        float height = 0.22F;
        // le dessus : la plaque ronde des niveaux precurseurs, en eventail
        VertexConsumer plaque = buffers.getBuffer(RenderType.entityCutoutNoCull(PLAQUE));
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad(p, plaque, overlay, light, 0xFFFFFFFF, 0, 1, 0,
                    0, height, 0, 0.5F, 0.5F,
                    radius * c1, height, radius * s1, 0.5F + 0.5F * c1, 0.5F + 0.5F * s1,
                    radius * c0, height, radius * s0, 0.5F + 0.5F * c0, 0.5F + 0.5F * s0,
                    0, height, 0, 0.5F, 0.5F);
        }
        // le flanc : la bande a glyphes
        VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = 10.0F * i / SEGMENTS, u1 = 10.0F * (i + 1) / SEGMENTS;
            float mc = (c0 + c1) * 0.5F, ms = (s0 + s1) * 0.5F;
            quad(p, metal, overlay, light, 0xFFFFFFFF, mc, 0, ms,
                    radius * c0, 0, radius * s0, u0, 0.78F,
                    radius * c0, height, radius * s0, u0, 0.22F,
                    radius * c1, height, radius * s1, u1, 0.22F,
                    radius * c1, 0, radius * s1, u1, 0.78F);
        }
        // quatre montants, en diagonale
        VertexConsumer pilier = buffers.getBuffer(RenderType.entityCutoutNoCull(PILIER));
        for (int k = 0; k < 4; k++) {
            float a = Mth.HALF_PI * k + Mth.PI / 4.0F;
            float x = 1.28F * Mth.cos(a);
            float z = 1.28F * Mth.sin(a);
            box(p, pilier, overlay, light, x - 0.12F, height, z - 0.12F, x + 0.12F, height + 1.3F, z + 0.12F);
        }
        VertexConsumer caps = buffers.getBuffer(RenderType.entityTranslucent(LUMIERE));
        for (int k = 0; k < 4; k++) {
            float a = Mth.HALF_PI * k + Mth.PI / 4.0F;
            float x = 1.28F * Mth.cos(a);
            float z = 1.28F * Mth.sin(a);
            boxTinted(p, caps, overlay, FULL, argb(0.9F, 1.0F, 1.0F, 1.0F),
                    x - 0.14F, height + 1.3F, z - 0.14F, x + 0.14F, height + 1.52F, z + 0.14F);
        }
        // la lueur du plateau, puis la colonne : deux cylindres a circuits qui montent et
        // s'effacent vers le haut, le coeur d'abord (il ecrit la profondeur, l'enveloppe se pose dessus)
        disc(p, buffers.getBuffer(RenderType.entityTranslucent(LUMIERE)), overlay, 0, height + 0.01F, 0, 1.2F,
                time * 0.5F, 1.0F, argb(0.85F, 1.0F, 1.0F, 1.0F), true);
        VertexConsumer beam = buffers.getBuffer(RenderType.entityTranslucent(CIRCUIT));
        column(p, beam, overlay, 0.45F, height, 4.6F, -time * 0.9F, 0.95F);
        column(p, beam, overlay, 0.9F, height, 5.2F, time * 0.4F, 0.6F);
    }

    // ================================================================ l'arche

    private static void arch(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time) {
        PoseStack.Pose p = pose.last();
        VertexConsumer pilier = buffers.getBuffer(RenderType.entityCutoutNoCull(PILIER));
        box(p, pilier, overlay, light, -1.3F, 0.12F, -0.3F, -0.88F, 2.95F, 0.3F);
        box(p, pilier, overlay, light, 0.88F, 0.12F, -0.3F, 1.3F, 2.95F, 0.3F);
        // le linteau : la bande a glyphes trois fois sur la longueur, comme les glyphes allumes
        VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        lintel(p, metal, overlay, light, -1.5F, 2.95F, -0.36F, 1.5F, 3.5F, 0.36F, 3.0F);
        VertexConsumer glyphs = buffers.getBuffer(RenderType.entityTranslucentEmissive(GLYPHES));
        int glow = argb(0.55F + 0.45F * Mth.sin(time * 2.2F), 1.0F, 1.0F, 1.0F);
        for (int side = -1; side <= 1; side += 2) {
            float z = 0.364F * side;
            quad(p, glyphs, overlay, FULL, glow, 0, 0, side,
                    -1.5F, 2.95F, z, 0.0F, 1.0F,
                    1.5F, 2.95F, z, 3.0F, 1.0F,
                    1.5F, 3.5F, z, 3.0F, 0.0F,
                    -1.5F, 3.5F, z, 0.0F, 0.0F);
        }
        VertexConsumer bord = buffers.getBuffer(RenderType.entityCutoutNoCull(BORD));
        box(p, bord, overlay, light, -1.5F, 0.0F, -0.45F, 1.5F, 0.12F, 0.45F);
        // le voile : une lueur qui ecrit la profondeur, et l'eau lumineuse qui coule, des deux cotes
        VertexConsumer glowQuad = buffers.getBuffer(RenderType.entityTranslucent(LUMIERE));
        quad(p, glowQuad, overlay, FULL, argb(0.85F, 1.0F, 1.0F, 1.0F), 0, 0, 1,
                -0.88F, 0.12F, 0.0F, 0.0F, 1.0F,
                0.88F, 0.12F, 0.0F, 1.0F, 1.0F,
                0.88F, 2.95F, 0.0F, 1.0F, 0.0F,
                -0.88F, 2.95F, 0.0F, 0.0F, 0.0F);
        VertexConsumer veil = buffers.getBuffer(RenderType.entityTranslucentEmissive(VOILE));
        float flow = time * 0.25F;
        for (int side = -1; side <= 1; side += 2) {
            float z = 0.012F * side;
            quad(p, veil, overlay, FULL, argb(0.7F, 1.0F, 1.0F, 1.0F), 0, 0, side,
                    -0.88F, 0.12F, z, 0.0F, 2.0F + flow,
                    0.88F, 0.12F, z, 1.0F, 2.0F + flow,
                    0.88F, 2.95F, z, 1.0F, flow,
                    -0.88F, 2.95F, z, 0.0F, flow);
        }
    }

    // ================================================================ outils de dessin

    private static int argb(float a, float r, float g, float b) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, int color, float u, float v,
                               int overlay, int light, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(p, nx, ny, nz);
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, int overlay, int light, int color,
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
    private static void disc(PoseStack.Pose p, VertexConsumer vc, int overlay, float cx, float cy, float z, float radius,
                             float angle, float scale, int color) {
        disc(p, vc, overlay, cx, cy, z, radius, angle, scale, color, false);
    }

    /** Un disque debout (plan xy), ou couche au sol (plan xz) si « flat ». */
    private static void disc(PoseStack.Pose p, VertexConsumer vc, int overlay, float cx, float cy, float z, float radius,
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
    private static void column(PoseStack.Pose p, VertexConsumer vc, int overlay, float radius, float y0, float y1,
                               float scroll, float alpha) {
        int bottom = argb(alpha, 1.0F, 1.0F, 1.0F);
        int top = argb(0.0F, 1.0F, 1.0F, 1.0F);
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
    private static void lintel(PoseStack.Pose p, VertexConsumer vc, int overlay, int light,
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

    private static void box(PoseStack.Pose p, VertexConsumer vc, int overlay, int light,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        boxTinted(p, vc, overlay, light, 0xFFFFFFFF, x0, y0, z0, x1, y1, z1);
    }

    /** Une boite ; chaque face prend toute la texture. */
    private static void boxTinted(PoseStack.Pose p, VertexConsumer vc, int overlay, int light, int color,
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
