package com.emerald.client;

import com.emerald.block.ArcPortalBlock;
import com.emerald.block.entity.ArcPortalBlockEntity;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import static com.emerald.client.PortalMesh.*;

/**
 * Les portails d'Arcencium (ArcPortalBlock, cahier §84), aux textures de
 * tools/arc_porte_textures.py : le metal noir de la matiere de l'Arcencium, ses fissures
 * qui s'allument d'un arc-en-ciel qui COURT le long du metal, et un voile a la couleur du
 * portail -- or pour l'Heure Doree, nuit etoilee pour les brumes, aube pour le rappel.
 *
 *   ANNEAU  -- une porte ronde, debout sur un seuil de gangue : 2,9 blocs, le voile dedans ;
 *   ARCHE   -- deux piliers et une voute en plein cintre, un passage de deux blocs ;
 *   PLATEAU -- un disque au sol, sa bande de metal, le voile couche et une colonne de lumiere.
 *
 * Reperes : l'origine au milieu du bloc, au sol ; +z regarde FACING.
 *
 * Les memes regles que la porte de Haven (HavenGateRenderer) : UNE TEXTURE A LA FOIS
 * (chaque piece ecrite en entier avant la suivante), et les lueurs qui ecrivent la
 * profondeur (entityTranslucent) sous les couches emissives -- Distant Horizons efface
 * sinon ce qui a le ciel derriere lui.
 */
public class ArcPortalRenderer implements BlockEntityRenderer<ArcPortalBlockEntity> {

    private static final ResourceLocation METAL = tex("metal");
    private static final ResourceLocation FISSURES = tex("fissures");
    private static final ResourceLocation PILIER = tex("pilier");
    private static final ResourceLocation PILIER_FISSURES = tex("pilier_fissures");
    private static final ResourceLocation BORD = tex("bord");
    private static final ResourceLocation SOCLE = tex("socle");
    private static final ResourceLocation LUEUR = tex("lueur");
    private static final ResourceLocation TOURBILLON = tex("tourbillon");
    private static final ResourceLocation ETOILES = tex("etoiles");
    private static final ResourceLocation COLONNE = tex("colonne");

    /** L'anneau : centre, rayons, epaisseur. */
    public static final float RING_CY = 1.52F;
    public static final float RING_OUT = 1.42F;
    public static final float RING_IN = 1.06F;
    private static final float RING_DEPTH = 0.16F;
    /** L'arche : ses mesures sont celles du bloc (ArcPortalBlock), que le passage (ArcPortals) partage. */
    private static final float ARCH_HALF = ArcPortalBlock.ARCH_HALF;
    private static final float ARCH_SPRING = ArcPortalBlock.ARCH_SPRING;
    private static final float ARCH_PILLAR = ArcPortalBlock.ARCH_PILLAR;
    private static final float ARCH_DEPTH = 0.2F;
    /** Le seuil de gangue sous l'anneau et l'arche. */
    private static final float SILL = 0.12F;
    /** Le plateau. */
    public static final float PAD_RADIUS = 1.42F;
    private static final float PAD_HEIGHT = 0.22F;

    /** Le passage de l'arche, vu de son centre : un rectangle coiffe d'un demi-cercle. */
    private static final float ARCH_CY = 1.45F;
    private static final float[][] ARCH_OUTLINE = archOutline(56);

    /** Les couleurs d'un portail : sa lueur, son tourbillon, ses etoiles. */
    private record Colors(int glow, int swirl, int stars) {
    }

    public ArcPortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "textures/block/arc_porte/" + name + ".png");
    }

    private static Colors colors(ArcPortalBlock.Tint tint) {
        return switch (tint) {
            case DOREE -> new Colors(argb(0.88F, 1.0F, 0.68F, 0.18F), argb(0.8F, 1.0F, 0.93F, 0.6F),
                    argb(0.95F, 1.0F, 0.96F, 0.78F));
            case ETOILEE -> new Colors(argb(0.93F, 0.19F, 0.12F, 0.52F), argb(0.62F, 0.5F, 0.68F, 1.0F),
                    argb(1.0F, 1.0F, 1.0F, 1.0F));
            case AUBE -> new Colors(argb(0.88F, 1.0F, 0.58F, 0.5F), argb(0.75F, 1.0F, 0.88F, 0.72F),
                    argb(0.92F, 1.0F, 0.97F, 0.9F));
            // le depart du QG : les couleurs des vitres de la borne
            case DEFI -> new Colors(argb(0.9F, 0.86F, 0.12F, 0.1F), argb(0.72F, 1.0F, 0.6F, 0.42F),
                    argb(0.92F, 1.0F, 0.9F, 0.8F));
            case LIBRE -> new Colors(argb(0.9F, 0.1F, 0.38F, 0.95F), argb(0.68F, 0.55F, 0.86F, 1.0F),
                    argb(0.92F, 0.9F, 0.96F, 1.0F));
        };
    }

    @Override
    public void render(ArcPortalBlockEntity portal, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = portal.getBlockState();
        if (!(state.getBlock() instanceof ArcPortalBlock) || portal.getLevel() == null) {
            return;
        }
        float time = ((portal.getLevel().getGameTime() % 72000L) + partialTick) / 20.0F;
        Direction facing = state.getValue(ArcPortalBlock.FACING);
        ArcPortalBlock.Tint tint = state.getValue(ArcPortalBlock.TEINTE);
        Colors c = colors(tint);
        boolean starry = tint == ArcPortalBlock.Tint.ETOILEE;
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        switch (state.getValue(ArcPortalBlock.STYLE)) {
            case ANNEAU -> ring(pose, buffers, light, overlay, time, c, starry);
            case ARCHE -> arch(pose, buffers, light, overlay, time, c, starry);
            case PLATEAU -> pad(pose, buffers, light, overlay, time, c, starry, cameraInColumn(portal));
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ArcPortalBlockEntity portal) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(ArcPortalBlockEntity portal) {
        return new AABB(portal.getBlockPos()).inflate(2.0, 0.0, 2.0).expandTowards(0.0, 4.5, 0.0);
    }

    // ================================================================ l'anneau

    private static void ring(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time,
                             Colors c, boolean starry) {
        PoseStack.Pose p = pose.last();
        VertexConsumer socle = buffers.getBuffer(RenderType.entityCutoutNoCull(SOCLE));
        box(p, socle, overlay, light, -0.8F, 0.0F, -0.42F, 0.8F, SILL, 0.42F);
        VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        bandFaces(p, metal, overlay, light, RING_CY, RING_IN, RING_OUT, RING_DEPTH, 0.0F, Mth.TWO_PI, 6.0F, -1.0F);
        VertexConsumer bord = buffers.getBuffer(RenderType.entityCutoutNoCull(BORD));
        bandEdges(p, bord, overlay, light, RING_CY, RING_IN, RING_OUT, RING_DEPTH, 0.0F, Mth.TWO_PI, 6.0F);
        VertexConsumer cracks = buffers.getBuffer(RenderType.entityTranslucentEmissive(FISSURES));
        bandFaces(p, cracks, overlay, FULL, RING_CY, RING_IN, RING_OUT, RING_DEPTH + 0.004F, 0.0F, Mth.TWO_PI, 6.0F, time);
        // le voile : la lueur (qui ecrit la profondeur), le tourbillon des deux cotes, les etoiles
        disc(p, buffers.getBuffer(RenderType.entityTranslucent(LUEUR)), overlay, 0, RING_CY, 0.0F, RING_IN,
                time * 0.3F, 1.0F, c.glow());
        VertexConsumer swirl = buffers.getBuffer(RenderType.entityTranslucentEmissive(TOURBILLON));
        disc(p, swirl, overlay, 0, RING_CY, 0.012F, RING_IN, -time * 0.8F, 1.0F, c.swirl());
        disc(p, swirl, overlay, 0, RING_CY, -0.012F, RING_IN, time * 0.8F, 1.0F, c.swirl());
        VertexConsumer stars = buffers.getBuffer(RenderType.entityTranslucentEmissive(ETOILES));
        int twinkle = twinkle(c, starry, time);
        disc(p, stars, overlay, 0, RING_CY, 0.02F, RING_IN * 0.97F, time * 0.12F, starry ? 0.75F : 1.0F, twinkle);
        disc(p, stars, overlay, 0, RING_CY, -0.02F, RING_IN * 0.97F, -time * 0.12F, starry ? 0.75F : 1.0F, twinkle);
    }

    // ================================================================ l'arche

    private static void arch(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time,
                             Colors c, boolean starry) {
        PoseStack.Pose p = pose.last();
        float x0 = ARCH_HALF;
        float x1 = ARCH_HALF + ARCH_PILLAR;
        float d = ARCH_DEPTH;
        VertexConsumer socle = buffers.getBuffer(RenderType.entityCutoutNoCull(SOCLE));
        box(p, socle, overlay, light, -x1 - 0.14F, 0.0F, -0.46F, x1 + 0.14F, SILL, 0.46F);
        // les deux pieds des piliers
        box(p, socle, overlay, light, -x1 - 0.07F, SILL, -d - 0.07F, -x0 + 0.07F, SILL + 0.2F, d + 0.07F);
        box(p, socle, overlay, light, x0 - 0.07F, SILL, -d - 0.07F, x1 + 0.07F, SILL + 0.2F, d + 0.07F);
        VertexConsumer pilier = buffers.getBuffer(RenderType.entityCutoutNoCull(PILIER));
        box(p, pilier, overlay, light, -x1, SILL + 0.2F, -d, -x0, ARCH_SPRING, d);
        box(p, pilier, overlay, light, x0, SILL + 0.2F, -d, x1, ARCH_SPRING, d);
        VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        bandFaces(p, metal, overlay, light, ARCH_SPRING, x0, x1, d, 0.0F, Mth.PI, 6.0F, -1.0F);
        VertexConsumer bord = buffers.getBuffer(RenderType.entityCutoutNoCull(BORD));
        bandEdges(p, bord, overlay, light, ARCH_SPRING, x0, x1, d, 0.0F, Mth.PI, 6.0F);
        VertexConsumer cracks = buffers.getBuffer(RenderType.entityTranslucentEmissive(FISSURES));
        bandFaces(p, cracks, overlay, FULL, ARCH_SPRING, x0, x1, d + 0.004F, 0.0F, Mth.PI, 6.0F, time);
        VertexConsumer pillarCracks = buffers.getBuffer(RenderType.entityTranslucentEmissive(PILIER_FISSURES));
        pillarCracks(p, pillarCracks, overlay, -x1, -x0, SILL + 0.2F, ARCH_SPRING, d + 0.004F, time, 0.5F);
        pillarCracks(p, pillarCracks, overlay, x0, x1, SILL + 0.2F, ARCH_SPRING, d + 0.004F, time, 0.0F);
        // le voile, a la forme du passage
        fan(p, buffers.getBuffer(RenderType.entityTranslucent(LUEUR)), overlay, 0.0F, time * 0.2F, 2.6F, c.glow());
        VertexConsumer swirl = buffers.getBuffer(RenderType.entityTranslucentEmissive(TOURBILLON));
        fan(p, swirl, overlay, 0.012F, -time * 0.7F, 1.6F, c.swirl());
        fan(p, swirl, overlay, -0.012F, time * 0.7F, 1.6F, c.swirl());
        VertexConsumer stars = buffers.getBuffer(RenderType.entityTranslucentEmissive(ETOILES));
        int twinkle = twinkle(c, starry, time);
        fan(p, stars, overlay, 0.02F, time * 0.1F, starry ? 1.2F : 1.7F, twinkle);
        fan(p, stars, overlay, -0.02F, -time * 0.1F, starry ? 1.2F : 1.7F, twinkle);
    }

    /** Les fissures d'un pilier, sur ses deux grandes faces : l'arc-en-ciel monte le long du pilier. */
    private static void pillarCracks(PoseStack.Pose p, VertexConsumer vc, int overlay, float x0, float x1,
                                     float y0, float y1, float z, float time, float phase) {
        int bottom = rainbow(time * 0.12F + phase, 1.0F);
        int top = rainbow(time * 0.12F + phase + 0.3F, 1.0F);
        for (int side = -1; side <= 1; side += 2) {
            float zz = z * side;
            vertex(p, vc, x0, y0, zz, bottom, 0.0F, 1.0F, overlay, FULL, 0, 0, side);
            vertex(p, vc, x1, y0, zz, bottom, 1.0F, 1.0F, overlay, FULL, 0, 0, side);
            vertex(p, vc, x1, y1, zz, top, 1.0F, 0.0F, overlay, FULL, 0, 0, side);
            vertex(p, vc, x0, y1, zz, top, 0.0F, 0.0F, overlay, FULL, 0, 0, side);
        }
    }

    /** Le contour du passage de l'arche, en partant d'en bas, vu de (0, ARCH_CY). */
    private static float[][] archOutline(int n) {
        float[][] out = new float[n][2];
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + 2 * Math.PI * i / n;
            double dx = Math.cos(a);
            double dy = Math.sin(a);
            double t = 0.0;
            while (t < 4.0 && insideArch(dx * (t + 0.005), ARCH_CY + dy * (t + 0.005))) {
                t += 0.005;
            }
            out[i][0] = (float) (dx * t);
            out[i][1] = (float) (ARCH_CY + dy * t);
        }
        return out;
    }

    private static boolean insideArch(double x, double y) {
        if (y < SILL || Math.abs(x) > ARCH_HALF) {
            return false;
        }
        return y <= ARCH_SPRING || x * x + (y - ARCH_SPRING) * (y - ARCH_SPRING) <= ARCH_HALF * ARCH_HALF;
    }

    /** Le voile de l'arche : un eventail sur son contour, l'image tournee de « angle ». */
    private static void fan(PoseStack.Pose p, VertexConsumer vc, int overlay, float z, float angle, float uvRadius,
                            int color) {
        float ca = Mth.cos(angle);
        float sa = Mth.sin(angle);
        float k = 0.5F / uvRadius;
        for (int i = 0; i < ARCH_OUTLINE.length; i++) {
            float[] a = ARCH_OUTLINE[i];
            float[] b = ARCH_OUTLINE[(i + 1) % ARCH_OUTLINE.length];
            float ax = a[0], ay = a[1] - ARCH_CY, bx = b[0], by = b[1] - ARCH_CY;
            quad(p, vc, overlay, FULL, color, 0, 0, 1,
                    0.0F, ARCH_CY, z, 0.5F, 0.5F,
                    a[0], a[1], z, 0.5F + (ax * ca - ay * sa) * k, 0.5F + (ax * sa + ay * ca) * k,
                    b[0], b[1], z, 0.5F + (bx * ca - by * sa) * k, 0.5F + (bx * sa + by * ca) * k,
                    0.0F, ARCH_CY, z, 0.5F, 0.5F);
        }
    }

    // ================================================================ le plateau

    /** La camera est-elle dans la colonne ? On la voyait alors de l'interieur (HavenGateRenderer). */
    private static boolean cameraInColumn(ArcPortalBlockEntity portal) {
        net.minecraft.world.phys.Vec3 eye = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        net.minecraft.core.BlockPos pos = portal.getBlockPos();
        double dx = eye.x - (pos.getX() + 0.5);
        double dz = eye.z - (pos.getZ() + 0.5);
        double dy = eye.y - pos.getY();
        return dx * dx + dz * dz < 1.1 * 1.1 && dy > -0.5 && dy < 5.0;
    }

    private static void pad(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time,
                            Colors c, boolean starry, boolean inColumn) {
        PoseStack.Pose p = pose.last();
        float r = PAD_RADIUS;
        float h = PAD_HEIGHT;
        VertexConsumer socle = buffers.getBuffer(RenderType.entityCutoutNoCull(SOCLE));
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            quad(p, socle, overlay, light, 0xFFFFFFFF, 0, 1, 0,
                    0, h, 0, 0.5F, 0.5F,
                    r * c1, h, r * s1, 0.5F + 0.5F * c1, 0.5F + 0.5F * s1,
                    r * c0, h, r * s0, 0.5F + 0.5F * c0, 0.5F + 0.5F * s0,
                    0, h, 0, 0.5F, 0.5F);
        }
        VertexConsumer metal = buffers.getBuffer(RenderType.entityCutoutNoCull(METAL));
        cylinderBand(p, metal, overlay, light, r, 0.0F, h, 8.0F, -1.0F);
        VertexConsumer cracks = buffers.getBuffer(RenderType.entityTranslucentEmissive(FISSURES));
        cylinderBand(p, cracks, overlay, FULL, r + 0.004F, 0.0F, h, 8.0F, time);
        // le voile couche : la lueur, le tourbillon, les etoiles
        disc(p, buffers.getBuffer(RenderType.entityTranslucent(LUEUR)), overlay, 0, h + 0.01F, 0.0F, r * 0.86F,
                time * 0.3F, 1.0F, c.glow(), true);
        disc(p, buffers.getBuffer(RenderType.entityTranslucentEmissive(TOURBILLON)), overlay, 0, h + 0.02F, 0.0F,
                r * 0.86F, -time * 0.8F, 1.0F, c.swirl(), true);
        disc(p, buffers.getBuffer(RenderType.entityTranslucentEmissive(ETOILES)), overlay, 0, h + 0.03F, 0.0F,
                r * 0.84F, time * 0.12F, starry ? 0.75F : 1.0F, twinkle(c, starry, time), true);
        if (inColumn) {
            return;
        }
        VertexConsumer column = buffers.getBuffer(RenderType.entityTranslucent(COLONNE));
        column(p, column, overlay, 0.7F, h, 3.4F, -time * 0.5F, 0.9F, c.glow() & 0xFFFFFF);
    }

    // ================================================================ outils

    /** Le scintillement des etoiles : franc dans la nuit etoilee, discret ailleurs. */
    private static int twinkle(Colors c, boolean starry, float time) {
        float base = (c.stars() >>> 24) / 255.0F;
        float k = starry ? 0.75F + 0.25F * Mth.sin(time * 3.1F) : 0.4F + 0.2F * Mth.sin(time * 2.3F);
        return withAlpha(c.stars(), base * k);
    }

    /**
     * Les deux faces d'une bande en arc (plan xy), de l'angle « from » a « to » : u le long de
     * la bande (« repeat » fois par tour entier), v de l'interieur a l'exterieur. Si
     * « rainbowTime » est positif, chaque troncon prend sa couleur d'arc-en-ciel, qui court.
     */
    private static void bandFaces(PoseStack.Pose p, VertexConsumer vc, int overlay, int light,
                                  float cy, float inner, float outer, float depth, float from, float to,
                                  float repeat, float rainbowTime) {
        int n = Math.max(6, Math.round(SEGMENTS * (to - from) / Mth.TWO_PI));
        for (int i = 0; i < n; i++) {
            float a0 = from + (to - from) * i / n;
            float a1 = from + (to - from) * (i + 1) / n;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = repeat * (a0 - from) / Mth.TWO_PI;
            float u1 = repeat * (a1 - from) / Mth.TWO_PI;
            int color = rainbowTime < 0 ? 0xFFFFFFFF
                    : rainbow((a0 + a1) * 0.5F / Mth.TWO_PI + rainbowTime * 0.12F, 1.0F);
            for (int side = -1; side <= 1; side += 2) {
                float z = depth * side;
                quad(p, vc, overlay, light, color, 0, 0, side,
                        inner * c0, cy + inner * s0, z, u0, 1.0F,
                        outer * c0, cy + outer * s0, z, u0, 0.0F,
                        outer * c1, cy + outer * s1, z, u1, 0.0F,
                        inner * c1, cy + inner * s1, z, u1, 1.0F);
            }
        }
    }

    /** Les tranches exterieure et interieure d'une bande en arc. */
    private static void bandEdges(PoseStack.Pose p, VertexConsumer vc, int overlay, int light,
                                  float cy, float inner, float outer, float depth, float from, float to,
                                  float repeat) {
        int n = Math.max(6, Math.round(SEGMENTS * (to - from) / Mth.TWO_PI));
        for (int i = 0; i < n; i++) {
            float a0 = from + (to - from) * i / n;
            float a1 = from + (to - from) * (i + 1) / n;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = repeat * (a0 - from) / Mth.TWO_PI;
            float u1 = repeat * (a1 - from) / Mth.TWO_PI;
            float mc = (c0 + c1) * 0.5F, ms = (s0 + s1) * 0.5F;
            quad(p, vc, overlay, light, 0xFFFFFFFF, mc, ms, 0,
                    outer * c0, cy + outer * s0, -depth, u0, 0.0F,
                    outer * c1, cy + outer * s1, -depth, u1, 0.0F,
                    outer * c1, cy + outer * s1, depth, u1, 1.0F,
                    outer * c0, cy + outer * s0, depth, u0, 1.0F);
            quad(p, vc, overlay, light, 0xFFFFFFFF, -mc, -ms, 0,
                    inner * c0, cy + inner * s0, -depth, u0, 0.0F,
                    inner * c0, cy + inner * s0, depth, u0, 1.0F,
                    inner * c1, cy + inner * s1, depth, u1, 1.0F,
                    inner * c1, cy + inner * s1, -depth, u1, 0.0F);
        }
    }

    /** La bande autour du plateau (un cylindre de y0 a y1), coloree comme bandFaces. */
    private static void cylinderBand(PoseStack.Pose p, VertexConsumer vc, int overlay, int light, float radius,
                                     float y0, float y1, float repeat, float rainbowTime) {
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            float u0 = repeat * i / SEGMENTS;
            float u1 = repeat * (i + 1) / SEGMENTS;
            float mc = (c0 + c1) * 0.5F, ms = (s0 + s1) * 0.5F;
            int color = rainbowTime < 0 ? 0xFFFFFFFF : rainbow((float) i / SEGMENTS + rainbowTime * 0.12F, 1.0F);
            quad(p, vc, overlay, light, color, mc, 0, ms,
                    radius * c0, y0, radius * s0, u0, 1.0F,
                    radius * c0, y1, radius * s0, u0, 0.0F,
                    radius * c1, y1, radius * s1, u1, 0.0F,
                    radius * c1, y0, radius * s1, u1, 1.0F);
        }
    }
}
