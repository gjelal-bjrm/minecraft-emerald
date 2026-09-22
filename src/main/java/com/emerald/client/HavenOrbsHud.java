package com.emerald.client;

import com.emerald.haven.Haven;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.HavenOrbsPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Le compteur d'orbes precurseurs, en haut a droite de l'ecran, dans Haven seulement (lot 3,
 * cahier §86) : un orbe dessine, le solde, et les orbes caches trouves (« 12/150 »).
 */
public final class HavenOrbsHud {

    private static int orbs;
    private static int found;
    private static int total;
    private static boolean known;

    private HavenOrbsHud() {
    }

    public static void accept(HavenOrbsPayload payload) {
        orbs = payload.orbs();
        found = payload.found();
        total = payload.total();
        known = true;
    }

    /** Pour les photos : le solde connu du client. */
    public static int orbs() {
        return orbs;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!known || mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null
                || !mc.level.dimension().equals(Haven.LEVEL)) {
            return;
        }
        Component value = Component.literal(String.valueOf(orbs));
        Component hidden = Component.translatable("game.emeraldweapons.haven.orbe.hud", found, total);
        int w = Math.max(mc.font.width(value) + 18, mc.font.width(hidden)) + 10;
        // EN BAS A DROITE, ET NON EN HAUT : le haut-droit porte la minicarte de JourneyMap
        // (profil du joueur) et les effets du jeu -- le compteur y disparaissait (photo du 22 sept.)
        int x = graphics.guiWidth() - w - 4;
        int y = graphics.guiHeight() - 30;
        graphics.fill(x, y, x + w, y + 26, 0x90000000);
        graphics.fill(x, y, x + w, y + 1, 0xC0FFB030);
        // l'orbe : un ovale dore, un reflet
        int cx = x + 9;
        int cy = y + 9;
        for (int dy = -5; dy <= 5; dy++) {
            int half = (int) Math.round(4.0 * Math.sqrt(1.0 - (dy * dy) / 30.0));
            graphics.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, dy < -2 ? 0xFFFFD27A : dy < 2 ? 0xFFFFA630 : 0xFFD8761A);
        }
        graphics.fill(cx - 2, cy - 3, cx, cy - 1, 0xFFFFF4D0);
        graphics.drawString(mc.font, value, x + 18, y + 4, 0xFFFFC040, true);
        graphics.drawString(mc.font, hidden, x + 5, y + 16, 0xFFB0B0B0, false);
    }

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY,
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "haven_orbs"),
                    (LayeredDraw.Layer) HavenOrbsHud::render);
        }
    }
}
