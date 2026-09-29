package com.emerald.menu.bag;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * La fenetre sur le sac : les cases visibles, vues par les cases du menu.
 *
 * DES COPIES, JAMAIS LES PILES DU SAC. Le code vanilla des menus modifie en place la
 * pile qu'une case lui rend (grow, setCount, split), puis previent la case. Rendre la
 * pile interne du sac, c'etait laisser Sophisticated Backpacks ignorer le changement :
 * il n'enregistre son contenu que lorsqu'on passe par lui. La fenetre tient donc ses
 * propres piles, et le protocole est simple :
 *  - reload : relit le sac et remplace les piles de la fenetre. Jamais pendant un
 *    clic (le code vanilla garde alors des references sur elles) ;
 *  - flush : ecrit dans le sac, case par case, ce qui a change depuis le dernier
 *    reload -- rien d'autre, pour ne pas ecraser ce que le sac a ramasse entre-temps.
 * Le menu relit au debut de chaque clic et a chaque tique, et ecrit a la fin du clic.
 *
 * Cote client, la fenetre n'est qu'un miroir : les cases y arrivent par la
 * synchronisation des menus, et ni reload ni flush n'y touchent.
 */
public final class BagWindow implements Container {

    private final int size;
    private final NonNullList<ItemStack> view;
    private final NonNullList<ItemStack> seen;
    @Nullable
    private Bag bag;
    /** La premiere case du sac montree (un multiple de 9). */
    private int offset;

    BagWindow(int size) {
        this.size = size;
        this.view = NonNullList.withSize(size, ItemStack.EMPTY);
        this.seen = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /** Montre ce sac, a partir de cette case. Cote serveur, hors d'un clic. */
    void show(@Nullable Bag bag, int offset) {
        this.bag = bag;
        this.offset = offset;
        reload();
    }

    @Nullable
    Bag bag() {
        return this.bag;
    }

    int offset() {
        return this.offset;
    }

    /** Relit le sac. */
    void reload() {
        for (int i = 0; i < this.size; i++) {
            int at = this.offset + i;
            ItemStack stack = this.bag != null && at < this.bag.size() ? this.bag.get(at) : ItemStack.EMPTY;
            this.view.set(i, stack);
            this.seen.set(i, stack.copy());
        }
    }

    /** Ecrit dans le sac les cases changees depuis le dernier reload. */
    void flush() {
        if (this.bag == null) {
            return;
        }
        for (int i = 0; i < this.size; i++) {
            int at = this.offset + i;
            if (at >= this.bag.size()) {
                break;
            }
            ItemStack now = this.view.get(i);
            if (!ItemStack.matches(now, this.seen.get(i))) {
                this.bag.set(at, now.copy());
                this.seen.set(i, now.copy());
            }
        }
    }

    // ------------------------------------------------------------ Container

    @Override
    public int getContainerSize() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.view) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.view.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = this.view.get(slot);
        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        return stack.split(amount);                 // la fin du clic l'ecrira dans le sac
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = this.view.get(slot);
        this.view.set(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        // pas de limite ici : une case du sac tient plus qu'une pile, et le miroir du
        // client doit afficher 256 quand le serveur en envoie 256
        this.view.set(slot, stack);
    }

    @Override
    public int getMaxStackSize() {
        return 64 * 64;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return 64 * 64;
    }

    @Override
    public void setChanged() {
        // rien : le menu ecrit le sac a la fin du clic
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        // JAMAIS : vider la fenetre viderait le sac a la prochaine ecriture
    }
}
