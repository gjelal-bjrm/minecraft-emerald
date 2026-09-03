package com.emerald.util;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Ce que la mort ne doit pas emporter.
 *
 * A la mort, Minecraft ne ressuscite pas le joueur : il en CONSTRUIT UN AUTRE
 * et ne recopie qu'une poignee de choses. Les donnees persistantes de l'ancien
 * -- ou vivent le niveau de Heros, son experience, ses points places et son
 * element -- restent sur le cadavre. Le joueur repartait donc Heros 1 apres
 * chaque mort, ce qui efface une heure de jeu.
 *
 * On les recopie donc a la main. Le mode PUNIT deja la mort comme le vanilla :
 * on laisse son equipement au sol. La fiche du personnage, elle, est le
 * resultat de toute la partie, pas de la derniere seconde.
 *
 * Tout le compose est recopie, et non nos seules cles : les refroidissements,
 * la Rage du Glaive, la Surcharge de l'Orage y vivent aussi, et ils sont tous
 * dates -- ce qui doit expirer expirera de lui-meme.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class PlayerPersistence {

    private PlayerPersistence() {
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original)
                || !(event.getEntity() instanceof ServerPlayer clone)) {
            return;
        }
        CompoundTag from = original.getPersistentData();
        CompoundTag to = clone.getPersistentData();
        for (String key : from.getAllKeys()) {
            if (!to.contains(key)) {              // ce que le jeu a deja recopie prime
                net.minecraft.nbt.Tag value = from.get(key);
                if (value != null) {
                    to.put(key, value.copy());
                }
            }
        }
    }
}
