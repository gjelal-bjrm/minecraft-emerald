package com.emerald.haven.journey;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionState;
import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.gun.MorphGunKeeper;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le parcours du joueur dans Haven, lot 1 (cahier §79.2, points 1 et 2) : la premiere
 * arrivee, et le guide qui mene au QG puis a la borne.
 *
 * LA PREMIERE ARRIVEE joue un titre -- « Bienvenue a Haven », sous-titre « Rendez-vous
 * au quartier general » -- une fois l'ecran de chargement ferme (HavenTitlePayload), une
 * seule fois par joueur et par monde (HavenProgress.welcomed). Les textes du titre et de
 * la barre sont courts : verifies a l'ecran, en plein ecran a l'echelle d'interface 6,
 * a cote de la minicarte (photos du 21 sept.).
 *
 * L'OBJECTIF RESTE AFFICHE EN HAUT, AU CENTRE, dans une barre a lui : « en haut au
 * centre de l'ecran », disait le joueur. Le centre est libre dans Haven -- pas de
 * siege ni de Maree dans la ville -- et le chronometre du mode est en haut a gauche.
 * Deux objectifs pour l'instant, par lobby :
 *   1. REJOINDRE LE QG : distance et direction, la barre se remplit en approchant ;
 *   2. dans la boite du Hip Hog, LANCER LA PARTIE a la borne (vitre rouge : le Defi),
 *      ou attendre les autres une fois qu'on a vote.
 * Le chantier, l'atelier et une ville fermee n'ont pas de guide.
 *
 * LE CARNET (le livre de FTB Quests) suit par des succes caches, comme les etapes du
 * mode : carnet/haven_arrivee, carnet/haven_qg, carnet/haven_depart, puis au lot 2
 * carnet/haven_arme et carnet/haven_reprise (tools/quests_book.py).
 *
 * LOT 2, LE RETOUR DU DEFI (cahier §81). A la reouverture du lobby, la ville est ENVAHIE
 * si un joueur present revient d'un Defi sans avoir repris les rues ({@link #reopenMode}).
 * Son arrivee joue alors un deuxieme titre, « Haven a ete envahie » -- une arme l'attend
 * au QG --, une seule fois. Deux objectifs de plus, avant ceux du lot 1 :
 *   3. TON ARME AU QG : revenu d'un Defi sans le Scatter Gun, le ratelier derriere le
 *      comptoir le lui donne (HavenRack) ;
 *   4. REPRENDRE LES RUES : {@link #REPRISE_GOAL} monstres abattus EN EQUIPE pendant
 *      l'invasion (compte de la ville, HavenInvasionState) ; atteint, chaque joueur present
 *      a repris les rues, la ville redevient paisible, et un titre annonce la suite.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenJourney {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Intervalle du guide, en tiques. */
    public static final int EVERY = 10;
    /** Delai du titre de premiere arrivee, en tiques : le terrain se charge d'abord. */
    public static final int TITLE_DELAY = 40;
    /** Les monstres a abattre en equipe pour reprendre les rues (choix du joueur, 21 sept.). */
    public static final int REPRISE_GOAL = 25;

    /** Ce que le guide demande au joueur. */
    public enum Objective { QG, EQUIPE, BORNE, ATTENTE, ARME, REPRISE }

    /** Un titre a jouer : a quelle tique, et lequel (HavenTitlePayload). */
    private record Title(long due, int kind) {
    }

    /** Un message du chat differe : l'arrivee n'en envoie plus pendant son titre (cahier §83). */
    private record Later(long due, Component text) {
    }

    /**
     * Les messages de l'arrivee attendent que son titre soit passe : a la connexion, le chat
     * se remplissait et cachait le titre (« il y a un peu trop de messages dans le chat quand
     * j'arrive », 22 sept.). Titre : 40 tiques, puis trois secondes de monde a l'ecran chez le
     * client, puis 130 tiques d'affichage.
     */
    public static final int AFTER_TITLE = 260;

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static final Map<UUID, Objective> SHOWN = new HashMap<>();
    /** Les joueurs qui ont rejoint le QG pendant ce lobby. */
    private static final Set<UUID> HQ_THIS_LOBBY = new HashSet<>();
    /** La distance au QG a la premiere lecture du lobby : la barre se remplit a partir d'elle. */
    private static final Map<UUID, Double> START_DISTANCE = new HashMap<>();
    /** Les titres a jouer. */
    private static final Map<UUID, Title> TITLES = new HashMap<>();
    /** Les messages differes, par joueur. */
    private static final Map<UUID, List<Later>> LATER = new HashMap<>();
    /** L'equipe s'est reunie au QG pendant ce lobby : le choix du mode a ete propose. */
    private static boolean reunited;
    /** Les cobayes du banc : guides comme de vrais joueurs. */
    private static final Map<UUID, ServerPlayer> SUBJECTS = new LinkedHashMap<>();

    private static long lobbySeen = Long.MIN_VALUE;
    private static int ticks;

    private HavenJourney() {
    }

    // ================================================================ chemins d'entree

    /**
     * L'arrivee dans un appartement (HavenArrival.arrive) : le titre de la premiere fois, ou
     * celui de la deuxieme arrivee -- revenu d'un Defi, la ville envahie --, une fois chacun.
     */
    public static void onArrive(ServerPlayer player) {
        HavenProgress.Entry entry = HavenProgress.get(player.getUUID());
        long due = player.level().getGameTime() + TITLE_DELAY;
        // l'agenda, s'il manque ; a la premiere arrivee, il remplace le dictionnaire des animaux
        boolean given = HavenAgenda.ensure(player, !entry.welcomed);
        if (given) {
            later(player, AFTER_TITLE, Component.translatable("game.emeraldweapons.haven.agenda.recu")
                    .withStyle(ChatFormatting.GOLD));
        }
        if (!entry.welcomed) {
            entry.welcomed = true;
            HavenProgress.save();
            TITLES.put(player.getUUID(), new Title(due, HavenTitlePayload.ARRIVEE));
            award(player, "haven_arrivee");
            LOGGER.info("Parcours de Haven : premiere arrivee de {}", player.getGameProfile().getName());
            return;
        }
        if (entry.departures > 0 && !entry.invaded && !entry.reprise
                && HavenInvasion.mode(player.server) == HavenInvasion.Mode.INVASION) {
            entry.invaded = true;
            HavenProgress.save();
            TITLES.put(player.getUUID(), new Title(due, HavenTitlePayload.ENVAHIE));
            LOGGER.info("Parcours de Haven : deuxieme arrivee de {}, la ville envahie", player.getGameProfile().getName());
        }
    }

    /**
     * Le mode de la ville a la reouverture du lobby : ENVAHIE si un joueur present revient
     * d'un Defi sans avoir repris les rues, PAISIBLE sinon (HavenInvasion, et
     * HavenArrival.reopen, qui l'applique avant de placer quiconque). L'operateur en
     * chantier ne compte pas.
     */
    public static HavenInvasion.Mode reopenMode(MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.addAll(SUBJECTS.values());
        for (ServerPlayer player : players) {
            if ((!player.isFakePlayer() || SUBJECTS.containsKey(player.getUUID())) && !HavenRules.chantier(player)
                    && HavenProgress.awaitsReprise(player.getUUID())) {
                return HavenInvasion.Mode.INVASION;
            }
        }
        return HavenInvasion.Mode.PAISIBLE;
    }

    /** Le depart vers une partie (HavenArrival.toVillage) : compte, succes, et plus de guide. */
    public static void onDeparture(ServerPlayer player) {
        HavenProgress.Entry entry = HavenProgress.get(player.getUUID());
        entry.departures++;
        HavenProgress.save();
        award(player, "haven_depart");
        hide(player.getUUID());
        TITLES.remove(player.getUUID());
    }

    // ================================================================ lecture

    /** Le guide s'adresse-t-il a ce joueur ? Dans la ville ouverte, lobby ouvert, hors chantier, vivant. */
    public static boolean guided(ServerPlayer player) {
        MinecraftServer server = player.server;
        return (!player.isFakePlayer() || SUBJECTS.containsKey(player.getUUID()))
                && Haven.is(player.level()) && HavenInvasion.cityOpen(server)
                && !HavenRules.chantier(player) && player.isAlive() && !player.hasDisconnected();
    }

    /**
     * L'objectif du joueur. Un vote fait : l'attente. Revenu d'un Defi sans le Scatter Gun :
     * son arme au QG ; puis, la ville envahie et les rues pas encore reprises : la reprise.
     * Sinon : le QG ; dans le Hip Hog, l'equipe a attendre ; l'equipe reunie, le choix du
     * mode a la borne -- Defi ou Monde ouvert, sans pousser l'un plus que l'autre (§83).
     */
    public static Objective objective(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!HQ_THIS_LOBBY.contains(id) && inHq(player)) {
            reachHq(player);
        }
        if (HavenState.get(player.server).vote(id) != null) {
            return Objective.ATTENTE;
        }
        HavenProgress.Entry entry = HavenProgress.get(id);
        if (entry.departures > 0 && (entry.forms & GunForm.RED_1.bit()) == 0) {
            return Objective.ARME;
        }
        if (entry.departures > 0 && !entry.reprise && HavenInvasion.mode(player.server) == HavenInvasion.Mode.INVASION) {
            return Objective.REPRISE;
        }
        if (!HQ_THIS_LOBBY.contains(id)) {
            return Objective.QG;
        }
        // L'EQUIPE EST ATTENDUE jusqu'a ce que chacun soit passe au QG dans ce lobby : qui en
        // ressort ensuite garde la borne (un joueur seul qui revenait a son appartement lisait
        // « l'equipe arrive (0 sur 1) »). Le message de la reunion, lui, attend que tous y
        // soient ensemble (reunite).
        return reachedHq(player.server) >= teamSize(player.server) ? Objective.BORNE : Objective.EQUIPE;
    }

    /** Les joueurs de l'equipe deja passes au QG dans ce lobby. */
    public static int reachedHq(MinecraftServer server) {
        int reached = 0;
        for (ServerPlayer player : cityPlayers(server)) {
            if (HQ_THIS_LOBBY.contains(player.getUUID())) {
                reached++;
            }
        }
        return reached;
    }

    /** Les joueurs de la ville que le guide suit (l'equipe). */
    public static int teamSize(MinecraftServer server) {
        return cityPlayers(server).size();
    }

    /** Un message du chat pour plus tard (dans tant de tiques). */
    public static void later(ServerPlayer player, int delay, Component text) {
        LATER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                .add(new Later(player.level().getGameTime() + delay, text));
    }

    /** L'objectif affiche en ce moment, ou null s'il n'y a pas de barre. */
    @Nullable
    public static Objective shown(UUID player) {
        return SHOWN.get(player);
    }

    /** La barre du joueur, ou null. */
    @Nullable
    public static ServerBossEvent bar(UUID player) {
        return BARS.get(player);
    }

    public static boolean titlePending(UUID player) {
        return TITLES.containsKey(player);
    }

    /** Le titre prevu pour ce joueur (HavenTitlePayload), -1 s'il n'y en a pas. */
    public static int titleKind(UUID player) {
        Title title = TITLES.get(player);
        return title == null ? -1 : title.kind();
    }

    public static boolean reachedHqThisLobby(UUID player) {
        return HQ_THIS_LOBBY.contains(player);
    }

    private static boolean inHq(ServerPlayer player) {
        HavenArrival.Layout rooms = HavenArrival.layout(player.server);
        return rooms != null && rooms.inHq(HavenState.get(player.server).origin(),
                player.getX(), player.getY(), player.getZ());
    }

    /** La distance horizontale au centre du Hip Hog, en blocs ; -1 sans salles. */
    private static double distanceToHq(ServerPlayer player) {
        HavenArrival.Layout rooms = HavenArrival.layout(player.server);
        if (rooms == null) {
            return -1.0;
        }
        BlockPos hq = rooms.hqCenter(HavenState.get(player.server).origin());
        double dx = hq.getX() + 0.5 - player.getX();
        double dz = hq.getZ() + 0.5 - player.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Component directionToHq(ServerPlayer player) {
        HavenArrival.Layout rooms = HavenArrival.layout(player.server);
        if (rooms == null) {
            return Component.empty();
        }
        BlockPos hq = rooms.hqCenter(HavenState.get(player.server).origin());
        return Component.translatable(HavenArrival.direction(hq.getX() - player.getBlockX(), hq.getZ() - player.getBlockZ()));
    }

    // ================================================================ le bouton du QG

    /**
     * Le refus du bouton : ce qui manque pour la maitrise. Les quetes n'apparaissent
     * dans la phrase que lorsqu'elles existent (HavenProgress.REQUIRED_QUESTS).
     */
    public static Component lockedButton(UUID player) {
        int weapons = HavenProgress.missingWeapons(player);
        int quests = HavenProgress.missingQuests(player);
        int total = GunForm.values().length;
        MutableComponent text = HavenProgress.REQUIRED_QUESTS.isEmpty()
                ? Component.translatable("game.emeraldweapons.haven.parcours.bouton.refus", weapons, total)
                : Component.translatable("game.emeraldweapons.haven.parcours.bouton.refus.quetes", weapons, total, quests);
        return text.withStyle(ChatFormatting.GOLD);
    }

    // ================================================================ la tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long lobby = MorphGunKeeper.lobby(server);
        if (lobby != lobbySeen) {
            // un nouveau lobby : on repart du QG
            lobbySeen = lobby;
            HQ_THIS_LOBBY.clear();
            START_DISTANCE.clear();
            reunited = false;
        }
        if (++ticks % EVERY != 0) {
            return;
        }
        reunite(server);
        long now = server.overworld().getGameTime();
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.addAll(SUBJECTS.values());
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : players) {
            if (!seen.add(player.getUUID())) {
                continue;
            }
            update(player, now);
        }
        // les barres de joueurs partis
        for (UUID id : List.copyOf(BARS.keySet())) {
            if (!seen.contains(id)) {
                hide(id);
            }
        }
    }

    /** Un passage du guide sur un joueur : titre du a jouer, barre de l'objectif. */
    public static void update(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        if (!guided(player)) {
            hide(id);
            return;
        }
        Title due = TITLES.get(id);
        if (due != null && now >= due.due()) {
            TITLES.remove(id);
            playTitle(player, due.kind());
        }
        List<Later> waiting = LATER.get(id);
        if (waiting != null) {
            waiting.removeIf(message -> {
                if (now < message.due()) {
                    return false;
                }
                player.sendSystemMessage(message.text());
                return true;
            });
            if (waiting.isEmpty()) {
                LATER.remove(id);
            }
        }
        Objective objective = objective(player);
        ServerBossEvent bar = BARS.computeIfAbsent(id, k -> new ServerBossEvent(Component.empty(),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS));
        switch (objective) {
            case QG -> {
                double distance = distanceToHq(player);
                double start = START_DISTANCE.computeIfAbsent(id, k -> Math.max(1.0, distance));
                bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.qg",
                        (int) Math.round(Math.max(0.0, distance)), directionToHq(player)));
                bar.setColor(BossEvent.BossBarColor.BLUE);
                bar.setProgress((float) Math.max(0.0, Math.min(1.0, 1.0 - distance / start)));
            }
            case EQUIPE -> {
                int team = teamSize(player.server);
                int in = reachedHq(player.server);
                bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.equipe", in, team));
                bar.setColor(BossEvent.BossBarColor.YELLOW);
                bar.setProgress(team == 0 ? 0.0F : Math.min(1.0F, in / (float) team));
            }
            case BORNE -> {
                bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.borne"));
                bar.setColor(BossEvent.BossBarColor.PURPLE);
                bar.setProgress(1.0F);
            }
            case ATTENTE -> {
                bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.attente"));
                bar.setColor(BossEvent.BossBarColor.YELLOW);
                bar.setProgress(1.0F);
            }
            case ARME -> {
                // dans le Hip Hog, le ratelier est a vue : on dit ou ; dehors, la distance
                if (inHq(player)) {
                    bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.arme.qg"));
                    bar.setProgress(1.0F);
                } else {
                    double distance = distanceToHq(player);
                    double start = START_DISTANCE.computeIfAbsent(id, k -> Math.max(1.0, distance));
                    bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.arme",
                            (int) Math.round(Math.max(0.0, distance)), directionToHq(player)));
                    bar.setProgress((float) Math.max(0.0, Math.min(1.0, 1.0 - distance / start)));
                }
                bar.setColor(BossEvent.BossBarColor.PINK);
            }
            case REPRISE -> {
                int count = repriseCount(player.server);
                bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.reprise",
                        count, REPRISE_GOAL));
                bar.setColor(BossEvent.BossBarColor.RED);
                bar.setProgress(Math.max(0.0F, Math.min(1.0F, count / (float) REPRISE_GOAL)));
            }
        }
        SHOWN.put(id, objective);
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    /** Le premier pas dans le Hip Hog : message, son, et le succes du carnet la premiere fois. */
    private static void reachHq(ServerPlayer player) {
        UUID id = player.getUUID();
        HQ_THIS_LOBBY.add(id);
        HavenProgress.Entry entry = HavenProgress.get(id);
        if (!entry.hq) {
            entry.hq = true;
            HavenProgress.save();
            award(player, "haven_qg");
        }
        // plus de message ici : la reunion de l'equipe dit ce qu'on vient faire (reunite)
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    /**
     * L'equipe au complet dans le Hip Hog : une fois par lobby, le titre « L'equipe est
     * reunie » et, dans le chat, les deux modes a la borne -- le joueur voulait qu'on DEMANDE
     * quel mode l'equipe veut, au lieu de pousser vers le Defi (22 sept.). Seul, on est reuni
     * des qu'on entre.
     */
    private static void reunite(MinecraftServer server) {
        if (reunited) {
            return;
        }
        List<ServerPlayer> team = cityPlayers(server);
        if (team.isEmpty()) {
            return;
        }
        for (ServerPlayer player : team) {
            if (!inHq(player) || HavenState.get(server).vote(player.getUUID()) != null) {
                return;
            }
        }
        reunited = true;
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : team) {
            // pas pendant un titre d'arrivee encore a jouer : il passe d'abord
            Title pending = TITLES.get(player.getUUID());
            long due = pending == null ? now : pending.due() + AFTER_TITLE;
            if (pending == null) {
                TITLES.put(player.getUUID(), new Title(now, HavenTitlePayload.REUNION));
            }
            LATER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                    .add(new Later(due, Component.translatable("game.emeraldweapons.haven.parcours.reunion")
                            .withStyle(ChatFormatting.AQUA)));
        }
        LOGGER.info("Parcours de Haven : l'equipe est reunie au QG ({} joueur(s)), choix du mode propose", team.size());
    }

    /**
     * Le titre de la premiere arrivee, joue par le client du joueur (HavenTitlePayload) :
     * un titre de Minecraft envoye pendant « Chargement du terrain... » ne se voit jamais.
     * Il ne revient pas a la ligne et s'ecrit quatre fois plus gros que le texte (le
     * sous-titre, deux fois) : a l'echelle d'interface 4 du joueur, « Vous aviez
     * rendez-vous au quartier general » debordait en titre ; le sous-titre, raccourci, tient
     * meme a l'echelle 6. La distance et la direction sont dans la barre.
     */
    private static void playTitle(ServerPlayer player, int kind) {
        if (!player.isFakePlayer()) {
            PacketDistributor.sendToPlayer(player, new HavenTitlePayload(kind));
        }
    }

    // ================================================================ reprendre les rues

    /** Les monstres abattus vers la reprise, pour la ville ; 0 sans dimension. */
    public static int repriseCount(MinecraftServer server) {
        HavenInvasionState state = HavenInvasionState.get(server);
        return state == null ? 0 : state.reprise();
    }

    /** Les joueurs de la ville que le guide suit : vrais joueurs et cobayes, hors chantier. */
    private static List<ServerPlayer> cityPlayers(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        all.addAll(SUBJECTS.values());
        for (ServerPlayer player : all) {
            if (seen.add(player.getUUID()) && guided(player)) {
                out.add(player);
            }
        }
        return out;
    }

    /**
     * Un monstre de Haven abattu par un joueur. Il compte pour la reprise si la ville est
     * ouverte et envahie, et qu'un joueur present attend encore la reprise des rues : une
     * invasion lancee au bouton par une equipe qui a deja tout repris ne compte pour rien.
     */
    @SubscribeEvent
    public static void onHavenKill(HavenMonsterKilledEvent event) {
        ServerPlayer killer = event.getKiller();
        if (killer == null || (killer.isFakePlayer() && !SUBJECTS.containsKey(killer.getUUID()))) {
            return;
        }
        countKill(event.getLevel().getServer());
    }

    /**
     * Compte un monstre abattu vers la reprise.
     *
     * @return vrai si ce monstre a compte
     */
    static boolean countKill(MinecraftServer server) {
        if (!HavenInvasion.invasionActive(server)) {
            return false;
        }
        List<ServerPlayer> present = cityPlayers(server);
        boolean awaited = false;
        for (ServerPlayer player : present) {
            if (HavenProgress.awaitsReprise(player.getUUID())) {
                awaited = true;
                break;
            }
        }
        HavenInvasionState state = HavenInvasionState.get(server);
        if (!awaited || state == null) {
            return false;
        }
        int count = state.reprise() + 1;
        state.setReprise(count);
        if (count >= REPRISE_GOAL) {
            retake(server, present);
        }
        return true;
    }

    /**
     * Les rues sont reprises : chaque joueur present les a reprises (fiche, carnet, titre),
     * le compte repart de zero, et la ville redevient paisible -- ses habitants reviennent.
     */
    private static void retake(MinecraftServer server, List<ServerPlayer> present) {
        HavenInvasionState state = HavenInvasionState.get(server);
        if (state != null) {
            state.setReprise(0);
        }
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : present) {
            HavenProgress.Entry entry = HavenProgress.get(player.getUUID());
            entry.reprise = true;
            entry.invaded = true;
            award(player, "haven_reprise");
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.parcours.reprise.fin", REPRISE_GOAL)
                    .withStyle(ChatFormatting.GREEN));
            // tout de suite : on est en pleine rue, pas derriere un ecran de chargement
            TITLES.put(player.getUUID(), new Title(now, HavenTitlePayload.REPRISE));
        }
        HavenProgress.save();
        HavenInvasion.setMode(server, HavenInvasion.Mode.PAISIBLE, null);
        LOGGER.info("Parcours de Haven : les rues sont reprises ({} monstres), {} joueur(s)", REPRISE_GOAL, present.size());
    }

    private static void hide(UUID id) {
        ServerBossEvent bar = BARS.remove(id);
        if (bar != null) {
            bar.removeAllPlayers();
        }
        SHOWN.remove(id);
    }

    // ================================================================ le carnet

    /** Accorde le succes cache d'une etape du carnet (ce que lit le livre de FTB Quests). */
    static void award(ServerPlayer player, String key) {
        if (player.isFakePlayer()) {
            return;
        }
        AdvancementHolder holder = player.server.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "carnet/" + key));
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        List<String> left = new ArrayList<>();
        progress.getRemainingCriteria().forEach(left::add);
        for (String criterion : left) {
            player.getAdvancements().award(holder, criterion);
        }
    }

    /** Retire les succes caches du parcours : la remise a zero d'un joueur par l'operateur. */
    static void revokeAll(ServerPlayer player) {
        for (String key : List.of("haven_arrivee", "haven_qg", "haven_depart", "haven_arme", "haven_reprise")) {
            AdvancementHolder holder = player.server.getAdvancements().get(
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "carnet/" + key));
            if (holder == null) {
                continue;
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
            List<String> done = new ArrayList<>();
            progress.getCompletedCriteria().forEach(done::add);
            for (String criterion : done) {
                player.getAdvancements().revoke(holder, criterion);
            }
        }
    }

    // ================================================================ banc d'essai

    static void addSubject(ServerPlayer player) {
        SUBJECTS.put(player.getUUID(), player);
    }

    /** Un cobaye du banc, guide comme un vrai joueur. */
    static boolean isSubject(UUID player) {
        return SUBJECTS.containsKey(player);
    }

    /** Pour le banc : les messages differes de ce joueur, dans l'ordre. */
    static List<Component> laterTexts(UUID player) {
        List<Component> out = new ArrayList<>();
        for (Later later : LATER.getOrDefault(player, List.of())) {
            out.add(later.text());
        }
        return out;
    }

    /** Pour le banc : l'echeance du premier message differe, ou -1. */
    static long laterDue(UUID player) {
        List<Later> list = LATER.get(player);
        return list == null || list.isEmpty() ? -1L : list.get(0).due();
    }

    /** Pour le banc : la reunion de l'equipe, comme a la tique du serveur. */
    static void reuniteForTest(MinecraftServer server) {
        reunited = false;
        reunite(server);
    }

    static void removeSubject(UUID player) {
        SUBJECTS.remove(player);
        LATER.remove(player);
        hide(player);
        TITLES.remove(player);
        HQ_THIS_LOBBY.remove(player);
        START_DISTANCE.remove(player);
    }

    /** Oublie ce que le lobby sait d'un joueur (remise a zero). */
    static void forgetLobby(UUID player) {
        LATER.remove(player);
        HQ_THIS_LOBBY.remove(player);
        START_DISTANCE.remove(player);
        TITLES.remove(player);
        hide(player);
    }

    // ================================================================ evenements

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        HavenProgress.load(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HavenProgress.save();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (ServerBossEvent bar : BARS.values()) {
            bar.removeAllPlayers();
        }
        BARS.clear();
        SHOWN.clear();
        HQ_THIS_LOBBY.clear();
        START_DISTANCE.clear();
        TITLES.clear();
        LATER.clear();
        reunited = false;
        SUBJECTS.clear();
        lobbySeen = Long.MIN_VALUE;
        ticks = 0;
        HavenProgress.unload();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            hide(player.getUUID());
        }
    }
}
