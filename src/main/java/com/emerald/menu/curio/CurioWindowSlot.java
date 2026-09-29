package com.emerald.menu.curio;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Une case de la fenetre des artefacts (CurioPanel). Cote serveur, elle passe tout a la case Curios
 * qu'elle montre en ce moment -- lire, poser, prendre, valider -- et se vide si cette case n'existe
 * plus. Cote client, elle garde ce que le serveur y envoie, et ne demande a la case Curios du client
 * que son icone et ce qu'on peut y poser : elle n'ecrit jamais dans la copie des artefacts du client.
 */
public final class CurioWindowSlot extends Slot {

    private final CurioPanel panel;
    private final int windowSlot;

    CurioWindowSlot(CurioPanel panel, Container mirror, int windowSlot, int x, int y) {
        super(mirror, windowSlot, x, y);
        this.panel = panel;
        this.windowSlot = windowSlot;
    }

    public int windowSlot() {
        return this.windowSlot;
    }

    @Nullable
    private Slot target() {
        return this.panel.target(this.windowSlot);
    }

    @Override
    public boolean isActive() {
        return this.panel.ref(this.windowSlot) != null;
    }

    @Override
    public ItemStack getItem() {
        if (!this.panel.server()) {
            return super.getItem();
        }
        Slot target = target();
        return target == null ? ItemStack.EMPTY : target.getItem();
    }

    @Override
    public void set(ItemStack stack) {
        if (!this.panel.server()) {
            super.set(stack);
            return;
        }
        Slot target = target();
        if (target != null) {
            target.set(stack);
        } else {
            this.panel.giveBack(stack);
        }
    }

    @Override
    public ItemStack remove(int amount) {
        if (!this.panel.server()) {
            return super.remove(amount);
        }
        Slot target = target();
        return target == null ? ItemStack.EMPTY : target.remove(amount);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        Slot target = target();
        return target != null && target.mayPlace(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        if (!this.panel.server()) {
            return true;                                // le serveur tranchera (objet maudit)
        }
        Slot target = target();
        return target != null && target.mayPickup(player);
    }

    @Override
    public boolean allowModification(Player player) {
        if (!this.panel.server()) {
            return super.allowModification(player);
        }
        Slot target = target();
        return target != null && target.allowModification(player);
    }

    @Override
    public int getMaxStackSize() {
        Slot target = target();
        return target == null ? 64 : target.getMaxStackSize();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        Slot target = target();
        return target == null ? 0 : target.getMaxStackSize(stack);
    }

    @Override
    public void setChanged() {
        if (!this.panel.server()) {
            super.setChanged();
            return;
        }
        Slot target = target();
        if (target != null) {
            target.setChanged();
        }
    }

    @Nullable
    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        Slot target = target();
        return target == null ? null : target.getNoItemIcon();
    }
}
