package com.emerald.haven.invasion;

import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * La carte de l'invasion de Haven, telle que la decrit haven_invasion.json.
 *
 * LA CARTE VALIDEE PAR LE JOUEUR (13 sept.), recopiee proprement : zones
 * protegees, zones sures, tuiles d'apparition au sol avec leurs cellules de
 * pieds exactes, tuiles de vol des phantoms, douze points d'eco, place du
 * bouton du QG. Le format est documente dans le champ « _format » du fichier.
 *
 * TOUT EST EN CELLULES DU VOLUME, comme haven_rooms.json : un bloc du monde vaut
 * l'origine de pose ({@code HavenState.origin()}) plus la cellule. Les helpers
 * « World » font l'addition.
 *
 * LU PAR LE GESTIONNAIRE DE RESSOURCES DU SERVEUR, garde tant que le
 * gestionnaire est le meme : un /reload en fournit un autre, et le fichier est
 * relu (comme HavenRooms).
 */
public final class HavenInvasionData {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "jak/haven_invasion.json");

    /** La hauteur de pieds de la lettre « A » dans les colonnes des tuiles. */
    private static final int FEET_BASE = 58;
    /** Une tuile couvre 16 x 16 cellules. */
    public static final int TILE = 16;

    /** Les couleurs d'eco de Jak 3. */
    public enum EcoColor {
        RED, YELLOW, BLUE, DARK;

        static EcoColor byName(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    /** Une boite de cellules, bornes incluses. */
    public record Box(BlockPos min, BlockPos max) {

        public boolean contains(int x, int y, int z) {
            return x >= this.min.getX() && x <= this.max.getX()
                    && y >= this.min.getY() && y <= this.max.getY()
                    && z >= this.min.getZ() && z <= this.max.getZ();
        }

        public Box inflate(int n) {
            return new Box(this.min.offset(-n, -n, -n), this.max.offset(n, n, n));
        }
    }

    /** Une zone nommee : protegee (type appartement, porte, place, bar, entree, borne, rideau) ou sure (type vide). */
    public record Zone(String name, String type, Box box) {
    }

    /** Un centre d'exclusion des apparitions : porte d'appartement ou entree du bar, en cellules. */
    public record Center(String name, double x, double z) {
    }

    /**
     * Une tuile d'apparition au sol.
     *
     * @param cells les cellules de pieds, empaquetees par {@link #pack} : x | z << 11 | y << 21
     */
    public record GroundTile(int tx, int tz, int openSky, int[] cells) {
    }

    /** Une tuile de vol des phantoms : apparition entre plancher et plafond, en cellules. */
    public record PhantomTile(int tx, int tz, int floor, int ceiling) {
    }

    /**
     * Un point d'eco.
     *
     * @param feet   la cellule d'air ou poser le ramassage
     * @param ground la cellule pleine dessous
     */
    public record EcoPoint(int number, String name, EcoColor color, BlockPos feet, BlockPos ground, boolean openSky) {

        /** Les pieds en coordonnees du monde. */
        public BlockPos feetWorld(BlockPos origin) {
            return origin.offset(this.feet);
        }
    }

    /** La place du bouton du QG : la cellule du bloc, le comptoir dessous, le cote de l'embleme. */
    public record Button(BlockPos cell, BlockPos floor, Direction facing) {
    }

    /** Le contenu du fichier. */
    public record Data(String volume, String sha1, int width, int height, int depth, int waterMaxY,
                       int exclusionRadius, List<Center> centers, List<Zone> protectedZones, List<Zone> safeZones,
                       List<GroundTile> groundTiles, int groundCells, List<PhantomTile> phantomTiles,
                       List<EcoPoint> ecoPoints, @Nullable Button button) {
    }

    @Nullable
    private static ResourceManager readFrom;
    @Nullable
    private static Data cached;

    private HavenInvasionData() {
    }

    /** La carte, ou null si le fichier manque ou ne se lit pas (la raison est au journal). */
    @Nullable
    public static synchronized Data get(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        if (manager != readFrom) {
            readFrom = manager;
            cached = read(manager);
        }
        return cached;
    }

    /** Les douze points d'eco, dans l'ordre du fichier ; vide si la carte manque. */
    public static List<EcoPoint> ecoPoints(MinecraftServer server) {
        Data data = get(server);
        return data == null ? List.of() : data.ecoPoints();
    }

    // ------------------------------------------------------------- cellules empaquetees

    public static int pack(int x, int y, int z) {
        return x | (z << 11) | (y << 21);
    }

    public static int unpackX(int packed) {
        return packed & 0x7FF;
    }

    public static int unpackZ(int packed) {
        return (packed >>> 11) & 0x3FF;
    }

    public static int unpackY(int packed) {
        return (packed >>> 21) & 0xFF;
    }

    // ------------------------------------------------------------- lecture

    @Nullable
    private static Data read(ResourceManager manager) {
        Optional<Resource> found = manager.getResource(FILE);
        if (found.isEmpty()) {
            LOGGER.error("invasion de Haven : {} absent", FILE);
            return null;
        }
        try (Reader reader = found.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray dims = root.getAsJsonArray("dims");

            List<Center> centers = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("centres_exclusion")) {
                JsonObject c = e.getAsJsonObject();
                centers.add(new Center(c.get("nom").getAsString(), c.get("x").getAsDouble(), c.get("z").getAsDouble()));
            }
            List<Zone> protectedZones = zones(root.getAsJsonArray("zones_protegees"));
            List<Zone> safeZones = zones(root.getAsJsonArray("zone_sure"));

            List<GroundTile> ground = new ArrayList<>();
            int groundCells = 0;
            for (JsonElement e : root.getAsJsonObject("apparition_sol").getAsJsonArray("tuiles")) {
                GroundTile tile = groundTile(e.getAsJsonObject());
                ground.add(tile);
                groundCells += tile.cells().length;
            }
            int declared = root.getAsJsonObject("apparition_sol").get("cellules").getAsInt();
            if (declared != groundCells) {
                throw new IllegalStateException("apparition_sol : " + groundCells + " cellules lues, " + declared + " annoncees");
            }

            List<PhantomTile> phantoms = new ArrayList<>();
            for (JsonElement e : root.getAsJsonObject("vol_phantoms").getAsJsonArray("tuiles")) {
                JsonObject t = e.getAsJsonObject();
                JsonArray tile = t.getAsJsonArray("tuile");
                phantoms.add(new PhantomTile(tile.get(0).getAsInt(), tile.get(1).getAsInt(),
                        t.get("plancher").getAsInt(), t.get("plafond").getAsInt()));
            }

            List<EcoPoint> eco = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("points_eco")) {
                JsonObject p = e.getAsJsonObject();
                eco.add(new EcoPoint(p.get("n").getAsInt(), p.get("nom").getAsString(),
                        EcoColor.byName(p.get("couleur").getAsString()), pos(p.getAsJsonArray("pieds")),
                        pos(p.getAsJsonArray("sol")), p.get("ciel_ouvert").getAsBoolean()));
            }

            Button button = null;
            if (root.has("bouton_qg")) {
                JsonObject b = root.getAsJsonObject("bouton_qg");
                Direction facing = Direction.byName(b.get("facing").getAsString());
                button = new Button(pos(b.getAsJsonArray("cellule")), pos(b.getAsJsonArray("sol")),
                        facing == null || facing.getAxis().isVertical() ? Direction.SOUTH : facing);
            }

            Data data = new Data(root.get("volume").getAsString(), root.get("sha1").getAsString(),
                    dims.get(0).getAsInt(), dims.get(1).getAsInt(), dims.get(2).getAsInt(),
                    root.get("eau_y_max").getAsInt(), root.get("rayon_exclusion").getAsInt(),
                    List.copyOf(centers), List.copyOf(protectedZones), List.copyOf(safeZones),
                    List.copyOf(ground), groundCells, List.copyOf(phantoms), List.copyOf(eco), button);
            LOGGER.info("invasion de Haven : {} lu, {} tuiles au sol ({} cellules), {} tuiles de phantoms, {} points d'eco",
                    FILE, ground.size(), groundCells, phantoms.size(), eco.size());
            return data;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("invasion de Haven : {} illisible", FILE, e);
            return null;
        }
    }

    private static List<Zone> zones(JsonArray array) {
        List<Zone> out = new ArrayList<>();
        for (JsonElement e : array) {
            JsonObject z = e.getAsJsonObject();
            out.add(new Zone(z.get("nom").getAsString(), z.has("type") ? z.get("type").getAsString() : "",
                    new Box(pos(z.getAsJsonArray("min")), pos(z.getAsJsonArray("max")))));
        }
        return out;
    }

    /** « pieds » : 256 lettres, dz puis dx ; '.' rien, 'A' = 58. « etages » : [dx, y, dz] en plus. */
    private static GroundTile groundTile(JsonObject t) {
        JsonArray tile = t.getAsJsonArray("tuile");
        int tx = tile.get(0).getAsInt();
        int tz = tile.get(1).getAsInt();
        String feet = t.get("pieds").getAsString();
        if (feet.length() != TILE * TILE) {
            throw new IllegalStateException("tuile " + tx + "," + tz + " : " + feet.length() + " colonnes");
        }
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < feet.length(); i++) {
            char c = feet.charAt(i);
            if (c == '.') {
                continue;
            }
            int dz = i / TILE;
            int dx = i % TILE;
            cells.add(pack(tx * TILE + dx, FEET_BASE + (c - 'A'), tz * TILE + dz));
        }
        if (t.has("etages")) {
            for (JsonElement e : t.getAsJsonArray("etages")) {
                JsonArray a = e.getAsJsonArray();
                cells.add(pack(tx * TILE + a.get(0).getAsInt(), a.get(1).getAsInt(), tz * TILE + a.get(2).getAsInt()));
            }
        }
        if (cells.size() != t.get("cellules").getAsInt()) {
            throw new IllegalStateException("tuile " + tx + "," + tz + " : " + cells.size() + " cellules lues, "
                    + t.get("cellules").getAsInt() + " annoncees");
        }
        int[] packed = new int[cells.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = cells.get(i);
        }
        return new GroundTile(tx, tz, t.get("ciel_ouvert").getAsInt(), packed);
    }

    private static BlockPos pos(JsonArray array) {
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }
}
