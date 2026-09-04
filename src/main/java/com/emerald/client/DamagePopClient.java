package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.DamagePopPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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
 * LE JOUEUR NE LES VOYAIT PAS. La premiere version les dessinait DANS le monde,
 * a l'etape « apres les particules » du rendu : sous un shader (Iris et
 * Complementary, que le mode retient), cette etape passe par un pipeline qui
 * ne rend pas nos textes -- ils n'apparaissaient qu'en rendu vanilla, que
 * personne n'utilise. Ils sont donc dessines maintenant sur l'INTERFACE : on
 * retient les matrices de la camera a chaque image, on projette la position
 * du coup a l'ecran, et on ecrit le chiffre la, comme un element de HUD. Le
 * HUD est dessine par le jeu apres le shader, quel que soit le shader.
 *
 * LE CRITIQUE SE VOIT AVANT DE SE LIRE. Il est plus gros, dore, precede d'un
 * eclair, et il BONDIT -- il part plus haut, plus vite, puis retombe -- la ou
 * un coup ordinaire monte doucement et s'efface.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class DamagePopClient {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Duree de vie, en ticks. Le critique reste un peu plus. */
    private static final int LIFE = 24;
    private static final int LIFE_CRIT = 32;
    private static final int PLAIN = 0xF2F2F2;
    private static final int CRIT = 0xFFC83C;
    private static final int CRIT_EDGE = 0xFF6A2E;

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
    /** Les matrices de la derniere image : projection x vue, et l'oeil. */
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 eye = Vec3.ZERO;
    private static boolean ready;

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
        LOGGER.debug("Chiffre de degats recu : {}", text);
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

    /** Le champ de vision de l'image en cours, tel que le jeu l'a calcule (course, effets). */
    private static double fov = 70.0;

    @SubscribeEvent
    public static void onFov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
        fov = event.getFOV();
    }

    /**
     * LA CAMERA SE RECONSTRUIT, ELLE NE SE RECOIT PAS. La premiere version
     * retenait les matrices a l'etape « apres le monde » du rendu : sous Iris,
     * rien n'arrivait, et les chiffres restaient invisibles. On rebatit donc
     * projection et vue depuis la camera du jeu, a chaque image, au moment de
     * dessiner l'interface -- deux objets que tout shader laisse intacts.
     */
    private static void refreshCamera(Minecraft mc, float partial) {
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        eye = camera.getPosition();
        Matrix4f view = new Matrix4f().rotation(camera.rotation().conjugate(new org.joml.Quaternionf()));
        VIEW_PROJECTION.set(mc.gameRenderer.getProjectionMatrix(fov)).mul(view);
        ready = true;
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (POPS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        refreshCamera(mc, partial);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        for (Pop pop : POPS) {
            float t = (pop.age + partial) / pop.life();
            // LA TRAJECTOIRE dit la nature du coup. Un coup ordinaire monte d'un
            // demi-bloc en decelerant ; un critique bondit d'un bloc puis
            // retombe d'un quart -- une parabole, pas une montee.
            float rise = pop.crit
                    ? 1.0F * Mth.sin(t * (float) Math.PI * 0.85F)
                    : 0.55F * (1.0F - (1.0F - t) * (1.0F - t));
            Vec3 at = pop.origin.add(pop.drift * t, rise, 0.0);
            // la projection : monde -> clip -> ecran
            Vector4f clip = new Vector4f((float) (at.x - eye.x), (float) (at.y - eye.y),
                    (float) (at.z - eye.z), 1.0F);
            VIEW_PROJECTION.transform(clip);
            if (clip.w <= 0.0F) {
                continue;                    // derriere la camera
            }
            float sx = (clip.x / clip.w * 0.5F + 0.5F) * width;
            float sy = (1.0F - (clip.y / clip.w * 0.5F + 0.5F)) * height;
            if (sx < -40 || sx > width + 40 || sy < -20 || sy > height + 20) {
                continue;
            }
            // Le critique GROSSIT a l'impact puis se stabilise : un coup de
            // poing visuel dans les six premiers ticks. La taille suit aussi la
            // distance, pour qu'un coup a vingt blocs ne couvre pas l'ecran.
            float punch = pop.crit ? 1.0F + 0.6F * Math.max(0.0F, 1.0F - t * 4.0F) : 1.0F;
            float distance = (float) at.distanceTo(eye);
            float scale = (pop.crit ? 1.9F : 1.25F) * punch * Mth.clamp(6.0F / Math.max(2.0F, distance), 0.45F, 1.6F);
            float fade = t < 0.7F ? 1.0F : 1.0F - (t - 0.7F) / 0.3F;
            int alpha = Math.max(8, (int) (fade * 255));
            graphics.pose().pushPose();
            graphics.pose().translate(sx, sy, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            float half = font.width(pop.text) / 2.0F;
            if (pop.crit) {
                // un lisere orange sous le dore : c'est lui qui donne le relief
                graphics.drawString(font, pop.text, Math.round(-half) + 1, 1,
                        (alpha << 24) | CRIT_EDGE, false);
            }
            graphics.drawString(font, pop.text, Math.round(-half), 0,
                    (alpha << 24) | (pop.crit ? CRIT : PLAIN), !pop.crit);
            graphics.pose().popPose();
        }
    }
}
