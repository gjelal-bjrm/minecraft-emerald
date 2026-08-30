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

    /**
     * Demi-cote de la muraille.
     *
     * Elle etait a 62 pour une pyramide de quatre-vingt-huit blocs de haut :
     * vue d'ensemble, un muret autour d'une montagne. On monte a 80, et
     * surtout on l'EPAISSIT et on la HAUSSE -- une enceinte se juge a sa
     * proportion, pas a son emprise.
     */
    private static final int HALF = 80;

    /** Epaisseur de la courtine. */
    private static final int THICK = 5;

    /** Hauteur de la masse pleine ; on marche sur le bloc du dessus. */
    private static final int WALL_TOP = 18;

    /** Le chemin de ronde, seule hauteur ou l'on marche sur le mur. */
    private static final int WALK = WALL_TOP + 1;

    private static final int TOWER_RADIUS = 7;
    private static final int TOWER_TOP = 32;

    /**
     * L'ouverture est taillee sur la Porte du Sceau, pas l'inverse.
     *
     * Le releve de son NBT donne cinq blocs de large et huit de haut : c'est
     * elle qui commande, et une baie trop etroite l'aurait tronquee.
     */
    private static final int GATE_HALF = SealDoor.WIDTH / 2;
    private static final int GATE_HEIGHT = SealDoor.HEIGHT;

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

        // LA PYRAMIDE D'ABORD, et l'enceinte ensuite, autour d'elle.
        //
        // L'inverse etait l'erreur de fond : on batissait les murs, puis on
        // posait la pyramide a un decalage devine (-44, -47) en esperant
        // qu'elle tombe au milieu. Elle tombait a cote, en grande partie hors
        // les murs. La commande de placement du jeu decide seule de l'endroit
        // exact et de la rotation ; la seule facon fiable de s'y accorder est
        // de la laisser faire, puis de MESURER ou elle a atterri.
        greatPyramid(level, source, ground.getX(), y, ground.getZ());
        int[] bounds = measure(level, ground.getX(), y, ground.getZ());
        int cx = (bounds[0] + bounds[2]) / 2;
        int cz = (bounds[1] + bounds[3]) / 2;
        reskin(level, bounds, y);

        clearSite(level, cx, y, cz, bounds);
        courtyard(level, cx, y, cz);
        curtainWall(level, cx, y, cz);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                cornerTower(level, cx + sx * HALF, y, cz + sz * HALF);
            }
        }
        // QUATRE portes, une par cote.
        //
        // La pyramide est posee par la commande du jeu, qui choisit elle-meme
        // sa rotation : impossible de savoir a l'avance vers ou son entree
        // regarde, et une porte unique tombait donc de travers une fois sur
        // quatre... ou trois. Quatre portes reglent la question par
        // construction, et une forteresse a quatre portes n'a rien d'absurde.
        for (int side = 0; side < 4; side++) {
            gatehouse(level, cx, y, cz, side);
        }

        int summit = summitOf(level, cx, y, cz);
        BlockPos anchor = crown(level, cx, summit, cz);
        ascent(level, cx, y, cz, summit, bounds);
        SanctuaryGarrison.populate(level, new BlockPos(cx, y, cz), HALF);
        SanctuaryMist.register(new BlockPos(cx, y, cz), HALF, anchor);
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
            // aucun decalage devine : on la pose ici, et on mesurera ou elle
            // a reellement atterri -- c'est elle qui decide, pas nous
            String command = String.format("place structure cataclysm:cursed_pyramid %d %d %d",
                    cx, y, cz);
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

    /**
     * La montee vers l'ancre : une tour d'escalier et sa passerelle.
     *
     * C'est le point qui a bloque deux fois -- « je ne trouve pas l'ancre ».
     * Elle est au sommet, a quatre-vingt-huit blocs, et la Pyramide Maudite n'a
     * jamais ete concue pour qu'on l'escalade par l'exterieur. Tailler un
     * escalier dans sa face aurait suppose de connaitre sa forme, qu'on ne
     * connait pas puisque c'est la commande du jeu qui la pose.
     *
     * On batit donc a cote, ce qu'on maitrise entierement : une tour creuse au
     * pied de la face sud, un escalier en vis dedans, une passerelle au sommet.
     * La montee est longue et c'est tant mieux -- on doit sentir qu'on monte a
     * quelque chose.
     */
    private static void ascent(ServerLevel level, int cx, int y, int cz, int summit,
                               int[] bounds) {
        int tx = cx;
        // JUSTE au-dela du pied de la pyramide, cote sud : plus pres, la tour
        // serait noyee dans la maconnerie ; plus loin, la passerelle
        // traverserait la moitie de la cour
        int tz = bounds[3] + 6;
        int top = summit + 2;
        int r = 4;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r + 0.5) {
                    continue;
                }
                boolean shell = dist > r - 1.0;
                for (int wy = y + 1; wy <= top; wy++) {
                    if (!shell) {
                        set(level, tx + dx, wy, tz + dz, Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    int h = wy - y;
                    set(level, tx + dx, wy, tz + dz,
                            h <= 2 ? base() : h % 8 == 0 ? shrineTrim() : tower());
                }
                set(level, tx + dx, y, tz + dz, floor());
                // les fenetres, pour qu'on voie ou l'on en est
                if (shell && (dx == 0 || dz == 0) && Math.floorMod(top - y, 1) == 0) {
                    for (int wy = y + 6; wy < top; wy += 7) {
                        set(level, tx + dx, wy, tz + dz, glow());
                    }
                }
            }
        }
        // l'entree, cote cour
        for (int dy = 1; dy <= 3; dy++) {
            set(level, tx, y + dy, tz + r, Blocks.AIR.defaultBlockState());
            set(level, tx, y + dy, tz + r - 1, Blocks.AIR.defaultBlockState());
        }
        // l'escalier en vis
        for (int step = 0; step + y < top - 1; step++) {
            double angle = step * 0.55;
            int sx = tx + (int) Math.round(Math.cos(angle) * (r - 1.5));
            int sz = tz + (int) Math.round(Math.sin(angle) * (r - 1.5));
            set(level, sx, y + step, sz, trim());
            for (int clear = 1; clear <= 3; clear++) {
                set(level, sx, y + step + clear, sz, Blocks.AIR.defaultBlockState());
            }
            if (step % 12 == 0) {
                set(level, tx, y + step + 2, tz, lantern());   // le fut eclaire
            }
        }
        // la sortie et la passerelle vers le parvis
        for (int dz = 0; dz <= tz - cz - 4; dz++) {
            int bz = tz - dz;
            for (int dx = -1; dx <= 1; dx++) {
                set(level, tx + dx, top, bz, trim());
                for (int clear = 1; clear <= 3; clear++) {
                    set(level, tx + dx, top + clear, bz, Blocks.AIR.defaultBlockState());
                }
            }
            // un garde-corps : la passerelle est haute
            set(level, tx - 2, top + 1, bz, merlon());
            set(level, tx + 2, top + 1, bz, merlon());
            if (Math.floorMod(dz, 8) == 0) {
                set(level, tx - 2, top + 2, bz, lantern());
                set(level, tx + 2, top + 2, bz, lantern());
            }
        }
        for (int dy = 1; dy <= 3; dy++) {
            set(level, tx, top + dy, tz - r + 1, Blocks.AIR.defaultBlockState());
        }
    }

    // ------------------------------------------------------------- l'enceinte

    private static void clearSite(ServerLevel level, int cx, int y, int cz, int[] keep) {
        for (int dx = -HALF - TOWER_RADIUS; dx <= HALF + TOWER_RADIUS; dx++) {
            for (int dz = -HALF - TOWER_RADIUS; dz <= HALF + TOWER_RADIUS; dz++) {
                // on ne rase JAMAIS la pyramide qu'on vient de poser
                if (cx + dx >= keep[0] - 1 && cx + dx <= keep[2] + 1
                        && cz + dz >= keep[1] - 1 && cz + dz <= keep[3] + 1) {
                    continue;
                }
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
                // Pas de motif calcule sur (dx + dz) : cela dessinait des
                // rayures DIAGONALES en travers de toute la cour, ce qui ne
                // ressemble a aucun dallage. Un damier franc, ou rien.
                boolean road = Math.abs(dx) <= GATE_HALF || Math.abs(dz) <= GATE_HALF;
                boolean tile = Math.floorMod(dx, 8) < 4 == Math.floorMod(dz, 8) < 4;
                set(level, cx + dx, y, cz + dz,
                        road ? trim() : tile ? floor() : walkStone());
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
                    if (Math.abs(d) <= GATE_HALF) {
                        continue;              // le passage des quatre portes
                    }
                    for (int dy = 1; dy <= WALL_TOP; dy++) {
                        BlockState mat = dy <= 2 ? base()
                                : dy == WALL_TOP ? trim()
                                : (Math.floorMod(d, 9) == 0 ? shrine() : body());
                        set(level, xz[0], y + dy, xz[1], mat);
                    }
                    set(level, xz[0], y + WALK, xz[1], floor());     // on marche ici

                    // Les deux bords portent un mur PLEIN, sans trou.
                    //
                    // La premiere version alternait merlon, vide, verre, vide.
                    // Vu de loin cela ne faisait ni un creneau ni un mur, juste
                    // un pointille -- et le verre prismatique intercale jurait
                    // avec la pierre. Un parapet continu se lit comme une
                    // fortification ; l'eclairage se pose PAR-DESSUS.
                    if (t == 0 || t == THICK - 1) {
                        set(level, xz[0], y + WALK + 1, xz[1], merlon());
                    }
                }
            }
            // les lanternes, POSEES SUR le parapet interieur : elles eclairent
            // le chemin de ronde sans y creuser de breche
            if (Math.floorMod(d, 9) == 0) {
                for (int side = 0; side < 4; side++) {
                    int[] xz = wallPoint(side, d, THICK - 1, cx, cz);
                    set(level, xz[0], y + WALK + 2, xz[1], lantern());
                }
            }
        }
        // quatre volees d'escalier, une par cote, qui aboutissent VRAIMENT a neuf
        // Les volees courent LE LONG de la face interieure, adossees au mur.
        //
        // Perpendiculaires, elles s'avancaient de neuf blocs dans la cour en
        // une rampe pleine : de loin, quatre petites pyramides grises posees au
        // hasard au milieu de rien. Une rampe de rempart se colle au mur.
        rampAlong(level, cx, y, cz, 0);
        rampAlong(level, cx, y, cz, 1);
        rampAlong(level, cx, y, cz, 2);
        rampAlong(level, cx, y, cz, 3);
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
     * Une tour d'angle QU'ON PEUT VRAIMENT VISITER.
     *
     * Trois defauts corriges d'un coup, tous constates a l'essai.
     *
     * L'entree etait decalee : on ne percait la paroi que sur l'axe exact de la
     * tour, alors que le chemin de ronde est LARGE de trois blocs. On perce
     * desormais sur toute son epaisseur, par les deux courtines qui aboutissent
     * a la tour.
     *
     * L'escalier n'en etait pas un : les marches, calculees tous les 0,62
     * radian sur un rayon de 3,4, tombaient a plus de deux blocs l'une de
     * l'autre. On tourne maintenant en seize marches par tour sur un rayon de
     * 3, ce qui les rend jointives.
     *
     * Et il n'y avait aucun plancher : meme entre, on tombait de dix-huit
     * blocs. Chaque palier a le sien, perce du seul puits de l'escalier.
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
                set(level, tx + dx, y, tz + dz, floor());
                for (int dy = 1; dy <= TOWER_TOP; dy++) {
                    set(level, tx + dx, y + dy, tz + dz, shell
                            ? (dy <= 2 ? base() : dy % 6 == 0 ? shrineTrim() : tower())
                            : Blocks.AIR.defaultBlockState());
                }
                if (shell && Math.floorMod(dx + dz, 2) == 0) {
                    set(level, tx + dx, y + TOWER_TOP + 1, tz + dz, merlon());
                }
                if (shell && (dx == 0 || dz == 0)) {
                    set(level, tx + dx, y + 5, tz + dz, glow());
                    set(level, tx + dx, y + 13, tz + dz, glow());
                }
            }
        }

        // le fut central : il porte les paliers et guide la vis
        for (int dy = 1; dy <= TOWER_TOP; dy++) {
            set(level, tx, y + dy, tz, dy % 6 == 0 ? shrineTrim() : tower());
        }

        // les planchers, perces du puits de l'escalier
        for (int landing = 6; landing < TOWER_TOP; landing += 6) {
            floorDisc(level, tx, y + landing, tz, r - 1, 1.8);
        }
        // et celui du niveau d'entree : sans lui, on entre et on tombe
        floorDisc(level, tx, y + WALK - 1, tz, r - 1, 1.8);

        spiral(level, tx, y, tz, TOWER_TOP - 1, 3.0);

        // le sommet
        floorDisc(level, tx, y + TOWER_TOP, tz, r - 1, 2.6);
        // La lanterne PLANAIT : elle etait posee au-dessus du puits d'escalier,
        // c'est-a-dire au-dessus du vide. On lui donne un socle.
        set(level, tx, y + TOWER_TOP, tz, shrineTrim());
        set(level, tx, y + TOWER_TOP + 1, tz, lantern());

        // Les portes : DES PORTES, pas des breches.
        //
        // La version precedente evidait la paroi sur tout le diametre et sur
        // trois hauteurs : une croix beante par laquelle on tombait du haut du
        // rempart. Ce sont maintenant deux baies de trois de large sur trois de
        // haut, percees uniquement dans la coque, cote courtines.
        doorway(level, tx, y + WALK, tz, r, true);
        doorway(level, tx, y + WALK, tz, r, false);
        // et deux au ras du sol, cote cour : on entre par le bas
        doorway(level, tx, y + 1, tz, r, true);
        doorway(level, tx, y + 1, tz, r, false);
    }

    /**
     * Un plancher rond, perce en son centre.
     *
     * Le trou laisse passer l'escalier ; sans lui, la vis buterait sous chaque
     * palier. Le rayon interieur decide de la largeur du puits.
     */
    private static void floorDisc(ServerLevel level, int tx, int y, int tz,
                                  double outer, double inner) {
        int r = (int) Math.ceil(outer);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > outer) {
                    continue;
                }
                set(level, tx + dx, y, tz + dz,
                        dist > inner ? floor() : Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * Un escalier en vis JOINTIF.
     *
     * Seize marches par tour : l'arc entre deux marches vaut 2 pi r / 16, soit
     * un peu plus d'un bloc pour un rayon de 3 -- elles se touchent une fois
     * arrondies. La version precedente tournait de 0,62 radian par marche, ce
     * qui les espacait de plus de deux blocs : on ne montait pas, on sautait de
     * piquet en piquet quand on ne tombait pas.
     */
    private static void spiral(ServerLevel level, int tx, int y, int tz,
                               int height, double radius) {
        // Le nombre de marches par tour se DEDUIT du rayon, il n'est pas fixe.
        //
        // Seize marches convenaient a un rayon de trois ; sur un rayon de deux,
        // elles retombaient a la meme case une fois arrondies -- une pile
        // verticale qu'on ne peut pas gravir. On vise un arc constant d'environ
        // un bloc et quart entre deux marches, quel que soit le rayon.
        int perTurn = Math.max(6, (int) Math.round(Math.PI * 2 * radius / 1.25));
        double perStep = Math.PI * 2 / perTurn;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int step = 1; step <= height; step++) {
            double angle = step * perStep;
            int sx = tx + (int) Math.round(Math.cos(angle) * radius);
            int sz = tz + (int) Math.round(Math.sin(angle) * radius);
            set(level, sx, y + step, sz, trim());
            // la marche precedente est prolongee : deux cases par palier, sinon
            // la vis se prend de biais et l'on rate une marche sur deux
            if (lastX != Integer.MIN_VALUE && (lastX != sx || lastZ != sz)) {
                set(level, lastX, y + step, lastZ, trim());
            }
            for (int clear = 1; clear <= 3; clear++) {
                set(level, sx, y + step + clear, sz, Blocks.AIR.defaultBlockState());
                if (lastX != Integer.MIN_VALUE) {
                    set(level, lastX, y + step + clear, lastZ, Blocks.AIR.defaultBlockState());
                }
            }
            lastX = sx;
            lastZ = sz;
        }
    }

    /** Le repere d'une porte : son axe le long du mur, et sa normale sortante. */
    private record Gate(int ax, int az, int nx, int nz, int ox, int oz) {

        /** Le repere du cote demande : 0 nord, 1 sud, 2 ouest, 3 est. */
        static Gate of(int cx, int cz, int side) {
            return switch (side) {
                case 0 -> new Gate(1, 0, 0, -1, cx, cz - HALF);
                case 1 -> new Gate(1, 0, 0, 1, cx, cz + HALF);
                case 2 -> new Gate(0, 1, -1, 0, cx - HALF, cz);
                default -> new Gate(0, 1, 1, 0, cx + HALF, cz);
            };
        }

        int x(int along, int depth) {
            return this.ox + this.ax * along + this.nx * depth;
        }

        int z(int along, int depth) {
            return this.oz + this.az * along + this.nz * depth;
        }

        BlockPos centre(int y) {
            return new BlockPos(this.ox, y, this.oz);
        }

        /** La direction vers le dehors : celle que la porte doit regarder. */
        Direction outward() {
            if (this.nx != 0) {
                return this.nx > 0 ? Direction.EAST : Direction.WEST;
            }
            return this.nz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    /**
     * Un corps de garde, sur le cote demande.
     *
     * Il y en a QUATRE, un par cote. La pyramide est posee par la commande du
     * jeu, qui choisit elle-meme sa rotation : impossible de savoir vers ou son
     * entree regarde, et une porte unique tombait de travers trois fois sur
     * quatre. Quatre portes reglent la question par construction, et une
     * forteresse a quatre portes n'a rien d'absurde.
     */
    private static void gatehouse(ServerLevel level, int cx, int y, int cz, int side) {
        Gate g = Gate.of(cx, cz, side);

        for (int flank = -1; flank <= 1; flank += 2) {
            int seat = flank * (GATE_HALF + 4);
            for (int a = -3; a <= 3; a++) {
                for (int d = -3; d <= 3; d++) {
                    if (Math.abs(a) + Math.abs(d) > 4) {
                        continue;
                    }
                    boolean shell = Math.abs(a) == 3 || Math.abs(d) == 3
                            || Math.abs(a) + Math.abs(d) == 4;
                    int px = g.x(seat + a, d);
                    int pz = g.z(seat + a, d);
                    for (int dy = 1; dy <= TOWER_TOP - 2; dy++) {
                        set(level, px, y + dy, pz, shell
                                ? (dy <= 2 ? base() : dy % 5 == 0 ? shrineTrim() : tower())
                                : Blocks.AIR.defaultBlockState());
                    }
                    if (shell && Math.floorMod(a + d, 2) == 0) {
                        set(level, px, y + TOWER_TOP - 1, pz, merlon());
                    }
                }
            }
            // Les tours du corps de garde etaient CREUSES ET VIDES : ni
            // plancher, ni escalier, ni porte. On y tombait, quand on arrivait
            // a y entrer. Elles recoivent le meme traitement que les tours
            // d'angle, a leur echelle.
            int bx = g.x(seat, 0);
            int bz = g.z(seat, 0);
            for (int dy = 1; dy <= TOWER_TOP - 2; dy++) {
                set(level, bx, y + dy, bz, dy % 5 == 0 ? shrineTrim() : tower());
            }
            for (int landing = 5; landing < TOWER_TOP - 3; landing += 5) {
                floorDisc(level, bx, y + landing, bz, 2.4, 1.1);
            }
            floorDisc(level, bx, y + WALK - 1, bz, 2.4, 1.1);
            floorDisc(level, bx, y + TOWER_TOP - 2, bz, 2.4, 1.1);
            spiral(level, bx, y, bz, TOWER_TOP - 4, 2.0);
            // une porte au chemin de ronde, une au ras de la cour
            doorway(level, bx, y + WALK, bz, 3, true);
            doorway(level, bx, y + WALK, bz, 3, false);
            doorway(level, bx, y + 1, bz, 3, true);
            doorway(level, bx, y + 1, bz, 3, false);

            set(level, bx, y + TOWER_TOP - 2, bz, shrineTrim());
            set(level, bx, y + TOWER_TOP - 1, bz, lantern());
        }

        // la voute : un arc, pas un linteau plat
        for (int d = -2; d <= 2; d++) {
            for (int a = -GATE_HALF - 1; a <= GATE_HALF + 1; a++) {
                int px = g.x(a, d);
                int pz = g.z(a, d);
                int arch = GATE_HEIGHT + 1 + (GATE_HALF + 1 - Math.abs(a)) / 2;
                for (int dy = GATE_HEIGHT + 1; dy <= arch; dy++) {
                    set(level, px, y + dy, pz, trim());
                }
                for (int dy = arch + 1; dy <= arch + 3; dy++) {
                    set(level, px, y + dy, pz, body());
                }
            }
            for (int dy = 1; dy <= GATE_HEIGHT; dy++) {
                set(level, g.x(-GATE_HALF - 1, d), y + dy, g.z(-GATE_HALF - 1, d),
                        dy % 3 == 0 ? shrineTrim() : trim());
                set(level, g.x(GATE_HALF + 1, d), y + dy, g.z(GATE_HALF + 1, d),
                        dy % 3 == 0 ? shrineTrim() : trim());
            }
            for (int a = -GATE_HALF; a <= GATE_HALF; a++) {
                set(level, g.x(a, d), y, g.z(a, d), trim());
                for (int dy = 1; dy <= GATE_HEIGHT; dy++) {
                    set(level, g.x(a, d), y + dy, g.z(a, d), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int a = -GATE_HALF + 1; a <= GATE_HALF - 1; a += 2) {
            set(level, g.x(a, 0), y + GATE_HEIGHT + 1, g.z(a, 0),
                    Blocks.AIR.defaultBlockState());
        }
        for (int flank = -1; flank <= 1; flank += 2) {
            int a = flank * (GATE_HALF + 2);
            set(level, g.x(a, 3), y + 1, g.z(a, 3), shrineTrim());
            set(level, g.x(a, 3), y + 2, g.z(a, 3),
                    first(lantern(), "supplementaries:fire_pit", "minecraft:campfire"));
        }

        BlockState pulley = optional("supplementaries:pulley_block");
        BlockState rope = optional("supplementaries:rope");
        BlockState crank = optional("supplementaries:crank");
        if (pulley != null) {
            set(level, g.x(0, 0), y + GATE_HEIGHT + 6, g.z(0, 0), pulley);
        }
        if (rope != null) {
            for (int dy = GATE_HEIGHT + 4; dy <= GATE_HEIGHT + 5; dy++) {
                set(level, g.x(0, 0), y + dy, g.z(0, 0), rope);
            }
        }
        BlockState handle = crank != null ? crank : Blocks.LEVER.defaultBlockState();
        set(level, g.x(GATE_HALF + 2, -1), y + WALK, g.z(GATE_HALF + 2, -1), handle);

        Direction outward = g.outward();
        if (SealDoor.available()) {
            SealDoor.place(level, g.centre(y).above(), outward, false);
        }
        SanctuaryGate.register(g.centre(y), GATE_HALF, GATE_HEIGHT,
                g.ax(), g.az(), outward);
        SanctuaryGate.close(level, g.centre(y));
    }


    /**
     * L'emprise reelle de la pyramide, mesuree apres coup.
     *
     * On balaie les colonnes autour du point demande et on retient celles qui
     * portent quelque chose de haut : au-dessus de six blocs, en terrain
     * degage, ce ne peut etre que la pyramide. C'est grossier et c'est
     * suffisant -- on ne cherche pas sa forme, seulement sa boite.
     *
     * @return {xMin, zMin, xMax, zMax}
     */
    private static int[] measure(ServerLevel level, int cx, int y, int cz) {
        int reach = 150;
        int xMin = Integer.MAX_VALUE;
        int zMin = Integer.MAX_VALUE;
        int xMax = Integer.MIN_VALUE;
        int zMax = Integer.MIN_VALUE;
        for (int dx = -reach; dx <= reach; dx += 2) {
            for (int dz = -reach; dz <= reach; dz += 2) {
                int x = cx + dx;
                int z = cz + dz;
                int top = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        x, z);
                if (top - y < 6) {
                    continue;
                }
                xMin = Math.min(xMin, x);
                zMin = Math.min(zMin, z);
                xMax = Math.max(xMax, x);
                zMax = Math.max(zMax, z);
            }
        }
        if (xMin > xMax) {
            // rien de haut : la pyramide n'a pas ete posee, on garde le centre
            return new int[]{cx - 20, cz - 20, cx + 20, cz + 20};
        }
        return new int[]{xMin, zMin, xMax, zMax};
    }

    /**
     * Rhabille la pyramide de NOS materiaux.
     *
     * Elle arrive en gres, ce qui ne dit rien de l'Arcencium. On ne repeint que
     * la PEAU -- les trois premiers blocs pleins rencontres en descendant
     * depuis le ciel -- parce que c'est tout ce qu'on voit, et que repeindre le
     * volume entier ferait sept cent mille blocs pour rien.
     *
     * Les escaliers, dalles et murets sont laisses tels quels : leur silhouette
     * porte le relief du batiment, et les remplacer par des blocs pleins
     * l'aplatirait.
     */
    private static void reskin(ServerLevel level, int[] bounds, int y) {
        for (int x = bounds[0]; x <= bounds[2]; x++) {
            for (int z = bounds[1]; z <= bounds[3]; z++) {
                int top = level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        x, z) - 1;
                if (top - y < 4) {
                    continue;
                }
                int painted = 0;
                for (int dy = top; dy > y && painted < 3; dy--) {
                    BlockPos pos = new BlockPos(x, dy, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockState skin = skinFor(state, dy - y);
                    if (skin != null) {
                        level.setBlock(pos, skin, 2);
                    }
                    painted++;
                }
            }
        }
    }

    /**
     * Le materiau qui remplace celui-ci, ou rien s'il faut le laisser.
     *
     * On ne touche qu'aux blocs PLEINS d'un seul etat : un escalier ou une
     * dalle porte une orientation qu'il faudrait recopier, et se tromper de
     * recopie abime la forme plus surement qu'un gres laisse en place.
     */
    @Nullable
    private static BlockState skinFor(BlockState state, int height) {
        if (!state.getProperties().isEmpty()) {
            return null;                       // oriente : on n'y touche pas
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (id.startsWith(EmeraldWeaponsMod.MODID)) {
            return null;                       // deja a nous
        }
        if (id.contains("chiseled") || id.contains("cut_")) {
            return shrineTrim();
        }
        if (id.contains("smooth")) {
            return trim();
        }
        if (id.contains("sandstone") || id.contains("sand")) {
            // une assise plus sombre en bas, l'arcencium en haut : la pyramide
            // s'eclaircit vers son sommet, ou se trouve l'ancre
            return height > 55 ? shrine() : height > 25 ? body() : base();
        }
        return null;
    }

    /**
     * Une rampe de rempart, adossee a la face interieure du mur.
     *
     * Elle monte le long du mur, sur trois blocs de large, et se termine de
     * plain-pied sur le chemin de ronde. Chaque marche est portee par la
     * maconnerie sous elle : pas de rampe pleine posee dans la cour, pas de
     * volee suspendue.
     */
    private static void rampAlong(ServerLevel level, int cx, int y, int cz, int side) {
        Gate g = Gate.of(cx, cz, side);
        // on part a vingt blocs du milieu du cote, vers la porte
        int from = 20;
        for (int step = 0; step <= WALK; step++) {
            int along = from + step;
            for (int w = 0; w < 3; w++) {
                int depth = -THICK - w;        // vers l'interieur de la cour
                int px = g.x(along, depth);
                int pz = g.z(along, depth);
                for (int fill = 0; fill < step; fill++) {
                    set(level, px, y + fill, pz, body());
                }
                set(level, px, y + step, pz, trim());
                for (int clear = 1; clear <= 3; clear++) {
                    set(level, px, y + step + clear, pz, Blocks.AIR.defaultBlockState());
                }
            }
            // le garde-corps cote cour
            set(level, g.x(along, -THICK - 3), y + step + 1, g.z(along, -THICK - 3), merlon());
        }
        // le palier : on debouche sur le chemin de ronde sans marche perdue
        for (int a = from + WALK; a <= from + WALK + 2; a++) {
            for (int w = 0; w < 3; w++) {
                set(level, g.x(a, -THICK - w), y + WALK, g.z(a, -THICK - w), floor());
            }
        }
    }

    /** La pierre du dallage secondaire : le damier a besoin de deux teintes. */
    private static BlockState walkStone() {
        return ModBlocks.GANGUE_STONE.get().defaultBlockState();
    }


    /**
     * Une baie percee dans la coque d'une tour, sur un axe.
     *
     * Trois de large et trois de haut, et RIEN de plus : evider tout le
     * diametre transformait la tour en carrefour ouvert d'ou l'on tombait.
     * L'encadrement en pierre taillee dit que c'est une porte et non un trou.
     */
    private static void doorway(ServerLevel level, int tx, int y, int tz,
                                int r, boolean alongX) {
        for (int side = -1; side <= 1; side += 2) {
            for (int w = -1; w <= 1; w++) {
                for (int dy = 0; dy < 3; dy++) {
                    // on ne perce que la coque : deux blocs d'epaisseur suffisent
                    for (int t = 0; t < 2; t++) {
                        int off = side * (r - t);
                        int px = alongX ? tx + off : tx + w;
                        int pz = alongX ? tz + w : tz + off;
                        set(level, px, y + dy, pz, Blocks.AIR.defaultBlockState());
                    }
                }
                // le linteau, qui referme proprement au-dessus
                int off = side * r;
                int px = alongX ? tx + off : tx + w;
                int pz = alongX ? tz + w : tz + off;
                set(level, px, y + 3, pz, shrineTrim());
            }
        }
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
