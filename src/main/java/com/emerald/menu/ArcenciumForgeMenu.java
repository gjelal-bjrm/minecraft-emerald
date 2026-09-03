package com.emerald.menu;

import com.emerald.artifact.Artifact;
import com.emerald.item.ModItems;
import com.emerald.item.Upgrade;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * La Forge d'Arcencium : l'amelioration +1 a +10, et rien d'autre.
 *
 * L'Etabli de Sertissage savait deja forger, mais en cachant tout : une
 * pierre dans la case d'artefact, le metal pris dans le sac sans qu'on sache
 * lequel ni combien, et le tirage au moment de prendre la piece. Ici la piece
 * est posee, l'echelle entiere des dix crans s'affiche avec ce qu'on possede,
 * et un bouton tente le cran suivant. Le metal et la pierre restent dans le
 * sac : on n'a pas a deplacer neuf lingots pour une tentative.
 *
 * Le bouton passe par le mecanisme vanilla des menus (clickMenuButton) : pas
 * de paquet a ecrire, et le serveur revalide tout -- la piece, le cran, la
 * pierre, le metal -- avant de tirer.
 */
public class ArcenciumForgeMenu extends AbstractContainerMenu {

    public static final int SLOT_GEAR = 0;
    /** Le bouton « Forger ». */
    public static final int BUTTON_FORGE = 0;

    /** Ce que l'ecran doit savoir du dernier coup : rien, gagne, perdu, refuse. */
    public static final int RESULT_NONE = 0;
    public static final int RESULT_WON = 1;
    public static final int RESULT_KEPT = 2;
    public static final int RESULT_MISSING = 3;
    public static final int DATA_RESULT = 0;
    public static final int DATA_LEVEL = 1;

    private final ContainerLevelAccess access;
    private final Player owner;
    private final SimpleContainerData data = new SimpleContainerData(2);
    private final Container input = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            ArcenciumForgeMenu.this.slotsChanged(this);
        }
    };

    public ArcenciumForgeMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public ArcenciumForgeMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.ARCENCIUM_FORGE.get(), id);
        this.access = access;
        this.owner = inventory.player;
        this.addSlot(new Slot(this.input, SLOT_GEAR, 9, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isGear(stack);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 158 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 216));
        }
        this.addDataSlots(this.data);
        // LA PIECE EN MAIN MONTE D'ELLE-MEME SUR LA FORGE.
        //
        // On vient a la forge avec l'arme qu'on veut monter ; la poser a la
        // main serait un clic de plus pour rien. Cote serveur seulement : le
        // client recoit le contenu a l'ouverture.
        if (!inventory.player.level().isClientSide) {
            ItemStack held = inventory.player.getMainHandItem();
            if (isGear(held)) {
                this.input.setItem(SLOT_GEAR, held.copy());
                inventory.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        ItemStack.EMPTY);
            }
        }
    }

    /** Toute piece capable d'accueillir un artefact ou une rune : ce que l'etabli accepte. */
    public static boolean isGear(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Artifact artifact : Artifact.values()) {
            if (artifact.fits(stack)) {
                return true;
            }
        }
        for (com.emerald.rune.RuneFamily family : com.emerald.rune.RuneFamily.values()) {
            if (family.accepts(stack)) {
                return true;
            }
        }
        return false;
    }

    public ItemStack gear() {
        return this.input.getItem(SLOT_GEAR);
    }

    public int lastResult() {
        return this.data.get(DATA_RESULT);
    }

    public int lastLevel() {
        return this.data.get(DATA_LEVEL);
    }

    /** Combien de pierres de forge le joueur porte. */
    public static int stones(Player player) {
        return Upgrade.carried(player, new Upgrade.Cost(ModItems.FORGE_STONE.get(), 1));
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        this.data.set(DATA_RESULT, RESULT_NONE);        // une autre piece, une autre histoire
        this.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_FORGE || player.level().isClientSide) {
            return false;
        }
        ItemStack gear = gear();
        int before = Upgrade.of(gear);
        if (!isGear(gear) || before >= Upgrade.MAX) {
            return false;
        }
        // ON PAIE D'ABORD, ON TIRE ENSUITE : pierre puis metal. Un tirage qui
        // se solderait par un echec de paiement laisserait la piece amelioree
        // sans que rien n'ait ete depense.
        if (stones(player) < 1 || !Upgrade.affordable(player, gear)) {
            this.data.set(DATA_RESULT, RESULT_MISSING);
            this.data.set(DATA_LEVEL, before);
            player.level().playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.VILLAGER_NO,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
            this.broadcastChanges();
            return true;
        }
        takeStone(player);
        if (!Upgrade.charge(player, gear)) {
            return false;
        }
        int after = Upgrade.attempt(before, player.level().random);
        Upgrade.set(gear, after);
        this.input.setItem(SLOT_GEAR, gear);            // le nom a change : on le re-annonce
        boolean won = after > before;
        if (won) {
            com.emerald.util.Celebration.upgrade(player, after);
        }
        this.data.set(DATA_RESULT, won ? RESULT_WON : RESULT_KEPT);
        this.data.set(DATA_LEVEL, after);
        player.displayClientMessage(Component.translatable(
                        won ? "socket.emeraldweapons.upgrade.won"
                                : "socket.emeraldweapons.upgrade.kept", after)
                .withStyle(won ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.GRAY), false);
        player.level().playSound(null, player.blockPosition(),
                won ? net.minecraft.sounds.SoundEvents.ANVIL_USE
                        : net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.9F,
                won ? 0.9F + after * 0.06F : 0.6F);
        this.broadcastChanges();
        return true;
    }

    private static void takeStone(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack held = inventory.getItem(slot);
            if (held.is(ModItems.FORGE_STONE.get())) {
                held.shrink(1);
                return;
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return moved;
        }
        ItemStack stack = slot.getItem();
        moved = stack.copy();
        int playerStart = 1;
        int playerEnd = this.slots.size();
        if (index == SLOT_GEAR) {
            if (!this.moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (isGear(stack) && !this.slots.get(SLOT_GEAR).hasItem()) {
            if (!this.moveItemStackTo(stack, SLOT_GEAR, SLOT_GEAR + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
    public void removed(Player player) {
        super.removed(player);
        this.access.execute((level, pos) -> this.clearContainer(player, this.input));
        if (this.access == ContainerLevelAccess.NULL) {
            this.clearContainer(player, this.input);   // ouvert par commande : on rend quand meme
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return this.access.evaluate((level, pos) ->
                level.getBlockState(pos).is(com.emerald.block.ModBlocks.ARCENCIUM_FORGE.get())
                        && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }
}
