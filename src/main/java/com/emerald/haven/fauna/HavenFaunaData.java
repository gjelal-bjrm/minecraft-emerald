package com.emerald.haven.fauna;

import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La carte de la faune de Haven, telle que la decrit haven_fauna.json (tools/haven_fauna_map.py).
 *
 * L'EAU DU PORT, colonne par colonne : le BASSIN (l'eau fermee par l'arc de la ville
 * et la jetee des deux tours) et le LARGE (toute eau reliee au bord de la grille),
 * avec leur profondeur ; et les QUAIS, cellules de pieds au bord de l'une ou de
 * l'autre. Tout est en cellules du volume, comme haven_invasion.json : un bloc du
 * monde vaut l'origine de pose plus la cellule.
 *
 * LU PAR LE GESTIONNAIRE DE RESSOURCES DU SERVEUR et garde tant qu'il est le meme
 * (un /reload relit le fichier), comme HavenInvasionData.
 */
public final class HavenFaunaData {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "jak/haven_fauna.json");

    public static final int TILE = 16;

    /** Ce qu'est une colonne d'eau. */
    public enum Water { NONE, BASIN, OPEN_SEA }

    /**
     * Une tuile de la carte.
     *
     * @param water  256 octets, ligne par ligne (dz puis dx) : 0 pas d'eau libre, 1 a 26 bassin
     *               (profondeur), -1 a -26 large (profondeur, en negatif)
     * @param basin  les colonnes du bassin, empaquetees dx | dz << 4
     * @param sea    les colonnes du large, de meme
     * @param quays  les quais, empaquetes comme HavenInvasionData.pack (cellules du volume)
     * @param quaySide 1 bassin, 2 large, pour chaque quai
     */
    public record Tile(int tx, int tz, byte[] water, short[] basin, short[] sea, int[] quays, byte[] quaySide) {

        public Water kind(int dx, int dz) {
            byte v = this.water[dz * TILE + dx];
            return v > 0 ? Water.BASIN : v < 0 ? Water.OPEN_SEA : Water.NONE;
        }

        public int depth(int dx, int dz) {
            return Math.abs(this.water[dz * TILE + dx]);
        }
    }

    /** Le contenu du fichier. */
    public record Data(String volume, String sha1, int width, int height, int depth, int surface,
                       Map<Long, Tile> tiles, List<Tile> list, int basinColumns, int seaColumns, int quayCells) {

        @Nullable
        public Tile tile(int cellX, int cellZ) {
            if (cellX < 0 || cellZ < 0) {
                return null;
            }
            return this.tiles.get(key(cellX / TILE, cellZ / TILE));
        }

        /** L'eau d'une colonne du volume. */
        public Water water(int cellX, int cellZ) {
            Tile tile = tile(cellX, cellZ);
            return tile == null ? Water.NONE : tile.kind(cellX % TILE, cellZ % TILE);
        }

        /** L'eau sous une position du monde. */
        public Water waterAt(BlockPos origin, double x, double z) {
            return water((int) Math.floor(x) - origin.getX(), (int) Math.floor(z) - origin.getZ());
        }
    }

    @Nullable
    private static ResourceManager readFrom;
    @Nullable
    private static Data cached;

    private HavenFaunaData() {
    }

    public static long key(int tx, int tz) {
        return ((long) tx << 32) | (tz & 0xFFFFFFFFL);
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

    @Nullable
    private static Data read(ResourceManager manager) {
        Optional<Resource> found = manager.getResource(FILE);
        if (found.isEmpty()) {
            LOGGER.error("faune de Haven : {} absent", FILE);
            return null;
        }
        try (Reader reader = found.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray dims = root.getAsJsonArray("dims");
            Map<Long, Tile> tiles = new HashMap<>();
            List<Tile> list = new ArrayList<>();
            int basinColumns = 0;
            int seaColumns = 0;
            int quayCells = 0;
            for (JsonElement e : root.getAsJsonArray("tuiles")) {
                JsonObject t = e.getAsJsonObject();
                JsonArray at = t.getAsJsonArray("tuile");
                int tx = at.get(0).getAsInt();
                int tz = at.get(1).getAsInt();
                byte[] water = new byte[TILE * TILE];
                List<Short> basin = new ArrayList<>();
                List<Short> sea = new ArrayList<>();
                if (t.has("eau")) {
                    String chars = t.get("eau").getAsString();
                    if (chars.length() != TILE * TILE) {
                        throw new IllegalStateException("tuile " + tx + "," + tz + " : " + chars.length() + " colonnes");
                    }
                    for (int i = 0; i < chars.length(); i++) {
                        char c = chars.charAt(i);
                        short packed = (short) ((i % TILE) | ((i / TILE) << 4));
                        if (c >= 'a' && c <= 'z') {
                            water[i] = (byte) (c - 'a' + 1);
                            basin.add(packed);
                        } else if (c >= 'A' && c <= 'Z') {
                            water[i] = (byte) -(c - 'A' + 1);
                            sea.add(packed);
                        }
                    }
                }
                int[] quays = new int[0];
                byte[] sides = new byte[0];
                if (t.has("quais")) {
                    JsonArray q = t.getAsJsonArray("quais");
                    quays = new int[q.size()];
                    sides = new byte[q.size()];
                    for (int i = 0; i < q.size(); i++) {
                        JsonArray a = q.get(i).getAsJsonArray();
                        quays[i] = pack(tx * TILE + a.get(0).getAsInt(), a.get(1).getAsInt(), tz * TILE + a.get(2).getAsInt());
                        sides[i] = (byte) a.get(3).getAsInt();
                    }
                }
                Tile tile = new Tile(tx, tz, water, shorts(basin), shorts(sea), quays, sides);
                tiles.put(key(tx, tz), tile);
                list.add(tile);
                basinColumns += basin.size();
                seaColumns += sea.size();
                quayCells += quays.length;
            }
            Data data = new Data(root.get("volume").getAsString(), root.get("sha1").getAsString(),
                    dims.get(0).getAsInt(), dims.get(1).getAsInt(), dims.get(2).getAsInt(),
                    root.get("eau_y").getAsInt(), Map.copyOf(tiles), List.copyOf(list), basinColumns, seaColumns, quayCells);
            LOGGER.info("faune de Haven : {} lu, {} tuiles, {} colonnes de bassin, {} de large, {} quais",
                    FILE, list.size(), basinColumns, seaColumns, quayCells);
            return data;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("faune de Haven : {} illisible", FILE, e);
            return null;
        }
    }

    private static short[] shorts(List<Short> in) {
        short[] out = new short[in.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = in.get(i);
        }
        return out;
    }

    // ------------------------------------------------------------- cellules empaquetees (comme HavenInvasionData)

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
}
