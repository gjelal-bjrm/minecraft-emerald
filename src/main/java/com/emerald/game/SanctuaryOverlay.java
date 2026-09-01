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
