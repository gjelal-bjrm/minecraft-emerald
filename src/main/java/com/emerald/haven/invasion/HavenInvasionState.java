package com.emerald.haven.invasion;

import com.emerald.haven.Haven;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L'etat sauvegarde de l'invasion : le mode de la ville, la generation de sa
 * population et le registre des blocs casses.
 *
 * RANGE DANS LE STOCKAGE DU NIVEAU DE HAVEN, sauvegarde avec ses troncons : un
 * arret sauvegarde en meme temps les trous et la liste qui les rebouche, et un
 * plantage les perd ensemble.
 *
 * LE REGISTRE garde, par position, l'etat complet du bloc casse, le NBT de son
 * entite de bloc s'il en avait une, et la tique (temps de jeu du monde) ou il
 * revient.
 *
 * LA GENERATION change a chaque retrait general de la population (fermeture de
 * la ville, pose) : les monstres et les habitants portent celle de leur naissance,
 * et un troncon recharge ne rend que ceux de la generation courante
 * (HavenInvasion.welcome).
 */
public final class HavenInvasionState extends SavedData {

    public static final String KEY = "emeraldweapons_haven_invasion";

    /** Un bloc casse qui attend sa reconstruction. */
    public static final class Pending {
        final BlockState state;
        @Nullable
        final CompoundTag blockEntity;
        long due;
        /** Tiques deja attendues au-dela de l'echeance parce qu'un joueur se tenait dans la case. */
        int waited;

        Pending(BlockState state, @Nullable CompoundTag blockEntity, long due) {
            this.state = state;
            this.blockEntity = blockEntity;
            this.due = due;
        }

        public BlockState state() {
            return this.state;
        }

        @Nullable
        public CompoundTag blockEntity() {
            return this.blockEntity;
        }

        public long due() {
            return this.due;
        }
    }

    private HavenInvasion.Mode mode = HavenInvasion.Mode.INVASION;
    private long generation;
    final Map<Long, Pending> pending = new LinkedHashMap<>();
    /** L'echeance la plus proche du registre (volatile) ; MIN_VALUE force un parcours. */
    long earliest = Long.MIN_VALUE;

    /** L'etat de la ville, ou null si la dimension n'est pas chargee. */
    @Nullable
    public static HavenInvasionState get(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        return level == null ? null : get(level);
    }

    public static HavenInvasionState get(ServerLevel haven) {
        return haven.getDataStorage().computeIfAbsent(
                new Factory<>(HavenInvasionState::new, HavenInvasionState::load), KEY);
    }

    static HavenInvasionState load(CompoundTag tag, HolderLookup.Provider registries) {
        HavenInvasionState state = new HavenInvasionState();
        state.readFrom(tag, registries);
        return state;
    }

    /** Relit l'etat depuis un tag : la lecture de disque, et le rechargement simule du banc d'essai. */
    void readFrom(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            this.mode = HavenInvasion.Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException e) {
            this.mode = HavenInvasion.Mode.INVASION;
        }
        this.generation = tag.getLong("Generation");
        this.pending.clear();
        this.earliest = Long.MIN_VALUE;
        var blocks = registries.lookupOrThrow(Registries.BLOCK);
        for (Tag entry : tag.getList("Blocs", Tag.TAG_COMPOUND)) {
            CompoundTag block = (CompoundTag) entry;
            BlockState state = NbtUtils.readBlockState(blocks, block.getCompound("Etat"));
            if (state.isAir()) {
                continue;                   // un bloc inconnu (mod retire) se lit en air : rien a reposer
            }
            CompoundTag be = block.contains("Entite", Tag.TAG_COMPOUND) ? block.getCompound("Entite") : null;
            this.pending.put(block.getLong("Pos"), new Pending(state, be, block.getLong("Tique")));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Mode", this.mode.name());
        tag.putLong("Generation", this.generation);
        ListTag list = new ListTag();
        for (Map.Entry<Long, Pending> entry : this.pending.entrySet()) {
            CompoundTag block = new CompoundTag();
            block.putLong("Pos", entry.getKey());
            block.put("Etat", NbtUtils.writeBlockState(entry.getValue().state));
            if (entry.getValue().blockEntity != null) {
                block.put("Entite", entry.getValue().blockEntity.copy());
            }
            block.putLong("Tique", entry.getValue().due);
            list.add(block);
        }
        tag.put("Blocs", list);
        return tag;
    }

    public HavenInvasion.Mode mode() {
        return this.mode;
    }

    void setMode(HavenInvasion.Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            setDirty();
        }
    }

    public long generation() {
        return this.generation;
    }

    void bumpGeneration() {
        this.generation++;
        setDirty();
    }

    public int pendingCount() {
        return this.pending.size();
    }

    @Nullable
    public Pending pendingAt(BlockPos pos) {
        return this.pending.get(pos.asLong());
    }
}
