package com.emerald.menu;

import com.emerald.menu.bag.BagMenu;
import com.emerald.menu.bag.BagPanel;
import com.emerald.menu.bag.Bags;
import com.emerald.menu.curio.CurioPanel;
import com.emerald.menu.curio.CurioRef;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * L'INVENTAIRE D'ARCENCIUM (cahier §111) : tout sur un ecran, a la place de E.
 *
 * « Il manque une interface centralisee a travers laquelle je peux tout faire sans
 * avoir a passer par l'interface de l'inventaire, puis la fermer pour ouvrir celle du
 * personnage. » A gauche les cases d'artefacts (Curios) ; au milieu l'inventaire du
 * jeu, tel quel -- le personnage, l'armure, la main gauche, la grille 2x2 --, plus une
 * poubelle ; a droite le sac porte (BagPanel), avec ses onglets et ses tris.
 *
 * LES INDEX DE L'INVENTAIRE DU JEU. 0 le resultat, 1-4 la grille, 5-8 l'armure, 9-35
 * l'inventaire, 36-44 la barre d'action, 45 la main gauche : ce que les mods et le jeu
 * supposent d'un inventaire. Puis 46 la poubelle, la fenetre des artefacts, et le sac.
 *
 * LES CASES D'ARTEFACTS sont une fenetre qui defile (CurioPanel) : ses cases lisent en
 * direct la case Curios de leur rang. Le nombre de cases d'un type change ecran ouvert
 * (une ceinture de Relics ajoute des charmes) sans rien casser : c'est ce qui plantait
 * la premiere version (cahier 111, I).
 *
 * LE MORPH GUN reste dans l'inventaire tant que l'ecran est ouvert (MorphGunKeeper
 * exempte ce menu, comme l'inventaire du jeu) : la grille, la poubelle et le sac le
 * refusent, comme le JET-Board -- leurs gardiens ne les y verraient pas.
 */
public class ArcInventoryMenu extends BagMenu {

    public static final Component TITLE = Component.translatable("container.emeraldweapons.arc_inventory");

    public static final int SLOT_RESULT = 0;
    public static final int SLOT_CRAFT = 1;
    public static final int SLOT_ARMOR = 5;
    public static final int SLOT_MAIN = 9;
    public static final int SLOT_HOTBAR = 36;
    public static final int SLOT_OFFHAND = 45;
    public static final int SLOT_TRASH = 46;
    public static final int SLOT_CURIOS = 47;

    /** Le corps de l'ecran : la texture de l'inventaire du jeu. */
    public static final int BODY_W = 176;
    public static final int BODY_H = 166;
    public static final int GAP = 2;
    /** La poubelle, sous la grille d'artisanat, dans le corps. */
    public static final int TRASH_X = 131;
    public static final int TRASH_Y = 62;

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ResourceLocation[] ARMOR_ICONS = {
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};

    private final Player owner;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 2, 2);
    private final ResultContainer resultSlots = new ResultContainer();
    private final SimpleContainer trash = new SimpleContainer(1);
    /** La fenetre des artefacts ; null sans Curios. */
    @Nullable
    private final CurioPanel curios;
    /**
     * La mise en page, la meme des deux cotes. Les artefacts a gauche, sur toute la hauteur ; le
     * corps et le sac a cote, centres en hauteur (bodyY).
     */
    public final int mainX;
    public final int bodyY;
    public final int bagX;
    public final int width;
    public final int height;

    /** Cote client : la liste des cases d'artefacts arrive avec l'ouverture. */
    public ArcInventoryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, inventory, readCurios(buf));
    }

    private ArcInventoryMenu(int id, Inventory inventory, List<CurioRef> curioLayout) {
        super(ModMenus.ARC_INVENTORY.get(), id);
        this.owner = inventory.player;
        boolean withCurios = ModList.get().isLoaded("curios");
        this.mainX = withCurios ? CurioPanel.WIDTH + GAP : 0;
        this.height = withCurios ? Math.max(CurioPanel.HEIGHT, BODY_H) : BODY_H;
        this.bodyY = (this.height - BODY_H) / 2;
        this.bagX = this.mainX + BODY_W + GAP;
        this.width = this.bagX + BagPanel.WIDTH;
        int y0 = this.bodyY;

        this.addSlot(new ResultSlot(this.owner, this.craftSlots, this.resultSlots, 0, this.mainX + 154, y0 + 28));
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                this.addSlot(new Slot(this.craftSlots, col + row * 2, this.mainX + 98 + col * 18, y0 + 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !Bags.forbidden(stack);
                    }
                });
            }
        }
        for (int i = 0; i < ARMOR.length; i++) {
            this.addSlot(new Armor(inventory, this.owner, ARMOR[i], 39 - i, this.mainX + 8, y0 + 8 + i * 18, ARMOR_ICONS[i]));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + (row + 1) * 9, this.mainX + 8 + col * 18, y0 + 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, this.mainX + 8 + col * 18, y0 + 142));
        }
        this.addSlot(new Slot(inventory, Inventory.SLOT_OFFHAND, this.mainX + 77, y0 + 62) {
            @Override
            public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
                ArcInventoryMenu.this.owner.onEquipItem(EquipmentSlot.OFFHAND, oldStack, newStack);
                super.setByPlayer(newStack, oldStack);
            }

            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });
        this.addSlot(new Slot(this.trash, 0, this.mainX + TRASH_X, y0 + TRASH_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return trashable(stack);
            }
        });
        if (withCurios) {
            this.curios = new CurioPanel(this, this.owner, curioLayout, this::addSlot, 0, 0);
            this.addDataSlots(this.curios.data());
        } else {
            this.curios = null;
        }
        this.addBag(this.owner, this.bagX, y0);
    }

    /** Ouvre l'inventaire d'Arcencium : le serveur decrit les cases d'artefacts du joueur. */
    public static void open(ServerPlayer player) {
        List<CurioRef> layout = CurioPanel.layout(player);
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) ->
                new ArcInventoryMenu(id, inventory, layout), TITLE), buf -> writeCurios(buf, layout));
    }

    /** Le menu d'un joueur, sans l'ouvrir : pour le banc d'essai. */
    static ArcInventoryMenu create(int id, ServerPlayer player) {
        return new ArcInventoryMenu(id, player.getInventory(), CurioPanel.layout(player));
    }

    private static void writeCurios(RegistryFriendlyByteBuf buf, List<CurioRef> curios) {
        buf.writeVarInt(curios.size());
        for (CurioRef ref : curios) {
            buf.writeUtf(ref.identifier(), 128);
            buf.writeVarInt(ref.index());
        }
    }

    private static List<CurioRef> readCurios(RegistryFriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 512);
        List<CurioRef> curios = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            curios.add(new CurioRef(buf.readUtf(128), buf.readVarInt()));
        }
        return curios;
    }

    /** La poubelle prend tout, sauf ce que les gardiens suivent et ce qui contient d'autres objets. */
    public static boolean trashable(ItemStack stack) {
        return !stack.isEmpty() && !Bags.forbidden(stack) && !Bags.isContainer(stack);
    }

    /** La fenetre des artefacts, ou null sans Curios. */
    @Nullable
    public CurioPanel curios() {
        return this.curios;
    }

    public boolean isCurio(int index) {
        return this.curios != null && this.curios.isWindowSlot(index);
    }

    public ItemStack trashed() {
        return this.trash.getItem(0);
    }

    @Override
    protected int inventoryStart() {
        return SLOT_MAIN;
    }

    @Override
    protected int inventoryEnd() {
        return SLOT_OFFHAND;
    }

    // ================================================================ envoi

    @Override
    public void broadcastChanges() {
        if (this.curios != null) {
            this.curios.refresh();                      // la liste du moment, avant de lire les cases
        }
        super.broadcastChanges();
    }

    @Override
    public void broadcastFullState() {
        if (this.curios != null) {
            this.curios.refresh();
        }
        super.broadcastFullState();
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        if (this.curios != null) {
            this.curios.opened();
        }
    }

    @Override
    protected boolean onButton(Player player, int id) {
        return this.curios != null && this.curios.button(id);
    }

    // ================================================================ artisanat

    @Override
    public void slotsChanged(Container container) {
        if (container == this.craftSlots && this.owner instanceof ServerPlayer served) {
            craft(served);
        }
    }

    /** Ce que fait l'inventaire du jeu (CraftingMenu.slotChangedCraftingGrid, protegee). */
    private void craft(ServerPlayer served) {
        ServerLevel level = served.serverLevel();
        CraftingInput input = this.craftSlots.asCraftInput();
        ItemStack made = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> found =
                level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (found.isPresent() && this.resultSlots.setRecipeUsed(level, served, found.get())) {
            ItemStack out = found.get().value().assemble(input, level.registryAccess());
            if (out.isItemEnabled(level.enabledFeatures())) {
                made = out;
            }
        }
        this.resultSlots.setItem(0, made);
        this.setRemoteSlot(SLOT_RESULT, made);
        served.connection.send(new ClientboundContainerSetSlotPacket(
                this.containerId, this.incrementStateId(), SLOT_RESULT, made));
    }

    // ================================================================ clics

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // LA POUBELLE PREND TOUT CE QU'ON LUI DONNE, et ce qu'elle tenait est detruit : le
        // jeu, lui, echangerait et rendrait l'objet precedent au curseur
        if (slotId == SLOT_TRASH && clickType == ClickType.PICKUP && !this.getCarried().isEmpty()) {
            ItemStack carried = this.getCarried();
            if (trashable(carried)) {
                this.trash.setItem(0, carried);
                this.setCarried(ItemStack.EMPTY);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slot.container != this.resultSlots && slot.container != this.trash && !isCurio(slot.index)
                && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && slot.container != this.trash && !isCurio(slot.index)
                && super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * Maj+clic. De l'inventaire : l'armure et la main gauche d'abord (comme le jeu), puis
     * une case d'artefact libre qui l'accepte (montree ou non), puis le sac, et sinon de
     * l'inventaire a la barre d'action et retour. De partout ailleurs (sac, armure,
     * artefacts, grille, poubelle) : vers l'inventaire.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (this.bag != null && this.bag.isBagSlot(index)) {
            return moveOutOfBag(slot);
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        EquipmentSlot equipment = player.getEquipmentSlotForItem(copy);
        boolean inInventory = index >= SLOT_MAIN && index < SLOT_OFFHAND;
        if (index == SLOT_RESULT) {
            if (!this.moveItemStackTo(stack, SLOT_MAIN, SLOT_OFFHAND, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, copy);
        } else if (!inInventory) {
            if (!this.moveItemStackTo(stack, SLOT_MAIN, SLOT_OFFHAND, false)) {
                return ItemStack.EMPTY;
            }
        } else if (equipment.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && !this.slots.get(8 - equipment.getIndex()).hasItem()) {
            int target = 8 - equipment.getIndex();
            if (!this.moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (equipment == EquipmentSlot.OFFHAND && !this.slots.get(SLOT_OFFHAND).hasItem()) {
            if (!this.moveItemStackTo(stack, SLOT_OFFHAND, SLOT_OFFHAND + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (this.curios != null && this.curios.isCurio(stack)
                && (player.level().isClientSide || this.curios.equip(stack))) {
            if (player.level().isClientSide) {
                return ItemStack.EMPTY;                 // le serveur equipe, ou range ailleurs
            }
            // equipe dans une case d'artefact
        } else {
            ItemStack bagged = moveIntoBag(player, slot);
            if (bagged != null) {
                return bagged;
            }
            if (index >= SLOT_MAIN && index < SLOT_HOTBAR) {
                if (!this.moveItemStackTo(stack, SLOT_HOTBAR, SLOT_OFFHAND, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= SLOT_HOTBAR && index < SLOT_OFFHAND) {
                if (!this.moveItemStackTo(stack, SLOT_MAIN, SLOT_HOTBAR, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, SLOT_MAIN, SLOT_OFFHAND, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, copy);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        if (index == SLOT_RESULT) {
            player.drop(stack, false);
        }
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.resultSlots.clearContent();
        if (!player.level().isClientSide) {
            this.clearContainer(player, this.craftSlots);
            this.trash.clearContent();                  // ce qui est dans la poubelle est detruit
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Une case d'armure : ArmorSlot, reservee a son paquet dans le jeu. */
    private static final class Armor extends Slot {

        private final LivingEntity owner;
        private final EquipmentSlot equipment;
        private final ResourceLocation icon;

        Armor(Container container, LivingEntity owner, EquipmentSlot equipment, int index, int x, int y,
              ResourceLocation icon) {
            super(container, index, x, y);
            this.owner = owner;
            this.equipment = equipment;
            this.icon = icon;
        }

        @Override
        public void setByPlayer(ItemStack newStack, ItemStack oldStack) {
            this.owner.onEquipItem(this.equipment, oldStack, newStack);
            super.setByPlayer(newStack, oldStack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.canEquip(this.equipment, this.owner);
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack worn = this.getItem();
            if (!worn.isEmpty() && !player.isCreative()
                    && EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                return false;
            }
            return super.mayPickup(player);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, this.icon);
        }
    }
}
