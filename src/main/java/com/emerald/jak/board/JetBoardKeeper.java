package com.emerald.jak.board;

import com.emerald.haven.Haven;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Le JET-Board n'est jamais perdu (cahier §100). C'est l'achat qui fait le proprietaire
 * (JetBoard.owns) : toutes les secondes, dans Haven, celui qui l'a achete et n'a plus sa planche
 * nulle part -- ni dans sa case, ni dans son inventaire, ni dans un sac porte, ni dans la grille
 * d'artisanat, ni au bout de sa souris -- la retrouve dans sa case, ou dans son inventaire si elle
 * est prise. Sa case est gardee a la mort (drop_rule ALWAYS_KEEP) ; une planche jetee disparait au
 * sol (JetBoardItem) et revient ici.
 *
 * LE SAC COMPTE (cahier §111) : l'ecran de Sophisticated Backpacks accepte la planche, et le
 * gardien, qui ne regardait pas dedans, en donnait une seconde a chaque passage.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class JetBoardKeeper {

    private JetBoardKeeper() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0 && Haven.is(player.level())) {
            guard(player);
        }
    }

    /** Rend sa planche a un proprietaire qui ne l'a plus ; ne fait rien sinon. */
    public static void guard(ServerPlayer player) {
        if (!player.isAlive() || !JetBoard.owns(player.getUUID()) || has(player)) {
            return;
        }
        boolean curios = ModList.get().isLoaded("curios");
        if (!(curios && JetBoardCurios.equip(player))) {
            player.getInventory().add(new ItemStack(ModItems.JET_BOARD.get()));
        }
    }

    private static boolean has(ServerPlayer player) {
        if (player.getInventory().contains(stack -> stack.is(ModItems.JET_BOARD.get()))
                || player.containerMenu.getCarried().is(ModItems.JET_BOARD.get())) {
            return true;
        }
        for (ItemStack stack : player.inventoryMenu.getCraftSlots().getItems()) {
            if (stack.is(ModItems.JET_BOARD.get())) {
                return true;
            }
        }
        for (net.neoforged.neoforge.items.IItemHandler bag : com.emerald.item.Stash.bags(player)) {
            for (int slot = 0; slot < bag.getSlots(); slot++) {
                if (bag.getStackInSlot(slot).is(ModItems.JET_BOARD.get())) {
                    return true;
                }
            }
        }
        return ModList.get().isLoaded("curios") && JetBoardCurios.worn(player);
    }
}
