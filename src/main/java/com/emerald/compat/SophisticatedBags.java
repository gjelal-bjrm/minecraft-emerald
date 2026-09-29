package com.emerald.compat;

import com.emerald.menu.bag.Bag;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;

import javax.annotation.Nullable;

/**
 * Les sacs de Sophisticated Backpacks pour nos ecrans. Classe A PART : on n'y entre
 * qu'apres avoir demande a ModList (Bags.of), et rien d'autre ne charge l'API du mod.
 *
 * LE CONTENEUR BRUT, pas celui que le sac offre aux autres mods. La capacite de
 * l'objet passe par les ameliorations a l'entree : l'amelioration « Vide » du Sac
 * d'Arcencium y detruit la chair putride, les os, les fils. C'est voulu au ramassage ;
 * ranger a la main un objet que le sac detruirait sans rien dire ne l'est pas. Le
 * propre ecran du sac ecrit ses cases directement : nous aussi (setStackInSlot), apres
 * avoir demande au sac si l'objet est admis (isItemValid : sacs imbriques, cases
 * memorisees) et combien la case en tient (getStackLimit : quatre piles avec
 * l'amelioration de palier 2).
 */
public final class SophisticatedBags {

    private SophisticatedBags() {
    }

    /** Le sac derriere cette pile, ou null si ce n'en est pas un (ou s'il n'a pas encore de contenu). */
    @Nullable
    public static Bag of(ItemStack stack) {
        if (!(stack.getItem() instanceof BackpackItem)) {
            return null;
        }
        IBackpackWrapper wrapper = BackpackWrapper.fromStack(stack);
        if (wrapper.getContentsUuid().isEmpty()) {
            return null;                            // un sac neuf, jamais ouvert : rien a montrer
        }
        return new Backpack(stack, wrapper);
    }

    private record Backpack(ItemStack stack, IBackpackWrapper wrapper) implements Bag {

        private InventoryHandler inventory() {
            return this.wrapper.getInventoryHandler();
        }

        @Override
        public int size() {
            return inventory().getSlots();
        }

        @Override
        public ItemStack get(int slot) {
            return inventory().getStackInSlot(slot).copy();
        }

        @Override
        public void set(int slot, ItemStack value) {
            inventory().setStackInSlot(slot, value);
        }

        @Override
        public int limit(int slot, ItemStack value) {
            return inventory().getStackLimit(slot, value);
        }

        @Override
        public boolean accepts(int slot, ItemStack value) {
            InventoryHandler inventory = inventory();
            return inventory.isSlotAccessible(slot) && inventory.isItemValid(slot, value);
        }

        @Override
        public void sort() {
            this.wrapper.sort();                    // le tri du sac : ses reglages, ses cases a ne pas trier
        }

        @Override
        public int multiplier() {
            return Math.max(1, (int) Math.round(inventory().getStackSizeMultiplier()));
        }

        @Override
        public Object key() {
            return this.wrapper.getContentsUuid().orElse(null);
        }
    }
}
