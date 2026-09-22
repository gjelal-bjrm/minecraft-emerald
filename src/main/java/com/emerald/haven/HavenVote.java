package com.emerald.haven;

import com.emerald.block.HavenVoteBlock;
import com.emerald.block.ModBlocks;
import com.emerald.game.GameManager;
import com.emerald.game.GameState;
import com.emerald.game.ModeChoice;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.HavenVoteMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Le vote au QG : Defi ou Libre, a l'unanimite de ceux qui sont dans la ville.
 *
 * DEUX MOITIES. La premiere est PURE -- {@link #tally}, {@link #electors},
 * {@link Countdown} : elle ne lit que la liste d'electeurs qu'on lui donne, et
 * le banc d'essai la fait tourner avec des joueurs simules, sans serveur. La
 * seconde lit le monde (qui est dans la ville, qui est dans le bar, qui a vote
 * quoi), fait parler la premiere, et execute ce qu'elle decide.
 *
 * LA REGLE, telle que le joueur l'a fixee :
 * - electeurs = joueurs en ligne dans la ville, sauf l'operateur en chantier ;
 *   un joueur inactif compte toujours : l'unanimite reste stricte, et un
 *   operateur peut forcer le depart (/arcencium haven skip) ;
 * - depart seulement si TOUS ont vote la meme chose ET sont dans le Hip Hog ;
 * - on peut changer d'avis ; un compte a rebours de cinq secondes laisse le
 *   temps de le faire, et TOUT changement l'annule -- un avis qui change, un
 *   joueur qui arrive, part ou sort du bar.
 *
 * LA DECONNEXION se traite dans l'evenement meme, en excluant nommement celui
 * qui part : PlayerList.remove lance l'evenement AVANT de retirer le joueur du
 * niveau (PlayerList.java:367-384). Sans cette exclusion, le partant compterait
 * encore une tique, et son opposition tiendrait apres son depart.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenVote {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Cinq secondes : assez pour se raviser, pas assez pour s'impatienter. */
    public static final int COUNTDOWN_TICKS = 5 * 20;

    /** Un electeur tel que le serveur le voit a un instant. */
    public record Elector(UUID id, String name, boolean inBar, @Nullable GameState.Mode vote) {
    }

    /** Le decompte a un instant. */
    public record Tally(int electors, int defi, int libre, int inBar, @Nullable GameState.Mode unanimous) {

        /** Tous d'accord, et tous dans le bar : le compte a rebours peut courir. */
        public boolean ready() {
            return this.unanimous != null && this.inBar == this.electors;
        }
    }

    /** Ce qu'une tique du compte a rebours a produit. */
    public enum Step { NONE, STARTED, CANCELLED, RESTARTED, COUNTING, DEPART }

    private static final Countdown COUNTDOWN = new Countdown();
    private static List<Elector> current = List.of();
    private static Tally currentTally = tally(List.of());
    private static int ticks;

    private HavenVote() {
    }

    // ================================================================ logique pure

    /**
     * Le decompte d'une liste d'electeurs.
     *
     * Une ville vide n'a pas d'unanimite : sans ce garde-fou, un serveur dont
     * le dernier joueur se deconnecte lancerait un depart pour personne.
     */
    public static Tally tally(List<Elector> electors) {
        int defi = 0;
        int libre = 0;
        int inBar = 0;
        for (Elector elector : electors) {
            if (elector.vote() == GameState.Mode.DEFI) {
                defi++;
            } else if (elector.vote() == GameState.Mode.LIBRE) {
                libre++;
            }
            if (elector.inBar()) {
                inBar++;
            }
        }
        int count = electors.size();
        GameState.Mode unanimous = count == 0 ? null
                : defi == count ? GameState.Mode.DEFI
                : libre == count ? GameState.Mode.LIBRE
                : null;
        return new Tally(count, defi, libre, inBar, unanimous);
    }

    /**
     * Les electeurs parmi les joueurs presents : ni operateur en chantier, ni partant.
     *
     * Triee par nom : c'est l'ordre de la liste affichee a l'ecran, et un ordre
     * stable evite de rouvrir l'ecran de chacun pour une simple permutation.
     */
    public static List<Elector> electors(Collection<Elector> online, Set<UUID> chantier,
                                         @Nullable UUID leaving) {
        List<Elector> out = new ArrayList<>();
        for (Elector elector : online) {
            if (!chantier.contains(elector.id()) && !elector.id().equals(leaving)) {
                out.add(elector);
            }
        }
        out.sort(Comparator.comparing(Elector::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Elector::id));
        return out;
    }

    /**
     * Le compte a rebours, sans serveur.
     *
     * Il retient QUI etait d'accord et SUR QUOI au moment ou il est parti. A
     * chaque appel, il compare : le meme groupe, le meme avis, tous encore dans
     * le bar -- il continue ; sinon il s'annule, et repart aussitot si le
     * nouveau groupe est lui aussi unanime (un joueur qui arrive dans le bar
     * avec le meme vote relance les cinq secondes au lieu de les voler).
     */
    public static final class Countdown {
        private int remaining;
        private Set<UUID> group = Set.of();
        @Nullable
        private GameState.Mode mode;

        /** Tiques restantes ; 0 quand rien ne court. */
        public int remaining() {
            return this.remaining;
        }

        @Nullable
        public GameState.Mode mode() {
            return this.mode;
        }

        public void reset() {
            this.remaining = 0;
            this.group = Set.of();
            this.mode = null;
        }

        /**
         * @param advance vrai une fois par tique ; faux pour un simple recalcul
         *                (un vote, une deconnexion), qui ne doit pas manger de temps
         */
        public Step step(List<Elector> electors, boolean advance) {
            Tally tally = tally(electors);
            Set<UUID> ids = new HashSet<>();
            for (Elector elector : electors) {
                ids.add(elector.id());
            }
            if (this.remaining > 0) {
                if (tally.ready() && tally.unanimous() == this.mode && ids.equals(this.group)) {
                    if (!advance) {
                        return Step.COUNTING;
                    }
                    this.remaining--;
                    return this.remaining == 0 ? Step.DEPART : Step.COUNTING;
                }
                this.reset();
                if (tally.ready()) {
                    start(tally.unanimous(), ids);
                    return Step.RESTARTED;
                }
                return Step.CANCELLED;
            }
            if (tally.ready()) {
                start(tally.unanimous(), ids);
                return Step.STARTED;
            }
            return Step.NONE;
        }

        private void start(GameState.Mode mode, Set<UUID> ids) {
            this.remaining = COUNTDOWN_TICKS;
            this.mode = mode;
            this.group = Set.copyOf(ids);
        }
    }

    // ================================================================ lecture du monde

    /** Les electeurs de ce tick, dans l'ordre de l'ecran. */
    public static List<Elector> current() {
        return current;
    }

    public static Tally currentTally() {
        return currentTally;
    }

    public static int remaining() {
        return COUNTDOWN.remaining();
    }

    /**
     * Les electeurs tels qu'ils sont dans le monde.
     *
     * Les FakePlayer des mods (et du banc d'essai) ne votent pas : ils sont dans
     * le niveau sans etre des joueurs.
     */
    public static List<Elector> collect(MinecraftServer server, ServerLevel level, @Nullable UUID leaving) {
        HavenState state = HavenState.get(server);
        HavenArrival.Layout layout = HavenArrival.layout(server);
        BlockPos origin = state.origin();
        List<Elector> online = new ArrayList<>();
        Set<UUID> chantier = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isFakePlayer()) {
                continue;
            }
            if (HavenRules.chantier(player)) {
                chantier.add(player.getUUID());
            }
            boolean inBar = layout != null && layout.inHq(origin, player.getX(), player.getY(), player.getZ());
            online.add(new Elector(player.getUUID(), player.getGameProfile().getName(), inBar,
                    state.vote(player.getUUID())));
        }
        return electors(online, chantier, leaving);
    }

    // ================================================================ serveur

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (++ticks % 20 == 0) {
            keepVoteBlock(server);
        }
        followVoteBlock(server);
        HavenState state = HavenState.get(server);
        // une partie commencee n'a plus de vote, meme si la phase n'a pas encore
        // ete fermee (HavenArrival.closeIfStarted, sans ordre garanti entre nous)
        if (state.phase() != HavenState.Phase.ACCUEIL || !HavenArrival.gameWaiting(server)) {
            if (COUNTDOWN.remaining() > 0 || !current.isEmpty()) {
                reset();
            }
            return;
        }
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return;
        }
        refresh(server, level, null, true);
        reopenStaleMenus(level);
    }

    /**
     * La deconnexion, traitee avant que le joueur quitte le niveau.
     *
     * Le partant est exclu NOMMEMENT : l'evenement part avant son retrait. Un
     * compte a rebours en cours s'annule (le groupe a change) et repart sans
     * lui s'il reste une unanimite ; aucun depart n'a lieu ici, donc aucune
     * teleportation d'un joueur en train d'etre retire.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.server;
        if (HavenState.get(server).phase() != HavenState.Phase.ACCUEIL || !HavenArrival.gameWaiting(server)) {
            return;
        }
        ServerLevel level = Haven.level(server);
        if (level != null) {
            refresh(server, level, player.getUUID(), false);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        reset();
        ticks = 0;
        lastWanted = null;
        warnedBlocked = false;
    }

    /** Oublie le compte a rebours et le decompte : reouverture, depart, arret. */
    public static void reset() {
        COUNTDOWN.reset();
        current = List.of();
        currentTally = tally(current);
    }

    private static void refresh(MinecraftServer server, ServerLevel level, @Nullable UUID leaving, boolean advance) {
        List<Elector> electors = collect(server, level, leaving);
        Step step = COUNTDOWN.step(electors, advance);
        GameState.Mode mode = COUNTDOWN.mode();
        current = electors;
        currentTally = tally(electors);
        switch (step) {
            case STARTED -> {
                tell(level, leaving, Component.translatable("game.emeraldweapons.haven.vote.start", modeName(mode))
                        .withStyle(ChatFormatting.GOLD), false);
                countdownBar(level, leaving);
            }
            case RESTARTED -> {
                tell(level, leaving, Component.translatable("game.emeraldweapons.haven.vote.restart", modeName(mode))
                        .withStyle(ChatFormatting.GOLD), false);
                countdownBar(level, leaving);
            }
            case CANCELLED -> tell(level, leaving, Component.translatable("game.emeraldweapons.haven.vote.cancel")
                    .withStyle(ChatFormatting.RED), false);
            case COUNTING -> {
                if (advance && COUNTDOWN.remaining() % 20 == 0) {
                    countdownBar(level, leaving);
                }
            }
            case DEPART -> depart(server, mode);
            case NONE -> {
            }
        }
    }

    private static void countdownBar(ServerLevel level, @Nullable UUID leaving) {
        int seconds = (COUNTDOWN.remaining() + 19) / 20;
        for (ServerPlayer player : level.players()) {
            if (!player.getUUID().equals(leaving)) {
                player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.vote.count", seconds)
                        .withStyle(ChatFormatting.GOLD), true);
                player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.4F);
            }
        }
    }

    /**
     * Un vote recu d'un menu. Le serveur revalide tout : la phase, la ville, le chantier.
     */
    public static void cast(ServerPlayer player, GameState.Mode mode) {
        MinecraftServer server = player.server;
        HavenState state = HavenState.get(server);
        if (state.phase() != HavenState.Phase.ACCUEIL || !HavenArrival.gameWaiting(server)
                || !(player.level() instanceof ServerLevel level) || !Haven.is(level)) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.vote.closed")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (HavenRules.chantier(player)) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.vote.chantier")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        boolean changed = state.setVote(player.getUUID(), mode);
        // la confirmation, a chaque clic (vitre de la borne ou bouton de l'ecran) :
        // le nom du choix au-dessus de la barre d'objets, et un son
        player.displayClientMessage(Component.translatable("gui.emeraldweapons.haven_vote.mine", modeName(mode))
                .withStyle(mode == GameState.Mode.DEFI ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7F, changed ? 1.2F : 1.0F);
        if (!changed) {
            return;                     // le meme avis : rien ne change, rien ne s'annule
        }
        Tally tally = tally(collect(server, level, null));
        tell(level, null, Component.translatable("game.emeraldweapons.haven.vote.cast", player.getName(),
                        modeName(mode), mode == GameState.Mode.DEFI ? tally.defi() : tally.libre(), tally.electors())
                .withStyle(mode == GameState.Mode.DEFI ? ChatFormatting.GOLD : ChatFormatting.GREEN), false);
        refresh(server, level, null, false);
    }

    /**
     * LE DEPART, dans un ordre fixe.
     *
     * 1. La phase passe a PARTI AVANT tout le reste : un joueur qui se connecte
     *    pendant le depart va au village, pas dans un appartement.
     * 2. Le mode est applique ; ModeChoice l'annonce a TOUS les joueurs du
     *    serveur, ceux de la ville compris.
     * 3. Les votes sont effaces, les deux moities de la borne retirees : sa place peut
     *    accueillir l'arche du depart.
     * 4. L'ARCHE DU DEPART S'OUVRE dans le bar (HavenDeparture, cahier §84) : chacun la
     *    passe quand il est pret et arrive au village -- teleportation, reapparition dans
     *    l'overworld, mode de jeu d'origine rendu, kit de depart, annonce du village. Le
     *    kit vide l'inventaire : c'est voulu ici, et nulle part ailleurs dans la ville --
     *    et seulement si la Lame n'a pas encore ouvert la partie (LOBBY ou PROLOGUE), comme
     *    pour le retardataire. Le vote et « skip » exigent deja une partie en attente ; la
     *    garde est la pour qu'aucun autre appel ne vide les poches d'un serveur en pleine
     *    partie. Qui n'est pas dans la ville part tout de suite ; sans place pour l'arche,
     *    tout le monde part d'un coup, comme avant.
     */
    public static void depart(MinecraftServer server, @Nullable GameState.Mode mode) {
        HavenState state = HavenState.get(server);
        ServerLevel overworld = server.overworld();
        GameState game = GameState.get(overworld);
        GameState.Mode chosen = mode == null ? game.mode() : mode;
        boolean kit = game.status() == GameState.Status.LOBBY || game.status() == GameState.Status.PROLOGUE;
        reset();
        state.setPhase(HavenState.Phase.PARTI);
        ModeChoice.choose(overworld, chosen);
        state.clearVotes();
        // les deux moities partent tout de suite, troncon charge s'il le faut :
        // sans cela, une borne loin de tout joueur restait au monde sauvegarde
        keepVoteBlock(server, true);
        List<ServerPlayer> players = List.copyOf(server.getPlayerList().getPlayers());
        if (com.emerald.haven.journey.HavenDeparture.open(server, chosen, kit)) {
            // l'arche emmene ceux de la ville ; les autres (hors de la ville) partent tout de suite
            for (ServerPlayer player : players) {
                if (!Haven.is(player.level()) && !HavenRules.chantier(player)) {
                    HavenArrival.toVillage(player, kit);
                }
            }
            LOGGER.info("ville de Haven : depart vers le village en {}, {} joueurs, par l'arche du QG",
                    chosen, players.size());
            return;
        }
        for (ServerPlayer player : players) {
            HavenArrival.toVillage(player, kit);
        }
        GameManager.announce(overworld, "game.emeraldweapons.village_intro",
                "game.emeraldweapons.village_intro.sub", 0x9CE8FF);
        BlockPos village = GameState.get(overworld).village();
        Component where = Component.translatable("game.emeraldweapons.locked.where",
                village.getX(), village.getY(), village.getZ(), 0).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : overworld.players()) {
            player.sendSystemMessage(where);
        }
        LOGGER.info("ville de Haven : depart vers le village en {}, {} joueurs", chosen, players.size());
    }

    // ================================================================ la borne

    /**
     * Le cote des vitres de la borne : le SUD, mesure dans le volume ctyport.
     *
     * La borne est en (335, 68, 168) du volume, a cote du bout du comptoir
     * (cases 327-333 en z 167, 334-335 en z 166) : ses deux cases et leurs
     * voisines sont de l'air, sur de l'andesite polie. Dans la boite du bar,
     * 1283 cases praticables, atteintes depuis la borne ; leur centre de gravite
     * est en (349,6 ; 182,4), soit 14,6 a l'est et 14,4 au sud : une diagonale,
     * qui ne tranche pas. L'entree tranche : le sol quitte la boite par son bord
     * sud (z 207 vers 208, x 352 a 372, 21 cases), et par une seule case a l'est
     * (372, 198). Vu de la borne, le milieu de cette entree est a 27 a l'est et
     * 39 au sud : le joueur qui entre arrive par le sud. Tournee vers le sud, la
     * borne lui montre ses vitres et tourne le dos au comptoir. (Devant elle :
     * 16 cases libres droit devant, 132 praticables dans un cone de 90 degres a
     * moins de 12 ; vers l'est, 21 et 167 ; au nord, 1 et 75.)
     */
    public static final Direction VOTE_FACING = Direction.SOUTH;

    /** 2|16 : ni voisins prevenus, ni formes recalculees -- rien ne coule, rien ne tombe. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Ce que la derniere tique voulait : null avant la premiere. */
    @Nullable
    private static Boolean lastWanted;
    private static boolean warnedBlocked;

    /** L'etat d'une moitie de la borne, tournee vers {@link #VOTE_FACING}. */
    public static BlockState voteState(DoubleBlockHalf half) {
        return ModBlocks.HAVEN_VOTE.get().defaultBlockState()
                .setValue(HavenVoteBlock.FACING, VOTE_FACING)
                .setValue(HavenVoteBlock.HALF, half);
    }

    /** La borne a sa place : ville posee et finie, lobby en attente de joueurs. */
    public static boolean voteBlockWanted(HavenState state) {
        return state.phase() == HavenState.Phase.ACCUEIL && state.built() && !HavenSite.busy();
    }

    /**
     * Pose ou retire la borne du QG, paresseusement.
     *
     * Posee seulement quand la ville est finie et la phase ACCUEIL, retiree
     * sinon -- et seulement si son troncon est deja charge : on ne charge pas
     * la ville pour un bloc. Une repose de la ville l'efface ; ce controle,
     * toutes les secondes, la remet quand la pose est finie.
     *
     * @return la position de la moitie basse, ou null si la ville ou ses salles manquent
     */
    @Nullable
    public static BlockPos keepVoteBlock(MinecraftServer server) {
        return keepVoteBlock(server, false);
    }

    /**
     * La borne a la fin d'une pose de la ville (HavenSite.done).
     *
     * La pose retire la borne en recouvrant ses cases ; le controle d'une seconde
     * ne la remettait que si son troncon etait encore charge, ce qui n'est plus
     * le cas quand personne n'est pres du QG : le monde etait alors sauvegarde
     * sans borne. Ici on charge ce seul troncon, une fois par pose, pour la
     * reposer tout de suite.
     */
    public static void placeVoteBlockAfterPose(MinecraftServer server) {
        keepVoteBlock(server, true);
    }

    /**
     * La borne suit un changement de ce qu'on veut, tout de suite et troncon charge.
     *
     * Appelee a chaque tique : quand la phase change (depart, reouverture par
     * /arcencium setup ou haven ouvrir, partie commencee ailleurs) ou qu'une
     * pose commence ou finit, on charge le troncon une fois et on pose ou retire
     * les deux moities -- sans attendre qu'un joueur passe pres du bar.
     */
    public static void followVoteBlock(MinecraftServer server) {
        boolean wanted = voteBlockWanted(HavenState.get(server));
        if (lastWanted != null && lastWanted != wanted) {
            keepVoteBlock(server, true);
        }
        lastWanted = wanted;
    }

    /**
     * Les deux moities ensemble : la basse sur hq.vote.floor + 1, la haute
     * au-dessus. Pose refusee (et signalee une fois) si une des deux cases est
     * prise par autre chose que la borne ; une moitie orpheline est alors
     * retiree, pour ne jamais laisser une demi-borne.
     */
    @Nullable
    private static BlockPos keepVoteBlock(MinecraftServer server, boolean load) {
        ServerLevel level = Haven.level(server);
        HavenArrival.Layout layout = HavenArrival.layout(server);
        if (level == null || layout == null) {
            return null;
        }
        HavenState state = HavenState.get(server);
        BlockPos pos = layout.votePos(state.origin());
        BlockPos top = pos.above();
        if (load) {
            level.getChunkAt(pos);
        } else if (!level.isLoaded(pos)) {
            return pos;
        }
        BlockState lower = level.getBlockState(pos);
        BlockState upper = level.getBlockState(top);
        if (voteBlockWanted(state)) {
            BlockState wantLower = voteState(DoubleBlockHalf.LOWER);
            BlockState wantUpper = voteState(DoubleBlockHalf.UPPER);
            if (lower == wantLower && upper == wantUpper) {
                return pos;
            }
            // UNE ARCHE DU DEPART ORPHELINE sur la place de la borne (un arret du serveur en
            // plein depart, d'avant le nettoyage a l'arret) : elle s'en va, la borne revient
            for (BlockPos cell : new BlockPos[]{pos, top}) {
                if (level.getBlockState(cell).is(ModBlocks.ARC_PORTAL.get())
                        && !com.emerald.haven.journey.HavenDeparture.isGate(level, cell)) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            if (HavenVoteBlock.fits(level, pos)) {
                level.setBlock(pos, wantLower, QUIET);
                level.setBlock(top, wantUpper, QUIET);
                warnedBlocked = false;
                LOGGER.info("ville de Haven : borne du QG posee en {} et {}, vitres vers {}",
                        pos.toShortString(), top.toShortString(), VOTE_FACING.getName());
                return pos;
            }
            if (!warnedBlocked) {
                warnedBlocked = true;
                LOGGER.warn("ville de Haven : borne du QG non posee, cases prises : {} en {}, {} en {}",
                        lower, pos.toShortString(), upper, top.toShortString());
            }
        }
        Block vote = ModBlocks.HAVEN_VOTE.get();
        boolean removed = false;
        if (upper.is(vote)) {
            level.setBlock(top, Blocks.AIR.defaultBlockState(), QUIET);
            removed = true;
        }
        if (lower.is(vote)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            removed = true;
        }
        if (removed) {
            LOGGER.info("ville de Haven : borne du QG retiree de {} et {}", pos.toShortString(), top.toShortString());
        }
        return pos;
    }

    /**
     * L'ecran montre les noms envoyes a son ouverture : quand la liste des
     * electeurs change (quelqu'un arrive dans la ville ou la quitte), on le
     * rouvre pour ceux qui le regardent. Les decomptes, eux, suivent chaque
     * tique par les donnees du menu.
     */
    private static void reopenStaleMenus(ServerLevel level) {
        List<UUID> ids = HavenVoteMenu.rosterIds(current);
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.containerMenu instanceof HavenVoteMenu menu && menu.pos() != null
                    && !menu.roster().equals(ids)) {
                HavenVoteMenu.open(player, menu.pos());
            }
        }
    }

    // ================================================================ textes

    public static Component modeName(@Nullable GameState.Mode mode) {
        return Component.translatable(mode == GameState.Mode.LIBRE
                ? "gui.emeraldweapons.haven_vote.libre" : "gui.emeraldweapons.haven_vote.defi");
    }

    private static void tell(ServerLevel level, @Nullable UUID except, Component message, boolean actionBar) {
        for (ServerPlayer player : level.players()) {
            if (!player.getUUID().equals(except)) {
                player.displayClientMessage(message, actionBar);
            }
        }
    }
}
