package com.emerald.quest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/*
 * PlayerQuestData — stocke la progression des quêtes pour un joueur.
 *
 * Stocké dans les données persistantes du joueur via le système d'attachments
 * NeoForge (PlayerDataAttachment). Pour la simplicité, on utilise ici une Map
 * statique en mémoire + sérialisation NBT dans les events de save/load.
 *
 * En production, remplace la Map statique par un IAttachment NeoForge enregistré
 * dans Jak3Attachments pour une vraie persistance multi-joueurs robuste.
 */
public class PlayerQuestData {

    // Cache mémoire : UUID → données de quêtes
    private static final Map<UUID, PlayerQuestData> CACHE = new HashMap<>();

    // État d'une quête pour ce joueur
    private final Set<Integer> activeQuests    = new HashSet<>();
    private final Set<Integer> completedQuests = new HashSet<>();
    private final Set<Integer> unlockedQuests  = new HashSet<>();
    private final Map<Integer, Integer> progress = new HashMap<>(); // questId → compteur actuel

    // -------------------------------------------------------------------------
    // Accès static
    // -------------------------------------------------------------------------
    public static PlayerQuestData get(ServerPlayer player) {
        return CACHE.computeIfAbsent(player.getUUID(), uuid -> new PlayerQuestData());
    }

    // -------------------------------------------------------------------------
    // API quêtes
    // -------------------------------------------------------------------------
    public void startQuest(int questId) {
        activeQuests.add(questId);
        progress.put(questId, 0);
    }

    public void completeQuest(int questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
        progress.remove(questId);
    }

    public void unlockQuest(int questId) {
        unlockedQuests.add(questId);
    }

    public boolean isActive(int questId) {
        return activeQuests.contains(questId);
    }

    public boolean isCompleted(int questId) {
        return completedQuests.contains(questId);
    }

    public boolean isUnlocked(int questId) {
        return unlockedQuests.contains(questId);
    }

    public Set<Integer> getActiveQuestIds() {
        return Collections.unmodifiableSet(activeQuests);
    }

    /*
     * Incrémente le compteur de progression d'une quête et retourne la nouvelle valeur.
     */
    public int incrementProgress(int questId) {
        int current = progress.getOrDefault(questId, 0) + 1;
        progress.put(questId, current);
        return current;
    }

    public int getProgress(int questId) {
        return progress.getOrDefault(questId, 0);
    }

    // -------------------------------------------------------------------------
    // Sérialisation NBT
    // Appelée depuis Jak3EventHandlers dans les events PlayerLoggedInEvent /
    // PlayerLoggedOutEvent / SavePlayerDataEvent pour persister les données.
    // -------------------------------------------------------------------------
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();

        tag.putIntArray("ActiveQuests",    activeQuests.stream().mapToInt(i -> i).toArray());
        tag.putIntArray("CompletedQuests", completedQuests.stream().mapToInt(i -> i).toArray());
        tag.putIntArray("UnlockedQuests",  unlockedQuests.stream().mapToInt(i -> i).toArray());

        CompoundTag progressTag = new CompoundTag();
        progress.forEach((id, val) -> progressTag.putInt("q" + id, val));
        tag.put("Progress", progressTag);

        return tag;
    }

    public static PlayerQuestData fromNBT(CompoundTag tag) {
        PlayerQuestData data = new PlayerQuestData();

        for (int id : tag.getIntArray("ActiveQuests"))    data.activeQuests.add(id);
        for (int id : tag.getIntArray("CompletedQuests")) data.completedQuests.add(id);
        for (int id : tag.getIntArray("UnlockedQuests"))  data.unlockedQuests.add(id);

        if (tag.contains("Progress")) {
            CompoundTag progressTag = tag.getCompound("Progress");
            for (String key : progressTag.getAllKeys()) {
                int questId = Integer.parseInt(key.substring(1));
                data.progress.put(questId, progressTag.getInt(key));
            }
        }

        return data;
    }

    /*
     * À appeler depuis PlayerLoggedInEvent pour charger les données sauvegardées.
     */
    public static void loadFromPlayer(ServerPlayer player) {
        CompoundTag persistentData = player.getPersistentData();
        if (persistentData.contains("Jak3Quests")) {
            CACHE.put(player.getUUID(),
                    fromNBT(persistentData.getCompound("Jak3Quests")));
        } else {
            CACHE.put(player.getUUID(), new PlayerQuestData());
        }
    }

    /*
     * À appeler depuis PlayerLoggedOutEvent / SavePlayerDataEvent.
     */
    public static void saveToPlayer(ServerPlayer player) {
        PlayerQuestData data = CACHE.get(player.getUUID());
        if (data != null) {
            player.getPersistentData().put("Jak3Quests", data.toNBT());
        }
    }
}
