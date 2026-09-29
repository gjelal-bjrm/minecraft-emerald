package com.emerald.menu.bag;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Une case du panneau du sac.
 *
 * UNE CASE DU SAC TIENT PLUS QU'UNE PILE (256 lingots dans le Sac d'Arcencium). Le
 * code vanilla des menus ne le prevoit pas : il donnerait au curseur tout ce que la
 * case contient, ou l'echangerait tel quel contre ce que tient le curseur. D'ou :
 *  - on ne prend jamais plus d'une pile a la fois (tryRemove) ;
 *  - une case trop pleine ne s'echange pas contre un autre objet (mayPlace) ;
 *  - on peut completer une case jusqu'a ce que le sac permet (getMaxStackSize).
 * Les touches 1 a 9 sur une case trop pleine sont traitees par BagPanel.intercept.
 */
public class BagSlot extends Slot {

    private final BagPanel panel;

    BagSlot(BagPanel panel, BagWindow window, int index, int x, int y) {
        super(window, index, x, y);
        this.panel = panel;
    }

    @Override
    public boolean isActive() {
        return this.panel.shows(this.getContainerSlot());
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (stack.isEmpty() || !this.panel.accepts(this.getContainerSlot(), stack)) {
            return false;
        }
        ItemStack here = this.getItem();
        return here.isEmpty() || here.getCount() <= here.getMaxStackSize()
                || ItemStack.isSameItemSameComponents(here, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64 * this.panel.multiplier();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return this.panel.limit(this.getContainerSlot(), stack);
    }

    @Override
    public Optional<ItemStack> tryRemove(int count, int decrement, Player player) {
        int pile = Math.max(1, this.getItem().getMaxStackSize());
        return super.tryRemove(Math.min(count, pile), decrement, player);
    }
}
