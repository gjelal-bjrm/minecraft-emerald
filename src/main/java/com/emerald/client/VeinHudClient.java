package com.emerald.client;

import com.emerald.network.VeinSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * LA BOUSSOLE DE L'AURORE : ou creuser, lu a l'ecran.
 *
 * POURQUOI PAS DES PARTICULES. La premiere version envoyait un rai de lumiere
 * du filon jusqu'au-dessus du sol. Deux raisons pour lesquelles cela ne pouvait
 * pas marcher, et le joueur l'a constate en dix minutes de minage sans jamais
 * etre guide :
 *
 *  1. UNE PARTICULE SE CACHE DERRIERE LA PIERRE. Elle est dessinee avec le test
 *     de profondeur : sous terre, un rai qui monte a travers vingt blocs de
 *     roche n'existe pour personne. Or c'est SOUS TERRE qu'on a besoin d'etre
 *     guide -- en surface, il n'y a rien a miner ;
 *  2. `sendParticles` ne quitte pas trente-deux blocs. Le serveur ne l'envoie
 *     qu'aux joueurs a moins de 32 blocs, alors que la sonde en cherchait 40 :
 *     les filons les plus lointains -- ceux qu'on n'aurait pas trouves seul --
 *     etaient precisement les seuls a ne rien afficher.
 *
 * L'INTERFACE, ELLE, NE SE CACHE DERRIERE RIEN. Ni la roche, ni les shaders --
 * la lecon des nombres de degats : ce qui est dessine dans le monde disparait
 * sous Iris, ce qui est dessine sur le HUD survit a tout.
 *
 * Une fleche RELATIVE au regard, et non un point cardinal. « Sud-ouest »
 * demande de savoir ou est le sud ; « en haut a droite » se suit sans reflechir.
 */
public final class VeinHudClient {

    /** Largeur du panneau : la meme que celle du chronometre, pour aligner. */
    private static final int WIDTH = 116;
    private static final int LINE = 10;
    /** Au-dela, on considere que le serveur s'est taille : le panneau s'efface. */
    private static final int STALE = 20 * 8;
    /** Trois lignes : au-dela, on ne lit plus, on subit. */
    private static final int SHOWN = 3;

    private static final int DIAMOND = 0xFF6BE0FF;
    private static final int ARCENCIUM = 0xFFE478FF;

    private static java.util.List<Long> positions = java.util.List.of();
    private static int kinds;
    private static long seenAt = -1L;

    private VeinHudClient() {
    }

    public static void accept(VeinSyncPayload payload) {
        positions = payload.positions();
        kinds = payload.kinds();
        Minecraft mc = Minecraft.getInstance();
        seenAt = mc.level == null ? -1L : mc.level.getGameTime();
    }

    /** Vide le panneau : l'Aurore s'est terminee, ou l'on a change de monde. */
    public static void clear() {
        positions = java.util.List.of();
        kinds = 0;
        seenAt = -1L;
    }

    /**
     * Dessine le panneau et rend la hauteur occupee, zero s'il n'y a rien.
     *
     * Appele par {@link GameHudClient}, sous la meteo : c'est une information
     * de meteo, elle appartient a la meme colonne.
     */
    public static int render(GuiGraphics graphics, Minecraft mc, int x, int y) {
        if (mc.player == null || mc.level == null || positions.isEmpty()
                || seenAt < 0 || mc.level.getGameTime() - seenAt > STALE) {
            return 0;
        }
        int rows = Math.min(SHOWN, positions.size());
        graphics.fill(x, y, x + WIDTH, y + 2 + rows * LINE, 0x8C060608);
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        for (int i = 0; i < rows; i++) {
            BlockPos pos = BlockPos.of(positions.get(i));
            boolean diamond = (kinds & (1 << i)) != 0;
            double dx = pos.getX() + 0.5 - px;
            double dy = pos.getY() + 0.5 - py;
            double dz = pos.getZ() + 0.5 - pz;
            int flat = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            Component line = Component.literal(arrow(mc, dx, dz) + " ")
                    .append(Component.translatable(diamond
                            ? "weather.emeraldweapons.vein.diamond"
                            : "weather.emeraldweapons.vein.arcencium"))
                    .append(Component.literal(" " + flat + "m " + depth(dy)));
            graphics.drawString(mc.font, line, x + 3, y + 2 + i * LINE,
                    diamond ? DIAMOND : ARCENCIUM, false);
        }
        return 2 + rows * LINE + 2;
    }

    /**
     * La fleche, tournee dans le repere du JOUEUR.
     *
     * Zero degre veut dire « droit devant » : on tourne jusqu'a ce que la
     * fleche pointe vers le haut, et l'on marche. C'est la seule facon d'etre
     * guide sans lire de coordonnees.
     */
    private static String arrow(Minecraft mc, double dx, double dz) {
        double toTarget = Math.toDegrees(Math.atan2(dz, dx));
        double looking = mc.player.getYRot() + 90.0;      // yaw 0 = +Z, atan2 0 = +X
        double delta = Mth.wrapDegrees(toTarget - looking);
        // LE SIGNE COMPTE : l'angle croit vers l'EST puis le SUD, c'est-a-dire
        // vers la GAUCHE du joueur. Sans ce moins, la fleche envoyait
        // exactement a l'oppose -- et une boussole qui ment est pire qu'aucune.
        int step = (int) Math.round((-delta + 360.0) / 45.0) % 8;
        return switch (step) {
            case 0 -> "↑";      // droit devant
            case 1 -> "↗";
            case 2 -> "→";
            case 3 -> "↘";
            case 4 -> "↓";
            case 5 -> "↙";
            case 6 -> "←";
            default -> "↖";
        };
    }

    /** La profondeur, en clair : c'est elle qui dit s'il faut creuser ou remonter. */
    private static String depth(double dy) {
        int d = (int) Math.round(Math.abs(dy));
        if (d <= 1) {
            return "▬";                 // au meme niveau
        }
        return (dy < 0 ? "▼" : "▲") + Integer.toString(d);
    }
}
