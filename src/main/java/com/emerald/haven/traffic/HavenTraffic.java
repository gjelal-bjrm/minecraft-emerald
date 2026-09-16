package com.emerald.haven.traffic;

import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.invasion.HavenSpawner;
import com.emerald.init.Jak3Registry;
import com.emerald.jak.vehicle.JakVehicleEntity;
import com.emerald.jak.vehicle.VehicleDynamics;
import com.emerald.jak.vehicle.VehicleSpec;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Le trafic civil de la ville paisible : des voitures et des motos de Jak 3, pilotees
 * par des habitants, sur les voies du jeu (HavenTrafficData, TrafficDriver).
 *
 * « La ville se remplit de villageois des differentes regions qui marchent, certains
 * volent en voiture volante » (decision du joueur du 13 sept.). Le trafic n'existe
 * qu'en mode PAISIBLE : il part avec le retour de l'invasion, comme les habitants.
 *
 * LA POPULATION EST CELLE DES VOIES, comme celle des rues (HavenInvasion) : chaque
 * branche a un QUOTA tire de sa longueur ({@link #quota} : un vehicule tous les
 * {@link #SPACING} blocs, arrondi par un hasard fixe de la branche), et les branches
 * dont le depart est charge et qui comptent moins de vehicules que leur quota en
 * recoivent, les plus loin des joueurs d'abord, {@link #SPAWNS_PER_SECOND} par seconde
 * au plus. Un vehicule nait sur sa voie, a plus de {@link #SPAWN_MIN} blocs de tout
 * joueur et hors de sa vue a moins de {@link #SIGHT}, a plus de {@link #SPAWN_GAP}
 * blocs d'un autre vehicule du trafic, dans l'air libre, dans un troncon qui tique,
 * lance a la vitesse de la branche ; celui qui entre dans un troncon qui ne tique
 * pas est retire (voir update). Modele au hasard parmi les six civils (Jak 3 tire
 * de meme, uniformement).
 * Jak 3 ne tient que six vehicules civils a la fois pour toute la ville ; ici on
 * suit les rues : un vehicule tous les 80 blocs de voie, {@link #LOADED_MAX} charges
 * au plus.
 *
 * LES VEHICULES SONT DES JakVehicleEntity ordinaires, avec un pilote (TrafficDriver,
 * cote serveur, sauvegarde avec l'entite), en voie haute, que le serveur conduit a
 * la place de sa physique de vol. Solides comme les autres : la voiture d'un joueur
 * s'y cogne, et ils freinent derriere elle. On ne monte pas dedans (Jak 3 les
 * detourne ; pas ici, pas pour l'instant). Le pilote est un habitant sans metier,
 * sans intelligence (setNoAi) et invulnerable, assis a la place 0 ; il ne compte
 * pas pour la population des rues (etiquette {@link #DRIVER_TAG}).
 *
 * PERSISTANTS ET DE LA VILLE D'AUJOURD'HUI, comme les monstres et les habitants :
 * etiquette de generation, acceptes au rechargement en PAISIBLE seulement
 * (HavenInvasion.welcome, qui traite {@link #TRAFFIC_TAG} comme un habitant) ; le
 * pilote, sauvegarde DANS son vehicule, porte les memes etiquettes et suit le meme
 * verdict. Retires au retour de l'invasion, a la fermeture et a une pose (HavenInvasion),
 * au depart (HavenCars), et quand ils sortent de la grille.
 */
public final class HavenTraffic {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** L'etiquette d'un vehicule du trafic (sauvegardee avec lui). */
    public static final String TRAFFIC_TAG = "emeraldweapons.haven_trafic";
    /** L'etiquette du pilote d'un vehicule du trafic. */
    public static final String DRIVER_TAG = "emeraldweapons.haven_pilote";

    /** Un vehicule tous les tant de blocs de voie, en moyenne (Jak 3 : 12 m + hasard x 180 m). */
    public static final double SPACING = 80.0;
    /** Vehicules du trafic charges au plus. */
    public static final int LOADED_MAX = 60;
    /**
     * Apparitions par seconde. A deux, une zone qui tique sur 80 blocs ne gardait que
     * 11 vehicules pour 38 de quota (banc) : chacun la traverse en cinq secondes. En
     * jeu, un joueur a 40 m/s decouvre les voies plus vite encore.
     */
    public static final int SPAWNS_PER_SECOND = 4;
    /**
     * Aucune apparition a moins de tant de blocs d'un joueur, ni sous ses yeux a moins de
     * SIGHT. Les voies sont en plein ciel : a 160 blocs, presque tout se voyait et rien
     * n'apparaissait (5 vehicules pour 20 au banc). Jak 3 fait apparaitre les siens des
     * qu'une cellule de 50 m entre dans le champ a plus de 67 m de la camera.
     */
    public static final double SPAWN_MIN = 64.0;
    public static final double SIGHT = 96.0;
    /** Deux vehicules du trafic ne naissent pas a moins de tant de blocs l'un de l'autre. */
    public static final double SPAWN_GAP = 24.0;
    /**
     * L'origine du vehicule au-dessus de la voie. Le jeu pose l'origine sur la carte de
     * hauteur (hvehicle.gc:66-78) ; une voiture de joueur en voie haute y pend a peu
     * pres au meme endroit (propulseurs sur la carte). Zero.
     */
    public static final double LANE_HANG = 0.0;
    /** Essais de place par branche et par seconde. */
    private static final int TRIES = 6;

    @Nullable
    private static HavenTrafficData.Data quotasFrom;
    private static int[] quotas = new int[0];
    /** Les vehicules par branche courante, refaits chaque seconde (choix au carrefour). */
    private static int[] users = new int[0];

    private HavenTraffic() {
    }

    // ================================================================ lecture

    /** Les vehicules du trafic charges (copie). */
    public static List<JakVehicleEntity> loaded(ServerLevel level) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(JakVehicleEntity.class),
                car -> car.traffic() != null && !car.isRemoved()));
    }

    /** Les pilotes charges, dans ou hors de leur vehicule (copie). */
    public static List<Villager> drivers(ServerLevel level) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Villager.class),
                v -> v.getTags().contains(DRIVER_TAG) && !v.isRemoved()));
    }

    /** Vrai pour un vehicule du trafic ou son pilote. */
    public static boolean isTraffic(@Nullable Entity entity) {
        return entity != null && (entity.getTags().contains(TRAFFIC_TAG) || entity.getTags().contains(DRIVER_TAG));
    }

    /** Le quota d'une branche : sa longueur divisee par SPACING, arrondie par un hasard fixe de la branche. */
    public static int quota(HavenTrafficData.Branch branch) {
        if (branch.exit()) {
            return 0;
        }
        long h = branch.index() * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        double frac = (h & 0xFFFFFFL) / (double) 0x1000000;
        return (int) Math.floor(branch.length() / SPACING + frac);
    }

    static int[] quotas(HavenTrafficData.Data data) {
        if (quotasFrom != data) {
            quotasFrom = data;
            quotas = new int[data.branches().size()];
            for (int i = 0; i < quotas.length; i++) {
                quotas[i] = quota(data.branch(i));
            }
        }
        return quotas;
    }

    /** Le troncon ou une branche fait naitre ses vehicules : celui de son depart. */
    public static long spawnChunk(HavenTrafficData.Data data, HavenTrafficData.Branch branch, BlockPos origin) {
        Vec3 start = data.start(branch, origin);
        return ChunkPos.asLong(BlockPos.containing(start));
    }

    // ================================================================ conduite

    /** Un tick d'un vehicule du trafic (appele par JakVehicleEntity.tick, cote serveur). */
    public static void drive(JakVehicleEntity car) {
        if (!(car.level() instanceof ServerLevel level) || car.traffic() == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        HavenTrafficData.Data data = HavenTrafficData.get(server);
        if (data == null) {
            return;
        }
        BlockPos origin = HavenState.get(server).origin();
        if (!car.traffic().drive(car, data, origin, users, level.random)) {
            remove(car);
        }
    }

    // ================================================================ chaque seconde

    /**
     * Le controle d'une seconde, ville ouverte (appele par HavenInvasion.update) :
     * retraits, comptes par branche, apparitions en PAISIBLE.
     */
    public static void update(ServerLevel level, HavenInvasion.Mode mode, List<Vec3> anchors) {
        MinecraftServer server = level.getServer();
        HavenTrafficData.Data data = HavenTrafficData.get(server);
        HavenInvasionData.Data map = HavenInvasionData.get(server);
        if (data == null) {
            return;
        }
        BlockPos origin = HavenState.get(server).origin();
        List<JakVehicleEntity> cars = loaded(level);
        int[] counts = new int[data.branches().size()];
        for (JakVehicleEntity car : cars) {
            // UN VEHICULE GELE EST RETIRE. Au bord de la zone qui tique (la distance de
            // simulation, en jeu), une entite s'arrete net ; sur une voie, les suivants
            // freinent derriere elle et s'entassent (banc du 16 sept. : quatre vehicules a
            // 12 blocs d'intervalle, tous a l'arret). Jak 3 fait disparaitre les siens
            // au-dela de 247 m de la camera ; ici, celui qui entre dans un troncon charge
            // sans tique disparait, et renait plus loin sur une voie qui tique.
            if (mode != HavenInvasion.Mode.PAISIBLE || outside(map, origin, car)
                    || !level.isPositionEntityTicking(car.blockPosition())) {
                remove(car);
                continue;
            }
            int branch = car.traffic().branch();
            if (branch >= 0 && branch < counts.length) {
                counts[branch]++;
            }
        }
        for (Villager driver : drivers(level)) {
            if (driver.getVehicle() == null || mode != HavenInvasion.Mode.PAISIBLE) {
                driver.discard();
            }
        }
        users = counts;
        if (mode == HavenInvasion.Mode.PAISIBLE && !anchors.isEmpty()) {
            spawn(level, data, origin, anchors, counts);
        }
    }

    private static boolean outside(@Nullable HavenInvasionData.Data map, BlockPos origin, Entity entity) {
        if (map == null) {
            return false;
        }
        double x = entity.getX() - origin.getX();
        double z = entity.getZ() - origin.getZ();
        return x < -8 || z < -8 || x >= map.width() + 8 || z >= map.depth() + 8
                || entity.getY() > origin.getY() + map.height() + 32 || entity.getY() < origin.getY() + map.waterMaxY();
    }

    /** Une branche sous son quota, et la distance de son depart au joueur le plus proche. */
    private record Deficit(HavenTrafficData.Branch branch, int missing, double distance) {
    }

    private static void spawn(ServerLevel level, HavenTrafficData.Data data, BlockPos origin, List<Vec3> anchors,
                              int[] counts) {
        int[] quota = quotas(data);
        int loaded = 0;
        for (int c : counts) {
            loaded += c;
        }
        int budget = Math.min(SPAWNS_PER_SECOND, LOADED_MAX - loaded);
        if (budget <= 0) {
            return;
        }
        List<Deficit> deficits = new ArrayList<>();
        for (HavenTrafficData.Branch branch : data.branches()) {
            int want = quota[branch.index()];
            int have = counts[branch.index()];
            if (want <= have) {
                continue;
            }
            long chunk = spawnChunk(data, branch, origin);
            if (!level.getChunkSource().hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) || !level.areEntitiesLoaded(chunk)) {
                continue;
            }
            Vec3 start = data.start(branch, origin);
            deficits.add(new Deficit(branch, want - have, HavenInvasion.nearestDistance(anchors, start.x, start.z)));
        }
        deficits.sort(Comparator.comparingDouble(Deficit::distance).reversed());
        String generation = HavenInvasion.generationTag(level);
        for (Deficit deficit : deficits) {
            if (budget <= 0) {
                return;
            }
            JakVehicleEntity car = spawnOn(level, data, origin, deficit.branch(), anchors, generation);
            if (car != null) {
                budget--;
            }
        }
    }

    /** Tente de faire naitre un vehicule sur une branche ; null si aucune place ne convient. */
    @Nullable
    static JakVehicleEntity spawnOn(ServerLevel level, HavenTrafficData.Data data, BlockPos origin,
                                    HavenTrafficData.Branch branch, List<Vec3> anchors, String generation) {
        RandomSource random = level.random;
        Vec3 start = data.start(branch, origin);
        Vec3 end = data.end(branch, origin);
        double length = TrafficDriver.horizontal(start, end);
        if (length < 20.0) {
            return null;
        }
        double dx = (end.x - start.x) / length;
        double dz = (end.z - start.z) / length;
        String model = JakVehicleEntity.MODELS.get(random.nextInt(JakVehicleEntity.MODELS.size()));
        VehicleSpec spec = VehicleSpec.of(model);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        for (int i = 0; i < TRIES; i++) {
            double along = 8.0 + random.nextDouble() * (length - 16.0);
            double side = (random.nextDouble() - 0.5) * Math.min(branch.width(), 3.0);
            double x = start.x + dx * along - dz * side;
            double z = start.z + dz * along + dx * side;
            double y = lerp(start.y, end.y, along / length) + LANE_HANG;
            if (HavenInvasion.nearestDistance(anchors, x, z) < SPAWN_MIN) {
                continue;
            }
            BlockPos at = BlockPos.containing(x, y, z);
            // dans un troncon qui tique seulement : ne au bord de la zone chargee, un vehicule
            // restait fige en l'air, a 15 m/s de vitesse et zero de mouvement (banc du 16 sept.)
            if (!level.isLoaded(at) || !level.isPositionEntityTicking(at)) {
                continue;
            }
            if (HavenInvasion.nearestDistance(anchors, x, z) <= SIGHT && HavenSpawner.seen(level, anchors, at, SIGHT)) {
                continue;
            }
            AABB near = new AABB(x - SPAWN_GAP, y - 8, z - SPAWN_GAP, x + SPAWN_GAP, y + 8, z + SPAWN_GAP);
            if (!level.getEntitiesOfClass(JakVehicleEntity.class, near).isEmpty()) {
                continue;
            }
            boolean free = true;
            for (AABB box : JakVehicleEntity.boxesAt(spec, x, y, z, yaw)) {
                free &= level.noCollision(box);
            }
            if (!free) {
                continue;
            }
            JakVehicleEntity car = Jak3Registry.JAK_VEHICLE.get().create(level);
            if (car == null) {
                return null;
            }
            car.setModel(model);
            car.moveTo(x, y, z, yaw, 0.0F);
            car.setMode(VehicleDynamics.MODE_HAUT);
            car.setTraffic(new TrafficDriver(branch.index(), speedOffset(spec, random)));
            car.addTag(TRAFFIC_TAG);
            car.addTag(generation);
            double speed = branch.speedMs() / 20.0;
            car.setDeltaMovement(dx * speed, 0.0, dz * speed);
            if (!level.addFreshEntity(car)) {
                return null;
            }
            Villager driver = new Villager(EntityType.VILLAGER, level,
                    HavenInvasion.VILLAGER_TYPES.get(random.nextInt(HavenInvasion.VILLAGER_TYPES.size())));
            driver.setVillagerData(driver.getVillagerData().setProfession(VillagerProfession.NONE));
            driver.setInvulnerable(true);
            driver.setNoAi(true);
            driver.setPersistenceRequired();
            driver.setCanPickUpLoot(false);
            driver.addTag(HavenInvasion.VILLAGER_TAG);
            driver.addTag(DRIVER_TAG);
            driver.addTag(generation);
            driver.moveTo(x, y, z, yaw, 0.0F);
            if (level.addFreshEntity(driver) && !driver.startRiding(car, true)) {
                driver.discard();
            }
            return car;
        }
        return null;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * Math.max(0.0, Math.min(1.0, t));
    }

    /** L'ecart de vitesse de Jak 3 : car-a/b +0..3 m/s, car-c -2..0, motos +0..4 (car.gc:132, bike.gc:146). */
    static double speedOffset(VehicleSpec spec, RandomSource random) {
        if (spec.isBike()) {
            return random.nextDouble() * 4.0;
        }
        return "carc".equals(spec.model) ? -random.nextDouble() * 2.0 : random.nextDouble() * 3.0;
    }

    // ================================================================ retraits

    /** Retire un vehicule du trafic et son pilote. */
    public static void remove(JakVehicleEntity car) {
        for (Entity passenger : new ArrayList<>(car.getPassengers())) {
            if (passenger.getTags().contains(DRIVER_TAG)) {
                passenger.stopRiding();
                passenger.discard();
            }
        }
        car.ejectPassengers();
        car.discard();
    }

    /** Retire tous les vehicules du trafic et tous les pilotes charges ; rend le nombre de vehicules. */
    public static int removeAll(ServerLevel level) {
        int removed = 0;
        for (JakVehicleEntity car : loaded(level)) {
            remove(car);
            removed++;
        }
        for (Villager driver : drivers(level)) {
            driver.discard();
        }
        if (removed > 0) {
            LOGGER.info("trafic de Haven : {} vehicules retires", removed);
        }
        return removed;
    }
}
