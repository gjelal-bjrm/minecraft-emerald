package com.emerald.datagen;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
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
                ), 6))); // veine de 6 blocs
    }

    private static void bootstrapPF(BootstrapContext<PlacedFeature> ctx) {
        HolderGetter<ConfiguredFeature<?, ?>> cfLookup = ctx.lookup(Registries.CONFIGURED_FEATURE);

        ctx.register(ARCENCIUM_ORE_PF, new PlacedFeature(
                cfLookup.getOrThrow(ARCENCIUM_ORE_CF),
                List.of(
                        CountPlacement.of(4),        // 4 veines par chunk
                        InSquarePlacement.spread(),
                        HeightRangePlacement.triangle(
                                VerticalAnchor.absolute(-32),
                                VerticalAnchor.absolute(48)),
                        BiomeFilter.biome()
                )));
    }

    private static void bootstrapBM(BootstrapContext<BiomeModifier> ctx) {
        HolderGetter<PlacedFeature> pfLookup  = ctx.lookup(Registries.PLACED_FEATURE);
        HolderGetter<Biome>         biomeLookup = ctx.lookup(Registries.BIOME);

        ctx.register(ADD_ARCENCIUM_ORE, new BiomeModifiers.AddFeaturesBiomeModifier(
                biomeLookup.getOrThrow(BiomeTags.IS_OVERWORLD),
                HolderSet.direct(pfLookup.getOrThrow(ARCENCIUM_ORE_PF)),
                GenerationStep.Decoration.UNDERGROUND_ORES));
    }
}
