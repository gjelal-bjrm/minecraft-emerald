package com.emerald.haven;

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
     * LE MODE DE JEU D'ORIGINE, PAR JOUEUR.
     *
     * Sauvegarde, parce qu'un joueur deconnecte dans la ville, un serveur qui
     * s'arrete ou un depart pendant qu'il est hors ligne ne doivent pas le
     * laisser en aventure au village, ou il ne pourrait ni miner ni poser.
     */
    private final Map<UUID, GameType> originalModes = new HashMap<>();

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
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Phase", this.phase.name());
        tag.putBoolean("Wanted", this.wanted);
        tag.putBoolean("Built", this.built);
        tag.putBoolean("ResetPending", this.resetPending);
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
}
