package com.emerald.rune;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Le grade d'une option, de C a S.
 *
 * C'EST LA PIECE QUE J'AVAIS MANQUEE. Je croyais qu'une rune portait UNE
 * statistique dont le rang multipliait la valeur. Le vrai systeme de NosTale
 * fait tout autrement : une rune porte PLUSIEURS options, chacune d'un grade,
 * et le rang de rarete ne change pas la force d'une option -- il change
 * COMBIEN on en a et DE QUEL GRADE.
 *
 * La difference n'est pas cosmetique. Avec ma version, deux runes du meme rang
 * ne differaient que par un chiffre ; avec celle-ci, elles different par leur
 * composition, et une Legendaire a cinq options peut se reveler moins utile
 * qu'une Excellente a trois si les statistiques tombent mal. C'est cette
 * incertitude-la qui donne envie d'en ramasser encore une.
 *
 * Un grade S vaut deux fois et demie un grade C. L'ecart est franc a dessein :
 * il faut qu'on lise « S » et qu'on sache immediatement que la rune vaut le
 * detour.
 */
public enum RuneGrade implements net.minecraft.util.StringRepresentable {

    C(1.00, ChatFormatting.GRAY),
    B(1.40, ChatFormatting.GREEN),
    A(1.90, ChatFormatting.AQUA),
    S(2.60, ChatFormatting.LIGHT_PURPLE);

    private final double factor;
    private final ChatFormatting colour;

    RuneGrade(double factor, ChatFormatting colour) {
        this.factor = factor;
        this.colour = colour;
    }

    /** Ce que le grade multiplie. */
    public double factor() {
        return this.factor;
    }

    public ChatFormatting colour() {
        return this.colour;
    }

    public Component label() {
        return Component.literal(name()).withStyle(this.colour);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Le grade nomme par sa lettre, ou C si la lettre est inconnue. */
    public static RuneGrade of(char letter) {
        for (RuneGrade grade : values()) {
            if (grade.name().charAt(0) == letter) {
                return grade;
            }
        }
        return C;
    }
}
