package com.emerald.haven.door;

import com.emerald.block.HavenDoorBlock;
import com.emerald.block.ModBlocks;
import com.emerald.block.entity.HavenDoorBlockEntity;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenRooms;
import com.emerald.haven.HavenState;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Les portes de Jak 3 de la ville (cahier §95) : celles que le mod pose d'office, et la pose
 * d'une porte, qu'elle vienne du mod ou du joueur.
 *
 * D'OFFICE : la porte du Hip Hog a la porte de chacun des trois appartements (leurs ouvertures
 * de trois sur quatre, haven_rooms.json) et a l'entree du bar -- en biais, a 33,6 degres, comme
 * dans le jeu (hip-door-a, ctyport-actors.json) --, et le grand sas du port la ou Jak 3 le met
 * (com-airlock-outer). Posees apres chaque pose de la ville, et une fois au demarrage sur une
 * ville deja posee (l'etat de Haven garde la version ; une porte d'office qui a bouge d'une
 * version a l'autre est remplacee). Une porte ne prend que des cellules vides : ce que le
 * joueur a mis dans une ouverture reste. Le releve de l'atelier les ignore (JakCityCapture) ;
 * celles que le joueur pose lui-meme, il les releve.
 *
 * LE VOLUME N'EST PAS JAK 3 AU CENTIMETRE. Au bar, le voxeliseur a pose le sol une cellule
 * au-dessus de celui du jeu (le seuil, en y 66) et l'escalier du mur en biais ne laisse que
 * 2,1 blocs libres le long de la lame, decales de 0,33 : la porte s'y tient sur le seuil, au
 * milieu de ce qui est libre, et ses bords entrent dans le mur. Au port, l'ouverture du mur
 * fait douze cellules, centree en z 148 et non 148,4 : le sas s'y centre.
 *
 * La seconde porte du Hip Hog de Jak 3 (cellule 792, 66, 156) n'est pas posee : le joueur a mis
 * une porte en cuivre a cet endroit dans son atelier.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenDoors {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);
    /** Ce que la ville posee doit avoir (HavenState.doors). */
    public static final int VERSION = 2;
    /** 2|16 : ni voisins prevenus, ni formes recalculees. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Une cellule de porte cherche son controleur a cette distance au plus (le sas fait douze de large). */
    private static final int SEARCH = 13;

    /** La porte du bar, en cellules du volume : hip-door-a de Jak 3, en biais, sur le seuil. */
    static final HavenDoorFrame BAR = new HavenDoorFrame(HavenDoorKind.HIP, 361.115, 67.0, 197.413, -33.6F);
    /** Le grand sas du port : com-airlock-outer de Jak 3, face a l'est, centre dans l'ouverture du mur. */
    static final HavenDoorFrame SAS = new HavenDoorFrame(HavenDoorKind.SAS, 583.36, 62.0, 148.0, -90.0F);

    private static boolean breaking;
    private static Set<Long> defaultCells;

    private HavenDoors() {
    }

    /** Les portes d'office, en cellules du volume. */
    public static List<HavenDoorFrame> defaults(MinecraftServer server) {
        List<HavenDoorFrame> frames = new ArrayList<>();
        HavenRooms.Data data = HavenRooms.get(server);
        if (data != null) {
            for (HavenRooms.Room room : data.rooms()) {
                HavenRooms.Box door = room.door();
                if (door == null) {
                    continue;
                }
                // l'ouverture est dans le plan d'un mur : sa largeur court le long de l'autre axe
                boolean alongZ = door.sizeX() == 1;
                double x = alongZ ? door.min().getX() + 0.5 : (door.min().getX() + door.max().getX() + 1) / 2.0;
                double z = alongZ ? (door.min().getZ() + door.max().getZ() + 1) / 2.0 : door.min().getZ() + 0.5;
                frames.add(new HavenDoorFrame(HavenDoorKind.HIP, x, door.min().getY(), z, alongZ ? 90.0F : 0.0F));
            }
        }
        frames.add(BAR);
        frames.add(SAS);
        return frames;
    }

    /** Cette cellule du volume appartient-elle a une porte d'office ? (le releve de l'atelier ne les compte pas) */
    public static boolean isDefaultCell(MinecraftServer server, int x, int y, int z) {
        if (defaultCells == null) {
            Set<Long> cells = new HashSet<>();
            for (HavenDoorFrame frame : defaults(server)) {
                for (BlockPos cell : withController(frame)) {
                    cells.add(cell.asLong());
                }
            }
            defaultCells = Set.copyOf(cells);
        }
        return defaultCells.contains(BlockPos.asLong(x, y, z));
    }

    /** Apres la pose de la ville : les portes d'office. */
    public static void afterPose(MinecraftServer server, ServerLevel level, HavenState state) {
        int placed = placeDefaults(server, level, state.origin());
        state.setDoors(VERSION);
        LOGGER.info("Haven : {} portes de Jak 3 posees apres la pose", placed);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        defaultCells = null;
        HavenState state = HavenState.get(server);
        if (!state.built() || state.doors() >= VERSION) {
            return;
        }
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return;
        }
        int placed = placeDefaults(server, level, state.origin());
        state.setDoors(VERSION);
        LOGGER.info("Haven : {} portes de Jak 3 posees sur la ville deja posee", placed);
    }

    private static int placeDefaults(MinecraftServer server, ServerLevel level, BlockPos origin) {
        int placed = 0;
        for (HavenDoorFrame cellFrame : defaults(server)) {
            HavenDoorFrame frame = cellFrame.moved(origin.getX(), origin.getY(), origin.getZ());
            removeMoved(level, frame);
            if (place(level, frame, true)) {
                placed++;
            }
        }
        return placed;
    }

    /**
     * Une porte d'office qui a bouge d'une version a l'autre (le sas recentre dans son
     * ouverture) : l'ancienne, de la meme sorte, dont le controleur est a quelques blocs du
     * nouveau, s'en va avant la pose.
     */
    private static void removeMoved(Level level, HavenDoorFrame frame) {
        BlockPos controller = frame.controller();
        int r = (int) Math.ceil(frame.kind().width / 2.0) + 2;
        List<HavenDoorFrame> old = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(controller.offset(-r, -2, -r), controller.offset(r, 2, r))) {
            if (level.getBlockEntity(p) instanceof HavenDoorBlockEntity entity && entity.kind() == frame.kind()
                    && !same(entity.frame(), frame)) {
                old.add(entity.frame());
            }
        }
        for (HavenDoorFrame gone : old) {
            remove(level, gone);
            LOGGER.info("Haven : porte {} deplacee, l'ancienne en {} {} {} s'en va", frame.kind(),
                    gone.x(), gone.y(), gone.z());
        }
    }

    private static boolean same(HavenDoorFrame a, HavenDoorFrame b) {
        return a.kind() == b.kind() && Math.abs(a.x() - b.x()) < 1.0e-6 && Math.abs(a.y() - b.y()) < 1.0e-6
                && Math.abs(a.z() - b.z()) < 1.0e-6 && Math.abs(a.yaw() - b.yaw()) < 1.0e-3F;
    }

    /** Les cellules d'une porte, controleur compris (en biais, la lame peut ne pas le toucher). */
    public static List<BlockPos> withController(HavenDoorFrame frame) {
        List<BlockPos> cells = new ArrayList<>(frame.cells());
        BlockPos controller = frame.controller();
        if (!cells.contains(controller)) {
            cells.add(controller);
        }
        return cells;
    }

    /**
     * Pose une porte : chaque cellule vide de son ouverture, le controleur avec son entite.
     *
     * @param partial vrai pour une porte d'office : les cellules deja prises (le joueur) sont sautees ;
     *                faux pour la pose a la main : il faut que tout soit libre
     * @return vrai si la porte est posee
     */
    public static boolean place(Level level, HavenDoorFrame frame, boolean partial) {
        List<BlockPos> cells = withController(frame);
        BlockPos controller = frame.controller();
        Block door = ModBlocks.HAVEN_DOOR.get();
        for (BlockPos cell : cells) {
            level.getChunk(cell.getX() >> 4, cell.getZ() >> 4);
            BlockState existing = level.getBlockState(cell);
            boolean free = existing.isAir() || existing.canBeReplaced() || existing.is(door);
            if (!free && (!partial || cell.equals(controller))) {
                return false;
            }
        }
        for (BlockPos cell : cells) {
            BlockState existing = level.getBlockState(cell);
            if (!(existing.isAir() || existing.canBeReplaced() || existing.is(door))) {
                continue;
            }
            level.setBlock(cell, door.defaultBlockState()
                    .setValue(HavenDoorBlock.KIND, frame.kind())
                    .setValue(HavenDoorBlock.OPEN, false)
                    .setValue(HavenDoorBlock.CONTROLLER, cell.equals(controller)), QUIET);
        }
        if (level.getBlockEntity(controller) instanceof HavenDoorBlockEntity entity) {
            entity.place(frame);
        }
        return true;
    }

    /**
     * Une cellule de porte s'en va : toute la porte s'en va. Le controleur est la cellule elle-meme,
     * ou le plus proche dont la porte la contient.
     */
    public static void breakAround(Level level, BlockPos pos) {
        if (breaking) {
            return;
        }
        HavenDoorFrame frame = null;
        if (level.getBlockEntity(pos) instanceof HavenDoorBlockEntity entity) {
            frame = entity.frame();
        } else {
            BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
            search:
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                for (int dy = -SEARCH; dy <= 0; dy++) {
                    for (int dz = -SEARCH; dz <= SEARCH; dz++) {
                        probe.setWithOffset(pos, dx, dy, dz);
                        if (level.getBlockEntity(probe) instanceof HavenDoorBlockEntity entity
                                && withController(entity.frame()).contains(pos)) {
                            frame = entity.frame();
                            break search;
                        }
                    }
                }
            }
        }
        if (frame != null) {
            remove(level, frame);
        }
    }

    /** Toutes les cellules d'une porte redeviennent de l'air (celle qui s'en va deja n'est plus une porte). */
    private static void remove(Level level, HavenDoorFrame frame) {
        breaking = true;
        try {
            for (BlockPos cell : withController(frame)) {
                if (level.getBlockState(cell).getBlock() instanceof HavenDoorBlock) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), QUIET);
                }
            }
        } finally {
            breaking = false;
        }
    }

    /** Pour le banc : l'entite d'une porte d'office, a sa cellule-controleur dans le monde. */
    public static BlockEntity controllerAt(ServerLevel level, BlockPos origin, HavenDoorFrame cellFrame) {
        return level.getBlockEntity(cellFrame.moved(origin.getX(), origin.getY(), origin.getZ()).controller());
    }
}
