package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.WeatherPulsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Ce qui rend une tempete INQUIETANTE : l'ecran qui blanchit et le sol qui
 * tremble.
 *
 * Les particules disent qu'il se passe quelque chose ; elles ne le font pas
 * ressentir. Un eclair qui tombe a vingt blocs sans que l'ecran bronche reste
 * un decor -- c'est exactement le reproche fait a la premiere Nuit, « au final
 * c'est une nuit normale ». Ces deux effets touchent le joueur lui-meme, et
 * c'est pour cela qu'ils comptent plus que le reste.
 *
 * Tous deux s'amortissent tout seuls, sans rien a nettoyer : un coup rate ne
 * laisse pas la camera de travers.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class WeatherAtmosphere {

    /** L'eclat en cours, 0 -> 1, et sa couleur. */
    private static float flash;
    private static int flashColor = 0xFFFFFF;

    /** La secousse en cours, en degres d'amplitude. */
    private static float shake;

    /** Le tremblement de fond, pendant les tempetes les plus dures. */
    private static float rumble;

    private WeatherAtmosphere() {
    }

    /** Recoit un coup : eclair proche, impact de meteore. */
    public static void accept(WeatherPulsePayload payload) {
        float strength = payload.flash() / 100.0F;
        // on ne fait que MONTER : deux eclairs coup sur coup ne s'annulent pas
        if (strength > flash) {
            flash = Math.min(1.0F, strength);
            flashColor = payload.color();
        }
        shake = Math.max(shake, payload.shake() / 100.0F);
    }

    /** Le tremblement continu d'une tempete, pose chaque tick par le client. */
    public static void setRumble(float value) {
        rumble = value;
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        // l'eclat retombe vite -- c'est un eclair, pas un lever de soleil --
        // la secousse plus lentement, comme une onde qui se dissipe
        flash = Math.max(0.0F, flash - 0.075F);
        shake = Math.max(0.0F, shake * 0.86F - 0.002F);
    }

    // ------------------------------------------------------------- l'eclat

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            // sous la barre d'action, au-dessus de tout le reste : l'eclat doit
            // laver l'image sans effacer le texte qui explique ce qui arrive
            event.registerBelow(VanillaGuiLayers.CHAT,
                    ResourceLocation.fromNamespaceAndPath(
                            EmeraldWeaponsMod.MODID, "weather_flash"),
                    WeatherAtmosphere::renderFlash);
        }
    }

    private static void renderFlash(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        if (flash <= 0.01F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        // la courbe au carre garde l'eclat bref : lineaire, il s'attardait et
        // ressemblait davantage a un ecran de degats qu'a un eclair
        int alpha = (int) (flash * flash * 190.0F);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                (alpha << 24) | (flashColor & 0xFFFFFF));
    }

    // ---------------------------------------------------------- la secousse

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float amount = shake + rumble;
        if (amount <= 0.001F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // trois frequences premieres entre elles : la camera tremble au lieu de
        // balancer regulierement, ce qui trahirait le sinus
        double t = (mc.level.getGameTime() + event.getPartialTick()) * 1.0;
        event.setYaw(event.getYaw() + (float) Math.sin(t * 1.7) * amount * 1.4F);
        event.setPitch(event.getPitch() + (float) Math.sin(t * 2.3) * amount * 1.1F);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 3.1) * amount * 1.8F);
    }
}
