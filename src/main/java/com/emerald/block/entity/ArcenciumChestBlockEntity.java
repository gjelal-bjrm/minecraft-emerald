package com.emerald.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Coffre d'Arcencium. Se comporte comme un coffre vanilla, texture a part. */
public class ArcenciumChestBlockEntity extends ChestBlockEntity {

    public ArcenciumChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCENCIUM_CHEST.get(), pos, state);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.emeraldweapons.arcencium_chest");
    }
}
