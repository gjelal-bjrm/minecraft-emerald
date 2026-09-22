package com.emerald.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Un portail d'Arcencium (ArcPortalBlock) : il se dessine (ArcPortalRenderer). Celui que pose
 * le jeu est TEMPORAIRE : quand plus rien ne le reconnait -- l'Heure Doree finie, l'Aurore
 * finie, le depart du QG passe, ou un redemarrage qui a tout oublie --, il s'efface de
 * lui-meme. Un portail pose a la main (vitrine des photos) reste.
 */
public class ArcPortalBlockEntity extends BlockEntity {

    private static final int CHECK = 40;

    private boolean temporary;

    public ArcPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARC_PORTAL.get(), pos, state);
    }

    public void setTemporary(boolean temporary) {
        this.temporary = temporary;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, ArcPortalBlockEntity portal) {
        if (portal.temporary && level.getGameTime() % CHECK == 0 && level instanceof ServerLevel server && !known(server, pos)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Un des trois poseurs d'arches connait-il encore celle-ci ? */
    private static boolean known(ServerLevel level, BlockPos pos) {
        return com.emerald.weather.GoldenGate.isGate(pos)
                || com.emerald.mine.AuroreCaves.isArch(pos)
                || com.emerald.haven.journey.HavenDeparture.isGate(level, pos);
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
