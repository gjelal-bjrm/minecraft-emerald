package com.emerald.mine;

import com.emerald.block.ModBlocks;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.Weather;
import com.emerald.weather.WeatherManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * LES ECLATS : plus de porte fermee devant l'Arcencium.
 *
 * « Comme d'habitude, l'Arcencium ne peut pas etre pris avec une pioche en
 * fer. » Le filon reste un bloc a pioche de diamant pour le BRUT (le butin
 * normal, l'experience, la Fortune), mais une pioche d'un cran en dessous
 * n'en repart plus les mains vides : elle en detache un ou deux ECLATS, et
 * quatre eclats font un brut. Le fer est le chemin lent, le diamant le
 * chemin plein ; aucun des deux n'est un mur.
 *
 * Pendant l'Aurore, les eclats doublent aussi -- l'Aurore paie la mine, quelle
 * que soit la pioche.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class ArcenciumShards {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Set<UUID> told = new HashSet<>();

    private ArcenciumShards() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator()
                || !event.getState().is(ModBlocks.ARCENCIUM_ORE.get())) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        // la bonne pioche a son butin normal : le brut, par la table de butin
        if (!tool.is(ItemTags.PICKAXES) || tool.isCorrectToolForDrops(event.getState())) {
            return;
        }
        int count = 1 + level.random.nextInt(2);
        if (WeatherManager.current() == Weather.AURORE) {
            count += 1 + level.random.nextInt(2);
        }
        Block.popResource(level, event.getPos(), new ItemStack(ModItems.ARCENCIUM_SHARD.get(), count));
        level.playSound(null, event.getPos(), SoundEvents.AMETHYST_CLUSTER_BREAK,
                SoundSource.BLOCKS, 0.8F, 1.3F);
        LOGGER.info("Eclats : {} pour {} avec {}", count, player.getName().getString(), tool.getItem());
        if (told.add(player.getUUID())) {
            player.displayClientMessage(Component.translatable("mine.emeraldweapons.shards.hint")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }
}
