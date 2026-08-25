package com.emerald.datagen;

import com.emerald.block.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.Set;

/**
 * Loot tables des blocs du village. Les deux blocs historiques gardent
 * leurs tables ecrites a la main (src/main/resources), on ne les touche pas.
 */
public class ModBlockLootTableProvider extends BlockLootSubProvider {

    public ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    /** Blocs hors palette du village, a traiter a part. */
    private static final java.util.List<DeferredBlock<? extends Block>> EXTRA =
            java.util.List.of(ModBlocks.ARCENCIUM_CHEST, ModBlocks.SOCKET_BENCH);

    @Override
    protected void generate() {
        // le coffre se ramasse tel quel ; ChestBlock deverse son contenu tout seul
        for (DeferredBlock<? extends Block> holder : EXTRA) {
            dropSelf(holder.get());
        }
        for (DeferredBlock<? extends Block> holder : ModBlocks.VILLAGE_BLOCKS) {
            Block block = holder.get();
            if (block instanceof SlabBlock) {
                add(block, this::createSlabItemTable);
            } else if (block == ModBlocks.PRISMATIC_GLASS.get() || block == ModBlocks.PRISMATIC_GLASS_PANE.get()) {
                dropWhenSilkTouch(block);
            } else if (block == ModBlocks.PRISMATIC_GRASS_BLOCK.get()) {
                add(block, b -> createSingleItemTableWithSilkTouch(b, Blocks.DIRT));
            } else if (block == ModBlocks.PRISM_LEAVES.get()) {
                add(block, b -> createLeavesDrops(b, ModBlocks.PRISM_SAPLING.get(), NORMAL_LEAVES_SAPLING_CHANCES));
            } else if (block == ModBlocks.PRISM_TUFT.get()) {
                add(block, b -> createShearsOnlyDrop(b));
            } else {
                dropSelf(block);
            }
        }
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return java.util.stream.Stream.concat(
                ModBlocks.VILLAGE_BLOCKS.stream(), EXTRA.stream())
                .map(h -> (Block) h.get())::iterator;
    }
}
