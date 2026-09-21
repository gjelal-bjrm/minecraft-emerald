package com.emerald.block;

import com.emerald.block.entity.HavenGunRackBlockEntity;
import com.emerald.haven.journey.HavenRack;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le ratelier d'armes du QG de Haven (cahier §81) : le Morph Gun y est pose, et un clic
 * donne le Scatter Gun au joueur qui revient de son premier Defi (HavenRack.take).
 *
 * TIRE DES TEXTURES DE JAK 3, comme la borne et le bouton (tools/haven_rack_textures.py) :
 * un socle au panneau d'acier du ratelier du Hip Hog (gun-gunrack-01), un pied de metal
 * rouge (hip-tredmetal04), un berceau a encoches (gun-gunrack-02) et deux montants.
 * CHOISI PAR LE JOUEUR SUR PHOTOS (21 sept.) parmi trois -- mural, sur pied, vitrine :
 * « sur pied », qui se voit de tous les cotes. L'arme elle-meme est dessinee par
 * HavenGunRackRenderer. Incassable, sans butin, sans objet : HavenRack le pose et le
 * retire comme la borne.
 */
public class HavenGunRackBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** Socle, pied, berceau : la forme, dessinee face au nord (l'avant en z = 0). */
    private static final VoxelShape NORTH = Shapes.or(
            Block.box(3, 0, 3, 13, 1, 13),
            Block.box(7, 1, 7, 9, 11, 9),
            Block.box(2, 11, 6, 14, 14, 10)).optimize();

    public HavenGunRackBlock(Properties properties) {
        super(properties.noOcclusion().lightLevel(state -> 3));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return rotate(NORTH, state.getValue(FACING));
    }

    /** Tourne une forme dessinee face au nord vers l'orientation donnee. */
    private static VoxelShape rotate(VoxelShape north, Direction facing) {
        if (facing == Direction.NORTH) {
            return north;
        }
        VoxelShape[] out = {Shapes.empty()};
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            VoxelShape box = switch (facing) {
                case SOUTH -> Shapes.box(1 - x1, y0, 1 - z1, 1 - x0, y1, 1 - z0);
                case EAST -> Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1);
                case WEST -> Shapes.box(z0, y0, 1 - x1, z1, y1, 1 - x0);
                default -> Shapes.box(x0, y0, z0, x1, y1, z1);
            };
            out[0] = Shapes.or(out[0], box);
        });
        return out[0].optimize();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer server) {
            HavenRack.take(server, pos);
        }
        return InteractionResult.CONSUME;
    }

    /** Un objet en main n'empeche pas de prendre l'arme. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HavenGunRackBlockEntity(pos, state);
    }
}
