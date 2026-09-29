package com.emerald.menu.bag;

import com.emerald.item.Sorting;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import javax.annotation.Nullable;

/**
 * Tout objet porte qui expose un conteneur MODIFIABLE : une boite de Shulker dans
 * l'inventaire, un sac d'un autre mod. Un conteneur qu'on ne peut qu'inserer et
 * extraire n'est pas montre : reecrire une case y demanderait de vider puis de
 * remettre, et un refus en chemin perdrait la pile.
 */
final class HandlerBag implements Bag {

    private final ItemStack stack;
    private final IItemHandlerModifiable handler;

    HandlerBag(ItemStack stack, IItemHandlerModifiable handler) {
        this.stack = stack;
        this.handler = handler;
    }

    @Override
    public ItemStack stack() {
        return this.stack;
    }

    @Override
    public int size() {
        return this.handler.getSlots();
    }

    @Override
    public ItemStack get(int slot) {
        return this.handler.getStackInSlot(slot).copy();
    }

    @Override
    public void set(int slot, ItemStack value) {
        this.handler.setStackInSlot(slot, value);
    }

    @Override
    public int limit(int slot, ItemStack value) {
        return Math.min(this.handler.getSlotLimit(slot), value.getMaxStackSize());
    }

    @Override
    public boolean accepts(int slot, ItemStack value) {
        return this.handler.isItemValid(slot, value);
    }

    @Override
    public void sort() {
        Sorting.handler(this.handler);
    }

    @Override
    public int multiplier() {
        return 1;
    }

    @Nullable
    @Override
    public Object key() {
        return null;
    }
}
