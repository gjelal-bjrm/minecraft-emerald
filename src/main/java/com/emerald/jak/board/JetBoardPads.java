package com.emerald.jak.board;

import com.emerald.haven.Haven;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * LES TREMPLINS DU JET-BOARD (cahier §100) : trois bouches d'eco bleu au pied des chaines de rails.
 *
 * Les rails de Jak 3 pendent sous les arcades, de quatorze a trente blocs au-dessus de la rue, et
 * rien n'y mene : pas de plancher d'ou sauter (le plus haut est douze blocs dessous, le saut de la
 * planche en fait six et demi), et depuis la baie les piliers des arcades barrent toutes les
 * trajectoires essayees. On y passe en planche : le tremplin la lance sur le rail, trois blocs
 * apres son bout, dans le sens ou la chaine se remonte. A pied, il ne fait rien.
 *
 *  - A L'EST, sous le bout bas du rail 6 : la chaine est ne se fait que dans ce sens, en montant
 *    (6, 7, 8) -- dans l'autre, aucun saut ne retombe sur le rail suivant (simule a toutes les
 *    vitesses et toutes les charges). Elle finit en l'air au-dessus de la baie. Son elan est le
 *    plus long (quinze blocs, la planche arrive a 20 m/s) : le saut de quatorze blocs vers le rail 7
 *    demande alors 80 % de charge, ce que la barre du cheval garde quand on tient Espace ; a
 *    16 m/s, il fallait la charge pleine, au dixieme de seconde pres ;
 *  - A L'OUEST, sous le bout du rail 9, cote centre, et sous celui du rail 11, au bout de la
 *    ville : la chaine ouest se fait dans les deux sens.
 *
 * LE SAUT est calcule au pas d'une tique, comme JetBoardEntity.simulate : sommet a 0,8 bloc
 * au-dessus de la hauteur de glisse, a l'aplomb du point vise -- la planche s'y accroche en
 * redescendant. Les places et les elans ont ete choisis sur un sol plat, la boite de la planche
 * (joueur compris) ne touchant aucun bloc de la ville posee le long du vol ; le banc des vehicules
 * le verifie dans le monde, meubles de l'atelier compris.
 */
public final class JetBoardPads {

    /** Le tremplin se prend a tant de blocs de son centre, et jusqu'a tant au-dessus de lui. */
    static final double REACH = 1.5;
    static final double ABOVE = 1.5;
    /** Le sommet du saut, au-dessus de la hauteur de glisse du point vise. */
    static final double APEX = 0.8;
    /** Le point vise : a tant de blocs du dernier point du rail. */
    static final double LANDING = 3.0;
    /** L'eco bleu de ses etincelles. */
    static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.35F, 0.78F, 1.0F), 1.6F);

    /**
     * Un tremplin : son centre au sol, la vitesse de depart (m/s), le rail vise, le cap, et la
     * duree du vol jusqu'au sommet (tiques) -- la planche ne se dirige pas pendant ce temps.
     */
    public record Pad(int number, Vec3 at, Vec3 launch, int rail, float yaw, int flight) {
    }

    /**
     * Ou le poser : le rail (numero de JetBoardRails), l'elan (le tremplin est a tant de blocs du
     * point vise, en plan, dans l'axe du rail), et la hauteur du sol, en cellule du volume (les pieds).
     */
    private record Spec(int rail, double runUp, int floor) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec(0, 15.0, 66),
            new Spec(3, 16.0, 67),
            new Spec(5, 13.0, 67));

    private static final class Holder {
        static final List<Pad> PADS = build();
    }

    private JetBoardPads() {
    }

    private static List<Pad> build() {
        List<Pad> out = new ArrayList<>();
        List<JetBoardRails.Rail> rails = JetBoardRails.rails();
        for (Spec spec : SPECS) {
            if (spec.rail() >= rails.size()) {
                continue;
            }
            JetBoardRails.Rail rail = rails.get(spec.rail());
            List<Vec3> points = rail.points();
            // le point vise, en remontant le rail depuis son dernier point, et la direction vers l'interieur
            double left = LANDING;
            Vec3 land = points.get(0);
            Vec3 inward = null;
            for (int i = points.size() - 1; i > 0; i--) {
                Vec3 a = points.get(i);
                Vec3 b = points.get(i - 1);
                double length = a.distanceTo(b);
                if (left <= length) {
                    land = a.add(b.subtract(a).scale(left / length));
                    inward = b.subtract(a).normalize();
                    break;
                }
                left -= length;
            }
            if (inward == null) {
                continue;
            }
            Vec3 flat = new Vec3(inward.x, 0.0, inward.z).normalize();
            double floor = spec.floor() + Haven.ORIGIN.getY();
            Vec3 at = new Vec3(land.x - flat.x * spec.runUp(), floor, land.z - flat.z * spec.runUp());
            double top = land.y + JetBoardEntity.HOVER_RAIL + APEX;
            double rise = top - (floor + JetBoardEntity.HOVER);
            double half = JetBoardEntity.GRAVITY_MS2 * JetBoardEntity.TICK / 2.0;
            double vy = Math.sqrt(2.0 * JetBoardEntity.GRAVITY_MS2 * rise + half * half) - half;
            // la tique du sommet du saut discret (JetBoardEntity.jumpSpeed) : l'elan y est parcouru
            double ticks = vy / (JetBoardEntity.GRAVITY_MS2 * JetBoardEntity.TICK) + 0.5;
            double speed = spec.runUp() / (ticks * JetBoardEntity.TICK);
            float yaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
            out.add(new Pad(out.size(), at, new Vec3(flat.x * speed, vy, flat.z * speed), spec.rail(), yaw,
                    (int) Math.ceil(ticks)));
        }
        return List.copyOf(out);
    }

    public static List<Pad> pads() {
        return Holder.PADS;
    }

    /** Le tremplin sous la planche (sa position, le bas de sa boite), ou null. */
    @Nullable
    public static Pad under(Vec3 board) {
        for (Pad pad : Holder.PADS) {
            double dx = board.x - pad.at().x;
            double dz = board.z - pad.at().z;
            double dy = board.y - pad.at().y;
            if (dx * dx + dz * dz <= REACH * REACH && dy >= -0.3 && dy <= ABOVE) {
                return pad;
            }
        }
        return null;
    }
}
