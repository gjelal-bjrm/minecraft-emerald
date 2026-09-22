package com.emerald.haven.fauna;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.invasion.HavenInvasionState;
import com.emerald.haven.invasion.HavenProtection;
import com.emerald.haven.invasion.HavenSpawner;
import com.emerald.haven.quest.HavenQuests;
import com.emerald.jak.gun.GunImpacts;
import com.emerald.jak.vehicle.VehicleImpacts;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.WolfVariant;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LES ANIMAUX DE HAVEN (22 sept., cahier §85).
 *
 * « Dans la ville, je n'ai pas vu d'animaux. » Le plan valide le 21 sept. (§79.4) et les
 * ajouts du joueur (§79.7) : des chats et des chiens, peu nombreux, en ville paisible ; des
 * MOUETTES sur les quais ; des POISSONS dans le bassin du port ; des DANGERS AU LARGE --
 * « pas seulement des requins, plein de choses dangereuses » -- ; et L'ARMEE DE MOUETTES :
 * qui en frappe une voit les autres fondre sur lui, comme les cocottes de Zelda.
 *
 * SIX ROLES, chacun a sa place (haven_fauna.json, tools/haven_fauna_map.py) :
 *
 *  - COMPAGNONS ({@link Role#PET}) : chats et chiens du jeu, apprivoises (collier de
 *    couleur) sauf un chat sur trois, autour de six lieux de la ville ({@link #PET_ZONES}),
 *    TREIZE en tout. Seulement EN VILLE PAISIBLE : a l'invasion, ils rentrent comme les
 *    habitants. Invulnerables, et aucune voiture ne les renverse (VehicleImpacts).
 *  - MOUETTES ({@link Role#GULL}, Alex's Mobs) : sur tous les quais, une pour
 *    {@value #GULL_QUAY_CELLS} cellules de quai. Frappees par un joueur, elles appellent
 *    l'armee (SeagullArmy) ; rien d'autre ne les blesse.
 *  - POISSONS ({@link Role#FISH}) : dans le bassin, un pour {@value #FISH_COLUMNS} colonnes,
 *    en bancs : morues, saumons, poissons tropicaux, poissons volants et ctenophores
 *    d'Alex's Mobs, harengs, lieus, merous, thons et fletans d'Aquaculture, hippocampes et
 *    raies manta de Living Things. Invulnerables, sauf a secher hors de l'eau.
 *  - RIVAGE ({@link Role#SHORE}) : phoques (Alex's Mobs) et crabes (Living Things) sur les
 *    pontons bas du bassin. Invulnerables.
 *  - DANGERS ({@link Role#DANGER}) : AU LARGE SEULEMENT, un pour {@value #DANGER_COLUMNS}
 *    colonnes : requins marteaux, requins lutins et squelettes de poisson (Alex's Mobs),
 *    requins (Living Things), meduses d'Aquaculture qui piquent. Ils ne ciblent qu'un
 *    joueur DANS L'EAU DU LARGE, a moins de {@value #AGGRO_RADIUS} blocs : qui nage trop
 *    loin se fait mordre ; le bassin reste sur. Le requin marteau TOURNE autour de sa
 *    proie dix-huit a vingt-deux secondes avant de mordre (son propre but, CirclePreyGoal) :
 *    on voit l'aileron, on a le temps de rentrer ; les autres attaquent tout de suite. Un danger qui entre dans le bassin est
 *    ramene a sa derniere place au large. Ils blessent (HavenRules) et se tuent.
 *  - ARMEE ({@link Role#ARMY}) : les mouettes de l'armee, le temps de sa colere. On ne les
 *    blesse pas -- sauf pendant la quete de l'armee de mouettes (cahier §86), qui est la
 *    leur : le tireur peut alors les abattre, et elles comptent.
 *
 * LA POPULATION suit la regle de la ville (HavenInvasion) : un quota par troncon,
 * compte par troncon de NAISSANCE, persistant, etiquete de la generation de la ville ;
 * refuse au rechargement s'il n'est plus de la ville d'aujourd'hui ({@link #welcome}).
 * Plafonds de ce qui est charge par role ({@link Role#loadedMax}) : les bancs de poissons
 * et les mouettes ne doivent pas faire ramer la ville. Les especes des mods absents sont
 * sautees : sans Alex's Mobs, ni mouettes ni armee.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenFauna {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** L'etiquette d'entite d'un animal de la ville (sauvegardee avec lui). */
    public static final String TAG = "emeraldweapons.haven_faune";
    /** La cle, dans les donnees persistantes, du role. */
    public static final String ROLE_KEY = "emeraldweapons.haven_role";
    /** La cle du lieu d'un compagnon (index dans PET_ZONES). */
    public static final String ZONE_KEY = "emeraldweapons.haven_lieu";

    public enum Role {
        PET("compagnon", 16, 16.0),
        GULL("mouette", 40, 16.0),
        FISH("poisson", 110, 24.0),
        SHORE("rivage", 12, 16.0),
        DANGER("danger", 30, 32.0),
        ARMY("armee", 64, 0.0);

        public final String id;
        /** Au plus tant d'animaux de ce role charges, quelle que soit la distance de vue. */
        public final int loadedMax;
        /** Aucune apparition a moins de ce rayon d'un joueur. */
        public final double spawnMin;

        Role(String id, int loadedMax, double spawnMin) {
            this.id = id;
            this.loadedMax = loadedMax;
            this.spawnMin = spawnMin;
        }

        @Nullable
        static Role byId(String id) {
            for (Role role : values()) {
                if (role.id.equals(id)) {
                    return role;
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------- densites

    /** Cellules de quai par mouette : 128 dans toute la ville. */
    public static final int GULL_QUAY_CELLS = 150;
    /** Colonnes du bassin par poisson : 1 340 dans le bassin. */
    public static final int FISH_COLUMNS = 160;
    /** Cellules de ponton bas par phoque ou crabe. */
    public static final int SHORE_QUAY_CELLS = 30;
    /** Colonnes du large par danger : 200 au large. */
    public static final int DANGER_COLUMNS = 2048;
    /** Un ponton bas : au plus tant de cellules au-dessus de la surface. */
    public static final int LOW_QUAY_RISE = 4;
    /** Apparitions au plus par seconde, tous roles confondus. */
    public static final int SPAWNS_PER_SECOND = 24;
    /** Un danger ne vise un joueur du large qu'a moins de ce rayon. */
    public static final double AGGRO_RADIUS = 24.0;
    /** Un compagnon plus loin que cela de son lieu s'en va (il reviendra chez lui, hors de vue). */
    public static final double PET_LEASH = 48.0;
    /** Un animal du rivage plus loin que cela de son troncon s'en va. */
    public static final double SHORE_LEASH = 40.0;
    /** Rayon d'un lieu de compagnons. */
    public static final double PET_RADIUS = 20.0;

    /** Un lieu de compagnons : un point d'eco de la carte de l'invasion, et combien de chats et de chiens. */
    public record PetZone(int eco, String name, int cats, int dogs) {
    }

    /** Six lieux, treize compagnons : « chats et chiens, sans abus ». */
    public static final List<PetZone> PET_ZONES = List.of(
            new PetZone(2, "rue des appartements", 2, 1),
            new PetZone(1, "devant le Hip Hog", 1, 1),
            new PetZone(3, "place du bras ouest", 1, 1),
            new PetZone(4, "arcades du bras ouest", 2, 0),
            new PetZone(7, "cour du bras central", 1, 1),
            new PetZone(10, "place du bras est", 1, 1));

    /** Une espece, son role, son poids au tirage et la taille de ses bancs. */
    public record Species(String id, Role role, int weight, int groupMin, int groupMax, boolean stings) {
    }

    /** Les especes. Celles d'un mod absent sont sautees. */
    public static final List<Species> SPECIES = List.of(
            // les mouettes
            new Species("alexsmobs:seagull", Role.GULL, 1, 1, 1, false),
            // le bassin
            new Species("minecraft:cod", Role.FISH, 10, 3, 5, false),
            new Species("minecraft:salmon", Role.FISH, 6, 2, 4, false),
            new Species("minecraft:tropical_fish", Role.FISH, 10, 3, 6, false),
            new Species("alexsmobs:flying_fish", Role.FISH, 5, 2, 4, false),
            new Species("alexsmobs:comb_jelly", Role.FISH, 3, 1, 2, false),
            new Species("alexsmobs:lobster", Role.FISH, 2, 1, 1, false),
            new Species("alexsmobs:mimic_octopus", Role.FISH, 1, 1, 1, false),
            new Species("aquaculture:atlantic_herring", Role.FISH, 6, 3, 5, false),
            new Species("aquaculture:pollock", Role.FISH, 3, 2, 3, false),
            new Species("aquaculture:red_grouper", Role.FISH, 2, 1, 1, false),
            new Species("aquaculture:tuna", Role.FISH, 1, 1, 1, false),
            new Species("aquaculture:atlantic_halibut", Role.FISH, 1, 1, 1, false),
            new Species("livingthings:seahorse", Role.FISH, 3, 1, 2, false),
            new Species("livingthings:mantaray", Role.FISH, 2, 1, 1, false),
            // le rivage
            new Species("alexsmobs:seal", Role.SHORE, 4, 1, 3, false),
            new Species("livingthings:crab", Role.SHORE, 4, 1, 2, false),
            // le large
            new Species("alexsmobs:hammerhead_shark", Role.DANGER, 5, 1, 1, false),
            new Species("livingthings:shark", Role.DANGER, 4, 1, 1, false),
            new Species("alexsmobs:frilled_shark", Role.DANGER, 2, 1, 1, false),
            new Species("alexsmobs:skelewag", Role.DANGER, 2, 1, 2, false),
            new Species("aquaculture:jellyfish", Role.DANGER, 3, 1, 3, true));

    /** La piqure d'une meduse : degats, et le delai avant la suivante sur le meme joueur. */
    public static final float STING_DAMAGE = 2.0F;
    private static final int STING_COOLDOWN = 20;
    /** Le rappel du large dans la barre d'action, au plus toutes les tant de tiques. */
    private static final int WARN_PERIOD = 20 * 60;

    // ------------------------------------------------------------- memoire volatile

    /** Derniere position au large de chaque danger charge. */
    private static final Map<UUID, Vec3> LAST_SEA = new HashMap<>();
    private static final Map<UUID, Long> STUNG = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    /** Les apparitions recentes, pour le banc (256 au plus). */
    static final List<Spawned> RECENT = new ArrayList<>();

    record Spawned(Role role, String species, Vec3 position, long tick) {
    }

    @Nullable
    private static Quotas quotas;
    @Nullable
    private static Map<Role, List<Resolved>> resolved;

    private HavenFauna() {
    }

    // ================================================================ lecture

    /** Un animal de la ville, dans Haven. */
    public static boolean isFauna(@Nullable Entity entity) {
        return entity instanceof Mob && entity.getTags().contains(TAG) && Haven.is(entity.level());
    }

    /** Le role d'un animal de la ville, ou null. */
    @Nullable
    public static Role role(@Nullable Entity entity) {
        if (entity == null || !entity.getTags().contains(TAG)) {
            return null;
        }
        return Role.byId(entity.getPersistentData().getString(ROLE_KEY));
    }

    /** Une mouette, des quais ou de l'armee (la quete du Pecheur les compte toutes les deux). */
    public static boolean gullOrArmy(@Nullable Entity entity) {
        Role role = role(entity);
        return role == Role.GULL || role == Role.ARMY;
    }

    /** Un danger du large (vise par les armes, blesse les joueurs). */
    public static boolean isDanger(@Nullable Entity entity) {
        return isFauna(entity) && role(entity) == Role.DANGER;
    }

    /** Un animal dont les coups portent sur un joueur : un danger du large, une mouette de l'armee. */
    public static boolean hurtsPlayers(@Nullable Entity entity) {
        if (!isFauna(entity)) {
            return false;
        }
        Role role = role(entity);
        return role == Role.DANGER || role == Role.ARMY;
    }

    /** Un animal que les vehicules ne renversent pas : tous sauf les dangers du large. */
    public static boolean sparedByVehicles(@Nullable Entity entity) {
        return isFauna(entity) && role(entity) != Role.DANGER;
    }

    /** Les animaux charges, d'un role ou de tous (null). */
    public static List<Mob> loaded(ServerLevel level, @Nullable Role role) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Mob.class),
                m -> m.getTags().contains(TAG) && !m.isRemoved() && (role == null || role(m) == role)));
    }

    /**
     * Un animal qui se recharge avec son troncon est-il de la ville d'aujourd'hui ?
     * Ville ouverte, generation courante ; un compagnon, seulement en ville paisible ;
     * une mouette de l'armee, jamais (sa colere est finie).
     */
    public static boolean welcome(ServerLevel level, Entity entity) {
        if (!entity.getTags().contains(TAG)) {
            return true;
        }
        Role role = role(entity);
        if (role == null || role == Role.ARMY || !HavenInvasion.cityOpen(level.getServer())) {
            return false;
        }
        if (!entity.getTags().contains(HavenInvasion.GENERATION_TAG + HavenInvasionState.get(level).generation())) {
            return false;
        }
        return role != Role.PET || HavenInvasion.mode(level.getServer()) == HavenInvasion.Mode.PAISIBLE;
    }

    // ================================================================ tique

    /** Une tique de la ville ouverte (appelee par HavenInvasion.update). */
    public static void tick(ServerLevel level, int ticks) {
        HavenFaunaData.Data data = HavenFaunaData.get(level.getServer());
        if (data == null) {
            return;
        }
        SeagullArmy.tick(level);
        if (ticks % 5 == 0) {
            sting(level);
        }
        if (ticks % 10 == 0) {
            sweep(level, data);
            aggression(level, data);
        }
        if (ticks % 20 == 7) {
            spawn(level, data);
        }
    }

    // ================================================================ especes

    private record Resolved(Species species, EntityType<?> type) {
    }

    /** Les especes presentes, par role (les mods absents sont sautes ; journal au premier appel). */
    static Map<Role, List<Resolved>> species() {
        if (resolved == null) {
            Map<Role, List<Resolved>> out = new EnumMap<>(Role.class);
            List<String> missing = new ArrayList<>();
            for (Species s : SPECIES) {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(s.id())).orElse(null);
                if (type == null) {
                    missing.add(s.id());
                    continue;
                }
                out.computeIfAbsent(s.role(), r -> new ArrayList<>()).add(new Resolved(s, type));
            }
            resolved = out;
            LOGGER.info("faune de Haven : {} especes presentes{}", SPECIES.size() - missing.size(),
                    missing.isEmpty() ? "" : ", absentes (mod manquant) : " + missing);
        }
        return resolved;
    }

    /** Les identifiants des especes presentes d'un role (pour le banc). */
    public static List<String> presentSpecies(Role role) {
        List<String> out = new ArrayList<>();
        for (Resolved r : species().getOrDefault(role, List.of())) {
            out.add(r.species().id());
        }
        return out;
    }

    @Nullable
    private static Resolved pick(Role role, RandomSource random) {
        List<Resolved> list = species().get(role);
        if (list == null || list.isEmpty()) {
            return null;
        }
        int total = 0;
        for (Resolved r : list) {
            total += r.species().weight();
        }
        int roll = random.nextInt(total);
        for (Resolved r : list) {
            roll -= r.species().weight();
            if (roll < 0) {
                return r;
            }
        }
        return list.get(list.size() - 1);
    }

    // ================================================================ quotas

    /** Les quotas par troncon du monde, et les places, pour une carte et une origine. */
    static final class Quotas {
        final HavenFaunaData.Data data;
        final HavenInvasionData.Data invasion;
        final BlockPos origin;
        final Map<Long, HavenFaunaData.Tile> tiles = new HashMap<>();
        final Map<Role, Map<Long, Integer>> want = new EnumMap<>(Role.class);
        /** Les pontons bas du bassin, par troncon (cellules empaquetees). */
        final Map<Long, int[]> lowQuays = new HashMap<>();
        /** Les places de chaque lieu de compagnons, en coordonnees du monde. */
        final List<List<BlockPos>> petCells = new ArrayList<>();
        final List<BlockPos> petCenters = new ArrayList<>();

        Quotas(HavenFaunaData.Data data, HavenInvasionData.Data invasion, BlockPos origin) {
            this.data = data;
            this.invasion = invasion;
            this.origin = origin;
            for (Role role : Role.values()) {
                this.want.put(role, new HashMap<>());
            }
            for (HavenFaunaData.Tile tile : data.list()) {
                long key = chunkKey(origin, tile.tx(), tile.tz());
                this.tiles.put(key, tile);
                this.want.get(Role.GULL).put(key, quota(tile.tx(), tile.tz(), tile.quays().length, GULL_QUAY_CELLS, 1));
                this.want.get(Role.FISH).put(key, quota(tile.tx(), tile.tz(), tile.basin().length, FISH_COLUMNS, 2));
                this.want.get(Role.DANGER).put(key, quota(tile.tx(), tile.tz(), tile.sea().length, DANGER_COLUMNS, 3));
                List<Integer> low = new ArrayList<>();
                for (int i = 0; i < tile.quays().length; i++) {
                    int cell = tile.quays()[i];
                    if (tile.quaySide()[i] == 1 && HavenFaunaData.unpackY(cell) <= data.surface() + LOW_QUAY_RISE) {
                        low.add(cell);
                    }
                }
                if (!low.isEmpty()) {
                    this.lowQuays.put(key, low.stream().mapToInt(Integer::intValue).toArray());
                    this.want.get(Role.SHORE).put(key, quota(tile.tx(), tile.tz(), low.size(), SHORE_QUAY_CELLS, 4));
                }
            }
            for (PetZone zone : PET_ZONES) {
                List<BlockPos> cells = new ArrayList<>();
                BlockPos center = BlockPos.ZERO;
                for (HavenInvasionData.EcoPoint point : invasion.ecoPoints()) {
                    if (point.number() == zone.eco()) {
                        center = point.feet();
                    }
                }
                for (HavenInvasionData.GroundTile tile : invasion.groundTiles()) {
                    double tx = tile.tx() * HavenInvasionData.TILE + 8.0 - center.getX();
                    double tz = tile.tz() * HavenInvasionData.TILE + 8.0 - center.getZ();
                    if (Math.sqrt(tx * tx + tz * tz) > PET_RADIUS + HavenInvasionData.TILE) {
                        continue;
                    }
                    for (int cell : tile.cells()) {
                        int x = HavenInvasionData.unpackX(cell);
                        int y = HavenInvasionData.unpackY(cell);
                        int z = HavenInvasionData.unpackZ(cell);
                        double dx = x - center.getX();
                        double dz = z - center.getZ();
                        if (Math.sqrt(dx * dx + dz * dz) <= PET_RADIUS && Math.abs(y - center.getY()) <= 4) {
                            cells.add(origin.offset(x, y, z));
                        }
                    }
                }
                this.petCells.add(cells);
                this.petCenters.add(origin.offset(center));
            }
        }
    }

    /** Le quota d'une tuile : sa surface divisee par {@code per}, arrondi par un hasard fixe (voir HavenInvasion.quota). */
    static int quota(int tx, int tz, int surface, int per, int salt) {
        if (surface <= 0) {
            return 0;
        }
        long h = tx * 0x9E3779B97F4A7C15L + tz * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        double frac = (h & 0xFFFFFFL) / (double) 0x1000000;
        return (int) Math.floor(surface / (double) per + frac);
    }

    static long chunkKey(BlockPos origin, int tx, int tz) {
        return ChunkPos.asLong(SectionPos.blockToSectionCoord(origin.getX() + tx * HavenFaunaData.TILE),
                SectionPos.blockToSectionCoord(origin.getZ() + tz * HavenFaunaData.TILE));
    }

    @Nullable
    static Quotas quotas(MinecraftServer server) {
        HavenFaunaData.Data data = HavenFaunaData.get(server);
        HavenInvasionData.Data invasion = HavenInvasionData.get(server);
        if (data == null || invasion == null) {
            return null;
        }
        BlockPos origin = HavenState.get(server).origin();
        if (quotas == null || quotas.data != data || quotas.invasion != invasion || !quotas.origin.equals(origin)) {
            quotas = new Quotas(data, invasion, origin);
        }
        return quotas;
    }

    // ================================================================ apparitions

    private record Deficit(Role role, long key, int missing, double distance) {
    }

    private static void spawn(ServerLevel level, HavenFaunaData.Data data) {
        MinecraftServer server = level.getServer();
        Quotas q = quotas(server);
        List<Vec3> anchors = HavenInvasion.anchors(level);
        if (q == null || anchors.isEmpty()) {
            return;
        }
        String generation = HavenInvasion.generationTag(level);
        boolean peaceful = HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE;
        Map<Role, Map<Long, Integer>> counts = new EnumMap<>(Role.class);
        Map<Role, Integer> loaded = new EnumMap<>(Role.class);
        int[] petCounts = new int[PET_ZONES.size() * 2];
        for (Role role : Role.values()) {
            counts.put(role, new HashMap<>());
            loaded.put(role, 0);
        }
        for (Mob mob : loaded(level, null)) {
            Role role = role(mob);
            if (role == null) {
                continue;
            }
            loaded.merge(role, 1, Integer::sum);
            counts.get(role).merge(HavenInvasion.homeOf(mob), 1, Integer::sum);
            if (role == Role.PET && mob.getPersistentData().contains(ZONE_KEY)) {
                int zone = mob.getPersistentData().getInt(ZONE_KEY);
                if (zone >= 0 && zone < PET_ZONES.size()) {
                    petCounts[zone * 2 + (mob instanceof Wolf ? 1 : 0)]++;
                }
            }
        }
        int budget = SPAWNS_PER_SECOND;
        long now = level.getGameTime();

        // les compagnons : lieu par lieu, en ville paisible seulement
        if (peaceful) {
            for (int z = 0; z < PET_ZONES.size() && budget > 0; z++) {
                PetZone zone = PET_ZONES.get(z);
                BlockPos center = q.petCenters.get(z);
                if (!level.isLoaded(center) || !level.areEntitiesLoaded(ChunkPos.asLong(center))) {
                    continue;
                }
                for (int dog = 0; dog <= 1 && budget > 0; dog++) {
                    int want = dog == 1 ? zone.dogs() : zone.cats();
                    for (int i = petCounts[z * 2 + dog]; i < want && budget > 0; i++) {
                        if (loaded.get(Role.PET) >= Role.PET.loadedMax) {
                            break;
                        }
                        BlockPos feet = groundSpot(level, q.petCells.get(z), anchors, Role.PET.spawnMin, level.random);
                        if (feet == null) {
                            break;
                        }
                        Mob mob = create(level, dog == 1 ? EntityType.WOLF : EntityType.CAT, Vec3.atBottomCenterOf(feet),
                                Role.PET, ChunkPos.asLong(feet), generation);
                        if (mob != null) {
                            mob.getPersistentData().putInt(ZONE_KEY, z);
                            loaded.merge(Role.PET, 1, Integer::sum);
                            budget--;
                            record(new Spawned(Role.PET, dog == 1 ? "minecraft:wolf" : "minecraft:cat", mob.position(), now));
                        }
                    }
                }
            }
        }

        // les autres : troncons charges sous leur quota, les plus proches d'abord
        List<Deficit> deficits = new ArrayList<>();
        for (Role role : List.of(Role.GULL, Role.SHORE, Role.FISH, Role.DANGER)) {
            if (species().getOrDefault(role, List.of()).isEmpty() || loaded.get(role) >= role.loadedMax) {
                continue;
            }
            for (Map.Entry<Long, Integer> entry : q.want.get(role).entrySet()) {
                long key = entry.getKey();
                int have = counts.get(role).getOrDefault(key, 0);
                if (entry.getValue() <= have) {
                    continue;
                }
                if (!level.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)) || !level.areEntitiesLoaded(key)) {
                    continue;
                }
                double cx = (ChunkPos.getX(key) << 4) + 8.0;
                double cz = (ChunkPos.getZ(key) << 4) + 8.0;
                double distance = HavenInvasion.nearestDistance(anchors, cx, cz);
                if (distance + 12.0 < role.spawnMin) {
                    continue;
                }
                deficits.add(new Deficit(role, key, entry.getValue() - have, distance));
            }
        }
        deficits.sort(Comparator.comparingDouble(Deficit::distance));
        for (Deficit deficit : deficits) {
            if (budget <= 0) {
                break;
            }
            Role role = deficit.role();
            HavenFaunaData.Tile tile = q.tiles.get(deficit.key());
            if (tile == null || loaded.get(role) >= role.loadedMax) {
                continue;
            }
            Resolved species = pick(role, level.random);
            if (species == null) {
                continue;
            }
            int group = Math.min(deficit.missing(), species.species().groupMin()
                    + level.random.nextInt(species.species().groupMax() - species.species().groupMin() + 1));
            Vec3 at = switch (role) {
                case GULL -> quaySpot(level, q, tile.quays(), anchors, role.spawnMin, level.random);
                case SHORE -> quaySpot(level, q, q.lowQuays.getOrDefault(deficit.key(), new int[0]), anchors, role.spawnMin, level.random);
                case FISH -> waterSpot(level, q, tile, true, anchors, role.spawnMin, level.random);
                case DANGER -> waterSpot(level, q, tile, false, anchors, role.spawnMin, level.random);
                default -> null;
            };
            if (at == null) {
                continue;
            }
            // le plafond compte AUSSI dans un banc : un banc de cinq le depassait (banc du 22 sept.)
            for (int i = 0; i < Math.max(1, group) && budget > 0 && loaded.get(role) < role.loadedMax; i++) {
                Vec3 pos = i == 0 ? at : near(level, at, role, anchors, level.random);
                if (pos == null) {
                    break;
                }
                Mob mob = create(level, species.type(), pos, role, deficit.key(), generation);
                if (mob != null) {
                    loaded.merge(role, 1, Integer::sum);
                    budget--;
                    record(new Spawned(role, species.species().id(), mob.position(), now));
                }
            }
        }
    }

    /** Une place au sol dans une liste de cellules du monde : hors zone sure, loin des joueurs, hors de leur vue. */
    @Nullable
    private static BlockPos groundSpot(ServerLevel level, List<BlockPos> cells, List<Vec3> anchors, double min,
                                       RandomSource random) {
        if (cells.isEmpty()) {
            return null;
        }
        for (int i = 0; i < 12; i++) {
            BlockPos feet = cells.get(random.nextInt(cells.size()));
            if (fitsGround(level, feet, anchors, min)) {
                return feet;
            }
        }
        return null;
    }

    private static boolean fitsGround(ServerLevel level, BlockPos feet, List<Vec3> anchors, double min) {
        double x = feet.getX() + 0.5;
        double z = feet.getZ() + 0.5;
        double nearest = HavenInvasion.nearestDistance(anchors, x, z);
        if (nearest < min || !HavenSpawner.standable(level, feet)) {
            return false;
        }
        return nearest > HavenInvasion.SIGHT_RADIUS || !HavenSpawner.seen(level, anchors, feet, HavenInvasion.SIGHT_RADIUS);
    }

    @Nullable
    private static Vec3 quaySpot(ServerLevel level, Quotas q, int[] cells, List<Vec3> anchors, double min, RandomSource random) {
        if (cells.length == 0) {
            return null;
        }
        for (int i = 0; i < 12; i++) {
            int cell = cells[random.nextInt(cells.length)];
            BlockPos feet = q.origin.offset(HavenFaunaData.unpackX(cell), HavenFaunaData.unpackY(cell), HavenFaunaData.unpackZ(cell));
            if (fitsGround(level, feet, anchors, min)) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return null;
    }

    /** Une place dans l'eau d'une tuile, sous la surface, loin des joueurs (l'eau cache le reste). */
    @Nullable
    private static Vec3 waterSpot(ServerLevel level, Quotas q, HavenFaunaData.Tile tile, boolean basin, List<Vec3> anchors,
                                  double min, RandomSource random) {
        short[] columns = basin ? tile.basin() : tile.sea();
        if (columns.length == 0) {
            return null;
        }
        for (int i = 0; i < 12; i++) {
            short packed = columns[random.nextInt(columns.length)];
            int dx = packed & 0xF;
            int dz = (packed >> 4) & 0xF;
            int depth = tile.depth(dx, dz);
            if (depth < 2) {
                continue;
            }
            int cellY = q.data.surface() - 1 - random.nextInt(depth - 1);
            BlockPos pos = q.origin.offset(tile.tx() * HavenFaunaData.TILE + dx, cellY, tile.tz() * HavenFaunaData.TILE + dz);
            double x = pos.getX() + 0.5;
            double z = pos.getZ() + 0.5;
            if (HavenInvasion.nearestDistance(anchors, x, z) < min || !level.isLoaded(pos)
                    || !level.getFluidState(pos).is(FluidTags.WATER)) {
                continue;
            }
            return new Vec3(x, pos.getY() + 0.1, z);
        }
        return null;
    }

    /** Un compagnon de banc, a un ou deux blocs du premier, dans le meme element -- et lui aussi assez loin des joueurs. */
    @Nullable
    private static Vec3 near(ServerLevel level, Vec3 at, Role role, List<Vec3> anchors, RandomSource random) {
        for (int i = 0; i < 6; i++) {
            Vec3 pos = at.add(random.nextInt(5) - 2, role == Role.FISH || role == Role.DANGER ? random.nextInt(3) - 1 : 0,
                    random.nextInt(5) - 2);
            BlockPos block = BlockPos.containing(pos);
            if (!level.isLoaded(block) || HavenInvasion.nearestDistance(anchors, pos.x, pos.z) < role.spawnMin) {
                continue;
            }
            if (role == Role.FISH || role == Role.DANGER) {
                if (level.getFluidState(block).is(FluidTags.WATER)) {
                    return pos;
                }
            } else if (HavenSpawner.standable(level, block)) {
                return Vec3.atBottomCenterOf(block);
            }
        }
        return null;
    }

    /**
     * Fait naitre un animal de la ville : les reglages d'une apparition du jeu
     * (finalizeSpawn : couleurs, varietes), puis persistant, etiquete, prepare a son role.
     */
    @Nullable
    static Mob create(ServerLevel level, EntityType<?> type, Vec3 at, Role role, long home, String generation) {
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        mob.addTag(generation);
        mob.getPersistentData().putString(ROLE_KEY, role.id);
        mob.getPersistentData().putLong(HavenInvasion.HOME_KEY, home);
        if (role == Role.PET) {
            dress(level, mob);
        }
        prepare(mob, role);
        return level.addFreshEntity(mob) ? mob : null;
    }

    /**
     * Les buts d'un animal selon son role, a la naissance et a chaque rechargement (le jeu
     * les recree avec l'entite) : les dangers et le rivage perdent leurs cibles d'eux-memes --
     * les requins ne visent qu'un joueur du large, par la ville ({@link #aggression}) ; les
     * phoques ne chassent plus les poissons volants, invulnerables -- et l'armee perd tout,
     * elle vole ou la mene SeagullArmy.
     */
    static void prepare(Mob mob, Role role) {
        switch (role) {
            case DANGER, SHORE -> mob.targetSelector.removeAllGoals(goal -> true);
            case ARMY -> {
                mob.goalSelector.removeAllGoals(goal -> true);
                mob.targetSelector.removeAllGoals(goal -> true);
                mob.setInvulnerable(true);
                call(mob, "setFlying", true);
            }
            case PET -> {
                mob.setInvulnerable(true);
                // UN ANIMAL APPRIVOISE SANS MAITRE RESTE ASSIS POUR TOUJOURS : le but « assis sur
                // ordre » du jeu le fait asseoir quand son maitre est introuvable (vu en photo :
                // le chien assis au milieu de la rue). Sans lui, chiens et chats se promenent ;
                // les chats gardent l'envie de s'asseoir sur un coffre ou un four.
                mob.goalSelector.removeAllGoals(goal -> goal instanceof net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal);
                if (mob instanceof TamableAnimal tame) {
                    tame.setOrderedToSit(false);
                    tame.setInSittingPose(false);
                }
            }
            default -> {
            }
        }
    }

    /** Un compagnon : chien apprivoise (collier), chat apprivoise deux fois sur trois ; varietes au hasard. */
    private static void dress(ServerLevel level, Mob mob) {
        RandomSource random = level.random;
        DyeColor collar = DyeColor.values()[random.nextInt(DyeColor.values().length)];
        if (mob instanceof Wolf wolf) {
            Registry<WolfVariant> variants = level.registryAccess().registryOrThrow(Registries.WOLF_VARIANT);
            variants.getRandom(random).ifPresent(wolf::setVariant);
            wolf.setTame(true, true);
            collar(wolf, collar);
        } else if (mob instanceof Cat cat) {
            BuiltInRegistries.CAT_VARIANT.getRandom(random).ifPresent(cat::setVariant);
            if (random.nextInt(3) != 0) {
                cat.setTame(true, true);
                collar(cat, collar);
            }
        }
    }

    /** La couleur du collier : son setter est prive, la sauvegarde la lit. */
    private static void collar(TamableAnimal animal, DyeColor color) {
        CompoundTag tag = new CompoundTag();
        animal.addAdditionalSaveData(tag);
        tag.putByte("CollarColor", (byte) color.getId());
        animal.readAdditionalSaveData(tag);
    }

    private static final Map<String, Method> METHODS = new HashMap<>();

    /** Appelle une methode publique d'un mod qu'on ne compile pas (setFlying, peck des mouettes). */
    static void call(Object target, String name, Object... args) {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            types[i] = args[i] instanceof Boolean ? boolean.class : args[i].getClass();
        }
        String key = target.getClass().getName() + "#" + name + "/" + args.length;
        try {
            Method method = METHODS.get(key);
            if (method == null) {
                if (METHODS.containsKey(key)) {
                    return;
                }
                method = target.getClass().getMethod(name, types);
                METHODS.put(key, method);
            }
            method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            METHODS.put(key, null);
        }
    }

    private static void record(Spawned spawned) {
        RECENT.add(spawned);
        if (RECENT.size() > 256) {
            RECENT.remove(0);
        }
    }

    // ================================================================ balayage

    /** Toutes les demi-secondes : hors de la grille, dangers ramenes au large, compagnons et rivage tenus pres de chez eux. */
    private static void sweep(ServerLevel level, HavenFaunaData.Data data) {
        MinecraftServer server = level.getServer();
        BlockPos origin = HavenState.get(server).origin();
        Quotas q = quotas(server);
        List<UUID> seen = new ArrayList<>();
        for (Mob mob : loaded(level, null)) {
            Role role = role(mob);
            double x = mob.getX() - origin.getX();
            double z = mob.getZ() - origin.getZ();
            if (role == null || x < 0 || z < 0 || x >= data.width() || z >= data.depth()
                    || mob.getY() > origin.getY() + data.height() + 48 || mob.getY() < origin.getY() + data.surface() - 12) {
                mob.discard();
                continue;
            }
            if (mob.isOnFire()) {
                mob.clearFire();
            }
            switch (role) {
                case DANGER -> {
                    seen.add(mob.getUUID());
                    HavenFaunaData.Water water = data.waterAt(origin, mob.getX(), mob.getZ());
                    if (water == HavenFaunaData.Water.OPEN_SEA) {
                        LAST_SEA.put(mob.getUUID(), mob.position());
                    } else if (water == HavenFaunaData.Water.BASIN
                            || (water == HavenFaunaData.Water.NONE && basinSide(data, origin, mob.getX(), mob.getZ()))) {
                        Vec3 back = LAST_SEA.get(mob.getUUID());
                        if (back == null) {
                            mob.discard();
                        } else {
                            mob.getNavigation().stop();
                            mob.setTarget(null);
                            mob.setDeltaMovement(Vec3.ZERO);
                            mob.teleportTo(back.x, back.y, back.z);
                        }
                    }
                }
                case PET -> {
                    int zone = mob.getPersistentData().getInt(ZONE_KEY);
                    if (q != null && zone >= 0 && zone < q.petCenters.size()) {
                        BlockPos center = q.petCenters.get(zone);
                        if (Math.hypot(mob.getX() - center.getX(), mob.getZ() - center.getZ()) > PET_LEASH) {
                            mob.discard();
                        }
                    }
                }
                case SHORE -> {
                    long home = HavenInvasion.homeOf(mob);
                    double hx = (ChunkPos.getX(home) << 4) + 8.0;
                    double hz = (ChunkPos.getZ(home) << 4) + 8.0;
                    if (Math.hypot(mob.getX() - hx, mob.getZ() - hz) > SHORE_LEASH) {
                        mob.discard();
                    }
                }
                case ARMY -> {
                    if (!SeagullArmy.enlisted(mob)) {
                        SeagullArmy.dismiss(level, mob);
                    }
                }
                default -> {
                }
            }
        }
        LAST_SEA.keySet().retainAll(seen);
    }

    /**
     * Sous un quai ou un ponton DU BASSIN : l'eau y est couverte (ni bassin ni large sur la
     * carte), mais le bassin est a quatre blocs et le large nulle part. Un requin s'y
     * glissait depuis le bord du bassin sans etre ramene (banc du 22 sept.).
     */
    static boolean basinSide(HavenFaunaData.Data data, BlockPos origin, double x, double z) {
        int cx = (int) Math.floor(x) - origin.getX();
        int cz = (int) Math.floor(z) - origin.getZ();
        boolean basin = false;
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                HavenFaunaData.Water w = data.water(cx + dx, cz + dz);
                if (w == HavenFaunaData.Water.OPEN_SEA) {
                    return false;
                }
                basin |= w == HavenFaunaData.Water.BASIN;
            }
        }
        return basin;
    }

    // ================================================================ le large

    /**
     * Les dangers ne visent qu'un joueur DANS L'EAU DU LARGE, a moins de
     * {@value #AGGRO_RADIUS} blocs ; un joueur sorti de l'eau, ou rentre au bassin, est
     * lache. Et le joueur qui entre au large en est prevenu (une fois par minute).
     */
    private static void aggression(ServerLevel level, HavenFaunaData.Data data) {
        List<Mob> dangers = loaded(level, Role.DANGER);
        BlockPos origin = HavenState.get(level.getServer()).origin();
        for (Mob danger : dangers) {
            if (danger.getTarget() instanceof Player target && !exposed(level, data, origin, target)) {
                danger.setTarget(null);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !exposed(level, data, origin, player)) {
                continue;
            }
            warn(level, player);
            aggro(dangers, player);
        }
    }

    /** Un joueur dans l'eau du large, hors zone sure. */
    static boolean exposed(ServerLevel level, HavenFaunaData.Data data, BlockPos origin, Player player) {
        return player.isAlive() && player.isInWater() && player.level() == level
                && data.waterAt(origin, player.getX(), player.getZ()) == HavenFaunaData.Water.OPEN_SEA
                && !HavenProtection.inSafeZone(level.getServer(), player.getX(), player.getY(), player.getZ());
    }

    /** Les dangers libres a portee se tournent vers ce joueur. */
    static int aggro(List<Mob> dangers, Player player) {
        int count = 0;
        for (Mob danger : dangers) {
            if (danger.getTarget() == null && danger.isInWater()
                    && danger.distanceToSqr(player) <= AGGRO_RADIUS * AGGRO_RADIUS) {
                danger.setTarget(player);
                count++;
            }
        }
        return count;
    }

    /** Pour le banc : l'agressivite pour un joueur simule (un FakePlayer n'est pas dans level.players()). */
    public static int aggroForTest(ServerLevel level, Player player) {
        HavenFaunaData.Data data = HavenFaunaData.get(level.getServer());
        BlockPos origin = HavenState.get(level.getServer()).origin();
        List<Mob> dangers = loaded(level, Role.DANGER);
        if (data == null || !exposed(level, data, origin, player)) {
            int released = 0;
            for (Mob danger : dangers) {
                if (danger.getTarget() == player) {
                    danger.setTarget(null);
                    released++;
                }
            }
            return -released;
        }
        return aggro(dangers, player);
    }

    private static void warn(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        Long last = WARNED.get(player.getUUID());
        if (last != null && now - last < WARN_PERIOD) {
            return;
        }
        WARNED.put(player.getUUID(), now);
        player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.faune.large")
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** Les meduses piquent qui les touche : {@value #STING_DAMAGE} points et un ralentissement. */
    private static void sting(ServerLevel level) {
        long now = level.getGameTime();
        for (Mob mob : loaded(level, Role.DANGER)) {
            if (!stings(mob)) {
                continue;
            }
            AABB box = mob.getBoundingBox().inflate(0.35);
            for (Player player : level.getEntitiesOfClass(Player.class, box, p -> p.isAlive() && !p.isSpectator())) {
                stingPlayer(level, mob, player, now);
            }
        }
    }

    static boolean stings(Mob mob) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        for (Species s : SPECIES) {
            if (s.stings() && s.id().equals(id.toString())) {
                return true;
            }
        }
        return false;
    }

    static boolean stingPlayer(ServerLevel level, Mob jelly, Player player, long now) {
        Long last = STUNG.get(player.getUUID());
        if (last != null && now - last < STING_COOLDOWN) {
            return false;
        }
        STUNG.put(player.getUUID(), now);
        boolean hurt = player.hurt(level.damageSources().mobAttack(jelly), STING_DAMAGE);
        if (hurt) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            level.playSound(null, player.blockPosition(), SoundEvents.PUFFER_FISH_STING, SoundSource.HOSTILE, 0.8F, 1.3F);
        }
        return hurt;
    }

    // ================================================================ retraits

    /** Retire tous les animaux charges (fermeture de la ville, pose). */
    public static int removeAll(ServerLevel level) {
        SeagullArmy.clear(level);
        int removed = 0;
        for (Mob mob : loaded(level, null)) {
            mob.discard();
            removed++;
        }
        LAST_SEA.clear();
        return removed;
    }

    /** A l'invasion, les compagnons rentrent. */
    public static int removePets(ServerLevel level) {
        int removed = 0;
        for (Mob mob : loaded(level, Role.PET)) {
            mob.discard();
            removed++;
        }
        return removed;
    }

    // ================================================================ evenements

    /** Un animal qui se recharge : accepte s'il est de la ville d'aujourd'hui, et ses buts refaits. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() || event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)
                || !Haven.is(level) || !(event.getEntity() instanceof Mob mob) || !mob.getTags().contains(TAG)) {
            return;
        }
        if (!welcome(level, mob)) {
            event.setCanceled(true);
            return;
        }
        Role role = role(mob);
        if (role != null) {
            prepare(mob, role);
        }
    }

    /**
     * Les coups sur un animal de la ville : compagnons, rivage et armee n'en prennent
     * aucun ; les poissons seulement le sechage (un poisson echoue meurt, un autre le
     * remplacera) ; une mouette seulement d'un joueur -- et elle appelle l'armee ; les
     * dangers tout. /kill et le vide passent toujours.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || !(target.level() instanceof ServerLevel level) || !isFauna(target)) {
            return;
        }
        DamageSource source = event.getSource();
        // /kill et le vide passent ; le choc d'un vehicule aussi passe outre l'invulnerabilite,
        // mais il ne doit renverser aucun animal de la ville (VehicleImpacts les ecarte deja)
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(VehicleImpacts.DAMAGE_TYPE)) {
            return;
        }
        Role role = role(target);
        if (role == null) {
            return;
        }
        // le tireur de la quete des mouettes, s'il y en a un derriere ce coup
        ServerPlayer hunter = source.getEntity() instanceof ServerPlayer p ? p
                : source.is(GunImpacts.DAMAGE_TYPE) && target.getLastHurtByMob() instanceof ServerPlayer shooter ? shooter
                : null;
        switch (role) {
            case PET, SHORE -> event.setCanceled(true);
            case ARMY -> {
                // LA COLERE DES MOUETTES NE SE COMBAT PAS -- sauf pendant la quete du Pecheur,
                // qui est justement celle de l'armee : la, elles s'abattent comme les autres.
                if (hunter == null || hunter.isSpectator() || !HavenQuests.gullHunt()) {
                    event.setCanceled(true);
                }
            }
            case FISH -> {
                if (!source.is(DamageTypes.DRY_OUT)) {
                    event.setCanceled(true);
                }
            }
            case GULL -> {
                // un coup de joueur ; ou un tir du Morph Gun, qui n'a pas d'auteur mais laisse le
                // tireur en dernier agresseur (GunImpacts.hurt) -- il ne vise les mouettes que
                // pendant la quete de l'armee de mouettes
                if (hunter != null && !hunter.isSpectator()) {
                    SeagullArmy.trigger(level, hunter, (Mob) target);
                } else {
                    event.setCanceled(true);
                }
            }
            case DANGER -> {
            }
        }
    }

    /** Ni butin ni experience. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (isFauna(event.getEntity())) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isFauna(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Pas de petits : la ville tient ses comptes. */
    @SubscribeEvent
    public static void onBaby(BabyEntitySpawnEvent event) {
        if (isFauna(event.getParentA()) || isFauna(event.getParentB())) {
            event.setCanceled(true);
        }
    }

    /** Ni seau, ni laisse, ni nourriture, ni apprivoisement : on regarde les animaux de la ville. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide() && isFauna(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide() && isFauna(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SEA.clear();
        STUNG.clear();
        WARNED.clear();
        RECENT.clear();
        quotas = null;
        resolved = null;
        SeagullArmy.reset();
    }

    /** Pour le banc : les places d'un lieu de compagnons (coordonnees du monde). */
    static List<BlockPos> petCellsForTest(MinecraftServer server, int zone) {
        Quotas q = quotas(server);
        return q == null ? List.of() : q.petCells.get(zone);
    }

    /** Pour le banc : le quota total d'un role sur toute la ville. */
    static int totalQuota(MinecraftServer server, Role role) {
        Quotas q = quotas(server);
        if (q == null) {
            return 0;
        }
        if (role == Role.PET) {
            int n = 0;
            for (PetZone zone : PET_ZONES) {
                n += zone.cats() + zone.dogs();
            }
            return n;
        }
        int total = 0;
        for (int v : q.want.get(role).values()) {
            total += v;
        }
        return total;
    }

    /** Pour le banc : l'eau sous une position du monde. */
    static HavenFaunaData.Water waterAt(MinecraftServer server, double x, double z) {
        HavenFaunaData.Data data = HavenFaunaData.get(server);
        return data == null ? HavenFaunaData.Water.NONE : data.waterAt(HavenState.get(server).origin(), x, z);
    }
}
