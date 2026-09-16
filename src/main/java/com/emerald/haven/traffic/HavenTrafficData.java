package com.emerald.haven.traffic;

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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Les voies du trafic civil de Jak 3 dans le port, telles que les decrit
 * haven_traffic.json (ecrit par tools/jak_navgraph.py depuis le nav-graph 112 de
 * ctyport-vis.go, que le decompilateur n'exporte pas).
 *
 * TOUT EST EN CELLULES DU VOLUME, comme haven_invasion.json : un bloc du monde vaut
 * l'origine de pose plus la cellule. Les noeuds portent l'altitude de la voie
 * haute a leur endroit -- la carte de hauteur de Jak 3, 75,0 presque partout sur
 * le port, quelques bosses --, un rayon de virage et un cap. Les branches sont a
 * SENS UNIQUE, de src vers dest ; une branche sans destination est une sortie du
 * quartier, avec le point vers lequel on s'eloigne avant de disparaitre.
 *
 * LU PAR LE GESTIONNAIRE DE RESSOURCES DU SERVEUR, garde tant que le gestionnaire
 * est le meme : un /reload en fournit un autre, et le fichier est relu.
 */
public final class HavenTrafficData {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "jak/haven_traffic.json");

    /**
     * Un noeud du graphe.
     *
     * @param y        l'altitude de la voie a ce noeud, en cellules
     * @param angleDeg le cap du noeud, dans le repere de Jak 3 : direction (sin a, 0, -cos a)
     * @param radius   le rayon de virage, en cellules
     * @param branches les indices des branches qui en partent
     */
    public record Node(int index, int id, double x, double y, double z, double angleDeg, double radius, int[] branches) {

        public Vec3 world(BlockPos origin) {
            return new Vec3(origin.getX() + this.x, origin.getY() + this.y, origin.getZ() + this.z);
        }
    }

    /**
     * Une branche, de {@code src} vers {@code dest} ; {@code dest} vaut -1 pour une
     * sortie, et {@code exitX}, {@code exitZ} disent alors vers ou l'on s'eloigne.
     *
     * @param length   la longueur a plat, en cellules
     * @param speedMs  la vitesse de croisiere de Jak 3, en m/s
     * @param width    la largeur de la voie, en cellules
     * @param maxUsers le nombre de vehicules au plus sur la branche (Jak 3)
     */
    public record Branch(int index, int src, int dest, double length, double speedMs, double width, int maxUsers,
                         double exitX, double exitZ) {

        public boolean exit() {
            return this.dest < 0;
        }
    }

    /** Le contenu du fichier. */
    public record Data(String volume, int graphId, double laneY, List<Node> nodes, List<Branch> branches) {

        public Node node(int index) {
            return this.nodes.get(index);
        }

        public Branch branch(int index) {
            return this.branches.get(index);
        }

        /** Le depart d'une branche, dans le monde. */
        public Vec3 start(Branch branch, BlockPos origin) {
            return node(branch.src()).world(origin);
        }

        /** Le bout d'une branche, dans le monde : son noeud d'arrivee, ou le point de sortie a l'altitude du depart. */
        public Vec3 end(Branch branch, BlockPos origin) {
            if (!branch.exit()) {
                return node(branch.dest()).world(origin);
            }
            Node from = node(branch.src());
            return new Vec3(origin.getX() + branch.exitX(), origin.getY() + from.y(), origin.getZ() + branch.exitZ());
        }
    }

    @Nullable
    private static ResourceManager readFrom;
    @Nullable
    private static Data cached;

    private HavenTrafficData() {
    }

    /** Les voies, ou null si le fichier manque ou ne se lit pas (la raison est au journal). */
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
            LOGGER.error("trafic de Haven : {} absent", FILE);
            return null;
        }
        try (Reader reader = found.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Node> nodes = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("nodes")) {
                JsonObject n = e.getAsJsonObject();
                JsonArray out = n.getAsJsonArray("branches");
                int[] branches = new int[out.size()];
                for (int i = 0; i < branches.length; i++) {
                    branches[i] = out.get(i).getAsInt();
                }
                nodes.add(new Node(n.get("index").getAsInt(), n.get("id").getAsInt(), n.get("x").getAsDouble(),
                        n.get("y").getAsDouble(), n.get("z").getAsDouble(), n.get("angle_deg").getAsDouble(),
                        n.get("radius").getAsDouble(), branches));
            }
            List<Branch> branches = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("branches")) {
                JsonObject b = e.getAsJsonObject();
                boolean exit = !b.has("dest") || b.get("dest").isJsonNull();
                branches.add(new Branch(b.get("index").getAsInt(), b.get("src").getAsInt(),
                        exit ? -1 : b.get("dest").getAsInt(), b.get("length").getAsDouble(),
                        b.get("speed_ms").getAsDouble(), b.get("width").getAsDouble(), b.get("max_users").getAsInt(),
                        exit ? b.get("exit_x").getAsDouble() : 0.0, exit ? b.get("exit_z").getAsDouble() : 0.0));
            }
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).index() != i) {
                    throw new IllegalStateException("noeud " + i + " numerote " + nodes.get(i).index());
                }
            }
            for (int i = 0; i < branches.size(); i++) {
                Branch b = branches.get(i);
                if (b.index() != i || b.src() < 0 || b.src() >= nodes.size() || b.dest() >= nodes.size()) {
                    throw new IllegalStateException("branche " + i + " : " + b);
                }
            }
            Data data = new Data(root.get("volume").getAsString(), root.get("graph_id").getAsInt(),
                    root.get("lane_y_default").getAsDouble(), List.copyOf(nodes), List.copyOf(branches));
            LOGGER.info("trafic de Haven : {} lu, graphe {}, {} noeuds, {} branches dont {} sortie(s)", FILE,
                    data.graphId(), nodes.size(), branches.size(), branches.stream().filter(Branch::exit).count());
            return data;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("trafic de Haven : {} illisible", FILE, e);
            return null;
        }
    }
}
