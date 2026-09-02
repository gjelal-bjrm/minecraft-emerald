package com.emerald.block;

import com.emerald.menu.ArcenciumForgeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * La Forge d'Arcencium : ou l'on monte une piece de +1 a +10.
 *
 * L'Etabli de Sertissage sertit, grave et tire la rarete ; la forge ne fait
 * que l'amelioration, mais elle la MONTRE : l'echelle des metaux, ce qu'on
 * porte, et la chance du coup. On y vient l'arme en main, elle monte d'elle-
 * meme sur l'enclume.
 */
public class ArcenciumForgeBlock extends Block {

    public static final Component TITLE =
            Component.translatable("container.emeraldweapons.arcencium_forge");

    public ArcenciumForgeBlock(Properties properties) {
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

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        // l'arme en main ne doit pas empecher d'ouvrir : c'est justement elle qu'on vient monter
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        player.openMenu(state.getMenuProvider(level, pos));
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) ->
                new ArcenciumForgeMenu(id, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
    }
}
