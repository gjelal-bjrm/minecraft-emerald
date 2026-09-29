package com.emerald.item;

import com.emerald.menu.bag.Bags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Le tri des boutons de l'inventaire d'Arcencium : les piles identiques fusionnees,
 * puis rangees dans l'ordre du jeu (l'ordre d'enregistrement des objets : les blocs
 * d'une meme famille ensemble, chaque mod a la suite), puis par nom, puis les plus
 * grosses piles d'abord.
 *
 * L'INVENTAIRE : seules les 27 cases du milieu. La barre d'action est ce que le joueur
 * a dispose a la main, on n'y touche pas ; le Morph Gun et le JET-Board ne bougent pas
 * non plus (leurs gardiens les suivent case par case).
 *
 * Les sacs de Sophisticated Backpacks se trient par leur propre tri (leurs reglages,
 * leurs cases a ne pas trier) ; ce tri-ci ne sert qu'aux autres conteneurs portes.
 */
public final class Sorting {

    private static final Comparator<ItemStack> ORDER =
            Comparator.comparingInt((ItemStack stack) -> BuiltInRegistries.ITEM.getId(stack.getItem()))
                    .thenComparing(stack -> stack.getHoverName().getString())
                    .thenComparing(ItemStack::getCount, Comparator.reverseOrder());

    private static final int MAIN_START = Inventory.getSelectionSize();
    private static final int MAIN_END = Inventory.INVENTORY_SIZE;

    private Sorting() {
    }

    /** Trie les 27 cases du milieu de l'inventaire. */
    public static void inventory(Player player) {
        Inventory inventory = player.getInventory();
        List<Integer> free = new ArrayList<>();
        List<ItemStack> loose = new ArrayList<>();
        for (int slot = MAIN_START; slot < MAIN_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (Bags.forbidden(stack)) {
                continue;                               // reste a sa place
            }
            free.add(slot);
            if (!stack.isEmpty()) {
                loose.add(stack.copy());
            }
        }
        List<ItemStack> sorted = merge(loose, ItemStack::getMaxStackSize);
        sorted.sort(ORDER);
        for (int i = 0; i < free.size(); i++) {
            inventory.setItem(free.get(i), i < sorted.size() ? sorted.get(i) : ItemStack.EMPTY);
        }
        inventory.setChanged();
    }

    /** Trie un conteneur porte qui n'est pas un sac de Sophisticated Backpacks. */
    public static void handler(IItemHandlerModifiable handler) {
        List<ItemStack> loose = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                loose.add(stack.copy());
            }
        }
        List<ItemStack> sorted = merge(loose, ItemStack::getMaxStackSize);
        sorted.sort(ORDER);
        if (sorted.size() > handler.getSlots()) {
            return;                                     // ne devrait pas arriver : fusionner ne grossit rien
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            handler.setStackInSlot(slot, slot < sorted.size() ? sorted.get(slot) : ItemStack.EMPTY);
        }
    }

    /** Fusionne les piles identiques, jusqu'a la limite donnee par pile. */
    static List<ItemStack> merge(List<ItemStack> stacks, ToIntFunction<ItemStack> limit) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : stacks) {
            ItemStack rest = stack.copy();
            for (ItemStack kept : out) {
                if (rest.isEmpty()) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(kept, rest)) {
                    int room = limit.applyAsInt(kept) - kept.getCount();
                    if (room > 0) {
                        int moved = Math.min(room, rest.getCount());
                        kept.grow(moved);
                        rest.shrink(moved);
                    }
                }
            }
            while (!rest.isEmpty()) {
                out.add(rest.split(Math.max(1, Math.min(limit.applyAsInt(rest), rest.getCount()))));
            }
        }
        return out;
    }
}
