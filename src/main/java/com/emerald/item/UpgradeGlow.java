package com.emerald.item;

import net.minecraft.world.item.ItemStack;

/**
 * Ce que l'amelioration MONTRE : la couleur et l'ampleur de l'aura, cran par cran.
 *
 * Le bareme suit NosTale, tel que le joueur l'a decrit et que le releve le
 * confirme :
 *
 *    +5 rouge, +6 vert, +7 blanc          -- version COURTE
 *    +8 rouge, +9 vert, +10 blanc         -- version LONGUE, constante, large
 *
 * Le cycle rouge-vert-blanc se repete donc deux fois, et c'est l'AMPLEUR qui
 * separe les deux tours : un +8 ne se distingue pas d'un +5 par sa couleur mais
 * par la taille de ce qui l'entoure. C'est ce qui rend la lecture immediate --
 * on voit d'abord si l'aura est grosse, puis de quelle couleur elle est, et ces
 * deux questions suffisent a situer l'objet entre +5 et +10.
 *
 * Les crans +1 a +4 n'ont pas de couleur documentee. Ils recoivent une lueur
 * BLANCHE FAIBLE qui grandit : on ne leur invente pas une teinte, on leur donne
 * une presence. Un +3 doit se voir, sans qu'on puisse le confondre avec un +7.
 *
 * Tout passe par ici, cote serveur comme cote client : les particules de
 * l'arme et le calque de l'armure lisent la meme table. Deux tables auraient
 * fini par diverger, et l'arme aurait brille rouge pendant que l'armure
 * brillait orange.
 */
public final class UpgradeGlow {

    /** Une aura : sa couleur, son intensite de zero a un, et si elle est large. */
    public record Aura(int colour, float intensity, boolean large) {

        public float red() {
            return ((this.colour >> 16) & 0xFF) / 255.0F;
        }

        public float green() {
            return ((this.colour >> 8) & 0xFF) / 255.0F;
        }

        public float blue() {
            return (this.colour & 0xFF) / 255.0F;
        }
    }

    private static final int RED = 0xFF3A2E;
    private static final int GREEN = 0x4CFF6A;
    private static final int WHITE = 0xF4F6FF;
    /** La lueur des petits crans : un blanc froid, sans teinte. */
    private static final int FAINT = 0xD8E4FF;

    private static final Aura NONE = new Aura(0, 0.0F, false);

    private UpgradeGlow() {
    }

    /** L'aura d'un cran. */
    public static Aura of(int level) {
        return switch (Math.max(0, Math.min(Upgrade.MAX, level))) {
            case 0 -> NONE;
            case 1 -> new Aura(FAINT, 0.12F, false);
            case 2 -> new Aura(FAINT, 0.18F, false);
            case 3 -> new Aura(FAINT, 0.25F, false);
            case 4 -> new Aura(FAINT, 0.33F, false);
            case 5 -> new Aura(RED, 0.55F, false);
            case 6 -> new Aura(GREEN, 0.55F, false);
            case 7 -> new Aura(WHITE, 0.55F, false);
            case 8 -> new Aura(RED, 1.0F, true);
            case 9 -> new Aura(GREEN, 1.0F, true);
            default -> new Aura(WHITE, 1.0F, true);
        };
    }

    /** L'aura d'une piece. */
    public static Aura of(ItemStack stack) {
        return of(Upgrade.of(stack));
    }

    /** Vrai si la piece a quelque chose a montrer. */
    public static boolean glows(ItemStack stack) {
        return Upgrade.of(stack) > 0;
    }
}
