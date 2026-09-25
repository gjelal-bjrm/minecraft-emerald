package com.emerald.jak.board;

import com.emerald.haven.Haven;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * LE TRACE DE LA COURSE DE TESS (cahier §100) : « enchainer les deux chaines en passant des
 * anneaux ». D'est en ouest, parce que la chaine est ne se fait qu'en montant (JetBoardPads) :
 *
 *  1. le tremplin est, sous le bout bas du rail 6 ; les rails 6, 7 et 8 en montant, les sauts de
 *     quatorze et onze blocs entre eux ;
 *  2. LE PLONGEON : le rail 8 finit a trente blocs au-dessus de la baie, la planche y tombe (entre
 *     seize et quarante blocs plus loin selon sa vitesse, a cote du cargo amarre) ;
 *  3. LA BAIE vers l'ouest, au large des quais, de la jetee du centre et du bout du bras ouest --
 *     un demi-kilometre d'eau libre, verifie tous les demi-blocs a deux blocs de marge ;
 *  4. L'ESCALIER du quai ouest -- les quais sont a huit blocs au-dessus de l'eau, une planche n'en
 *     remonte que par des marches d'un bloc : celles-ci, en diagonale --, puis la rue
 *     jusqu'au tremplin ouest, sous le bout du rail 9, en passant a l'ouest de deux bacs de deux
 *     blocs de haut (chemin cherche cellule par cellule, marches d'un bloc au plus, voisines
 *     comprises : la planche fait 0,9 bloc de large) ;
 *  5. les rails 9, 10 et 11, sauts de huit blocs ; l'arrivee au bout du rail 11.
 *
 * Les anneaux des rails sont a la hauteur du torse de celui qui glisse, tous les douze blocs ; ceux
 * des sauts au milieu du vide, a la hauteur des arcs qui retombent sur le rail suivant (simules a
 * toutes les vitesses et toutes les charges), et ils se passent a toute hauteur de cet arc : qui a
 * saute est passe. Ceux de la baie posent leur bas sur l'eau.
 */
public final class JetBoardCourse {

    /** Un anneau, ou le tremplin du depart : son centre, son cap, son rayon dessine, et ou il se passe. */
    public record Ring(Vec3 center, float yaw, float radius, double reach, double band, Kind kind) {

        /** Le centre du joueur (sa position plus 0,9) passe-t-il cet anneau ? */
        public boolean passedBy(Vec3 torso) {
            double dx = torso.x - this.center.x;
            double dz = torso.z - this.center.z;
            return dx * dx + dz * dz <= this.reach * this.reach && Math.abs(torso.y - this.center.y) <= this.band;
        }
    }

    public enum Kind { PAD, RAIL, GAP, WATER, STAIRS, STREET, FINISH }

    /** Tous les douze blocs de rail ; le premier a quatre blocs de l'entree, le dernier a deux de la sortie. */
    static final double RAIL_SPACING = 12.0;
    /** Le torse d'un joueur debout sur la planche, au-dessus d'elle : pieds 0,04 plus haut, torse 0,9. */
    public static final double RIDER_TORSO = JetBoardEntity.BOARD_TOP + 0.9;
    /** Le torse de celui qui glisse : la planche a 0,35 au-dessus du tube. */
    static final double TORSO_ON_RAIL = JetBoardEntity.HOVER_RAIL + RIDER_TORSO;
    /** Le dessus de l'eau de la baie (cellule 57 du volume, pleine d'eau), dans le monde. */
    static final double WATER_TOP = 58.0 + Haven.ORIGIN.getY();

    /** La baie, en cellules du volume : du plongeon au pied de l'escalier du quai ouest. */
    private static final double[][] BAY = {
            {812, 268}, {760, 290}, {700, 300}, {640, 306}, {570, 298}, {495, 288}, {435, 270}, {380, 272},
            {362, 280}};
    /**
     * L'escalier du quai ouest (son pied, son milieu, le haut) puis la rue : cellule du volume et
     * hauteur des pieds.
     */
    private static final double[][] STAIRS = {{354, 279, 58}, {349, 274, 61}, {344, 271, 66}};
    private static final double[] STREET = {343, 258, 66};

    private static final class Holder {
        static final List<Ring> RINGS = build();
    }

    private JetBoardCourse() {
    }

    public static List<Ring> rings() {
        return Holder.RINGS;
    }

    private static List<Ring> build() {
        List<Ring> out = new ArrayList<>();
        List<JetBoardPads.Pad> pads = JetBoardPads.pads();
        if (JetBoardRails.rails().size() < 6 || pads.size() < 2) {
            return List.of();
        }
        pad(out, pads.get(0));
        rail(out, 0, -1, JetBoardPads.LANDING, false);
        gap(out, 0, -1, 1, 1);
        rail(out, 1, 1, 0.0, false);
        gap(out, 1, 1, 2, 1);
        rail(out, 2, 1, 0.0, false);
        Vec3 last = end(2, 1);
        for (double[] p : BAY) {
            Vec3 at = new Vec3(p[0] + 0.5, WATER_TOP + 2.8, p[1] + 0.5);
            out.add(new Ring(at, heading(last, at), 3.0F, 4.5, 4.0, Kind.WATER));
            last = at;
        }
        for (double[] p : STAIRS) {
            Vec3 at = ground(p);
            out.add(new Ring(at, heading(last, at), 2.0F, 3.0, 3.5, Kind.STAIRS));
            last = at;
        }
        Vec3 street = ground(STREET);
        out.add(new Ring(street, heading(last, street), 2.0F, 3.5, 3.5, Kind.STREET));
        pad(out, pads.get(1));
        rail(out, 3, -1, JetBoardPads.LANDING, false);
        gap(out, 3, -1, 4, 1);
        rail(out, 4, 1, 0.0, false);
        gap(out, 4, 1, 5, 1);
        rail(out, 5, 1, 0.0, true);
        return List.copyOf(out);
    }

    private static void pad(List<Ring> out, JetBoardPads.Pad pad) {
        // la bande est large : le joueur qui passe le tremplin est deja deux blocs plus haut la tique d'apres
        out.add(new Ring(pad.at().add(0.0, 1.5, 0.0), pad.yaw(), (float) JetBoardPads.REACH, JetBoardPads.REACH + 0.5, 3.0,
                Kind.PAD));
    }

    /**
     * Les anneaux d'un rail, dans le sens de la glisse, a partir de {@code entry} blocs de son entree ;
     * le dernier devient l'arrivee si {@code finish}.
     */
    private static void rail(List<Ring> out, int index, int sense, double entry, boolean finish) {
        JetBoardRails.Rail rail = JetBoardRails.rails().get(index);
        List<Vec3> points = new ArrayList<>(rail.points());
        if (sense < 0) {
            java.util.Collections.reverse(points);
        }
        double total = 0.0;
        for (int i = 0; i + 1 < points.size(); i++) {
            total += points.get(i).distanceTo(points.get(i + 1));
        }
        List<Ring> rings = new ArrayList<>();
        for (double d = entry + 4.0; d <= total - 2.0; d += RAIL_SPACING) {
            rings.add(railRing(points, d, Kind.RAIL));
        }
        if (rings.isEmpty()) {
            rings.add(railRing(points, (entry + total) / 2.0, Kind.RAIL));
        }
        if (finish) {
            Ring last = rings.remove(rings.size() - 1);
            rings.add(new Ring(last.center(), last.yaw(), 2.2F, 2.6, 3.0, Kind.FINISH));
        }
        out.addAll(rings);
    }

    private static Ring railRing(List<Vec3> points, double along, Kind kind) {
        double left = along;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            double length = a.distanceTo(b);
            if (left <= length || i + 2 == points.size()) {
                Vec3 p = a.add(b.subtract(a).scale(Math.min(1.0, left / length)));
                return new Ring(p.add(0.0, TORSO_ON_RAIL, 0.0), heading(a, b), 1.6F, 2.2, 2.5, kind);
            }
            left -= length;
        }
        throw new IllegalStateException("rail vide");
    }

    /**
     * L'anneau d'un saut : au milieu du vide entre la sortie d'un rail et l'entree du suivant, a la
     * hauteur des arcs qui y retombent -- le torse de 3,9 a 8,3 blocs au-dessus du depart pour le
     * saut de quatorze blocs, de 2,6 a 7,7 pour ceux de huit -- et il se passe a toute cette hauteur.
     */
    private static void gap(List<Ring> out, int from, int fromSense, int to, int toSense) {
        Vec3 a = end(from, fromSense);
        Vec3 b = end(to, -toSense);
        double length = Math.hypot(b.x - a.x, b.z - a.z);
        Vec3 mid = a.add(b).scale(0.5);
        double lift = TORSO_ON_RAIL + 0.35 * length;
        out.add(new Ring(new Vec3(mid.x, a.y + lift, mid.z), heading(a, b), 2.6F, 3.5 + 0.1 * length, 4.5, Kind.GAP));
    }

    /** Un anneau pose au sol : le torse de celui qui plane au-dessus de cette cellule (x, z, pieds). */
    private static Vec3 ground(double[] p) {
        return new Vec3(p[0] + 0.5, p[2] + Haven.ORIGIN.getY() + JetBoardEntity.HOVER + RIDER_TORSO, p[1] + 0.5);
    }

    /** Le bout d'un rail par ou l'on sort en glissant dans ce sens. */
    private static Vec3 end(int index, int sense) {
        List<Vec3> points = JetBoardRails.rails().get(index).points();
        return sense > 0 ? points.get(points.size() - 1) : points.get(0);
    }

    private static float heading(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }
}
