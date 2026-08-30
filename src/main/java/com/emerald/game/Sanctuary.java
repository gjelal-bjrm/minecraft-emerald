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
    private static final int HALF = 96;

    /** Epaisseur de la courtine. */
    private static final int THICK = 6;

    /**
     * Hauteur de la masse pleine ; on marche sur le bloc du dessus.
     *
     * Vingt-quatre, et non vingt-deux, pour une raison d'ajustement : les
     * tours ont un plancher tous les six blocs, et la porte du chemin de ronde
     * doit s'ouvrir SUR l'un d'eux. A vingt-deux, on debouchait a un metre du
     * sol et l'on tombait de cinq blocs a l'interieur de la tour.
     */
    private static final int WALL_TOP = 24;

    /** Le chemin de ronde, seule hauteur ou l'on marche sur le mur. */
    private static final int WALK = WALL_TOP + 1;

    /**
     * Rayon des tours. Neuf, et non sept.
     *
     * Une tour de rayon sept laisse un interieur de onze blocs, dont un fut
     * central et une vis : il n'y restait pas de place pour se tenir. A neuf,
     * on circule autour de la vis et les paliers servent a quelque chose.
     */
    private static final int TOWER_RADIUS = 9;

    /**
     * Hauteur des tours. Quarante-deux, un multiple de six.
     *
     * Les planchers se posent tous les six blocs : une hauteur qui n'en est pas
     * un multiple laisse le dernier etage en l'air, sans volee pour y monter.
     */
    private static final int TOWER_TOP = 42;

    /** Les mesures de la Pyramide Maudite, relevees dans ses onze modeles. */
    private static final int PYRAMID_W = 89;
    private static final int PYRAMID_D = 94;

    /** Le centre du modele : c'est la que ses quatre quadrants se rejoignent. */
    private static final int PYRAMID_CX = 44;
    private static final int PYRAMID_CZ = 47;

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

        int cx = ground.getX();
        int cz = ground.getZ();

        // On POSE la pyramide au centre, on ne la cherche plus.
        //
        // Deux methodes ont echoue avant celle-ci. Deviner un decalage la
        // faisait tomber a cote. Mesurer apres coup marchait en theorie, mais
        // le balayage accrochait le sanctuaire d'a cote quand on en batissait
        // deux -- et l'enceinte se centrait alors entre les deux.
        //
        // La bonne facon etait de lire les modeles. Les quatre quadrants font
        // 47 et 42 de large sur 47 de profond, et leur jonction -- le centre de
        // la pyramide -- tombe a (44, 47) de l'angle. On connait donc l'endroit
        // exact ou poser chaque morceau pour que le sommet vienne sur le
        // centre voulu, sans rien mesurer.
        int[] bounds = {cx - PYRAMID_CX, cz - PYRAMID_CZ,
                cx - PYRAMID_CX + PYRAMID_W, cz - PYRAMID_CZ + PYRAMID_D};
        greatPyramid(level, source, cx, y, cz);
        reskin(level, bounds, y);

        clearSite(level, cx, y, cz, bounds);
        courtyard(level, cx, y, cz);
        curtainWall(level, cx, y, cz);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                cornerTower(level, cx, cz, cx + sx * HALF, y, cz + sz * HALF);
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

        // Le sommet du modele n'est pas au milieu de son emprise : il tombe a
        // (44, 44) de l'angle, alors que la jonction des quadrants est a
        // (44, 47). L'ancre etait donc bien au centre de la PLACE, mais trois
        // blocs a cote du faite de la pyramide.
        int apexZ = cz - (PYRAMID_CZ - 44);
        int summit = summitOf(level, cx, y, apexZ);
        BlockPos anchor = crown(level, cx, summit, apexZ);
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
                // On DESCEND depuis le plafond du monde au lieu d'interroger la
                // carte des hauteurs. Celle-ci se met a jour au fil des poses et
                // se laisse tromper par ce qu'on vient de batir ; un balayage
                // franc ne rend que ce qui est reellement la, maintenant.
                int top = y;
                for (int probe = level.getMaxBuildHeight() - 1; probe > y; probe--) {
                    if (!level.getBlockState(new BlockPos(cx + dx, probe, cz + dz)).isAir()) {
                        top = probe;
                        break;
                    }
                }
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
            int ox = cx - PYRAMID_CX;
            int oz = cz - PYRAMID_CZ;
            // Les quadrants, dans l'ordre releve : 1 au nord-ouest, 2 au
            // sud-ouest, 3 au nord-est, 4 au sud-est. Les superieurs se posent
            // quarante-huit blocs plus haut, aux memes abscisses.
            int[][] quads = {{1, 0, 0}, {2, 0, 47}, {3, 47, 0}, {4, 47, 47}};
            boolean ok = true;
            for (int[] q : quads) {
                // La moitie basse est ENTERREE, et c'est ce qui manquait.
                //
                // Les quatre morceaux inferieurs ne sont pas un socle : ce sont
                // les salles du tombeau, un pave de quarante-huit blocs de haut
                // que le generateur enfouit. Poses au niveau du sol, ils
                // sortaient de terre en un enorme bloc rectangulaire sous la
                // pyramide -- « je ne comprends pas pourquoi tu l'as mise sur
                // un pilier ». On les descend, en laissant affleurer quatre
                // blocs qui font un parvis naturel.
                ok &= template(level, source, "cursed_pyramid_lower" + q[0],
                        ox + q[1], y - BURIED, oz + q[2]);
                ok &= template(level, source, "cursed_pyramid_upper" + q[0],
                        ox + q[1], y - BURIED + 48, oz + q[2]);
            }
            if (ok) {
                scrubMarkers(level, ox, y - BURIED, oz);
                return;
            }
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .warn("Pyramide de Cataclysm incomplete, repli sur la notre");
        }
        steppedPyramid(level, cx, y, cz);
    }

    /**
     * Pose un modele a un endroit EXACT.
     *
     * « place template » depose le fichier tel quel a la position donnee, sans
     * assemblage ni rotation, contrairement a « place structure » qui laisse le
     * generateur decider. C'est ce qui rend le placement previsible.
     */
    private static boolean template(ServerLevel level, CommandSourceStack source,
                                    String name, int x, int y, int z) {
        try {
            level.getServer().getCommands().performPrefixedCommand(
                    source.withSuppressedOutput().withPermission(4),
                    String.format("place template cataclysm:%s %d %d %d", name, x, y, z));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
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
        // deux coffres encadrent l'ancre : le sommet doit payer la montee
        lootChest(level, cx - 3, y + 1, cz, "minecraft:chests/end_city_treasure");
        lootChest(level, cx + 3, y + 1, cz, "minecraft:chests/desert_pyramid");

        BlockPos anchor = new BlockPos(cx, y + 2, cz);
        level.setBlockAndUpdate(anchor, ModBlocks.PRISMATIC_ANCHOR.get().defaultBlockState());
        // On VERIFIE : l'ancre a disparu une fois sans qu'on sache pourquoi, et
        // une place forte sans son ancre n'a plus d'objet. Mieux vaut un
        // avertissement dans le journal qu'une enigme de plus.
        if (!level.getBlockState(anchor).is(ModBlocks.PRISMATIC_ANCHOR.get())) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .warn("Ancre non posee en {} : {}", anchor, level.getBlockState(anchor));
        }
        return anchor;
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
            // Pas de lanterne AU-DESSUS DES PORTES : la boucle d'eclairage
            // ignorait l'ouverture des baies, alors que le parapet qui les
            // porte n'y existe pas. Elles pendaient donc dans le vide entre
            // les deux tours du corps de garde.
            if (Math.floorMod(d, 9) == 0 && Math.abs(d) > GATE_HALF) {
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
     * Une tour d'angle : coque ronde, planchers pleins, escalier droit.
     *
     * Tout le detail est dans {@link #roundTower}. Ne restent ici que ses deux
     * portes : l'une sur le chemin de ronde, l'autre au ras de la cour.
     */
    private static void cornerTower(ServerLevel level, int cx, int cz,
                                    int tx, int y, int tz) {
        roundTower(level, tx, y, tz, TOWER_RADIUS, TOWER_TOP);
        // Vers la cour, jamais vers le dehors : le signe se deduit de la
        // position du coin par rapport au centre de la place.
        int inX = Integer.signum(cx - tx);
        int inZ = Integer.signum(cz - tz);
        // au chemin de ronde, les deux courtines qui aboutissent a la tour
        doorway(level, tx, y + WALK, tz, TOWER_RADIUS, true, inX);
        doorway(level, tx, y + WALK, tz, TOWER_RADIUS, false, inZ);
        // Au sol, la baie doit s'ECARTER de la courtine.
        //
        // Une tour d'angle est prise dans les deux murs qui s'y rejoignent :
        // percee sur l'axe de la tour, la baie du bas suivait exactement la
        // ligne du rempart et le tunnelisait de part en part -- d'ou les trous
        // qui debouchaient DEHORS, l'inverse exact de ce qu'on veut. On la
        // decale lateralement d'une epaisseur de mur, ce qui la fait deboucher
        // franchement dans la cour.
        doorway(level, tx, y + 1, tz, TOWER_RADIUS, true, inX, inZ * (THICK + 1));
        doorway(level, tx, y + 1, tz, TOWER_RADIUS, false, inZ, inX * (THICK + 1));
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

        // Les deux tours du corps de garde, batties comme les autres.
        //
        // Elles reposaient sur un losange tronque dont le test de coque
        // laissait passer des cellules : d'ou les grands trous qu'on voyait
        // depuis l'exterieur. Un cercle franc, et le meme interieur que les
        // tours d'angle.
        for (int flank = -1; flank <= 1; flank += 2) {
            int seat = flank * (GATE_HALF + 6);
            int bx = g.x(seat, 0);
            int bz = g.z(seat, 0);
            roundTower(level, bx, y, bz, 6, TOWER_TOP - 6);
            // La normale de la porte pointe DEHORS : on perce donc a l'oppose.
            boolean acrossX = g.nx() != 0;
            int inward = -(acrossX ? g.nx() : g.nz());
            doorway(level, bx, y + WALK, bz, 6, acrossX, inward);
            doorway(level, bx, y + 1, bz, 6, acrossX, inward);
            // et le long du rempart, pour passer d'une tour au chemin de ronde
            doorway(level, bx, y + WALK, bz, 6, !acrossX, 1);
            doorway(level, bx, y + WALK, bz, 6, !acrossX, -1);
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
        // Le palier, ET le passage vers le chemin de ronde.
        //
        // « quand on arrive en haut, on ne peut pas passer » : la rampe
        // aboutissait bien a la bonne hauteur, mais le garde-corps interieur du
        // rempart lui barrait la route. On ouvre donc la travee en face.
        // Le palier commence APRES la derniere marche.
        //
        // Il partait deux blocs plus tot, donc il recouvrait les deux dernieres
        // marches et les remplissait en dur jusqu'a la hauteur du chemin de
        // ronde : on grimpait, et l'on butait sur un pan de trois blocs pousse
        // devant soi. Le palier ne doit jamais mordre sur la volee.
        for (int a = from + WALK + 1; a <= from + WALK + 3; a++) {
            for (int w = 0; w < 3; w++) {
                int px = g.x(a, -THICK - w);
                int pz = g.z(a, -THICK - w);
                // le palier repose sur la maconnerie jusqu'au sol : sans cela
                // il saillait du mur comme une dalle suspendue dans le vide
                for (int fill = 0; fill < WALK; fill++) {
                    set(level, px, y + fill, pz, body());
                }
                set(level, px, y + WALK, pz, floor());
                for (int clear = 1; clear <= 3; clear++) {
                    set(level, px, y + WALK + clear, pz, Blocks.AIR.defaultBlockState());
                }
            }
            // La breche dans le parapet, du cote INTERIEUR.
            //
            // Elle s'ouvrait a des profondeurs positives, c'est-a-dire au-dela
            // de la face exterieure du rempart : on perforait le vide dehors
            // pendant que le garde-corps continuait de barrer la route. Le
            // corps du mur est aux profondeurs NEGATIVES.
            for (int t = 0; t < THICK; t++) {
                set(level, g.x(a, -t), y + WALK, g.z(a, -t), floor());
                for (int clear = 1; clear <= 3; clear++) {
                    set(level, g.x(a, -t), y + WALK + clear, g.z(a, -t),
                            Blocks.AIR.defaultBlockState());
                }
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
    /**
     * Une baie, sur UN cote seulement.
     *
     * Elle en percait deux, ce qui donnait quatre portes par tour -- dont deux
     * ouvertes sur l'exterieur. On entrait donc dans la place forte par une
     * tour de garde sans passer par sa porte, et l'enceinte ne servait plus a
     * rien. Le cote est desormais impose par l'appelant.
     *
     * @param side +1 ou -1 : le sens ou percer, le long de l'axe choisi
     */
    private static void doorway(ServerLevel level, int tx, int y, int tz,
                                int r, boolean alongX, int side) {
        doorway(level, tx, y, tz, r, alongX, side, 0);
    }

    /**
     * @param shift decalage lateral de la baie, perpendiculairement a son axe
     */
    private static void doorway(ServerLevel level, int tx, int y, int tz,
                                int r, boolean alongX, int side, int shift) {
        // Une baie de DEUX blocs de large, et rien qui deborde.
        //
        // Trois de large sur une tour ronde, avec un linteau pose par-dessus,
        // laissait aux extremites des morceaux qui saillaient dans le vide :
        // « les portes debordent du cote du mur, ce n'est pas realiste ». On
        // s'en tient au couloir central, et la coque se referme d'elle-meme
        // au-dessus -- une voute taillee n'a pas besoin d'un linteau rapporte.
        {
            for (int w0 = 0; w0 <= 1; w0++) {
                int w = w0 + shift;
                for (int dy = 0; dy < 3; dy++) {
                    for (int t = 0; t <= r; t++) {
                        int off = side * (r - t);
                        int px = alongX ? tx + off : tx + w;
                        int pz = alongX ? tz + w : tz + off;
                        double dist = Math.sqrt((double) (px - tx) * (px - tx)
                                + (double) (pz - tz) * (pz - tz));
                        if (dist > r + 0.5) {
                            continue;          // dehors : on ne touche a rien
                        }
                        if (dist < r - 1.5) {
                            break;             // dedans : la coque est franchie
                        }
                        set(level, px, y + dy, pz, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }


    /**
     * La part enterree du tombeau : TOUTE sa hauteur.
     *
     * A quarante-quatre, il en affleurait quatre blocs, ce qui soulevait la
     * pyramide d'autant : elle paraissait posee sur une marche, et l'entree du
     * tombeau -- qui se trouve au sommet de la partie enterree -- passait
     * au-dessus du sol, inaccessible. A quarante-huit, la pyramide s'assoit
     * exactement sur la cour et son entree tombe de plain-pied.
     */
    private static final int BURIED = 48;

    /**
     * Retire les blocs techniques du modele.
     *
     * « place template » depose le fichier TEL QUEL, blocs de structure et
     * jonctions compris -- vingt-trois dans le seul premier quadrant. Ce sont
     * eux, « les structures blocs posees aleatoirement a l'interieur ». La
     * commande de generation les consommerait ; celle-ci ne le fait pas, c'est
     * a nous de nettoyer.
     */
    private static void scrubMarkers(ServerLevel level, int ox, int oy, int oz) {
        for (int dx = 0; dx < PYRAMID_W; dx++) {
            for (int dz = 0; dz < PYRAMID_D; dz++) {
                for (int dy = 0; dy < 88; dy++) {
                    BlockPos pos = new BlockPos(ox + dx, oy + dy, oz + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if (id.equals("structure_block") || id.equals("jigsaw")
                            || id.equals("structure_void")) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /**
     * L'interieur d'une tour : des planchers PLEINS et un escalier droit.
     *
     * Trois versions de vis ont echoue avant celle-ci, et toujours pour la
     * meme raison : une helice traverse chaque plancher sur toute sa course, si
     * bien que le degagement qu'elle exige au-dessus d'elle y taille une fente
     * continue. D'ou « plein de trous dans le sol a chaque etage ».
     *
     * Un escalier DROIT ne perce qu'a son arrivee. Chaque etage a donc son
     * plancher plein et une seule tremie, celle par ou l'on debouche ; la volee
     * suivante repart dans une autre direction, ce qui fait tourner la montee
     * sans jamais entamer le reste du sol.
     */
    private static void towerInterior(ServerLevel level, int tx, int y, int tz,
                                      int radius, int top) {
        int storey = 6;
        double inner = radius - 1.0;
        for (int base = 0; base + storey <= top; base += storey) {
            int turn = (base / storey) % 4;
            int dx = turn == 0 ? 1 : turn == 2 ? -1 : 0;
            int dz = turn == 1 ? 1 : turn == 3 ? -1 : 0;

            // le plancher du palier, PLEIN : l'escalier y percera sa tremie
            solidFloor(level, tx, y + base + storey, tz, inner);

            // le coffre de l'etage, adosse a la paroi, a l'oppose de la volee
            lootChest(level, tx + (int) (dx == 0 ? inner - 2 : 0),
                    y + base + storey + 1,
                    tz + (int) (dz == 0 ? inner - 2 : 0),
                    "minecraft:chests/simple_dungeon");

            int reach = (int) inner - 1;
            for (int i = 0; i < storey; i++) {
                int px = tx - dx * (reach - i);
                int pz = tz - dz * (reach - i);
                // deux blocs de large : on ne monte pas en file indienne
                for (int w = 0; w <= 1; w++) {
                    int qx = px + (dx == 0 ? w : 0);
                    int qz = pz + (dz == 0 ? w : 0);
                    set(level, qx, y + base + i, qz, trim());
                    for (int clear = 1; clear <= 3; clear++) {
                        set(level, qx, y + base + i + clear, qz,
                                Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** Un plancher rond et PLEIN, sans le moindre trou. */
    /**
     * Le rayon du plancher doit rejoindre la COQUE, pas s'arreter avant.
     *
     * La coque commence a {@code rayon - 1} ; un plancher trace jusqu'a
     * {@code rayon - 1,5} laissait un demi-bloc de vide tout autour --
     * « beaucoup de trous, surtout dans les bords ».
     */
    private static void solidFloor(ServerLevel level, int tx, int y, int tz, double radius) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (Math.sqrt(dx * dx + dz * dz) <= radius) {
                    set(level, tx + dx, y, tz + dz, floor());
                }
            }
        }
    }

    /**
     * Une tour ronde et creuse, coque comprise.
     *
     * Les tours du corps de garde etaient bâties sur un losange tronque dont le
     * test de coque laissait passer des cellules : d'ou « d'immenses trous au
     * centre, vus de l'exterieur ». Un cercle franc n'a pas ce defaut.
     */
    private static void roundTower(ServerLevel level, int tx, int y, int tz,
                                   int radius, int top) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.5) {
                    continue;
                }
                boolean shell = dist > radius - 1.0;
                set(level, tx + dx, y, tz + dz, floor());
                for (int dy = 1; dy <= top; dy++) {
                    set(level, tx + dx, y + dy, tz + dz, shell
                            ? (dy <= 2 ? base() : dy % 6 == 0 ? shrineTrim() : tower())
                            : Blocks.AIR.defaultBlockState());
                }
                // Le parapet est CONTINU, et les creneaux se posent par-dessus.
                //
                // Un bloc de muret isole reste un poteau : c'est en se touchant
                // que ces blocs se lient et forment une balustrade. Les semer
                // un sur deux donnait une couronne de piquets separes, ce qui
                // n'a l'air de rien. On pose donc la ceinture entiere, puis un
                // bloc plein tous les trois pour le creneau.
                if (shell) {
                    set(level, tx + dx, y + top + 1, tz + dz, merlon());
                    if (Math.floorMod(dx * 3 + dz, 3) == 0) {
                        set(level, tx + dx, y + top + 2, tz + dz, trim());
                    }
                }
                if (shell && (dx == 0 || dz == 0) && radius > 4) {
                    set(level, tx + dx, y + 6, tz + dz, glow());
                }
            }
        }
        // L'escalier monte jusqu'au SOMMET, dernier plancher compris.
        //
        // Il s'arretait deux blocs plus bas et l'on posait le toit par-dessus :
        // le dernier etage n'avait donc aucun acces, dans toutes les tours.
        towerInterior(level, tx, y, tz, radius, top);
        // Rien au MILIEU du dernier plancher : c'est la que l'escalier
        // debouche, et le bloc qui portait la lanterne barrait la sortie --
        // « on est bloque par un bloc sur lequel tu as pose une lanterne ».
        // Les lanternes vont sur le parapet, ou elles ne genent personne.
        for (int side = -1; side <= 1; side += 2) {
            set(level, tx + side * (radius - 2), y + top + 1, tz, lantern());
            set(level, tx, y + top + 1, tz + side * (radius - 2), lantern());
        }
    }


    /**
     * Un coffre de butin, pose contre la paroi.
     *
     * Une tour qu'on visite doit RECOMPENSER la visite : sans cela on monte
     * une fois par curiosite, et plus jamais. Un coffre par etage suffit --
     * l'interet est d'y passer, pas d'y camper.
     */
    private static void lootChest(ServerLevel level, int x, int y, int z, String table) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof
                net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest) {
            chest.setLootTable(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.LOOT_TABLE,
                            ResourceLocation.parse(table)),
                    level.random.nextLong());
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
