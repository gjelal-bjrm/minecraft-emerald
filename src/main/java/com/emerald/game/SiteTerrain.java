package com.emerald.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * LE TERRAIN AUTOUR D'UN CHANTIER (cahier §92) : ou le poser, et comment le raccorder.
 *
 * « Ils ne sont pas vraiment integres dans le decor. Ils ecrasent le decor, ca ne fait pas du
 * tout naturel, ce n'est pas beau » (le joueur, 24 sept., photos des sanctuaires). Le chantier
 * aplanissait un carre : la colline tranchee a la verticale, le creux comble d'un socle de
 * briques a bords droits. Choix du joueur : un BON SITE et des TALUS NATURELS -- pour les
 * sanctuaires et pour l'arene du boss.
 *
 * 1. LE SITE ({@link #flattest}) : autour de la place prevue, l'endroit le plus plat. Les
 *    hauteurs se lisent au GENERATEUR, sans generer un seul troncon ; l'eau est evitee, et la ou
 *    il y en a, c'est sa SURFACE qui compte ; le sol du chantier se pose a la hauteur MEDIANE du
 *    terrain sous son emprise, pour trancher autant qu'on comble.
 * 2. LE RACCORD ({@link Blend}) : hors de l'emprise, le terrain est remodele en pente douce, de
 *    la hauteur du chantier au pied de ses murs jusqu'au terrain d'origine plus loin. La largeur
 *    de la pente suit la denivelee -- un sur deux au plus, dans la limite de la bande --, et le
 *    bord ondule. Chaque colonne garde ses propres matieres : son dessus (herbe, sable, neige),
 *    sa sous-couche, sa roche, et ce qui y poussait. Sur l'eau, la pente descend jusqu'a sa
 *    SURFACE : seule la berge qui en sort est comblee, des matieres de la terre ferme d'alentour ;
 *    au-dela, les lacs et les ruisseaux restent tels quels.
 */
public final class SiteTerrain {

    /** Les troncons d'un raccord, tenus le temps de le faire. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("arcencium_raccord",
            Comparator.comparingLong(ChunkPos::toLong));
    /** Troncons tenus a la fois par un raccord. */
    private static final int BATCH = 12;
    /** La part d'eau sous l'emprise au-dela de laquelle on cherche plus loin. */
    private static final double TOO_WET = 0.2;
    /** Le rayon de recherche le plus large : au-dela, on garde le site le moins mouille. */
    private static final int SEARCH_MAX = 256;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SiteTerrain.class);

    private SiteTerrain() {
    }

    /** Un site : son centre, et la hauteur du bloc de sol (la mediane du terrain sous l'emprise). */
    public record Site(int x, int z, int y) {
        public BlockPos ground() {
            return new BlockPos(this.x, this.y, this.z);
        }
    }

    /**
     * L'endroit le plus plat autour de (x0, z0), pour une emprise carree de demi-cote `half`,
     * dans un rayon de recherche `search`. Une grille de hauteurs tous les trente-deux blocs,
     * lue au generateur ; chaque candidat est note par l'ecart moyen a sa mediane, l'eau sous son
     * emprise, et un peu par sa distance a la place prevue.
     *
     * UNE LECTURE PAR POINT, EN PARALLELE. Avec les mods de terrain, une hauteur lue au generateur
     * coute deux millisecondes et demie : deux lectures par point tous les seize blocs figeaient
     * le serveur 2,6 s par site (24 sept.), trois fois au debut d'une partie. L'eau se deduit
     * desormais du niveau de la mer, la grille est deux fois plus lache, et les points se lisent
     * sur tous les coeurs a la fois -- le generateur le permet, il sert deja aux fils de la
     * generation.
     *
     * SUR L'EAU, LA SURFACE. Un point mouille comptait pour le fond du lac : un chantier au bord
     * d'un lac se posait sous le niveau de l'eau, et l'arene des photos a ete batie noyee (24
     * sept.). Il compte desormais pour la surface : le chantier se pose au moins a fleur d'eau.
     *
     * ET PLUS LOIN SI TOUT EST MOUILLE. A quarante-huit blocs d'un point en mer, tous les sites
     * etaient dans l'eau : l'arene s'est dressee au milieu des flots, sur un anneau de sable.
     * Tant que le meilleur site a plus d'un cinquieme d'eau sous son emprise, le rayon double,
     * jusqu'a 256 blocs ; au-dela, on garde le moins mouille. Une recherche nulle (la commande
     * d'essai) garde sa place.
     *
     * @param allowed les centres permis (la distance aux autres chantiers), ou null
     */
    public static Site flattest(ServerLevel level, int x0, int z0, int half, int search,
                                @Nullable BiPredicate<Integer, Integer> allowed) {
        long start = System.nanoTime();
        Heights heights = new Heights(level, x0, z0);
        int reads = 0;
        int span = half / Heights.STEP;
        int count = (2 * span + 1) * (2 * span + 1);
        int[] samples = new int[count];
        int[] sorted = new int[count];
        Site best = null;
        double bestScore = Double.MAX_VALUE;
        double bestWet = 1.0;
        int radius = search;
        while (true) {
            int r = radius / Heights.STEP;
            reads += heights.ensure(r + span);
            for (int ci = -r; ci <= r; ci++) {
                for (int cj = -r; cj <= r; cj++) {
                    int x = x0 + ci * Heights.STEP;
                    int z = z0 + cj * Heights.STEP;
                    if (allowed != null && !allowed.test(x, z)) {
                        continue;
                    }
                    int k = 0;
                    int soaked = 0;
                    for (int i = ci - span; i <= ci + span; i++) {
                        for (int j = cj - span; j <= cj + span; j++) {
                            int sample = heights.at(i, j);
                            samples[k++] = sample >> 1;
                            soaked += sample & 1;
                        }
                    }
                    System.arraycopy(samples, 0, sorted, 0, count);
                    Arrays.sort(sorted);
                    int median = sorted[count / 2];
                    double spread = 0;
                    for (k = 0; k < count; k++) {
                        spread += Math.abs(samples[k] - median);
                    }
                    double wet = (double) soaked / count;
                    double score = spread / count + 40.0 * wet + Math.hypot(x - x0, z - z0) / 48.0;
                    if (score < bestScore) {
                        bestScore = score;
                        bestWet = wet;
                        best = new Site(x, z, median);
                    }
                }
            }
            if (best != null && bestWet <= TOO_WET || radius == 0 || radius >= SEARCH_MAX) {
                break;
            }
            radius = Math.min(SEARCH_MAX, radius * 2);
        }
        if (best == null) {
            best = new Site(x0, z0, heights.at(0, 0) >> 1);
        }
        LOGGER.info("Site le plus plat pres de ({}, {}) : ({}, {}) au sol {}, a {} blocs, {} % d'eau ; "
                        + "cherche a {} blocs, {} hauteurs lues en {} ms",
                x0, z0, best.x(), best.z(), best.y(), (int) Math.hypot(best.x() - x0, best.z() - z0),
                Math.round(bestWet * 100), radius, reads, (System.nanoTime() - start) / 1_000_000);
        return best;
    }

    /**
     * Les hauteurs lues au generateur, tous les trente-deux blocs autour d'un point, une seule
     * fois chacune (la recherche qui s'elargit relit les memes), et par paquets en parallele.
     */
    private static final class Heights {
        static final int STEP = 32;
        private final ServerLevel level;
        private final ChunkGenerator generator;
        private final RandomState random;
        private final int seaLevel;
        private final int x0;
        private final int z0;
        private final java.util.Map<Long, Integer> read = new java.util.HashMap<>();

        Heights(ServerLevel level, int x0, int z0) {
            this.level = level;
            this.generator = level.getChunkSource().getGenerator();
            this.random = level.getChunkSource().randomState();
            this.seaLevel = level.getSeaLevel();
            this.x0 = x0;
            this.z0 = z0;
        }

        /** Lit, sur tous les coeurs, les points a moins de g pas du centre qui manquent encore ; rend leur nombre. */
        int ensure(int g) {
            List<Long> missing = new ArrayList<>();
            for (int i = -g; i <= g; i++) {
                for (int j = -g; j <= g; j++) {
                    long key = ChunkPos.asLong(i, j);
                    if (!this.read.containsKey(key)) {
                        missing.add(key);
                    }
                }
            }
            this.read.putAll(missing.parallelStream().collect(
                    java.util.stream.Collectors.toMap(key -> key, this::sample)));
            return missing.size();
        }

        /** Le sol au point (i, j) de la grille (lu par ensure), fois deux, plus un s'il est sous l'eau. */
        int at(int i, int j) {
            return this.read.get(ChunkPos.asLong(i, j));
        }

        /**
         * Sous le niveau de la mer, le generateur remplit d'eau : le point est mouille, et c'est
         * la surface de l'eau qui compte.
         */
        private int sample(long key) {
            int x = this.x0 + ChunkPos.getX(key) * STEP;
            int z = this.z0 + ChunkPos.getZ(key) * STEP;
            int floor = this.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.level, this.random);
            boolean wet = floor < this.seaLevel;
            return ((wet ? this.seaLevel : floor) - 1) * 2 + (wet ? 1 : 0);
        }
    }

    /**
     * Le raccord d'un chantier au terrain, par bouchees : une grille autour de l'emprise, la
     * distance de chaque colonne a l'emprise, puis colonne par colonne la pente, comblee ou
     * tranchee dans les matieres du lieu. Il tient ses troncons par ticket, douze a la fois, et
     * ne demande que ceux ou il a quelque chose a faire.
     */
    public static final class Blend {
        private final ServerLevel level;
        private final int x0;
        private final int z0;
        private final int width;
        private final int depth;
        private final BitSet footprint;
        /** La distance a l'emprise en tiers de bloc (chanfrein 3-4), ou MAX_VALUE. */
        private final int[] distance;
        private final int groundY;
        private final int band;
        /** Dans l'emprise : tout est vide au-dessus de cette hauteur, plein dessous ; -1 : on n'y touche pas. */
        private final int clearFrom;
        private final List<ChunkPos> chunks = new ArrayList<>();
        /** Le paquet de troncons en cours : [debut, fin[ dans la liste, tenus par ticket. */
        private int batchFrom;
        private int batchTo;
        private boolean held;
        /** La colonne suivante dans le paquet : index dans le paquet, en seize fois seize par troncon. */
        private int cursor;
        private int moved;
        /** Les dessus de la terre ferme rencontres, comptes ; le plus courant fait les berges. */
        private final java.util.Map<BlockState, Integer> landCount = new java.util.HashMap<>();
        private final java.util.Map<BlockState, Column> landSample = new java.util.HashMap<>();
        @Nullable
        private Column land;
        /** Les colonnes du paquet lues a la premiere passe, pour la seconde. */
        private final Column[] columns = new Column[BATCH * 256];

        /**
         * @param x0        le coin nord-ouest de la grille, en coordonnees du monde
         * @param footprint les colonnes de l'emprise, index gx + gz * width
         * @param groundY   la hauteur du sol au pied de l'emprise, ou la pente commence
         * @param band      la largeur maximale de la pente, en blocs
         * @param clearFrom dans l'emprise, la hauteur au-dessus de laquelle tout est vide (-1 : rien)
         */
        public Blend(ServerLevel level, int x0, int z0, int width, int depth, BitSet footprint,
                     int groundY, int band, int clearFrom) {
            this.level = level;
            this.x0 = x0;
            this.z0 = z0;
            this.width = width;
            this.depth = depth;
            this.footprint = footprint;
            this.groundY = groundY;
            this.band = band;
            this.clearFrom = clearFrom;
            this.distance = chamfer(width, depth, footprint);
            for (int cx = x0 >> 4; cx <= (x0 + width - 1) >> 4; cx++) {
                for (int cz = z0 >> 4; cz <= (z0 + depth - 1) >> 4; cz++) {
                    if (useful(cx, cz)) {
                        this.chunks.add(new ChunkPos(cx, cz));
                    }
                }
            }
        }

        /** Ce troncon a-t-il une colonne a raccorder, ou de l'emprise a fonder ? Sinon on ne le demande pas. */
        private boolean useful(int cx, int cz) {
            for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                    int gx = x - this.x0;
                    int gz = z - this.z0;
                    if (gx < 0 || gz < 0 || gx >= this.width || gz >= this.depth) {
                        continue;
                    }
                    int i = gx + gz * this.width;
                    if (this.footprint.get(i) ? this.clearFrom >= 0 : this.distance[i] <= this.band * 3) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** Les colonnes remodelees : pour le journal. */
        public int moved() {
            return this.moved;
        }

        /**
         * Une bouchee, jusqu'a l'echeance.
         *
         * PAR PAQUETS DE TRONCONS. Tenir d'un coup les quatre cents troncons d'un raccord les
         * faisait tous generer en meme temps : la machine saturait, et le client de photos a
         * cesse de repondre (24 sept.) -- un joueur, lui, aurait senti le jeu ramer au debut de
         * la partie. On en tient douze a la fois : on les demande, on attend qu'ils soient la,
         * on raccorde leurs colonnes, on les rend, et l'on passe aux douze suivants.
         *
         * @return vrai quand tout est fait (les tickets sont alors rendus)
         */
        public boolean step(long deadline) {
            while (this.batchFrom < this.chunks.size()) {
                if (!this.held) {
                    this.batchTo = Math.min(this.chunks.size(), this.batchFrom + BATCH);
                    for (int i = this.batchFrom; i < this.batchTo; i++) {
                        ChunkPos pos = this.chunks.get(i);
                        this.level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
                    }
                    this.held = true;
                    this.cursor = 0;
                    return false;
                }
                for (int i = this.batchFrom; i < this.batchTo; i++) {
                    ChunkPos pos = this.chunks.get(i);
                    if (this.level.getChunkSource().getChunkNow(pos.x, pos.z) == null) {
                        return false;             // la generation travaille : on repassera
                    }
                }
                // DEUX PASSES : la premiere lit les colonnes du paquet et compte la terre ferme,
                // la seconde remodele -- la berge du premier troncon connait deja la terre de
                // tout le paquet
                int cells = (this.batchTo - this.batchFrom) * 256;
                while (this.cursor < 2 * cells) {
                    if ((this.cursor & 31) == 0 && System.nanoTime() > deadline) {
                        return false;
                    }
                    boolean survey = this.cursor < cells;
                    int k = survey ? this.cursor : this.cursor - cells;
                    this.cursor++;
                    ChunkPos pos = this.chunks.get(this.batchFrom + k / 256);
                    int gx = pos.getMinBlockX() + (k & 15) - this.x0;
                    int gz = pos.getMinBlockZ() + ((k >> 4) & 15) - this.z0;
                    if (gx >= 0 && gz >= 0 && gx < this.width && gz < this.depth) {
                        if (survey) {
                            this.columns[k] = survey(gx + gz * this.width);
                        } else {
                            column(gx + gz * this.width, this.columns[k]);
                        }
                    }
                }
                release();
                this.batchFrom = this.batchTo;
            }
            return true;
        }

        /** Rend les troncons du paquet : a la fin de chacun, ou si le chantier est abandonne. */
        public void release() {
            if (this.held) {
                for (int i = this.batchFrom; i < this.batchTo; i++) {
                    ChunkPos pos = this.chunks.get(i);
                    this.level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
                }
                this.held = false;
            }
        }

        /** Premiere passe : la colonne lue et, si elle est seche, comptee ; null si le raccord n'y touche pas. */
        @Nullable
        private Column survey(int index) {
            if (this.footprint.get(index) || this.distance[index] > this.band * 3) {
                return null;
            }
            Column c = read(this.x0 + index % this.width, this.z0 + index / this.width);
            if (c != null && !c.wet) {
                countLand(c);
            }
            return c;
        }

        /** Seconde passe : la colonne remodelee, d'apres sa lecture (chaque colonne ne touche qu'a elle). */
        private void column(int index, @Nullable Column c) {
            int gx = index % this.width;
            int gz = index / this.width;
            int x = this.x0 + gx;
            int z = this.z0 + gz;
            if (this.footprint.get(index)) {
                if (this.clearFrom >= 0) {
                    found(x, z);
                }
                return;
            }
            double dist = this.distance[index] / 3.0;
            if (dist > this.band) {
                return;
            }
            // SUR L'EAU, LA PENTE VISE LA SURFACE, pas le fond. Visant le fond d'un ruisseau, elle
            // portait bien plus loin que celle de ses rives : le lit se comblait sur une longue
            // langue de terre, une rigole a parois droites entaillait le talus devant une tour
            // des Sables (24 sept.). Desormais seule la berge qui sort de l'eau est comblee ; le
            // reste du lac, du ruisseau, reste tel quel.
            if (c == null || c.wet && c.water >= this.groundY) {
                return;                           // on ne creuse pas sous l'eau
            }
            int surface = c.wet ? c.water : c.h0;
            int need = Math.abs(surface - this.groundY);
            if (need == 0) {
                return;
            }
            // la pente : un sur deux au plus, six blocs au moins, et le bord ondule -- mais jamais
            // au-dela de la bande, sans quoi le terrain d'origine reprendrait par une marche
            double reach = Math.min(this.band, Math.max(6.0, need * 2.0) * (0.8 + 0.4 * noise(x, z, 37)));
            double s = smooth(Math.min(1.0, dist / reach));
            if (s >= 1.0) {
                return;
            }
            double wobble = s > 0.08 && s < 0.92 ? (noise(x, z, 91) - 0.5) * 2.0 : 0.0;
            int t = (int) Math.round(this.groundY + (surface - this.groundY) * s + wobble);
            if (c.wet) {
                if (t <= c.water) {
                    return;                       // encore sous l'eau : on n'y touche pas
                }
                // UNE BERGE QUI SORT DE L'EAU prend les matieres de la terre ferme. Comblee avec
                // celles du fond -- gravier, argile, pierre --, elle faisait des gradins gris au
                // bord du lac du Givre (24 sept.).
                fill(x, z, this.land != null ? c.clad(this.land) : c, t);
            } else if (t > c.h0) {
                fill(x, z, c, t);
            } else if (t < c.h0) {
                cut(x, z, c, t);
            } else {
                return;
            }
            this.moved++;
        }

        /** Dans l'emprise (l'arene) : vide au-dessus de clearFrom, plein dessous jusqu'au terrain. */
        private void found(int x, int z) {
            Column c = read(x, z);
            if (c == null) {
                return;
            }
            clearAbove(x, z, this.clearFrom);
            for (int y = c.h0 + 1; y <= this.clearFrom; y++) {
                put(x, y, z, c.deep);
            }
            this.moved++;
        }

        private void fill(int x, int z, Column c, int t) {
            put(x, c.h0, z, c.sub);               // l'herbe d'avant ne reste pas enterree
            for (int y = c.h0 + 1; y <= t; y++) {
                put(x, y, z, y == t ? c.top : y >= t - 3 ? c.sub : c.deep);
            }
            dress(x, t, z, c);
        }

        private void cut(int x, int z, Column c, int t) {
            clearAbove(x, z, t);
            put(x, t, z, c.top);
            dress(x, t, z, c);
        }

        /** Compte le dessus d'une colonne seche ; le plus courant devient la terre des berges. */
        private void countLand(Column c) {
            int count = this.landCount.merge(c.top, 1, Integer::sum);
            this.landSample.putIfAbsent(c.top, c);
            if (this.land == null || count > this.landCount.getOrDefault(this.land.top, 0)) {
                Column sample = this.landSample.get(c.top);
                // la neige qui la couvrait, oui ; ses fleurs et ses herbes, non
                BlockState snow = sample.deco != null && sample.deco.is(Blocks.SNOW) ? sample.deco : null;
                this.land = new Column(sample.h0, sample.top, sample.sub, sample.deep, snow, false,
                        Integer.MIN_VALUE);
            }
        }

        /** Ce qui poussait la, ou un peu d'herbe sur l'herbe. */
        private void dress(int x, int t, int z, Column c) {
            BlockPos above = new BlockPos(x, t + 1, z);
            if (!this.level.getBlockState(above).isAir()) {
                return;
            }
            if (c.deco != null) {
                put(x, t + 1, z, c.deco);
            } else if (c.top.is(Blocks.GRASS_BLOCK) && noise(x, z, 13) < 0.2) {
                put(x, t + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
            }
        }

        private void clearAbove(int x, int z, int from) {
            int top = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + 2;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = from + 1; y <= Math.min(top, from + 200); y++) {
                BlockState existing = this.level.getBlockState(pos.set(x, y, z));
                if (!existing.isAir()) {
                    if (existing.hasBlockEntity()) {
                        this.level.removeBlockEntity(pos);
                    }
                    this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
            }
        }

        private void put(int x, int y, int z, BlockState state) {
            this.level.setBlock(new BlockPos(x, y, z), state, 2 | 16);
        }

        /** Le sol vrai d'une colonne, sous les arbres et l'eau, et ses matieres. */
        @Nullable
        private Column read(int x, int z) {
            int start = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            int floor = Math.max(this.level.getMinBuildHeight(), start - 96);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, start, z);
            int water = Integer.MIN_VALUE;
            while (pos.getY() > floor) {
                BlockState state = this.level.getBlockState(pos);
                if (water == Integer.MIN_VALUE && !state.getFluidState().isEmpty()) {
                    water = pos.getY();
                }
                if (isGround(state)) {
                    break;
                }
                pos.move(0, -1, 0);
            }
            if (pos.getY() <= floor) {
                return null;
            }
            int h0 = pos.getY();
            BlockState top = this.level.getBlockState(pos);
            BlockState sub = top;
            for (int d = 1; d <= 4; d++) {
                BlockState below = this.level.getBlockState(pos.set(x, h0 - d, z));
                if (isGround(below) && below.getBlock() != top.getBlock()) {
                    sub = below;
                    break;
                }
            }
            BlockState deep = this.level.getBlockState(pos.set(x, h0 - 6, z));
            if (!isGround(deep)) {
                deep = sub;
            }
            BlockState deco = this.level.getBlockState(pos.set(x, h0 + 1, z));
            if (deco.isAir() || !deco.canBeReplaced() || !deco.getFluidState().isEmpty()) {
                deco = null;
            }
            return new Column(h0, top, sub, deep, deco, water != Integer.MIN_VALUE, water);
        }
    }

    /** Une colonne lue : son sol, ses matieres, et le haut de l'eau qui la couvre (MIN_VALUE : seche). */
    private record Column(int h0, BlockState top, BlockState sub, BlockState deep,
                          @Nullable BlockState deco, boolean wet, int water) {
        /** La meme colonne, habillee des matieres d'une autre. */
        Column clad(Column materials) {
            return new Column(this.h0, materials.top, materials.sub, materials.deep, materials.deco,
                    this.wet, this.water);
        }
    }

    /** Du sol : plein, sec, ni feuillage, ni tronc, ni branche d'arbre dynamique, ni plante. */
    private static boolean isGround(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()
                || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.blocksMotion()) {
            return false;
        }
        return !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().startsWith("dynamictrees");
    }

    /** La distance a l'emprise, en tiers de bloc : chanfrein 3-4, deux passes. */
    private static int[] chamfer(int width, int depth, BitSet footprint) {
        int[] d = new int[width * depth];
        for (int i = 0; i < d.length; i++) {
            d[i] = footprint.get(i) ? 0 : Integer.MAX_VALUE / 2;
        }
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int i = x + z * width;
                if (x > 0) {
                    d[i] = Math.min(d[i], d[i - 1] + 3);
                }
                if (z > 0) {
                    d[i] = Math.min(d[i], d[i - width] + 3);
                    if (x > 0) {
                        d[i] = Math.min(d[i], d[i - width - 1] + 4);
                    }
                    if (x < width - 1) {
                        d[i] = Math.min(d[i], d[i - width + 1] + 4);
                    }
                }
            }
        }
        for (int z = depth - 1; z >= 0; z--) {
            for (int x = width - 1; x >= 0; x--) {
                int i = x + z * width;
                if (x < width - 1) {
                    d[i] = Math.min(d[i], d[i + 1] + 3);
                }
                if (z < depth - 1) {
                    d[i] = Math.min(d[i], d[i + width] + 3);
                    if (x < width - 1) {
                        d[i] = Math.min(d[i], d[i + width + 1] + 4);
                    }
                    if (x > 0) {
                        d[i] = Math.min(d[i], d[i + width - 1] + 4);
                    }
                }
            }
        }
        return d;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** Un bruit de valeur doux (maille de douze blocs), entre 0 et 1, stable pour une graine. */
    private static double noise(int x, int z, int seed) {
        double fx = x / 12.0;
        double fz = z / 12.0;
        int ix = (int) Math.floor(fx);
        int iz = (int) Math.floor(fz);
        double tx = smooth(fx - ix);
        double tz = smooth(fz - iz);
        double a = hash(ix, iz, seed);
        double b = hash(ix + 1, iz, seed);
        double c = hash(ix, iz + 1, seed);
        double d = hash(ix + 1, iz + 1, seed);
        return (a + (b - a) * tx) + ((c + (d - c) * tx) - (a + (b - a) * tx)) * tz;
    }

    private static double hash(int x, int z, int seed) {
        long h = x * 73856093L ^ z * 19349663L ^ seed * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0;
    }
}
