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
 * Une dalle de briques de gangue a douze blocs de la Lame, DEUX RANGEES face a
 * face, deux lanternes. Pose a la mise en place, apres la Lame : elle est donc
 * au meme endroit dans chaque partie, et l'on sait ou aller.
 *
 * LA SECONDE RANGEE N'EST PAS DE NOUS, ET C'EST LE BUT.
 *
 * Le mode se joue dans un modpack, et le joueur passait son temps a chercher
 * une enclume. Reparer une epee, retirer un enchantement, tailler une gemme :
 * ce sont les gestes qui ENTOURENT nos trois etablis, on les fait entre deux
 * ameliorations, et les envoyer chercher ailleurs casse la boucle qui ramene
 * au village. On emprunte donc au jeu et a Apotheosis ce qui manque, et on le
 * pose en face.
 *
 * Les blocs d'Apotheosis sont demandes au registre et poses SEULEMENT s'ils
 * existent : le mode doit tourner sans lui.
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

        // la dalle : 10 x 9, et de l'air au-dessus pour qu'on y circule
        for (int dx = -3; dx <= 6; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                BlockPos floor = centre.offset(dx, -1, dz);
                boolean edge = dx == -3 || dx == 6 || Math.abs(dz) == 4;
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
        // EN FACE, CE QU'ON EMPRUNTE. Sept postes sur la rangee d'en face, a
        // quatre blocs : on tient au milieu et l'on atteint les deux rangees.
        //
        // Du jeu : l'enclume repare et combine, la meule retire et repare sans
        // livre, la table de forge monte au netherite, l'etabli sert a tout --
        // et c'est aussi la que se sertit une gemme d'Apotheosis.
        station(level, centre.offset(4, 0, -3), Blocks.ANVIL.defaultBlockState());
        station(level, centre.offset(4, 0, -2), Blocks.GRINDSTONE.defaultBlockState());
        station(level, centre.offset(4, 0, -1), Blocks.SMITHING_TABLE.defaultBlockState());
        station(level, centre.offset(4, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState());
        // D'Apotheosis : tailler les gemmes, demonter une piece pour recuperer
        // ce qu'elle porte, et reforger. Absents, on saute -- sans rien casser.
        borrowed(level, centre.offset(4, 0, 1), "apotheosis:gem_cutting_table");
        borrowed(level, centre.offset(4, 0, 2), "apotheosis:salvaging_table");
        borrowed(level, centre.offset(4, 0, 3), "apotheosis:simple_reforging_table");
        // ON RETIENT L'ENDROIT. Sans cela, il faut chercher les trois blocs
        // autour de la Lame -- et dans un monde d'essai remis en place plusieurs
        // fois, la recherche tombait sur les etablis d'un atelier precedent.
        GameState.get(level).setWorkshop(centre);
        LOGGER.info("Atelier pose en {} (Lame en {})", centre, blade);
        // quatre lanternes sur des piliers, aux quatre coins de la dalle
        for (int dx : new int[]{-3, 6}) {
            for (int dz : new int[]{-3, 3}) {
                level.setBlock(centre.offset(dx, 0, dz), Blocks.DEEPSLATE_BRICK_WALL.defaultBlockState(), 3);
                level.setBlock(centre.offset(dx, 1, dz), Blocks.LANTERN.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Un poste emprunte a un autre mod, pose s'il est la.
     *
     * On ne code jamais en dur une dependance qu'on n'a pas declaree : si
     * Apotheosis n'est pas installe, la dalle a simplement trois places vides,
     * et le mode tourne. Le journal le dit une fois, pour qu'on sache pourquoi.
     */
    private static void borrowed(ServerLevel level, BlockPos at, String id) {
        net.minecraft.resources.ResourceLocation key =
                net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null) {
            return;
        }
        var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(key);
        if (block.isEmpty()) {
            LOGGER.info("Atelier : {} absent du jeu, la place reste vide", id);
            return;
        }
        station(level, at, block.get().defaultBlockState());
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
