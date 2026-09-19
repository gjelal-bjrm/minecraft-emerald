package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Les rendus des entites du Morph Gun (client).
 *
 *  - Eco : la munition gun-ammo-* de Jak 3 (tools/jak_gun.py), a l'echelle du jeu,
 *    qui tourne a 60 degres/s et flotte, en PLEINE LUMIERE (sa lueur propre), avec
 *    un halo de sa couleur (textures/entity/gun/eco_halo.png) ;
 *  - Ball : la boule du Peace Maker, deux disques face camera (violet, coeur
 *    blanc), qui grandit pendant la charge, collee a la bouche du canon du tireur,
 *    puis suit la spirale du vol ;
 *  - Arc : la foudre du Peace Maker, un zigzag de deux rubans croises (violet,
 *    coeur lavande) en melange additif, retire toutes les deux tiques ;
 *  - Shockwave : l'anneau rouge du Wave Concussor, qui suit le front de l'onde ;
 *  - Grenade : la grenade gun-grenade du Plasmite RPG, dans son halo rouge ;
 *  - Saucer : la soucoupe gun-saucer du Gyro Burster, qui tourne, halo jaune en rafale ;
 *  - GravityField : le bord violet du champ du Mass Inverter ;
 *  - Nuke : le missile gun-nuke de la Super Nova, puis sa boule de feu.
 */
public final class GunRenderers {

    public static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
            "textures/entity/gun/eco_halo.png");
    public static final ResourceLocation ORB = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
            "textures/entity/gun/peace_orb.png");

    private static final int FULL_BRIGHT = 0xF000F0;

    private GunRenderers() {
    }

    /** Un carre de cote 1 centre, face camera (la pose doit deja porter l'orientation de la camera). */
    private static void billboard(PoseStack.Pose pose, VertexConsumer out, int r, int g, int b, int a) {
        vertex(pose, out, -0.5F, -0.5F, 0.0F, 1.0F, r, g, b, a);
        vertex(pose, out, 0.5F, -0.5F, 1.0F, 1.0F, r, g, b, a);
        vertex(pose, out, 0.5F, 0.5F, 1.0F, 0.0F, r, g, b, a);
        vertex(pose, out, -0.5F, 0.5F, 0.0F, 0.0F, r, g, b, a);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, float x, float y, float u, float v,
                               int r, int g, int b, int a) {
        out.addVertex(pose, x, y, 0.0F).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT).setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    // ================================================================ munition d'eco

    public static final class Eco extends EntityRenderer<GunEcoEntity> {

        public Eco(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.15F;
        }

        @Override
        public void render(GunEcoEntity eco, float yaw, float partial, PoseStack poseStack, MultiBufferSource buffers,
                           int light) {
            if (eco.hidden(partial)) {
                return;
            }
            GunForm.Family family = eco.family();
            JakGunModel model = JakGunModel.ammo(family);
            double t = eco.tickCount + partial;
            float bob = (float) (0.06 * Math.sin(t * 0.1));
            poseStack.pushPose();
            poseStack.translate(0.0F, bob, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees((float) (t * 3.0) + eco.getYRot()));
            if (model != null) {
                PoseStack.Pose last = poseStack.last();
                VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(MorphGunItemRenderer.ATLAS));
                for (int tri = 0; tri < model.triangles; tri++) {
                    for (int corner = 0; corner < 4; corner++) {
                        int s = tri * 3 + Math.min(corner, 2);
                        out.addVertex(last, model.positions[s * 3], model.positions[s * 3 + 1], model.positions[s * 3 + 2])
                                .setColor(model.colors[s])
                                .setUv(model.uvs[s * 2], model.uvs[s * 2 + 1])
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(FULL_BRIGHT)
                                .setNormal(last, model.normals[s * 3], model.normals[s * 3 + 1], model.normals[s * 3 + 2]);
                    }
                }
            }
            poseStack.popPose();

            float mid = (model == null ? 0.2F : model.maxY * 0.5F) + bob;
            float pulse = 0.75F + 0.25F * (float) Math.sin(t * 0.2);
            poseStack.pushPose();
            poseStack.translate(0.0F, mid, 0.0F);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            float size = 0.75F + 0.1F * pulse;
            poseStack.scale(size, size, size);
            int rgb = MorphGunItem.textColor(family);
            billboard(poseStack.last(), buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO)),
                    (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, (int) (140 * pulse));
            poseStack.popPose();
            super.render(eco, yaw, partial, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(GunEcoEntity entity) {
            return HALO;
        }
    }

    // ================================================================ boule du Peace Maker

    public static final class Ball extends EntityRenderer<GunPeaceBallEntity> {

        public Ball(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(GunPeaceBallEntity ball, float yaw, float partial, PoseStack poseStack,
                           MultiBufferSource buffers, int light) {
            float grow = Math.min(1.0F, (ball.tickCount + partial) / GunSpec.PEACE_GROW);
            float size = 0.25F + 0.45F * grow;
            poseStack.pushPose();
            LivingEntity shooter = ball.shooter();
            if (!ball.launched() && shooter instanceof Player player) {
                Vec3 muzzle = GunClient.muzzle(player, partial);
                double x = Mth.lerp(partial, ball.xo, ball.getX());
                double y = Mth.lerp(partial, ball.yo, ball.getY());
                double z = Mth.lerp(partial, ball.zo, ball.getZ());
                poseStack.translate(muzzle.x - x, muzzle.y - y, muzzle.z - z);
            } else {
                Vec3 offset = GunPeaceBallEntity.spiral(ball, partial);
                poseStack.translate(offset.x, offset.y, offset.z);
            }
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees((ball.tickCount + partial) * 25.0F));
            VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(ORB));
            poseStack.pushPose();
            poseStack.scale(size, size, size);
            billboard(poseStack.last(), out, 150, 80, 255, 230);
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.scale(size * 0.45F, size * 0.45F, size * 0.45F);
            billboard(poseStack.last(), out, 245, 230, 255, 255);
            poseStack.popPose();
            poseStack.popPose();
            super.render(ball, yaw, partial, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(GunPeaceBallEntity entity) {
            return ORB;
        }
    }

    // ================================================================ foudre du Peace Maker

    public static final class Arc extends EntityRenderer<GunArcEntity> {

        public Arc(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(GunArcEntity arc, float yaw, float partial, PoseStack poseStack, MultiBufferSource buffers,
                           int light) {
            Vector3f end = arc.end();
            double length = Math.sqrt(end.x * end.x + end.y * end.y + end.z * end.z);
            if (length < 1.0e-3) {
                return;
            }
            float life = 1.0F - Math.min(1.0F, (arc.tickCount + partial) / GunArcEntity.LIFE);
            RandomSource random = RandomSource.create(arc.seed() * 31L + arc.tickCount / 2);
            int n = Math.max(3, (int) Math.ceil(length / 0.9));
            Vec3 direction = new Vec3(end.x, end.y, end.z).normalize();
            Vec3 u = direction.cross(new Vec3(0.0, 1.0, 0.0));
            u = u.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : u.normalize();
            Vec3 v = u.cross(direction).normalize();
            Vec3[] points = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                Vec3 base = new Vec3(end.x, end.y, end.z).scale(i / (double) n);
                if (i > 0 && i < n) {
                    base = base.add(u.scale((random.nextDouble() - 0.5) * 0.6)).add(v.scale((random.nextDouble() - 0.5) * 0.6));
                }
                points[i] = base;
            }
            VertexConsumer out = buffers.getBuffer(RenderType.lightning());
            Matrix4f matrix = poseStack.last().pose();
            ribbon(matrix, out, points, u, 0.16F, 0.55F, 0.25F, 1.0F, 0.5F * life);
            ribbon(matrix, out, points, v, 0.16F, 0.55F, 0.25F, 1.0F, 0.5F * life);
            ribbon(matrix, out, points, u, 0.05F, 0.95F, 0.88F, 1.0F, 0.9F * life);
            ribbon(matrix, out, points, v, 0.05F, 0.95F, 0.88F, 1.0F, 0.9F * life);
            super.render(arc, yaw, partial, poseStack, buffers, light);
        }

        /** Un ruban le long des points, large de 2 w dans la direction `side`, dessine des deux faces. */
        private static void ribbon(Matrix4f m, VertexConsumer out, Vec3[] p, Vec3 side, float w,
                                   float r, float g, float b, float a) {
            for (int i = 0; i + 1 < p.length; i++) {
                Vec3 a0 = p[i].subtract(side.scale(w));
                Vec3 a1 = p[i].add(side.scale(w));
                Vec3 b1 = p[i + 1].add(side.scale(w));
                Vec3 b0 = p[i + 1].subtract(side.scale(w));
                quad(m, out, a0, a1, b1, b0, r, g, b, a);
                quad(m, out, b0, b1, a1, a0, r, g, b, a);
            }
        }

        private static void quad(Matrix4f m, VertexConsumer out, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3,
                                 float r, float g, float b, float a) {
            out.addVertex(m, (float) p0.x, (float) p0.y, (float) p0.z).setColor(r, g, b, a);
            out.addVertex(m, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, a);
            out.addVertex(m, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, a);
            out.addVertex(m, (float) p3.x, (float) p3.y, (float) p3.z).setColor(r, g, b, a);
        }

        @Override
        public ResourceLocation getTextureLocation(GunArcEntity entity) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }

    // ================================================================ modeles de Jak 3

    /** Dessine un modele cuit (tools/jak_gun.py) dans le repere courant, en pleine lumiere. */
    private static void model(JakGunModel model, PoseStack.Pose last, VertexConsumer out) {
        for (int tri = 0; tri < model.triangles; tri++) {
            for (int corner = 0; corner < 4; corner++) {
                int s = tri * 3 + Math.min(corner, 2);
                out.addVertex(last, model.positions[s * 3], model.positions[s * 3 + 1], model.positions[s * 3 + 2])
                        .setColor(model.colors[s])
                        .setUv(model.uvs[s * 2], model.uvs[s * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(FULL_BRIGHT)
                        .setNormal(last, model.normals[s * 3], model.normals[s * 3 + 1], model.normals[s * 3 + 2]);
            }
        }
    }

    // ================================================================ onde du Wave Concussor

    /**
     * L'anneau de l'onde : une couronne a plat qui suit le front, brillante au bord et
     * fondue vers le centre, et un rideau bas dresse sur le front. Sa lumiere suit
     * l'intensite de l'onde, puis s'eteint en {@value GunShockwaveEntity#FADE_TICKS} tiques.
     */
    public static final class Shockwave extends EntityRenderer<GunShockwaveEntity> {

        private static final int SEGMENTS = 64;

        public Shockwave(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(GunShockwaveEntity wave, net.minecraft.client.renderer.culling.Frustum frustum,
                                    double x, double y, double z) {
            return wave.shouldRender(x, y, z) && frustum.isVisible(wave.getBoundingBoxForCulling());
        }

        @Override
        public void render(GunShockwaveEntity wave, float yaw, float partial, PoseStack poseStack, MultiBufferSource buffers,
                           int light) {
            double age = wave.tickCount + partial;
            float radius = (float) wave.radiusAt(age);
            float over = (float) Math.max(0.0, age - wave.expandTicks());
            float fade = 1.0F - Math.min(1.0F, over / GunShockwaveEntity.FADE_TICKS);
            float glow = (float) Math.max(0.25, wave.intensityAt(radius)) * fade;
            if (glow <= 0.0F) {
                return;
            }
            float inner = Math.max(0.0F, radius - 2.2F);
            VertexConsumer out = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = poseStack.last().pose();
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = Math.PI * 2.0 * i / SEGMENTS;
                double a1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);
                // la couronne a plat, des deux faces
                flat(m, out, c0, s0, c1, s1, inner, radius, 0.12F, 0.0F, 0.75F * glow);
                // le rideau du front
                out.addVertex(m, c0 * radius, 0.12F, s0 * radius).setColor(1.0F, 0.42F, 0.12F, 0.8F * glow);
                out.addVertex(m, c1 * radius, 0.12F, s1 * radius).setColor(1.0F, 0.42F, 0.12F, 0.8F * glow);
                out.addVertex(m, c1 * radius, 1.3F, s1 * radius).setColor(1.0F, 0.2F, 0.05F, 0.0F);
                out.addVertex(m, c0 * radius, 1.3F, s0 * radius).setColor(1.0F, 0.2F, 0.05F, 0.0F);
                out.addVertex(m, c0 * radius, 1.3F, s0 * radius).setColor(1.0F, 0.2F, 0.05F, 0.0F);
                out.addVertex(m, c1 * radius, 1.3F, s1 * radius).setColor(1.0F, 0.2F, 0.05F, 0.0F);
                out.addVertex(m, c1 * radius, 0.12F, s1 * radius).setColor(1.0F, 0.42F, 0.12F, 0.8F * glow);
                out.addVertex(m, c0 * radius, 0.12F, s0 * radius).setColor(1.0F, 0.42F, 0.12F, 0.8F * glow);
            }
            super.render(wave, yaw, partial, poseStack, buffers, light);
        }

        private static void flat(Matrix4f m, VertexConsumer out, float c0, float s0, float c1, float s1,
                                 float inner, float outer, float y, float alphaIn, float alphaOut) {
            out.addVertex(m, c0 * inner, y, s0 * inner).setColor(1.0F, 0.3F, 0.08F, alphaIn);
            out.addVertex(m, c0 * outer, y, s0 * outer).setColor(1.0F, 0.5F, 0.15F, alphaOut);
            out.addVertex(m, c1 * outer, y, s1 * outer).setColor(1.0F, 0.5F, 0.15F, alphaOut);
            out.addVertex(m, c1 * inner, y, s1 * inner).setColor(1.0F, 0.3F, 0.08F, alphaIn);
            out.addVertex(m, c1 * inner, y, s1 * inner).setColor(1.0F, 0.3F, 0.08F, alphaIn);
            out.addVertex(m, c1 * outer, y, s1 * outer).setColor(1.0F, 0.5F, 0.15F, alphaOut);
            out.addVertex(m, c0 * outer, y, s0 * outer).setColor(1.0F, 0.5F, 0.15F, alphaOut);
            out.addVertex(m, c0 * inner, y, s0 * inner).setColor(1.0F, 0.3F, 0.08F, alphaIn);
        }

        @Override
        public ResourceLocation getTextureLocation(GunShockwaveEntity entity) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }

    // ================================================================ grenade du Plasmite RPG

    /** La grenade gun-grenade de Jak 3, a l'echelle du jeu, qui roule sur elle-meme, dans son halo rouge. */
    public static final class Grenade extends EntityRenderer<GunGrenadeEntity> {

        public Grenade(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(GunGrenadeEntity grenade, float yaw, float partial, PoseStack poseStack,
                           MultiBufferSource buffers, int light) {
            JakGunModel model = JakGunModel.get("gun_grenade");
            float t = grenade.tickCount + partial;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.16F, 0.0F);
            if (model != null) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(t * 27.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(t * 41.0F));
                model(model, poseStack.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(MorphGunItemRenderer.ATLAS)));
                poseStack.popPose();
            }
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            float pulse = 0.8F + 0.2F * (float) Math.sin(t * 0.9F);
            poseStack.scale(1.1F * pulse, 1.1F * pulse, 1.1F * pulse);
            billboard(poseStack.last(), buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO)), 255, 90, 40, 170);
            poseStack.popPose();
            super.render(grenade, yaw, partial, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(GunGrenadeEntity entity) {
            return HALO;
        }
    }

    // ================================================================ soucoupe du Gyro Burster

    /**
     * La soucoupe gun-saucer de Jak 3, a l'echelle du jeu ({@value GunSpec#GYRO_SCALE}),
     * qui tourne a deux tours par seconde ; un halo jaune pendant la rafale ; elle
     * retrecit avant de s'eteindre.
     */
    public static final class Saucer extends EntityRenderer<GunSaucerEntity> {

        public Saucer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.5F;
        }

        @Override
        public void render(GunSaucerEntity saucer, float yaw, float partial, PoseStack poseStack,
                           MultiBufferSource buffers, int light) {
            JakGunModel model = JakGunModel.get("gun_saucer");
            float t = saucer.tickCount + partial;
            float scale = GunSpec.GYRO_SCALE;
            if (saucer.phase() == GunSaucerEntity.SHRINK && saucer.shrinkAge >= 0) {
                scale *= Math.max(0.0F, 1.0F - (t - saucer.shrinkAge) / GunSpec.GYRO_SHRINK_TICKS);
            }
            boolean still = saucer.phase() == GunSaucerEntity.SIT || saucer.phase() == GunSaucerEntity.SHRINK;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.15F, 0.0F);
            if (model != null && scale > 0.01F) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.YP.rotationDegrees(still ? 0.0F : t * GunSpec.GYRO_SPIN_DEGREES));
                poseStack.scale(scale, scale, scale);
                model(model, poseStack.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(MorphGunItemRenderer.ATLAS)));
                poseStack.popPose();
            }
            if (saucer.firing()) {
                poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                float pulse = 0.8F + 0.2F * (float) Math.sin(t * 1.6F);
                poseStack.scale(2.6F * pulse, 2.6F * pulse, 2.6F * pulse);
                billboard(poseStack.last(), buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO)), 255, 215, 60, 150);
            }
            poseStack.popPose();
            super.render(saucer, yaw, partial, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(GunSaucerEntity entity) {
            return HALO;
        }
    }

    // ================================================================ champ du Mass Inverter

    /**
     * Le bord du champ : un anneau violet a plat qui suit son rayon, et un rideau bas.
     * Plein pendant que le champ s'etend, il palit ensuite jusqu'a la fin du champ.
     */
    public static final class GravityField extends EntityRenderer<GunGravityFieldEntity> {

        private static final int SEGMENTS = 96;

        public GravityField(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(GunGravityFieldEntity field, net.minecraft.client.renderer.culling.Frustum frustum,
                                    double x, double y, double z) {
            return field.shouldRender(x, y, z) && frustum.isVisible(field.getBoundingBoxForCulling());
        }

        @Override
        public void render(GunGravityFieldEntity field, float yaw, float partial, PoseStack poseStack,
                           MultiBufferSource buffers, int light) {
            double age = field.tickCount + partial;
            float radius = (float) GunGravityFieldEntity.radiusAt(age);
            float fade = age <= GunSpec.INVERTER_GROW_TICKS ? 1.0F
                    : Math.max(0.0F, 1.0F - (float) ((age - GunSpec.INVERTER_GROW_TICKS)
                    / (GunSpec.INVERTER_FIELD_TICKS - GunSpec.INVERTER_GROW_TICKS)));
            float glow = 0.25F + 0.75F * fade;
            if (radius < 0.05F) {
                return;
            }
            float inner = Math.max(0.0F, radius - 3.0F);
            VertexConsumer out = buffers.getBuffer(RenderType.lightning());
            Matrix4f m = poseStack.last().pose();
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = Math.PI * 2.0 * i / SEGMENTS;
                double a1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);
                float y = 0.1F;
                for (int face = 0; face < 2; face++) {
                    boolean up = face == 0;
                    ring(m, out, up ? c0 : c1, up ? s0 : s1, up ? c1 : c0, up ? s1 : s0, inner, radius, y, 0.6F * glow);
                }
                out.addVertex(m, c0 * radius, y, s0 * radius).setColor(0.62F, 0.3F, 1.0F, 0.7F * glow);
                out.addVertex(m, c1 * radius, y, s1 * radius).setColor(0.62F, 0.3F, 1.0F, 0.7F * glow);
                out.addVertex(m, c1 * radius, 1.8F, s1 * radius).setColor(0.45F, 0.15F, 0.9F, 0.0F);
                out.addVertex(m, c0 * radius, 1.8F, s0 * radius).setColor(0.45F, 0.15F, 0.9F, 0.0F);
                out.addVertex(m, c0 * radius, 1.8F, s0 * radius).setColor(0.45F, 0.15F, 0.9F, 0.0F);
                out.addVertex(m, c1 * radius, 1.8F, s1 * radius).setColor(0.45F, 0.15F, 0.9F, 0.0F);
                out.addVertex(m, c1 * radius, y, s1 * radius).setColor(0.62F, 0.3F, 1.0F, 0.7F * glow);
                out.addVertex(m, c0 * radius, y, s0 * radius).setColor(0.62F, 0.3F, 1.0F, 0.7F * glow);
            }
            super.render(field, yaw, partial, poseStack, buffers, light);
        }

        private static void ring(Matrix4f m, VertexConsumer out, float c0, float s0, float c1, float s1,
                                 float inner, float outer, float y, float alpha) {
            out.addVertex(m, c0 * inner, y, s0 * inner).setColor(0.45F, 0.15F, 0.9F, 0.0F);
            out.addVertex(m, c0 * outer, y, s0 * outer).setColor(0.7F, 0.4F, 1.0F, alpha);
            out.addVertex(m, c1 * outer, y, s1 * outer).setColor(0.7F, 0.4F, 1.0F, alpha);
            out.addVertex(m, c1 * inner, y, s1 * inner).setColor(0.45F, 0.15F, 0.9F, 0.0F);
        }

        @Override
        public ResourceLocation getTextureLocation(GunGravityFieldEntity entity) {
            return TextureAtlas.LOCATION_BLOCKS;
        }
    }

    // ================================================================ missile de la Super Nova

    /**
     * Le missile gun-nuke de Jak 3, a l'echelle du jeu (2,5), le nez dans sa course ;
     * plante, il clignote ; detone, il laisse place a une boule blanche et violette
     * qui grossit puis s'efface.
     */
    public static final class Nuke extends EntityRenderer<GunNukeEntity> {

        public Nuke(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public void render(GunNukeEntity nuke, float yaw, float partial, PoseStack poseStack, MultiBufferSource buffers,
                           int light) {
            float t = nuke.tickCount + partial;
            poseStack.pushPose();
            if (nuke.state() == GunNukeEntity.DETONATED) {
                float age = nuke.detonationAge < 0 ? 0.0F : t - nuke.detonationAge;
                float f = Math.min(1.0F, age / 24.0F);
                float size = 4.0F + 36.0F * (float) Math.sqrt(f);
                int alpha = (int) (255.0F * (1.0F - f) * (1.0F - f));
                poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO));
                poseStack.pushPose();
                poseStack.scale(size, size, size);
                billboard(poseStack.last(), out, 170, 90, 255, alpha);
                poseStack.popPose();
                poseStack.scale(size * 0.5F, size * 0.5F, size * 0.5F);
                billboard(poseStack.last(), out, 255, 245, 255, alpha);
            } else {
                JakGunModel model = JakGunModel.get("gun_nuke");
                Vec3 v = nuke.getDeltaMovement();
                if (v.lengthSqr() > 1.0e-6) {
                    float heading = (float) Math.toDegrees(Math.atan2(v.x, v.z));
                    float pitch = (float) Math.toDegrees(Math.atan2(v.y, Math.sqrt(v.x * v.x + v.z * v.z)));
                    poseStack.mulPose(Axis.YP.rotationDegrees(heading));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
                }
                boolean blink = nuke.state() == GunNukeEntity.EMBEDDED && ((int) t / 2) % 2 == 0;
                if (model != null) {
                    poseStack.pushPose();
                    poseStack.scale(2.5F, 2.5F, 2.5F);
                    model(model, poseStack.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(MorphGunItemRenderer.ATLAS)));
                    poseStack.popPose();
                }
                poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                float glow = blink ? 2.4F : 1.3F;
                poseStack.scale(glow, glow, glow);
                billboard(poseStack.last(), buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO)), 170, 90, 255,
                        blink ? 230 : 150);
            }
            poseStack.popPose();
            super.render(nuke, yaw, partial, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(GunNukeEntity entity) {
            return HALO;
        }
    }
}
