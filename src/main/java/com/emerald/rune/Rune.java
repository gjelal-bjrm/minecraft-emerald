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
 * Le catalogue des OPTIONS qu'une rune peut porter, cale sur le releve NosTale.
 *
 * CHAQUE OPTION A UNE FOURCHETTE DE GRADES, PAS SEULEMENT UN PLANCHER. C'est la
 * correction que le joueur a exigee, et elle etait due : « Degats critiques »
 * n'existe qu'en C, « Attaque augmentee » va de C a A et jamais en S, « Degats
 * relatifs » n'existe qu'en S. Mon premier catalogue n'avait qu'un plancher, et
 * le tirage pouvait poser des degats critiques dans une case S -- ce que
 * NosTale ne fait jamais. Une case S ne recoit donc que ce qui a le DROIT d'y
 * etre, et c'est ce qui la rend precieuse.
 *
 * CHAQUE OPTION A SES PROPRES VALEURS PAR GRADE. Le releve les donne : l'attaque
 * fait 95 en C, 142 en B, 190 en A ; les SL font 11 en B et 17 en A. Ce ne
 * sont pas les memes rapports d'une option a l'autre, et un multiplicateur
 * unique -- ce que j'avais -- les aplatissait tous. Les maxima ci-dessous
 * reprennent donc les rapports du releve, ramenes a l'echelle de Minecraft.
 *
 * LES MINIMA NE SONT PAS PUBLICS. Le releve les donne tous « inconnu » : ils ne
 * se voient qu'en jeu, apres identification. On prend donc soixante pour cent du
 * maximum de chaque grade, et c'est la seule invention de ce fichier. Elle est
 * signalee ici pour qu'on la remplace le jour ou l'on aura les vrais.
 *
 * Les options qui n'ont pas d'equivalent chez NosTale -- cadence, allonge,
 * cataclysme -- gardent des fourchettes de notre cru, dans le meme esprit.
 */
public enum Rune implements StringRepresentable {

    // ----------------------------------------- arme : l'offensif permanent

    /** Attaque augmentee : C a A, 95 / 142 / 190. L'option de base. */
    TRANCHANT(RuneFamily.WEAPON, RuneGrade.C, RuneGrade.A, 0.95, 1.42, 1.90, 0),
    /** Chance de critique : C seulement, 9. */
    CHANCE(RuneFamily.WEAPON, RuneGrade.C, RuneGrade.C, 6.0, 0, 0, 0),
    /** Degats critiques : C seulement, +39 a +57 % -- les chiffres donnes par le joueur. */
    FUREUR(RuneFamily.WEAPON, RuneGrade.C, RuneGrade.C, 57.0, 0, 0, 0),
    /** Vitesse d'attaque. Sans equivalent : de notre cru, B a A. */
    CADENCE(RuneFamily.WEAPON, RuneGrade.B, RuneGrade.A, 0, 0.09, 0.14, 0),
    /** Portee, en blocs. Sans equivalent : B a A. */
    ALLONGE(RuneFamily.WEAPON, RuneGrade.B, RuneGrade.A, 0, 0.20, 0.32, 0),
    /** Armure adverse ignoree. Sans equivalent : A seulement. */
    PERCEE(RuneFamily.WEAPON, RuneGrade.A, RuneGrade.A, 0, 0, 5.0, 0),
    /** SL Attaque : C a A -- des NIVEAUX de fiche, 9-10 / 11-13 / 14-17. */
    SL_ATTAQUE(RuneFamily.WEAPON, RuneGrade.C, RuneGrade.A,
            new double[]{9, 11, 14, 0}, new double[]{10, 13, 17, 0}),
    /** SL Element : C a A, memes niveaux. */
    SL_ELEMENT(RuneFamily.WEAPON, RuneGrade.C, RuneGrade.A,
            new double[]{9, 11, 14, 0}, new double[]{10, 13, 17, 0}),
    /** Degats relatifs : S seulement, 19. L'option la plus recherchee. */
    RAVAGE(RuneFamily.WEAPON, RuneGrade.S, RuneGrade.S, 0, 0, 0, 12.0),

    // ------------------------------------------- arme : le declenchement

    /** Syncope : C seulement, 4. */
    SYNCOPE(RuneFamily.WEAPON, RuneGrade.C, RuneGrade.C, 4.0, 0, 0, 0),
    /** Saignement leger : C seulement, 4. */
    SAIGNEE(RuneFamily.WEAPON, RuneGrade.C, RuneGrade.C, 4.0, 0, 0, 0),
    /** Regeneration HP par victoire : B a A, 142 / 190. */
    CUREE(RuneFamily.WEAPON, RuneGrade.B, RuneGrade.A, 0, 1.42, 1.90, 0),
    /** Recharges effacees a la mise a mort. Sans equivalent : B seulement. */
    AUBAINE(RuneFamily.WEAPON, RuneGrade.B, RuneGrade.B, 0, 6.0, 0, 0),
    /** Degats sous trente pour cent de vie. Sans equivalent : A seulement. */
    ACHARNEMENT(RuneFamily.WEAPON, RuneGrade.A, RuneGrade.A, 0, 0, 8.0, 0),
    /** Armure face a trois ennemis. Sans equivalent : A seulement. */
    CERNE(RuneFamily.WEAPON, RuneGrade.A, RuneGrade.A, 0, 0, 1.4, 0),
    /** Frappe tout autour de la cible. Sans equivalent : S seulement. */
    CATACLYSME(RuneFamily.WEAPON, RuneGrade.S, RuneGrade.S, 0, 0, 0, 4.0),
    /**
     * SL Generale : S seulement, 9 a 13 NIVEAUX dans LES QUATRE voies.
     *
     * L'option la plus forte du catalogue, et de loin -- le joueur la voulait a
     * cette hauteur. Elle vaut quatre fois une SL unique de grade A, ce qui est
     * le prix d'une case S dans une rune de rang 7 ou 8, ou le S n'est meme plus
     * garanti (voir RuneMark.roll).
     */
    SL_TOTAL(RuneFamily.WEAPON, RuneGrade.S, RuneGrade.S,
            new double[]{0, 0, 0, 9}, new double[]{0, 0, 0, 13}),

    // -------------------------------------- armure : le defensif permanent

    /** Defense : C a A, 66 / 114 / 190. */
    CARAPACE(RuneFamily.ARMOR, RuneGrade.C, RuneGrade.A, 0.66, 1.14, 1.90, 0),
    /** Reduction des critiques subis : C a A, d'apres les reductions 38 / 38 / 47. */
    EGIDE(RuneFamily.ARMOR, RuneGrade.C, RuneGrade.A, 3.8, 3.8, 4.7, 0),
    /** Max HP : B a A. */
    ENDURANCE(RuneFamily.ARMOR, RuneGrade.B, RuneGrade.A, 0, 1.3, 2.0, 0),
    /** Esquive : B a A. */
    ESQUIVE(RuneFamily.ARMOR, RuneGrade.B, RuneGrade.A, 0, 1.2, 1.8, 0),
    /** Resistance d'armure. Sans equivalent : A seulement. */
    ABSORPTION(RuneFamily.ARMOR, RuneGrade.A, RuneGrade.A, 0, 0, 0.5, 0),
    /** Recuperation HP en defense : S seulement. */
    REGENERATION(RuneFamily.ARMOR, RuneGrade.S, RuneGrade.S, 0, 0, 0, 0.30),
    /** Toutes les defenses en pour cent : S seulement. */
    SAUVEGARDE(RuneFamily.ARMOR, RuneGrade.S, RuneGrade.S, 0, 0, 0, 4.5),
    /** SL Defense : C a A, 9-10 / 11-13 / 14-17. */
    SL_DEFENSE(RuneFamily.ARMOR, RuneGrade.C, RuneGrade.A,
            new double[]{9, 11, 14, 0}, new double[]{10, 13, 17, 0}),
    /** SL HP/MP : C a A, memes niveaux. */
    SL_VITALITE(RuneFamily.ARMOR, RuneGrade.C, RuneGrade.A,
            new double[]{9, 11, 14, 0}, new double[]{10, 13, 17, 0});

    public static final Codec<Rune> CODEC = StringRepresentable.fromEnum(Rune::values);

    public static final StreamCodec<ByteBuf, Rune> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], Rune::ordinal);

    /**
     * La part du maximum ou commence la fourchette.
     *
     * LA SEULE INVENTION DE CE FICHIER : les minima de NosTale ne sont pas
     * publics. Soixante pour cent donne une fourchette assez large pour que deux
     * runes de meme grade se distinguent, assez etroite pour qu'un grade
     * superieur reste toujours meilleur qu'un tirage chanceux du grade
     * inferieur.
     */
    private static final double FLOOR = 0.68;      // 39 / 57 : l'exemple du joueur

    private final RuneFamily family;
    private final RuneGrade lowest;
    private final RuneGrade highest;
    /** Le maximum a chaque grade, dans l'ordre C, B, A, S ; zero hors fourchette. */
    private final double[] max;
    /**
     * Les minima DONNES, pour les options qui se comptent en niveaux.
     *
     * Nul partout ailleurs : on prend alors {@link #FLOOR} du maximum. Les SL
     * ne s'en contentent pas -- un niveau et demi n'existe pas, et le joueur a
     * donne les fourchettes exactes (3-5 / 6-9 / 10-13, et 9-13 pour la
     * Generale). Quand ce tableau est la, le tirage est ENTIER.
     */
    private final double[] mins;
    private final String id;

    Rune(RuneFamily family, RuneGrade lowest, RuneGrade highest,
         double maxC, double maxB, double maxA, double maxS) {
        this(family, lowest, highest, null, new double[]{maxC, maxB, maxA, maxS});
    }

    /** Une option qui se compte en NIVEAUX : minima donnes, tirage entier. */
    Rune(RuneFamily family, RuneGrade lowest, RuneGrade highest, double[] mins, double[] max) {
        this.family = family;
        this.lowest = lowest;
        this.highest = highest;
        this.max = max;
        this.mins = mins;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    /** Vrai si l'option se compte en niveaux de fiche : tirage entier. */
    public boolean levels() {
        return this.mins != null;
    }

    public RuneFamily family() {
        return this.family;
    }

    /** Le grade en dessous duquel cette option n'apparait jamais. */
    public RuneGrade floor() {
        return this.lowest;
    }

    /** Le grade au-dessus duquel cette option n'apparait jamais. */
    public RuneGrade ceiling() {
        return this.highest;
    }

    /** Vrai si l'option peut occuper une case de ce grade. */
    public boolean allows(RuneGrade grade) {
        return grade.ordinal() >= this.lowest.ordinal()
                && grade.ordinal() <= this.highest.ordinal();
    }

    public double max(RuneGrade grade) {
        return this.max[grade.ordinal()];
    }

    public double min(RuneGrade grade) {
        return this.mins != null ? this.mins[grade.ordinal()]
                : this.max[grade.ordinal()] * FLOOR;
    }

    /**
     * Tire la valeur d'une option de ce grade, uniformement dans sa fourchette.
     *
     * Le releve ne dit pas si le vrai tirage penche -- la case est « inconnu »
     * -- et faire pencher le notre sans le savoir reviendrait a empiler une
     * seconde loterie sur celle du grade.
     */
    public double roll(RuneGrade grade, RandomSource random) {
        double a = min(grade);
        double b = max(grade);
        if (levels()) {
            // ENTIER, et bornes comprises : « SL Attaque 12 », jamais 11,73.
            int lo = (int) Math.round(a);
            int hi = (int) Math.round(b);
            return hi <= lo ? lo : lo + random.nextInt(hi - lo + 1);
        }
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
                levels() ? String.valueOf((int) Math.round(value))
                        : String.format(Locale.ROOT, "%.2f", value));
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }
}
