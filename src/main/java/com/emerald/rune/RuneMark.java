package com.emerald.rune;

import com.emerald.item.GearRarity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;

/**
 * Une rune complete : une famille, un rang, et les options qu'elle a tirees.
 *
 * LE RANG NE MULTIPLIE RIEN. Il decide du SCHEMA -- combien d'options et de
 * quels grades -- et c'est tout. Une option de grade C vaut la meme chose sur
 * une rune Utile et sur une Phenomenale ; ce qui change, c'est que la
 * Phenomenale en porte six dont deux S.
 *
 * C'est la structure reelle de NosTale et elle est bien meilleure que celle que
 * j'avais posee : deux runes de meme rang ne different plus par un chiffre mais
 * par leur COMPOSITION, et une Legendaire aux mauvaises statistiques peut valoir
 * moins qu'une Excellente bien tombee. C'est cette incertitude qui donne envie
 * d'en ramasser une de plus.
 */
public record RuneMark(RuneFamily family, int rank, List<Option> options) {

    /** Une ligne de la rune : la statistique, son grade, et sa valeur tiree. */
    public record Option(Rune stat, RuneGrade grade, double value) {

        public static final Codec<Option> CODEC = RecordCodecBuilder.create(i -> i.group(
                Rune.CODEC.fieldOf("stat").forGetter(Option::stat),
                StringRepresentable.fromEnum(RuneGrade::values)
                        .fieldOf("grade").forGetter(Option::grade),
                Codec.DOUBLE.fieldOf("value").forGetter(Option::value)
        ).apply(i, Option::new));

        public static final StreamCodec<ByteBuf, Option> STREAM_CODEC = StreamCodec.composite(
                Rune.STREAM_CODEC, Option::stat,
                ByteBufCodecs.idMapper(i -> RuneGrade.values()[i], RuneGrade::ordinal),
                Option::grade,
                ByteBufCodecs.DOUBLE, Option::value,
                Option::new);

        /**
         * Ou tombe la valeur dans sa fourchette, de zero a un.
         *
         * « 1,04 » ne dit rien tant qu'on ignore si c'est un bon jet. Le
         * pourcentage, lui, se lit sans connaitre les bornes.
         */
        public double quality() {
            double a = this.stat.min(this.grade);
            double b = this.stat.max(this.grade);
            return b <= a ? 1.0 : Math.max(0.0, Math.min(1.0, (this.value - a) / (b - a)));
        }
    }

    public static final Codec<RuneMark> CODEC = RecordCodecBuilder.create(i -> i.group(
            StringRepresentable.fromEnum(RuneFamily::values)
                    .fieldOf("family").forGetter(RuneMark::family),
            Codec.INT.fieldOf("rank").forGetter(RuneMark::rank),
            Option.CODEC.listOf().fieldOf("options").forGetter(RuneMark::options)
    ).apply(i, RuneMark::new));

    public static final Codec<List<RuneMark>> LIST_CODEC = CODEC.listOf();

    public static final StreamCodec<ByteBuf, RuneMark> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> RuneFamily.values()[i], RuneFamily::ordinal),
            RuneMark::family,
            ByteBufCodecs.VAR_INT, RuneMark::rank,
            Option.STREAM_CODEC.apply(ByteBufCodecs.list()), RuneMark::options,
            RuneMark::new);

    public static final StreamCodec<ByteBuf, List<RuneMark>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());

    /**
     * Le schema d'options par rang, releve sur NosTale (ligne « Complete »).
     *
     * L'indice zero est le rang NORMAL : aucune option, de sorte qu'une rune
     * sans qualite ne donne rien plutot qu'une valeur par defaut qu'on croirait
     * acquise.
     *
     * LE RANG HUIT EST NOTRE AJOUT. Le releve donne le meme schema au sept et
     * au huit (CBAAS dans les deux cas), ce qui rendrait chez nous le
     * Phenomenal strictement inutile -- il est deja le plus dur a obtenir. On
     * lui ajoute donc un second S. C'est le seul endroit ou l'on s'ecarte de la
     * source, et c'est parce qu'elle nous laisserait un rang vide.
     */
    private static final String[] PATTERN = {
            "", "C", "CC", "CB", "CBB", "CBA", "CBAA", "CBAAS", "CBAASS"
    };

    public static String pattern(int rank) {
        return PATTERN[Math.max(0, Math.min(PATTERN.length - 1, rank))];
    }

    /**
     * Tire une rune neuve.
     *
     * DEUX CONTRAINTES, et l'ordre dans lequel on les honore compte.
     *
     * D'abord, chaque option a une FOURCHETTE de grades, plancher et plafond :
     * une case C ne recoit que les options de base, une case S ne recoit que
     * les options exceptionnelles. On remplit du plus bas au plus haut -- l'ordre
     * du schema -- et l'on tire sans remise, si bien qu'aucune case ne peut
     * rester vide : chaque famille garde au moins autant d'options par grade
     * que le schema le plus exigeant en demande (verifie par le banc d'essai).
     *
     * Ensuite, on tire SANS REMISE : une rune ne porte jamais deux fois la meme
     * ligne. Deux Tranchants sur la meme pierre s'additionneraient en silence et
     * vaudraient mieux que n'importe quelle combinaison variee, ce qui
     * supprimerait le choix que le systeme est cense offrir.
     */
    public static RuneMark roll(RuneFamily family, int rank, RandomSource random) {
        String shape = pattern(rank);
        List<Rune> pool = new ArrayList<>(Rune.of(family));
        // LE NOMBRE D'OPTIONS EST TIRE : entre le schema moins deux (une au
        // moins) et le schema entier. Une Legendaire (cinq) en a trois, quatre
        // ou cinq ; une Phenomenale (six) en a quatre a six.
        int most = shape.length();
        int least = Math.max(1, most - 2);
        int count = least + random.nextInt(most - least + 1);
        // LES GRADES SONT TIRES SANS REMISE DANS LE VIVIER DU RANG : le
        // schema « CBAAS » n'est plus une suite imposee mais cinq lettres dont
        // on prend `count`. Le S n'est donc plus garanti, il est possible.
        List<Character> letters = new ArrayList<>();
        for (char c : shape.toCharArray()) {
            letters.add(c);
        }
        List<Option> options = new ArrayList<>(count);

        for (int i = 0; i < count && !letters.isEmpty(); i++) {
            RuneGrade grade = RuneGrade.of(letters.remove(random.nextInt(letters.size())));
            List<Rune> eligible = new ArrayList<>();
            for (Rune candidate : pool) {
                // PLANCHER ET PLAFOND : une case S ne recoit que ce qui a le
                // droit d'y etre. C'est ce qui la rend precieuse -- des degats
                // critiques en S, que NosTale ne fait jamais, la banaliseraient.
                if (candidate.allows(grade)) {
                    eligible.add(candidate);
                }
            }
            if (eligible.isEmpty()) {
                break;             // catalogue trop maigre : on s'arrete net
            }
            Rune stat = eligible.get(random.nextInt(eligible.size()));
            pool.remove(stat);
            options.add(new Option(stat, grade, stat.roll(grade, random)));
        }
        return new RuneMark(family, rank, List.copyOf(options));
    }

    public GearRarity rarity() {
        GearRarity[] all = GearRarity.values();
        return all[Math.max(0, Math.min(all.length - 1, this.rank))];
    }

    /** Ce que cette rune donne pour une statistique donnee. */
    public double value(Rune stat) {
        double sum = 0.0;
        for (Option option : this.options) {
            if (option.stat() == stat) {
                sum += option.value();
            }
        }
        return sum;
    }

    /** Le nom colore : « Rune d'arme Legendaire ». */
    public Component label() {
        GearRarity rarity = rarity();
        return Component.translatable("rune.emeraldweapons.named",
                        Component.translatable("rune.emeraldweapons.family."
                                + this.family.name().toLowerCase(java.util.Locale.ROOT)
                                + ".short"),
                        rarity.label())
                .withStyle(style -> style.withColor(rarity.colour()).withItalic(false));
    }
}
