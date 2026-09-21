package com.emerald.haven;

import com.emerald.block.HavenGateBlock;
import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les transports de Haven (cahier §82) : les deux modeles de porte precurseur que le joueur
 * a gardes apres les photos de la victoire (« l'arche permettrait de passer d'un bout de la
 * ville a l'autre, avec un cooldown pour eviter les spams abuse, et le portail de monter aux
 * etages superieurs des deux tours »).
 *
 * L'ARCHE : une a chaque bout de la ville, a la pointe nord du bras ouest et a celle du bras
 * est -- les deux bouts du fer a cheval. On la traverse A PIED et l'on ressort devant l'autre,
 * dos a elle ; puis {@link #ARCH_COOLDOWN} tiques de recharge pour ce joueur.
 *
 * LE PORTAIL : un plateau a chaque arret des deux tours du large. Elles sont creuses (coupes
 * du volume, cahier §81.2) : on s'y tient au quai du pied, sur la terrasse en croix et sur
 * l'anneau du sommet. Debout sur le plateau, on REGARDE EN HAUT une seconde pour monter a
 * l'arret suivant, EN BAS pour descendre ; il faut ensuite detourner le regard avant de
 * repartir. Dans Haven, seuls les monstres blessent : une chute depuis la terrasse ne coute rien.
 *
 * Les places sont en cellules du volume de la ville, choisies sur les cartes du volume
 * (sol plein sous une emprise de 3 x 3, cinq cellules d'air au-dessus des arches, quatre
 * au-dessus des plateaux). Poses et retires comme la borne, le bouton et le ratelier
 * (HavenVote.voteBlockWanted : ville finie, phase ACCUEIL) ; le releve de l'atelier les
 * ignore (JakCityCapture.managed). L'operateur en chantier ne les declenche pas.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenGates {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** La recharge de l'arche, par joueur : vingt secondes. */
    public static final int ARCH_COOLDOWN = 20 * 20;
    /** Le regard vers le haut ou le bas, tenu tant de tiques, fait partir le portail. */
    public static final int LOOK_TICKS = 20;
    /** Au-dela de cet angle (en degres), le joueur regarde en haut ou en bas. */
    public static final float LOOK_ANGLE = 45.0F;
    /** Le passage de l'arche : a moins de cela de son centre, en blocs. */
    public static final double ARCH_REACH = 0.9;
    /** Le plateau du portail : a moins de cela de son centre, en blocs. */
    public static final double PAD_REACH = 1.3;
    /** On ressort de l'arche a cette distance, devant elle. */
    private static final int ARCH_EXIT = 2;
    /** En s'approchant d'une arche a moins de cela, on lit ou elle mene ; au-dela de {@link #NEAR_LEAVE}, on l'oublie. */
    private static final double NEAR_ENTER = 5.0;
    private static final double NEAR_LEAVE = 8.0;
    /** Rappels dans la barre d'action, en tiques. */
    private static final int HINT_EVERY = 20;

    /** Une place de transport. */
    public enum Kind { ARCHE, PORTAIL }

    /**
     * Une station : sa cellule (les pieds, dans le volume de la ville), son sens, son
     * groupe (les deux arches ; les trois arrets d'une tour) et son rang dans le groupe.
     */
    public record Station(String id, Kind kind, BlockPos cell, Direction facing, String group, int level) {

        public BlockState state() {
            return ModBlocks.HAVEN_GATE.get().defaultBlockState()
                    .setValue(HavenGateBlock.FACING, this.facing)
                    .setValue(HavenGateBlock.STYLE, this.kind == Kind.ARCHE ? HavenGateBlock.Style.ARCHE
                            : HavenGateBlock.Style.PORTAIL);
        }
    }

    /**
     * Les stations. Arches : sol en y 61, pieds en 62 (le bras des appartements est plus
     * bas que la rue) ; tours : quai 65, terrasse 122, sommet 155 (les sols ; pieds + 1).
     * Les plateaux des tours sont tous au nord, cote ville.
     */
    public static final List<Station> STATIONS = List.of(
            new Station("arche_ouest", Kind.ARCHE, new BlockPos(60, 62, 100), Direction.SOUTH, "arches", 0),
            new Station("arche_est", Kind.ARCHE, new BlockPos(1180, 62, 100), Direction.SOUTH, "arches", 1),
            new Station("tour_ouest_pied", Kind.PORTAIL, new BlockPos(482, 66, 585), Direction.NORTH, "tour_ouest", 0),
            new Station("tour_ouest_terrasse", Kind.PORTAIL, new BlockPos(482, 123, 590), Direction.NORTH, "tour_ouest", 1),
            new Station("tour_ouest_sommet", Kind.PORTAIL, new BlockPos(482, 156, 605), Direction.NORTH, "tour_ouest", 2),
            new Station("tour_est_pied", Kind.PORTAIL, new BlockPos(770, 66, 585), Direction.NORTH, "tour_est", 0),
            new Station("tour_est_terrasse", Kind.PORTAIL, new BlockPos(770, 123, 590), Direction.NORTH, "tour_est", 1),
            new Station("tour_est_sommet", Kind.PORTAIL, new BlockPos(770, 156, 605), Direction.NORTH, "tour_est", 2));

    /** 2|16 : ni voisins prevenus, ni formes recalculees. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Fin de recharge de l'arche, par joueur (tique de jeu de Haven). */
    private static final Map<UUID, Long> ARCH_READY = new HashMap<>();
    /** Le regard tenu sur un plateau : tiques, et le sens (+1 en haut, -1 en bas). */
    private static final Map<UUID, int[]> LOOK = new HashMap<>();
    /** Les joueurs qui doivent detourner le regard avant de repartir (arrives par le portail). */
    private static final Set<UUID> DISARMED = new LinkedHashSet<>();
    /** Le dernier rappel dans la barre d'action, par joueur. */
    private static final Map<UUID, Long> HINTED = new HashMap<>();
    /** Les joueurs qui ont deja lu ou mene l'arche pres de laquelle ils se tiennent. */
    private static final Set<UUID> NEAR_ARCH = new LinkedHashSet<>();
    /** Les cobayes du banc. */
    private static final Map<UUID, ServerPlayer> SUBJECTS = new HashMap<>();

    @Nullable
    private static Boolean lastWanted;
    private static int ticks;
    private static final Set<String> WARNED = new LinkedHashSet<>();

    private HavenGates() {
    }

    // ================================================================ lecture

    @Nullable
    public static Station station(String id) {
        for (Station station : STATIONS) {
            if (station.id().equals(id)) {
                return station;
            }
        }
        return null;
    }

    /** La place d'une station dans le monde. */
    public static BlockPos position(MinecraftServer server, Station station) {
        return HavenState.get(server).origin().offset(station.cell());
    }

    /** L'autre arche, ou l'arret du dessus (+1) ou du dessous (-1) d'une tour ; null s'il n'y en a pas. */
    @Nullable
    public static Station next(Station from, int step) {
        for (Station station : STATIONS) {
            if (station.group().equals(from.group()) && station != from) {
                if (from.kind() == Kind.ARCHE || station.level() == from.level() + step) {
                    return station;
                }
            }
        }
        return null;
    }

    /** Les transports sont la : ville finie, lobby en attente de joueurs. */
    public static boolean wanted(MinecraftServer server) {
        return Haven.level(server) != null && HavenVote.voteBlockWanted(HavenState.get(server));
    }

    // ================================================================ la pose

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        boolean wanted = wanted(server);
        if (lastWanted == null || lastWanted != wanted) {
            keep(server, true);
        } else if (++ticks % 20 == 0) {
            keep(server, false);
        }
        lastWanted = wanted;
        if (wanted) {
            tick(server);
        }
    }

    /**
     * Pose ou retire toutes les stations. Pose refusee (et signalee une fois) si la case est
     * prise par autre chose que de l'air, un bloc remplacable ou la porte elle-meme.
     *
     * @param load charger les troncons qui ne le sont pas
     * @return les stations posees
     */
    public static int keep(MinecraftServer server, boolean load) {
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return 0;
        }
        boolean wanted = wanted(server);
        Block gate = ModBlocks.HAVEN_GATE.get();
        int placed = 0;
        for (Station station : STATIONS) {
            BlockPos pos = position(server, station);
            if (load) {
                level.getChunkAt(pos);
            } else if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState current = level.getBlockState(pos);
            if (wanted) {
                BlockState want = station.state();
                if (current == want) {
                    placed++;
                    continue;
                }
                if (current.is(gate) || current.isAir() || current.canBeReplaced()) {
                    level.setBlock(pos, want, QUIET);
                    WARNED.remove(station.id());
                    placed++;
                    if (!current.is(gate)) {
                        LOGGER.info("ville de Haven : {} pose en {}", station.id(), pos.toShortString());
                    }
                } else if (WARNED.add(station.id())) {
                    LOGGER.warn("ville de Haven : {} non pose, case prise par {} en {}", station.id(), current,
                            pos.toShortString());
                }
            } else if (current.is(gate)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            }
        }
        return placed;
    }

    // ================================================================ la tique

    /** Un passage sur tous les joueurs de la ville. */
    static void tick(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return;
        }
        long now = level.getGameTime();
        List<ServerPlayer> players = new ArrayList<>(level.players());
        for (ServerPlayer subject : SUBJECTS.values()) {
            if (subject.level() == level && !players.contains(subject)) {
                players.add(subject);
            }
        }
        for (ServerPlayer player : players) {
            if (!player.isAlive() || player.isSpectator() || HavenRules.chantier(player)) {
                LOOK.remove(player.getUUID());
                continue;
            }
            update(server, level, player, now);
        }
    }

    /** Un joueur : dans une arche, ou sur un plateau ? */
    public static void update(MinecraftServer server, ServerLevel level, ServerPlayer player, long now) {
        UUID id = player.getUUID();
        Station pad = null;
        boolean nearArch = false;
        for (Station station : STATIONS) {
            Vec3 center = Vec3.atBottomCenterOf(position(server, station));
            double dx = player.getX() - center.x;
            double dz = player.getZ() - center.z;
            double dy = player.getY() - center.y;
            double flat = dx * dx + dz * dz;
            if (station.kind() == Kind.ARCHE) {
                if (flat <= ARCH_REACH * ARCH_REACH && dy >= -0.5 && dy <= 2.5) {
                    arch(server, level, player, station, now);
                    return;
                }
                if (flat <= NEAR_LEAVE * NEAR_LEAVE && Math.abs(dy) <= 6.0) {
                    nearArch = true;
                    if (flat <= NEAR_ENTER * NEAR_ENTER && NEAR_ARCH.add(id)) {
                        // en s'approchant : ou elle mene, une fois
                        Station to = next(station, 0);
                        if (to != null) {
                            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.arche.vers",
                                    archSide(to)).withStyle(ChatFormatting.AQUA), true);
                            HINTED.put(id, now);
                        }
                    }
                }
            } else if (flat <= PAD_REACH * PAD_REACH && dy >= -0.3 && dy <= 1.2) {
                pad = station;
            }
        }
        if (!nearArch) {
            NEAR_ARCH.remove(id);
        }
        if (pad == null) {
            LOOK.remove(id);
            DISARMED.remove(id);
            return;
        }
        portal(server, level, player, pad, now);
    }

    // ================================================================ l'arche

    private static void arch(MinecraftServer server, ServerLevel level, ServerPlayer player, Station from, long now) {
        UUID id = player.getUUID();
        if (player.isPassenger()) {
            hint(player, now, Component.translatable("game.emeraldweapons.haven.arche.vehicule")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        long ready = ARCH_READY.getOrDefault(id, Long.MIN_VALUE);
        if (now < ready) {
            long seconds = (ready - now + 19) / 20;
            hint(player, now, Component.translatable("game.emeraldweapons.haven.arche.recharge", seconds)
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        Station to = next(from, 0);
        if (to == null) {
            return;
        }
        BlockPos gate = position(server, to);
        BlockPos exit = gate.relative(to.facing(), ARCH_EXIT);
        Vec3 fromCenter = Vec3.atBottomCenterOf(position(server, from));
        level.playSound(null, fromCenter.x, fromCenter.y, fromCenter.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.1F);
        level.sendParticles(ParticleTypes.END_ROD, fromCenter.x, fromCenter.y + 1.4, fromCenter.z, 20, 0.5, 0.9, 0.2, 0.02);
        move(player, level, exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5, to.facing().toYRot(), 0.0F);
        level.playSound(null, exit, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.sendParticles(ParticleTypes.END_ROD, gate.getX() + 0.5, gate.getY() + 1.4, gate.getZ() + 0.5, 20, 0.5, 0.9, 0.2, 0.02);
        ARCH_READY.put(id, now + ARCH_COOLDOWN);
        HINTED.put(id, now);
        // on arrive pres de l'autre arche : son rappel « vers... » ne doit pas couvrir l'arrivee
        NEAR_ARCH.add(id);
        player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.arche.arrivee", archSide(to))
                .withStyle(ChatFormatting.AQUA), true);
        LOGGER.info("ville de Haven : {} passe l'arche, de {} a {}", player.getGameProfile().getName(), from.id(), to.id());
    }

    // ================================================================ le portail

    private static void portal(MinecraftServer server, ServerLevel level, ServerPlayer player, Station pad, long now) {
        UUID id = player.getUUID();
        Station up = next(pad, 1);
        Station down = next(pad, -1);
        float pitch = player.getXRot();
        int wanted = pitch <= -LOOK_ANGLE && up != null ? 1 : pitch >= LOOK_ANGLE && down != null ? -1 : 0;
        if (DISARMED.contains(id)) {
            // arrive par le portail : il doit d'abord detourner le regard
            if (Math.abs(pitch) < LOOK_ANGLE) {
                DISARMED.remove(id);
            }
            wanted = 0;
        }
        if (player.isPassenger()) {
            wanted = 0;
        }
        if (wanted == 0) {
            LOOK.remove(id);
            hint(player, now, padHint(pad, up != null, down != null));
            return;
        }
        final int dir = wanted;
        int[] look = LOOK.computeIfAbsent(id, k -> new int[]{0, dir});
        if (look[1] != dir) {
            look[0] = 0;
            look[1] = dir;
        }
        look[0]++;
        Vec3 center = Vec3.atBottomCenterOf(position(server, pad));
        if (look[0] % 4 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 0.3 + (wanted > 0 ? look[0] * 0.08 : 2.0 - look[0] * 0.08),
                    center.z, 3, 0.4, 0.1, 0.4, 0.0);
        }
        if (look[0] < LOOK_TICKS) {
            player.displayClientMessage(Component.translatable(wanted > 0 ? "game.emeraldweapons.haven.portail.monte"
                    : "game.emeraldweapons.haven.portail.descend").withStyle(ChatFormatting.AQUA), true);
            HINTED.put(id, now);
            return;
        }
        LOOK.remove(id);
        Station to = wanted > 0 ? up : down;
        BlockPos arrival = position(server, to);
        // sur le plateau, un peu au-dessus de sa dalle : on retombe dessus
        move(player, level, arrival.getX() + 0.5, arrival.getY() + 0.25, arrival.getZ() + 0.5, player.getYRot(), 0.0F);
        DISARMED.add(id);
        level.playSound(null, arrival, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.4F);
        level.sendParticles(ParticleTypes.END_ROD, arrival.getX() + 0.5, arrival.getY() + 1.0, arrival.getZ() + 0.5,
                24, 0.5, 1.0, 0.5, 0.03);
        player.displayClientMessage(stationName(to).withStyle(ChatFormatting.AQUA), true);
        HINTED.put(id, now);
        LOGGER.info("ville de Haven : {} prend le portail, de {} a {}", player.getGameProfile().getName(), pad.id(), to.id());
    }

    /** « Tour ouest, la terrasse : regarde en haut pour monter, en bas pour descendre. » */
    private static Component padHint(Station pad, boolean up, boolean down) {
        String key = up && down ? "game.emeraldweapons.haven.portail.aide"
                : up ? "game.emeraldweapons.haven.portail.aide.haut" : "game.emeraldweapons.haven.portail.aide.bas";
        return Component.translatable(key, stationName(pad)).withStyle(ChatFormatting.AQUA);
    }

    /** « le bout est de la ville » : le cote d'une arche, tire de son identifiant. */
    public static MutableComponent archSide(Station arch) {
        String side = arch.id().substring(arch.id().indexOf('_') + 1);
        return Component.translatable("game.emeraldweapons.haven.arche.bout." + side);
    }

    /** « Tour ouest, la terrasse ». */
    public static MutableComponent stationName(Station station) {
        return Component.translatable("game.emeraldweapons.haven.portail.arret",
                Component.translatable("game.emeraldweapons.haven.portail." + station.group()),
                Component.translatable("game.emeraldweapons.haven.portail.niveau." + station.level()));
    }

    // ================================================================ outils

    /** Un rappel dans la barre d'action, une fois par seconde au plus. */
    private static void hint(ServerPlayer player, long now, Component text) {
        Long last = HINTED.get(player.getUUID());
        if (last != null && now - last < HINT_EVERY) {
            return;
        }
        HINTED.put(player.getUUID(), now);
        player.displayClientMessage(text, true);
    }

    /** Le deplacement dans la ville. Un cobaye du banc n'a pas de connexion : on le pose. */
    private static void move(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.stopRiding();
        if (player.isFakePlayer()) {
            player.moveTo(x, y, z, yaw, pitch);
        } else {
            player.teleportTo(level, x, y, z, yaw, pitch);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
    }

    // ================================================================ evenements

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LOOK.remove(id);
        DISARMED.remove(id);
        HINTED.remove(id);
        NEAR_ARCH.remove(id);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ARCH_READY.clear();
        LOOK.clear();
        DISARMED.clear();
        HINTED.clear();
        NEAR_ARCH.clear();
        SUBJECTS.clear();
        WARNED.clear();
        lastWanted = null;
        ticks = 0;
    }

    // ================================================================ banc d'essai

    public static void addSubject(ServerPlayer player) {
        SUBJECTS.put(player.getUUID(), player);
    }

    public static void removeSubject(UUID player) {
        SUBJECTS.remove(player);
        ARCH_READY.remove(player);
        LOOK.remove(player);
        DISARMED.remove(player);
        HINTED.remove(player);
        NEAR_ARCH.remove(player);
    }

    /** Pour le banc : la recharge de l'arche de ce joueur est finie. */
    public static void rechargeForTest(UUID player) {
        ARCH_READY.remove(player);
    }

    /** Pour le banc : la tique des transports, sur la ville du serveur. */
    public static void tickForTest(MinecraftServer server) {
        tick(server);
    }
}
