package com.emerald.quest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.*;

/*
 * QuestManager — système de quêtes simple pour le mod Jak 3.
 *
 * Architecture :
 *   - Les quêtes sont définies statiquement dans QUEST_DEFINITIONS
 *   - La progression est stockée côté joueur via PlayerQuestData (Capability/Attachment)
 *   - Les conditions de complétion sont vérifiées à chaque événement pertinent
 *     (mort de mob, collecte d'item, construction) depuis les event handlers
 *
 * Types de conditions :
 *   KILL_MOB     — tuer N entités d'un type donné
 *   COLLECT_ITEM — avoir N exemplaires d'un item dans l'inventaire
 *   REACH_LOCATION — s'approcher d'un BlockPos dans un rayon donné
 *
 * Récompenses :
 *   - XP
 *   - Items (définis par ResourceLocation)
 *   - Déblocage d'une quête suivante (questId chaîné)
 */
public class QuestManager {

    // -------------------------------------------------------------------------
    // Définitions des quêtes
    // -------------------------------------------------------------------------
    public static final Map<Integer, QuestDefinition> QUEST_DEFINITIONS = new LinkedHashMap<>();

    static {
        // Quête 0 : Éliminer les Metalheads près de l'arène
        QUEST_DEFINITIONS.put(0, new QuestDefinition(
                0,
                "Les Metalheads de l'arène",
                "Des Metalheads ont envahi les abords de l'arène de Spargus. Élimines-en 5.",
                QuestCondition.killMob("emerald:metalhead", 5),
                new QuestReward(200, Items.DIAMOND, 2),
                1 // quête suivante débloquée
        ));

        // Quête 1 : Collecter du minerai Precursor
        QUEST_DEFINITIONS.put(1, new QuestDefinition(
                1,
                "Minerai des anciens",
                "Rapporte 5 minerais de fer des ruines Precursor au marchand.",
                QuestCondition.collectItem("minecraft:iron_ingot", 5),
                new QuestReward(150, Items.GOLD_INGOT, 3),
                2
        ));

        // Quête 2 : Atteindre Haven City
        QUEST_DEFINITIONS.put(2, new QuestDefinition(
                2,
                "Route vers Haven City",
                "Trouve la route menant à Haven City et atteins ses portes.",
                QuestCondition.reachLocation(0, 64, 0, 50), // rayon 50 blocs autour de l'origine
                new QuestReward(300, Items.NETHERITE_SCRAP, 1),
                3
        ));

        // Quête 3 : Détruire 3 KG Deathbots
        QUEST_DEFINITIONS.put(3, new QuestDefinition(
                3,
                "Résistance contre les KG",
                "Les KG Deathbots patrouillent Haven City. Neutralises-en 3.",
                QuestCondition.killMob("emerald:kg_deathbot", 3),
                new QuestReward(400, Items.NETHERITE_INGOT, 1),
                -1 // dernière quête de la chaîne
        ));
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /*
     * Démarre une quête pour un joueur.
     * Ignoré si la quête est déjà active ou complétée.
     */
    public static void startQuest(Player player, int questId) {
        if (!(player instanceof ServerPlayer sp)) return;

        PlayerQuestData data = PlayerQuestData.get(sp);
        if (data.isCompleted(questId) || data.isActive(questId)) return;

        QuestDefinition quest = QUEST_DEFINITIONS.get(questId);
        if (quest == null) return;

        data.startQuest(questId);
        sp.sendSystemMessage(Component.literal(
                "§6[Nouvelle quête] §e" + quest.title));
        sp.sendSystemMessage(Component.literal(
                "§7" + quest.description));
    }

    /*
     * Notifie une progression de type KILL_MOB.
     * Appelé depuis un LivingDeathEvent dans Jak3EventHandlers.
     */
    public static void onMobKilled(Player player, String mobId) {
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerQuestData data = PlayerQuestData.get(sp);

        for (int questId : data.getActiveQuestIds()) {
            QuestDefinition quest = QUEST_DEFINITIONS.get(questId);
            if (quest == null) continue;
            if (quest.condition.type != ConditionType.KILL_MOB) continue;
            if (!quest.condition.targetId.equals(mobId)) continue;

            int progress = data.incrementProgress(questId);
            int required = quest.condition.amount;

            sp.sendSystemMessage(Component.literal(
                    "§7[" + quest.title + "] " + progress + "/" + required));

            if (progress >= required) {
                completeQuest(sp, questId);
            }
        }
    }

    /*
     * Notifie une progression de type COLLECT_ITEM.
     * Appelé depuis un ItemPickupEvent dans Jak3EventHandlers.
     */
    public static void onItemCollected(Player player, String itemId, int count) {
        if (!(player instanceof ServerPlayer sp)) return;
        PlayerQuestData data = PlayerQuestData.get(sp);

        for (int questId : data.getActiveQuestIds()) {
            QuestDefinition quest = QUEST_DEFINITIONS.get(questId);
            if (quest == null) continue;
            if (quest.condition.type != ConditionType.COLLECT_ITEM) continue;
            if (!quest.condition.targetId.equals(itemId)) continue;

            // Vérifie le compte dans l'inventaire directement
            int inv = (int) Arrays.stream(sp.getInventory().items.toArray())
                    .filter(s -> s instanceof net.minecraft.world.item.ItemStack is
                            && is.getItem().toString().equals(itemId))
                    .mapToLong(s -> ((net.minecraft.world.item.ItemStack) s).getCount())
                    .sum();

            sp.sendSystemMessage(Component.literal(
                    "§7[" + quest.title + "] " + Math.min(inv, quest.condition.amount)
                            + "/" + quest.condition.amount));

            if (inv >= quest.condition.amount) {
                completeQuest(sp, questId);
            }
        }
    }

    /*
     * Notifie une progression de type REACH_LOCATION.
     * Appelé depuis un PlayerTickEvent dans Jak3EventHandlers (toutes les 40 ticks).
     */
    public static void onPlayerMoved(ServerPlayer player) {
        PlayerQuestData data = PlayerQuestData.get(player);

        for (int questId : data.getActiveQuestIds()) {
            QuestDefinition quest = QUEST_DEFINITIONS.get(questId);
            if (quest == null) continue;
            if (quest.condition.type != ConditionType.REACH_LOCATION) continue;

            double dist = player.position().distanceTo(
                    new net.minecraft.world.phys.Vec3(
                            quest.condition.targetX,
                            quest.condition.targetY,
                            quest.condition.targetZ));

            if (dist <= quest.condition.amount) { // amount = rayon ici
                completeQuest(player, questId);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Complétion et récompenses
    // -------------------------------------------------------------------------
    private static void completeQuest(ServerPlayer player, int questId) {
        PlayerQuestData data = PlayerQuestData.get(player);
        if (data.isCompleted(questId)) return;

        data.completeQuest(questId);

        QuestDefinition quest = QUEST_DEFINITIONS.get(questId);

        player.sendSystemMessage(Component.literal(
                "§a[Quête terminée] §2" + quest.title + " §a!"));

        // Récompenses
        player.giveExperiencePoints(quest.reward.xp);
        net.minecraft.world.item.ItemStack rewardStack =
                new net.minecraft.world.item.ItemStack(quest.reward.item, quest.reward.itemCount);
        player.addItem(rewardStack);

        player.sendSystemMessage(Component.literal(
                "§6Récompense : +" + quest.reward.xp + " XP, "
                        + quest.reward.itemCount + "x " + quest.reward.item));

        // Déblocage quête suivante
        if (quest.nextQuestId >= 0) {
            QuestDefinition next = QUEST_DEFINITIONS.get(quest.nextQuestId);
            if (next != null) {
                player.sendSystemMessage(Component.literal(
                        "§b[Nouvelle quête disponible] " + next.title
                                + " — parle à un habitant de Spargus."));
                data.unlockQuest(quest.nextQuestId);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Structures de données internes
    // -------------------------------------------------------------------------

    public enum ConditionType { KILL_MOB, COLLECT_ITEM, REACH_LOCATION }

    public static class QuestCondition {
        public final ConditionType type;
        public final String targetId;
        public final int amount;
        public final double targetX, targetY, targetZ;

        private QuestCondition(ConditionType type, String targetId, int amount,
                               double tx, double ty, double tz) {
            this.type = type; this.targetId = targetId; this.amount = amount;
            this.targetX = tx; this.targetY = ty; this.targetZ = tz;
        }

        public static QuestCondition killMob(String mobId, int count) {
            return new QuestCondition(ConditionType.KILL_MOB, mobId, count, 0, 0, 0);
        }

        public static QuestCondition collectItem(String itemId, int count) {
            return new QuestCondition(ConditionType.COLLECT_ITEM, itemId, count, 0, 0, 0);
        }

        public static QuestCondition reachLocation(double x, double y, double z, int radius) {
            return new QuestCondition(ConditionType.REACH_LOCATION, "", radius, x, y, z);
        }
    }

    public record QuestReward(int xp, net.minecraft.world.item.Item item, int itemCount) {}

    public record QuestDefinition(
            int id,
            String title,
            String description,
            QuestCondition condition,
            QuestReward reward,
            int nextQuestId
    ) {}
}
