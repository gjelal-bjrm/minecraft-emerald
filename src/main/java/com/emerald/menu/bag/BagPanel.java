package com.emerald.menu.bag;

import com.emerald.item.Sorting;
import com.emerald.network.BagTabsPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * LE PANNEAU DU SAC, commun a l'inventaire d'Arcencium, a la Forge, a l'Autel et a
 * l'Etabli : six rangees de neuf cases sur le sac porte, une barre de defilement, un
 * onglet par sac (quatre au plus), un bouton de tri pour le sac et un pour l'inventaire.
 *
 * « Je suis oblige d'ouvrir mon sac, de recuperer les objets pour les mettre dans mon
 * inventaire, et ensuite faire les tentatives, et apres ce qui reste, je dois le
 * remettre dans le sac. » Le sac s'affiche la ou l'on en a besoin ; Maj+clic range dans
 * le sac ou en sort.
 *
 * LE SERVEUR FAIT FOI. Il lit le sac (Bag), le montre par une fenetre de copies
 * (BagWindow) et envoie au client la taille, la rangee, les onglets par les donnees du
 * menu, et les icones des onglets par un paquet (BagTabsPayload). Les boutons passent
 * par clickMenuButton, le mecanisme vanilla des menus. Le client ne predit que ce que
 * le code vanilla predit deja sur les cases ; ranger dans le sac tout entier (Maj+clic)
 * se fait au serveur seul, qui corrige l'ecran a la tique suivante.
 */
public final class BagPanel {

    public static final int COLS = 9;
    public static final int ROWS = 6;
    public static final int SIZE = COLS * ROWS;
    public static final int MAX_TABS = 4;

    /** Le panneau a l'ecran : sa taille, et la premiere case dans le panneau. */
    public static final int WIDTH = 188;
    public static final int HEIGHT = 166;
    public static final int SLOTS_X = 8;
    public static final int SLOTS_Y = 18;

    public static final int BUTTON_ROW = 1000;             // + la premiere rangee a montrer
    public static final int BUTTON_TAB = 3000;             // + l'onglet
    public static final int BUTTON_SORT_BAG = 3100;
    public static final int BUTTON_SORT_INVENTORY = 3101;

    public static final int DATA_SIZE = 0;
    public static final int DATA_FIRST = 1;
    public static final int DATA_TABS = 2;
    public static final int DATA_SELECTED = 3;
    public static final int DATA_MULT = 4;
    public static final int DATA_COUNT = 5;

    /** Les sacs portes sont recherches toutes les demi-secondes, et au debut de chaque clic. */
    private static final int RESCAN_TICKS = 10;

    private final BagMenu menu;
    private final Player player;
    private final boolean server;
    private final BagWindow window = new BagWindow(SIZE);
    private final ContainerData data;
    private final int firstSlot;
    /** L'origine du panneau dans l'image de l'ecran. */
    public final int x;
    public final int y;

    // ---- serveur
    private List<Bag> bags = List.of();
    private int selected;
    private int firstRow;
    private int depth;
    private int ticks;
    private List<ItemStack> sent = List.of();
    /** Le client a le menu : on peut lui envoyer les onglets. */
    private boolean live;

    // ---- client
    private List<ItemStack> icons = List.of();

    BagPanel(BagMenu menu, Player player, int x, int y) {
        this.menu = menu;
        this.player = player;
        this.server = !player.level().isClientSide;
        this.x = x;
        this.y = y;
        this.data = this.server ? new Live() : new SimpleContainerData(DATA_COUNT);
        this.firstSlot = menu.slots.size();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                menu.addBagSlot(new BagSlot(this, this.window, col + row * COLS,
                        x + SLOTS_X + col * 18, y + SLOTS_Y + row * 18));
            }
        }
        if (this.server) {
            this.bags = Bags.scan(player, MAX_TABS);
            select(0);
        }
    }

    ContainerData data() {
        return this.data;
    }

    // ================================================================ lecture (deux cotes)

    /** Nombre de cases du sac montre (0 : aucun sac). */
    public int size() {
        return this.data.get(DATA_SIZE);
    }

    public boolean present() {
        return size() > 0;
    }

    public int firstRow() {
        return this.data.get(DATA_FIRST);
    }

    public int rows() {
        return (size() + COLS - 1) / COLS;
    }

    public int maxFirstRow() {
        return Math.max(0, rows() - ROWS);
    }

    public int tabs() {
        return this.data.get(DATA_TABS);
    }

    public int selected() {
        return this.data.get(DATA_SELECTED);
    }

    public int multiplier() {
        return Math.max(1, this.data.get(DATA_MULT));
    }

    public int firstSlot() {
        return this.firstSlot;
    }

    public boolean isBagSlot(int menuIndex) {
        return menuIndex >= this.firstSlot && menuIndex < this.firstSlot + SIZE;
    }

    /** Cette case de la fenetre montre-t-elle une case du sac ? */
    public boolean shows(int windowSlot) {
        return firstRow() * COLS + windowSlot < size();
    }

    /** Les icones des onglets (client : recues du serveur). */
    public List<ItemStack> icons() {
        return this.icons;
    }

    public void setIcons(List<ItemStack> icons) {
        this.icons = List.copyOf(icons);
    }

    boolean accepts(int windowSlot, ItemStack stack) {
        if (!shows(windowSlot) || Bags.forbidden(stack)) {
            return false;
        }
        if (!this.server) {
            return true;                                // le serveur tranchera
        }
        Bag bag = this.window.bag();
        return bag != null && stack != bag.stack() && bag.accepts(this.window.offset() + windowSlot, stack);
    }

    int limit(int windowSlot, ItemStack stack) {
        if (this.server) {
            Bag bag = this.window.bag();
            return bag == null ? stack.getMaxStackSize() : bag.limit(this.window.offset() + windowSlot, stack);
        }
        int max = stack.getMaxStackSize();
        return max <= 1 ? max : max * multiplier();
    }

    // ================================================================ serveur

    /** Le sac montre, ou null (QuickStash). Serveur, entre begin et end. */
    @Nullable
    Bag shown() {
        return current();
    }

    @Nullable
    private Bag current() {
        return this.window.bag();
    }

    private void select(int tab) {
        this.selected = this.bags.isEmpty() ? 0 : Mth.clamp(tab, 0, this.bags.size() - 1);
        this.firstRow = 0;
        this.window.show(this.bags.isEmpty() ? null : this.bags.get(this.selected), 0);
    }

    private void scroll(int row) {
        Bag bag = current();
        if (bag == null) {
            return;
        }
        this.firstRow = Mth.clamp(row, 0, maxRow(bag));
        this.window.show(bag, this.firstRow * COLS);
    }

    private static int maxRow(Bag bag) {
        return Math.max(0, (bag.size() + COLS - 1) / COLS - ROWS);
    }

    private static boolean same(Bag a, Bag b) {
        return a.stack() == b.stack() || a.key() != null && a.key().equals(b.key());
    }

    /** Relit la liste des sacs portes ; garde l'onglet montre s'il est encore la. */
    private void rescan() {
        Bag shown = current();
        List<Bag> found = Bags.scan(this.player, MAX_TABS);
        int keep = -1;
        if (shown != null) {
            for (int i = 0; i < found.size(); i++) {
                if (same(found.get(i), shown)) {
                    keep = i;
                    break;
                }
            }
        }
        this.bags = found;
        if (keep < 0) {
            select(0);
            return;
        }
        this.selected = keep;
        Bag again = found.get(keep);
        this.firstRow = Math.min(this.firstRow, maxRow(again));
        this.window.show(again, this.firstRow * COLS);
    }

    /**
     * Le debut d'un clic : le sac tel qu'il est MAINTENANT, et encore porte. Ce qu'un autre mod
     * a change dans la fenetre hors d'un clic (la touche Suppr de TrashSlot) est ecrit d'abord :
     * relu sans cela, l'objet supprime serait revenu dans le sac.
     */
    void begin() {
        if (!this.server || this.depth++ > 0) {
            return;
        }
        this.window.flush();
        Bag bag = current();
        if (bag != null && !Bags.isWorn(this.player, bag.stack())) {
            rescan();                                   // le sac montre a quitte le joueur
        }
        this.window.reload();
    }

    /** La fin d'un clic : ce qu'il a change est ecrit dans le sac. */
    void end() {
        if (this.server && --this.depth == 0) {
            this.window.flush();
        }
    }

    /** A chaque envoi du menu (chaque tique) : ecrire, puis relire. */
    void sync() {
        if (!this.server) {
            return;
        }
        this.window.flush();
        if (this.depth > 0) {
            return;                                     // en plein clic : on ne remplace rien
        }
        if (++this.ticks >= RESCAN_TICKS) {
            this.ticks = 0;
            rescan();
        }
        this.window.reload();
        if (this.live) {
            sendTabs();
        }
    }

    /**
     * Le menu vient d'etre envoye au client (sendAllDataToRemote) : les onglets partent
     * maintenant. PAS AVANT : la Forge pose la piece tenue en main des sa construction, ce
     * qui envoie deja le menu -- les icones arrivaient au client avant l'ecran, pour un menu
     * qu'il n'avait pas encore, et n'etaient jamais renvoyees (photo du 29 sept.).
     */
    void opened() {
        if (!this.server) {
            return;
        }
        this.live = true;
        this.sent = List.of();
        sendTabs();
    }

    private void sendTabs() {
        if (!(this.player instanceof ServerPlayer served)) {
            return;
        }
        List<ItemStack> now = new ArrayList<>();
        for (Bag bag : this.bags) {
            now.add(bag.stack());
        }
        if (sameStacks(now, this.sent)) {
            return;
        }
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : now) {
            copies.add(stack.copyWithCount(1));
        }
        this.sent = copies;
        PacketDistributor.sendToPlayer(served, new BagTabsPayload(this.menu.containerId, copies));
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** Les boutons du panneau (et le tri de l'inventaire). Faux pour un autre bouton. */
    boolean button(int id) {
        if (id < BUTTON_ROW || id > BUTTON_SORT_INVENTORY) {
            return false;
        }
        if (!this.server) {
            return true;
        }
        this.window.flush();
        Bag bag = current();
        if (bag != null && !Bags.isWorn(this.player, bag.stack())) {
            rescan();
        }
        this.window.reload();
        if (id == BUTTON_SORT_INVENTORY) {
            Sorting.inventory(this.player);
        } else if (id == BUTTON_SORT_BAG) {
            Bag shown = current();
            if (shown != null) {
                shown.sort();
                this.window.reload();
            }
        } else if (id >= BUTTON_TAB) {
            select(id - BUTTON_TAB);
        } else {
            scroll(id - BUTTON_ROW);
        }
        return true;
    }

    /**
     * Range cette pile dans le sac montre, tout entier : d'abord sur les piles du meme
     * objet, puis dans les cases vides. Rend ce qui n'est pas entre. Serveur seulement.
     */
    public ItemStack insert(ItemStack stack) {
        Bag bag = current();
        if (!this.server || bag == null || stack.isEmpty() || Bags.forbidden(stack) || stack == bag.stack()) {
            return stack;
        }
        this.window.flush();
        ItemStack rest = Bags.insert(bag, stack);
        this.window.reload();
        return rest;
    }

    /**
     * Les touches 1 a 9 (et F) sur une case du sac qui tient plus qu'une pile : le code
     * vanilla y poserait la case entiere dans la barre d'action. On n'en prend qu'une
     * pile, ou l'on complete la pile de la barre. Vrai si le clic est traite ici.
     */
    boolean intercept(int slotId, int button, ClickType type, Player clicker) {
        if (type != ClickType.SWAP || !isBagSlot(slotId)) {
            return false;
        }
        Slot slot = this.menu.getSlot(slotId);
        ItemStack here = slot.getItem();
        int pile = here.getMaxStackSize();
        if (here.getCount() <= pile) {
            return false;                               // une case ordinaire : le jeu sait faire
        }
        if (!(button >= 0 && button < Inventory.getSelectionSize() || button == Inventory.SLOT_OFFHAND)) {
            return true;
        }
        Inventory inventory = clicker.getInventory();
        ItemStack there = inventory.getItem(button);
        if (there.isEmpty()) {
            inventory.setItem(button, here.split(pile));
            slot.setChanged();
        } else if (ItemStack.isSameItemSameComponents(there, here)) {
            int moved = Math.min(pile - there.getCount(), here.getCount());
            if (moved > 0) {
                there.grow(moved);
                here.shrink(moved);
                slot.setChanged();
                inventory.setChanged();
            }
        }
        return true;
    }

    // ================================================================ donnees vivantes

    /** Les donnees du panneau, lues a chaque envoi : rien n'est recopie. */
    private final class Live implements ContainerData {
        @Override
        public int get(int index) {
            Bag bag = current();
            return switch (index) {
                case DATA_SIZE -> bag == null ? 0 : Math.min(bag.size(), Short.MAX_VALUE);
                case DATA_FIRST -> BagPanel.this.firstRow;
                case DATA_TABS -> BagPanel.this.bags.size();
                case DATA_SELECTED -> BagPanel.this.selected;
                case DATA_MULT -> bag == null ? 1 : Math.min(bag.multiplier(), 64);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
