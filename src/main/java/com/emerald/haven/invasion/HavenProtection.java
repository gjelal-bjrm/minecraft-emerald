package com.emerald.haven.invasion;

import com.emerald.block.HavenInvasionButtonBlock;
import com.emerald.block.HavenVoteBlock;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenRooms;
import com.emerald.haven.HavenState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Ce que les armes ne cassent jamais dans Haven, et ou les monstres n'entrent pas.
 *
 * UN BLOC EST PROTEGE (isProtected) si l'une de ces regles le couvre :
 * - hors de Haven, ou carte illisible : tout est protege ;
 * - hors de la grille de la ville, ou au-dessus de son sommet ;
 * - SOUS LA SURFACE DE L'EAU : cellule y <= 57 (Y monde <= 62), anti-inondation ;
 * - dans une zone protegee de la carte validee : appartements, portes, places de
 *   vehicules, Hip Hog, parvis, borne, rideau du bord ;
 * - sur une place de voiture ou de moto de haven_rooms.json, sol compris, marge 1 ;
 * - la borne de vote (sol et deux moities, marge 1) et le bouton du QG (comptoir
 *   et bouton, marge 1), lus a leur place meme s'ils ne sont pas poses ;
 * - par son etat : une barriere, un bloc incassable (durete negative), la borne,
 *   le bouton.
 *
 * LA ZONE SURE (inSafeZone) : appartements et Hip Hog. Aucun monstre n'y entre,
 * aucun monstre n'y blesse un joueur.
 *
 * Aucune de ces fonctions ne charge de troncon : l'etat du bloc n'est lu que si
 * les regles de cellule ne suffisent pas, dans un niveau ou l'appelant l'a deja.
 */
public final class HavenProtection {

    private HavenProtection() {
    }

    /**
     * Vrai si une arme ne doit pas casser ce bloc.
     *
     * @param level le niveau ; tout autre niveau que Haven est protege
     * @param pos   la position du monde
     */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return reason(level, pos) != null;
    }

    /** La raison de la protection, pour le journal et le banc d'essai ; null si le bloc est cassable. */
    @Nullable
    public static String reason(ServerLevel level, BlockPos pos) {
        if (!Haven.is(level)) {
            return "hors de Haven";
        }
        MinecraftServer server = level.getServer();
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        if (data == null) {
            return "carte d'invasion illisible";
        }
        BlockPos origin = HavenState.get(server).origin();
        String cell = cellReason(server, data, pos.getX() - origin.getX(), pos.getY() - origin.getY(),
                pos.getZ() - origin.getZ());
        if (cell != null) {
            return cell;
        }
        return stateReason(level.getBlockState(pos), level, pos);
    }

    /**
     * Les regles de cellule seules (sans lire le monde).
     *
     * @return la raison, ou null si la cellule n'est couverte par aucune
     */
    @Nullable
    public static String cellReason(MinecraftServer server, HavenInvasionData.Data data, int x, int y, int z) {
        if (x < 0 || z < 0 || y < 0 || x >= data.width() || z >= data.depth() || y >= data.height()) {
            return "hors de la grille";
        }
        if (y <= data.waterMaxY()) {
            return "sous la surface de l'eau (y <= " + data.waterMaxY() + ")";
        }
        for (HavenInvasionData.Zone zone : data.protectedZones()) {
            if (zone.box().contains(x, y, z)) {
                return zone.name();
            }
        }
        HavenRooms.Data rooms = HavenRooms.get(server);
        if (rooms != null) {
            for (HavenRooms.Room room : rooms.rooms()) {
                if (vehicle(room.car(), x, y, z) || vehicle(room.bike(), x, y, z)) {
                    return "place de vehicule de " + room.id();
                }
            }
            if (rooms.hq() != null && rooms.hq().voteFloor() != null) {
                BlockPos floor = rooms.hq().voteFloor();
                if (new HavenInvasionData.Box(floor, floor.above(2)).inflate(1).contains(x, y, z)) {
                    return "borne de vote";
                }
            }
        }
        HavenInvasionData.Button button = data.button();
        if (button != null && new HavenInvasionData.Box(button.floor(), button.cell()).inflate(1).contains(x, y, z)) {
            return "bouton du QG";
        }
        return null;
    }

    /** Une place de vehicule : son air, son sol, et une cellule de marge. */
    private static boolean vehicle(@Nullable HavenRooms.Car place, int x, int y, int z) {
        if (place == null) {
            return false;
        }
        BlockPos min = place.place().min();
        BlockPos max = place.place().max();
        return new HavenInvasionData.Box(new BlockPos(min.getX(), Math.min(min.getY(), place.floor().getY()), min.getZ()), max)
                .inflate(1).contains(x, y, z);
    }

    /** Les regles d'etat : barriere, bloc incassable, borne, bouton. */
    @Nullable
    public static String stateReason(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.is(Blocks.BARRIER)) {
            return "barriere";
        }
        if (state.getBlock() instanceof HavenVoteBlock) {
            return "borne de vote";
        }
        if (state.getBlock() instanceof HavenInvasionButtonBlock) {
            return "bouton du QG";
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return "incassable";
        }
        return null;
    }

    // ------------------------------------------------------------- zone sure

    /** Vrai si le point du monde est dans une zone sure (appartements, Hip Hog). */
    public static boolean inSafeZone(MinecraftServer server, double x, double y, double z) {
        return safeZoneAt(server, x, y, z) != null;
    }

    /** La zone sure qui contient ce point du monde, ou null. */
    @Nullable
    public static HavenInvasionData.Zone safeZoneAt(MinecraftServer server, double x, double y, double z) {
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        if (data == null) {
            return null;
        }
        BlockPos origin = HavenState.get(server).origin();
        int cx = (int) Math.floor(x) - origin.getX();
        int cy = (int) Math.floor(y) - origin.getY();
        int cz = (int) Math.floor(z) - origin.getZ();
        for (HavenInvasionData.Zone zone : data.safeZones()) {
            if (zone.box().contains(cx, cy, cz)) {
                return zone;
            }
        }
        return null;
    }
}
