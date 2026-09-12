package com.emerald.item;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Les cases Curios du joueur. Classe A PART pour que Stash ne charge jamais
 * l'API de Curios quand le mod n'est pas la : on n'y entre qu'apres avoir
 * demande a ModList.
 */
public final class CuriosStash {

    private CuriosStash() {
    }

    public static List<ItemStack> worn(Player player) {
        List<ItemStack> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            IItemHandlerModifiable equipped = curios.getEquippedCurios();
            for (int slot = 0; slot < equipped.getSlots(); slot++) {
                out.add(equipped.getStackInSlot(slot));
            }
        });
        return out;
    }

    /** Pose l'objet dans la premiere case libre de ce type. Faux si aucune. */
    public static boolean equip(Player player, String slotType, ItemStack stack) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(curios -> curios.getStacksHandler(slotType).map(handler -> {
                    var stacks = handler.getStacks();
                    for (int slot = 0; slot < stacks.getSlots(); slot++) {
                        if (stacks.getStackInSlot(slot).isEmpty()) {
                            curios.setEquippedCurio(slotType, slot, stack);
                            return true;
                        }
                    }
                    return false;
                }))
                .orElse(false);
    }
}
