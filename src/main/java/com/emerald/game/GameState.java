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
    public static final long GAME_MINUTES = 60L;
    public static final long GAME_TICKS = GAME_MINUTES * 60L * 20L;

    /** Rayon de la zone de jeu, en blocs. */
    public static final int PLAY_RADIUS = 750;

    public enum Status { LOBBY, PROLOGUE, RUNNING, WON, LOST }

    /** Distance entre le village et chaque ancre. */
    public static final int ANCHOR_DISTANCE = 450;

    private Status status = Status.LOBBY;
    private long startTick;
    private int anchorsActive;
    private int anchorsInProgress;
    private BlockPos village = BlockPos.ZERO;
    /** Vrai des que le monde a ete prepare : la mise en place ne se joue qu'une fois. */
    private boolean prepared;
    private final List<BlockPos> anchors = new ArrayList<>();
    private final List<BlockPos> activated = new ArrayList<>();

    public static GameState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(GameState::new, GameState::load), KEY);
    }

    private static GameState load(CompoundTag tag, HolderLookup.Provider registries) {
        GameState state = new GameState();
        state.status = Status.values()[Math.floorMod(tag.getInt("Status"), Status.values().length)];
        state.startTick = tag.getLong("StartTick");
        state.anchorsActive = tag.getInt("AnchorsActive");
        state.anchorsInProgress = tag.getInt("AnchorsInProgress");
        state.village = BlockPos.of(tag.getLong("Village"));
        state.prepared = tag.getBoolean("Prepared");
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
        tag.putLong("StartTick", this.startTick);
        tag.putInt("AnchorsActive", this.anchorsActive);
        tag.putInt("AnchorsInProgress", this.anchorsInProgress);
        tag.putLong("Village", this.village.asLong());
        tag.putBoolean("Prepared", this.prepared);
        tag.putLongArray("Anchors", this.anchors.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Activated", this.activated.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    // ------------------------------------------------------------- lecture

    public Status status() {
        return this.status;
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
            case RUNNING -> GamePhase.forTicks(elapsed(level));
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

    public void finish(boolean won) {
        this.status = won ? Status.WON : Status.LOST;
        setDirty();
    }

    /** Remet la partie a zero SANS toucher au village ni aux ancres deja places. */
    public void reset() {
        this.status = Status.LOBBY;
        this.startTick = 0L;
        this.anchorsActive = 0;
        this.anchorsInProgress = 0;
        this.anchors.clear();
        this.activated.clear();
        setDirty();
    }
}
