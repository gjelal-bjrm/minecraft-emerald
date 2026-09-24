package com.emerald.jak.vehicle;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenRooms;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenSpawner;
import com.emerald.haven.traffic.HavenLaneMap;
import com.emerald.init.Jak3Registry;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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
 * Le banc d'essai des voitures et des motos, INERTE sans EMERALDWEAPONS_AUTOTEST=vehicules.
 *
 * Il tourne dans la dimension de la ville, cote serveur, avec la vraie entite
 * et VehiclePhysics : le vehicule tique comme en jeu, l'autotest ne fait que lui
 * donner des commandes (a la place d'un conducteur) et MESURER le monde.
 *
 * Trois terrains :
 *  - les places des appartements, pour la pose, les doublons, les remises en
 *    place, et la moto garee sans gener ni la porte ni la voiture ;
 *  - la rue du bar, pour la hauteur de rase-sol, la voie haute et les sieges ;
 *  - la mer du generateur a l'est de la ville, plate et sans fin, pour la
 *    vitesse de 40 m/s, le frein, la direction, un virage serre, et des
 *    obstacles d'un bloc poses puis retires : mur a pleine vitesse, virage
 *    contre un mur, coin en diagonale, plafond pendant la montee.
 *
 * LA VOITURE cara passe par tout ; CHAQUE MOTO ensuite par l'essentiel : rase-sol
 * a sa hauteur calculee, voie haute, une seule place, 40 m/s, frein, et le mur
 * d'un bloc a pleine vitesse sous ses quatre phases, comme en jeu. La hauteur de
 * rase-sol se juge sans pilote puis avec le poids d'un pilote, impose faute de
 * joueur sur le serveur d'essai.
 *
 * LES OBSTACLES DE LA VOITURE PASSENT DEUX FOIS : sous-pas coupes, pour mesurer
 * seulement ce qu'ils evitent, puis comme en jeu, controles compris. Pendant ces
 * essais, chaque tick est surveille : aucune boite dans un bloc, et la coupe de
 * coin -- le trajet droit entre deux ticks, echantillonne -- sous CUT_TOLERANCE.
 *
 * LA CONDUITE PAR UN CLIENT se juge a ce que le serveur renverrait au conducteur :
 * la place 0 prise et la voiture declaree conduite par un client, ses positions
 * rejouees comme handleMoveVehicle, et un ServerEntity temoin qui capte les
 * paquets (voir planDriverSync).
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
    /**
     * Le passage devant une porte, en cellules vers la rue : celui que
     * jak_voxelize.py laisse libre (CAR_GAP). Les portes s'ouvrent a l'ouest (-X).
     */
    private static final int DOOR_PASSAGE = 2;

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

    // les chocs (planCollisions)
    @Nullable
    private static JakVehicleEntity second;
    @Nullable
    private static Mob rammed;
    @Nullable
    private static Villager bystander;
    private static float rammedHealth;
    private static double rammedLaunch;
    private static boolean rammedHurt;
    private static float bystanderHealth;
    private static double hitSpeed;
    private static int crashesBefore;
    /**
     * Le couloir du renversement, en cellules : la voie haute au-dessus de l'eau du nord de
     * la grille. DANS LA VILLE, et non en mer : hors de la grille, le balayage de l'invasion
     * retire monstres et habitants avant meme que la voiture n'arrive (banc du 20 sept.).
     */
    private static final double RAM_CELL_X = 560.0;
    private static final double RAM_CELL_Z = 40.0;
    private static int ramsBefore;

    // conduite par un client (planDriverSync)
    @Nullable
    private static JakVehicleEntity drivenCar;
    @Nullable
    private static ArmorStand driverSeat;
    @Nullable
    private static ServerEntity witness;
    private static final List<Vec3> motionSent = new ArrayList<>();
    private static int emptyTicks;
    private static int motionMismatches;
    private static double drivenX;
    private static double expectedMotionX;
    /**
     * Les paquets du conducteur arrives entre deux ticks du serveur, en boucle : les
     * deux horloges derivent, et un a-coup du client laisse des ticks vides puis en
     * rattrape plusieurs. Trois ticks vides d'affilee : ServerEntity ne regarde la
     * vitesse que tous les trois ticks (updateInterval), l'un d'eux tombe dessus.
     */
    private static final int[] DRIVER_PACKETS = {1, 1, 2, 1, 0, 1, 1, 0, 0, 0, 3, 1, 1, 2, 1};
    private static final int DRIVER_TICKS = 150;

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

    /**
     * Le couloir de l'essai du pont entre les deux tours, en cellules : a x constant,
     * du bassin au nord du pont a la mer au sud, de l'eau partout sauf le tablier, qui
     * le traverse de z 608 a 636 et monte aux cellules 74 et 75 (dessus en 76,0 au
     * plus). La voie haute plate, cellule 75,0, y butait de cote ; la carte de hauteur
     * de Jak 3 la releve de 10 blocs au-dessus du pont (HavenLaneMap).
     */
    private static final double BRIDGE_X = 620.5;
    private static final double BRIDGE_NORTH_Z = 330.5;
    private static final double BRIDGE_SOUTH_Z = 680.5;
    private static final double BRIDGE_DECK_NORTH_Z = 608.0;
    private static final double BRIDGE_DECK_SOUTH_Z = 637.0;
    private static final double BRIDGE_DECK_TOP = 76.0;
    /** Au nord, la carte est revenue a sa base bien avant cette cellule : on y juge le retour a 75,0. */
    private static final double BRIDGE_BACK_Z = 470.5;
    private static double bridgeClearance;
    private static double bridgeSlowest;
    private static double bridgePeakCell;
    private static boolean bridgeInside;
    private static boolean bridgeHigh;

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
                line("autotest des voitures et des motos de Haven, " + LocalDateTime.now().withNano(0));
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
        // le couloir du pont entre les tours, du bassin a la mer
        for (double z = BRIDGE_NORTH_Z - 32; z <= BRIDGE_SOUTH_Z + 16; z += 32) {
            hold(level, new ChunkPos(BlockPos.containing(o.getX() + BRIDGE_X, 64, o.getZ() + z)));
        }
        // le couloir du renversement : la voie haute au-dessus de l'eau du nord, dans la grille
        for (double x = RAM_CELL_X - 16; x <= RAM_CELL_X + 80; x += 16) {
            hold(level, new ChunkPos(BlockPos.containing(o.getX() + x, 80, o.getZ() + RAM_CELL_Z)));
        }
        line("tickets poses sur " + HELD.size() + " troncons (rue du bar, places, couloir du pont, couloir du"
                + " renversement, zone en mer de 648 x 192 blocs)");

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
        planDriverSync();
        STEPS.add((s, l, t) -> {
            cushion();
            return true;
        });
        planCollisions();
        planBalance();
        planDamage();
        STEPS.add((s, l, t) -> {
            // hors du pont, rien ne change : la bosse du carrefour ouest (+8 dans la carte du jeu) ne degage rien
            // dans notre ville, seul le trafic la suit
            BlockPos at = HavenState.get(s).origin();
            double player = VehiclePhysics.havenTrafficY(at.getX() + 271.0, at.getZ() + 318.0) - at.getY();
            double traffic = HavenLaneMap.rise(271.0, 318.0);
            check("voie haute : hors du pont, la voie du joueur reste a la cellule 75,0 (seul le trafic suit les autres bosses)",
                    Math.abs(player - VehiclePhysics.HAVEN_TRAFFIC_CELL) < 1.0E-9 && traffic > 5.0
                            && Math.abs(HavenLaneMap.playerHighest() - 10.0) < 1.0E-9,
                    String.format(Locale.ROOT, "carrefour ouest, cellule (271 ; 318) : voie du joueur %.3f, trafic +%.2f ;"
                            + " plus haute bosse du joueur +%.1f (le pont)", player, traffic, HavenLaneMap.playerHighest()));
            return true;
        });
        planBridge("cara", true);
        planBridge("cara", false);
        planBridge("bikea", true);
        for (String model : JakVehicleEntity.BIKES) {
            planBike(state, street, model);
        }
        STEPS.add((s, l, t) -> {
            check("aucune valeur NaN ou infinie dans la position, la vitesse ou le lacet", !nan,
                    nan ? nanWhere : "surveille a chaque tick de l'essai");
            return true;
        });
    }

    // ------------------------------------------------------------- appartements

    private static void planApartments(MinecraftServer server, HavenState state) {
        BlockPos o = state.origin();
        List<HavenCars.Place> places = HavenCars.places(server);
        List<HavenCars.Place> cars = places.stream().filter(p -> !p.isBike()).toList();
        List<HavenCars.Place> bikes = places.stream().filter(HavenCars.Place::isBike).toList();
        STEPS.add((s, l, t) -> {
            boolean paired = cars.size() == 3 && bikes.size() == 3;
            for (int i = 0; paired && i < 3; i++) {
                paired = JakVehicleEntity.CARS.get(i).equals(cars.get(i).model())
                        && JakVehicleEntity.BIKES.get(i).equals(bikes.get(i).model())
                        && cars.get(i).room().equals(bikes.get(i).room())
                        && !cars.get(i).key().equals(bikes.get(i).key());
            }
            check("haven_rooms.json : trois places de voiture et trois places de moto lues, une de chaque par appartement",
                    paired, places.size() + " places : " + places);
            Map<String, Integer> before = countByRoom(l);
            line("vehicules d'appartement deja presents au lancement : " + before
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
                List<JakVehicleEntity> found = carsOf(l, place.key());
                boolean one = found.size() == 1;
                String detail = found.size() + " vehicule(s)";
                boolean ok = one;
                if (one) {
                    JakVehicleEntity c = found.get(0);
                    apartmentCars.put(place.key(), c.getUUID());
                    Vec3 center = HavenCars.modelCenter(c);
                    double wantX = o.getX() + place.floor().getX() + 0.5;
                    double wantZ = o.getZ() + place.floor().getZ() + 0.5;
                    double gap = Math.hypot(center.x - wantX, center.z - wantZ);
                    double yawGap = Math.abs(Mth.wrapDegrees(c.getYRot() - place.yaw()));
                    ok = gap < 0.6 && yawGap < 1.0 && place.model().equals(c.model())
                            && c.getUUID().equals(registry.car(place.key()));
                    double front = VehiclePhysics.probe(c, c.spec().thrusterFrontZ);
                    detail = String.format(Locale.ROOT,
                            "%s, centre du modele a %.2f bloc de la place (%.1f ; %.1f), lacet %.1f (voulu %.1f),"
                                    + " registre %s ; origine %s, propulseur avant a %.2f du sol",
                            c.model(), gap, wantX, wantZ, c.getYRot(), place.yaw(),
                            c.getUUID().equals(registry.car(place.key())) ? "d'accord" : "DIFFERENT",
                            fmt(c.position()), front);
                }
                check("un vehicule sur la place " + place.key() + " (" + place.model() + ")", ok, detail);
            }
            for (int i = 0; i < bikes.size() && i < cars.size(); i++) {
                bikeParking(s, l, o, bikes.get(i), cars.get(i));
            }
            line("vehicules marques charges par place : " + counts);
            // second passage : memoire oubliee comme apres un redemarrage, et deux controles
            HavenCars.forgetMemory();
            HavenCars.update(s);
            HavenCars.update(s);
            return true;
        });
        STEPS.add((s, l, t) -> t >= 40);
        STEPS.add((s, l, t) -> {
            for (HavenCars.Place place : places) {
                List<JakVehicleEntity> found = carsOf(l, place.key());
                check("second passage : toujours un seul vehicule pour " + place.key(),
                        found.size() == 1 && found.get(0).getUUID().equals(apartmentCars.get(place.key())),
                        found.size() + " vehicule(s), meme identifiant : "
                                + (found.size() == 1 && found.get(0).getUUID().equals(apartmentCars.get(place.key()))));
            }
            // un doublon marque apparait (vieille sauvegarde) : il doit etre retire, voiture comme moto
            HavenCars.spawn(l, cars.get(1), o);
            HavenCars.spawn(l, bikes.get(1), o);
            HavenCars.update(s);
            return true;
        });
        STEPS.add((s, l, t) -> t >= 2);
        STEPS.add((s, l, t) -> {
            for (HavenCars.Place place : List.of(cars.get(1), bikes.get(1))) {
                List<JakVehicleEntity> found = carsOf(l, place.key());
                check("doublon marque de " + place.key() + " retire", found.size() == 1
                                && found.get(0).getUUID().equals(apartmentCars.get(place.key())),
                        found.size() + " vehicule(s) apres le controle");
            }
            // Les vehicules 3 sortent de la ville PAR LE HAUT : meme troncon, toujours
            // charge. Envoyee a x = -40, dans un troncon non charge, une voiture y
            // devenait invisible au niveau et le controle en posait une autre (premier passage).
            for (HavenCars.Place place : List.of(cars.get(2), bikes.get(2))) {
                JakVehicleEntity third = onlyCar(l, place.key());
                if (third != null) {
                    third.moveTo(third.getX(), o.getY() + state.height() + 5.0, third.getZ(), third.getYRot(), 0.0F);
                }
            }
            // les vehicules 1 disparaissent (une repose les aurait retires) : ils doivent etre remplaces
            for (HavenCars.Place place : List.of(cars.get(0), bikes.get(0))) {
                JakVehicleEntity first = onlyCar(l, place.key());
                if (first != null) {
                    first.discard();
                }
            }
            HavenCars.update(s);
            return true;
        });
        STEPS.add((s, l, t) -> t >= 2);
        STEPS.add((s, l, t) -> {
            for (HavenCars.Place place : List.of(cars.get(2), bikes.get(2))) {
                JakVehicleEntity third = onlyCar(l, place.key());
                double gap = third == null ? Double.NaN : Math.hypot(HavenCars.modelCenter(third).x
                        - (o.getX() + place.floor().getX() + 0.5), HavenCars.modelCenter(third).z
                        - (o.getZ() + place.floor().getZ() + 0.5));
                check(place.model() + " sortie de la ville ramenee sur sa place", third != null && gap < 0.6
                                && third.getUUID().equals(apartmentCars.get(place.key())),
                        third == null ? "introuvable" : String.format(Locale.ROOT, "a %.2f bloc de sa place", gap));
            }
            for (HavenCars.Place place : List.of(cars.get(0), bikes.get(0))) {
                List<JakVehicleEntity> firsts = carsOf(l, place.key());
                check(place.model() + " disparue remplacee une fois", firsts.size() == 1
                                && !firsts.get(0).getUUID().equals(apartmentCars.get(place.key())),
                        firsts.size() + " vehicule(s), identifiant nouveau : " + (firsts.size() == 1
                                && !firsts.get(0).getUUID().equals(apartmentCars.get(place.key()))));
            }
            // le depart : tous les vehicules de la ville retires
            int removed = HavenCars.removeAll(l);
            int left = HavenCars.loadedCars(l).size();
            check("depart : tous les vehicules charges de la ville retires", left == 0 && removed >= places.size(),
                    removed + " retires, " + left + " restants");
            // la phase reste ACCUEIL dans ce monde d'essai : on les rend pour la suite
            HavenCars.update(s);
            return true;
        });
        STEPS.add((s, l, t) -> t >= 2);
        STEPS.add((s, l, t) -> {
            Map<String, Integer> counts = countByRoom(l);
            boolean ok = places.stream().allMatch(p -> counts.getOrDefault(p.key(), 0) == 1)
                    && counts.size() == places.size();
            check("apres le depart simule, le controle rend un vehicule par place, voitures et motos (phase ACCUEIL)",
                    ok, counts.toString());
            return true;
        });
    }

    /**
     * La moto d'un appartement, garee a cote de sa voiture : ses boites sur la
     * place sont dans l'air, son modele tient dans la place de haven_rooms.json,
     * rien ne touche la voiture ni le passage devant la porte -- sur la place, et
     * la ou les deux vehicules planent maintenant.
     */
    private static void bikeParking(MinecraftServer server, ServerLevel level, BlockPos o,
                                    HavenCars.Place bikePlace, HavenCars.Place carPlace) {
        HavenRooms.Room room = null;
        HavenRooms.Data data = HavenRooms.get(server);
        if (data != null) {
            for (HavenRooms.Room candidate : data.rooms()) {
                if (candidate.id().equals(bikePlace.room())) {
                    room = candidate;
                }
            }
        }
        JakVehicleEntity bike = onlyCar(level, bikePlace.key());
        JakVehicleEntity carOfRoom = onlyCar(level, carPlace.key());
        if (room == null || room.bike() == null || room.door() == null || bike == null || carOfRoom == null) {
            check("moto de " + bikePlace.room() + " garee sans gene", false, "salle, place, porte, moto ou voiture"
                    + " introuvable : salle " + room + ", moto " + bike + ", voiture " + carOfRoom);
            return;
        }
        VehicleSpec spec = VehicleSpec.of(bikePlace.model());
        Vec3 home = HavenCars.placeOrigin(bikePlace, o);
        List<AABB> parked = JakVehicleEntity.boxesAt(spec, home.x, home.y, home.z, bikePlace.yaw());
        Vec3 carHome = HavenCars.placeOrigin(carPlace, o);
        List<AABB> carParked = JakVehicleEntity.boxesAt(VehicleSpec.of(carPlace.model()), carHome.x, carHome.y,
                carHome.z, carPlace.yaw());
        HavenRooms.Box cells = room.bike().place();
        AABB placeBox = new AABB(o.getX() + cells.min().getX(), o.getY() + cells.min().getY(), o.getZ() + cells.min().getZ(),
                o.getX() + cells.max().getX() + 1, o.getY() + cells.max().getY() + 1, o.getZ() + cells.max().getZ() + 1);
        HavenRooms.Box door = room.door();
        AABB passage = new AABB(o.getX() + door.min().getX() - DOOR_PASSAGE, o.getY() + door.min().getY(),
                o.getZ() + door.min().getZ(), o.getX() + door.max().getX() + 1, o.getY() + door.max().getY() + 1,
                o.getZ() + door.max().getZ() + 1);
        AABB model = footprint(spec, home, bikePlace.yaw());

        boolean airParked = parked.stream().noneMatch(box -> blocksIn(level, box));
        boolean inPlace = model.minX >= placeBox.minX - 1.0E-6 && model.maxX <= placeBox.maxX + 1.0E-6
                && model.minZ >= placeBox.minZ - 1.0E-6 && model.maxZ <= placeBox.maxZ + 1.0E-6
                && model.minY >= placeBox.minY - 1.0E-6;
        List<AABB> live = bike.collisionBoxes();
        List<AABB> carLive = carOfRoom.collisionBoxes();
        boolean apartParked = !touches(parked, carParked);
        boolean apartLive = !touches(live, carLive);
        boolean doorFree = parked.stream().noneMatch(passage::intersects) && live.stream().noneMatch(passage::intersects)
                && !model.intersects(passage);
        boolean airLive = !insideBlocks(bike);
        double hover = height(bike);
        // la hauteur n'est que rapportee : sous la place, le sol de l'appartement
        // n'est pas connu d'avance ; elle est jugee dans la rue du bar (planHover)
        check("moto de " + bikePlace.room() + " (" + bikePlace.model() + ") garee a cote de la voiture, sans gene",
                airParked && inPlace && apartParked && apartLive && doorFree && airLive,
                String.format(Locale.ROOT, "place x %d..%d z %d..%d ; boites sur la place dans l'air %b, modele dans la"
                                + " place %b (emprise x %.2f..%.2f z %.2f..%.2f) ; a l'ecart de la voiture : garees %b"
                                + " (%.2f bloc), en vol %b (%.2f bloc) ; passage de la porte libre %b ; en vol hors des"
                                + " blocs %b, propulseurs a %.3f du sol (%.3f calcules sans pilote)",
                        cells.min().getX(), cells.max().getX(), cells.min().getZ(), cells.max().getZ(), airParked,
                        inPlace, model.minX, model.maxX, model.minZ, model.maxZ, apartParked, separation(parked, carParked),
                        apartLive, separation(live, carLive), doorFree, airLive, hover, spec.equilibriumDistance(false)));
    }

    // ------------------------------------------------------------- rue du bar

    private static void planStreet(HavenState state, BlockPos street) {
        planStreetSpawn(street, "cara");
        planHover(120, false);
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
        planHover(120, true);
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
        planStreetCleanup();
    }

    /** Pose {@code model} au sol sur une place libre de la rue du bar. */
    private static void planStreetSpawn(BlockPos street, String model) {
        STEPS.add((s, l, t) -> {
            JakVehicleEntity probe = Jak3Registry.JAK_VEHICLE.get().create(l);
            if (probe == null) {
                check("creation d'un vehicule", false, "EntityType.create a rendu null");
                return true;
            }
            probe.setModel(model);
            if (!findStreetSpot(l, probe, street)) {
                check("place libre pour " + model + " dans la rue du bar", false,
                        "aucune colonne de rue (Y 71) degagee a 16 blocs de la cellule (375, 66, 211)");
                return true;
            }
            VehicleSpec spec = probe.spec();
            car = probe;
            car.moveTo(street0X, streetGround - spec.minY, street0Z, streetYaw, 0.0F);
            l.addFreshEntity(car);
            SPAWNED.add(car);
            samples.clear();
            line(String.format(Locale.ROOT, "rue du bar : %s posee au sol en (%.1f ; %.2f ; %.1f), lacet %.0f,"
                            + " sol en Y %.2f ; voie haute degagee au-dessus : %s", model, street0X, car.getY(), street0Z,
                    streetYaw, streetGround, streetHigh));
            check(model + " sans gravite vanilla (pas d'expulsion pour vol sur serveur dedie)",
                    car.isNoGravity() && car.getGravity() == 0.0,
                    "isNoGravity " + car.isNoGravity() + ", getGravity " + car.getGravity());
            return true;
        });
    }

    /**
     * Rase-sol sans entree : {@code ticks} ticks, puis la derniere seconde a
     * +-0,2 de la hauteur calculee, sans pilote ou avec le poids d'un pilote.
     *
     * Sans pilote, le vehicule vient d'etre pose au sol. Pilote, il part de sa
     * hauteur a vide, apres la redescente, et s'enfonce jusqu'a sa hauteur
     * pilotee. Le serveur d'essai n'a pas de joueur a asseoir : le poids est
     * impose (JakVehicleEntity.setAutotestDriver) le temps de l'etape.
     *
     * Banc hors jeu (loi du mod, pose au sol, seconde de 5 a 6 s) : toutes les
     * hauteurs a 0,005 de leur calcul -- motos a vide 3,334 a 3,335 pour 3,333,
     * pilotees 2,501 a 2,505 pour 2,5 ; voitures 3,406 a vide, pilotees 3,270,
     * 3,224 et 3,285. Quand le portage amortissait apres la gravite, la moto
     * pilotee restait vers 3,1 : ce controle l'aurait refusee.
     */
    private static void planHover(int ticks, boolean driver) {
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (t == 0) {
                car.setAutotestDriver(driver);
                samples.clear();
            }
            double d = height(car);
            if (t % 20 == 0) {
                line(String.format(Locale.ROOT, "  rase-sol %s%s t=%d : propulseurs a %.3f du sol, vy %.3f", car.model(),
                        driver ? " pilotee" : "", t, d, car.getDeltaMovement().y));
            }
            if (t >= ticks - 20) {
                samples.add(d);
            }
            if (t < ticks) {
                return false;
            }
            car.setAutotestDriver(false);
            VehicleSpec spec = car.spec();
            double target = spec.equilibriumDistance(driver);
            double min = samples.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
            double max = samples.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
            check(car.model() + (driver ? " : rase-sol pilote (poids du pilote simule) stable a +-0,2 de la hauteur visee"
                            : " : rase-sol sans entree stable a +-0,2 de la hauteur visee"),
                    Math.abs(min - target) <= 0.2 && Math.abs(max - target) <= 0.2,
                    String.format(Locale.ROOT, "visee %.3f (calcul, %s : masse %.0f, ressort %.2f, sonde %.1f),"
                                    + " mesuree %.3f a %.3f sur la derniere seconde", target,
                            driver ? "pilote d'une masse de vehicule" : "sans pilote", spec.mass, spec.springLift,
                            spec.probeDistance, min, max));
            return true;
        });
    }

    private static void planStreetCleanup() {
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
        for (AABB box : JakVehicleEntity.boxesAt(probe.spec(), x, y, z, yaw)) {
            if (!level.noCollision(null, box)) {
                return false;
            }
        }
        return true;
    }

    private static void seats(ServerLevel level, JakVehicleEntity vehicle) {
        VehicleSpec spec = vehicle.spec();
        if (!spawnStands(level, vehicle, 4)) {
            return;
        }
        StringBuilder detail = new StringBuilder();
        boolean ok = true;
        for (int i = 0; i < 3; i++) {
            ArmorStand stand = stands.get(i);
            boolean boarded = vehicle.board(stand);
            int seat = vehicle.seatOf(stand);
            double gap = seatGap(vehicle, stand, seat < 0 ? 0 : seat);
            boolean good = boarded && seat == i && gap < 1.0E-3;
            ok &= good;
            detail.append(String.format(Locale.ROOT, "passager %d : monte %b, siege %d, ecart %.5f ; ", i, boarded, seat, gap));
        }
        check("trois passagers aux sieges 0, 1, 2 et aux positions GOAL", ok && spec.seatCount() == 3, detail.toString());
        check("personne ne conduit sans joueur a la place 0", vehicle.getControllingPassenger() == null,
                "conducteur " + vehicle.getControllingPassenger());
        ArmorStand fourth = stands.get(3);
        check("quatrieme passager refuse (trois places)", !vehicle.board(fourth),
                "passagers " + vehicle.getPassengers().size());

        dismount(level, vehicle, stands.get(1), "voiture");
        boolean again = vehicle.board(fourth);
        check("la place liberee est la premiere libre", again && vehicle.seatOf(fourth) == 1,
                "monte " + again + ", siege " + vehicle.seatOf(fourth));
    }

    /**
     * La moto monoplace : un passager a la place du pilote GOAL, un second
     * refuse, une descente au sol a cote, puis la place reprise.
     */
    private static void bikeSeat(ServerLevel level, JakVehicleEntity bike) {
        VehicleSpec spec = bike.spec();
        if (!spawnStands(level, bike, 2)) {
            return;
        }
        ArmorStand rider = stands.get(0);
        ArmorStand second = stands.get(1);
        boolean boarded = bike.board(rider);
        int seat = bike.seatOf(rider);
        double gap = seatGap(bike, rider, 0);
        check(spec.model + " monoplace : le passager prend le siege 0, a la position du pilote GOAL",
                spec.seatCount() == 1 && boarded && seat == 0 && gap < 1.0E-3,
                String.format(Locale.ROOT, "places %d, monte %b, siege %d, ecart %.5f au siege (%.3f ; %.3f ; %.3f)",
                        spec.seatCount(), boarded, seat, gap, spec.seat(0, 0), spec.seat(0, 1), spec.seat(0, 2)));
        boolean refused = !bike.board(second);
        check(spec.model + " monoplace : second passager refuse", refused && bike.getPassengers().size() == 1
                        && !second.isPassenger(),
                "monte " + !refused + ", passagers " + bike.getPassengers().size());
        check(spec.model + " : personne ne conduit sans joueur", bike.getControllingPassenger() == null,
                "conducteur " + bike.getControllingPassenger());
        dismount(level, bike, rider, spec.model);
        boolean again = bike.board(second);
        check(spec.model + " : la place liberee se reprend", again && bike.seatOf(second) == 0,
                "monte " + again + ", siege " + bike.seatOf(second));
    }

    private static boolean spawnStands(ServerLevel level, JakVehicleEntity vehicle, int count) {
        for (int i = 0; i < count; i++) {
            ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            if (stand == null) {
                check("creation des porte-armures", false, "EntityType.create a rendu null");
                return false;
            }
            stand.moveTo(vehicle.getX() + 8.0, streetGround, vehicle.getZ(), 0.0F, 0.0F);
            level.addFreshEntity(stand);
            stands.add(stand);
            SPAWNED.add(stand);
        }
        return true;
    }

    /** L'ecart entre le point d'attache d'un passager et son siege GOAL, dans le monde. */
    private static double seatGap(JakVehicleEntity vehicle, ArmorStand stand, int seat) {
        VehicleSpec spec = vehicle.spec();
        vehicle.positionRider(stand);
        double yaw = Math.toRadians(vehicle.getYRot());
        double wx = vehicle.getX() + spec.seat(seat, 0) * Math.cos(yaw) - spec.seat(seat, 2) * Math.sin(yaw);
        double wy = vehicle.getY() + spec.seat(seat, 1);
        double wz = vehicle.getZ() + spec.seat(seat, 2) * Math.cos(yaw) + spec.seat(seat, 0) * Math.sin(yaw);
        Vec3 riding = stand.position().add(stand.getVehicleAttachmentPoint(vehicle));
        return riding.distanceTo(new Vec3(wx, wy, wz));
    }

    private static void dismount(ServerLevel level, JakVehicleEntity vehicle, ArmorStand leaving, String what) {
        leaving.stopRiding();
        AABB box = leaving.getBoundingBox();
        boolean clear = true;
        for (AABB part : vehicle.collisionBoxes()) {
            clear &= !part.intersects(box);
        }
        boolean floor = !level.noCollision(null, box.move(0.0, -0.1, 0.0).setMaxY(box.minY));
        double away = Math.hypot(leaving.getX() - vehicle.getX(), leaving.getZ() - vehicle.getZ());
        check("descente a cote de la " + what + ", hors de ses boites, sur un sol",
                !leaving.isPassenger() && clear && floor && level.noCollision(leaving, box) && away < 8.0,
                String.format(Locale.ROOT, "pose en %s, a %.2f blocs de l'origine, hors des boites %b, sol dessous %b",
                        fmt(leaving.position()), away, clear, floor));
    }

    // ------------------------------------------------------------- en mer

    private static void planSea(HavenState state) {
        planSeaSpawn("cara");
        planTopSpeed();
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
        planSeaCleanup();
    }

    /** Pose {@code model} a sa hauteur de rase-sol au-dessus de la mer, l'avant vers le large. */
    private static void planSeaSpawn(String model) {
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = Jak3Registry.JAK_VEHICLE.get().create(l);
            if (c == null) {
                return true;
            }
            c.setModel(model);
            double water = groundBelow(l, sea0X, 90.0, sea0Z, 40.0);
            double y = (Double.isNaN(water) ? 63.0 : water) + c.spec().equilibriumDistance(false) - c.spec().thrusterY;
            // lacet -90 : l'avant regarde +X, vers le large
            seaHoverY = y;
            c.moveTo(sea0X, y, sea0Z, -90.0F, 0.0F);
            l.addFreshEntity(c);
            SPAWNED.add(c);
            car = c;
            line(String.format(Locale.ROOT, "mer : %s posee en (%.1f ; %.2f ; %.1f), surface de l'eau en Y %.2f",
                    model, sea0X, y, sea0Z, water));
            return true;
        });
    }

    /** Plane au-dessus de l'eau, gaz a fond dix secondes, puis frein jusqu'a l'arret. */
    private static void planTopSpeed() {
        STEPS.add((s, l, t) -> {
            if (t < 60) {
                return false;
            }
            double d = height(car);
            check(car.model() + " plane au-dessus de l'eau (les sondes voient l'eau, comme dans le jeu)",
                    Math.abs(d - car.spec().equilibriumDistance(false)) < 0.3 && !car.isInWater(),
                    String.format(Locale.ROOT, "propulseurs a %.3f de la surface (visee %.3f), dans l'eau : %b", d,
                            car.spec().equilibriumDistance(false), car.isInWater()));
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
            check(car.model() + " gaz a fond 10 s : vitesse maximale atteinte sans la depasser de plus de 5 %",
                    reach95 >= 0 && maxSpeed <= 1.05 * vmax,
                    String.format(Locale.ROOT, "vitesse max mesuree %.4f bloc/tick (%.2f m/s) pour %.2f reglee ;"
                                    + " 95 %% atteints au tick %d ; parcouru jusqu'en X %.1f",
                            maxSpeed, maxSpeed * 20.0, vmax, reach95, car.getX()));
            check(car.model() + " : vitesse de Jak 3, 40 m/s, soit deux blocs par tick (max-xz-speed, car.gc:96, bike.gc:110)",
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
            check(car.model() + " : frein, le vehicule s'arrete", marker >= 0,
                    marker >= 0 ? "vitesse avant sous 0,02 bloc/tick apres " + marker + " ticks (frein "
                            + String.format(Locale.ROOT, "%.0f m/s2", 24.0 * car.spec().brakeFactor) + ")"
                            : "vitesse avant " + forward);
            yawStart = 0.0;
            car.setAutotestInput(new VehicleDynamics.Input(false, false, 1));
            return true;
        });
    }

    private static void planSeaCleanup() {
        STEPS.add((s, l, t) -> {
            if (car != null) {
                car.setAutotestInput(null);
                car.discard();
                car = null;
            }
            return true;
        });
    }

    // ------------------------------------------------------------- le pont entre les tours

    /**
     * La voie haute au-dessus du pont entre les deux tours, gaz a fond.
     *
     * Le joueur : « la hauteur max n'est pas assez haute vers les ponts entre les deux
     * tours ». La voie plate tenait la voiture pilotee propulseurs a la cellule 75,0,
     * dessous vers 73 : le tablier du pont, qui monte a 75, l'arretait de cote. Le jeu
     * releve sa carte de trafic a cet endroit, de 10 blocs, et la voie du joueur la suit.
     *
     * Le vehicule part sur l'eau, monte en voie haute, puis traverse le couloir a
     * pleine vitesse : jamais ralenti, jamais dans un bloc, toujours en voie haute, et
     * deux blocs au moins entre ses boites et le tablier. Du sud au nord, il finit au
     * large du bassin, ou la voie doit etre revenue a la cellule 75,0.
     */
    private static void planBridge(String model, boolean southward) {
        String run = model + (southward ? ", du nord au sud" : ", du sud au nord");
        STEPS.add((s, l, t) -> {
            BlockPos o = HavenState.get(s).origin();
            JakVehicleEntity c = Jak3Registry.JAK_VEHICLE.get().create(l);
            if (c == null) {
                check("pont (" + run + ") : creation du vehicule", false, "EntityType.create a rendu null");
                return true;
            }
            c.setModel(model);
            VehicleSpec spec = c.spec();
            double x = o.getX() + BRIDGE_X;
            double z = o.getZ() + (southward ? BRIDGE_NORTH_Z : BRIDGE_SOUTH_Z);
            double water = groundBelow(l, x, o.getY() + 72.0, z, 30.0);
            double y = (Double.isNaN(water) ? o.getY() + 58.0 : water) + spec.equilibriumDistance(true) - spec.thrusterY;
            // lacet 0 : l'avant regarde +Z, vers le sud
            c.moveTo(x, y, z, southward ? 0.0F : 180.0F, 0.0F);
            l.addFreshEntity(c);
            SPAWNED.add(c);
            c.setAutotestDriver(true);
            car = c;
            line(String.format(Locale.ROOT, "pont (%s) : pose en cellule (%.1f ; %.2f ; %.1f), eau en Y %.2f ; carte de trafic"
                            + " ici en cellule %.2f, au-dessus du tablier %.2f", run, BRIDGE_X, y - o.getY(), z - o.getZ(), water,
                    VehiclePhysics.havenTrafficY(x, z) - o.getY(),
                    VehiclePhysics.havenTrafficY(x, o.getZ() + 0.5 * (BRIDGE_DECK_NORTH_Z + BRIDGE_DECK_SOUTH_Z)) - o.getY()));
            return true;
        });
        // la montee, puis les gaz
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (t < 40) {
                return false;
            }
            if (t == 40) {
                car.toggleMode();
                marker = -1;
                return false;
            }
            if (marker < 0 && car.mode() == VehicleDynamics.MODE_HAUT) {
                marker = t - 40;
            }
            if (t < 140) {
                return false;
            }
            check("pont (" + run + ") : montee finie en voie haute avant 2 s",
                    car.mode() == VehicleDynamics.MODE_HAUT && marker > 0 && marker <= VehicleDynamics.TRANSITION_TICKS,
                    "voie haute au tick " + marker + ", mode " + car.mode());
            bridgeClearance = Double.MAX_VALUE;
            bridgeSlowest = Double.MAX_VALUE;
            bridgePeakCell = -Double.MAX_VALUE;
            bridgeInside = false;
            bridgeHigh = true;
            reach95 = -1;
            car.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            return true;
        });
        // la traversee
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            BlockPos o = HavenState.get(s).origin();
            VehicleSpec spec = car.spec();
            double cz = car.getZ() - o.getZ();
            double speed = horizontalSpeed(car);
            if (reach95 < 0) {
                if (speed >= 0.95 * spec.maxSpeed) {
                    reach95 = t;
                }
            } else {
                bridgeSlowest = Math.min(bridgeSlowest, speed);
            }
            bridgeInside |= insideBlocks(car);
            bridgeHigh &= car.mode() == VehicleDynamics.MODE_HAUT;
            for (AABB box : car.collisionBoxes()) {
                if (box.maxZ - o.getZ() >= BRIDGE_DECK_NORTH_Z && box.minZ - o.getZ() <= BRIDGE_DECK_SOUTH_Z) {
                    bridgeClearance = Math.min(bridgeClearance, box.minY - (o.getY() + BRIDGE_DECK_TOP));
                    bridgePeakCell = Math.max(bridgePeakCell, car.getY() + spec.thrusterY - o.getY());
                }
            }
            if (t % 20 == 0) {
                line(String.format(Locale.ROOT, "  pont t=%d : cellule z %.1f, propulseurs en cellule %.2f, carte %.2f, %.2f bloc/tick",
                        t, cz, car.getY() + spec.thrusterY - o.getY(),
                        VehiclePhysics.trafficY(car) - o.getY(), speed));
            }
            boolean across = southward ? cz >= BRIDGE_SOUTH_Z - 8.0 : cz <= BRIDGE_BACK_Z;
            if (!across && t < 600) {
                return false;
            }
            check("pont (" + run + ") : franchi en voie haute a pleine vitesse, sans ralentir ni toucher",
                    across && bridgeHigh && !bridgeInside && reach95 >= 0 && bridgeSlowest >= 0.95 * spec.maxSpeed,
                    String.format(Locale.ROOT, "arrive en cellule z %.1f au tick %d ; 95 %% de la vitesse au tick %d, puis jamais"
                                    + " sous %.3f bloc/tick (reglee %.2f) ; toujours en voie haute %b ; boite dans un bloc %b",
                            cz, t, reach95, bridgeSlowest, spec.maxSpeed, bridgeHigh, bridgeInside));
            check("pont (" + run + ") : deux blocs au moins entre le vehicule et le tablier",
                    bridgeClearance >= 2.0 && bridgeClearance < Double.MAX_VALUE,
                    String.format(Locale.ROOT, "au plus pres, %.2f blocs entre le dessous des boites et le dessus du tablier"
                                    + " (cellule %.1f) ; propulseurs jusqu'en cellule %.2f au-dessus du pont, carte a %.2f",
                            bridgeClearance, BRIDGE_DECK_TOP, bridgePeakCell,
                            VehiclePhysics.havenTrafficY(o.getX() + BRIDGE_X,
                                    o.getZ() + 0.5 * (BRIDGE_DECK_NORTH_Z + BRIDGE_DECK_SOUTH_Z)) - o.getY()));
            car.setAutotestInput(VehicleDynamics.Input.NONE);
            return true;
        });
        if (!southward) {
            // au large du bassin, la carte est a sa base : la voie est revenue a la cellule 75,0
            STEPS.add((s, l, t) -> {
                if (car == null) {
                    return true;
                }
                if (t < 120) {
                    return false;
                }
                BlockPos o = HavenState.get(s).origin();
                VehicleSpec spec = car.spec();
                double cell = car.getY() + spec.thrusterY - o.getY();
                double hang = VehiclePhysics.floorY(car) - (car.getY() + spec.thrusterY);
                double wanted = VehiclePhysics.HAVEN_TRAFFIC_CELL + VehiclePhysics.FLOOR_ABOVE_TRAFFIC
                        - VehicleDynamics.highHang(spec, true);
                check("pont (" + run + ") : passe le pont, la voie haute revient a sa hauteur d'avant (cellule 75,0)",
                        car.mode() == VehicleDynamics.MODE_HAUT && Math.abs(cell - wanted) <= 0.5
                                && Math.abs(VehiclePhysics.trafficY(car) - o.getY() - VehiclePhysics.HAVEN_TRAFFIC_CELL) < 1.0E-6,
                        String.format(Locale.ROOT, "en cellule z %.1f : propulseurs a la cellule %.3f pour %.3f calcules (pilote),"
                                        + " %.3f sous le plancher, carte a %.3f, mode %d", car.getZ() - o.getZ(), cell, wanted, hang,
                                VehiclePhysics.trafficY(car) - o.getY(), car.mode()));
                return true;
            });
        }
        STEPS.add((s, l, t) -> {
            if (car != null) {
                car.setAutotestInput(null);
                car.setAutotestDriver(false);
                car.discard();
                car = null;
            }
            return true;
        });
    }

    // ------------------------------------------------------------- les chocs

    /**
     * Les chocs, en pleine mer : vehicule contre vehicule, le poids qui decide, un
     * monstre renverse, un habitant seulement bouscule, et le mur qui s'entend.
     *
     * Les deux vehicules sont simules par le serveur (aucun conducteur) : chacun
     * calcule sa part du choc dans sa propre tique, comme le client du conducteur et
     * le serveur le font en jeu.
     */
    private static void planCollisions() {
        // 1. une voiture lancee dans une voiture a l'arret
        planPair("cara", "cara", 40.0);
        STEPS.add((s, l, t) -> chase(l, t, "cara contre cara",
                (fast, slow) -> {
                    check("choc : la voiture percutee est poussee dans le sens du choc, celle qui percute est freinee",
                            slow.getDeltaMovement().x > 0.15 && fast.getDeltaMovement().x < hitSpeed * 0.85,
                            String.format(Locale.ROOT, "avant le choc %.3f ; apres : percutante %.3f, percutee %.3f bloc/tick",
                                    hitSpeed, fast.getDeltaMovement().x, slow.getDeltaMovement().x));
                    check("choc : masses egales, restitution 0,4 -- la percutee prend a peu pres 0,7 de la vitesse d'approche",
                            Math.abs(slow.getDeltaMovement().x - 0.7 * hitSpeed) < 0.25 * hitSpeed,
                            String.format(Locale.ROOT, "percutee %.3f pour %.3f attendus", slow.getDeltaMovement().x,
                                    0.7 * hitSpeed));
                }));
        planCollisionCleanup();

        // 2. une moto lancee dans une voiture : le leger part, le lourd bouge a peine
        planPair("bikea", "carc", 40.0);
        STEPS.add((s, l, t) -> chase(l, t, "bikea contre carc",
                (fast, slow) -> {
                    double mine = fast.getDeltaMovement().x;
                    double theirs = slow.getDeltaMovement().x;
                    check("choc : la moto (masse 2) contre la car-c (masse 9) -- la moto est renvoyee, la voiture bouge a peine",
                            mine < 0.0 && theirs > 0.05 && theirs < 0.45 * hitSpeed,
                            String.format(Locale.ROOT, "approche %.3f ; moto %.3f (renvoyee), voiture %.3f bloc/tick",
                                    hitSpeed, mine, theirs));
                }));
        planCollisionCleanup();

        // 3. renverser : un monstre prend le choc, un habitant est seulement bouscule
        STEPS.add((s, l, t) -> {
            BlockPos o = HavenState.get(s).origin();
            JakVehicleEntity c = Jak3Registry.JAK_VEHICLE.get().create(l);
            if (c == null) {
                return true;
            }
            c.setModel("cara");
            VehicleSpec spec = c.spec();
            double x = o.getX() + RAM_CELL_X;
            double z = o.getZ() + RAM_CELL_Z + 0.5;
            double y = VehiclePhysics.havenTrafficY(x, z) + VehiclePhysics.FLOOR_ABOVE_TRAFFIC
                    - VehicleDynamics.highHang(spec, false) - spec.thrusterY;
            c.moveTo(x, y, z, -90.0F, 0.0F);    // lacet -90 : l'avant regarde +X
            c.setMode(VehicleDynamics.MODE_HAUT);
            l.addFreshEntity(c);
            SPAWNED.add(c);
            car = c;
            // le monstre juge les degats ; l'habitant, plus loin sur la meme ligne, juge la
            // projection : il survit au choc et garde donc la vitesse recue (un monstre mort
            // la perd dans la tique meme, avant que le banc ne regarde)
            Vec3 ahead = new Vec3(c.getX() + 20.0, c.getY(), c.getZ());
            Mob zombie = HavenSpawner.spawnMonster(l, HavenInvasion.Kind.ZOMBIE, ahead, true);
            Villager villager = HavenSpawner.spawnVillager(l, VillagerType.PLAINS,
                    new Vec3(c.getX() + 34.0, c.getY(), c.getZ()));
            for (Mob mob : new Mob[]{zombie, villager}) {
                if (mob != null) {
                    mob.setNoAi(true);
                    mob.setNoGravity(true);
                    mob.setDeltaMovement(Vec3.ZERO);
                    SPAWNED.add(mob);
                }
            }
            rammed = zombie;
            bystander = villager;
            rammedHealth = zombie == null ? -1.0F : zombie.getHealth();
            bystanderHealth = villager == null ? -1.0F : villager.getHealth();
            rammedLaunch = 0.0;
            rammedHurt = false;
            ramsBefore = VehicleImpacts.rams();
            c.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null || rammed == null) {
                return true;
            }
            // la vitesse donnee au monstre se releve a la tique du choc : mort, il la perd aussitot
            rammedHurt |= !rammed.isAlive() || rammed.getHealth() < rammedHealth;
            if (bystander != null) {
                rammedLaunch = Math.max(rammedLaunch, bystander.getDeltaMovement().x);
            }
            if (t < 90 && (bystander == null || car.getX() < bystander.getX() + 3.0)) {
                return false;
            }
            check("renversement : le monstre prend le choc (masse x vitesse) et perd sa vie",
                    rammedHurt && !rammed.isRemoved(), String.format(Locale.ROOT, "PV %.1f -> %.1f, retire %b",
                            rammedHealth, rammed.isAlive() ? rammed.getHealth() : 0.0F, rammed.isRemoved()));
            check("renversement : l'habitant de la ville paisible est renverse lui aussi, comme dans Jak 3 : projete"
                            + " devant le vehicule, et il y laisse sa vie",
                    VehicleImpacts.rams() >= ramsBefore + 2 && VehicleImpacts.lastLaunch() > 0.3 && bystander != null
                            && (!bystander.isAlive() || bystander.getHealth() < bystanderHealth),
                    String.format(Locale.ROOT, "%d renverse(s), derniere projection %.2f bloc/tick ; habitant vu a %.2f,"
                                    + " PV %.1f -> %.1f", VehicleImpacts.rams() - ramsBefore, VehicleImpacts.lastLaunch(),
                            rammedLaunch, bystanderHealth,
                            bystander != null && bystander.isAlive() ? bystander.getHealth() : 0.0F));
            car.setAutotestInput(null);
            return true;
        });
        planCollisionCleanup();

        // 4. le mur : la vitesse perdue d'un coup s'entend
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = spawnSea("cara", 0.0);
            if (c == null) {
                return true;
            }
            car = c;
            crashesBefore = VehicleImpacts.crashes();
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos p = BlockPos.containing(c.getX() + 30.0, c.getY() + dy, c.getZ() + dz);
                    l.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    PLACED.add(p);
                }
            }
            c.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (t < 80) {
                return false;
            }
            check("choc contre un mur : la vitesse perdue d'un coup est reconnue comme un choc (son et eclats)",
                    VehicleImpacts.crashes() > crashesBefore,
                    "chocs comptes " + crashesBefore + " -> " + VehicleImpacts.crashes());
            // et il abime la voiture, cinq ou dix points de Jak sur vingt-quatre (cahier §98), sans la detruire
            float lost = (1.0F - car.health()) * VehicleDamage.CAR_HIT_POINTS;
            check("choc contre un mur : la voiture perd cinq a dix points de Jak (paliers du jeu), et reste entiere",
                    lost >= VehicleDamage.SMALL_POINTS - 0.01F && lost <= 2.0F * VehicleDamage.BIG_POINTS + 0.5F
                            && car.health() > 0.0F,
                    String.format(Locale.ROOT, "sante %.3f, %.2f points perdus, etat %s", car.health(), lost, car.state()));
            car.setAutotestInput(null);
            return true;
        });
        STEPS.add((s, l, t) -> {
            for (BlockPos p : PLACED) {
                l.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            PLACED.clear();
            return true;
        });
        planCollisionCleanup();
    }

    // ------------------------------------------------------------- l'equilibre

    /**
     * L'equilibre des vehicules (VehicleAttitude, retour du joueur du 21 sept.) : le poids
     * du pilote qui couche le vehicule dans le virage, le choc de flanc qui le fait giter,
     * le mur de face qui le fait piquer du nez, le mur de biais qui le fait vriller, le
     * souffle d'une explosion ; chaque fois, le retour a plat. Tout en mer, simule par le
     * serveur ; les angles se lisent dans l'equilibre simule (car.attitude()).
     */
    private static void planBalance() {
        // 1. le poids du pilote dans un virage a gauche, volant a fond, puis lache
        for (String model : new String[]{"cara", "bikea"}) {
            double[] bank = new double[2];            // 0 le plus bas, 1 tenu
            STEPS.add((s, l, t) -> {
                JakVehicleEntity c = spawnSea(model, 0.0);
                if (c == null) {
                    return true;
                }
                car = c;
                c.setAutotestDriver(true);
                c.setAutotestInput(new VehicleDynamics.Input(true, false, 1));
                bank[0] = 0.0;
                return true;
            });
            STEPS.add((s, l, t) -> {
                if (car == null) {
                    return true;
                }
                double roll = Math.toDegrees(car.attitude().roll);
                bank[0] = Math.min(bank[0], roll);
                if (t < 100) {
                    return false;
                }
                bank[1] = roll;
                car.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
                return true;
            });
            STEPS.add((s, l, t) -> {
                if (car == null) {
                    return true;
                }
                if (t < 100) {
                    return false;
                }
                double expected = Math.toDegrees(VehicleAttitude.bankAngle(car.spec()));
                double level = Math.toDegrees(car.attitude().roll);
                check("equilibre (" + model + ") : volant a gauche, le poids du pilote couche le vehicule DANS le virage (gauche"
                                + " en bas), jusqu'ou les propulseurs de roulis le retiennent",
                        bank[1] < 0.0 && Math.abs(bank[1] + expected) < 3.0 && bank[0] > -(expected + 20.0),
                        String.format(Locale.ROOT, "roulis tenu %.1f deg pour -%.1f calcules, le plus bas %.1f",
                                bank[1], expected, bank[0]));
                check("equilibre (" + model + ") : volant lache, le vehicule revient a plat en cinq secondes",
                        Math.abs(level) < 3.0, String.format(Locale.ROOT, "roulis %.2f deg, 5 s apres", level));
                car.setAutotestInput(null);
                car.setAutotestDriver(false);
                return true;
            });
            planCollisionCleanup();
        }

        // 2. un choc de flanc : la percutee gite puis se redresse, la percutante pique du nez
        double[] side = new double[5];                // 0 roulis extreme, 1 tangage le plus bas, 2 tick du choc, 3 ecart publie
        STEPS.add((s, l, t) -> {
            JakVehicleEntity a = spawnSea("cara", 0.0);
            JakVehicleEntity b = spawnSea("cara", 40.0);
            if (a == null || b == null) {
                return true;
            }
            // de profil : lacet 0, l'avant regarde +Z ; a arrive de l'ouest sur son flanc droit
            b.setYRot(0.0F);
            b.syncParts();
            car = a;
            second = b;
            a.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            side[0] = 0.0;
            side[1] = 0.0;
            side[2] = -1.0;
            side[3] = 1.0;
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null || second == null) {
                return true;
            }
            if (side[2] < 0.0) {
                if (second.getDeltaMovement().x > 0.05) {
                    side[2] = t;
                    line(String.format(Locale.ROOT, "choc de flanc au tick %d : percutante a %.3f bloc/tick", t,
                            car.getDeltaMovement().x));
                    car.setAutotestInput(null);
                } else if (t > 200) {
                    check("equilibre : choc de flanc", false, "aucun choc en 10 s");
                    return true;
                }
                return false;
            }
            double roll = Math.toDegrees(second.attitude().roll);
            if (Math.abs(roll) > Math.abs(side[0])) {
                side[0] = roll;
            }
            side[1] = Math.min(side[1], Math.toDegrees(car.attitude().pitch));
            if (t == (int) side[2] + 3) {
                side[3] = Math.abs(second.publishedAttitude()[1] - second.attitude().roll);
            }
            if (t < side[2] + 140) {
                return false;
            }
            check("equilibre : heurtee de flanc, la voiture gite fort (le choc porte sous son centre de masse)",
                    Math.abs(side[0]) > 10.0,
                    String.format(Locale.ROOT, "roulis extreme %.1f deg (%s en haut)", side[0], side[0] > 0.0 ? "gauche" : "droite"));
            check("equilibre : puis ses propulseurs de roulis la remettent a plat en sept secondes",
                    Math.abs(roll) < 3.0, String.format(Locale.ROOT, "roulis %.2f deg, 7 s apres le choc", roll));
            check("equilibre : la voiture qui percute pique du nez",
                    side[1] < -2.0, String.format(Locale.ROOT, "tangage le plus bas %.1f deg", side[1]));
            check("equilibre : le serveur publie celui des vehicules qu'il simule (donnee d'entite, pour les clients)",
                    side[3] < 0.003, String.format(Locale.ROOT, "ecart publie / simule %.4f rad, 3 ticks apres le choc", side[3]));
            return true;
        });
        planCollisionCleanup();

        // 3. un mur de face : le nez pique, puis se releve
        double[] front = new double[2];               // 0 tangage le plus bas, 1 tick du choc
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = spawnSea("cara", 0.0);
            if (c == null) {
                return true;
            }
            car = c;
            wallAhead(l, c, 30.0, -3, 3);
            c.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            front[0] = 0.0;
            front[1] = -1.0;
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (front[1] < 0.0) {
                if (car.getDeltaMovement().x < 0.0) {
                    front[1] = t;
                    car.setAutotestInput(null);
                } else if (t > 200) {
                    check("equilibre : mur de face", false, "aucun choc en 10 s");
                    clearWall(l);
                    return true;
                }
                return false;
            }
            front[0] = Math.min(front[0], Math.toDegrees(car.attitude().pitch));
            if (t < front[1] + 60) {
                return false;
            }
            double pitch = Math.toDegrees(car.attitude().pitch);
            check("equilibre : contre un mur de face, la voiture pique du nez (le choc porte sur sa boite avant, sous le centre de masse)",
                    front[0] < -3.0, String.format(Locale.ROOT, "tangage le plus bas %.1f deg", front[0]));
            check("equilibre : puis ses propulseurs la remettent a plat en trois secondes",
                    Math.abs(pitch) < 2.0, String.format(Locale.ROOT, "tangage %.2f deg, 3 s apres", pitch));
            clearWall(l);
            return true;
        });
        planCollisionCleanup();

        // 4. un mur aborde de biais : la voiture vrille et repart dans l'angle du rebond
        double[] glance = new double[3];              // 0 lacet au choc, 1 tick du choc, 2 vrille
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = spawnSea("cara", 0.0);
            if (c == null) {
                return true;
            }
            car = c;
            // lacet -60 : l'avant regarde (0,87 ; 0,5), vers le mur a l'est, en glissant au sud
            c.setYRot(-60.0F);
            c.syncParts();
            wallAhead(l, c, 20.0, -6, 40);
            c.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            glance[1] = -1.0;
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (glance[1] < 0.0) {
                if (car.getDeltaMovement().x < 0.0) {
                    glance[0] = car.getYRot();
                    glance[1] = t;
                    car.setAutotestInput(null);
                } else if (t > 200) {
                    check("equilibre : mur de biais", false, "aucun choc en 10 s");
                    clearWall(l);
                    return true;
                }
                return false;
            }
            if (t < glance[1] + 30) {
                return false;
            }
            glance[2] = Mth.wrapDegrees(car.getYRot() - (float) glance[0]);
            check("equilibre : un mur aborde de biais fait vriller la voiture, qui se detourne du mur (lacet qui augmente :"
                            + " vers la droite, loin du mur a l'est)",
                    glance[2] > 8.0, String.format(Locale.ROOT, "vrille %.1f deg en 1,5 s, sans volant", glance[2]));
            clearWall(l);
            return true;
        });
        planCollisionCleanup();

        // 5. le souffle d'une explosion, a trois blocs du flanc gauche
        double[] blast = new double[5];               // 0 vitesse vers la droite, 1 vitesse verticale, 2 roulis extreme, 3 touches
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = spawnSea("cara", 0.0);
            if (c == null) {
                return true;
            }
            car = c;
            blast[2] = 0.0;
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (t < 20) {
                return false;
            }
            // lacet -90 : la gauche est -Z
            Vec3 at = car.position().add(0.0, 0.0, -(car.spec().boxSide / 2.0 + 3.0));
            int before = VehicleImpacts.blasts();
            VehicleImpacts.blast(l, at, VehicleImpacts.BLAST_PLASMITE_RADIUS, VehicleImpacts.BLAST_PLASMITE);
            blast[3] = VehicleImpacts.blasts() - before;
            blast[0] = car.getDeltaMovement().z;
            blast[1] = car.getDeltaMovement().y;
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            double roll = Math.toDegrees(car.attitude().roll);
            if (Math.abs(roll) > Math.abs(blast[2])) {
                blast[2] = roll;
            }
            if (t < 140) {
                return false;
            }
            check("equilibre : le souffle d'une explosion (Plasmite RPG, a 3 blocs du flanc gauche) pousse la voiture vers sa"
                            + " droite et la souleve",
                    blast[3] == 1 && blast[0] > 0.2 && blast[1] > 0.0,
                    String.format(Locale.ROOT, "%d vehicule(s) touche(s), vitesse %.3f vers la droite, %.3f vers le haut",
                            (int) blast[3], blast[0], blast[1]));
            check("equilibre : ... la fait giter, le flanc souffle en haut, puis elle revient a plat",
                    blast[2] > 5.0 && Math.abs(roll) < 3.0,
                    String.format(Locale.ROOT, "roulis extreme %.1f deg (gauche en haut positif), %.2f deg 7 s apres", blast[2], roll));
            return true;
        });
        planCollisionCleanup();

        // 6. l'equilibre d'un conducteur client : publie tel quel, borne, et le serveur n'y touche pas
        float[][] remote = new float[3][];
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = spawnSea("cara", 0.0);
            if (c == null) {
                return true;
            }
            car = c;
            c.setAutotestRemoteDriver(true);
            c.acceptDriverAttitude(Float.NaN, 0.3F);
            remote[0] = c.publishedAttitude();
            c.acceptDriverAttitude(2.0F, -0.2F);
            remote[1] = c.publishedAttitude();
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (t < 10) {
                return false;
            }
            remote[2] = car.publishedAttitude();
            float max = (float) VehicleAttitude.MAX_TILT;
            check("equilibre : celui qu'envoie le client du conducteur est publie tel quel -- NaN refuse, 75 degres au plus --,"
                            + " et le serveur, qui ne simule pas sa voiture, n'y touche pas",
                    remote[0][0] == 0.0F && remote[0][1] == 0.0F && Math.abs(remote[1][0] - max) < 1.0E-6
                            && Math.abs(remote[1][1] + 0.2F) < 1.0E-6 && remote[2][0] == remote[1][0] && remote[2][1] == remote[1][1],
                    String.format(Locale.ROOT, "NaN : (%.3f ; %.3f) ; (2 ; -0,2) : (%.3f ; %.3f) ; 10 ticks apres : (%.3f ; %.3f)",
                            remote[0][0], remote[0][1], remote[1][0], remote[1][1], remote[2][0], remote[2][1]));
            car.setAutotestRemoteDriver(false);
            return true;
        });
        planCollisionCleanup();
    }

    /** Un mur de pierre en travers, {@code ahead} blocs a l'est du vehicule, de {@code z0} a {@code z1} blocs en Z. */
    private static void wallAhead(ServerLevel level, JakVehicleEntity c, double ahead, int z0, int z1) {
        for (int dy = -3; dy <= 3; dy++) {
            for (int dz = z0; dz <= z1; dz++) {
                BlockPos p = BlockPos.containing(c.getX() + ahead, c.getY() + dy, c.getZ() + dz);
                level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                PLACED.add(p);
            }
        }
    }

    private static void clearWall(ServerLevel level) {
        for (BlockPos p : PLACED) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        PLACED.clear();
    }

    /** Pose deux vehicules en mer : {@code chaser} lance vers {@code target}, arrete, a {@code gap} blocs. */
    private static void planPair(String chaser, String target, double gap) {
        STEPS.add((s, l, t) -> {
            JakVehicleEntity a = spawnSea(chaser, 0.0);
            JakVehicleEntity b = spawnSea(target, gap);
            if (a == null || b == null) {
                return true;
            }
            car = a;
            second = b;
            a.setAutotestInput(new VehicleDynamics.Input(true, false, 0));
            return true;
        });
    }

    /**
     * Attend le choc entre {@code car} et {@code second}, puis juge.
     *
     * Le choc se voit a la vitesse du vehicule percute : tant qu'il n'a pas bouge,
     * rien n'est arrive. La vitesse d'approche est relevee la tique d'avant.
     */
    private static boolean chase(ServerLevel level, int t, String where, java.util.function.BiConsumer<JakVehicleEntity,
            JakVehicleEntity> judge) {
        if (car == null || second == null) {
            return true;
        }
        if (t == 0) {
            hitSpeed = 0.0;
        }
        if (second.getDeltaMovement().x <= 0.02 && t < 160) {
            hitSpeed = car.getDeltaMovement().x;
            return false;
        }
        line(String.format(Locale.ROOT, "choc (%s) au tick %d : approche %.3f bloc/tick, ecart %.2f blocs", where, t,
                hitSpeed, second.getX() - car.getX()));
        judge.accept(car, second);
        car.setAutotestInput(null);
        return true;
    }

    /** Pose un vehicule en mer a {@code offsetX} blocs a l'est du point de depart. */
    private static JakVehicleEntity spawnSea(String model, double offsetX) {
        ServerLevel level = Haven.level(ServerLifecycleHooks.getCurrentServer());
        if (level == null) {
            return null;
        }
        JakVehicleEntity c = Jak3Registry.JAK_VEHICLE.get().create(level);
        if (c == null) {
            return null;
        }
        c.setModel(model);
        double water = groundBelow(level, sea0X + offsetX, 90.0, sea0Z, 40.0);
        double y = (Double.isNaN(water) ? 63.0 : water) + c.spec().equilibriumDistance(false) - c.spec().thrusterY;
        // lacet -90 : l'avant regarde +X
        c.moveTo(sea0X + offsetX, y, sea0Z, -90.0F, 0.0F);
        level.addFreshEntity(c);
        SPAWNED.add(c);
        return c;
    }

    // ------------------------------------------------------------- degats (cahier §98)

    private static JakVehicleEntity doomed;
    private static JakVehicleEntity neighbour;
    private static Mob blastVictim;
    private static float blastVictimHealth;
    private static ArmorStand doomedRider;
    private static int explosionsBefore;

    /**
     * La sante des vehicules : les etats, un tir, un rayon, les coups de poing, les paliers des
     * chocs, la destruction -- l'occupant qui saute, l'explosion qui blesse un monstre et abime la
     * voiture d'a cote, l'epave qui disparait --, la grenade Plasmite, et la sauvegarde.
     */
    private static void planDamage() {
        STEPS.add((s, l, t) -> {
            check("degats : les etats -- parfait a 1, bon a 0,8, moyen a 0,6, mal a 0,3, detruit a 0",
                    VehicleDamage.State.of(1.0F) == VehicleDamage.State.PARFAIT
                            && VehicleDamage.State.of(0.8F) == VehicleDamage.State.BON
                            && VehicleDamage.State.of(0.6F) == VehicleDamage.State.MOYEN
                            && VehicleDamage.State.of(0.3F) == VehicleDamage.State.MAL
                            && VehicleDamage.State.of(0.0F) == VehicleDamage.State.DETRUIT,
                    "VehicleDamage.State.of");
            JakVehicleEntity c = spawnSea("cara", 60.0);
            JakVehicleEntity d = spawnSea("carb", 90.0);
            if (c == null || d == null) {
                return true;
            }
            doomed = c;
            neighbour = d;
            net.neoforged.neoforge.common.util.FakePlayer f = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(l);
            // un tir de Blaster sur une des boites de la voiture : deux points de Jak sur vingt-quatre
            boolean shot = com.emerald.jak.gun.GunImpacts.hurt(f, null, (Entity) c.getParts()[1],
                    com.emerald.jak.gun.GunSpec.BLASTER_DAMAGE);
            float afterShot = c.health();
            check("degats : un tir de Blaster sur une boite de la voiture lui retire deux points de Jak sur vingt-quatre",
                    shot && Math.abs(afterShot - (1.0F - 2.0F / 24.0F)) < 1.0E-4F,
                    String.format(Locale.ROOT, "sante %.4f (attendu %.4f), etat %s", afterShot, 1.0F - 2.0F / 24.0F, c.state()));
            // un rayon d'arme s'arrete sur la voiture ; un passager ne vise pas la sienne
            Vec3 from = new Vec3(c.getX(), c.getY() + 0.5, c.getZ() - 12.0);
            com.emerald.jak.gun.GunImpacts.Ray ray = com.emerald.jak.gun.GunImpacts.ray(l, f, from, new Vec3(0.0, 0.0, 1.0),
                    24.0, 0.1F);
            ArmorStand stand = new ArmorStand(l, c.getX(), c.getY() + 1.0, c.getZ());
            l.addFreshEntity(stand);
            SPAWNED.add(stand);
            boolean seated = stand.startRiding(c, true);
            doomedRider = stand;
            check("degats : un rayon d'arme s'arrete sur la voiture ; un occupant ne vise pas la sienne",
                    VehicleDamage.vehicleOf(ray.target()) == c && seated && !VehicleDamage.shootable(c, stand)
                            && VehicleDamage.shootable(c, f),
                    "rayon sur " + (ray.target() == null ? "rien" : ray.target().getClass().getSimpleName())
                            + String.format(Locale.ROOT, " a %.1f bloc(s)", ray.end().distanceTo(from)) + ", occupant assis " + seated);
            // les coups de poing : quatre points a mains nues, un par demi-seconde ; rien avec une epee
            net.minecraft.world.damagesource.DamageSource fist = l.damageSources().playerAttack(f);
            boolean first = c.hurt(fist, 1.0F);
            boolean again = c.hurt(fist, 1.0F);
            float afterPunch = c.health();
            f.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            boolean sword = d.hurt(l.damageSources().playerAttack(f), 7.0F);
            f.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            check("degats : un coup de poing retire quatre points, un seul par demi-seconde ; une epee du dehors, rien",
                    first && !again && Math.abs(afterShot - afterPunch - 4.0F / 24.0F) < 1.0E-4F && !sword
                            && d.health() == 1.0F,
                    String.format(Locale.ROOT, "premier coup %s, second %s, sante %.4f -> %.4f ; epee %s, carb %.3f",
                            first, again, afterShot, afterPunch, sword, d.health()));
            // les paliers d'un choc, sur la voiture d'a cote : presque rien, cinq, dix
            float h0 = d.health();
            VehicleDamage.collision(d, 0.0);
            float h1 = d.health();
            VehicleDamage.collision(d, 0.05);
            float h2 = d.health();
            VehicleDamage.collision(d, 0.35);
            float h3 = d.health();
            check("degats : les paliers d'un choc -- un choc tout juste audible presque rien, puis cinq, puis dix points",
                    (h0 - h1) * 24.0F < 0.5F && Math.abs((h1 - h2) * 24.0F - 5.0F) < 1.0E-3F
                            && Math.abs((h2 - h3) * 24.0F - 10.0F) < 1.0E-3F,
                    String.format(Locale.ROOT, "points retires %.2f, %.2f, %.2f", (h0 - h1) * 24.0F, (h1 - h2) * 24.0F,
                            (h2 - h3) * 24.0F));
            d.setHealth(1.0F);
            // la voiture d'a cote vient se ranger a cinq blocs, le monstre a trois
            d.moveTo(c.getX(), c.getY(), c.getZ() + 5.5, -90.0F, 0.0F);
            // a cote de la voiture, hors de ses boites : dedans, elle l'aurait ecarte. Un husk
            // ordinaire : un monstre de l'invasion, hors de la grille, est retire a la demi-seconde
            // (HavenInvasion.sweep) -- le premier essai l'avait perdu avant l'explosion --, et un
            // zombie brulerait au soleil
            Mob zombie = EntityType.HUSK.create(l);
            if (zombie != null) {
                zombie.moveTo(c.getX(), c.getY(), c.getZ() - 4.0, 0.0F, 0.0F);
                zombie.setNoAi(true);
                zombie.setNoGravity(true);
                l.addFreshEntity(zombie);
                SPAWNED.add(zombie);
                blastVictimHealth = zombie.getHealth();
            }
            blastVictim = zombie;
            explosionsBefore = VehicleDamage.explosions();
            // la derniere goutte : detruite
            c.setHealth(0.05F);
            VehicleDamage.damage(c, 2.0F);
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (doomed == null) {
                return true;
            }
            if (VehicleDamage.explosions() == explosionsBefore && t < 40) {
                return false;
            }
            float zombieLost = blastVictim == null ? -1.0F : blastVictimHealth - blastVictim.getHealth();
            check("destruction : l'occupant saute, la voiture explose en moins d'une demi-seconde et devient une epave",
                    VehicleDamage.explosions() == explosionsBefore + 1 && doomed.wrecked() && doomed.health() <= 0.0F
                            && doomedRider != null && !doomedRider.isPassenger() && t <= 12,
                    "explosion apres " + t + " tique(s), epave " + doomed.wrecked() + ", occupant a bord "
                            + (doomedRider != null && doomedRider.isPassenger()));
            check("destruction : l'explosion blesse le monstre a quatre blocs (deux points de Jak) et abime la voiture a cinq",
                    // huit PV, moins ce que retient l'armure naturelle du husk
                    zombieLost > 7.0F && neighbour.health() < 1.0F && neighbour.health() > 0.9F,
                    String.format(Locale.ROOT, "monstre -%.1f PV a %.1f bloc(s), voiture voisine %.3f", zombieLost,
                            blastVictim == null ? -1.0 : blastVictim.position().distanceTo(doomed.position()), neighbour.health()));
            return true;
        });
        STEPS.add((s, l, t) -> {
            if (doomed == null) {
                return true;
            }
            if (!doomed.isRemoved() && t < VehicleDamage.WRECK_TICKS + 40) {
                return false;
            }
            check("destruction : l'epave disparait apres cinq secondes",
                    doomed.isRemoved() && t >= VehicleDamage.WRECK_TICKS - 15,
                    "retiree apres " + t + " tiques");
            // la grenade Plasmite detruit d'un coup ; la sante se sauvegarde
            JakVehicleEntity g = spawnSea("cara", 140.0);
            if (g == null) {
                return true;
            }
            int hit = VehicleDamage.explosion(l, g.position(), VehicleImpacts.BLAST_PLASMITE_RADIUS, VehicleDamage.PLASMITE, null);
            neighbour.setHealth(0.5F);
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            neighbour.saveWithoutId(tag);
            JakVehicleEntity copy = Jak3Registry.JAK_VEHICLE.get().create(l);
            if (copy != null) {
                copy.load(tag);
            }
            check("degats : la grenade Plasmite detruit une voiture d'un coup ; la sante se sauvegarde et se relit",
                    hit == 1 && g.health() <= 0.0F && copy != null && Math.abs(copy.health() - 0.5F) < 1.0E-6F,
                    String.format(Locale.ROOT, "vehicules touches %d, sante %.2f ; relue %.2f", hit, g.health(),
                            copy == null ? Float.NaN : copy.health()));
            return true;
        });
        STEPS.add((s, l, t) -> {
            for (Entity e : SPAWNED) {
                if (!e.isRemoved() && (e instanceof JakVehicleEntity || e instanceof ArmorStand || e instanceof Mob)) {
                    e.discard();
                }
            }
            doomed = null;
            neighbour = null;
            blastVictim = null;
            doomedRider = null;
            return true;
        });
    }

    private static void planCollisionCleanup() {
        STEPS.add((s, l, t) -> {
            for (JakVehicleEntity c : new JakVehicleEntity[]{car, second}) {
                if (c != null) {
                    c.setAutotestInput(null);
                    c.discard();
                }
            }
            car = null;
            second = null;
            for (Entity e : SPAWNED) {
                if (e instanceof Mob mob && !mob.isRemoved()) {
                    mob.discard();
                }
            }
            rammed = null;
            bystander = null;
            return true;
        });
    }

    // ------------------------------------------------------------- conduite par un client

    /**
     * Ce que le client du conducteur recoit du serveur pendant qu'il conduit.
     *
     * Le client du conducteur simule la voiture et envoie ses positions. Le serveur
     * rangeait le deplacement recu dans getDeltaMovement, et ServerEntity le
     * renvoyait tous les trois ticks a ceux qui voient la voiture, conducteur
     * compris (ClientboundSetEntityMotionPacket), que le client pose sans garde
     * (Entity.lerpMotion). Un tick du serveur sans paquet valait zero : la voiture
     * du joueur s'arretait net en pleine course.
     *
     * Un porte-armure prend la place 0, et le drapeau d'autotest dit au serveur
     * qu'un client conduit (setAutotestRemoteDriver) : un FakePlayer ne monte dans
     * rien (FakePlayer.startRiding rend faux), et le serveur d'essai n'a pas de
     * joueur. Le serveur ne simule plus la voiture. Un
     * ServerEntity temoin, construit comme celui du ChunkMap, capte ce qui partirait
     * vers les clients. Entre deux ticks, on rejoue les positions du conducteur comme
     * handleMoveVehicle (absMoveTo), deux blocs par paquet selon DRIVER_PACKETS, en
     * aller-retour sur la mer. La garde du client (JakVehicleEntity.lerpMotion) ne
     * s'essaie pas sans client.
     */
    /**
     * Le coussin du bord de la ville (VehiclePhysics.cushion), en calcul : une voiture a pleine
     * vitesse qui entre dans les douze derniers blocs devant le rideau s'arrete avant lui,
     * sans jamais perdre d'un coup assez de vitesse pour sonner un choc ; celle qui s'en
     * eloigne ne sent rien.
     */
    private static void cushion() {
        double gap = VehiclePhysics.CUSHION + 0.5;
        double v = 2.0;
        double worstDrop = 0.0;
        double closest = gap;
        for (int tick = 0; tick < 200; tick++) {
            double next = VehiclePhysics.soften(v, gap, 1);
            worstDrop = Math.max(worstDrop, v - next);
            v = next;
            gap -= v;
            closest = Math.min(closest, gap);
        }
        double away = VehiclePhysics.soften(-2.0, 1.0, 1);
        check("bord de la ville : a pleine vitesse vers le rideau, la voiture s'arrete avant lui, sans perte assez brusque"
                        + " pour sonner un choc ; en s'eloignant, rien ne la retient",
                closest >= 0.0 && worstDrop < VehicleImpacts.CRASH_DROP && away == -2.0,
                String.format(Locale.ROOT, "au plus pres %.2f bloc, plus forte perte %.3f bloc/tick (seuil %.1f)",
                        closest, worstDrop, VehicleImpacts.CRASH_DROP));
    }

    private static void planDriverSync() {
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = Jak3Registry.JAK_VEHICLE.get().create(l);
            if (c == null) {
                check("conduite par un client : voiture posee", false, "creation impossible");
                return true;
            }
            c.setModel("cara");
            double x = sea0X + 200.0;
            double z = sea0Z + 48.0;
            double water = groundBelow(l, x, 90.0, z, 40.0);
            double y = (Double.isNaN(water) ? 63.0 : water) + c.spec().equilibriumDistance(false) - c.spec().thrusterY;
            c.moveTo(x, y, z, -90.0F, 0.0F);
            l.addFreshEntity(c);
            SPAWNED.add(c);
            drivenCar = c;
            ArmorStand seat = EntityType.ARMOR_STAND.create(l);
            if (seat == null) {
                check("conduite par un client : porte-armure pose", false, "creation impossible");
                return true;
            }
            seat.moveTo(x, y, z, -90.0F, 0.0F);
            l.addFreshEntity(seat);
            SPAWNED.add(seat);
            boolean boarded = seat.startRiding(c, true);
            c.setAutotestRemoteDriver(true);
            driverSeat = seat;
            drivenX = x;
            expectedMotionX = 0.0;
            check("conduite par un client : place 0 prise, le serveur ne simule plus la voiture",
                    boarded && c.occupant(0) == seat && !c.isControlledByLocalInstance(),
                    "monte : " + boarded + ", place 0 : " + c.occupant(0)
                            + ", simulee par le serveur : " + c.isControlledByLocalInstance());
            return true;
        });
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = drivenCar;
            if (c == null || c.isRemoved() || driverSeat == null || c.occupant(0) != driverSeat) {
                check("conduite par un client : voiture et conducteur toujours la", false,
                        "retires ou descendu au tick " + t);
                return true;
            }
            if (t == 0) {
                motionSent.clear();
                emptyTicks = 0;
                motionMismatches = 0;
                crashesBefore = VehicleImpacts.crashes();
                int id = c.getId();
                witness = new ServerEntity(l, c, c.getType().updateInterval(), c.getType().trackDeltas(), packet -> {
                    if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.getId() == id) {
                        motionSent.add(new Vec3(motion.getXa(), motion.getYa(), motion.getZa()));
                    }
                });
            } else {
                // ce que ChunkMap.TrackedEntity fait apres le tick des entites
                witness.sendChanges();
                // le deplacement recu au tick precedent, lu par la voiture a son tick
                if (Math.abs(c.serverMotion().x - expectedMotionX) > 1.0E-6) {
                    motionMismatches++;
                }
            }
            if (t >= DRIVER_TICKS) {
                long stops = motionSent.stream().filter(v -> v.lengthSqr() < 1.0E-4).count();
                check("conduite par un client : le serveur ne renvoie aucune vitesse au conducteur",
                        motionSent.isEmpty(),
                        motionSent.size() + " paquets de vitesse, dont " + stops + " a l'arret, en " + DRIVER_TICKS
                                + " ticks dont " + emptyTicks + " sans paquet du conducteur (intervalle "
                                + c.getType().updateInterval() + ", vitesse suivie : " + c.getType().trackDeltas() + ")");
                check("conduite par un client : le serveur lit la vitesse du conducteur dans ses positions",
                        motionMismatches == 0,
                        motionMismatches + " ticks ou serverMotion differe du deplacement recu");
                // 22 sept. : une tique sans paquet du conducteur faisait sonner un choc en
                // plein ciel ; c'est desormais son client qui entend les chocs
                check("conduite par un client : aucun choc fantome quand ses paquets arrivent en desordre",
                        VehicleImpacts.crashes() == crashesBefore,
                        (VehicleImpacts.crashes() - crashesBefore) + " choc(s) entendus en " + DRIVER_TICKS
                                + " ticks dont " + emptyTicks + " sans paquet");
                // et le serveur ne la fait buter contre aucun autre vehicule (canCollideWith)
                JakVehicleEntity probeCar = Jak3Registry.JAK_VEHICLE.get().create(l);
                if (probeCar != null) {
                    probeCar.setModel("cara");
                    probeCar.moveTo(c.getX(), c.getY(), c.getZ(), 0.0F, 0.0F);
                    check("conduite par un client : le serveur ne la fait buter contre aucun autre vehicule",
                            !c.canCollideWith(probeCar) && !probeCar.canCollideWith(c),
                            "voiture du joueur -> autre : " + c.canCollideWith(probeCar)
                                    + ", autre -> voiture du joueur : " + probeCar.canCollideWith(c));
                    probeCar.discard();
                }
                // un dernier paquet : la voiture a de l'elan quand le conducteur descend
                drivenX += 2.0;
                c.absMoveTo(drivenX, c.getY(), c.getZ(), c.getYRot(), 0.0F);
                expectedMotionX = 2.0;
                return true;
            }
            int arrived = DRIVER_PACKETS[t % DRIVER_PACKETS.length];
            double direction = (t / DRIVER_PACKETS.length) % 2 == 0 ? 1.0 : -1.0;
            if (arrived == 0) {
                emptyTicks++;
            }
            for (int i = 0; i < arrived; i++) {
                drivenX += 2.0 * direction;
                c.absMoveTo(drivenX, c.getY(), c.getZ(), c.getYRot(), 0.0F);
            }
            expectedMotionX = 2.0 * direction * arrived;
            return false;
        });
        STEPS.add((s, l, t) -> {
            JakVehicleEntity c = drivenCar;
            if (c != null && !c.isRemoved() && driverSeat != null) {
                Vec3 received = c.serverMotion();
                c.setAutotestRemoteDriver(false);
                driverSeat.stopRiding();
                driverSeat.discard();
                check("conduite par un client : le conducteur descend, la voiture garde son elan",
                        Math.abs(received.x - expectedMotionX) < 1.0E-6
                                && c.getDeltaMovement().distanceTo(received) < 1.0E-6 && c.isControlledByLocalInstance(),
                        "deplacement recu " + fmt(received) + ", vitesse apres la descente " + fmt(c.getDeltaMovement())
                                + ", simulee par le serveur : " + c.isControlledByLocalInstance());
                c.discard();
            }
            drivenCar = null;
            driverSeat = null;
            witness = null;
            return true;
        });
    }

    // ------------------------------------------------------------- les motos

    /**
     * Une moto : rase-sol, voie haute et redescente dans la rue du bar (en mer si
     * la rue est couverte), sa place unique, puis 40 m/s, le frein et le mur d'un
     * bloc a pleine vitesse, en mer, comme en jeu (sous-pas compris).
     */
    private static void planBike(HavenState state, BlockPos street, String model) {
        STEPS.add((s, l, t) -> {
            VehicleSpec spec = VehicleSpec.of(model);
            line(String.format(Locale.ROOT, "moto %s : rase-sol calcule a %.3f blocs sans pilote, %.3f pilotee ;"
                            + " voie haute pendue a %.3f sous le plancher", model, spec.equilibriumDistance(false),
                    spec.equilibriumDistance(true), VehicleDynamics.highHang(spec, false)));
            return true;
        });
        planStreetSpawn(street, model);
        planHover(120, false);
        STEPS.add((s, l, t) -> car == null || !streetHigh || highMode(car, t, "rue du bar, " + model));
        STEPS.add((s, l, t) -> car == null || !streetHigh || descent(car, t, "rue du bar, " + model));
        planHover(120, true);
        STEPS.add((s, l, t) -> {
            if (car == null) {
                return true;
            }
            if (t < 20) {
                return false;
            }
            bikeSeat(l, car);
            return true;
        });
        planStreetCleanup();

        planSeaSpawn(model);
        planTopSpeed();
        STEPS.add((s, l, t) -> {
            if (t < 20) {
                return false;
            }
            car.setAutotestInput(VehicleDynamics.Input.NONE);
            return true;
        });
        STEPS.add((s, l, t) -> streetHigh || highMode(car, t, "mer, " + model));
        STEPS.add((s, l, t) -> streetHigh || descent(car, t, "mer, " + model));
        STEPS.add((s, l, t) -> {
            VehiclePhysics.substeps = true;
            line("obstacles (" + model + ", avec sous-pas) : comme en jeu, controles compris");
            return true;
        });
        for (double phase : new double[]{0.0, 0.5, 1.0, 1.5}) {
            planWall(model + ", avec sous-pas", true, phase);
        }
        planSeaCleanup();
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
                    String.format(Locale.ROOT, "%s : vitesse a l'impact %.3f bloc/tick (%.1f m/s), face du mur en X %.3f,"
                                    + " bord avant le plus loin %.4f, origine finale X %.3f, choc au tick %d, rebond %b",
                            car.model(), impact, impact * 20.0, wallX, peak, car.getX(), marker, flag) + monitorDetail());
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

    /** Pose le vehicule a l'arret, ou lance a la vitesse maximale, gaz et moteur a fond. */
    private static void launch(JakVehicleEntity c, double x, double y, double z, float yaw, boolean fullSpeed) {
        // un essai de physique part d'un vehicule neuf : les chocs des essais d'avant l'abimaient
        // jusqu'a le detruire, et une epave ne vole plus (cahier §98)
        c.setHealth(1.0F);
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
        double deepest = 0.0;
        for (int k = 1; k < CUT_SAMPLES; k++) {
            double f = (double) k / CUT_SAMPLES;
            double x = Mth.lerp(f, prevX, c.getX());
            double y = Mth.lerp(f, prevY, c.getY());
            double z = Mth.lerp(f, prevZ, c.getZ());
            float yaw = Mth.rotLerp((float) f, prevYaw, c.getYRot());
            for (AABB box : JakVehicleEntity.boxesAt(spec, x, y, z, yaw)) {
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

    /** Vrai si une boite du vehicule recoupe un bloc (les entites ne comptent pas). */
    private static boolean insideBlocks(JakVehicleEntity c) {
        for (AABB box : c.collisionBoxes()) {
            if (blocksIn(c.level() instanceof ServerLevel s ? s : null, box)) {
                return true;
            }
        }
        return false;
    }

    /** Vrai si la boite recoupe un bloc ; les entites, dont le vehicule lui-meme, ne comptent pas. */
    private static boolean blocksIn(@Nullable ServerLevel level, AABB box) {
        if (level == null) {
            return false;
        }
        for (VoxelShape shape : level.getBlockCollisions(null, box.deflate(1.0E-4))) {
            if (!shape.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Vrai si une boite de {@code a} recoupe une boite de {@code b}. */
    private static boolean touches(List<AABB> a, List<AABB> b) {
        for (AABB x : a) {
            for (AABB y : b) {
                if (x.intersects(y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** L'ecart horizontal le plus faible entre deux jeux de boites, en blocs (negatif s'ils se recoupent a plat). */
    private static double separation(List<AABB> a, List<AABB> b) {
        double best = Double.MAX_VALUE;
        for (AABB x : a) {
            for (AABB y : b) {
                double gapX = Math.max(y.minX - x.maxX, x.minX - y.maxX);
                double gapZ = Math.max(y.minZ - x.maxZ, x.minZ - y.maxZ);
                best = Math.min(best, Math.max(gapX, gapZ));
            }
        }
        return best;
    }

    /**
     * L'emprise des bornes du modele tournees du lacet, a cette origine : en x et
     * z, le rectangle qui contient le modele ; en y, du bas au haut du modele.
     */
    private static AABB footprint(VehicleSpec spec, Vec3 origin, float yawDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (double lx : new double[]{spec.minX, spec.maxX}) {
            for (double lz : new double[]{spec.minZ, spec.maxZ}) {
                double wx = origin.x + lx * Math.cos(yaw) - lz * Math.sin(yaw);
                double wz = origin.z + lz * Math.cos(yaw) + lx * Math.sin(yaw);
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
            }
        }
        return new AABB(minX, origin.y + spec.minY, minZ, maxX, origin.y + spec.maxY, maxZ);
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

    /** Les vehicules marques charges, par cle de place. */
    private static Map<String, Integer> countByRoom(ServerLevel level) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (JakVehicleEntity c : HavenCars.loadedCars(level)) {
            if (!c.isRemoved() && c.room() != null) {
                counts.merge(c.room(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** Les vehicules marques de cette cle de place. */
    private static List<JakVehicleEntity> carsOf(ServerLevel level, String key) {
        List<JakVehicleEntity> cars = new ArrayList<>();
        for (JakVehicleEntity c : HavenCars.loadedCars(level)) {
            if (!c.isRemoved() && key.equals(c.room())) {
                cars.add(c);
            }
        }
        return cars;
    }

    @Nullable
    private static JakVehicleEntity onlyCar(ServerLevel level, String key) {
        List<JakVehicleEntity> cars = carsOf(level, key);
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
