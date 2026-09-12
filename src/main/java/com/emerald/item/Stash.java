package com.emerald.item;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * LES POCHES DU JOUEUR : son inventaire, puis chaque sac qu'il porte.
 *
 * « Que notre forge et tous nos equipements d'amelioration reconnaissent
 * notre inventaire sans que j'aie besoin de passer les objets d'un
 * inventaire a l'autre. » Le Sac d'Arcencium (un sac de Sophisticated
 * Backpacks, prerempli d'ameliorations -- voir game/ArcenciumBackpack)
 * ramasse tout seul nos objets ; s'il fallait ensuite les en sortir pour
 * payer la Forge, on n'aurait rien gagne.
 *
 * On ne connait PAS le sac ici : tout objet porte qui expose un conteneur
 * (la capacite ItemHandler, que Sophisticated Backpacks fournit sur ses
 * sacs) compte comme une poche. Dans l'inventaire, en main gauche, ou dans
 * une case Curios -- c'est la que le sac se porte, dans le dos. La Forge,
 * l'Autel, le Carnet et les cadeaux passent tous par ici, et un seul endroit
 * sait fouiller.
 *
 * L'ordre est toujours le meme : l'inventaire d'abord, les sacs ensuite.
 * On prend d'abord ce que le joueur a sous la main, on range d'abord ou il
 * regarde.
 */
public final class Stash {

    private Stash() {
    }

    /** Les conteneurs portes, sacs et autres, dans l'ordre ou on les fouille. */
    public static List<IItemHandler> bags(Player player) {
        List<IItemHandler> bags = new ArrayList<>();
        for (ItemStack stack : worn(player)) {
            if (stack.isEmpty()) {
                continue;
            }
            IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
            if (handler != null) {
                bags.add(handler);
            }
        }
        return bags;
    }

    /** Ce que le joueur porte sans le ranger : inventaire, main gauche, cases Curios. */
    private static List<ItemStack> worn(Player player) {
        List<ItemStack> all = new ArrayList<>(player.getInventory().items);
        all.addAll(player.getInventory().offhand);
        if (ModList.get().isLoaded("curios")) {
            all.addAll(CuriosStash.worn(player));
        }
        return all;
    }

    /** Combien de cet objet le joueur possede, poches comprises. */
    public static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        for (IItemHandler bag : bags(player)) {
            for (int slot = 0; slot < bag.getSlots(); slot++) {
                ItemStack stack = bag.getStackInSlot(slot);
                if (stack.is(item)) {
                    n += stack.getCount();
                }
            }
        }
        return n;
    }

    /**
     * Preleve jusqu'a tant de cet objet, l'inventaire d'abord.
     *
     * @return combien ont vraiment ete pris
     */
    public static int take(Player player, Item item, int wanted) {
        int left = wanted;
        for (ItemStack stack : player.getInventory().items) {
            left -= shrink(stack, item, left);
        }
        for (ItemStack stack : player.getInventory().offhand) {
            left -= shrink(stack, item, left);
        }
        for (IItemHandler bag : bags(player)) {
            for (int slot = 0; slot < bag.getSlots() && left > 0; slot++) {
                // une case de sac peut tenir plus qu'une pile : on tire
                // jusqu'a ce qu'elle ne rende plus rien
                while (left > 0 && bag.getStackInSlot(slot).is(item)) {
                    ItemStack got = bag.extractItem(slot, left, false);
                    if (got.isEmpty()) {
                        break;
                    }
                    left -= got.getCount();
                }
            }
        }
        return wanted - left;
    }

    private static int shrink(ItemStack stack, Item item, int left) {
        if (left <= 0 || !stack.is(item)) {
            return 0;
        }
        int taken = Math.min(left, stack.getCount());
        stack.shrink(taken);
        return taken;
    }

    /** Range dans l'inventaire, sinon dans un sac, sinon aux pieds : rien ne se perd. */
    public static void give(Player player, ItemStack stack) {
        if (stack.isEmpty() || player.getInventory().add(stack)) {
            return;
        }
        ItemStack rest = stack;
        for (IItemHandler bag : bags(player)) {
            rest = ItemHandlerHelper.insertItem(bag, rest, false);
            if (rest.isEmpty()) {
                return;
            }
        }
        player.drop(rest, false);
    }

    /**
     * Toutes les piles que le joueur a sur lui : inventaire, pieces portees,
     * contenu des sacs. Pour les tests d'equipement du Carnet -- une arme
     * rangee dans le sac est une arme qu'on a.
     */
    public static List<ItemStack> everything(Player player) {
        List<ItemStack> all = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) {
                all.add(stack);
            }
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty()) {
                all.add(worn);
            }
        }
        for (IItemHandler bag : bags(player)) {
            for (int slot = 0; slot < bag.getSlots(); slot++) {
                ItemStack stack = bag.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    all.add(stack);
                }
            }
        }
        return all;
    }
}
