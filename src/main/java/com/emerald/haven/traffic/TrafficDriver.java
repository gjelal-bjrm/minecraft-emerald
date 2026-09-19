package com.emerald.haven.traffic;

import com.emerald.jak.vehicle.JakVehicleEntity;
import com.emerald.jak.vehicle.VehiclePhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Le pilote d'un vehicule du trafic : suit les branches du graphe de Jak 3, cote serveur.
 *
 * C'est le port, simplifie, de la conduite IA du jeu (vehicle-controller-method-18,
 * hvehicle-util.gc:10-233, et l'integration cinematique de hvehicle-method-157,
 * hvehicle.gc:613-651) :
 *
 *  - LA VOIE : la branche courante, du noeud de depart au noeud d'arrivee ; le point
 *    vise est 0,4 s devant sur la voie, et continue sur la branche suivante, choisie
 *    d'avance au hasard parmi celles qui partent du noeud d'arrivee (vehicle-control.gc:181-219,
 *    tirage uniforme, sans depasser leur nombre maximal d'usagers) ;
 *  - LA VITESSE : la vitesse de la branche plus l'ecart propre du vehicule, suivie au
 *    gain 2/s (a = 2 (v visee - v)) ; bornee au bout de la branche par la vitesse de
 *    virage sqrt(rayon x 12 m/s2), elargie pour un virage doux, sans borne sous 10
 *    degres, et appliquee des la distance de freinage (v2 - v_virage2) / 24 ;
 *  - LE VEHICULE DE DEVANT : si, dans un cone de 45 degres, une collision est predite
 *    dans les 2 s, on ne va pas plus vite que lui (hvehicle-util.gc:131-145) ;
 *  - L'ALTITUDE : celle de la voie entre les deux noeuds, rappel a_y = 8 (h - y) - v_y ;
 *  - LE DEPLACEMENT : cinematique, mais A TRAVERS LES COLLISIONS DE LA VILLE (VehiclePhysics.move,
 *    toutes les boites) : contre un obstacle -- une voiture de joueur garee sur la voie --,
 *    la vitesse bloquee tombe a zero, et le vehicule repart quand la voie se libere ;
 *  - LE CAP suit la vitesse, lisse a 0,6 x v(m/s) par seconde ; le roulis visuel vient
 *    du client, comme pour les voitures des joueurs.
 *
 * Une sortie du quartier (branche sans destination) : le vehicule s'eloigne vers le
 * point de sortie et disparait au bout ; un noeud sans branche sortante, de meme.
 */
public final class TrafficDriver {

    /** Anticipation : le point vise est 0,4 s devant, sur la voie (hvehicle-util.gc:185). */
    static final double LOOKAHEAD_S = 0.4;
    /** La vitesse suit sa consigne au gain 2/s (hvehicle-util.gc:213). */
    static final double SPEED_GAIN = 2.0;
    /** Acceleration de virage, m/s2 (vehicle-control.gc:339, en dur dans le jeu). */
    static final double TURN_ACCEL_MS2 = 12.0;
    /** Rappel vertical : a_y = 8 (h - y) - v_y, en metres et m/s (hvehicle.gc:623-624). */
    static final double VERTICAL_GAIN = 8.0;
    /** Le cap se lisse a 0,6 x v(m/s) par seconde (hvehicle.gc:639-646). */
    static final double YAW_SMOOTH = 0.6;
    /** Le vehicule de devant : collision predite a 2 s, cone de 45 degres, 48 blocs de portee. */
    static final double LEADER_HORIZON_TICKS = 40.0;
    static final double LEADER_CONE_COS = Math.cos(Math.toRadians(45.0));
    static final double LEADER_RANGE = 48.0;
    /**
     * Le couloir devant nous : un vehicule compte s'il passe a moins de tant de blocs de
     * notre axe (le jeu balaie une sphere de 1,5 rayon de collision le long de la vitesse,
     * hvehicle-util.gc:92-101). Un simple rayon de 10 blocs attrapait les voitures de la
     * voie d'en face, a 8 blocs de cote : on freinait jusqu'a zero devant chacune.
     */
    static final double LANE_HALF_WIDTH = 4.5;
    /** Sous 10 degres, un virage n'en est pas un (vehicle-control.gc:119-128). */
    static final double STRAIGHT_COS = Math.cos(Math.toRadians(10.0));
    static final double TURN_COS45 = Math.cos(Math.toRadians(45.0));
    /** Le point vise est au moins a tant de blocs devant, a l'arret. */
    static final double LOOKAHEAD_MIN = 4.0;
    private static final double TICK = 0.05;

    private static final String TAG_BRANCH = "Branche";
    private static final String TAG_NEXT = "Suivante";
    private static final String TAG_OFFSET = "Ecart";

    /** La branche courante, et la suivante (-1 : pas encore choisie, ou une sortie). */
    private int branch;
    private int next = -1;
    /** L'ecart de vitesse de ce vehicule, en m/s, tire a la naissance (target-speed-offset). */
    private final double speedOffset;

    public TrafficDriver(int branch, double speedOffset) {
        this.branch = branch;
        this.speedOffset = speedOffset;
    }

    public int branch() {
        return this.branch;
    }

    public int next() {
        return this.next;
    }

    public double speedOffset() {
        return this.speedOffset;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_BRANCH, this.branch);
        tag.putInt(TAG_NEXT, this.next);
        tag.putDouble(TAG_OFFSET, this.speedOffset);
        return tag;
    }

    public static TrafficDriver load(CompoundTag tag) {
        TrafficDriver driver = new TrafficDriver(tag.getInt(TAG_BRANCH), tag.getDouble(TAG_OFFSET));
        driver.next = tag.contains(TAG_NEXT) ? tag.getInt(TAG_NEXT) : -1;
        return driver;
    }

    /**
     * Un tick de conduite.
     *
     * @param users le nombre de vehicules du trafic sur chaque branche, pour le choix au carrefour
     * @return faux si le vehicule doit disparaitre : bout d'une sortie, cul-de-sac, voie inconnue
     */
    public boolean drive(JakVehicleEntity car, HavenTrafficData.Data data, BlockPos origin, int[] users,
                         RandomSource random) {
        if (this.branch < 0 || this.branch >= data.branches().size()) {
            return false;
        }
        HavenTrafficData.Branch current = data.branch(this.branch);
        Vec3 start = data.start(current, origin);
        Vec3 end = data.end(current, origin);
        double length = horizontal(start, end);
        if (length < 1.0E-3) {
            return false;
        }
        Vec3 dir = new Vec3((end.x - start.x) / length, 0.0, (end.z - start.z) / length);
        Vec3 pos = car.position();
        double s = (pos.x - start.x) * dir.x + (pos.z - start.z) * dir.z;

        if (s >= length) {
            // le bout de la branche : la suivante, choisie d'avance, ou la fin du chemin
            if (current.exit()) {
                return false;
            }
            if (this.next < 0) {
                this.next = choose(data, current.dest(), users, random);
            }
            if (this.next < 0) {
                return false;
            }
            this.branch = this.next;
            this.next = -1;
            current = data.branch(this.branch);
            start = data.start(current, origin);
            end = data.end(current, origin);
            length = horizontal(start, end);
            if (length < 1.0E-3) {
                return false;
            }
            dir = new Vec3((end.x - start.x) / length, 0.0, (end.z - start.z) / length);
            s = (pos.x - start.x) * dir.x + (pos.z - start.z) * dir.z;
        }
        if (this.next < 0 && !current.exit()) {
            this.next = choose(data, current.dest(), users, random);
        }

        Vec3 v = car.getDeltaMovement();
        double speed = Math.hypot(v.x, v.z);
        double wanted = (current.speedMs() + this.speedOffset) / 20.0;

        // le virage au bout de la branche : freiner a temps
        Vec3 nextDir = null;
        Vec3 nextStart = null;
        if (this.next >= 0) {
            HavenTrafficData.Branch following = data.branch(this.next);
            nextStart = data.start(following, origin);
            Vec3 nextEnd = data.end(following, origin);
            double nextLength = horizontal(nextStart, nextEnd);
            if (nextLength > 1.0E-3) {
                nextDir = new Vec3((nextEnd.x - nextStart.x) / nextLength, 0.0, (nextEnd.z - nextStart.z) / nextLength);
                double cos = dir.x * nextDir.x + dir.z * nextDir.z;
                if (cos < STRAIGHT_COS) {
                    double radius = Math.max(4.0, data.node(current.dest()).radius());
                    double turn = Math.sqrt(radius * TURN_ACCEL_MS2) / 20.0
                            * (1.0 + Math.max(0.0, (cos - TURN_COS45) / (1.0 - TURN_COS45)));
                    double accel = TURN_ACCEL_MS2 / 400.0;
                    if (speed > turn && (speed * speed - turn * turn) / (2.0 * accel) >= length - s) {
                        wanted = Math.min(wanted, turn);
                    }
                }
            }
        }
        wanted = Math.min(wanted, leaderLimit(car, dir, v));

        // le point vise, 0,4 s devant sur la voie, qui deborde sur la branche suivante
        double ahead = s + Math.max(LOOKAHEAD_MIN, speed * LOOKAHEAD_S * 20.0);
        Vec3 target;
        if (ahead <= length || nextDir == null) {
            double along = Math.min(ahead, length);
            target = new Vec3(start.x + dir.x * along, 0.0, start.z + dir.z * along);
        } else {
            double along = ahead - length;
            target = new Vec3(nextStart.x + nextDir.x * along, 0.0, nextStart.z + nextDir.z * along);
        }
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        double distance = Math.hypot(dx, dz);
        double desiredX = distance > 1.0E-6 ? dx / distance * wanted : 0.0;
        double desiredZ = distance > 1.0E-6 ? dz / distance * wanted : 0.0;
        double vx = v.x + (desiredX - v.x) * SPEED_GAIN * TICK;
        double vz = v.z + (desiredZ - v.z) * SPEED_GAIN * TICK;

        // l'altitude de la voie a l'aplomb du vehicule (la carte de hauteur), en m et m/s comme dans le jeu
        double laneY = HavenTraffic.laneY(origin, pos.x, pos.z);
        double vyMs = v.y * 20.0;
        vyMs += (VERTICAL_GAIN * (laneY - pos.y) - vyMs) * TICK;
        double vy = vyMs / 20.0;

        Vec3 asked = new Vec3(vx, vy, vz);
        Vec3 allowed = VehiclePhysics.move(car, asked);
        // ce qui n'a pas pu bouger est perdu : le vehicule s'arrete contre l'obstacle
        car.setDeltaMovement(blocked(asked.x, allowed.x) ? 0.0 : vx, blocked(asked.y, allowed.y) ? 0.0 : vy,
                blocked(asked.z, allowed.z) ? 0.0 : vz);

        Vec3 now = car.getDeltaMovement();
        double moving = Math.hypot(now.x, now.z);
        if (moving > 5.0E-4) {
            // avant = (-sin lacet, cos lacet) : le lacet d'une direction (dx, dz) est atan2(-dx, dz)
            float yaw = (float) Math.toDegrees(Math.atan2(-now.x, now.z));
            float turned = Mth.wrapDegrees(yaw - car.getYRot());
            car.setYRot(car.getYRot() + turned * (float) Math.min(1.0, YAW_SMOOTH * moving * 20.0 * TICK));
        }
        car.syncParts();
        return true;
    }

    /** La branche suivante au noeud {@code node} : au hasard parmi celles qui ont de la place, sinon parmi toutes ; -1 sans branche. */
    static int choose(HavenTrafficData.Data data, int node, int[] users, RandomSource random) {
        int[] out = data.node(node).branches();
        if (out.length == 0) {
            return -1;
        }
        List<Integer> free = new ArrayList<>();
        for (int b : out) {
            if (users == null || b >= users.length || users[b] < data.branch(b).maxUsers()) {
                free.add(b);
            }
        }
        return free.isEmpty() ? out[random.nextInt(out.length)] : free.get(random.nextInt(free.size()));
    }

    /**
     * La vitesse a ne pas depasser a cause du vehicule de devant : sa propre vitesse le
     * long de notre axe, si une collision est predite dans les 2 s. Les voitures des
     * joueurs comptent (leur deplacement recu tient lieu de vitesse).
     */
    public static double leaderLimit(JakVehicleEntity car, Vec3 dir, Vec3 v) {
        double limit = Double.MAX_VALUE;
        AABB around = car.getBoundingBox().inflate(LEADER_RANGE);
        for (JakVehicleEntity other : car.level().getEntitiesOfClass(JakVehicleEntity.class, around,
                o -> o != car && !o.isRemoved())) {
            double rx = other.getX() - car.getX();
            double rz = other.getZ() - car.getZ();
            double d = Math.hypot(rx, rz);
            if (d < 1.0E-3 || (rx * dir.x + rz * dir.z) / d < LEADER_CONE_COS) {
                continue;
            }
            Vec3 ov = other.serverMotion();
            double vrx = ov.x - v.x;
            double vrz = ov.z - v.z;
            double vv = vrx * vrx + vrz * vrz;
            double t = vv < 1.0E-9 ? 0.0 : Mth.clamp(-(rx * vrx + rz * vrz) / vv, 0.0, LEADER_HORIZON_TICKS);
            double cx = rx + vrx * t;
            double cz = rz + vrz * t;
            double lateral = Math.abs(cx * dir.z - cz * dir.x);
            double ahead = cx * dir.x + cz * dir.z;
            if (lateral < LANE_HALF_WIDTH && ahead > -2.0 && ahead < 3.0 * LANE_HALF_WIDTH) {
                limit = Math.min(limit, Math.max(0.0, ov.x * dir.x + ov.z * dir.z));
            }
        }
        return limit;
    }

    private static boolean blocked(double asked, double allowed) {
        return Math.abs(asked - allowed) > 1.0E-7;
    }

    static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(b.x - a.x, b.z - a.z);
    }
}
