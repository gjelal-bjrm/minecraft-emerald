package com.emerald.haven.traffic;

import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * La carte de hauteur de la voie haute de Jak 3 sur le port : *traffic-height-map*,
 * decoupee et mise en cellules par tools/jak_navgraph.py (haven_lane_map.json).
 *
 * Le jeu pose ses vehicules a cette altitude, ceux du trafic comme celui du joueur
 * (hvehicle.gc:619 et 726-728) : 17,5 m -- la cellule 75,0 -- presque partout, avec
 * des BOSSES et des CREUX. Le fichier porte deux grilles :
 *
 *  - {@code rise}, la carte du jeu telle quelle : celle du trafic des habitants ;
 *  - {@code player_rise}, celle de la voie haute du joueur, qui a demande qu'elle ne
 *    monte « que quand ce n'est pas assez haut » : l'outil n'y garde que les bosses
 *    qui degagent un obstacle du volume. Une seule aujourd'hui, +10 blocs au-dessus
 *    du pont entre les deux tours, dont le tablier monte a la cellule 75 : a plat,
 *    la voiture le heurtait de cote. Les quatre autres bosses du jeu ne degagent
 *    rien chez nous, et les creux ne sont jamais suivis : la voie du joueur ne
 *    descend pas sous sa base.
 *
 * LUE DANS LE JAR, PAR LES DEUX COTES, et non par le gestionnaire de ressources du
 * serveur : c'est le client du conducteur qui simule sa voiture
 * (VehiclePhysics.tick), et il lui faut le meme plancher qu'au serveur. Sans
 * fichier lisible, la voie est plate, comme avant la carte.
 *
 * Interpolation bilineaire entre des points espaces de 24 blocs, coordonnees
 * bornees a la decoupe comme get-height-at-point (height-map.gc:86-111) ; le tour
 * de la decoupe est nul (l'outil le verifie), donc la voie est plate au large.
 */
public final class HavenLaneMap {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final String PATH = "/data/" + EmeraldWeaponsMod.MODID + "/jak/haven_lane_map.json";

    /** Une grille : {@code rise[iz * xDim + ix]} au point (x0 + ix pas, z0 + iz pas), en cellules. */
    private record Grid(double x0, double z0, double spacing, int xDim, int zDim, double[] rise) {

        static final Grid FLAT = new Grid(0.0, 0.0, 1.0, 2, 2, new double[4]);

        double at(double cellX, double cellZ) {
            double fx = Math.max(0.0, Math.min((cellX - this.x0) / this.spacing, this.xDim - 1.001));
            double fz = Math.max(0.0, Math.min((cellZ - this.z0) / this.spacing, this.zDim - 1.001));
            int ix = (int) fx;
            int iz = (int) fz;
            double tx = fx - ix;
            double tz = fz - iz;
            int row = iz * this.xDim + ix;
            double top = this.rise[row] * (1.0 - tx) + this.rise[row + 1] * tx;
            double bottom = this.rise[row + this.xDim] * (1.0 - tx) + this.rise[row + this.xDim + 1] * tx;
            return top * (1.0 - tz) + bottom * tz;
        }

        double highest() {
            double highest = 0.0;
            for (double v : this.rise) {
                highest = Math.max(highest, v);
            }
            return highest;
        }
    }

    /** Les deux grilles du fichier. */
    private record Maps(Grid traffic, Grid player) {
        static final Maps FLAT = new Maps(Grid.FLAT, Grid.FLAT);
    }

    /** Chargees au premier appel, une fois, quel que soit le fil (client et serveur integre). */
    private static final class Holder {
        static final Maps MAPS = load();
    }

    private HavenLaneMap() {
    }

    /**
     * La hauteur de la voie de Jak 3 au-dessus de sa base (cellule 75,0), en blocs :
     * positive sur une bosse, negative dans un creux. Celle du trafic.
     */
    public static double rise(double cellX, double cellZ) {
        return Holder.MAPS.traffic.at(cellX, cellZ);
    }

    /**
     * Celle de la voie haute du joueur : les bosses utiles seulement, jamais sous la
     * base. Sa hauteur maximale ne change que la ou elle ne suffisait pas.
     */
    public static double playerRise(double cellX, double cellZ) {
        return Math.max(0.0, Holder.MAPS.player.at(cellX, cellZ));
    }

    /** La plus haute bosse de la voie du joueur, en blocs. */
    public static double playerHighest() {
        return Holder.MAPS.player.highest();
    }

    private static Maps load() {
        try (InputStream in = HavenLaneMap.class.getResourceAsStream(PATH)) {
            if (in == null) {
                LOGGER.error("Haven : {} absent du jar, la voie haute reste plate", PATH);
                return Maps.FLAT;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                return new Maps(grid(root, "rise"), grid(root, "player_rise"));
            }
        } catch (Exception e) {
            LOGGER.error("Haven : {} illisible, la voie haute reste plate", PATH, e);
            return Maps.FLAT;
        }
    }

    private static Grid grid(JsonObject root, String name) {
        int xDim = root.get("x_dim").getAsInt();
        int zDim = root.get("z_dim").getAsInt();
        JsonArray rows = root.getAsJsonArray(name);
        if (xDim < 2 || zDim < 2 || rows == null || rows.size() != zDim) {
            throw new IllegalStateException(name + " : dimensions " + xDim + " x " + zDim + ", "
                    + (rows == null ? "aucune" : String.valueOf(rows.size())) + " rangee(s)");
        }
        double[] rise = new double[xDim * zDim];
        for (int iz = 0; iz < zDim; iz++) {
            JsonArray row = rows.get(iz).getAsJsonArray();
            if (row.size() != xDim) {
                throw new IllegalStateException(name + ", rangee " + iz + " : " + row.size() + " points pour " + xDim);
            }
            for (int ix = 0; ix < xDim; ix++) {
                rise[iz * xDim + ix] = row.get(ix).getAsDouble();
            }
        }
        return new Grid(root.get("x0").getAsDouble(), root.get("z0").getAsDouble(),
                root.get("spacing").getAsDouble(), xDim, zDim, rise);
    }
}
