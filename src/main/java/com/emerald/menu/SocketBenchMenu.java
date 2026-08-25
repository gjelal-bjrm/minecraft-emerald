package com.emerald.menu;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.ArtifactItem;
import com.emerald.artifact.Artifacts;
import com.emerald.block.ModBlocks;
import com.emerald.item.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * L'Etabli de Sertissage.
 *
 * Trois emplacements : la piece d'equipement, l'artefact, et le resultat.
 *
 * Le sertissage n'est pas une fusion : la piece ressort identique, avec un
 * artefact en plus. Si elle en portait deja un, celui-ci est DETRUIT -- on peut
 * changer d'avis, mais cela coute. C'est ce qui donne du poids au choix sans
 * jamais l'enfermer.
 */
public class SocketBenchMenu extends AbstractContainerMenu {

    public static final int SLOT_GEAR = 0;
    public static final int SLOT_ARTIFACT = 1;
    public static final int SLOT_RESULT = 2;

    private final ContainerLevelAccess access;
    private final Container inputs = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            SocketBenchMenu.this.slotsChanged(this);
        }
    };
    private final Container result = new SimpleContainer(1);

    public SocketBenchMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public SocketBenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(ModMenus.SOCKET_BENCH.get(), id);
        this.access = access;

        this.addSlot(new Slot(this.inputs, SLOT_GEAR, 27, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // toute piece capable d'accueillir au moins un artefact
                for (Artifact artifact : Artifact.values()) {
                    if (artifact.fits(stack)) {
                        return true;
                    }
                }
                return false;
            }
        });
        this.addSlot(new Slot(this.inputs, SLOT_ARTIFACT, 76, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ArtifactItem && Artifacts.of(stack) != null;
            }
        });
        this.addSlot(new Slot(this.result, SLOT_RESULT, 134, 47) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                SocketBenchMenu.this.inputs.setItem(SLOT_GEAR, ItemStack.EMPTY);
                SocketBenchMenu.this.inputs.setItem(SLOT_ARTIFACT, ItemStack.EMPTY);
                super.onTake(player, stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        this.result.setItem(0, socket(this.inputs.getItem(SLOT_GEAR),
                                      this.inputs.getItem(SLOT_ARTIFACT)));
        this.broadcastChanges();
    }

    /** Le resultat, ou une pile vide si la combinaison ne tient pas. */
    @Nullable
    private static ItemStack socket(ItemStack gear, ItemStack artifactStack) {
        Artifact artifact = Artifacts.of(artifactStack);
        if (gear.isEmpty() || artifact == null || !artifact.fits(gear)) {
            return ItemStack.EMPTY;
        }
        if (Artifacts.has(gear, artifact)) {
            return ItemStack.EMPTY;          // deja serti du meme : rien a faire
        }
        ItemStack out = gear.copy();
        Artifacts.set(out, artifact);
        return out;
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
        if (index <= SLOT_RESULT) {
            if (!this.moveItemStackTo(stack, SLOT_RESULT + 1, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(stack, moved);
        } else if (!this.moveItemStackTo(stack, SLOT_GEAR, SLOT_RESULT, false)) {
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
        this.access.execute((level, pos) -> this.clearContainer(player, this.inputs));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.SOCKET_BENCH.get());
    }
}
