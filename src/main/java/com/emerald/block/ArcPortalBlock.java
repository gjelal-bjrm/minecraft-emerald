package com.emerald.block;

import com.emerald.block.entity.ArcPortalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Locale;

/**
 * LES PORTAILS D'ARCENCIUM (22 sept., cahier §84) : la Porte doree de l'Heure Doree, les
 * Brumes etoilees et la Brume de rappel de l'Aurore, redessinees.
 *
 * « Je n'aime pas du tout les portails. Ils sont moches, mal faits et pas pratiques a
 * prendre [...] parfois, j'ai besoin de les contourner et je ne sais pas quelle partie je
 * dois contourner. » C'etaient six cubes translucides en croix, dont chaque bras
 * teleportait. Ce sont desormais des portes DESSINEES (client/ArcPortalRenderer), au metal
 * noir de l'Arcencium parcouru de fissures d'arc-en-ciel, avec un voile a la couleur de
 * leur role : on voit ou elles sont, et ou l'on passe.
 *
 * TROIS FORMES (STYLE) montrees au joueur : l'ANNEAU debout, l'ARCHE a deux piliers, le
 * PLATEAU au sol et sa colonne -- il a choisi L'ARCHE pour tous (photos du 22 sept. ; les
 * deux autres restent pour la vitrine). CINQ TEINTES (TEINTE) : DOREE (l'Heure Doree),
 * ETOILEE (les brumes qui vont par paires), AUBE (le rappel vers la surface), et, au depart
 * du QG de Haven, DEFI (rouge) ou LIBRE (bleu), les couleurs des vitres de la borne.
 *
 * Le bloc n'a pas de modele (tout est dessine), pas de collision (on traverse l'anneau et
 * l'arche ; sur le plateau, une dalle), une lueur, et une forme au sol pour le viseur.
 */
public class ArcPortalBlock extends Block implements EntityBlock {

    /** L'arche : demi-largeur du passage, naissance de la voute, piliers (ArcPortalRenderer, ArcPortals). */
    public static final float ARCH_HALF = 0.92F;
    public static final float ARCH_SPRING = 2.2F;
    public static final float ARCH_PILLAR = 0.36F;

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Style> STYLE = EnumProperty.create("style", Style.class);
    public static final EnumProperty<Tint> TEINTE = EnumProperty.create("teinte", Tint.class);

    public enum Style implements StringRepresentable {
        ANNEAU, ARCHE, PLATEAU;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Tint implements StringRepresentable {
        DOREE, ETOILEE, AUBE, DEFI, LIBRE;

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** Pour le viseur : une dalle au sol. */
    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 2, 16);
    /** Le plateau, ou l'on se tient : a la hauteur du dessin (0,22 bloc). */
    private static final VoxelShape PAD = Block.box(0, 0, 0, 16, 3.5, 16);

    public ArcPortalBlock(Properties properties) {
        super(properties.noOcclusion().lightLevel(state -> 13));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(STYLE, Style.ANNEAU)
                .setValue(TEINTE, Tint.DOREE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STYLE, TEINTE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FLOOR;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(STYLE) == Style.PLATEAU ? PAD : Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcPortalBlockEntity(pos, state);
    }

    @javax.annotation.Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || type != com.emerald.block.entity.ModBlockEntities.ARC_PORTAL.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ArcPortalBlockEntity.serverTick(lvl, pos, (ArcPortalBlockEntity) be);
    }
}
