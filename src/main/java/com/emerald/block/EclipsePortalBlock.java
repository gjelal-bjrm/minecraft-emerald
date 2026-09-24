package com.emerald.block;

import com.emerald.block.entity.EclipsePortalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * UN PORTAIL DE L'ECLIPSE (24 sept. 2026, cahier §88).
 *
 * « Je veux vraiment que ce soit des portails d'horreur », et rien de vivant. Un cadre de
 * pierre noire fendue, tenu par des chaines, autour d'un vide ou tourne une fumee
 * cramoisie (client/EclipsePortalRenderer). Les horreurs de l'Eclipse en sortent ; on
 * ne peut pas le traverser (weather/Eclipse repousse et blesse qui s'y frotte).
 *
 * Le bloc n'est qu'une ancre : il ne se dessine pas lui-meme, il n'arrete rien, et il
 * s'efface des que l'Eclipse ne le reconnait plus (EclipsePortalBlockEntity).
 * Reperes du dessin : l'origine au milieu du bloc, au sol ; +z regarde FACING.
 */
public class EclipsePortalBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** La demi-largeur du vide, en blocs ; la hauteur du pied des montants, et de la pointe. */
    public static final float VOID_HALF = 1.15F;
    public static final float VOID_SPRING = 2.9F;
    public static final float VOID_TOP = 4.5F;

    /** Pour le viseur : une dalle au sol. */
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 2, 16);

    public EclipsePortalBlock(Properties properties) {
        super(properties.noOcclusion().lightLevel(state -> 4));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FLOOR;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EclipsePortalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide || type != com.emerald.block.entity.ModBlockEntities.ECLIPSE_PORTAL.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> EclipsePortalBlockEntity.serverTick(lvl, pos, (EclipsePortalBlockEntity) be);
    }
}
