package com.emerald.haven.invasion;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.haven.traffic.HavenTraffic;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L'invasion de Haven : les monstres dans la ville, ou les habitants quand elle est paisible.
 *
 * API POUR LES AUTRES CHANTIERS :
 * - {@link #mode(MinecraftServer)} : INVASION ou PAISIBLE ; {@link #invasionActive} : ville ouverte ET invasion ;
 * - {@link #isHavenMonster(Entity)} et {@link #kindOf(Entity)} : reconnaitre un monstre de l'invasion ;
 * - {@link HavenMonsterKilledEvent} sur NeoForge.EVENT_BUS : un monstre de Haven est mort (son butin vanilla
 *   et son experience sont supprimes ; c'est la qu'on lache les munitions) ;
 * - {@link #isHavenVillager(Entity)} : un habitant de la ville paisible (invulnerable, sans commerce) ;
 * - {@link #loadedMonsters} et {@link #loadedVillagers} : ce qui est charge en ce moment.
 *
 * LE MODE est sauvegarde (HavenInvasionState). On arrive en INVASION : chaque
 * reouverture du lobby le remet. Le bouton du QG le bascule pour toute la ville.
 *
 * LA VILLE OUVERTE ({@link #cityOpen}) : lobby ouvert, ville posee, aucune pose en
 * cours. Hors de la, rien n'apparait, et tout ce qui etait la est retire.
 *
 * LA POPULATION EST CELLE DE TOUTE LA VILLE, PAS D'UNE BULLE AUTOUR DU JOUEUR
 * (refonte du 16 sept.). Le premier reglage faisait apparaitre les monstres a 16-40
 * blocs des joueurs et les retirait a 72 : « ils apparaissent a cote de moi, mais
 * quand je me deplace en voiture un peu plus loin dans la ville, les rues sont
 * vides ». A 40 m/s, on traverse une telle bulle en une seconde. Desormais chaque
 * tuile au sol de la carte -- un troncon du monde -- a un QUOTA tire de sa surface
 * praticable ({@link #quota} : une cellule sur {@link #MONSTER_CELLS} pour les
 * monstres, sur {@link #VILLAGER_CELLS} pour les habitants, arrondi par un hasard
 * fixe de la tuile pour que les petites rues ne restent pas toutes vides). Chaque
 * entite compte pour le troncon ou elle est NEE ({@link #homeOf}, sauvegarde avec
 * elle), pas pour celui ou elle se tient. Chaque seconde, les troncons CHARGES
 * (blocs et entites) sous leur quota sont completes,
 * les plus loin des joueurs d'abord -- ils viennent d'apparaitre au bord de la vue --,
 * a {@link #SPAWNS_PER_SECOND} apparitions au plus. Une apparition se fait a plus de
 * {@link #SPAWN_MIN} blocs de tout joueur, hors de sa vue a moins de
 * {@link #SIGHT_RADIUS}, hors des centres d'exclusion (portes, entree du bar) et des
 * zones sures, sur un sol plein (HavenSpawner). Sans aucun joueur dans la ville,
 * rien n'apparait.
 *
 * LES ENTITES SONT PERSISTANTES : le jeu ne les retire jamais lui-meme
 * (setPersistenceRequired : ni au loin, ni au repos ; Mob.checkDespawn remet alors
 * noActionTime a zero a chaque tique, donc les monstres flanent au soleil fige au
 * lieu de rester plantes). Loin des joueurs, leurs troncons se dechargent avec elles ;
 * quand on revient, elles sont la ou on les a laissees. Un monstre ou un habitant
 * qui se recharge n'est accepte que s'il est de la ville d'aujourd'hui ({@link #welcome} :
 * ville ouverte, generation courante, espece du mode) ; sinon il est refuse. La
 * generation change a chaque retrait general (fermeture, pose), pour que ce qui
 * dormait dans un troncon decharge ne revienne jamais. La ville ne retire elle-meme
 * que ce qui sort de la grille ; tout au changement de mode, a la fermeture ou a une
 * pose (troncons charges ; les autres au rechargement). Garde-fou :
 * {@link #LOADED_MAX_MONSTERS} et {@link #LOADED_MAX_VILLAGERS} entites chargees,
 * quelle que soit la distance de vue.
 *
 * LES PHANTOMS restent autour des joueurs : {@link #PHANTOMS_PER_PLAYER} par joueur,
 * chacun comptant pour le joueur le plus proche, dans leur bande de vol.
 *
 * LE SOLEIL FIGE NE BRULE PAS : Mob.isSunBurnTick exige level.isDay(), et
 * Level.isDay() rend faux dans une dimension a heure fixe (Level.java:425 ;
 * haven a fixed_time 6000). Le casque est quand meme la, INCASSABLE : Zombie.aiStep
 * et AbstractSkeleton.aiStep ne mettent pas le feu a un porteur de casque, et le
 * casque d'origine s'userait jusqu'a casser. Le phantom (Phantom.aiStep) n'a pas de
 * casque : il ne brule pas pour la meme raison, mesure sur 60 secondes par le banc.
 *
 * LES ZONES SURES : un monstre ou un habitant qui y entre est ramene a sa derniere
 * position dehors ; un monstre ne vise pas un joueur qui s'y tient, et ne l'y blesse
 * pas (HavenRules).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenInvasion {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public enum Mode { INVASION, PAISIBLE }

    /** Les monstres de l'invasion. */
    public enum Kind { ZOMBIE, ZOMBIE_VILLAGER, SKELETON, PHANTOM }

    /** L'etiquette d'entite d'un monstre de l'invasion (sauvegardee avec lui). */
    public static final String MONSTER_TAG = "emeraldweapons.haven_monstre";
    /** L'etiquette d'entite d'un habitant de la ville paisible. */
    public static final String VILLAGER_TAG = "emeraldweapons.haven_habitant";
    /** Le prefixe de l'etiquette de generation : la ville dans laquelle l'entite est nee. */
    public static final String GENERATION_TAG = "emeraldweapons.haven_gen.";
    /** La cle, dans les donnees persistantes de l'entite, du troncon ou elle est nee. */
    public static final String HOME_KEY = "emeraldweapons.haven_troncon";

    /** Les sept regions des habitants. */
    public static final List<VillagerType> VILLAGER_TYPES = List.of(VillagerType.DESERT, VillagerType.JUNGLE,
            VillagerType.PLAINS, VillagerType.SAVANNA, VillagerType.SNOW, VillagerType.SWAMP, VillagerType.TAIGA);

    // ------------------------------------------------------------- densites (mesurees par le banc)
    //
    // Premiers chiffres (13 sept.) : 12 au sol et 10 habitants par joueur dans 64 blocs.
    // Deuxieme reglage (15 sept.) : 28 et 24 par joueur, de 16 a 40 blocs, hors de vue.
    // En voiture, les rues restaient vides des qu'on quittait la bulle (16 sept.) :
    // troisieme reglage, par troncon, pour toute la ville.

    /**
     * Cellules praticables par monstre au sol. A 150 : 1 200 monstres dans la ville,
     * 125 a 140 charges autour d'un joueur a dix troncons de vue (banc du 16 sept.).
     */
    public static final int MONSTER_CELLS = 150;
    /** Cellules praticables par habitant : 1 000 dans la ville, une centaine charges autour d'un joueur. */
    public static final int VILLAGER_CELLS = 180;
    /**
     * Garde-fous sur ce qui est charge, quelle que soit la distance de vue du serveur.
     * Le banc en charge 535 et 454 (cinq zones de 13 x 13 troncons) : 11 ms de tique en
     * combat a 416 monstres, 9 ms a 320 habitants, sur une reference de 4,5 ms.
     */
    public static final int LOADED_MAX_MONSTERS = 600;
    public static final int LOADED_MAX_VILLAGERS = 640;
    public static final int PHANTOMS_PER_PLAYER = 4;
    /** Apparitions au sol au plus par seconde, toute la ville confondue. */
    public static final int SPAWNS_PER_SECOND = 64;
    /** Aucune apparition au sol a moins de ce rayon d'un joueur. */
    public static final double SPAWN_MIN = 16.0;
    /**
     * Une apparition au sol a moins de ce rayon d'un joueur doit etre hors de sa vue ;
     * au-dela, comme les apparitions de la nuit vanilla (24 blocs), elle peut se voir.
     */
    public static final double SIGHT_RADIUS = 24.0;
    public static final double PHANTOM_MAX = 48.0;
    /** Deux secondes entre deux appuis du bouton. */
    public static final int BUTTON_COOLDOWN = 40;

    private static final int SPAWN_PERIOD = 20;
    private static final int SWEEP_PERIOD = 10;

    // ------------------------------------------------------------- memoire volatile

    /** Derniere position hors zone sure de chaque monstre ou habitant charge. */
    private static final Map<UUID, Vec3> LAST_OUTSIDE = new HashMap<>();
    /** Les apparitions recentes, pour le banc d'essai (256 au plus). */
    static final List<HavenSpawner.Spawned> RECENT = new ArrayList<>();
    /** Les ancres du banc d'essai : des joueurs simules, en plus des vrais. */
    static final List<Vec3> TEST_ANCHORS = new ArrayList<>();
    /** Les quotas par troncon, pour la carte et l'origine courantes. */
    @Nullable
    private static Quotas quotas;

    private static int ticks;
    private static boolean started;
    private static boolean lastOpen;
    private static boolean lastBusy;
    @Nullable
    private static HavenState.Phase lastPhase;
    private static long lastPress = Long.MIN_VALUE / 2;
    private static boolean warnedPeaceful;

    private HavenInvasion() {
    }

    // ================================================================ lecture

    /** Le mode de la ville ; INVASION si la dimension n'est pas chargee. */
    public static Mode mode(MinecraftServer server) {
        HavenInvasionState state = HavenInvasionState.get(server);
        return state == null ? Mode.INVASION : state.mode();
    }

    /** La ville recoit : lobby ouvert, ville posee, aucune pose en cours. */
    public static boolean cityOpen(MinecraftServer server) {
        return Haven.level(server) != null && HavenArrival.lobbyOpen(server)
                && HavenState.get(server).built() && !HavenSite.busy();
    }

    /** Ville ouverte et en invasion. */
    public static boolean invasionActive(MinecraftServer server) {
        return cityOpen(server) && mode(server) == Mode.INVASION;
    }

    /** Vrai si l'entite est un monstre de l'invasion, dans Haven. */
    public static boolean isHavenMonster(@Nullable Entity entity) {
        return entity instanceof Mob && entity.getTags().contains(MONSTER_TAG) && Haven.is(entity.level());
    }

    /** Vrai si l'entite est un habitant de la ville paisible, dans Haven. */
    public static boolean isHavenVillager(@Nullable Entity entity) {
        return entity instanceof Villager && entity.getTags().contains(VILLAGER_TAG) && Haven.is(entity.level());
    }

    /** L'espece d'un monstre de l'invasion, ou null si ce n'en est pas un. */
    @Nullable
    public static Kind kindOf(@Nullable Entity entity) {
        if (!isHavenMonster(entity)) {
            return null;
        }
        if (entity instanceof Phantom) {
            return Kind.PHANTOM;
        }
        if (entity instanceof ZombieVillager) {
            return Kind.ZOMBIE_VILLAGER;
        }
        if (entity instanceof Zombie) {
            return Kind.ZOMBIE;
        }
        return entity instanceof Skeleton ? Kind.SKELETON : null;
    }

    /** Les monstres de l'invasion charges dans la ville (copie). */
    public static List<Mob> loadedMonsters(ServerLevel level) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Mob.class),
                m -> m.getTags().contains(MONSTER_TAG) && !m.isRemoved()));
    }

    /** Les habitants charges dans la ville (copie). */
    public static List<Villager> loadedVillagers(ServerLevel level) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Villager.class),
                v -> v.getTags().contains(VILLAGER_TAG) && !v.getTags().contains(HavenTraffic.DRIVER_TAG) && !v.isRemoved()));
    }

    /** Les monstres charges, sur le serveur en cours ; vide sans serveur ni ville. */
    public static List<Mob> monsters() {
        ServerLevel level = currentLevel();
        return level == null ? List.of() : loadedMonsters(level);
    }

    /** Les habitants charges, sur le serveur en cours ; vide sans serveur ni ville. */
    public static List<Villager> villagers() {
        ServerLevel level = currentLevel();
        return level == null ? List.of() : loadedVillagers(level);
    }

    @Nullable
    private static ServerLevel currentLevel() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : Haven.level(server);
    }

    /** L'etiquette de generation de la ville d'aujourd'hui. */
    public static String generationTag(ServerLevel level) {
        return GENERATION_TAG + HavenInvasionState.get(level).generation();
    }

    /**
     * Un monstre ou un habitant qui se recharge avec son troncon est-il de la ville
     * d'aujourd'hui ? Ville ouverte, generation courante, espece du mode. Une entite
     * qui n'est pas de l'invasion est toujours la bienvenue.
     */
    public static boolean welcome(ServerLevel level, Entity entity) {
        boolean monster = entity.getTags().contains(MONSTER_TAG);
        // un habitant, un vehicule du trafic ou son pilote : la ville paisible
        boolean villager = entity.getTags().contains(VILLAGER_TAG) || entity.getTags().contains(HavenTraffic.TRAFFIC_TAG);
        if (!monster && !villager) {
            return true;
        }
        if (!cityOpen(level.getServer())) {
            return false;
        }
        HavenInvasionState state = HavenInvasionState.get(level);
        if (!entity.getTags().contains(GENERATION_TAG + state.generation())) {
            return false;
        }
        return monster ? state.mode() == Mode.INVASION : state.mode() == Mode.PAISIBLE;
    }

    // ================================================================ quotas

    /**
     * Le quota d'une tuile : sa surface praticable divisee par {@code cellsPer}, arrondi
     * par un hasard FIXE de la tuile. Un arrondi ordinaire laissait vides toutes les
     * tuiles sous la moitie du pas -- les rues etroites --, et un plafond entier leur
     * donnait a toutes un monstre : le hasard fixe donne a chacune sa chance, et la
     * meme d'une seconde a l'autre.
     */
    public static int quota(HavenInvasionData.GroundTile tile, int cellsPer) {
        long h = tile.tx() * 0x9E3779B97F4A7C15L + tile.tz() * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        double frac = (h & 0xFFFFFFL) / (double) 0x1000000;
        return (int) Math.floor(tile.cells().length / (double) cellsPer + frac);
    }

    /**
     * Le troncon pour lequel une entite compte : celui ou elle est nee, sauvegarde avec
     * elle ; sinon (posee par le banc) celui ou elle se tient. COMPTER PAR NAISSANCE ET
     * NON PAR POSITION : sinon les monstres qui convergent sur un joueur en combat
     * videraient leurs troncons, que la ville remplirait a nouveau, sans fin (mesure au
     * banc : 188 troncons vides et 16 monstres de trop sur un seul apres 30 s de combat).
     */
    public static long homeOf(Entity entity) {
        return entity.getPersistentData().contains(HOME_KEY)
                ? entity.getPersistentData().getLong(HOME_KEY) : entity.chunkPosition().toLong();
    }

    /** Le troncon du monde d'une tuile : celui de sa premiere cellule (l'origine de pose est alignee sur 16). */
    public static long chunkKey(BlockPos origin, HavenInvasionData.GroundTile tile) {
        return ChunkPos.asLong(SectionPos.blockToSectionCoord(origin.getX() + tile.tx() * HavenInvasionData.TILE),
                SectionPos.blockToSectionCoord(origin.getZ() + tile.tz() * HavenInvasionData.TILE));
    }

    /** Les tuiles au sol par troncon du monde, avec leurs quotas, pour une carte et une origine. */
    static final class Quotas {
        final HavenInvasionData.Data data;
        final BlockPos origin;
        final Map<Long, HavenInvasionData.GroundTile> tiles = new HashMap<>();
        final Map<Long, Integer> monsters = new HashMap<>();
        final Map<Long, Integer> villagers = new HashMap<>();

        Quotas(HavenInvasionData.Data data, BlockPos origin) {
            this.data = data;
            this.origin = origin;
            for (HavenInvasionData.GroundTile tile : data.groundTiles()) {
                long key = chunkKey(origin, tile);
                this.tiles.put(key, tile);
                this.monsters.put(key, quota(tile, MONSTER_CELLS));
                this.villagers.put(key, quota(tile, VILLAGER_CELLS));
            }
        }
    }

    @Nullable
    private static Quotas quotas(MinecraftServer server) {
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        if (data == null) {
            return null;
        }
        BlockPos origin = HavenState.get(server).origin();
        if (quotas == null || quotas.data != data || !quotas.origin.equals(origin)) {
            quotas = new Quotas(data, origin);
        }
        return quotas;
    }

    // ================================================================ mode

    /**
     * Change le mode de toute la ville.
     *
     * Paisible : les monstres partent tout de suite. Invasion : les habitants
     * rentrent. L'annonce va a tous les joueurs de Haven, avec un son ; le
     * voyant du bouton suit.
     *
     * @param who le nom affiche dans l'annonce ; null pour une annonce anonyme
     * @return vrai si le mode a change
     */
    public static boolean setMode(MinecraftServer server, Mode mode, @Nullable Component who) {
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return false;
        }
        HavenInvasionState state = HavenInvasionState.get(level);
        if (state.mode() == mode) {
            return false;
        }
        state.setMode(mode);
        int removed = mode == Mode.PAISIBLE ? removeMonsters(level)
                : removeVillagers(level) + HavenTraffic.removeAll(level);
        HavenInvasionButton.keep(server, true);
        announce(level, mode, who);
        LOGGER.info("ville de Haven : mode {} ({}), {} entites retirees", mode,
                who == null ? "sans auteur" : who.getString(), removed);
        return true;
    }

    /** Le clic sur le bouton du QG. Tout est revalide : Haven, ville ouverte, delai. */
    public static void pressButton(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.server;
        if (!(player.level() instanceof ServerLevel level) || !Haven.is(level)) {
            return;
        }
        if (!cityOpen(server)) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.invasion.closed")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        long now = level.getGameTime();
        if (now - lastPress < BUTTON_COOLDOWN) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.invasion.wait")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        lastPress = now;
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.9F, 0.8F);
        Mode next = mode(server) == Mode.INVASION ? Mode.PAISIBLE : Mode.INVASION;
        setMode(server, next, player.getDisplayName());
        if (next == Mode.INVASION) {
            warnPeacefulDifficulty(player);
        }
    }

    /**
     * En difficulte PAISIBLE, le jeu retire tout monstre (Mob.checkDespawn) : l'invasion
     * ne peut pas avoir lieu. On le dit au joueur, a l'arrivee et au bouton ; seul le
     * journal le disait (spawn), et un joueur ne lit pas le journal.
     */
    public static void warnPeacefulDifficulty(ServerPlayer player) {
        if (player.server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.invasion.peaceful_difficulty")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    public static Component modeName(Mode mode) {
        return Component.translatable(mode == Mode.INVASION
                ? "game.emeraldweapons.haven.invasion.mode.invasion"
                : "game.emeraldweapons.haven.invasion.mode.paisible");
    }

    private static void announce(ServerLevel level, Mode mode, @Nullable Component who) {
        boolean invasion = mode == Mode.INVASION;
        String key = "game.emeraldweapons.haven.invasion." + (invasion ? "on" : "off");
        Component chat = (who == null ? Component.translatable(key + ".anon") : Component.translatable(key, who))
                .withStyle(invasion ? ChatFormatting.RED : ChatFormatting.GREEN);
        Component bar = Component.translatable("game.emeraldweapons.haven.invasion.bar." + (invasion ? "on" : "off"))
                .withStyle(invasion ? ChatFormatting.RED : ChatFormatting.AQUA);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(chat);
            player.displayClientMessage(bar, true);
            if (invasion) {
                player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.8F, 1.0F);
            } else {
                player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.0F, 1.2F);
            }
        }
    }

    // ================================================================ tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        update(event.getServer());
    }

    /**
     * Une tique de la ville : cycle de vie, reconstructions, balayage, apparitions.
     *
     * - premiere tique du serveur : le registre des casses est reconstruit en
     *   entier (un redemarrage ne laisse pas de trou) ; la population sauvegardee
     *   reste si la ville est ouverte, sinon tout ce qui est charge est retire ;
     * - une pose commence : les trous encore vides sont rebouches, tout est retire ;
     * - le lobby se rouvre (phase PARTI ou ABSENTE vers ACCUEIL) : retour en INVASION ;
     * - la ville se ferme (depart vers la partie) : tout est retire, tout est reconstruit.
     */
    public static void update(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return;
        }
        ticks++;
        boolean busy = HavenSite.busy();
        if (!started) {
            started = true;
            int rebuilt = busy ? HavenDestruction.flushForPose(level) : HavenDestruction.rebuildAll(level);
            int removed = cityOpen(server) ? 0 : removeAll(level);
            if (rebuilt > 0 || removed > 0) {
                LOGGER.info("ville de Haven : demarrage, {} blocs reconstruits, {} entites de l'invasion retirees",
                        rebuilt, removed);
            }
            lastBusy = busy;
            lastOpen = cityOpen(server);
        }
        if (busy && !lastBusy) {
            int restored = HavenDestruction.flushForPose(level);
            int removed = removeAll(level);
            LOGGER.info("ville de Haven : une pose commence, {} trous rebouches, {} entites retirees", restored, removed);
        }
        lastBusy = busy;

        HavenState.Phase phase = HavenState.get(server).phase();
        boolean lobbyPhase = phase == HavenState.Phase.ACCUEIL || phase == HavenState.Phase.CHANTIER;
        if (lastPhase != null && lastPhase != HavenState.Phase.ACCUEIL && lastPhase != HavenState.Phase.CHANTIER
                && lobbyPhase) {
            setMode(server, Mode.INVASION, null);
        }
        lastPhase = phase;

        boolean open = cityOpen(server);
        if (lastOpen && !open) {
            int removed = removeAll(level);
            int rebuilt = HavenDestruction.rebuildAll(level);
            LOGGER.info("ville de Haven : la ville ferme, {} entites retirees, {} blocs reconstruits", removed, rebuilt);
        }
        lastOpen = open;
        if (!open) {
            return;
        }
        HavenDestruction.tick(level);
        if (ticks % SWEEP_PERIOD == 0) {
            sweep(level);
        }
        if (ticks % SPAWN_PERIOD == 0) {
            spawn(level);
            HavenTraffic.update(level, mode(server), anchors(level));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_OUTSIDE.clear();
        RECENT.clear();
        TEST_ANCHORS.clear();
        quotas = null;
        ticks = 0;
        started = false;
        lastOpen = false;
        lastBusy = false;
        lastPhase = null;
        lastPress = Long.MIN_VALUE / 2;
        warnedPeaceful = false;
    }

    // ================================================================ ancres

    /** Les points autour desquels la ville vit : les joueurs de Haven (ni spectateur, ni chantier), et le banc. */
    public static List<Vec3> anchors(ServerLevel level) {
        List<Vec3> out = new ArrayList<>(TEST_ANCHORS);
        for (ServerPlayer player : level.players()) {
            if (!player.isFakePlayer() && !player.isSpectator() && player.isAlive() && !HavenRules.chantier(player)) {
                out.add(player.position());
            }
        }
        return out;
    }

    static double horizontal(Vec3 a, double x, double z) {
        double dx = a.x - x;
        double dz = a.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** La distance a plat au joueur le plus proche ; infinie sans joueur. */
    public static double nearestDistance(List<Vec3> anchors, double x, double z) {
        double best = Double.MAX_VALUE;
        for (Vec3 anchor : anchors) {
            best = Math.min(best, horizontal(anchor, x, z));
        }
        return best;
    }

    /** L'ancre la plus proche de l'entite, a plat : celle pour qui elle compte. */
    static int nearestIndex(List<Vec3> anchors, Entity entity) {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < anchors.size(); i++) {
            double d = horizontal(anchors.get(i), entity.getX(), entity.getZ());
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    // ================================================================ balayage

    /**
     * Toutes les demi-secondes, sur ce qui est charge : hors de la grille, zones
     * sures, cibles a l'abri, metiers des habitants.
     */
    private static void sweep(ServerLevel level) {
        MinecraftServer server = level.getServer();
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        BlockPos origin = HavenState.get(server).origin();
        Set<UUID> seen = new HashSet<>();
        for (Mob mob : loadedMonsters(level)) {
            if (outside(data, origin, mob)) {
                mob.discard();
                continue;
            }
            seen.add(mob.getUUID());
            keepOutOfSafeZones(server, mob);
            if (mob.getTarget() instanceof Player target
                    && HavenProtection.inSafeZone(server, target.getX(), target.getY(), target.getZ())) {
                mob.setTarget(null);
            }
        }
        for (Villager villager : loadedVillagers(level)) {
            if (outside(data, origin, villager)) {
                villager.discard();
                continue;
            }
            seen.add(villager.getUUID());
            keepOutOfSafeZones(server, villager);
            if (villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
                // un poste de travail trouve dans la ville : sans metier, pas de commerce
                villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
            }
        }
        // les entites dechargees reprennent leur memoire au rechargement, depuis leur position
        LAST_OUTSIDE.keySet().retainAll(seen);
    }

    /** Hors de la grille, ou trop haut (un phantom parti dans le ciel). */
    private static boolean outside(@Nullable HavenInvasionData.Data data, BlockPos origin, Entity entity) {
        if (data == null) {
            return true;
        }
        double x = entity.getX() - origin.getX();
        double z = entity.getZ() - origin.getZ();
        return x < 0 || z < 0 || x >= data.width() || z >= data.depth()
                || entity.getY() > origin.getY() + data.height() + 32 || entity.getY() < origin.getY() + data.waterMaxY() - 8;
    }

    private static void keepOutOfSafeZones(MinecraftServer server, Mob mob) {
        if (!HavenProtection.inSafeZone(server, mob.getX(), mob.getY(), mob.getZ())) {
            LAST_OUTSIDE.put(mob.getUUID(), mob.position());
            return;
        }
        Vec3 back = LAST_OUTSIDE.get(mob.getUUID());
        if (back == null) {
            mob.discard();
            return;
        }
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.teleportTo(back.x, back.y, back.z);
    }

    // ================================================================ apparitions

    /** Un troncon charge sous son quota, et sa distance au joueur le plus proche. */
    private record Deficit(HavenInvasionData.GroundTile tile, int missing, double distance) {
    }

    private static void spawn(ServerLevel level) {
        MinecraftServer server = level.getServer();
        Quotas q = quotas(server);
        List<Vec3> anchors = anchors(level);
        if (q == null || anchors.isEmpty()) {
            return;
        }
        if (server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            if (!warnedPeaceful) {
                warnedPeaceful = true;
                LOGGER.warn("ville de Haven : difficulte PAISIBLE, le jeu retire tout monstre ; l'invasion ne peut pas avoir lieu");
            }
        }
        String generation = generationTag(level);
        long now = level.getGameTime();
        if (mode(server) == Mode.INVASION) {
            Map<Long, Integer> counts = new HashMap<>();
            int[] phantoms = new int[anchors.size()];
            int ground = 0;
            for (Mob mob : loadedMonsters(level)) {
                if (mob instanceof Phantom) {
                    phantoms[nearestIndex(anchors, mob)]++;
                } else {
                    counts.merge(homeOf(mob), 1, Integer::sum);
                    ground++;
                }
            }
            int budget = Math.min(SPAWNS_PER_SECOND, LOADED_MAX_MONSTERS - ground);
            for (Deficit deficit : deficits(level, q, q.monsters, counts, anchors)) {
                if (budget <= 0) {
                    break;
                }
                for (int i = 0; i < deficit.missing() && budget > 0; i++) {
                    BlockPos feet = HavenSpawner.findInTile(level, q.data, q.origin, deficit.tile(), anchors, level.random, true);
                    if (feet == null) {
                        break;
                    }
                    Kind kind = HavenSpawner.pickGroundKind(level.random);
                    Mob mob = HavenSpawner.spawnMonster(level, kind, Vec3.atBottomCenterOf(feet), true);
                    if (mob != null) {
                        mob.addTag(generation);
                        mob.getPersistentData().putLong(HOME_KEY, chunkKey(q.origin, deficit.tile()));
                        budget--;
                        record(new HavenSpawner.Spawned(kind, null, mob.position(), now));
                    }
                }
            }
            for (int k = 0; k < anchors.size(); k++) {
                if (phantoms[k] < PHANTOMS_PER_PLAYER && level.random.nextInt(4) == 0) {
                    Vec3 at = HavenSpawner.findPhantom(level, q.data, q.origin, anchors.get(k), anchors, level.random);
                    if (at != null) {
                        Mob phantom = HavenSpawner.spawnMonster(level, Kind.PHANTOM, at, true);
                        if (phantom != null) {
                            phantom.addTag(generation);
                            record(new HavenSpawner.Spawned(Kind.PHANTOM, null, phantom.position(), now));
                        }
                    }
                }
            }
        } else {
            Map<Long, Integer> counts = new HashMap<>();
            Map<VillagerType, Integer> byType = new HashMap<>();
            List<Villager> loaded = loadedVillagers(level);
            for (Villager villager : loaded) {
                counts.merge(homeOf(villager), 1, Integer::sum);
                byType.merge(villager.getVillagerData().getType(), 1, Integer::sum);
            }
            int budget = Math.min(SPAWNS_PER_SECOND, LOADED_MAX_VILLAGERS - loaded.size());
            for (Deficit deficit : deficits(level, q, q.villagers, counts, anchors)) {
                if (budget <= 0) {
                    break;
                }
                for (int i = 0; i < deficit.missing() && budget > 0; i++) {
                    BlockPos feet = HavenSpawner.findInTile(level, q.data, q.origin, deficit.tile(), anchors, level.random, true);
                    if (feet == null) {
                        break;
                    }
                    // la region la moins representee : les sept se voient vite
                    VillagerType type = VILLAGER_TYPES.get(0);
                    for (VillagerType candidate : VILLAGER_TYPES) {
                        if (byType.getOrDefault(candidate, 0) < byType.getOrDefault(type, 0)) {
                            type = candidate;
                        }
                    }
                    Villager villager = HavenSpawner.spawnVillager(level, type, Vec3.atBottomCenterOf(feet));
                    if (villager != null) {
                        villager.addTag(generation);
                        villager.getPersistentData().putLong(HOME_KEY, chunkKey(q.origin, deficit.tile()));
                        byType.merge(type, 1, Integer::sum);
                        budget--;
                        record(new HavenSpawner.Spawned(null, type, villager.position(), now));
                    }
                }
            }
        }
    }

    /**
     * Les troncons charges (blocs ET entites, sinon on doublerait ce qui n'est pas
     * encore lu) sous leur quota, les plus loin des joueurs d'abord.
     */
    private static List<Deficit> deficits(ServerLevel level, Quotas q, Map<Long, Integer> quota,
                                          Map<Long, Integer> counts, List<Vec3> anchors) {
        List<Deficit> out = new ArrayList<>();
        for (Map.Entry<Long, HavenInvasionData.GroundTile> entry : q.tiles.entrySet()) {
            long key = entry.getKey();
            int want = quota.getOrDefault(key, 0);
            int have = counts.getOrDefault(key, 0);
            if (want <= have) {
                continue;
            }
            if (!level.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)) || !level.areEntitiesLoaded(key)) {
                continue;
            }
            HavenInvasionData.GroundTile tile = entry.getValue();
            double cx = q.origin.getX() + tile.tx() * HavenInvasionData.TILE + 8.0;
            double cz = q.origin.getZ() + tile.tz() * HavenInvasionData.TILE + 8.0;
            out.add(new Deficit(tile, want - have, nearestDistance(anchors, cx, cz)));
        }
        out.sort(Comparator.comparingDouble(Deficit::distance).reversed());
        return out;
    }

    private static void record(HavenSpawner.Spawned spawned) {
        RECENT.add(spawned);
        if (RECENT.size() > 256) {
            RECENT.remove(0);
        }
    }

    // ================================================================ retraits

    /**
     * Retire monstres et habitants charges, et change de generation : ce qui dormait
     * dans un troncon decharge sera refuse a son rechargement (voir {@link #welcome}).
     */
    public static int removeAll(ServerLevel level) {
        int removed = removeMonsters(level) + removeVillagers(level) + HavenTraffic.removeAll(level);
        HavenInvasionState.get(level).bumpGeneration();
        return removed;
    }

    public static int removeMonsters(ServerLevel level) {
        int removed = 0;
        for (Mob mob : loadedMonsters(level)) {
            LAST_OUTSIDE.remove(mob.getUUID());
            mob.discard();
            removed++;
        }
        return removed;
    }

    public static int removeVillagers(ServerLevel level) {
        int removed = 0;
        for (Villager villager : loadedVillagers(level)) {
            LAST_OUTSIDE.remove(villager.getUUID());
            villager.discard();
            removed++;
        }
        return removed;
    }

    // ================================================================ evenements

    /** Un monstre ou un habitant qui se recharge avec son troncon : accepte s'il est de la ville d'aujourd'hui. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && !event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel level
                && Haven.is(level) && !welcome(level, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Le point d'accroche : apres tout le monde, un monstre de Haven est bien mort. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Kind kind = kindOf(mob);
        if (kind == null) {
            return;
        }
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer player ? player
                : mob.getKillCredit() instanceof ServerPlayer credited ? credited : null;
        NeoForge.EVENT_BUS.post(new HavenMonsterKilledEvent(level, mob, kind, event.getSource(), killer));
        LAST_OUTSIDE.remove(mob.getUUID());
    }

    /** Ni butin vanilla, ni equipement, pour les monstres et les habitants. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (isHavenMonster(event.getEntity()) || isHavenVillager(event.getEntity())) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExperience(LivingExperienceDropEvent event) {
        if (isHavenMonster(event.getEntity()) || isHavenVillager(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Un zombie tombe a l'eau ne devient pas noye ; un habitant ne se change en rien. */
    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Pre event) {
        if (isHavenMonster(event.getEntity()) || isHavenVillager(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Pas de joueur a l'abri pour cible. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player target && isHavenMonster(event.getEntity())
                && target.level() instanceof ServerLevel level
                && HavenProtection.inSafeZone(level.getServer(), target.getX(), target.getY(), target.getZ())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /** Pas de commerce avec les habitants : rien ne sort de la ville par eux. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide() && isHavenVillager(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!event.getLevel().isClientSide() && isHavenVillager(event.getTarget())) {
            event.setCanceled(true);
        }
    }

    // ================================================================ banc d'essai

    /** Compte par espece des monstres charges. */
    static Map<Kind, Integer> countByKind() {
        Map<Kind, Integer> out = new EnumMap<>(Kind.class);
        for (Mob mob : monsters()) {
            Kind kind = kindOf(mob);
            if (kind != null) {
                out.merge(kind, 1, Integer::sum);
            }
        }
        return out;
    }

    /** Oublie le delai du bouton (banc d'essai). */
    static void resetButtonCooldown() {
        lastPress = Long.MIN_VALUE / 2;
    }
}
