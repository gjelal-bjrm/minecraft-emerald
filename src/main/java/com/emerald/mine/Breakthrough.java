package com.emerald.mine;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * LES PERCEES : la roche qui s'ouvre.
 *
 * « Quand on mine de la pierre, aleatoirement, ca casse des filons et ca cree
 * des chemins, pour que les joueurs descendent ou avancent plus vite. »
 *
 * En cassant de la PIERRE (jamais un minerai) sous y = 48, une chance sur
 * quarante-cinq que la roche CEDE : un craquement, un grondement, et devant
 * soi un passage s'ouvre BLOC PAR BLOC, en s'eloignant -- on voit la fissure
 * courir, on ne recoit pas un trou. Quatre formes :
 *
 *   le Couloir    1x2, 6 a 14 blocs droit devant      -> avancer vite
 *   l'Escalier    descend d'un bloc par bloc, 5 a 10   -> descendre sans sauter
 *   la Cheminee   2x2 sur 6 a 12 blocs, qui tombe dans une chambre a l'eau,
 *                 d'ou repart un couloir              -> descendre d'un coup
 *   la Salle      5x4x5                                -> respirer, poser une base
 *
 * « CA CASSE DES FILONS » : la percee suit une faille, et les minerais qu'elle
 * croise restent dans ses parois, exposes -- plus deux a quatre qu'elle y
 * plante elle-meme. On ne detruit jamais un minerai, on le decouvre.
 *
 * Les garde-fous sont absolus : on ne creuse que de la roche naturelle, on
 * s'arrete NET devant tout autre bloc (jamais une base, jamais un coffre), on
 * refuse toute emprise qui touche l'eau ou la lave, vingt secondes de repit
 * entre deux, et jamais pres du village ni sous un sanctuaire (Underground).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Breakthrough {

    /** Une chance sur tant, par bloc de pierre casse. */
    private static final int CHANCE = 45;
    /** Le repit entre deux percees pour un meme joueur : vingt secondes. */
    private static final int REST = 400;
    /** Ce qu'une percee plante dans ses parois. */
    private static final int ORES_MIN = 2;
    private static final int ORES_MAX = 4;

    public enum Shape { COULOIR, ESCALIER, CHEMINEE, SALLE }

    /** Un chantier en cours : ses tranches, ce qu'il a deja ouvert, et son eau. */
    private static final class Dig {
        final ServerLevel level;
        final ArrayDeque<List<BlockPos>> slices;
        final Set<BlockPos> carved = new HashSet<>();
        final List<BlockPos> water;
        final Shape shape;

        Dig(ServerLevel level, ArrayDeque<List<BlockPos>> slices, List<BlockPos> water, Shape shape) {
            this.level = level;
            this.slices = slices;
            this.water = water;
            this.shape = shape;
        }
    }

    private static final List<Dig> digs = new ArrayList<>();
    private static final Map<UUID, Long> rest = new HashMap<>();

    private Breakthrough() {
    }

    // ------------------------------------------------------------ l'evenement

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
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
        Shape shape = roll(level.random);
        if (!open(level, player, event.getPos(), shape)) {
            // l'emprise touchait autre chose que de la roche : on n'insiste pas,
            // et l'on rend le repit -- ce n'etait pas une percee
            rest.remove(player.getUUID());
        }
    }

    private static Shape roll(RandomSource random) {
        int r = random.nextInt(100);
        return r < 40 ? Shape.COULOIR : r < 70 ? Shape.ESCALIER : r < 85 ? Shape.CHEMINEE : Shape.SALLE;
    }

    /**
     * Ouvre une percee depuis un bloc, dans la direction du regard du joueur.
     * Sert aussi a la commande d'essai. Rend faux si l'emprise n'etait pas
     * entierement de la roche.
     */
    public static boolean open(ServerLevel level, ServerPlayer player, BlockPos from, Shape shape) {
        Direction facing = player.getDirection();
        Plan plan = plan(level, from, facing, shape);
        if (plan == null) {
            return false;
        }
        digs.add(new Dig(level, plan.slices, plan.water, shape));
        // LE CRAQUEMENT, puis le grondement : on entend la roche ceder avant de
        // la voir s'ouvrir
        level.playSound(null, from, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
        level.playSound(null, from, SoundEvents.ANCIENT_DEBRIS_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
        player.displayClientMessage(Component.translatable("mine.emeraldweapons.breakthrough")
                .withStyle(ChatFormatting.GRAY), true);
        return true;
    }

    // ---------------------------------------------------------------- le plan

    private record Plan(ArrayDeque<List<BlockPos>> slices, List<BlockPos> water) {
    }

    /**
     * L'emprise, tranche par tranche, dans l'ordre ou elle s'ouvrira.
     *
     * Chaque tranche est ce qui s'ouvre en UNE tique : on voit la fissure
     * courir. Avant d'en ouvrir une seule, on verifie TOUTE l'emprise : de la
     * roche, de l'air ou du minerai, rien d'autre, et pas une goutte de fluide
     * a son contact. Sinon, pas de percee du tout -- on ne creuse pas a moitie
     * dans la cave de quelqu'un.
     */
    @Nullable
    private static Plan plan(ServerLevel level, BlockPos from, Direction facing, Shape shape) {
        RandomSource random = level.random;
        ArrayDeque<List<BlockPos>> slices = new ArrayDeque<>();
        List<BlockPos> water = new ArrayList<>();
        Direction right = facing.getClockWise();
        switch (shape) {
            case COULOIR -> {
                int length = 6 + random.nextInt(9);
                for (int i = 1; i <= length; i++) {
                    BlockPos step = from.relative(facing, i);
                    slices.add(List.of(step, step.above()));
                }
            }
            case ESCALIER -> {
                int steps = 5 + random.nextInt(6);
                for (int i = 1; i <= steps; i++) {
                    BlockPos step = from.relative(facing, i).below(i);
                    slices.add(List.of(step, step.above(), step.above(2)));
                }
                // un palier au bout, pour ne pas finir le nez dans la roche
                BlockPos landing = from.relative(facing, steps + 1).below(steps);
                slices.add(List.of(landing, landing.above(), landing.relative(facing), landing.relative(facing).above()));
            }
            case CHEMINEE -> {
                int depth = 6 + random.nextInt(7);
                BlockPos top = from.relative(facing);
                BlockPos[] shaft = {top, top.relative(right), top.relative(facing), top.relative(facing).relative(right)};
                // la gorge : quatre blocs par niveau, du haut vers le bas
                for (int d = 0; d <= depth; d++) {
                    List<BlockPos> slice = new ArrayList<>(4);
                    for (BlockPos column : shaft) {
                        slice.add(column.below(d));
                    }
                    slices.add(slice);
                }
                // la chambre : 3x3, trois de haut, dont le sol est d'eau -- on y
                // tombe sans se faire mal, quelle que soit la hauteur
                BlockPos floor = top.relative(facing).below(depth + 1);
                for (int h = 0; h < 3; h++) {
                    List<BlockPos> slice = new ArrayList<>(9);
                    for (int a = -1; a <= 1; a++) {
                        for (int b = -1; b <= 1; b++) {
                            BlockPos cell = floor.relative(facing, a).relative(right, b).above(h);
                            slice.add(cell);
                            if (h == 0) {
                                water.add(cell);
                            }
                        }
                    }
                    slices.add(slice);
                }
                // et l'on repart : un couloir depuis la chambre, au-dessus de l'eau
                for (int i = 2; i <= 7; i++) {
                    BlockPos step = floor.relative(facing, i).above();
                    slices.add(List.of(step, step.above()));
                }
            }
            case SALLE -> {
                for (int i = 1; i <= 5; i++) {
                    List<BlockPos> slice = new ArrayList<>(20);
                    for (int w = -2; w <= 2; w++) {
                        for (int h = 0; h < 4; h++) {
                            slice.add(from.relative(facing, i).relative(right, w).above(h));
                        }
                    }
                    slices.add(slice);
                }
            }
        }
        // LA VERIFICATION, avant le premier bloc
        for (List<BlockPos> slice : slices) {
            for (BlockPos pos : slice) {
                if (pos.getY() < level.getMinBuildHeight() + 2) {
                    return null;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && !Underground.natural(state) && !Underground.ore(state)) {
                    return null;                  // autre chose que de la roche : on s'arrete la
                }
                for (Direction side : Direction.values()) {
                    if (!level.getFluidState(pos.relative(side)).isEmpty()) {
                        return null;              // l'eau ou la lave entrerait : non
                    }
                }
            }
        }
        return new Plan(slices, water);
    }

    // ------------------------------------------------------------ le chantier

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || digs.isEmpty()
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        var it = digs.iterator();
        while (it.hasNext()) {
            Dig dig = it.next();
            if (dig.level != level) {
                continue;
            }
            List<BlockPos> slice = dig.slices.poll();
            if (slice != null) {
                carve(dig, slice);
                continue;
            }
            finish(dig);
            it.remove();
        }
    }

    /** Une tranche s'ouvre : la roche part, le minerai reste. */
    private static void carve(Dig dig, List<BlockPos> slice) {
        ServerLevel level = dig.level;
        for (BlockPos pos : slice) {
            BlockState state = level.getBlockState(pos);
            if (!Underground.natural(state)) {
                continue;                          // de l'air, ou un minerai qu'on garde
            }
            // l'evenement 2001 : le bruit et les eclats du bloc, pour tous ceux
            // qui regardent -- c'est ce qui fait courir la fissure
            level.levelEvent(2001, pos, Block.getId(state));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            dig.carved.add(pos);
        }
    }

    /** Le chantier fini : l'eau de la chambre, et les filons dans les parois. */
    private static void finish(Dig dig) {
        ServerLevel level = dig.level;
        for (BlockPos pos : dig.water) {
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
        }
        plantOres(level, dig.carved);
        if (!dig.carved.isEmpty()) {
            BlockPos any = dig.carved.iterator().next();
            level.playSound(null, any, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7F, 0.8F);
        }
    }

    /**
     * LA FAILLE EXPOSE SES FILONS : deux a quatre minerais dans les parois.
     *
     * Choisis dans la roche naturelle qui borde ce qu'on vient d'ouvrir, jamais
     * a la place d'autre chose. Le minerai suit la profondeur -- ardoise sous
     * zero -- et une fois sur quatre l'un d'eux est de l'Arcencium : c'est
     * NOTRE sous-sol, il doit le dire de temps en temps.
     */
    private static void plantOres(ServerLevel level, Set<BlockPos> carved) {
        List<BlockPos> walls = new ArrayList<>();
        for (BlockPos pos : carved) {
            for (Direction side : Direction.values()) {
                BlockPos wall = pos.relative(side);
                if (!carved.contains(wall) && Underground.natural(level.getBlockState(wall))) {
                    walls.add(wall);
                }
            }
        }
        if (walls.isEmpty()) {
            return;
        }
        Collections.shuffle(walls, new java.util.Random(level.random.nextLong()));
        int count = Math.min(walls.size(), ORES_MIN + level.random.nextInt(ORES_MAX - ORES_MIN + 1));
        boolean arcencium = level.random.nextInt(4) == 0;
        for (int i = 0; i < count; i++) {
            BlockPos wall = walls.get(i);
            BlockState ore = i == 0 && arcencium
                    ? ModBlocks.ARCENCIUM_ORE.get().defaultBlockState()
                    : oreFor(level.random, wall.getY());
            level.setBlock(wall, ore, 3);
        }
    }

    private static BlockState oreFor(RandomSource random, int y) {
        boolean deep = y < 0;
        int r = random.nextInt(100);
        Block ore;
        if (r < 38) {
            ore = deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
        } else if (r < 56) {
            ore = deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
        } else if (r < 72) {
            ore = deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
        } else if (r < 86) {
            ore = deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
        } else if (r < 94) {
            ore = deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
        } else {
            ore = y < 16 ? (deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE)
                    : (deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE);
        }
        return ore.defaultBlockState();
    }
}
