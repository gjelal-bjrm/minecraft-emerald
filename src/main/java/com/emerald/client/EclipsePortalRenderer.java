package com.emerald.client;

import com.emerald.block.EclipsePortalBlock;
import com.emerald.block.entity.EclipsePortalBlockEntity;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static com.emerald.client.PortalMesh.FULL;
import static com.emerald.client.PortalMesh.argb;
import static com.emerald.client.PortalMesh.quad;
import static com.emerald.client.PortalMesh.vertex;

/**
 * LE PORTAIL DE L'ECLIPSE (cahier §88), aux textures de tools/eclipse_portal_textures.py.
 *
 * « Pas des bouches, rien de vivant : de vrais portails d'horreur. » Ce qui est dessine :
 *   - un VIDE en ogive, noir, ou tournent deux fumees cramoisies en sens contraires ;
 *   - un LISERE rouge sur son bord, qui vacille ;
 *   - un CADRE de pierre noire en blocs disjoints, de travers, dont les fissures rougeoient
 *     chacune a son rythme ;
 *   - deux CHAINES de fer en croix devant le vide, qui se balancent : le portail est tenu
 *     ferme, et il cede ;
 *   - des eclats de pierre en ORBITE lente, et de la CENDRE au sol.
 * Il s'ouvre en une seconde et demie depuis un point.
 *
 * Reperes : l'origine au milieu du bloc, au sol ; +z regarde FACING ; le vide est dans
 * le plan z = 0. Memes regles que les arches (ArcPortalRenderer) : une texture a la fois,
 * et les couches emissives seulement sur une surface qui a deja ecrit la profondeur.
 */
public class EclipsePortalRenderer implements BlockEntityRenderer<EclipsePortalBlockEntity> {

    private static final ResourceLocation PIERRE = tex("pierre");
    private static final ResourceLocation FISSURES = tex("pierre_fissures");
    private static final ResourceLocation VIDE = tex("vide");
    private static final ResourceLocation FUMEE = tex("fumee");
    private static final ResourceLocation BORD = tex("bord");
    private static final ResourceLocation CHAINE = tex("chaine");
    private static final ResourceLocation CENDRE = tex("cendre");

    private static final float H = EclipsePortalBlock.VOID_HALF;
    private static final float SPRING = EclipsePortalBlock.VOID_SPRING;
    private static final float TOP = EclipsePortalBlock.VOID_TOP;
    private static final float FOOT = 0.12F;
    /** L'ogive : deux arcs de rayon R, centres a (±(R - H), SPRING), qui se rejoignent en TOP. */
    private static final float R;
    private static final float ARC_CX;
    private static final float APEX_ANGLE;
    private static final int ARC_STEPS = 9;
    /** Le contour du vide, de bas en gauche, jusqu'en bas a droite (sens trigonometrique inverse). */
    private static final float[][] OUTLINE;
    private static final float CX = 0.0F;
    private static final float CY = 2.2F;

    static {
        float rise = TOP - SPRING;
        R = (rise * rise + H * H) / (2.0F * H);
        ARC_CX = R - H;
        APEX_ANGLE = (float) Math.acos(-ARC_CX / R);
        int n = 2 + ARC_STEPS * 2 + 1 + 2;
        OUTLINE = new float[n][];
        int i = 0;
        OUTLINE[i++] = new float[]{-H, FOOT};
        OUTLINE[i++] = new float[]{-H, SPRING};
        for (int k = 1; k <= ARC_STEPS; k++) {             // arc gauche, centre a droite
            float a = (float) Math.PI - ((float) Math.PI - APEX_ANGLE) * k / (ARC_STEPS + 1);
            OUTLINE[i++] = new float[]{ARC_CX + R * Mth.cos(a), SPRING + R * Mth.sin(a)};
        }
        OUTLINE[i++] = new float[]{0.0F, TOP};
        for (int k = ARC_STEPS; k >= 1; k--) {             // arc droit, miroir
            float a = (float) Math.PI - ((float) Math.PI - APEX_ANGLE) * k / (ARC_STEPS + 1);
            OUTLINE[i++] = new float[]{-(ARC_CX + R * Mth.cos(a)), SPRING + R * Mth.sin(a)};
        }
        OUTLINE[i++] = new float[]{H, SPRING};
        OUTLINE[i] = new float[]{H, FOOT};
    }

    /** Quand ce client a vu le portail pour la premiere fois : il s'ouvre depuis un point. */
    private static final Map<BlockPos, Long> FIRST_SEEN = new ConcurrentHashMap<>();
    private static final float OPENING = 30.0F;

    public EclipsePortalRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                "textures/block/eclipse_porte/" + name + ".png");
    }

    @Override
    public void render(EclipsePortalBlockEntity portal, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        BlockState state = portal.getBlockState();
        if (!(state.getBlock() instanceof EclipsePortalBlock) || portal.getLevel() == null) {
            return;
        }
        long now = portal.getLevel().getGameTime();
        float time = ((now % 72000L) + partialTick) / 20.0F;
        long first = FIRST_SEEN.computeIfAbsent(portal.getBlockPos().immutable(), p -> now);
        float open = Mth.clamp((now - first + partialTick) / OPENING, 0.0F, 1.0F);
        open = open * open * (3.0F - 2.0F * open);
        if (FIRST_SEEN.size() > 64) {
            FIRST_SEEN.keySet().removeIf(p -> !EclipsePortalBlockEntity.CLIENT_OPEN.contains(p));
        }
        Direction facing = state.getValue(EclipsePortalBlock.FACING);
        long seed = portal.getBlockPos().asLong();

        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        // la cendre au sol ne grandit pas : elle etait la avant que le portail ne s'ouvre
        ash(pose, buffers, light, overlay);
        pose.translate(0.0F, CY, 0.0F);
        pose.scale(0.2F + 0.8F * open, 0.2F + 0.8F * open, 1.0F);
        pose.translate(0.0F, -CY, 0.0F);
        voidLayers(pose, buffers, overlay, time, open);
        rim(pose, buffers, overlay, time);
        frame(pose, buffers, light, overlay, time, seed);
        chains(pose, buffers, light, overlay, time);
        shards(pose, buffers, light, overlay, time, seed);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(EclipsePortalBlockEntity portal) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(EclipsePortalBlockEntity portal) {
        return new AABB(portal.getBlockPos()).inflate(3.5, 0.0, 3.5).expandTowards(0.0, 6.0, 0.0);
    }

    // =================================================================== le vide

    /** Le vide noir, puis deux fumees qui tournent en sens contraires, devant et derriere. */
    private static void voidLayers(PoseStack pose, MultiBufferSource buffers, int overlay, float time, float open) {
        PoseStack.Pose p = pose.last();
        // LE VIDE SANS LUMIERE. Dessine en pleine lumiere, Complementary l'eclairait comme
        // une lampe : un bordeaux uniforme a chaque photo, fumee ou pas. Sans lumiere, il
        // reste le trou noir qu'il doit etre ; seules les fumees emissives y brillent.
        fan(p, buffers.getBuffer(RenderType.entityTranslucent(VIDE)), overlay, 0.0F, time * 0.03F, 0.2F,
                argb(0.98F, 1.0F, 1.0F, 1.0F), LightTexture.pack(0, 0));
        // LE VIDE RESTE NOIR. A la premiere photo, deux fumees presque opaques, repetees
        // quatre fois sur l'ogive, le peignaient d'un rouge-violet uniforme : on ne voyait
        // plus un trou mais une porte peinte. Une seule image par ogive, des filets minces.
        float pulse = 0.85F + 0.1F * Mth.sin(time * 1.3F) + 0.05F * Mth.sin(time * 3.7F);
        int smoke = argb(pulse * open, 1.0F, 1.0F, 1.0F);
        VertexConsumer swirl = buffers.getBuffer(RenderType.entityTranslucentEmissive(FUMEE));
        for (float z : new float[]{0.012F, -0.012F}) {
            fan(p, swirl, overlay, z, time * 0.45F, 0.19F, smoke);
            fan(p, swirl, overlay, z * 1.6F, -time * 0.28F, 0.21F, argb(pulse * 0.6F * open, 1.0F, 0.8F, 0.9F));
        }
    }

    /**
     * Un eventail sur le contour de l'ogive, l'image tournee de « angle » autour du centre :
     * « scale » dit combien d'image couvre un bloc (petit = image agrandie).
     */
    private static void fan(PoseStack.Pose p, VertexConsumer vc, int overlay, float z, float angle, float scale,
                            int color) {
        fan(p, vc, overlay, z, angle, scale, color, FULL);
    }

    private static void fan(PoseStack.Pose p, VertexConsumer vc, int overlay, float z, float angle, float scale,
                            int color, int light) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        for (int i = 0; i < OUTLINE.length - 1; i++) {
            float[] a = OUTLINE[i];
            float[] b = OUTLINE[i + 1];
            // un triangle, ecrit comme un quadrilatere dont deux sommets se confondent
            vertex(p, vc, CX, CY, z, color, uv(0, 0, cos, sin, scale, true), uv(0, 0, cos, sin, scale, false),
                    overlay, light, 0, 0, 1);
            vertex(p, vc, a[0], a[1], z, color, uv(a[0] - CX, a[1] - CY, cos, sin, scale, true),
                    uv(a[0] - CX, a[1] - CY, cos, sin, scale, false), overlay, light, 0, 0, 1);
            vertex(p, vc, b[0], b[1], z, color, uv(b[0] - CX, b[1] - CY, cos, sin, scale, true),
                    uv(b[0] - CX, b[1] - CY, cos, sin, scale, false), overlay, light, 0, 0, 1);
            vertex(p, vc, b[0], b[1], z, color, uv(b[0] - CX, b[1] - CY, cos, sin, scale, true),
                    uv(b[0] - CX, b[1] - CY, cos, sin, scale, false), overlay, light, 0, 0, 1);
        }
        // et le bas, du coin droit au coin gauche
        float[] a = OUTLINE[OUTLINE.length - 1];
        float[] b = OUTLINE[0];
        vertex(p, vc, CX, CY, z, color, uv(0, 0, cos, sin, scale, true), uv(0, 0, cos, sin, scale, false),
                overlay, light, 0, 0, 1);
        vertex(p, vc, a[0], a[1], z, color, uv(a[0] - CX, a[1] - CY, cos, sin, scale, true),
                uv(a[0] - CX, a[1] - CY, cos, sin, scale, false), overlay, light, 0, 0, 1);
        vertex(p, vc, b[0], b[1], z, color, uv(b[0] - CX, b[1] - CY, cos, sin, scale, true),
                uv(b[0] - CX, b[1] - CY, cos, sin, scale, false), overlay, light, 0, 0, 1);
        vertex(p, vc, b[0], b[1], z, color, uv(b[0] - CX, b[1] - CY, cos, sin, scale, true),
                uv(b[0] - CX, b[1] - CY, cos, sin, scale, false), overlay, light, 0, 0, 1);
    }

    private static float uv(float dx, float dy, float cos, float sin, float scale, boolean u) {
        float rx = cos * dx - sin * dy;
        float ry = sin * dx + cos * dy;
        return 0.5F + (u ? rx : -ry) * scale;
    }

    /** Le lisere : une bande rouge le long du bord du vide, qui vacille. */
    private static void rim(PoseStack pose, MultiBufferSource buffers, int overlay, float time) {
        PoseStack.Pose p = pose.last();
        float flicker = 0.65F + 0.25F * Mth.sin(time * 5.1F) * Mth.sin(time * 2.3F + 1.0F)
                + 0.1F * Mth.sin(time * 17.0F);
        int color = argb(Mth.clamp(flicker, 0.3F, 1.0F), 1.0F, 1.0F, 1.0F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentEmissive(BORD));
        float width = 0.16F;
        for (float z : new float[]{0.02F, -0.02F}) {
            float v = 0.0F;
            for (int i = 0; i < OUTLINE.length - 1; i++) {
                float[] a = OUTLINE[i];
                float[] b = OUTLINE[i + 1];
                float[] na = outward(a);
                float[] nb = outward(b);
                float len = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
                quad(p, vc, overlay, FULL, color, 0, 0, 1,
                        a[0] - na[0] * width * 0.3F, a[1] - na[1] * width * 0.3F, z, 0.0F, v,
                        b[0] - nb[0] * width * 0.3F, b[1] - nb[1] * width * 0.3F, z, 0.0F, v + len,
                        b[0] + nb[0] * width, b[1] + nb[1] * width, z, 1.0F, v + len,
                        a[0] + na[0] * width, a[1] + na[1] * width, z, 1.0F, v);
                v += len;
            }
        }
    }

    /** La direction qui s'eloigne du centre du vide, en ce point du contour. */
    private static float[] outward(float[] point) {
        float dx = point[0] - CX;
        float dy = point[1] - CY;
        if (point[1] <= FOOT + 0.01F) {
            dy = 0.0F;                              // les deux pieds : vers les cotes seulement
        }
        float len = (float) Math.hypot(dx, dy);
        return len < 1.0E-4F ? new float[]{0.0F, 1.0F} : new float[]{dx / len, dy / len};
    }

    // ================================================================= le cadre

    /** Des blocs de pierre noire le long de l'ogive, de travers, fendus de rouge. */
    private static void frame(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time,
                              long seed) {
        int lit = Math.max(light, LightTexture.pack(6, 0));
        // DES BLOCS, PAS DES CONFETTIS. A la premiere photo, dix-sept petits cubes espaces,
        // chacun couvert de la texture de fissures entiere, se lisaient comme des paillettes
        // rouges. Vingt-deux blocs larges qui se chevauchent, et des fissures a l'echelle
        // du bloc (boxUv) : un cadre de pierre, fendu.
        Random random = new Random(seed);
        int stones = 22;
        float[][] placed = new float[stones][];
        for (int i = 0; i < stones; i++) {
            float t = (i + 0.5F) / stones;
            float[] at = along(t);
            float[] n = outward(at);
            float tilt = (random.nextFloat() - 0.5F) * 22.0F;
            float w = 0.62F + random.nextFloat() * 0.34F;
            float h = 0.48F + random.nextFloat() * 0.24F;
            float d = 0.55F + random.nextFloat() * 0.3F;
            float push = 0.26F + random.nextFloat() * 0.1F;
            placed[i] = new float[]{at[0] + n[0] * push, at[1] + n[1] * push,
                    (float) Math.toDegrees(Math.atan2(n[1], n[0])) - 90.0F + tilt, w, h, d,
                    random.nextFloat() * 6.28F};
        }
        // deux socles aux pieds
        VertexConsumer stone = buffers.getBuffer(RenderType.entityCutoutNoCull(PIERRE));
        for (float[] s : placed) {
            stoneBox(pose, stone, overlay, lit, 0xFFFFFFFF, s, 0.0F);
        }
        PoseStack.Pose p = pose.last();
        boxUv(p, stone, overlay, lit, 0xFFFFFFFF, -H - 0.85F, 0.0F, -0.55F, -H + 0.1F, 0.7F, 0.55F, 0.9F);
        boxUv(p, stone, overlay, lit, 0xFFFFFFFF, H - 0.1F, 0.0F, -0.55F, H + 0.85F, 0.7F, 0.55F, 0.9F);
        VertexConsumer cracks = buffers.getBuffer(RenderType.entityTranslucentEmissive(FISSURES));
        for (float[] s : placed) {
            float glow = 0.45F + 0.4F * Mth.sin(time * (1.1F + s[6] * 0.2F) + s[6]);
            stoneBox(pose, cracks, overlay, FULL, argb(Mth.clamp(glow, 0.12F, 0.9F), 1.0F, 1.0F, 1.0F), s, 0.006F);
        }
        float base = 0.5F + 0.3F * Mth.sin(time * 0.9F);
        int baseGlow = argb(base, 1.0F, 1.0F, 1.0F);
        boxUv(p, cracks, overlay, FULL, baseGlow, -H - 0.856F, -0.006F, -0.556F, -H + 0.106F, 0.706F, 0.556F, 0.9F);
        boxUv(p, cracks, overlay, FULL, baseGlow, H - 0.106F, -0.006F, -0.556F, H + 0.856F, 0.706F, 0.556F, 0.9F);
    }

    /**
     * Une boite dont chaque face prend autant de texture que sa taille (« scale » images par
     * bloc) : les fissures gardent la meme finesse sur un petit eclat et sur un gros bloc.
     */
    private static void boxUv(PoseStack.Pose p, VertexConsumer vc, int overlay, int light, int color,
                              float x0, float y0, float z0, float x1, float y1, float z1, float scale) {
        float w = (x1 - x0) * scale;
        float h = (y1 - y0) * scale;
        float d = (z1 - z0) * scale;
        quad(p, vc, overlay, light, color, 0, 0, 1,
                x0, y0, z1, 0, h, x1, y0, z1, w, h, x1, y1, z1, w, 0, x0, y1, z1, 0, 0);
        quad(p, vc, overlay, light, color, 0, 0, -1,
                x1, y0, z0, 0, h, x0, y0, z0, w, h, x0, y1, z0, w, 0, x1, y1, z0, 0, 0);
        quad(p, vc, overlay, light, color, -1, 0, 0,
                x0, y0, z0, 0, h, x0, y0, z1, d, h, x0, y1, z1, d, 0, x0, y1, z0, 0, 0);
        quad(p, vc, overlay, light, color, 1, 0, 0,
                x1, y0, z1, 0, h, x1, y0, z0, d, h, x1, y1, z0, d, 0, x1, y1, z1, 0, 0);
        quad(p, vc, overlay, light, color, 0, 1, 0,
                x0, y1, z1, 0, d, x1, y1, z1, w, d, x1, y1, z0, w, 0, x0, y1, z0, 0, 0);
        quad(p, vc, overlay, light, color, 0, -1, 0,
                x0, y0, z0, 0, d, x1, y0, z0, w, d, x1, y0, z1, w, 0, x0, y0, z1, 0, 0);
    }

    private static void stoneBox(PoseStack pose, VertexConsumer vc, int overlay, int light, int color, float[] s,
                                 float grow) {
        pose.pushPose();
        pose.translate(s[0], s[1], 0.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(s[2]));
        float w = s[3] / 2 + grow;
        float h = s[4] / 2 + grow;
        float d = s[5] / 2 + grow;
        boxUv(pose.last(), vc, overlay, light, color, -w, -h, -d, w, h, d, 0.9F);
        pose.popPose();
    }

    /** Un point du contour a la fraction « t » de sa longueur. */
    private static float[] along(float t) {
        float total = 0.0F;
        float[] lengths = new float[OUTLINE.length - 1];
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = (float) Math.hypot(OUTLINE[i + 1][0] - OUTLINE[i][0], OUTLINE[i + 1][1] - OUTLINE[i][1]);
            total += lengths[i];
        }
        float goal = t * total;
        for (int i = 0; i < lengths.length; i++) {
            if (goal <= lengths[i]) {
                float f = goal / lengths[i];
                return new float[]{Mth.lerp(f, OUTLINE[i][0], OUTLINE[i + 1][0]),
                        Mth.lerp(f, OUTLINE[i][1], OUTLINE[i + 1][1])};
            }
            goal -= lengths[i];
        }
        return OUTLINE[OUTLINE.length - 1];
    }

    // ================================================================ les chaines

    /** Deux chaines en croix devant le vide (et derriere), qui se balancent un peu. */
    private static void chains(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time) {
        int lit = Math.max(light, LightTexture.pack(12, 0));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CHAINE));
        PoseStack.Pose p = pose.last();
        float sway = 0.06F * Mth.sin(time * 0.8F);
        for (float z : new float[]{0.2F, -0.2F}) {
            chain(p, vc, overlay, lit, -H - 0.1F, SPRING + 0.9F, H + 0.15F, 0.55F, z, sway);
            chain(p, vc, overlay, lit, H + 0.1F, SPRING + 0.9F, -H - 0.15F, 0.55F, z, -sway);
        }
    }

    private static void chain(PoseStack.Pose p, VertexConsumer vc, int overlay, int light,
                              float x0, float y0, float x1, float y1, float z, float sway) {
        int segments = 8;
        float half = 0.14F;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        float nx = -dy / len * half;
        float ny = dx / len * half;
        for (int i = 0; i < segments; i++) {
            float t0 = (float) i / segments;
            float t1 = (float) (i + 1) / segments;
            // la chaine pend : un creux au milieu, et le balancement
            float s0 = Mth.sin(t0 * (float) Math.PI) * (0.14F + sway);
            float s1 = Mth.sin(t1 * (float) Math.PI) * (0.14F + sway);
            float ax = x0 + dx * t0;
            float ay = y0 + dy * t0 - s0;
            float bx = x0 + dx * t1;
            float by = y0 + dy * t1 - s1;
            float v0 = t0 * len * 1.6F;
            float v1 = t1 * len * 1.6F;
            quad(p, vc, overlay, light, 0xFFFFFFFF, 0, 0, 1,
                    ax - nx, ay - ny, z, 0.0F, v0,
                    bx - nx, by - ny, z, 0.0F, v1,
                    bx + nx, by + ny, z, 1.0F, v1,
                    ax + nx, ay + ny, z, 1.0F, v0);
        }
    }

    // ====================================================== eclats et cendre

    /** Des eclats de pierre en orbite lente, dans le plan du portail. */
    private static void shards(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time,
                               long seed) {
        int lit = Math.max(light, LightTexture.pack(6, 0));
        Random random = new Random(seed ^ 0x5DEECE66DL);
        int count = 7;
        float[][] at = new float[count][];
        for (int i = 0; i < count; i++) {
            float phase = random.nextFloat() * 6.28F;
            float radius = 2.35F + random.nextFloat() * 0.6F;
            float speed = 0.12F + random.nextFloat() * 0.1F;
            float a = phase + time * speed * (i % 2 == 0 ? 1 : -1);
            float size = 0.12F + random.nextFloat() * 0.14F;
            float bob = 0.12F * Mth.sin(time * 1.3F + phase);
            at[i] = new float[]{Mth.cos(a) * radius * 0.8F, CY + 0.3F + Mth.sin(a) * radius + bob,
                    (random.nextFloat() - 0.5F) * 0.6F, size, (time * 40.0F + phase * 57.0F) % 360.0F};
        }
        VertexConsumer stone = buffers.getBuffer(RenderType.entityCutoutNoCull(PIERRE));
        for (float[] s : at) {
            shard(pose, stone, overlay, lit, 0xFFFFFFFF, s, 0.0F);
        }
        VertexConsumer cracks = buffers.getBuffer(RenderType.entityTranslucentEmissive(FISSURES));
        for (float[] s : at) {
            shard(pose, cracks, overlay, FULL, argb(0.7F, 1.0F, 1.0F, 1.0F), s, 0.004F);
        }
    }

    private static void shard(PoseStack pose, VertexConsumer vc, int overlay, int light, int color, float[] s,
                              float grow) {
        pose.pushPose();
        pose.translate(s[0], s[1], s[2]);
        pose.mulPose(Axis.YP.rotationDegrees(s[4]));
        pose.mulPose(Axis.ZP.rotationDegrees(s[4] * 0.7F));
        float h = s[3] + grow;
        boxUv(pose.last(), vc, overlay, light, color, -h, -h * 1.6F, -h, h, h * 1.6F, h, 0.9F);
        pose.popPose();
    }

    /** La cendre au sol : un disque noir effiloche, un peu plus large que le portail. */
    private static void ash(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        PoseStack.Pose p = pose.last();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(CENDRE));
        float r = 2.6F;
        quad(p, vc, overlay, light, 0xEEFFFFFF, 0, 1, 0,
                -r, 0.02F, r, 0.0F, 1.0F,
                r, 0.02F, r, 1.0F, 1.0F,
                r, 0.02F, -r, 1.0F, 0.0F,
                -r, 0.02F, -r, 0.0F, 0.0F);
    }
}
