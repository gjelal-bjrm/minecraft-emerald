package com.emerald.mine;

import com.emerald.game.GameState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * CE QUE LE SOUS-SOL A LE DROIT DE FAIRE, ET OU.
 *
 * Les grottes prennent vie -- percees, resonance, poches, echos -- mais
 * jamais n'importe ou ni sur n'importe quoi. Trois regles, tenues ici pour
 * que chaque systeme les lise au meme endroit :
 *
 *  - SOUS y = 48 seulement. La surface appartient au joueur et a ses
 *    batisses ; le sous-sol commence la ou la lumiere du jour s'arrete ;
 *  - JAMAIS pres du village (48 blocs, la zone de paix) ni sous un sanctuaire
 *    (110 blocs autour de chaque ancre, l'emprise et sa marge) : ils ont deja
 *    leur vie, et l'on ne perce pas un tombeau par accident ;
 *  - ON NE TOUCHE QU'A LA ROCHE NATURELLE. Un bloc pose par le joueur, un
 *    coffre, un minerai : le sous-sol s'arrete net devant. Le minerai n'est
 *    jamais detruit, il est DECOUVERT.
 */
public final class Underground {

    /** La surface s'arrete la ; en dessous, le sous-sol vit. */
    public static final int CEILING = 48;
    private static final int VILLAGE_PEACE = 48;
    private static final int SANCTUARY_KEEP = 110;

    private Underground() {
    }

    /** La roche qu'on a le droit de percer : la pierre du monde, rien d'autre. */
    public static boolean natural(BlockState state) {
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF)
                || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE) || state.is(Blocks.ANDESITE)
                || state.is(Blocks.CALCITE) || state.is(Blocks.DRIPSTONE_BLOCK)
                || state.is(Tags.Blocks.STONES) || state.is(Tags.Blocks.COBBLESTONES_DEEPSLATE);
    }

    public static boolean ore(BlockState state) {
        return state.is(Tags.Blocks.ORES);
    }

    /** Vrai la ou le sous-sol a le droit de vivre. */
    public static boolean allowed(ServerLevel level, BlockPos pos) {
        if (pos.getY() >= CEILING) {
            return false;
        }
        GameState state = GameState.get(level);
        BlockPos village = state.village();
        if (!village.equals(BlockPos.ZERO) && flat(village, pos) < VILLAGE_PEACE) {
            return false;
        }
        for (BlockPos anchor : state.anchors()) {
            if (flat(anchor, pos) < SANCTUARY_KEEP) {
                return false;
            }
        }
        return true;
    }

    /** Distance horizontale : la hauteur ne compte pas pour une emprise. */
    public static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
