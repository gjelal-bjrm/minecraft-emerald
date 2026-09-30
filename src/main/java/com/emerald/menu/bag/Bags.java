package com.emerald.menu.bag;

import com.emerald.item.CuriosStash;
import com.emerald.item.ModItems;
import com.emerald.jak.gun.MorphGunKeeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Trouver les sacs d'un joueur, et dire ce qui n'y entre pas.
 *
 * L'ORDRE DES ONGLETS : le dos d'abord (les cases Curios, ou se porte le Sac
 * d'Arcencium), puis la main gauche, puis l'inventaire. Le sac principal est le
 * premier onglet ; une boite de Shulker ou un second sac viennent apres.
 */
public final class Bags {

    private Bags() {
    }

    /** Le sac derriere cette pile, ou null si elle n'en est pas un. Cote serveur. */
    @Nullable
    public static Bag of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (ModList.get().isLoaded("sophisticatedbackpacks")) {
            Bag backpack = com.emerald.compat.SophisticatedBags.of(stack);
            if (backpack != null) {
                return backpack;
            }
        }
        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler instanceof IItemHandlerModifiable modifiable && handler.getSlots() > 0) {
            return new HandlerBag(stack, modifiable);
        }
        return null;
    }

    /** Les piles portees ou un sac peut se trouver, dans l'ordre des onglets. */
    public static List<ItemStack> worn(Player player) {
        List<ItemStack> all = new ArrayList<>();
        if (ModList.get().isLoaded("curios")) {
            all.addAll(CuriosStash.worn(player));
        }
        all.addAll(player.getInventory().offhand);
        all.addAll(player.getInventory().items);
        return all;
    }

    /** Les sacs du joueur, au plus tant. Cote serveur. */
    public static List<Bag> scan(Player player, int max) {
        List<Bag> bags = new ArrayList<>();
        for (ItemStack stack : worn(player)) {
            Bag bag = of(stack);
            if (bag != null) {
                bags.add(bag);
                if (bags.size() >= max) {
                    break;
                }
            }
        }
        return bags;
    }

    /** Cette pile precise est-elle encore portee ? Par identite : un sac jete ne se lit plus. */
    public static boolean isWorn(Player player, ItemStack stack) {
        for (ItemStack worn : worn(player)) {
            if (worn == stack) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ce qui n'entre dans aucun sac par nos ecrans : le Morph Gun et le JET-Board, que
     * leurs gardiens doivent toujours voir (ils comptent l'inventaire, pas les sacs,
     * et en donneraient un second).
     */
    public static boolean forbidden(ItemStack stack) {
        return MorphGunKeeper.isGun(stack) || stack.is(ModItems.JET_BOARD.get());
    }

    /**
     * Range cette pile dans ce sac, tout entier : d'abord sur les piles du meme objet, puis dans
     * les cases vides. Rend ce qui n'est pas entre (la pile elle-meme si le sac la refuse). Cote
     * serveur ; le panneau d'un ecran y passe aussi (BagPanel.insert), entre sa relecture et son
     * ecriture.
     */
    public static ItemStack insert(Bag bag, ItemStack stack) {
        if (stack.isEmpty() || forbidden(stack) || stack == bag.stack()) {
            return stack;
        }
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int slot = 0; slot < bag.size() && !rest.isEmpty(); slot++) {
                ItemStack here = bag.get(slot);
                boolean fits = pass == 0
                        ? !here.isEmpty() && ItemStack.isSameItemSameComponents(here, rest)
                        : here.isEmpty();
                if (!fits || !bag.accepts(slot, rest)) {
                    continue;
                }
                int room = bag.limit(slot, rest) - here.getCount();
                if (room <= 0) {
                    continue;
                }
                int moved = Math.min(room, rest.getCount());
                bag.set(slot, rest.copyWithCount(here.getCount() + moved));
                rest.shrink(moved);
            }
        }
        return rest;
    }

    /** Une case de ce sac au moins prend-elle cet objet ? (Plein ou non.) */
    public static boolean accepts(Bag bag, ItemStack stack) {
        if (stack.isEmpty() || forbidden(stack) || stack == bag.stack()) {
            return false;
        }
        for (int slot = 0; slot < bag.size(); slot++) {
            if (bag.accepts(slot, stack)) {
                return true;
            }
        }
        return false;
    }

    /** Un objet qui contient d'autres objets : un sac, une boite. La poubelle les refuse. */
    public static boolean isContainer(ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(Capabilities.ItemHandler.ITEM) != null;
    }
}
