package com.emerald.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Le nom d'une piece, compose par les deux systemes qui y ont droit.
 *
 * Il en fallait UN SEUL ENDROIT. La rarete ecrivait le nom, et l'amelioration
 * voulait l'ecrire aussi : chacune aurait efface l'autre, et le dernier a
 * parler aurait gagne. Un joueur qui ameliore une piece Legendaire l'aurait vue
 * redevenir anonyme.
 *
 * La composition suit celle de NosTale : le cran d'abord, la qualite ensuite,
 * le nom enfin.
 *
 *     +8 Legendaire Glaive d'Arcencium
 *
 * La COULEUR vient de la rarete et non du cran, parce que c'est elle qui range
 * les pieces entre elles -- le cran dit de combien une piece frappe plus fort,
 * pas ce qu'elle est.
 */
public final class GearName {

    private GearName() {
    }

    /**
     * Refait le nom d'apres l'etat courant de la piece.
     *
     * Une piece sans qualite ni amelioration PERD son nom au lieu d'en recevoir
     * un vide : on rend alors la main au jeu, qui affichera le nom d'origine.
     */
    public static void refresh(ItemStack stack) {
        GearRarity rarity = GearRarity.of(stack);
        int level = Upgrade.of(stack);

        if (rarity == GearRarity.NORMAL && level <= 0) {
            stack.remove(DataComponents.CUSTOM_NAME);
            return;
        }

        MutableComponent named = Component.empty();
        if (level > 0) {
            named.append(Component.literal("+" + level + " "));
        }
        if (rarity != GearRarity.NORMAL) {
            named.append(rarity.label()).append(Component.literal(" "));
        }
        named.append(Component.translatable(stack.getItem().getDescriptionId()));

        int colour = rarity == GearRarity.NORMAL ? 0xF0E68C : rarity.colour();
        stack.set(DataComponents.CUSTOM_NAME,
                named.withStyle(style -> style.withColor(colour).withItalic(false)));
    }
}
