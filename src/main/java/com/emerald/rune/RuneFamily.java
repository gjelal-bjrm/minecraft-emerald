package com.emerald.rune;

import com.emerald.item.ModArmorMaterials;
import com.emerald.weapons.ArcenciumBowItem;
import com.emerald.weapons.ArcenciumGlaiveItem;
import com.emerald.weapons.ArcenciumScepterItem;
import com.emerald.weapons.EmeraldWindblade;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Les deux familles de runes, et ce que chacune accepte.
 *
 * NosTale equipe un personnage d'une arme PRINCIPALE et d'une arme SECONDAIRE,
 * toutes deux runables -- et toutes deux avec des runes d'ARME. Nous n'avons pas
 * d'arme secondaire : c'est le CASQUE qui en tient le role, et il prend donc une
 * rune d'arme, tout simplement.
 *
 * J'AVAIS INVENTE UNE TROISIEME FAMILLE, et elle n'avait pas lieu d'etre. Mon
 * objection etait qu'une rune d'arme posee sur un casque ferait exactement ce
 * qu'elle ferait sur l'arme -- mais c'est precisement ainsi que NosTale
 * fonctionne, et cela suffit : le casque n'a pas besoin d'un role propre, il a
 * besoin d'un SECOND emplacement offensif. Une famille de plus n'ajoutait qu'un
 * catalogue a maintenir.
 *
 * Le casque accepte donc les DEUX familles, dans deux emplacements distincts :
 * une rune d'armure parce qu'il est une piece d'armure, une rune d'arme parce
 * qu'il tient lieu d'arme secondaire.
 */
public enum RuneFamily implements net.minecraft.util.StringRepresentable {

    /**
     * L'arme tenue -- et le casque, qui fait office d'arme secondaire.
     *
     * C'est la famille offensive : degats directs, critique, et les effets a
     * declenchement que le releve NosTale range lui aussi ici (syncope,
     * saignement, regeneration par victoire).
     */
    WEAPON(stack -> isModWeapon(stack) || isArcenciumArmor(stack, EquipmentSlot.HEAD)
            // et l'equipement vanilla, plafonne par sa rarete (voir GearEligibility)
            || com.emerald.item.GearEligibility.isVanillaSword(stack)
            || com.emerald.item.GearEligibility.isVanillaArmor(stack, EquipmentSlot.HEAD)),

    /** Les quatre pieces d'Arcencium : le defensif. */
    ARMOR(stack -> isArcenciumArmor(stack, null)
            || com.emerald.item.GearEligibility.isVanillaArmor(stack, null));

    private final java.util.function.Predicate<ItemStack> accepts;

    RuneFamily(java.util.function.Predicate<ItemStack> accepts) {
        this.accepts = accepts;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean accepts(ItemStack stack) {
        return this.accepts.test(stack);
    }

    /**
     * Toute arme du mode, la Lame du Serment exceptee.
     *
     * Elle se dissout a la fin du prologue : y graver une rune la detruirait,
     * exactement comme pour les artefacts.
     */
    private static boolean isModWeapon(ItemStack stack) {
        if (stack.is(com.emerald.item.ModItems.OATH_BLADE.get())) {
            return false;
        }
        return stack.getItem() instanceof EmeraldWindblade
                || stack.getItem() instanceof ArcenciumBowItem
                || stack.getItem() instanceof ArcenciumScepterItem
                || stack.getItem() instanceof ArcenciumGlaiveItem;
    }

    /** Une piece d'armure d'Arcencium ; si slot est nul, n'importe laquelle. */
    private static boolean isArcenciumArmor(ItemStack stack, EquipmentSlot slot) {
        return stack.getItem() instanceof ArmorItem armor
                && armor.getMaterial().equals(ModArmorMaterials.ARCENCIUM)
                && (slot == null || armor.getEquipmentSlot() == slot);
    }
}
