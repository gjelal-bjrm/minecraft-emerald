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

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String path) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, path));
    }

    /** Le jeune arbre : tronc courbe, feuillage en nuage. C'est le plus commun. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> PRISM_TREE = key("prism_tree");

    /** L'arbre adulte : un tronc qui se divise et un houppier rond. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> PRISM_TREE_TALL = key("prism_tree_tall");

    /**
     * LE GRAND ARBRE : la silhouette du cerisier, en prisme.
     *
     * Le joueur voulait « un bel arbre, de differentes tailles, avec des formes
     * majestueuses ». Le cerisier est la plus belle charpente que le jeu sache
     * poser : un tronc court, quatre branches qui s'ecartent a l'horizontale, et
     * un houppier large d'ou pendent des rideaux de feuilles. En prisme, c'est
     * exactement l'arbre qu'on veut voir de loin.
     */
    public static final ResourceKey<ConfiguredFeature<?, ?>> PRISM_TREE_GRAND = key("prism_tree_grand");

    /** Le tirage : petit le plus souvent, grand de temps en temps. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> PRISM_GROVE = key("prism_grove");
}
