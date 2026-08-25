package com.emerald.block.entity;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EmeraldWeaponsMod.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcenciumChestBlockEntity>>
            ARCENCIUM_CHEST = BLOCK_ENTITIES.register("arcencium_chest",
                    () -> BlockEntityType.Builder.of(ArcenciumChestBlockEntity::new,
                            ModBlocks.ARCENCIUM_CHEST.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
