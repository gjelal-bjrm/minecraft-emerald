package com.emerald.weather;

import java.util.ArrayList;
import java.util.List;

/**
 * La forme d'une fissure, partagee entre le serveur qui CREUSE et le client
 * qui DESSINE : les deux doivent suivre exactement la meme ligne, sinon la
 * fente annoncee et le trou qui s'ouvre ne coincideraient pas.
 *
 * Une fissure est une ligne principale et, souvent, une ou deux ramifications
 * qui s'en ecartent en biais -- c'est ce qui la fait ressembler a une vraie
 * craquelure plutot qu'a une tranchee. Chaque ligne est brisee : des points le
 * long d'une direction, courbes lentement, dentelures fines, et effiles aux
 * bouts. Tout est tire d'un bruit seede sur la position : rien ne transite
 * d'autre que le centre, la direction, la longueur et la largeur.
 *
 * Une fissure nait au centre et se propage vers ses bouts, d'abord comme une
 * fente dessinee (l'annonce), puis comme un vrai trou. Les bouts ne s'ouvrent
 * jamais : ils restent des fentes, comme une vraie fissure finit en cheveu.
 */
public final class FissureShape {

    /** Points le long d'une ligne. */
    public static final int POINTS = 18;
    /** Ticks pour que la fente annoncee se propage du centre aux bouts. */
    public static final int OPEN_TICKS = 12;
    /** Ticks avant que le sol ne cede, une fois la fente annoncee. */
    public static final int COLLAPSE_AT = 30;
    /** Ticks que met l'effondrement a courir du centre aux bouts. */
    public static final int CARVE_TICKS = 24;
    /** Part de chaque ligne qui s'ouvre vraiment : au-dela, la fente reste un trait. */
    public static final double CARVED_SPAN = 0.82;

    private FissureShape() {
    }

    /**
     * Une ligne de fissure : la principale, ou une ramification.
     *
     * Une ramification part d'un point de la principale, plus courte, plus
     * etroite, moins profonde, large a la jonction et effilee au bout.
     */
    public record Line(double x, double z, float dir, float length, float width, int depth,
                       long seed, boolean branch) {

        /** Le point i de la ligne, en coordonnees du monde : {x, z}. */
        public double[] point(int i) {
            double t = i / (double) (POINTS - 1);
            double u = (t - 0.5) * length;
            double edge = Math.sin(t * Math.PI);
            // deux echelles : une courbure lente, et la dentelure fine
            double bend = Math.sin(t * Math.PI * 1.3 + hash(seed, 3) * 6.0) * length * 0.05 * edge;
            double jag = (hash(seed, i) - 0.5) * (0.5 + width * 0.3) * edge;
            double off = bend + jag;
            double sx = -Math.sin(dir);
            double sz = Math.cos(dir);
            return new double[]{
                    x + Math.cos(dir) * u + sx * off,
                    z + Math.sin(dir) * u + sz * off};
        }

        /** Ou en est la propagation au point i : 0 la ou elle nait, 1 au bout. */
        public double progress(int i) {
            double t = i / (double) (POINTS - 1);
            return branch ? t : Math.abs(t - 0.5) * 2.0;
        }

        /** La largeur relative au point i : irreguliere, et effilee la ou la ligne finit. */
        public double taper(int i) {
            double t = i / (double) (POINTS - 1);
            double shape = branch ? Math.pow(1.0 - t, 0.7) : Math.pow(Math.sin(t * Math.PI), 0.6);
            double noise = 0.55 + 0.45 * hash(seed, i + 100);
            return shape * noise;
        }

        /** La profondeur relative au point i : plus profond au milieu, ou pres de la jonction. */
        public double depthAt(int i) {
            double t = i / (double) (POINTS - 1);
            return branch ? 0.3 + 0.5 * (1.0 - t) : 0.35 + 0.65 * Math.sin(t * Math.PI);
        }
    }

    public static long seed(double x, double z) {
        return Double.doubleToLongBits(x) * 31 + Double.doubleToLongBits(z) * 17;
    }

    public static double hash(long seed, int i) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /**
     * La principale et ses ramifications, les memes chez le serveur et chez
     * le client. Une craquelure en a rarement ; une grande fissure en a
     * presque toujours une, souvent deux.
     */
    public static List<Line> lines(double x, double z, float dir, float length, float width, int depth) {
        long seed = seed(x, z);
        List<Line> out = new ArrayList<>(3);
        Line main = new Line(x, z, dir, length, width, depth, seed, false);
        out.add(main);
        int branches = width < 1.4F
                ? (hash(seed, 7) < 0.30 ? 1 : 0)
                : 1 + (hash(seed, 8) < 0.5 ? 1 : 0);
        for (int k = 0; k < branches; k++) {
            // la jonction : pas trop pres des bouts de la principale
            int at = 4 + (int) (hash(seed, 10 + k) * (POINTS - 8));
            double[] origin = main.point(at);
            float side = hash(seed, 20 + k) < 0.5 ? -1.0F : 1.0F;
            float angle = dir + side * (float) (0.45 + 0.5 * hash(seed, 30 + k));
            float len = length * (float) (0.25 + 0.30 * hash(seed, 40 + k));
            // une ramification est centree a mi-longueur : son point 0 est la jonction
            double cx = origin[0] + Math.cos(angle) * len * 0.5;
            double cz = origin[1] + Math.sin(angle) * len * 0.5;
            out.add(new Line(cx, cz, angle, len, width * 0.55F, Math.max(1, (int) (depth * 0.6)),
                    seed + 1000L * (k + 1), true));
        }
        return out;
    }
}
