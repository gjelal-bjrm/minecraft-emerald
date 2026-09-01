package com.emerald.hero;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * Les quatre voies de la fiche, sur le modele reel de NosTale.
 *
 * LES POINTS N'ACHETENT PAS DE LA VALEUR, ILS ACHETENT DES NIVEAUX. C'est la
 * decouverte qui a fait refaire ce fichier : je croyais que NosTale ajoutait un
 * gain fixe par point, et le vrai bareme montre tout autre chose -- une voie
 * monte de zero a cent, chaque niveau coute de plus en plus cher, et chaque
 * niveau rapporte de plus en plus. Le rendement est donc degressif par le COUT
 * et croissant par la VALEUR, ce qui n'a rien a voir avec une droite.
 *
 * Cette forme est ce qui rend la repartition interessante :
 *
 *    - monter une voie au maximum coute 406 des 486 points ;
 *    - il en reste 80, soit le niveau 36 dans une deuxieme voie ;
 *    - repartir egalement donne le niveau 47 partout ;
 *    - deux voies pleines demanderaient 812 points : c'est impossible.
 *
 * On choisit donc vraiment, et aucun choix n'est gratuit. Le vrai bareme de
 * l'Attaque demande 410 points pour cent niveaux ; la table ci-dessous en
 * demande 406, soit un ecart d'un pour cent, et elle sert aux quatre voies
 * plutot qu'une table par voie -- quatre tables presque identiques ne se
 * verifient pas, une seule se lit.
 *
 * Les paliers tombent TOUS LES DIX NIVEAUX et non tous les vingt points, et ils
 * donnent des statistiques secondaires -- critique, esquive, resistance -- que
 * Minecraft ne connait pas : voir {@link HeroCombat}.
 *
 * Bareme releve sur NosTale (Gameforge EU, donnees post-extension) ; le prompt
 * ayant servi au relevé est dans tools/prompts/nostale_points_specialiste.md.
 */
public enum HeroStat {

    /** Ce que l'on frappe : degats d'arme, puis le critique. */
    ATTAQUE("attaque", ChatFormatting.RED, 0.05,
            new Tier(HeroBonus.CRIT_CHANCE, new double[]{2, 0, 3, 0, 4, 0, 5, 0, 6, 0}),
            new Tier(HeroBonus.CRIT_DAMAGE, new double[]{0, 10, 0, 15, 0, 20, 0, 25, 0, 30})),

    /** Ce que l'on declenche : la force des effets propres au mode. */
    ELEMENT("element", ChatFormatting.LIGHT_PURPLE, 0.35,
            new Tier(HeroBonus.RESISTANCE, new double[]{1, 2, 2, 3, 3, 4, 4, 5, 5, 6})),

    /** Ce que l'on encaisse : armure, puis l'esquive. */
    DEFENSE("defense", ChatFormatting.AQUA, 0.05,
            new Tier(HeroBonus.DODGE, new double[]{1, 0, 2, 0, 3, 0, 4, 0, 5, 0}),
            new Tier(HeroBonus.CRIT_TAKEN, new double[]{0, 5, 0, 8, 0, 10, 0, 12, 0, 15})),

    /**
     * Ce que l'on tient : points de vie, et -- comme chez NosTale -- de
     * l'attaque et de l'armure par palier. C'est la voie du bagarreur : elle
     * ne fait rien mieux que les autres, elle fait un peu des trois.
     */
    VITALITE("vitalite", ChatFormatting.GREEN, 0.12,
            new Tier(HeroBonus.ATTACK_FLAT, new double[]{0.3, 0, 0.5, 0, 0.7, 0, 0.9, 0, 1.1, 0}),
            new Tier(HeroBonus.ARMOR_FLAT, new double[]{0, 0.5, 0, 0.8, 0, 1.0, 0, 1.2, 0, 1.5}));

    /** Ce qu'une voie ne depasse pas a l'ACHAT. Cent, comme le plafond de base de NosTale. */
    public static final int MAX_PATH = 100;
    /**
     * Ce qu'elle ne depasse pas une fois les SL des runes ajoutees.
     *
     * Cent vingt, comme le plafond releve chez NosTale apres perfectionnement.
     * Cent est ce qu'on peut ACHETER ; les vingt derniers ne s'obtiennent que
     * par la chance, et donnent une raison de chercher une bonne rune meme
     * quand une voie est deja pleine.
     */
    public static final int SOFT_CAP = 120;
    /** Tous les combien un palier tombe. */
    public static final int TIER = 10;

    /**
     * Ce que la valeur d'un niveau vaut, par tranche de dix.
     *
     * Elle MONTE avec le cout : chez NosTale, un niveau d'Attaque rapporte cinq
     * points au debut et vingt a la fin. Sans cela, un rendement decroissant en
     * cout et constant en valeur rendrait les vingt derniers niveaux absurdes,
     * et personne ne monterait jamais une voie au maximum.
     */
    private static final double[] BAND = {1.0, 1.0, 1.1, 1.2, 1.3, 1.4, 1.6, 1.8, 2.0, 2.4};

    /** Une statistique secondaire, et ce qu'elle donne a chacun des dix paliers. */
    public record Tier(HeroBonus bonus, double[] perTier) {
    }

    private final String key;
    private final ChatFormatting colour;
    private final double base;
    private final Tier[] tiers;

    HeroStat(String key, ChatFormatting colour, double base, Tier... tiers) {
        this.key = key;
        this.colour = colour;
        this.base = base;
        this.tiers = tiers;
    }

    // ------------------------------------------------------------- le bareme

    /**
     * Ce que coute le passage de ce niveau-ci au suivant.
     *
     * La table suit celle de NosTale de pres, en tranches franches plutot qu'au
     * niveau pres : les trois derniers niveaux coutent huit, neuf et dix, ce
     * qui est exactement leur prix la-bas, et c'est cette fin abrupte qui fait
     * qu'on n'atteint le centieme qu'en y renoncant partout ailleurs.
     */
    public static int cost(int level) {
        if (level < 10) {
            return 1;
        }
        if (level < 20) {
            return 2;
        }
        if (level < 40) {
            return 3;
        }
        if (level < 60) {
            return 4;
        }
        if (level < 80) {
            return 5;
        }
        if (level < 90) {
            return 6;
        }
        if (level < 97) {
            return 7;
        }
        return level == 97 ? 8 : level == 98 ? 9 : 10;
    }

    /** Tout ce qu'il a fallu depenser pour arriver a ce niveau. Sert au remboursement. */
    public static int costTo(int level) {
        int total = 0;
        for (int l = 0; l < Math.min(MAX_PATH, Math.max(0, level)); l++) {
            total += cost(l);
        }
        return total;
    }

    // -------------------------------------------------------------- la valeur

    /** Le gain principal de la voie, pour un niveau donne. */
    public double value(int level) {
        double total = 0.0;
        for (int l = 0; l < Math.min(SOFT_CAP, Math.max(0, level)); l++) {
            total += this.base * BAND[Math.min(BAND.length - 1, l / TIER)];
        }
        return total;
    }

    /** Combien de paliers ce niveau a franchis. */
    public static int tiers(int level) {
        return Math.min(BAND.length, Math.max(0, level) / TIER);
    }

    /** Ce que les paliers franchis accordent, pour une statistique secondaire donnee. */
    public double bonus(HeroBonus which, int level) {
        int reached = tiers(level);
        double total = 0.0;
        for (Tier tier : this.tiers) {
            if (tier.bonus() != which) {
                continue;
            }
            for (int t = 0; t < reached && t < tier.perTier().length; t++) {
                total += tier.perTier()[t];
            }
        }
        return total;
    }

    /** Toutes les statistiques secondaires de cette voie, dans l'ordre. */
    public Tier[] tiers() {
        return this.tiers;
    }

    // ------------------------------------------------------------- l'affichage

    public String tag() {
        return "HeroStat" + name();
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    public Component label() {
        return Component.translatable("hero.emeraldweapons." + this.key);
    }

    /**
     * Ce que la voie donne, en clair.
     *
     * Le texte vient de la traduction et non du code : chaque voie mesure autre
     * chose -- des degats, de l'armure, des points de vie, un pourcentage -- et
     * une seule phrase generique les rendrait toutes illisibles.
     */
    public Component summary(int level) {
        return Component.translatable("hero.emeraldweapons." + this.key + ".effect",
                String.format(Locale.ROOT, "%.1f", value(level)));
    }

    /**
     * Ce que les paliers ont deja donne, en une ligne.
     *
     * On montre l'ACQUIS et non le prochain : un joueur qui repartit ses points
     * compare ce qu'il a, pas ce qu'il aurait. Le prochain palier se lit deja a
     * la distance qui l'en separe.
     */
    public Component tierSummary(int level) {
        Component line = null;
        for (Tier tier : this.tiers) {
            double got = bonus(tier.bonus(), level);
            if (got <= 0.0) {
                continue;
            }
            Component part = Component.translatable(
                    "hero.emeraldweapons.bonus." + tier.bonus().name().toLowerCase(Locale.ROOT),
                    String.format(Locale.ROOT, "%.1f", got));
            line = line == null ? part : line.copy().append(", ").append(part);
        }
        return line == null ? Component.translatable("hero.emeraldweapons.no_tier") : line;
    }

    /** Combien de niveaux restent avant le prochain palier. */
    public static int toNextTier(int level) {
        return TIER - level % TIER;
    }
}
