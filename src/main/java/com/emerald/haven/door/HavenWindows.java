package com.emerald.haven.door;

import com.emerald.block.HavenWindowBlock;
import com.emerald.block.entity.HavenWindowBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LES FENETRES de vitres de Jak 3 (cahier §101) : une fenetre, ce sont les vitres du meme axe
 * reliees dans leur plan. Un clic sur l'une ferme ou rouvre l'iris de toutes (toggle) ; une
 * vitre posee prend l'etat de sa fenetre et l'agrandit (joined), une vitre cassee la reduit ou la
 * coupe en deux (left). Chaque vitre garde le rectangle de sa fenetre : l'iris est UNE ellipse,
 * centree sur la fenetre entiere, et chacune en dessine sa part.
 */
public final class HavenWindows {

    /** L'iris met tant de tiques a se fermer ou a se rouvrir : une seconde et un cinquieme. */
    public static final int DURATION = 24;
    /** Une fenetre ne compte pas plus de vitres (le parcours s'arrete la). */
    static final int MAX_PANES = 512;

    /** Une fenetre : ses vitres, et son rectangle en blocs (le long de l'axe, en hauteur), bornes comprises. */
    public record Window(List<BlockPos> panes, int u0, int u1, int v0, int v1) {
    }

    private HavenWindows() {
    }

    /** La fenetre de cette vitre : ses voisines du meme axe, de proche en proche, dans son plan. */
    public static Window group(BlockGetter level, BlockPos from) {
        BlockState first = level.getBlockState(from);
        if (!(first.getBlock() instanceof HavenWindowBlock)) {
            return new Window(List.of(), 0, 0, 0, 0);
        }
        Direction.Axis axis = first.getValue(HavenWindowBlock.AXIS);
        List<BlockPos> panes = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(from.immutable());
        seen.add(from.immutable());
        int u0 = Integer.MAX_VALUE;
        int u1 = Integer.MIN_VALUE;
        int v0 = Integer.MAX_VALUE;
        int v1 = Integer.MIN_VALUE;
        while (!queue.isEmpty() && panes.size() < MAX_PANES) {
            BlockPos pos = queue.poll();
            panes.add(pos);
            int u = axis == Direction.Axis.X ? pos.getX() : pos.getZ();
            u0 = Math.min(u0, u);
            u1 = Math.max(u1, u);
            v0 = Math.min(v0, pos.getY());
            v1 = Math.max(v1, pos.getY());
            for (Direction direction : HavenWindowBlock.sides(axis)) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next) && HavenWindowBlock.continues(level.getBlockState(next), axis)) {
                    queue.add(next);
                }
            }
        }
        return new Window(List.copyOf(panes), u0, u1, v0, v1);
    }

    /**
     * Un clic : la fenetre entiere se ferme, ou se rouvre. En pleine course, l'iris fait demi-tour
     * d'ou il est -- la courbe est symetrique, il repart sans a-coup.
     */
    public static void toggle(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof HavenWindowBlockEntity clicked)) {
            return;
        }
        Window window = group(level, pos);
        long now = level.getGameTime();
        long elapsed = now - clicked.start();
        long start = elapsed < DURATION ? now - (DURATION - elapsed) : now;
        boolean closing = !clicked.closed();
        apply(level, window, closing, start);
        double cx = 0.0;
        double cy = 0.0;
        double cz = 0.0;
        for (BlockPos pane : window.panes()) {
            cx += pane.getX() + 0.5;
            cy += pane.getY() + 0.5;
            cz += pane.getZ() + 0.5;
        }
        int n = Math.max(1, window.panes().size());
        level.playSound(null, cx / n, cy / n, cz / n, closing ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT,
                SoundSource.BLOCKS, Math.min(1.0F, 0.5F + 0.05F * n), 1.35F);
    }

    /** Toute la fenetre a cet etat, depuis cette tique : chaque vitre le garde avec le rectangle. */
    static void apply(Level level, Window window, boolean closed, long start) {
        for (BlockPos pane : window.panes()) {
            if (level.getBlockEntity(pane) instanceof HavenWindowBlockEntity entity) {
                entity.set(closed, start, window.u0(), window.u1(), window.v0(), window.v1());
            }
        }
    }

    /** Une vitre posee : elle prend l'etat de la fenetre qu'elle rejoint, et la fenetre s'agrandit. */
    public static void joined(Level level, BlockPos pos) {
        Window window = group(level, pos);
        HavenWindowBlockEntity model = null;
        for (BlockPos pane : window.panes()) {
            if (!pane.equals(pos) && level.getBlockEntity(pane) instanceof HavenWindowBlockEntity other) {
                model = other;
                break;
            }
        }
        if (model != null) {
            apply(level, window, model.closed(), model.start());
        } else if (level.getBlockEntity(pos) instanceof HavenWindowBlockEntity alone) {
            apply(level, window, alone.closed(), alone.start());
        }
    }

    /** Une vitre cassee : ce qui reste de sa fenetre -- un morceau, ou deux -- reprend ses bornes. */
    public static void left(Level level, BlockPos pos, Direction.Axis axis) {
        Set<BlockPos> done = new HashSet<>();
        for (Direction direction : HavenWindowBlock.sides(axis)) {
            BlockPos next = pos.relative(direction);
            if (done.contains(next) || !HavenWindowBlock.continues(level.getBlockState(next), axis)
                    || !(level.getBlockEntity(next) instanceof HavenWindowBlockEntity entity)) {
                continue;
            }
            Window window = group(level, next);
            done.addAll(window.panes());
            apply(level, window, entity.closed(), entity.start());
        }
    }
}
