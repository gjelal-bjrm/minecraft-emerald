package com.emerald.datagen;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.List;

/**
 * Blockstates + modeles de blocs + modeles d'items des blocs du village.
 * Les deux blocs historiques (arcencium_block / arcencium_ore) gardent
 * leurs JSON ecrits a la main : on ne les regenere pas.
 */
public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, EmeraldWeaponsMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // --- pierre : familles completes
        family(ModBlocks.GANGUE_STONE, ModBlocks.GANGUE_STONE_STAIRS, ModBlocks.GANGUE_STONE_SLAB, ModBlocks.GANGUE_STONE_WALL);
        family(ModBlocks.GANGUE_BRICKS, ModBlocks.GANGUE_BRICK_STAIRS, ModBlocks.GANGUE_BRICK_SLAB, ModBlocks.GANGUE_BRICK_WALL);
        family(ModBlocks.POLISHED_GANGUE, ModBlocks.POLISHED_GANGUE_STAIRS, ModBlocks.POLISHED_GANGUE_SLAB, null);
        family(ModBlocks.VEINED_STONE, ModBlocks.VEINED_STONE_STAIRS, ModBlocks.VEINED_STONE_SLAB, ModBlocks.VEINED_STONE_WALL);
        family(ModBlocks.ARCENCIUM_BRICKS, ModBlocks.ARCENCIUM_BRICK_STAIRS, ModBlocks.ARCENCIUM_BRICK_SLAB, ModBlocks.ARCENCIUM_BRICK_WALL);
        family(ModBlocks.CORRUPTED_BRICKS, ModBlocks.CORRUPTED_BRICK_STAIRS, ModBlocks.CORRUPTED_BRICK_SLAB, ModBlocks.CORRUPTED_BRICK_WALL);
        cube(ModBlocks.CHISELED_ARCENCIUM);

        // --- bois
        family(ModBlocks.CRYSTAL_PLANKS, ModBlocks.CRYSTAL_STAIRS, ModBlocks.CRYSTAL_SLAB, null);
        fence(ModBlocks.CRYSTAL_FENCE, ModBlocks.CRYSTAL_PLANKS);

        // --- verre : translucide
        ResourceLocation glass = blockTexture(ModBlocks.PRISMATIC_GLASS.get());
        simpleBlockWithItem(ModBlocks.PRISMATIC_GLASS.get(),
                models().cubeAll("prismatic_glass", glass).renderType("translucent"));
        paneBlockWithRenderType(ModBlocks.PRISMATIC_GLASS_PANE.get(), glass,
                modLoc("block/prismatic_glass_pane_top"), "translucent");
        itemModels().withExistingParent("prismatic_glass_pane", mcLoc("item/generated"))
                .texture("layer0", glass);

        // --- lanterne : posee / suspendue
        ResourceLocation lanternTex = modLoc("block/arcencium_lantern");
        ModelFile lantern = models().withExistingParent("arcencium_lantern", mcLoc("block/template_lantern"))
                .texture("lantern", lanternTex).renderType("cutout");
        ModelFile hanging = models().withExistingParent("arcencium_lantern_hanging", mcLoc("block/template_hanging_lantern"))
                .texture("lantern", lanternTex).renderType("cutout");
        getVariantBuilder(ModBlocks.ARCENCIUM_LANTERN.get()).forAllStatesExcept(state ->
                        ConfiguredModel.builder()
                                .modelFile(state.getValue(LanternBlock.HANGING) ? hanging : lantern)
                                .build(),
                LanternBlock.WATERLOGGED);
        // L'ICONE a sa propre texture, en seize sur seize.
        //
        // On pointait « layer0 » sur la texture du BLOC, qui fait seize par
        // quarante-huit -- la lanterne y est dessinee en trois bandes que le
        // gabarit recolle en volume. Ecrasee dans une case d'inventaire, elle
        // donnait une barre coloree. Le jeu lui-meme ne s'y prend pas
        // autrement : sa lanterne a une texture d'objet distincte.
        itemModels().withExistingParent("arcencium_lantern", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/arcencium_lantern"));

        // --- vegetal
        simpleBlockWithItem(ModBlocks.PRISMATIC_GRASS_BLOCK.get(),
                models().cubeBottomTop("prismatic_grass_block",
                        modLoc("block/prismatic_grass_block_side"),
                        mcLoc("block/dirt"),
                        modLoc("block/prismatic_grass_block_top")));
        cross(ModBlocks.PRISM_BLOOM);
        cross(ModBlocks.PRISM_TUFT);
        cross(ModBlocks.PRISM_SAPLING);
        logBlock(ModBlocks.PRISM_LOG.get());
        simpleBlockItem(ModBlocks.PRISM_LOG.get(), unchecked("prism_log"));
        simpleBlockWithItem(ModBlocks.PRISM_LEAVES.get(),
                models().cubeAll("prism_leaves", blockTexture(ModBlocks.PRISM_LEAVES.get()))
                        .renderType("cutout_mipped"));

        // --- textile
        for (DeferredBlock<Block> wool : List.of(ModBlocks.VERDIGRIS_WOOL, ModBlocks.OCHRE_WOOL,
                ModBlocks.OLD_ROSE_WOOL, ModBlocks.SLATE_BLUE_WOOL, ModBlocks.ECRU_WOOL)) {
            cube(wool);
        }
        for (DeferredBlock<? extends Block> carpet : List.of(ModBlocks.VERDIGRIS_CARPET, ModBlocks.OCHRE_CARPET,
                ModBlocks.OLD_ROSE_CARPET, ModBlocks.SLATE_BLUE_CARPET, ModBlocks.ECRU_CARPET)) {
            String name = carpet.getId().getPath();
            simpleBlockWithItem(carpet.get(), models().carpet(name, modLoc("block/" + name)));
        }
    }

    // ------------------------------------------------------------ helpers

    private ModelFile unchecked(String blockModelName) {
        return new ModelFile.UncheckedModelFile(modLoc("block/" + blockModelName));
    }

    private void cube(DeferredBlock<? extends Block> block) {
        simpleBlockWithItem(block.get(), cubeAll(block.get()));
    }

    /** Cube + escaliers + dalle + (mur optionnel), tous sur la texture du cube. */
    private void family(DeferredBlock<Block> base, DeferredBlock<StairBlock> stairs,
                        DeferredBlock<SlabBlock> slab, DeferredBlock<WallBlock> wall) {
        cube(base);
        ResourceLocation tex = blockTexture(base.get());
        if (stairs != null) {
            stairsBlock(stairs.get(), tex);
            simpleBlockItem(stairs.get(), unchecked(stairs.getId().getPath()));
        }
        if (slab != null) {
            slabBlock(slab.get(), modLoc("block/" + base.getId().getPath()), tex);
            simpleBlockItem(slab.get(), unchecked(slab.getId().getPath()));
        }
        if (wall != null) {
            wallBlock(wall.get(), tex);
            itemModels().wallInventory(wall.getId().getPath(), tex);
        }
    }

    private void fence(DeferredBlock<FenceBlock> fence, DeferredBlock<Block> base) {
        ResourceLocation tex = blockTexture(base.get());
        fenceBlock(fence.get(), tex);
        itemModels().fenceInventory(fence.getId().getPath(), tex);
    }

    /** Plante en croix (cutout) ; l'item reprend la texture du bloc. */
    private void cross(DeferredBlock<? extends Block> block) {
        String name = block.getId().getPath();
        ResourceLocation tex = blockTexture(block.get());
        simpleBlock(block.get(), models().cross(name, tex).renderType("cutout"));
        itemModels().withExistingParent(name, mcLoc("item/generated")).texture("layer0", tex);
    }
}
