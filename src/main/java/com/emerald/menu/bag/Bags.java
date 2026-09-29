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

    /** Un objet qui contient d'autres objets : un sac, une boite. La poubelle les refuse. */
    public static boolean isContainer(ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(Capabilities.ItemHandler.ITEM) != null;
    }
}
