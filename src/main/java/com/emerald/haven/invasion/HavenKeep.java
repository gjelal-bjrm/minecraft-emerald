package com.emerald.haven.invasion;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ce qu'un joueur mort dans Haven retrouve a sa reapparition : ses poches et son experience.
 *
 * POURQUOI PAS « ANNULER LE BUTIN » : Player.dropEquipment VIDE l'inventaire
 * (inventory.dropAll) avant LivingDropsEvent ; annuler l'evenement supprimerait
 * les objets au lieu de les garder. Et la regle keepInventory est celle de tout
 * le serveur, pas d'une dimension.
 *
 * DONC : a la mort (HavenRules, LivingDeathEvent en LOWEST), l'inventaire et
 * l'experience sont copies ICI puis l'inventaire est vide -- rien ne tombe, ni au
 * sol ni dans une tombe --, l'experience lachee est annulee, et PlayerEvent.Clone
 * rend le tout au nouveau joueur. Sauvegarde avec le monde : un joueur qui se
 * deconnecte sur l'ecran de mort, ou un serveur qui s'arrete, ne perd rien.
 */
public final class HavenKeep extends SavedData {

    public static final String KEY = "emeraldweapons_haven_keep";

    private final Map<UUID, CompoundTag> kept = new HashMap<>();

    public static HavenKeep get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(HavenKeep::new, HavenKeep::load), KEY);
    }

    private static HavenKeep load(CompoundTag tag, HolderLookup.Provider registries) {
        HavenKeep keep = new HavenKeep();
        for (Tag entry : tag.getList("Joueurs", Tag.TAG_COMPOUND)) {
            CompoundTag player = (CompoundTag) entry;
            if (player.hasUUID("Joueur")) {
                keep.kept.put(player.getUUID("Joueur"), player.getCompound("Garde"));
            }
        }
        return keep;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : this.kept.entrySet()) {
            CompoundTag player = new CompoundTag();
            player.putUUID("Joueur", entry.getKey());
            player.put("Garde", entry.getValue());
            list.add(player);
        }
        tag.put("Joueurs", list);
        return tag;
    }

    /**
     * Garde l'inventaire et l'experience d'un joueur qui meurt, puis vide ses poches.
     *
     * Une garde deja presente (mort sans reapparition entre deux, cas qui ne
     * devrait pas arriver) n'est pas ecrasee : ses objets sont ajoutes a la suite.
     */
    public static void stash(ServerPlayer player) {
        HavenKeep keep = get(player.server);
        CompoundTag tag = new CompoundTag();
        tag.put("Inventaire", player.getInventory().save(new ListTag()));
        tag.putInt("Niveau", player.experienceLevel);
        tag.putFloat("Progres", player.experienceProgress);
        tag.putInt("Total", player.totalExperience);
        CompoundTag previous = keep.kept.get(player.getUUID());
        if (previous != null) {
            ListTag merged = previous.getList("Inventaire", Tag.TAG_COMPOUND).copy();
            merged.addAll(tag.getList("Inventaire", Tag.TAG_COMPOUND));
            tag.put("Surplus", merged);
        }
        keep.kept.put(player.getUUID(), tag);
        player.getInventory().clearContent();
        keep.setDirty();
    }

    /**
     * Rend au joueur reapparu ce qui a ete garde a sa mort.
     *
     * @return vrai s'il y avait une garde
     */
    public static boolean restore(ServerPlayer player) {
        HavenKeep keep = get(player.server);
        CompoundTag tag = keep.kept.remove(player.getUUID());
        if (tag == null) {
            return false;
        }
        player.getInventory().load(tag.getList("Inventaire", Tag.TAG_COMPOUND));
        if (tag.contains("Surplus", Tag.TAG_LIST)) {
            // les objets d'une garde precedente : chacun dans une case libre, sinon au plus pres
            var surplus = new net.minecraft.world.entity.player.Inventory(player);
            surplus.load(tag.getList("Surplus", Tag.TAG_COMPOUND));
            for (int i = 0; i < surplus.getContainerSize(); i++) {
                if (!surplus.getItem(i).isEmpty()) {
                    player.getInventory().placeItemBackInInventory(surplus.getItem(i).copy());
                }
            }
        }
        player.experienceLevel = tag.getInt("Niveau");
        player.experienceProgress = tag.getFloat("Progres");
        player.totalExperience = tag.getInt("Total");
        keep.setDirty();
        return true;
    }

    public static boolean has(MinecraftServer server, UUID player) {
        return get(server).kept.containsKey(player);
    }
}
