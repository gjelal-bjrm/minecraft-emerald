package com.emerald.jak.gun;

import javax.annotation.Nullable;

/**
 * Les douze formes du Morph Gun de Jak 3, dans l'ordre des pickup-types du jeu
 * (26 a 37, game-info-h.gc:78-89) : l'ordinal est l'indice de la pose
 * gun-idle-* dans morph_gun.bin, et 26 + ordinal le type d'objet de Jak.
 *
 * UNE FORME N'EST PAS UN OBJET. Il n'y a qu'un objet, emeraldweapons:morph_gun ;
 * la forme courante vit dans son composant (MorphGunData). Changer d'arme,
 * c'est changer de pose du meme squelette.
 *
 * LES FAMILLES SUIVENT LA CROIX DE JAK 3 (target-gun.gc:1336-1497), transposee
 * sur les fleches : haut rouge, bas jaune, gauche bleu, droite sombre. Un
 * nouvel appui sur la famille tenue passe a l'arme suivante possedee.
 */
public enum GunForm {
    RED_1(Family.RED, 1, "scatter_gun", "gun-idle-red"),
    RED_2(Family.RED, 2, "wave_concussor", "gun-idle-red2"),
    RED_3(Family.RED, 3, "plasmite_rpg", "gun-idle-red3"),
    YELLOW_1(Family.YELLOW, 1, "blaster", "gun-idle-yellow"),
    YELLOW_2(Family.YELLOW, 2, "beam_reflexor", "gun-idle-yellow2"),
    YELLOW_3(Family.YELLOW, 3, "gyro_burster", "gun-idle-yellow3"),
    BLUE_1(Family.BLUE, 1, "vulcan_fury", "gun-idle-blue"),
    BLUE_2(Family.BLUE, 2, "arc_wielder", "gun-idle-blue2"),
    BLUE_3(Family.BLUE, 3, "needle_lazer", "gun-idle-blue3"),
    DARK_1(Family.DARK, 1, "peace_maker", "gun-idle-dark"),
    DARK_2(Family.DARK, 2, "mass_inverter", "gun-idle-dark2"),
    DARK_3(Family.DARK, 3, "super_nova", "gun-idle-dark3");

    /**
     * Une famille d'eco : sa reserve, sa couleur, sa fleche.
     *
     * LES CAPACITES sont celles du palier des deux ameliorations d'histoire de
     * Jak 3 (game-info.gc:72-146 ; base jaune 100, rouge 50, bleu 100, sombre 5,
     * fact-h.gc:169-176) : jaune 200, rouge 100, bleu 200, sombre 15. Avoir les
     * douze armes correspond a la fin du jeu, et la Super Nova exige 10 eco
     * sombres alors que la reserve de base n'en a que 5. Choix du plan, fait a
     * la place du joueur, a corriger s'il le souhaite.
     */
    public enum Family {
        RED("red", 100, 0xFFDC0C0C),
        YELLOW("yellow", 200, 0xFFFFD220),
        BLUE("blue", 200, 0xFF25AAFF),
        DARK("dark", 15, 0xFF6C1FD8);

        /** Le nom de la famille dans les animations du jeu (gun-gun-red-yellow...). */
        public final String jak;
        public final int capacity;
        /** Le reflet de famille du jeu (etude du modele), en ARGB. */
        public final int color;

        Family(String jak, int capacity, int color) {
            this.jak = jak;
            this.capacity = capacity;
            this.color = color;
        }

        public String translationKey() {
            return "item.emeraldweapons.morph_gun.family." + this.jak;
        }

        @Nullable
        public static Family byIndex(int index) {
            Family[] all = values();
            return index >= 0 && index < all.length ? all[index] : null;
        }
    }

    /** Les quatre armes de base (jalon A). */
    public static final int BASE_MASK = RED_1.bit() | YELLOW_1.bit() | BLUE_1.bit() | DARK_1.bit();

    /**
     * Les formes DONNEES A L'ARRIVEE : les armes de base, et les ameliorations deja
     * portees (rouges et jaunes). Provisoire, decision du joueur du 16 sept. : quand
     * les quetes des PNJ de Haven existeront, on n'arrivera plus qu'avec le Scatter
     * Gun, et les autres se gagneront ; d'ici la, tout ce qui est code s'essaie.
     */
    public static final int ARRIVAL_MASK = BASE_MASK | RED_2.bit() | RED_3.bit() | YELLOW_2.bit() | YELLOW_3.bit();

    public final Family family;
    /** 1, 2 ou 3 dans sa famille. */
    public final int rank;
    /** Identifiant stable : sauvegarde et cles de langue. */
    public final String id;
    /** La pose d'une clef de morph_gun.bin. */
    public final String pose;

    GunForm(Family family, int rank, String id, String pose) {
        this.family = family;
        this.rank = rank;
        this.id = id;
        this.pose = pose;
    }

    public int bit() {
        return 1 << this.ordinal();
    }

    /** Le pickup-type de Jak 3 (26 a 37). */
    public int pickupType() {
        return 26 + this.ordinal();
    }

    public String translationKey() {
        return "item.emeraldweapons.morph_gun.form." + this.id;
    }

    public static GunForm byOrdinal(int ordinal) {
        GunForm[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : RED_1;
    }

    public static GunForm byId(String id) {
        for (GunForm form : values()) {
            if (form.id.equals(id)) {
                return form;
            }
        }
        return RED_1;
    }

    public static GunForm of(Family family, int rank) {
        return values()[family.ordinal() * 3 + Math.max(0, Math.min(2, rank - 1))];
    }

    /**
     * Ce qu'un appui sur la fleche d'une famille donne, comme la croix de Jak 3.
     *
     * Sur la famille tenue : l'arme possedee suivante (1 -> 2 -> 3 -> 1), ou la
     * meme s'il n'y en a qu'une. Sur une autre famille : sa premiere arme
     * possedee. Null si le joueur n'en possede aucune.
     */
    @Nullable
    public static GunForm select(GunForm current, Family wanted, int owned) {
        if (current.family == wanted) {
            for (int step = 1; step <= 3; step++) {
                GunForm next = of(wanted, (current.rank - 1 + step) % 3 + 1);
                if ((owned & next.bit()) != 0) {
                    return next;
                }
            }
            return null;
        }
        for (int rank = 1; rank <= 3; rank++) {
            GunForm first = of(wanted, rank);
            if ((owned & first.bit()) != 0) {
                return first;
            }
        }
        return null;
    }

    /**
     * Le point « main droite » de la famille dans le repere de l'arme (m), mesure
     * sur les animations de garde de Jak (build/jak/gun/morph-gun.json, « mains »).
     * C'est ce point qui est pose sur la main du joueur.
     */
    public double[] rightHand() {
        return switch (this.family) {
            case RED -> new double[]{-0.041, -0.150, -0.204};
            case YELLOW -> new double[]{-0.021, -0.187, -0.230};
            case BLUE -> new double[]{0.045, 0.185, -0.541};
            case DARK -> new double[]{-0.040, -0.151, -0.202};
        };
    }

    /** Le point « main gauche », pour les mesures de prise (morph-gun.json). */
    public double[] leftHand() {
        return switch (this.family) {
            case RED -> new double[]{0.065, -0.075, 0.245};
            case YELLOW -> new double[]{0.084, -0.106, 0.194};
            case BLUE -> new double[]{0.245, 0.150, -0.052};
            case DARK -> new double[]{0.066, -0.082, 0.277};
        };
    }
}
