package com.emerald.block;

import com.emerald.game.SanctuarySeals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Un Sceau du Tombeau : on l'eveille d'un contact, et il ne se rendort pas.
 *
 * Trois d'entre eux dorment dans la pyramide, et l'ancre du sommet refuse de
 * s'allumer tant qu'ils ne sont pas tous eveilles. C'est ce qui met l'interieur
 * sur le chemin de l'objectif au lieu d'en faire un detour facultatif -- un
 * tresor qu'on peut ignorer reste ignore, une serrure non.
 *
 * Il ne se casse pas facilement : on ne contourne pas une serrure a la pioche.
 */
public class TombSealBlock extends Block {

    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public TombSealBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(LIT)) {
            return InteractionResult.CONSUME;      // deja eveille, rien a refaire
        }
        if (!(level instanceof ServerLevel server)
                || !SanctuarySeals.light(server, pos, player)) {
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos, state.setValue(LIT, true), 3);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.BLOCKS, 1.0F, 1.4F);
        server.sendParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                40, 0.3, 0.6, 0.3, 0.08);
        return InteractionResult.CONSUME;
    }

    /** Eteint, il respire a peine ; eveille, il fait colonne. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean lit = state.getValue(LIT);
        for (int i = 0; i < (lit ? 4 : 1); i++) {
            level.addParticle(lit ? ParticleTypes.END_ROD : ParticleTypes.SMOKE,
                    pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    pos.getY() + 1.0 + random.nextDouble() * (lit ? 3.0 : 0.4),
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                    0.0, lit ? 0.05 : 0.01, 0.0);
        }
    }
}
