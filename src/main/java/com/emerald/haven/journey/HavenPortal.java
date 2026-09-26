package com.emerald.haven.journey;

import com.emerald.block.HavenGateBlock;
import com.emerald.block.ModBlocks;
import com.emerald.block.entity.HavenGateBlockEntity;
import com.emerald.game.GameState;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenState;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le Portail de Haven (cahier §106) : l'anneau precurseur qu'on FABRIQUE (quatre lingots
 * d'Arcencium et une perle de l'Ender) et qu'on pose dans le monde de la partie.
 *
 * « Permettre aux joueurs qui ont lance un Defi de pouvoir l'arreter : qu'on leur permette de
 * fabriquer le portail a travers lequel ils pourraient revenir en ville » (le joueur, 27 sept.) --
 * et la Porte de Haven du mode libre, prevue au §79.2 (point 9, lot 6), jamais faite jusque-la.
 * Choix du joueur : les deux modes, un seul objet ; en Defi chacun rentre quand il veut.
 *
 * L'ARRIVEE EST TOUJOURS LA MEME : l'ANNEAU DE LA VILLE, au QG, a la place de l'arche du depart
 * (HavenDeparture.place) -- « je ne veux pas que le portail apparaisse dans une des salles de repos
 * des joueurs » (le joueur, 27 sept. ; choix : le QG). On ressort deux blocs devant lui, tourne vers
 * le bar. Si l'arche du depart est encore ouverte, l'anneau se tient quatre blocs devant elle.
 *
 *   - MONDE OUVERT : on passe son portail, on arrive au QG ; repasser l'anneau de la ville ramene
 *     devant le portail d'ou l'on vient, le dos tourne a lui. Aller-retour a volonte ; le lobby
 *     n'est pas rouvert, la partie continue.
 *   - DEFI : passer son portail, c'est QUITTER LE DEFI. Deux secondes dans l'anneau (un pas de
 *     travers ne suffit pas), puis le QG, ou l'on attend l'equipe ; on reapparait dans son
 *     appartement. Le Defi continue pour les autres ; quand plus personne n'y joue, il finit et la
 *     ville rouvre, comme apres une defaite (HavenReturn.bringBack). L'anneau de la ville ne ramene
 *     pas dans le Defi.
 *
 * UN PORTAIL PAR JOUEUR : en poser un autre retire le premier, et rend son objet. Accroupi, un clic
 * droit sur son portail le reprend (un operateur peut reprendre celui de n'importe qui). Il est
 * incassable autrement, comme toutes les portes precurseurs. N'importe qui peut le passer.
 *
 * SAUVEGARDE (Data) : les portails, le portail d'ou chacun est venu, et qui a quitte le Defi en
 * cours -- un redemarrage ne doit ni renvoyer au village celui qui attend en ville, ni oublier un
 * portail fabrique. L'anneau de la ville, lui, est temporaire : pose tant qu'un joueur venu par un
 * portail est en ville, il s'efface de lui-meme ensuite (HavenGateBlockEntity).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenPortal {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Tiques entre deux regards sur les portails. */
    private static final int CHECK = 5;
    /** En Defi : le temps a passer dans l'anneau avant de quitter la partie, en tiques. */
    public static final int ABANDON_HOLD = 40;
    /** L'anneau de la ville se tient a tant de blocs devant l'arche du depart, si elle est ouverte. */
    private static final int BESIDE_DEPARTURE = 4;
    /** On ressort a tant de blocs devant un anneau, le dos tourne a lui. */
    private static final int OUT = 2;

    /** Un portail, ou le portail d'ou l'on vient : sa dimension, son bloc, sa face. */
    public record Spot(ResourceKey<Level> dimension, BlockPos pos, Direction facing) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", this.dimension.location().toString());
            tag.putIntArray("Pos", new int[]{this.pos.getX(), this.pos.getY(), this.pos.getZ()});
            tag.putString("Face", this.facing.getSerializedName());
            return tag;
        }

        @Nullable
        static Spot load(CompoundTag tag) {
            ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dim"));
            int[] p = tag.getIntArray("Pos");
            Direction face = Direction.byName(tag.getString("Face"));
            if (dim == null || p.length != 3 || face == null || !face.getAxis().isHorizontal()) {
                return null;
            }
            return new Spot(ResourceKey.create(Registries.DIMENSION, dim), new BlockPos(p[0], p[1], p[2]), face);
        }

        boolean at(Level level, BlockPos where) {
            return level.dimension().equals(this.dimension) && this.pos.equals(where);
        }
    }

    /** Ce qu'une pose a donne. */
    public enum Placed { POSE, DEPLACE, DANS_HAVEN, SANS_VILLE, PAS_LA_PLACE }

    /** L'anneau de la ville, au QG, s'il est pose. */
    @Nullable
    private static BlockPos ring;
    /** Le temps deja passe dans un anneau en Defi, par joueur. */
    private static final Map<UUID, Integer> DWELL = new HashMap<>();
    /** Les cobayes du banc : comptes comme de vrais joueurs. */
    private static final List<ServerPlayer> SUBJECTS = new ArrayList<>();
    /** L'anneau de la ville est tenu pour l'automate de photos. */
    private static boolean pinned;
    private static int ticks;

    private HavenPortal() {
    }

    // ================================================================ lecture

    /** Les portails poses, les portails d'ou l'on vient, et qui a quitte le Defi. */
    public static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), Data.KEY);
    }

    /** Une partie partie de la ville, qu'un portail peut quitter ou ou il peut ramener. */
    private static boolean away(MinecraftServer server) {
        HavenState state = HavenState.get(server);
        return state.built() && state.phase() == HavenState.Phase.PARTI && Haven.level(server) != null;
    }

    /** Le Defi en cours, qu'un portail peut quitter : ni fini, ni deja rentre (porte de victoire, defaite). */
    private static boolean challenge(MinecraftServer server) {
        GameState game = GameState.get(server.overworld());
        return game.timed() && (game.status() == GameState.Status.PROLOGUE || game.status() == GameState.Status.RUNNING)
                && HavenReturn.stage() == HavenReturn.Stage.AUCUN;
    }

    /** Le Monde ouvert, ou le portail fait l'aller-retour. */
    private static boolean openWorld(MinecraftServer server) {
        return !GameState.get(server.overworld()).timed();
    }

    /** Vrai si ce joueur a quitte le Defi en cours par un portail (HavenReturn.remaining ne le compte plus). */
    public static boolean left(MinecraftServer server, UUID id) {
        return data(server).left.contains(id);
    }

    /** Le joueur est-il a Haven pour de bon, venu par un portail (il ne doit pas etre renvoye au village) ? */
    public static boolean holding(ServerPlayer player) {
        MinecraftServer server = player.server;
        if (!Haven.is(player.level()) || !away(server)) {
            return false;
        }
        Data data = data(server);
        return GameState.get(server.overworld()).timed() ? data.left.contains(player.getUUID())
                : data.back.containsKey(player.getUUID());
    }

    /** Ce bloc est-il un portail pose (et pas un reste, apres qu'on en a pose un autre) ? */
    public static boolean isPortal(Level level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return true;
        }
        for (Spot spot : data(server).portals.values()) {
            if (spot.at(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /** Ce bloc est-il l'anneau de la ville, en ce moment ? */
    public static boolean isRing(Level level, BlockPos pos) {
        return Haven.is(level) && pos.equals(ring);
    }

    /** L'anneau de la ville, en ce moment ; null s'il n'est pas pose. */
    @Nullable
    public static BlockPos ring() {
        return ring;
    }

    // ================================================================ pose et reprise

    /**
     * Pose le portail d'un joueur, la face vers lui ; son ancien portail, s'il en avait un, s'en va.
     *
     * @param feet la cellule ou se dresse l'anneau (il faut un sol et quatre cellules libres)
     */
    public static Placed place(ServerPlayer player, ServerLevel level, BlockPos feet, Direction facing) {
        MinecraftServer server = player.server;
        if (Haven.is(level)) {
            return Placed.DANS_HAVEN;
        }
        if (!HavenState.get(server).built() || Haven.level(server) == null) {
            return Placed.SANS_VILLE;
        }
        if (!HavenReturn.clear(level, feet)) {
            return Placed.PAS_LA_PLACE;
        }
        Data data = data(server);
        Spot old = data.portals.get(player.getUUID());
        boolean moved = old != null && removeBlock(server, old);
        level.setBlock(feet, ModBlocks.HAVEN_GATE.get().defaultBlockState()
                .setValue(HavenGateBlock.FACING, facing)
                .setValue(HavenGateBlock.STYLE, HavenGateBlock.Style.ANNEAU), Block.UPDATE_ALL);
        if (level.getBlockEntity(feet) instanceof HavenGateBlockEntity gate) {
            gate.setOwner(player.getUUID());
        }
        data.portals.put(player.getUUID(), new Spot(level.dimension(), feet.immutable(), facing));
        data.setDirty();
        level.playSound(null, feet, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 0.8F, 1.2F);
        LOGGER.info("Portail de Haven : {} le pose en {} ({}){}", player.getGameProfile().getName(),
                feet.toShortString(), level.dimension().location(), moved ? ", l'ancien s'en va" : "");
        return moved ? Placed.DEPLACE : Placed.POSE;
    }

    /**
     * Accroupi, clic droit sur un portail : son maitre le reprend (un operateur, n'importe lequel).
     *
     * @return vrai si le bloc etait un portail de joueur (le clic est alors pris)
     */
    public static boolean pickUp(ServerPlayer player, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof HavenGateBlockEntity gate) || gate.owner() == null) {
            return false;
        }
        UUID owner = gate.owner();
        if (!owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("item.emeraldweapons.haven_portal.not_yours")
                    .withStyle(ChatFormatting.RED), true);
            return true;
        }
        Data data = data(player.server);
        Spot spot = data.portals.get(owner);
        if (spot != null && spot.at(level, pos)) {
            data.portals.remove(owner);
            data.setDirty();
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        give(player, new ItemStack(ModItems.HAVEN_PORTAL.get()));
        player.displayClientMessage(Component.translatable("item.emeraldweapons.haven_portal.picked")
                .withStyle(ChatFormatting.AQUA), true);
        LOGGER.info("Portail de Haven : {} reprend celui de {} en {}", player.getGameProfile().getName(), owner,
                pos.toShortString());
        return true;
    }

    /** Retire le bloc d'un portail, ou qu'il soit (son tronçon est charge au besoin) ; vrai s'il y etait. */
    private static boolean removeBlock(MinecraftServer server, Spot spot) {
        ServerLevel level = server.getLevel(spot.dimension());
        if (level == null) {
            return false;
        }
        level.getChunk(spot.pos().getX() >> 4, spot.pos().getZ() >> 4);
        if (level.getBlockState(spot.pos()).is(ModBlocks.HAVEN_GATE.get())) {
            level.setBlock(spot.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // ================================================================ la tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % CHECK != 0) {
            return;
        }
        tick(event.getServer());
    }

    static void tick(MinecraftServer server) {
        Data data = data(server);
        if (!away(server)) {
            // le lobby est rouvert, ou il n'y a pas de ville : plus personne n'a quitte de Defi
            if (!data.left.isEmpty()) {
                data.left.clear();
                data.setDirty();
            }
            clearRings(server);
            DWELL.clear();
            return;
        }
        boolean challenge = challenge(server);
        boolean open = openWorld(server);
        boolean wanted = false;
        for (ServerPlayer player : players(server)) {
            if (HavenRules.chantier(player) || !player.isAlive()) {
                continue;
            }
            if (Haven.is(player.level())) {
                DWELL.remove(player.getUUID());
                Spot back = data.back.get(player.getUUID());
                boolean came = open ? back != null : data.left.contains(player.getUUID());
                if (!came) {
                    continue;
                }
                wanted = true;
                BlockPos at = ensureRing(server);
                if (at == null || !HavenReturn.inDoor(player, Vec3.atBottomCenterOf(at))) {
                    continue;
                }
                if (open) {
                    toWorld(server, player, back);
                } else if (!player.isFakePlayer() && ticks % 40 == 0) {
                    player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.portail.sans_retour")
                            .withStyle(ChatFormatting.GRAY), true);
                }
                continue;
            }
            if (data.back.remove(player.getUUID()) != null) {
                // revenu dans le monde autrement (la mort, une commande) : plus de retour en attente
                data.setDirty();
            }
            Spot through = portalAround(data, player);
            if (through == null) {
                DWELL.remove(player.getUUID());
                continue;
            }
            if (open) {
                wanted |= toHaven(server, player, through);     // il vient d'arriver : l'anneau reste
            } else if (challenge && !data.left.contains(player.getUUID())) {
                int held = DWELL.merge(player.getUUID(), CHECK, Integer::sum);
                if (held >= ABANDON_HOLD) {
                    abandon(server, player);
                } else if (!player.isFakePlayer()) {
                    player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.portail.quitter",
                            String.format(java.util.Locale.ROOT, "%.1f", (ABANDON_HOLD - held) / 20.0))
                            .withStyle(ChatFormatting.GOLD), true);
                }
            } else if (!player.isFakePlayer() && ticks % 40 == 0) {
                player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.portail.ferme")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        // plus personne en ville n'est venu par un portail : l'anneau de la ville s'en va
        if (!wanted && !data.left.isEmpty() && challenge) {
            wanted = true;          // qui a quitte le Defi va y arriver ; l'anneau l'attend
        }
        if (!wanted) {
            clearRings(server);
        }
        if (challenge && !data.left.isEmpty() && HavenReturn.remaining(server).isEmpty()) {
            HavenReturn.bringBack(server, "Defi quitte par le portail : plus personne n'y joue");
        }
    }

    /** Les joueurs en ligne, et les cobayes du banc. */
    private static List<ServerPlayer> players(MinecraftServer server) {
        Map<UUID, ServerPlayer> all = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            all.put(player.getUUID(), player);
        }
        for (ServerPlayer subject : SUBJECTS) {
            all.putIfAbsent(subject.getUUID(), subject);
        }
        return new ArrayList<>(all.values());
    }

    /** Le portail dans lequel se tient ce joueur, dans sa dimension ; null s'il n'y en a pas. */
    @Nullable
    private static Spot portalAround(Data data, ServerPlayer player) {
        for (Spot spot : data.portals.values()) {
            if (spot.dimension().equals(player.level().dimension()) && player.blockPosition().closerThan(spot.pos(), 4)
                    && HavenReturn.inDoor(player, Vec3.atBottomCenterOf(spot.pos()))) {
                return spot;
            }
        }
        return null;
    }

    // ================================================================ les passages

    /**
     * Monde ouvert : le portail mene au QG, devant l'anneau de la ville.
     *
     * @return vrai si le joueur est arrive
     */
    private static boolean toHaven(MinecraftServer server, ServerPlayer player, Spot through) {
        ServerLevel haven = Haven.level(server);
        if (haven == null || !arrive(server, player)) {
            return false;
        }
        Data data = data(server);
        data.back.put(player.getUUID(), through);
        data.setDirty();
        if (!player.isFakePlayer()) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.portail.arrivee")
                    .withStyle(ChatFormatting.AQUA));
        }
        LOGGER.info("Portail de Haven : {} rentre au QG (Monde ouvert)", player.getGameProfile().getName());
        return true;
    }

    /**
     * Pose le joueur deux blocs devant l'anneau de la ville, tourne vers le bar ; a defaut
     * d'anneau, dans son appartement.
     *
     * @return faux si la ville n'a ni anneau ni appartement pour lui
     */
    private static boolean arrive(MinecraftServer server, ServerPlayer player) {
        ServerLevel haven = Haven.level(server);
        if (haven == null) {
            return false;
        }
        BlockPos at = ensureRing(server);
        Vec3 target;
        float yaw;
        if (at != null) {
            Direction facing = haven.getBlockState(at).getValue(HavenGateBlock.FACING);
            target = Vec3.atBottomCenterOf(at.relative(facing, OUT));
            yaw = facing.toYRot();
        } else {
            HavenArrival.Placement place = HavenArrival.place(server, player.getUUID(), player.getGameProfile().getName());
            if (place == null) {
                return false;
            }
            target = Vec3.atBottomCenterOf(place.feet());
            yaw = place.yaw();
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
        teleport(player, haven, target, yaw);
        return true;
    }

    /** Monde ouvert : l'anneau de l'appartement ramene devant le portail d'ou l'on vient. */
    private static void toWorld(MinecraftServer server, ServerPlayer player, Spot back) {
        ServerLevel level = server.getLevel(back.dimension());
        if (level == null) {
            level = server.overworld();
        }
        Data data = data(server);
        data.back.remove(player.getUUID());
        data.setDirty();
        BlockPos out = back.pos().relative(back.facing(), OUT);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.2F);
        teleport(player, level, Vec3.atBottomCenterOf(out), back.facing().toYRot());
        LOGGER.info("Portail de Haven : {} repart vers son portail en {}", player.getGameProfile().getName(),
                back.pos().toShortString());
    }

    /** Defi : le joueur quitte la partie, arrive au QG, et attend l'equipe ; il reapparaitra chez lui. */
    private static void abandon(MinecraftServer server, ServerPlayer player) {
        Data data = data(server);
        data.left.add(player.getUUID());
        data.setDirty();
        DWELL.remove(player.getUUID());
        HavenArrival.Placement place = HavenArrival.place(server, player.getUUID(), player.getGameProfile().getName());
        arrive(server, player);
        if (place != null && !player.isFakePlayer()) {
            HavenArrival.setRespawn(player, place);
        }
        int still = HavenReturn.remaining(server).size();
        if (!player.isFakePlayer()) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.portail.quitte", still)
                    .withStyle(ChatFormatting.AQUA));
        }
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != player && !Haven.is(other.level())) {
                other.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.portail.quitte.autres",
                        player.getDisplayName(), still).withStyle(ChatFormatting.GOLD));
            }
        }
        LOGGER.info("Portail de Haven : {} quitte le Defi, {} y joue(nt) encore", player.getGameProfile().getName(), still);
    }

    /** Change de monde, sans chute ni elan ; un cobaye du banc ne change pas de dimension. */
    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 at, float yaw) {
        if (player.isFakePlayer()) {
            return;
        }
        player.teleportTo(level, at.x, at.y, at.z, yaw, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    // ================================================================ l'anneau de la ville

    /** La place de l'anneau de la ville et sa face : celles de l'arche du depart, au QG. */
    public record RingSpot(BlockPos pos, Direction facing) {
    }

    /**
     * La place de l'anneau de la ville : celle de l'arche du depart (la meme a chaque partie), sa
     * face tournee comme elle, vers le centre du bar ; quatre blocs devant elle si elle est encore
     * ouverte. Null si la ville n'a pas de place.
     */
    @Nullable
    public static RingSpot ringSpot(MinecraftServer server) {
        ServerLevel haven = Haven.level(server);
        var place = HavenDeparture.place(server);
        if (haven == null || place == null) {
            return null;
        }
        BlockPos base = place.anchor();
        if (HavenDeparture.isOpen() && HavenDeparture.gate() != null) {
            base = HavenDeparture.gate().relative(HavenDeparture.facing(), BESIDE_DEPARTURE);
        }
        BlockPos spot = HavenReturn.doorSpot(haven, base);
        return spot == null ? null : new RingSpot(spot, place.facing());
    }

    /** L'anneau de la ville, pose s'il ne l'est pas ; null si rien n'y tient. */
    @Nullable
    static BlockPos ensureRing(MinecraftServer server) {
        ServerLevel haven = Haven.level(server);
        if (haven == null) {
            return null;
        }
        if (ring != null && haven.getBlockState(ring).is(ModBlocks.HAVEN_GATE.get())) {
            return ring;
        }
        RingSpot spot = ringSpot(server);
        if (spot == null) {
            return null;
        }
        BlockState state = ModBlocks.HAVEN_GATE.get().defaultBlockState()
                .setValue(HavenGateBlock.FACING, spot.facing())
                .setValue(HavenGateBlock.STYLE, HavenGateBlock.Style.ANNEAU);
        ring = spot.pos().immutable();
        haven.setBlock(ring, state, Block.UPDATE_ALL);
        if (haven.getBlockEntity(ring) instanceof HavenGateBlockEntity gate) {
            gate.setTemporary(true);
        }
        haven.sendParticles(ParticleTypes.END_ROD, ring.getX() + 0.5, ring.getY() + 1.5, ring.getZ() + 0.5, 12, 0.6, 0.8, 0.6, 0.02);
        LOGGER.info("Portail de Haven : anneau de la ville au QG en {} (face {})", ring.toShortString(), spot.facing());
        return ring;
    }

    private static void clearRings(MinecraftServer server) {
        if (ring == null || pinned) {
            return;
        }
        ServerLevel haven = Haven.level(server);
        if (haven != null && haven.getBlockState(ring).is(ModBlocks.HAVEN_GATE.get())) {
            haven.setBlock(ring, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        ring = null;
    }

    /** Pour l'automate de photos : l'anneau de la ville, pose et tenu jusqu'a l'arret. */
    public static void pinForPhoto(MinecraftServer server) {
        pinned = ensureRing(server) != null;
    }

    /** Le retour du Defi (HavenReturn.bringBack) : plus personne n'a quitte de Defi. */
    static void onReturn(MinecraftServer server) {
        Data data = data(server);
        if (!data.left.isEmpty()) {
            data.left.clear();
            data.setDirty();
        }
        DWELL.clear();
        clearRings(server);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ring = null;
        DWELL.clear();
        SUBJECTS.clear();
        pinned = false;
        ticks = 0;
    }

    // ================================================================ banc d'essai

    static void addSubject(ServerPlayer player) {
        SUBJECTS.add(player);
    }

    static void clearSubjects() {
        SUBJECTS.clear();
    }

    /** Pour le banc : une tique des portails tout de suite. */
    static void tickForTest(MinecraftServer server) {
        tick(server);
    }

    /** Pour le banc : le cobaye quitte le Defi comme s'il avait tenu deux secondes dans l'anneau. */
    static void abandonForTest(MinecraftServer server, ServerPlayer player) {
        abandon(server, player);
    }

    // ================================================================ la sauvegarde

    /** Ce que le monde garde des portails. */
    public static final class Data extends SavedData {

        public static final String KEY = "emeraldweapons_haven_portals";

        /** Le portail de chacun. */
        final Map<UUID, Spot> portals = new LinkedHashMap<>();
        /** Monde ouvert : le portail d'ou vient celui qui est chez lui. */
        final Map<UUID, Spot> back = new LinkedHashMap<>();
        /** Defi : ceux qui l'ont quitte par un portail. */
        final Set<UUID> left = new LinkedHashSet<>();

        /** Le portail d'un joueur, ou null. */
        @Nullable
        public Spot portal(UUID id) {
            return this.portals.get(id);
        }

        public int count() {
            return this.portals.size();
        }

        public boolean hasLeft(UUID id) {
            return this.left.contains(id);
        }

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            read(tag.getList("Portals", Tag.TAG_COMPOUND), data.portals);
            read(tag.getList("Back", Tag.TAG_COMPOUND), data.back);
            for (Tag entry : tag.getList("Left", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) entry;
                if (c.hasUUID("Player")) {
                    data.left.add(c.getUUID("Player"));
                }
            }
            return data;
        }

        private static void read(ListTag list, Map<UUID, Spot> into) {
            for (Tag entry : list) {
                CompoundTag c = (CompoundTag) entry;
                Spot spot = Spot.load(c);
                if (c.hasUUID("Player") && spot != null) {
                    into.put(c.getUUID("Player"), spot);
                }
            }
        }

        private static ListTag write(Map<UUID, Spot> from) {
            ListTag list = new ListTag();
            for (Map.Entry<UUID, Spot> entry : from.entrySet()) {
                CompoundTag c = entry.getValue().save();
                c.putUUID("Player", entry.getKey());
                list.add(c);
            }
            return list;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put("Portals", write(this.portals));
            tag.put("Back", write(this.back));
            ListTag left = new ListTag();
            for (UUID id : this.left) {
                CompoundTag c = new CompoundTag();
                c.putUUID("Player", id);
                left.add(c);
            }
            tag.put("Left", left);
            return tag;
        }
    }
}
