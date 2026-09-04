package com.emerald.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * Ce que les trois etablis acceptent, et jusqu'ou.
 *
 * LE JOUEUR L'A DEMANDE POUR LE DEBUT DE PARTIE : on trouve une epee de fer
 * et un plastron de diamant bien avant sa premiere arme d'Arcencium, et ils
 * doivent pouvoir passer par la Forge, l'etabli et les runes -- sinon les
 * trois stations du village restent fermees pendant une demi-heure.
 *
 * Mais l'equipement vanilla est TROP FAIBLE pour aller au bout : +7 au plus
 * a la Forge, Ancestral (rang 5) au plus a l'etabli, et donc des runes de
 * rang 5 au plus, par la regle qui lie le rang de la rune a celui de la
 * piece. Les +8 a +10, le Mysterieux, le Legendaire et le Phenomenal restent
 * aux pieces du mode : c'est ce qui donne une raison de les chercher.
 *
 * « Vanilla » se lit au registre : l'espace de noms minecraft, et rien
 * d'autre. Les pieces des autres mods ne passent pas -- on ne connait ni
 * leur force ni leur equilibre, et l'on ne devine pas.
 */
public final class GearEligibility {

    /** Cran d'amelioration au plus pour l'equipement vanilla. */
    public static final int VANILLA_UPGRADE_MAX = 7;
    /** Rang de rarete au plus pour l'equipement vanilla : Ancestral. */
    public static final int VANILLA_RARITY_MAX = 5;

    private GearEligibility() {
    }

    private static boolean vanilla(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && "minecraft".equals(key.getNamespace());
    }

    /** Une epee vanilla, quel que soit le metal. */
    public static boolean isVanillaSword(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem && vanilla(stack);
    }

    /** Une piece d'armure vanilla ; si slot est nul, n'importe laquelle. */
    public static boolean isVanillaArmor(ItemStack stack, EquipmentSlot slot) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem armor && vanilla(stack)
                && (slot == null || armor.getEquipmentSlot() == slot);
    }

    /** Une piece vanilla qui passe par les etablis, arme ou armure. */
    public static boolean isVanillaGear(ItemStack stack) {
        return isVanillaSword(stack) || isVanillaArmor(stack, null);
    }

    /** Le cran d'amelioration au plus pour cette piece. */
    public static int upgradeMax(ItemStack stack) {
        return isVanillaGear(stack) ? VANILLA_UPGRADE_MAX : Upgrade.MAX;
    }

    /** Le rang de rarete au plus pour cette piece. */
    public static int rarityMax(ItemStack stack) {
        return isVanillaGear(stack) ? VANILLA_RARITY_MAX : GearRarity.values().length - 1;
    }
}
