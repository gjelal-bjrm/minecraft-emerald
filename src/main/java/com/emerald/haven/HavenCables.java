package com.emerald.haven;

import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LES CABLES DU PORT EN CHAINES (cahier §94).
 *
 * « Les cables qui relient les tours devraient etre plus fins, il faudrait des barres de fer
 * ou quelque chose de fin » (le joueur, 24 sept.). Le volume de la ville les pose en lignes de
 * blocs pleins -- des cables d'un metre. Choix du joueur : des CHAINES.
 *
 * PAS EN BLOCS. Le premier essai posait une chaine dans chaque cellule, sur l'axe principal de
 * son troncon : la ou le cable monte ou descend, il avance en escalier, et chaque marche etait
 * un bout de chaine horizontal decale d'un bloc du suivant -- « elles ne sont pas reliees entre
 * elles verticalement, on dirait qu'elles flottent dans le vide » (le joueur, photo a l'appui).
 * Un bloc de chaine est droit et centre ; un cable en biais ne se dessine pas avec. Desormais la
 * ville posee VIDE les cellules des cables, et le client dessine chaque cable entier comme une
 * chaine continue le long de sa vraie ligne, celle du decor de Jak 3 (HavenCableRenderer).
 *
 * Les donnees : haven_cables.json, ecrit par tools/jak_cables.py d'apres le volume tel quel --
 * on ne touche pas au volume, dont le sha1 tient le releve de l'atelier, les salles, la faune,
 * l'invasion et la ville posee de chaque monde. « cells » : les murs que le voxeliseur a poses
 * le long des cables ; « lines » : chaque cable recousu bout a bout, en polyligne, avec pour
 * chaque bout une cellule TEMOIN, un bloc plein de la tour ou il s'accroche : le client ne
 * dessine un cable que si ce bloc est la, c'est-a-dire si la ville est posee.
 *
 * QUAND. Apres chaque pose de la ville (HavenSite.done), avant le rejeu des releves ; et au
 * demarrage sur une ville deja posee, tant que l'etat de Haven n'est pas a la version 2 -- les
 * murs d'origine et les chaines du premier essai s'en vont pareil. Ce que le joueur aurait mis a
 * la place d'un cable reste. Le releve de l'atelier ignore ces cellules (JakCityCapture).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenCables {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);
    private static final String PATH = "/data/emeraldweapons/jak/haven_cables.json";
    /** Ce que la ville posee doit etre : les cellules vides, la chaine dessinee (HavenState.cables). */
    public static final int VERSION = 2;
    /** Le mur du volume (jak_voxelize.PALETTE[WALL]) et la chaine du premier essai : ils s'en vont. */
    private static final Block WALL = Blocks.DEEPSLATE_BRICKS;
    /** 2|16 : ni voisins prevenus, ni formes recalculees. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private HavenCables() {
    }

    /** Une cellule du volume que la ville posee vide. */
    public record Cell(int x, int y, int z, boolean rail) {
    }

    /**
     * Un cable entier : sa polyligne en cellules du volume (flottantes), et pour chaque bout la
     * cellule d'un bloc plein de la tour ou il s'accroche (null s'il n'y en a pas).
     */
    public record Line(boolean rail, List<Vec3> points, @Nullable BlockPos startWitness, @Nullable BlockPos endWitness) {
    }

    private record Data(String sha1, List<Cell> cells, Set<Long> keys, List<Line> lines) {
        static final Data NONE = new Data("", List.of(), Set.of(), List.of());
    }

    private static final class Holder {
        static final Data DATA = load();
    }

    /** Les cellules, pour qui veut les compter ou les parcourir (le banc). */
    public static List<Cell> cells() {
        return Holder.DATA.cells();
    }

    /** Les cables entiers : la chaine les suit (HavenCableRenderer), le JET-Board y glissera. */
    public static List<Line> lines() {
        return Holder.DATA.lines();
    }

    /** Cette cellule du volume est-elle un cable ? (coordonnees de cellule, pas du monde) */
    public static boolean isCable(int x, int y, int z) {
        return Holder.DATA.keys().contains(BlockPos.asLong(x, y, z));
    }

    /** Apres la pose de la ville : les cellules des cables vides, et l'etat le note. */
    public static void afterPose(MinecraftServer server, ServerLevel level, HavenState state) {
        int changed = apply(level, state);
        state.setCables(VERSION);
        LOGGER.info("Haven : {} cellules de cables videes apres la pose (les chaines sont dessinees)", changed);
    }

    /** Au demarrage, une ville deja posee dont les cables sont encore des murs, ou des chaines en blocs. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        HavenState state = HavenState.get(server);
        if (!state.built() || state.cables() >= VERSION) {
            return;
        }
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return;
        }
        long start = System.nanoTime();
        int changed = apply(level, state);
        int before = state.cables();
        state.setCables(VERSION);
        LOGGER.info("Haven : {} cellules de cables videes sur la ville deja posee (version {} -> {}), en {} ms",
                changed, before, VERSION, (System.nanoTime() - start) / 1_000_000L);
    }

    /**
     * Chaque cellule listee qui porte encore le mur du volume, ou la chaine du premier essai,
     * devient de l'air. Les troncons sont charges au passage (une centaine, une fois).
     *
     * @return le nombre de blocs changes
     */
    public static int apply(ServerLevel level, HavenState state) {
        Data data = Holder.DATA;
        if (data.cells().isEmpty()) {
            return 0;
        }
        if (!data.sha1().equals(state.sha1())) {
            LOGGER.warn("Haven : les cables sont tires du volume {}, la ville posee est {} : rien de change",
                    data.sha1(), state.sha1());
            return 0;
        }
        BlockPos origin = state.origin();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (Cell cell : data.cells()) {
            pos.set(origin.getX() + cell.x(), origin.getY() + cell.y(), origin.getZ() + cell.z());
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            BlockState existing = level.getBlockState(pos);
            if (!existing.is(WALL) && !existing.is(Blocks.CHAIN)) {
                continue;                     // deja vide, ou autre chose que le joueur a voulu
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            changed++;
        }
        return changed;
    }

    private static Data load() {
        try (InputStream in = HavenCables.class.getResourceAsStream(PATH)) {
            if (in == null) {
                LOGGER.error("Haven : {} absent du jar, les cables restent des murs", PATH);
                return Data.NONE;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray array = root.getAsJsonArray("cells");
                List<Cell> cells = new ArrayList<>(array.size());
                Set<Long> keys = new HashSet<>(array.size() * 2);
                for (int i = 0; i < array.size(); i++) {
                    JsonArray c = array.get(i).getAsJsonArray();
                    Cell cell = new Cell(c.get(0).getAsInt(), c.get(1).getAsInt(), c.get(2).getAsInt(),
                            "rail".equals(c.get(4).getAsString()));
                    cells.add(cell);
                    keys.add(BlockPos.asLong(cell.x(), cell.y(), cell.z()));
                }
                List<Line> lines = new ArrayList<>();
                for (JsonElement element : root.getAsJsonArray("lines")) {
                    JsonObject line = element.getAsJsonObject();
                    List<Vec3> points = new ArrayList<>();
                    for (JsonElement p : line.getAsJsonArray("points")) {
                        JsonArray v = p.getAsJsonArray();
                        points.add(new Vec3(v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble()));
                    }
                    JsonArray witness = line.getAsJsonArray("witness");
                    lines.add(new Line("rail".equals(line.get("kind").getAsString()), List.copyOf(points),
                            cellOrNull(witness.get(0)), cellOrNull(witness.get(1))));
                }
                return new Data(root.get("sha1").getAsString(), List.copyOf(cells), Set.copyOf(keys), List.copyOf(lines));
            }
        } catch (Exception e) {
            LOGGER.error("Haven : {} illisible, les cables restent des murs", PATH, e);
            return Data.NONE;
        }
    }

    @Nullable
    private static BlockPos cellOrNull(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        JsonArray v = element.getAsJsonArray();
        return new BlockPos(v.get(0).getAsInt(), v.get(1).getAsInt(), v.get(2).getAsInt());
    }
}
