package com.emerald.menu.bag;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * RANGER D'UN CLIC (cahier 112). Alt+clic sur une pile l'envoie dans le sac porte ; Alt+Maj+clic,
 * toutes les piles du meme objet de ce conteneur. Sur tout ecran de conteneur : nos ecrans (le sac
 * montre), mais aussi un coffre, un tonneau, une boite de Shulker, le coffre d'un autre mod (le
 * premier sac du joueur, dans l'ordre des onglets). Le client envoie la case (QuickStashPayload,
 * client/QuickStashClient) ; tout se decide ici.
 *
 * LE GESTE DE LA MAIN, fait par le serveur : la pile est prise par un clic du menu lui-meme -- il
 * applique ses propres regles, comme si le joueur la prenait --, posee dans le sac, et ce qui n'y
 * entre pas retourne dans sa case, ou dans l'inventaire. Le curseur est vide avant et apres : rien
 * n'est cree, rien n'est perdu. Les cases de resultat (artisanat, four, marchand) ne sont pas
 * prises : on ne fabrique pas d'un Alt+clic. L'ecran d'un sac de Sophisticated Backpacks est laisse a
 * lui-meme : le sac qu'il montre est peut-etre celui qu'on remplirait.
 */
public final class QuickStash {

    /** Les menus des sacs de Sophisticated Backpacks : le client n'y envoie rien non plus. */
    public static final String BACKPACK_MENUS = "net.p3pp3rf1y.sophisticatedbackpacks.";

    private QuickStash() {
    }

    /** Alt+clic sur cette case du menu ouvert ; bulk : Alt+Maj+clic. */
    public static void stash(ServerPlayer player, int containerId, int index, boolean bulk) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId || player.isSpectator() || index < 0 || index >= menu.slots.size()
                || !menu.getCarried().isEmpty() || !menu.stillValid(player)
                || menu.getClass().getName().startsWith(BACKPACK_MENUS)) {
            return;
        }
        Slot clicked = menu.getSlot(index);
        if (!takeable(menu, clicked, player)) {
            return;
        }
        Target target = target(menu, player);
        if (target == null) {
            say(player, "gui.emeraldweapons.bag.none");
            return;
        }
        ItemStack model = clicked.getItem().copy();
        if (Bags.forbidden(model) || clicked.getItem() == target.stack() || !target.accepts(model)) {
            say(player, "gui.emeraldweapons.stash.refused");
            return;
        }
        boolean full = false;
        for (int at : bulk ? sameKind(menu, clicked, model) : List.of(index)) {
            Slot slot = menu.getSlot(at);
            if (!takeable(menu, slot, player) || slot.getItem() == target.stack()
                    || !ItemStack.isSameItemSameComponents(slot.getItem(), model)) {
                continue;
            }
            if (!moveOne(menu, at, target, player)) {
                full = true;                            // le sac n'a pas tout pris : on s'arrete la
                break;
            }
        }
        if (full) {
            say(player, "gui.emeraldweapons.stash.full");
        }
        menu.broadcastChanges();
    }

    /** Une case : prise d'un clic, posee dans le sac, le reste rendu. Vrai si tout est entre. */
    private static boolean moveOne(AbstractContainerMenu menu, int index, Target target, Player player) {
        menu.clicked(index, 0, ClickType.PICKUP, player);
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return true;                                // le menu n'a rien donne : rien a ranger
        }
        ItemStack rest = target.insert(carried);
        menu.setCarried(rest);
        boolean all = rest.isEmpty();
        giveBack(menu, index, player);
        return all;
    }

    /**
     * Ce qui reste au curseur retourne dans sa case si elle le prend, sinon dans l'inventaire (ou a
     * terre s'il est plein, comme le jeu) : jamais perdu.
     */
    public static void giveBack(AbstractContainerMenu menu, int index, Player player) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        ItemStack here = menu.getSlot(index).getItem();
        if (here.isEmpty() || ItemStack.isSameItemSameComponents(here, carried)) {
            menu.clicked(index, 0, ClickType.PICKUP, player);
        }
        ItemStack left = menu.getCarried();
        if (!left.isEmpty()) {
            menu.setCarried(ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(left);
        }
    }

    /** Une case dont on prendrait la pile a la main -- pas un resultat, pas le sac montre lui-meme. */
    private static boolean takeable(AbstractContainerMenu menu, Slot slot, Player player) {
        return slot.hasItem() && !isResult(slot) && slot.mayPickup(player)
                && (!(menu instanceof BagMenu bagged) || bagged.stashable(slot.index));
    }

    private static boolean isResult(Slot slot) {
        return slot instanceof ResultSlot || slot instanceof FurnaceResultSlot || slot instanceof MerchantResultSlot
                || slot.container instanceof ResultContainer;
    }

    /** Alt+Maj+clic : la case cliquee, puis celles du meme conteneur qui tiennent le meme objet. */
    private static List<Integer> sameKind(AbstractContainerMenu menu, Slot clicked, ItemStack model) {
        Object group = group(clicked);
        List<Integer> out = new ArrayList<>();
        out.add(clicked.index);
        for (Slot slot : menu.slots) {
            if (slot.index != clicked.index && group.equals(group(slot))
                    && ItemStack.isSameItemSameComponents(slot.getItem(), model)) {
                out.add(slot.index);
            }
        }
        return out;
    }

    /**
     * Le conteneur d'une case. Dans l'inventaire du joueur, la barre d'action et le reste vont a
     * part (ce qu'on garde en main n'est pas range avec le butin) ; l'armure et la main gauche,
     * seules.
     */
    private static Object group(Slot slot) {
        if (slot instanceof SlotItemHandler handled) {
            return handled.getItemHandler();
        }
        if (slot.container instanceof Inventory) {
            int at = slot.getContainerSlot();
            if (at < Inventory.getSelectionSize()) {
                return Section.HOTBAR;
            }
            return at < Inventory.INVENTORY_SIZE ? Section.MAIN : slot;
        }
        return slot.container;
    }

    private enum Section { HOTBAR, MAIN }

    /** Un mot au joueur, au-dessus de la barre d'action. */
    public static void say(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }

    // ================================================================ ou va la pile

    /** Le sac montre par nos ecrans ; ailleurs, le premier sac du joueur. Null sans sac. */
    @Nullable
    private static Target target(AbstractContainerMenu menu, Player player) {
        if (menu instanceof BagMenu bagged && bagged.bag() != null) {
            BagPanel panel = bagged.bag();
            panel.begin();
            try {
                Bag shown = panel.shown();
                return shown == null ? null : new PanelTarget(panel, shown.stack());
            } finally {
                panel.end();
            }
        }
        List<Bag> bags = Bags.scan(player, 1);
        return bags.isEmpty() ? null : new BagTarget(bags.get(0));
    }

    private interface Target {
        /** La pile du sac : il ne se range pas en lui-meme. */
        ItemStack stack();

        /** Une case au moins peut-elle prendre cet objet ? */
        boolean accepts(ItemStack stack);

        /** Range la pile ; rend ce qui n'est pas entre. */
        ItemStack insert(ItemStack stack);
    }

    /** Nos ecrans : par le panneau, qui relit et ecrit sa fenetre autour de chaque geste. */
    private record PanelTarget(BagPanel panel, ItemStack stack) implements Target {
        @Override
        public boolean accepts(ItemStack stack) {
            this.panel.begin();
            try {
                Bag shown = this.panel.shown();
                return shown != null && Bags.accepts(shown, stack);
            } finally {
                this.panel.end();
            }
        }

        @Override
        public ItemStack insert(ItemStack stack) {
            this.panel.begin();
            try {
                return this.panel.insert(stack);
            } finally {
                this.panel.end();
            }
        }
    }

    /** Ailleurs : le sac lui-meme, qu'aucune fenetre ne recopie. */
    private record BagTarget(Bag bag) implements Target {
        @Override
        public ItemStack stack() {
            return this.bag.stack();
        }

        @Override
        public boolean accepts(ItemStack stack) {
            return Bags.accepts(this.bag, stack);
        }

        @Override
        public ItemStack insert(ItemStack stack) {
            return Bags.insert(this.bag, stack);
        }
    }
}
