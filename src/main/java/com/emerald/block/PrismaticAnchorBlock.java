package com.emerald.block;

import com.emerald.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Une Ancre Prismatique.
 *
 * On l'alimente en Arcencium pour lancer son rituel ; un siege suit
 * immediatement. Une ancre tenue devient un point de reapparition, ce qui lie
 * l'objectif principal a la penalite de mort au lieu d'en faire deux systemes
 * separes.
 */
public class PrismaticAnchorBlock extends Block {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public PrismaticAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        GameManager.describeAnchor(level, pos, player);
        return InteractionResult.CONSUME;
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        if (GameManager.feedAnchor(level, pos, player, stack)) {
            return net.minecraft.world.ItemInteractionResult.CONSUME;
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Le faisceau : c'est ce qui rend une ancre reperable de loin. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean active = state.getValue(ACTIVE);
        int count = active ? 6 : 2;
        for (int i = 0; i < count; i++) {
            level.addParticle(active ? ParticleTypes.END_ROD : ParticleTypes.PORTAL,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    pos.getY() + 1.0 + random.nextDouble() * (active ? 8.0 : 3.0),
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                    0.0, active ? 0.08 : 0.02, 0.0);
        }
    }
}
