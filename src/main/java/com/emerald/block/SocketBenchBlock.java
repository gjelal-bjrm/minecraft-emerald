package com.emerald.block;

import com.emerald.menu.SocketBenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Etabli de Sertissage : le seul moyen d'installer un artefact.
 *
 * C'est lui qui verrouille la complementarite des trois activites du mode : les
 * artefacts s'arrachent aux tempetes, l'Arcencium se mine, mais l'etabli se
 * fabrique en bois de Prisme. Sans avoir abattu d'arbre, un artefact ne sert
 * a rien.
 */
public class SocketBenchBlock extends Block {

    private static final Component TITLE =
            Component.translatable("container.emeraldweapons.socket_bench");

    public SocketBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        player.openMenu(state.getMenuProvider(level, pos));
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) ->
                new SocketBenchMenu(id, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
    }
}
