package com.emerald.block;

import com.emerald.particles.ModParticles;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Plante luminescente (Fleur de Prisme, Herbe de Prisme).
 *
 * Le niveau de lumiere d'un bloc est fige dans son etat : il n'y a pas de
 * "lumiere seulement la nuit" natif. On combine donc une lumiere faible
 * constante (props.lightLevel) avec des particules colorees emises
 * UNIQUEMENT la nuit, cote client -- de jour la plante est discrete, de
 * nuit elle luit et petille.
 */
public class GlowingPlantBlock extends BushBlock {
    public static final MapCodec<GlowingPlantBlock> CODEC = simpleCodec(GlowingPlantBlock::new);
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);

    public GlowingPlantBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!level.isNight() || random.nextInt(6) != 0) return;
        // mote qui s'eleve doucement depuis la plante, teinte choisie cote particule
        double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.5;
        double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
        level.addParticle(ModParticles.PRISM_MOTE.get(), x, y, z,
                (random.nextDouble() - 0.5) * 0.01, 0.012 + random.nextDouble() * 0.01,
                (random.nextDouble() - 0.5) * 0.01);
    }
}
