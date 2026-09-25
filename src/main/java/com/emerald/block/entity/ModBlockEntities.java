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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OathBladeBlockEntity>>
            OATH_BLADE = BLOCK_ENTITIES.register("oath_blade",
                    () -> BlockEntityType.Builder.of(OathBladeBlockEntity::new,
                            ModBlocks.OATH_BLADE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HavenGunRackBlockEntity>>
            HAVEN_GUN_RACK = BLOCK_ENTITIES.register("haven_gun_rack",
                    () -> BlockEntityType.Builder.of(HavenGunRackBlockEntity::new,
                            ModBlocks.HAVEN_GUN_RACK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HavenGateBlockEntity>>
            HAVEN_GATE = BLOCK_ENTITIES.register("haven_gate",
                    () -> BlockEntityType.Builder.of(HavenGateBlockEntity::new,
                            ModBlocks.HAVEN_GATE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcPortalBlockEntity>>
            ARC_PORTAL = BLOCK_ENTITIES.register("arc_portal",
                    () -> BlockEntityType.Builder.of(ArcPortalBlockEntity::new,
                            ModBlocks.ARC_PORTAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EclipsePortalBlockEntity>>
            ECLIPSE_PORTAL = BLOCK_ENTITIES.register("eclipse_portal",
                    () -> BlockEntityType.Builder.of(EclipsePortalBlockEntity::new,
                            ModBlocks.ECLIPSE_PORTAL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HavenDoorBlockEntity>>
            HAVEN_DOOR = BLOCK_ENTITIES.register("haven_door",
                    () -> BlockEntityType.Builder.of(HavenDoorBlockEntity::new,
                            ModBlocks.HAVEN_DOOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HavenWindowBlockEntity>>
            HAVEN_WINDOW = BLOCK_ENTITIES.register("haven_window",
                    () -> BlockEntityType.Builder.of(HavenWindowBlockEntity::new,
                            ModBlocks.HAVEN_WINDOW.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
