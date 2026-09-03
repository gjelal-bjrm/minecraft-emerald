package com.emerald.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Les accessoires du modpack -- Artifacts (49) et Relics (30) -- comme butin.
 *
 * Le cahier les garde tous (section 7) mais rien dans le mode ne les faisait
 * tomber : ils vivent dans des structures qu'une partie de 90 minutes ne
 * croise pas. Ici on les tire au sort dans le registre, par espace de noms,
 * pour qu'ils tombent comme les notres. Aucune classe des deux mods n'est
 * citee : sans eux, la reserve est vide et rien ne tombe.
 *
 * Une relique tiree ici est livree DEJA ETUDIEE (voir RelicResearch) : le
 * joueur ne veut pas d'enigme entre lui et l'objet qu'il vient de gagner.
 */
public final class ModAccessories {

    private static final String[] NAMESPACES = {"artifacts", "relics"};
    @Nullable
    private static List<Item> pool;

    private ModAccessories() {
    }

    private static List<Item> pool() {
        if (pool == null) {
            List<Item> found = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                boolean ours = false;
                for (String ns : NAMESPACES) {
                    ours |= key.getNamespace().equals(ns);
                }
                if (!ours || item instanceof BlockItem || item instanceof SpawnEggItem) {
                    continue;
                }
                // chez Relics, seules les reliques comptent : pas les fioles ni les fragments
                if (key.getNamespace().equals("relics") && !RelicResearch.isRelic(item)) {
                    continue;
                }
                found.add(item);
            }
            pool = found;
        }
        return pool;
    }

    /** Un accessoire au sort, ou null si aucun des deux mods n'est la. */
    @Nullable
    public static ItemStack random(RandomSource random) {
        List<Item> items = pool();
        if (items.isEmpty()) {
            return null;
        }
        ItemStack stack = new ItemStack(items.get(random.nextInt(items.size())));
        RelicResearch.complete(stack);
        return stack;
    }

    public static int size() {
        return pool().size();
    }
}
