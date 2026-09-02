package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.DamagePopPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Les chiffres de degats qui flottent au-dessus des cibles.
 *
 * Un coup qui ne s'affiche pas ne s'evalue pas : le joueur monte sa fiche, ses
 * runes et ses ameliorations, et sans chiffre il ne sait jamais si le +8 a
 * change quelque chose. Le chiffre est la seule preuve tangible de tout ce
 * qu'on a bati.
 *
 * LE CRITIQUE SE VOIT AVANT DE SE LIRE. Il est plus gros, dore, precede d'un
 * eclair, et il BONDIT -- il part plus haut, plus vite, puis retombe -- la ou un
 * coup ordinaire monte doucement et s'efface. On doit pouvoir reconnaitre un
 * critique du coin de l'oeil, au milieu d'une vague, sans lire le nombre.
 *
 * Tout est dessine face a la camera, avec la police du jeu, en SEE_THROUGH :
 * un chiffre cache derriere le monstre qui l'a recu ne servirait a rien.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class DamagePopClient {

    /** Duree de vie, en ticks. Le critique reste un peu plus. */
    private static final int LIFE = 22;
    private static final int LIFE_CRIT = 30;

    private static final int PLAIN = 0xFFF2F2F2;
    private static final int CRIT = 0xFFFFC83C;
    private static final int CRIT_EDGE = 0xFFFF6A2E;

    /** Un chiffre en vol. */
    private static final class Pop {
        final Vec3 origin;
        final String text;
        final boolean crit;
        final float drift;           // derive laterale, pour que deux coups ne se superposent pas
        int age;

        Pop(Vec3 origin, String text, boolean crit, float drift) {
            this.origin = origin;
            this.text = text;
            this.crit = crit;
            this.drift = drift;
        }

        int life() {
            return this.crit ? LIFE_CRIT : LIFE;
        }
    }

    private static final List<Pop> POPS = new ArrayList<>();

    private DamagePopClient() {
    }

    public static void accept(DamagePopPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        float drift = (mc.level == null ? 0.0F : mc.level.random.nextFloat() - 0.5F) * 0.6F;
        // Un chiffre entier se lit d'un coup ; une decimale ne sert que sous
        // dix, la ou elle fait encore la difference entre deux coups.
        String text = payload.amount() < 10.0F
                ? String.format(Locale.ROOT, "%.1f", payload.amount())
                : String.valueOf(Math.round(payload.amount()));
        if (payload.crit()) {
            text = "⚡ " + text;
        }
        POPS.add(new Pop(new Vec3(payload.x(), payload.y(), payload.z()), text, payload.crit(), drift));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Iterator<Pop> it = POPS.iterator();
        while (it.hasNext()) {
            Pop pop = it.next();
            if (++pop.age >= pop.life()) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || POPS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        Font font = mc.font;
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);

        for (Pop pop : POPS) {
            float t = (pop.age + partial) / pop.life();
            // LA TRAJECTOIRE dit la nature du coup. Un coup ordinaire monte d'un
            // demi-bloc en decelerant ; un critique bondit d'un bloc puis
            // retombe d'un quart -- une parabole, pas une montee.
            float rise = pop.crit
                    ? 1.0F * Mth.sin(t * (float) Math.PI * 0.85F)
                    : 0.55F * (1.0F - (1.0F - t) * (1.0F - t));
            // Le critique GROSSIT a l'impact puis se stabilise : un coup de
            // poing visuel dans les six premiers ticks.
            float punch = pop.crit ? 1.0F + 0.6F * Math.max(0.0F, 1.0F - t * 4.0F) : 1.0F;
            float scale = (pop.crit ? 0.040F : 0.026F) * punch;
            float fade = t < 0.7F ? 1.0F : 1.0F - (t - 0.7F) / 0.3F;
            int alpha = Math.max(8, (int) (fade * 255));

            Vec3 at = pop.origin.add(pop.drift * t, rise, 0.0);
            pose.pushPose();
            pose.translate(at.x - eye.x, at.y - eye.y, at.z - eye.z);
            pose.mulPose(camera.rotation());
            pose.scale(-scale, -scale, scale);
            Matrix4f matrix = pose.last().pose();

            float half = font.width(pop.text) / 2.0F;
            if (pop.crit) {
                // un lisere orange sous le dore : c'est lui qui donne le relief
                int edge = (alpha << 24) | (CRIT_EDGE & 0xFFFFFF);
                font.drawInBatch(pop.text, -half + 1, 1, edge, false, matrix, buffer,
                        Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            }
            int colour = (alpha << 24) | ((pop.crit ? CRIT : PLAIN) & 0xFFFFFF);
            font.drawInBatch(pop.text, -half, 0, colour, !pop.crit, matrix, buffer,
                    Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            pose.popPose();
        }
        buffer.endBatch();
    }
}
