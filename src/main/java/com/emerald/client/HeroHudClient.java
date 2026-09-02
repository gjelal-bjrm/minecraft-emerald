package com.emerald.client;

import com.emerald.hero.HeroLevel;
import com.emerald.hero.HeroStat;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.HeroSyncPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Le niveau Heros a l'ecran, et la touche qui ouvre la fiche.
 *
 * Deux choses, et deux seulement, en permanence : le niveau et le pourcentage
 * du niveau suivant. C'est ce qu'on veut savoir en jouant. Tout le reste --
 * les quatre voies, les paliers, les bonus -- vit dans la fiche, qui s'ouvre a
 * la demande : afficher en continu ce qui ne se consulte qu'entre deux vagues
 * encombrerait l'ecran sans rien apprendre.
 *
 * EN BAS A GAUCHE. Le haut a gauche porte deja le chronometre et les ancres,
 * le haut a droite la minimap, le centre les barres de siege et les annonces,
 * le bord droit la Sonde. Le coin inferieur gauche est le dernier libre, et il
 * a l'avantage d'etre a cote de la barre d'experience ordinaire -- les deux
 * progressions se lisent d'un meme coup d'oeil.
 *
 * Un LISERE s'allume quand des points attendent d'etre places. Sans lui, un
 * joueur en plein siege ne remarque pas qu'il a gagne de quoi se renforcer :
 * la recompense existe et ne sert a rien.
 */
public class HeroHudClient {

    public static final KeyMapping SHEET_KEY = new KeyMapping(
            "key.emeraldweapons.hero_sheet",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.emeraldweapons");

    private static final int PANEL_W = 96;
    private static final int PANEL_H = 26;
    private static final int MARGIN = 4;

    private static final int GOLD = 0xFFFFD24A;
    private static final int VIOLET = 0xFFB98CFF;

    /** La derniere fiche recue. Nulle tant que le serveur n'a rien dit. */
    private static HeroSyncPayload sheet;

    public static void accept(HeroSyncPayload payload) {
        sheet = payload;
    }

    public static HeroSyncPayload sheet() {
        return sheet;
    }

    /** Le niveau atteint dans cette voie, selon la derniere fiche. */
    public static int path(HeroStat stat) {
        return sheet == null ? 0 : sheet.path(stat.ordinal());
    }

    // ------------------------------------------------------------- le dessin

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (sheet == null || mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        int x = MARGIN;
        int y = graphics.guiHeight() - PANEL_H - MARGIN;

        boolean waiting = sheet.free() > 0;
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0xC00A0A12);
        frame(graphics, x, y, PANEL_W, PANEL_H, waiting ? VIOLET : 0x60FFFFFF);

        boolean maxed = sheet.level() >= HeroLevel.MAX_LEVEL;
        // Le pourcentage passe par le RAPPORT et non par la difference : le
        // cout d'un niveau change a chaque palier, et « il me manque quarante
        // points » ne dit rien tant qu'on ignore le prix courant.
        int percent = maxed ? 100
                : (int) Math.floor(100.0 * sheet.xp() / Math.max(1, sheet.needed()));

        graphics.drawString(mc.font, Component.translatable(
                        "hero.emeraldweapons.hud", sheet.level()),
                x + 5, y + 4, GOLD, true);
        String right = maxed ? "MAX" : percent + "%";
        graphics.drawString(mc.font, right,
                x + PANEL_W - 5 - mc.font.width(right), y + 4, 0xFFC8C8D4, true);

        // la jauge
        int bx = x + 5;
        int bw = PANEL_W - 10;
        int by = y + 15;
        graphics.fill(bx, by, bx + bw, by + 4, 0xFF1C1C28);
        int fill = maxed ? bw : (int) (bw * Math.min(1.0, sheet.xp() / (double) Math.max(1, sheet.needed())));
        if (fill > 0) {
            graphics.fill(bx, by, bx + fill, by + 4, maxed ? VIOLET : GOLD);
        }
        if (waiting) {
            String tip = Component.translatable("hero.emeraldweapons.hud_points",
                    sheet.free()).getString();
            graphics.drawString(mc.font, tip, x + 5, y - 10, VIOLET, true);
        }
    }

    private static void frame(GuiGraphics graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y, x + 1, y + h, colour);
        graphics.fill(x + w - 1, y, x + w, y + h, colour);
    }

    // -------------------------------------------------------------- la touche

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
    public static class Input {
        @SubscribeEvent
        public static void onTick(PlayerTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || event.getEntity() != mc.player) {
                return;
            }
            while (SHEET_KEY.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new HeroScreen());
                }
            }
        }
    }

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "hero_hud"),
                    (LayeredDraw.Layer) HeroHudClient::render);
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(SHEET_KEY);
        }
    }
}
