package com.emerald.datagen;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, EmeraldWeaponsMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.NEEDS_DIAMOND_TOOL).add(ModBlocks.ARCENCIUM_ORE.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.ARCENCIUM_ORE.get(),
                ModBlocks.ARCENCIUM_BLOCK.get(), ModBlocks.ARCENCIUM_CHEST.get());

        // Outils : bois a la hache, terre a la pelle, feuilles a la houe, le reste a la pioche
        Set<Block> axe = Set.of(ModBlocks.CRYSTAL_PLANKS.get(), ModBlocks.CRYSTAL_STAIRS.get(),
                ModBlocks.CRYSTAL_SLAB.get(), ModBlocks.CRYSTAL_FENCE.get(), ModBlocks.PRISM_LOG.get());
        Set<Block> none = Set.of(ModBlocks.PRISM_BLOOM.get(), ModBlocks.PRISM_TUFT.get(), ModBlocks.PRISM_SAPLING.get(),
                ModBlocks.VERDIGRIS_WOOL.get(), ModBlocks.OCHRE_WOOL.get(), ModBlocks.OLD_ROSE_WOOL.get(),
                ModBlocks.SLATE_BLUE_WOOL.get(), ModBlocks.ECRU_WOOL.get(),
                ModBlocks.VERDIGRIS_CARPET.get(), ModBlocks.OCHRE_CARPET.get(), ModBlocks.OLD_ROSE_CARPET.get(),
                ModBlocks.SLATE_BLUE_CARPET.get(), ModBlocks.ECRU_CARPET.get());
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.SOCKET_BENCH.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.ARCENCIUM_FORGE.get());
        tag(BlockTags.NEEDS_IRON_TOOL).add(ModBlocks.ARCENCIUM_FORGE.get());
        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(ModBlocks.PRISMATIC_GRASS_BLOCK.get());
        tag(BlockTags.MINEABLE_WITH_HOE).add(ModBlocks.PRISM_LEAVES.get());
        for (DeferredBlock<? extends Block> holder : ModBlocks.VILLAGE_BLOCKS) {
            Block b = holder.get();
            if (axe.contains(b)) {
                tag(BlockTags.MINEABLE_WITH_AXE).add(b);
            } else if (!none.contains(b) && b != ModBlocks.PRISMATIC_GRASS_BLOCK.get()
                    && b != ModBlocks.PRISM_LEAVES.get()) {
                tag(BlockTags.MINEABLE_WITH_PICKAXE).add(b);
            }
            // Familles vanilla : comportement (escaliers, dalles, murs qui se connectent...)
            if (b instanceof StairBlock) tag(BlockTags.STAIRS).add(b);
            if (b instanceof SlabBlock) tag(BlockTags.SLABS).add(b);
            if (b instanceof WallBlock) tag(BlockTags.WALLS).add(b);
            if (b instanceof FenceBlock) {
                tag(BlockTags.FENCES).add(b);
                tag(BlockTags.WOODEN_FENCES).add(b);
            }
            if (b instanceof CarpetBlock) tag(BlockTags.WOOL_CARPETS).add(b);
        }
        tag(BlockTags.WOODEN_STAIRS).add(ModBlocks.CRYSTAL_STAIRS.get());
        tag(BlockTags.WOODEN_SLABS).add(ModBlocks.CRYSTAL_SLAB.get());
        tag(BlockTags.PLANKS).add(ModBlocks.CRYSTAL_PLANKS.get());
        tag(BlockTags.LOGS_THAT_BURN).add(ModBlocks.PRISM_LOG.get());
        tag(BlockTags.LEAVES).add(ModBlocks.PRISM_LEAVES.get());
        tag(BlockTags.SAPLINGS).add(ModBlocks.PRISM_SAPLING.get());
        tag(BlockTags.WOOL).add(ModBlocks.VERDIGRIS_WOOL.get(), ModBlocks.OCHRE_WOOL.get(),
                ModBlocks.OLD_ROSE_WOOL.get(), ModBlocks.SLATE_BLUE_WOOL.get(), ModBlocks.ECRU_WOOL.get());
        // Les plantes poussent dessus, l'arbre s'y enracine
        tag(BlockTags.DIRT).add(ModBlocks.PRISMATIC_GRASS_BLOCK.get());
        tag(BlockTags.SMALL_FLOWERS).add(ModBlocks.PRISM_BLOOM.get());
        tag(BlockTags.FLOWERS).add(ModBlocks.PRISM_BLOOM.get());
        tag(BlockTags.REPLACEABLE_BY_TREES).add(ModBlocks.PRISM_TUFT.get(), ModBlocks.PRISM_BLOOM.get());
        tag(BlockTags.SWORD_EFFICIENT).add(ModBlocks.PRISM_TUFT.get(), ModBlocks.PRISM_BLOOM.get(),
                ModBlocks.PRISM_LEAVES.get(), ModBlocks.PRISM_SAPLING.get());
        tag(BlockTags.IMPERMEABLE).add(ModBlocks.PRISMATIC_GLASS.get());
    }
}
