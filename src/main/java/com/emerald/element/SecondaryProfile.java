package com.emerald.element;

import com.emerald.item.GearRarity;
import com.emerald.item.ModArmorMaterials;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Ce que l'ARME SECONDAIRE ajoute -- chez nous, le casque.
 *
 * Le releve des armes secondaires de NosTale (Dague, Arbalete, Arme enchantee,
 * niveau 98) montre deux choses que je n'avais pas :
 *
 * 1. LES LIGNES ORANGE S'ADDITIONNENT. Element, reduction de resistance
 *    adverse et declenchement de force d'attaque se cumulent entre l'arme
 *    principale et la secondaire. Le casque n'est donc pas qu'un porte-rune :
 *    il porte lui-meme des chiffres.
 *
 * 2. LE CRITIQUE N'EN FAIT PAS PARTIE. Les armes secondaires du releve
 *    affichent bien un critique, mais dans leur bloc BLANC -- celui des
 *    caracteristiques propres a l'arme -- et non dans l'orange qui se cumule.
 *    J'avais commence par en donner au casque, ce qui aurait rendu au mage le
 *    critique qu'on venait justement de lui retirer. Le casque ne donne donc
 *    aucun critique a personne, et la privation du mage reste entiere.
 *
 * Tout monte avec la RARETE DU CASQUE, de sorte qu'ameliorer sa tete serve
 * aussi a attaquer -- ce qui n'etait le cas d'aucune piece d'armure jusqu'ici.
 */
public final class SecondaryProfile {

    /** Puissance elementaire ajoutee, en pour cent. */
    private static final double ELEMENT = 8.0;
    /** Reduction de resistance adverse ajoutee. */
    private static final double PIERCE = 6.0;
    /** Le declenchement propre au casque : rare, et franc. */
    private static final double SURGE_CHANCE = 10.0;
    private static final double SURGE_POWER = 90.0;

    private static final double ELEMENT_PER_RANK = 1.00;
    private static final double PIERCE_PER_RANK = 0.60;

    private SecondaryProfile() {
    }

    /** Le casque d'Arcencium porte, ou une pile vide. */
    private static ItemStack helmet(LivingEntity entity) {
        ItemStack worn = entity.getItemBySlot(EquipmentSlot.HEAD);
        return worn.getItem() instanceof ArmorItem armor
                && armor.getMaterial().equals(ModArmorMaterials.ARCENCIUM)
                ? worn : ItemStack.EMPTY;
    }

    private static int rank(LivingEntity entity) {
        ItemStack worn = helmet(entity);
        return worn.isEmpty() ? -1 : GearRarity.of(worn).rank();
    }

    public static double elementPower(LivingEntity entity) {
        int rank = rank(entity);
        return rank < 0 ? 0.0 : ELEMENT + rank * ELEMENT_PER_RANK;
    }

    public static double resistPierce(LivingEntity entity) {
        int rank = rank(entity);
        return rank < 0 ? 0.0 : PIERCE + rank * PIERCE_PER_RANK;
    }

    /**
     * Le declenchement du casque, qui se cumule a celui de l'arme.
     *
     * DEUX JETS ET NON UN, contrairement au critique. Chez NosTale les deux
     * armes portent chacune leur ligne, et les deux peuvent tomber sur le meme
     * coup ; les fondre en un seul jet supprimerait les rares coups ou tout se
     * declenche a la fois, qui sont precisement ceux dont on se souvient.
     */
    public static double surge(LivingEntity entity, net.minecraft.util.RandomSource random) {
        if (rank(entity) < 0) {
            return 1.0;
        }
        return random.nextDouble() * 100.0 < SURGE_CHANCE
                ? 1.0 + SURGE_POWER / 100.0
                : 1.0;
    }
}
