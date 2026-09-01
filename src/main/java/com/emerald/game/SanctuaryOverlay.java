package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;

/**
 * Le calque : les corrections du joueur, rejouees telles quelles.
 *
 * Ce fichier est ENGENDRE par tools/apply_diff.py a partir du releve de la
 * Sonde. On ne l'ecrit pas a la main, et surtout on ne l'interprete pas.
 *
 * C'est la lecon de dix allers-retours. Le joueur corrigeait le sanctuaire a
 * la main ; je lisais son releve, j'en tirais une REGLE -- « il veut des blocs
 * pleins sur les paliers », « il veut une bordure » -- et je reecrivais le
 * generateur d'apres cette regle. Chaque traduction perdait quelque chose, et
 * il fallait tout recommencer.
 *
 * Le calque supprime la traduction. Chaque ligne du releve devient une pose,
 * a la position exacte et dans l'etat exact, orientation comprise. Il
 * s'applique en dernier, une fois tout le reste bati, et ecrase ce qui le
 * gene -- c'est bien ce qu'on lui demande.
 *
 * Les positions sont RELATIVES au centre du sanctuaire, ce qui les rend
 * valables dans n'importe quel monde : la structure est deterministe, donc ce
 * qui genait a telle case genera toujours a telle case.
 */
public final class SanctuaryOverlay {

    /** {dx, dy, dz, etat} -- engendre, ne pas editer a la main. */
    private static final String[][] CELLS = {
            {"-1", "8", "37", "emeraldweapons:polished_gangue"},
            {"1", "8", "37", "emeraldweapons:polished_gangue"},
            {"0", "9", "37", "emeraldweapons:polished_gangue"},
            {"0", "40", "-6", "emeraldweapons:arcencium_brick_slab[type=bottom,waterlogged=false]"},
            {"-15", "19", "-4", "minecraft:sand"},
            {"15", "19", "-4", "minecraft:sand"},
            {"-3", "40", "-3", "emeraldweapons:arcencium_brick_slab[type=bottom,waterlogged=false]"},
            {"3", "40", "-3", "emeraldweapons:arcencium_brick_slab[type=bottom,waterlogged=false]"},
            {"-1", "38", "0", "emeraldweapons:arcencium_brick_slab[type=bottom,waterlogged=false]"},
            {"0", "38", "0", "emeraldweapons:arcencium_brick_slab[type=bottom,waterlogged=false]"},
            {"1", "38", "0", "emeraldweapons:arcencium_brick_slab[type=bottom,waterlogged=false]"},
            {"-3", "39", "0", "emeraldweapons:arcencium_brick_slab[type=bottom,waterlogged=false]"},
            {"-2", "39", "0", "minecraft:air"},
            {"2", "39", "0", "minecraft:air"},
            {"3", "39", "0", "emeraldweapons:arcencium_brick_slab[type=bottom,waterlogged=false]"},
            {"-3", "1", "10", "minecraft:smooth_sandstone"},
            {"3", "1", "10", "minecraft:smooth_sandstone"},
            {"-2", "2", "10", "minecraft:air"},
            {"2", "2", "10", "minecraft:air"},
            {"-2", "3", "10", "minecraft:air"},
            {"2", "3", "10", "minecraft:air"},
            {"-3", "1", "11", "minecraft:smooth_sandstone"},
            {"3", "1", "11", "minecraft:smooth_sandstone"},
            {"-2", "2", "11", "minecraft:air"},
            {"2", "2", "11", "minecraft:air"},
            {"-2", "3", "11", "minecraft:air"},
            {"2", "3", "11", "minecraft:air"},
            {"-3", "1", "12", "minecraft:smooth_sandstone"},
            {"3", "1", "12", "minecraft:smooth_sandstone"},
            {"-2", "2", "12", "minecraft:air"},
            {"2", "2", "12", "minecraft:air"},
            {"-2", "3", "12", "minecraft:air"},
            {"2", "3", "12", "minecraft:air"},
            {"-3", "1", "13", "minecraft:smooth_sandstone"},
            {"3", "1", "13", "minecraft:smooth_sandstone"},
            {"-2", "2", "13", "minecraft:air"},
            {"2", "2", "13", "minecraft:air"},
            {"-2", "3", "13", "minecraft:air"},
            {"2", "3", "13", "minecraft:air"},
            {"4", "6", "13", "minecraft:sand"},
            {"5", "6", "13", "minecraft:sand"},
            {"-3", "1", "14", "minecraft:smooth_sandstone"},
            {"3", "1", "14", "minecraft:smooth_sandstone"},
            {"-2", "2", "14", "minecraft:air"},
            {"2", "2", "14", "minecraft:air"},
            {"-2", "3", "14", "minecraft:air"},
            {"2", "3", "14", "minecraft:air"},
            {"3", "6", "18", "minecraft:sand"},
            {"-2", "10", "31", "minecraft:air"},
            {"2", "10", "31", "minecraft:air"},
            {"-2", "11", "31", "minecraft:air"},
            {"2", "11", "31", "minecraft:air"},
            {"-2", "12", "31", "minecraft:air"},
            {"2", "12", "31", "minecraft:air"},
            {"-2", "9", "32", "emeraldweapons:corrupted_brick_stairs[facing=east,half=bottom,shape=outer_left,waterlogged=false]"},
            {"2", "9", "32", "emeraldweapons:corrupted_brick_stairs[facing=west,half=bottom,shape=outer_right,waterlogged=false]"},
            {"-2", "10", "32", "minecraft:air"},
            {"2", "10", "32", "minecraft:air"},
            {"-2", "11", "32", "minecraft:air"},
            {"2", "11", "32", "minecraft:air"},
            {"-2", "8", "33", "emeraldweapons:corrupted_bricks"},
            {"2", "8", "33", "emeraldweapons:corrupted_bricks"},
            {"-2", "9", "33", "minecraft:air"},
            {"2", "9", "33", "minecraft:air"},
            {"-2", "10", "33", "minecraft:air"},
            {"2", "10", "33", "minecraft:air"},
            {"-2", "6", "34", "emeraldweapons:corrupted_bricks"},
            {"-2", "7", "34", "emeraldweapons:corrupted_bricks"},
            {"-3", "8", "34", "emeraldweapons:corrupted_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"},
            {"-2", "8", "34", "emeraldweapons:corrupted_bricks"},
            {"2", "8", "34", "emeraldweapons:corrupted_bricks"},
            {"3", "8", "34", "emeraldweapons:corrupted_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"},
            {"-3", "9", "34", "minecraft:air"},
            {"-2", "9", "34", "minecraft:air"},
            {"2", "9", "34", "minecraft:air"},
            {"3", "9", "34", "minecraft:air"},
            {"-2", "1", "35", "emeraldweapons:arcencium_bricks"},
            {"2", "1", "35", "emeraldweapons:arcencium_bricks"},
            {"-2", "2", "35", "emeraldweapons:arcencium_bricks"},
            {"2", "2", "35", "emeraldweapons:arcencium_bricks"},
            {"-2", "3", "35", "emeraldweapons:arcencium_bricks"},
            {"2", "3", "35", "emeraldweapons:arcencium_bricks"},
            {"-3", "8", "35", "emeraldweapons:corrupted_brick_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]"},
            {"-2", "8", "35", "emeraldweapons:corrupted_bricks"},
            {"2", "8", "35", "emeraldweapons:corrupted_bricks"},
            {"3", "8", "35", "emeraldweapons:corrupted_brick_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]"},
            {"0", "11", "35", "minecraft:air"},
            {"-2", "1", "36", "emeraldweapons:arcencium_bricks"},
            {"2", "1", "36", "emeraldweapons:arcencium_bricks"},
            {"-2", "2", "36", "emeraldweapons:arcencium_bricks"},
            {"2", "2", "36", "emeraldweapons:arcencium_bricks"},
            {"-2", "3", "36", "emeraldweapons:arcencium_bricks"},
            {"2", "3", "36", "emeraldweapons:arcencium_bricks"},
            {"-2", "1", "37", "emeraldweapons:arcencium_bricks"},
            {"2", "1", "37", "emeraldweapons:arcencium_bricks"},
            {"-2", "2", "37", "emeraldweapons:arcencium_bricks"},
            {"2", "2", "37", "emeraldweapons:arcencium_bricks"},
            {"-2", "3", "37", "emeraldweapons:arcencium_bricks"},
            {"2", "3", "37", "emeraldweapons:arcencium_bricks"},
            {"-2", "5", "37", "emeraldweapons:polished_gangue"},
            {"2", "5", "37", "emeraldweapons:polished_gangue"},
            {"-2", "6", "37", "emeraldweapons:polished_gangue"},
            {"-1", "6", "37", "minecraft:air"},
            {"1", "6", "37", "minecraft:air"},
            {"2", "6", "37", "emeraldweapons:polished_gangue"},
            {"-2", "7", "37", "emeraldweapons:polished_gangue"},
            {"-1", "7", "37", "minecraft:air"},
            {"0", "7", "37", "minecraft:air"},
            {"1", "7", "37", "minecraft:air"},
            {"2", "7", "37", "emeraldweapons:polished_gangue"},
            {"0", "8", "37", "minecraft:air"},
    };

    private SanctuaryOverlay() {
    }

    public static int apply(ServerLevel level, int cx, int y, int cz) {
        SanctuaryLedger.part("calque");
        int posed = 0;
        for (String[] cell : CELLS) {
            BlockPos at = new BlockPos(cx + Integer.parseInt(cell[0]),
                    y + Integer.parseInt(cell[1]), cz + Integer.parseInt(cell[2]));
            try {
                var parsed = BlockStateParser.parseForBlock(
                        BuiltInRegistries.BLOCK.asLookup(), cell[3], false);
                level.setBlock(at, parsed.blockState(), 2);
                SanctuaryLedger.record(at.getX(), at.getY(), at.getZ(), parsed.blockState());
                posed++;
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException bad) {
                org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                        .warn("Calque : etat illisible « {} »", cell[3]);
            }
        }
        return posed;
    }
}
