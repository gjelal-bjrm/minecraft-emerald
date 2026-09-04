package com.emerald.specialization;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * Ce que le serveur fait du vol des ailes : effacer la chute.
 *
 * Le plane se calcule sur le client (voir WingsFlightClient) ; ici on ne
 * fait que retirer les degats de chute a proportion des ailes, pour qu'un
 * atterrissage apres un plane ne blesse pas. Des +5, un tiers de la chute
 * est pardonne ; a +15 et au-dela, toute la chute.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class WingsFlight {

    private WingsFlight() {
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int level = Specialization.level(player);
        if (level < 5) {
            return;
        }
        float kept = level >= Specialization.WINGS_FULL ? 0.0F
                : 1.0F - 0.33F - 0.067F * (level - 5);          // 67 % a +5, 0 % a +15
        if (kept <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setDamageMultiplier(event.getDamageMultiplier() * kept);
        }
    }
}
