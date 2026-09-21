package com.emerald.haven.journey;

import com.emerald.block.HavenGateBlock;
import com.emerald.block.ModBlocks;
import com.emerald.block.entity.HavenGateBlockEntity;
import com.emerald.game.GameManager;
import com.emerald.game.GameState;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenState;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Le retour du Defi a Haven (parcours, lot 2, cahier §79.2 point 4 et §81).
 *
 * Avant, une partie finie ne ramenait nulle part : il fallait « /arcencium haven ouvrir ».
 * Desormais, un DEFI parti de la ville (phase PARTI, ville prete a rouvrir) :
 *   - DEFAITE (le chrono a zero, Finale.defeat) : le titre de fin se lit -- dix, cent et
 *     vingt tiques --, puis tout le monde revient a Haven ({@link #DEFEAT_DELAY}) ;
 *   - VICTOIRE (le boss tombe, Finale.victory) : une PORTE PRECURSEUR s'ouvre la ou il est
 *     tombe, au coeur du combat (HavenGateBlock). Qui y entre rentre dans son appartement
 *     et y attend l'equipe ; quand tous sont rentres, ou au bout de {@link #DOOR_TIMEOUT}
 *     tiques (cinq minutes), le lobby se rouvre et chacun retrouve son appartement.
 * LE RETOUR, c'est la reouverture du lobby -- celle de la commande : la Lame replantee
 * (GameManager.setup, sans accueil au village), puis HavenArrival.reopen, EN GARDANT les
 * appartements. La mort pendant le Defi ne change pas (§79.6 : la tombe, le village).
 * Le Monde ouvert non plus : sa victoire relance un cycle.
 *
 * RIEN N'EST SAUVEGARDE : une porte ne survit pas a un redemarrage (elle s'efface d'elle-
 * meme, HavenGateBlockEntity). A la premiere tique du serveur, un Defi deja fini dans un
 * monde parti de la ville rouvre le lobby : c'est le retour qu'un arret a interrompu.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenReturn {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Apres la defaite : le titre de fin (Finale, 10 + 100 + 20 tiques), puis le retour. */
    public static final int DEFEAT_DELAY = 130;
    /** La porte de victoire reste ouverte cinq minutes au plus. */
    public static final int DOOR_TIMEOUT = 20 * 60 * 5;
    /** Rappel de la porte dans la barre d'action, en tiques. */
    private static final int REMINDER = 100;
    /** Qui s'approche a moins de cela du centre de la porte la passe, en blocs (l'anneau fait 3,7 de large). */
    public static final double DOOR_REACH = 1.4;

    /** Ou en est le retour. */
    public enum Stage { AUCUN, DEFAITE, PORTE }

    private static Stage stage = Stage.AUCUN;
    /** La tique (overworld) du retour apres la defaite, ou de la fermeture de la porte. */
    private static long due;
    @Nullable
    private static BlockPos door;
    /** Les joueurs passes par la porte, qui attendent l'equipe dans leur appartement. */
    private static final Set<UUID> THROUGH = new LinkedHashSet<>();
    /** Les cobayes du banc : comptes comme de vrais joueurs. */
    private static final List<ServerPlayer> SUBJECTS = new ArrayList<>();
    private static boolean started;

    private HavenReturn() {
    }

    // ================================================================ lecture

    public static Stage stage() {
        return stage;
    }

    @Nullable
    public static BlockPos door() {
        return door;
    }

    public static long due() {
        return due;
    }

    /** Vrai si ce joueur est passe par la porte et attend l'equipe dans la ville. */
    public static boolean holding(ServerPlayer player) {
        return stage == Stage.PORTE && THROUGH.contains(player.getUUID());
    }

    /** La porte de victoire ouverte en ce moment est-elle a cette place ? (HavenGateBlockEntity) */
    public static boolean isGate(Level level, BlockPos pos) {
        return stage == Stage.PORTE && door != null && door.equals(pos) && level.dimension().equals(Level.OVERWORLD);
    }

    /**
     * Le retour s'applique : un DEFI (le chrono compte), parti de la ville (phase PARTI), et
     * une ville prete a rouvrir. Un monde sans ville garde la fin d'avant.
     */
    public static boolean applies(MinecraftServer server) {
        return GameState.get(server.overworld()).timed() && HavenState.get(server).phase() == HavenState.Phase.PARTI
                && HavenArrival.canOpen(server);
    }

    // ================================================================ la fin du Defi

    /**
     * La defaite (Finale.defeat) : le retour apres le titre.
     *
     * @return vrai si la ville prend la fin en charge (Finale ne rappelle pas « /arcencium stop »)
     */
    public static boolean onDefeat(ServerLevel overworld) {
        MinecraftServer server = overworld.getServer();
        if (stage != Stage.AUCUN) {
            return true;
        }
        if (!applies(server)) {
            return false;
        }
        stage = Stage.DEFAITE;
        due = overworld.getGameTime() + DEFEAT_DELAY;
        THROUGH.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.retour.defaite")
                    .withStyle(ChatFormatting.AQUA));
        }
        LOGGER.info("Retour du Defi : defaite, retour a Haven dans {} tiques", DEFEAT_DELAY);
        return true;
    }

    /**
     * La victoire (Finale.victory, en Defi) : la porte, la ou le boss est tombe.
     *
     * @param where la position du boss a sa mort ; null (victoire a la commande) : l'arene, ou un joueur
     * @return vrai si la ville prend la fin en charge
     */
    public static boolean onVictory(ServerLevel overworld, @Nullable BlockPos where) {
        MinecraftServer server = overworld.getServer();
        if (stage != Stage.AUCUN) {
            return true;
        }
        if (!applies(server)) {
            return false;
        }
        // la ou le boss est tombe ; un boss qui vole meurt en l'air : alors pres du joueur
        // le plus proche, puis au centre de l'arene
        BlockPos arena = GameState.get(overworld).finale();
        List<BlockPos> bases = new ArrayList<>();
        if (where != null) {
            bases.add(where);
        }
        ServerPlayer nearest = nearest(overworld, where != null ? where : arena);
        if (nearest != null) {
            bases.add(nearest.blockPosition());
        }
        if (!arena.equals(BlockPos.ZERO)) {
            bases.add(arena);
        }
        BlockPos spot = null;
        for (BlockPos base : bases) {
            spot = doorSpot(overworld, base);
            if (spot != null) {
                break;
            }
        }
        if (spot == null) {
            spot = nearest != null ? nearest.blockPosition() : bases.isEmpty() ? overworld.getSharedSpawnPos() : bases.get(0);
        }
        Direction facing = facingNearest(overworld, spot);
        placeDoor(overworld, spot, facing);
        stage = Stage.PORTE;
        due = overworld.getGameTime() + DOOR_TIMEOUT;
        door = spot;
        THROUGH.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.retour.porte",
                    spot.getX(), spot.getY(), spot.getZ(), DOOR_TIMEOUT / 1200).withStyle(ChatFormatting.GOLD));
        }
        overworld.playSound(null, spot, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.6F, 1.4F);
        LOGGER.info("Retour du Defi : victoire, porte precurseur en {} (face {})", spot.toShortString(), facing);
        return true;
    }

    // ================================================================ la tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!started) {
            started = true;
            recover(server);
        }
        tick(server);
    }

    /** Une tique du retour : l'heure du retour apres la defaite, ou la porte. */
    static void tick(MinecraftServer server) {
        switch (stage) {
            case AUCUN -> {
            }
            case DEFAITE -> {
                if (!stillOurs(server, GameState.Status.LOST)) {
                    cancel(server, "la partie a change entre-temps");
                } else if (server.overworld().getGameTime() >= due) {
                    bringBack(server, "defaite");
                }
            }
            case PORTE -> tickDoor(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        stage = Stage.AUCUN;
        due = 0L;
        door = null;
        THROUGH.clear();
        SUBJECTS.clear();
        started = false;
    }

    /**
     * Au demarrage : un Defi fini (gagne ou perdu) dans un monde parti de la ville rouvre le
     * lobby -- le retour qu'un arret a coupe, ou une partie finie avant le lot 2.
     */
    private static void recover(MinecraftServer server) {
        GameState.Status status = GameState.get(server.overworld()).status();
        if ((status == GameState.Status.WON || status == GameState.Status.LOST) && applies(server)) {
            LOGGER.info("Retour du Defi : partie deja finie ({}) au demarrage, retour a Haven", status);
            bringBack(server, "demarrage");
        }
    }

    /** La partie est-elle encore celle que le retour attend ? Un operateur a pu tout relancer. */
    private static boolean stillOurs(MinecraftServer server, GameState.Status expected) {
        return HavenState.get(server).phase() == HavenState.Phase.PARTI
                && GameState.get(server.overworld()).status() == expected;
    }

    private static void tickDoor(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (door == null || !stillOurs(server, GameState.Status.WON)) {
            cancel(server, "la partie a change entre-temps");
            return;
        }
        long now = overworld.getGameTime();
        List<ServerPlayer> remaining = remaining(server);
        if (remaining.isEmpty()) {
            bringBack(server, "toute l'equipe est rentree");
            return;
        }
        if (now >= due) {
            bringBack(server, "la porte se referme, " + remaining.size() + " retardataire(s) ramene(s)");
            return;
        }
        Vec3 center = Vec3.atBottomCenterOf(door);
        for (ServerPlayer player : remaining) {
            if (player.level() != overworld || !player.isAlive() || !inDoor(player, center)) {
                continue;
            }
            if (remaining.size() == 1) {
                // le dernier : pas la peine de le poser d'abord, tout le monde rentre
                LOGGER.info("Retour du Defi : {} passe la porte, le dernier", player.getGameProfile().getName());
                bringBack(server, "toute l'equipe est rentree");
                return;
            }
            enter(server, player, remaining.size() - 1);
        }
        if (now % 10 == 0) {
            overworld.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1.2, center.z, 4, 0.5, 0.8, 0.5, 0.02);
        }
        if (now % REMINDER == 0) {
            long seconds = Math.max(0L, (due - now) / 20L);
            Component left = Component.literal(String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L));
            for (ServerPlayer player : remaining) {
                if (player.level() != overworld || player.isFakePlayer()) {
                    continue;
                }
                int dx = door.getX() - player.getBlockX();
                int dz = door.getZ() - player.getBlockZ();
                int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
                player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.retour.porte.barre",
                        distance, Component.translatable(HavenArrival.direction(dx, dz)), left)
                        .withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** Dans la porte : a moins de {@link #DOOR_REACH} de son centre, les pieds a sa hauteur. */
    public static boolean inDoor(ServerPlayer player, Vec3 center) {
        double dx = player.getX() - center.x;
        double dz = player.getZ() - center.z;
        double dy = player.getY() - center.y;
        return dx * dx + dz * dz <= DOOR_REACH * DOOR_REACH && dy >= -0.5 && dy <= 3.0;
    }

    /** Les joueurs encore dans le Defi : en ligne, hors chantier, pas encore passes par la porte. */
    public static List<ServerPlayer> remaining(MinecraftServer server) {
        List<ServerPlayer> out = new ArrayList<>();
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        all.addAll(SUBJECTS);
        Set<UUID> seen = new LinkedHashSet<>();
        for (ServerPlayer player : all) {
            if (!seen.add(player.getUUID()) || (player.isFakePlayer() && !SUBJECTS.contains(player))
                    || HavenRules.chantier(player) || THROUGH.contains(player.getUUID()) || Haven.is(player.level())) {
                continue;
            }
            out.add(player);
        }
        return out;
    }

    /** Un joueur passe la porte : son appartement, sa reapparition la-bas, et il attend l'equipe. */
    private static void enter(MinecraftServer server, ServerPlayer player, int left) {
        THROUGH.add(player.getUUID());
        HavenArrival.Placement place = HavenArrival.place(server, player.getUUID(), player.getGameProfile().getName());
        ServerLevel haven = Haven.level(server);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
        // un cobaye du banc ne change pas de dimension : il n'est inscrit dans aucun monde
        if (place != null && haven != null && !player.isFakePlayer()) {
            BlockPos feet = place.feet();
            player.teleportTo(haven, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, place.yaw(), 0.0F);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            HavenArrival.setRespawn(player, place);
        }
        int total = THROUGH.size() + left;
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.retour.attente",
                THROUGH.size(), total).withStyle(ChatFormatting.AQUA));
        LOGGER.info("Retour du Defi : {} passe la porte ({}/{})", player.getGameProfile().getName(), THROUGH.size(), total);
    }

    // ================================================================ le retour

    /** Le retour : la porte retiree, la Lame replantee, le lobby rouvert en gardant les appartements. */
    public static void bringBack(MinecraftServer server, String why) {
        ServerLevel overworld = server.overworld();
        removeDoor(overworld);
        stage = Stage.AUCUN;
        door = null;
        THROUGH.clear();
        GameManager.clear();
        GameManager.setup(overworld, overworld.getSharedSpawnPos(), false);
        int moved = HavenArrival.reopen(server, true);
        LOGGER.info("Retour du Defi ({}) : lobby rouvert, {} joueur(s) ramene(s)", why, moved);
    }

    /** Abandon sans retour : un operateur a relance la partie ou rouvert la ville. */
    private static void cancel(MinecraftServer server, String why) {
        removeDoor(server.overworld());
        stage = Stage.AUCUN;
        door = null;
        THROUGH.clear();
        LOGGER.info("Retour du Defi abandonne : {}", why);
    }

    // ================================================================ la porte

    /** Le joueur le plus proche d'un point, dans ce monde ; null s'il n'y en a pas. */
    @Nullable
    private static ServerPlayer nearest(ServerLevel level, BlockPos from) {
        ServerPlayer best = null;
        double distance = Double.MAX_VALUE;
        List<ServerPlayer> players = new ArrayList<>(level.players());
        for (ServerPlayer subject : SUBJECTS) {
            if (subject.level() == level) {
                players.add(subject);
            }
        }
        for (ServerPlayer player : players) {
            if (HavenRules.chantier(player)) {
                continue;
            }
            double d = player.distanceToSqr(Vec3.atCenterOf(from));
            if (d < distance) {
                distance = d;
                best = player;
            }
        }
        return best;
    }

    /**
     * La place de la porte : au plus pres du point, un sol qui porte et quatre cellules
     * d'air au-dessus (l'anneau fait 3,7 blocs de haut), a huit blocs et trois etages au
     * plus ; null s'il n'y en a pas.
     */
    @Nullable
    public static BlockPos doorSpot(ServerLevel level, BlockPos base) {
        for (int r = 0; r <= 8; r++) {
            for (int dy = 0; dy <= 6; dy++) {
                int y = base.getY() + (dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockPos feet = new BlockPos(base.getX() + dx, y, base.getZ() + dz);
                        if (clear(level, feet)) {
                            return feet;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean clear(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                || !level.getFluidState(feet).isEmpty()) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            BlockState state = level.getBlockState(feet.above(i));
            if (!state.isAir() && !state.canBeReplaced()) {
                return false;
            }
        }
        return true;
    }

    /** La porte regarde le joueur le plus proche : on la voit de face en se retournant. */
    private static Direction facingNearest(ServerLevel level, BlockPos spot) {
        ServerPlayer nearest = nearest(level, spot);
        if (nearest == null) {
            return Direction.SOUTH;
        }
        Direction facing = Direction.getNearest(nearest.getX() - (spot.getX() + 0.5), 0.0, nearest.getZ() - (spot.getZ() + 0.5));
        return facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
    }

    private static void placeDoor(ServerLevel level, BlockPos spot, Direction facing) {
        BlockState state = ModBlocks.HAVEN_GATE.get().defaultBlockState().setValue(HavenGateBlock.FACING, facing);
        level.setBlock(spot, state, Block.UPDATE_ALL);
        BlockEntity be = level.getBlockEntity(spot);
        if (be instanceof HavenGateBlockEntity gate) {
            gate.setTemporary(true);
        }
    }

    private static void removeDoor(ServerLevel level) {
        if (door != null && level.getBlockState(door).is(ModBlocks.HAVEN_GATE.get())) {
            level.setBlock(door, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // ================================================================ banc d'essai

    static void addSubject(ServerPlayer player) {
        SUBJECTS.add(player);
    }

    static void clearSubjects() {
        SUBJECTS.clear();
    }

    /** Pour le banc : oublie tout retour en cours, porte comprise. */
    static void resetForTest(MinecraftServer server) {
        cancel(server, "banc d'essai");
    }

    /** Pour le banc : l'heure est venue (retour apres la defaite, fermeture de la porte). */
    static void expireForTest() {
        due = Long.MIN_VALUE;
    }
}
