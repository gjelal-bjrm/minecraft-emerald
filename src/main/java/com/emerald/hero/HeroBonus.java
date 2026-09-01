package com.emerald.hero;

/**
 * Les statistiques SECONDAIRES, celles que les paliers accordent.
 *
 * NosTale ne donne pas que des chiffres bruts a ses paliers : il donne du
 * critique, de l'esquive, de la resistance. C'est ce qui empeche une voie
 * d'etre un simple curseur -- monter l'Attaque ne rend pas seulement les coups
 * plus forts, elle les rend plus SOUVENT decisifs, ce qui ne se ressent pas de
 * la meme facon.
 *
 * Aucune de ces sept n'existe comme attribut dans Minecraft. Elles sont donc
 * appliquees a la main, au moment du coup, par {@link HeroCombat}.
 */
public enum HeroBonus {

    /** Chance, en pour cent, qu'un coup devienne critique. */
    CRIT_CHANCE,
    /** Ce que le critique ajoute au multiplicateur, en pour cent. */
    CRIT_DAMAGE,
    /** Chance, en pour cent, d'annuler entierement un coup recu. */
    DODGE,
    /** Ce qu'on retire aux critiques SUBIS, en pour cent. */
    CRIT_TAKEN,
    /** Reduction, en pour cent, des degats indirects : magie, projectiles, effets. */
    RESISTANCE,
    /** Degats d'arme ajoutes en plus du lineaire de la voie. */
    ATTACK_FLAT,
    /** Armure ajoutee en plus du lineaire de la voie. */
    ARMOR_FLAT
}
