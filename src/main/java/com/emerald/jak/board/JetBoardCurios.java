package com.emerald.jak.board;

import com.emerald.item.CuriosStash;
import com.emerald.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * La case du JET-Board chez Curios. Classe A PART, comme CuriosStash : on n'y entre qu'apres avoir
 * demande a ModList, et rien d'autre ne charge l'API de Curios quand le mod n'est pas la.
 */
final class JetBoardCurios {

    private JetBoardCurios() {
    }

    /** La planche est dans sa case. */
    static boolean equipped(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(curios -> curios.getStacksHandler(JetBoard.SLOT))
                .map(handler -> {
                    for (int slot = 0; slot < handler.getStacks().getSlots(); slot++) {
                        if (handler.getStacks().getStackInSlot(slot).is(ModItems.JET_BOARD.get())) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    /** La planche est quelque part dans les cases Curios. */
    static boolean worn(Player player) {
        for (ItemStack stack : CuriosStash.worn(player)) {
            if (stack.is(ModItems.JET_BOARD.get())) {
                return true;
            }
        }
        return false;
    }

    /** Pose une planche dans sa case, si elle est libre. */
    static boolean equip(Player player) {
        return CuriosStash.equip(player, JetBoard.SLOT, new ItemStack(ModItems.JET_BOARD.get()));
    }
}
