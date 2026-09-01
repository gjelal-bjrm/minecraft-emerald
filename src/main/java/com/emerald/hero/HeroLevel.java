package com.emerald.hero;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Le niveau Heros : une progression parallele a celle du jeu.
 *
 * Elle monte plus vite que l'experience ordinaire et s'arrete a cent. Chaque
 * montee rend des points de specialisation, que l'on repartit entre quatre
 * statistiques -- c'est le modele de NosTale, et il vaut pour la meme raison :
 * un joueur qui choisit ou mettre ses points se souvient de son personnage,
 * alors qu'une progression automatique ne se remarque pas.
 *
 * LE TOTAL EST FIXE A QUATRE CENT QUATRE-VINGT-SIX au niveau cent, et le
 * bareme y tombe juste :
 *
 *    niveaux  2 a 25  : 3 points  (24 montees =  72)
 *    niveaux 26 a 50  : 4 points  (25 montees = 100)
 *    niveaux 51 a 75  : 5 points  (25 montees = 125)
 *    niveaux 76 a 99  : 6 points  (24 montees = 144)
 *    niveau  100      : 45 points (le dernier vaut un palier entier)
 *
 * Le gros lot final est deliberé : les derniers niveaux sont les plus longs, et
 * une recompense plate les rendrait ingrats.
 */
public final class HeroLevel {

    public static final int MAX_LEVEL = 100;

    private static final String TAG_XP = "HeroXp";
    private static final String TAG_LEVEL = "HeroLevel";
    private static final String TAG_FREE = "HeroPoints";

    private HeroLevel() {
    }

    /** Les points rendus en atteignant ce niveau-ci. */
    public static int pointsFor(int level) {
        if (level >= MAX_LEVEL) {
            return 45;
        }
        if (level >= 76) {
            return 6;
        }
        if (level >= 51) {
            return 5;
        }
        if (level >= 26) {
            return 4;
        }
        return level >= 2 ? 3 : 0;
    }

    /**
     * L'experience qu'il faut pour passer CE niveau-ci.
     *
     * LA COURBE EST CALEE SUR L'HEURE QUE DURE LE MODE, et le chiffre a ete
     * mesure et non estime. Ma premiere version demandait soixante-seize mille
     * points en tout -- six mille quatre cents monstres. J'avais ecrit « plus
     * rapide que le jeu » dans le commentaire sans jamais faire la somme ; la
     * progression n'aurait pas passe le niveau trente.
     *
     * Celle-ci coute six mille trois cents points en tout. Une fois retires les
     * trente-quatre niveaux qu'offrent les trois ancres, il reste de quoi
     * occuper cinq a six cents monstres au bareme reel du mode -- un zombie en
     * vaut sept, non les douze que j'avais d'abord supposes. C'est une heure
     * menee sans relache, ce qui est exactement la duree du mode.
     *
     * La pente est DOUCE et non exponentielle : vingt-huit points au premier
     * palier, cent six au dernier. Une courbe plus raide rendait les vingt
     * premiers niveaux gratuits -- on passait cinquante en cent soixante-dix
     * monstres -- et les vingt derniers hors d'atteinte. Ce qui doit couter
     * cher ici, ce n'est pas le dernier niveau, c'est le centieme.
     */
    public static int needed(int level) {
        return 28 + level * 3 / 5 + level * level / 500;
    }

    /**
     * Offre des niveaux ENTIERS, pour les objectifs qui les meritent.
     *
     * Les trois ancres ne rendent pas de l'experience mais des niveaux : dix,
     * puis douze, puis douze. C'est une recompense qu'on ne peut pas rater et
     * qui se sent tout de suite -- une jauge qui avance un peu ne recompense
     * pas la prise d'un sanctuaire, une fiche qui gagne quarante points si.
     *
     * L'experience en cours est REMISE A ZERO plutot que reportee : sinon un
     * cadeau de dix niveaux ferait parfois onze, selon ce qu'on avait mis de
     * cote, et le joueur ne saurait plus ce qu'il a recu.
     *
     * @return combien de niveaux ont reellement ete donnes
     */
    public static int grantLevels(Player player, int count) {
        CompoundTag tag = tag(player);
        int level = level(player);
        int gained = 0;
        for (int i = 0; i < count && level < MAX_LEVEL; i++) {
            level++;
            gained++;
            tag.putInt(TAG_FREE, tag.getInt(TAG_FREE) + pointsFor(level));
        }
        if (gained == 0) {
            return 0;
        }
        tag.putInt(TAG_LEVEL, level);
        tag.putInt(TAG_XP, 0);
        return gained;
    }

    // ------------------------------------------------------------- la fiche

    public static int level(Player player) {
        return Math.max(1, Math.min(MAX_LEVEL, tag(player).getInt(TAG_LEVEL)));
    }

    public static int xp(Player player) {
        return tag(player).getInt(TAG_XP);
    }

    public static int free(Player player) {
        return tag(player).getInt(TAG_FREE);
    }

    /** Le NIVEAU atteint dans une voie, de zero a cent. */
    public static int path(Player player, HeroStat stat) {
        return Math.max(0, Math.min(HeroStat.MAX_PATH, tag(player).getInt(stat.tag())));
    }

    /**
     * Le niveau REELLEMENT en vigueur : ce qu'on a achete, plus les SL des runes.
     *
     * Les options SL des runes n'offrent pas des points mais des NIVEAUX, et ces
     * niveaux-la ne se paient pas. Ils s'ajoutent par-dessus l'achat, et
     * peuvent pousser une voie AU-DELA DU CENTIEME, que l'on ne peut pas
     * atteindre en depensant.
     *
     * C'est le plafond des runes, et il est justifie : cent est ce qu'on peut
     * acheter, cent vingt ce que la chance peut donner. Un joueur qui a deja
     * tout depense dans une voie a donc encore une raison de chercher une bonne
     * rune -- sans ce depassement, les SL ne serviraient qu'aux voies qu'on a
     * negligees.
     *
     * Les paliers, eux, restent bloques a dix : {@link HeroStat#tiers} ne compte
     * pas plus loin. Le depassement donne du lineaire, pas des bonus francs, de
     * sorte qu'une rune ne puisse jamais offrir un palier entier.
     */
    public static int effective(Player player, HeroStat stat) {
        double gift = com.emerald.rune.Runes.total(player, slOf(stat))
                + com.emerald.rune.Runes.total(player, com.emerald.rune.Rune.SL_TOTAL);
        return Math.min(HeroStat.SOFT_CAP,
                path(player, stat) + (int) Math.floor(gift));
    }

    /** L'option SL qui nourrit cette voie-ci. */
    private static com.emerald.rune.Rune slOf(HeroStat stat) {
        return switch (stat) {
            case ATTAQUE -> com.emerald.rune.Rune.SL_ATTAQUE;
            case ELEMENT -> com.emerald.rune.Rune.SL_ELEMENT;
            case DEFENSE -> com.emerald.rune.Rune.SL_DEFENSE;
            case VITALITE -> com.emerald.rune.Rune.SL_VITALITE;
        };
    }

    /** Ce que cette voie a deja coute en points. */
    public static int spent(Player player, HeroStat stat) {
        return HeroStat.costTo(path(player, stat));
    }

    private static CompoundTag tag(Player player) {
        return player.getPersistentData();
    }

    /**
     * Ajoute de l'experience, et rend le nombre de niveaux gagnes.
     *
     * On monte en BOUCLE et non d'un cran : un gros gain -- la prise d'une
     * ancre, la chute d'un boss -- doit pouvoir faire franchir plusieurs
     * niveaux d'un coup, sinon l'experience se perd sans qu'on comprenne
     * pourquoi.
     */
    public static int grant(Player player, int amount) {
        CompoundTag tag = tag(player);
        int level = level(player);
        if (level >= MAX_LEVEL) {
            return 0;
        }
        int xp = tag.getInt(TAG_XP) + Math.max(0, amount);
        int gained = 0;
        while (level < MAX_LEVEL && xp >= needed(level)) {
            xp -= needed(level);
            level++;
            gained++;
            tag.putInt(TAG_FREE, tag.getInt(TAG_FREE) + pointsFor(level));
        }
        tag.putInt(TAG_XP, level >= MAX_LEVEL ? 0 : xp);
        tag.putInt(TAG_LEVEL, level);
        return gained;
    }

    /**
     * Monte une voie de tant de NIVEAUX, au prix courant.
     *
     * On achete des niveaux et non de la valeur : c'est le modele reel de
     * NosTale. Chaque niveau a son prix, qui monte de un a dix, et l'on
     * s'arrete des que la cagnotte ne suffit plus au SUIVANT -- jamais a
     * moitie. Un niveau a moitie paye n'existe pas, et laisser des points
     * disparaitre dans un achat partiel serait la pire des reponses a un clic.
     *
     * Le plafond a cent est ce qui fait le CHOIX. Monter une voie entiere coute
     * 406 des 486 points : on peut en avoir une pleine et une amorcee, ou
     * quatre a mi-hauteur, jamais deux pleines.
     *
     * @return combien de niveaux ont reellement ete achetes
     */
    public static int spend(Player player, HeroStat stat, int levels) {
        CompoundTag tag = tag(player);
        int free = tag.getInt(TAG_FREE);
        int level = path(player, stat);
        int bought = 0;
        while (bought < levels && level < HeroStat.MAX_PATH
                && free >= HeroStat.cost(level)) {
            free -= HeroStat.cost(level);
            level++;
            bought++;
        }
        if (bought == 0) {
            return 0;
        }
        tag.putInt(TAG_FREE, free);
        tag.putInt(stat.tag(), level);
        return bought;
    }

    /** Ce que coute le prochain niveau d'une voie, ou zero si elle est pleine. */
    public static int nextCost(Player player, HeroStat stat) {
        int level = path(player, stat);
        return level >= HeroStat.MAX_PATH ? 0 : HeroStat.cost(level);
    }

    /**
     * Rend tous les points places : la fiche se refait, elle ne se subit pas.
     *
     * Le remboursement passe par le CUMUL DES COUTS et non par un compteur
     * separe : deux comptes qui doivent rester d'accord finissent toujours par
     * diverger, alors que le niveau seul suffit a retrouver ce qu'il a coute.
     */
    public static int reset(Player player) {
        CompoundTag tag = tag(player);
        int back = 0;
        for (HeroStat stat : HeroStat.values()) {
            back += HeroStat.costTo(tag.getInt(stat.tag()));
            tag.putInt(stat.tag(), 0);
        }
        tag.putInt(TAG_FREE, tag.getInt(TAG_FREE) + back);
        return back;
    }
}
