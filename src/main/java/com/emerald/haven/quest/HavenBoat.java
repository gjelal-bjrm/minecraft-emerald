package com.emerald.haven.quest;

import com.emerald.haven.HavenState;
import com.emerald.haven.fauna.HavenFaunaData;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * LE BATEAU DU PECHEUR (lot 3, cahier §86) : « un PNJ sur un bateau, dans l'eau de la ville,
 * pour donner une raison d'aller dans l'eau » (le joueur, §79.7).
 *
 * Un petit bateau de peche de cinq blocs sur neuf, amarre au pied de l'escalier qui descend
 * dans le bassin sous l'arc nord (photo de reperage du 22 sept.) : la marche du bas donne sur
 * la poupe, ouverte, et l'on monte a bord a pied. Coque de chene noir a fleur d'eau, pont de
 * chene noir, bastingage de sapin, un mat de sapin et sa lanterne, deux tonneaux. Le Pecheur
 * se tient au milieu du pont, tourne vers l'escalier.
 *
 * Pose avec les heros (HavenNpcs), retire avec eux et a l'arret du serveur : il ne reste
 * jamais dans une sauvegarde. Sa place se recalcule a l'identique depuis la carte de l'eau,
 * et le retrait rend l'eau (sous la surface) et l'air (au-dessus) A SES SEULS BLOCS. Le
 * retrait passe a chaque arret, bateau pose ou non : il rendait l'eau et l'air a toute sa
 * place, et dans l'atelier, ou le bateau n'est jamais pose, ce que le joueur y aurait bati
 * aurait ete efface a la fermeture, puis perdu au releve suivant (25 sept.). Il n'y touche
 * plus a rien.
 */
public final class HavenBoat {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Ou chercher l'escalier (cellules) : sous l'arc nord, face au milieu du port. */
    private static final BlockPos STAIR_HINT = new BlockPos(560, 58, 268);
    private static final int HALF_WIDTH = 2;
    private static final int LENGTH = 9;

    private record Layout(BlockPos stern, Direction forward) {
    }

    @Nullable
    private static Layout layout;
    private static boolean built;

    private HavenBoat() {
    }

    /** Le milieu du pont (pieds du Pecheur), ou null si le bateau n'a pas de place. */
    @Nullable
    public static BlockPos deck(ServerLevel level) {
        Layout l = layout(level);
        return l == null ? null : l.stern().relative(l.forward(), LENGTH / 2);
    }

    /** Le lacet du Pecheur : tourne vers l'escalier. */
    public static float facing() {
        return layout == null ? 0.0F : layout.forward().getOpposite().toYRot();
    }

    @Nullable
    private static Layout layout(ServerLevel level) {
        if (layout != null) {
            return layout;
        }
        HavenFaunaData.Data data = HavenFaunaData.get(level.getServer());
        if (data == null) {
            return null;
        }
        BlockPos origin = HavenState.get(level.getServer()).origin();
        // la marche du bas : le quai bas du bassin le plus proche du point d'escalier
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (HavenFaunaData.Tile tile : data.list()) {
            for (int i = 0; i < tile.quays().length; i++) {
                int cell = tile.quays()[i];
                int y = HavenFaunaData.unpackY(cell);
                if (tile.quaySide()[i] != 1 || y > data.surface() + 2) {
                    continue;
                }
                double dx = HavenFaunaData.unpackX(cell) - STAIR_HINT.getX();
                double dz = HavenFaunaData.unpackZ(cell) - STAIR_HINT.getZ();
                double d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = new BlockPos(HavenFaunaData.unpackX(cell), y, HavenFaunaData.unpackZ(cell));
                }
            }
        }
        if (best == null) {
            LOGGER.warn("quetes de Haven : aucun escalier bas pres de {} pour le bateau du Pecheur", STAIR_HINT.toShortString());
            return null;
        }
        // le cote ou tout le bateau tient dans l'eau libre du bassin
        for (Direction forward : new Direction[]{Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.NORTH}) {
            for (int gap = 1; gap <= 3; gap++) {
                BlockPos sternCell = best.relative(forward, gap);
                if (fits(data, sternCell, forward)) {
                    BlockPos stern = origin.offset(sternCell.getX(), data.surface() + 1, sternCell.getZ());
                    layout = new Layout(stern, forward);
                    LOGGER.info("quetes de Haven : bateau du Pecheur au pied de l'escalier {} (poupe {}, cap {})",
                            best.toShortString(), stern.toShortString(), forward);
                    return layout;
                }
            }
        }
        LOGGER.warn("quetes de Haven : pas d'eau libre pour le bateau pres de {}", best.toShortString());
        return null;
    }

    private static boolean fits(HavenFaunaData.Data data, BlockPos stern, Direction forward) {
        Direction side = forward.getClockWise();
        for (int along = 0; along < LENGTH; along++) {
            for (int across = -HALF_WIDTH - 1; across <= HALF_WIDTH + 1; across++) {
                BlockPos cell = stern.relative(forward, along).relative(side, across);
                if (data.water(cell.getX(), cell.getZ()) != HavenFaunaData.Water.BASIN) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Pose le bateau s'il manque (troncons charges). */
    static void keep(ServerLevel level) {
        if (built) {
            return;
        }
        Layout l = layout(level);
        if (l == null || !level.isLoaded(l.stern()) || !level.isLoaded(l.stern().relative(l.forward(), LENGTH))) {
            return;
        }
        build(level, l, true);
        built = true;
    }

    /** Retire le bateau : l'eau sous la surface, l'air au-dessus, la ou sont ses blocs. */
    static void remove(ServerLevel level) {
        // l'atelier n'a pas de bateau (la ville y est fermee) : rien a rendre, sauf a un bateau pose juste avant
        if (!built && HavenState.get(level.getServer()).atelier()) {
            return;
        }
        Layout l = layout(level);
        if (l != null && level.isLoaded(l.stern())) {
            build(level, l, false);
        }
        built = false;
    }

    static void reset() {
        layout = null;
        built = false;
    }

    /**
     * Pose (ou retire) chaque bloc. La coque a la hauteur de l'eau (surface), le pont au-dessus,
     * le bastingage sur les flancs et la proue, la poupe ouverte vers l'escalier.
     */
    private static void build(ServerLevel level, Layout l, boolean place) {
        Direction forward = l.forward();
        Direction side = forward.getClockWise();
        BlockPos stern = l.stern();                 // hauteur du pont : surface + 1 (pieds sur la coque)
        BlockState hull = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState rail = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        List<BlockPos> rails = new ArrayList<>();
        for (int along = 0; along < LENGTH; along++) {
            // la proue s'affine : les deux dernieres rangees perdent leurs bords
            int half = along >= LENGTH - 1 ? 0 : along >= LENGTH - 2 ? 1 : HALF_WIDTH;
            for (int across = -half; across <= half; across++) {
                BlockPos column = stern.relative(forward, along).relative(side, across);
                BlockPos hullPos = column.below();
                put(level, hullPos, place, hull, water);
                // un rang de coque sous l'eau sur le pourtour : le flanc se voit du quai
                boolean edge = Math.abs(across) == half || along == 0 || along == LENGTH - 1;
                if (edge) {
                    put(level, hullPos.below(), place, hull, water);
                }
                boolean railHere = (Math.abs(across) == half && along > 0) || along == LENGTH - 1;
                if (railHere) {
                    rails.add(column);
                }
            }
        }
        for (BlockPos pos : rails) {
            put(level, pos, place, rail, air);
        }
        // le mat et sa lanterne, aux deux tiers vers la proue ; deux tonneaux contre le flanc
        BlockPos mast = stern.relative(forward, LENGTH - 3);
        for (int h = 0; h < 4; h++) {
            put(level, mast.above(h), place, rail, air);
        }
        put(level, mast.above(4), place, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), air);
        put(level, stern.relative(forward, 2).relative(side, HALF_WIDTH - 1), place, Blocks.BARREL.defaultBlockState(), air);
        put(level, stern.relative(forward, 3).relative(side, HALF_WIDTH - 1), place, Blocks.BARREL.defaultBlockState(), air);
        if (place) {
            // les barrieres se relient entre elles
            for (BlockPos pos : rails) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof FenceBlock) {
                    level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Pose un bloc du bateau ; au retrait, rend l'eau ou l'air -- si c'est bien un bloc du bateau qui est la. */
    private static void put(ServerLevel level, BlockPos pos, boolean place, BlockState boat, BlockState back) {
        BlockState now = level.getBlockState(pos);
        if (place ? now != boat : now.is(boat.getBlock())) {
            level.setBlock(pos, place ? boat : back, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
}
