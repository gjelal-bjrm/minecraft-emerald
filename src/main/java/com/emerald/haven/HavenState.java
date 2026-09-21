package com.emerald.haven;

import com.emerald.game.GameState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * L'etat de la ville, sauvegarde avec le monde, a cote de celui de la partie.
 *
 * UNE SAVEDDATA A PART, ET NON DES CHAMPS DE GAMESTATE. Le statut de la partie
 * est sauvegarde et transmis au client par son rang dans l'enumeration :
 * y inserer une phase de lobby decalerait tous les mondes existants et le HUD.
 * Ici, la phase est sauvegardee par son NOM.
 *
 * Rangee dans le stockage de l'overworld, comme GameState : elle se lit donc
 * de la meme facon depuis n'importe quel niveau.
 *
 * UN MONDE ANCIEN LIT « ABSENTE » et ne change pas : la ville ne s'y pose que
 * sur commande.
 */
public final class HavenState extends SavedData {

    public static final String KEY = "emeraldweapons_haven";

    /**
     * ABSENTE : pas de lobby (monde ancien, ou pas encore ouvert).
     * CHANTIER : la ville se pose au premier demarrage.
     * ACCUEIL : la ville est prete a recevoir les joueurs.
     * PARTI : le vote a eu lieu, la partie se joue au village.
     */
    public enum Phase { ABSENTE, CHANTIER, ACCUEIL, PARTI }

    private Phase phase = Phase.ABSENTE;
    /** Une pose a ete demandee dans ce monde. */
    private boolean wanted;
    /** La derniere pose demandee est allee a son terme. */
    private boolean built;
    /** La derniere pose demandee commencait par remettre la boite au generateur. */
    private boolean resetPending;
    private String sha1 = "";
    private BlockPos origin = Haven.ORIGIN;
    private int width = Haven.GRID_WIDTH;
    private int height = Haven.GRID_HEIGHT;
    private int depth = Haven.GRID_DEPTH;
    /**
     * L'ATELIER (HavenAtelier) : le monde ou le joueur retouche la ville. Les
     * operateurs y batissent librement, la ville y est vide, et son releve
     * (JakCityCapture) repart dans le mod. Faux partout ailleurs, et dans tous les
     * mondes existants : la cle manque, getBoolean rend faux.
     */
    private boolean atelier;
    /**
     * LE MODE DE JEU D'ORIGINE, PAR JOUEUR.
     *
     * Sauvegarde, parce qu'un joueur deconnecte dans la ville, un serveur qui
     * s'arrete ou un depart pendant qu'il est hors ligne ne doivent pas le
     * laisser en aventure au village, ou il ne pourrait ni miner ni poser.
     */
    private final Map<UUID, GameType> originalModes = new HashMap<>();
    /**
     * L'APPARTEMENT DE CHAQUE JOUEUR : {salle, place}, salle a partir de 0.
     *
     * Sauvegarde, et jamais libere tant que le lobby est ouvert : un joueur qui
     * se deconnecte retrouve sa place, et celui qui arrive apres lui ne la
     * prend pas. Oublie a la reouverture du lobby (HavenArrival.reopen).
     * Rangee dans l'ordre d'inscription, qui est l'ordre des places.
     */
    private final Map<UUID, int[]> apartments = new LinkedHashMap<>();
    /**
     * LES VOTES DU QG, par joueur, sauvegardes : un serveur qui redemarre ne
     * fait pas revoter. Effaces au depart et a la reouverture.
     */
    private final Map<UUID, GameState.Mode> votes = new HashMap<>();

    public static HavenState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(HavenState::new, HavenState::load), KEY);
    }

    private static HavenState load(CompoundTag tag, HolderLookup.Provider registries) {
        HavenState state = new HavenState();
        try {
            state.phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException e) {
            state.phase = Phase.ABSENTE;
        }
        state.wanted = tag.getBoolean("Wanted");
        state.built = tag.getBoolean("Built");
        state.resetPending = tag.getBoolean("ResetPending");
        state.atelier = tag.getBoolean("Atelier");
        state.sha1 = tag.getString("Sha1");
        if (tag.contains("Origin")) {
            state.origin = BlockPos.of(tag.getLong("Origin"));
        }
        if (tag.contains("Width")) {
            state.width = tag.getInt("Width");
            state.height = tag.getInt("Height");
            state.depth = tag.getInt("Depth");
        }
        for (Tag entry : tag.getList("Modes", Tag.TAG_COMPOUND)) {
            CompoundTag mode = (CompoundTag) entry;
            if (mode.hasUUID("Player")) {
                state.originalModes.put(mode.getUUID("Player"),
                        GameType.byName(mode.getString("Mode"), GameType.SURVIVAL));
            }
        }
        for (Tag entry : tag.getList("Apartments", Tag.TAG_COMPOUND)) {
            CompoundTag apartment = (CompoundTag) entry;
            if (apartment.hasUUID("Player")) {
                state.apartments.put(apartment.getUUID("Player"),
                        new int[]{apartment.getInt("Room"), apartment.getInt("Slot")});
            }
        }
        for (Tag entry : tag.getList("Votes", Tag.TAG_COMPOUND)) {
            CompoundTag vote = (CompoundTag) entry;
            if (vote.hasUUID("Player")) {
                try {
                    state.votes.put(vote.getUUID("Player"), GameState.Mode.valueOf(vote.getString("Mode")));
                } catch (IllegalArgumentException ignored) {
                    // un vote illisible est un vote absent : on revote
                }
            }
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Phase", this.phase.name());
        tag.putBoolean("Wanted", this.wanted);
        tag.putBoolean("Built", this.built);
        tag.putBoolean("ResetPending", this.resetPending);
        tag.putBoolean("Atelier", this.atelier);
        tag.putString("Sha1", this.sha1);
        tag.putLong("Origin", this.origin.asLong());
        tag.putInt("Width", this.width);
        tag.putInt("Height", this.height);
        tag.putInt("Depth", this.depth);
        ListTag modes = new ListTag();
        for (Map.Entry<UUID, GameType> entry : this.originalModes.entrySet()) {
            CompoundTag mode = new CompoundTag();
            mode.putUUID("Player", entry.getKey());
            mode.putString("Mode", entry.getValue().getName());
            modes.add(mode);
        }
        tag.put("Modes", modes);
        ListTag apartmentList = new ListTag();
        for (Map.Entry<UUID, int[]> entry : this.apartments.entrySet()) {
            CompoundTag apartment = new CompoundTag();
            apartment.putUUID("Player", entry.getKey());
            apartment.putInt("Room", entry.getValue()[0]);
            apartment.putInt("Slot", entry.getValue()[1]);
            apartmentList.add(apartment);
        }
        tag.put("Apartments", apartmentList);
        ListTag voteList = new ListTag();
        for (Map.Entry<UUID, GameState.Mode> entry : this.votes.entrySet()) {
            CompoundTag vote = new CompoundTag();
            vote.putUUID("Player", entry.getKey());
            vote.putString("Mode", entry.getValue().name());
            voteList.add(vote);
        }
        tag.put("Votes", voteList);
        return tag;
    }

    // ------------------------------------------------------------- lecture

    public Phase phase() {
        return this.phase;
    }

    public boolean wanted() {
        return this.wanted;
    }

    public boolean built() {
        return this.built;
    }

    /** Ce monde est-il l'atelier de la ville ? */
    public boolean atelier() {
        return this.atelier;
    }

    public void setAtelier(boolean atelier) {
        if (this.atelier != atelier) {
            this.atelier = atelier;
            this.setDirty();
        }
    }

    public boolean resetPending() {
        return this.resetPending;
    }

    public String sha1() {
        return this.sha1;
    }

    public BlockPos origin() {
        return this.origin;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int depth() {
        return this.depth;
    }

    @Nullable
    public GameType originalMode(UUID player) {
        return this.originalModes.get(player);
    }

    // ------------------------------------------------------------- ecriture

    public void setPhase(Phase phase) {
        this.phase = phase;
        setDirty();
    }

    /** Une pose commence : tant qu'elle n'est pas finie, le demarrage suivant la reprend. */
    public void beginPose(BlockPos origin, boolean reset) {
        this.wanted = true;
        this.built = false;
        this.resetPending = reset;
        this.origin = origin.immutable();
        setDirty();
    }

    public void finishPose(String sha1, BlockPos origin, int width, int height, int depth) {
        this.built = true;
        this.resetPending = false;
        this.sha1 = sha1;
        this.origin = origin.immutable();
        this.width = width;
        this.height = height;
        this.depth = depth;
        setDirty();
    }

    /** Retient le mode d'entree dans la ville, sans ecraser celui d'une entree precedente. */
    public void rememberMode(UUID player, GameType mode) {
        if (this.originalModes.putIfAbsent(player, mode) == null) {
            setDirty();
        }
    }

    /** Rend et oublie le mode d'origine, ou null s'il n'y en avait pas. */
    @Nullable
    public GameType forgetMode(UUID player) {
        GameType mode = this.originalModes.remove(player);
        if (mode != null) {
            setDirty();
        }
        return mode;
    }

    // ------------------------------------------------------------- appartements

    /** {salle, place} du joueur (une copie), ou null s'il n'en a pas encore. */
    @Nullable
    public int[] apartment(UUID player) {
        int[] apartment = this.apartments.get(player);
        return apartment == null ? null : apartment.clone();
    }

    /** Le nombre d'inscrits de chaque salle. */
    public int[] roomCounts(int rooms) {
        int[] counts = new int[rooms];
        for (int[] apartment : this.apartments.values()) {
            if (apartment[0] >= 0 && apartment[0] < rooms) {
                counts[apartment[0]]++;
            }
        }
        return counts;
    }

    public int apartmentCount() {
        return this.apartments.size();
    }

    public void assignApartment(UUID player, int room, int slot) {
        this.apartments.put(player, new int[]{room, slot});
        setDirty();
    }

    /** Retire une inscription : le banc d'essai, apres son joueur simule, et rien d'autre. */
    public void removeApartment(UUID player) {
        if (this.apartments.remove(player) != null) {
            setDirty();
        }
    }

    public void clearApartments() {
        if (!this.apartments.isEmpty()) {
            this.apartments.clear();
            setDirty();
        }
    }

    // ------------------------------------------------------------- votes

    @Nullable
    public GameState.Mode vote(UUID player) {
        return this.votes.get(player);
    }

    /** @return vrai si le vote a change */
    public boolean setVote(UUID player, GameState.Mode mode) {
        if (this.votes.put(player, mode) == mode) {
            return false;
        }
        setDirty();
        return true;
    }

    public void clearVotes() {
        if (!this.votes.isEmpty()) {
            this.votes.clear();
            setDirty();
        }
    }
}
