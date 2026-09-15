package com.emerald.jak.gun;

import javax.annotation.Nullable;

/**
 * Les matrices des os du Morph Gun pour une pose, une clef ou un instant d'une
 * transformation : le chemin que suivent le rendu en main ET le banc d'essai.
 *
 * LA RECETTE DE L'OUTIL (tools/jak_gun.py, lecon 2), en doubles :
 *     monde[i]     = monde[parent(i)] x TRS(i)
 *     habillage[i] = monde[i] x inverse_repos[i]
 *     sommet pose  = habillage[os du sommet] x position au repos
 * Le quaternion de chaque TRS est RENORMALISE ici, toujours : sur une clef
 * exacte, dans une pose, et apres le nlerp entre deux clefs. Les quaternions
 * du jeu ont une norme de 0,99994 a 1,00002 ; tels quels, ils deplacaient les
 * matrices des poses de 2,4e-4 (mesure de l'outil).
 *
 * CACHER, C'EST L'ECHELLE (lecon 3) : un triangle est cache quand l'echelle de
 * son os -- racine cubique de |det(habillage)| -- est sous ratio x celle de main.
 *
 * LE CHANGEMENT DE FAMILLE (plan corrige par la critique) : vers le rouge,
 * instantane ; vers le jaune, le bleu ou le sombre, COUPE DIRECTE sur la
 * premiere clef de gun-gun-red-yellow, -red-blue ou -red-dark, puis
 * l'animation. Dans Jak 3 le changement est instantane, cache par le geste de
 * Jak (gun-util.gc:768-770). Mesure du banc : a l'echelle de main de la cible
 * (paragraphe suivant), la premiere clef de red-yellow et de red-blue n'est qu'a
 * 1,6 cm de la pose rouge (red-blue : 50 triangles de visibilite differente) ;
 * les 0,52 m de l'etude venaient de l'echelle. Dans une famille, seules les cinq
 * ameliorations animees du jeu se jouent (gun-util.gc:540-576).
 *
 * L'ECHELLE DE MAIN RESTE CELLE DE LA FORME CIBLE pendant une transformation.
 * gun-gun-red-yellow part de l'echelle de l'arme rangee (main 1,43, celle de
 * gun-idle) et gun-gun-red-blue de 1,00, pour finir a 0,83 comme toutes les
 * poses (mesure sur le .bin, clef par clef) : dans Jak 3 ce sont des SORTIES
 * d'arme, pendant lesquelles l'echelle vient du joint de Jak (read-scale,
 * gun-util.gc:272-283). Jouees telles quelles en main, l'arme gonflait de 72 % a
 * la premiere image (planche-java-transformations.png, premier essai). On
 * remplace donc l'echelle locale de `main` (enfant de prejoint, identite ; son
 * origine reste en 0) par celle de la pose cible : toutes les pieces suivent,
 * seules leurs positions relatives s'animent.
 *
 * Une instance par fil (le rendu en garde une) : elle n'est pas partagee.
 */
public final class GunPose {

    private static final int TRS = 10;

    /** Les os des chargeurs, dans l'ordre de GunForm.Family (rouge, jaune, bleu, sombre). */
    public static final String[] MAGAZINES = {"magazineRed", "magazineYellow", "magazineBlue", "magazineDark"};

    public final JakGunModel model;
    /** Matrices monde, 3 x 4 par colonnes (m00 m10 m20, m01 m11 m21, m02 m12 m22, tx ty tz), par os. */
    public final double[] world;
    /** Matrices d'habillage, meme rangement. */
    public final double[] skin;
    /** Echelle de chaque os : racine cubique de |det(habillage)|. */
    public final double[] scale;
    private final double[] trs;
    private final double[] local = new double[12];
    private double limit;

    public GunPose(JakGunModel model) {
        this.model = model;
        this.world = new double[model.bones * 12];
        this.skin = new double[model.bones * 12];
        this.scale = new double[model.bones];
        this.trs = new double[model.bones * TRS];
        rest();
    }

    // ================================================================ choix de la pose

    /** La pose de repos (celle des triangles du .bin : habillage = identite). */
    public GunPose rest() {
        for (int i = 0; i < this.trs.length; i++) {
            this.trs[i] = this.model.restTrs[i];
        }
        compute();
        return this;
    }

    /** Une pose d'une clef par son indice dans le .bin. */
    public GunPose pose(int index) {
        int base = index * this.model.bones * TRS;
        for (int i = 0; i < this.trs.length; i++) {
            this.trs[i] = this.model.poses[base + i];
        }
        compute();
        return this;
    }

    /** La pose d'une forme ; la pose de repos si le .bin ne la connait pas. */
    public GunPose pose(GunForm form) {
        int index = this.model.poseIndex(form.pose);
        return index < 0 ? rest() : pose(index);
    }

    /**
     * Une transformation a l'instant donne : lerp de la translation et de
     * l'echelle, nlerp du quaternion (signe aligne), comme le glTF LINEAR.
     * Avant la premiere clef, la premiere ; apres la derniere, la derniere.
     */
    public GunPose anim(int index, double seconds) {
        return anim(index, seconds, null);
    }

    /**
     * Une transformation a l'instant donne, l'echelle de `main` prise dans la
     * pose de scaleOf (voir l'en-tete) ; null : l'animation telle quelle.
     */
    public GunPose anim(int index, double seconds, @Nullable GunForm scaleOf) {
        float[] times = this.model.animTimes[index];
        float[] keys = this.model.animKeys[index];
        int bones = this.model.bones;
        int k = 0;
        double f = 0.0;
        if (times.length > 1 && seconds > times[0]) {
            if (seconds >= times[times.length - 1]) {
                k = times.length - 1;
            } else {
                while (times[k + 1] <= seconds) {
                    k++;
                }
                f = (seconds - times[k]) / (times[k + 1] - times[k]);
            }
        }
        int a0 = k * bones * TRS;
        int b0 = Math.min(k + 1, times.length - 1) * bones * TRS;
        for (int j = 0; j < bones; j++) {
            int a = a0 + j * TRS;
            int b = b0 + j * TRS;
            int o = j * TRS;
            for (int c = 0; c < 3; c++) {
                this.trs[o + c] = keys[a + c] + (keys[b + c] - keys[a + c]) * f;
                this.trs[o + 7 + c] = keys[a + 7 + c] + (keys[b + 7 + c] - keys[a + 7 + c]) * f;
            }
            double dot = 0.0;
            for (int c = 3; c < 7; c++) {
                dot += (double) keys[a + c] * keys[b + c];
            }
            double sign = dot < 0.0 ? -1.0 : 1.0;
            for (int c = 3; c < 7; c++) {
                this.trs[o + c] = keys[a + c] + (sign * keys[b + c] - keys[a + c]) * f;
            }
        }
        int pose = scaleOf == null || this.model.main < 0 ? -1 : this.model.poseIndex(scaleOf.pose);
        if (pose >= 0) {
            int from = (pose * bones + this.model.main) * TRS;
            int to = this.model.main * TRS;
            for (int c = 7; c < 10; c++) {
                this.trs[to + c] = this.model.poses[from + c];
            }
        }
        compute();
        return this;
    }

    /**
     * La pose que montre une arme, a tant de tiques de son dernier changement.
     *
     * @param ticksSinceChange tiques de jeu depuis data.changeTick(), temps partiel compris
     */
    public GunPose show(GunForm previous, GunForm form, double ticksSinceChange) {
        Clip clip = clip(this.model, previous, form);
        if (clip.anim() >= 0 && ticksSinceChange < clip.seconds() * 20.0) {
            return anim(clip.anim(), Math.max(0.0, ticksSinceChange) / 20.0, form);
        }
        return pose(form);
    }

    public GunPose show(MorphGunData data, double ticksSinceChange) {
        return show(data.previous(), data.form(), ticksSinceChange);
    }

    /**
     * Retouche la pose courante : des os mis a l'echelle 0 (les chargeurs des
     * reserves vides, gun-util.gc:348-423 -- leurs triangles sont alors caches) et
     * un os tourne autour de son axe local Y (les cylindres de la Vulcan Fury, joint
     * barrel 0, gun-util.gc:315 et 914 ; leur Y local est l'axe du canon). A appeler
     * APRES show/pose/anim, qui reposent les TRS.
     *
     * @param hidden   un bit par os (indices 0 a 63)
     * @param spinBone l'os a tourner, ou -1
     * @param radians  l'angle de rotation
     */
    public GunPose tweak(long hidden, int spinBone, double radians) {
        boolean spin = spinBone >= 0 && spinBone < this.model.bones && radians != 0.0;
        if (hidden == 0L && !spin) {
            return this;
        }
        for (int b = 0; b < Math.min(64, this.model.bones); b++) {
            if (((hidden >>> b) & 1L) != 0L) {
                this.trs[b * TRS + 7] = 0.0;
                this.trs[b * TRS + 8] = 0.0;
                this.trs[b * TRS + 9] = 0.0;
            }
        }
        if (spin) {
            int o = spinBone * TRS;
            double qx = this.trs[o + 3];
            double qy = this.trs[o + 4];
            double qz = this.trs[o + 5];
            double qw = this.trs[o + 6];
            double s = Math.sin(radians * 0.5);
            double c = Math.cos(radians * 0.5);
            // q x r, r = rotation locale autour de Y (0, s, 0, c)
            this.trs[o + 3] = qx * c - qz * s;
            this.trs[o + 4] = qw * s + qy * c;
            this.trs[o + 5] = qx * s + qz * c;
            this.trs[o + 6] = qw * c - qy * s;
        }
        compute();
        return this;
    }

    // ================================================================ transformations

    /**
     * Une transformation a jouer.
     *
     * @param anim    indice dans le .bin, -1 : changement instantane
     * @param seconds duree de l'animation
     * @param ticks   tiques Minecraft pendant lesquelles le tir est bloque (7, et 17 pour red-blue)
     */
    public record Clip(int anim, @Nullable String name, double seconds, int ticks) {
        public static final Clip NONE = new Clip(-1, null, 0.0, 0);
    }

    /** Le nom de l'animation d'un changement, ou null s'il est instantane (voir l'en-tete). */
    @Nullable
    public static String clipName(@Nullable GunForm from, GunForm to) {
        if (from == null || from == to) {
            return null;
        }
        if (from.family != to.family) {
            return to.family == GunForm.Family.RED ? null : "gun-gun-red-" + to.family.jak;
        }
        return "gun-gun-" + from.family.jak + from.rank + "-" + to.family.jak + to.rank;
    }

    public static Clip clip(JakGunModel model, @Nullable GunForm from, GunForm to) {
        String name = clipName(from, to);
        int index = name == null ? -1 : model.animIndex(name);
        if (index < 0) {
            return Clip.NONE;
        }
        double seconds = model.animDurations[index];
        return new Clip(index, name, seconds, (int) Math.ceil(seconds * 20.0 - 1.0e-3));
    }

    // ================================================================ lecture

    /** Vrai si le triangle se dessine : l'echelle de son os n'est pas sous ratio x main. */
    public boolean visible(int triangle) {
        return this.scale[this.model.triangleBone[triangle] & 0xFF] >= this.limit;
    }

    /** Le sommet s (triangle x 3 + coin), pose, dans out[0..2]. */
    public void vertex(int s, double[] out) {
        int b = (this.model.vertexBone[s] & 0xFF) * 12;
        double x = this.model.positions[s * 3];
        double y = this.model.positions[s * 3 + 1];
        double z = this.model.positions[s * 3 + 2];
        double[] m = this.skin;
        out[0] = m[b] * x + m[b + 3] * y + m[b + 6] * z + m[b + 9];
        out[1] = m[b + 1] * x + m[b + 4] * y + m[b + 7] * z + m[b + 10];
        out[2] = m[b + 2] * x + m[b + 5] * y + m[b + 8] * z + m[b + 11];
    }

    /** La normale du sommet s, posee et renormalisee, dans out[0..2]. */
    public void normal(int s, double[] out) {
        int b = (this.model.vertexBone[s] & 0xFF) * 12;
        double x = this.model.normals[s * 3];
        double y = this.model.normals[s * 3 + 1];
        double z = this.model.normals[s * 3 + 2];
        double[] m = this.skin;
        double nx = m[b] * x + m[b + 3] * y + m[b + 6] * z;
        double ny = m[b + 1] * x + m[b + 4] * y + m[b + 7] * z;
        double nz = m[b + 2] * x + m[b + 5] * y + m[b + 8] * z;
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (n < 1.0e-12) {
            out[0] = 0.0;
            out[1] = 1.0;
            out[2] = 0.0;
        } else {
            out[0] = nx / n;
            out[1] = ny / n;
            out[2] = nz / n;
        }
    }

    /** L'origine d'un os dans le repere de l'arme (translation de sa matrice monde). */
    public void boneOrigin(int bone, double[] out) {
        out[0] = this.world[bone * 12 + 9];
        out[1] = this.world[bone * 12 + 10];
        out[2] = this.world[bone * 12 + 11];
    }

    /** La boite des triangles visibles : {min x, min y, min z, max x, max y, max z}. */
    public double[] visibleBox() {
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        double[] p = new double[3];
        for (int t = 0; t < this.model.triangles; t++) {
            if (!visible(t)) {
                continue;
            }
            for (int v = 0; v < 3; v++) {
                vertex(t * 3 + v, p);
                for (int c = 0; c < 3; c++) {
                    box[c] = Math.min(box[c], p[c]);
                    box[c + 3] = Math.max(box[c + 3], p[c]);
                }
            }
        }
        return box;
    }

    public int visibleCount() {
        int n = 0;
        for (int t = 0; t < this.model.triangles; t++) {
            if (visible(t)) {
                n++;
            }
        }
        return n;
    }

    // ================================================================ calcul

    private void compute() {
        JakGunModel m = this.model;
        for (int i = 0; i < m.bones; i++) {
            trsMatrix(this.trs, i * TRS, this.local);
            int parent = m.boneParents[i];
            if (parent < 0) {
                System.arraycopy(this.local, 0, this.world, i * 12, 12);
            } else {
                mul(this.world, parent * 12, this.local, 0, this.world, i * 12);
            }
            mulFloat(this.world, i * 12, m.inverseBind, i * 12, this.skin, i * 12);
            this.scale[i] = Math.cbrt(Math.abs(det(this.skin, i * 12)));
        }
        this.limit = m.main >= 0 ? m.ratio * this.scale[m.main] : -1.0;
    }

    /** Matrice d'un TRS ; le quaternion est renormalise (voir l'en-tete). */
    static void trsMatrix(double[] trs, int o, double[] m) {
        double qx = trs[o + 3];
        double qy = trs[o + 4];
        double qz = trs[o + 5];
        double qw = trs[o + 6];
        double n = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        if (n > 1.0e-12) {
            qx /= n;
            qy /= n;
            qz /= n;
            qw /= n;
        } else {
            qx = 0.0;
            qy = 0.0;
            qz = 0.0;
            qw = 1.0;
        }
        double sx = trs[o + 7];
        double sy = trs[o + 8];
        double sz = trs[o + 9];
        double xx = qx * qx, yy = qy * qy, zz = qz * qz;
        double xy = qx * qy, xz = qx * qz, yz = qy * qz;
        double xw = qx * qw, yw = qy * qw, zw = qz * qw;
        m[0] = (1.0 - 2.0 * (yy + zz)) * sx;
        m[1] = 2.0 * (xy + zw) * sx;
        m[2] = 2.0 * (xz - yw) * sx;
        m[3] = 2.0 * (xy - zw) * sy;
        m[4] = (1.0 - 2.0 * (xx + zz)) * sy;
        m[5] = 2.0 * (yz + xw) * sy;
        m[6] = 2.0 * (xz + yw) * sz;
        m[7] = 2.0 * (yz - xw) * sz;
        m[8] = (1.0 - 2.0 * (xx + yy)) * sz;
        m[9] = trs[o];
        m[10] = trs[o + 1];
        m[11] = trs[o + 2];
    }

    /** out = a x b, matrices affines 3 x 4 par colonnes ; out ne doit pas etre b. */
    private static void mul(double[] a, int ao, double[] b, int bo, double[] out, int oo) {
        for (int j = 0; j < 4; j++) {
            double bx = b[bo + 3 * j];
            double by = b[bo + 3 * j + 1];
            double bz = b[bo + 3 * j + 2];
            for (int i = 0; i < 3; i++) {
                out[oo + 3 * j + i] = a[ao + i] * bx + a[ao + 3 + i] * by + a[ao + 6 + i] * bz
                        + (j == 3 ? a[ao + 9 + i] : 0.0);
            }
        }
    }

    private static void mulFloat(double[] a, int ao, float[] b, int bo, double[] out, int oo) {
        for (int j = 0; j < 4; j++) {
            double bx = b[bo + 3 * j];
            double by = b[bo + 3 * j + 1];
            double bz = b[bo + 3 * j + 2];
            for (int i = 0; i < 3; i++) {
                out[oo + 3 * j + i] = a[ao + i] * bx + a[ao + 3 + i] * by + a[ao + 6 + i] * bz
                        + (j == 3 ? a[ao + 9 + i] : 0.0);
            }
        }
    }

    private static double det(double[] m, int o) {
        double m00 = m[o], m10 = m[o + 1], m20 = m[o + 2];
        double m01 = m[o + 3], m11 = m[o + 4], m21 = m[o + 5];
        double m02 = m[o + 6], m12 = m[o + 7], m22 = m[o + 8];
        return m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);
    }
}
