package com.emerald.menu.curio;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.common.inventory.CurioSlot;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les cases d'artefacts de l'inventaire d'Arcencium, vues par Curios. Classe A PART, comme
 * CuriosStash : on n'y entre qu'apres avoir demande a ModList, et le menu se charge sans Curios.
 *
 * Les cases sont celles de Curios (CurioSlot) : elles valident ce qu'on y pose (les etiquettes des
 * types de cases, ICurio.canEquip), refusent de lacher un objet maudit, et montrent l'icone de leur
 * type. Seuls les types visibles sont montres, dans l'ordre de Curios ; les cases cosmetiques restent
 * dans l'ecran de Curios.
 */
final class CuriosSlots {

    private CuriosSlots() {
    }

    /** Les cases d'artefacts du joueur, dans l'ordre ou elles s'affichent, telles qu'elles sont MAINTENANT. */
    static List<CurioRef> layout(Player player) {
        List<CurioRef> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> {
            Map<String, ISlotType> types = CuriosApi.getPlayerSlots(player);
            List<Map.Entry<String, ICurioStacksHandler>> entries = new ArrayList<>(inventory.getCurios().entrySet());
            entries.sort(Comparator.comparingInt((Map.Entry<String, ICurioStacksHandler> entry) -> {
                ISlotType type = types.get(entry.getKey());
                return type == null ? Integer.MAX_VALUE : type.getOrder();
            }).thenComparing(Map.Entry::getKey));
            for (Map.Entry<String, ICurioStacksHandler> entry : entries) {
                ICurioStacksHandler handler = entry.getValue();
                if (!handler.isVisible()) {
                    continue;
                }
                for (int index = 0; index < handler.getSlots(); index++) {
                    out.add(new CurioRef(entry.getKey(), index));
                }
            }
        });
        return out;
    }

    /** La case de Curios pour cette reference, ou null si ce cote ne la connait pas (encore, ou plus). */
    @Nullable
    static Slot slot(Player player, CurioRef ref) {
        Optional<ICurioStacksHandler> found = CuriosApi.getCuriosInventory(player)
                .flatMap(inventory -> inventory.getStacksHandler(ref.identifier()));
        if (found.isEmpty()) {
            return null;
        }
        ICurioStacksHandler handler = found.get();
        if (ref.index() >= handler.getSlots() || ref.index() >= handler.getRenders().size()) {
            return null;
        }
        // LE CONSTRUCTEUR COMPLET, comme l'ecran de Curios : les autres laissent la liste des
        // etats actifs vide (null), et CurioSlot.set la lit des que le contenu change -- le
        // client plantait a la deuxieme ouverture (NullPointerException, prise du 29 sept.)
        return new CurioSlot(player, handler.getStacks(), ref.index(), ref.identifier(), 0, 0,
                handler.getRenders(), handler.getActiveStates(), handler.canToggleRendering(), false, false);
    }

    /** Cet objet va-t-il dans au moins un type de case de ce joueur ? Pour ne pas predire au client. */
    static boolean isCurio(Player player, ItemStack stack) {
        return !stack.isEmpty() && !CuriosApi.getItemStackSlots(stack, player).isEmpty();
    }

    /** Pour le banc : des cases en plus ou en moins, comme les ceintures de Relics pour les charmes. */
    static void addSlots(Player player, String type, ResourceLocation id, int amount) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory ->
                inventory.addTransientSlotModifier(type, id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    static void removeSlots(Player player, String type, ResourceLocation id) {
        CuriosApi.getCuriosInventory(player).ifPresent(inventory -> inventory.removeSlotModifier(type, id));
    }

    /** Le nombre de cases de ce type, tel que Curios le tient. */
    static int size(Player player, String type) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inventory -> inventory.getStacksHandler(type))
                .map(ICurioStacksHandler::getSlots)
                .orElse(0);
    }
}
