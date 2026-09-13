package com.emerald.jak.vehicle;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.init.Jak3Registry;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Le banc d'essai des voitures, INERTE sans EMERALDWEAPONS_AUTOTEST=vehicules.
 *
 * Il tourne dans la dimension de la ville, cote serveur, avec la vraie entite
 * et VehiclePhysics : la voiture tique comme en jeu, l'autotest ne fait que lui
 * donner des commandes (a la place d'un conducteur) et MESURER le monde.
 *
 * Trois terrains :
 *  - les places des appartements, pour la pose, les doublons et les remises en place ;
 *  - la rue du bar, pour la hauteur de rase-sol, la voie haute et les sieges ;
 *  - la mer du generateur a l'est de la ville, plate et sans fin, pour la
 *    vitesse de 40 m/s, le frein, la direction, un virage serre, et des
 *    obstacles d'un bloc poses puis retires : mur a pleine vitesse, virage
 *    contre un mur, coin en diagonale, plafond pendant la montee.
 *
 * LES OBSTACLES PASSENT DEUX FOIS : sous-pas coupes, pour mesurer seulement ce
 * qu'ils evitent, puis comme en jeu, controles compris. Pendant ces essais,
 * chaque tick est surveille : aucune boite dans un bloc, et la coupe de coin --
 * le trajet droit entre deux ticks, echantillonne -- sous CUT_TOLERANCE.
 *
 * Rapport dans vehicules_autotest.txt, dans le dossier du serveur, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class VehicleAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final String VARIABLE = "EMERALDWEAPONS_AUTOTEST";
    private static final boolean ENABLED = "vehicules".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(VARIABLE), "").trim());

    private static final TicketType<ChunkPos> TICKET = TicketType.create("arcencium_autotest_voitures",
            Comparator.comparingLong(ChunkPos::toLong));
    /** Distance 3 : le troncon et ses voisins tiquent avec leurs entites. */
    private static final int TICKET_DISTANCE = 3;
    private static final int TIMEOUT_TICKS = 20 * 60 * 20;

    private interface Step {
        /** @return vrai quand l'etape est finie */
        boolean tick(MinecraftServer server, ServerLevel level, int t);
    }

    private static final StringBuilder OUT = new StringBuilder();
    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<ChunkPos> HELD = new ArrayList<>();
    private static final List<Entity> SPAWNED = new ArrayList<>();
    private static boolean started;
    private static boolean finished;
    private static int stepIndex;
    private static int stepTick;
    private static int total;
    private static int passed;
    private static int failed;
    private static boolean nan;
    private static String nanWhere = "";

    // etat partage des etapes
    @Nullable
    private static JakVehicleEntity car;
    private static final List<Double> samples = new ArrayList<>();
    private static double street0X;
    private static double street0Z;
    private static double streetGround;
    private static float streetYaw;
    private static boolean streetHigh;
    private static double sea0X;
    private static double sea0Z;
    private static double wallX;
    private static final List<ArmorStand> stands = new ArrayList<>();
    private static final Map<String, UUID> apartmentCars = new HashMap<>();
    private static double maxSpeed;
    private static int reach95;
    private static int marker;
    private static double yawStart;
    private static double peak;
    private static boolean flag;
    private static double seaHoverY;
    private static double lastSpeed;
    private static double impact;
    private static int contacts;
    private static int runsTouched;
    private static int ceilingY;
    private static double sumYaw;
    private static double sumForward;

    /**
     * Coupe de coin toleree, en blocs : le trajet droit entre deux ticks peut
     * entrer d'autant dans un bloc. Au-dela, un coin est traverse.
     */
    private static final double CUT_TOLERANCE = 0.25;
    /** Echantillons du trajet droit par tick : a deux blocs par tick, un tous les 6 centiemes de bloc. */
    private static final int CUT_SAMPLES = 32;
    private static boolean monitor;
    private static boolean monitorPrev;
    private static double prevX;
    private static double prevY;
    private static double prevZ;
    private static float prevYaw;
    private static double maxCut;
    private static String cutWhere = "";
    /** Ticks dont le trajet droit entre dans un bloc de plus d'un centieme. */
    private static int cutTicks;
    /** Coupe la plus profonde et premier tick ralenti de la trajectoire en cours (coin). */
    private static double runCut;
    private static int runContact;
    private static int insideTicks;
    private static String insideWhere = "";
    /** Les blocs poses par l'essai, rendus au generateur apres chaque obstacle et a la fin. */
    private static final List<BlockPos> PLACED = new ArrayList<>();

    private VehicleAutotest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = Haven.level(server);
        if (++total > TIMEOUT_TICKS) {
            check("autotest fini en moins de 20 minutes", false, "delai depasse a l'etape " + stepIndex);
            end(server);
            return;
        }
        if (level == null) {
            if (total == 200) {
                check("dimension de la ville chargee", false, "Haven.level(server) est nul");
                end(server);
            }
            return;
        }
        // Un niveau sans joueur cesse de faire tiquer ses entites apres 300 ticks
        // (ServerLevel.tick, emptyTime) : sans cela, les voitures de l'essai se
        // figent au bout de quinze secondes -- mesure au premier passage.
        level.resetEmptyTime();
        try {
            if (!started) {
                HavenState state = HavenState.get(server);
                if (!state.built() || HavenSite.busy()) {
                    return;
                }
                started = true;
                line("autotest des voitures de Haven, " + LocalDateTime.now().withNano(0));
                line("ville : phase " + state.phase() + ", origine " + state.origin().toShortString()
                        + ", grille " + state.width() + " x " + state.height() + " x " + state.depth());
                plan(server, level, state);
            }
            if (stepIndex >= STEPS.size()) {
                end(server);
                return;
            }
            if (car != null && !car.isRemoved()) {
                watchNaN(car, "etape " + stepIndex);
                watchCollisions(car, "etape " + stepIndex + ", tick " + stepTick);
            }
            if (STEPS.get(stepIndex).tick(server, level, stepTick++)) {
                stepIndex++;
                stepTick = 0;
            }
            // apres l'etape : une voiture qu'elle vient de poser ailleurs repart de la
            rememberPose();
        } catch (RuntimeException e) {
            LOGGER.error("autotest vehicules : exception", e);
            check("aucune exception", false, e + " a l'etape " + stepIndex);
            end(server);
        }
    }

    // ------------------------------------------------------------- le plan

    private static void plan(MinecraftServer server, ServerLevel level, HavenState state) {
        BlockPos o = state.origin();
        BlockPos street = o.offset(Haven.BAR_FRONT_CELL);
        sea0X = o.getX() + state.width() + 80.5;
        sea0Z = o.getZ() + state.depth() / 2.0 + 0.5;
        wallX = Math.floor(sea0X) + 120.0;

        hold(level, new ChunkPos(street));
        for (HavenCars.Place place : HavenCars.places(server)) {
            hold(level, new ChunkPos(BlockPos.containing(HavenCars.placeOrigin(place, o))));
        }
        // A 40 m/s, dix secondes de gaz font 400 blocs, et un virage serre a pleine
        // vitesse a 42 blocs de rayon : une zone, et plus un couloir. Un ticket de
        // distance 3 fait tiquer les entites de son troncon et des voisins ; tous
        // les 32 blocs, les zones se touchent.
        for (double x = sea0X - 48; x <= sea0X + 600; x += 32) {
            for (double z = sea0Z - 96; z <= sea0Z + 96; z += 32) {
                hold(level, new ChunkPos(BlockPos.containing(x, 64, z)));
            }
        }
        line("tickets poses sur " + HELD.size() + " troncons (rue du bar, places, zone en mer de 648 x 192 blocs)");

        // 1. attendre que les troncons soient la, entites comprises
        STEPS.add((s, l, t) -> {
            boolean ready = true;
            for (ChunkPos pos : HELD) {
                ready &= l.areEntitiesLoaded(pos.toLong()) && l.isPositionEntityTicking(pos.getWorldPosition());
            }
            if (ready) {
                line("troncons prets apres " + t + " ticks");
                return true;
            }
            if (t > 3 * 60 * 20) {
                check("troncons de l'essai charges en moins de 3 minutes", false, "delai depasse");
                return true;
            }
            return false;
        });

        planApartments(server, state);
        planStreet(state, street);
        planSea(state);
    }

    // ------------------------------------------------------------- appartements

    private static void planApartments(MinecraftServer server, HavenState state) {
        BlockPos o = state.origin();
        List<HavenCars.Place> places = HavenCars.places(server);
        STEPS.add((s, l, t) -> {
            check("haven_rooms.json : trois places de voiture lues", places.size() == 3,
                    places.size() + " places : " + places);
            Map<String, Integer> before = countByRoom(l);
            line("voitures d'appartement deja presentes au lancement : " + before
                    + (state.phase() == HavenState.Phase.ACCUEIL ? "" : " (phase " + state.phase() + ")"));
            HavenCars.update(s);
            return true;
        });
        // quelques secondes : controles automatiques, montee en rase-sol
        STEPS.add((s, l, t) -> t >= 100);
        STEPS.add((s, l, t) -> {
            apartmentCars.clear();
            Map<String, Integer> counts = countByRoom(l);
            HavenCars.Registry registry = HavenCars.Registry.get(s);
            for (HavenCars.Place place : places) {
                List<JakVehicleEntity> cars = carsOf(l, place.room());
                boolean one = cars.size() == 1;
                String detail = cars.size() + " voiture(s)";
                boolean ok = one;
                if (one) {
                    JakVehicleEntity c = cars.get(0);
                    apartmentCars.put(place.room(), c.getUUID());
                    Vec3 center = HavenCars.modelCenter(c);
                    double wantX = o.getX() + place.floor().getX() + 0.5;
                    double wantZ = o.getZ() + place.floor().getZ() + 0.5;
                    double gap = Math.hypot(center.x - wantX, center.z - wantZ);
                    double yawGap = Math.abs(Mth.wrapDegrees(c.getYRot() - place.yaw()));
                    ok = gap < 0.6 && yawGap < 1.0 && place.model().equals(c.model())
                            && c.getUUID().equals(registry.car(place.room()));
                    double front = VehiclePhysics.probe(c, c.spec().thrusterFrontZ);
                    detail = String.format(Locale.ROOT,
                            "%s, centre du modele a %.2f bloc de la place (%.1f ; %.1f), lacet %.1f (voulu %.1f),"
                                    + " registre %s ; origine %s, propulseur avant a %.2f du sol",
                            c.model(), gap, wantX, wantZ, c.getYRot(), place.yaw(),
                            c.getUUID().equals(registry.car(place.room())) ? "d'accord" : "DIFFERENT",
                            fmt(c.position()), front);
                }
                check("une voiture sur la place de " + place.room() + " (" + place.model() + ")", ok, detail);
            }
            line("voitures marquees chargees par appartement : " + counts);
            // second passage : memoire oubliee comme apres un redemarrage, et deux controles
            HavenCars.forgetMemory();
            HavenCars.update(s);
            HavenCars.update(s);
            return true;
        });
        STEPS.add((s, l, t) -> t >= 40);
        STEPS.add((s, l, t) -> {
            for (HavenCars.Place place : places) {
                List<JakVehicleEntity> cars = carsOf(l, place.room());
                check("second passage : toujours une seule voiture pour " + place.room(),
                        cars.size() == 1 && cars.get(0).getUUID().equals(apartmentCars.get(place.room())),
                        cars.size() + " voiture(s), meme identifiant : "
                                + (cars.size() == 1 && cars.get(0).getUUID().equals(apartmentCars.get(place.room()))));
            }
            // un doublon marque apparait (vieille sauvegarde) : il doit etre retire
            HavenCars.spawn(l, places.get(1), o);
            HavenCars.update(s);
            return true;
        });
        STEPS.add((s, l, t) -> t >= 2);
        STEPS.add((s, l, t) -> {
            List<JakVehicleEntity> cars = carsOf(l, places.get(1).room());
            check("doublon marque de " + places.get(1).room() + " retire", cars.size() == 1
                            && cars.get(0).getUUID().equals(apartmentCars.get(places.get(1).room())),
                    cars.size() + " voiture(s) apres le controle");
            // La voiture 3 sort de la ville PAR LE HAUT : meme troncon, toujours
            // charge. Envoyee a x = -40, dans un troncon non charge, elle y devenait
            // invisible au niveau et le controle en posait une autre (premier passage).
            JakVehicleEntity third = onlyCar(l, places.get(2).room());
            if (third != null) {
                third.moveTo(third.getX(), o.getY() + state.height() + 5.0, third.getZ(), third.getYRot(), 0.0F);
            }
            // la voiture 1 disparait (une repose l'aurait retiree) : elle doit etre remplacee
            JakVehicleEntity first = onlyCar(l, places.get(0).room());
            if (first != null) {
                first.discard();
            }
            HavenCars.update(s);
            return true;
        });
        STEPS.add((s, l, t) -> t >= 2);
        STEPS.add((s, l, t) -> {
            JakVehicleEntity third = onlyCar(l, places.get(2).room());
            double gap = third == null ? Double.NaN : Math.hypot(HavenCars.modelCenter(third).x
                    - (o.getX() + places.get(2).floor().getX() + 0.5), HavenCars.modelCenter(third).z
                    - (o.getZ() + places.get(2).floor().getZ() + 0.5));
            check("voiture sortie de la ville ramenee sur sa place", third != null && gap < 0.6
                            && third.getUUID().equals(apartmentCars.get(places.get(2).room())),
                    third == null ? "introuvable" : String.format(Locale.ROOT, "a %.2f bloc de sa place", gap));
            List<JakVehicleEntity> firsts = carsOf(l, places.get(0).room());
            check("voiture disparue remplacee une fois", firsts.size() == 1
                            && !firsts.get(0).getUUID().equals(apartmentCars.get(places.get(0).room())),
                    firsts.size() + " voiture(s), identifiant nouveau : " + (firsts.size() == 1
                            && !firsts.get(0).getUUID().equals(apartmentCars.get(places.get(0).room()))));
            // le depart : toutes les voitures de la ville retirees
            int removed = HavenCars.removeAll(l);
            int left = HavenCars.loadedCars(l).size();
            check("depart : toutes les voitures chargees de la ville retirees", left == 0,
                    removed + " retirees, " + left + " restantes");
            // la phase reste ACCUEIL dans ce monde d'essai : on les rend pour la suite
            HavenCars.update(s);
            return true;
        });
        STEPS.add((s, l, t) -> t >= 2);
        STEPS.add((s, l, t) -> {
            Map<String, Integer> counts = countByRoom(l);
            boolean ok = places.stream().allMatch(p -> counts.getOrDefault(p.room(), 0) == 1)
                    && counts.size() == places.size();
            check("apres le depart simule, le controle rend une voiture par place (phase ACCUEIL)", ok,
                    counts.toString());
            return true;
        });
    }

    // ------------------------------------------------------------- rue du bar

    private static void planStreet(HavenState state, BlockPos street) {
        STEPS.add((s, l, t) -> {
            JakVehicleEntity probe = Jak3Registry.JAK_VEHICLE.get().create(l);
            if (probe == null) {
                check("creation d'une voiture", false, "EntityType.create a rendu null");
                return true;
            }
            probe.setModel("cara");
            if (!findStreetSpot(l, probe, street)) {
                check("place libre pour une voiture dans la rue du bar", false,
                        "aucune colonne de rue (Y 71) degagee a 16 blocs de la cellule (375, 66, 211)");
                return true;
            }
            VehicleSpec spec = probe.spec();
            car = probe;
            car.moveTo(street0X, streetGround - spec.minY, street0Z, streetYaw, 0.0F);
            l.addFreshEntity(car);
            SPAWNED.add(car);
            samples.clear();
            line(String.format(Locale.ROOT, "rue du bar : cara posee au sol en (%.1f ; %.2f ; %.1f), lacet %.0f,"
                    + " sol en Y %.2f ; voie haute degagee au-dessus : %s", street0X, car.getY(), street0Z,
                    streetYaw, streetGround, streetHigh));
            check("voiture sans gravite vanilla (pas d'expulsion pour vol sur serveur dedie)",
                    car.isNoGravity() && car.getGravity() == 0.0,
                    "isNoGravity " + car.isNoGravity() + ", getGravity " + car.getGravity());
            return true;
        });
        // rase-sol sans entree : 6 secondes
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            double d = height(car);
            if (t % 20 == 0) {
                line(String.format(Locale.ROOT, "  rase-sol t=%d : propulseurs a %.3f du sol, vy %.3f", t, d,
                        car.getDeltaMovement().y));
            }
            if (t >= 100) {
                samples.add(d);
            }
            if (t < 120) {
                return false;
            }
            double target = car.spec().equilibriumDistance(false);
            double min = samples.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
            double max = samples.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
            check("rase-sol sans entree : stable a +-0,2 de la hauteur visee",
                    Math.abs(min - target) <= 0.2 && Math.abs(max - target) <= 0.2,
                    String.format(Locale.ROOT, "visee %.3f (calcul, sans pilote), mesuree %.3f a %.3f sur la derniere seconde",
                            target, min, max));
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (!streetHigh) {
                line("rue du bar couverte au-dessus : la voie haute est essayee en mer");
                return true;
            }
            return highMode(car, t, "rue du bar");
        });
        STEPS.add((s, l, t) -> car == null || !streetHigh || descent(car, t, "rue du bar"));
        // sieges : trois porte-armures, puis un quatrieme
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (t < 20) {
                return false;
            }
            seats(l, car);
            return true;
        });
        STEPS.add((s, l, t) -> {
            for (ArmorStand stand : stands) {
                stand.stopRiding();
                stand.discard();
            }
            stands.clear();
            if (car != null) {
                car.discard();
                car = null;
            }
            return true;
        });
    }

    private static boolean findStreetSpot(ServerLevel level, JakVehicleEntity probe, BlockPos street) {
        VehicleSpec spec = probe.spec();
        double hover = spec.equilibriumDistance(false) - spec.thrusterY;
        // la colonne doit etre libre jusqu'au plancher : la montee le frole avant de pendre dessous
        double floor = Haven.ORIGIN.getY() + VehiclePhysics.HAVEN_TRAFFIC_CELL + VehiclePhysics.FLOOR_ABOVE_TRAFFIC
                - spec.thrusterY;
        float[] yaws = {0.0F, 90.0F, 45.0F, 135.0F};
        boolean found = false;
        for (int pass = 0; pass < 2 && !found; pass++) {
            for (int r = 0; r <= 16 && !found; r += 2) {
                for (int dx = -r; dx <= r && !found; dx += 2) {
                    for (int dz = -r; dz <= r && !found; dz += 2) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        double x = street.getX() + dx + 0.5;
                        double z = street.getZ() + dz + 0.5;
                        double ground = groundBelow(level, x, 100.0, z, 40.0);
                        if (Double.isNaN(ground) || Math.abs(ground - 71.0) > 0.5) {
                            continue;
                        }
                        for (float yaw : yaws) {
                            double y = ground + hover;
                            if (!free(level, probe, x, ground - spec.minY, z, yaw) || !free(level, probe, x, y, z, yaw)) {
                                continue;
                            }
                            boolean high = true;
                            for (double h = y; h <= floor + 1.0 && high; h += 1.0) {
                                high = free(level, probe, x, h, z, yaw);
                            }
                            if (pass == 1 || high) {
                                street0X = x;
                                street0Z = z;
                                streetGround = ground;
                                streetYaw = yaw;
                                streetHigh = high;
                                found = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    private static boolean free(ServerLevel level, JakVehicleEntity probe, double x, double y, double z, float yaw) {
        VehicleSpec spec = probe.spec();
        AABB main = new AABB(x - spec.boxSide / 2, y + spec.boxBottom, z - spec.boxSide / 2,
                x + spec.boxSide / 2, y + spec.boxTop, z + spec.boxSide / 2);
        if (!level.noCollision(null, main)) {
            return false;
        }
        for (int i = 0; i < spec.partCount(); i++) {
            if (!level.noCollision(null, probe.partBox(i, x, y, z, yaw))) {
                return false;
            }
        }
        return true;
    }

    private static void seats(ServerLevel level, JakVehicleEntity vehicle) {
        VehicleSpec spec = vehicle.spec();
        for (int i = 0; i < 4; i++) {
            ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            if (stand == null) {
                check("creation des porte-armures", false, "EntityType.create a rendu null");
                return;
            }
            stand.moveTo(vehicle.getX() + 8.0, streetGround, vehicle.getZ(), 0.0F, 0.0F);
            level.addFreshEntity(stand);
            stands.add(stand);
            SPAWNED.add(stand);
        }
        double yaw = Math.toRadians(vehicle.getYRot());
        StringBuilder detail = new StringBuilder();
        boolean ok = true;
        for (int i = 0; i < 3; i++) {
            ArmorStand stand = stands.get(i);
            boolean boarded = vehicle.board(stand);
            vehicle.positionRider(stand);
            int seat = vehicle.seatOf(stand);
            double wx = vehicle.getX() + spec.seat(seat < 0 ? 0 : seat, 0) * Math.cos(yaw)
                    - spec.seat(seat < 0 ? 0 : seat, 2) * Math.sin(yaw);
            double wy = vehicle.getY() + spec.seat(seat < 0 ? 0 : seat, 1);
            double wz = vehicle.getZ() + spec.seat(seat < 0 ? 0 : seat, 2) * Math.cos(yaw)
                    + spec.seat(seat < 0 ? 0 : seat, 0) * Math.sin(yaw);
            Vec3 riding = stand.position().add(stand.getVehicleAttachmentPoint(vehicle));
            double gap = riding.distanceTo(new Vec3(wx, wy, wz));
            boolean good = boarded && seat == i && gap < 1.0E-3;
            ok &= good;
            detail.append(String.format(Locale.ROOT, "passager %d : monte %b, siege %d, ecart %.5f ; ", i, boarded, seat, gap));
        }
        check("trois passagers aux sieges 0, 1, 2 et aux positions GOAL", ok, detail.toString());
        check("personne ne conduit sans joueur a la place 0", vehicle.getControllingPassenger() == null,
                "conducteur " + vehicle.getControllingPassenger());
        ArmorStand fourth = stands.get(3);
        check("quatrieme passager refuse (trois places)", !vehicle.board(fourth),
                "passagers " + vehicle.getPassengers().size());

        ArmorStand leaving = stands.get(1);
        leaving.stopRiding();
        AABB box = leaving.getBoundingBox();
        boolean clear = true;
        for (AABB part : vehicle.collisionBoxes()) {
            clear &= !part.intersects(box);
        }
        boolean floor = !level.noCollision(null, box.move(0.0, -0.1, 0.0).setMaxY(box.minY));
        double away = Math.hypot(leaving.getX() - vehicle.getX(), leaving.getZ() - vehicle.getZ());
        check("descente a cote de la voiture, hors de ses boites, sur un sol",
                !leaving.isPassenger() && clear && floor && level.noCollision(leaving, box) && away < 8.0,
                String.format(Locale.ROOT, "pose en %s, a %.2f blocs de l'origine, hors des boites %b, sol dessous %b",
                        fmt(leaving.position()), away, clear, floor));
        boolean again = vehicle.board(fourth);
        check("la place liberee est la premiere libre", again && vehicle.seatOf(fourth) == 1,
                "monte " + again + ", siege " + vehicle.seatOf(fourth));
    }

    // ------------------------------------------------------------- en mer

    private static void planSea(HavenState state) {
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = Jak3Registry.JAK_VEHICLE.get().create(l);
            if (c == null) {
                return true;
            }
            c.setModel("cara");
            double water = groundBelow(l, sea0X, 90.0, sea0Z, 40.0);
            double y = (Double.isNaN(water) ? 63.0 : water) + c.spec().equilibriumDistance(false) - c.spec().thrusterY;
            // lacet -90 : l'avant regarde +X, vers le large
            seaHoverY = y;
            c.moveTo(sea0X, y, sea0Z, -90.0F, 0.0F);
            l.addFreshEntity(c);
            SPAWNED.add(c);
            car = c;
            line(String.format(Locale.ROOT, "mer : cara posee en (%.1f ; %.2f ; %.1f), surface de l'eau en Y %.2f",
                    sea0X, y, sea0Z, water));
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (t < 60) {
                return false;
            }
            double d = height(car);
            check("plane au-dessus de l'eau (les sondes voient l'eau, comme dans le jeu)",
                    Math.abs(d - car.spec().equilibriumDistance(false)) < 0.3 && !car.isInWater(),
                    String.format(Locale.ROOT, "propulseurs a %.3f de la surface, dans l'eau : %b", d, car.isInWater()));
            maxSpeed = 0.0;
            reach95 = -1;
            car.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            return true;
        });
        // gaz a fond 10 s
        STEPS.add((s, l, t) -> {
            double speed = horizontalSpeed(car);
            maxSpeed = Math.max(maxSpeed, speed);
            if (reach95 < 0 && speed >= 0.95 * car.spec().maxSpeed) {
                reach95 = t;
            }
            if (t < 200) {
                return false;
            }
            double vmax = car.spec().maxSpeed;
            check("gaz a fond 10 s : vitesse maximale atteinte sans la depasser de plus de 5 %",
                    reach95 >= 0 && maxSpeed <= 1.05 * vmax,
                    String.format(Locale.ROOT, "vitesse max mesuree %.4f bloc/tick (%.2f m/s) pour %.2f reglee ;"
                                    + " 95 %% atteints au tick %d ; parcouru jusqu'en X %.1f",
                            maxSpeed, maxSpeed * 20.0, vmax, reach95, car.getX()));
            check("vitesse de Jak 3 : 40 m/s, soit deux blocs par tick (max-xz-speed, car.gc:96)",
                    Math.abs(maxSpeed * 20.0 - 40.0) <= 2.0,
                    String.format(Locale.ROOT, "%.2f m/s mesures, VehicleSpec.MAX_SPEED_MS = %.1f",
                            maxSpeed * 20.0, VehicleSpec.MAX_SPEED_MS));
            car.setAutotestInput(new VehicleDynamics.Input(false, true, 0));
            marker = -1;
            return true;
        });
        // frein
        STEPS.add((s, l, t) -> {
            // La vitesse AVANT, signe compris : frein tenu sous 2 m/s, la marche
            // arriere s'enclenche, et la vitesse passe par zero entre deux ticks
            // sans jamais tomber sous 0,02 (premier passage).
            double yaw = Math.toRadians(car.getYRot());
            double forward = -(car.getX() - car.xo) * Math.sin(yaw) + (car.getZ() - car.zo) * Math.cos(yaw);
            if (marker < 0 && t > 0 && forward < 0.02) {
                marker = t;
                car.setAutotestInput(VehicleDynamics.Input.NONE);
            }
            if (t < 60) {
                return false;
            }
            check("frein : la voiture s'arrete", marker >= 0,
                    marker >= 0 ? "vitesse avant sous 0,02 bloc/tick apres " + marker + " ticks"
                            : "vitesse avant " + forward);
            yawStart = 0.0;
            car.setAutotestInput(new VehicleDynamics.Input(false, false, 1));
            return true;
        });
        // direction, a l'arret puis en roulant
        STEPS.add((s, l, t) -> {
            // le lacet se CUMULE tick par tick : un demi-tour ramene par wrapDegrees
            // lisait -150 degres pour +210 (premier passage)
            yawStart += Mth.wrapDegrees(car.getYRot() - car.yRotO);
            if (t == 20) {
                double turned = yawStart;
                check("direction a gauche, a l'arret : le lacet diminue", turned < -30.0,
                        String.format(Locale.ROOT, "%.1f degres en 1 s", turned));
                yawStart = 0.0;
                peak = 0.0;
                car.setAutotestInput(new VehicleDynamics.Input(true, false, -1));
            }
            if (t > 20) {
                peak += horizontalSpeed(car);
            }
            if (t < 60) {
                return false;
            }
            double turned = yawStart;
            check("direction a droite en roulant : la voiture tourne et avance", turned > 30.0 && peak > 5.0,
                    String.format(Locale.ROOT, "%.1f degres et %.1f blocs en 2 s", turned, peak));
            car.setAutotestInput(new VehicleDynamics.Input(false, true, 0));
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (t < 40) {
                return false;
            }
            car.setAutotestInput(VehicleDynamics.Input.NONE);
            return true;
        });
        planTightTurn();
        // voie haute en mer si la rue etait couverte
        STEPS.add((s, l, t) -> streetHigh || highMode(car, t, "mer"));
        STEPS.add((s, l, t) -> streetHigh || descent(car, t, "mer"));
        // obstacles d'un bloc, a pleine vitesse : d'abord sous-pas coupes, pour
        // mesurer ce qu'ils evitent, puis comme en jeu, controles compris
        planObstacles("sans sous-pas", false);
        planObstacles("avec sous-pas", true);
        STEPS.add((s, l, t) -> {
            check("aucune valeur NaN ou infinie dans la position, la vitesse ou le lacet", !nan,
                    nan ? nanWhere : "surveille a chaque tick de l'essai");
            if (car != null) {
                car.discard();
                car = null;
            }
            return true;
        });
    }

    /**
     * La montee en voie haute : bascule au tick 0, montee en MODE_MONTEE, puis
     * l'equilibre du jeu -- le plancher moins VehicleDynamics.highHang, sans
     * pilote ici -- a +-0,5 en 40 ticks au plus. Dans la ville, les propulseurs
     * doivent aussi etre a la cellule 75,0 de la carte de trafic.
     */
    private static boolean highMode(JakVehicleEntity c, int t, String where) {
        VehicleSpec spec = c.spec();
        double floorY = VehiclePhysics.floorY(c);
        double expected = floorY - VehicleDynamics.highHang(spec, false);
        if (t == 0) {
            c.toggleMode();
            marker = -1;
            peak = -Double.MAX_VALUE;
            samples.clear();
            flag = c.mode() == VehicleDynamics.MODE_MONTEE;
            line(String.format(Locale.ROOT, "voie haute (%s) : bascule en mode %d, propulseurs en Y %.2f, carte de trafic"
                            + " en Y %.2f, plancher en Y %.2f, equilibre calcule en Y %.3f (sans pilote)", where, c.mode(),
                    c.getY() + spec.thrusterY, VehiclePhysics.trafficY(c), floorY, expected));
            for (VehicleSpec other : VehicleSpec.ALL) {
                line(String.format(Locale.ROOT, "  %s pilotee : propulseurs a la cellule %.3f, %.3f sous le plancher (calcul)",
                        other.model, VehiclePhysics.HAVEN_TRAFFIC_CELL + VehiclePhysics.FLOOR_ABOVE_TRAFFIC
                                - VehicleDynamics.highHang(other, true), VehicleDynamics.highHang(other, true)));
            }
            return false;
        }
        double gap = c.getY() + spec.thrusterY - expected;
        peak = Math.max(peak, gap);
        samples.add(gap);
        if (marker < 0 && c.mode() == VehicleDynamics.MODE_HAUT) {
            marker = t;
        }
        if (t % 10 == 0) {
            line(String.format(Locale.ROOT, "  voie haute t=%d : ecart a l'equilibre %.3f, mode %d, transition %d",
                    t, gap, c.mode(), c.transitionTicks()));
        }
        if (t < 100) {
            return false;
        }
        check("voie haute (" + where + ") : montee en MODE_MONTEE, finie en voie haute avant 2 s",
                flag && marker > 0 && marker <= VehicleDynamics.TRANSITION_TICKS,
                "montee apres la bascule : " + flag + ", voie haute au tick " + marker);
        if (Haven.is(c.level())) {
            double probeY = c.getY() + spec.thrusterY;
            double cell = probeY - Haven.ORIGIN.getY();
            double hang = floorY - probeY;
            check("voie haute (" + where + ") : hauteur de Jak 3, propulseurs a la cellule 75,0 +-0,5 (carte de trafic)"
                            + " et pendus sous le plancher comme dans le jeu",
                    Math.abs(cell - VehiclePhysics.HAVEN_TRAFFIC_CELL) <= 0.5
                            && Math.abs(hang - VehicleDynamics.highHang(spec, false)) <= 0.1,
                    String.format(Locale.ROOT, "propulseurs a la cellule %.3f (Y %.3f, %.2f blocs au-dessus de la rue),"
                                    + " %.3f sous le plancher pour %.3f calcules", cell, probeY, cell - 66.0, hang,
                            VehicleDynamics.highHang(spec, false)));
        }
        // premier tick a partir duquel l'ecart reste sous 0,5 jusqu'a la fin
        int settled = -1;
        for (int i = samples.size() - 1; i >= 0; i--) {
            if (Math.abs(samples.get(i)) > 0.5) {
                settled = i + 2;
                break;
            }
        }
        if (settled < 0) {
            settled = 1;
        }
        check("voie haute (" + where + ") : equilibre du jeu atteint a +-0,5 en 2 s au plus, et tenu",
                c.mode() == VehicleDynamics.MODE_HAUT && settled <= VehicleDynamics.TRANSITION_TICKS,
                String.format(Locale.ROOT, "stable a +-0,5 des le tick %d, depassement max %.3f, ecart final %.3f, mode %d",
                        settled, peak, samples.get(samples.size() - 1), c.mode()));
        return true;
    }

    /** Le retour au rase-sol : descente jusqu'au contact des sondes, puis hauteur de rase-sol. */
    private static boolean descent(JakVehicleEntity c, int t, String where) {
        VehicleSpec spec = c.spec();
        if (t == 0) {
            c.toggleMode();
            marker = -1;
            flag = false;
            samples.clear();
            return false;
        }
        if (marker < 0 && c.mode() == VehicleDynamics.MODE_SOL) {
            marker = t;
        }
        flag |= insideBlocks(c);
        if (t > 100) {
            samples.add(height(c));
        }
        if (t < 120) {
            return false;
        }
        double target = spec.equilibriumDistance(false);
        double min = samples.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        double max = samples.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        check("redescente (" + where + ") : contact des sondes, rase-sol retrouve, jamais dans un bloc",
                marker >= 0 && !flag && Math.abs(min - target) <= 0.3 && Math.abs(max - target) <= 0.3,
                String.format(Locale.ROOT, "mode rase-sol au tick %d, hauteur finale %.3f a %.3f (visee %.3f), boite dans un bloc : %b",
                        marker, min, max, target, flag));
        return true;
    }

    // ------------------------------------------------------------- virage serre et obstacles

    /** Virage serre a pleine vitesse, en pleine mer : le lacet suit la loi du jeu et la vitesse reste. */
    private static void planTightTurn() {
        STEPS.add((s, l, t) -> {
            launch(car, sea0X + 200.5, seaHoverY, sea0Z - 40.0, -90.0F, true);
            car.setAutotestInput(new VehicleDynamics.Input(true, false, -1));
            yawStart = 0.0;
            sumYaw = 0.0;
            sumForward = 0.0;
            peak = 0.0;
            marker = 0;
            return true;
        });
        STEPS.add((s, l, t) -> {
            float turned = Mth.wrapDegrees(car.getYRot() - car.yRotO);
            yawStart += turned;
            double yaw = Math.toRadians(car.getYRot());
            double dx = car.getX() - car.xo;
            double dz = car.getZ() - car.zo;
            double forward = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
            double lateral = dx * Math.cos(yaw) + dz * Math.sin(yaw);
            // la direction est lissee a 8/s : on mesure apres une seconde
            if (t >= 20) {
                sumYaw += turned;
                sumForward += forward;
                marker++;
                peak = Math.max(peak, Math.abs(lateral) / Math.max(1.0E-6, Math.abs(forward)));
            }
            if (t < 60) {
                return false;
            }
            VehicleSpec spec = car.spec();
            double v = sumForward / marker;
            double rate = sumYaw / marker;
            double expected = Math.toDegrees(spec.steerGain * VehicleSpec.TICK * spec.halfGainSpeed
                    / (spec.halfGainSpeed + v));
            double radius = v / Math.toRadians(Math.abs(rate));
            check("virage serre a pleine vitesse : lacet de la loi du jeu (gain x 15/(15+v)), vitesse gardee, sans deraper",
                    v >= 0.9 * spec.maxSpeed && Math.abs(rate / expected - 1.0) <= 0.1 && peak < 0.3,
                    String.format(Locale.ROOT, "vitesse moyenne %.3f bloc/tick (%.1f m/s), lacet %.3f degre/tick pour %.3f"
                                    + " calcules, rayon %.1f blocs, glissade laterale %.0f %% au plus, %.1f degres en 3 s",
                            v, v * 20.0, rate, expected, radius, peak * 100.0, yawStart));
            car.setAutotestInput(new VehicleDynamics.Input(false, true, 0));
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (t < 40) {
                return false;
            }
            car.setAutotestInput(VehicleDynamics.Input.NONE);
            return true;
        });
    }

    /** Les quatre obstacles d'un bloc, sous-pas coupes ({@code checked} faux, mesure seule) ou comme en jeu. */
    private static void planObstacles(String pass, boolean checked) {
        STEPS.add((s, l, t) -> {
            VehiclePhysics.substeps = checked;
            line("obstacles (" + pass + ")" + (checked ? " : comme en jeu, controles compris"
                    : " : mesures seulement, pour voir ce que les sous-pas evitent"));
            return true;
        });
        for (double phase : new double[]{0.0, 0.5, 1.0, 1.5}) {
            planWall(pass, checked, phase);
        }
        planCorridor(pass, checked);
        planCorner(pass, checked, false);
        planCorner(pass, checked, true);
        planCeiling(pass, checked);
        STEPS.add((s, l, t) -> {
            VehiclePhysics.substeps = true;
            return true;
        });
    }

    /**
     * Mur d'un bloc d'epaisseur, droit devant, a deux blocs par tick. Quatre
     * phases : le bord avant part a 24, 24,5, 25 et 25,5 blocs du mur, pour que
     * le tick du choc le fasse arriver devant, sur, ou au-dela de la face.
     */
    private static void planWall(String pass, boolean checked, double phase) {
        STEPS.add((s, l, t) -> {
            int x = (int) wallX;
            int z = (int) Math.floor(sea0Z);
            build(l, x, x, 58, 80, z - 8, z + 8);
            launch(car, wallX - 40.0, seaHoverY, sea0Z, -90.0F, true);
            double front = -Double.MAX_VALUE;
            for (AABB box : car.collisionBoxes()) {
                front = Math.max(front, box.maxX);
            }
            launch(car, car.getX() + (wallX - 24.0 - phase) - front, seaHoverY, sea0Z, -90.0F, true);
            car.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            startMonitor();
            peak = -Double.MAX_VALUE;
            marker = -1;
            impact = 0.0;
            lastSpeed = 0.0;
            flag = false;
            return true;
        });
        STEPS.add((s, l, t) -> {
            double speed = horizontalSpeed(car);
            for (AABB box : car.collisionBoxes()) {
                peak = Math.max(peak, box.maxX);
            }
            if (marker < 0 && t > 0 && speed < 0.95 * car.spec().maxSpeed) {
                marker = t;
                impact = lastSpeed;
            }
            lastSpeed = speed;
            flag |= car.getDeltaMovement().x < -0.05;
            if (t < 40) {
                return false;
            }
            monitor = false;
            car.setAutotestInput(VehicleDynamics.Input.NONE);
            double vmax = car.spec().maxSpeed;
            verdict(checked, String.format(Locale.ROOT, "mur d'un bloc a pleine vitesse, phase %.1f (%s) : ni traverse ni penetre",
                            phase, pass),
                    impact >= 0.95 * vmax && peak <= wallX + 1.0E-6 && car.getX() < wallX && insideTicks == 0
                            && maxCut <= CUT_TOLERANCE,
                    String.format(Locale.ROOT, "vitesse a l'impact %.3f bloc/tick (%.1f m/s), face du mur en X %.3f,"
                                    + " bord avant le plus loin %.4f, origine finale X %.3f, choc au tick %d, rebond %b",
                            impact, impact * 20.0, wallX, peak, car.getX(), marker, flag) + monitorDetail());
            clearBuilt(l);
            return true;
        });
    }

    /** Virage serre a pleine vitesse vers un mur d'un bloc qui longe la trajectoire. */
    private static void planCorridor(String pass, boolean checked) {
        int x0 = (int) Math.floor(sea0X) + 140;
        int x1 = x0 + 200;
        int wz = (int) Math.floor(sea0Z) - 6;
        STEPS.add((s, l, t) -> {
            build(l, x0, x1, 58, 80, wz, wz);
            launch(car, x0 + 10.5, seaHoverY, sea0Z, -90.0F, true);
            // lacet -90 : la gauche est -Z, du cote du mur
            car.setAutotestInput(new VehicleDynamics.Input(true, false, 1));
            startMonitor();
            peak = Double.MAX_VALUE;
            contacts = 0;
            yawStart = 0.0;
            return true;
        });
        STEPS.add((s, l, t) -> {
            yawStart += Mth.wrapDegrees(car.getYRot() - car.yRotO);
            double nearest = Double.MAX_VALUE;
            for (AABB box : car.collisionBoxes()) {
                if (box.maxX > x0 && box.minX < x1 + 1) {
                    nearest = Math.min(nearest, box.minZ);
                }
            }
            peak = Math.min(peak, nearest);
            if (nearest <= wz + 1 + 0.01) {
                contacts++;
            }
            if (t < 50) {
                return false;
            }
            monitor = false;
            car.setAutotestInput(VehicleDynamics.Input.NONE);
            verdict(checked, "virage serre a pleine vitesse contre un mur d'un bloc (" + pass + ") : ni traverse ni penetre",
                    peak >= wz + 1 - 1.0E-6 && contacts > 0 && insideTicks == 0 && maxCut <= CUT_TOLERANCE,
                    String.format(Locale.ROOT, "face du mur en Z %d, bord le plus proche %.4f, %d tick(s) au contact,"
                                    + " %.1f degres en 2,5 s, origine finale %s", wz + 1, peak, contacts, yawStart,
                            fmt(car.position())) + monitorDetail());
            clearBuilt(l);
            return true;
        });
    }

    /**
     * Le bout d'un mur d'un bloc, aborde a 45 degres a pleine vitesse : 33
     * trajectoires decalees d'un quart de bloc, pour que le coin d'une boite passe
     * par le coin du mur a toutes les distances.
     *
     * POURQUOI SI SERRE. Les quatre boites de la voiture se suivent sur la
     * diagonale, a 1,73 bloc l'une de l'autre sur chaque axe : l'une d'elles
     * tombe presque toujours dans le coin et la voiture glisse. Seuls quelques
     * alignements laissent passer un trajet en L. Au premier passage, neuf
     * trajectoires decalees d'un bloc n'en trouvaient aucun, meme sans sous-pas.
     *
     * DEUX BOUTS DE MUR, EN MIROIR. A 45 degres, les deux axes sont a egalite et
     * l'ordre du L tient a un arrondi : ici la verticale, puis Z, puis X. Le bout
     * d'une colonne le long de Z ne se coupe qu'en X d'abord ; celui d'une rangee
     * le long de X, qu'en Z d'abord (banc hors jeu coupe_coin_sim.py : 0,38 bloc
     * au pire, sous-pas coupes). Un conducteur aborde les coins sous tous les
     * angles : les deux ordres existent en jeu, l'essai fait les deux murs.
     *
     * @param row vrai : rangee le long de X ; faux : colonne le long de Z
     */
    private static void planCorner(String pass, boolean checked, boolean row) {
        int cx = (int) Math.floor(sea0X) + (row ? 250 : 300);
        int cz = (int) Math.floor(sea0Z);
        String wall = row ? "rangee le long de X" : "colonne le long de Z";
        STEPS.add((s, l, t) -> {
            if (row) {
                build(l, cx, cx + 20, 58, 80, cz, cz);
                startMonitor();
                runsTouched = 0;
                return true;
            }
            build(l, cx, cx, 58, 80, cz, cz + 20);
            // Temoin : une surveillance aveugle rendrait aussi zero. La voiture est
            // posee a l'ouest du mur, puis a l'est, sans passer par la physique :
            // le trajet droit entre les deux traverse le bloc, et doit se voir.
            startMonitor();
            launch(car, cx - 10.5, seaHoverY, cz + 10.5, -90.0F, false);
            prevX = car.getX();
            prevY = car.getY();
            prevZ = car.getZ();
            prevYaw = car.getYRot();
            monitorPrev = true;
            car.moveTo(cx + 11.5, seaHoverY, cz + 10.5, -90.0F, 0.0F);
            watchCollisions(car, "temoin");
            verdict(checked, "temoin (" + pass + ") : la surveillance voit une voiture passee au travers d'un mur d'un bloc",
                    maxCut >= 0.9 && cutTicks == 1,
                    String.format(Locale.ROOT, "coupe mesuree %.3f bloc, %d tick(s) signale(s)", maxCut, cutTicks));
            startMonitor();
            runsTouched = 0;
            return true;
        });
        for (int k = -16; k <= 16; k++) {
            double offset = k / 4.0;
            STEPS.add((s, l, t) -> {
                double diagonal = Math.sqrt(0.5);
                // la trajectoire passe par le bout du mur, decalee en travers ; en miroir pour la rangee
                double px = cx + 0.5 + (row ? -offset : offset) / 2.0;
                double pz = cz + 0.5 + (row ? offset : -offset) / 2.0;
                // lacet -45 : l'avant regarde +X +Z
                launch(car, px - 20.0 * diagonal, seaHoverY, pz - 20.0 * diagonal, -45.0F, true);
                car.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
                runCut = 0.0;
                runContact = -1;
                return true;
            });
            STEPS.add((s, l, t) -> {
                if (runContact < 0 && horizontalSpeed(car) < 0.95 * car.spec().maxSpeed) {
                    runContact = t;
                }
                if (t < 25) {
                    return false;
                }
                if (runContact >= 0) {
                    runsTouched++;
                }
                // detail par trajectoire, a comparer au banc hors jeu (coupe_coin_sim.py)
                line(String.format(Locale.ROOT, "  coin, %s (%s), decalage %+.2f : premier tick ralenti %d, coupe de coin %.3f bloc",
                        wall, pass, offset, runContact, runCut));
                return true;
            });
        }
        STEPS.add((s, l, t) -> {
            monitor = false;
            car.setAutotestInput(VehicleDynamics.Input.NONE);
            verdict(checked, "bout d'un mur d'un bloc, " + wall + ", en diagonale a pleine vitesse (" + pass
                            + ") : aucun coin traverse",
                    runsTouched >= 10 && insideTicks == 0 && maxCut <= CUT_TOLERANCE,
                    String.format(Locale.ROOT, "33 trajectoires a 45 degres autour du bout du mur (X %d, Z %d), %d ont touche"
                            + " le mur ; tolerance %.2f bloc", cx, cz, runsTouched, CUT_TOLERANCE) + monitorDetail());
            clearBuilt(l);
            return true;
        });
    }

    /** Plafond d'un bloc au-dessus de la voiture, qui bascule en voie haute a pleine vitesse. */
    private static void planCeiling(String pass, boolean checked) {
        int x0 = (int) Math.floor(sea0X) + 380;
        int x1 = x0 + 100;
        int z0 = (int) Math.floor(sea0Z) - 10;
        int z1 = z0 + 20;
        STEPS.add((s, l, t) -> {
            ceilingY = (int) Math.floor(seaHoverY + car.spec().boxTop) + 3;
            build(l, x0, x1, ceilingY, ceilingY, z0, z1);
            launch(car, x0 + 5.5, seaHoverY, sea0Z, -90.0F, true);
            car.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            car.toggleMode();
            flag = car.mode() == VehicleDynamics.MODE_MONTEE;
            startMonitor();
            peak = -Double.MAX_VALUE;
            contacts = -1;
            return true;
        });
        STEPS.add((s, l, t) -> {
            for (AABB box : car.collisionBoxes()) {
                if (box.maxX > x0 && box.minX < x1 + 1 && box.maxZ > z0 && box.minZ < z1 + 1) {
                    peak = Math.max(peak, box.maxY);
                }
            }
            if (contacts < 0 && peak >= ceilingY - 0.01) {
                contacts = t;
            }
            if (t < 70) {
                return false;
            }
            monitor = false;
            car.setAutotestInput(VehicleDynamics.Input.NONE);
            int mode = car.mode();
            verdict(checked, "plafond d'un bloc pendant la montee en voie haute, a pleine vitesse (" + pass
                            + ") : ni traverse ni penetre",
                    flag && peak <= ceilingY + 1.0E-6 && contacts >= 0 && insideTicks == 0 && maxCut <= CUT_TOLERANCE,
                    String.format(Locale.ROOT, "dessous du plafond en Y %d, haut des boites sous le plafond au plus %.4f,"
                            + " contact au tick %d, montee en MODE_MONTEE %b", ceilingY, peak, contacts, flag) + monitorDetail());
            verdict(checked, "montee bloquee plus de 2 s : la voiture redescend (" + pass + ")",
                    mode == VehicleDynamics.MODE_DESCENTE || mode == VehicleDynamics.MODE_SOL, "mode final " + mode);
            clearBuilt(l);
            car.resetFlight();
            return true;
        });
    }

    /** Pose la voiture a l'arret, ou lancee a la vitesse maximale, gaz et moteur a fond. */
    private static void launch(JakVehicleEntity c, double x, double y, double z, float yaw, boolean fullSpeed) {
        c.resetFlight();
        c.moveTo(x, y, z, yaw, 0.0F);
        if (fullSpeed) {
            double r = Math.toRadians(yaw);
            double v = c.spec().maxSpeed;
            c.setDeltaMovement(-Math.sin(r) * v, 0.0, Math.cos(r) * v);
            c.controls().throttle = 1.0;
            c.controls().engine = 1.0;
        }
    }

    private static void build(ServerLevel level, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
                    PLACED.add(pos);
                }
            }
        }
    }

    private static void clearBuilt(ServerLevel level) {
        for (BlockPos pos : PLACED) {
            level.setBlock(pos, Haven.generatorState(pos.getY()), 2);
        }
        PLACED.clear();
    }

    /** Controle compte, ou simple mesure au rapport quand les sous-pas sont coupes. */
    private static void verdict(boolean checked, String what, boolean ok, String detail) {
        if (checked) {
            check(what, ok, detail);
        } else {
            line("MESURE " + what + (ok ? " (dans la tolerance)" : " (HORS tolerance)") + " -- " + detail);
        }
    }

    private static void startMonitor() {
        monitor = true;
        maxCut = 0.0;
        cutWhere = "";
        cutTicks = 0;
        insideTicks = 0;
        insideWhere = "";
    }

    private static String monitorDetail() {
        return String.format(Locale.ROOT, " ; boite dans un bloc a %d tick(s)%s ; trajet droit dans un bloc a %d tick(s),"
                + " coupe de coin la plus profonde %.3f bloc%s", insideTicks, insideWhere, cutTicks, maxCut, cutWhere);
    }

    private static void rememberPose() {
        if (car != null && !car.isRemoved()) {
            prevX = car.getX();
            prevY = car.getY();
            prevZ = car.getZ();
            prevYaw = car.getYRot();
            monitorPrev = true;
        } else {
            monitorPrev = false;
        }
    }

    /**
     * Pendant un obstacle : aucune boite dans un bloc a la fin du tick, et le
     * trajet droit depuis le tick precedent -- ce qu'une collision continue
     * aurait suivi -- n'entre pas plus loin que CUT_TOLERANCE dans un bloc.
     */
    private static void watchCollisions(JakVehicleEntity c, String where) {
        if (!monitor) {
            return;
        }
        if (insideBlocks(c) && insideTicks++ == 0) {
            insideWhere = " (d'abord a " + where + ", origine " + fmt(c.position()) + ")";
        }
        if (!monitorPrev) {
            return;
        }
        VehicleSpec spec = c.spec();
        double half = spec.boxSide / 2.0;
        double deepest = 0.0;
        for (int k = 1; k < CUT_SAMPLES; k++) {
            double f = (double) k / CUT_SAMPLES;
            double x = Mth.lerp(f, prevX, c.getX());
            double y = Mth.lerp(f, prevY, c.getY());
            double z = Mth.lerp(f, prevZ, c.getZ());
            float yaw = Mth.rotLerp((float) f, prevYaw, c.getYRot());
            List<AABB> boxes = new ArrayList<>(4);
            boxes.add(new AABB(x - half, y + spec.boxBottom, z - half, x + half, y + spec.boxTop, z + half));
            for (int i = 0; i < spec.partCount(); i++) {
                boxes.add(c.partBox(i, x, y, z, yaw));
            }
            for (AABB box : boxes) {
                for (VoxelShape shape : c.level().getBlockCollisions(null, box)) {
                    for (AABB block : shape.toAabbs()) {
                        double depth = Math.min(overlap(box.minX, box.maxX, block.minX, block.maxX),
                                Math.min(overlap(box.minY, box.maxY, block.minY, block.maxY),
                                        overlap(box.minZ, box.maxZ, block.minZ, block.maxZ)));
                        deepest = Math.max(deepest, depth);
                        if (depth > maxCut) {
                            maxCut = depth;
                            cutWhere = String.format(Locale.ROOT, " (%s, a %.2f du tick, bloc %s)", where, f,
                                    BlockPos.containing(block.getCenter()).toShortString());
                        }
                    }
                }
            }
        }
        if (deepest > 0.01) {
            cutTicks++;
        }
        runCut = Math.max(runCut, deepest);
    }

    private static double overlap(double aMin, double aMax, double bMin, double bMax) {
        return Math.min(aMax, bMax) - Math.max(aMin, bMin);
    }

    /** Vrai si une boite de la voiture recoupe un bloc (les entites ne comptent pas). */
    private static boolean insideBlocks(JakVehicleEntity c) {
        for (AABB box : c.collisionBoxes()) {
            for (VoxelShape shape : c.level().getBlockCollisions(null, box.deflate(1.0E-4))) {
                if (!shape.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------- mesures

    /** Distance moyenne des deux propulseurs au sol, mesuree par un rayon de 20 blocs. */
    private static double height(JakVehicleEntity c) {
        VehicleSpec spec = c.spec();
        double yaw = Math.toRadians(c.getYRot());
        double sum = 0.0;
        for (double along : new double[]{spec.thrusterFrontZ, spec.thrusterRearZ}) {
            double x = c.getX() - Math.sin(yaw) * along;
            double z = c.getZ() + Math.cos(yaw) * along;
            double y = c.getY() + spec.thrusterY;
            sum += y - groundBelow(c.level() instanceof ServerLevel s ? s : null, x, y, z, 20.0);
        }
        return sum / 2.0;
    }

    private static double groundBelow(@Nullable ServerLevel level, double x, double y, double z, double reach) {
        if (level == null) {
            return Double.NaN;
        }
        BlockHitResult hit = level.clip(new ClipContext(new Vec3(x, y, z), new Vec3(x, y - reach, z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? Double.NaN : hit.getLocation().y;
    }

    private static double horizontalSpeed(JakVehicleEntity c) {
        return Math.hypot(c.getX() - c.xo, c.getZ() - c.zo);
    }

    private static void watchNaN(JakVehicleEntity c, String where) {
        Vec3 v = c.getDeltaMovement();
        if (!nan && !VehicleDynamics.finite(c.getX(), c.getY(), c.getZ(), v.x, v.y, v.z, c.getYRot(),
                c.controls().engine, c.controls().steer)) {
            nan = true;
            nanWhere = where + " : position " + c.position() + ", vitesse " + v + ", lacet " + c.getYRot();
        }
    }

    private static Map<String, Integer> countByRoom(ServerLevel level) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (JakVehicleEntity c : HavenCars.loadedCars(level)) {
            if (!c.isRemoved() && c.room() != null) {
                counts.merge(c.room(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private static List<JakVehicleEntity> carsOf(ServerLevel level, String room) {
        List<JakVehicleEntity> cars = new ArrayList<>();
        for (JakVehicleEntity c : HavenCars.loadedCars(level)) {
            if (!c.isRemoved() && room.equals(c.room())) {
                cars.add(c);
            }
        }
        return cars;
    }

    @Nullable
    private static JakVehicleEntity onlyCar(ServerLevel level, String room) {
        List<JakVehicleEntity> cars = carsOf(level, room);
        return cars.size() == 1 ? cars.get(0) : null;
    }

    private static void hold(ServerLevel level, ChunkPos pos) {
        if (!HELD.contains(pos)) {
            level.getChunkSource().addRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            HELD.add(pos);
        }
    }

    private static void end(MinecraftServer server) {
        finished = true;
        monitor = false;
        VehiclePhysics.substeps = true;
        ServerLevel level = Haven.level(server);
        if (level != null) {
            // un essai interrompu au milieu d'un obstacle ne laisse pas de pierre en mer
            clearBuilt(level);
        }
        for (Entity entity : SPAWNED) {
            if (!entity.isRemoved() && (entity instanceof ArmorStand
                    || (entity instanceof JakVehicleEntity vehicle && vehicle.room() == null))) {
                entity.discard();
            }
        }
        if (level != null) {
            for (ChunkPos pos : HELD) {
                level.getChunkSource().removeRegionTicket(TICKET, pos, TICKET_DISTANCE, pos);
            }
        }
        HELD.clear();
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("vehicules_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest vehicules : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest vehicules : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest vehicules : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }

    private static String fmt(Vec3 v) {
        return String.format(Locale.ROOT, "(%.2f ; %.2f ; %.2f)", v.x, v.y, v.z);
    }
}
