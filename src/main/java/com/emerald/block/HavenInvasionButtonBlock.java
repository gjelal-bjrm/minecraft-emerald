package com.emerald.block;

import com.emerald.haven.invasion.HavenInvasion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le bouton d'alerte du QG, sur le bout du comptoir du Hip Hog.
 *
 * TIRE DES TEXTURES DU HIP HOG, comme la borne : un socle de caissons rouille
 * marque de l'embleme du bar (FACING, le cote de l'embleme), une plaque de metal,
 * un collier dore (hip-tgoldring01) et un voyant bombe qui luit : ROUGE pendant
 * l'invasion, BLEU quand la ville est paisible (PEACEFUL). Douze unites de haut.
 *
 * LE CLIC de n'importe quel joueur bascule toute la ville (HavenInvasion.pressButton,
 * qui revalide tout). Incassable, sans butin, non poussable, sans objet :
 * HavenInvasionButton le pose et le retire comme la borne.
 */
public class HavenInvasionButtonBlock extends Block {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty PEACEFUL = BooleanProperty.create("peaceful");

    /** La lueur du voyant. */
    public static final int LIGHT = 6;

    /** Socle, plaque, collier, voyant : le modele, symetrique autour de l'axe vertical. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 8, 13),
            Block.box(2, 8, 2, 14, 9, 14),
            Block.box(5, 9, 5, 11, 10, 11),
            Block.box(6, 10, 6, 10, 12, 10)).optimize();

    public HavenInvasionButtonBlock(Properties properties) {
        super(properties.noOcclusion().lightLevel(state -> LIGHT));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(PEACEFUL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PEACEFUL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            HavenInvasion.pressButton(server, pos);
        }
        return InteractionResult.CONSUME;
    }

    /** Un objet en main -- l'arme comprise -- n'empeche pas d'appuyer. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            HavenInvasion.pressButton(server, pos);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
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
