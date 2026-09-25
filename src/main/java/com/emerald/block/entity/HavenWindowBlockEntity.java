package com.emerald.block.entity;

import com.emerald.block.HavenWindowBlock;
import com.emerald.haven.door.HavenWindows;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Une vitre de Jak 3 (cahier §101) : ce que sa fenetre veut -- fermee ou ouverte --, depuis quelle
 * tique l'iris s'y rend, et le rectangle de toute la fenetre reliee, dans son plan (le long de son
 * axe, et en hauteur). Chaque vitre dessine sa part de l'iris (HavenWindowRenderer) ; HavenWindows
 * les met toutes d'accord quand on clique, pose ou casse.
 */
public class HavenWindowBlockEntity extends BlockEntity {

    private boolean closed;
    /** La tique ou l'iris a commence a se rendre ou il va ; loin dans le passe, il y est. */
    private long start = Long.MIN_VALUE / 2;
    /** La fenetre entiere, en blocs, bornes comprises : le long de l'axe (u) et en hauteur (v). */
    private int u0;
    private int u1;
    private int v0;
    private int v1;

    public HavenWindowBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAVEN_WINDOW.get(), pos, state);
        int u = u(pos, state);
        this.u0 = u;
        this.u1 = u;
        this.v0 = pos.getY();
        this.v1 = pos.getY();
    }

    /** La coordonnee le long de l'axe de la vitre : x pour une vitre en X, z pour une vitre en Z. */
    public static int u(BlockPos pos, BlockState state) {
        return state.getValue(HavenWindowBlock.AXIS) == Direction.Axis.X ? pos.getX() : pos.getZ();
    }

    public boolean closed() {
        return this.closed;
    }

    public long start() {
        return this.start;
    }

    public int u0() {
        return this.u0;
    }

    public int u1() {
        return this.u1;
    }

    public int v0() {
        return this.v0;
    }

    public int v1() {
        return this.v1;
    }

    /** L'iris va la, depuis cette tique, dans cette fenetre ; le client en est averti. */
    public void set(boolean closed, long start, int u0, int u1, int v0, int v1) {
        this.closed = closed;
        this.start = start;
        this.u0 = u0;
        this.u1 = u1;
        this.v0 = v0;
        this.v1 = v1;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** L'avancee de l'iris vers sa cible, de 0 (il part) a 1 (il y est). */
    public float progress(long now, float partial) {
        return Mth.clamp((now - this.start + partial) / HavenWindows.DURATION, 0.0F, 1.0F);
    }

    /** L'ouverture de la fenetre : 1 grande ouverte (plus d'iris), 0 fermee ; en douceur aux deux bouts. */
    public float openness(long now, float partial) {
        float t = this.progress(now, partial);
        float eased = t * t * (3.0F - 2.0F * t);
        return this.closed ? 1.0F - eased : eased;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Fermee", this.closed);
        tag.putLong("Debut", this.start);
        tag.putIntArray("Fenetre", new int[]{this.u0, this.u1, this.v0, this.v1});
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.closed = tag.getBoolean("Fermee");
        this.start = tag.contains("Debut") ? tag.getLong("Debut") : Long.MIN_VALUE / 2;
        int[] window = tag.getIntArray("Fenetre");
        if (window.length == 4) {
            this.u0 = window[0];
            this.u1 = window[1];
            this.v0 = window[2];
            this.v1 = window[3];
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
