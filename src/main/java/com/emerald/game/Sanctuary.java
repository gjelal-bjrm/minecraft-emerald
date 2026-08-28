package com.emerald.game;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.commands.CommandSourceStack;
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
 * Trois lecons d'un premier essai rate, ecrites ici pour ne pas les reperdre.
 *
 * 1. NE PAS REBATIR CE QUI EXISTE. La premiere version empilait six gradins de
 *    briques et appelait cela une pyramide ; a cote, la Pyramide Maudite de
 *    Cataclysm fait quatre-vingt-huit blocs de haut, avec ses salles, ses
 *    pieges et ses obelisques. On pose donc la VRAIE, par la commande de
 *    placement du jeu qui sait assembler ses onze morceaux, et on batit autour.
 *
 * 2. UNE TOUR SE VISITE. Les tours pleines de la premiere version n'etaient que
 *    des colonnes decoratives. Celles-ci sont creuses, avec un escalier interne
 *    et une porte qui donne sur le chemin de ronde.
 *
 * 3. UN ESCALIER MENE QUELQUE PART. La rampe montait a la hauteur six, dans la
 *    masse pleine du mur -- on grimpait dans le vide. Le chemin de ronde est
 *    desormais une vraie surface, a une hauteur unique, et tout ce qui monte y
 *    aboutit.
 */
public final class Sanctuary {

    /** Demi-cote de la muraille. Large : elle doit contenir la pyramide. */
    private static final int HALF = 62;

    /** Epaisseur de la courtine. */
    private static final int THICK = 3;

    /** Hauteur de la masse pleine ; on marche sur le bloc du dessus. */
    private static final int WALL_TOP = 8;

    /** Le chemin de ronde, seule hauteur ou l'on marche sur le mur. */
    private static final int WALK = WALL_TOP + 1;

    private static final int TOWER_RADIUS = 5;
    private static final int TOWER_TOP = 18;

    private static final int GATE_HALF = 3;
    private static final int GATE_HEIGHT = 7;

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

    private static BlockState floor() {
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

    @Nullable
    private static BlockState optional(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.BLOCK.containsKey(key)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(key);
        return block == Blocks.AIR ? null : block.defaultBlockState();
    }

    /** Le premier de ces blocs qui existe : on s'enrichit du modpack sans en dependre. */
    private static BlockState first(BlockState fallback, String... ids) {
        for (String id : ids) {
            BlockState found = optional(id);
            if (found != null) {
                return found;
            }
        }
        return fallback;
    }

    // --------------------------------------------------------- construction

    /**
     * Bâtit le sanctuaire. La pyramide vient de Cataclysm, le reste est a nous.
     *
     * @return la position de l'ancre
     */
    public static BlockPos build(ServerLevel level, CommandSourceStack source, BlockPos ground) {
        int y = ground.getY();
        int cx = ground.getX();
        int cz = ground.getZ();

        clearSite(level, cx, y, cz);
        courtyard(level, cx, y, cz);
        curtainWall(level, cx, y, cz);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                cornerTower(level, cx + sx * HALF, y, cz + sz * HALF);
            }
        }
        gatehouse(level, cx, y, cz);

        greatPyramid(level, source, cx, y, cz);
        // On MESURE le sommet au lieu de le supposer : la pyramide de Cataclysm
        // est posee par la commande du jeu, qui assemble ses onze morceaux comme
        // elle l'entend. Se fier a une hauteur ecrite en dur, c'etait risquer un
        // parvis flottant en l'air ou noye dans la maconnerie.
        BlockPos anchor = crown(level, cx, summitOf(level, cx, y, cz), cz);
        SanctuaryGarrison.populate(level, new BlockPos(cx, y, cz), HALF);
        SanctuaryMist.register(new BlockPos(cx, y, cz), HALF);
        return anchor;
    }

    /**
     * La hauteur du point le plus haut au centre, la ou l'ancre doit se poser.
     *
     * On sonde une petite zone plutot que la seule colonne centrale : un sommet
     * de pyramide porte souvent une pointe ou un creux, et n'en prendre qu'un
     * bloc donnerait un parvis de travers.
     */
    private static int summitOf(ServerLevel level, int cx, int y, int cz) {
        int best = y;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int top = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        cx + dx, cz + dz);
                best = Math.max(best, top);
            }
        }
        return best;
    }

    /**
     * La Pyramide Maudite, posee par la commande de placement du jeu.
     *
     * C'est elle qui sait assembler les onze morceaux du modele -- quatre
     * inferieurs, quatre superieurs, deux obelisques et le sommet -- et les
     * poser dans le bon ordre. Le reconstituer a la main, c'etait s'exposer a
     * en deviner le decoupage de travers.
     *
     */
    private static void greatPyramid(ServerLevel level, CommandSourceStack source,
                                    int cx, int y, int cz) {
        if (BuiltInRegistries.BLOCK.containsKey(
                ResourceLocation.fromNamespaceAndPath("cataclysm", "door_of_seal"))) {
            String command = String.format("place structure cataclysm:cursed_pyramid %d %d %d",
                    cx - 44, y, cz - 47);
            try {
                level.getServer().getCommands().performPrefixedCommand(
                        source.withSuppressedOutput().withPermission(4), command);
                return;
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                        .warn("Pyramide de Cataclysm non posee, repli sur la notre", e);
            }
        }
        steppedPyramid(level, cx, y, cz);
    }

    /**
     * Notre pyramide, gardee comme REPLI seulement.
     *
     * Elle sert quand Cataclysm manque. Elle reste modeste, et c'est assume :
     * ce n'est pas la peine de rivaliser avec un batiment fait a la main quand
     * on peut poser celui-la.
     */
    private static void steppedPyramid(ServerLevel level, int cx, int y, int cz) {
        int tiers = 10;
        for (int tier = 0; tier < tiers; tier++) {
            int half = 22 - tier * 2;
            int top = y + 1 + tier * 3;
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    boolean rim = Math.abs(dx) == half || Math.abs(dz) == half;
                    for (int dy = 0; dy < 3; dy++) {
                        set(level, cx + dx, top + dy, cz + dz,
                                rim && dy == 2 ? shrineTrim() : shrine());
                    }
                }
            }
            for (int side = -1; side <= 1; side += 2) {
                set(level, cx + side * half, top + 2, cz, glow());
                set(level, cx, top + 2, cz + side * half, glow());
            }
            // l'escalier, gradin par gradin : il DOIT mener au sommet
            for (int dx = -1; dx <= 1; dx++) {
                for (int step = 0; step < 3; step++) {
                    set(level, cx + dx, top + step, cz + half - step, trim());
                    for (int clear = 1; clear <= 3; clear++) {
                        set(level, cx + dx, top + step + clear, cz + half - step,
                                Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /**
     * Le parvis de l'ancre, au sommet.
     *
     * Il est POSE PAR-DESSUS ce qui se trouve la : le sommet de la pyramide de
     * Cataclysm n'est pas plat, et l'ancre doit se voir de la cour. Quatre
     * obelisques d'arcencium l'encadrent, et une colonne de verre prismatique
     * descend jusqu'a la maconnerie -- de loin, c'est elle qu'on repere.
     */
    private static BlockPos crown(ServerLevel level, int cx, int y, int cz) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 1; dy <= 8; dy++) {
                    set(level, cx + dx, y + dy, cz + dz, Blocks.AIR.defaultBlockState());
                }
                boolean rim = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                set(level, cx + dx, y, cz + dz, rim ? shrineTrim() : shrine());
            }
        }
        // les quatre obelisques
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int ox = cx + sx * 4;
                int oz = cz + sz * 4;
                for (int dy = 1; dy <= 5; dy++) {
                    set(level, ox, y + dy, oz, dy % 2 == 0 ? shrineTrim() : shrine());
                }
                set(level, ox, y + 6, oz, glow());
                set(level, ox, y + 7, oz, lantern());
            }
        }
        // le socle, un cran plus haut que le parvis
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, cx + dx, y + 1, cz + dz, shrineTrim());
            }
        }
        BlockPos anchor = new BlockPos(cx, y + 2, cz);
        level.setBlockAndUpdate(anchor, ModBlocks.PRISMATIC_ANCHOR.get().defaultBlockState());
        return anchor;
    }

    // ------------------------------------------------------------- l'enceinte

    private static void clearSite(ServerLevel level, int cx, int y, int cz) {
        for (int dx = -HALF - TOWER_RADIUS; dx <= HALF + TOWER_RADIUS; dx++) {
            for (int dz = -HALF - TOWER_RADIUS; dz <= HALF + TOWER_RADIUS; dz++) {
                // on ne degage que la BANDE de l'enceinte et la cour au sol :
                // vider tout le volume jusqu'au ciel couterait des centaines de
                // milliers de blocs pour rien, la pyramide se posant apres
                boolean band = Math.abs(dx) > HALF - THICK - 2 || Math.abs(dz) > HALF - THICK - 2;
                int top = band ? TOWER_TOP + 3 : 2;
                for (int dy = 1; dy <= top; dy++) {
                    set(level, cx + dx, y + dy, cz + dz, Blocks.AIR.defaultBlockState());
                }
                for (int dy = 0; dy >= -3; dy--) {
                    BlockPos pos = new BlockPos(cx + dx, y + dy, cz + dz);
                    if (level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()) {
                        level.setBlock(pos, base(), 2);
                    }
                }
            }
        }
    }

    private static void courtyard(ServerLevel level, int cx, int y, int cz) {
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                boolean road = Math.abs(dx) <= GATE_HALF && dz > 0;
                // un damier discret : une cour d'un seul bloc fait dalle de beton
                boolean checker = Math.floorMod(dx + dz, 8) == 0;
                set(level, cx + dx, y, cz + dz,
                        road ? trim() : checker ? shrineTrim() : floor());
            }
        }
    }

    /**
     * La courtine : trois blocs d'epaisseur, un chemin de ronde praticable.
     *
     * Le detail qui change tout est le CHEMIN DE RONDE : la masse est pleine
     * jusqu'a huit, on marche donc a neuf, et c'est la seule hauteur ou l'on
     * marche. Tout ce qui monte -- rampes, tours -- debouche a neuf. Le parapet
     * se dresse a dix et onze, cote exterieur seulement, pour ne pas gener la
     * circulation cote cour.
     */
    private static void curtainWall(ServerLevel level, int cx, int y, int cz) {
        for (int d = -HALF; d <= HALF; d++) {
            for (int t = 0; t < THICK; t++) {
                for (int side = 0; side < 4; side++) {
                    int[] xz = wallPoint(side, d, t, cx, cz);
                    if (side == 1 && Math.abs(d) <= GATE_HALF) {
                        continue;              // le passage de la porte
                    }
                    for (int dy = 1; dy <= WALL_TOP; dy++) {
                        BlockState mat = dy <= 2 ? base()
                                : dy == WALL_TOP ? trim()
                                : (Math.floorMod(d, 9) == 0 ? shrine() : body());
                        set(level, xz[0], y + dy, xz[1], mat);
                    }
                    set(level, xz[0], y + WALK, xz[1], floor());     // on marche ici

                    if (t == 0) {              // le parapet, cote exterieur
                        set(level, xz[0], y + WALK + 1, xz[1],
                                Math.floorMod(d, 2) == 0 ? merlon() : glow());
                        if (Math.floorMod(d, 2) == 0) {
                            set(level, xz[0], y + WALK + 2, xz[1], merlon());
                        }
                    }
                    if (t == THICK - 1 && Math.floorMod(d, 2) == 0) {
                        set(level, xz[0], y + WALK + 1, xz[1], merlon());  // garde-corps
                    }
                    // les meurtrieres, percees dans la masse
                    if (t == 0 && Math.floorMod(d, 7) == 0) {
                        set(level, xz[0], y + 5, xz[1], glow());
                        set(level, xz[0], y + 6, xz[1], glow());
                    }
                }
            }
            // les lanternes du chemin de ronde
            if (Math.floorMod(d, 11) == 0) {
                for (int side = 0; side < 4; side++) {
                    int[] xz = wallPoint(side, d, THICK - 1, cx, cz);
                    set(level, xz[0], y + WALK + 1, xz[1], lantern());
                }
            }
        }
        // quatre volees d'escalier, une par cote, qui aboutissent VRAIMENT a neuf
        stairToWalk(level, cx - 20, y, cz - HALF + THICK, Direction.NORTH, 0, 1);
        stairToWalk(level, cx + 20, y, cz + HALF - THICK, Direction.SOUTH, 0, -1);
        stairToWalk(level, cx - HALF + THICK, y, cz + 20, Direction.WEST, 1, 0);
        stairToWalk(level, cx + HALF - THICK, y, cz - 20, Direction.EAST, -1, 0);
    }

    private static int[] wallPoint(int side, int d, int t, int cx, int cz) {
        return switch (side) {
            case 0 -> new int[]{cx + d, cz - HALF + t};
            case 1 -> new int[]{cx + d, cz + HALF - t};
            case 2 -> new int[]{cx - HALF + t, cz + d};
            default -> new int[]{cx + HALF - t, cz + d};
        };
    }

    /**
     * Une volee d'escalier de la cour au chemin de ronde.
     *
     * Elle part du pied du mur vers l'interieur et monte marche par marche
     * jusqu'a la hauteur exacte du chemin de ronde. Un palier de deux blocs la
     * termine, faute de quoi on arrive nez au parapet.
     */
    private static void stairToWalk(ServerLevel level, int x, int y, int z,
                                    Direction facing, int dx, int dz) {
        for (int step = 0; step < WALK; step++) {
            int sx = x - dx * (step + 1);
            int sz = z - dz * (step + 1);
            for (int w = -1; w <= 1; w++) {
                int px = sx + (dx == 0 ? w : 0);
                int pz = sz + (dz == 0 ? w : 0);
                for (int fill = 0; fill <= step; fill++) {
                    set(level, px, y + fill, pz, body());
                }
                set(level, px, y + step + 1, pz, stair(
                        ModBlocks.POLISHED_GANGUE_STAIRS.get(), facing.getOpposite()));
                for (int clear = 2; clear <= 4; clear++) {
                    set(level, px, y + step + clear, pz, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /**
     * Une tour d'angle CREUSE, avec son escalier et sa porte.
     *
     * C'etait le reproche le plus net du premier essai : des tours pleines,
     * qu'on ne pouvait pas visiter. Celle-ci a un rez-de-chaussee ouvert sur la
     * cour, un escalier en vis contre sa paroi, une sortie a hauteur du chemin
     * de ronde, et une plateforme sommitale creneleee.
     */
    private static void cornerTower(ServerLevel level, int tx, int y, int tz) {
        int r = TOWER_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r + 0.5) {
                    continue;
                }
                boolean shell = dist > r - 1.0;
                for (int dy = 1; dy <= TOWER_TOP; dy++) {
                    if (!shell) {
                        set(level, tx + dx, y + dy, tz + dz, Blocks.AIR.defaultBlockState());
                        continue;              // le creux : c'est la qu'on entre
                    }
                    BlockState mat = dy <= 2 ? base()
                            : dy % 6 == 0 ? shrineTrim() : tower();
                    set(level, tx + dx, y + dy, tz + dz, mat);
                }
                // le plancher et la plateforme
                set(level, tx + dx, y, tz + dz, floor());
                if (!shell) {
                    set(level, tx + dx, y + TOWER_TOP, tz + dz, floor());
                }
                // les creneaux du sommet
                if (shell && Math.floorMod(dx + dz, 2) == 0) {
                    set(level, tx + dx, y + TOWER_TOP + 1, tz + dz, merlon());
                }
                // les fenetres, en croix
                if (shell && (dx == 0 || dz == 0)) {
                    set(level, tx + dx, y + 5, tz + dz, glow());
                    set(level, tx + dx, y + 12, tz + dz, glow());
                }
            }
        }
        // l'escalier en vis, contre la paroi
        int steps = TOWER_TOP - 1;
        for (int step = 0; step < steps; step++) {
            double angle = step * 0.62;        // un tour complet tous les dix pas
            int sx = tx + (int) Math.round(Math.cos(angle) * (r - 1.6));
            int sz = tz + (int) Math.round(Math.sin(angle) * (r - 1.6));
            set(level, sx, y + step, sz, trim());
            set(level, sx, y + step + 1, sz, Blocks.AIR.defaultBlockState());
            set(level, sx, y + step + 2, sz, Blocks.AIR.defaultBlockState());
        }
        // la trappe du sommet, et la porte vers le chemin de ronde
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, tx + dx, y + TOWER_TOP, tz + dz, Blocks.AIR.defaultBlockState());
            }
        }
        for (int dy = WALK; dy <= WALK + 2; dy++) {
            for (int d = -r; d <= r; d++) {
                // deux ouvertures, vers les deux courtines qui rejoignent la tour
                set(level, tx + d, y + dy, tz, Math.abs(d) > r - 2
                        ? Blocks.AIR.defaultBlockState() : level.getBlockState(
                                new BlockPos(tx + d, y + dy, tz)));
                set(level, tx, y + dy, tz + d, Math.abs(d) > r - 2
                        ? Blocks.AIR.defaultBlockState() : level.getBlockState(
                                new BlockPos(tx, y + dy, tz + d)));
            }
        }
        set(level, tx, y + TOWER_TOP + 1, tz, lantern());
    }

    /**
     * Le corps de garde et sa herse.
     *
     * La herse n'est plus une grille de barreaux -- « on dirait une prison ».
     * C'est un treillis de metal noir monte dans un cadre de pierre taillee,
     * suspendu a deux chaines qui remontent a la poulie. Le passage est
     * voute, flanque de braseros, et perce d'assommoirs au-dessus.
     */
    private static void gatehouse(ServerLevel level, int cx, int y, int cz) {
        int gz = cz + HALF;

        // les deux tours du corps de garde, plus hautes que la courtine
        for (int side = -1; side <= 1; side += 2) {
            int bx = cx + side * (GATE_HALF + 4);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 4) {
                        continue;
                    }
                    boolean shell = Math.abs(dx) == 3 || Math.abs(dz) == 3
                            || Math.abs(dx) + Math.abs(dz) == 4;
                    for (int dy = 1; dy <= TOWER_TOP - 2; dy++) {
                        if (!shell) {
                            set(level, bx + dx, y + dy, gz + dz, Blocks.AIR.defaultBlockState());
                            continue;
                        }
                        set(level, bx + dx, y + dy, gz + dz,
                                dy <= 2 ? base() : dy % 5 == 0 ? shrineTrim() : tower());
                    }
                    if (shell && Math.floorMod(dx + dz, 2) == 0) {
                        set(level, bx + dx, y + TOWER_TOP - 1, gz + dz, merlon());
                    }
                }
            }
            set(level, bx, y + TOWER_TOP - 1, gz, lantern());
        }

        // la voute du passage : un arc, pas un linteau plat
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -GATE_HALF - 1; dx <= GATE_HALF + 1; dx++) {
                int arch = GATE_HEIGHT + 1 + (GATE_HALF + 1 - Math.abs(dx)) / 2;
                for (int dy = GATE_HEIGHT + 1; dy <= arch; dy++) {
                    set(level, cx + dx, y + dy, gz + dz, trim());
                }
                for (int dy = arch + 1; dy <= arch + 3; dy++) {
                    set(level, cx + dx, y + dy, gz + dz, body());
                }
            }
            // les jambages tailles
            for (int dy = 1; dy <= GATE_HEIGHT; dy++) {
                set(level, cx - GATE_HALF - 1, y + dy, gz + dz,
                        dy % 3 == 0 ? shrineTrim() : trim());
                set(level, cx + GATE_HALF + 1, y + dy, gz + dz,
                        dy % 3 == 0 ? shrineTrim() : trim());
            }
            for (int dx = -GATE_HALF; dx <= GATE_HALF; dx++) {
                set(level, cx + dx, y, gz + dz, trim());
                for (int dy = 1; dy <= GATE_HEIGHT; dy++) {
                    set(level, cx + dx, y + dy, gz + dz, Blocks.AIR.defaultBlockState());
                }
            }
        }
        // les assommoirs : des trous dans la voute, par ou l'on recoit les visiteurs
        for (int dx = -GATE_HALF + 1; dx <= GATE_HALF - 1; dx += 2) {
            set(level, cx + dx, y + GATE_HEIGHT + 1, gz, Blocks.AIR.defaultBlockState());
        }
        // les braseros du seuil
        for (int side = -1; side <= 1; side += 2) {
            int bx = cx + side * (GATE_HALF + 2);
            set(level, bx, y + 1, gz + 3, shrineTrim());
            set(level, bx, y + 2, gz + 3, first(lantern(),
                    "supplementaries:fire_pit", "minecraft:campfire"));
        }

        // le mecanisme : poulie, cordes, manivelle, et les chaines de la herse
        BlockState pulley = optional("supplementaries:pulley_block");
        BlockState rope = optional("supplementaries:rope");
        BlockState crank = optional("supplementaries:crank");
        if (pulley != null) {
            set(level, cx, y + GATE_HEIGHT + 6, gz, pulley);
        }
        if (rope != null) {
            for (int dy = GATE_HEIGHT + 4; dy <= GATE_HEIGHT + 5; dy++) {
                set(level, cx, y + dy, gz, rope);
            }
        }
        if (crank != null) {
            set(level, cx + GATE_HALF + 2, y + WALK, gz - 1, crank);
        } else {
            set(level, cx + GATE_HALF + 2, y + WALK, gz - 1,
                    Blocks.LEVER.defaultBlockState());
        }

        SanctuaryGate.register(new BlockPos(cx, y, gz), GATE_HALF, GATE_HEIGHT);
        SanctuaryGate.close(level, new BlockPos(cx, y, gz));
    }

    // ----------------------------------------------------------- outillage

    private static BlockState stair(Block block, Direction facing) {
        return block.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM);
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        // drapeau 2 : on previent le client sans declencher de mise a jour de
        // voisinage -- sur cent mille blocs, les cascades couteraient bien plus
        // cher que la pose elle-meme
        level.setBlock(new BlockPos(x, y, z), state, 2);
    }
}
