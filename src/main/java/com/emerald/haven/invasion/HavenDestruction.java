package com.emerald.haven.invasion;

import com.emerald.haven.Haven;
import com.emerald.jak.vehicle.JakVehicleEntity;
import com.emerald.jak.vehicle.VehiclePart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Le decor destructible de Haven : ce qu'une arme casse revient a l'identique.
 *
 * API POUR LES AUTRES CHANTIERS :
 * - {@link #tryBreak(ServerLevel, BlockPos, Entity)} : casse un bloc par une arme ; vrai s'il est casse ;
 * - {@link #breakBlock} : la meme chose, avec la raison d'un refus ({@link Result}) ;
 * - {@link #isPending} et {@link #pendingCount} : ce qui attend sa reconstruction ;
 * - {@link #rebuildAll} : tout reconstruire tout de suite.
 *
 * CE QUE FAIT UNE CASSE. Seulement dans Haven, ville ouverte (lobby ouvert, ville
 * posee, aucune pose en cours). Refusee si le bloc est de l'air, deja en attente,
 * protege (HavenProtection), liquide ou baigne (waterlogged), s'il touche un
 * liquide par une de ses six faces (l'eau entrerait dans le trou), ou s'il porte
 * un cadre ou un tableau (qui tomberait avec son objet). Sinon le bloc est
 * retire SANS BUTIN (setBlock, pas destroyBlock ; le contenu d'un coffre ou d'un
 * lutrin est vide avant, sinon onRemove le jetterait au sol), sans prevenir ses
 * voisins (drapeaux 2|16, ceux de la pose de la ville : rien ne coule, rien ne
 * tombe), avec au plus {@link #EFFECTS_PER_TICK} effets de casse vanilla (son et
 * eclats) par tique. L'etat complet et le NBT de l'entite de bloc sont memorises.
 * L'autre moitie d'une porte, d'une plante haute ou d'un lit part avec lui, et
 * les voisins qui ne tiendraient plus (torche, lanterne, tapis, rail...) aussi,
 * jusqu'a {@link #MAX_GROUP} blocs.
 *
 * LA RECONSTRUCTION, entre {@link #DELAY_MIN_TICKS} et {@link #DELAY_MAX_TICKS}
 * tiques plus tard (tirage par casse), en commencant par les blocs du dessous,
 * au plus {@link #REBUILDS_PER_TICK} par tique ; l'ecoulement programme dans la
 * case est efface. Une case occupee -- joueur, monstre,
 * habitant, vehicule -- attend qu'elle se libere : rien ne se referme sur personne.
 * Troncon non charge : on attend qu'il le soit.
 *
 * PLAFONDS : {@link #BREAKS_PER_TICK} casses par tique (une explosion de dix blocs
 * ne vide pas le quartier en une tique), {@link #MAX_PENDING} blocs en attente.
 *
 * LE REGISTRE EST SAUVEGARDE avec le niveau de Haven (HavenInvasionState), et
 * reconstruit en entier au demarrage du serveur, au depart vers la partie, et
 * au debut d'une pose de la ville (HavenInvasion appelle ces reconstructions).
 */
public final class HavenDestruction {

    /** Ce qu'une casse a donne. */
    public enum Result { BROKEN, NOT_HAVEN, CLOSED, UNLOADED, AIR, PENDING, PROTECTED, LIQUID, DECOR, TICK_LIMIT, FULL }

    /** Dix a quinze secondes. */
    public static final int DELAY_MIN_TICKS = 200;
    public static final int DELAY_MAX_TICKS = 300;
    public static final int BREAKS_PER_TICK = 96;
    public static final int REBUILDS_PER_TICK = 256;
    public static final int MAX_PENDING = 8192;
    public static final int EFFECTS_PER_TICK = 8;
    /** Un bloc casse et ce qui tombe avec lui. */
    public static final int MAX_GROUP = 16;
    /**
     * Une case occupee attend, par pas de dix tiques, SANS LIMITE. Le premier reglage
     * attendait cinq secondes puis reposait le bloc quand meme : un joueur reste dans
     * un trou plus de cinq secondes, et il se retrouvait mure (revue du 13 sept.). Les
     * monstres, les habitants et les vehicules comptent aussi : un bloc dans un zombie
     * l'etouffe, un bloc dans une voiture la bloque.
     */
    private static final int OCCUPIED_WAIT_STEP = 10;

    /** 2|16 : ni voisins prevenus, ni formes recalculees. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static long countedTick = Long.MIN_VALUE;
    private static int breaksThisTick;
    private static int effectsThisTick;

    private HavenDestruction() {
    }

    // ================================================================ casse

    /**
     * Casse un bloc de Haven par une arme.
     *
     * @param level  le niveau (hors de Haven : refuse)
     * @param pos    la position du monde
     * @param source le tireur ou le projectile, pour le journal ; peut etre null
     * @return vrai si le bloc est casse et inscrit au registre
     */
    public static boolean tryBreak(ServerLevel level, BlockPos pos, @Nullable Entity source) {
        return breakBlock(level, pos, source) == Result.BROKEN;
    }

    /** Comme {@link #tryBreak}, avec la raison d'un refus. */
    public static Result breakBlock(ServerLevel level, BlockPos pos, @Nullable Entity source) {
        if (!Haven.is(level)) {
            return Result.NOT_HAVEN;
        }
        MinecraftServer server = level.getServer();
        if (!HavenInvasion.cityOpen(server)) {
            return Result.CLOSED;
        }
        countTick(level);
        if (breaksThisTick >= BREAKS_PER_TICK) {
            return Result.TICK_LIMIT;
        }
        HavenInvasionState state = HavenInvasionState.get(level);
        if (state.pending.size() >= MAX_PENDING) {
            return Result.FULL;
        }
        Result refusal = refusal(level, state, pos);
        if (refusal != null) {
            return refusal;
        }
        BlockState block = level.getBlockState(pos);
        long due = level.getGameTime() + DELAY_MIN_TICKS
                + level.random.nextInt(DELAY_MAX_TICKS - DELAY_MIN_TICKS + 1);

        List<BlockPos> group = new ArrayList<>();
        remove(level, state, pos, due);
        group.add(pos);
        BlockPos other = otherHalf(block, pos);
        if (other != null && level.isLoaded(other) && level.getBlockState(other).is(block.getBlock())
                && refusal(level, state, other) == null) {
            remove(level, state, other, due);
            group.add(other);
        }
        // ce qui ne tient plus : torches, lanternes, tapis, rails, plantes, moitie restante
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(group);
        while (!queue.isEmpty() && group.size() < MAX_GROUP) {
            BlockPos at = queue.poll();
            for (Direction direction : Direction.values()) {
                if (group.size() >= MAX_GROUP) {
                    break;
                }
                BlockPos next = at.relative(direction);
                if (!level.isLoaded(next)) {
                    continue;
                }
                BlockState neighbor = level.getBlockState(next);
                if (neighbor.isAir() || neighbor.canSurvive(level, next) || refusal(level, state, next) != null) {
                    continue;
                }
                remove(level, state, next, due);
                group.add(next);
                queue.add(next);
            }
        }
        breaksThisTick++;
        if (effectsThisTick < EFFECTS_PER_TICK) {
            effectsThisTick++;
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(block));
        }
        state.earliest = Math.min(state.earliest, due);
        state.setDirty();
        return Result.BROKEN;
    }

    /** La raison de ne pas casser ce bloc, ou null. */
    @Nullable
    private static Result refusal(ServerLevel level, HavenInvasionState state, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return Result.UNLOADED;
        }
        if (state.pending.containsKey(pos.asLong())) {
            return Result.PENDING;
        }
        if (HavenProtection.isProtected(level, pos)) {
            return Result.PROTECTED;
        }
        BlockState block = level.getBlockState(pos);
        if (block.isAir()) {
            return Result.AIR;
        }
        if (!block.getFluidState().isEmpty() || touchesFluid(level, pos)) {
            return Result.LIQUID;
        }
        AABB cell = new AABB(pos).inflate(0.05);
        if (!level.getEntitiesOfClass(HangingEntity.class, cell.inflate(1.0),
                hanging -> hanging.getBoundingBox().inflate(0.1).intersects(cell)).isEmpty()) {
            return Result.DECOR;
        }
        return null;
    }

    private static boolean touchesFluid(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (level.isLoaded(next) && !level.getFluidState(next).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** L'autre moitie d'une porte, d'une plante haute ou d'un lit ; null s'il n'y en a pas. */
    @Nullable
    private static BlockPos otherHalf(BlockState state, BlockPos pos) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        }
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return pos.relative(BedBlock.getConnectedDirection(state));
        }
        return null;
    }

    /** Memorise et retire un bloc, sans butin ni voisins prevenus. */
    private static void remove(ServerLevel level, HavenInvasionState state, BlockPos pos, long due) {
        BlockState block = level.getBlockState(pos);
        BlockEntity entity = level.getBlockEntity(pos);
        CompoundTag tag = entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
        Clearable.tryClear(entity);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
        state.pending.put(pos.asLong(), new HavenInvasionState.Pending(block, tag, due));
    }

    private static void countTick(ServerLevel level) {
        long now = level.getGameTime();
        if (now != countedTick) {
            countedTick = now;
            breaksThisTick = 0;
            effectsThisTick = 0;
        }
    }

    // ================================================================ reconstruction

    /** Vrai si ce bloc attend sa reconstruction. */
    public static boolean isPending(ServerLevel level, BlockPos pos) {
        return Haven.is(level) && HavenInvasionState.get(level).pending.containsKey(pos.asLong());
    }

    /** Le nombre de blocs en attente ; 0 si la ville n'est pas chargee. */
    public static int pendingCount(MinecraftServer server) {
        HavenInvasionState state = HavenInvasionState.get(server);
        return state == null ? 0 : state.pendingCount();
    }

    /**
     * Une tique de reconstruction (appelee par HavenInvasion).
     *
     * On ne parcourt le registre que si l'echeance la plus proche est passee.
     */
    static void tick(ServerLevel level) {
        HavenInvasionState state = HavenInvasionState.get(level);
        if (state.pending.isEmpty()) {
            state.earliest = Long.MAX_VALUE;
            return;
        }
        long now = level.getGameTime();
        if (now < state.earliest) {
            return;
        }
        List<Map.Entry<Long, HavenInvasionState.Pending>> due = new ArrayList<>();
        long next = Long.MAX_VALUE;
        for (Map.Entry<Long, HavenInvasionState.Pending> entry : state.pending.entrySet()) {
            if (entry.getValue().due <= now) {
                due.add(entry);
            } else {
                next = Math.min(next, entry.getValue().due);
            }
        }
        due.sort(Comparator.comparingInt(entry -> BlockPos.getY(entry.getKey())));
        int done = 0;
        for (Map.Entry<Long, HavenInvasionState.Pending> entry : due) {
            if (done >= REBUILDS_PER_TICK) {
                next = now + 1;
                break;
            }
            BlockPos pos = BlockPos.of(entry.getKey());
            HavenInvasionState.Pending pending = entry.getValue();
            if (!level.isLoaded(pos)) {
                pending.due = now + 20;
                next = Math.min(next, pending.due);
                continue;
            }
            if (occupied(level, pos)) {
                pending.due = now + OCCUPIED_WAIT_STEP;
                pending.waited += OCCUPIED_WAIT_STEP;
                next = Math.min(next, pending.due);
                continue;
            }
            restore(level, pos, pending);
            state.pending.remove(entry.getKey());
            done++;
        }
        state.earliest = next;
        if (done > 0) {
            state.setDirty();
        }
    }

    /**
     * Reconstruit tout le registre tout de suite, du bas vers le haut, troncons
     * charges s'il le faut : depart vers la partie, demarrage du serveur.
     *
     * @return le nombre de blocs reposes
     */
    public static int rebuildAll(ServerLevel level) {
        if (!Haven.is(level)) {
            return 0;
        }
        HavenInvasionState state = HavenInvasionState.get(level);
        if (state.pending.isEmpty()) {
            return 0;
        }
        List<Map.Entry<Long, HavenInvasionState.Pending>> all = new ArrayList<>(state.pending.entrySet());
        all.sort(Comparator.comparingInt(entry -> BlockPos.getY(entry.getKey())));
        for (Map.Entry<Long, HavenInvasionState.Pending> entry : all) {
            restore(level, BlockPos.of(entry.getKey()), entry.getValue());
        }
        state.pending.clear();
        state.earliest = Long.MAX_VALUE;
        state.setDirty();
        return all.size();
    }

    /**
     * Au debut d'une pose de la ville : on ne repose que les trous encore vides.
     *
     * La pose ecrit elle-meme chaque cellule ; un bloc qu'elle aurait deja pose
     * (ou qu'une remise au generateur aurait remplace) n'est pas ecrase par
     * l'ancien. Le registre est ensuite vide.
     *
     * @return le nombre de blocs reposes
     */
    static int flushForPose(ServerLevel level) {
        HavenInvasionState state = HavenInvasionState.get(level);
        int restored = 0;
        List<Map.Entry<Long, HavenInvasionState.Pending>> all = new ArrayList<>(state.pending.entrySet());
        all.sort(Comparator.comparingInt(entry -> BlockPos.getY(entry.getKey())));
        for (Map.Entry<Long, HavenInvasionState.Pending> entry : all) {
            BlockPos pos = BlockPos.of(entry.getKey());
            if (level.getBlockState(pos).isAir()) {
                restore(level, pos, entry.getValue());
                restored++;
            }
        }
        state.pending.clear();
        state.earliest = Long.MAX_VALUE;
        state.setDirty();
        return restored;
    }

    /** Repose l'etat et le NBT, efface l'ecoulement programme, et previent les clients. */
    private static void restore(ServerLevel level, BlockPos pos, HavenInvasionState.Pending pending) {
        level.setBlock(pos, pending.state, QUIET);
        level.getFluidTicks().clearArea(new BoundingBox(pos));
        if (pending.blockEntity != null) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null) {
                entity.loadWithComponents(pending.blockEntity, level.registryAccess());
                entity.setChanged();
            }
        }
        level.sendBlockUpdated(pos, pending.state, pending.state, Block.UPDATE_CLIENTS);
    }

    /** Quelqu'un dans la case : joueur, monstre, habitant, ou une voiture et ses parties. */
    private static boolean occupied(ServerLevel level, BlockPos pos) {
        return !level.getEntitiesOfClass(Entity.class, new AABB(pos), e -> e.isAlive()
                && (e instanceof LivingEntity || e instanceof JakVehicleEntity || e instanceof VehiclePart)).isEmpty();
    }

    // ================================================================ banc d'essai

    /**
     * Un redemarrage simule : le registre est ecrit comme a la sauvegarde, oublie,
     * puis relu depuis ce tag. Rien n'est repose.
     *
     * @return le nombre d'entrees relues
     */
    static int reloadForTest(ServerLevel level) {
        HavenInvasionState state = HavenInvasionState.get(level);
        CompoundTag tag = state.save(new CompoundTag(), level.registryAccess());
        state.pending.clear();
        state.readFrom(tag, level.registryAccess());
        return state.pending.size();
    }
}
