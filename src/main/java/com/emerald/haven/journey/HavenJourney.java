package com.emerald.haven.journey;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
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
 * LE CARNET (le livre de FTB Quests) suit par trois succes caches, comme les etapes du
 * mode : carnet/haven_arrivee, carnet/haven_qg, carnet/haven_depart (tools/quests_book.py).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenJourney {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Intervalle du guide, en tiques. */
    public static final int EVERY = 10;
    /** Delai du titre de premiere arrivee, en tiques : le terrain se charge d'abord. */
    public static final int TITLE_DELAY = 40;

    /** Ce que le guide demande au joueur. */
    public enum Objective { QG, BORNE, ATTENTE }

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static final Map<UUID, Objective> SHOWN = new HashMap<>();
    /** Les joueurs qui ont rejoint le QG pendant ce lobby. */
    private static final Set<UUID> HQ_THIS_LOBBY = new HashSet<>();
    /** La distance au QG a la premiere lecture du lobby : la barre se remplit a partir d'elle. */
    private static final Map<UUID, Double> START_DISTANCE = new HashMap<>();
    /** Les titres a jouer : joueur -> tique de jeu. */
    private static final Map<UUID, Long> TITLES = new HashMap<>();
    /** Les cobayes du banc : guides comme de vrais joueurs. */
    private static final Map<UUID, ServerPlayer> SUBJECTS = new LinkedHashMap<>();

    private static long lobbySeen = Long.MIN_VALUE;
    private static int ticks;

    private HavenJourney() {
    }

    // ================================================================ chemins d'entree

    /** L'arrivee dans un appartement (HavenArrival.arrive) : le titre de la premiere fois. */
    public static void onArrive(ServerPlayer player) {
        HavenProgress.Entry entry = HavenProgress.get(player.getUUID());
        if (entry.welcomed) {
            return;
        }
        entry.welcomed = true;
        HavenProgress.save();
        TITLES.put(player.getUUID(), player.level().getGameTime() + TITLE_DELAY);
        award(player, "haven_arrivee");
        LOGGER.info("Parcours de Haven : premiere arrivee de {}", player.getGameProfile().getName());
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

    /** L'objectif du joueur ; au premier pas dans le Hip Hog, on passe a la borne. */
    public static Objective objective(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!HQ_THIS_LOBBY.contains(id) && inHq(player)) {
            reachHq(player);
        }
        if (!HQ_THIS_LOBBY.contains(id)) {
            return Objective.QG;
        }
        return HavenState.get(player.server).vote(id) != null ? Objective.ATTENTE : Objective.BORNE;
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
        }
        if (++ticks % EVERY != 0) {
            return;
        }
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
        Long due = TITLES.get(id);
        if (due != null && now >= due) {
            TITLES.remove(id);
            playArrivalTitle(player);
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
            case BORNE -> {
                bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.borne"));
                bar.setColor(BossEvent.BossBarColor.RED);
                bar.setProgress(1.0F);
            }
            case ATTENTE -> {
                bar.setName(Component.translatable("game.emeraldweapons.haven.parcours.objectif.attente"));
                bar.setColor(BossEvent.BossBarColor.YELLOW);
                bar.setProgress(1.0F);
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
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.parcours.qg")
                .withStyle(ChatFormatting.GOLD));
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    /**
     * Le titre de la premiere arrivee, joue par le client du joueur (HavenTitlePayload) :
     * un titre de Minecraft envoye pendant « Chargement du terrain... » ne se voit jamais.
     * Il ne revient pas a la ligne et s'ecrit quatre fois plus gros que le texte (le
     * sous-titre, deux fois) : a l'echelle d'interface 4 du joueur, « Vous aviez
     * rendez-vous au quartier general » debordait en titre ; le sous-titre, raccourci, tient
     * meme a l'echelle 6. La distance et la direction sont dans la barre.
     */
    private static void playArrivalTitle(ServerPlayer player) {
        if (!player.isFakePlayer()) {
            PacketDistributor.sendToPlayer(player, HavenTitlePayload.INSTANCE);
        }
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
        for (String key : List.of("haven_arrivee", "haven_qg", "haven_depart")) {
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

    static void removeSubject(UUID player) {
        SUBJECTS.remove(player);
        hide(player);
        TITLES.remove(player);
        HQ_THIS_LOBBY.remove(player);
        START_DISTANCE.remove(player);
    }

    /** Oublie ce que le lobby sait d'un joueur (remise a zero). */
    static void forgetLobby(UUID player) {
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
