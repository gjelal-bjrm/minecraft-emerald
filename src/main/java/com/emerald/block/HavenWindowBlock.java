package com.emerald.block;

import com.emerald.block.entity.HavenWindowBlockEntity;
import com.emerald.haven.door.HavenWindows;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * UNE VITRE DE JAK 3 (cahier §101), demandee par le joueur : « style futuriste comme les portes,
 * bordure tres legere, vitre au centre ; elles se relient entre elles comme les vitres ; clic
 * droit, la bordure se referme sur la vitre en ellipse -- un iris qui se rejoint au centre --, re-clic,
 * elle se rouvre. Masque ou montre l'exterieur. » Et, a son choix, UN IRIS PAR FENETRE : un cadre fin
 * autour de toute la fenetre reliee, une seule ellipse qui se resserre en son centre, et un clic sur
 * n'importe quelle vitre ferme ou rouvre toute la fenetre.
 *
 *  - Une vitre fine, tournee selon l'axe du mur (AXIS : elle s'etend le long de x ou de z). Elle se
 *    relie a ses voisines du meme axe, a gauche, a droite, en haut, en bas : ensemble elles font une
 *    fenetre, et le cadre -- le metal clair des portes -- n'en borde que le pourtour (UP, DOWN,
 *    LEFT, RIGHT : relie de ce cote, donc sans cadre).
 *  - L'iris, l'acier sombre du sas du port, est dessine par l'entite de chaque vitre
 *    (HavenWindowRenderer), devant le verre, des deux faces, en retrait dans l'epaisseur du cadre.
 *  - Fermee, la fenetre masque l'exterieur ; la lumiere, elle, passe toujours : la premiere version
 *    l'arretait, et la vitre fermee, noire pour elle-meme, s'eteignait d'un coup a la fin de l'iris
 *    (photo) -- le joueur avait demande de masquer l'exterieur, pas de faire le noir.
 */
public class HavenWindowBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    /** La vitre et son cadre, trois pixels d'epaisseur au milieu du bloc. */
    private static final VoxelShape SHAPE_X = Block.box(0.0, 0.0, 6.5, 16.0, 16.0, 9.5);
    private static final VoxelShape SHAPE_Z = Block.box(6.5, 0.0, 0.0, 9.5, 16.0, 16.0);

    public HavenWindowBlock(Properties properties) {
        super(properties.noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(UP, false).setValue(DOWN, false).setValue(LEFT, false).setValue(RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, UP, DOWN, LEFT, RIGHT);
    }

    /** Le cote « gauche » d'une vitre : vers -x pour une vitre en X, vers -z pour une vitre en Z. */
    public static Direction left(Direction.Axis axis) {
        return axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
    }

    /** Les quatre voisines possibles d'une vitre dans son plan. */
    public static List<Direction> sides(Direction.Axis axis) {
        Direction left = left(axis);
        return List.of(left, left.getOpposite(), Direction.UP, Direction.DOWN);
    }

    /** Cet etat de voisine prolonge-t-il une vitre de cet axe ? */
    public static boolean continues(BlockState neighbor, Direction.Axis axis) {
        return neighbor.getBlock() instanceof HavenWindowBlock && neighbor.getValue(AXIS) == axis;
    }

    @Nullable
    private static BooleanProperty side(Direction.Axis axis, Direction direction) {
        if (direction == Direction.UP) {
            return UP;
        }
        if (direction == Direction.DOWN) {
            return DOWN;
        }
        Direction left = left(axis);
        return direction == left ? LEFT : direction == left.getOpposite() ? RIGHT : null;
    }

    /** L'etat relie a ses voisines, tel qu'il doit etre a cette place. */
    public static BlockState connected(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        for (Direction direction : sides(axis)) {
            state = state.setValue(side(axis, direction), continues(level.getBlockState(pos.relative(direction)), axis));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // face au joueur : regardant au nord ou au sud, la vitre s'etend le long de x
        Direction.Axis axis = context.getHorizontalDirection().getAxis() == Direction.Axis.Z
                ? Direction.Axis.X : Direction.Axis.Z;
        return connected(context.getLevel(), context.getClickedPos(), this.defaultBlockState().setValue(AXIS, axis));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        Direction.Axis axis = state.getValue(AXIS);
        BooleanProperty side = side(axis, direction);
        return side == null ? state : state.setValue(side, continues(neighbor, axis));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? SHAPE_X : SHAPE_Z;
    }

    /** Entre deux vitres reliees, ni tranche de verre ni bout de cadre : la fenetre est d'un seul tenant. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return (continues(adjacent, state.getValue(AXIS)) && side(state.getValue(AXIS), direction) != null)
                || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HavenWindowBlockEntity(pos, state);
    }

    /** Un clic, et toute la fenetre se ferme ou se rouvre. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide) {
            HavenWindows.toggle(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Une vitre en main pose une vitre contre celle qu'on vise : on agrandit la fenetre, on ne la ferme pas. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof HavenWindowBlock) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!level.isClientSide && !oldState.is(this)) {
            HavenWindows.joined(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        boolean gone = !newState.is(this);
        super.onRemove(state, level, pos, newState, moved);
        if (!level.isClientSide && gone) {
            HavenWindows.left(level, pos, state.getValue(AXIS));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.emeraldweapons.haven_window.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.emeraldweapons.haven_window.tooltip2").withStyle(ChatFormatting.GRAY));
    }
}
