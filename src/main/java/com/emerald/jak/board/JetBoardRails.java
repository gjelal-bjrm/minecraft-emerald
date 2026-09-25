package com.emerald.jak.board;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenCables;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * LES RAILS DU JET-BOARD (cahier §100) : les six tubes a glisser de Jak 3 que la ville a gardes
 * (HavenCables, lignes « rail » -- city-port-smallpipe-straight-grind), en coordonnees du monde,
 * des deux cotes. Deux chaines : a l'est (trois rails, sauts de 14 et 11 blocs), a l'ouest (trois
 * rails, sauts de 8). Ils ne sont que dessines (HavenCableRenderer) : la glisse est ici, en calcul.
 *
 * Dans le jeu, la planche s'accroche a une arete trouvee par la recherche de rebord
 * (collide-edge-grab.gc), a 2,9 m de cote et 0,7 m de haut ; ici, au plus proche point d'un rail.
 */
public final class JetBoardRails {

    /** Un rail : sa polyligne, dans le monde. */
    public record Rail(int index, List<Vec3> points) {
    }

    /** Le point d'un rail le plus proche : rail, segment, abscisse le long du segment (0 a sa longueur), point. */
    public record Hit(Rail rail, int segment, double along, Vec3 point) {
    }

    private static final class Holder {
        static final List<Rail> RAILS = build();
    }

    private JetBoardRails() {
    }

    private static List<Rail> build() {
        List<Rail> out = new ArrayList<>();
        for (HavenCables.Line line : HavenCables.lines()) {
            if (!line.rail() || line.points().size() < 2) {
                continue;
            }
            List<Vec3> world = new ArrayList<>();
            for (Vec3 p : line.points()) {
                world.add(p.add(Haven.ORIGIN.getX(), Haven.ORIGIN.getY(), Haven.ORIGIN.getZ()));
            }
            out.add(new Rail(out.size(), List.copyOf(world)));
        }
        return List.copyOf(out);
    }

    public static List<Rail> rails() {
        return Holder.RAILS;
    }

    /**
     * Le point de rail le plus proche de {@code at}, a moins de {@code reach} de cote et dans la
     * bande [{@code below} ; {@code above}] en hauteur (au-dessus du rail positif) ; null sinon.
     */
    @Nullable
    public static Hit nearest(Vec3 at, double reach, double below, double above) {
        Hit best = null;
        double bestFlat = reach * reach;
        for (Rail rail : Holder.RAILS) {
            List<Vec3> points = rail.points();
            for (int i = 0; i + 1 < points.size(); i++) {
                Vec3 a = points.get(i);
                Vec3 b = points.get(i + 1);
                Vec3 ab = b.subtract(a);
                double lengthSqr = ab.lengthSqr();
                if (lengthSqr < 1.0E-6) {
                    continue;
                }
                // le plus proche du segment, en plan : c'est la qu'on retombe sur un rail
                double t = ((at.x - a.x) * ab.x + (at.z - a.z) * ab.z) / (ab.x * ab.x + ab.z * ab.z + 1.0E-9);
                t = Math.max(0.0, Math.min(1.0, t));
                Vec3 p = a.add(ab.scale(t));
                double dx = at.x - p.x;
                double dz = at.z - p.z;
                double flat = dx * dx + dz * dz;
                double dy = at.y - p.y;
                if (flat < bestFlat && dy >= below && dy <= above) {
                    bestFlat = flat;
                    best = new Hit(rail, i, t * Math.sqrt(lengthSqr), p);
                }
            }
        }
        return best;
    }

    /** La longueur d'un segment. */
    public static double length(Rail rail, int segment) {
        return rail.points().get(segment + 1).distanceTo(rail.points().get(segment));
    }

    /** Le point a cette abscisse du segment. */
    public static Vec3 at(Rail rail, int segment, double along) {
        Vec3 a = rail.points().get(segment);
        Vec3 b = rail.points().get(segment + 1);
        double length = a.distanceTo(b);
        return length < 1.0E-6 ? a : a.add(b.subtract(a).scale(along / length));
    }

    /** La direction du segment, dans le sens de la glisse (+1 vers la fin de la ligne, -1 vers son debut). */
    public static Vec3 direction(Rail rail, int segment, int sense) {
        Vec3 d = rail.points().get(segment + 1).subtract(rail.points().get(segment)).normalize();
        return sense > 0 ? d : d.scale(-1.0);
    }
}
