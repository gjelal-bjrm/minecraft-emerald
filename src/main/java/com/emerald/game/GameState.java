package com.emerald.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * L'etat d'une partie, sauvegarde avec le monde.
 *
 * Une SavedData plutot que des champs statiques : une partie doit survivre a un
 * arret du serveur, et un monde ne doit rien savoir de l'etat d'un autre.
 *
 * Le temps est compte en TICKS DE MONDE et non en horloge reelle, pour que la
 * partie se fige avec le serveur au lieu d'expirer pendant qu'il est eteint.
 */
public class GameState extends SavedData {

    public static final String KEY = "emeraldweapons_game";

    /** Duree d'une partie, du retrait de la lame a la defaite par le temps. */
    /**
     * QUATRE-VINGT-DIX MINUTES, ET NON SOIXANTE.
     *
     * A l'essai, quarante minutes ne suffisaient pas a tenir le PREMIER
     * sanctuaire : le temps d'equiper, de miner de quoi payer une ancre et de
     * traverser 450 blocs, la moitie de la partie etait passee. Les phases
     * gardent leurs proportions (voir GamePhase).
     */
    public static final long GAME_MINUTES = 90L;
    public static final long GAME_TICKS = GAME_MINUTES * 60L * 20L;

    /** Rayon de la zone de jeu, en blocs. */
    public static final int PLAY_RADIUS = 750;

    public enum Status { LOBBY, PROLOGUE, RUNNING, WON, LOST }

    /**
     * LES DEUX FACONS DE JOUER LE MODE.
     *
     * DEFI est le mode d'origine : quatre-vingt-dix minutes, la Maree qui
     * referme la carte, et une defaite si le temps passe. C'est une partie, avec
     * un debut et une fin.
     *
     * LIBRE est le meme monde SANS L'HORLOGE. Les trois sanctuaires se dressent,
     * la meteo tourne, le boss vient quand les trois ancres sont tenues -- mais
     * rien ne presse et rien ne se referme. Le boss abattu, le CYCLE RECOMMENCE :
     * trois nouveaux sanctuaires ailleurs, et l'on garde tout ce qu'on a bati.
     *
     * Le choix appartient au monde, pas au joueur ni au serveur : deux mondes
     * peuvent donc se jouer differemment, et un monde ne change pas de nature
     * entre deux connexions.
     */
    public enum Mode { DEFI, LIBRE }

    /** Distance entre le village et chaque ancre. */
    public static final int ANCHOR_DISTANCE = 450;

    private Status status = Status.LOBBY;
    private Mode mode = Mode.DEFI;
    /** Vrai des que quelqu'un a tranche : sinon on repose la question. */
    private boolean modeChosen;
    /** Le tour de piste, en mode LIBRE : il commence a un et ne s'arrete pas. */
    private int cycle = 1;
    private long startTick;
    private int anchorsActive;
    private int anchorsInProgress;
    private BlockPos village = BlockPos.ZERO;
    /** Vrai des que le monde a ete prepare : la mise en place ne se joue qu'une fois. */
    private boolean prepared;
    private final List<BlockPos> anchors = new ArrayList<>();
    private final List<BlockPos> activated = new ArrayList<>();
    /** L'arene finale : ZERO tant que l'Arc-en-ciel n'est pas leve. */
    private BlockPos finale = BlockPos.ZERO;
    private String finaleBoss = "";
    private long finaleTick;

    public static GameState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(GameState::new, GameState::load), KEY);
    }

    private static GameState load(CompoundTag tag, HolderLookup.Provider registries) {
        GameState state = new GameState();
        state.status = Status.values()[Math.floorMod(tag.getInt("Status"), Status.values().length)];
        state.mode = Mode.values()[Math.floorMod(tag.getInt("Mode"), Mode.values().length)];
        state.modeChosen = tag.getBoolean("ModeChosen");
        state.cycle = Math.max(1, tag.getInt("Cycle"));
        state.startTick = tag.getLong("StartTick");
        state.anchorsActive = tag.getInt("AnchorsActive");
        state.anchorsInProgress = tag.getInt("AnchorsInProgress");
        state.village = BlockPos.of(tag.getLong("Village"));
        state.prepared = tag.getBoolean("Prepared");
        state.finale = BlockPos.of(tag.getLong("Finale"));
        state.finaleBoss = tag.getString("FinaleBoss");
        state.finaleTick = tag.getLong("FinaleTick");
        for (long packed : tag.getLongArray("Anchors")) {
            state.anchors.add(BlockPos.of(packed));
        }
        for (long packed : tag.getLongArray("Activated")) {
            state.activated.add(BlockPos.of(packed));
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Status", this.status.ordinal());
        tag.putInt("Mode", this.mode.ordinal());
        tag.putBoolean("ModeChosen", this.modeChosen);
        tag.putInt("Cycle", this.cycle);
        tag.putLong("StartTick", this.startTick);
        tag.putInt("AnchorsActive", this.anchorsActive);
        tag.putInt("AnchorsInProgress", this.anchorsInProgress);
        tag.putLong("Village", this.village.asLong());
        tag.putBoolean("Prepared", this.prepared);
        tag.putLong("Finale", this.finale.asLong());
        tag.putString("FinaleBoss", this.finaleBoss);
        tag.putLong("FinaleTick", this.finaleTick);
        tag.putLongArray("Anchors", this.anchors.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Activated", this.activated.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    // ------------------------------------------------------------- lecture

    public Status status() {
        return this.status;
    }

    public Mode mode() {
        return this.mode;
    }

    /** Vrai quand l'horloge compte : le raccourci lu par la Maree et le HUD. */
    public boolean timed() {
        return this.mode == Mode.DEFI;
    }

    public boolean modeChosen() {
        return this.modeChosen;
    }

    public int cycle() {
        return this.cycle;
    }

    /**
     * Choisit le regime. Refuse une fois la partie ouverte : changer de nature
     * au milieu d'une course arreterait un chronometre deja lance, ou en
     * lancerait un sur un monde qu'on habite depuis trois heures.
     */
    public boolean chooseMode(Mode value) {
        if (this.status != Status.LOBBY) {
            return false;
        }
        this.mode = value;
        this.modeChosen = true;
        setDirty();
        return true;
    }

    /**
     * LE CYCLE SUIVANT, en monde ouvert.
     *
     * Le boss est tombe : on efface les objectifs, PAS le monde. Les
     * sanctuaires abattus restent debout ou ils sont -- ce sont des ruines
     * qu'on a prises -- et trois autres se dressent ailleurs. Le chronometre
     * n'est pas touche : il ne sert a rien ici, et le remettre a zero
     * fausserait les statistiques de partie.
     */
    public void nextCycle() {
        this.cycle++;
        this.anchorsActive = 0;
        this.anchorsInProgress = 0;
        this.activated.clear();
        this.finale = BlockPos.ZERO;
        this.finaleBoss = "";
        this.finaleTick = 0L;
        setDirty();
    }

    public int anchorsActive() {
        return this.anchorsActive;
    }

    public BlockPos village() {
        return this.village;
    }

    public boolean isPrepared() {
        return this.prepared;
    }

    public void markPrepared() {
        this.prepared = true;
        setDirty();
    }

    /** Le centre de l'arene finale, ou ZERO tant qu'elle n'est pas levee. */
    public BlockPos finale() {
        return this.finale;
    }

    public String finaleBoss() {
        return this.finaleBoss;
    }

    public long finaleTick() {
        return this.finaleTick;
    }

    public void beginFinale(BlockPos pos, String boss, long tick) {
        this.finale = pos;
        this.finaleBoss = boss;
        this.finaleTick = tick;
        setDirty();
    }

    public List<BlockPos> anchors() {
        return List.copyOf(this.anchors);
    }

    public boolean isActivated(BlockPos pos) {
        return this.activated.contains(pos);
    }

    /**
     * Le point de reapparition le plus proche : une ancre activee, ou le
     * village a defaut.
     *
     * Les ancres SONT les points de reapparition du mode : c'est ce qui lie
     * l'objectif principal a la penalite de mort, plutot que d'en faire deux
     * systemes independants.
     */
    public BlockPos respawnFor(BlockPos from) {
        BlockPos best = this.village;
        double bestDist = from.distSqr(this.village);
        for (BlockPos anchor : this.activated) {
            double dist = from.distSqr(anchor);
            if (dist < bestDist) {
                bestDist = dist;
                best = anchor;
            }
        }
        return best;
    }

    /**
     * Le palier du prochain siege : il depend du RANG d'activation, pas de
     * l'ancre choisie. Deux groupes qui lancent deux rituels en parallele
     * affrontent donc les paliers 1 et 2, dans l'ordre ou ils ont depose leur
     * Arcencium.
     */
    public int nextTier() {
        return Math.min(3, this.anchorsActive + this.anchorsInProgress + 1);
    }

    public long elapsed(ServerLevel level) {
        return this.status == Status.RUNNING ? level.getGameTime() - this.startTick : 0L;
    }

    public long remaining(ServerLevel level) {
        return Math.max(0L, GAME_TICKS - elapsed(level));
    }

    public GamePhase phase(ServerLevel level) {
        return switch (this.status) {
            case LOBBY -> GamePhase.LOBBY;
            case PROLOGUE -> GamePhase.PROLOGUE;
            // EN MONDE OUVERT, LA PHASE SUIT LES ANCRES ET NON L'HORLOGE.
            //
            // Les phases pilotent la meteo et l'equipement des monstres. Les
            // lire sur un chronometre qui ne compte plus donnerait l'Assaut
            // permanent au bout d'une heure et demie de jeu tranquille : le
            // monde durcirait tout seul pendant qu'on batit une maison.
            case RUNNING -> this.mode == Mode.LIBRE
                    ? GamePhase.forProgress(this.anchorsActive, !this.finale.equals(BlockPos.ZERO))
                    : GamePhase.forTicks(elapsed(level));
            case WON, LOST -> GamePhase.FIN;
        };
    }

    // ------------------------------------------------------------ ecriture

    /**
     * Le prologue commence au RETRAIT de la lame, pas a la mise en place.
     *
     * Tant que la lame est plantee, l'etat reste LOBBY : sinon l'interface
     * annonce « Defendez le village » alors que rien n'a encore commence.
     */
    public void beginPrologue() {
        this.status = Status.PROLOGUE;
        setDirty();
    }

    /** Retour a l'attente, sans toucher au village ni aux ancres deja poses. */
    public void returnToLobby() {
        this.status = Status.LOBBY;
        setDirty();
    }

    /** Appele au retrait de la Lame du Serment : c'est ici que le chrono part. */
    public void begin(ServerLevel level) {
        this.status = Status.RUNNING;
        this.startTick = level.getGameTime();
        this.anchorsActive = 0;
        this.anchorsInProgress = 0;
        this.activated.clear();
        this.finale = BlockPos.ZERO;
        this.finaleBoss = "";
        this.finaleTick = 0L;
        setDirty();
    }

    public void anchorStarted() {
        this.anchorsInProgress++;
        setDirty();
    }

    public void anchorFinished(BlockPos pos, boolean won) {
        this.anchorsInProgress = Math.max(0, this.anchorsInProgress - 1);
        if (won && !this.activated.contains(pos)) {
            this.activated.add(pos);
            this.anchorsActive = this.activated.size();
        }
        setDirty();
    }

    public void setVillage(BlockPos pos) {
        this.village = pos;
        setDirty();
    }

    public void setAnchors(List<BlockPos> positions) {
        this.anchors.clear();
        this.anchors.addAll(positions);
        setDirty();
    }

    /** Avance l'horloge de la partie : l'outil de test des phases et de la Maree. */
    public void skip(long ticks) {
        this.startTick -= ticks;
        setDirty();
    }

    public void finish(boolean won) {
        this.status = won ? Status.WON : Status.LOST;
        setDirty();
    }

    /**
     * Remet la partie a zero SANS toucher au village ni aux ancres deja places.
     *
     * Le regime, lui, SURVIT : c'est un choix qu'on a fait pour ce monde, pas
     * un etat de partie. Une nouvelle mise en place dans un monde ouvert doit
     * rester un monde ouvert.
     */
    public void reset() {
        this.status = Status.LOBBY;
        this.cycle = 1;
        this.startTick = 0L;
        this.anchorsActive = 0;
        this.anchorsInProgress = 0;
        this.anchors.clear();
        this.activated.clear();
        this.finale = BlockPos.ZERO;
        this.finaleBoss = "";
        this.finaleTick = 0L;
        setDirty();
    }
}
