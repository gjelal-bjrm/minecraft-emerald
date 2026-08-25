package com.emerald.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Support de rendu de la Lame du Serment.
 *
 * Elle ne stocke rien : son seul role est de permettre a un renderer de
 * dessiner la vraie epee en trois dimensions, plantee dans le sol. Un modele
 * de bloc ordinaire ne saurait pas le faire -- deux plans croises portant la
 * texture donnent une decalcomanie, pas une lame.
 */
public class OathBladeBlockEntity extends BlockEntity {

    public OathBladeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OATH_BLADE.get(), pos, state);
    }
}
