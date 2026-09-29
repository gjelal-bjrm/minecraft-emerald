package com.emerald.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
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
 * Les cases d'artefacts de l'inventaire d'Arcencium. Classe A PART, comme CuriosStash :
 * on n'y entre qu'apres avoir demande a ModList, et le menu se charge sans Curios.
 *
 * Les cases sont celles de Curios (CurioSlot) : elles valident ce qu'on y pose (les
 * etiquettes des types de cases, ICurio.canEquip), refusent de lacher un objet maudit,
 * et montrent l'icone de leur type. Seuls les types visibles sont montres, dans l'ordre
 * de Curios ; les cases cosmetiques restent dans l'ecran de Curios.
 */
final class CuriosSlots {

    private CuriosSlots() {
    }

    /** Les cases d'artefacts du joueur, cote serveur, dans l'ordre ou elles s'affichent. */
    static List<ArcInventoryMenu.CurioRef> layout(Player player) {
        List<ArcInventoryMenu.CurioRef> out = new ArrayList<>();
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
                    out.add(new ArcInventoryMenu.CurioRef(entry.getKey(), index));
                }
            }
        });
        return out;
    }

    /** La case de Curios pour cette reference, ou null si ce cote ne la connait pas (encore). */
    @Nullable
    static Slot slot(Player player, ArcInventoryMenu.CurioRef ref, int x, int y) {
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
        return new CurioSlot(player, handler.getStacks(), ref.index(), ref.identifier(), x, y,
                handler.getRenders(), handler.getActiveStates(), handler.canToggleRendering(), false, false);
    }
}
