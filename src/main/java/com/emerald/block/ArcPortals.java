package com.emerald.block;

import com.emerald.block.entity.ArcPortalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Les arches d'Arcencium posees par le jeu (cahier §84) : la Porte doree de l'Heure Doree,
 * les brumes de l'Aurore, le depart du QG de Haven. Le joueur a choisi L'ARCHE pour tous
 * (photos du 22 sept.) : deux piliers et une voute, un passage de deux blocs.
 *
 * SEUL LE VOILE TELEPORTE. Les six cubes d'avant teleportaient par n'importe lequel de
 * leurs bras : « parfois, j'ai besoin de les contourner et je ne sais pas quelle partie je
 * dois contourner ». Ici on passe a cote, derriere, contre un pilier sans rien declencher :
 * il faut avoir les pieds DANS le passage, entre les piliers et a moins de
 * {@value #VEIL_DEPTH} bloc de son plan ({@link #inVeil}).
 *
 * UNE ARCHE A SA PLACE : un passage de trois blocs de large sur quatre de haut, un sol sous
 * ses trois blocs, et de quoi se tenir devant et derriere ({@link #fits}). La sortie se fait
 * devant l'arche d'arrivee, du cote ou elle regarde, le dos tourne a elle ({@link #exit}).
 */
public final class ArcPortals {

    /** La demi-largeur du voile qui teleporte : un peu moins que le passage, les piliers ne comptent pas. */
    public static final double VEIL_HALF = ArcPortalBlock.ARCH_HALF - 0.12;
    /** A quelle distance du plan de l'arche les pieds comptent comme dedans. */
    public static final double VEIL_DEPTH = 0.4;
    /** La sortie : a tant de blocs devant l'arche. */
    public static final double EXIT_AHEAD = 1.7;

    /** Une place pour une arche : son bloc, et le cote vers lequel elle regarde. */
    public record Placement(BlockPos anchor, Direction facing) {
    }

    private ArcPortals() {
    }

    /** Pose une arche du jeu : temporaire (elle s'efface si plus personne ne la connait). */
    public static void place(ServerLevel level, BlockPos anchor, Direction facing, ArcPortalBlock.Tint tint) {
        Direction side = facing.getClockWise();
        // les herbes et fleurs du passage s'en vont : sinon elles traversent les piliers
        for (int l = -1; l <= 1; l++) {
            for (int y = 0; y <= 3; y++) {
                BlockPos pos = anchor.relative(side, l).above(y);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(anchor, ModBlocks.ARC_PORTAL.get().defaultBlockState()
                .setValue(ArcPortalBlock.STYLE, ArcPortalBlock.Style.ARCHE)
                .setValue(ArcPortalBlock.TEINTE, tint)
                .setValue(ArcPortalBlock.FACING, facing), 3);
        if (level.getBlockEntity(anchor) instanceof ArcPortalBlockEntity portal) {
            portal.setTemporary(true);
        }
        level.playSound(null, anchor, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.8F, 1.4F);
    }

    /** Retire une arche (s'il y en a bien une), dans un nuage de prisme. */
    public static void remove(ServerLevel level, BlockPos anchor) {
        if (level.getBlockState(anchor).is(ModBlocks.ARC_PORTAL.get())) {
            level.setBlock(anchor, Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                    anchor.getX() + 0.5, anchor.getY() + 1.4, anchor.getZ() + 0.5, 16, 0.9, 1.2, 0.2, 0.0);
        }
    }

    // ================================================================ la place

    /** L'arche tient-elle ici, tournee vers « facing » ? Passage libre, sol dessous, de quoi passer des deux cotes. */
    public static boolean fits(ServerLevel level, BlockPos anchor, Direction facing) {
        return window(level, anchor, facing) && walkway(level, anchor, facing) && walkway(level, anchor, facing.getOpposite());
    }

    /** Comme fits, mais il suffit de pouvoir y entrer par devant (la brume de rappel, taillee dans la roche). */
    public static boolean fitsFront(ServerLevel level, BlockPos anchor, Direction facing) {
        return window(level, anchor, facing) && walkway(level, anchor, facing);
    }

    private static boolean window(ServerLevel level, BlockPos anchor, Direction facing) {
        if (!level.isLoaded(anchor)) {
            return false;
        }
        Direction side = facing.getClockWise();
        for (int l = -1; l <= 1; l++) {
            BlockPos base = anchor.relative(side, l);
            if (!solid(level, base.below())) {
                return false;
            }
            for (int y = 0; y <= 3; y++) {
                if (!clear(level, base.above(y))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** De quoi se tenir d'un cote de l'arche : deux blocs libres sur un sol. */
    private static boolean walkway(ServerLevel level, BlockPos anchor, Direction side) {
        BlockPos step = anchor.relative(side);
        return clear(level, step) && clear(level, step.above()) && solid(level, step.below());
    }

    static boolean clear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return (state.isAir() || state.canBeReplaced()) && state.getFluidState().isEmpty()
                && !state.is(ModBlocks.ARC_PORTAL.get());
    }

    static boolean solid(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    /**
     * Une place a une distance de « min » a « max » de « near », a sa hauteur a quelques blocs
     * pres (« up » plus haut, « down » plus bas) ; l'arche regarde « lookAt » si l'on peut.
     */
    @Nullable
    public static Placement find(ServerLevel level, BlockPos near, double min, double max, int up, int down,
                                 int tries, @Nullable BlockPos lookAt, RandomSource random) {
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * (max - min);
            int x = (int) Math.round(near.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(near.getZ() + Math.sin(angle) * distance);
            Placement found = at(level, new BlockPos(x, near.getY(), z), up, down, lookAt);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Une place dans la colonne de « around », en cherchant vers le haut puis vers le bas. */
    @Nullable
    public static Placement at(ServerLevel level, BlockPos around, int up, int down, @Nullable BlockPos lookAt) {
        if (!level.isLoaded(around)) {
            return null;
        }
        for (int dy = 0; dy <= Math.max(up, down); dy++) {
            for (int sign : dy == 0 ? new int[]{1} : new int[]{1, -1}) {
                if (sign > 0 && dy > up || sign < 0 && dy > down) {
                    continue;
                }
                BlockPos pos = around.above(dy * sign);
                for (Direction facing : facings(pos, lookAt)) {
                    if (fits(level, pos, facing)) {
                        return new Placement(pos, facing);
                    }
                }
            }
        }
        return null;
    }

    /** Autour d'un point, du plus pres au plus loin, a sa hauteur a « vertical » blocs pres. */
    @Nullable
    public static Placement nearest(ServerLevel level, BlockPos around, int radius, int vertical,
                                    @Nullable BlockPos lookAt) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    Placement found = at(level, around.offset(dx, 0, dz), vertical, vertical, lookAt);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    /** Les quatre orientations, celle qui regarde « lookAt » d'abord. */
    public static List<Direction> facings(BlockPos from, @Nullable BlockPos lookAt) {
        List<Direction> out = new ArrayList<>(4);
        if (lookAt != null && !lookAt.equals(from)) {
            Direction toward = Direction.getNearest(lookAt.getX() - from.getX(), 0, lookAt.getZ() - from.getZ());
            if (toward.getAxis().isHorizontal()) {
                out.add(toward);
                out.add(toward.getOpposite());
            }
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!out.contains(d)) {
                out.add(d);
            }
        }
        return out;
    }

    // ================================================================ le passage

    /** Les pieds de cette entite sont-ils dans le voile de l'arche ? */
    public static boolean inVeil(Entity entity, BlockPos anchor, Direction facing) {
        double dx = entity.getX() - (anchor.getX() + 0.5);
        double dz = entity.getZ() - (anchor.getZ() + 0.5);
        double dy = entity.getY() - anchor.getY();
        double depth = dx * facing.getStepX() + dz * facing.getStepZ();
        Direction side = facing.getClockWise();
        double lateral = dx * side.getStepX() + dz * side.getStepZ();
        if (Math.abs(depth) > VEIL_DEPTH || Math.abs(lateral) > VEIL_HALF || dy < -0.5) {
            return false;
        }
        double top = ArcPortalBlock.ARCH_SPRING
                + Math.sqrt(Math.max(0.0, VEIL_HALF * VEIL_HALF - lateral * lateral));
        return dy <= top - 1.0;
    }

    /** Ou l'on ressort d'une arche : devant elle, au sol. */
    public static Vec3 exit(BlockPos anchor, Direction facing) {
        return Vec3.atBottomCenterOf(anchor).add(facing.getStepX() * EXIT_AHEAD, 0.0, facing.getStepZ() * EXIT_AHEAD);
    }

    /** Le regard a la sortie : droit devant, le dos a l'arche. */
    public static float exitYaw(Direction facing) {
        return facing.toYRot();
    }
}
