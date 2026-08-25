package com.emerald.datagen;

import com.emerald.block.ModBlocks;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SingleItemRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.concurrent.CompletableFuture;

/**
 * Recettes des blocs du village. Logique de cout :
 *  - la gangue (masse) est quasi gratuite : cobblestone + arcencium brut
 *  - la brique d'Arcencium (noble) coute un lingot pour 8
 *  - la corruption s'obtient en gatant la brique (oeil d'araignee fermente)
 *  - les laines se teignent par bain : laine blanche + teinture + lingot
 */
public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        // ----------------------------------------------------------- gangue
        ring(out, ModBlocks.GANGUE_STONE.get(), 8, Blocks.COBBLESTONE, ModItems.RAW_ARCENCIUM.get());
        polished(out, RecipeCategory.BUILDING_BLOCKS, ModBlocks.GANGUE_BRICKS.get(), ModBlocks.GANGUE_STONE.get());
        polished(out, RecipeCategory.BUILDING_BLOCKS, ModBlocks.POLISHED_GANGUE.get(), ModBlocks.GANGUE_BRICKS.get());
        ring(out, ModBlocks.VEINED_STONE.get(), 8, ModBlocks.GANGUE_STONE.get(), ModItems.RAW_ARCENCIUM.get());

        family(out, ModBlocks.GANGUE_STONE, ModBlocks.GANGUE_STONE_STAIRS, ModBlocks.GANGUE_STONE_SLAB, ModBlocks.GANGUE_STONE_WALL);
        family(out, ModBlocks.GANGUE_BRICKS, ModBlocks.GANGUE_BRICK_STAIRS, ModBlocks.GANGUE_BRICK_SLAB, ModBlocks.GANGUE_BRICK_WALL);
        family(out, ModBlocks.POLISHED_GANGUE, ModBlocks.POLISHED_GANGUE_STAIRS, ModBlocks.POLISHED_GANGUE_SLAB, null);
        family(out, ModBlocks.VEINED_STONE, ModBlocks.VEINED_STONE_STAIRS, ModBlocks.VEINED_STONE_SLAB, ModBlocks.VEINED_STONE_WALL);

        // tailleur de pierre : tout derive de la gangue brute
        stonecut(out, ModBlocks.GANGUE_BRICKS.get(), ModBlocks.GANGUE_STONE.get(), 1);
        stonecut(out, ModBlocks.POLISHED_GANGUE.get(), ModBlocks.GANGUE_STONE.get(), 1);
        stonecut(out, ModBlocks.GANGUE_BRICK_STAIRS.get(), ModBlocks.GANGUE_STONE.get(), 1);
        stonecut(out, ModBlocks.GANGUE_BRICK_SLAB.get(), ModBlocks.GANGUE_STONE.get(), 2);
        stonecut(out, ModBlocks.GANGUE_BRICK_WALL.get(), ModBlocks.GANGUE_STONE.get(), 1);
        stonecut(out, ModBlocks.POLISHED_GANGUE_STAIRS.get(), ModBlocks.GANGUE_STONE.get(), 1);
        stonecut(out, ModBlocks.POLISHED_GANGUE_SLAB.get(), ModBlocks.GANGUE_STONE.get(), 2);

        // --------------------------------------------------------- arcencium
        ring(out, ModBlocks.ARCENCIUM_BRICKS.get(), 8, ModBlocks.GANGUE_BRICKS.get(), ModItems.ARCENCIUM_INGOT.get());
        ring(out, ModBlocks.CORRUPTED_BRICKS.get(), 8, ModBlocks.ARCENCIUM_BRICKS.get(), Items.FERMENTED_SPIDER_EYE);
        chiseledBuilder(RecipeCategory.BUILDING_BLOCKS, ModBlocks.CHISELED_ARCENCIUM.get(),
                Ingredient.of(ModBlocks.ARCENCIUM_BRICK_SLAB.get()))
                .unlockedBy(getHasName(ModBlocks.ARCENCIUM_BRICKS.get()), has(ModBlocks.ARCENCIUM_BRICKS.get()))
                .save(out);
        family(out, ModBlocks.ARCENCIUM_BRICKS, ModBlocks.ARCENCIUM_BRICK_STAIRS, ModBlocks.ARCENCIUM_BRICK_SLAB, ModBlocks.ARCENCIUM_BRICK_WALL);
        family(out, ModBlocks.CORRUPTED_BRICKS, ModBlocks.CORRUPTED_BRICK_STAIRS, ModBlocks.CORRUPTED_BRICK_SLAB, ModBlocks.CORRUPTED_BRICK_WALL);

        // ----------------------------------------------------- verre, lumiere
        ring(out, ModBlocks.PRISMATIC_GLASS.get(), 8, Blocks.GLASS, ModItems.ARCENCIUM_INGOT.get());
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.PRISMATIC_GLASS_PANE.get(), 16)
                .pattern("GGG").pattern("GGG")
                .define('G', ModBlocks.PRISMATIC_GLASS.get())
                .unlockedBy(getHasName(ModBlocks.PRISMATIC_GLASS.get()), has(ModBlocks.PRISMATIC_GLASS.get()))
                .save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModBlocks.ARCENCIUM_LANTERN.get())
                .pattern("NNN").pattern("NIN").pattern("NNN")
                .define('N', Items.GOLD_NUGGET).define('I', ModItems.ARCENCIUM_INGOT.get())
                .unlockedBy(getHasName(ModItems.ARCENCIUM_INGOT.get()), has(ModItems.ARCENCIUM_INGOT.get()))
                .save(out);

        // ------------------------------------------------------------ vegetal
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.PRISMATIC_GRASS_BLOCK.get())
                .requires(Blocks.GRASS_BLOCK).requires(ModItems.RAW_ARCENCIUM.get())
                .unlockedBy(getHasName(ModItems.RAW_ARCENCIUM.get()), has(ModItems.RAW_ARCENCIUM.get()))
                .save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, ModBlocks.CRYSTAL_PLANKS.get(), 4)
                .requires(ModBlocks.PRISM_LOG.get())
                .unlockedBy(getHasName(ModBlocks.PRISM_LOG.get()), has(ModBlocks.PRISM_LOG.get()))
                .save(out);
        family(out, ModBlocks.CRYSTAL_PLANKS, ModBlocks.CRYSTAL_STAIRS, ModBlocks.CRYSTAL_SLAB, null);
        fenceBuilder(ModBlocks.CRYSTAL_FENCE.get(), Ingredient.of(ModBlocks.CRYSTAL_PLANKS.get()))
                .unlockedBy(getHasName(ModBlocks.CRYSTAL_PLANKS.get()), has(ModBlocks.CRYSTAL_PLANKS.get()))
                .save(out);

        // --------------------------------------------- derives de l'Arbre de Prisme
        // Le manche et la doublure : sans eux, aucune piece d'Arcencium n'est
        // fabricable. C'est ce qui rend le bucheronnage aussi obligatoire que
        // le minage.
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PRISM_BRANCH.get(), 4)
                .pattern("P").pattern("P")
                .define('P', ModBlocks.CRYSTAL_PLANKS.get())
                .unlockedBy(getHasName(ModBlocks.CRYSTAL_PLANKS.get()), has(ModBlocks.CRYSTAL_PLANKS.get()))
                .save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.PRISM_FIBER.get())
                .requires(ModBlocks.PRISM_LEAVES.get(), 3)
                .unlockedBy(getHasName(ModBlocks.PRISM_LEAVES.get()), has(ModBlocks.PRISM_LEAVES.get()))
                .save(out);

        // ------------------------------------------------------ armure d'Arcencium
        // Forme vanilla, avec la Fibre de Prisme en doublure au creux de la piece.
        armor(out, ModItems.ARCENCIUM_HELMET.get(), "AAA", "AFA", null);
        armor(out, ModItems.ARCENCIUM_CHESTPLATE.get(), "A A", "AFA", "AAA");
        armor(out, ModItems.ARCENCIUM_LEGGINGS.get(), "AAA", "AFA", "A A");
        armor(out, ModItems.ARCENCIUM_BOOTS.get(), "A A", "AFA", null);

        // ------------------------------------------------------------ textile
        dye(out, ModBlocks.VERDIGRIS_WOOL.get(), Items.GREEN_DYE);
        dye(out, ModBlocks.OCHRE_WOOL.get(), Items.ORANGE_DYE);
        dye(out, ModBlocks.OLD_ROSE_WOOL.get(), Items.PINK_DYE);
        dye(out, ModBlocks.SLATE_BLUE_WOOL.get(), Items.BLUE_DYE);
        dye(out, ModBlocks.ECRU_WOOL.get(), Items.LIGHT_GRAY_DYE);
        carpet(out, ModBlocks.VERDIGRIS_CARPET.get(), ModBlocks.VERDIGRIS_WOOL.get());
        carpet(out, ModBlocks.OCHRE_CARPET.get(), ModBlocks.OCHRE_WOOL.get());
        carpet(out, ModBlocks.OLD_ROSE_CARPET.get(), ModBlocks.OLD_ROSE_WOOL.get());
        carpet(out, ModBlocks.SLATE_BLUE_CARPET.get(), ModBlocks.SLATE_BLUE_WOOL.get());
        carpet(out, ModBlocks.ECRU_CARPET.get(), ModBlocks.ECRU_WOOL.get());
    }

    // ------------------------------------------------------------ helpers

    /** 8 `around` autour d'1 `center` -> count `result`. */
    private static void ring(RecipeOutput out, ItemLike result, int count, ItemLike around, ItemLike center) {
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, result, count)
                .pattern("AAA").pattern("ACA").pattern("AAA")
                .define('A', around).define('C', center)
                .unlockedBy(getHasName(center), has(center))
                .save(out);
    }

    /** Escaliers / dalle / mur d'une famille, par craft classique. */
    private static void family(RecipeOutput out, DeferredBlock<Block> base, DeferredBlock<? extends Block> stairs,
                               DeferredBlock<? extends Block> slab, DeferredBlock<? extends Block> wall) {
        Ingredient ing = Ingredient.of(base.get());
        String unlock = getHasName(base.get());
        if (stairs != null) {
            stairBuilder(stairs.get(), ing).unlockedBy(unlock, has(base.get())).save(out);
        }
        if (slab != null) {
            slabBuilder(RecipeCategory.BUILDING_BLOCKS, slab.get(), ing).unlockedBy(unlock, has(base.get())).save(out);
        }
        if (wall != null) {
            wallBuilder(RecipeCategory.DECORATIONS, wall.get(), ing).unlockedBy(unlock, has(base.get())).save(out);
        }
    }

    /** Tailleur de pierre, sauvegarde DANS NOTRE namespace (le helper vanilla
     *  stonecutterResultFromBase ecrit ses ids dans "minecraft:"). */
    private static void stonecut(RecipeOutput out, ItemLike result, ItemLike base, int count) {
        String name = getItemName(result) + "_from_" + getItemName(base) + "_stonecutting";
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(base), RecipeCategory.BUILDING_BLOCKS, result, count)
                .unlockedBy(getHasName(base), has(base))
                .save(out, ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, name));
    }

    /** Piece d'armure : 'A' lingot d'Arcencium, 'F' fibre de Prisme. */
    private static void armor(RecipeOutput out, ItemLike result, String r1, String r2, String r3) {
        ShapedRecipeBuilder b = ShapedRecipeBuilder.shaped(RecipeCategory.COMBAT, result)
                .pattern(r1).pattern(r2);
        if (r3 != null) {
            b.pattern(r3);
        }
        b.define('A', ModItems.ARCENCIUM_INGOT.get())
                .define('F', ModItems.PRISM_FIBER.get())
                .unlockedBy(getHasName(ModItems.ARCENCIUM_INGOT.get()), has(ModItems.ARCENCIUM_INGOT.get()))
                .save(out);
    }

    /** Bain de teinture : 4 laines blanches + 1 teinture + 1 lingot -> 4 laines. */
    private static void dye(RecipeOutput out, ItemLike wool, ItemLike dye) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, wool, 4)
                .requires(Items.WHITE_WOOL, 4).requires(dye).requires(ModItems.ARCENCIUM_INGOT.get())
                .unlockedBy(getHasName(ModItems.ARCENCIUM_INGOT.get()), has(ModItems.ARCENCIUM_INGOT.get()))
                .save(out);
    }
}
