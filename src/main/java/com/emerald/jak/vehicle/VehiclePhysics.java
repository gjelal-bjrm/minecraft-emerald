package com.emerald.jak.vehicle;

import com.emerald.haven.Haven;
import com.emerald.haven.traffic.HavenLaneMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Le vol d'une voiture dans le monde : sondes, voie haute, collisions.
 *
 * PARTAGEE ENTRE LES DEUX COTES. Le client du conducteur l'appelle a chaque
 * tick et envoie la position au serveur, comme le bateau ; le serveur l'appelle
 * pour les voitures sans conducteur et pour l'autotest. Les lois elles-memes
 * vivent dans VehicleDynamics ; ici on les nourrit avec le monde.
 *
 * LES COLLISIONS PASSENT PAR TOUTES LES BOITES. La voiture et ses trois parties
 * se deplacent ensemble, axe par axe : sur un seul axe, garder le plus petit
 * deplacement permis par chaque boite ne laisse jamais l'une d'elles entrer
 * dans un bloc. Entity.move fait ensuite le mouvement lui-meme -- chute,
 * blocs traverses, evenements --, deja degage. A deux blocs par tick, le tick
 * est decoupe en sous-pas pour ne pas couper les coins (voir MAX_CORNER_CUT).
 */
public final class VehiclePhysics {

    /**
     * La voie haute de la ville, en cellules du volume au-dessus de l'origine de
     * pose : la carte de trafic de Jak 3.
     *
     * Le jeu lit son altitude dans *traffic-height-map* (hvehicle.gc:619) : 17,5 m
     * sur presque tout le port. L'origine du volume est a -57,5 m, donc 17,5 m
     * tombe en cellule 75,0 : Y 80,0 du monde, 9 blocs au-dessus de la rue
     * (cellule 66). C'est la que roule le trafic (hvehicle.gc:623-627), et c'est a
     * cette altitude que le jeu compare la voiture pour finir sa montee
     * (hvehicle.gc:579) et pour savoir s'il faut monter (hvehicle-util.gc:294).
     *
     * C'EST LA BASE, PAS TOUTE LA CARTE : le jeu la releve par endroits, de 10 blocs
     * au-dessus du pont entre les deux tours, dont le tablier monte jusqu'ici. La
     * voie du joueur suit ces bosses, et elles seules (HavenLaneMap.playerRise) :
     * plate, elle butait de cote contre le pont.
     */
    public static final double HAVEN_TRAFFIC_CELL = 75.0;
    /**
     * Le plancher invisible, au-dessus de la carte : 6144 = 1,5 m
     * (hvehicle-physics.gc:120). La poussee ne s'exerce que dessous, et la voiture
     * y pend de VehicleDynamics.highHang : 1,5 bloc pour car-a pilotee, ses
     * propulseurs pile sur la carte, cellule 75,0 (plancher en 76,5, Y 81,5).
     */
    public static final double FLOOR_ABOVE_TRAFFIC = 1.5;
    /**
     * Hors de la ville, la carte de trafic passe a cette hauteur au-dessus du sol :
     * celle du port au-dessus de sa rue, 75,0 - 66 = 9 blocs. Le plancher est donc
     * 10,5 blocs au-dessus du sol, et les propulseurs se tiennent a 9.
     */
    public static final double TRAFFIC_ABOVE_GROUND = HAVEN_TRAFFIC_CELL - 66.0;
    /** Portee de la sonde qui cherche le sol sous la voie haute, hors de la ville. */
    public static final double HIGH_GROUND_SEARCH = 64.0;

    /**
     * Coupe de coin toleree par sous-pas, en blocs.
     *
     * Les collisions se font axe par axe (Entity.collideWithShapes) : la boite
     * avance sur la verticale, puis sur un axe horizontal, puis sur l'autre, en L.
     * Chaque trajet d'axe est balaye, donc aucun mur, meme d'un bloc, n'est
     * saute ; mais le L contourne un coin que la diagonale aurait heurte. Le coin
     * d'une boite passe alors dans le coin d'un bloc, d'au plus a b / (a + b) pour
     * des deplacements a et b sur deux axes : 0,35 bloc a un bloc par tick en
     * diagonale, 0,71 a deux blocs par tick (calcul). Le tick est decoupe jusqu'a
     * rester sous cette valeur.
     *
     * MESURE (autotest vehicules, bout d'un mur d'un bloc a 45 degres et 40 m/s,
     * 33 trajectoires) : sous-pas coupes, 2 trajectoires entrent de 0,383 et
     * 0,258 bloc dans le coin, comme le banc hors jeu ; avec les sous-pas, 0,000.
     * Les quatre boites de la voiture se suivent sur la diagonale et rattrapent
     * la plupart des coins : la coupe reste rare, mais elle existe.
     */
    public static final double MAX_CORNER_CUT = 0.2;
    /** Sous-pas au plus par tick : a deux blocs par tick en diagonale, il en faut 4. */
    public static final int MAX_SUBSTEPS = 8;
    /** Vrai en jeu. L'autotest les coupe le temps de mesurer ce qu'ils evitent. */
    static boolean substeps = true;

    private VehiclePhysics() {
    }

    /** Un tick de vol. A appeler par l'instance qui simule la voiture. */
    public static void tick(JakVehicleEntity car, VehicleDynamics.Input input) {
        VehicleSpec spec = car.spec();
        double front = probe(car, spec.thrusterFrontZ);
        double rear = probe(car, spec.thrusterRearZ);
        boolean grounded = VehicleDynamics.grounded(front, rear);

        int mode = car.mode();
        double probeY = car.getY() + spec.thrusterY;
        double floorY = VehicleDynamics.isHigh(mode) ? floorY(car) : Double.NaN;
        if (VehicleDynamics.isHigh(mode) && Double.isNaN(floorY)) {
            // pas de sol sous la voie haute hors de la ville : on vole comme au ras du sol
            mode = VehicleDynamics.MODE_SOL;
        }
        // sous son plancher, la voiture « touche » la voie (on-flight-level, hvehicle-physics.gc:121-123)
        boolean onFlightLevel = VehicleDynamics.isHigh(mode) && probeY <= floorY;
        boolean driver = car.hasDriverWeight();

        Vec3 v = car.getDeltaMovement();
        double yaw = Math.toRadians(car.getYRot());
        // avant (-sin, cos), gauche (cos, sin) : lacet 0 regarde +Z, sa gauche est +X
        double[] motion = {
                -v.x * Math.sin(yaw) + v.z * Math.cos(yaw),
                v.x * Math.cos(yaw) + v.z * Math.sin(yaw)};
        double vy = VehicleDynamics.verticalVelocity(spec, v.y, front, rear, mode, probeY, floorY, driver);
        double yawDelta = VehicleDynamics.horizontal(spec, car.controls(), input, motion, grounded || onFlightLevel);

        if (yawDelta != 0.0) {
            float turned = car.getYRot() + (float) yawDelta;
            if (canTurn(car, turned)) {
                car.setYRot(turned);
                yaw = Math.toRadians(turned);
            }
        }

        Vec3 wanted = new Vec3(
                -Math.sin(yaw) * motion[0] + Math.cos(yaw) * motion[1],
                vy,
                Math.cos(yaw) * motion[0] + Math.sin(yaw) * motion[1]);
        Vec3 allowed = move(car, wanted);

        car.setDeltaMovement(
                bounce(wanted.x, allowed.x),
                bounce(wanted.y, allowed.y),
                bounce(wanted.z, allowed.z));
        car.syncParts();
    }

    /**
     * Deplace la voiture de {@code wanted}, en sous-pas, et rend le deplacement fait.
     *
     * Chaque sous-pas passe par collide (toutes les boites), puis par Entity.move.
     * Un axe bloque le reste jusqu'a la fin du tick : la voiture s'arrete contre
     * l'obstacle au lieu d'y revenir a chaque sous-pas, et le rebond se decide sur
     * le tick entier, comme avant.
     *
     * Le serveur, lui, valide la position d'un conducteur par un seul Entity.move
     * de la boite centrale (ServerGamePacketListenerImpl.handleMoveVehicle) : si
     * cette boite frole un coin en diagonale, les deux trajets peuvent differer, et
     * le serveur renvoie la voiture a sa position precedente pour ce tick.
     */
    public static Vec3 move(JakVehicleEntity car, Vec3 wanted) {
        int steps = substeps ? substepCount(wanted) : 1;
        Vec3 step = wanted.scale(1.0 / steps);
        double startX = car.getX();
        double startY = car.getY();
        double startZ = car.getZ();
        boolean freeX = true;
        boolean freeY = true;
        boolean freeZ = true;
        for (int i = 0; i < steps; i++) {
            Vec3 part = new Vec3(freeX ? step.x : 0.0, freeY ? step.y : 0.0, freeZ ? step.z : 0.0);
            if (part.lengthSqr() == 0.0) {
                break;
            }
            Vec3 allowed = collide(car, part);
            car.move(MoverType.SELF, allowed);
            freeX &= Math.abs(allowed.x - part.x) <= 1.0E-7;
            freeY &= Math.abs(allowed.y - part.y) <= 1.0E-7;
            freeZ &= Math.abs(allowed.z - part.z) <= 1.0E-7;
        }
        return new Vec3(car.getX() - startX, car.getY() - startY, car.getZ() - startZ);
    }

    /** Le nombre de sous-pas pour que la coupe de coin reste sous MAX_CORNER_CUT. */
    static int substepCount(Vec3 wanted) {
        double x = Math.abs(wanted.x);
        double y = Math.abs(wanted.y);
        double z = Math.abs(wanted.z);
        double cut = Math.max(cornerCut(x, z), Math.max(cornerCut(y, x), cornerCut(y, z)));
        return Math.max(1, Math.min(MAX_SUBSTEPS, (int) Math.ceil(cut / MAX_CORNER_CUT)));
    }

    /** Ce qu'un trajet en L de a puis b coupe au plus dans un coin : a b / (a + b). */
    private static double cornerCut(double a, double b) {
        return a + b > 0.0 ? a * b / (a + b) : 0.0;
    }

    /** Rebond de 0,4 sur l'axe bloque ; un simple arret pour les chocs mous. */
    private static double bounce(double wanted, double allowed) {
        if (Math.abs(wanted - allowed) <= 1.0E-7) {
            return wanted;
        }
        return Math.abs(wanted) > 0.2 ? -wanted * VehicleSpec.BOUNCE : 0.0;
    }

    /**
     * La distance d'un propulseur au sol, ou NaN sans sol a portee.
     *
     * Le jeu sonde le decor ET l'eau (collide-spec water, hvehicle-physics.gc:40) :
     * les voitures planent au-dessus du port. Un propulseur deja dans un bloc
     * touche a distance nulle, et le ressort pousse a fond.
     */
    public static double probe(JakVehicleEntity car, double localZ) {
        VehicleSpec spec = car.spec();
        double yaw = Math.toRadians(car.getYRot());
        double x = car.getX() - Math.sin(yaw) * localZ;
        double z = car.getZ() + Math.cos(yaw) * localZ;
        double y = car.getY() + spec.thrusterY;
        return down(car, x, y, z, spec.probeDistance);
    }

    private static double down(Entity car, double x, double y, double z, double reach) {
        BlockHitResult hit = car.level().clip(new ClipContext(new Vec3(x, y, z), new Vec3(x, y - reach, z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, car));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN : y - hit.getLocation().y;
    }

    /**
     * L'altitude de la carte de trafic sous la voiture, ou NaN.
     *
     * Dans la ville, celle de Jak 3 : origine de pose + 75,0, plus la bosse de la
     * carte a l'aplomb de la voiture. Ailleurs, le sol sous la voiture + 9, suivi
     * en continu ; sans sol trouve, la derniere altitude connue.
     */
    public static double trafficY(JakVehicleEntity car) {
        Level level = car.level();
        if (Haven.is(level)) {
            return havenTrafficY(car.getX(), car.getZ());
        }
        VehicleSpec spec = car.spec();
        double y = car.getY() + spec.thrusterY;
        double ground = down(car, car.getX(), y, car.getZ(), HIGH_GROUND_SEARCH);
        if (!Double.isNaN(ground)) {
            car.setLastFloor(y - ground + TRAFFIC_ABOVE_GROUND);
        }
        return car.lastFloor();
    }

    /** La carte de trafic de la ville a l'aplomb de (x, z), en Y du monde : la base, plus la bosse de Jak 3. */
    public static double havenTrafficY(double x, double z) {
        return Haven.ORIGIN.getY() + HAVEN_TRAFFIC_CELL
                + HavenLaneMap.playerRise(x - Haven.ORIGIN.getX(), z - Haven.ORIGIN.getZ());
    }

    /** Le plancher de la voie haute pour les propulseurs, 1,5 bloc au-dessus de la carte, ou NaN. */
    public static double floorY(JakVehicleEntity car) {
        return trafficY(car) + FLOOR_ABOVE_TRAFFIC;
    }

    /** Le lacet peut changer si aucune partie n'entre dans un obstacle ou elle n'etait pas deja. */
    private static boolean canTurn(JakVehicleEntity car, float yaw) {
        Level level = car.level();
        for (int i = 0; i < car.spec().partCount(); i++) {
            AABB now = car.partBox(i, car.getX(), car.getY(), car.getZ(), car.getYRot());
            AABB then = car.partBox(i, car.getX(), car.getY(), car.getZ(), yaw);
            if (!level.noCollision(car, then) && level.noCollision(car, now)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Le deplacement permis a toutes les boites de la voiture.
     *
     * Dans l'ordre du jeu : la verticale, puis le plus grand des deux axes
     * horizontaux, puis l'autre (Entity.collideWithShapes).
     */
    public static Vec3 collide(JakVehicleEntity car, Vec3 wanted) {
        if (wanted.lengthSqr() == 0.0) {
            return wanted;
        }
        Level level = car.level();
        List<AABB> boxes = car.collisionBoxes();
        AABB sweep = boxes.get(0);
        for (AABB box : boxes) {
            sweep = sweep.minmax(box);
        }
        List<VoxelShape> entities = level.getEntityCollisions(car, sweep.expandTowards(wanted));

        double dx = 0.0;
        double dy = axis(car, boxes, entities, 0.0, 0.0, 0.0, 1, wanted.y);
        double dz = 0.0;
        if (Math.abs(wanted.x) >= Math.abs(wanted.z)) {
            dx = axis(car, boxes, entities, dx, dy, dz, 0, wanted.x);
            dz = axis(car, boxes, entities, dx, dy, dz, 2, wanted.z);
        } else {
            dz = axis(car, boxes, entities, dx, dy, dz, 2, wanted.z);
            dx = axis(car, boxes, entities, dx, dy, dz, 0, wanted.x);
        }
        return new Vec3(dx, dy, dz);
    }

    private static double axis(JakVehicleEntity car, List<AABB> boxes, List<VoxelShape> entities,
                               double dx, double dy, double dz, int axis, double amount) {
        if (amount == 0.0) {
            return 0.0;
        }
        Vec3 step = axis == 0 ? new Vec3(amount, 0, 0) : axis == 1 ? new Vec3(0, amount, 0) : new Vec3(0, 0, amount);
        double allowed = amount;
        for (AABB box : boxes) {
            Vec3 result = Entity.collideBoundingBox(car, step, box.move(dx, dy, dz), car.level(), entities);
            double got = axis == 0 ? result.x : axis == 1 ? result.y : result.z;
            if (Math.abs(got) < Math.abs(allowed)) {
                allowed = got;
            }
        }
        return allowed;
    }
}
