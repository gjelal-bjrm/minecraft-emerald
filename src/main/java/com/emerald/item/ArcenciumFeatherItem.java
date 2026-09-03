package com.emerald.item;

import com.emerald.specialization.Specialization;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * La Plume d'Arcencium : le materiau de la specialisation.
 *
 * Un clic droit tente le palier suivant, au prix courant en plumes, avec la
 * chance courante (voir Specialization). Lachee par les monstres, surtout
 * les forts ; trouvee dans les coffres. Elle survit a la partie avec la
 * specialisation qu'elle sert, donc elle peut etre rare.
 */
public class ArcenciumFeatherItem extends Item {

    public ArcenciumFeatherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // La plume ne monte plus rien a elle seule : elle renvoie a l'autel,
        // ou l'on voit ce qu'on depense, ce qu'on risque et ce qu'on gagne.
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(Component.translatable(
                            "item.emeraldweapons.arcencium_feather.altar")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("item.emeraldweapons.arcencium_feather.desc")
                .withStyle(ChatFormatting.GRAY));
        Player player = net.neoforged.fml.loading.FMLEnvironment.dist.isClient()
                ? com.emerald.client.ClientPlayerAccess.player() : null;
        if (player != null) {
            int lvl = com.emerald.client.WingsClient.level(player);
            if (lvl >= Specialization.MAX) {
                tooltip.add(Component.translatable("specialization.emeraldweapons.max")
                        .withStyle(ChatFormatting.GOLD));
            } else {
                int target = lvl + 1;
                tooltip.add(Component.translatable("item.emeraldweapons.arcencium_feather.next",
                        target, Specialization.COST[target], Specialization.ODDS[target])
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }
    }
}
