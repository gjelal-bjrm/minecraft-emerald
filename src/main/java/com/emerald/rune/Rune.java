package com.emerald.rune;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Le catalogue des OPTIONS qu'une rune peut porter : dix-sept pour l'arme,
 * neuf pour l'armure.
 *
 * Une rune n'est pas une option, elle en contient plusieurs -- voir
 * {@link RuneMark}. Cette enumeration ne decrit donc pas des objets mais les
 * lignes qui peuvent apparaitre dessus.
 *
 * CHAQUE OPTION A UN GRADE MINIMAL, et c'est la seconde chose que j'avais
 * manquee. Le grade n'est pas tire au sort et colle sur n'importe quelle
 * ligne : il appartient a la ligne. « Attaque augmentee » est une option de
 * base et se voit partout ; « Ravage » est une option exceptionnelle et
 * n'apparait que dans un emplacement S, c'est-a-dire sur une rune Legendaire ou
 * Phenomenale.
 *
 * Un emplacement accepte donc toute option dont le grade minimal lui est
 * inferieur ou egal : un emplacement S peut recevoir une option de base, mais
 * un emplacement C ne recevra jamais de Ravage. C'est ce qui fait qu'un rang
 * eleve ne donne pas seulement PLUS, mais donne acces a ce qu'on ne voit nulle
 * part ailleurs.
 *
 * LES BORNES SONT CELLES DU GRADE C. Le grade multiplie
 * ({@link RuneGrade#factor}) ; le rang de rarete, lui, ne touche jamais une
 * valeur -- il decide du schema. Les vraies bornes de NosTale ne sont pas
 * publiques (le releve les donne explicitement « inconnu » : elles ne se voient
 * qu'en jeu, apres identification), celles-ci sont donc les notres.
 */
public enum Rune implements StringRepresentable {

    // ----------------------------------------- arme : l'offensif permanent

    /** Degats d'arme. L'option de base, celle qu'on voit partout. */
    TRANCHANT(RuneFamily.WEAPON, RuneGrade.C, 0xFF7A5C, 0.60, 1.20),
    /**
     * Probabilite de coup critique, en pour cent.
     *
     * Elle s'ajoute a celle des paliers d'Attaque de la fiche du Heros et passe
     * par le meme calcul : deux systemes qui produiraient chacun leur critique
     * donneraient deux jets par coup, et un joueur ne saurait plus lequel vient
     * de tomber.
     */
    CHANCE(RuneFamily.WEAPON, RuneGrade.C, 0xFFD24A, 0.80, 1.60),
    /** Degats critiques, en pour cent. */
    FUREUR(RuneFamily.WEAPON, RuneGrade.C, 0xFF4D6D, 3.00, 6.00),
    /** Vitesse d'attaque. */
    CADENCE(RuneFamily.WEAPON, RuneGrade.B, 0xFFC46B, 0.05, 0.11),
    /** Portee d'attaque, en blocs. */
    ALLONGE(RuneFamily.WEAPON, RuneGrade.B, 0x9CE8FF, 0.12, 0.25),
    /** Part de l'armure adverse ignoree, en pour cent. */
    PERCEE(RuneFamily.WEAPON, RuneGrade.A, 0xE8E8F0, 2.00, 4.00),
    /**
     * SL : des NIVEAUX offerts dans une voie de la fiche du Heros.
     *
     * C'est le pont entre les deux systemes, et c'est le releve NosTale qui me
     * l'a appris -- « SL Attaque 17 » ne donne pas dix-sept points d'attaque
     * mais dix-sept NIVEAUX dans la voie. Ces niveaux-la ne se paient pas : ils
     * s'ajoutent par-dessus ce qu'on a achete, et peuvent pousser une voie
     * au-dela du centieme, que l'on ne peut pas atteindre autrement.
     *
     * C'est de loin l'option la plus forte du catalogue, parce qu'elle
     * rapporte sur une echelle ou chaque niveau coute jusqu'a dix points. Elle
     * ne se voit donc jamais en dessous du grade B.
     */
    SL_ATTAQUE(RuneFamily.WEAPON, RuneGrade.B, 0xFF9C4A, 3.00, 5.00),
    /** SL : des niveaux dans la voie Element. */
    SL_ELEMENT(RuneFamily.WEAPON, RuneGrade.B, 0xE478FF, 3.00, 5.00),

    /**
     * Degats totaux, en pour cent -- l'option la plus recherchee.
     *
     * Elle repond aux « degats relatifs » de NosTale, que le releve donne comme
     * la seule option S vraiment convoitee. Un pourcentage sur le total vaut
     * mieux qu'un ajout plat parce qu'il multiplie TOUT le reste : c'est
     * exactement ce qui doit rendre un emplacement S desirable.
     */
    RAVAGE(RuneFamily.WEAPON, RuneGrade.S, 0xFF9C30, 1.20, 2.60),

    // -------------------------------------- armure : le defensif permanent

    /** Points d'armure. */
    CARAPACE(RuneFamily.ARMOR, RuneGrade.C, 0xB0C4FF, 0.35, 0.70),
    /** Reduction des degats critiques subis, en pour cent. */
    EGIDE(RuneFamily.ARMOR, RuneGrade.C, 0x6BE0FF, 2.00, 4.50),
    /** Points de vie maximaux. */
    ENDURANCE(RuneFamily.ARMOR, RuneGrade.B, 0x78E8AE, 0.60, 1.30),
    /** Probabilite d'annuler un coup, en pour cent. */
    ESQUIVE(RuneFamily.ARMOR, RuneGrade.B, 0xC0E8FF, 0.60, 1.20),
    /** Resistance de l'armure, qui la fait tenir face aux gros coups. */
    ABSORPTION(RuneFamily.ARMOR, RuneGrade.A, 0xC9A26B, 0.20, 0.45),
    /** Vie rendue par seconde. Le releve la donne en S : c'est la plus forte. */
    REGENERATION(RuneFamily.ARMOR, RuneGrade.S, 0x9CFF8C, 0.10, 0.22),
    /** Toutes les defenses, en pour cent -- l'autre option exceptionnelle. */
    SAUVEGARDE(RuneFamily.ARMOR, RuneGrade.S, 0xD6D6C0, 1.50, 3.20),
    /** SL : des niveaux dans la voie Defense. Voir {@link #SL_ATTAQUE}. */
    SL_DEFENSE(RuneFamily.ARMOR, RuneGrade.B, 0x7DB8FF, 3.00, 5.00),
    /** SL : des niveaux dans la voie Vitalite. */
    SL_VITALITE(RuneFamily.ARMOR, RuneGrade.B, 0x9CFF8C, 3.00, 5.00),

    // ------------------------ arme, suite : les effets a DECLENCHEMENT
    //
    // Elles etaient dans une famille a part, et elles n'avaient rien a y faire.
    // Le releve NosTale range la syncope et le saignement parmi les options
    // d'ARME, en grade C, et la regeneration par victoire en grade B : ce sont
    // bien des runes d'arme, simplement conditionnelles.
    //
    // Elles rejoignent donc la famille, ce qui lui donne dix-sept options -- et
    // c'est tant mieux : une rune d'arme peut desormais tomber franchement
    // offensive ou franchement opportuniste, et deux Legendaires ne se
    // ressemblent plus du tout.

    /** Probabilite, en pour cent, de clouer la cible sur place. */
    SYNCOPE(RuneFamily.WEAPON, RuneGrade.C, 0xD6D6C0, 1.00, 2.40),
    /** Probabilite, en pour cent, de faire saigner la cible. */
    SAIGNEE(RuneFamily.WEAPON, RuneGrade.C, 0xB03040, 1.50, 3.20),
    /** A la mise a mort : vie rendue. */
    CUREE(RuneFamily.WEAPON, RuneGrade.B, 0xFF4D6D, 0.60, 1.30),
    /** A la mise a mort : recharges effacees. */
    AUBAINE(RuneFamily.WEAPON, RuneGrade.B, 0xC77DFF, 3.00, 6.00),
    /** Sous trente pour cent de vie : degats ajoutes, en pour cent. */
    ACHARNEMENT(RuneFamily.WEAPON, RuneGrade.A, 0xFF9C30, 3.00, 6.00),
    /** Entoure de trois ennemis ou plus : armure ajoutee. */
    CERNE(RuneFamily.WEAPON, RuneGrade.A, 0x6BE0FF, 0.50, 1.00),
    /** Probabilite, en pour cent, qu'un coup frappe tout ce qui entoure la cible. */
    CATACLYSME(RuneFamily.WEAPON, RuneGrade.S, 0xB98CFF, 1.20, 2.80),
    /**
     * SL TOTAL : des niveaux dans les QUATRE voies a la fois.
     *
     * Le releve la donne en S et la plafonne bien plus bas que les SL
     * individuelles -- onze contre dix-sept -- et c'est logique : elle rapporte
     * quatre fois. On la garde donc modeste, et exclusivement au grade S.
     *
     * Elle est en S et n'apparait donc que sur une rune Legendaire ou
     * Phenomenale -- ce qui est la place d'une option qui touche a tout.
     */
    SL_TOTAL(RuneFamily.WEAPON, RuneGrade.S, 0xFFD24A, 0.80, 1.60);

    public static final Codec<Rune> CODEC = StringRepresentable.fromEnum(Rune::values);

    public static final StreamCodec<ByteBuf, Rune> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], Rune::ordinal);

    private final RuneFamily family;
    private final RuneGrade floor;
    private final int colour;
    private final double low;
    private final double high;
    private final String id;

    Rune(RuneFamily family, RuneGrade floor, int colour, double low, double high) {
        this.family = family;
        this.floor = floor;
        this.colour = colour;
        this.low = low;
        this.high = high;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public RuneFamily family() {
        return this.family;
    }

    /** Le grade en dessous duquel cette option n'apparait jamais. */
    public RuneGrade floor() {
        return this.floor;
    }

    public int colour() {
        return this.colour;
    }

    public double min(RuneGrade grade) {
        return this.low * grade.factor();
    }

    public double max(RuneGrade grade) {
        return this.high * grade.factor();
    }

    /**
     * Tire la valeur d'une option de ce grade.
     *
     * UNIFORME entre les deux bornes. Le releve NosTale ne dit pas si le vrai
     * tirage penche -- la case est explicitement « inconnu » -- et faire pencher
     * le notre sans le savoir reviendrait a empiler une seconde loterie sur
     * celle du grade. Deux hasards superposes donnent un systeme ou l'on
     * n'obtient jamais rien de bon meme quand on obtient quelque chose de rare.
     */
    public double roll(RuneGrade grade, RandomSource random) {
        double a = min(grade);
        double b = max(grade);
        return a + random.nextDouble() * (b - a);
    }

    /** Toutes les options d'une famille. */
    public static List<Rune> of(RuneFamily family) {
        List<Rune> out = new ArrayList<>();
        for (Rune rune : values()) {
            if (rune.family == family) {
                out.add(rune);
            }
        }
        return out;
    }

    public String translationKey() {
        return "rune.emeraldweapons." + this.id;
    }

    /** L'effet chiffre, tel qu'il s'affiche dans l'infobulle. */
    public Component effect(double value) {
        return Component.translatable(translationKey() + ".effect",
                String.format(Locale.ROOT, "%.2f", value));
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
