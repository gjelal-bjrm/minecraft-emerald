package com.emerald.game;

import com.emerald.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'atelier du village : les trois etablis du mode, cote a cote.
 *
 * Le joueur l'a demande pour donner une raison de REVENIR au village : c'est
 * la que le personnage et l'equipement s'ameliorent. Sans lui, les trois
 * stations se fabriquent -- Arcencium, plumes, amethyste -- et personne ne
 * les a avant la deuxieme moitie de partie ; le village n'est alors qu'un
 * point de depart qu'on ne revoit jamais.
 *
 * Une dalle de briques de gangue a douze blocs de la Lame, trois stations en
 * ligne face a elle, deux lanternes. Pose a la mise en place, apres la Lame :
 * elle est donc au meme endroit dans chaque partie, et l'on sait ou aller.
 */
public final class Workshop {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Distance de la Lame, en blocs, plein est. */
    private static final int OFFSET = 12;

    private Workshop() {
    }

    public static void place(ServerLevel level, BlockPos blade) {
        BlockPos centre = floorNear(level, blade);
        int cx = centre.getX();
        int cz = centre.getZ();

        // la dalle : 7 x 5, et de l'air au-dessus pour qu'on y circule
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos floor = centre.offset(dx, -1, dz);
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 2;
                level.setBlock(floor, edge ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 3);
                for (int dy = 0; dy <= 3; dy++) {
                    level.setBlock(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // les trois stations, face a la Lame (a l'ouest), l'etabli au milieu
        station(level, centre.offset(0, 0, -2), ModBlocks.ARCENCIUM_FORGE.get().defaultBlockState());
        station(level, centre.offset(0, 0, 0), ModBlocks.SOCKET_BENCH.get().defaultBlockState());
        station(level, centre.offset(0, 0, 2), ModBlocks.SPECIALIZATION_ALTAR.get().defaultBlockState());
        LOGGER.info("Atelier pose en {} (Lame en {})", centre, blade);
        // deux lanternes sur des piliers, aux coins vers la Lame
        for (int dz : new int[]{-2, 2}) {
            level.setBlock(centre.offset(-3, 0, dz), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 3);
            level.setBlock(centre.offset(-3, 1, dz), Blocks.LANTERN.defaultBlockState(), 3);
        }
    }

    /**
     * UN SOL AU NIVEAU DE LA LAME. Le releve de surface donne le point le plus
     * haut de la colonne : dans un village bati sur plusieurs etages -- la
     * citadelle du monde d'essai -- c'etait un toit, douze blocs au-dessus de la
     * Lame, et l'atelier y etait invisible. On cherche donc, de douze a dix-huit
     * blocs de la Lame et dans toutes les directions, une colonne dont le sol
     * est a trois blocs pres de celui de la Lame, avec trois blocs d'air dessus.
     * A defaut, la surface plein est, comme avant.
     */
    private static BlockPos floorNear(ServerLevel level, BlockPos blade) {
        int by = blade.getY();
        for (int d = OFFSET; d <= OFFSET + 6; d++) {
            for (Direction dir : new Direction[]{Direction.EAST, Direction.SOUTH, Direction.NORTH, Direction.WEST}) {
                int x = blade.getX() + dir.getStepX() * d;
                int z = blade.getZ() + dir.getStepZ() * d;
                for (int y = by + 3; y >= by - 3; y--) {
                    BlockPos feet = new BlockPos(x, y, z);
                    if (level.getBlockState(feet.below()).isSolid()
                            && level.getBlockState(feet).isAir()
                            && level.getBlockState(feet.above()).isAir()
                            && level.getBlockState(feet.above(2)).isAir()) {
                        return feet;
                    }
                }
            }
        }
        int x = blade.getX() + OFFSET;
        return new BlockPos(x, WorldSetup.surfaceY(level, x, blade.getZ()), blade.getZ());
    }

    private static void station(ServerLevel level, BlockPos at, BlockState state) {
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            state = state.setValue(HorizontalDirectionalBlock.FACING, Direction.WEST);
        }
        level.setBlock(at, state, 3);
    }
}
