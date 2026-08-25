package com.emerald.game;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

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
    public static final long GAME_TICKS = 50L * 60L * 20L;

    /** Rayon de la zone de jeu, en blocs. */
    public static final int PLAY_RADIUS = 750;

    public enum Status { LOBBY, PROLOGUE, RUNNING, WON, LOST }

    private Status status = Status.LOBBY;
    private long startTick;
    private int anchorsActive;
    private int anchorsInProgress;

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
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Status", this.status.ordinal());
        tag.putLong("StartTick", this.startTick);
        tag.putInt("AnchorsActive", this.anchorsActive);
        tag.putInt("AnchorsInProgress", this.anchorsInProgress);
        return tag;
    }

    // ------------------------------------------------------------- lecture

    public Status status() {
        return this.status;
    }

    public int anchorsActive() {
        return this.anchorsActive;
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

    public void beginPrologue() {
        this.status = Status.PROLOGUE;
        setDirty();
    }

    /** Appele au retrait de la Lame du Serment : c'est ici que le chrono part. */
    public void begin(ServerLevel level) {
        this.status = Status.RUNNING;
        this.startTick = level.getGameTime();
        this.anchorsActive = 0;
        this.anchorsInProgress = 0;
        setDirty();
    }

    public void anchorStarted() {
        this.anchorsInProgress++;
        setDirty();
    }

    public void anchorFinished(boolean won) {
        this.anchorsInProgress = Math.max(0, this.anchorsInProgress - 1);
        if (won) {
            this.anchorsActive++;
        }
        setDirty();
    }

    public void finish(boolean won) {
        this.status = won ? Status.WON : Status.LOST;
        setDirty();
    }

    public void reset() {
        this.status = Status.LOBBY;
        this.startTick = 0L;
        this.anchorsActive = 0;
        this.anchorsInProgress = 0;
        setDirty();
    }
}
