package com.emerald.block;

import com.emerald.block.entity.ArcenciumChestBlockEntity;
import com.emerald.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Coffre d'Arcencium.
 *
 * ChestBlock gere deja tout : orientation, appairage gauche/droite, animation
 * du couvercle. On ne lui substitue que le type de block entity, ce qui suffit
 * a lui donner sa propre texture via ArcenciumChestRenderer.
 */
public class ArcenciumChestBlock extends ChestBlock {

    public ArcenciumChestBlock(Properties properties) {
        super(properties, ModBlockEntities.ARCENCIUM_CHEST::get);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcenciumChestBlockEntity(pos, state);
    }
}
