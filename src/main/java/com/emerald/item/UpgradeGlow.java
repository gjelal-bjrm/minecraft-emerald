package com.emerald.item;

import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Ce que l'amelioration MONTRE : couleur, ampleur et rythme de l'aura, cran par cran.
 *
 * LE BAREME A CHANGE DE PALETTE. La premiere version suivait NosTale -- rouge,
 * vert, blanc, deux fois -- et le joueur l'a jugee laide en 3D : le blanc pur
 * des +7 et +10 ecrasait l'armure, et le cycle ne disait rien du mode. On
 * prend la palette du mode, celle des raretes et des meteos :
 *
 *    +1 a +4   blanc froid, trait de plus en plus epais     -- une PRESENCE
 *    +5 or, +6 turquoise, +7 violet                          -- une COULEUR
 *    +8 or, +9 turquoise, +10 prismatique                    -- DOUBLE contour,
 *                                                              et la pulsation
 *
 * Deux tours de la meme palette, comme avant : c'est l'AMPLEUR qui separe un
 * +5 d'un +8 -- double contour, pulsation qui monte le long du corps -- et
 * la couleur qui situe dans le tour. Le +10 ne prend aucune couleur fixe : sa
 * teinte tourne lentement, c'est l'Arcencium lui-meme.
 *
 * Tout passe par ici, cote serveur comme cote client : les particules de
 * l'arme, le halo de la lame, le lisere et la gravure de l'armure lisent la
 * meme table. Deux tables auraient fini par diverger.
 */
public final class UpgradeGlow {

    /**
     * Une aura.
     *
     * @param colour    la couleur de base (blanc pour le prismatique)
     * @param intensity de zero a un
     * @param large     le second tour : double contour et pulsation
     * @param prismatic la teinte tourne avec le temps
     * @param width     epaisseur du lisere : 1 fin, 2 moyen, 3 large
     */
    public record Aura(int colour, float intensity, boolean large, boolean prismatic, int width) {

        public float red() {
            return ((this.colour >> 16) & 0xFF) / 255.0F;
        }

        public float green() {
            return ((this.colour >> 8) & 0xFF) / 255.0F;
        }

        public float blue() {
            return (this.colour & 0xFF) / 255.0F;
        }

        /** La couleur A CET INSTANT : fixe, ou en rotation lente pour le prismatique. */
        public int colourAt(float time) {
            if (!this.prismatic) {
                return this.colour;
            }
            // un tour complet en six secondes, saturation douce : lumiere, pas neon
            float hue = (time / 120.0F) % 1.0F;
            return java.awt.Color.HSBtoRGB(hue, 0.55F, 1.0F) & 0xFFFFFF;
        }

        /** Les trois composantes de la couleur a cet instant, de zero a un. */
        public float[] tint(float time) {
            int c = this.colourAt(time);
            return new float[]{((c >> 16) & 0xFF) / 255.0F, ((c >> 8) & 0xFF) / 255.0F,
                    (c & 0xFF) / 255.0F};
        }
    }

    private static final int FAINT = 0xE6EEFF;
    private static final int GOLD = 0xFFD36B;
    private static final int TEAL = 0x5FE8D8;
    private static final int VIOLET = 0xB98CFF;
    private static final int PRISM = 0xFFFFFF;

    private static final Aura NONE = new Aura(0, 0.0F, false, false, 0);

    /** Duree d'un aller de la pulsation, des pieds a la tete, en ticks. */
    public static final float PULSE_PERIOD = 44.0F;

    private UpgradeGlow() {
    }

    /** L'aura d'un cran. */
    public static Aura of(int level) {
        return switch (Math.max(0, Math.min(Upgrade.MAX, level))) {
            case 0 -> NONE;
            case 1 -> new Aura(FAINT, 0.40F, false, false, 1);
            case 2 -> new Aura(FAINT, 0.55F, false, false, 1);
            case 3 -> new Aura(FAINT, 0.55F, false, false, 2);
            case 4 -> new Aura(FAINT, 0.70F, false, false, 2);
            case 5 -> new Aura(GOLD, 0.85F, false, false, 2);
            case 6 -> new Aura(TEAL, 0.85F, false, false, 2);
            case 7 -> new Aura(VIOLET, 0.85F, false, false, 2);
            case 8 -> new Aura(GOLD, 1.0F, true, false, 3);
            case 9 -> new Aura(TEAL, 1.0F, true, false, 3);
            default -> new Aura(PRISM, 1.0F, true, true, 3);
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

    /**
     * LA PULSATION : une onde qui monte le long du corps.
     *
     * Pas une respiration -- une respiration a l'unisson clignote, et quatre
     * respirations decalees font une lumiere qui hesite. Ici une seule onde
     * part des pieds et gagne la tete en un peu plus de deux secondes ; chaque
     * piece s'allume a son passage puis retombe. C'est la vague de la lame,
     * portee au corps. {@code height} est la place de la piece sur le corps,
     * de zero (pieds) a un (tete).
     */
    public static float pulse(float height, float time) {
        float p = (time % PULSE_PERIOD) / PULSE_PERIOD;   // 0 -> 1, en boucle
        float d = Math.abs(p - height * 0.8F);            // la tete a 0,8 : l'onde finit visible
        return Mth.clamp(1.0F - d * 4.5F, 0.0F, 1.0F);
    }
}
