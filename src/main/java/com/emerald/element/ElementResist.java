package com.emerald.element;

import com.emerald.hero.HeroBonus;
import com.emerald.hero.HeroLevel;
import com.emerald.hero.HeroStat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Ce qui encaisse un element : la resistance, cote joueur et cote creature.
 *
 * DEUX SOURCES BIEN SEPAREES, et il le fallait.
 *
 * Le JOUEUR resiste par son ARMURE, accordee piece par piece. Chaque piece
 * accordee a un element protege contre cet element-la. Quatre pieces d'un meme
 * element font un mur contre lui et rien contre les trois autres ; quatre
 * elements differents font une protection tiede partout. C'est le meme choix
 * que la fiche du Heros -- se specialiser ou s'etaler -- et il se pose ici sur
 * un terrain different, ce qui evite qu'il se reponde deux fois de la meme
 * facon.
 *
 * La CREATURE resiste par ce qu'elle est. Une bete coriace resiste mieux
 * qu'une bete fragile, ce qui suit ses points de vie -- la seule mesure
 * comparable d'un mod a l'autre, deja utilisee pour l'experience et pour le
 * rang des runes. On ne tient donc aucune table de noms, qui manquerait tout ce
 * que le modpack ajoute.
 */
public final class ElementResist {

    /** Ce qu'une piece d'armure accordee protege contre son element, en pour cent. */
    private static final double PER_PIECE = 11.0;
    /** Ce qu'aucune resistance ne depasse : un element doit toujours mordre un peu. */
    private static final double CEILING = 75.0;
    /** Ce qu'une creature tres coriace resiste, au plus. */
    private static final double MOB_CEILING = 30.0;
    /** Points de vie au-dela desquels ce plafond est atteint. */
    private static final double TOUGH = 300.0;
    /** Ce qu'un boss a deux elements resiste en plus, en pour cent. */
    private static final double DUAL_BONUS = 18.0;

    private ElementResist() {
    }

    /**
     * La resistance d'une entite a cet element, en pour cent.
     *
     * Elle ne comprend PAS l'affinite des elements opposes : celle-ci multiplie
     * les degats en amont, et la compter ici la ferait jouer deux fois.
     */
    public static double of(LivingEntity entity, Element element) {
        if (element == Element.NEUTRE) {
            return 0.0;
        }
        if (entity instanceof Player player) {
            double total = 0.0;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                    continue;
                }
                ItemStack worn = player.getItemBySlot(slot);
                if (Attunement.of(worn) == element) {
                    total += PER_PIECE;
                }
            }
            // La voie Element protege contre TOUS les elements : c'est ce qui
            // la distingue de l'armure, qui ne protege que contre celui qu'on a
            // choisi. Se specialiser bat toujours s'etaler, mais coute un choix.
            total += HeroStat.ELEMENT.bonus(HeroBonus.RESISTANCE,
                    HeroLevel.effective(player, HeroStat.ELEMENT));
            return Math.min(CEILING, total);
        }
        // Une creature : sa robustesse fait sa resistance, sauf contre son
        // contraire, devant lequel elle ne resiste a rien. Sans cette exception,
        // un boss serait dur a tuer meme avec le bon element, et le systeme
        // n'aurait plus de reponse a offrir.
        // LA TABLE D'ABORD : chaque creature a quatre resistances fixes, une par
        // element, et non un seul chiffre. C'est ce qui fait qu'il n'y a pas UN
        // bon element mais un par famille d'ennemis.
        double base = MobElement.resistance(entity, element);
        boolean dual = Attunement.dual(entity);
        if (base <= 0.0 && !dual) {
            return 0.0;                   // son contraire : la porte reste ouverte
        }
        // La robustesse s'y ajoute : une bete coriace encaisse mieux, quel que
        // soit son profil.
        base += MOB_CEILING * Math.min(1.0, entity.getMaxHealth() / TOUGH);
        // LE BOSS BI-ELEMENT resiste en plus, et il ne tombe jamais a zero meme
        // devant l'un de ses contraires : c'est ce qui fait qu'on ne le demonte
        // pas avec une seule bonne arme.
        return dual ? Math.min(CEILING, base + DUAL_BONUS) : base;
    }

    /**
     * La resistance qui reste apres la percee de l'attaquant.
     *
     * La BAISSE DE RESISTANCE est la troisieme facon dont le mage se distingue,
     * apres l'absence de critique et la puissance elementaire. Elle a un effet
     * que rien d'autre ne produit : elle rend ses degats fiables. Les autres
     * armes voient leur element s'ecraser contre une cible resistante ; le
     * sceptre, lui, passe.
     */
    public static double after(double resistance, ItemStack weapon, LivingEntity attacker) {
        double pierce = WeaponProfile.resistPierce(weapon)
                + SecondaryProfile.resistPierce(attacker);
        return Math.max(0.0, resistance - pierce);
    }
}
