package com.emerald.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.common.Tags;

/**
 * Ce que les trois etablis acceptent, et jusqu'ou.
 *
 * LE JOUEUR L'A DEMANDE POUR LE DEBUT DE PARTIE : on trouve une epee de fer
 * et un plastron de diamant bien avant sa premiere arme d'Arcencium, et ils
 * doivent pouvoir passer par la Forge, l'etabli et les runes -- sinon les
 * trois stations du village restent fermees pendant une demi-heure.
 *
 * PUIS IL L'A DEMANDE POUR TOUT LE RESTE, et il avait raison de s'etonner.
 * La regle disait « l'espace de noms minecraft, et rien d'autre » : elle a
 * tenu tant que le mode se jouait seul, mais il se joue dans un modpack de
 * quatre cent quarante mods. Une epee de silent gear, une hache de forbidden
 * arcanus, un arc d'un mod d'aventure : tout cela se ramassait, et rien de
 * tout cela n'entrait a l'etabli. La moitie de ce qu'on trouve etait morte
 * pour nos trois stations.
 *
 * (Ce qui trompait : une epee de PIERRE portant des affixes d'Apotheosis
 * passait deja. Un affixe est une donnee posee sur l'objet, pas un objet
 * neuf -- l'epee reste `minecraft:stone_sword`, donc vanilla, donc admise.)
 *
 * DEUX FAMILLES, DEUX PLAFONDS, ET C'EST TOUT.
 *
 *  - CE QUI EST DU MODE -- les quatre armes d'Arcencium et ses quatre pieces
 *    d'armure -- va jusqu'au bout : +10 a la Forge, Prismatique a l'etabli,
 *    et les runes du dernier rang ;
 *  - TOUT LE RESTE, vanilla comme modde, plafonne a +7 et au Solaire (rang 5),
 *    et donc a des runes de rang 5 par la regle qui lie le rang de la rune a
 *    celui de la piece.
 *
 * Un seul plafond pour tout ce qui n'est pas de nous, et non un troisieme
 * palier pour les mods : on ne connait ni la force ni l'equilibre de quatre
 * cents mods, et pretendre les classer serait inventer. Le plafond garde
 * intacte la seule chose qui compte -- l'equipement du mode reste ce qu'on
 * cherche, parce qu'il est le seul a aller au bout.
 */
public final class GearEligibility {

    /** Cran d'amelioration au plus pour ce qui n'est pas du mode. */
    public static final int BORROWED_UPGRADE_MAX = 7;
    /** Rang de rarete au plus pour ce qui n'est pas du mode : Solaire. */
    public static final int BORROWED_RARITY_MAX = 5;

    private GearEligibility() {
    }

    private static boolean is(ItemStack stack, TagKey<Item> tag) {
        return stack.is(tag);
    }

    private static boolean vanilla(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && "minecraft".equals(key.getNamespace());
    }

    /**
     * Une arme au corps a corps ou a distance, d'ou qu'elle vienne.
     *
     * On lit d'abord les ETIQUETTES communes -- `c:tools/melee_weapon` et
     * `c:tools/ranged_weapon` -- que NeoForge remplit pour le jeu et que les
     * mods serieux remplissent pour eux ; le modpack en rajoute par ses
     * scripts d'unification. La classe `SwordItem` sert de filet pour les mods
     * qui n'etiquettent rien.
     *
     * Une pile de plus d'un objet n'est jamais un equipement : c'est le
     * garde-fou contre une etiquette trop large.
     */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() > 1) {
            return false;
        }
        return is(stack, Tags.Items.MELEE_WEAPON_TOOLS)
                || is(stack, Tags.Items.RANGED_WEAPON_TOOLS)
                || is(stack, Tags.Items.TOOLS_BOW)
                || is(stack, Tags.Items.TOOLS_CROSSBOW)
                || is(stack, Tags.Items.TOOLS_SPEAR)
                || is(stack, Tags.Items.TOOLS_MACE)
                || stack.getItem() instanceof SwordItem;
    }

    /**
     * Une piece d'armure portee par un joueur ; si slot est nul, n'importe
     * laquelle.
     *
     * `AnimalArmorItem` est ecarte : l'armure de cheval et la carapace de
     * loup sont des `ArmorItem` que personne ne porte, et une rune dessus
     * n'aurait aucun effet a lire.
     */
    public static boolean isArmor(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty() || stack.getItem() instanceof AnimalArmorItem) {
            return false;
        }
        if (stack.getItem() instanceof ArmorItem armor) {
            return slot == null || armor.getEquipmentSlot() == slot;
        }
        return slot == null && is(stack, Tags.Items.ARMORS);
    }

    /** Une epee vanilla, quel que soit le metal. Garde pour les anciens appels. */
    public static boolean isVanillaSword(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SwordItem && vanilla(stack);
    }

    /** Une piece d'armure vanilla ; si slot est nul, n'importe laquelle. */
    public static boolean isVanillaArmor(ItemStack stack, EquipmentSlot slot) {
        return vanilla(stack) && isArmor(stack, slot);
    }

    /**
     * Cette piece est-elle EMPRUNTEE, c'est-a-dire pas du mode ?
     *
     * C'est ce qui decide du plafond. On ne demande pas « d'ou vient-elle » a
     * un registre mais « est-elle des notres » aux deux familles de runes :
     * elles sont la seule definition de notre equipement, et deux definitions
     * finiraient par diverger.
     */
    public static boolean isBorrowedGear(ItemStack stack) {
        return !stack.isEmpty() && !com.emerald.rune.RuneFamily.isOurs(stack);
    }

    /** Le cran d'amelioration au plus pour cette piece. */
    public static int upgradeMax(ItemStack stack) {
        return isBorrowedGear(stack) ? BORROWED_UPGRADE_MAX : Upgrade.MAX;
    }

    /** Le rang de rarete au plus pour cette piece. */
    public static int rarityMax(ItemStack stack) {
        return isBorrowedGear(stack) ? BORROWED_RARITY_MAX : GearRarity.values().length - 1;
    }
}
