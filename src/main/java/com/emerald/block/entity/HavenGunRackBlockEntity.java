package com.emerald.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'entite du ratelier d'armes du QG : sans donnees, elle n'existe que pour que le client
 * dessine le Morph Gun pose dessus (HavenGunRackRenderer).
 */
public class HavenGunRackBlockEntity extends BlockEntity {

    public HavenGunRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HAVEN_GUN_RACK.get(), pos, state);
    }
}
