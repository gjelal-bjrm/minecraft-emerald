package com.emerald.world;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Cles des configured features, dans une classe SANS dependance au datagen :
 * ModBlocks les reference au runtime (TreeGrower), et charger
 * ModWorldGenProvider (une classe de datagen) en jeu serait fragile.
 */
public final class ModConfiguredFeatures {
    private ModConfiguredFeatures() {}

    public static final ResourceKey<ConfiguredFeature<?, ?>> PRISM_TREE = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "prism_tree"));
}
