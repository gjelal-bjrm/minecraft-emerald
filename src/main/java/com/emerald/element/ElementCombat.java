package com.emerald.element;

import com.emerald.hero.HeroEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Les degats elementaires : ce qui s'ajoute au coup, et pourquoi.
 *
 * LES DEGATS ELEMENTAIRES SE CALCULENT SUR LES DEGATS BRUTS, comme chez
 * NosTale. Ce n'est pas un detail : cela lie l'element a l'arme au lieu d'en
 * faire une source separee. Une arme qui frappe fort frappe fort aussi en
 * element, et l'on ne peut pas contourner ses degats bruts en misant tout sur
 * l'element.
 *
 *     elementaire = brut x puissance x affinite x voie Element x (1 - resistance)
 *
 * Quatre facteurs, et chacun repond a une decision differente :
 *
 *   - la PUISSANCE vient de l'arme et de sa rarete : c'est le choix de classe ;
 *   - l'AFFINITE vient du couple attaquant/defenseur : c'est le choix tactique ;
 *   - la VOIE ELEMENT vient de la fiche du Heros : c'est le choix de
 *     progression, et c'est ce qui donne enfin un role concret a cette voie,
 *     qui ne servait jusqu'ici qu'a de vagues « effets du mode » ;
 *   - la RESISTANCE vient de la fiche du defenseur.
 *
 * Le tout s'AJOUTE aux degats plutot que de les multiplier. Un multiplicateur
 * rendrait l'element indissociable du coup et le ferait profiter du critique,
 * ce qui doublerait deux fois -- or le sceptre n'a justement pas de critique.
 */
public final class ElementCombat {

    private ElementCombat() {
    }

    /**
     * Ce que l'element ajoute a ce coup-ci. Zero si l'arme n'est pas accordee.
     *
     * @param raw les degats bruts, avant tout ajout
     */
    public static float bonus(Player attacker, LivingEntity victim, ItemStack weapon, float raw) {
        // L'ELEMENT VIENT DU JOUEUR, la puissance de son arme. Tant qu'il n'a
        // pas choisi, aucun degat elementaire ne part -- le systeme reste
        // invisible pour qui n'y touche pas.
        Element mine = Attunement.of(attacker);
        if (mine == Element.NEUTRE || raw <= 0.0F) {
            return 0.0F;
        }
        // L'ARME ET LE CASQUE S'ADDITIONNENT : ce sont les lignes orange du
        // releve, les seules qui se cumulent entre arme principale et arme
        // secondaire. Le casque tient chez nous le role de la secondaire.
        double power = WeaponProfile.elementPower(weapon)
                + SecondaryProfile.elementPower(attacker);
        if (power <= 0.0) {
            return 0.0F;
        }
        double affinity = Attunement.affinity(mine, victim);
        double mastery = HeroEvents.elementBonus(attacker);

        double bonus = raw * (power / 100.0) * affinity * mastery;

        // LA RESISTANCE, et la percee qui l'entame. Le mage passe la ou les
        // autres s'ecrasent : c'est sa troisieme distinction, apres l'absence
        // de critique et la puissance elementaire.
        double resist = ElementResist.after(ElementResist.of(victim, mine),
                weapon, attacker);
        bonus *= Math.max(0.0, 1.0 - resist / 100.0);
        return (float) bonus;
    }

    /** Vrai si ce coup portera un element : sert a n'afficher l'effet que s'il existe. */
    public static boolean carries(Player attacker, ItemStack weapon) {
        return Attunement.of(attacker) != Element.NEUTRE
                && WeaponProfile.elementPower(weapon) > 0.0;
    }
}
