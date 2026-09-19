package com.emerald.jak.gun;

import javax.annotation.Nullable;

/**
 * Les chiffres de tir des armes du Morph Gun, pris dans les sources GOAL de Jak 3 et convertis : les quatre
 * armes de base (jalon A), puis les ameliorations rouges et jaunes (jalon B ; etude au cahier, section 70).
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
 * Une quatrieme, pour le Wave Concussor, qui est un MAINTIEN dans le jeu mais dont
 * le tir part au relachement :
 *  - CHARGE : tenue, l'arme charge (au plus 1 s) et debite son eco par paliers ;
 *    relachee -- ou gachette perdue --, l'onde part avec la force de la charge.
 *
 * LE COUT SE PAIE DE DEUX FACONS (target-gun.gc:773-809 et 2899-2916) : `cost` est
 * le SEUIL qui autorise le tir, `debit` ce que le tir preleve aussitot. Le Wave
 * Concussor (paliers de charge), le Beam Reflexor (un de plus au 2e et au 3e
 * ennemi) et le Gyro Burster (50 sur toute la rafale) prelevent le reste eux-memes.
 *
 * LA REGLE DES DEGATS, choisie pour Haven : 1 point de degat de Jak = 4 PV de
 * Minecraft. Un grunt de Jak 3 a 5 points de vie (grunt.gc:290, kg-grunt.gc:521,
 * metalhead-grunt.gc:88) ; un zombie, un squelette ou un phantom en a 20. Un
 * monstre de Haven encaisse donc ce qu'encaisse un grunt : trois tirs de Blaster
 * (2 points), deux coups de Scatter Gun a bout portant (3 points), trois balles de
 * Vulcan Fury (2), et le Peace Maker (16) tue d'un coup, puis de proche en proche.
 */
public enum GunSpec {
    SCATTER(GunForm.RED_1, Trigger.PRESS, 330, 1, 1),
    BLASTER(GunForm.YELLOW_1, Trigger.PRESS, 96, 1, 1),
    VULCAN(GunForm.BLUE_1, Trigger.SPIN, 120, 1, 1),
    PEACE(GunForm.DARK_1, Trigger.HOLD, 255, 1, 1),
    // les ameliorations, APRES les armes de base : les releves du banc citent les ordinaux
    WAVE(GunForm.RED_2, Trigger.CHARGE, 240, 1, 0),
    PLASMITE(GunForm.RED_3, Trigger.PRESS, 330, 10, 10),
    REFLEXOR(GunForm.YELLOW_2, Trigger.PRESS, 96, 1, 1),
    GYRO(GunForm.YELLOW_3, Trigger.PRESS, 0, 10, 0);

    public enum Trigger { PRESS, HOLD, SPIN, CHARGE }

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

    // ------------------------------------------------------------- Wave Concussor (gun-red-shot.gc:417-1453)

    /** Le relachement ne compte qu'apres 0,1 s de charge (:765). */
    public static final int WAVE_CHARGE_MIN = 2;
    /** Charge pleine en 1 s (total-charge-time, :516). */
    public static final int WAVE_CHARGE_FULL = 20;
    /** Les paliers du cout, en tiques de charge : 0,1 s, 0,25 s, 0,5 s, 0,75 s et 1 s, un eco rouge chacun (:734-762). */
    private static final int[] WAVE_COST_TICKS = {2, 5, 10, 15, 20};
    /** Le rayon final de l'onde, de la charge nulle a la charge pleine : 3 a 18 m (:514-515, 1315). */
    public static final double WAVE_RADIUS_MIN = 3.0;
    public static final double WAVE_RADIUS_MAX = 18.0;
    /** L'onde va de 3 a 18 m en 0,7 s (total-explode-time, :513), ecretee a son rayon final. */
    public static final int WAVE_EXPAND_TICKS = 14;
    /** 5 points de Jak x l'intensite, jamais moins de 1 (:517, 664-665). */
    public static final float WAVE_DAMAGE = 5.0F;
    public static final float WAVE_DAMAGE_MIN = 1.0F;
    /** A moins de 6 m du centre, la touche est sure ; au-dela, il faut une ligne de vue (:598-631). */
    public static final double WAVE_SURE = 6.0;
    /** La tranche verticale de l'onde autour de son sol : elle court au sol, ce n'est pas une sphere. */
    public static final double WAVE_HEIGHT = 4.0;
    /** Le sol de l'onde se cherche de 6 m au-dessus du tireur a 20 m dessous (:983-1006). */
    public static final double WAVE_GROUND_UP = 6.0;
    public static final double WAVE_GROUND_DOWN = 20.0;
    /** La poussee radiale a pleine intensite, en blocs par tique, et son soulevement. */
    public static final double WAVE_PUSH = 1.4;
    public static final double WAVE_LIFT = 0.45;

    /** Ce qu'une charge de `ticks` tiques a coute : un eco rouge par palier franchi, 5 au plus. */
    public static int waveCost(int ticks) {
        int cost = 0;
        for (int step : WAVE_COST_TICKS) {
            if (ticks >= step) {
                cost++;
            }
        }
        return cost;
    }

    // ------------------------------------------------------------- Plasmite RPG (gun-red-shot.gc:44-415, 1527-1822)

    /** 266240 unites/s = 65 m/s. */
    public static final double PLASMITE_SPEED = 3.25;
    /** Le tir part toujours vers le haut : y d'au moins 0,3 avant normalisation (:1535-1536). */
    public static final double PLASMITE_MIN_UP = 0.3;
    /** 184320 = 45 m/s2, et 80 m/s au plus (:134). */
    public static final double PLASMITE_GRAVITY = 45.0 / 400.0;
    public static final double PLASMITE_MAX_SPEED = 4.0;
    /** Chaque rebond garde 60 % de la vitesse (:1666-1668). */
    public static final double PLASMITE_BOUNCE = 0.6;
    /** Vie de 3 s (:135), puis elle explose. */
    public static final int PLASMITE_LIFE = 60;
    /** La visee balistique : une cible dans une sphere de 35 m centree 35 m devant, a 45 degres et 7 m de denivele au plus (:1551-1634). */
    public static final double PLASMITE_AIM_RADIUS = 35.0;
    public static final double PLASMITE_AIM_COS = 0.707;
    public static final double PLASMITE_AIM_DROP = 7.0;
    /** La meche de proximite : 2/3 du souffle, 0,5 s au plus, tout de suite a 2 m (:293-400). */
    public static final double PLASMITE_FUSE_RADIUS = 20.0 * 2.0 / 3.0;
    public static final int PLASMITE_FUSE_MAX = 10;
    public static final double PLASMITE_FUSE_NOW = 2.0;
    /** Le souffle : 12 points dans 20 m (:133, 237). */
    public static final double PLASMITE_BLAST = 20.0;
    public static final float PLASMITE_DAMAGE = 12.0F;
    public static final int PLASMITE_TARGETS = 48;
    public static final double PLASMITE_BREAK_RADIUS = 3.5;
    public static final int PLASMITE_BLOCKS = 48;

    // ------------------------------------------------------------- Beam Reflexor (gun-yellow-shot.gc:931-958, 1271-1723)

    /** 200 m/s au depart, 133,3 m/s apres un rebond (:952, 1379). */
    public static final double REFLEXOR_SPEED = 10.0;
    public static final double REFLEXOR_SPEED_AFTER = 20.0 / 3.0;
    /** Vie de 3 s (:1627-1631). */
    public static final int REFLEXOR_LIFE = 60;
    /** Quatre ennemis au plus (max-actor-deflect-count, :1626). */
    public static final int REFLEXOR_ENEMIES = 4;
    /** 1,5 point a la premiere touche, 1 ensuite (:1613-1616, 1707-1709). */
    public static final float REFLEXOR_DAMAGE = 1.5F;
    public static final float REFLEXOR_DAMAGE_AFTER = 1.0F;
    /** Un eco jaune de plus au 2e et au 3e ennemi touche (:1697-1703). */
    public static final int REFLEXOR_EXTRA_COSTS = 2;
    /** Le tir reflechi ne part jamais vers le ciel : y plafonne a 0,2 (:1277-1279). */
    public static final double REFLEXOR_MAX_UP = 0.2;
    /** Apres un rebond, trois fois sur quatre, le tir se revise vers un ennemi d'une sphere de 100 m centree 50 m devant (:1284-1378). */
    public static final double REFLEXOR_REAIM_CHANCE = 0.75;
    public static final double REFLEXOR_REAIM_RADIUS = 100.0;
    public static final double REFLEXOR_REAIM_AHEAD = 50.0;
    /** Un ennemi touche est ignore 0,15 s (:1659-1674). */
    public static final int REFLEXOR_IGNORE_TICKS = 3;
    /** Rebonds au plus par tique de calcul : un coin ne boucle pas sans fin. */
    public static final int REFLEXOR_BOUNCES_PER_TICK = 6;
    /** Le bloc touche casse a chaque rebond, tant de fois au plus par tir. */
    public static final int REFLEXOR_BLOCKS = 3;

    // ------------------------------------------------------------- Gyro Burster (gun-yellow-shot.gc:64-928)

    /** La soucoupe part a 100 m/s, a 31 degres vers le haut : y force a 0,6 avant normalisation (:862-883). */
    public static final double GYRO_SPEED = 5.0;
    public static final double GYRO_LAUNCH_UP = 0.6;
    /** Vol lance 0,05 s, puis la vitesse tombe de 100 a 10 m/s en 0,1 s (:358, 740-745). */
    public static final int GYRO_FLOAT_TICKS = 1;
    public static final int GYRO_SLOW_TICKS = 2;
    public static final double GYRO_DRIFT = 0.5;
    /** Un mur a moins de 10 m devant : reflexion, 8 m/s, puis 0,5 s sans nouveau rebond (:749-790). */
    public static final double GYRO_WALL_PROBE = 10.0;
    public static final double GYRO_WALL_SPEED = 0.4;
    public static final int GYRO_WALL_COOLDOWN = 10;
    /** Elle tire 1 s apres son arret, pendant 4 s (:791-799, total-fire-time). */
    public static final int GYRO_ARM_TICKS = 20;
    public static final int GYRO_FIRE_TICKS = 80;
    /** Deux tirs par salve, une salve toutes les 0,05 s (:172-175). */
    public static final int GYRO_SHOTS_PER_TICK = 2;
    /** Cibles a 35 m, vues (:193-221). Un monstre devant le tireur pese 3, sinon 1 (:223-235 ; les autres poids du jeu visent des especes absentes de Haven). */
    public static final double GYRO_RANGE = 35.0;
    public static final float GYRO_DAMAGE = 2.0F;
    /** Faute de cible, le tir part vers le bas : x et z dans [-1, 1], y dans [-0,85, -0,3], a 10 m (:290-300). */
    public static final double GYRO_STRAY_RANGE = 10.0;
    /** 50 eco jaunes sur toute la rafale (total-ammo-to-drain, :371). */
    public static final double GYRO_DRAIN = 50.0;
    /** Posee, elle attend 1 s, retrecit en 0,2 s, et disparait (:486-590) ; 30 s de vie au plus (:364). */
    public static final int GYRO_SIT_TICKS = 20;
    public static final int GYRO_SHRINK_TICKS = 4;
    public static final int GYRO_LIFE = 600;
    /** Echelle du modele dans le jeu (:323). */
    public static final float GYRO_SCALE = 3.5F;
    /** Deux tours par seconde (:714). */
    public static final float GYRO_SPIN_DEGREES = 36.0F;

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
    /** La reserve qu'il faut pour tirer (ammo-required). */
    public final int cost;
    /** Ce que le tir preleve aussitot ; le reste, l'arme le preleve elle-meme (voir l'en-tete). */
    public final int debit;

    GunSpec(GunForm form, Trigger trigger, double delayJak, int cost, int debit) {
        this.form = form;
        this.trigger = trigger;
        this.delayJak = delayJak;
        this.cost = cost;
        this.debit = debit;
    }

    /** Le delai de tir en tiques Minecraft, a la rotation donnee (canon tournant seulement). */
    public double delay(double spin) {
        if (this.trigger != Trigger.SPIN) {
            return this.delayJak / JAK_TICKS;
        }
        double f = Math.max(0.0, Math.min(1.0, spin / SPIN_MAX));
        return (SPIN_DELAY_SLOW + (SPIN_DELAY_FAST - SPIN_DELAY_SLOW) * f) / JAK_TICKS;
    }

    /** L'arme d'une forme ; null pour les ameliorations bleues et sombres, pas encore portees. */
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
