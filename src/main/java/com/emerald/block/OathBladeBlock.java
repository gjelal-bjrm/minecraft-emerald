package com.emerald.block;

import com.emerald.game.GameManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La Lame du Serment, plantee au centre du village.
 *
 * C'est elle qui declenche la partie. Tant qu'elle est en terre, personne ne
 * peut sortir du village ni creuser, et aucun chronometre ne tourne : un joueur
 * qui rejoint en retard trouve donc la partie encore en attente, au lieu d'avoir
 * rate l'annonce. Le declencheur est une action volontaire, jamais un minuteur.
 */
public class OathBladeBlock extends Block {

    public static final BooleanProperty PLANTED = BooleanProperty.create("planted");

    private static final VoxelShape SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    public OathBladeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(PLANTED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PLANTED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!state.getValue(PLANTED)) {
            return InteractionResult.PASS;
        }
        GameManager.pullOathBlade(level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** Elle appelle : sans cet halo, rien n'attire le joueur vers la place. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PLANTED)) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 0.4 + random.nextDouble() * 1.2,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    0.0, 0.01, 0.0);
        }
    }
}
