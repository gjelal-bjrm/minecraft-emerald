package com.emerald.jak;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Pose un quartier, etage par etage, sans figer le serveur.
 *
 * Le port de Haven fait sept cent mille blocs. Les poser d'un bloc sur le tick
 * de la commande gelerait le jeu une minute entiere et ferait expirer les
 * clients ; on etale donc la pose sur plusieurs secondes, par paquets.
 *
 * L'ordre de parcours du fichier est horizontal -- une couche complete, puis la
 * suivante -- ce qui n'est pas un detail : le quartier POUSSE du sol vers le
 * ciel sous les yeux du joueur au lieu d'apparaitre par tranches verticales, et
 * on voit tout de suite si le plan au sol est bon sans attendre la fin.
 *
 * La pose commence par VIDER la boite du quartier. Les plages d'air du fichier
 * sont sautees sans rien ecrire ; reposer un quartier au meme endroit laissait
 * donc l'ancien partout ou le nouveau n'a que de l'air. L'eau d'une version
 * precedente, posee six blocs trop haut, aurait continue de noyer les places
 * apres la correction du fichier, et on aurait conclu a tort que rien n'avait
 * change.
 *
 * VIDER, C'EST REMETTRE A L'ETAT DE BASE, ET L'ETAT DE BASE N'EST PAS
 * TOUJOURS L'AIR. Dans la dimension de la ville, le generateur fournit deja la
 * pierre du fond et la mer : tout remettre en air y creusait un trou carre dans
 * l'ocean. Le plan dit donc, couche par couche, ce que la boite doit redevenir
 * (ou rien du tout, pour une pose sans vidage), et peut sauter l'eau du volume
 * que la mer du generateur donne deja -- six blocs sur sept du port.
 *
 * UN TRAVAIL VIT DANS SON NIVEAU. Le chantier ne tiquait que dans l'overworld ;
 * il tique maintenant dans le niveau de son plan, et plusieurs chantiers
 * peuvent tourner a la fois pourvu que leurs boites ne se touchent pas.
 *
 * LES TRONCONS SONT TENUS PAR UN TICKET, pas charges un par un. Le chargement
 * bloquant d'un troncon a la fois gelait le fil serveur ; un ticket par troncon
 * de la boite laisse les fils de generation travailler en parallele, et tient
 * la boite chargee du debut a la fin. Il n'expire pas -- le ticket des
 * sanctuaires tombait apres deux minutes, et une pose qui depasse ce delai
 * aurait recharge ses troncons en bloquant -- donc on le rend explicitement a
 * la fin.
 *
 * LE DRAPEAU EST 2|16. Le 2 previent le client. Le 16 retient les mises a jour
 * de forme des voisins : sans lui, poser un bloc contre la mer prevenait l'eau
 * voisine, qui planifiait son ecoulement et allait noyer les poches d'air
 * fermees sous la ville.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class JakBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Blocs poses (ou retires) par tick. Assez pour aller vite, assez peu pour rester fluide. */
    private static final int PER_TICK = 20_000;

    /** Temps de fil serveur qu'un chantier s'accorde par tick, en plus du plafond de blocs. */
    private static final long NANOS_PER_TICK = 25_000_000L;

    /** Tiques d'attente des entites, une fois les troncons charges, avant de passer outre. */
    private static final int ENTITY_WAIT = 400;

    /** 2 : on previent le client ; 16 : sans mise a jour de forme des voisins. */
    public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * Le ticket des chantiers de quartier, sans delai d'expiration.
     *
     * Rendu a la fin de chaque pose, et oublie avec le serveur : un ticket ne
     * se sauvegarde pas, il ne peut donc pas survivre a un arret.
     */
    public static final TicketType<ChunkPos> TICKET = TicketType.create("arcencium_haven",
            Comparator.comparingLong(ChunkPos::toLong));

    private static final List<Job> JOBS = new ArrayList<>();

    private JakBuilder() {
    }

    /** Vrai si une pose de l'overworld est en cours : la commande d'essai n'en veut pas deux. */
    public static boolean busy() {
        for (Job job : JOBS) {
            if (job.level.dimension().equals(Level.OVERWORLD)) {
                return true;
            }
        }
        return false;
    }

    /** Interrompt les poses de l'overworld, celles de la commande d'essai. */
    public static void cancel() {
        for (Job job : List.copyOf(JOBS)) {
            if (job.level.dimension().equals(Level.OVERWORLD)) {
                cancel(job);
            }
        }
    }

    /** Interrompt un chantier et rend ses tickets. Son rappel de fin n'est pas appele. */
    public static void cancel(Job job) {
        if (JOBS.remove(job)) {
            job.cancelled = true;
            job.release();
        }
    }

    /**
     * Lance la pose, comme le fait la commande d'essai.
     *
     * L'origine est le coin (minx, miny, minz) du volume : le quartier se batit
     * donc vers l'est, le sud et le haut depuis le point donne, ce qui est
     * previsible et suffit pour un outil d'essai. Tout ce qui se trouve dans
     * cette boite est d'abord retire, et l'eau du volume est posee.
     */
    public static void start(ServerLevel level, JakVolume volume, BlockPos origin,
                             @Nullable ServerPlayer watcher) {
        if (start(new Plan(level, volume, origin).watcher(watcher)) == null && watcher != null) {
            watcher.sendSystemMessage(Component.translatable("command.emeraldweapons.jak.busy"));
        }
    }

    /**
     * Lance une pose etalee sur les ticks de son niveau.
     *
     * @return le chantier, ou null si sa boite touche celle d'un chantier en cours
     */
    @Nullable
    public static Job start(Plan plan) {
        Job job = new Job(plan);
        if (conflicts(job)) {
            return null;
        }
        job.hold();
        job.spread = true;
        JOBS.add(job);
        return job;
    }

    /**
     * Pose d'un seul tenant, sur le fil appelant, jusqu'a une echeance.
     *
     * C'est la pose d'un monde neuf, faite pendant le demarrage du serveur :
     * personne n'est encore la pour voir le jeu fige. Si l'echeance tombe avant
     * la fin -- le chien de garde d'un serveur dedie tue le serveur au bout
     * d'une minute sans tick --, le chantier est confie aux ticks et finit
     * etale.
     *
     * @param deadline instant limite en {@link System#nanoTime()}
     * @return le chantier (fini, ou continue sur les ticks), ou null si sa boite
     *         touche celle d'un chantier en cours
     */
    @Nullable
    public static Job runBlocking(Plan plan, long deadline) {
        Job job = new Job(plan);
        if (conflicts(job)) {
            return null;
        }
        job.hold();
        job.startedBlocking = true;
        job.blockingCall = true;
        boolean finished;
        try {
            finished = job.loadBlocking(deadline) && job.step(deadline, Integer.MAX_VALUE);
        } finally {
            job.blockingCall = false;
        }
        if (!finished) {
            job.spread = true;
            JOBS.add(job);
            LOGGER.warn("{} : la pose bloquante n'a pas fini avant son echeance ; la suite s'etale sur les ticks",
                    plan.label);
        }
        return job;
    }

    private static boolean conflicts(Job job) {
        for (Job other : JOBS) {
            if (other.level == job.level
                    && other.minX <= job.maxX && job.minX <= other.maxX
                    && other.minZ <= job.maxZ && job.minZ <= other.maxZ) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (JOBS.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Job job : List.copyOf(JOBS)) {
            if (job.level != level) {
                continue;
            }
            if (job.step(System.nanoTime() + NANOS_PER_TICK, PER_TICK)) {
                JOBS.remove(job);
            }
        }
    }

    /**
     * Un serveur integre se relance dans le meme processus : un chantier reste
     * dans la liste garderait le niveau d'un monde ferme.
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        JOBS.clear();
    }

    private static boolean matches(BlockState found, BlockState wanted) {
        // l'air des caves vaut l'air, comme le jugeait deja le vidage d'origine
        return found == wanted || (wanted.isAir() && found.isAir());
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    // ------------------------------------------------------------- le plan

    /** Ce qu'on demande a une pose : ou, quoi, et ce qu'on fait de la boite avant. */
    public static final class Plan {
        private final ServerLevel level;
        private final JakVolume volume;
        private final BlockPos origin;
        @Nullable
        private ServerPlayer watcher;
        @Nullable
        private IntFunction<BlockState> baseline = y -> Blocks.AIR.defaultBlockState();
        private boolean skipWater;
        private boolean awaitEntities;
        private boolean clearFluidTicks;
        @Nullable
        private Consumer<ServerLevel> beforePlace;
        @Nullable
        private Consumer<Report> onDone;
        private String label = "quartier";

        public Plan(ServerLevel level, JakVolume volume, BlockPos origin) {
            this.level = level;
            this.volume = volume;
            this.origin = origin.immutable();
        }

        /** Le joueur qui recoit le message de fin de la commande d'essai. */
        public Plan watcher(@Nullable ServerPlayer watcher) {
            this.watcher = watcher;
            return this;
        }

        /** Vider la boite en air avant de poser (vrai par defaut), ou n'y pas toucher. */
        public Plan clear(boolean clear) {
            this.baseline = clear ? y -> Blocks.AIR.defaultBlockState() : null;
            return this;
        }

        /** L'etat que doit reprendre chaque couche de la boite avant la pose ; null : aucun. */
        public Plan baseline(@Nullable IntFunction<BlockState> baseline) {
            this.baseline = baseline;
            return this;
        }

        /** Ne pas poser l'eau du volume, quand le niveau la fournit deja. */
        public Plan skipWater(boolean skipWater) {
            this.skipWater = skipWater;
            return this;
        }

        /** Attendre aussi le chargement des entites de la boite, pour pouvoir les retirer. */
        public Plan awaitEntities(boolean awaitEntities) {
            this.awaitEntities = awaitEntities;
            return this;
        }

        /** Oublier, a la fin, les ecoulements planifies dans la boite. */
        public Plan clearFluidTicks(boolean clearFluidTicks) {
            this.clearFluidTicks = clearFluidTicks;
            return this;
        }

        /** Appele une fois la boite remise a l'etat de base, juste avant la pose. */
        public Plan beforePlace(@Nullable Consumer<ServerLevel> beforePlace) {
            this.beforePlace = beforePlace;
            return this;
        }

        /** Appele a la fin, avant que les tickets soient rendus. */
        public Plan onDone(@Nullable Consumer<Report> onDone) {
            this.onDone = onDone;
            return this;
        }

        /** Le nom du chantier dans le journal. */
        public Plan label(String label) {
            this.label = label;
            return this;
        }
    }

    /**
     * Le compte rendu d'une pose, en TEMPS REEL.
     *
     * L'ancien message divisait le temps de jeu par vingt : il annoncait vingt
     * -cinq secondes pour une pose que le journal encadrait de retards de
     * quatre-vingt-dix-sept secondes. Les durees sont prises en nanoTime.
     */
    public record Report(String label, String mode, long placed, long reset, int chunks,
                         long awaitNanos, long resetNanos, long placeNanos, long busyNanos,
                         long totalNanos, int ticks, long heapStart, long heapPeak,
                         int loadedPeak) {

        public long seconds() {
            return Math.max(1L, Math.round(this.totalNanos / 1.0e9));
        }
    }

    // ------------------------------------------------------------- le chantier

    /** L'etat d'une pose en cours : l'attente des troncons, la remise a l'etat de base, puis les plages. */
    public static final class Job {
        private enum Phase { AWAIT, RESET, PLACE, DONE }

        private final Plan plan;
        private final ServerLevel level;
        private final JakVolume volume;
        private final BlockPos origin;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;
        private final int cx0;
        private final int cz0;
        private final int spanX;
        private final int spanZ;
        /** L'etat de base de chaque couche de la boite, indexe par y - minY ; null : pas de vidage. */
        @Nullable
        private final BlockState[] baseline;

        private Phase phase = Phase.AWAIT;
        private boolean held;
        private boolean spread;
        private boolean startedBlocking;
        private boolean blockingCall;
        private boolean cancelled;
        private int entityWaited;
        private int waited;

        private int chunk;
        private long reset;

        private int run;
        private int withinRun;
        private long cursor;
        private long placed;

        private final long started = System.nanoTime();
        private long awaitDone;
        private long resetDone;
        private long stepFrom;
        private long busyNanos;
        private int ticks;
        private final long heapStart;
        private long heapPeak;
        private int loadedPeak;
        @Nullable
        private Report report;

        private Job(Plan plan) {
            this.plan = plan;
            this.level = plan.level;
            this.volume = plan.volume;
            this.origin = plan.origin;
            this.minX = this.origin.getX();
            this.minZ = this.origin.getZ();
            this.maxX = this.minX + this.volume.width() - 1;
            this.maxZ = this.minZ + this.volume.depth() - 1;
            this.minY = Math.max(this.origin.getY(), this.level.getMinBuildHeight());
            this.maxY = Math.min(this.origin.getY() + this.volume.height() - 1,
                    this.level.getMaxBuildHeight() - 1);
            this.cx0 = this.minX >> 4;
            this.cz0 = this.minZ >> 4;
            this.spanX = (this.maxX >> 4) - this.cx0 + 1;
            this.spanZ = (this.maxZ >> 4) - this.cz0 + 1;
            if (plan.baseline != null && this.maxY >= this.minY) {
                this.baseline = new BlockState[this.maxY - this.minY + 1];
                for (int y = this.minY; y <= this.maxY; y++) {
                    this.baseline[y - this.minY] = plan.baseline.apply(y);
                }
            } else {
                this.baseline = null;
            }
            this.heapStart = usedHeap();
            this.heapPeak = this.heapStart;
        }

        public ServerLevel level() {
            return this.level;
        }

        /** Vrai quand la pose est finie ou interrompue. */
        public boolean done() {
            return this.phase == Phase.DONE || this.cancelled;
        }

        @Nullable
        public Report report() {
            return this.report;
        }

        /** Le nombre de troncons de la boite. */
        public int chunks() {
            return this.spanX * this.spanZ;
        }

        private ChunkPos chunkPos(int n) {
            return new ChunkPos(this.cx0 + n % this.spanX, this.cz0 + n / this.spanX);
        }

        private void hold() {
            for (int n = 0; n < chunks(); n++) {
                ChunkPos pos = chunkPos(n);
                // distance 0 : le troncon lui-meme, a l'etat FINI, sans le faire
                // tiquer -- ni ecoulement ni entites qui bougent pendant la pose
                this.level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
            }
            this.held = true;
        }

        private void release() {
            if (!this.held) {
                return;
            }
            this.held = false;
            for (int n = 0; n < chunks(); n++) {
                ChunkPos pos = chunkPos(n);
                this.level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            }
        }

        private void sample() {
            this.heapPeak = Math.max(this.heapPeak, usedHeap());
            this.loadedPeak = Math.max(this.loadedPeak,
                    this.level.getChunkSource().getLoadedChunksCount());
        }

        /** @return vrai quand tout est pose */
        private boolean step(long deadline, int budget) {
            this.stepFrom = System.nanoTime();
            if (!this.blockingCall) {
                this.ticks++;
            }
            try {
                while (true) {
                    switch (this.phase) {
                        case AWAIT -> {
                            if (!chunksReady()) {
                                return false;
                            }
                            this.awaitDone = System.nanoTime();
                            if (this.baseline != null) {
                                this.phase = Phase.RESET;
                            } else {
                                this.resetDone = this.awaitDone;
                                beforePlace();
                                this.phase = Phase.PLACE;
                            }
                        }
                        case RESET -> {
                            if (!resetBox(deadline, budget)) {
                                return false;
                            }
                            this.resetDone = System.nanoTime();
                            // les ecoulements planifies par l'eau remise partent
                            // AVANT la pose : un troncon qu'un joueur rend actif
                            // pendant la pose les jouerait sinon en retard, apres
                            // les poches voisines, et l'eau coulerait dedans
                            clearFluidTicks();
                            beforePlace();
                            this.phase = Phase.PLACE;
                        }
                        case PLACE -> {
                            if (!place(deadline, budget)) {
                                return false;
                            }
                            finish();
                            return true;
                        }
                        case DONE -> {
                            return true;
                        }
                    }
                }
            } finally {
                this.busyNanos += System.nanoTime() - this.stepFrom;
                sample();
            }
        }

        /** Tous les troncons de la boite sont-ils charges -- et leurs entites, si on les attend ? */
        private boolean chunksReady() {
            int missing = 0;
            int withoutEntities = 0;
            for (int n = 0; n < chunks(); n++) {
                int cx = this.cx0 + n % this.spanX;
                int cz = this.cz0 + n / this.spanX;
                if (this.level.getChunkSource().getChunkNow(cx, cz) == null) {
                    missing++;
                } else if (this.plan.awaitEntities && !this.blockingCall
                        && !this.level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    withoutEntities++;
                }
            }
            if (missing == 0 && (withoutEntities == 0 || this.entityWaited >= ENTITY_WAIT)) {
                return true;
            }
            if (missing == 0) {
                this.entityWaited++;
            }
            this.waited++;
            if (this.waited % 200 == 0) {
                LOGGER.info("{} : attente des troncons, {} manquants et {} sans leurs entites sur {}",
                        this.plan.label, missing, withoutEntities, chunks());
            }
            return false;
        }

        /**
         * Charge la boite d'un seul tenant.
         *
         * Le premier appel bloquant fait passer tous les tickets deja poses : les
         * fils de generation fabriquent alors toute la boite en parallele, et les
         * appels suivants trouvent le plus souvent leur troncon deja pret.
         */
        private boolean loadBlocking(long deadline) {
            for (int n = 0; n < chunks(); n++) {
                if (System.nanoTime() > deadline) {
                    return false;
                }
                this.level.getChunk(this.cx0 + n % this.spanX, this.cz0 + n / this.spanX);
                if ((n & 63) == 0) {
                    sample();
                }
            }
            return true;
        }

        private LevelChunk column(int cx, int cz) {
            LevelChunk column = this.level.getChunkSource().getChunkNow(cx, cz);
            return column != null ? column : this.level.getChunk(cx, cz);
        }

        /**
         * Remet la boite a l'etat de base, troncon par troncon.
         *
         * On lit les sections de troncon directement. Une section dont toutes les
         * couches ont le meme etat de base -- tout le ciel, toute la pierre du
         * fond -- se juge d'un coup sur sa palette : si aucun etat ne differe, on
         * la saute sans lire un bloc. Le travail ne porte que sur ce qui a deja
         * ete bati, et sur la couche ou pierre, mer et ciel se rencontrent.
         *
         * @return vrai quand toute la boite est a l'etat de base
         */
        private boolean resetBox(long deadline, int budget) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int writes = 0;
            while (this.chunk < chunks()) {
                if (writes >= budget || System.nanoTime() > deadline) {
                    return false;
                }
                int cx = this.cx0 + this.chunk % this.spanX;
                int cz = this.cz0 + this.chunk / this.spanX;
                this.chunk++;

                LevelChunk column = column(cx, cz);
                LevelChunkSection[] sections = column.getSections();
                int x0 = Math.max(cx << 4, this.minX);
                int x1 = Math.min((cx << 4) + 15, this.maxX);
                int z0 = Math.max(cz << 4, this.minZ);
                int z1 = Math.min((cz << 4) + 15, this.maxZ);
                for (int i = 0; i < sections.length; i++) {
                    int base = SectionPos.sectionToBlockCoord(column.getSectionYFromSectionIndex(i));
                    int y0 = Math.max(base, this.minY);
                    int y1 = Math.min(base + 15, this.maxY);
                    if (y0 > y1) {
                        continue;
                    }
                    LevelChunkSection section = sections[i];
                    BlockState uniform = this.baseline[y0 - this.minY];
                    for (int y = y0 + 1; y <= y1; y++) {
                        if (this.baseline[y - this.minY] != uniform) {
                            uniform = null;
                            break;
                        }
                    }
                    if (uniform != null) {
                        BlockState wanted = uniform;
                        if (!section.maybeHas(state -> !matches(state, wanted))) {
                            continue;
                        }
                    }
                    for (int y = y0; y <= y1; y++) {
                        BlockState wanted = this.baseline[y - this.minY];
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                if (!matches(section.getBlockState(x & 15, y & 15, z & 15), wanted)) {
                                    this.level.setBlock(pos.set(x, y, z), wanted, FLAGS);
                                    this.reset++;
                                    writes++;
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }

        private void beforePlace() {
            if (this.plan.beforePlace != null) {
                this.plan.beforePlace.accept(this.level);
            }
        }

        /**
         * Pose les plages du volume.
         *
         * L'index lineaire se defait dans l'ordre d'ecriture : y, puis z, puis x.
         * Se tromper ici ne plante pas : cela produit un quartier transpose, ce
         * qui se voit tout de suite mais ne se devine pas.
         *
         * @return vrai quand tout est pose
         */
        private boolean place(long deadline, int budget) {
            int w = this.volume.width();
            int d = this.volume.depth();
            long layer = (long) w * d;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int left = budget;
            int sinceClock = 0;
            while (this.run < this.volume.runCount()) {
                if (left <= 0) {
                    return false;
                }
                if (sinceClock >= 65_536) {
                    sinceClock = 0;
                    if (System.nanoTime() > deadline) {
                        return false;
                    }
                }
                int block = this.volume.runBlock(this.run);
                int length = this.volume.runLength(this.run);
                int rest = length - this.withinRun;
                BlockState state = this.volume.state(block);

                if (block == 0 || (this.plan.skipWater && state.is(Blocks.WATER))) {
                    // l'air : on saute la plage entiere d'un coup, sans rien
                    // poser. C'est ce qui rend la pose tenable -- les neuf
                    // dixiemes du volume sont vides. L'eau aussi, quand le
                    // niveau la fournit deja
                    this.cursor += rest;
                    this.run++;
                    this.withinRun = 0;
                    continue;
                }

                int take = Math.min(left, rest);
                for (int i = 0; i < take; i++) {
                    long index = this.cursor + i;
                    int y = this.origin.getY() + (int) (index / layer);
                    if (y < this.level.getMinBuildHeight() || y >= this.level.getMaxBuildHeight()) {
                        continue;
                    }
                    int x = this.origin.getX() + (int) (index % w);
                    int z = this.origin.getZ() + (int) ((index / w) % d);
                    this.level.setBlock(pos.set(x, y, z), state, FLAGS);
                }
                this.cursor += take;
                this.placed += take;
                this.withinRun += take;
                left -= take;
                sinceClock += take;
                if (this.withinRun >= length) {
                    this.run++;
                    this.withinRun = 0;
                }
            }
            return true;
        }

        private String mode() {
            if (!this.startedBlocking) {
                return "etale";
            }
            return this.spread ? "bloquant puis etale" : "bloquant";
        }

        /**
         * Efface les ecoulements planifies dans la boite, si le plan le demande.
         *
         * Un bloc d'eau remis par le vidage planifie son ecoulement a la pose,
         * quels que soient les drapeaux. On l'efface deux fois : a la sortie de la
         * remise, avant que la pose ne mure l'eau ; et a la fin, pour ce que la
         * pose elle-meme a planifie.
         */
        private void clearFluidTicks() {
            if (this.plan.clearFluidTicks && this.maxY >= this.minY) {
                this.level.getFluidTicks().clearArea(new BoundingBox(
                        this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ));
            }
        }

        private void finish() {
            this.phase = Phase.DONE;
            // la pose a pu murer ou remplacer un bloc d'eau, mais le tick
            // planifie, lui, reste sauve avec le troncon et partirait des qu'un
            // joueur approche
            clearFluidTicks();
            long end = System.nanoTime();
            sample();
            this.report = new Report(this.plan.label, mode(), this.placed, this.reset, chunks(),
                    this.awaitDone - this.started, this.resetDone - this.awaitDone,
                    end - this.resetDone, this.busyNanos + (end - this.stepFrom),
                    end - this.started, this.ticks, this.heapStart, this.heapPeak, this.loadedPeak);
            LOGGER.info("{} pose : {} blocs en {} ms reels ({}, {} ticks, {} ms de fil serveur), {} anciens blocs"
                            + " remis a l'etat de base d'abord ; troncons {} ms, remise {} ms, pose {} ms ;"
                            + " {} troncons dans la boite, {} charges au plus, tas {} -> {} Mo au plus",
                    this.plan.label, this.placed, this.report.totalNanos() / 1_000_000L, this.report.mode(),
                    this.ticks, this.report.busyNanos() / 1_000_000L, this.reset,
                    this.report.awaitNanos() / 1_000_000L, this.report.resetNanos() / 1_000_000L,
                    this.report.placeNanos() / 1_000_000L, chunks(), this.loadedPeak,
                    this.heapStart >> 20, this.heapPeak >> 20);
            if (this.plan.watcher != null) {
                this.plan.watcher.sendSystemMessage(Component.translatable(
                                "command.emeraldweapons.jak.done", this.placed, this.report.seconds())
                        .withStyle(ChatFormatting.AQUA));
            }
            try {
                if (this.plan.onDone != null) {
                    this.plan.onDone.accept(this.report);
                }
            } catch (RuntimeException e) {
                LOGGER.error("{} : le rappel de fin de pose a echoue", this.plan.label, e);
            } finally {
                release();
            }
        }
    }
}
