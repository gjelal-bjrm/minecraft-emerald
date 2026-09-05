package com.emerald.mine;

import com.emerald.block.ModBlocks;
import com.emerald.game.GameState;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.Weather;
import com.emerald.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LES POCHES : la decouverte.
 *
 * En cassant un minerai sous y = 48, une chance sur douze (une sur six pendant
 * l'Aurore) qu'il s'ouvre : un craquement, un echo creux, et derriere lui une
 * CAVITE qu'on n'a pas creusee. Tu ne creuses plus VERS quelque chose, tu
 * tombes dessus. Au fond, tire au sort :
 *
 *   la Geode      50 %   parois d'amethyste et cristaux ; pierres d'element et
 *                        plumes posees au sol, a ramasser
 *   la Cache      30 %   un coffre -- fer et or, ou or et diamant en profondeur,
 *                        et une rune une fois sur trois
 *   le Filon riche 15 %  six a dix blocs d'Arcencium d'un coup
 *   le Vide        5 %   la cavite est plus grande, NOIRE, et une elite y dort
 *
 * LE VIDE EST LE PARI. Sans lui, les Poches sont un distributeur -- on sait
 * qu'on gagne, on cesse d'y penser. Avec lui, le craquement de la roche fait
 * lever la tete a chaque fois ; c'est ce qui rend les dix-neuf autres
 * memorables. Il est VERROUILLE tant que moins de deux sanctuaires sont tenus :
 * avant, le joueur n'est pas pret a se battre dans le noir a trois blocs.
 *
 * Aucun message : le trou parle de lui-meme. La cavite s'ouvre bloc par bloc
 * par le chantier des Percees (Breakthrough), avec les memes garde-fous --
 * roche naturelle seulement, rien a moins d'un bloc d'un fluide, sinon rien.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Pockets {

    /** Une chance sur tant, par minerai casse ; deux fois plus sous l'Aurore. */
    private static final int CHANCE = 12;
    /** Combien de sanctuaires tenus avant que le Vide puisse s'ouvrir. */
    private static final int VIDE_UNLOCK = 2;
    /** La marque de l'elite du Vide : elle paie double. */
    public static final String TAG_VIDE = "emeraldweapons_vide";
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public enum Kind { GEODE, CACHE, FILON, VIDE }

    private Pockets() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
                || !Underground.ore(event.getState())
                || !Underground.allowed(level, event.getPos())) {
            return;
        }
        int chance = WeatherManager.current() == Weather.AURORE ? CHANCE / 2 : CHANCE;
        if (level.random.nextInt(chance) != 0) {
            return;
        }
        Direction away = awayFrom(player, event.getPos());
        open(level, event.getPos(), away, null);
    }

    /** La direction du regard, quantifiee : la poche s'ouvre DERRIERE le minerai. */
    private static Direction awayFrom(ServerPlayer player, BlockPos ore) {
        Vec3 eye = player.getEyePosition();
        return Direction.getNearest(ore.getX() + 0.5 - eye.x, ore.getY() + 0.5 - eye.y,
                ore.getZ() + 0.5 - eye.z);
    }

    /**
     * Ouvre une poche derriere un bloc, dans une direction. Sert aux Percees
     * (une sur six debouche sur une poche) et a la commande d'essai.
     *
     * @return faux si l'emprise n'etait pas entierement de la roche
     */
    public static boolean open(ServerLevel level, BlockPos from, Direction away, @Nullable Kind forced) {
        Kind kind = forced != null ? forced : roll(level);
        if (kind == Kind.VIDE && GameState.get(level).anchorsActive() < VIDE_UNLOCK) {
            kind = forced != null ? Kind.GEODE : roll(level);
            if (kind == Kind.VIDE) {
                kind = Kind.GEODE;
            }
        }
        Direction right = away.getAxis().isHorizontal() ? away.getClockWise() : Direction.EAST;
        Direction up = away.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH;
        ArrayDeque<List<BlockPos>> slices = new ArrayDeque<>();
        List<BlockPos> footprint = new ArrayList<>();

        // LA GORGE : un bloc derriere le minerai, puis la cavite
        BlockPos throat = from.relative(away);
        slices.add(List.of(throat));
        footprint.add(throat);
        BlockPos centre = throat.relative(away, 2);
        int reach = kind == Kind.VIDE ? 2 : 1;
        int height = kind == Kind.VIDE ? 4 : 2;
        int depth = kind == Kind.VIDE ? 2 : 1;
        // du plus proche au plus loin : la cavite s'ouvre en s'eloignant
        for (int d = -depth; d <= depth; d++) {
            List<BlockPos> slice = new ArrayList<>();
            for (int w = -reach; w <= reach; w++) {
                for (int h = 0; h < height; h++) {
                    // un blob, pas une boite : on rogne les coins
                    if (kind != Kind.VIDE && Math.abs(w) + Math.abs(d) > 2) {
                        continue;
                    }
                    slice.add(centre.relative(away, d).relative(right, w).relative(up, h));
                }
            }
            slices.add(slice);
            footprint.addAll(slice);
        }
        if (!Breakthrough.clear(level, footprint)) {
            // On DIT pourquoi : une poche qui ne s'ouvre pas sans un mot dans le
            // journal est une enigme de plus a la prochaine seance.
            LOGGER.info("Poche {} refusee en {} : l'emprise ({} blocs) touche autre chose que de la roche",
                    kind, from, footprint.size());
            return false;
        }
        LOGGER.info("Poche {} ouverte derriere {} vers {} ({} blocs)", kind, from, away, footprint.size());
        level.playSound(null, from, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.0F, 0.5F);
        level.playSound(null, from, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.9F, 0.6F);
        final Kind chosen = kind;
        Breakthrough.enqueue(level, slices, List.of(), away,
                carved -> furnish(level, chosen, centre, up, carved));
        return true;
    }

    private static Kind roll(ServerLevel level) {
        int r = level.random.nextInt(100);
        return r < 50 ? Kind.GEODE : r < 80 ? Kind.CACHE : r < 95 ? Kind.FILON : Kind.VIDE;
    }

    // -------------------------------------------------------------- le fond

    /** La cavite est ouverte : on y pose ce qu'elle cache. */
    private static void furnish(ServerLevel level, Kind kind, BlockPos centre, Direction up, Set<BlockPos> carved) {
        List<BlockPos> walls = walls(level, carved);
        BlockPos floor = lowest(carved, centre);
        switch (kind) {
            case GEODE -> geode(level, walls, carved, floor);
            case CACHE -> cache(level, floor, centre);
            case FILON -> filon(level, walls);
            case VIDE -> vide(level, floor);
        }
    }

    /** Les blocs de roche qui bordent la cavite, dans un ordre au sort. */
    private static List<BlockPos> walls(ServerLevel level, Set<BlockPos> carved) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> walls = new ArrayList<>();
        for (BlockPos pos : carved) {
            for (Direction side : Direction.values()) {
                BlockPos wall = pos.relative(side);
                if (!carved.contains(wall) && seen.add(wall)
                        && Underground.natural(level.getBlockState(wall))) {
                    walls.add(wall);
                }
            }
        }
        Collections.shuffle(walls, new java.util.Random(level.random.nextLong()));
        return walls;
    }

    /** Le point le plus bas de la cavite, le plus proche du centre : la ou l'on pose. */
    private static BlockPos lowest(Set<BlockPos> carved, BlockPos centre) {
        BlockPos best = centre;
        int bestY = Integer.MAX_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : carved) {
            double dist = pos.distSqr(centre);
            if (pos.getY() < bestY || (pos.getY() == bestY && dist < bestDist)) {
                bestY = pos.getY();
                bestDist = dist;
                best = pos;
            }
        }
        return best;
    }

    /**
     * La Geode : la moitie des parois en amethyste, des cristaux qui poussent
     * vers l'interieur, et au sol ce qui se ramasse -- pierres d'element,
     * plumes. Les cristaux eclairent : c'est la seule poche qu'on VOIT s'ouvrir.
     */
    private static void geode(ServerLevel level, List<BlockPos> walls, Set<BlockPos> carved, BlockPos floor) {
        RandomSource random = level.random;
        int lined = 0;
        for (BlockPos wall : walls) {
            if (random.nextInt(100) < 45) {
                level.setBlock(wall, Blocks.AMETHYST_BLOCK.defaultBlockState(), 3);
                lined++;
                // un cristal sur la face qui donne dans la cavite
                for (Direction side : Direction.values()) {
                    BlockPos inside = wall.relative(side);
                    if (carved.contains(inside) && level.getBlockState(inside).isAir()
                            && lined % 3 == 0) {
                        level.setBlock(inside, Blocks.AMETHYST_CLUSTER.defaultBlockState()
                                .setValue(AmethystClusterBlock.FACING, side), 3);
                        break;
                    }
                }
            }
        }
        List<com.emerald.element.Element> flavours = new ArrayList<>();
        for (com.emerald.element.Element element : com.emerald.element.Element.values()) {
            if (element != com.emerald.element.Element.NEUTRE) {
                flavours.add(element);
            }
        }
        Vec3 at = Vec3.atCenterOf(floor);
        int stones = 2 + random.nextInt(2);
        for (int i = 0; i < stones; i++) {
            com.emerald.element.Element flavour = flavours.get(random.nextInt(flavours.size()));
            drop(level, at, com.emerald.element.ElementStoneItem.stack(flavour,
                    com.emerald.item.ModItems.ELEMENT_STONE.get(), 1 + random.nextInt(2)));
        }
        drop(level, at, new ItemStack(com.emerald.item.ModItems.ARCENCIUM_FEATHER.get(), 1 + random.nextInt(2)));
        level.playSound(null, floor, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
    }

    /** La Cache : un coffre, rempli selon la profondeur, et une rune une fois sur trois. */
    private static void cache(ServerLevel level, BlockPos floor, BlockPos centre) {
        RandomSource random = level.random;
        Direction facing = Direction.getNearest(centre.getX() - floor.getX(), 0, centre.getZ() - floor.getZ());
        if (!facing.getAxis().isHorizontal()) {
            facing = Direction.NORTH;
        }
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing.getOpposite());
        level.setBlock(floor, chest, 3);
        if (!(level.getBlockEntity(floor) instanceof ChestBlockEntity box)) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>();
        if (floor.getY() < 0) {
            loot.add(new ItemStack(Items.DIAMOND, 2 + random.nextInt(3)));
            loot.add(new ItemStack(Items.GOLD_INGOT, 3 + random.nextInt(4)));
        } else {
            loot.add(new ItemStack(Items.IRON_INGOT, 6 + random.nextInt(5)));
            loot.add(new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(3)));
        }
        loot.add(new ItemStack(Items.TORCH, 8 + random.nextInt(8)));
        if (random.nextInt(3) == 0) {
            loot.add(com.emerald.rune.RuneItem.stack(
                    com.emerald.rune.RuneDrops.simulate(60.0, random),
                    com.emerald.item.ModItems.RUNE.get()));
        }
        // eparpilles dans le coffre, pas alignes : c'est une cache, pas un etal
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < box.getContainerSize(); i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, new java.util.Random(random.nextLong()));
        for (int i = 0; i < loot.size(); i++) {
            box.setItem(slots.get(i), loot.get(i));
        }
    }

    /** Le Filon riche : six a dix blocs d'Arcencium dans les parois. */
    private static void filon(ServerLevel level, List<BlockPos> walls) {
        int count = Math.min(walls.size(), 6 + level.random.nextInt(5));
        for (int i = 0; i < count; i++) {
            level.setBlock(walls.get(i), ModBlocks.ARCENCIUM_ORE.get().defaultBlockState(), 3);
        }
        level.playSound(null, walls.isEmpty() ? BlockPos.ZERO : walls.get(0),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.7F);
    }

    /**
     * Le Vide : rien a voir, et une elite qui dort. La garnison des sanctuaires
     * fournit la bete, attachee a la cavite -- elle ne vient pas vous chercher
     * dehors, c'est vous qui entrez.
     */
    private static void vide(ServerLevel level, BlockPos floor) {
        com.emerald.game.SanctuaryGarrison.postGuard(level, floor.above(), 4, TAG_VIDE);
        level.playSound(null, floor, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    private static void drop(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level, at.x, at.y + 0.3, at.z, stack);
        item.setDeltaMovement(0.0, 0.05, 0.0);
        level.addFreshEntity(item);
    }
}
