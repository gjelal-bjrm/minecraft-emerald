package com.emerald.weather;

import com.emerald.block.ArcPortalBlock;
import com.emerald.block.ArcPortals;
import com.emerald.block.ModBlocks;
import com.emerald.game.GameState;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.mine.Underground;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * LES PORTES DOREES : l'atelier a portee de main, le temps de l'Heure Doree.
 *
 * L'Heure Doree recompense de s'asseoir a l'etabli -- la Forge reussit mieux, l'Etabli ne
 * prend pas son Eclat. Mais l'atelier est au village, et le joueur est a huit cents metres
 * de la, dans une mine. La fenetre recompensait donc ceux qui n'etaient pas partis.
 *
 * « Faire apparaitre des teleporteurs assez proches des joueurs, en indiquant la direction,
 * pour teleporter le joueur au village ; et un portail doit rester jusqu'a quarante-cinq
 * secondes apres la fin, avec un timer. »
 *
 * Deux portes par joueur, donc :
 *
 *   - LA PORTE DES CHAMPS, a dix ou quinze blocs de lui, la ou une arche tient ;
 *   - LA PORTE DU VILLAGE, a cote de l'atelier, commune a tous.
 *
 * On passe dans l'une, on ressort devant l'autre. La porte des champs est PERSONNELLE : la
 * porte du village renvoie chacun a la sienne, la ou il etait, et non a celle du voisin.
 *
 * DES ARCHES D'ARCENCIUM, DOREES (22 sept., cahier §84) : c'etaient six cubes translucides
 * en croix, dont chaque bras teleportait -- « pas pratiques a prendre ». Seul le VOILE
 * teleporte (ArcPortals.inVeil) : on passe a cote sans rien declencher. Et la porte du
 * village n'est plus AU MILIEU des etablis : « en voulant ameliorer mes armes, j'ai
 * accidentellement pris le portail qui m'a re-teleporte en zone de combat ».
 *
 * QUARANTE-CINQ SECONDES DE PLUS apres la fin de la meteo, avec le compte a rebours sur la
 * barre d'action : le temps de finir sa tentative et de rentrer. Passe ce delai, les portes
 * se dissipent -- qui est reste au village rentrera a pied, et c'est le prix de l'avoir su.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class GoldenGate {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** A quelle distance du joueur la porte des champs se leve. */
    private static final int NEAR_MIN = 9;
    private static final int NEAR_MAX = 15;
    /** Combien de points on essaie avant de renoncer. */
    private static final int TRIES = 60;
    /** Le sursis apres la fin de la meteo. */
    private static final int GRACE = 45 * 20;
    /** Le compte a rebours s'affiche a partir d'ici, sur la barre d'action. */
    private static final int COUNTDOWN = 45 * 20;
    /** Pas de retour avant tant de tiques apres une arrivee. */
    private static final int ARRIVAL_REST = 40;
    /** Hors de la dalle de l'atelier, et a cette distance au moins de chaque etabli. */
    private static final int STATION_GAP = 4;

    /** Chaque arche et le cote vers lequel elle regarde. */
    private static final Map<BlockPos, Direction> gates = new HashMap<>();
    /** L'arche des champs de chaque joueur. */
    private static final Map<UUID, BlockPos> fieldOf = new HashMap<>();
    /** L'arche du village, commune. */
    @Nullable
    private static BlockPos villageGate;
    /** Qui est dans un voile cette tique (et lequel), et qui l'etait a la precedente. */
    private static final Map<UUID, BlockPos> touching = new HashMap<>();
    private static final Set<UUID> wasTouching = new HashSet<>();
    private static final Map<UUID, Long> arrivedAt = new HashMap<>();
    /** La tique ou tout se dissipe, ou -1 si les portes ne sont pas levees. */
    private static long closeAt = -1L;
    private static boolean warned;

    private GoldenGate() {
    }

    // --------------------------------------------------------------- cycle

    /** L'Heure Doree commence : une arche pres de chacun, une a cote de l'atelier. */
    public static void begin(ServerLevel level) {
        clear(level);
        BlockPos workshop = GameState.get(level).workshop();
        BlockPos village = GameState.get(level).village();
        BlockPos anchor = workshop.equals(BlockPos.ZERO) ? village : workshop;
        if (anchor.equals(BlockPos.ZERO)) {
            return;                       // pas de village : pas de porte
        }
        ArcPortals.Placement home = workshop.equals(BlockPos.ZERO)
                ? ArcPortals.nearest(level, village, 8, 4, village)
                : besideWorkshop(level, workshop);
        if (home == null) {
            LOGGER.warn("Heure Doree : aucune place pour l'arche du village pres de {} ; pas de portes", anchor);
            return;
        }
        villageGate = home.anchor();
        place(level, home);
        int raised = 0;
        for (ServerPlayer player : level.players()) {
            ArcPortals.Placement field = spotNear(level, player.blockPosition());
            if (field == null) {
                // ON LE DIT. Sans message, le joueur croyait la fenetre cassee.
                player.sendSystemMessage(Component.translatable("weather.emeraldweapons.gate.nowhere")
                        .withStyle(ChatFormatting.GRAY));
                continue;
            }
            fieldOf.put(player.getUUID(), field.anchor());
            place(level, field);
            raised++;
            int distance = (int) Math.round(Underground.flat(field.anchor(), player.blockPosition()));
            player.sendSystemMessage(Component.translatable("weather.emeraldweapons.gate.opened", distance)
                    .withStyle(style -> style.withColor(Weather.HEURE_DOREE.color)));
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.0F, 1.3F);
        }
        closeAt = -1L;
        warned = false;
        LOGGER.info("Heure Doree : arche du village en {} et {} arche(s) des champs {}",
                villageGate, raised, fieldOf.values());
    }

    /** L'Heure Doree finit : les portes tiennent encore quarante-cinq secondes. */
    public static void end(ServerLevel level) {
        if (gates.isEmpty()) {
            return;
        }
        closeAt = level.getGameTime() + GRACE;
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.translatable("weather.emeraldweapons.gate.closing", GRACE / 20)
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    /** Efface tout : a la fin du sursis, ou quand une nouvelle Heure Doree se leve. */
    public static void clear(ServerLevel level) {
        for (BlockPos pos : gates.keySet()) {
            ArcPortals.remove(level, pos);
        }
        gates.clear();
        fieldOf.clear();
        villageGate = null;
        touching.clear();
        wasTouching.clear();
        closeAt = -1L;
        warned = false;
    }

    /** Vrai tant qu'une porte existe : l'Heure Doree, ou son sursis. */
    public static boolean open() {
        return !gates.isEmpty();
    }

    /** Cette arche est-elle une porte doree en service ? (ArcPortalBlockEntity efface les autres.) */
    public static boolean isGate(BlockPos pos) {
        return gates.containsKey(pos);
    }

    /** La porte la plus proche d'un joueur, pour la boussole. */
    @Nullable
    public static BlockPos nearest(BlockPos from) {
        BlockPos best = null;
        for (BlockPos anchor : gates.keySet()) {
            if (best == null || anchor.distSqr(from) < best.distSqr(from)) {
                best = anchor;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- pose

    private static void place(ServerLevel level, ArcPortals.Placement where) {
        ArcPortals.place(level, where.anchor(), where.facing(), ArcPortalBlock.Tint.DOREE);
        gates.put(where.anchor(), where.facing());
    }

    /**
     * LA PORTE DU VILLAGE, A COTE DE L'ATELIER ET NON DEDANS (22 sept.). Elle se posait sur le
     * premier sol degage au centre de l'atelier -- le dessus de l'Etabli a sertir, a hauteur
     * de tete entre la Forge et l'Autel. Elle se pose maintenant au bord de la dalle
     * (Workshop : de -3 a +6 en x, de -4 a +4 en z), du cote de la Lame d'abord, jamais a
     * moins de {@value #STATION_GAP} blocs d'un etabli, et regarde l'atelier : on en sort face
     * aux etablis.
     */
    @Nullable
    private static ArcPortals.Placement besideWorkshop(ServerLevel level, BlockPos centre) {
        int[][] around = {{-7, 0}, {-8, 0}, {1, 8}, {1, -8}, {1, 9}, {1, -9}, {10, 0}, {-7, 4}, {-7, -4}, {11, 0}};
        for (int[] offset : around) {
            BlockPos column = centre.offset(offset[0], 0, offset[1]);
            ArcPortals.Placement found = ArcPortals.at(level, column, 3, 4, centre);
            if (found != null && !nearStation(level, found.anchor())) {
                return found;
            }
        }
        // l'atelier est encaisse : plus loin, tout autour
        ArcPortals.Placement wide = ArcPortals.find(level, centre, 9, 16, 4, 5, TRIES, centre, level.random);
        return wide != null && !nearStation(level, wide.anchor()) ? wide : null;
    }

    /** Un etabli du mode ou de l'atelier (forge, etabli a sertir, autel, enclume...) a portee ? */
    private static boolean nearStation(ServerLevel level, BlockPos at) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -STATION_GAP; dx <= STATION_GAP; dx++) {
            for (int dz = -STATION_GAP; dz <= STATION_GAP; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                    if (state.is(ModBlocks.ARCENCIUM_FORGE.get()) || state.is(ModBlocks.SOCKET_BENCH.get())
                            || state.is(ModBlocks.SPECIALIZATION_ALTAR.get()) || state.is(Blocks.ANVIL)
                            || state.is(Blocks.GRINDSTONE) || state.is(Blocks.SMITHING_TABLE)
                            || state.is(Blocks.CRAFTING_TABLE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Une place pour l'arche a dix ou quinze blocs du joueur, tournee vers lui.
     *
     * SOUS TERRE COMME EN SURFACE : le joueur peut etre au fond d'une mine, et c'est
     * justement la que la porte sert. On cherche donc une arche qui tienne a la hauteur du
     * joueur ou a quelques blocs pres, et non un point de la surface. TROIS PASSES, de la
     * plus belle a la plus sure : l'anneau voulu ; plus loin et plus haut, pour les cavernes
     * etroites et les corniches ; tout pres de lui.
     */
    @Nullable
    private static ArcPortals.Placement spotNear(ServerLevel level, BlockPos from) {
        ArcPortals.Placement found = ArcPortals.find(level, from, NEAR_MIN, NEAR_MAX, 6, 6, TRIES, from, level.random);
        if (found != null) {
            return found;
        }
        found = ArcPortals.find(level, from, 5, 26, 16, 24, TRIES * 2, from, level.random);
        if (found != null) {
            return found;
        }
        return ArcPortals.nearest(level, from, 4, 3, from);
    }

    // -------------------------------------------------------------- voyage

    /**
     * LE PASSAGE : instantane, contrairement aux Brumes d'Aurore.
     *
     * La brume traverse la roche sur trente blocs, et l'arc se voit. Ici on franchit huit
     * cents metres : une courbe demanderait vingt secondes de vol sur une fenetre qui en
     * compte trois cents, et chargerait tous les chunks du chemin. Un eclat, un son, et l'on
     * ressort devant l'autre arche, le dos tourne a elle.
     */
    private static void pass(ServerLevel level, ServerPlayer player, BlockPos here) {
        BlockPos exit;
        boolean toVillage = !here.equals(villageGate);
        if (toVillage) {
            exit = villageGate;
        } else {
            exit = fieldOf.get(player.getUUID());
            if (exit == null) {
                player.displayClientMessage(Component.translatable("weather.emeraldweapons.gate.no_return")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        Direction facing = exit == null ? null : gates.get(exit);
        if (facing == null) {
            return;
        }
        level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.6, 1.0, 0.6, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.0F, 1.3F);
        Vec3 out = ArcPortals.exit(exit, facing);
        player.teleportTo(level, out.x, out.y, out.z, ArcPortals.exitYaw(facing), player.getXRot());
        arrivedAt.put(player.getUUID(), level.getGameTime());
        level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                out.x, out.y + 1.0, out.z, 40, 0.6, 1.0, 0.6, 0.05);
        level.playSound(null, exit, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.1F);
        player.displayClientMessage(Component.translatable(toVillage
                        ? "weather.emeraldweapons.gate.to_village"
                        : "weather.emeraldweapons.gate.to_field")
                .withStyle(style -> style.withColor(Weather.HEURE_DOREE.color)), true);
    }

    // --------------------------------------------------------------- tique

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD) || gates.isEmpty()) {
            touching.clear();
            wasTouching.clear();
            return;
        }
        long now = level.getGameTime();
        // QUI EST DANS UN VOILE : les pieds entre les piliers, pres du plan de l'arche
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            for (Map.Entry<BlockPos, Direction> gate : gates.entrySet()) {
                if (gate.getKey().distSqr(player.blockPosition()) <= 16.0
                        && ArcPortals.inVeil(player, gate.getKey(), gate.getValue())) {
                    touching.put(player.getUUID(), gate.getKey());
                    break;
                }
            }
        }
        // LES DEPARTS : qui vient d'ENTRER dans un voile, et n'arrive pas a l'instant.
        for (Map.Entry<UUID, BlockPos> entry : touching.entrySet()) {
            UUID id = entry.getKey();
            if (wasTouching.contains(id) || now - arrivedAt.getOrDefault(id, -1000L) < ARRIVAL_REST) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                pass(level, player, entry.getValue());
            }
        }
        wasTouching.clear();
        wasTouching.addAll(touching.keySet());
        touching.clear();

        // LE COMPTE A REBOURS, sur la barre d'action : on doit savoir combien
        // il reste avant de rentrer a pied.
        if (closeAt >= 0) {
            int left = (int) (closeAt - now);
            if (left <= 0) {
                clear(level);
                for (ServerPlayer player : level.players()) {
                    player.sendSystemMessage(Component.translatable("weather.emeraldweapons.gate.closed")
                            .withStyle(ChatFormatting.GRAY));
                }
                return;
            }
            if (left <= COUNTDOWN && now % 20 == 0) {
                Component line = Component.translatable("weather.emeraldweapons.gate.countdown", left / 20)
                        .withStyle(style -> style.withColor(left <= 200 ? 0xFF616B : 0xFFC46B));
                for (ServerPlayer player : level.players()) {
                    player.displayClientMessage(line, true);
                }
                if (!warned && left <= 200) {
                    warned = true;
                    for (ServerPlayer player : level.players()) {
                        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(),
                                SoundSource.PLAYERS, 0.8F, 1.4F);
                    }
                }
            }
        }
        // les motes qui montent de chaque arche : on les voit de loin
        if (now % 10 == 0) {
            for (BlockPos anchor : gates.keySet()) {
                level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                        anchor.getX() + 0.5, anchor.getY() + 1.6, anchor.getZ() + 0.5,
                        3, 0.35, 0.8, 0.35, 0.01);
            }
        }
    }

    /**
     * A l'arret, les arches s'en vont AVANT la sauvegarde : elles ne restent pas dans le monde.
     * En dernier, apres WeatherManager qui finit la meteo en cours.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        if (!gates.isEmpty()) {
            clear(event.getServer().overworld());
        }
    }

    /** Les arches, pour les essais. */
    public static List<BlockPos> anchors() {
        return new ArrayList<>(gates.keySet());
    }

    /** Pour les essais : le cote vers lequel regarde une arche, ou null. */
    @Nullable
    public static Direction facing(BlockPos anchor) {
        return gates.get(anchor);
    }

    /** Pour les essais : l'arche du village, ou null. */
    @Nullable
    public static BlockPos villageGate() {
        return villageGate;
    }
}
