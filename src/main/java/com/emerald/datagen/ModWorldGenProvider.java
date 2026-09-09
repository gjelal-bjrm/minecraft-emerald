package com.emerald.datagen;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.world.ModConfiguredFeatures;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.WeightedListInt;
import net.minecraft.world.level.levelgen.feature.WeightedPlacedFeature;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.foliageplacers.RandomSpreadFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.BendingTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.CherryTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.FancyTrunkPlacer;

import java.util.OptionalInt;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModWorldGenProvider extends DatapackBuiltinEntriesProvider {

    public static final ResourceKey<ConfiguredFeature<?, ?>> ARCENCIUM_ORE_CF = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "arcencium_ore"));

    public static final ResourceKey<PlacedFeature> ARCENCIUM_ORE_PF = ResourceKey.create(
            Registries.PLACED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "arcencium_ore"));

    /** Les trois tailles, posees une par une : le tirage a besoin de features PLACEES. */
    public static final ResourceKey<PlacedFeature> PRISM_TREE_PF = pf("prism_tree");
    public static final ResourceKey<PlacedFeature> PRISM_TREE_TALL_PF = pf("prism_tree_tall");
    public static final ResourceKey<PlacedFeature> PRISM_TREE_GRAND_PF = pf("prism_tree_grand");
    /** Le bosquet : le tirage, seul a etre pose dans le monde. */
    public static final ResourceKey<PlacedFeature> PRISM_GROVE_PF = pf("prism_grove");

    /** Les biomes ou pousse le prisme : ceux qui portent deja le village. */
    public static final net.minecraft.tags.TagKey<Biome> GROWS_PRISM =
            net.minecraft.tags.TagKey.create(Registries.BIOME,
                    ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "grows_prism_tree"));

    public static final ResourceKey<BiomeModifier> ADD_PRISM_TREE = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "add_prism_tree"));

    private static ResourceKey<PlacedFeature> pf(String path) {
        return ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, path));
    }

    public static final ResourceKey<BiomeModifier> ADD_ARCENCIUM_ORE = ResourceKey.create(
            NeoForgeRegistries.Keys.BIOME_MODIFIERS,
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "add_arcencium_ore"));

    public ModWorldGenProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries,
                new RegistrySetBuilder()
                        .add(Registries.CONFIGURED_FEATURE, ModWorldGenProvider::bootstrapCF)
                        .add(Registries.PLACED_FEATURE,     ModWorldGenProvider::bootstrapPF)
                        .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ModWorldGenProvider::bootstrapBM),
                Set.of(EmeraldWeaponsMod.MODID));
    }

    private static void bootstrapCF(BootstrapContext<ConfiguredFeature<?, ?>> ctx) {
        ctx.register(ARCENCIUM_ORE_CF, new ConfiguredFeature<>(Feature.ORE,
                new OreConfiguration(List.of(
                        OreConfiguration.target(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES),
                                ModBlocks.ARCENCIUM_ORE.get().defaultBlockState()),
                        OreConfiguration.target(new TagMatchTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES),
                                ModBlocks.ARCENCIUM_ORE.get().defaultBlockState())
                ), 5, 0.5F))); // veine de 5 blocs, et la moitie de celles a l'air libre est jetee, comme le diamant

        // ------------------------------------------------ l'Arbre de Prisme
        //
        // TROIS TAILLES, TROIS CHARPENTES. Le joueur voulait « un bel arbre, de
        // differentes tailles, avec des formes majestueuses », et une seule
        // silhouette repetee ne fait pas un bosquet, elle fait un decor.
        //
        // Le PETIT garde ce qu'on avait : tronc courbe, feuillage en nuage
        // irregulier, la silhouette de l'azalee. C'est le buisson qui remplit
        // les bords.
        //
        // Le MOYEN prend la charpente du grand chene : un tronc qui se divise
        // en branches et un houppier rond pose dessus, six a treize blocs.
        //
        // Le GRAND prend celle du cerisier, la plus belle que le jeu sache
        // poser : un tronc court, des branches qui s'ecartent a l'horizontale,
        // un houppier large et des rideaux de feuilles qui pendent en dessous.
        // C'est celui qu'on voit de loin, et c'est pour lui qu'on traverse la
        // plaine.
        ctx.register(ModConfiguredFeatures.PRISM_TREE, new ConfiguredFeature<>(Feature.TREE,
                new TreeConfiguration.TreeConfigurationBuilder(
                        BlockStateProvider.simple(ModBlocks.PRISM_LOG.get()),
                        new BendingTrunkPlacer(5, 2, 0, 3, UniformInt.of(1, 2)),
                        BlockStateProvider.simple(ModBlocks.PRISM_LEAVES.get()),
                        new RandomSpreadFoliagePlacer(ConstantInt.of(3), ConstantInt.of(0), ConstantInt.of(2), 50),
                        new TwoLayersFeatureSize(1, 0, 1))
                        .ignoreVines()
                        .build()));

        ctx.register(ModConfiguredFeatures.PRISM_TREE_TALL, new ConfiguredFeature<>(Feature.TREE,
                new TreeConfiguration.TreeConfigurationBuilder(
                        BlockStateProvider.simple(ModBlocks.PRISM_LOG.get()),
                        new FancyTrunkPlacer(6, 5, 2),
                        BlockStateProvider.simple(ModBlocks.PRISM_LEAVES.get()),
                        new FancyFoliagePlacer(ConstantInt.of(2), ConstantInt.of(4), 4),
                        new TwoLayersFeatureSize(0, 0, 0, OptionalInt.of(4)))
                        .ignoreVines()
                        .build()));

        ctx.register(ModConfiguredFeatures.PRISM_TREE_GRAND, new ConfiguredFeature<>(Feature.TREE,
                new TreeConfiguration.TreeConfigurationBuilder(
                        BlockStateProvider.simple(ModBlocks.PRISM_LOG.get()),
                        new CherryTrunkPlacer(7, 1, 0,
                                new WeightedListInt(SimpleWeightedRandomList.<IntProvider>builder()
                                        .add(ConstantInt.of(1), 1)
                                        .add(ConstantInt.of(2), 1)
                                        .add(ConstantInt.of(3), 1)
                                        .build()),
                                UniformInt.of(2, 4), UniformInt.of(-4, -3), UniformInt.of(-1, 0)),
                        BlockStateProvider.simple(ModBlocks.PRISM_LEAVES.get()),
                        new CherryFoliagePlacer(ConstantInt.of(4), ConstantInt.of(0), ConstantInt.of(5),
                                0.25F, 0.5F, 0.16666667F, 0.33333334F),
                        new TwoLayersFeatureSize(1, 0, 2))
                        .ignoreVines()
                        .build()));

        // LE TIRAGE. Un sur dix est grand, trois sur dix sont moyens, le reste
        // est petit : assez de grands pour qu'on en croise, assez de petits
        // pour que le bosquet ait un sous-bois.
        ctx.register(ModConfiguredFeatures.PRISM_GROVE, new ConfiguredFeature<>(Feature.RANDOM_SELECTOR,
                new RandomFeatureConfiguration(
                        List.of(new WeightedPlacedFeature(
                                        pfLookupOf(ctx, PRISM_TREE_GRAND_PF), 0.10F),
                                new WeightedPlacedFeature(
                                        pfLookupOf(ctx, PRISM_TREE_TALL_PF), 0.33F)),
                        pfLookupOf(ctx, PRISM_TREE_PF))));
    }

    /** Le renvoi vers une feature placee, depuis le bootstrap des configured. */
    private static net.minecraft.core.Holder<PlacedFeature> pfLookupOf(
            BootstrapContext<ConfiguredFeature<?, ?>> ctx, ResourceKey<PlacedFeature> key) {
        return ctx.lookup(Registries.PLACED_FEATURE).getOrThrow(key);
    }

    private static void bootstrapPF(BootstrapContext<PlacedFeature> ctx) {
        HolderGetter<ConfiguredFeature<?, ?>> cfLookup = ctx.lookup(Registries.CONFIGURED_FEATURE);

        ctx.register(ARCENCIUM_ORE_PF, new PlacedFeature(
                cfLookup.getOrThrow(ARCENCIUM_ORE_CF),
                List.of(
                        // PLUS RARE ET PLUS PROFOND QUE LE DIAMANT.
                        //
                        // A neuf veines de huit entre -48 et 64, l'Arcencium
                        // etait douze fois plus abondant que le diamant, et il
                        // culminait a y = 8 la ou le diamant n'est presque pas :
                        // on tombait sur l'Arcencium AVANT le diamant qui sert a
                        // le miner. Deux veines de cinq entre -64 et 0 (pic a
                        // -32) : on rencontre le diamant d'abord, l'Arcencium
                        // ensuite, et l'Aurore le double. Ce qui manque au
                        // compte vient des Poches, des Percees, des Echos et des
                        // coffres des sanctuaires -- le sous-sol vit, desormais.
                        CountPlacement.of(2),
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(-64),
                                VerticalAnchor.absolute(0)),
                        BiomeFilter.biome()
                )));

        // LES TROIS ARBRES, POSES SANS FILTRE : ils ne servent qu'au tirage, et
        // c'est le bosquet qui porte les conditions.
        HolderGetter<ConfiguredFeature<?, ?>> cf = cfLookup;
        ctx.register(PRISM_TREE_PF, new PlacedFeature(
                cf.getOrThrow(ModConfiguredFeatures.PRISM_TREE), List.of()));
        ctx.register(PRISM_TREE_TALL_PF, new PlacedFeature(
                cf.getOrThrow(ModConfiguredFeatures.PRISM_TREE_TALL), List.of()));
        ctx.register(PRISM_TREE_GRAND_PF, new PlacedFeature(
                cf.getOrThrow(ModConfiguredFeatures.PRISM_TREE_GRAND), List.of()));

        // LE BOSQUET DANS LE MONDE.
        //
        // Un chunk sur six, un arbre par chunk retenu, sur le sol et seulement
        // ou un plant de prisme survivrait. Cela donne un arbre tous les
        // quarante blocs environ dans les biomes ouverts : on en croise en
        // marchant, sans que la plaine devienne une foret.
        ctx.register(PRISM_GROVE_PF, new PlacedFeature(
                cf.getOrThrow(ModConfiguredFeatures.PRISM_GROVE),
                List.of(
                        RarityFilter.onAverageOnceEvery(6),
                        InSquarePlacement.spread(),
                        SurfaceWaterDepthFilter.forMaxDepth(0),
                        HeightmapPlacement.onHeightmap(
                                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR),
                        BiomeFilter.biome(),
                        BlockPredicateFilter.forPredicate(
                                net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate
                                        .wouldSurvive(ModBlocks.PRISM_SAPLING.get().defaultBlockState(),
                                                net.minecraft.core.BlockPos.ZERO))
                )));
    }

    private static void bootstrapBM(BootstrapContext<BiomeModifier> ctx) {
        HolderGetter<PlacedFeature> pfLookup  = ctx.lookup(Registries.PLACED_FEATURE);
        HolderGetter<Biome>         biomeLookup = ctx.lookup(Registries.BIOME);

        ctx.register(ADD_ARCENCIUM_ORE, new BiomeModifiers.AddFeaturesBiomeModifier(
                biomeLookup.getOrThrow(BiomeTags.IS_OVERWORLD),
                HolderSet.direct(pfLookup.getOrThrow(ARCENCIUM_ORE_PF)),
                GenerationStep.Decoration.UNDERGROUND_ORES));

        ctx.register(ADD_PRISM_TREE, new BiomeModifiers.AddFeaturesBiomeModifier(
                biomeLookup.getOrThrow(GROWS_PRISM),
                HolderSet.direct(pfLookup.getOrThrow(PRISM_GROVE_PF)),
                GenerationStep.Decoration.VEGETAL_DECORATION));
    }
}
