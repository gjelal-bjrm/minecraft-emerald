package com.emerald.block;

import com.emerald.particles.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Feuillage de Prisme : des motes multicolores s'en detachent et tombent
 * doucement (cote client). Plus frequentes la nuit.
 */
public class PrismLeavesBlock extends LeavesBlock {
    public PrismLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        int chance = level.isNight() ? 18 : 40;
        if (random.nextInt(chance) != 0) return;
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isAir()) return;
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() - 0.05;
        double z = pos.getZ() + random.nextDouble();
        level.addParticle(ModParticles.PRISM_MOTE.get(), x, y, z,
                (random.nextDouble() - 0.5) * 0.01, -0.02 - random.nextDouble() * 0.015,
                (random.nextDouble() - 0.5) * 0.01);
    }
}
