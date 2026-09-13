package com.emerald.haven;

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
        check("porte du bar en cellule (361, 66, 197)",
                same(doorGot, doorWanted) && same(doorUp, doorUpWanted),
                "monde " + name(doorGot) + " / " + name(doorUp) + ", volume " + name(doorWanted)
                        + " / " + name(doorUpWanted) + ", en " + o.offset(door).toShortString());

        long[] flowing = countFlowing(level, state);
        check("eau qui coule dans la boite juste apres la pose", flowing[0] == 0 && flowing[1] == 0,
                flowing[0] + " blocs" + where(flowing) + ", " + flowing[1] + " troncons non charges");

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
