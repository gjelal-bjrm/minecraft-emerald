package com.emerald.item;

import com.emerald.haven.journey.HavenPortal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Le Portail de Haven a poser (HavenPortal, cahier §106) : quatre lingots d'Arcencium et une perle
 * de l'Ender.
 *
 * Clic droit sur le sol : l'anneau se dresse au-dessus du bloc vise, sa face vers le joueur. Il faut
 * un sol et quatre blocs libres au-dessus. En poser un deuxieme retire le premier, et rend son objet.
 */
public class HavenPortalItem extends Item {

    public HavenPortalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
            return InteractionResult.PASS;
        }
        BlockPos feet = context.getClickedFace() == Direction.UP ? context.getClickedPos().above()
                : context.getClickedPos().relative(context.getClickedFace());
        Direction facing = player.getDirection().getOpposite();
        HavenPortal.Placed placed = HavenPortal.place(player, server, feet, facing);
        switch (placed) {
            case POSE, DEPLACE -> {
                if (!player.getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                    if (placed == HavenPortal.Placed.DEPLACE && !player.getInventory().add(new ItemStack(this))) {
                        player.drop(new ItemStack(this), false);
                    }
                }
                player.displayClientMessage(Component.translatable(placed == HavenPortal.Placed.DEPLACE
                        ? "item.emeraldweapons.haven_portal.moved" : "item.emeraldweapons.haven_portal.placed")
                        .withStyle(ChatFormatting.AQUA), false);
                return InteractionResult.CONSUME;
            }
            case DANS_HAVEN -> fail(player, "item.emeraldweapons.haven_portal.in_haven");
            case SANS_VILLE -> fail(player, "item.emeraldweapons.haven_portal.no_city");
            case PAS_LA_PLACE -> fail(player, "item.emeraldweapons.haven_portal.blocked");
        }
        return InteractionResult.FAIL;
    }

    private static void fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.emeraldweapons.haven_portal.hint.open").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.emeraldweapons.haven_portal.hint.challenge").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.emeraldweapons.haven_portal.hint.pickup").withStyle(ChatFormatting.GRAY));
    }
}
