package com.emerald.haven;

import com.emerald.block.HavenDoorBlock;
import com.emerald.block.entity.HavenDoorBlockEntity;
import com.emerald.haven.door.HavenDoorFrame;
import com.emerald.haven.door.HavenDoorKind;
import com.emerald.haven.door.HavenDoors;
import com.emerald.jak.JakBuilder;
import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Le banc d'essai de la ville, INERTE sans la variable d'environnement
 * EMERALDWEAPONS_AUTOTEST=haven.
 *
 * Il pose la ville -- en mode bloquant dans un monde neuf, ou elle se pose deja
 * au demarrage ; par un rebuild etale sinon --, mesure la pose, controle ce
 * qu'on doit voir en jeu, attend soixante secondes de ticks pour laisser a
 * l'eau le temps de couler si elle devait couler, ecrit le rapport dans
 * haven_autotest.txt, dans le dossier de jeu du serveur (run-server/ pour la
 * tache runServer), et arrete le serveur.
 *
 * LES CONTROLES LISENT LE MONDE, pas le code : on a deja vu deux ports
 * « corriges » sur des chiffres rester casses en jeu.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final String VARIABLE = "EMERALDWEAPONS_AUTOTEST";
    private static final boolean ENABLED = "haven".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(VARIABLE), "").trim());

    /** Soixante secondes de ticks entre les deux balayages d'eau. */
    private static final int WAIT_TICKS = 60 * 20;
    /** Au-dela de vingt minutes de pose, on rend un rapport d'echec plutot que d'attendre. */
    private static final int POSE_TIMEOUT_TICKS = 20 * 60 * 20;
    /** Distance du ticket pendant l'attente : 31, troncons tiquant avec leurs entites. */
    private static final int TICKING_DISTANCE = 2;

    private enum Stage { START, POSE, WAIT, END }

    private static Stage stage = Stage.START;
    private static final StringBuilder OUT = new StringBuilder();
    private static final List<ChunkPos> HELD = new ArrayList<>();
    private static int passed;
    private static int failed;
    private static int waited;
    private static String how = "";

    private HavenAutotest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        MinecraftServer server = event.getServer();
        switch (stage) {
            case START -> {
                header();
                if (HavenSite.busy()) {
                    how = HavenSite.freshWorld()
                            ? "monde neuf, pose bloquante au demarrage, finie sur les ticks"
                            : "reprise d'une pose inachevee";
                    stage = Stage.POSE;
                } else {
                    how = "monde existant : /arcencium haven rebuild, etale";
                    stage = Stage.POSE;
                    Component failure = HavenSite.start(server, true, HavenSite.Mode.ETALE, null);
                    if (failure != null) {
                        check("lancement du rebuild", false, failure.getString());
                        end(server);
                    }
                }
            }
            case POSE -> {
                if (++waited > POSE_TIMEOUT_TICKS) {
                    check("pose finie en moins de 20 minutes", false, "delai depasse");
                    end(server);
                }
            }
            case WAIT -> {
                if (++waited >= WAIT_TICKS) {
                    afterWait(server);
                    end(server);
                }
            }
            case END -> {
            }
        }
    }

    /**
     * Appele a la fin de chaque pose de la ville, tickets encore tenus.
     *
     * Dans un monde neuf, la pose bloquante finit PENDANT le demarrage, avant le
     * premier tick : c'est donc ici, et non dans le tick, que tout commence.
     */
    static void onPoseDone(MinecraftServer server, JakVolume volume, JakBuilder.Report report) {
        if (!ENABLED || (stage != Stage.START && stage != Stage.POSE)) {
            return;
        }
        header();
        if (stage == Stage.START) {
            how = HavenSite.freshWorld() ? "monde neuf, pose bloquante au demarrage" : "pose au demarrage";
        }
        ServerLevel level = Haven.level(server);
        HavenState state = HavenState.get(server);
        BlockPos o = state.origin();
        Runtime runtime = Runtime.getRuntime();

        line("mode de pose : " + report.mode() + " (" + how + ")");
        line("duree reelle : " + ms(report.totalNanos()) + " ms ; attente des troncons "
                + ms(report.awaitNanos()) + " ms, remise au generateur " + ms(report.resetNanos())
                + " ms, pose " + ms(report.placeNanos()) + " ms ; fil serveur " + ms(report.busyNanos())
                + " ms ; " + report.ticks() + " ticks"
                + (report.ticks() > 0 ? String.format(Locale.ROOT, ", soit %.1f ms reelles par tick",
                        report.totalNanos() / 1.0e6 / report.ticks()) : ""));
        line("blocs poses : " + report.placed() + " ; blocs remis au generateur : " + report.reset());
        line("troncons de la boite : " + report.chunks() + " ; charges au plus dans haven : "
                + report.loadedPeak() + " ; charges maintenant : "
                + level.getChunkSource().getLoadedChunksCount());
        line("tas : " + mb(report.heapStart()) + " Mo au depart, " + mb(report.heapPeak()) + " Mo au plus, "
                + mb(runtime.totalMemory() - runtime.freeMemory()) + " Mo apres, sur "
                + mb(runtime.maxMemory()) + " Mo permis");
        line(String.format(Locale.ROOT, "tick moyen du serveur (100 derniers) : %.1f ms",
                server.getAverageTickTimeNanos() / 1.0e6));
        line("origine : " + o.toShortString() + " ; grille " + state.width() + " x " + state.height()
                + " x " + state.depth() + " ; volume " + Haven.VOLUME);

        BlockPos street = Haven.STREET_CELL;
        BlockPos door = Haven.BAR_DOOR_CELL;
        BlockState streetWanted = cell(volume, street);
        BlockState doorWanted = cell(volume, door);
        BlockState doorUpWanted = cell(volume, door.above());

        BlockPos streetAt = o.offset(street);
        BlockState streetGot = level.getBlockState(streetAt);
        check("rue en cellule (375, 65, 211) : sol de la palette en Y 70",
                streetAt.getY() == 70 && streetGot == streetWanted && !streetGot.isAir(),
                "Y " + streetAt.getY() + ", monde " + name(streetGot) + ", volume " + name(streetWanted));

        BlockState above1 = level.getBlockState(streetAt.above());
        BlockState above2 = level.getBlockState(streetAt.above(2));
        check("air aux deux cellules au-dessus (pieds en Y 71)", above1.isAir() && above2.isAir(),
                name(above1) + " en Y " + (streetAt.getY() + 1) + ", " + name(above2) + " en Y "
                        + (streetAt.getY() + 2));

        BlockPos sea = new BlockPos(o.getX() + state.width() + 300, Haven.WATER_TOP,
                o.getZ() + state.depth() / 2);
        BlockState seaGot = level.getBlockState(sea);
        BlockState seaAbove = level.getBlockState(sea.above());
        check("eau du generateur loin en mer en Y 62",
                seaGot.is(Blocks.WATER) && seaGot.getFluidState().isSource() && seaAbove.isAir(),
                sea.toShortString() + " : " + name(seaGot) + ", au-dessus " + name(seaAbove));

        pockets(level, volume, o);

        BlockState doorGot = level.getBlockState(o.offset(door));
        BlockState doorUp = level.getBlockState(o.offset(door.above()));
        // au-dessus du seuil, l'air du volume -- ou la porte de Jak 3, posee d'office (cahier §95)
        check("porte du bar en cellule (361, 66, 197) : le seuil, et au-dessus l'air ou la porte de Jak 3",
                same(doorGot, doorWanted) && (same(doorUp, doorUpWanted)
                        || doorUp.getBlock() instanceof HavenDoorBlock),
                "monde " + name(doorGot) + " / " + name(doorUp) + ", volume " + name(doorWanted)
                        + " / " + name(doorUpWanted) + ", en " + o.offset(door).toShortString());

        long[] flowing = countFlowing(level, state);
        check("eau qui coule dans la boite juste apres la pose", flowing[0] == 0 && flowing[1] == 0,
                flowing[0] + " blocs" + where(flowing) + ", " + flowing[1] + " troncons non charges");

        // LES CABLES (cahier §94) : chaque cellule de haven_cables.json est vide apres la pose (la
        // chaine est dessinee par le client), et chaque cable entier a un bloc temoin plein au
        // moins, sans quoi le client ne le dessinerait jamais
        int emptied = 0;
        for (HavenCables.Cell cell : HavenCables.cells()) {
            if (level.getBlockState(o.offset(cell.x(), cell.y(), cell.z())).isAir()) {
                emptied++;
            }
        }
        int seen = 0;
        for (HavenCables.Line line : HavenCables.lines()) {
            boolean start = line.startWitness() != null && !level.getBlockState(o.offset(line.startWitness())).isAir();
            boolean end = line.endWitness() != null && !level.getBlockState(o.offset(line.endWitness())).isAir();
            if (start || end) {
                seen++;
            }
        }
        check("cables : les cellules des cables sont vides, chaque cable entier a son bloc temoin, etat a la version "
                        + HavenCables.VERSION,
                emptied == HavenCables.cells().size() && HavenCables.cells().size() > 2000
                        && seen == HavenCables.lines().size() && HavenCables.lines().size() >= 12
                        && state.cables() == HavenCables.VERSION,
                emptied + " cellules vides sur " + HavenCables.cells().size() + ", " + seen + " cables sur "
                        + HavenCables.lines().size() + " avec leur temoin, etat " + state.cables());

        doors(server, level, o, state);
        windows(level, o);
        gear(level, o);

        curtain(level, volume, o);

        String resource = Haven.volumeSha1(server);
        String rooms = roomsSha1(server);
        check("HavenState : pose finie", state.wanted() && state.built(),
                "phase " + state.phase() + ", demandee " + state.wanted() + ", finie " + state.built());
        check("HavenState : sha1 pose egal a celui des donnees du volume",
                !resource.isEmpty() && resource.equals(state.sha1()) && resource.equals(volume.sha1()),
                "pose " + state.sha1() + ", en-tete " + resource + ", volume lu " + volume.sha1());
        check("haven_rooms.json tire du volume pose (meme sha1)",
                !resource.isEmpty() && resource.equals(rooms),
                "salles " + rooms + ", volume " + resource);
        if (HavenSite.freshWorld()) {
            check("HavenState : phase ACCUEIL apres la pose du monde neuf",
                    state.phase() == HavenState.Phase.ACCUEIL, "phase " + state.phase());
        } else {
            line("phase : " + state.phase() + " (un monde existant garde sa phase)");
        }

        hold(level, state);
        line("troncons de la boite tenus en tick d'entites pendant 60 s : " + HELD.size());
        waited = 0;
        stage = Stage.WAIT;
    }

    private static void afterWait(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        HavenState state = HavenState.get(server);
        int ticking = 0;
        for (ChunkPos pos : HELD) {
            if (level.isPositionEntityTicking(pos.getWorldPosition())) {
                ticking++;
            }
        }
        long[] flowing = countFlowing(level, state);
        check("eau qui coule dans la boite 60 s plus tard", flowing[0] == 0 && flowing[1] == 0,
                flowing[0] + " blocs" + where(flowing) + ", " + flowing[1] + " troncons non charges ; "
                        + ticking + " troncons sur " + HELD.size() + " tiquaient");
        line(String.format(Locale.ROOT, "tick moyen du serveur pendant l'attente : %.1f ms",
                server.getAverageTickTimeNanos() / 1.0e6));
    }

    /**
     * Les poches d'air sous la mer, couches 52 a 57 du volume.
     *
     * Le generateur remplit d'eau tout ce que la pose ne recouvre pas ; une
     * poche n'y survit que si le volume l'ecrit en air des caves, que la pose
     * ecrit. On compte, cellule par cellule, ce qu'elles sont devenues.
     */
    private static void pockets(ServerLevel level, JakVolume volume, BlockPos o) {
        long w = volume.width();
        long d = volume.depth();
        long layer = w * d;
        long from = 52L * layer;
        long to = 58L * layer;
        long cursor = 0;
        long cave = 0;
        long caveDry = 0;
        long air = 0;
        long airWet = 0;
        BlockPos first = null;
        BlockState firstGot = null;
        for (int run = 0; run < volume.runCount() && cursor < to; run++) {
            long end = cursor + volume.runLength(run);
            BlockState state = volume.state(volume.runBlock(run));
            if (end > from && state.isAir()) {
                boolean isCave = state.is(Blocks.CAVE_AIR);
                for (long i = Math.max(cursor, from); i < Math.min(end, to); i++) {
                    BlockPos pos = o.offset((int) (i % w), (int) (i / layer), (int) ((i / w) % d));
                    BlockState got = level.getBlockState(pos);
                    if (isCave) {
                        cave++;
                        if (got.isAir()) {
                            caveDry++;
                        }
                        if (first == null) {
                            first = pos;
                            firstGot = got;
                        }
                    } else {
                        air++;
                        if (!got.getFluidState().isEmpty()) {
                            airWet++;
                        }
                    }
                }
            }
            cursor = end;
        }
        if (cave > 0) {
            check("poche sous la mer : air ou air des caves", caveDry == cave,
                    "premiere cellule cave_air en " + first.toShortString() + " : " + name(firstGot) + " ; "
                            + caveDry + " sur " + cave + " cellules cave_air du volume sont de l'air dans le monde");
        } else {
            check("poche sous la mer : air ou air des caves", false,
                    "le volume ne contient aucune cellule cave_air dans les couches 52 a 57");
        }
        line("cellules d'air ordinaire du volume dans les couches 52 a 57 : " + air + ", dont " + airWet
                + " remplies d'eau par le generateur");
    }

    /**
     * Le rideau de barrieres du bord, dans la mer : couches 52 a 57, Y 57 a 62.
     *
     * Une barriere seche dans l'eau du generateur laisse une fente d'un bloc
     * sur tout le pourtour, que ni la marche ni le balayage d'eau qui coule ne
     * voient. On lit le monde a chaque cellule du pourtour ou le volume met une
     * barriere : elle doit y etre, et porter de l'eau source.
     */
    /**
     * LES PORTES DE JAK 3 (cahier §95). Chaque porte d'office a son controleur, de la bonne sorte,
     * fermee, et ses cellules barrent le passage. Le bar (en biais) et le sas s'ouvrent pour
     * quelqu'un qui reste devant, liberent leur passage -- les bords du sas restent pleins -- et
     * se referment quand il s'en va. Une porte posee a la main sur la rue tient toutes ses
     * cellules ; en casser une emporte la porte entiere.
     */
    private static void doors(MinecraftServer server, ServerLevel level, BlockPos o, HavenState state) {
        List<HavenDoorFrame> frames = HavenDoors.defaults(server);
        int doors = 0;
        int closedCells = 0;
        int cells = 0;
        StringBuilder missing = new StringBuilder();
        List<HavenDoorBlockEntity> entities = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            HavenDoorFrame frame = frames.get(i);
            if (HavenDoors.controllerAt(level, o, frame) instanceof HavenDoorBlockEntity entity
                    && entity.kind() == frame.kind() && !entity.target() && entity.progress() == 0.0F
                    && !entity.cellsOpen()) {
                doors++;
                entities.add(entity);
            } else {
                missing.append(' ').append(i);
                entities.add(null);
            }
            for (BlockPos cell : frame.moved(o.getX(), o.getY(), o.getZ()).cells()) {
                BlockState got = level.getBlockState(cell);
                if (got.getBlock() instanceof HavenDoorBlock) {
                    cells++;
                    if (!got.getCollisionShape(level, cell).isEmpty()) {
                        closedCells++;
                    }
                }
            }
        }
        check("portes de Jak 3 : les trois appartements, le bar et le sas ont leur porte, fermee, qui barre le passage",
                doors == frames.size() && frames.size() >= 5 && cells > 0 && closedCells == cells
                        && state.doors() == HavenDoors.VERSION,
                doors + " portes sur " + frames.size() + (missing.length() > 0 ? " (manquent :" + missing + ")" : "")
                        + ", " + closedCells + " cellules fermees sur " + cells + ", etat " + state.doors());

        // le bar et le sas, les deux dernieres portes d'office : ouvrir, puis laisser se refermer
        for (int i = frames.size() - 2; i < frames.size(); i++) {
            HavenDoorBlockEntity entity = entities.get(i);
            if (entity == null) {
                check("porte d'office " + i + " : s'ouvre et se referme", false, "pas de porte");
                continue;
            }
            cycle(level, i, entity);
        }

        // a la main, sur la rue devant le bar : la petite porte et celle du Hip Hog
        BlockPos street = o.offset(Haven.BAR_FRONT_CELL);
        HavenDoorFrame small = new HavenDoorFrame(HavenDoorKind.PETITE, street.getX() + 0.5, street.getY(),
                street.getZ() + 0.5, 0.0F);
        HavenDoorFrame hip = new HavenDoorFrame(HavenDoorKind.HIP, street.getX() - 3.5, street.getY(),
                street.getZ() + 0.5, 0.0F);
        for (HavenDoorFrame frame : List.of(small, hip)) {
            placeAndBreak(level, frame);
        }
    }

    /**
     * LES VITRES DE JAK 3 (cahier §101), une fenetre de trois sur deux posee sur la rue, six blocs
     * devant le bar : elles se relient, et le cadre n'en borde que le pourtour ; un clic ferme toute
     * la fenetre, l'iris clos au bout d'une course ; une vitre ajoutee a une fenetre fermee se ferme
     * avec elle ; casser la colonne du milieu coupe la fenetre en deux, et chaque moitie s'ouvre seule.
     */
    private static void windows(ServerLevel level, BlockPos o) {
        BlockPos base = o.offset(Haven.BAR_FRONT_CELL).south(6);
        com.emerald.block.HavenWindowBlock block = com.emerald.block.ModBlocks.HAVEN_WINDOW.get();
        boolean free = true;
        for (int dx = -1; dx <= 4; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                free &= level.getBlockState(base.offset(dx, dy, 0)).isAir();
            }
        }
        if (!free) {
            check("vitres de Jak 3 : une place libre de six sur trois devant le bar", false, "en " + base.toShortString());
            return;
        }
        BlockState placed = block.defaultBlockState().setValue(com.emerald.block.HavenWindowBlock.AXIS,
                net.minecraft.core.Direction.Axis.X);
        List<BlockPos> panes = new ArrayList<>();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 2; dx++) {
                BlockPos at = base.offset(dx, dy, 0);
                level.setBlock(at, com.emerald.block.HavenWindowBlock.connected(level, at, placed), Block.UPDATE_ALL);
                panes.add(at);
            }
        }
        // le cadre : la ou aucune vitre ne continue
        BlockState corner = level.getBlockState(base);
        BlockState middle = level.getBlockState(base.offset(1, 1, 0));
        boolean frame = !corner.getValue(com.emerald.block.HavenWindowBlock.LEFT)
                && corner.getValue(com.emerald.block.HavenWindowBlock.RIGHT)
                && corner.getValue(com.emerald.block.HavenWindowBlock.UP)
                && !corner.getValue(com.emerald.block.HavenWindowBlock.DOWN)
                && middle.getValue(com.emerald.block.HavenWindowBlock.LEFT)
                && middle.getValue(com.emerald.block.HavenWindowBlock.RIGHT)
                && !middle.getValue(com.emerald.block.HavenWindowBlock.UP)
                && middle.getValue(com.emerald.block.HavenWindowBlock.DOWN);
        com.emerald.haven.door.HavenWindows.Window window = com.emerald.haven.door.HavenWindows.group(level, base);
        check("vitres de Jak 3 : une fenetre de trois sur deux, reliee, le cadre sur son seul pourtour",
                frame && window.panes().size() == 6 && window.u0() == base.getX() && window.u1() == base.getX() + 2
                        && window.v0() == base.getY() && window.v1() == base.getY() + 1,
                window.panes().size() + " vitres, de " + window.u0() + " a " + window.u1() + " et de " + window.v0()
                        + " a " + window.v1() + ", coin " + corner + ", milieu du haut " + middle);

        com.emerald.haven.door.HavenWindows.toggle(level, base.offset(2, 1, 0));
        long now = level.getGameTime();
        int closing = 0;
        int shut = 0;
        for (BlockPos pane : panes) {
            if (level.getBlockEntity(pane) instanceof com.emerald.block.entity.HavenWindowBlockEntity entity
                    && entity.closed() && entity.u1() == base.getX() + 2 && entity.v1() == base.getY() + 1) {
                closing += entity.openness(now, 0.0F) == 1.0F ? 1 : 0;
                shut += entity.openness(now + com.emerald.haven.door.HavenWindows.DURATION, 0.0F) == 0.0F ? 1 : 0;
            }
        }
        check("vitres de Jak 3 : un clic sur une vitre ferme toute la fenetre, l'iris clos au bout d'une course",
                closing == 6 && shut == 6, closing + " vitres qui partent grandes ouvertes, " + shut + " closes "
                        + com.emerald.haven.door.HavenWindows.DURATION + " tiques plus tard");
        // l'iris est dit parti il y a une course : la fenetre est fermee
        long past = now - com.emerald.haven.door.HavenWindows.DURATION;
        for (BlockPos pane : panes) {
            if (level.getBlockEntity(pane) instanceof com.emerald.block.entity.HavenWindowBlockEntity entity) {
                entity.set(true, past, entity.u0(), entity.u1(), entity.v0(), entity.v1());
            }
        }

        // une vitre de plus au bout du bas : elle rejoint la fenetre fermee
        BlockPos extra = base.offset(3, 0, 0);
        level.setBlock(extra, com.emerald.block.HavenWindowBlock.connected(level, extra, placed), Block.UPDATE_ALL);
        boolean joined = level.getBlockEntity(extra) instanceof com.emerald.block.entity.HavenWindowBlockEntity entity
                && entity.closed() && entity.openness(level.getGameTime(), 0.0F) == 0.0F
                && level.getBlockEntity(base) instanceof com.emerald.block.entity.HavenWindowBlockEntity first
                && first.u1() == base.getX() + 3;
        check("vitres de Jak 3 : une vitre ajoutee a une fenetre fermee se ferme avec elle, et la fenetre s'agrandit",
                joined, "vitre ajoutee en " + extra.toShortString());

        // la colonne du milieu cassee : deux fenetres, chacune avec ses bornes, et un clic n'ouvre que la sienne
        level.setBlock(base.offset(1, 0, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(base.offset(1, 1, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        com.emerald.haven.door.HavenWindows.toggle(level, base);
        boolean split = level.getBlockEntity(base) instanceof com.emerald.block.entity.HavenWindowBlockEntity leftPane
                && !leftPane.closed() && leftPane.u0() == base.getX() && leftPane.u1() == base.getX()
                && level.getBlockEntity(base.offset(2, 0, 0)) instanceof com.emerald.block.entity.HavenWindowBlockEntity rightPane
                && rightPane.closed() && rightPane.u0() == base.getX() + 2 && rightPane.u1() == base.getX() + 3;
        check("vitres de Jak 3 : la colonne du milieu cassee, deux fenetres ; la gauche se rouvre seule",
                split, "");
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 3; dx++) {
                level.setBlock(base.offset(dx, dy, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Une porte d'office : quelqu'un reste devant jusqu'a ce qu'elle soit grande ouverte, puis s'en va. */
    private static void cycle(ServerLevel level, int index, HavenDoorBlockEntity entity) {
        HavenDoorFrame frame = entity.frame();
        BlockPos at = entity.getBlockPos();
        // les cellules que la porte tient (au bar, le linteau garde les siennes)
        List<BlockPos> held = new ArrayList<>();
        for (BlockPos cell : frame.cells()) {
            if (level.getBlockState(cell).getBlock() instanceof HavenDoorBlock) {
                held.add(cell);
            }
        }
        for (int t = 0; t < entity.kind().duration + 5; t++) {
            entity.trigger(level);
            HavenDoorBlockEntity.serverTick(level, at, level.getBlockState(at), entity);
        }
        int passable = 0;
        int clear = 0;
        int solidEdges = 0;
        int edges = 0;
        for (BlockPos cell : held) {
            boolean empty = level.getBlockState(cell).getCollisionShape(level, cell).isEmpty();
            if (frame.clearWhenOpen(cell)) {
                clear++;
                passable += empty ? 1 : 0;
            } else {
                edges++;
                solidEdges += empty ? 0 : 1;
            }
        }
        boolean opened = entity.target() && entity.progress() == 1.0F && entity.cellsOpen();
        for (int t = 0; t < 2 * entity.kind().duration; t++) {
            HavenDoorBlockEntity.serverTick(level, at, level.getBlockState(at), entity);
        }
        int closed = 0;
        for (BlockPos cell : held) {
            closed += level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() ? 0 : 1;
        }
        boolean shut = !entity.target() && entity.progress() == 0.0F && !entity.cellsOpen();
        check("porte d'office " + index + " (" + entity.kind() + ") : grande ouverte pour qui reste devant, "
                        + "puis refermee derriere lui",
                opened && clear > 0 && passable == clear && solidEdges == edges && shut && closed == held.size(),
                "ouverte " + opened + ", passage " + passable + "/" + clear + " cellules libres, bords "
                        + solidEdges + "/" + edges + " pleins ; refermee " + shut + ", " + closed + "/"
                        + held.size() + " cellules pleines");
    }

    /** Une porte posee a la main (comme l'objet de l'atelier) : toutes ses cellules, puis cassee par le haut. */
    private static void placeAndBreak(ServerLevel level, HavenDoorFrame frame) {
        boolean placed = HavenDoors.place(level, frame, false);
        List<BlockPos> all = HavenDoors.withController(frame);
        int held = 0;
        for (BlockPos cell : all) {
            held += level.getBlockState(cell).getBlock() instanceof HavenDoorBlock ? 1 : 0;
        }
        boolean controlled = level.getBlockEntity(frame.controller()) instanceof HavenDoorBlockEntity entity
                && entity.kind() == frame.kind();
        // la cellule la plus haute, au bout de la largeur : la plus loin du controleur
        BlockPos top = all.get(0);
        for (BlockPos cell : all) {
            if (cell.getY() > top.getY() || (cell.getY() == top.getY() && cell.getX() > top.getX())) {
                top = cell;
            }
        }
        level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        int left = 0;
        for (BlockPos cell : all) {
            left += level.getBlockState(cell).isAir() ? 0 : 1;
        }
        check("porte " + frame.kind() + " posee a la main sur la rue : " + all.size()
                        + " cellules et son controleur ; cassee par le haut, elle part entiere",
                placed && held == all.size() && controlled && left == 0,
                "posee " + placed + ", " + held + "/" + all.size() + " cellules, controleur " + controlled
                        + " ; apres la casse en " + top.toShortString() + ", " + left + " cellules restent");
    }

    /**
     * EN VILLE, L'EQUIPEMENT DU DEHORS NE SERT A RIEN (cahier §96, HavenGear). Un faux joueur est
     * invulnerable : l'armure et le bouclier se lisent sur les evenements eux-memes, passes a la
     * regle comme le jeu les lui passe.
     */
    private static void gear(ServerLevel level, BlockPos o) {
        BlockPos street = o.offset(Haven.BAR_FRONT_CELL).south(3);
        net.minecraft.world.entity.monster.Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            check("equipement en ville : un zombie d'essai", false, "zombie impossible");
            return;
        }
        zombie.moveTo(street.getX() + 0.5, street.getY(), street.getZ() + 0.5, 0.0F, 0.0F);
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        net.neoforged.neoforge.common.util.FakePlayer fake = new net.neoforged.neoforge.common.util.FakePlayer(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(
                        "autotest-haven:equipement".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "[Haven]"));
        fake.moveTo(street.getX() + 2.5, street.getY(), street.getZ() + 0.5, 90.0F, 0.0F);
        try {
            fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            float before = zombie.getHealth();
            zombie.hurt(level.damageSources().playerAttack(fake), 10.0F);
            boolean swordUseless = zombie.getHealth() == before;
            // le poing reste : un point, jamais plus (dix demandes)
            fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
            zombie.invulnerableTime = 0;
            float beforeFist = zombie.getHealth();
            zombie.hurt(level.damageSources().playerAttack(fake), 10.0F);
            float fist = beforeFist - zombie.getHealth();
            boolean punch = fist > 0.5F && fist <= HavenGear.FIST;
            zombie.invulnerableTime = 0;
            net.minecraft.world.damagesource.DamageSource gun = new net.minecraft.world.damagesource.DamageSource(
                    level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(com.emerald.jak.gun.GunImpacts.DAMAGE_TYPE), null, null, zombie.position());
            zombie.hurt(gun, 6.0F);
            boolean gunWorks = zombie.getHealth() < before;
            net.neoforged.neoforge.common.damagesource.DamageContainer container =
                    new net.neoforged.neoforge.common.damagesource.DamageContainer(
                            level.damageSources().mobAttack(zombie), 10.0F);
            net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent incoming =
                    new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(fake, container);
            HavenGear.onIncomingDamage(incoming);
            container.setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR, 6.0F);
            container.setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ENCHANTMENTS, 2.0F);
            boolean bare = !incoming.isCanceled()
                    && container.getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR) == 0.0F
                    && container.getReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ENCHANTMENTS) == 0.0F;
            net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent block =
                    new net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent(fake,
                            new net.neoforged.neoforge.common.damagesource.DamageContainer(
                                    level.damageSources().mobAttack(zombie), 4.0F), true);
            HavenGear.onShield(block);
            boolean noShield = !block.getBlocked();
            net.minecraft.world.item.ItemStack boots =
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS);
            com.emerald.artifact.Artifacts.set(boots, com.emerald.artifact.Artifact.BOTTES_D_ECLAIR);
            fake.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
            boolean silent = !com.emerald.artifact.Artifacts.wearing(fake, com.emerald.artifact.Artifact.BOTTES_D_ECLAIR);
            check("en ville : une epee du dehors ne blesse pas, le poing d'un point au plus, le Morph Gun si ;"
                            + " l'armure, ses enchantements et le bouclier ne protegent plus ; un artefact se tait",
                    swordUseless && punch && gunWorks && bare && noShield && silent,
                    "epee inutile " + swordUseless + ", poing " + fist + ", Morph Gun " + gunWorks + " (" + before + " -> "
                            + zombie.getHealth() + "), armure nue " + bare + ", bouclier baisse " + noShield
                            + ", artefact muet " + silent);
        } finally {
            zombie.discard();
        }
    }

    private static void curtain(ServerLevel level, JakVolume volume, BlockPos o) {
        int w = volume.width();
        int d = volume.depth();
        long expected = 0;
        long wet = 0;
        long dry = 0;
        long other = 0;
        BlockPos firstBad = null;
        String firstBadGot = "";
        for (int y = 52; y <= 57 && y < volume.height(); y++) {
            for (int z = 0; z < d; z++) {
                int step = (z == 0 || z == d - 1) ? 1 : w - 1;
                for (int x = 0; x < w; x += step) {
                    BlockState wanted = volume.stateAt(x, y, z);
                    if (!wanted.is(Blocks.BARRIER)) {
                        continue;
                    }
                    expected++;
                    BlockPos pos = o.offset(x, y, z);
                    BlockState got = level.getBlockState(pos);
                    FluidState fluid = got.getFluidState();
                    boolean ok = got.is(Blocks.BARRIER) && fluid.isSource()
                            && fluid.is(net.minecraft.tags.FluidTags.WATER);
                    if (ok) {
                        wet++;
                    } else {
                        if (got.is(Blocks.BARRIER)) {
                            dry++;
                        } else {
                            other++;
                        }
                        if (firstBad == null) {
                            firstBad = pos;
                            firstBadGot = name(got);
                        }
                    }
                }
            }
        }
        check("rideau du bord noye dans la mer (Y 57 a 62)",
                expected > 0 && wet == expected,
                wet + " barrieres noyees sur " + expected + " ; " + dry + " seches, " + other + " autres"
                        + (firstBad == null ? "" : " ; la premiere en " + firstBad.toShortString()
                        + " : " + firstBadGot));
    }

    /** Le sha1 que porte haven_rooms.json, ou une chaine vide. */
    private static String roomsSha1(MinecraftServer server) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "jak/haven_rooms.json");
        Optional<Resource> found = server.getResourceManager().getResource(key);
        if (found.isEmpty()) {
            return "";
        }
        try (Reader reader = found.get().openAsReader()) {
            JsonElement sha1 = JsonParser.parseReader(reader).getAsJsonObject().get("sha1");
            return sha1 == null || sha1.isJsonNull() ? "" : sha1.getAsString();
        } catch (IOException | RuntimeException e) {
            LOGGER.error("autotest haven : haven_rooms.json illisible", e);
            return "";
        }
    }

    /**
     * Les blocs d'eau qui COULENT dans la boite, du fond du monde au sommet de la grille.
     *
     * La palette de chaque section dit d'un coup s'il peut y en avoir : on ne
     * lit bloc a bloc que les rares sections qui en contiennent peut-etre.
     *
     * @return {nombre, troncons non charges, premiere position (ou MIN_VALUE)}
     */
    private static long[] countFlowing(ServerLevel level, HavenState state) {
        BlockPos o = state.origin();
        int minX = o.getX();
        int maxX = minX + state.width() - 1;
        int minZ = o.getZ();
        int maxZ = minZ + state.depth() - 1;
        int minY = level.getMinBuildHeight();
        int maxY = Math.min(o.getY() + state.height() - 1, level.getMaxBuildHeight() - 1);
        long count = 0;
        long missing = 0;
        long first = Long.MIN_VALUE;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk column = level.getChunkSource().getChunkNow(cx, cz);
                if (column == null) {
                    missing++;
                    continue;
                }
                int x0 = Math.max(cx << 4, minX);
                int x1 = Math.min((cx << 4) + 15, maxX);
                int z0 = Math.max(cz << 4, minZ);
                int z1 = Math.min((cz << 4) + 15, maxZ);
                LevelChunkSection[] sections = column.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (!section.maybeHas(HavenAutotest::flowing)) {
                        continue;
                    }
                    int base = SectionPos.sectionToBlockCoord(column.getSectionYFromSectionIndex(i));
                    for (int y = Math.max(base, minY); y <= Math.min(base + 15, maxY); y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                if (flowing(section.getBlockState(x & 15, y & 15, z & 15))) {
                                    if (count == 0) {
                                        first = BlockPos.asLong(x, y, z);
                                    }
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return new long[]{count, missing, first};
    }

    private static boolean flowing(BlockState state) {
        FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !fluid.isSource();
    }

    /** Tient la boite en tick d'entites : l'eau ne coule que dans un troncon qui tique. */
    private static void hold(ServerLevel level, HavenState state) {
        BlockPos o = state.origin();
        for (int cx = o.getX() >> 4; cx <= (o.getX() + state.width() - 1) >> 4; cx++) {
            for (int cz = o.getZ() >> 4; cz <= (o.getZ() + state.depth() - 1) >> 4; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(JakBuilder.TICKET, pos, TICKING_DISTANCE, pos);
                HELD.add(pos);
            }
        }
    }

    private static void end(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        if (level != null) {
            for (ChunkPos pos : HELD) {
                level.getChunkSource().removeRegionTicket(JakBuilder.TICKET, pos, TICKING_DISTANCE, pos);
            }
        }
        HELD.clear();
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("haven_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest haven : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest haven : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        stage = Stage.END;
        server.halt(false);
    }

    private static void header() {
        if (OUT.length() == 0) {
            line("autotest de la ville de Haven, " + LocalDateTime.now().withNano(0));
        }
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest haven : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }

    /** L'etat que le volume met dans une cellule, lu au hasard dans ses plages. */
    private static BlockState cell(JakVolume volume, BlockPos cell) {
        return volume.stateAt(cell.getX(), cell.getY(), cell.getZ());
    }

    private static boolean same(BlockState found, BlockState wanted) {
        return found == wanted || (found.isAir() && wanted.isAir());
    }

    private static String name(@Nullable BlockState state) {
        if (state == null) {
            return "?";
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && !fluid.isSource()) {
            id += " (coule)";
        }
        return id;
    }

    private static String where(long[] flowing) {
        return flowing[2] == Long.MIN_VALUE ? "" : ", le premier en " + BlockPos.of(flowing[2]).toShortString();
    }

    private static long ms(long nanos) {
        return nanos / 1_000_000L;
    }

    private static long mb(long bytes) {
        return bytes >> 20;
    }
}
