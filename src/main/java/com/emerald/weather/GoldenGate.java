package com.emerald.weather;

import com.emerald.block.ModBlocks;
import com.emerald.game.GameState;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.mine.Underground;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * L'Heure Doree recompense de s'asseoir a l'etabli -- la Forge reussit quinze
 * points de plus, l'Etabli ne prend pas son Eclat. Mais l'atelier est au
 * village, et le joueur est a huit cents metres de la, dans une mine. La
 * fenetre recompensait donc ceux qui n'etaient pas partis.
 *
 * « Faire apparaitre des teleporteurs assez proches des joueurs, en indiquant
 * la direction, pour teleporter le joueur au village ; et un portail doit
 * rester jusqu'a quarante-cinq secondes apres la fin, avec un timer. »
 *
 * Deux portes par joueur, donc :
 *
 *   - LA PORTE DES CHAMPS, a dix ou quinze blocs de lui, sur un sol degage ;
 *   - LA PORTE DU VILLAGE, a l'atelier, commune a tous.
 *
 * On entre dans l'une, on ressort par l'autre. La porte des champs est
 * PERSONNELLE : la porte du village renvoie chacun a la sienne, la ou il
 * etait, et non a celle du voisin.
 *
 * QUARANTE-CINQ SECONDES DE PLUS apres la fin de la meteo, avec le compte a
 * rebours sur la barre d'action : le temps de finir sa tentative et de
 * rentrer. Passe ce delai, les portes se dissipent -- qui est reste au village
 * rentrera a pied, et c'est le prix de l'avoir su.
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

    /** Chaque bloc de porte et son ancre. */
    private static final Map<BlockPos, BlockPos> gateBlocks = new HashMap<>();
    /** L'ancre des champs de chaque joueur. */
    private static final Map<UUID, BlockPos> fieldOf = new HashMap<>();
    /** L'ancre du village, commune. */
    private static BlockPos villageGate;
    /** Qui touche une porte cette tique, et qui la touchait a la precedente. */
    private static final Set<UUID> touching = new HashSet<>();
    private static final Set<UUID> wasTouching = new HashSet<>();
    private static final Map<UUID, Long> arrivedAt = new HashMap<>();
    /** La tique ou tout se dissipe, ou -1 si les portes ne sont pas levees. */
    private static long closeAt = -1L;
    private static boolean warned;

    private GoldenGate() {
    }

    // --------------------------------------------------------------- cycle

    /** L'Heure Doree commence : une porte pres de chacun, une a l'atelier. */
    public static void begin(ServerLevel level) {
        clear(level);
        BlockPos workshop = GameState.get(level).workshop();
        BlockPos village = GameState.get(level).village();
        BlockPos anchor = workshop.equals(BlockPos.ZERO) ? village : workshop;
        if (anchor.equals(BlockPos.ZERO)) {
            return;                       // pas de village : pas de porte
        }
        villageGate = openGround(level, anchor, 2, 6);
        if (villageGate == null) {
            villageGate = anchor.above();
        }
        place(level, villageGate);
        int raised = 0;
        for (ServerPlayer player : level.players()) {
            BlockPos field = spotNear(level, player.blockPosition());
            if (field == null) {
                // ON LE DIT. Sans message, le joueur croyait la fenetre cassee.
                player.sendSystemMessage(Component.translatable("weather.emeraldweapons.gate.nowhere")
                        .withStyle(ChatFormatting.GRAY));
                continue;
            }
            fieldOf.put(player.getUUID(), field);
            place(level, field);
            raised++;
            int distance = (int) Math.round(Underground.flat(field, player.blockPosition()));
            player.sendSystemMessage(Component.translatable("weather.emeraldweapons.gate.opened", distance)
                    .withStyle(style -> style.withColor(Weather.HEURE_DOREE.color)));
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.0F, 1.3F);
        }
        closeAt = -1L;
        warned = false;
        LOGGER.info("Heure Doree : porte du village en {} et {} porte(s) des champs {}",
                villageGate, raised, fieldOf.values());
    }

    /** L'Heure Doree finit : les portes tiennent encore quarante-cinq secondes. */
    public static void end(ServerLevel level) {
        if (gateBlocks.isEmpty()) {
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
        for (BlockPos pos : gateBlocks.keySet()) {
            if (level.getBlockState(pos).is(ModBlocks.GOLDEN_GATE.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.0);
            }
        }
        gateBlocks.clear();
        fieldOf.clear();
        villageGate = null;
        closeAt = -1L;
        warned = false;
    }

    /** Vrai tant qu'une porte existe : l'Heure Doree, ou son sursis. */
    public static boolean open() {
        return !gateBlocks.isEmpty();
    }

    /** La porte la plus proche d'un joueur, pour la boussole. */
    @Nullable
    public static BlockPos nearest(BlockPos from) {
        BlockPos best = null;
        for (BlockPos anchor : new HashSet<>(gateBlocks.values())) {
            if (best == null || anchor.distSqr(from) < best.distSqr(from)) {
                best = anchor;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- pose

    /** Six blocs : une croix au sol et un de haut, comme les brumes. */
    private static void place(ServerLevel level, BlockPos anchor) {
        BlockPos[] cloud = {anchor, anchor.above(), anchor.north(), anchor.south(),
                anchor.east(), anchor.west()};
        for (BlockPos pos : cloud) {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, ModBlocks.GOLDEN_GATE.get().defaultBlockState(), 3);
                gateBlocks.put(pos, anchor);
            }
        }
        level.playSound(null, anchor, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.8F, 1.4F);
    }

    /**
     * Un sol degage a dix ou quinze blocs du joueur.
     *
     * SOUS TERRE COMME EN SURFACE : le joueur peut etre au fond d'une mine, et
     * c'est justement la que la porte sert. On cherche donc un endroit ou l'on
     * peut se tenir -- deux blocs d'air sur du solide -- a la hauteur du joueur
     * ou a quelques blocs pres, et non un point de la surface.
     */
    @Nullable
    private static BlockPos spotNear(ServerLevel level, BlockPos from) {
        // TROIS PASSES, DE LA PLUS BELLE A LA PLUS SURE. La premiere cherche
        // l'anneau voulu ; la deuxieme s'autorise plus loin et plus haut, pour
        // les cavernes etroites et les corniches ; la troisieme ouvre aux pieds
        // du joueur. Une seule passe rendait zero porte des qu'il n'y avait pas
        // de sol a six blocs -- en vol, sur un toit, au bord d'un gouffre.
        BlockPos found = ring(level, from, NEAR_MIN, NEAR_MAX, 6, 6, TRIES);
        if (found != null) {
            return found;
        }
        found = ring(level, from, 5, 26, 16, 24, TRIES * 2);
        if (found != null) {
            return found;
        }
        return level.getBlockState(from).isAir() && level.getBlockState(from.above()).isAir()
                ? from : null;
    }

    /** Un sol degage tire au hasard dans un anneau autour du joueur. */
    @Nullable
    private static BlockPos ring(ServerLevel level, BlockPos from, double near, double far,
                                 int up, int down, int tries) {
        for (int i = 0; i < tries; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = near + level.random.nextDouble() * (far - near);
            int x = (int) Math.round(from.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(from.getZ() + Math.sin(angle) * distance);
            BlockPos found = openGround(level, new BlockPos(x, from.getY(), z), up, down);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Deux blocs d'air sur du solide, en cherchant vers le haut puis vers le bas. */
    @Nullable
    private static BlockPos openGround(ServerLevel level, BlockPos around, int up, int down) {
        if (!level.hasChunk(around.getX() >> 4, around.getZ() >> 4)) {
            return null;
        }
        for (int dy = 0; dy <= Math.max(up, down); dy++) {
            for (int sign : dy == 0 ? new int[]{1} : new int[]{1, -1}) {
                if (sign > 0 && dy > up || sign < 0 && dy > down) {
                    continue;
                }
                BlockPos pos = around.above(dy * sign);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isSolid()) {
                    return pos;
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------- voyage

    /** Un joueur est dans une porte cette tique (GoldenGateBlock). */
    public static void touch(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (gateBlocks.containsKey(pos)) {
            touching.add(player.getUUID());
        }
    }

    /**
     * LE PASSAGE : instantane, contrairement aux Brumes d'Aurore.
     *
     * La brume traverse la roche sur trente blocs, et l'arc se voit. Ici on
     * franchit huit cents metres : une courbe demanderait vingt secondes de
     * vol sur une fenetre qui en compte trois cents, et chargerait tous les
     * chunks du chemin. Un eclat, un son, et l'on y est.
     */
    private static void pass(ServerLevel level, ServerPlayer player) {
        BlockPos here = gateBlocks.get(player.blockPosition());
        if (here == null) {
            for (Map.Entry<BlockPos, BlockPos> entry : gateBlocks.entrySet()) {
                if (entry.getKey().distSqr(player.blockPosition()) <= 2.0) {
                    here = entry.getValue();
                    break;
                }
            }
        }
        if (here == null) {
            return;
        }
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
        if (exit == null) {
            return;
        }
        level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.6, 1.0, 0.6, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.PLAYERS, 1.0F, 1.3F);
        player.teleportTo(exit.getX() + 0.5, exit.getY(), exit.getZ() + 0.5);
        arrivedAt.put(player.getUUID(), level.getGameTime());
        level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                exit.getX() + 0.5, exit.getY() + 1.0, exit.getZ() + 0.5, 40, 0.6, 1.0, 0.6, 0.05);
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
                || !level.dimension().equals(Level.OVERWORLD) || gateBlocks.isEmpty()) {
            touching.clear();
            wasTouching.clear();
            return;
        }
        long now = level.getGameTime();
        // LES DEPARTS : qui vient d'ENTRER, et qui n'arrive pas a l'instant.
        for (UUID id : touching) {
            if (wasTouching.contains(id) || now - arrivedAt.getOrDefault(id, -1000L) < 40) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                pass(level, player);
            }
        }
        wasTouching.clear();
        wasTouching.addAll(touching);
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
        // les motes qui montent de chaque porte : on les voit de loin
        if (now % 10 == 0) {
            for (BlockPos anchor : new HashSet<>(gateBlocks.values())) {
                level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                        anchor.getX() + 0.5, anchor.getY() + 1.2, anchor.getZ() + 0.5,
                        3, 0.35, 0.6, 0.35, 0.01);
            }
        }
    }

    /** Les ancres, pour les essais. */
    public static List<BlockPos> anchors() {
        return new ArrayList<>(new HashSet<>(gateBlocks.values()));
    }
}
