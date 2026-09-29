package com.emerald.menu;

import com.emerald.item.ModItems;
import com.emerald.item.Upgrade;
import com.emerald.specialization.Specialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import com.emerald.menu.bag.BagMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * L'Autel de Specialisation : aucune case a remplir, un seul bouton.
 *
 * Les plumes restent dans le sac -- l'inventaire est affiche pour qu'on les
 * voie -- et le bouton passe par clickMenuButton, le mecanisme vanilla des
 * menus : le serveur fait la tentative avec Specialization.tryUpgrade, la
 * MEME routine que la plume en clic droit. Une seule regle, deux portes.
 * Le verdict revient par ContainerData.
 *
 * LE SAC A COTE (cahier §111), et les plumes COMPTEES PAR LE SERVEUR, sac compris :
 * le client ne voit pas le contenu du sac, et l'ecran affichait 0 plume.
 */
public class SpecializationAltarMenu extends BagMenu {

    public static final int BUTTON_ATTEMPT = 0;
    public static final int RESULT_NONE = 0;
    public static final int RESULT_WON = 1;
    public static final int RESULT_KEPT = 2;
    public static final int RESULT_MISSING = 3;
    public static final int RESULT_MAX = 4;
    public static final int DATA_RESULT = 0;
    public static final int DATA_LEVEL = 1;
    /** Les plumes portees, sac compris, dans une seconde donnee du menu. */
    public static final int DATA_FEATHERS = 0;
    /** Le corps de l'ecran ; le panneau du sac est a sa droite. */
    public static final int BODY_W = 176;

    private final ContainerLevelAccess access;
    private final SimpleContainerData data = new SimpleContainerData(2);
    private final ContainerData reserves;

    public SpecializationAltarMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public SpecializationAltarMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.SPECIALIZATION_ALTAR.get(), id);
        this.access = access;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 158 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 216));
        }
        this.addDataSlots(this.data);
        Player owner = inventory.player;
        this.reserves = owner.level().isClientSide ? new SimpleContainerData(1) : new ContainerData() {
            @Override
            public int get(int index) {
                return Math.min(Short.MAX_VALUE, feathers(owner));
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        this.addDataSlots(this.reserves);
        this.addBag(owner, BODY_W + 2, 0);
    }

    /** Les plumes portees, sac compris (compte du serveur). */
    public int feathersCarried() {
        return this.reserves.get(DATA_FEATHERS);
    }

    @Override
    protected int inventoryStart() {
        return 0;
    }

    @Override
    protected int inventoryEnd() {
        return 36;
    }

    public int lastResult() {
        return this.data.get(DATA_RESULT);
    }

    public int lastLevel() {
        return this.data.get(DATA_LEVEL);
    }

    /** Combien de Plumes d'Arcencium le joueur porte. */
    public static int feathers(Player player) {
        return Upgrade.carried(player, new Upgrade.Cost(ModItems.ARCENCIUM_FEATHER.get(), 1));
    }

    @Override
    protected boolean onButton(Player player, int id) {
        if (id != BUTTON_ATTEMPT || !(player instanceof ServerPlayer server)) {
            return false;
        }
        Specialization.Attempt attempt = Specialization.tryUpgrade(server);
        int result = switch (attempt) {
            case SUCCESS -> RESULT_WON;
            case FAILURE -> RESULT_KEPT;
            case NOT_ENOUGH -> RESULT_MISSING;
            case MAX -> RESULT_MAX;
        };
        this.data.set(DATA_RESULT, result);
        this.data.set(DATA_LEVEL, Specialization.level(server));
        this.broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // rien a deplacer d'un cote a l'autre : l'autel n'a pas de case
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (this.bag != null && this.bag.isBagSlot(index)) {
            return moveOutOfBag(slot);
        }
        ItemStack bagged = moveIntoBag(player, slot);
        if (bagged != null) {
            return bagged;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        int hotbarStart = 27;
        boolean toHotbar = index < hotbarStart;
        if (!this.moveItemStackTo(stack, toHotbar ? hotbarStart : 0, toHotbar ? 36 : hotbarStart, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) ->
                level.getBlockState(pos).is(com.emerald.block.ModBlocks.SPECIALIZATION_ALTAR.get())
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }
}
