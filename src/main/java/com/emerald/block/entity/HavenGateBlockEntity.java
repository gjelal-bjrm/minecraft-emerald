package com.emerald.block.entity;

import com.emerald.haven.journey.HavenReturn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La porte precurseur de la victoire. Celle que pose le retour du Defi est TEMPORAIRE : si
 * le retour ne la reconnait plus -- un redemarrage l'a oublie, rien n'etant sauvegarde --,
 * elle s'efface d'elle-meme. Une porte posee a la main (vitrine des photos) reste.
 */
public class HavenGateBlockEntity extends BlockEntity {

    private static final int CHECK = 40;

    private boolean temporary;

    public HavenGateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAVEN_GATE.get(), pos, state);
    }

    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
        this.setChanged();
    }

    public boolean temporary() {
        return this.temporary;
    }

    public static void serverTick(Level level, BlockPos pos, HavenGateBlockEntity gate) {
        if (gate.temporary && level.getGameTime() % CHECK == 0 && !HavenReturn.isGate(level, pos)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Temporaire", this.temporary);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.temporary = tag.getBoolean("Temporaire");
    }
}
