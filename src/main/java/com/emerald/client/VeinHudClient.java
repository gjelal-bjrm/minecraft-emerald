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
    private static final int PREY = 0xFFFFC46B;
    private static final int MIST = 0xFFC8D8FF;

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

    /** Les filons connus, ou rien si le panneau est perime : le repere lit la meme source. */
    public static java.util.List<Long> shown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || seenAt < 0 || mc.level.getGameTime() - seenAt > STALE) {
            return java.util.List.of();
        }
        return positions;
    }

    public static int kinds() {
        return kinds;
    }

    /** La couleur d'une sorte, partagee entre le panneau et le repere. */
    public static int colourOf(int kind) {
        return switch (kind) {
            case VeinSyncPayload.KIND_DIAMOND -> DIAMOND;
            case VeinSyncPayload.KIND_PREY -> PREY;
            case VeinSyncPayload.KIND_MIST -> MIST;
            default -> ARCENCIUM;
        };
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
            int kind = VeinSyncPayload.kindAt(kinds, i);
            double dx = pos.getX() + 0.5 - px;
            double dy = pos.getY() + 0.5 - py;
            double dz = pos.getZ() + 0.5 - pz;
            int flat = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
            String key = switch (kind) {
                case VeinSyncPayload.KIND_DIAMOND -> "weather.emeraldweapons.vein.diamond";
                case VeinSyncPayload.KIND_PREY -> "weather.emeraldweapons.vein.prey";
                case VeinSyncPayload.KIND_MIST -> "weather.emeraldweapons.vein.mist";
                default -> "weather.emeraldweapons.vein.arcencium";
            };
            int colour = colourOf(kind);
            Component line = Component.literal(arrow(mc, dx, dz) + " ")
                    .append(Component.translatable(key))
                    .append(Component.literal(" " + flat + "m " + depth(dy)));
            graphics.drawString(mc.font, line, x + 3, y + 2 + i * LINE, colour, false);
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
        // LE SIGNE, VERIFIE A L'ECRAN ET NON DEDUIT.
        //
        // J'avais raisonne que le vecteur « a droite » du joueur etait
        // (cos yaw, sin yaw) et ajoute un moins en consequence. C'est le
        // vecteur A GAUCHE : lacet zero regarde le sud, et le sud a l'est sur
        // sa gauche. La capture d'essai l'a montre d'un coup d'oeil -- le filon
        // pose a l'est s'affichait a droite. Une boussole qui ment est pire
        // qu'aucune boussole ; celle-ci se relit sur une image, pas sur un
        // raisonnement.
        // SEIZE SECTEURS, ET UNE CIBLE QUAND ON EST ALIGNE.
        //
        // Mesure du defaut rapporte (« ca ne me disait pas dans quelle
        // direction ») : a huit secteurs, une case fait quarante-cinq degres,
        // et au bout d'un tunnel de vingt-trois blocs on pouvait passer a NEUF
        // BLOCS ET DEMI a cote du filon. A seize, l'ecart tombe a quatre et
        // demi ; et sous six degres on affiche une cible : c'est le signal
        // qu'on peut creuser tout droit, a deux blocs pres.
        if (Math.abs(delta) <= 6.0) {
            return "◎";
        }
        int step = (int) Math.round((delta + 360.0) / 22.5) % 16;
        if (step % 2 == 1) {
            // entre deux fleches : on dit de quel cote corriger
            return delta > 0 ? "»" : "«";
        }
        return switch (step / 2) {
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
