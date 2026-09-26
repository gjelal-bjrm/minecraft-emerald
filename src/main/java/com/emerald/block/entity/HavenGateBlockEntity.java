package com.emerald.block.entity;

import com.emerald.haven.journey.HavenPortal;
import com.emerald.haven.journey.HavenReturn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * La porte precurseur de la victoire. Celle que pose le retour du Defi est TEMPORAIRE : si
 * le retour ne la reconnait plus -- un redemarrage l'a oublie, rien n'etant sauvegarde --,
 * elle s'efface d'elle-meme. Une porte posee a la main (vitrine des photos) reste.
 *
 * LE PORTAIL DE HAVEN (HavenPortal, §106) : celui qu'un joueur a fabrique et pose a un maitre
 * (Owner) ; il s'efface s'il n'est plus son portail -- le joueur en a pose un autre pendant que
 * celui-ci dormait dans un tronçon decharge. L'anneau d'un appartement est temporaire, comme la
 * porte de la victoire.
 */
public class HavenGateBlockEntity extends BlockEntity {

    private static final int CHECK = 40;

    private boolean temporary;
    @Nullable
    private UUID owner;

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

    /** Le joueur qui a pose ce Portail de Haven ; null pour une porte du jeu. */
    @Nullable
    public UUID owner() {
        return this.owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, HavenGateBlockEntity gate) {
        if (level.getGameTime() % CHECK != 0) {
            return;
        }
        boolean stale = gate.temporary ? !HavenReturn.isGate(level, pos) && !HavenPortal.isRing(level, pos)
                : gate.owner != null && !HavenPortal.isPortal(level, pos);
        if (stale) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Temporaire", this.temporary);
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.temporary = tag.getBoolean("Temporaire");
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}
