package com.emerald.block;

import com.emerald.game.GameState;
import com.emerald.haven.HavenVote;
import com.emerald.menu.HavenVoteMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * La borne de vote du QG, pres du comptoir du Hip Hog : la « borne-ecran »
 * choisie par le joueur. Un socle, un pied aux caissons rouille marque de
 * l'embleme du bar, et une tete inclinee qui porte deux vitres lumineuses.
 *
 * DEUX CASES, COMME UNE PORTE : la borne mesure trente unites, donc deux
 * moities (HALF basse et haute), tournees ensemble (FACING, le cote des
 * vitres). Chacune a son modele (haven_vote_lower, haven_vote_upper) ; une
 * moitie sans l'autre disparait des qu'un voisin la previent.
 *
 * LE CLIC. Sur la vitre BLEUE : vote Monde ouvert. Sur la vitre ROUGE : vote
 * Defi. Ailleurs sur la borne : l'ecran de vote (HavenVoteMenu). Le point
 * touche que le client envoie est sur la FORME du bloc, pas sur la vitre
 * inclinee : on refait donc le rayon, de l'oeil du joueur a ce point, et on le
 * coupe par le plan des vitres (voir {@link #glassAt}). Le rayon, et non le
 * regard du serveur : la rotation du joueur arrive au serveur une tique apres
 * le clic, le point touche arrive avec lui.
 *
 * INCASSABLE, SANS BUTIN, NON POUSSABLE, SANS OBJET : c'est HavenVote qui pose
 * les deux moities quand la ville recoit les joueurs, et qui les retire au
 * depart.
 */
public class HavenVoteBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** La lumiere douce des vitres, portee par la moitie haute. */
    public static final int LIGHT = 10;

    // ---------------------------------------------------------------- geometrie
    // En unites de modele (seize par bloc), depuis le coin de la moitie BASSE,
    // borne tournee vers le sud : les nombres du modele haven_vote_upper, plus
    // seize en y. Les formes et le clic en sont tires : un seul jeu de nombres.

    /** L'inclinaison de la tete : l'angle du modele, autour de l'axe x. */
    private static final double TILT = Math.toRadians(-22.5);
    private static final double PIVOT_Y = 24.0;
    private static final double PIVOT_Z = 6.0;
    private static final double HEAD_X0 = 1.0;
    private static final double HEAD_X1 = 15.0;
    private static final double HEAD_Y0 = 18.0;
    private static final double HEAD_Y1 = 30.0;
    private static final double HEAD_Z0 = 2.0;
    /** La face avant des vitres : la tete s'arrete a 10, les vitres depassent d'un quart d'unite. */
    public static final double GLASS_Z = 10.25;
    public static final double GLASS_Y0 = 20.0;
    public static final double GLASS_Y1 = 28.0;
    /** La vitre bleue, a gauche quand on regarde la borne. */
    public static final double LIBRE_X0 = 2.5;
    public static final double LIBRE_X1 = 7.5;
    /** La vitre rouge, a droite. */
    public static final double DEFI_X0 = 8.5;
    public static final double DEFI_X1 = 13.5;
    /** Un clic sur le bord d'une vitre compte encore pour elle. */
    private static final double EDGE = 0.25;
    /** Jusqu'ou chercher la vitre derriere le point touche : les marches de la forme depassent devant elle. */
    private static final double BEYOND_HIT = 4.0;

    private static final VoxelShape[] LOWER_SHAPES = new VoxelShape[4];
    private static final VoxelShape[] UPPER_SHAPES = new VoxelShape[4];

    static {
        List<double[]> lower = List.of(
                new double[]{2, 0, 2, 14, 2, 14},           // socle
                new double[]{4, 2, 4, 12, 16, 12});         // pied, jusqu'en haut de la case
        List<double[]> upper = new ArrayList<>();
        upper.add(new double[]{4, 0, 4, 12, 2, 12});        // haut du pied
        upper.addAll(headSteps());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            LOWER_SHAPES[facing.get2DDataValue()] = shape(lower, facing);
            UPPER_SHAPES[facing.get2DDataValue()] = shape(upper, facing);
        }
    }

    public HavenVoteBlock(Properties properties) {
        // sans occlusion : la borne ne cache pas les faces voisines et laisse
        // passer la lumiere ; et seule la tete eclaire
        super(properties.noOcclusion()
                .lightLevel(state -> state.getValue(HALF) == DoubleBlockHalf.UPPER ? LIGHT : 0));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    // ---------------------------------------------------------------- clic

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            use(server, state, pos, hit);
        }
        return InteractionResult.CONSUME;
    }

    /** Un objet en main n'empeche pas de voter. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            use(server, state, pos, hit);
        }
        return ItemInteractionResult.CONSUME;
    }

    /** Une vitre vote ; le reste de la borne ouvre l'ecran. HavenVote.cast revalide tout. */
    private static void use(ServerPlayer player, BlockState state, BlockPos pos, BlockHitResult hit) {
        GameState.Mode glass = glassAt(state, pos, player.getEyePosition(), hit.getLocation());
        if (glass != null) {
            HavenVote.cast(player, glass);
        } else {
            HavenVoteMenu.open(player, base(state, pos));
        }
    }

    /**
     * La vitre sous un clic, ou null.
     *
     * On ramene l'oeil et le point touche dans le repere du modele (borne vers
     * le sud, tete redressee), puis on coupe le rayon par le plan des vitres.
     * Il faut venir de DEVANT (un clic de dos, a travers la tete, ne vote pas),
     * et couper le plan au plus a quelques unites derriere le point touche.
     *
     * @param state  une des deux moities
     * @param pos    la case de cette moitie
     * @param eye    l'oeil du joueur, dans le monde
     * @param target le point touche, dans le monde
     * @return LIBRE pour la vitre bleue, DEFI pour la rouge, null ailleurs
     */
    @Nullable
    public static GameState.Mode glassAt(BlockState state, BlockPos pos, Vec3 eye, Vec3 target) {
        if (!(state.getBlock() instanceof HavenVoteBlock)) {
            return null;
        }
        Direction facing = state.getValue(FACING);
        BlockPos base = base(state, pos);
        Vec3 from = tilt(toModel(facing, base, eye), -TILT);
        Vec3 to = tilt(toModel(facing, base, target), -TILT);
        Vec3 ray = to.subtract(from);
        double length = ray.length();
        if (length < 1.0e-6 || ray.z >= 0.0 || from.z <= GLASS_Z) {
            return null;
        }
        double t = (GLASS_Z - from.z) / ray.z;
        if (t < 0.0 || t > 1.0 + BEYOND_HIT / length) {
            return null;
        }
        double x = from.x + ray.x * t;
        double y = from.y + ray.y * t;
        if (y < GLASS_Y0 - EDGE || y > GLASS_Y1 + EDGE) {
            return null;
        }
        if (x >= LIBRE_X0 - EDGE && x <= LIBRE_X1 + EDGE) {
            return GameState.Mode.LIBRE;
        }
        if (x >= DEFI_X0 - EDGE && x <= DEFI_X1 + EDGE) {
            return GameState.Mode.DEFI;
        }
        return null;
    }

    /** La case de la moitie basse. */
    public static BlockPos base(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /**
     * Un point de la face des vitres, en unites de modele (borne vers le sud),
     * tete inclinee : (x, y) sur la vitre non inclinee, outward devant elle.
     */
    public static Vec3 glassPoint(double x, double y, double outward) {
        return tilt(new Vec3(x, y, GLASS_Z + outward), TILT);
    }

    /** Un point du monde dans le repere du modele (unites de modele, borne vers le sud). */
    public static Vec3 toModel(Direction facing, BlockPos base, Vec3 world) {
        double x = (world.x - base.getX()) * 16.0;
        double y = (world.y - base.getY()) * 16.0;
        double z = (world.z - base.getZ()) * 16.0;
        return switch (facing) {
            case NORTH -> new Vec3(16.0 - x, y, 16.0 - z);
            case WEST -> new Vec3(z, y, 16.0 - x);
            case EAST -> new Vec3(16.0 - z, y, x);
            default -> new Vec3(x, y, z);
        };
    }

    /**
     * Un point du modele dans le monde. Les rotations sont celles du blockstate
     * (y = 90 pour l'ouest, 180 le nord, 270 l'est), qui tournent le modele de
     * -y degres autour de l'axe vertical (BlockModelRotation.rotateYXZ).
     */
    public static Vec3 toWorld(Direction facing, BlockPos base, Vec3 model) {
        double x;
        double z;
        switch (facing) {
            case NORTH -> {
                x = 16.0 - model.x;
                z = 16.0 - model.z;
            }
            case WEST -> {
                x = 16.0 - model.z;
                z = model.x;
            }
            case EAST -> {
                x = model.z;
                z = 16.0 - model.x;
            }
            default -> {
                x = model.x;
                z = model.z;
            }
        }
        return new Vec3(base.getX() + x / 16.0, base.getY() + model.y / 16.0, base.getZ() + z / 16.0);
    }

    /** La rotation de la tete autour de l'axe x, pivot du modele (celle de FaceBakery). */
    private static Vec3 tilt(Vec3 p, double angle) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double y = p.y - PIVOT_Y;
        double z = p.z - PIVOT_Z;
        return new Vec3(p.x, PIVOT_Y + y * c - z * s, PIVOT_Z + y * s + z * c);
    }

    // ---------------------------------------------------------------- formes

    /**
     * La tete inclinee, en marches d'une unite de haut.
     *
     * Sa coupe (z, y) est un rectangle tourne, vitres comprises. Pour chaque
     * tranche d'une unite, on prend l'etendue du polygone dans la tranche
     * (sommets dedans, croisements des aretes avec ses deux bords), arrondie au
     * demi-pixel vers l'exterieur. La selection et la collision collent a la
     * pente, au lieu d'une boite qui engloberait le vide devant le haut de
     * l'ecran et derriere le bas de la tete.
     *
     * @return des boites {x0, y0, z0, x1, y1, z1} en unites de la moitie haute
     */
    static List<double[]> headSteps() {
        Vec3[] corners = {
                tilt(new Vec3(0, HEAD_Y0, HEAD_Z0), TILT), tilt(new Vec3(0, HEAD_Y0, GLASS_Z), TILT),
                tilt(new Vec3(0, HEAD_Y1, GLASS_Z), TILT), tilt(new Vec3(0, HEAD_Y1, HEAD_Z0), TILT)};
        List<double[]> steps = new ArrayList<>();
        for (int band = 16; band < 32; band++) {
            double lo = band;
            double hi = band + 1;
            double zMin = Double.MAX_VALUE;
            double zMax = -Double.MAX_VALUE;
            double yMin = Double.MAX_VALUE;
            double yMax = -Double.MAX_VALUE;
            for (int i = 0; i < corners.length; i++) {
                Vec3 a = corners[i];
                Vec3 b = corners[(i + 1) % corners.length];
                if (a.y >= lo && a.y <= hi) {
                    zMin = Math.min(zMin, a.z);
                    zMax = Math.max(zMax, a.z);
                    yMin = Math.min(yMin, a.y);
                    yMax = Math.max(yMax, a.y);
                }
                for (double h : new double[]{lo, hi}) {
                    if ((a.y - h) * (b.y - h) < 0.0) {
                        double z = a.z + (h - a.y) / (b.y - a.y) * (b.z - a.z);
                        zMin = Math.min(zMin, z);
                        zMax = Math.max(zMax, z);
                        yMin = Math.min(yMin, h);
                        yMax = Math.max(yMax, h);
                    }
                }
            }
            if (zMin <= zMax) {
                steps.add(new double[]{
                        HEAD_X0, clamp(Math.floor(yMin * 2.0) / 2.0 - 16.0), clamp(Math.floor(zMin * 2.0) / 2.0),
                        HEAD_X1, clamp(Math.ceil(yMax * 2.0) / 2.0 - 16.0), clamp(Math.ceil(zMax * 2.0) / 2.0)});
            }
        }
        return steps;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(16.0, v));
    }

    /** Des boites du modele (vers le sud), tournees comme le blockstate tourne le modele. */
    private static VoxelShape shape(List<double[]> boxes, Direction facing) {
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0];
            double z0 = b[2];
            double x1 = b[3];
            double z1 = b[5];
            double[] r = switch (facing) {
                case NORTH -> new double[]{16 - x1, 16 - z1, 16 - x0, 16 - z0};
                case WEST -> new double[]{16 - z1, x0, 16 - z0, x1};
                case EAST -> new double[]{z0, 16 - x1, z1, 16 - x0};
                default -> new double[]{x0, z0, x1, z1};
            };
            out = Shapes.or(out, Block.box(r[0], b[1], r[1], r[2], b[4], r[3]));
        }
        return out.optimize();
    }

    /** Selection et collision : la meme forme, en marches. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int index = state.getValue(FACING).get2DDataValue();
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES[index] : UPPER_SHAPES[index];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---------------------------------------------------------------- deux moities

    /** Les deux cases de la borne sont libres (air, remplacable, ou deja elle). */
    public static boolean fits(BlockGetter level, BlockPos base) {
        return free(level.getBlockState(base)) && free(level.getBlockState(base.above()));
    }

    private static boolean free(BlockState state) {
        return state.isAir() || state.getBlock() instanceof HavenVoteBlock || state.canBeReplaced();
    }

    /**
     * Pose a la main (aucun objet ne le permet aujourd'hui) : refusee si la case
     * du dessus est prise, comme une porte ; les vitres regardent le poseur.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * Une moitie dont l'autre a disparu part avec elle (DoorBlock.updateShape).
     * HavenVote pose et retire sans prevenir les voisins : ceci ne joue que si
     * quelque chose d'autre touche a la borne (une commande, un creatif).
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction other = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == other) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half && neighbor.getValue(FACING) == state.getValue(FACING)
                    ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
