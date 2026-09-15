package com.emerald.jak.gun;

import javax.annotation.Nullable;

/**
 * Les chiffres de tir des quatre armes de base, pris dans les sources GOAL de Jak 3 et convertis.
 *
 * LES CONVERSIONS (plan, cadre) : 1 m = 1 bloc ; tiques Jak / 15 = tiques
 * Minecraft (300 tiques Jak par seconde) ; m/s / 20 = blocs par tique ;
 * 65536 = 360 degres.
 *
 * LES TROIS GACHETTES (target-gun.gc:3275-3423, champ gun-control) :
 *  - APPUI (1) : un tir par pression ; une pression faite moins de 30 tiques Jak
 *    (2 tiques) avant la fin du delai est gardee et part des qu'il est ecoule ;
 *  - MAINTIEN (2) : tant que la gachette est tenue et qu'aucune charge n'est en
 *    cours, un tir en attente ; le Peace Maker en fait une charge au canon ;
 *  - CANON TOURNANT (4) : tenue, la rotation monte vers 218453 (1200 degres/s) a
 *    145635 par seconde (800 degres/s2) ; relachee, elle retombe a 109226 par
 *    seconde (600 degres/s2) ; le delai va lineairement de 120 a 30 tiques Jak
 *    (8 a 2 tiques) selon la rotation. Le tir n'attend pas la rotation pleine.
 *
 * LA REGLE DES DEGATS, choisie pour Haven : 1 point de degat de Jak = 4 PV de
 * Minecraft. Un grunt de Jak 3 a 5 points de vie (grunt.gc:290, kg-grunt.gc:521,
 * metalhead-grunt.gc:88) ; un zombie, un squelette ou un phantom en a 20. Un
 * monstre de Haven encaisse donc ce qu'encaisse un grunt : trois tirs de Blaster
 * (2 points), deux coups de Scatter Gun a bout portant (3 points), trois balles de
 * Vulcan Fury (2), et le Peace Maker (16) tue d'un coup, puis de proche en proche.
 */
public enum GunSpec {
    SCATTER(GunForm.RED_1, Trigger.PRESS, 330, 1),
    BLASTER(GunForm.YELLOW_1, Trigger.PRESS, 96, 1),
    VULCAN(GunForm.BLUE_1, Trigger.SPIN, 120, 1),
    PEACE(GunForm.DARK_1, Trigger.HOLD, 255, 1);

    public enum Trigger { PRESS, HOLD, SPIN }

    // ------------------------------------------------------------- communs

    /** Tiques Jak par tique Minecraft. */
    public static final double JAK_TICKS = 15.0;
    /** PV de Minecraft par point de degat de Jak (voir l'en-tete). */
    public static final float HP_PER_JAK = 4.0F;
    /** Une pression garde son tir si elle arrive a moins de 30 tiques Jak de la fin du delai. */
    public static final double PENDING_WINDOW = 30.0 / JAK_TICKS;
    /** Sans nouvelle du client depuis ce nombre de tiques, la gachette est relachee (relachement perdu). */
    public static final int TRIGGER_TIMEOUT = 10;
    /** Le client redit « tenue » toutes les ... tiques. */
    public static final int TRIGGER_KEEPALIVE = 5;

    // ------------------------------------------------------------- canon tournant

    public static final double SPIN_MAX = 1200.0;
    /** Degres/s gagnes par tique tenue (800 degres/s2). */
    public static final double SPIN_UP = 800.0 / 20.0;
    /** Degres/s perdus par tique relachee (600 degres/s2). */
    public static final double SPIN_DOWN = 600.0 / 20.0;
    public static final double SPIN_DELAY_SLOW = 120.0;
    public static final double SPIN_DELAY_FAST = 30.0;

    // ------------------------------------------------------------- Scatter Gun (gun-red-shot.gc:1825-2346)

    public static final int SCATTER_PROBES = 19;
    /** Sept sondes par image, les 19 en trois images. */
    public static final int SCATTER_PER_TICK = 7;
    public static final double SCATTER_RANGE = 15.0;
    public static final float SCATTER_PROBE_RADIUS = 0.2F;
    /** Les premieres sondes visent les cibles d'une sphere de 10,7 m centree 10,6 m devant. */
    public static final double SCATTER_AIM_RADIUS = 10.7;
    public static final double SCATTER_AIM_AHEAD = 10.6;
    public static final double SCATTER_CONE_V = 15.0;
    public static final double SCATTER_CONE_H = 45.0;
    public static final double SCATTER_NEAR = 6.0;
    public static final float SCATTER_DAMAGE_NEAR = 3.0F;
    public static final float SCATTER_DAMAGE_FAR = 2.0F;
    /** Blocs casses au plus par tir (le bloc touche par chaque sonde). */
    public static final int SCATTER_BLOCKS = 6;

    // ------------------------------------------------------------- Blaster (gun-yellow-shot.gc:961-988, 1585-1606)

    /** 819200 unites/s = 200 m/s = 10 blocs par tique. */
    public static final double BLASTER_SPEED = 10.0;
    public static final int BLASTER_LIFE = 60;
    public static final float BLASTER_DAMAGE = 2.0F;
    public static final double BLASTER_BREAK_RADIUS = 1.2;
    public static final int BLASTER_BLOCKS = 4;

    // ------------------------------------------------------------- Vulcan Fury (gun-blue-shot.gc:89-121, 2502-2522)

    public static final double VULCAN_RANGE = 80.0;
    public static final double VULCAN_SPREAD = 1.1;
    public static final float VULCAN_DAMAGE = 2.0F;

    // ------------------------------------------------------------- Peace Maker (gun-dark-shot.gc:1423-2121)

    /** La boule reste au canon au moins 0,3 s (90 tiques Jak). */
    public static final int PEACE_CHARGE_MIN = 6;
    /** Elle grossit en 60 tiques Jak. */
    public static final int PEACE_GROW = 4;
    /** 1,8 m par image a 60 images/s = 108 m/s. */
    public static final double PEACE_SPEED = 5.4;
    /** Impact apres 450 tiques Jak de vol. */
    public static final int PEACE_LIFE = 30;
    public static final double PEACE_BLAST = 10.0;
    public static final int PEACE_TARGETS = 16;
    public static final float PEACE_DAMAGE = 16.0F;
    /** Une foudre toutes les 0,1 s. */
    public static final int PEACE_CHAIN_TICKS = 2;
    /** Virage par tique : 1 degre par image a 15 m, 45 a 1 m, trois images par tique. */
    public static final double PEACE_TURN_FAR = 3.0;
    public static final double PEACE_TURN_NEAR = 135.0;
    /** Le verrou du tir : la cible la plus proche du regard, dans ce cone et cette portee. */
    public static final double PEACE_LOCK_ANGLE = 20.0;
    public static final double PEACE_LOCK_RANGE = 64.0;
    public static final double PEACE_BREAK_RADIUS = 3.0;
    public static final int PEACE_BLOCKS = 40;

    // ------------------------------------------------------------- munitions (collectables.gc:2511-2524)

    /** Un ramassage vaut 10 en jaune et en bleu, 5 en rouge, 1 en sombre. */
    public static int pickupAmount(GunForm.Family family) {
        return switch (family) {
            case RED -> 5;
            case YELLOW, BLUE -> 10;
            case DARK -> 1;
        };
    }

    public final GunForm form;
    public final Trigger trigger;
    /** Delai de tir en tiques Jak. */
    public final double delayJak;
    public final int cost;

    GunSpec(GunForm form, Trigger trigger, double delayJak, int cost) {
        this.form = form;
        this.trigger = trigger;
        this.delayJak = delayJak;
        this.cost = cost;
    }

    /** Le delai de tir en tiques Minecraft, a la rotation donnee (canon tournant seulement). */
    public double delay(double spin) {
        if (this.trigger != Trigger.SPIN) {
            return this.delayJak / JAK_TICKS;
        }
        double f = Math.max(0.0, Math.min(1.0, spin / SPIN_MAX));
        return (SPIN_DELAY_SLOW + (SPIN_DELAY_FAST - SPIN_DELAY_SLOW) * f) / JAK_TICKS;
    }

    /** L'arme d'une forme ; null pour les huit ameliorations (jalon B). */
    @Nullable
    public static GunSpec of(GunForm form) {
        for (GunSpec spec : values()) {
            if (spec.form == form) {
                return spec;
            }
        }
        return null;
    }

    /**
     * L'angle du canon de la Vulcan Fury (degres), deduit des tiques de gachette du composant.
     *
     * Tenue depuis t secondes : vitesse min(1200, 800 t), angle 400 t2 puis
     * 900 + 1200 (t - 1,5). Relachee : la vitesse retombe a 600 degres/s2. Le
     * serveur recule la tique de debut quand un canon deja lance est repris
     * (GunFire), pour que cette formule reparte de la bonne vitesse.
     */
    public static double spinAngle(long start, long end, double now) {
        if (start <= 0L) {
            return 0.0;
        }
        if (end < start) {
            return spinTheta(Math.max(0.0, (now - start) / 20.0));
        }
        double held = (end - start) / 20.0;
        double theta = spinTheta(held);
        double w = Math.min(SPIN_MAX, 800.0 * held);
        double tau = Math.max(0.0, (now - end) / 20.0);
        double stop = w / 600.0;
        return tau >= stop ? theta + w * w / 1200.0 : theta + w * tau - 300.0 * tau * tau;
    }

    private static double spinTheta(double t) {
        return t <= 1.5 ? 400.0 * t * t : 900.0 + 1200.0 * (t - 1.5);
    }
}
