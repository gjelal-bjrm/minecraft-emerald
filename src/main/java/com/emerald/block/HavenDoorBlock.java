package com.emerald.block;

import com.emerald.block.entity.HavenDoorBlockEntity;
import com.emerald.block.entity.ModBlockEntities;
import com.emerald.haven.door.HavenDoorKind;
import com.emerald.haven.door.HavenDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Une cellule d'une porte de Jak 3 (cahier §95) : la porte du Hip Hog, la petite, le sas du port.
 *
 * Une porte occupe toute son ouverture, une cellule par bloc : FERMEE, chaque cellule est un
 * bloc plein qui barre le passage ; OUVERTE, elle n'a plus de collision. Rien ne se dessine en
 * bloc : la cellule-CONTROLEUR porte l'entite qui dessine le modele de Jak 3 et ses battants
 * qui coulissent (HavenDoorRenderer), qui guette les joueurs et qui ouvre et referme la porte
 * (HavenDoorBlockEntity). Casser une cellule casse la porte entiere.
 */
public class HavenDoorBlock extends Block implements EntityBlock {

    public static final EnumProperty<HavenDoorKind> KIND = EnumProperty.create("kind", HavenDoorKind.class);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty CONTROLLER = BooleanProperty.create("controller");

    public HavenDoorBlock(Properties properties) {
        super(properties.noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(KIND, HavenDoorKind.HIP)
                .setValue(OPEN, false)
                .setValue(CONTROLLER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, OPEN, CONTROLLER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
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
        return state.getValue(CONTROLLER) ? new HavenDoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(CONTROLLER) || type != ModBlockEntities.HAVEN_DOOR.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> HavenDoorBlockEntity.serverTick(lvl, pos, st, (HavenDoorBlockEntity) be);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        // une cellule qui s'en va (et non une cellule qui s'ouvre) emporte toute la porte
        if (!newState.is(this) && !level.isClientSide) {
            HavenDoors.breakAround(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
