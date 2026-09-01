package com.emerald.game;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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

    /** Hauteur des quatre morceaux hauts : c'est ce qui donne le faite. */
    private static final int PYRAMID_H = 40;

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
     * @param tier 1 a 3 : la difficulte de l'ancre, donc la richesse du butin
     * @return la position de l'ancre
     */
    public static BlockPos build(ServerLevel level, CommandSourceStack source,
                                 BlockPos ground, int tier) {
        int rank = Math.max(1, Math.min(3, tier));
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

        // LA COUR D'ABORD, LA PYRAMIDE ENSUITE.
        //
        // L'ordre inverse expliquait la ceinture de terre autour d'elle et la
        // marche d'un bloc. On epargnait son emprise au moment de paver, mais
        // cette emprise est un RECTANGLE, alors que la pyramide n'en occupe
        // que le centre : les quatre coins du rectangle restaient donc en
        // terrain naturel, un cran plus bas que la cour, et le rhabillage
        // venait ensuite les peindre en briques corrompues -- d'ou l'anneau
        // brun. En pavant tout d'abord, la pyramide se pose PAR-DESSUS et
        // remplace ce qu'elle recouvre, sans laisser de trou autour.
        // Le registre s'ouvre AVANT la premiere pose : il note, pour chaque
        // bloc, la routine qui l'a mis et sa position relative au centre --
        // c'est ce qui permet ensuite de designer un defaut a l'ecran et de
        // retrouver la ligne qui en est responsable.
        SanctuaryLedger.begin(new BlockPos(cx, y, cz));
        clearSite(level, cx, y, cz, bounds);
        courtyard(level, cx, y, cz);
        int apex = greatPyramid(level, source, cx, y, cz);
        int repainted = reskin(level, bounds, y);
        curtainWall(level, cx, y, cz);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                cornerTower(level, cx, cz, cx + sx * HALF, y, cz + sz * HALF, rank);
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
            gatehouse(level, cx, y, cz, side, rank);
        }

        // Le sommet du modele n'est pas au milieu de son emprise : il tombe a
        // (44, 44) de l'angle, alors que la jonction des quadrants est a
        // (44, 47). L'ancre etait donc bien au centre de la PLACE, mais trois
        // blocs a cote du faite de la pyramide.
        int apexZ = cz - (PYRAMID_CZ - 44);
        // Le faite est CONNU, on ne le cherche plus.
        //
        // Le sondage a echoue de trois facons differentes -- carte des hauteurs
        // pas encore a jour, sanctuaire voisin accroche, pyramide absente sans
        // le dire -- et chaque fois l'ancre finissait ailleurs. Or les quatre
        // morceaux hauts font quarante blocs et sont poses a y+1 : leur sommet
        // est donc a y+40, sans rien a mesurer. On ne sonde plus qu'en dernier
        // recours, quand on a du se rabattre sur notre propre pyramide.
        int summit = apex >= 0 ? apex : summitOf(level, cx, y, apexZ);
        BlockPos anchor = crown(level, cx, summit, apexZ, rank);
        // Le couloir d'abord, l'ascension PAR-DESSUS lui.
        //
        // Les deux tenaient la ligne centrale de la face sud et se disputaient
        // le sol : ecartee, la volee laissait le porche tranquille mais devenait
        // deux rampes de biais qui n'allaient nulle part. La bonne reponse est
        // d'EMPILER plutot que de croiser -- le couloir passe au ras du sol, et
        // son toit sert de chemin d'ascension. Rien ne se gene plus, et l'on
        // gagne un parvis surelu au lieu d'une rampe posee a cote.
        //
        // L'ordre compte donc : le toit doit exister avant qu'on marche dessus.
        tombEntrance(level, cx, y, cz, rank, anchor);
        causewayRamps(level, cx, y, cz);
        summitStair(level, cx, y, cz, apexZ, summit);
        SanctuaryMist.register(new BlockPos(cx, y, cz), HALF, anchor);
        SanctuaryLedger.part("fini");
        // L'instantane se prend UNE FOIS TOUT POSE : c'est lui qui permettra
        // de relever, plus tard, ce que le joueur aura change -- y compris ce
        // qu'il aura ajoute la ou nous n'avions rien mis.
        SanctuaryLedger.capture(level, new BlockPos(cx, y, cz), HALF + 4, 12, 56);
        SanctuaryLedger.part("fini");

        // Un compte rendu, plutot qu'une devinette de plus.
        //
        // « le bloc manque » s'est repete trois fois sans que rien, ni dans le
        // journal ni a l'ecran, ne dise POURQUOI. On rapporte donc les trois
        // faits qui separent les hypotheses : la pyramide s'est-elle dressee,
        // a quelle hauteur le sommet a ete trouve, et quel bloc occupe
        // reellement la case de l'ancre.
        linkWalls(level);

        // La garnison EN DERNIER, quand plus un bloc ne bouge.
        //
        // Elle etait posee avant le parvis, l'escalier du sommet et le couloir
        // du tombeau : ces trois-la lui tombaient dessus et l'etouffaient.
        SanctuaryGarrison.populate(level, new BlockPos(cx, y, cz), HALF, WALK, TOWER_TOP);

        BlockState found = level.getBlockState(anchor);
        String occupant = BuiltInRegistries.BLOCK.getKey(found.getBlock()).toString();
        source.sendSuccess(() -> Component.literal(String.format(
                "Sol %d | sommet %d | pyramide %s | %d blocs rhabilles | %s | a l'ancre : %s",
                y, summit, apex >= 0 ? "dressee" : "ABSENTE", repainted,
                sealReport, occupant)), false);
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
    /**
     * @return la hauteur du faite, ou -1 si l'on s'est rabattu sur la notre
     */
    private static int greatPyramid(ServerLevel level, CommandSourceStack source,
                                    int cx, int y, int cz) {
        SanctuaryLedger.part("greatPyramid");
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
                // SEULEMENT la moitie haute, et POSEE SUR LE SOL.
                //
                // Les quatre morceaux du bas sont les salles du tombeau : un
                // pave plein de quarante-huit blocs que le generateur enfouit.
                // Il n'y a pas toujours la place de l'enterrer -- dans un monde
                // dont le sol est a moins vingt-quatre, il faudrait descendre a
                // moins soixante-douze, sous le plancher du monde. On l'a donc
                // vu ressortir en enorme boite rectangulaire, trois fois.
                //
                // On ne le pose plus du tout. La pyramide s'arrete a ce qui se
                // voit, et elle s'assoit franchement sur la cour. Perdre les
                // salles enterrees coute moins que ce socle qui gachait tout.
                // Un bloc AU-DESSUS de la cour, et non dedans : posee a la
                // hauteur du pavage, sa premiere assise se confondait avec lui
                // et la pyramide paraissait enfoncee d'un cran.
                ok &= template(level, "cursed_pyramid_upper" + q[0],
                        ox + q[1], y + 1, oz + q[2]);
            }
            // On VERIFIE qu'une masse se dresse la ou le faite devrait etre :
            // « place template » ne leve rien quand il echoue, si bien qu'une
            // pose ratee passait inapercue.
            boolean standing = probeTop(level, cx, y, cz - (PYRAMID_CZ - 44)) > y + 12;
            if (ok && standing) {
                scrubMarkers(level, ox, y + 1, oz);
                return y + 1 + PYRAMID_H - 1;
            }
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID).warn(
                    "Pyramide de Cataclysm non posee (commandes={}, dressee={}) "
                            + "a y={} : repli sur la notre", ok, standing, y);
        }
        steppedPyramid(level, cx, y, cz);
        return -1;
    }

    /**
     * Pose un modele a un endroit EXACT.
     *
     * « place template » depose le fichier tel quel a la position donnee, sans
     * assemblage ni rotation, contrairement a « place structure » qui laisse le
     * generateur decider. C'est ce qui rend le placement previsible.
     */
    /**
     * Pose un modele DIRECTEMENT, sans passer par une commande.
     *
     * On appelait « place template » par le repartiteur de commandes, et c'est
     * la que tout se perdait : cette methode avale ses propres echecs. Elle
     * rendait donc toujours vrai, la pyramide n'etait jamais posee, et l'on
     * cherchait la cause partout ailleurs -- le sommet, l'enfouissement, le
     * rhabillage, l'ancre -- pendant que le seul vrai probleme etait ici.
     *
     * L'API des modeles, elle, rend un {@code Optional} vide quand le modele
     * est introuvable et un booleen quand la pose echoue. On sait donc ce qui
     * s'est passe, et le journal le dit.
     */
    private static boolean template(ServerLevel level, String name, int x, int y, int z) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath("cataclysm", name);
        var found = level.getStructureManager().get(key);
        if (found.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .warn("Modele introuvable : {}", key);
            return false;
        }
        BlockPos at = new BlockPos(x, y, z);
        // le drapeau 2 previent le client sans declencher de mise a jour de
        // voisinage, comme partout ailleurs dans cette classe
        boolean placed = found.get().placeInWorld(level, at, at,
                new net.minecraft.world.level.levelgen.structure.templatesystem
                        .StructurePlaceSettings(), level.random, 2);
        if (!placed) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .warn("Pose refusee pour {} en {}", key, at);
        }
        return placed;
    }

    /**
     * Notre pyramide, gardee comme REPLI seulement.
     *
     * Elle sert quand Cataclysm manque. Elle reste modeste, et c'est assume :
     * ce n'est pas la peine de rivaliser avec un batiment fait a la main quand
     * on peut poser celui-la.
     */
    private static void steppedPyramid(ServerLevel level, int cx, int y, int cz) {
        SanctuaryLedger.part("steppedPyramid");
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
    private static BlockPos crown(ServerLevel level, int cx, int y, int cz, int rank) {
        SanctuaryLedger.part("crown");
        // Le parvis est EN GRADINS, pas en plateau.
        //
        // Une dalle de neuf sur neuf posee a plat sur la pointe de la pyramide
        // avait des flancs a pic : on n'y montait pas, et il fallait casser des
        // blocs a cote de l'ancre pour l'atteindre. Chaque anneau descend d'un
        // cran vers l'exterieur, ce qui fait un escalier sur les quatre faces.
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                if (ring > 6) {
                    continue;
                }
                int step = Math.max(0, ring - 2);   // plat jusqu'au rayon deux
                for (int dy = 1; dy <= 8; dy++) {
                    set(level, cx + dx, y - step + dy, cz + dz,
                            Blocks.AIR.defaultBlockState());
                }
                set(level, cx + dx, y - step, cz + dz,
                        ring == 2 || ring == 6 ? shrineTrim() : shrine());
                // le flanc de chaque gradin, pour qu'il ne flotte pas
                for (int fill = 1; fill <= 2; fill++) {
                    set(level, cx + dx, y - step - fill, cz + dz, shrine());
                }
            }
        }
        // Les quatre obelisques, poses SUR leur gradin.
        //
        // Ils partaient tous de la hauteur du sommet, alors que le parvis
        // descend d'un cran par anneau : au rayon quatre, ils flottaient donc a
        // deux blocs au-dessus du sol. Meme chose pour les coffres.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int ox = cx + sx * 4;
                int oz = cz + sz * 4;
                int foot = y - Math.max(0, 4 - 2);
                for (int dy = 1; dy <= 5; dy++) {
                    set(level, ox, foot + dy, oz, dy % 2 == 0 ? shrineTrim() : shrine());
                }
                set(level, ox, foot + 6, oz, glow());
                set(level, ox, foot + 7, oz, lantern());
            }
        }
        // le socle, un cran plus haut que le parvis
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, cx + dx, y + 1, cz + dz, shrineTrim());
            }
        }
        // deux coffres encadrent l'ancre : le sommet doit payer la montee
        // Rien au sommet que l'ancre.
        //
        // Les deux coffres y etaient une facilite : puisqu'on monte desormais
        // par l'exterieur, on les aurait pris sans jamais entrer. Le tresor
        // descend dans le tombeau, ou il faut aller le chercher.

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
        SanctuaryLedger.part("clearSite");
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
        SanctuaryLedger.part("courtyard");
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
        SanctuaryLedger.part("curtainWall");
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
                        setWall(level, xz[0], y + WALK + 1, xz[1]);
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
                                    int tx, int y, int tz, int rank) {
        SanctuaryLedger.part("cornerTower");
        roundTower(level, tx, y, tz, TOWER_RADIUS, TOWER_TOP, rank);
        // Vers la cour, jamais vers le dehors : le signe se deduit de la
        // position du coin par rapport au centre de la place.
        int inX = Integer.signum(cx - tx);
        int inZ = Integer.signum(cz - tz);
        // Au chemin de ronde, DECALEES vers la cour de deux blocs.
        //
        // La tour est centree sur la ligne EXTERIEURE du rempart : une baie
        // percee sur son axe tombait donc au ras du parapet, du mauvais cote de
        // la travee ou l'on marche. Deux blocs vers l'interieur la ramenent en
        // face du passage.
        doorway(level, tx, y + WALK, tz, TOWER_RADIUS, true, inX, inZ * 2);
        doorway(level, tx, y + WALK, tz, TOWER_RADIUS, false, inZ, inX * 2);
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
    private static void gatehouse(ServerLevel level, int cx, int y, int cz, int side, int rank) {
        SanctuaryLedger.part("gatehouse");
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
            roundTower(level, bx, y, bz, 6, TOWER_TOP - 6, rank);
            // La normale de la porte pointe DEHORS : on perce donc a l'oppose.
            boolean acrossX = g.nx() != 0;
            int inward = -(acrossX ? g.nx() : g.nz());
            doorway(level, bx, y + WALK, bz, 6, acrossX, inward);
            doorway(level, bx, y + 1, bz, 6, acrossX, inward);
            // Le long du rempart, DECALEES de deux vers la cour.
            //
            // Meme defaut que sur les tours d'angle, et pour la meme raison :
            // ces tours sont centrees sur la ligne EXTERIEURE du mur, donc une
            // baie percee sur leur axe tombe au ras du parapet, a cote de la
            // travee ou l'on marche. Je l'avais corrige aux angles et oublie
            // ici.
            doorway(level, bx, y + WALK, bz, 6, !acrossX, 1, inward * 2);
            doorway(level, bx, y + WALK, bz, 6, !acrossX, -1, inward * 2);
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

        // Le rempart PASSE AU-DESSUS de la porte.
        //
        // Il s'arretait a la voute, une douzaine de blocs sous le chemin de
        // ronde : la courtine s'interrompait donc en plein milieu de chaque
        // cote, on voyait le paysage -- et la pyramide -- par la breche, et la
        // ronde ne pouvait pas faire le tour. On remonte la maconnerie jusqu'a
        // la hauteur du mur, chemin de ronde et parapets compris.
        for (int d = 0; d < THICK; d++) {
            for (int a = -GATE_HALF - 1; a <= GATE_HALF + 1; a++) {
                for (int dy = GATE_HEIGHT + 5; dy <= WALL_TOP; dy++) {
                    BlockState mat = dy == WALL_TOP ? trim()
                            : (Math.floorMod(a, 9) == 0 ? shrine() : body());
                    set(level, g.x(a, -d), y + dy, g.z(a, -d), mat);
                }
                set(level, g.x(a, -d), y + WALK, g.z(a, -d), floor());
                for (int clear = 1; clear <= 3; clear++) {
                    set(level, g.x(a, -d), y + WALK + clear, g.z(a, -d),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
        // les deux parapets de cette travee, comme partout ailleurs
        for (int a = -GATE_HALF - 1; a <= GATE_HALF + 1; a++) {
            setWall(level, g.x(a, 0), y + WALK + 1, g.z(a, 0));
            setWall(level, g.x(a, -(THICK - 1)), y + WALK + 1, g.z(a, -(THICK - 1)));
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

        // La face ornee de la Porte du Sceau est a l'OPPOSE de son « facing ».
        // Tournee vers le dehors, elle montrait donc son revers aux visiteurs
        // et son ornement a la cour : exactement l'inverse de ce qu'on veut
        // d'une porte de forteresse.
        Direction outward = g.outward().getOpposite();
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
    private static int reskin(ServerLevel level, int[] bounds, int y) {
        SanctuaryLedger.part("reskin");
        int painted_total = 0;
        // On mesure d'abord la hauteur reelle du batiment, pour que le degrade
        // se repartisse dessus au lieu de dependre de chiffres ecrits en dur.
        int crest = y;
        for (int x = bounds[0]; x <= bounds[2]; x += 4) {
            for (int z = bounds[1]; z <= bounds[3]; z += 4) {
                crest = Math.max(crest, probeTop(level, x, y, z));
            }
        }
        int span = Math.max(1, crest - y);

        for (int x = bounds[0]; x <= bounds[2]; x++) {
            for (int z = bounds[1]; z <= bounds[3]; z++) {
                // On DESCEND depuis le ciel, comme pour le sommet : la carte
                // des hauteurs ne connaissait pas encore la pyramide qu'on
                // venait de poser par commande, si bien que le rhabillage ne
                // mordait que sur le sol autour d'elle -- « tu n'as pas change
                // la texture de la pyramide ».
                int top = probeTop(level, x, y, z);
                if (top - y < 2) {
                    continue;
                }
                // les quatre aretes recoivent la teinte claire : c'est ce qui
                // dessine la silhouette contre le ciel
                double ex = Math.abs(x - (bounds[0] + bounds[2]) / 2.0);
                double ez = Math.abs(z - (bounds[1] + bounds[3]) / 2.0);
                boolean ridge = Math.abs(ex - ez) < 1.5;

                int painted = 0;
                for (int dy = top; dy > level.getMinBuildHeight() && painted < 4; dy--) {
                    BlockPos pos = new BlockPos(x, dy, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    BlockState skin = skinFor(state, (dy - y) / (double) span, ridge);
                    if (skin != null) {
                        level.setBlock(pos, skin, 2);
                        painted++;
                        painted_total++;
                    }
                    painted++;
                }
            }
        }
        return painted_total;
    }

    /**
     * Le materiau qui remplace celui-ci, ou rien s'il faut le laisser.
     *
     * On ne touche qu'aux blocs PLEINS d'un seul etat : un escalier ou une
     * dalle porte une orientation qu'il faudrait recopier, et se tromper de
     * recopie abime la forme plus surement qu'un gres laisse en place.
     */
    /**
     * La peau de la pyramide : SOMBRE, avec deux degrades et des aretes claires.
     *
     * Le gres d'origine ne disait rien de l'Arcencium. La gamme va du plus
     * sombre en bas -- briques corrompues, presque noires -- au plus clair au
     * sommet, ou l'ancre attend. Les quatre aretes tranchent en pierre polie :
     * ce sont elles qui dessinent la silhouette contre le ciel, et sans elles
     * un volume de cette taille se lit comme une masse plate.
     *
     * @param height la hauteur RELATIVE, de 0 au pied a 1 au faite
     */
    @Nullable
    private static BlockState skinFor(BlockState state, double height, boolean ridge) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (id.startsWith(EmeraldWeaponsMod.MODID)) {
            return null;                       // deja a nous
        }
        boolean skin = id.contains("sandstone") || id.contains("sand")
                || id.contains("chiseled") || id.contains("smooth")
                || id.contains("cut_");
        if (!skin) {
            return null;
        }
        // La FORME d'abord : la surface de cette pyramide est faite
        // d'escaliers et de dalles, et c'est precisement pour cela que le
        // rhabillage ne l'avait jamais touchee -- on refusait tout bloc portant
        // des proprietes, de peur d'en abimer l'orientation. On recopie
        // desormais ces proprietes sur le bloc equivalent, ce qui garde le
        // relief intact tout en changeant la matiere.
        String shape = id.endsWith("_stairs") ? "stairs"
                : id.endsWith("_slab") ? "slab"
                : id.endsWith("_wall") ? "wall" : "block";

        Block target;
        // La masse est SOMBRE, et ne s'eclaircit qu'au sommet.
        //
        // Un partage a moitie-moitie donnait une pyramide claire vue de loin,
        // alors qu'on la veut de pierre noire. Les deux tiers du bas sont donc
        // en briques corrompues, presque noires ; la gangue ne vient qu'au
        // dernier quart, et l'arcencium qu'au faite, la ou se trouve l'ancre.
        if (ridge) {
            target = pick(shape, "polished_gangue");
        } else if (height > 0.90) {
            target = pick(shape, "arcencium_brick");
        } else if (height > 0.68) {
            target = pick(shape, "gangue_brick");
        } else {
            target = pick(shape, "corrupted_brick");
        }
        if (target == null) {
            return null;
        }
        return copyShape(state, target.defaultBlockState());
    }

    /** Le bloc de notre famille qui a cette forme, ou rien. */
    @Nullable
    private static Block pick(String shape, String family) {
        // Le pluriel ne va QU'AU bloc plein.
        //
        // Nos blocs derives s'appellent « corrupted_brick_stairs », au
        // singulier ; je fabriquais « corrupted_bricks_stairs ». Aucun escalier
        // ni aucune dalle n'existait donc sous ce nom, le rhabillage les
        // laissait tous, et comme la surface d'une pyramide est presque
        // entierement faite d'escaliers, elle restait en gres. Seuls ses rares
        // blocs pleins changeaient -- « j'ai remarque que tu as pose des blocs
        // d'arcencium corrompu ».
        boolean plural = !family.startsWith("polished");
        String path = switch (shape) {
            case "stairs" -> family + "_stairs";
            case "slab" -> family + "_slab";
            case "wall" -> family + "_wall";
            default -> plural ? family + "s" : family;
        };
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(
                EmeraldWeaponsMod.MODID, path);
        return BuiltInRegistries.BLOCK.containsKey(key)
                ? BuiltInRegistries.BLOCK.get(key) : null;
    }

    /**
     * Recopie l'orientation d'un bloc sur un autre.
     *
     * Facing, moitie, forme, type, immersion : on ne transporte que ce que les
     * deux blocs ont en commun, si bien qu'un escalier reste un escalier tourne
     * dans le meme sens, et une dalle reste haute ou basse.
     */
    private static BlockState copyShape(BlockState from, BlockState to) {
        BlockState out = to;
        for (net.minecraft.world.level.block.state.properties.Property<?> property
                : from.getProperties()) {
            if (out.hasProperty(property)) {
                out = transfer(from, out, property);
            }
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState transfer(
            BlockState from, BlockState to,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    /** La hauteur du premier bloc plein, sondee depuis le plafond du monde. */
    private static int probeTop(ServerLevel level, int x, int y, int z) {
        for (int probe = level.getMaxBuildHeight() - 1;
                probe > level.getMinBuildHeight(); probe--) {
            if (!level.getBlockState(new BlockPos(x, probe, z)).isAir()) {
                return probe;
            }
        }
        return y;
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
        SanctuaryLedger.part("rampAlong");
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
                set(level, px, y + step, pz, riser(g.ax(), g.az()));
                for (int clear = 1; clear <= 3; clear++) {
                    set(level, px, y + step + clear, pz, Blocks.AIR.defaultBlockState());
                }
            }
            // Le garde-corps, en MARCHES LIEES.
            //
            // Un muret par marche, chacun un cran plus haut que le precedent,
            // ne donne que des poteaux : ces blocs ne se lient qu'a leurs
            // voisins horizontaux, et deux marches successives sont en
            // diagonale. On double donc chaque muret d'un second a la MEME
            // hauteur sur la colonne suivante : la rampe devient une suite
            // continue de paliers qui s'accrochent les uns aux autres.
            int rail = -THICK - 3;
            setWall(level, g.x(along, rail), y + step + 1, g.z(along, rail));
            setWall(level, g.x(along + 1, rail), y + step + 1, g.z(along + 1, rail));
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
            // La breche ne touche QUE le parapet interieur.
            //
            // Elle courait sur toute l'epaisseur du mur, parapet exterieur
            // compris : la rampe debouchait donc a la fois sur le chemin de
            // ronde et sur le vide dehors, et l'on quittait la place forte en
            // marchant tout droit. Le garde-corps du dedans doit s'ouvrir,
            // celui du dehors doit tenir.
            for (int t = 1; t < THICK; t++) {
                set(level, g.x(a, -t), y + WALK, g.z(a, -t), floor());
                for (int clear = 1; clear <= 3; clear++) {
                    set(level, g.x(a, -t), y + WALK + clear, g.z(a, -t),
                            Blocks.AIR.defaultBlockState());
                }
            }
            // et le parapet exterieur est REPOSE, au cas ou une etape
            // precedente l'aurait entame
            setWall(level, g.x(a, 0), y + WALK + 1, g.z(a, 0));
        }

        // La main courante rejoint le rempart.
        //
        // Elle s'arretait a la derniere marche, laissant un vide entre elle et
        // le parapet : la rampe paraissait finir dans le vague. Deux blocs de
        // plus a hauteur du chemin de ronde suffisent a fermer la jonction.
        for (int a = from + WALK; a <= from + WALK + 3; a++) {
            setWall(level, g.x(a, -THICK - 3), y + WALK + 1, g.z(a, -THICK - 3));
        }
        // Et le RETOUR d'equerre, qui rejoint le parapet du rempart.
        //
        // La main courante longeait la rampe puis s'arretait net a trois blocs
        // du garde-corps interieur : il restait un trou d'angle par lequel on
        // tombait dans la cour. On ferme la travee en ramenant les murets
        // perpendiculairement jusqu'au parapet, ou ils se lient a lui.
        for (int d = THICK + 3; d >= THICK - 1; d--) {
            setWall(level, g.x(from + WALK + 3, -d), y + WALK + 1,
                    g.z(from + WALK + 3, -d));
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
     * L'enfouissement REELLEMENT possible a cette altitude.
     *
     * C'est ce qui manquait, et le symptome etait deroutant : dans un monde ou
     * le sol est bas -- moins cinquante-neuf a l'essai -- enterrer le tombeau
     * de quarante-huit blocs le poussait a moins cent sept, sous le plancher
     * du monde. La pose echouait alors SANS RIEN DIRE : il n'y avait plus de
     * pyramide, le sondage du sommet ne trouvait que la cour, et l'ancre
     * finissait au ras du sol. On borne donc a ce que le monde permet.
     */
    private static int burialAt(ServerLevel level, int y) {
        return Math.max(0, Math.min(BURIED, y - level.getMinBuildHeight() - 2));
    }

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
        SanctuaryLedger.part("scrubMarkers");
        int floor = level.getMinBuildHeight();
        for (int dx = 0; dx < PYRAMID_W; dx++) {
            for (int dz = 0; dz < PYRAMID_D; dz++) {
                for (int dy = 0; dy < 44; dy++) {
                    if (oy + dy <= floor) {
                        continue;
                    }
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
                                      int radius, int top, int rank) {
        SanctuaryLedger.part("towerInterior");
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
                    sanctuaryTable(rank));

            int reach = (int) inner - 1;
            for (int i = 0; i < storey; i++) {
                int px = tx - dx * (reach - i);
                int pz = tz - dz * (reach - i);
                // deux blocs de large : on ne monte pas en file indienne
                for (int w = 0; w <= 1; w++) {
                    int qx = px + (dx == 0 ? w : 0);
                    int qz = pz + (dz == 0 ? w : 0);
                    set(level, qx, y + base + i, qz, riser(dx, dz));
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
        SanctuaryLedger.part("solidFloor");
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
                                   int radius, int top, int rank) {
        SanctuaryLedger.part("roundTower");
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
                    // Rien par-dessus : le bloc poli qu'on y ajoutait
                    // n'apportait rien qu'une bosse claire. La ceinture de
                    // murets se suffit, puisqu'elle se lie d'elle-meme.
                    setWall(level, tx + dx, y + top + 1, tz + dz);
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
        towerInterior(level, tx, y, tz, radius, top, rank);
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
        // Lootr d'abord : il donne a CHAQUE joueur son propre tirage, ce qui
        // est la seule facon honnete de recompenser un siege mene a plusieurs.
        // A defaut, notre coffre d'Arcencium, qui a au moins l'air d'etre de la
        // maison. Le coffre vanilla ne vient qu'en dernier recours.
        BlockState chestBlock = first(ModBlocks.ARCENCIUM_CHEST.get().defaultBlockState(),
                "lootr:lootr_chest");
        level.setBlock(pos, chestBlock, 2);
        if (level.getBlockEntity(pos) instanceof
                net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest) {
            chest.setLootTable(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.LOOT_TABLE,
                            ResourceLocation.parse(table)),
                    level.random.nextLong());
        }
    }

    /** La table de butin du palier : moyenne, bonne, puis de quoi affronter le boss. */
    private static String sanctuaryTable(int rank) {
        return "%s:chests/sanctuary_tier%d".formatted(EmeraldWeaponsMod.MODID,
                Math.max(1, Math.min(3, rank)));
    }


    /**
     * Les deux rampes qui montent sur le toit du couloir.
     *
     * Sans elles, le parvis surelu serait un chemin sans acces : on voit ou il
     * mene, on ne sait pas y grimper. Elles partent donc du dallage, de part et
     * d'autre du porche, et montent VERS le centre pour se poser de plain-pied
     * sur le toit -- une de chaque cote, pour qu'on la trouve d'ou qu'on vienne
     * et pour que l'entree reste encadree plutot que barree.
     *
     * Quatre marches suffisent : le toit est a quatre blocs, et une volee plus
     * longue mangerait la cour sans rien ajouter.
     */
    /**
     * Ou finit le parvis : la premiere case ou la pyramide le recouvre.
     *
     * Les deux routines qui bordent le parvis en ont besoin, et elles doivent
     * s'accorder au bloc pres -- sans quoi l'une borde ce que l'autre a deja
     * enseveli. On part du seuil et l'on remonte tant que le ciel est libre
     * au-dessus du toit.
     */
    private static int parvisEnd(ServerLevel level, int cx, int y, int fromZ) {
        int stop = fromZ;
        while (stop > fromZ - 20
                && level.getBlockState(new BlockPos(cx, y + 5, stop)).isAir()) {
            stop--;
        }
        return stop;
    }

    private static void causewayRamps(ServerLevel level, int cx, int y, int cz) {
        SanctuaryLedger.part("causewayRamps");
        int fromZ = cz + PYRAMID_D - PYRAMID_CZ;
        int roof = y + 4;

        // LA PORTEE SE CALCULE UNE FOIS, et non case par case.
        //
        // Le test « le parvis est-il a ciel ouvert ici ? » etait refait a
        // chaque z. Sur une pyramide a GRADINS il alterne vrai, faux, vrai,
        // faux au rythme des terrasses : la rampe se posait donc en peigne, une
        // colonne sur deux, et l'on montait un escalier troue. Ce n'etait pas
        // un probleme de hauteur mais de CONTINUITE -- une condition juste,
        // appliquee au mauvais grain.
        //
        // On cherche donc une bonne fois la longueur du parvis decouvert, en
        // partant du seuil et en remontant tant que le ciel est libre, puis
        // l'on batit cette portee d'un seul tenant.
        int stop = parvisEnd(level, cx, y, fromZ);
        for (int dir = -1; dir <= 1; dir += 2) {
            for (int i = 0; i <= 3; i++) {
                int x = cx + dir * (2 + i);        // 2 au sommet, 5 au pied
                int h = roof - i;
                for (int z = stop + 1; z <= fromZ + 1; z++) {
                    // La premiere assise n'est PAS une marche.
                    //
                    // Elle est de plain-pied avec le toit : elle ne fait donc
                    // rien monter, et posait seulement un liseré pale le long
                    // du parvis, sur toute sa longueur. On la rend a
                    // l'arcencium -- le parvis s'elargit d'un bloc de chaque
                    // cote et court d'un seul tenant jusqu'aux marches d'en
                    // face. Les marches, elles, commencent ou l'on descend.
                    if (i == 0) {
                        set(level, x, h, z, shrineTrim());
                        continue;
                    }
                    // la marche regarde vers le centre : c'est le sens de la montee
                    set(level, x, h, z, riser(-dir, 0));
                    for (int clear = 1; clear <= 3; clear++) {
                        set(level, x, h + clear, z, Blocks.AIR.defaultBlockState());
                    }
                    // et son remblai, pour qu'elle ne flotte pas au-dessus de la cour
                    for (int fill = 1; fill <= 3; fill++) {
                        if (h - fill <= y) {
                            break;
                        }
                        if (level.getBlockState(new BlockPos(x, h - fill, z)).isAir()) {
                            set(level, x, h - fill, z, shrine());
                        }
                    }
                }
            }
        }
    }

    /**
     * Un escalier de la cour au sommet, taille dans le flanc sud.
     *
     * La pyramide a bien son propre escalier, mais il ne mene qu'a ses salles :
     * rien ne monte jusqu'au parvis de l'ancre, et il fallait creuser a cote
     * d'elle pour l'atteindre -- au risque de la casser.
     *
     * On ne suppose RIEN de la forme du batiment : a chaque pas vers le centre,
     * on sonde ce qui se trouve la et on pose la marche dessus. L'escalier
     * epouse donc la pente reelle, gradins compris, quelle que soit la pyramide
     * qu'on a fini par batir.
     */
    private static void summitStair(ServerLevel level, int sx, int y, int cz,
                                    int apexZ, int summit) {
        SanctuaryLedger.part("summitStair");
        int fromZ = cz + PYRAMID_D - PYRAMID_CZ;       // le pied de la face sud
        // On part du TOIT du couloir, jamais du sol : c'est lui le parvis, et
        // descendre plus bas reviendrait a rouvrir le tombeau par le plafond.
        int roof = y + 4;
        int last = roof;

        // LA VOLEE COMMENCE OU LE PARVIS FINIT.
        //
        // Elle partait du bord sud de l'emprise. Or a cet endroit la premiere
        // terrasse depasse deja le toit du couloir : la clause « rien tant
        // qu'on est sur le plat » ne se declenchait donc jamais, et la volee
        // batissait par-dessus le parvis sur toute sa longueur -- une rangee
        // de marches posee sur un chemin qui est plat par construction, et qui
        // n'avait rien a faire la.
        //
        // Le parvis se traverse de plain-pied, les rampes laterales y donnent
        // acces, et l'escalier ne prend qu'ensuite. Les deux routines lisent
        // la meme limite, faute de quoi l'une borderait ce que l'autre a
        // enseveli.
        int start = parvisEnd(level, sx, y, fromZ);
        for (int z = start; z >= apexZ + 3; z--) {
            int here = probeTop(level, sx, y, z);
            // RIEN TANT QU'ON EST SUR LE PLAT.
            //
            // J'avais retire cette clause en croyant que le parvis se
            // terminait en face verticale faute de marches. C'etait faux, et
            // le remede pire que le mal : sans elle, la volee pose une marche
            // a CHAQUE case du parvis, une case au-dessus de lui, et couvre
            // toute sa longueur d'un lisere dente. Le parvis se traverse de
            // plain-pied ; les marches n'ont a apparaitre qu'au moment ou la
            // pyramide monte, et c'est aux rampes laterales, non a la volee,
            // de donner l'acces par le bas.
            if (here <= roof && last <= roof) {
                continue;
            }
            // on ne redescend jamais : un escalier qui plonge n'en est plus un
            int step = Math.max(last, Math.min(here + 1, last + 1));

            // UNE MARCHE SEULEMENT LA OU L'ON MONTE.
            //
            // La volee posait un bloc a chaque case, y compris sur les cases
            // ou elle ne gagne pas un pouce -- c'est-a-dire sur les TERRASSES
            // de la pyramide, qui sont plates par nature. Cela donnait des
            // paliers de deux ou trois blocs d'escalier alignes a la meme
            // hauteur : de profil, une rangee de marches plates montre ses
            // dents et se lit comme un defaut, ce qu'elle est.
            //
            // La pyramide a gradins est deja un escalier, seulement trop raide
            // pour etre gravi. Notre volee n'a donc pas a la doubler : elle n'a
            // qu'a ajouter la marche intermediaire la ou le gradin se releve.
            // Sur le plat, on marche sur la terrasse elle-meme.
            boolean climbs = step > last;
            for (int w = -1; w <= 1; w++) {
                if (climbs) {
                    // on monte vers le nord, du pied de la face sud vers le faite
                    set(level, sx + w, step, z, riser(0, -1));
                }
                // QUATRE blocs de degagement, et non trois.
                //
                // Une terrasse qui monte de deux ou trois d'un coup laissait
                // un chapeau de pierre juste au-dessus de la marche : on
                // voyait la suite de l'escalier sans pouvoir s'y glisser.
                for (int clear = 1; clear <= 4; clear++) {
                    set(level, sx + w, step + clear, z, Blocks.AIR.defaultBlockState());
                }
                // le remblai sous la marche, pour qu'elle ne flotte pas --
                // mais jamais sous le toit, sinon on comble le couloir par-dessus
                for (int fill = 1; climbs && fill <= 2; fill++) {
                    if (step - fill > roof
                            && level.getBlockState(new BlockPos(sx + w, step - fill, z)).isAir()) {
                        set(level, sx + w, step - fill, z, shrine());
                    }
                }
            }
            if (Math.floorMod(z, 9) == 0) {
                set(level, sx - 2, step + 1, z, lantern());
                set(level, sx + 2, step + 1, z, lantern());
            }
            last = step;
        }
    }

    /**
     * L'entree du tombeau, au pied de la face sud.
     *
     * Le modele n'en a pas : sa porte se trouvait dans la moitie enterree, que
     * l'on ne pose plus. On perce donc un couloir droit jusqu'a la premiere
     * salle -- ou, s'il n'y en a pas, sur une douzaine de blocs, ce qui fait au
     * moins un porche.
     */
    /**
     * Habille une paroi du couloir, mais SEULEMENT s'il y avait de la pierre.
     *
     * Le couloir traverse la masse, puis debouche : au-dela du mur qu'il perce,
     * poser un plafond revient a suspendre un bloc dans le vide, et c'est ce
     * bloc qui depassait d'une case au bout du chemin. On ne remplace donc que
     * ce qui existe -- le tunnel se chemise dans le roc et s'arrete de lui-meme
     * en debouchant, sans qu'on ait a deviner ou est la piece.
     */
    /** Une lanterne au sol, si tant est qu'il y ait un sol. */
    private static void standLantern(ServerLevel level, int x, int y, int z) {
        if (!level.getBlockState(new BlockPos(x, y - 1, z)).isAir()) {
            set(level, x, y, z, lantern());
        }
    }

    private static void lineIfSolid(ServerLevel level, int x, int y, int z, BlockState state) {
        if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) {
            set(level, x, y, z, state);
        }
    }

    private static void tombEntrance(ServerLevel level, int cx, int y, int cz, int rank,
                                     BlockPos anchor) {
        SanctuaryLedger.part("tombEntrance");
        int fromZ = cz + PYRAMID_D - PYRAMID_CZ;
        int end = fromZ;
        // On creuse une PROFONDEUR FIXE, sans chercher a s'arreter.
        //
        // Le couloir s'interrompait des qu'il trouvait de l'air deux blocs plus
        // loin, ce qu'il rencontrait au bout de quatre : au bord d'une pyramide
        // a gradins, la maconnerie ne fait qu'un ou deux blocs de haut, et tout
        // ce qui est au-dessus est du ciel. La salle et les sceaux se
        // retrouvaient donc au pied du monument, en plein air -- « les tomb
        // seal ne sont pas du tout a l'interieur ». Trente blocs mettent
        // franchement sous la masse.
        for (int depth = 0; depth < 30; depth++) {
            int z = fromZ - depth;
            for (int w = -1; w <= 1; w++) {
                for (int dy = 1; dy <= 3; dy++) {
                    set(level, cx + w, y + dy, z, Blocks.AIR.defaultBlockState());
                }
                lineIfSolid(level, cx + w, y, z, trim());
                // Le plafond du grand couloir, lui, se pose SANS condition :
                // depuis qu'il sert de parvis, une case manquante n'est plus un
                // defaut d'habillage mais un trou dans le chemin.
                set(level, cx + w, y + 4, z, shrineTrim());
            }
            // Les lanternes AU SOL, et des deux cotes.
            //
            // Posees a hauteur de tete, elles n'avaient rien sous elles et
            // paraissaient suspendues ; et d'un seul cote, elles donnaient un
            // couloir bancal. Au sol, contre les jambages, elles bordent le
            // chemin sans gener la voie centrale.
            // ... et seulement si elles ont un sol sous elles : la ou le
            // couloir debouche, il n'y a plus rien pour les porter.
            if (Math.floorMod(depth, 5) == 0) {
                standLantern(level, cx - 1, y + 1, z);
                standLantern(level, cx + 1, y + 1, z);
            }
            end = z;
        }
        vault(level, cx, y, end, rank);

        // Le couloir REPART, etroit, vers le coeur du monument.
        //
        // Il s'arretait a la salle du tresor, qui devenait un cul-de-sac : rien
        // ne disait qu'il y avait autre chose derriere, et personne n'allait
        // plus loin. Neuf blocs de plus, sur la seule ligne centrale, percent
        // le mur du fond et laissent entrevoir l'interieur -- c'est le regard
        // qui donne envie d'entrer, pas une consigne.
        for (int depth = 1; depth <= 9; depth++) {
            int z = end - 3 - depth;
            for (int dy = 1; dy <= 3; dy++) {
                set(level, cx, y + dy, z, Blocks.AIR.defaultBlockState());
            }
            lineIfSolid(level, cx, y, z, trim());
            lineIfSolid(level, cx, y + 4, z, shrineTrim());
            // Ici le couloir n'a qu'une case de large : une lanterne posee au
            // milieu le boucherait. On lui creuse donc une niche de cote.
            // On ne creuse la niche que s'il y a de la matiere a creuser :
            // au-dela du mur perce, elle n'aurait rien a mordre et sa lanterne
            // se retrouverait posee sur un bloc invente en plein vide.
            if (depth % 4 == 0
                    && !level.getBlockState(new BlockPos(cx - 1, y + 1, z)).isAir()) {
                set(level, cx - 1, y + 1, z, Blocks.AIR.defaultBlockState());
                set(level, cx - 1, y + 2, z, Blocks.AIR.defaultBlockState());
                set(level, cx - 1, y, z, trim());
                set(level, cx - 1, y + 1, z, lantern());
            }
        }

        seals(level, cx, y, cz, fromZ, end, anchor, rank);
    }

    /**
     * La salle du tresor, au bout du couloir.
     *
     * C'est elle qui donne une raison d'entrer. Depuis que l'escalier exterieur
     * mene au sommet, l'interieur n'etait plus sur le chemin de rien : on
     * prenait l'ancre sans jamais y descendre. Le tresor y est donc descendu
     * avec lui -- quatre coffres, la ou il y en avait deux la-haut.
     *
     * Elle se creuse si le couloir n'a debouche nulle part, et se contente de
     * meubler la salle du modele s'il y en avait une.
     */
    private static void vault(ServerLevel level, int cx, int y, int z, int rank) {
        SanctuaryLedger.part("vault");
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    set(level, cx + dx, y + dy, z + dz, Blocks.AIR.defaultBlockState());
                }
                boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                set(level, cx + dx, y, z + dz, rim ? shrineTrim() : trim());
                set(level, cx + dx, y + 5, z + dz, shrine());
            }
        }
        // les quatre piliers d'angle, et leur lumiere
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int dy = 1; dy <= 4; dy++) {
                    set(level, cx + sx * 3, y + dy, z + sz * 3, shrine());
                }
                set(level, cx + sx * 2, y + 4, z + sz * 2, lantern());
            }
        }
        // quatre coffres, un par mur : de quoi valoir la descente
        lootChest(level, cx - 2, y + 1, z, sanctuaryTable(rank));
        lootChest(level, cx + 2, y + 1, z, sanctuaryTable(rank));
        lootChest(level, cx, y + 1, z - 2, sanctuaryTable(rank));
        lootChest(level, cx, y + 1, z + 2, sanctuaryTable(rank));
    }

    /**
     * Les trois sceaux, semes le long du couloir.
     *
     * Ils ne sont PAS tous dans la salle du fond : ce serait un seul detour, et
     * l'on n'aurait rien visite. Espaces le long du chemin, ils obligent a le
     * parcourir en entier, et le dernier attend dans la salle -- la ou sont
     * aussi les coffres, ce qui recompense d'etre alle jusqu'au bout.
     */
    /** Ou les trois sceaux sont tombes, pour le compte rendu de la commande. */
    private static String sealReport = "";

    private static void seals(ServerLevel level, int cx, int y, int cz, int fromZ,
                              int endZ, BlockPos anchor, int rank) {
        SanctuaryLedger.part("seals");
        // TROIS LIEUX MESURES, et non plus devines.
        //
        // Sept placements de suite ont echoue pour une seule raison : je posais
        // des sceaux dans un batiment que je n'ai pas dessine, en supposant ou
        // etaient ses salles. La sonde cherchait des pieces inexistantes, la
        // chambre haute se murait, la cave manquait de fond, la galerie a perce
        // le flanc. Chaque fois, un pari.
        //
        // Les quatre .nbt de Cataclysm ont donc ete decodes bloc par bloc, et
        // ils dementent trois de mes suppositions :
        //   - il n'y a PAS de pilier plein au centre. C'est une cheminee 5x5
        //     dont les quatre montants sont pleins et dont le noyau 3x3 est
        //     CREUX de y+1 a y+36 -- un puits vertical deja perce, et qui tombe
        //     pile sur l'axe de notre couloir (x cx-1..cx+1, z cz-4..cz-2) ;
        //   - la pyramide a un veritable etage a y+6, avec ses couloirs et ses
        //     coffres, que le modele relie a la grande salle basse ;
        //   - le couloir du tombeau n'est ceint de maconnerie pleine qu'a
        //     partir de sa treizieme case : avant, on est encore dans le gradin,
        //     et c'est ce qui a fait deboucher une alcove hors du flanc.
        java.util.List<BlockPos> placed = new java.util.ArrayList<>();
        sealReport = "";

        placed.add(doorwaySeal(level, cx, y, fromZ));      // 1. l'entree
        placed.add(upperCorridor(level, cx, y, cz));       // 2. l'etage, au coffre
        placed.add(chimneySeal(level, cx, y, cz));         // 3. l'etage, au pilier

        StringBuilder report = new StringBuilder();
        String[] labels = {"entree", "coffre", "pilier"};
        for (int i = 0; i < placed.size(); i++) {
            BlockPos p = placed.get(i);
            report.append(i == 0 ? "" : " | ").append(labels[i]).append(' ')
                    .append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ());
        }
        sealReport = report.toString();

        SanctuarySeals.register(anchor, placed);
    }

    /**
     * Une galerie laterale et la salle ou elle mene.
     *
     * C'est ce qui manquait depuis le debut : de quoi TOURNER. Un tombeau qui
     * n'est qu'un tunnel ne peut pas cacher trois choses, parce qu'on les voit
     * toutes en enfilade des l'entree.
     *
     * La galerie perce la paroi du couloir, court une dizaine de blocs et
     * debouche sur une piece de cinq sur cinq. Le sceau y attend contre son
     * coffre : on ne s'arrete pas devant un sceau, on s'arrete devant un
     * coffre, et le sceau se trouve dans le meme regard.
     */
    /**
     * 1. LE SCEAU DE L'ENTREE, dans le couloir du tombeau.
     *
     * A la treizieme case, et pas avant : c'est la premiere ou la maconnerie du
     * modele enveloppe le couloir de tous cotes. Plus pres du porche on est
     * encore dans le gradin, et une alcove creusee la ressortait du flanc.
     */
    private static BlockPos doorwaySeal(ServerLevel level, int cx, int y, int fromZ) {
        SanctuaryLedger.part("doorwaySeal");
        int z = fromZ - 13;
        set(level, cx - 1, y + 3, z, lantern());
        return freeSeal(level, cx - 1, y + 1, z);
    }

    /**
     * 2. L'ETAGE DE LA PYRAMIDE, contre un coffre du modele.
     *
     * Le modele a son propre etage a y+6, avec ses couloirs et ses coffres, et
     * notre couloir debouche deja dans sa grande salle basse. Entre le plafond
     * de cette salle et le plancher de l'etage il n'y a que DEUX assises : on y
     * taille une cage d'escalier, et tout le reste du trajet appartient au
     * batiment. Le sceau attend en diagonale d'un coffre du modele.
     */
    private static BlockPos upperCorridor(ServerLevel level, int cx, int y, int cz) {
        SanctuaryLedger.part("upperCorridor");
        // Quatre marches, larges de trois. La premiere est en y+1 et non en
        // y+2 : le sol de la salle etant en y+1, on aurait bute dessus.
        int[][] steps = {{1, 26}, {2, 25}, {3, 24}, {4, 23}, {5, 22}};
        for (int[] st : steps) {
            for (int w = -3; w <= -1; w++) {
                set(level, cx + w, y + st[0], cz + st[1], riser(0, -1));
                for (int clear = 1; clear <= 3; clear++) {
                    set(level, cx + w, y + st[0] + clear, cz + st[1],
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
        // le palier, de plain-pied sur l'etage
        for (int w = -3; w <= -1; w++) {
            set(level, cx + w, y + 5, cz + 21, trim());
            for (int clear = 1; clear <= 3; clear++) {
                set(level, cx + w, y + 5 + clear, cz + 21, Blocks.AIR.defaultBlockState());
            }
        }
        set(level, cx - 2, y + 8, cz + 22, lantern());
        return freeSeal(level, cx - 5, y + 6, cz + 13);
    }

    /**
     * 3. L'ETAGE, CONTRE LE PILIER CENTRAL.
     *
     * Le centre du monument n'est pas un pilier plein mais une cheminee de cinq
     * sur cinq : quatre montants de pierre et, entre eux, un noyau creux de
     * trois sur trois qui monte d'un trait jusqu'a trente-six blocs. On n'a donc
     * rien a percer verticalement -- le puits existe deja, il suffit d'y arriver
     * et d'y poser des echelons.
     *
     * Une galerie prolonge notre couloir jusqu'a son pied, les echelons montent
     * au quinzieme niveau, et l'on debouche contre le montant nord. C'est le
     * seul des trois qui demande de grimper, et c'est voulu.
     */
    private static BlockPos chimneySeal(ServerLevel level, int cx, int y, int cz) {
        SanctuaryLedger.part("chimneySeal");
        // la galerie, du fond du tresor au pied de la cheminee
        for (int z = cz + 14; z >= cz - 2; z--) {
            for (int w = -1; w <= 1; w++) {
                set(level, cx + w, y, z, trim());
                for (int dy = 1; dy <= 3; dy++) {
                    set(level, cx + w, y + dy, z, Blocks.AIR.defaultBlockState());
                }
                set(level, cx + w, y + 4, z, shrineTrim());
            }
            if (Math.floorMod(z, 6) == 0) {
                standLantern(level, cx - 1, y + 1, z);
                standLantern(level, cx + 1, y + 1, z);
            }
        }

        // les echelons, dans le noyau creux, adosses au montant nord
        for (int h = y + 1; h <= y + 16; h++) {
            set(level, cx, h, cz - 3, Blocks.AIR.defaultBlockState());
            if (level.getBlockState(new BlockPos(cx, h, cz - 4)).isAir()) {
                set(level, cx, h, cz - 4, shrine());
            }
            set(level, cx, h, cz - 3, Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING,
                            Direction.SOUTH));
        }

        // Le palier, en EPARGNANT la colonne de l'echelle.
        //
        // Le premier jet posait son plancher de z cz-3 a cz-6 : cz-3 EST le
        // puits, si bien qu'un bloc plein atterrissait au milieu des echelons
        // et arretait la montee net. Le plancher commence donc un bloc plus
        // loin, et le puits reste creux jusqu'en haut -- on grimpe, puis on
        // enjambe vers le nord.
        for (int z = cz - 4; z >= cz - 6; z--) {
            for (int w = 0; w <= 1; w++) {
                set(level, cx + w, y + 15, z, trim());
            }
        }
        for (int z = cz - 3; z >= cz - 6; z--) {
            for (int w = 0; w <= 1; w++) {
                for (int dy = 16; dy <= 18; dy++) {
                    set(level, cx + w, y + dy, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
        // et l'on degage au-dessus du dernier echelon, faute de quoi on
        // arriverait la tete dans la pierre
        for (int dy = 17; dy <= 18; dy++) {
            set(level, cx, y + dy, cz - 3, Blocks.AIR.defaultBlockState());
        }
        set(level, cx, y + 17, cz - 5, lantern());
        return freeSeal(level, cx + 1, y + 16, cz - 6);
    }

    /**
     * Un sceau pose a meme le sol, dans une piece deja ouverte.
     */
    private static BlockPos freeSeal(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, ModBlocks.TOMB_SEAL.get().defaultBlockState(), 3);
        return pos;
    }

    /**
     * L'escalier qui monte dans cette direction.
     *
     * On posait des blocs PLEINS partout ou l'on monte -- dans les tours, sur
     * les rampes du rempart, sur le flanc de la pyramide. Il fallait donc
     * sauter chaque marche, ce qui est penible sur quarante blocs de haut. Un
     * bloc d'escalier se gravit tout seul.
     *
     * Le sens : un escalier se pose en regardant dans la direction ou l'on
     * MONTE, et c'est cette meme direction qu'il faut lui donner ici.
     */
    private static BlockState riser(int dx, int dz) {
        Direction facing = dx > 0 ? Direction.EAST
                : dx < 0 ? Direction.WEST
                : dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return stair(ModBlocks.POLISHED_GANGUE_STAIRS.get(), facing);
    }

    // ----------------------------------------------------------- outillage

    private static BlockState stair(Block block, Direction facing) {
        return block.defaultBlockState()
                .setValue(StairBlock.FACING, facing)
                .setValue(StairBlock.HALF, Half.BOTTOM);
    }

    /**
     * Pose un MURET, en prevenant ses voisins.
     *
     * Tout le reste est pose avec le drapeau 2, qui n'envoie aucune mise a
     * jour de voisinage -- indispensable quand on ecrit cent mille blocs, sinon
     * les cascades coutent plus cher que la pose. Mais un muret calcule sa
     * FORME a partir de ses voisins : pose ainsi, il reste dans son etat par
     * defaut, c'est-a-dire un poteau isole, et ne se relie jamais a celui qu'on
     * pose juste apres. D'ou ces alignements de piquets qui ne se touchent pas.
     *
     * Ils sont assez peu nombreux -- le parapet et les mains courantes -- pour
     * qu'on puisse leur payer le drapeau 3.
     */
    /**
     * Les murets poses pendant la construction, a relier a la fin.
     *
     * Le drapeau 3 ne suffisait pas, et c'est logique : un muret calcule sa
     * forme au moment ou on le pose, d'apres les voisins qui existent DEJA. Le
     * suivant n'est pas encore la. Prevenir les voisins ne les fait pas
     * recalculer leur propre forme -- seule une nouvelle pose le fait.
     *
     * On les note donc au passage, et on repasse une fois tout bati pour
     * demander a chacun sa forme definitive, quand tous ses voisins sont en
     * place.
     */
    private static final java.util.List<BlockPos> walls = new java.util.ArrayList<>();

    private static void setWall(ServerLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        level.setBlock(pos, merlon(), 2);
        SanctuaryLedger.record(x, y, z, merlon());
        walls.add(pos);
    }

    /** Le second passage : chaque muret prend sa forme, voisins connus. */
    private static void linkWalls(ServerLevel level) {
        for (BlockPos pos : walls) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(ModBlocks.GANGUE_BRICK_WALL.get())) {
                continue;                      // casse ou recouvert depuis
            }
            level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), 2);
        }
        walls.clear();
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        // drapeau 2 : on previent le client sans declencher de mise a jour de
        // voisinage -- sur cent mille blocs, les cascades couteraient bien plus
        // cher que la pose elle-meme
        level.setBlock(new BlockPos(x, y, z), state, 2);
        // et l'on note QUI vient de poser ce bloc : c'est le seul point de
        // passage des poses, donc le seul endroit ou l'on ne peut oublier
        // personne -- une routine ajoutee demain sera enregistree sans qu'on
        // ait a y penser
        SanctuaryLedger.record(x, y, z, state);
    }
}
