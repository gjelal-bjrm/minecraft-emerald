package com.emerald.block;

import com.emerald.menu.SpecializationAltarMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * L'Autel de Specialisation : ou l'on monte son personnage de +1 a +20.
 *
 * Le pendant de la Forge pour la specialisation. La plume en clic droit
 * fonctionne toujours, mais elle ne montre rien ; l'autel montre l'echelle
 * entiere -- les vingt paliers, les plumes qu'on porte contre celles qu'il
 * faut, la chance de chaque coup -- et un bouton.
 */
public class SpecializationAltarBlock extends Block {

    public static final Component TITLE =
            Component.translatable("container.emeraldweapons.specialization_altar");

    public SpecializationAltarBlock(Properties properties) {
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        // les plumes en main ne doivent pas empecher d'ouvrir : on vient justement avec
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        player.openMenu(state.getMenuProvider(level, pos));
        return ItemInteractionResult.CONSUME;
    }

    @Nullable
    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inventory, player) ->
                new SpecializationAltarMenu(id, inventory, ContainerLevelAccess.create(level, pos)), TITLE);
    }
}
