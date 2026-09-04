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
    private static int mode = GameState.Mode.DEFI.ordinal();
    private static int cycle = 1;
    private static long remaining;
    private static int phase;
    private static int anchors;
    private static java.util.List<Long> anchorPositions = java.util.List.of();
    private static int heldMask;
    /** L'arene finale, ou 0 tant que l'Arc-en-ciel n'est pas leve. */
    private static long finalePos;

    public static void accept(GameSyncPayload payload) {
        status = payload.status();
        mode = payload.mode();
        cycle = payload.cycle();
        remaining = payload.remaining();
        phase = payload.phase();
        anchors = payload.anchors();
        anchorPositions = payload.anchorPositions();
        heldMask = payload.heldMask();
        finalePos = payload.finalePos();
    }

    public static long finalePos() {
        return finalePos;
    }

    public static int statusOrdinal() {
        return status;
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
            // hors partie on n'encombre pas l'ecran -- mais la meteo, elle, se
            // declenche aussi en lobby (/arcencium weather) et se voit deja par
            // son brouillard : la laisser sans etiquette n'aurait pas de sens
            weatherPanel(graphics, mc, MARGIN, MARGIN);
            VeinHudClient.render(graphics, mc, MARGIN, MARGIN + 14);
            return;
        }

        int x = MARGIN;
        int y = MARGIN;

        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0xB4060608);
        prismaticEdge(graphics, x, y, mc.level == null ? 0L : mc.level.getGameTime());

        // EN MONDE OUVERT, LA PREMIERE LIGNE DIT LE CYCLE ET NON L'HEURE.
        //
        // Un compte a rebours qui ne compte pas serait un mensonge, et un
        // « --:-- » ne dit rien. Le numero du cycle, lui, est la seule mesure
        // de progression qui existe ici : c'est le nombre de fois qu'on a
        // abattu le boss.
        boolean endless = mode == GameState.Mode.LIBRE.ordinal();
        int color = current == GameState.Status.RUNNING && !endless
                ? countdownColor() : phaseColor();
        if (endless && current == GameState.Status.RUNNING) {
            graphics.drawCenteredString(mc.font,
                    Component.translatable("game.emeraldweapons.hud.cycle", cycle),
                    x + PANEL_W / 2, y + 5, color);
        } else {
            String time = current == GameState.Status.RUNNING ? formatTime(remaining) : "--:--";
            graphics.drawCenteredString(mc.font, time, x + PANEL_W / 2, y + 5, color);
        }

        // la partie finie, le panneau dit le verdict plutot qu'une phase
        Component label;
        int labelColor = 0xFF9AA0A6;
        if (current == GameState.Status.WON) {
            label = Component.translatable("game.emeraldweapons.hud.won");
            labelColor = 0xFFFFD36B;
        } else if (current == GameState.Status.LOST) {
            label = Component.translatable("game.emeraldweapons.hud.lost");
            labelColor = 0xFFB98CFF;
        } else {
            label = Component.translatable(
                    GamePhase.values()[Math.floorMod(phase, GamePhase.values().length)].translationKey());
        }
        graphics.drawCenteredString(mc.font, label, x + PANEL_W / 2, y + 16, labelColor);

        anchorPips(graphics, x + PANEL_W / 2, y + PANEL_H - 5);
        anchorList(graphics, mc, x, y + PANEL_H + 2);
        // le panneau meteo se cale sous les ancres RESTANTES : compter celles
        // qui sont prises laisserait un trou qui grandit a chaque victoire
        int rows = 0;
        for (int i = 0; i < Math.min(3, anchorPositions.size()); i++) {
            if ((heldMask & (1 << i)) == 0) {
                rows++;
            }
        }
        if (finalePos != 0L && current == GameState.Status.RUNNING) {
            rows++;                                   // la ligne de l'Arc-en-ciel
        }
        int weatherY = y + PANEL_H + 2 + (rows > 0 ? rows * 10 + 4 : 0);
        weatherPanel(graphics, mc, x, weatherY);
        // LES FILONS SOUS LA METEO : c'est une information de meteo, elle
        // appartient a la meme colonne. Quatorze pixels sous elle -- douze de
        // panneau et deux de respiration.
        VeinHudClient.render(graphics, mc, x, weatherY + 14);
    }

    /**
     * La meteo, sous les ancres : le nom dans sa couleur et le temps restant,
     * ou l'avertissement quand une tempete approche. Une meteo qui change la
     * facon de jouer doit rester lisible sans ouvrir quoi que ce soit.
     */
    private static void weatherPanel(GuiGraphics graphics, Minecraft mc, int x, int y) {
        int pendingOrdinal = com.emerald.client.WeatherClient.pendingOrdinal();
        com.emerald.weather.Weather weather = com.emerald.client.WeatherClient.current();

        if (pendingOrdinal >= 0) {
            com.emerald.weather.Weather incoming = com.emerald.weather.Weather.values()[
                    Math.floorMod(pendingOrdinal, com.emerald.weather.Weather.values().length)];
            // LE PANNEAU NE DIT PLUS CE QUI VIENT.
            //
            // Il disait le nom et les secondes restantes, ce qui rendait le
            // presage inutile : on n'avait pas a deviner, il suffisait de lire
            // le coin de l'ecran. Il ne reste que le signe -- quelque chose
            // arrive -- et sa COULEUR, seul indice offert a qui la connait.
            graphics.fill(x, y, x + PANEL_W, y + 12, 0x8C060608);
            Component label = Component.literal("⚠ ")
                    .append(Component.translatable("game.emeraldweapons.weather.omen"));
            graphics.drawString(mc.font, label, x + 3, y + 2,
                    0xFF000000 | incoming.color, false);
            return;
        }
        if (weather == com.emerald.weather.Weather.CLEAR) {
            return;
        }
        graphics.fill(x, y, x + PANEL_W, y + 12, 0x8C060608);
        Component label = Component.translatable(weather.translationKey())
                .append(" " + formatTime(com.emerald.client.WeatherClient.remainingTicks()));
        graphics.drawString(mc.font, label, x + 3, y + 2, 0xFF000000 | weather.color, false);
    }

    /**
     * Les trois ancres, en permanence sous le chronometre.
     *
     * Distance et direction plutot que coordonnees brutes : on cherche a savoir
     * ou aller, pas a lire un nombre. Une ancre tenue s'affiche en vert et cesse
     * d'etre un objectif.
     */
    private static void anchorList(GuiGraphics graphics, Minecraft mc, int x, int y) {
        if (mc.player == null) {
            return;
        }
        GameState.Status current = GameState.Status.values()[
                Math.floorMod(status, GameState.Status.values().length)];
        boolean arena = finalePos != 0L && current == GameState.Status.RUNNING;
        // UNE ANCRE PRISE DISPARAIT DE LA LISTE.
        //
        // Elle restait affichee avec un losange plein au lieu d'un losange
        // vide. La nuance ne se lit pas d'un coup d'oeil : on se dirigeait vers
        // une ancre deja tenue en croyant qu'il restait a la prendre. Une liste
        // d'objectifs ne doit contenir que ce qui reste a faire -- ce qui est
        // fait se voit sur le terrain.
        java.util.List<Integer> todo = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(3, anchorPositions.size()); i++) {
            if ((heldMask & (1 << i)) == 0) {
                todo.add(i);
            }
        }
        if (todo.isEmpty() && !arena) {
            return;
        }
        int rows = todo.size() + (arena ? 1 : 0);
        graphics.fill(x, y, x + PANEL_W, y + 2 + rows * 10, 0x8C060608);
        if (arena) {
            // l'Arc-en-ciel en derniere ligne, dans sa couleur qui tourne : c'est l'objectif
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(finalePos);
            double dx = pos.getX() - mc.player.getX();
            double dz = pos.getZ() - mc.player.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            long time = mc.level == null ? 0L : mc.level.getGameTime();
            int color = 0xFF000000 | java.awt.Color.HSBtoRGB((time % 120L) / 120F, 0.55F, 1.0F);
            String label = String.format(Locale.ROOT, "%s %s  %dm", "◈", cardinal(dx, dz), distance);
            graphics.drawString(mc.font, label, x + 4, y + 2 + (rows - 1) * 10, color, false);
        }
        for (int r = 0; r < todo.size(); r++) {
            int i = todo.get(r);
            net.minecraft.core.BlockPos pos =
                    net.minecraft.core.BlockPos.of(anchorPositions.get(i));
            boolean done = false;
            double dx = pos.getX() - mc.player.getX();
            double dz = pos.getZ() - mc.player.getZ();
            int distance = (int) Math.sqrt(dx * dx + dz * dz);
            String label = String.format(Locale.ROOT, "%s %s  %dm",
                    done ? "◆" : "◇", cardinal(dx, dz), distance);
            graphics.drawString(mc.font, label, x + 4, y + 2 + r * 10,
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
