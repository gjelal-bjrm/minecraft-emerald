package com.emerald.item;

import com.emerald.haven.journey.HavenAgenda;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * L'agenda de Haven (cahier §83) : le « systeme de quete » que le joueur a voulu a la place
 * des messages qui disaient ou aller. Un clic droit l'ouvre ; ses pages sont les rendez-vous
 * du joueur, ecrits par le serveur selon ou il en est (HavenAgenda).
 */
public class HavenAgendaItem extends Item {

    public HavenAgendaItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            HavenAgenda.open(server);
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
