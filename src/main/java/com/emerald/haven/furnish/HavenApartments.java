package com.emerald.haven.furnish;

import com.emerald.block.HavenFurnishChestBlock;
import com.emerald.block.HavenWindowBlock;
import com.emerald.block.ModBlocks;
import com.emerald.block.entity.HavenWindowBlockEntity;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenState;
import com.emerald.haven.door.HavenWindows;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les appartements a amenager (cahier §107).
 *
 * « Quand les joueurs apparaissent dans la ville apres avoir lance le jeu pour la premiere fois,
 * des quetes obligatoires avant d'aller au QG : dans chacune des salles des joueurs, des coffres
 * qui contiennent enormement de materiaux de construction de Jak 3, plein de lumieres et de
 * meubles, pour decorer la salle comme ils le souhaitent. La premiere quete : ouvrir les fenetres
 * de Jak 3 ; la deuxieme : placer quelques meubles, un ou deux » (le joueur, 27 sept.). Choix du
 * joueur : un coffre sans fond (HavenFurnish) ; on ne casse que ce qu'on a pose.
 *
 *   - CONSTRUIRE CHEZ SOI : dans la boite d'air de SON appartement, le joueur passe en survie (en
 *     aventure partout ailleurs dans la ville) ; il y pose ce qu'il veut, et ne casse que ce qui a
 *     ete pose dans l'appartement -- murs, sol, vitres, porte et meubles de la ville restent. Ce
 *     qu'il casse ne lache rien : le coffre en redonne autant qu'on veut.
 *   - LE COFFRE D'AMENAGEMENT : un par appartement, pose par le mod pres des places d'arrivee,
 *     face a la porte ; jamais releve (JakCityCapture.managed).
 *   - LES DEUX QUETES, a la premiere arrivee, avant le rendez-vous au QG (HavenJourney) : ouvrir
 *     les fenetres de son appartement -- fermees a l'arrivee d'un nouveau venu --, puis poser
 *     {@link #FURNITURE_GOAL} objets. La borne ne prend pas le vote de qui ne les a pas faites.
 *     Les joueurs deja passes au QG avant elles n'ont rien a refaire.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenApartments {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Les objets a poser pour la deuxieme quete. */
    public static final int FURNITURE_GOAL = 2;
    /** Le mode de jeu, tant de tiques entre deux regards. */
    private static final int MODE_EVERY = 10;
    /** Les coffres, tant de tiques entre deux regards. */
    private static final int CHEST_EVERY = 100;

    /** La place du coffre de chaque appartement (par numero), une fois trouvee. */
    private static final Map<Integer, BlockPos> CHESTS = new HashMap<>();
    private static int ticks;

    private HavenApartments() {
    }

    // ================================================================ les salles

    /** L'appartement d'un joueur ; null s'il n'en a pas. */
    @Nullable
    public static HavenArrival.Room roomOf(MinecraftServer server, UUID id) {
        HavenArrival.Layout rooms = HavenArrival.layout(server);
        int[] apartment = HavenState.get(server).apartment(id);
        if (rooms == null || apartment == null || rooms.rooms().isEmpty()) {
            return null;
        }
        return rooms.rooms().get(Math.floorMod(apartment[0], rooms.rooms().size()));
    }

    /** Dans la boite d'air de la salle (bornes comprises), en coordonnees du monde. */
    public static boolean inBox(MinecraftServer server, HavenArrival.Room room, BlockPos pos, int grow) {
        BlockPos origin = HavenState.get(server).origin();
        BlockPos min = origin.offset(room.boxMin()).offset(-grow, -grow, -grow);
        BlockPos max = origin.offset(room.boxMax()).offset(grow, grow, grow);
        return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY()
                && pos.getY() <= max.getY() && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    /** Le joueur est-il dans son propre appartement ? */
    public static boolean atHome(ServerPlayer player) {
        if (!Haven.is(player.level())) {
            return false;
        }
        HavenArrival.Room room = roomOf(player.server, player.getUUID());
        return room != null && inBox(player.server, room, player.blockPosition(), 0);
    }

    // ================================================================ poser, casser

    /**
     * Une pose dans la ville (HavenRules) : permise dans la boite de son appartement, sur une place
     * libre ; elle est alors notee, et compte pour la quete.
     *
     * @return vrai si la pose est permise
     */
    public static boolean acceptPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)
                || !Haven.is(level) || HavenRules.chantier(player)) {
            return false;
        }
        HavenArrival.Room room = roomOf(player.server, player.getUUID());
        if (room == null) {
            return false;
        }
        List<BlockSnapshot> snapshots = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots() : List.of(event.getBlockSnapshot());
        for (BlockSnapshot snapshot : snapshots) {
            BlockState before = snapshot.getState();
            if (!inBox(player.server, room, snapshot.getPos(), 0) || !(before.isAir() || before.canBeReplaced())) {
                player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.appart.hors")
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
        }
        Data data = data(player.server);
        Set<Long> placed = data.placed(room.number());
        for (BlockSnapshot snapshot : snapshots) {
            placed.add(snapshot.getPos().asLong());
        }
        data.setDirty();
        furnished(player);
        return true;
    }

    /**
     * Une casse dans la ville (HavenRules) : ce qui a ete pose dans son appartement s'en va, sans
     * rien lacher ; le reste est protege.
     *
     * @return vrai si le bloc a ete retire (l'evenement doit alors etre annule : c'est deja fait)
     */
    public static boolean handleBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        HavenArrival.Room room = roomOf(player.server, player.getUUID());
        if (room == null) {
            return false;
        }
        Data data = data(player.server);
        Set<Long> placed = data.placed(room.number());
        BlockPos pos = event.getPos();
        if (!placed.remove(pos.asLong())) {
            return false;
        }
        data.setDirty();
        level.destroyBlock(pos, false, player);
        return true;
    }

    /** Un clic sur un bloc de la ville : chez soi, un bloc en main se pose ou qu'on vise. */
    public static boolean mayUse(Player player, ItemStack held) {
        return player instanceof ServerPlayer server && held.getItem() instanceof BlockItem && atHome(server);
    }

    // ================================================================ les quetes

    /** Les quetes de l'appartement restent-elles a faire ? Pas pour qui est deja passe au QG. */
    public static boolean pending(HavenProgress.Entry entry) {
        return !entry.hq && !entry.furnishedDone();
    }

    /** Un objet pose chez soi : la deuxieme quete avance. */
    private static void furnished(ServerPlayer player) {
        HavenProgress.Entry entry = HavenProgress.get(player.getUUID());
        if (!pending(entry) || !entry.windows || entry.furnished >= FURNITURE_GOAL) {
            return;
        }
        entry.furnished++;
        HavenProgress.save();
        if (entry.furnished >= FURNITURE_GOAL) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.appart.meubles.fait")
                    .withStyle(ChatFormatting.GREEN));
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
            LOGGER.info("Appartements : {} a amenage son appartement", player.getGameProfile().getName());
        }
    }

    /** Une vitre cliquee (HavenWindowBlock) : sa fenetre ouverte chez soi, la premiere quete est faite. */
    public static void windowUsed(ServerPlayer player, BlockPos pos) {
        HavenProgress.Entry entry = HavenProgress.get(player.getUUID());
        if (!pending(entry) || entry.windows) {
            return;
        }
        HavenArrival.Room room = roomOf(player.server, player.getUUID());
        ServerLevel haven = Haven.level(player.server);
        if (room == null || haven == null || !inBox(player.server, room, pos, 1)
                || !(haven.getBlockEntity(pos) instanceof HavenWindowBlockEntity window) || window.closed()) {
            return;
        }
        entry.windows = true;
        HavenProgress.save();
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.appart.fenetres.fait")
                .withStyle(ChatFormatting.GREEN));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.0F);
        LOGGER.info("Appartements : {} a ouvert ses fenetres", player.getGameProfile().getName());
    }

    /** La premiere arrivee d'un nouveau venu : les fenetres de son appartement se ferment (la quete les rouvre). */
    public static void closeWindows(ServerPlayer player) {
        if (!pending(HavenProgress.get(player.getUUID()))) {
            return;
        }
        HavenArrival.Room room = roomOf(player.server, player.getUUID());
        ServerLevel haven = Haven.level(player.server);
        if (room == null || haven == null) {
            return;
        }
        BlockPos origin = HavenState.get(player.server).origin();
        Set<BlockPos> done = new java.util.HashSet<>();
        int windows = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(room.boxMin()).offset(-1, -1, -1),
                origin.offset(room.boxMax()).offset(1, 1, 1))) {
            if (done.contains(pos) || !(haven.getBlockState(pos).getBlock() instanceof HavenWindowBlock)) {
                continue;
            }
            HavenWindows.Window window = HavenWindows.group(haven, pos);
            done.addAll(window.panes());
            HavenWindows.set(haven, window, true);
            windows++;
        }
        LOGGER.info("Appartements : {} fenetre(s) fermee(s) dans l'appartement {} pour {}", windows, room.number(),
                player.getGameProfile().getName());
    }

    // ================================================================ la tique : le mode de jeu, les coffres

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        MinecraftServer server = event.getServer();
        ServerLevel haven = Haven.level(server);
        if (haven == null || !HavenState.get(server).built()) {
            return;
        }
        if (ticks % MODE_EVERY == 0) {
            for (ServerPlayer player : haven.players()) {
                if (player.isFakePlayer() || HavenRules.chantier(player)) {
                    continue;
                }
                GameType now = player.gameMode.getGameModeForPlayer();
                if (now != GameType.ADVENTURE && now != GameType.SURVIVAL) {
                    continue;
                }
                GameType wanted = atHome(player) ? GameType.SURVIVAL : GameType.ADVENTURE;
                if (now != wanted) {
                    player.setGameMode(wanted);
                    if (wanted == GameType.SURVIVAL) {
                        player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.appart.chez_toi")
                                .withStyle(ChatFormatting.AQUA), true);
                    }
                }
            }
        }
        if (ticks % CHEST_EVERY == 0) {
            keepChests(server, haven);
        }
    }

    /** Un coffre d'amenagement dans chaque appartement, a sa place s'il n'y est pas. */
    public static void keepChests(MinecraftServer server, ServerLevel haven) {
        HavenArrival.Layout rooms = HavenArrival.layout(server);
        if (rooms == null) {
            return;
        }
        for (HavenArrival.Room room : rooms.rooms()) {
            BlockPos spot = chestSpot(server, haven, room);
            if (spot == null || !haven.isLoaded(spot)) {
                continue;
            }
            BlockState state = haven.getBlockState(spot);
            if (state.is(ModBlocks.HAVEN_FURNISH_CHEST.get())) {
                continue;
            }
            if (state.isAir()) {
                haven.setBlock(spot, ModBlocks.HAVEN_FURNISH_CHEST.get().defaultBlockState()
                        .setValue(HavenFurnishChestBlock.FACING, toDoor(room)), Block.UPDATE_ALL);
                LOGGER.info("Appartements : coffre d'amenagement de l'appartement {} en {}", room.number(), spot.toShortString());
            }
        }
    }

    /**
     * La place du coffre : deux cellules derriere la place d'arrivee du milieu, loin de la porte ;
     * a defaut, la cellule libre la plus proche, dans la salle. Gardee une fois trouvee.
     */
    @Nullable
    public static BlockPos chestSpot(MinecraftServer server, ServerLevel haven, HavenArrival.Room room) {
        BlockPos known = CHESTS.get(room.number());
        if (known != null) {
            return known;
        }
        if (room.spawns().isEmpty()) {
            return null;
        }
        BlockPos origin = HavenState.get(server).origin();
        BlockPos middle = room.spawns().get(room.spawns().size() / 2).above();
        Direction away = toDoor(room).getOpposite();
        BlockPos base = origin.offset(middle).relative(away, 2);
        for (int r = 0; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos p = base.offset(dx, 0, dz);
                    if (!inBox(server, room, p, 0)) {
                        continue;
                    }
                    BlockState here = haven.getBlockState(p);
                    if (here.is(ModBlocks.HAVEN_FURNISH_CHEST.get())
                            || (here.isAir() && haven.getBlockState(p.below()).isFaceSturdy(haven, p.below(), Direction.UP))) {
                        CHESTS.put(room.number(), p.immutable());
                        return p.immutable();
                    }
                }
            }
        }
        return null;
    }

    /** De la salle vers sa porte. */
    public static Direction toDoor(HavenArrival.Room room) {
        double dx = (room.doorMin().getX() + room.doorMax().getX()) / 2.0 - (room.boxMin().getX() + room.boxMax().getX()) / 2.0;
        double dz = (room.doorMin().getZ() + room.doorMax().getZ()) / 2.0 - (room.boxMin().getZ() + room.boxMax().getZ()) / 2.0;
        return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CHESTS.clear();
        ticks = 0;
    }

    // ================================================================ la sauvegarde

    public static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), Data.KEY);
    }

    /** Ce qui a ete pose dans chaque appartement : seul cela se casse. */
    public static final class Data extends SavedData {

        public static final String KEY = "emeraldweapons_haven_apartments";

        private final Map<Integer, Set<Long>> placed = new HashMap<>();

        public Set<Long> placed(int room) {
            return this.placed.computeIfAbsent(room, k -> new LinkedHashSet<>());
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            for (Tag entry : tag.getList("Rooms", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) entry;
                Set<Long> set = data.placed(c.getInt("Room"));
                for (long pos : c.getLongArray("Placed")) {
                    set.add(pos);
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag rooms = new ListTag();
            for (Map.Entry<Integer, Set<Long>> entry : this.placed.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putInt("Room", entry.getKey());
                c.put("Placed", new LongArrayTag(entry.getValue().stream().mapToLong(Long::longValue).toArray()));
                rooms.add(c);
            }
            tag.put("Rooms", rooms);
            return tag;
        }
    }
}
