package com.emerald.jak.vehicle;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenRooms;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.init.Jak3Registry;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Une voiture et une moto devant chaque appartement de la ville, tant que le
 * lobby est ouvert.
 *
 * cara et bikea pour l'appartement 1, carb et bikeb pour le 2, carc et bikec
 * pour le 3, sur les places que haven_rooms.json leur donne (car et bike). Ils
 * naissent quand la ville est posee et la phase ACCUEIL, et disparaissent tous
 * au depart (PARTI).
 *
 * UNE PLACE, UNE CLE. La voiture d'une salle a pour cle l'identifiant de la
 * salle -- celle des mondes d'avant les motos, dont les voitures et le registre
 * restent valables --, sa moto le meme suivi de « _moto ». Le registre et la
 * marque de l'entite portent cette cle : voiture et moto d'une meme salle ne se
 * prennent jamais l'une pour le doublon de l'autre.
 *
 * JAMAIS EN DOUBLE. Un vehicule vit dans la sauvegarde de son troncon : un
 * redemarrage le recharge, et en poser un autre serait un doublon. On ne pose
 * donc un vehicule que quand on SAIT qu'il n'existe plus : le registre
 * sauvegarde son identifiant et le troncon ou on l'a vu en dernier, et ce
 * troncon doit avoir ses entites chargees sans qu'il y soit. Tout vehicule
 * marque d'une place dont l'identifiant n'est pas celui du registre est retire.
 *
 * LES PLACES RESTENT CHARGEES par un ticket, comme le prevoit P11 : sans lui,
 * on ne pourrait jamais conclure qu'un vehicule manque.
 *
 * DETRUIT (cahier §98), un vehicule explose et son epave disparait : la place en
 * pose un neuf, a la verification suivante.
 *
 * REMIS SUR LEUR PLACE quand ils tombent a l'eau ou sortent de la boite de la
 * ville, et dans ces deux cas seulement, comme le joueur l'a decide :
 * l'etourderie d'un joueur ne doit pas priver son groupe de vehicule. Un
 * vehicule gare ailleurs y reste. Il n'y a pas de retour « abandonne loin de
 * sa place » : garee devant le Hip Hog, a deux cent cinquante blocs des
 * appartements, une voiture aurait disparu pendant que ses occupants votaient.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenCars {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Un controle par seconde. */
    public static final int CHECK_TICKS = 20;

    /** Ce qui suit l'identifiant de la salle dans la cle de la place de sa moto. */
    public static final String BIKE_SUFFIX = "_moto";

    /** Tient les troncons des places, entites chargees (niveau 32, sans les faire tiquer). */
    private static final TicketType<ChunkPos> PLACES = TicketType.create("arcencium_voitures",
            Comparator.comparingLong(ChunkPos::toLong));
    /** Charge quelques secondes le troncon ou un vehicule a ete vu en dernier. */
    private static final TicketType<ChunkPos> SEEK = TicketType.create("arcencium_voiture_perdue",
            Comparator.comparingLong(ChunkPos::toLong), 100);

    /**
     * Une place de vehicule tiree de haven_rooms.json.
     *
     * @param key   la cle du registre et de la marque de l'entite
     * @param room  l'identifiant de la salle
     * @param model le modele pose sur cette place
     */
    public record Place(String key, String room, String model, BlockPos floor, float yaw) {

        public boolean isBike() {
            return VehicleSpec.of(this.model).isBike();
        }
    }

    @Nullable
    private static List<Place> places;
    /** La lecture de HavenRooms dont {@link #places} est tiree ; une autre lecture (un /reload) la refait. */
    @Nullable
    private static HavenRooms.Data placesFrom;
    private static final Set<ChunkPos> HELD = new HashSet<>();

    private HavenCars() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_TICKS == 0) {
            update(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        forgetMemory();
    }

    /** Oublie tout ce qui n'est pas sauvegarde, comme un redemarrage. */
    public static void forgetMemory() {
        places = null;
        placesFrom = null;
        HELD.clear();
    }

    /**
     * Un joueur qui se deconnecte dans un vehicule d'appartement en descend d'abord.
     *
     * PlayerList.remove lance cet evenement AVANT de sauvegarder le joueur ;
     * seul joueur a bord, il emporterait sinon le vehicule dans son fichier
     * (RootVehicle) et le retirerait du monde. Le controle le croirait disparu
     * et en poserait un autre ; a la reconnexion, l'ancien reviendrait sous
     * lui, serait retire comme doublon, et il serait ejecte. Descendu ici, il
     * est pose a cote, et le vehicule reste la ou il l'a laisse.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.getVehicle() instanceof JakVehicleEntity car && car.room() != null) {
            player.stopRiding();
        }
    }

    // ------------------------------------------------------------- les places

    /**
     * Les places des appartements : les trois voitures, puis les trois motos,
     * dans l'ordre des salles du fichier ; vide s'il est illisible.
     *
     * Tirees de HavenRooms, le seul lecteur de haven_rooms.json, et refaites
     * quand il relit le fichier. Les vehicules d'une salle suivent son rang :
     * cara et bikea pour la premiere, carb et bikeb pour la deuxieme, carc et
     * bikec pour la troisieme.
     */
    public static List<Place> places(MinecraftServer server) {
        HavenRooms.Data data = HavenRooms.get(server);
        if (places != null && data == placesFrom) {
            return places;
        }
        List<Place> cars = new ArrayList<>();
        List<Place> bikes = new ArrayList<>();
        if (data == null) {
            LOGGER.error("vehicules de Haven : {} introuvable ou illisible", HavenRooms.FILE);
        } else {
            List<HavenRooms.Room> rooms = data.rooms();
            for (int i = 0; i < rooms.size() && i < JakVehicleEntity.CARS.size(); i++) {
                HavenRooms.Room room = rooms.get(i);
                if (room.car() == null) {
                    LOGGER.error("vehicules de Haven : {} n'a pas de place de voiture", room.id());
                } else {
                    cars.add(new Place(room.id(), room.id(), JakVehicleEntity.CARS.get(i), room.car().floor(),
                            room.car().yaw()));
                }
                if (room.bike() == null) {
                    LOGGER.error("vehicules de Haven : {} n'a pas de place de moto", room.id());
                } else {
                    bikes.add(new Place(room.id() + BIKE_SUFFIX, room.id(), JakVehicleEntity.BIKES.get(i),
                            room.bike().floor(), room.bike().yaw()));
                }
            }
        }
        List<Place> found = new ArrayList<>(cars);
        found.addAll(bikes);
        placesFrom = data;
        places = List.copyOf(found);
        return places;
    }

    /**
     * L'origine de l'entite sur sa place : le centre horizontal des bornes du
     * modele au milieu de la cellule de sol, son point le plus bas sur le sol.
     */
    public static Vec3 placeOrigin(Place place, BlockPos cityOrigin) {
        VehicleSpec spec = VehicleSpec.of(place.model());
        double yaw = Math.toRadians(place.yaw());
        double cx = cityOrigin.getX() + place.floor().getX() + 0.5;
        double cz = cityOrigin.getZ() + place.floor().getZ() + 0.5;
        // centre du modele tourne du lacet (Vec3.yRot(-lacet))
        double rx = spec.centerX * Math.cos(yaw) - spec.centerZ * Math.sin(yaw);
        double rz = spec.centerZ * Math.cos(yaw) + spec.centerX * Math.sin(yaw);
        return new Vec3(cx - rx, cityOrigin.getY() + place.floor().getY() + 1.0 - spec.minY, cz - rz);
    }

    /** Le centre horizontal des bornes du modele d'un vehicule, dans le monde. */
    public static Vec3 modelCenter(JakVehicleEntity car) {
        VehicleSpec spec = car.spec();
        double yaw = Math.toRadians(car.getYRot());
        return new Vec3(car.getX() + spec.centerX * Math.cos(yaw) - spec.centerZ * Math.sin(yaw),
                car.getY(),
                car.getZ() + spec.centerZ * Math.cos(yaw) + spec.centerX * Math.sin(yaw));
    }

    // ------------------------------------------------------------- le controle

    /** Le controle d'une seconde. Public pour l'autotest, qui le rejoue. */
    public static void update(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        if (level == null || HavenSite.busy()) {
            return;
        }
        HavenState state = HavenState.get(server);
        Registry registry = Registry.get(server);
        List<JakVehicleEntity> loaded = loadedCars(level);

        if (state.phase() == HavenState.Phase.ACCUEIL && state.built()) {
            List<Place> all = places(server);
            hold(level, state, all);
            for (Place place : all) {
                keep(level, state, registry, loaded, place);
            }
            for (JakVehicleEntity car : loaded) {
                if (car.isRemoved() || car.room() == null) {
                    continue;
                }
                UUID registered = registry.car(car.room());
                if (registered != null && !registered.equals(car.getUUID())) {
                    LOGGER.warn("vehicules de Haven : doublon de {} retire ({})", car.room(), car.getUUID());
                    car.ejectPassengers();
                    car.discard();
                }
            }
        } else if (state.phase() == HavenState.Phase.PARTI) {
            int removed = removeAll(level);
            if (removed > 0) {
                LOGGER.info("vehicules de Haven : {} vehicules retires au depart", removed);
            }
            for (String key : registry.rooms()) {
                UUID id = registry.car(key);
                if (id != null && level.getEntity(id) == null) {
                    long chunk = registry.chunk(key);
                    if (level.areEntitiesLoaded(chunk)) {
                        registry.remove(key);
                    } else {
                        seek(level, chunk);
                    }
                }
            }
            release(level);
        } else {
            release(level);
        }
    }

    private static void keep(ServerLevel level, HavenState state, Registry registry,
                             List<JakVehicleEntity> loaded, Place place) {
        UUID id = registry.car(place.key());
        JakVehicleEntity car = null;
        if (id != null && level.getEntity(id) instanceof JakVehicleEntity found && !found.isRemoved()) {
            car = found;
        }
        BlockPos origin = state.origin();
        Vec3 home = placeOrigin(place, origin);
        if (car == null) {
            if (id != null && !level.areEntitiesLoaded(registry.chunk(place.key()))) {
                // il dort peut-etre dans un troncon decharge : on le charge et on revient
                seek(level, registry.chunk(place.key()));
                return;
            }
            if (!level.areEntitiesLoaded(ChunkPos.asLong(BlockPos.containing(home)))) {
                return;
            }
            if (id == null) {
                // un registre perdu : on adopte le vehicule marque plutot que d'en poser un autre
                for (JakVehicleEntity stray : loaded) {
                    if (!stray.isRemoved() && place.key().equals(stray.room())) {
                        registry.put(place.key(), stray.getUUID(), stray.chunkPosition().toLong());
                        return;
                    }
                }
            }
            car = spawn(level, place, origin);
            if (car != null) {
                registry.put(place.key(), car.getUUID(), car.chunkPosition().toLong());
                LOGGER.info("vehicules de Haven : {} pose sur la place {} en {}", place.model(), place.key(),
                        car.blockPosition().toShortString());
            }
            return;
        }
        registry.updateChunk(place.key(), car.chunkPosition().toLong());
        if (car.health() <= 0.0F) {
            // une epave finit de bruler ou elle est ; quand elle disparait, une neuve est posee ici
            return;
        }
        // les deux seuls cas de retour decides par le joueur : dans l'eau, ou hors de la ville
        boolean outside = !inside(state, car);
        boolean wet = car.isInWater();
        if (outside || wet) {
            LOGGER.info("vehicules de Haven : {} ramene sur sa place ({})", place.key(),
                    outside ? "hors de la ville" : "dans l'eau");
            returnToPlace(car, place, origin);
        }
    }

    /** Pose le vehicule d'une place. */
    @Nullable
    public static JakVehicleEntity spawn(ServerLevel level, Place place, BlockPos cityOrigin) {
        JakVehicleEntity car = Jak3Registry.JAK_VEHICLE.get().create(level);
        if (car == null) {
            return null;
        }
        car.setModel(place.model());
        car.setRoom(place.key());
        Vec3 at = placeOrigin(place, cityOrigin);
        car.moveTo(at.x, at.y, at.z, place.yaw(), 0.0F);
        level.addFreshEntity(car);
        return car;
    }

    public static void returnToPlace(JakVehicleEntity car, Place place, BlockPos cityOrigin) {
        car.ejectPassengers();
        Vec3 at = placeOrigin(place, cityOrigin);
        car.resetFlight();
        car.moveTo(at.x, at.y, at.z, place.yaw(), 0.0F);
    }

    /** Retire tous les vehicules charges de la ville ; rend leur nombre. */
    public static int removeAll(ServerLevel level) {
        int removed = 0;
        for (JakVehicleEntity car : loadedCars(level)) {
            if (!car.isRemoved()) {
                car.ejectPassengers();
                car.discard();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Le controle que chaque vehicule fait sur lui-meme, cote serveur.
     *
     * Il rattrape les vehicules qui se chargent APRES le depart, dans un troncon
     * que personne ne visitait : ils se retirent seuls.
     */
    static void carTick(JakVehicleEntity car) {
        if (!(car.level() instanceof ServerLevel level) || !Haven.is(level) || HavenSite.busy()) {
            return;
        }
        HavenState.Phase phase = HavenState.get(level.getServer()).phase();
        if (phase == HavenState.Phase.PARTI
                || (car.room() != null && phase != HavenState.Phase.ACCUEIL)) {
            car.ejectPassengers();
            car.discard();
        }
    }

    public static List<JakVehicleEntity> loadedCars(ServerLevel level) {
        List<JakVehicleEntity> cars = new ArrayList<>();
        for (Entity entity : level.getEntities(EntityTypeTest.forClass(JakVehicleEntity.class), e -> true)) {
            cars.add((JakVehicleEntity) entity);
        }
        return cars;
    }

    private static boolean inside(HavenState state, Entity car) {
        BlockPos o = state.origin();
        return car.getX() >= o.getX() && car.getX() < o.getX() + state.width()
                && car.getZ() >= o.getZ() && car.getZ() < o.getZ() + state.depth()
                && car.getY() <= o.getY() + state.height();
    }

    private static void hold(ServerLevel level, HavenState state, List<Place> all) {
        for (Place place : all) {
            ChunkPos pos = new ChunkPos(BlockPos.containing(placeOrigin(place, state.origin())));
            if (HELD.add(pos)) {
                level.getChunkSource().addRegionTicket(PLACES, pos, 1, pos);
            }
        }
    }

    private static void release(ServerLevel level) {
        for (ChunkPos pos : HELD) {
            level.getChunkSource().removeRegionTicket(PLACES, pos, 1, pos);
        }
        HELD.clear();
    }

    private static void seek(ServerLevel level, long chunk) {
        ChunkPos pos = new ChunkPos(chunk);
        level.getChunkSource().addRegionTicket(SEEK, pos, 0, pos);
    }

    // ------------------------------------------------------------- le registre

    /**
     * Les vehicules des appartements, sauvegardes avec le monde : identifiant et
     * dernier troncon connu, par cle de place. A part de HavenState, qui
     * n'appartient pas aux vehicules. Le nom des champs NBT (« Appartement »,
     * « Voiture ») reste celui d'avant les motos : les anciens mondes se relisent.
     */
    public static final class Registry extends SavedData {

        public static final String KEY = "emeraldweapons_haven_cars";

        private final Map<String, UUID> cars = new HashMap<>();
        private final Map<String, Long> chunks = new HashMap<>();

        public static Registry get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new Factory<>(Registry::new, Registry::load), KEY);
        }

        private static Registry load(CompoundTag tag, HolderLookup.Provider registries) {
            Registry registry = new Registry();
            for (Tag entry : tag.getList("Voitures", Tag.TAG_COMPOUND)) {
                CompoundTag car = (CompoundTag) entry;
                if (car.hasUUID("Voiture")) {
                    registry.cars.put(car.getString("Appartement"), car.getUUID("Voiture"));
                    registry.chunks.put(car.getString("Appartement"), car.getLong("Troncon"));
                }
            }
            return registry;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<String, UUID> entry : this.cars.entrySet()) {
                CompoundTag car = new CompoundTag();
                car.putString("Appartement", entry.getKey());
                car.putUUID("Voiture", entry.getValue());
                car.putLong("Troncon", this.chunks.getOrDefault(entry.getKey(), 0L));
                list.add(car);
            }
            tag.put("Voitures", list);
            return tag;
        }

        /** Le vehicule enregistre pour cette cle de place, ou null. */
        @Nullable
        public UUID car(String key) {
            return this.cars.get(key);
        }

        public long chunk(String key) {
            return this.chunks.getOrDefault(key, 0L);
        }

        /** Les cles de place enregistrees. */
        public List<String> rooms() {
            return List.copyOf(this.cars.keySet());
        }

        void put(String key, UUID car, long chunk) {
            this.cars.put(key, car);
            this.chunks.put(key, chunk);
            this.setDirty();
        }

        void updateChunk(String key, long chunk) {
            Long before = this.chunks.put(key, chunk);
            if (before == null || before != chunk) {
                this.setDirty();
            }
        }

        void remove(String key) {
            this.cars.remove(key);
            this.chunks.remove(key);
            this.setDirty();
        }
    }
}
