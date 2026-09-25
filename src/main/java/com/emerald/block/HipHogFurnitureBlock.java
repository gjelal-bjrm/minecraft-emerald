package com.emerald.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Un meuble du bar du Hip Hog (cahier §103) : tabouret, tabouret haut, table haute, lampe suspendue. Leur
 * forme est celle de leur modele (tools/jak_bar.py, MODEL_ELEMENTS) : on passe entre les pieds.
 */
public class HipHogFurnitureBlock extends Block {

    public enum Kind {
        STOOL(Shapes.or(box(7, 0, 7, 9, 10, 9), box(4, 10, 4, 12, 12, 12))),
        HIGH_STOOL(Shapes.or(box(7, 0, 7, 9, 13, 9), box(4, 13, 4, 12, 15, 12))),
        // le plateau prend toute la case : deux tables voisines font une seule table
        HIGH_TABLE(Shapes.or(box(7, 0, 7, 9, 14, 9), box(0, 14, 0, 16, 16, 16))),
        HANGING_LAMP(Shapes.or(box(7, 12, 7, 9, 16, 9), box(4, 3, 4, 12, 12, 12)));

        private final VoxelShape shape;

        Kind(VoxelShape shape) {
            this.shape = shape;
        }
    }

    private final Kind kind;

    public HipHogFurnitureBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.kind.shape;
    }
}
