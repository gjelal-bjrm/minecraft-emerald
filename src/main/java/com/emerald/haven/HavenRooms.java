package com.emerald.haven;

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
import java.util.List;
import java.util.Optional;

/**
 * Les salles et le QG de la ville, tels que les decrit haven_rooms.json.
 *
 * TOUT EST EN CELLULES DU VOLUME, jamais en coordonnees du monde : le fichier
 * est ecrit par le voxeliseur, qui ne sait pas ou la ville sera posee. Un bloc
 * du monde vaut origine de pose ({@link HavenState#origin()}) plus cellule.
 *
 * Le fichier est lu par le gestionnaire de ressources du serveur, comme le
 * volume : il vit dans data/, donc un /reload ou un datapack le remplace sans
 * reconstruire le mod. La lecture est gardee tant que le gestionnaire est le
 * meme ; un /reload en fournit un autre, et le fichier est relu.
 */
public final class HavenRooms {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "jak/haven_rooms.json");

    /** Une boite de cellules, bornes incluses. */
    public record Box(BlockPos min, BlockPos max) {

        public boolean contains(int x, int y, int z) {
            return x >= this.min.getX() && x <= this.max.getX()
                    && y >= this.min.getY() && y <= this.max.getY()
                    && z >= this.min.getZ() && z <= this.max.getZ();
        }

        public boolean contains(BlockPos cell) {
            return contains(cell.getX(), cell.getY(), cell.getZ());
        }

        /** La meme boite, grandie de n cellules dans les six directions. */
        public Box inflate(int n) {
            return new Box(this.min.offset(-n, -n, -n), this.max.offset(n, n, n));
        }

        public int sizeX() {
            return this.max.getX() - this.min.getX() + 1;
        }

        public int sizeY() {
            return this.max.getY() - this.min.getY() + 1;
        }

        public int sizeZ() {
            return this.max.getZ() - this.min.getZ() + 1;
        }
    }

    /** Une place de vehicule devant une salle : celle de la voiture, ou celle de la moto a cote. */
    public record Car(Box place, BlockPos floor, float yaw) {
    }

    /**
     * Un appartement.
     *
     * @param number  son numero dans les commandes, a partir de 1
     * @param box     l'air interieur
     * @param spawns  des cellules de SOL : le joueur se tient une cellule au-dessus
     * @param door    l'ouverture laissee dans le mur
     * @param car     la place de la voiture devant la porte
     * @param bike    la place de la moto monoplace, a cote de la voiture
     */
    public record Room(int number, String id, Box box, int capacity, List<BlockPos> spawns,
                       @Nullable Box door, @Nullable Car car, @Nullable Car bike) {

        /**
         * L'interieur et sa coque : murs, sol et plafond.
         *
         * Le releve couvre la coque parce qu'amenager, c'est aussi toucher au
         * mur interieur -- une niche, une fenetre, un bloc remplace -- et qu'un
         * changement hors de la boite d'air ne serait sinon jamais vu.
         */
        public Box envelope() {
            return this.box.inflate(1);
        }
    }

    /** Le Hip Hog : son emprise, son comptoir et la place de l'element de vote. */
    public record Hq(Box box, @Nullable Box counter, @Nullable BlockPos voteFloor) {
    }

    /** Le contenu du fichier. */
    public record Data(String volume, String sha1, List<Room> rooms, @Nullable Hq hq) {
    }

    @Nullable
    private static ResourceManager readFrom;
    @Nullable
    private static Data cached;

    private HavenRooms() {
    }

    /** Les salles du fichier, ou null s'il manque ou ne se lit pas (la raison est au journal). */
    @Nullable
    public static synchronized Data get(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        if (manager != readFrom) {
            readFrom = manager;
            cached = read(manager);
        }
        return cached;
    }

    /** La salle de ce numero, ou null. */
    @Nullable
    public static Room room(MinecraftServer server, int number) {
        Data data = get(server);
        if (data == null) {
            return null;
        }
        for (Room room : data.rooms()) {
            if (room.number() == number) {
                return room;
            }
        }
        return null;
    }

    /** La salle dont la coque contient cette cellule, ou null. */
    @Nullable
    public static Room roomAtCell(MinecraftServer server, BlockPos cell) {
        Data data = get(server);
        if (data == null) {
            return null;
        }
        for (Room room : data.rooms()) {
            if (room.envelope().contains(cell)) {
                return room;
            }
        }
        return null;
    }

    @Nullable
    private static Data read(ResourceManager manager) {
        Optional<Resource> found = manager.getResource(FILE);
        if (found.isEmpty()) {
            LOGGER.error("salles de la ville : {} absent", FILE);
            return null;
        }
        try (Reader reader = found.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Room> rooms = new ArrayList<>();
            JsonArray list = root.getAsJsonArray("rooms");
            for (int i = 0; i < list.size(); i++) {
                JsonObject room = list.get(i).getAsJsonObject();
                List<BlockPos> spawns = new ArrayList<>();
                if (room.has("spawns")) {
                    for (JsonElement spawn : room.getAsJsonArray("spawns")) {
                        spawns.add(pos(spawn.getAsJsonArray()));
                    }
                }
                rooms.add(new Room(i + 1, room.get("id").getAsString(), box(room.getAsJsonObject("box")),
                        room.has("capacity") ? room.get("capacity").getAsInt() : 3, List.copyOf(spawns),
                        room.has("door") ? box(room.getAsJsonObject("door")) : null,
                        vehiclePlace(room, "car"), vehiclePlace(room, "bike")));
            }
            Hq hq = null;
            if (root.has("hq")) {
                JsonObject object = root.getAsJsonObject("hq");
                JsonObject vote = object.has("vote") ? object.getAsJsonObject("vote") : null;
                hq = new Hq(box(object.getAsJsonObject("box")),
                        object.has("counter") ? box(object.getAsJsonObject("counter")) : null,
                        vote != null && vote.has("floor") ? pos(vote.getAsJsonArray("floor")) : null);
            }
            return new Data(root.get("volume").getAsString(), root.get("sha1").getAsString(),
                    List.copyOf(rooms), hq);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("salles de la ville : {} illisible", FILE, e);
            return null;
        }
    }

    /** La place de vehicule {@code key} ("car" ou "bike") d'une salle, ou null si le fichier n'en a pas. */
    @Nullable
    private static Car vehiclePlace(JsonObject room, String key) {
        if (!room.has(key)) {
            return null;
        }
        JsonObject place = room.getAsJsonObject(key);
        return new Car(box(place), pos(place.getAsJsonArray("floor")), place.get("yaw").getAsFloat());
    }

    private static Box box(JsonObject object) {
        return new Box(pos(object.getAsJsonArray("min")), pos(object.getAsJsonArray("max")));
    }

    private static BlockPos pos(JsonArray array) {
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }
}
