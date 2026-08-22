package com.emerald.block;

import com.emerald.particles.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tronc de Prisme : veines figees qui vibrent (texture animee) et, la nuit,
 * une etincelle coloree s'echappe parfois d'une face exposee.
 */
public class PrismLogBlock extends RotatedPillarBlock {
    public PrismLogBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.isNight() || random.nextInt(14) != 0) return;
        Direction dir = Direction.getRandom(random);
        BlockPos adj = pos.relative(dir);
        if (!level.getBlockState(adj).isAir()) return;
        double x = pos.getX() + 0.5 + dir.getStepX() * 0.55 + (dir.getStepX() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double y = pos.getY() + 0.5 + dir.getStepY() * 0.55 + (dir.getStepY() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        double z = pos.getZ() + 0.5 + dir.getStepZ() * 0.55 + (dir.getStepZ() == 0 ? (random.nextDouble() - 0.5) * 0.8 : 0);
        level.addParticle(ModParticles.PRISM_MOTE.get(), x, y, z,
                dir.getStepX() * 0.01, 0.008 + dir.getStepY() * 0.01, dir.getStepZ() * 0.01);
    }
}
