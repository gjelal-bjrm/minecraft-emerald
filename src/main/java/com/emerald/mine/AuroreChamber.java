package com.emerald.mine;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.Weather;
import com.emerald.weather.WeatherEffects;
import com.emerald.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LA CHAMBRE D'AURORE : la roche qui s'ouvre sur une brume, et sa jumelle la
 * ou cela vaut le voyage.
 *
 * L'idee est du joueur : « quand on mine, une probabilite que ca creuse une
 * chambre de cinq sur cinq, et au centre le filon qui permet de se
 * teleporter -- mais des chemins qui valent la peine, qui menent vers des
 * endroits utiles. »
 *
 * En cassant de la roche naturelle sous y = 48 PENDANT L'AURORE, une chance
 * sur quarante, avec trente secondes de repit : devant soi, une chambre de
 * cinq sur cinq sur quatre s'ouvre bloc par bloc (la mecanique des Percees),
 * et une brume etoilee se leve en son centre. Sa jumelle se leve, dans cet
 * ordre :
 *
 *   1. CONTRE LE FILON que la boussole designe (diamant sans pioche en
 *      diamant, Arcencium avec) : une poche de trois sur trois est creusee
 *      contre le minerai, on arrive dessus ;
 *   2. sinon dans la plus grande grotte que le sondage connaisse ;
 *   3. sinon au jour, a la surface au-dessus de la chambre.
 *
 * Le message dit ou mene la brume. Un chemin qu'on ne comprend pas n'est pas
 * un chemin.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class AuroreChamber {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Une chance sur tant, par bloc de roche casse pendant l'Aurore. */
    private static final int CHANCE = 40;
    /** Trente secondes entre deux chambres pour un meme joueur. */
    private static final int REST = 30 * 20;
    /** La chambre : cinq de large, quatre de haut, cinq de profond, a deux blocs devant. */
    private static final int HALF = 2;
    private static final int HEIGHT = 4;
    private static final int AHEAD = 2;

    private static final Map<UUID, Long> rest = new HashMap<>();

    private AuroreChamber() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
                || WeatherManager.current() != Weather.AURORE
                || !Underground.natural(event.getState())
                || !Underground.allowed(level, event.getPos())) {
            return;
        }
        long now = level.getGameTime();
        if (now < rest.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        if (level.random.nextInt(CHANCE) != 0) {
            return;
        }
        rest.put(player.getUUID(), now + REST);
        if (!open(level, player)) {
            rest.remove(player.getUUID());          // la roche n'a pas cede : on ne compte pas
        }
    }

    /**
     * Ouvre la chambre devant le joueur. Sert aussi a la commande d'essai.
     *
     * @return faux si l'emprise n'etait pas entierement de la roche
     */
    public static boolean open(ServerLevel level, ServerPlayer player) {
        Direction facing = player.getDirection();
        Direction right = facing.getClockWise();
        BlockPos feet = player.blockPosition();
        ArrayDeque<List<BlockPos>> slices = new ArrayDeque<>();
        List<BlockPos> footprint = new ArrayList<>();
        for (int d = AHEAD; d < AHEAD + 2 * HALF + 1; d++) {
            List<BlockPos> slice = new ArrayList<>();
            for (int w = -HALF; w <= HALF; w++) {
                for (int h = 0; h < HEIGHT; h++) {
                    BlockPos cell = feet.relative(facing, d).relative(right, w).above(h);
                    slice.add(cell);
                    footprint.add(cell);
                }
            }
            slices.add(slice);
        }
        if (!Breakthrough.clear(level, footprint)) {
            return false;
        }
        BlockPos centre = feet.relative(facing, AHEAD + HALF);
        level.playSound(null, feet, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.2F, 0.5F);
        level.playSound(null, feet, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.0F, 0.7F);
        Breakthrough.enqueue(level, slices, List.of(), facing, carved -> finish(level, player, centre));
        return true;
    }

    /** La chambre est ouverte : la brume au centre, et sa jumelle la ou cela vaut le voyage. */
    private static void finish(ServerLevel level, ServerPlayer player, BlockPos centre) {
        Twin twin = twinFor(level, player, centre);
        if (twin == null) {
            LOGGER.info("Chambre d'Aurore : ouverte en {} mais aucune jumelle possible", centre);
            return;
        }
        if (!AuroreCaves.placePair(level, centre, twin.anchor())) {
            LOGGER.info("Chambre d'Aurore : ouverte en {}, la paire n'a pas pu se lever", centre);
            return;
        }
        int distance = (int) Math.round(Math.sqrt(centre.distSqr(twin.anchor())));
        net.minecraft.network.chat.MutableComponent line = switch (twin.kind()) {
            case VEIN -> Component.translatable("mine.emeraldweapons.aurore.chamber.vein",
                    Component.translatable(twin.arcencium() ? "weather.emeraldweapons.vein.arcencium"
                            : "weather.emeraldweapons.vein.diamond"), distance);
            case CAVE -> Component.translatable("mine.emeraldweapons.aurore.chamber.cave", distance);
            default -> Component.translatable("mine.emeraldweapons.aurore.chamber.surface",
                    twin.anchor().getY() - centre.getY());
        };
        player.sendSystemMessage(line.withStyle(style -> style.withColor(0x9CE8FF)));
        LOGGER.info("Chambre d'Aurore : {} -> {} ({}, {} blocs)", centre, twin.anchor(), twin.kind(), distance);
    }

    private enum Kind { VEIN, CAVE, SURFACE }

    private record Twin(BlockPos anchor, Kind kind, boolean arcencium) {
    }

    /** Ou la jumelle se leve : le filon designe, une grotte, le jour. */
    @Nullable
    private static Twin twinFor(ServerLevel level, ServerPlayer player, BlockPos centre) {
        BlockPos vein = WeatherEffects.guidedVein(player);
        if (vein != null) {
            BlockPos pocket = pocketBeside(level, vein, centre);
            if (pocket != null) {
                return new Twin(pocket, Kind.VEIN, level.getBlockState(vein).is(ModBlocks.ARCENCIUM_ORE.get()));
            }
        }
        BlockPos cave = AuroreCaves.bestCave(level, centre);
        if (cave != null && cave.distSqr(centre) > 12.0 * 12.0) {
            return new Twin(cave, Kind.CAVE, false);
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre);
        if (top.getY() > centre.getY() + 4 && level.getBlockState(top).isAir()) {
            return new Twin(top, Kind.SURFACE, false);
        }
        return null;
    }

    /**
     * UNE POCHE DE TROIS SUR TROIS CONTRE LE MINERAI, du cote qui regarde la
     * chambre en premier. Tout ce qu'elle emporte doit etre de la roche : on ne
     * perce jamais une base ni un lac. Le minerai lui-meme reste en place --
     * on arrive dessus, on ne le vole pas.
     */
    @Nullable
    private static BlockPos pocketBeside(ServerLevel level, BlockPos vein, BlockPos from) {
        List<Direction> sides = new ArrayList<>();
        Direction first = Direction.getNearest(from.getX() - vein.getX(), 0, from.getZ() - vein.getZ());
        sides.add(first);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d != first) {
                sides.add(d);
            }
        }
        for (Direction side : sides) {
            BlockPos core = vein.relative(side, 2);
            List<BlockPos> cells = new ArrayList<>();
            for (int a = -1; a <= 1; a++) {
                for (int h = -1; h <= 1; h++) {
                    for (int b = -1; b <= 1; b++) {
                        cells.add(core.offset(a, h, b));
                    }
                }
            }
            if (cells.contains(vein) || !Breakthrough.clear(level, cells)) {
                continue;
            }
            for (BlockPos cell : cells) {
                BlockState was = level.getBlockState(cell);
                if (!was.isAir() && !Underground.ore(was)) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            level.levelEvent(2001, core, Block.getId(Blocks.STONE.defaultBlockState()));
            return core.below();                     // le sol de la poche : la ou l'on se pose
        }
        return null;
    }
}
