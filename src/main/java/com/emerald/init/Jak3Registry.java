package com.emerald.init;

import com.emerald.entity.KGDeathbotEntity;
import com.emerald.entity.WastelanderEntity;
import com.emerald.world.structure.HavenCityStructure;
import com.emerald.world.structure.SpargusCityStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class Jak3Registry {

    public static final String MODID = "emeraldweapons";
    //@EventBusSubscriber(modid = "emeraldweapons")

    // -------------------------------------------------------------------------
    // Entités
    // -------------------------------------------------------------------------
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WastelanderEntity>> WASTELANDER =
            ENTITIES.register("wastelander", () ->
                    EntityType.Builder.<WastelanderEntity>of(WastelanderEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(8)
                            .build("wastelander"));

    public static final DeferredHolder<EntityType<?>, EntityType<KGDeathbotEntity>> KG_DEATHBOT =
            ENTITIES.register("kg_deathbot", () ->
                    EntityType.Builder.<KGDeathbotEntity>of(KGDeathbotEntity::new, MobCategory.MONSTER)
                            .sized(0.8f, 2.2f)  // plus grand qu'un joueur
                            .clientTrackingRange(10)
                            .build("kg_deathbot"));

    // -------------------------------------------------------------------------
    // Structure types
    // -------------------------------------------------------------------------
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<SpargusCityStructure>> SPARGUS_CITY =
            STRUCTURE_TYPES.register("spargus_city",
                    () -> () -> SpargusCityStructure.CODEC);

    public static final DeferredHolder<StructureType<?>, StructureType<HavenCityStructure>> HAVEN_CITY =
            STRUCTURE_TYPES.register("haven_city",
                    () -> () -> HavenCityStructure.CODEC);

    // -------------------------------------------------------------------------
    // Structure piece types
    // -------------------------------------------------------------------------
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MODID);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> SPARGUS_CITY_PIECE =
            STRUCTURE_PIECE_TYPES.register("spargus_city_piece",
                    () -> SpargusCityStructure.SpargusCityPiece::new);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> HAVEN_CITY_PIECE =
            STRUCTURE_PIECE_TYPES.register("haven_city_piece",
                    () -> HavenCityStructure.HavenCityPiece::new);

    // -------------------------------------------------------------------------
    // Enregistrement sur le bus d'événements mod
    // -------------------------------------------------------------------------
    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        STRUCTURE_PIECE_TYPES.register(modBus);
    }
}
