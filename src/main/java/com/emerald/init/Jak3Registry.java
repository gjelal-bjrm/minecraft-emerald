package com.emerald.init;

import com.emerald.entity.KGDeathbotEntity;
import com.emerald.entity.PrismaticBoltEntity;
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

    /** Trait du Sceptre d'Arcencium. Aucun modele : il se voit a ses particules. */
    public static final DeferredHolder<EntityType<?>, EntityType<PrismaticBoltEntity>> PRISMATIC_BOLT =
            ENTITIES.register("prismatic_bolt", () ->
                    EntityType.Builder.<PrismaticBoltEntity>of(PrismaticBoltEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("prismatic_bolt"));

    /** Eclair d'Arcencium. Purement visuel : jamais sauvegarde, vie 10 ticks. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.emerald.weather.ArcenciumBoltEntity>> ARCENCIUM_BOLT =
            ENTITIES.register("arcencium_bolt", () ->
                    EntityType.Builder.<com.emerald.weather.ArcenciumBoltEntity>of(
                                    com.emerald.weather.ArcenciumBoltEntity::new, MobCategory.MISC)
                            .noSave()
                            .sized(0.0f, 0.0f)
                            .clientTrackingRange(16)
                            .updateInterval(Integer.MAX_VALUE)
                            .build("arcencium_bolt"));

    /**
     * Voiture civile de Haven City (cara, carb, carc), pilotable. La taille
     * ci-dessous n'est que celle du type : l'entite prend celle de son modele
     * (JakVehicleEntity.getDimensions) et trois parties de collision couvrent
     * sa longueur (VehiclePart). Suivie a 10 troncons, comme le bateau.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.emerald.jak.vehicle.JakVehicleEntity>> JAK_VEHICLE =
            ENTITIES.register("jak_vehicle", () ->
                    EntityType.Builder.<com.emerald.jak.vehicle.JakVehicleEntity>of(
                                    com.emerald.jak.vehicle.JakVehicleEntity::new, MobCategory.MISC)
                            .sized(3.0f, 2.0f)
                            .fireImmune()
                            .clientTrackingRange(10)
                            .build("jak_vehicle"));

    // --- le Morph Gun (Haven) : tirs, foudre et munitions d'eco, jamais sauvegardes

    /**
     * Tir du Blaster : 10 blocs par tique, sans modele (sa trainee le dessine). Le
     * client calcule sa trajectoire depuis le lancer synchronise a l'apparition :
     * aucun paquet de position ni de vitesse ensuite (la vitesse envoyee serait
     * plafonnee a 3,9 blocs par tique), seulement le retrait.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.emerald.jak.gun.GunBlasterShotEntity>> GUN_BLASTER_SHOT =
            ENTITIES.register("gun_blaster_shot", () ->
                    EntityType.Builder.<com.emerald.jak.gun.GunBlasterShotEntity>of(
                                    com.emerald.jak.gun.GunBlasterShotEntity::new, MobCategory.MISC)
                            .noSave()
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(8)
                            .updateInterval(Integer.MAX_VALUE)
                            .build("gun_blaster_shot"));

    /** Boule du Peace Maker : charge au canon, puis vol chercheur ; suivie a chaque tique. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.emerald.jak.gun.GunPeaceBallEntity>> GUN_PEACE_BALL =
            ENTITIES.register("gun_peace_ball", () ->
                    EntityType.Builder.<com.emerald.jak.gun.GunPeaceBallEntity>of(
                                    com.emerald.jak.gun.GunPeaceBallEntity::new, MobCategory.MISC)
                            .noSave()
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("gun_peace_ball"));

    /** Segment de foudre du Peace Maker, purement visuel, six tiques. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.emerald.jak.gun.GunArcEntity>> GUN_ARC =
            ENTITIES.register("gun_arc", () ->
                    EntityType.Builder.<com.emerald.jak.gun.GunArcEntity>of(
                                    com.emerald.jak.gun.GunArcEntity::new, MobCategory.MISC)
                            .noSave()
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(8)
                            .updateInterval(Integer.MAX_VALUE)
                            .build("gun_arc"));

    /** Munition d'eco a ramasser (points de la carte, lachers des monstres). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.emerald.jak.gun.GunEcoEntity>> GUN_ECO =
            ENTITIES.register("gun_eco", () ->
                    EntityType.Builder.<com.emerald.jak.gun.GunEcoEntity>of(
                                    com.emerald.jak.gun.GunEcoEntity::new, MobCategory.MISC)
                            .noSave()
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(8)
                            .updateInterval(5)
                            .build("gun_eco"));

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
