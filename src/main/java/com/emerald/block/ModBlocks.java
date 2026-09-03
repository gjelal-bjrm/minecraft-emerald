package com.emerald.block;

import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.world.ModConfiguredFeatures;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Blocs du mod. La palette du village d'Arcencium suit la regle 85/15 :
 * la masse est sourde (gangue, bois), la couleur est rare (briques
 * d'Arcencium aux veines vibrantes, verre prismatique, lanternes, plantes).
 */
public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(EmeraldWeaponsMod.MODID);

    /** Ordre d'affichage dans l'onglet creatif du village. */
    public static final List<DeferredBlock<? extends Block>> VILLAGE_BLOCKS = new ArrayList<>();

    // ------------------------------------------------------------ existants

    public static final DeferredBlock<Block> ARCENCIUM_BLOCK = registerBlock("arcencium_block",
            () -> new Block(BlockBehaviour.Properties.of().strength(4f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST)));

    public static final DeferredBlock<Block> ARCENCIUM_ORE = registerBlock("arcencium_ore",
            () -> new DropExperienceBlock(UniformInt.of(2, 4), BlockBehaviour.Properties.of().strength(3F).requiresCorrectToolForDrops().sound(SoundType.STONE)));

    /**
     * Coffre d'Arcencium. Rendu par ArcenciumChestRenderer, qui lui donne sa
     * texture propre en simple comme en double.
     */
    public static final DeferredBlock<ArcenciumChestBlock> ARCENCIUM_CHEST = registerBlock("arcencium_chest",
            () -> new ArcenciumChestBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.AMETHYST)
                    .noOcclusion()));

    /**
     * Lame du Serment : le declencheur de partie, plantee au centre du village.
     *
     * Enregistree SANS objet de bloc : elle n'est jamais posee a la main, et
     * l'objet du meme nom existe deja (ModItems.OATH_BLADE), qui est la lame
     * qu'on emporte apres l'avoir retiree.
     */
    public static final DeferredBlock<OathBladeBlock> OATH_BLADE = registerBlockOnly("oath_blade",
            () -> new OathBladeBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F).noOcclusion().lightLevel(s -> 10)
                    .sound(SoundType.AMETHYST)));

    /** Ancre Prismatique : trois par partie, a alimenter en Arcencium. */
    public static final DeferredBlock<PrismaticAnchorBlock> PRISMATIC_ANCHOR =
            registerBlock("prismatic_anchor", () -> new PrismaticAnchorBlock(
                    BlockBehaviour.Properties.of().strength(-1.0F, 3600000.0F)
                            .lightLevel(s -> s.getValue(PrismaticAnchorBlock.ACTIVE) ? 15 : 7)
                            .sound(SoundType.AMETHYST)));

    /** Etabli de Sertissage : voir SocketBenchBlock. */
    public static final DeferredBlock<SocketBenchBlock> SOCKET_BENCH = registerBlock("socket_bench",
            () -> new SocketBenchBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F).sound(SoundType.WOOD)));

    /** Autel de Specialisation : les vingt paliers du personnage, montres en entier. */
    public static final DeferredBlock<SpecializationAltarBlock> SPECIALIZATION_ALTAR =
            registerBlock("specialization_altar", () -> new SpecializationAltarBlock(
                    BlockBehaviour.Properties.of().strength(4.0F, 6.0F).requiresCorrectToolForDrops()
                            .sound(SoundType.AMETHYST).lightLevel(s -> 9)));

    /** Forge d'Arcencium : l'amelioration +1 a +10, montree en entier. Voir ArcenciumForgeBlock. */
    public static final DeferredBlock<ArcenciumForgeBlock> ARCENCIUM_FORGE = registerBlock("arcencium_forge",
            () -> new ArcenciumForgeBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.ANVIL).lightLevel(s -> 6)));

    /**
     * Le Sceau du Tombeau.
     *
     * Tres dur et resistant aux explosions : c'est une SERRURE. Si on pouvait
     * la casser a la pioche, elle ne serait plus une condition mais un
     * obstacle -- et l'interieur de la pyramide redeviendrait facultatif.
     */
    public static final DeferredBlock<TombSealBlock> TOMB_SEAL = registerBlock("tomb_seal",
            () -> new TombSealBlock(BlockBehaviour.Properties.of()
                    .strength(50.0F, 1200.0F)
                    // Endormi, il luit tout de meme : a trois, il se noyait dans une salle
                    // sombre et l'on passait devant sans le voir. A sept, il se
                    // signale d'un couloir sans pour autant paraitre deja eveille.
                    .lightLevel(state -> state.getValue(TombSealBlock.LIT) ? 12 : 7)
                    .sound(SoundType.AMETHYST)));

    // --------------------------------------------- Gangue : la masse du village

    public static final DeferredBlock<Block> GANGUE_STONE = village("gangue_stone",
            () -> new Block(stone()));
    public static final DeferredBlock<StairBlock> GANGUE_STONE_STAIRS = village("gangue_stone_stairs",
            () -> new StairBlock(GANGUE_STONE.get().defaultBlockState(), stone()));
    public static final DeferredBlock<SlabBlock> GANGUE_STONE_SLAB = village("gangue_stone_slab",
            () -> new SlabBlock(stone()));
    public static final DeferredBlock<WallBlock> GANGUE_STONE_WALL = village("gangue_stone_wall",
            () -> new WallBlock(stone()));

    public static final DeferredBlock<Block> GANGUE_BRICKS = village("gangue_bricks",
            () -> new Block(stone()));
    public static final DeferredBlock<StairBlock> GANGUE_BRICK_STAIRS = village("gangue_brick_stairs",
            () -> new StairBlock(GANGUE_BRICKS.get().defaultBlockState(), stone()));
    public static final DeferredBlock<SlabBlock> GANGUE_BRICK_SLAB = village("gangue_brick_slab",
            () -> new SlabBlock(stone()));
    public static final DeferredBlock<WallBlock> GANGUE_BRICK_WALL = village("gangue_brick_wall",
            () -> new WallBlock(stone()));

    public static final DeferredBlock<Block> POLISHED_GANGUE = village("polished_gangue",
            () -> new Block(stone()));
    public static final DeferredBlock<StairBlock> POLISHED_GANGUE_STAIRS = village("polished_gangue_stairs",
            () -> new StairBlock(POLISHED_GANGUE.get().defaultBlockState(), stone()));
    public static final DeferredBlock<SlabBlock> POLISHED_GANGUE_SLAB = village("polished_gangue_slab",
            () -> new SlabBlock(stone()));

    public static final DeferredBlock<Block> VEINED_STONE = village("veined_stone",
            () -> new Block(stone()));
    public static final DeferredBlock<StairBlock> VEINED_STONE_STAIRS = village("veined_stone_stairs",
            () -> new StairBlock(VEINED_STONE.get().defaultBlockState(), stone()));
    public static final DeferredBlock<SlabBlock> VEINED_STONE_SLAB = village("veined_stone_slab",
            () -> new SlabBlock(stone()));
    public static final DeferredBlock<WallBlock> VEINED_STONE_WALL = village("veined_stone_wall",
            () -> new WallBlock(stone()));

    // --------------------------------- Arcencium : le noble, edifices importants

    public static final DeferredBlock<Block> ARCENCIUM_BRICKS = village("arcencium_bricks",
            () -> new Block(noble()));
    public static final DeferredBlock<StairBlock> ARCENCIUM_BRICK_STAIRS = village("arcencium_brick_stairs",
            () -> new StairBlock(ARCENCIUM_BRICKS.get().defaultBlockState(), noble()));
    public static final DeferredBlock<SlabBlock> ARCENCIUM_BRICK_SLAB = village("arcencium_brick_slab",
            () -> new SlabBlock(noble()));
    public static final DeferredBlock<WallBlock> ARCENCIUM_BRICK_WALL = village("arcencium_brick_wall",
            () -> new WallBlock(noble()));
    public static final DeferredBlock<Block> CHISELED_ARCENCIUM = village("chiseled_arcencium",
            () -> new Block(noble().lightLevel(s -> 6)));

    public static final DeferredBlock<Block> CORRUPTED_BRICKS = village("corrupted_bricks",
            () -> new Block(noble()));
    public static final DeferredBlock<StairBlock> CORRUPTED_BRICK_STAIRS = village("corrupted_brick_stairs",
            () -> new StairBlock(CORRUPTED_BRICKS.get().defaultBlockState(), noble()));
    public static final DeferredBlock<SlabBlock> CORRUPTED_BRICK_SLAB = village("corrupted_brick_slab",
            () -> new SlabBlock(noble()));
    public static final DeferredBlock<WallBlock> CORRUPTED_BRICK_WALL = village("corrupted_brick_wall",
            () -> new WallBlock(noble()));

    // ------------------------------------------------------ verre et lumiere

    public static final DeferredBlock<TransparentBlock> PRISMATIC_GLASS = village("prismatic_glass",
            () -> new TransparentBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)));
    public static final DeferredBlock<IronBarsBlock> PRISMATIC_GLASS_PANE = village("prismatic_glass_pane",
            () -> new IronBarsBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS_PANE)));
    public static final DeferredBlock<LanternBlock> ARCENCIUM_LANTERN = village("arcencium_lantern",
            () -> new LanternBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LANTERN).lightLevel(s -> 14)));

    // ------------------------------------------------------------ vegetal

    public static final DeferredBlock<Block> PRISMATIC_GRASS_BLOCK = village("prismatic_grass_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DIRT).sound(SoundType.GRASS)));
    public static final DeferredBlock<GlowingPlantBlock> PRISM_BLOOM = village("prism_bloom",
            () -> new GlowingPlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.POPPY).lightLevel(s -> 10)));
    public static final DeferredBlock<GlowingPlantBlock> PRISM_TUFT = village("prism_tuft",
            () -> new GlowingPlantBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS).lightLevel(s -> 6)));

    public static final DeferredBlock<PrismLogBlock> PRISM_LOG = village("prism_log",
            () -> new PrismLogBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LOG).lightLevel(s -> 5)));
    public static final DeferredBlock<PrismLeavesBlock> PRISM_LEAVES = village("prism_leaves",
            () -> new PrismLeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES).lightLevel(s -> 3)));
    public static final DeferredBlock<SaplingBlock> PRISM_SAPLING = village("prism_sapling",
            () -> new SaplingBlock(
                    new TreeGrower("prism", Optional.empty(), Optional.of(ModConfiguredFeatures.PRISM_TREE), Optional.empty()),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_SAPLING).lightLevel(s -> 3)));

    public static final DeferredBlock<Block> CRYSTAL_PLANKS = village("crystal_planks",
            () -> new Block(wood()));
    public static final DeferredBlock<StairBlock> CRYSTAL_STAIRS = village("crystal_stairs",
            () -> new StairBlock(CRYSTAL_PLANKS.get().defaultBlockState(), wood()));
    public static final DeferredBlock<SlabBlock> CRYSTAL_SLAB = village("crystal_slab",
            () -> new SlabBlock(wood()));
    public static final DeferredBlock<FenceBlock> CRYSTAL_FENCE = village("crystal_fence",
            () -> new FenceBlock(wood()));

    // ------------------------------------------------------------- textile
    // Teintures a l'arcencium : tons naturels et poussiereux.

    public static final DeferredBlock<Block> VERDIGRIS_WOOL = wool("verdigris_wool", MapColor.COLOR_CYAN);
    public static final DeferredBlock<Block> OCHRE_WOOL = wool("ochre_wool", MapColor.COLOR_YELLOW);
    public static final DeferredBlock<Block> OLD_ROSE_WOOL = wool("old_rose_wool", MapColor.COLOR_PINK);
    public static final DeferredBlock<Block> SLATE_BLUE_WOOL = wool("slate_blue_wool", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredBlock<Block> ECRU_WOOL = wool("ecru_wool", MapColor.TERRACOTTA_WHITE);

    public static final DeferredBlock<CarpetBlock> VERDIGRIS_CARPET = carpet("verdigris_carpet", MapColor.COLOR_CYAN);
    public static final DeferredBlock<CarpetBlock> OCHRE_CARPET = carpet("ochre_carpet", MapColor.COLOR_YELLOW);
    public static final DeferredBlock<CarpetBlock> OLD_ROSE_CARPET = carpet("old_rose_carpet", MapColor.COLOR_PINK);
    public static final DeferredBlock<CarpetBlock> SLATE_BLUE_CARPET = carpet("slate_blue_carpet", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredBlock<CarpetBlock> ECRU_CARPET = carpet("ecru_carpet", MapColor.TERRACOTTA_WHITE);

    // ------------------------------------------------------------- helpers

    /** Enregistre un bloc sans objet associe : pour ceux que seul le jeu pose. */
    private static <T extends Block> DeferredBlock<T> registerBlockOnly(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE_BRICKS).mapColor(MapColor.TERRACOTTA_LIGHT_GRAY);
    }

    private static BlockBehaviour.Properties noble() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_BRICKS)
                .mapColor(MapColor.COLOR_BLACK).sound(SoundType.AMETHYST);
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).mapColor(MapColor.COLOR_BROWN);
    }

    private static DeferredBlock<Block> wool(String name, MapColor color) {
        return village(name, () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_WOOL).mapColor(color)));
    }

    private static DeferredBlock<CarpetBlock> carpet(String name, MapColor color) {
        return village(name, () -> new CarpetBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.WHITE_CARPET).mapColor(color)));
    }

    /** Enregistre un bloc de la palette du village (+ item, + onglet creatif). */
    private static <T extends Block> DeferredBlock<T> village(String name, Supplier<T> block) {
        DeferredBlock<T> b = registerBlock(name, block);
        VILLAGE_BLOCKS.add(b);
        return b;
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventbus) {
        BLOCKS.register(eventbus);
    }
}
