package com.emerald.block;

import com.emerald.item.ModItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Les blocs du bar du Hip Hog (cahier §103) : le QG en version Minecraft du bar de Jak 3, un bloc par role, a
 * la texture de Jak 3 de ce role. tools/jak_bar.py ecrit leurs textures, modeles, etats et butins ; ils sont
 * aussi dans l'onglet du mod, pour l'atelier.
 */
public final class HipHogBlocks {

    private static final List<DeferredBlock<? extends Block>> ALL = new ArrayList<>();

    /** La banquette : son escalier (le siege des alcoves) en prend l'etat. */
    public static final DeferredBlock<Block> BOOTH = full("booth", MapColor.COLOR_GRAY, SoundType.WOOL, 0);
    /** Le comptoir : le ratelier et le bouton du QG s'y posent (le banc de la ville le verifie). */
    public static final DeferredBlock<Block> COUNTER = full("counter", MapColor.COLOR_GREEN, SoundType.WOOD, 0);

    static {
        full("roof", MapColor.COLOR_BROWN, SoundType.METAL, 0);
        full("green_metal", MapColor.COLOR_GREEN, SoundType.METAL, 0);
        full("yellow_wall", MapColor.COLOR_YELLOW, SoundType.STONE, 0);
        full("yellow_bricks", MapColor.COLOR_YELLOW, SoundType.STONE, 0);
        full("yellow_metal", MapColor.COLOR_YELLOW, SoundType.METAL, 0);
        full("red_metal", MapColor.COLOR_RED, SoundType.METAL, 0);
        full("red_panel", MapColor.COLOR_RED, SoundType.METAL, 0);
        full("carpet", MapColor.COLOR_RED, SoundType.WOOL, 0);
        full("step", MapColor.COLOR_RED, SoundType.WOOL, 0);
        full("metal_floor", MapColor.METAL, SoundType.METAL, 0);
        full("floor_grate", MapColor.METAL, SoundType.METAL, 0);
        full("pillar", MapColor.COLOR_GREEN, SoundType.METAL, 0);
        full("chrome", MapColor.METAL, SoundType.METAL, 0);
        full("wood", MapColor.WOOD, SoundType.WOOD, 0);
        full("grey_metal", MapColor.COLOR_GRAY, SoundType.METAL, 0);
        full("crate", MapColor.COLOR_GREEN, SoundType.WOOD, 0);
        full("curtain", MapColor.COLOR_YELLOW, SoundType.WOOL, 0);
        full("bottle_shelf", MapColor.WOOD, SoundType.GLASS, 0);
        full("blue_lamp", MapColor.COLOR_LIGHT_BLUE, SoundType.GLASS, 12);
        full("red_lamp", MapColor.COLOR_RED, SoundType.GLASS, 10);
        full("amber_lamp", MapColor.COLOR_ORANGE, SoundType.GLASS, 14);
        register("glass", () -> new TransparentBlock(properties(MapColor.NONE, SoundType.GLASS, 0).noOcclusion()));
        register("carpet_slab", () -> new SlabBlock(properties(MapColor.COLOR_RED, SoundType.WOOL, 0)));
        register("booth_stairs", () -> new StairBlock(BOOTH.get().defaultBlockState(),
                properties(MapColor.COLOR_GRAY, SoundType.WOOL, 0)));
        furniture("stool", HipHogFurnitureBlock.Kind.STOOL, 0);
        furniture("high_stool", HipHogFurnitureBlock.Kind.HIGH_STOOL, 0);
        furniture("high_table", HipHogFurnitureBlock.Kind.HIGH_TABLE, 0);
        furniture("hanging_lamp", HipHogFurnitureBlock.Kind.HANGING_LAMP, 15);
    }

    private HipHogBlocks() {
    }

    /** Charge la classe (et enregistre les blocs) avant les evenements de registre : ModBlocks.register. */
    static void init() {
    }

    /** Tous, dans l'ordre de l'onglet. */
    public static List<DeferredBlock<? extends Block>> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static BlockBehaviour.Properties properties(MapColor color, SoundType sound, int light) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().mapColor(color).sound(sound)
                .strength(1.5F, 6.0F);
        if (light > 0) {
            // une lampe : sa lumiere, et sa texture a pleine clarte meme dans le noir
            properties = properties.lightLevel(state -> light).emissiveRendering((state, level, pos) -> true);
        }
        return properties;
    }

    private static DeferredBlock<Block> full(String role, MapColor color, SoundType sound, int light) {
        return register(role, () -> new Block(properties(color, sound, light)));
    }

    private static void furniture(String role, HipHogFurnitureBlock.Kind kind, int light) {
        register(role, () -> new HipHogFurnitureBlock(kind, properties(MapColor.METAL, SoundType.METAL, light)
                .noOcclusion()));
    }

    private static <T extends Block> DeferredBlock<T> register(String role, Supplier<T> block) {
        String name = "hiphog_" + role;
        DeferredBlock<T> registered = ModBlocks.BLOCKS.register(name, block);
        ModItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        ALL.add(registered);
        return registered;
    }
}
