package com.emerald.init;

import com.emerald.entity.KGDeathbotEntity;
import com.emerald.entity.WastelanderEntity;
import com.emerald.quest.PlayerQuestData;
import com.emerald.quest.QuestManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/*
 * Jak3EventHandlers — connecte le système de quêtes aux événements du jeu.
 *
 * Événements gérés :
 *   - LivingDeathEvent      → notifie QuestManager.onMobKilled()
 *   - ItemPickupEvent       → notifie QuestManager.onItemCollected()
 *   - PlayerTickEvent       → vérifie REACH_LOCATION toutes les 40 ticks
 *   - PlayerLoggedInEvent   → charge PlayerQuestData depuis NBT
 *   - PlayerLoggedOutEvent  → sauvegarde PlayerQuestData dans NBT
 */
@EventBusSubscriber(modid = "emeraldweapons")
public class Jak3EventHandlers {

    // -------------------------------------------------------------------------
    // Mort de mob → progression quête KILL_MOB
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // On ne s'intéresse qu'aux mobs tués par un joueur
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        String mobId = getEntityId(event.getEntity());
        QuestManager.onMobKilled(player, mobId);
    }

    // -------------------------------------------------------------------------
    // Ramassage d'item → progression quête COLLECT_ITEM
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        String itemId = event.getItemEntity().getItem().getItem().toString();
        QuestManager.onItemCollected(player, itemId, event.getItemEntity().getItem().getCount());
    }

    // -------------------------------------------------------------------------
    // Tick joueur → progression quête REACH_LOCATION (toutes les 40 ticks)
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 40 != 0) return;

        QuestManager.onPlayerMoved(player);
    }

    // -------------------------------------------------------------------------
    // Connexion / déconnexion → persistance des données de quêtes
    // -------------------------------------------------------------------------
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerQuestData.loadFromPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerQuestData.saveToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Conserve les données de quêtes après mort ou changement de dimension
        if (event.getOriginal() instanceof ServerPlayer original
                && event.getEntity() instanceof ServerPlayer clone) {
            PlayerQuestData.saveToPlayer(original);
            // Copie les données NBT vers le clone
            if (original.getPersistentData().contains("Jak3Quests")) {
                clone.getPersistentData().put("Jak3Quests",
                        original.getPersistentData().getCompound("Jak3Quests"));
            }
            PlayerQuestData.loadFromPlayer(clone);
        }
    }

    // -------------------------------------------------------------------------
    // Utilitaire
    // -------------------------------------------------------------------------
    private static String getEntityId(net.minecraft.world.entity.Entity entity) {
        ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType());
        return key != null ? key.toString() : "unknown";
    }
}
