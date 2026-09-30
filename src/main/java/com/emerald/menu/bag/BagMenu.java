package com.emerald.menu.bag;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Un menu qui montre le sac porte a cote de ce qu'il montre deja (BagPanel).
 *
 * Les sous-classes posent leurs cases, puis appellent addBag ; elles recoivent leurs
 * propres boutons par onButton, et se servent de moveOutOfBag / moveIntoBag dans leur
 * quickMoveStack. Tout le reste -- le debut et la fin d'un clic sur le sac, l'envoi
 * de la fenetre a chaque tique, les boutons du panneau -- se fait ici.
 */
public abstract class BagMenu extends AbstractContainerMenu {

    @Nullable
    protected BagPanel bag;

    protected BagMenu(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    /** Ajoute le panneau du sac, a cette place dans l'image de l'ecran. Apres les autres cases. */
    protected final void addBag(Player player, int x, int y) {
        this.bag = new BagPanel(this, player, x, y);
        this.addDataSlots(this.bag.data());
    }

    final void addBagSlot(Slot slot) {
        this.addSlot(slot);
    }

    public final BagPanel bag() {
        return this.bag;
    }

    /** Le premier et le dernier (exclu) index des cases de l'inventaire du joueur dans ce menu. */
    protected abstract int inventoryStart();

    protected abstract int inventoryEnd();

    /**
     * Cette case se range-t-elle d'un Alt+clic (QuickStash) ? Pas les cases du sac lui-meme ;
     * les menus y ajoutent les leurs.
     */
    public boolean stashable(int index) {
        return this.bag == null || !this.bag.isBagSlot(index);
    }

    /** Les boutons propres au menu. */
    protected boolean onButton(Player player, int id) {
        return false;
    }

    @Override
    public final boolean clickMenuButton(Player player, int id) {
        if (this.bag != null && this.bag.button(id)) {
            return true;
        }
        return onButton(player, id);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (this.bag == null) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        this.bag.begin();
        try {
            if (!this.bag.intercept(slotId, button, clickType, player)) {
                super.clicked(slotId, button, clickType, player);
            }
        } finally {
            this.bag.end();
        }
    }

    @Override
    public void broadcastChanges() {
        if (this.bag != null) {
            this.bag.sync();
        }
        super.broadcastChanges();
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        if (this.bag != null) {
            this.bag.opened();
        }
    }

    @Override
    public void broadcastFullState() {
        if (this.bag != null) {
            this.bag.sync();
        }
        super.broadcastFullState();
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        // le double-clic rassemble dans l'inventaire, pas dans le sac : une case du sac
        // tient plus qu'une pile
        return !(slot instanceof BagSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * Maj+clic sur une case du sac : une pile au plus, vers ces cases d'abord (l'entree
     * d'une forge), puis vers l'inventaire. Le jeu rappelle tant qu'il reste le meme
     * objet dans la case : la case se vide pile par pile.
     */
    protected final ItemStack moveOutOfBag(Slot slot, int firstStart, int firstEnd) {
        // hors d'un clic (Inventory Essentials appelle quickMoveStack directement), le sac est
        // relu d'abord et ecrit ensuite, comme pour un clic
        if (this.bag == null) {
            return ItemStack.EMPTY;
        }
        this.bag.begin();
        try {
            return pileOut(slot, firstStart, firstEnd);
        } finally {
            this.bag.end();
        }
    }

    private ItemStack pileOut(Slot slot, int firstStart, int firstEnd) {
        ItemStack here = slot.getItem();
        if (here.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int pile = Math.min(here.getCount(), here.getMaxStackSize());
        ItemStack portion = here.copyWithCount(pile);
        if (firstEnd > firstStart) {
            this.moveItemStackTo(portion, firstStart, firstEnd, false);
        }
        if (!portion.isEmpty()) {
            this.moveItemStackTo(portion, inventoryStart(), inventoryEnd(), true);
        }
        int moved = pile - portion.getCount();
        if (moved <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = here.copyWithCount(moved);
        here.shrink(moved);
        slot.setChanged();
        return out;
    }

    protected final ItemStack moveOutOfBag(Slot slot) {
        return moveOutOfBag(slot, 0, 0);
    }

    /**
     * Maj+clic vers le sac : la pile entiere, rangee dans tout le sac (pas seulement les
     * cases visibles). Rend null si le sac n'en a rien pris -- l'appelant fait alors ce
     * que le jeu ferait --, la pile deplacee si tout est entre, et une pile vide si une
     * partie seulement est entree (le jeu s'arrete la, sans envoyer le reste ailleurs).
     *
     * Le client ne connait pas le sac : il ne predit rien et laisse le serveur corriger
     * l'ecran a la tique suivante.
     */
    @Nullable
    protected final ItemStack moveIntoBag(Player player, Slot slot) {
        if (this.bag == null || !this.bag.present()) {
            return null;
        }
        ItemStack stack = slot.getItem();
        if (Bags.forbidden(stack)) {
            return null;
        }
        if (player.level().isClientSide) {
            return ItemStack.EMPTY;
        }
        this.bag.begin();
        try {
            return pileIn(slot, stack);
        } finally {
            this.bag.end();
        }
    }

    @Nullable
    private ItemStack pileIn(Slot slot, ItemStack stack) {
        ItemStack rest = this.bag.insert(stack);
        int moved = stack.getCount() - rest.getCount();
        if (moved <= 0) {
            return null;
        }
        ItemStack out = stack.copyWithCount(moved);
        stack.shrink(moved);
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, out);
            return out;
        }
        slot.setChanged();
        return ItemStack.EMPTY;
    }
}
