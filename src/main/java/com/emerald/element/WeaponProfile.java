package com.emerald.element;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.Artifacts;
import com.emerald.item.GearRarity;
import com.emerald.weapons.ArcenciumBowItem;
import com.emerald.weapons.ArcenciumGlaiveItem;
import com.emerald.weapons.ArcenciumScepterItem;
import com.emerald.weapons.EmeraldWindblade;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Le caractere chiffre de chaque arme, cale sur de VRAIES armes de NosTale.
 *
 * Les pourcentages viennent de trois armes de niveau 99 relevees en jeu :
 * l'Arc des guerres navales de Joseon, l'Epee stygienne du fils de Thetis et la
 * Baguette d'Avalon. Les DEGATS BRUTS, eux, restent a l'echelle de Minecraft --
 * un millier et demi de degats obligerait a reequilibrer tout le bestiaire.
 *
 * CE RELEVE A CORRIGE QUATRE ERREURS, toutes du meme genre : j'avais fait des
 * ecarts quatre a cinq fois trop grands.
 *
 *                            ce que j'avais    la realite
 *   crit archer / epeiste         x4,7            x1,23
 *   element mage / epeiste        x4,7            x1,28
 *   percee mage / epeiste         x5,5            x1,16
 *   degats bruts arc / epee       -10 %           +2 %
 *
 * Les classes de NosTale se ressemblent BEAUCOUP plus que je ne le croyais. Ce
 * qui les separe n'est pas l'ampleur des chiffres mais leur NATURE : le mage
 * n'a pas un critique plus faible, il n'en a AUCUN -- la baguette d'Avalon n'a
 * tout simplement pas de ligne de critique, elle a une ligne de Concentration a
 * la place. Une difference de nature, pas de degre.
 *
 * Le releve donne aussi un declenchement present sur les trois armes : « avec
 * une probabilite de X, la force d'attaque augmente de Y ». Les esperances en
 * sont presque egales ; ce qui change est le RYTHME -- l'epeiste declenche
 * souvent pour peu, l'archer et le mage rarement pour beaucoup.
 */
public final class WeaponProfile {

    /** Ce que chaque rang de rarete ajoute a la chance de critique, en pour cent. */
    private static final double CHANCE_PER_RANK = 0.60;
    /** Ce que chaque rang ajoute aux degats critiques, en pour cent. */
    private static final double CRIT_PER_RANK = 5.00;
    /** Ce que chaque rang ajoute a la puissance elementaire, en pour cent. */
    private static final double ELEMENT_PER_RANK = 2.00;
    /** Ce que chaque rang ajoute a la percee de resistance, en pour cent. */
    private static final double PIERCE_PER_RANK = 1.00;

    /**
     * Les six chiffres d'une arme, avant rarete.
     *
     * @param chance      probabilite de coup critique, en pour cent
     * @param critDamage  ce que le critique ajoute au multiplicateur, en pour cent
     * @param element     puissance elementaire, en pour cent des degats bruts
     * @param pierce      ce qu'on retire a la resistance de la cible
     * @param surgeChance probabilite du declenchement
     * @param surgePower  ce que le declenchement ajoute, en pour cent
     */
    private record Profile(double chance, double critDamage, double element,
                           double pierce, double surgeChance, double surgePower) {
    }

    // L'EPEISTE SERT D'ETALON, les autres sont poses en proportion de lui.
    //
    // L'ECART DE CRITIQUE EST PLUS LARGE QUE CELUI DU RELEVE (24 contre 16, la
    // ou NosTale donne 27 contre 22), et c'est voulu. Avec les rapports exacts,
    // l'archer et l'epeiste finissaient a un pour cent l'un de l'autre : deux
    // classes qu'on ne distingue pas ne sont pas deux classes. On garde donc le
    // SENS du releve -- l'archer critique plus -- en lui donnant de quoi se
    // remarquer.
    private static final Profile BLADE = new Profile(16.0, 100.0, 22.0, 12.0, 40.0, 55.0);
    private static final Profile BOW = new Profile(24.0, 115.0, 22.5, 12.5, 35.0, 65.0);
    private static final Profile SCEPTER = new Profile(0.0, 0.0, 30.0, 22.0, 35.0, 70.0);
    private static final Profile GLAIVE = new Profile(20.0, 100.0, 24.0, 13.0, 38.0, 60.0);
    private static final Profile NONE = new Profile(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    /**
     * Ce que le Conduit de Prisme rend au Sceptre.
     *
     * Le Sceptre reste une arme de SOUTIEN : sans critique, son total est
     * legerement derriere les trois autres, ce qui est juste puisqu'il soigne et
     * repousse. C'est l'artefact qui en fait un mage -- il double alors son
     * element et passe devant, mais au prix d'un emplacement.
     */
    private static final double CONDUIT_ELEMENT = 30.0;

    private WeaponProfile() {
    }

    private static Profile of(ItemStack stack) {
        if (stack.getItem() instanceof ArcenciumScepterItem) {
            return SCEPTER;
        }
        if (stack.getItem() instanceof ArcenciumBowItem) {
            return BOW;
        }
        if (stack.getItem() instanceof ArcenciumGlaiveItem) {
            return GLAIVE;
        }
        if (stack.getItem() instanceof EmeraldWindblade) {
            return BLADE;
        }
        return NONE;
    }

    /** Vrai si l'arme participe a ces systemes. */
    public static boolean applies(ItemStack stack) {
        return of(stack) != NONE;
    }

    /**
     * La chance de critique de l'arme, rarete comprise, en pour cent.
     *
     * Le sceptre rend zero et le rang n'y change rien. Ce n'est pas un chiffre
     * bas mais une ABSENCE, et c'est toute sa difference.
     */
    public static double critChance(ItemStack stack) {
        Profile profile = of(stack);
        if (profile.chance() <= 0.0) {
            return 0.0;
        }
        return profile.chance() + GearRarity.of(stack).rank() * CHANCE_PER_RANK;
    }

    /** Les degats critiques de l'arme, rarete comprise, en pour cent. */
    public static double critDamage(ItemStack stack) {
        Profile profile = of(stack);
        if (profile.chance() <= 0.0) {
            return 0.0;
        }
        return profile.critDamage() + GearRarity.of(stack).rank() * CRIT_PER_RANK;
    }

    /**
     * La puissance elementaire de l'arme, rarete comprise, en pour cent.
     *
     * Elle ne sert a rien tant que l'arme n'est pas ACCORDEE a un element :
     * voir {@link Attunement}. C'est voulu -- tant qu'on n'a pas choisi, le
     * systeme reste invisible.
     */
    public static double elementPower(ItemStack stack) {
        Profile profile = of(stack);
        if (profile.element() <= 0.0) {
            return 0.0;
        }
        double base = profile.element();
        if (profile == SCEPTER && Artifacts.has(stack, Artifact.CONDUIT_DE_PRISME)) {
            base += CONDUIT_ELEMENT;
        }
        return base + GearRarity.of(stack).rank() * ELEMENT_PER_RANK;
    }

    /**
     * Ce que l'arme retire a la resistance de sa cible, en points de pour cent.
     *
     * LE MAGE MENE ICI TRES LARGEMENT, et c'est sa signature. Vingt-deux contre
     * douze, soit une fois et demie -- casque compris, vingt-huit contre
     * dix-huit. C'est ce qui rend ses degats FIABLES : la ou les autres voient
     * leur element s'ecraser contre une cible resistante, le sceptre passe.
     *
     * Le releve seul donnait un ecart bien plus mince (85 contre 80, six pour
     * cent) ; on l'a ouvert a dessein, parce qu'un ecart de six pour cent ne se
     * remarque pas en jeu et ne tiendrait pas lieu de caractere.
     */
    public static double resistPierce(ItemStack stack) {
        Profile profile = of(stack);
        if (profile.pierce() <= 0.0) {
            return 0.0;
        }
        return profile.pierce() + GearRarity.of(stack).rank() * PIERCE_PER_RANK;
    }

    /**
     * Le DECLENCHEMENT : « avec une probabilite de X, la force d'attaque
     * augmente de Y ».
     *
     * Present sur les trois armes du releve, avec des esperances presque
     * egales. Ce n'est donc pas un avantage mais une TEXTURE : l'epeiste
     * declenche souvent pour peu, l'archer et le mage rarement pour beaucoup.
     * Deux armes de meme valeur ne se ressentent pas pareil, et c'est tout
     * l'interet.
     *
     * @return le multiplicateur a appliquer, ou 1,0 si rien ne s'est declenche
     */
    public static double surge(ItemStack stack, RandomSource random) {
        Profile profile = of(stack);
        if (profile.surgeChance() <= 0.0) {
            return 1.0;
        }
        return random.nextDouble() * 100.0 < profile.surgeChance()
                ? 1.0 + profile.surgePower() / 100.0
                : 1.0;
    }

    /** La probabilite du declenchement, pour l'infobulle. */
    public static double surgeChance(ItemStack stack) {
        return of(stack).surgeChance();
    }

    /** Ce que le declenchement ajoute, pour l'infobulle. */
    public static double surgePower(ItemStack stack) {
        return of(stack).surgePower();
    }
}
