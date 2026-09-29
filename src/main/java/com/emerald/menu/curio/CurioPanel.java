package com.emerald.menu.curio;

import com.emerald.network.CurioLayoutPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LE PANNEAU DES ARTEFACTS de l'inventaire d'Arcencium : une fenetre de deux colonnes sur douze
 * rangees, qui defile, sur toutes les cases Curios du joueur (cahier 111, I).
 *
 * LES CASES SONT LUES EN DIRECT. La premiere version faisait une case Curios par case du joueur, a
 * l'ouverture. Une ceinture de Relics ajoute des cases de charme ; retiree ecran ouvert, la case
 * disparue restait dans le menu, et sa lecture a plante le serveur (« Slot 1 not in valid range »,
 * 29 sept., la Warp Drive posee a la place d'une ceinture de Relics). Ici, les cases du menu sont
 * fixes, et chacune designe, a chaque lecture, la case Curios de son rang dans la liste du moment :
 * une case qui disparait se vide, une case qui apparait se montre, sans rouvrir l'ecran.
 *
 * Le serveur relit la liste a chaque envoi du menu et l'envoie au client quand elle change
 * (CurioLayoutPayload) ; la rangee montree passe par les donnees du menu. Cote client, les cases
 * gardent ce que le serveur y envoie (un miroir), et ne demandent a Curios que l'icone et ce qui
 * s'y pose.
 */
public final class CurioPanel {

    public static final int COLS = 2;
    public static final int ROWS = 12;
    public static final int SIZE = COLS * ROWS;

    /** Le panneau a l'ecran, et la premiere case dans le panneau. */
    public static final int WIDTH = 56;
    public static final int HEIGHT = 226;
    public static final int SLOTS_X = 6;
    public static final int SLOTS_Y = 6;

    public static final int BUTTON_ROW = 5000;             // + la premiere rangee a montrer
    public static final int DATA_FIRST = 0;
    public static final int DATA_COUNT = 1;
    private static final int MAX_REFS = 512;

    private final AbstractContainerMenu menu;
    private final Player player;
    private final boolean server;
    private final int firstSlot;
    private final SimpleContainer mirror = new SimpleContainer(SIZE);
    private final ContainerData data;
    private final Map<CurioRef, Slot> targets = new HashMap<>();
    /** L'origine du panneau dans l'image de l'ecran. */
    public final int x;
    public final int y;
    private List<CurioRef> refs;
    private List<CurioRef> sent;
    private int firstRow;
    private boolean live;

    public CurioPanel(AbstractContainerMenu menu, Player player, List<CurioRef> initial, Consumer<Slot> add,
                      int x, int y) {
        this.menu = menu;
        this.player = player;
        this.server = !player.level().isClientSide;
        this.x = x;
        this.y = y;
        this.refs = cap(initial);
        this.sent = this.refs;
        this.data = this.server ? new Live() : new SimpleContainerData(2);
        this.firstSlot = menu.slots.size();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                add.accept(new CurioWindowSlot(this, this.mirror, col + row * COLS,
                        x + SLOTS_X + col * 18, y + SLOTS_Y + row * 18));
            }
        }
    }

    /** Les cases d'artefacts du joueur, telles qu'elles sont maintenant (vide sans Curios). */
    public static List<CurioRef> layout(Player player) {
        return ModList.get().isLoaded("curios") ? cap(CuriosSlots.layout(player)) : List.of();
    }

    private static List<CurioRef> cap(List<CurioRef> refs) {
        return List.copyOf(refs.size() > MAX_REFS ? refs.subList(0, MAX_REFS) : refs);
    }

    public ContainerData data() {
        return this.data;
    }

    boolean server() {
        return this.server;
    }

    // ================================================================ lecture (deux cotes)

    public List<CurioRef> refs() {
        return this.refs;
    }

    public int firstSlot() {
        return this.firstSlot;
    }

    public boolean isWindowSlot(int menuIndex) {
        return menuIndex >= this.firstSlot && menuIndex < this.firstSlot + SIZE;
    }

    public int firstRow() {
        return this.data.get(DATA_FIRST);
    }

    public int rows() {
        return (this.refs.size() + COLS - 1) / COLS;
    }

    public int maxFirstRow() {
        return Math.max(0, rows() - ROWS);
    }

    /** La case d'artefact montree par cette case de la fenetre, ou null. */
    @Nullable
    public CurioRef ref(int windowSlot) {
        int at = firstRow() * COLS + windowSlot;
        return at >= 0 && at < this.refs.size() ? this.refs.get(at) : null;
    }

    /** La case de Curios derriere cette case de la fenetre, si elle existe encore. */
    @Nullable
    Slot target(int windowSlot) {
        CurioRef ref = ref(windowSlot);
        return ref == null ? null : targetFor(ref);
    }

    @Nullable
    private Slot targetFor(CurioRef ref) {
        if (!ModList.get().isLoaded("curios")) {
            return null;
        }
        Slot slot = this.targets.get(ref);
        if (slot == null) {
            slot = CuriosSlots.slot(this.player, ref);
            if (slot == null) {
                return null;
            }
            this.targets.put(ref, slot);
        }
        return valid(slot) ? slot : null;
    }

    /** UNE CASE DISPARUE NE SE LIT PLUS : c'est la lecture de celle-la qui plantait. */
    private static boolean valid(Slot slot) {
        return !(slot instanceof SlotItemHandler handled)
                || handled.getContainerSlot() < handled.getItemHandler().getSlots();
    }

    /** Client : la liste recue du serveur. */
    public void setRefs(List<CurioRef> refs) {
        this.refs = cap(refs);
        this.targets.clear();
    }

    /** Cet objet va-t-il dans une case d'artefact ? (Pour que le client ne predise rien.) */
    public boolean isCurio(ItemStack stack) {
        return ModList.get().isLoaded("curios") && CuriosSlots.isCurio(this.player, stack);
    }

    // ================================================================ serveur

    /** A chaque envoi du menu : la liste du moment ; au client si elle a change. */
    public void refresh() {
        if (!this.server) {
            return;
        }
        List<CurioRef> now = layout(this.player);
        if (!now.equals(this.refs)) {
            this.refs = now;
            this.targets.clear();
            this.firstRow = Mth.clamp(this.firstRow, 0, maxFirstRow());
        }
        if (this.live && !this.refs.equals(this.sent) && this.player instanceof ServerPlayer served) {
            this.sent = this.refs;
            PacketDistributor.sendToPlayer(served, new CurioLayoutPayload(this.menu.containerId, this.refs));
        }
    }

    /** Le menu est chez le client : les changements de liste lui seront envoyes. */
    public void opened() {
        this.live = true;
        refresh();
    }

    /** Le defilement. Faux pour un autre bouton. */
    public boolean button(int id) {
        if (id < BUTTON_ROW || id >= BUTTON_ROW + MAX_REFS) {
            return false;
        }
        if (this.server) {
            this.firstRow = Mth.clamp(id - BUTTON_ROW, 0, maxFirstRow());
        }
        return true;
    }

    /**
     * Pose cette pile dans la premiere case d'artefact libre qui l'accepte, qu'elle soit montree ou
     * non (Maj+clic). Serveur seulement ; vrai si quelque chose a ete pose.
     */
    public boolean equip(ItemStack stack) {
        if (!this.server || stack.isEmpty()) {
            return false;
        }
        for (CurioRef ref : this.refs) {
            Slot slot = targetFor(ref);
            if (slot == null || slot.hasItem() || !slot.mayPlace(stack)) {
                continue;
            }
            int count = Math.min(stack.getCount(), slot.getMaxStackSize(stack));
            if (count <= 0) {
                continue;
            }
            slot.setByPlayer(stack.split(count));
            return true;
        }
        return false;
    }

    /** Une pile posee sur une case qui vient de disparaitre : rendue, jamais perdue. */
    void giveBack(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.player.getInventory().placeItemBackInInventory(stack);
        }
    }

    // ================================================================ banc

    /** Pour le banc : des cases en plus d'un type, comme une ceinture de Relics pour les charmes. */
    public static void addTestSlots(Player player, String type, net.minecraft.resources.ResourceLocation id, int amount) {
        CuriosSlots.addSlots(player, type, id, amount);
    }

    public static void removeTestSlots(Player player, String type, net.minecraft.resources.ResourceLocation id) {
        CuriosSlots.removeSlots(player, type, id);
    }

    /** Le nombre de cases de ce type, tel que Curios le tient. */
    public static int slotCount(Player player, String type) {
        return ModList.get().isLoaded("curios") ? CuriosSlots.size(player, type) : 0;
    }

    /** Les donnees du panneau, lues a chaque envoi. */
    private final class Live implements ContainerData {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FIRST -> CurioPanel.this.firstRow;
                case DATA_COUNT -> Math.min(CurioPanel.this.refs.size(), Short.MAX_VALUE);
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
