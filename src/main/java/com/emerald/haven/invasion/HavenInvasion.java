package com.emerald.haven.invasion;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Difficulty;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'invasion de Haven : les monstres dans la ville, ou les habitants quand elle est paisible.
 *
 * API POUR LES AUTRES CHANTIERS :
 * - {@link #mode(MinecraftServer)} : INVASION ou PAISIBLE ; {@link #invasionActive} : ville ouverte ET invasion ;
 * - {@link #isHavenMonster(Entity)} et {@link #kindOf(Entity)} : reconnaitre un monstre de l'invasion ;
 * - {@link HavenMonsterKilledEvent} sur NeoForge.EVENT_BUS : un monstre de Haven est mort (son butin vanilla
 *   et son experience sont supprimes ; c'est la qu'on lache les munitions) ;
 * - {@link #isHavenVillager(Entity)} : un habitant de la ville paisible (invulnerable, sans commerce).
 *
 * LE MODE est sauvegarde (HavenInvasionState). On arrive en INVASION : chaque
 * reouverture du lobby le remet. Le bouton du QG le bascule pour toute la ville.
 *
 * LA VILLE OUVERTE ({@link #cityOpen}) : lobby ouvert, ville posee, aucune pose en
 * cours. Hors de la, rien n'apparait, et tout ce qui etait la est retire.
 *
 * LES APPARITIONS, toutes les secondes, autour de chaque joueur de la ville (hors
 * spectateurs et operateurs en chantier), dans les tuiles de la carte validee :
 * au sol entre {@link #SPAWN_MIN} et {@link #SPAWN_MAX} blocs du joueur (et a au
 * moins SPAWN_MIN de tout joueur), dans une cellule de pieds de la carte -- donc a
 * plus de 24 blocs des portes et de l'entree du bar, hors zone sure --, revalidee
 * dans le monde (sol plein, deux cases libres, troncon qui tique) et HORS DE LA VUE
 * de tout joueur a moins de {@link #SIGHT_RADIUS} : on ne voit rien surgir au milieu
 * de la rue ; les phantoms dans leur bande de vol. Plafonds : {@link #GROUND_PER_PLAYER}
 * au sol et {@link #PHANTOMS_PER_PLAYER} phantoms par joueur, chacun comptant pour le
 * joueur le plus proche, {@link #MONSTERS_TOTAL} en tout ; habitants
 * {@link #VILLAGERS_PER_PLAYER} par joueur et {@link #VILLAGERS_TOTAL} en tout.
 * Chiffres retenus apres mesure du MSPT (banc « invasion »).
 *
 * LES MONSTRES FLANENT AU SOLEIL FIGE : sans cela, ils restaient plantes ou ils
 * etaient apparus des qu'aucun joueur n'etait a moins de 32 blocs (voir sweep).
 *
 * LE SOLEIL FIGE NE BRULE PAS : Mob.isSunBurnTick exige level.isDay(), et
 * Level.isDay() rend faux dans une dimension a heure fixe (Level.java:425 ;
 * haven a fixed_time 6000). Le casque est quand meme la, INCASSABLE : Zombie.aiStep
 * et AbstractSkeleton.aiStep ne mettent pas le feu a un porteur de casque, et le
 * casque d'origine s'userait jusqu'a casser. Le phantom (Phantom.aiStep) n'a pas de
 * casque : il ne brule pas pour la meme raison, mesure sur 60 secondes par le banc.
 *
 * NON PERSISTANTS ET PISTES : retires quand plus aucun joueur n'est a moins de
 * {@link #DESPAWN_RADIUS} blocs, au passage en paisible (monstres) ou en invasion
 * (habitants), a la fermeture de la ville. Un monstre ou un habitant sauvegarde avec
 * un troncon ne revient pas : il est refuse a son rechargement (etiquette).
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

    /** Les sept regions des habitants. */
    public static final List<VillagerType> VILLAGER_TYPES = List.of(VillagerType.DESERT, VillagerType.JUNGLE,
            VillagerType.PLAINS, VillagerType.SAVANNA, VillagerType.SNOW, VillagerType.SWAMP, VillagerType.TAIGA);

    // ------------------------------------------------------------- plafonds (mesures par le banc)
    //
    // Premiers chiffres (13 sept.) : 12 au sol et 10 habitants par joueur dans 64 blocs,
    // apparus de 24 a 56 blocs. En jeu, le joueur a trouve les rues vides : 27 monstres
    // apres deux minutes, 13 habitants (journal du 14 sept.), plantes loin et hors de
    // vue. Deuxieme reglage (15 sept.) : deux fois plus nombreux, plus pres, hors de
    // vue, et des monstres qui flanent au soleil (voir sweep).

    /**
     * Monstres au sol par joueur. CHAQUE MONSTRE COMPTE POUR LE JOUEUR LE PLUS PROCHE, ou
     * qu'il soit (nearestIndex). Compte dans un rayon, un monstre qui flanait au-dela
     * laissait sa place a un autre, et tous revenaient ensemble sur le joueur en combat :
     * 33 au sol et 7 phantoms autour d'un seul au banc, pour 28 et 4.
     */
    public static final int GROUND_PER_PLAYER = 28;
    public static final int PHANTOMS_PER_PLAYER = 4;
    public static final int MONSTERS_TOTAL = 120;
    /** Habitants par joueur, comptes de meme. */
    public static final int VILLAGERS_PER_PLAYER = 24;
    public static final int VILLAGERS_TOTAL = 96;
    /** Apparitions au sol au plus par joueur et par seconde. */
    public static final int SPAWNS_PER_CYCLE = 6;
    public static final double SPAWN_MIN = 16.0;
    public static final double SPAWN_MAX = 40.0;
    /**
     * Une apparition au sol a moins de ce rayon d'un joueur doit etre hors de sa vue ;
     * au-dela, comme les apparitions de la nuit vanilla (24 blocs), elle peut se voir.
     * Hors de vue jusqu'a SPAWN_MAX, les places degagees -- autour du bar et du bras
     * ouest -- ne se remplissaient pas : 3 et 4 monstres apres 20 s, 0 et 2 habitants,
     * pour 27 dans les rues etroites (banc du 15 sept.).
     */
    public static final double SIGHT_RADIUS = 24.0;
    public static final double PHANTOM_MAX = 48.0;
    /** Au-dela du plus proche joueur, monstres et habitants sont retires. */
    public static final double DESPAWN_RADIUS = 72.0;
    /** Deux secondes entre deux appuis du bouton. */
    public static final int BUTTON_COOLDOWN = 40;

    private static final int SPAWN_PERIOD = 20;
    private static final int SWEEP_PERIOD = 10;

    // ------------------------------------------------------------- memoire volatile

    private static final List<Mob> MONSTERS = new ArrayList<>();
    private static final List<Villager> VILLAGERS = new ArrayList<>();
    /** Derniere position hors zone sure de chaque monstre ou habitant suivi. */
    private static final Map<UUID, Vec3> LAST_OUTSIDE = new HashMap<>();
    /** Les apparitions recentes, pour le banc d'essai (256 au plus). */
    static final List<HavenSpawner.Spawned> RECENT = new ArrayList<>();
    /** Les ancres du banc d'essai : des joueurs simules, en plus des vrais. */
    static final List<Vec3> TEST_ANCHORS = new ArrayList<>();

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

    /** Les monstres suivis encore vivants (copie). */
    public static List<Mob> monsters() {
        MONSTERS.removeIf(Entity::isRemoved);
        return List.copyOf(MONSTERS);
    }

    /** Les habitants suivis encore presents (copie). */
    public static List<Villager> villagers() {
        VILLAGERS.removeIf(Entity::isRemoved);
        return List.copyOf(VILLAGERS);
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
        int removed = mode == Mode.PAISIBLE ? removeMonsters(level) : removeVillagers(level);
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
     *   entier (un redemarrage ne laisse pas de trou), les restes etiquetes retires ;
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
            int removed = removeAll(level);
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
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MONSTERS.clear();
        VILLAGERS.clear();
        LAST_OUTSIDE.clear();
        RECENT.clear();
        TEST_ANCHORS.clear();
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
    static List<Vec3> anchors(ServerLevel level) {
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

    private static double nearest(List<Vec3> anchors, Entity entity) {
        double best = Double.MAX_VALUE;
        for (Vec3 anchor : anchors) {
            best = Math.min(best, horizontal(anchor, entity.getX(), entity.getZ()));
        }
        return best;
    }

    // ================================================================ balayage

    /**
     * Toutes les demi-secondes : pistes perdues, joueurs trop loin, zones sures,
     * cibles a l'abri, metiers des habitants.
     */
    private static void sweep(ServerLevel level) {
        MinecraftServer server = level.getServer();
        List<Vec3> anchors = anchors(level);
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        BlockPos origin = HavenState.get(server).origin();
        for (Mob mob : List.copyOf(MONSTERS)) {
            if (mob.isRemoved()) {
                forget(mob);
                continue;
            }
            if (anchors.isEmpty() || nearest(anchors, mob) > DESPAWN_RADIUS || outside(data, origin, mob)) {
                mob.discard();
                forget(mob);
                continue;
            }
            // LE SOLEIL FIGE LES MONSTRES. En pleine lumiere, Monster.updateNoActionTime
            // ajoute 2 par tique a noActionTime, que seul un joueur a moins de 32 blocs
            // remet a zero (Mob.checkDespawn). Au-dela de 100, RandomStrollGoal refuse de
            // flaner ; au-dela de 600, un monstre sur 800 par tique disparait. Apparus a
            // plus de 24 blocs, les monstres restaient donc plantes hors de vue, puis
            // s'effacaient : le joueur a trouve les rues vides. Remis a zero toutes les
            // demi-secondes, ils flanent comme la nuit ; la ville les retire elle-meme.
            mob.setNoActionTime(0);
            keepOutOfSafeZones(server, mob);
            if (mob.getTarget() instanceof Player target
                    && HavenProtection.inSafeZone(server, target.getX(), target.getY(), target.getZ())) {
                mob.setTarget(null);
            }
        }
        for (Villager villager : List.copyOf(VILLAGERS)) {
            if (villager.isRemoved()) {
                forget(villager);
                continue;
            }
            if (anchors.isEmpty() || nearest(anchors, villager) > DESPAWN_RADIUS || outside(data, origin, villager)) {
                villager.discard();
                forget(villager);
                continue;
            }
            keepOutOfSafeZones(server, villager);
            if (villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
                // un poste de travail trouve dans la ville : sans metier, pas de commerce
                villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NONE));
            }
        }
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
            forget(mob);
            return;
        }
        mob.getNavigation().stop();
        mob.setTarget(null);
        mob.setDeltaMovement(Vec3.ZERO);
        mob.teleportTo(back.x, back.y, back.z);
    }

    private static void forget(Entity entity) {
        MONSTERS.remove(entity);
        VILLAGERS.remove(entity);
        LAST_OUTSIDE.remove(entity.getUUID());
    }

    // ================================================================ apparitions

    private static void spawn(ServerLevel level) {
        MinecraftServer server = level.getServer();
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        List<Vec3> anchors = anchors(level);
        if (data == null || anchors.isEmpty()) {
            return;
        }
        if (server.getWorldData().getDifficulty() == Difficulty.PEACEFUL) {
            if (!warnedPeaceful) {
                warnedPeaceful = true;
                LOGGER.warn("ville de Haven : difficulte PAISIBLE, le jeu retire tout monstre ; l'invasion ne peut pas avoir lieu");
            }
        }
        MONSTERS.removeIf(Entity::isRemoved);
        VILLAGERS.removeIf(Entity::isRemoved);
        BlockPos origin = HavenState.get(server).origin();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, new java.util.Random(level.random.nextLong()));
        if (mode(server) == Mode.INVASION) {
            int[] ground = new int[anchors.size()];
            int[] phantoms = new int[anchors.size()];
            for (Mob mob : MONSTERS) {
                if (mob instanceof Phantom) {
                    phantoms[nearestIndex(anchors, mob)]++;
                } else {
                    ground[nearestIndex(anchors, mob)]++;
                }
            }
            for (int k : order) {
                Vec3 anchor = anchors.get(k);
                int budget = Math.min(SPAWNS_PER_CYCLE, GROUND_PER_PLAYER - ground[k]);
                for (int i = 0; i < budget && MONSTERS.size() < MONSTERS_TOTAL; i++) {
                    BlockPos feet = HavenSpawner.findGround(level, data, origin, anchor, anchors, level.random);
                    if (feet == null) {
                        break;
                    }
                    Kind kind = HavenSpawner.pickGroundKind(level.random);
                    track(HavenSpawner.spawnMonster(level, kind, Vec3.atBottomCenterOf(feet), true), kind);
                }
                if (phantoms[k] < PHANTOMS_PER_PLAYER && MONSTERS.size() < MONSTERS_TOTAL && level.random.nextInt(4) == 0) {
                    Vec3 at = HavenSpawner.findPhantom(level, data, origin, anchor, anchors, level.random);
                    if (at != null) {
                        track(HavenSpawner.spawnMonster(level, Kind.PHANTOM, at, true), Kind.PHANTOM);
                    }
                }
            }
        } else {
            Map<VillagerType, Integer> byType = new HashMap<>();
            for (Villager villager : VILLAGERS) {
                byType.merge(villager.getVillagerData().getType(), 1, Integer::sum);
            }
            int[] near = new int[anchors.size()];
            for (Villager villager : VILLAGERS) {
                near[nearestIndex(anchors, villager)]++;
            }
            for (int k : order) {
                Vec3 anchor = anchors.get(k);
                int budget = Math.min(SPAWNS_PER_CYCLE, VILLAGERS_PER_PLAYER - near[k]);
                for (int i = 0; i < budget && VILLAGERS.size() < VILLAGERS_TOTAL; i++) {
                    BlockPos feet = HavenSpawner.findGround(level, data, origin, anchor, anchors, level.random);
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
                        VILLAGERS.add(villager);
                        byType.merge(type, 1, Integer::sum);
                        record(new HavenSpawner.Spawned(null, type, villager.position(), level.getGameTime()));
                    }
                }
            }
        }
    }

    private static void track(@Nullable Mob mob, Kind kind) {
        if (mob != null) {
            MONSTERS.add(mob);
            record(new HavenSpawner.Spawned(kind, null, mob.position(), mob.level().getGameTime()));
        }
    }

    private static void record(HavenSpawner.Spawned spawned) {
        RECENT.add(spawned);
        if (RECENT.size() > 256) {
            RECENT.remove(0);
        }
    }

    /** Suit un monstre pose par un autre chemin (le banc d'essai). */
    static void adopt(Mob mob) {
        if (mob.getTags().contains(MONSTER_TAG) && !MONSTERS.contains(mob)) {
            MONSTERS.add(mob);
        }
    }

    // ================================================================ retraits

    /** Retire monstres et habitants de l'invasion, suivis ou non. */
    public static int removeAll(ServerLevel level) {
        return removeMonsters(level) + removeVillagers(level);
    }

    public static int removeMonsters(ServerLevel level) {
        int removed = 0;
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.getTags().contains(MONSTER_TAG))) {
            mob.discard();
            removed++;
        }
        for (Mob mob : MONSTERS) {
            LAST_OUTSIDE.remove(mob.getUUID());
        }
        MONSTERS.clear();
        return removed;
    }

    public static int removeVillagers(ServerLevel level) {
        int removed = 0;
        for (Villager villager : level.getEntities(EntityTypeTest.forClass(Villager.class),
                v -> v.getTags().contains(VILLAGER_TAG))) {
            villager.discard();
            removed++;
        }
        for (Villager villager : VILLAGERS) {
            LAST_OUTSIDE.remove(villager.getUUID());
        }
        VILLAGERS.clear();
        return removed;
    }

    // ================================================================ evenements

    /** Un monstre ou un habitant sauvegarde avec son troncon ne revient pas. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && !event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel level
                && Haven.is(level)) {
            Entity entity = event.getEntity();
            if (entity.getTags().contains(MONSTER_TAG) || entity.getTags().contains(VILLAGER_TAG)) {
                event.setCanceled(true);
            }
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
        forget(mob);
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

    /** Compte par espece des monstres suivis. */
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
