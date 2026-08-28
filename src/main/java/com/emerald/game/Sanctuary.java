package com.emerald.game;

import com.emerald.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

import javax.annotation.Nullable;

/**
 * Le Sanctuaire d'Ancre : une place forte batie autour de son ancre.
 *
 * Le probleme qu'il resout d'abord est bete mais bloquant : posee sur le
 * terrain nu, une ancre tombait dans un ravin, en pleine mer ou sur un pic, et
 * bloquait toute la partie. Elle est desormais au sommet d'une pyramide dont
 * l'escalier fait l'acces -- on la voit de loin, on sait comment y monter.
 *
 * Le reste suit de cette idee. Une chose qu'on doit defendre merite d'etre
 * defendue par quelqu'un : une muraille, ses tours, une porte a herse, et des
 * gardiens dedans. Prendre l'ancre devient un ASSAUT plutot qu'une visite.
 *
 * Tout est bati en blocs du mode -- gangue, arcencium, verre prismatique -- ce
 * qui donne au sanctuaire l'air d'appartenir a la meme main que l'ancre qu'il
 * protege, et non d'etre un donjon emprunte ailleurs.
 *
 * Les mesures, une fois pour toutes :
 * <pre>
 *   muraille    67 x 67, epaisse de 2, haute de 8, chemin de ronde a 6
 *   tours       7 x 7 aux quatre angles, hautes de 13
 *   porte       au sud, ouverture de 5 sur 5, herse de barreaux
 *   pyramide    base 25 x 25, six gradins de 2, sommet a 13
 *   ancre       au sommet, sur son parvis
 * </pre>
 */
public final class Sanctuary {

    /** Demi-cote de la muraille. L'emprise totale fait donc 67 blocs. */
    private static final int HALF = 33;

    private static final int WALL_HEIGHT = 8;
    private static final int WALK_Y = 6;
    private static final int TOWER_HEIGHT = 13;

    /** Demi-cote de la base de la pyramide. */
    private static final int PYRAMID_HALF = 12;

    /** Nombre de gradins, chacun de deux blocs. */
    private static final int TIERS = 6;

    /** Demi-largeur de l'ouverture de la porte. */
    private static final int GATE_HALF = 2;

    private static final int GATE_HEIGHT = 5;

    private Sanctuary() {
    }

    // ------------------------------------------------------------ materiaux

    private static BlockState body() {
        return ModBlocks.GANGUE_BRICKS.get().defaultBlockState();
    }

    private static BlockState base() {
        return ModBlocks.CORRUPTED_BRICKS.get().defaultBlockState();
    }

    private static BlockState trim() {
        return ModBlocks.POLISHED_GANGUE.get().defaultBlockState();
    }

    private static BlockState walk() {
        return ModBlocks.VEINED_STONE.get().defaultBlockState();
    }

    private static BlockState tower() {
        return ModBlocks.GANGUE_STONE.get().defaultBlockState();
    }

    private static BlockState shrine() {
        return ModBlocks.ARCENCIUM_BRICKS.get().defaultBlockState();
    }

    private static BlockState shrineTrim() {
        return ModBlocks.CHISELED_ARCENCIUM.get().defaultBlockState();
    }

    private static BlockState glow() {
        return ModBlocks.PRISMATIC_GLASS.get().defaultBlockState();
    }

    private static BlockState merlon() {
        return ModBlocks.GANGUE_BRICK_WALL.get().defaultBlockState();
    }

    private static BlockState lantern() {
        return ModBlocks.ARCENCIUM_LANTERN.get().defaultBlockState()
                .setValue(LanternBlock.HANGING, false);
    }

    /**
     * Un bloc d'un autre mod, s'il est la.
     *
     * La poulie, la corde et la manivelle de Supplementaries donnent a la herse
     * son mecanisme visible. Ils sont facultatifs : sans eux la porte fonctionne
     * pareil, elle est seulement moins racontee.
     */
    @Nullable
    private static BlockState optional(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(key);
        return block == Blocks.AIR ? null : block.defaultBlockState();
    }

    // --------------------------------------------------------- construction

    /**
     * Bâtit le sanctuaire, l'ancre a son sommet.
     *
     * @param ground le sol au centre : la pyramide y prend appui
     * @return la position de l'ancre, au sommet
     */
    public static BlockPos build(ServerLevel level, BlockPos ground) {
        int y = ground.getY();
        int cx = ground.getX();
        int cz = ground.getZ();

        clearSite(level, cx, y, cz);
        courtyard(level, cx, y, cz);
        curtainWall(level, cx, y, cz);
        corners(level, cx, y, cz);
        gatehouse(level, cx, y, cz);
        BlockPos anchor = pyramid(level, cx, y, cz);
        SanctuaryGarrison.populate(level, new BlockPos(cx, y, cz), HALF);
        return anchor;
    }

    /**
     * Aplanit l'emprise avant de batir.
     *
     * On ne cherche pas un terrain parfait -- ce serait long et laid -- mais on
     * refuse qu'une colline traverse la muraille : une porte a moitie enterree
     * ne s'ouvre sur rien.
     */
    private static void clearSite(ServerLevel level, int cx, int y, int cz) {
        for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                for (int dy = 1; dy <= TOWER_HEIGHT + 4; dy++) {
                    set(level, cx + dx, y + dy, cz + dz, Blocks.AIR.defaultBlockState());
                }
                // et on comble ce qui manque dessous, pour ne pas batir sur le vide
                for (int dy = 0; dy >= -3; dy--) {
                    BlockPos pos = new BlockPos(cx + dx, y + dy, cz + dz);
                    if (level.getBlockState(pos).isAir()
                            || !level.getFluidState(pos).isEmpty()) {
                        level.setBlock(pos, base(), 2);
                    }
                }
            }
        }
    }

    /** Le sol de la cour : dalles de gangue, avec des allees vers la porte. */
    private static void courtyard(ServerLevel level, int cx, int y, int cz) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean path = Math.abs(dx) <= 2 && dz > 0;
                set(level, cx + dx, y, cz + dz, path ? trim() : walk());
            }
        }
    }

    /**
     * La muraille : deux blocs d'epaisseur, un chemin de ronde, des merlons.
     *
     * Le parapet est cree en laissant UN bloc sur deux : c'est ce qui fait lire
     * une fortification plutot qu'un mur. Les meurtrieres sont percees a hauteur
     * d'homme depuis le chemin de ronde.
     */
    private static void curtainWall(ServerLevel level, int cx, int y, int cz) {
        for (int d = -HALF; d <= HALF; d++) {
            for (int side = 0; side < 4; side++) {
                for (int thick = 0; thick < 2; thick++) {
                    int[] xz = wallPoint(side, d, thick, cx, cz);
                    if (isGateOpening(side, d)) {
                        continue;              // la porte se traite a part
                    }
                    for (int dy = 1; dy <= WALL_HEIGHT; dy++) {
                        BlockState material = dy == 1 ? base()
                                : dy == WALK_Y ? trim() : body();
                        set(level, xz[0], y + dy, xz[1], material);
                    }
                    // le parapet : un merlon sur deux
                    if (thick == 0 && Math.floorMod(d, 2) == 0) {
                        set(level, xz[0], y + WALL_HEIGHT + 1, xz[1], merlon());
                    }
                    // les meurtrieres, une tous les six blocs
                    if (thick == 1 && Math.floorMod(d, 6) == 0 && Math.abs(d) < HALF - 3) {
                        set(level, xz[0], y + WALK_Y + 2, xz[1], Blocks.AIR.defaultBlockState());
                        set(level, xz[0], y + WALK_Y + 3, xz[1], glow());
                    }
                }
            }
        }
        // les escaliers d'acces au chemin de ronde, dans deux angles opposes
        rampToWalk(level, cx - HALF + 3, y, cz - HALF + 3, 1);
        rampToWalk(level, cx + HALF - 3, y, cz + HALF - 3, -1);
    }

    /** Les coordonnees d'un point de muraille, par cote et par distance. */
    private static int[] wallPoint(int side, int d, int thick, int cx, int cz) {
        return switch (side) {
            case 0 -> new int[]{cx + d, cz - HALF + thick};        // nord
            case 1 -> new int[]{cx + d, cz + HALF - thick};        // sud
            case 2 -> new int[]{cx - HALF + thick, cz + d};        // ouest
            default -> new int[]{cx + HALF - thick, cz + d};       // est
        };
    }

    /** L'ouverture de la porte, au milieu du cote sud. */
    private static boolean isGateOpening(int side, int d) {
        return side == 1 && Math.abs(d) <= GATE_HALF;
    }

    /** Un escalier droit qui monte de la cour au chemin de ronde. */
    private static void rampToWalk(ServerLevel level, int x, int y, int z, int dir) {
        for (int step = 0; step < WALK_Y; step++) {
            int sz = z + step * dir;
            set(level, x, y + step, sz, trim());
            set(level, x, y + step + 1, sz,
                    stair(ModBlocks.POLISHED_GANGUE_STAIRS.get(),
                            dir > 0 ? Direction.SOUTH : Direction.NORTH));
        }
    }

    /**
     * Les quatre tours d'angle.
     *
     * Elles sont octogonales plutot que carrees : une tour carree se confond
     * avec le coin de la muraille, une tour ronde signale de loin qu'il y a
     * quelqu'un dedans.
     */
    private static void corners(ServerLevel level, int cx, int y, int cz) {
        int[][] spots = {
                {cx - HALF, cz - HALF}, {cx + HALF, cz - HALF},
                {cx - HALF, cz + HALF}, {cx + HALF, cz + HALF},
        };
        for (int[] spot : spots) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    // l'octogone : on rabote les angles du carre
                    if (Math.abs(dx) + Math.abs(dz) > 4) {
                        continue;
                    }
                    boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3
                            || Math.abs(dx) + Math.abs(dz) == 4;
                    for (int dy = 1; dy <= TOWER_HEIGHT; dy++) {
                        if (!edge && dy > 1 && dy < TOWER_HEIGHT) {
                            set(level, spot[0] + dx, y + dy, spot[1] + dz,
                                    Blocks.AIR.defaultBlockState());
                            continue;          // creuse : on peut y monter
                        }
                        BlockState material = dy == 1 ? base()
                                : dy % 6 == 0 ? shrine() : tower();
                        set(level, spot[0] + dx, y + dy, spot[1] + dz, material);
                    }
                    if (edge && Math.floorMod(dx + dz, 2) == 0) {
                        set(level, spot[0] + dx, y + TOWER_HEIGHT + 1, spot[1] + dz, merlon());
                    }
                }
            }
            set(level, spot[0], y + TOWER_HEIGHT + 1, spot[1], lantern());
        }
    }

    /**
     * Le corps de garde et sa herse.
     *
     * La herse est une grille de barreaux qui coulisse dans la voute. Elle est
     * BAISSEE a la construction : le sanctuaire est ferme, et l'ouvrir est le
     * premier geste de l'assaut. La poulie, la corde et la manivelle au-dessus
     * en montrent le mecanisme -- c'est par elles qu'on l'actionne.
     */
    private static void gatehouse(ServerLevel level, int cx, int y, int cz) {
        int gz = cz + HALF;

        // les deux tours qui encadrent le passage
        for (int side = -1; side <= 1; side += 2) {
            int bx = cx + side * (GATE_HALF + 3);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 1; dy <= TOWER_HEIGHT - 1; dy++) {
                        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        if (!edge && dy > 1 && dy < TOWER_HEIGHT - 1) {
                            continue;
                        }
                        set(level, bx + dx, y + dy, gz + dz,
                                dy == 1 ? base() : dy % 5 == 0 ? shrine() : tower());
                    }
                    if (Math.floorMod(dx + dz, 2) == 0) {
                        set(level, bx + dx, y + TOWER_HEIGHT, gz + dz, merlon());
                    }
                }
            }
            set(level, bx, y + TOWER_HEIGHT, gz, lantern());
        }

        // la voute au-dessus du passage
        for (int dx = -GATE_HALF - 1; dx <= GATE_HALF + 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = GATE_HEIGHT + 1; dy <= GATE_HEIGHT + 3; dy++) {
                    set(level, cx + dx, y + dy, gz + dz,
                            dy == GATE_HEIGHT + 1 ? shrineTrim() : body());
                }
            }
        }
        // les jambages, pour que le passage ne soit pas un simple trou
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = 1; dy <= GATE_HEIGHT; dy++) {
                set(level, cx - GATE_HALF - 1, y + dy, gz + dz, body());
                set(level, cx + GATE_HALF + 1, y + dy, gz + dz, body());
            }
        }
        for (int dx = -GATE_HALF; dx <= GATE_HALF; dx++) {
            set(level, cx + dx, y, gz, trim());
            set(level, cx + dx, y, gz + 1, trim());
            set(level, cx + dx, y, gz - 1, trim());
        }

        // le mecanisme, au-dessus de la voute : poulie, corde, manivelle
        BlockState pulley = optional("supplementaries:pulley_block");
        BlockState rope = optional("supplementaries:rope");
        BlockState crank = optional("supplementaries:crank");
        if (pulley != null) {
            set(level, cx, y + GATE_HEIGHT + 4, gz, pulley);
        }
        if (rope != null) {
            for (int dx = -GATE_HALF; dx <= GATE_HALF; dx += GATE_HALF) {
                set(level, cx + dx, y + GATE_HEIGHT + 3, gz, rope);
            }
        }
        if (crank != null) {
            set(level, cx + GATE_HALF + 2, y + WALK_Y + 1, gz, crank);
        }

        SanctuaryGate.register(level, new BlockPos(cx, y, gz), GATE_HALF, GATE_HEIGHT);
        SanctuaryGate.close(level, new BlockPos(cx, y, gz));
    }

    /**
     * La pyramide et son ancre.
     *
     * Six gradins de deux blocs, un escalier plein sud qui monte du seuil au
     * sommet -- c'est lui qui repond au probleme d'origine, l'ancre qu'on ne
     * pouvait pas atteindre. Chaque gradin porte une bande d'arcencium cisele
     * et deux lanternes, si bien que la montee se lit meme de nuit.
     *
     * @return la position de l'ancre
     */
    private static BlockPos pyramid(ServerLevel level, int cx, int y, int cz) {
        for (int tier = 0; tier < TIERS; tier++) {
            int half = PYRAMID_HALF - tier * 2;
            int top = y + 1 + tier * 2;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean rim = Math.abs(dx) == half || Math.abs(dz) == half;
                    for (int dy = 0; dy < 2; dy++) {
                        BlockState material = rim && dy == 1 ? shrineTrim() : shrine();
                        set(level, cx + dx, top + dy, cz + dz, material);
                    }
                }
            }
            // les insets lumineux, aux quatre faces de chaque gradin
            for (int side = -1; side <= 1; side += 2) {
                set(level, cx + side * half, top + 1, cz, glow());
                set(level, cx, top + 1, cz + side * half, glow());
            }
            // deux lanternes par gradin, de part et d'autre de l'escalier
            set(level, cx - 3, top + 2, cz + half - 1, lantern());
            set(level, cx + 3, top + 2, cz + half - 1, lantern());
        }

        int summit = y + 1 + TIERS * 2;

        // l'escalier sud, du seuil au sommet
        for (int tier = 0; tier < TIERS; tier++) {
            int half = PYRAMID_HALF - tier * 2;
            int stepY = y + 1 + tier * 2;
            for (int dx = -1; dx <= 1; dx++) {
                set(level, cx + dx, stepY, cz + half, trim());
                set(level, cx + dx, stepY + 1, cz + half,
                        stair(ModBlocks.ARCENCIUM_BRICK_STAIRS.get(), Direction.SOUTH));
                set(level, cx + dx, stepY + 2, cz + half, Blocks.AIR.defaultBlockState());
                set(level, cx + dx, stepY + 3, cz + half, Blocks.AIR.defaultBlockState());
            }
        }

        // le parvis du sommet
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(level, cx + dx, summit, cz + dz, shrineTrim());
                set(level, cx + dx, summit + 1, cz + dz, Blocks.AIR.defaultBlockState());
            }
        }
        for (int side = -2; side <= 2; side += 4) {
            for (int other = -2; other <= 2; other += 4) {
                set(level, cx + side, summit + 1, cz + other, lantern());
            }
        }

        BlockPos anchor = new BlockPos(cx, summit + 1, cz);
        level.setBlockAndUpdate(anchor, ModBlocks.PRISMATIC_ANCHOR.get().defaultBlockState());
        return anchor;
    }

    // ----------------------------------------------------------- outillage

    private static BlockState stair(Block block, Direction facing) {
        return block.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        // le drapeau 2 : on previent le client, mais on n'enclenche aucune mise
        // a jour de voisinage -- sur cent mille blocs, les cascades de mises a
        // jour couteraient bien plus cher que la pose elle-meme
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }
}
