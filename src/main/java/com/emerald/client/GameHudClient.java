package com.emerald.client;

import com.emerald.game.GamePhase;
import com.emerald.game.GameState;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.GameSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Locale;

/**
 * Le chronometre de partie, en haut de l'ecran.
 *
 * Dessine au code plutot que depuis une texture : le cadre doit changer de
 * couleur avec le temps restant et faire defiler une teinte prismatique, ce
 * qu'une image figee ne permet pas.
 *
 * Le compte a rebours se lit d'abord a sa COULEUR : vert, ambre, puis rouge.
 * On sait qu'on est en retard avant meme d'avoir lu les chiffres.
 *
 * Seule la classe imbriquee Setup porte @EventBusSubscriber : annoter aussi
 * l'exterieure, qui n'a aucun @SubscribeEvent, fait echouer le chargement du mod.
 */
public class GameHudClient {

    private static final int PANEL_W = 84;
    private static final int PANEL_H = 28;

    /**
     * En haut a GAUCHE, et non au centre.
     *
     * Le centre est deja pris trois fois : par Jade, qui affiche le bloc ou
     * l'entite visee, par les barres de boss pendant les sieges, et par les
     * titres d'annonce. Le coin superieur gauche est le seul reellement libre.
     */
    private static final int MARGIN = 4;

    private static final int GREEN = 0xFF78E8AE;
    private static final int AMBER = 0xFFFFC24A;
    private static final int RED = 0xFFFF616B;

    /** Seuils, en ticks, ou la couleur du chronometre bascule. */
    private static final long AMBER_AT = 10L * 60L * 20L;
    private static final long RED_AT = 3L * 60L * 20L;

    private static int status = GameState.Status.LOBBY.ordinal();
    private static long remaining;
    private static int phase;
    private static int anchors;
    private static java.util.List<Long> anchorPositions = java.util.List.of();
    private static int heldMask;

    public static void accept(GameSyncPayload payload) {
        status = payload.status();
        remaining = payload.remaining();
        phase = payload.phase();
        anchors = payload.anchors();
        anchorPositions = payload.anchorPositions();
        heldMask = payload.heldMask();
    }

    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static class Setup {
        @SubscribeEvent
        public static void onRegisterLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.HOTBAR,
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "game_timer"),
                    GameHudClient::render);
        }
    }

    private static void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        GameState.Status current = GameState.Status.values()[
                Math.floorMod(status, GameState.Status.values().length)];
        if (current == GameState.Status.LOBBY) {
            return;                       // hors partie, on n'encombre pas l'ecran
        }

        int x = MARGIN;
        int y = MARGIN;

        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0xB4060608);
        prismaticEdge(graphics, x, y, mc.level == null ? 0L : mc.level.getGameTime());

        int color = current == GameState.Status.RUNNING ? countdownColor() : phaseColor();
        String time = current == GameState.Status.RUNNING ? formatTime(remaining) : "--:--";
        graphics.drawCenteredString(mc.font, time, x + PANEL_W / 2, y + 5, color);

        Component label = Component.translatable(
                GamePhase.values()[Math.floorMod(phase, GamePhase.values().length)].translationKey());
        graphics.drawCenteredString(mc.font, label, x + PANEL_W / 2, y + 16, 0xFF9AA0A6);

        anchorPips(graphics, x + PANEL_W / 2, y + PANEL_H - 5);
        anchorList(graphics, mc, x, y + PANEL_H + 2);
    }

    /**
     * Les trois ancres, en permanence sous le chronometre.
     *
     * Distance et direction plutot que coordonnees brutes : on cherche a savoir
     * ou aller, pas a lire un nombre. Une ancre tenue s'affiche en vert et cesse
     * d'etre un objectif.
     */
    private static void anchorList(GuiGraphics graphics, Minecraft mc, int x, int y) {
        if (anchorPositions.isEmpty() || mc.player == null) {
            return;
        }
        int rows = Math.min(3, anchorPositions.size());
        graphics.fill(x, y, x + PANEL_W, y + 2 + rows * 10, 0x8C060608);
        for (int i = 0; i < rows; i++) {
            net.minecraft.core.BlockPos pos =
                    net.minecraft.core.BlockPos.of(anchorPositions.get(i));
            boolean done = (heldMask & (1 << i)) != 0;
            double dx = pos.getX() - mc.player.getX();
            double dz = pos.getZ() - mc.player.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            String label = String.format(Locale.ROOT, "%s %s  %dm",
                    done ? "◆" : "◇", cardinal(dx, dz), distance);
            graphics.drawString(mc.font, label, x + 4, y + 2 + i * 10,
                    done ? 0xFF78E8AE : 0xFF9CE8FF, false);
        }
    }

    /** Direction cardinale vers un point, du plus lisible au premier coup d'oeil. */
    private static String cardinal(double dx, double dz) {
        String[] names = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        // atan2 rend l'angle depuis l'axe +X, qui pointe a l'est ; on ramene sur
        // les huit secteurs en partant du sud, comme la boussole du jeu
        int index = (int) Math.round(((angle + 360.0) % 360.0) / 45.0) % 8;
        return names[(index + 6) % 8];
    }

    /** Liseré superieur dont la teinte defile : la signature visuelle du mod. */
    private static void prismaticEdge(GuiGraphics graphics, int x, int y, long time) {
        for (int i = 0; i < PANEL_W; i++) {
            float hue = ((i / (float) PANEL_W) * 0.75F + time * 0.006F) % 1.0F;
            graphics.fill(x + i, y, x + i + 1, y + 1,
                    0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.55F, 1.0F));
        }
    }

    /**
     * Trois losanges : les ancres deja tenues.
     *
     * L'objectif reste sous les yeux en permanence, sans ouvrir de menu -- c'est
     * le seul chiffre qui decide de tout le reste de la partie.
     */
    private static void anchorPips(GuiGraphics graphics, int centerX, int y) {
        int spacing = 12;
        int startX = centerX - spacing;
        for (int i = 0; i < 3; i++) {
            int px = startX + i * spacing;
            int color = i < anchors ? 0xFF9CE8FF : 0xFF3A3D42;
            graphics.fill(px - 2, y, px + 3, y + 1, color);
            graphics.fill(px - 1, y - 1, px + 2, y + 2, color);
        }
    }

    private static int countdownColor() {
        if (remaining <= RED_AT) {
            return RED;
        }
        return remaining <= AMBER_AT ? AMBER : GREEN;
    }

    private static int phaseColor() {
        return 0xFF000000 | GamePhase.values()[
                Math.floorMod(phase, GamePhase.values().length)].color;
    }

    private static String formatTime(long ticks) {
        long seconds = ticks / 20L;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }
}
