package com.emerald.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * LA PORTE DOREE de l'Heure Doree : on y entre, on ressort au village.
 *
 * Le meme corps que la Brume etoilee -- traversable, lumineuse, incassable --
 * mais elle appelle {@link com.emerald.weather.GoldenGate}, qui envoie a
 * l'atelier et ramene, au lieu du voyage a travers la roche.
 */
public class GoldenGateBlock extends Block {

    public GoldenGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
            com.emerald.weather.GoldenGate.touch(server, player, pos);
        }
    }
}
