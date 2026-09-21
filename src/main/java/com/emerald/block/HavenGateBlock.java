package com.emerald.block;

import com.emerald.block.entity.HavenGateBlockEntity;
import com.emerald.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * La porte precurseur de la victoire (parcours de Haven, lot 2, cahier §81) : elle s'ouvre
 * la ou le boss du Defi est tombe, et ramene chacun a son appartement (HavenReturn).
 *
 * TOUT EST DESSINE PAR HavenGateRenderer, aux textures de Jak 3 -- le warp gate
 * precurseur (tools/haven_porte_textures.py) : le bloc lui-meme n'a pas de modele, pas
 * de collision (on la traverse), une lueur, et une petite forme au sol pour le viseur.
 * TROIS MODELES (STYLE), montres au joueur sur photos le 21 sept. : l'ANNEAU (une porte
 * ronde debout, un voile bleu dedans) -- CHOISI pour la victoire, agrandi de 20 % --,
 * le PORTAIL (le plateau du warp gate et sa colonne de lumiere) et l'ARCHE (deux
 * piliers, un linteau a glyphes, un voile). Les deux autres sont GARDES a la demande
 * du joueur, pour se deplacer dans la ville plus tard : l'arche d'un bout a l'autre de
 * la ville (avec un delai contre les abus), le portail pour monter aux tours (cahier §81.2).
 * Incassable, sans butin, sans objet ; posee et retiree par HavenReturn -- et si un
 * redemarrage l'oublie, elle s'efface d'elle-meme (HavenGateBlockEntity).
 */
public class HavenGateBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);

    /** Les propositions de la porte. */
    public enum Style implements StringRepresentable {
        ANNEAU, PORTAIL, ARCHE;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** Pour le viseur seulement : une dalle au sol. */
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 2, 16);

    public HavenGateBlock(Properties properties) {
        super(properties.noOcclusion().noCollission().lightLevel(state -> 12));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(STYLE, Style.ANNEAU));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STYLE);
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
        return new HavenGateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.HAVEN_GATE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> HavenGateBlockEntity.serverTick(lvl, pos, (HavenGateBlockEntity) be);
    }
}
