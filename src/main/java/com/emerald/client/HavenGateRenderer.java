package com.emerald.client;

import com.emerald.block.HavenGateBlock;
import com.emerald.block.entity.HavenGateBlockEntity;
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

    /**
     * L'anneau, choisi par le joueur pour la victoire, « un peu petit, il faudrait
     * l'agrandir de 20 % » (photos du 21 sept.) : 3,7 blocs de haut et de large.
     */
    private static final float RING_SCALE = 1.2F;
    /** Les montants du plateau du portail : 2,3 blocs, au-dessus de la tete de qui s'y tient. */
    private static final float POST = 2.3F;

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
            case PORTAIL -> pad(pose, buffers, light, overlay, time, cameraInBeam(gate));
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

    /**
     * La camera est-elle dans la colonne de lumiere ? Debout sur le plateau pour regarder en
     * haut, on la voyait de l'interieur : toute la vue teintee (photo du 21 sept., nuit).
     */
    private static boolean cameraInBeam(HavenGateBlockEntity gate) {
        net.minecraft.world.phys.Vec3 eye = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        net.minecraft.core.BlockPos pos = gate.getBlockPos();
        double dx = eye.x - (pos.getX() + 0.5);
        double dz = eye.z - (pos.getZ() + 0.5);
        double dy = eye.y - pos.getY();
        return dx * dx + dz * dz < 1.2 * 1.2 && dy > -0.5 && dy < 6.0;
    }

    private static void pad(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time,
                            boolean inBeam) {
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
            // des montants plus hauts qu'un joueur : leurs chapeaux lumineux ne tombent pas a hauteur d'yeux
            box(p, pilier, overlay, light, x - 0.12F, height, z - 0.12F, x + 0.12F, height + POST, z + 0.12F);
        }
        VertexConsumer caps = buffers.getBuffer(RenderType.entityTranslucent(LUMIERE));
        for (int k = 0; k < 4; k++) {
            float a = Mth.HALF_PI * k + Mth.PI / 4.0F;
            float x = 1.28F * Mth.cos(a);
            float z = 1.28F * Mth.sin(a);
            boxTinted(p, caps, overlay, FULL, argb(0.9F, 1.0F, 1.0F, 1.0F),
                    x - 0.14F, height + POST, z - 0.14F, x + 0.14F, height + POST + 0.22F, z + 0.14F);
        }
        // la lueur du plateau, puis la colonne : deux cylindres a circuits qui montent et
        // s'effacent vers le haut, le coeur d'abord (il ecrit la profondeur, l'enveloppe se pose dessus)
        disc(p, buffers.getBuffer(RenderType.entityTranslucent(LUMIERE)), overlay, 0, height + 0.01F, 0, 1.2F,
                time * 0.5F, 1.0F, argb(0.85F, 1.0F, 1.0F, 1.0F), true);
        if (inBeam) {
            return;
        }
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
}
