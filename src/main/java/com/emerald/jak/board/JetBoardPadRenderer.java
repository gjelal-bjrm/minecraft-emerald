package com.emerald.jak.board;

import com.emerald.haven.Haven;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Les tremplins du JET-Board, dessines (cahier §100) : une bouche d'eco bleu -- un cercle au sol
 * qui bat, une spirale qui tourne dedans, une colonne de lumiere qui s'efface en montant -- et des
 * etincelles qui s'en elevent. Pleine lumiere, mais caches par les murs (textBackground, pas sa
 * variante « vue a travers ») : on les decouvre en passant.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class JetBoardPadRenderer {

    private static final int SEGMENTS = 40;
    private static final double RANGE = 96.0;
    private static final double SPARK_RANGE = 48.0;
    private static final float RED = 0.35F;
    private static final float GREEN = 0.78F;
    private static final float BLUE = 1.0F;
    private static final float OUTER = 1.5F;
    private static final float COLUMN = 4.5F;
    private static final float CORE = 7.0F;

    private JetBoardPadRenderer() {
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !Haven.is(level)) {
            return;
        }
        float time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.textBackground();
        VertexConsumer consumer = buffer.getBuffer(type);
        PoseStack pose = event.getPoseStack();
        boolean any = false;
        for (JetBoardPads.Pad pad : JetBoardPads.pads()) {
            if (pad.at().distanceToSqr(eye) > RANGE * RANGE) {
                continue;
            }
            any = true;
            pose.pushPose();
            pose.translate(pad.at().x - eye.x, pad.at().y - eye.y, pad.at().z - eye.z);
            draw(pose.last().pose(), consumer, time + pad.number() * 17.0F);
            pose.popPose();
        }
        if (any) {
            buffer.endBatch(type);
        }
    }

    private static void draw(Matrix4f m, VertexConsumer c, float time) {
        float beat = 0.75F + 0.25F * Mth.sin(time * 0.3F);
        // le cercle au sol, et le disque qu'il borde
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            ring(m, c, t0, t1, OUTER, OUTER - 0.35F, 0.04F, beat);
            ring(m, c, t0, t1, OUTER - 0.35F, 0.0F, 0.02F, 0.3F * beat);
        }
        // la spirale : quatre bras qui tournent vers le centre
        float spin = time * 0.12F;
        for (int arm = 0; arm < 4; arm++) {
            for (int k = 0; k < 6; k++) {
                float r0 = 0.25F + k * 0.13F;
                float t0 = spin + arm * Mth.HALF_PI + k * 0.35F;
                ring(m, c, t0, t0 + 0.45F, r0 + 0.1F, r0, 0.03F, 0.55F * beat * (1.0F - k * 0.1F));
            }
        }
        // la colonne : un fut de lumiere qui s'efface en montant, et un coeur plus etroit qui monte plus haut
        for (int i = 0; i < SEGMENTS; i++) {
            float t0 = (float) (i * Math.PI * 2.0 / SEGMENTS);
            float t1 = (float) ((i + 1) * Math.PI * 2.0 / SEGMENTS);
            column(m, c, t0, t1, OUTER - 0.15F, COLUMN, 0.55F * beat);
            column(m, c, t0, t1, 0.45F, CORE, 0.5F * beat);
        }
    }

    private static void column(Matrix4f m, VertexConsumer c, float t0, float t1, float r, float height, float a) {
        quad(m, c, Mth.cos(t0) * r, 0.0F, Mth.sin(t0) * r, Mth.cos(t1) * r, 0.0F, Mth.sin(t1) * r,
                Mth.cos(t1) * r, height, Mth.sin(t1) * r, Mth.cos(t0) * r, height, Mth.sin(t0) * r, a, 0.0F);
    }

    /** Un bout d'anneau a plat, a la hauteur y. */
    private static void ring(Matrix4f m, VertexConsumer c, float t0, float t1, float outer, float inner, float y, float a) {
        float c0 = Mth.cos(t0);
        float s0 = Mth.sin(t0);
        float c1 = Mth.cos(t1);
        float s1 = Mth.sin(t1);
        quad(m, c, c0 * outer, y, s0 * outer, c1 * outer, y, s1 * outer, c1 * inner, y, s1 * inner, c0 * inner, y, s0 * inner,
                a, a);
    }

    /** Un quadrilatere a deux faces ; alpha des points 1-2 et 3-4 (voir QuestMarkersClient). */
    private static void quad(Matrix4f m, VertexConsumer c,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, float aLow, float aHigh) {
        int light = 0xF000F0;
        c.addVertex(m, x1, y1, z1).setColor(RED, GREEN, BLUE, aLow).setLight(light);
        c.addVertex(m, x2, y2, z2).setColor(RED, GREEN, BLUE, aLow).setLight(light);
        c.addVertex(m, x3, y3, z3).setColor(RED, GREEN, BLUE, aHigh).setLight(light);
        c.addVertex(m, x4, y4, z4).setColor(RED, GREEN, BLUE, aHigh).setLight(light);
        c.addVertex(m, x4, y4, z4).setColor(RED, GREEN, BLUE, aHigh).setLight(light);
        c.addVertex(m, x3, y3, z3).setColor(RED, GREEN, BLUE, aHigh).setLight(light);
        c.addVertex(m, x2, y2, z2).setColor(RED, GREEN, BLUE, aLow).setLight(light);
        c.addVertex(m, x1, y1, z1).setColor(RED, GREEN, BLUE, aLow).setLight(light);
    }

    /** Les etincelles qui montent des tremplins proches. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || !Haven.is(level)) {
            return;
        }
        RandomSource random = level.random;
        for (JetBoardPads.Pad pad : JetBoardPads.pads()) {
            if (pad.at().distanceToSqr(mc.player.position()) > SPARK_RANGE * SPARK_RANGE) {
                continue;
            }
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(random.nextDouble()) * (OUTER - 0.2);
            // la poussiere garde un dixieme de sa vitesse de depart : 0,1 a 0,16 bloc par tique vers le haut
            level.addParticle(JetBoardPads.SPARK, pad.at().x + Math.cos(a) * r, pad.at().y + 0.1, pad.at().z + Math.sin(a) * r,
                    0.0, 1.0 + random.nextDouble() * 0.6, 0.0);
        }
    }
}
