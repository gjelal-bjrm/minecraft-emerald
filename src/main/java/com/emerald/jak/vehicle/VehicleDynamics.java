package com.emerald.jak.vehicle;

/**
 * Les lois de vol d'une voiture de Haven, un tick a la fois, sans Minecraft.
 *
 * POURQUOI A PART DE VehiclePhysics. Ce qui touche au monde -- les sondes, les
 * collisions, les parties -- vit dans VehiclePhysics ; ici il ne reste que des
 * nombres. Cette classe se compile seule avec le JDK : on a pu faire voler la
 * voiture au-dessus d'un sol plat simule, mesurer sa hauteur, sa vitesse et sa
 * montee en voie haute, et regler les amortissements AVANT de lancer un
 * serveur. L'autotest mesure ensuite les memes lois dans la vraie ville.
 *
 * Le code GOAL de reference : levels/factory/car/hvehicle-physics.gc
 * (sondes 26-106, voie haute 113-181, ressort 182-229) et hvehicle.gc
 * (commandes lissees 349-399, moteur 545-570). Le jeu tourne a 30 pas par
 * seconde ; un tick en vaut 1,5, d'ou les puissances 1,5 des amortissements.
 */
public final class VehicleDynamics {

    /** Rase-sol : le ressort des sondes. */
    public static final int MODE_SOL = 0;
    /**
     * Voie haute : un plancher invisible 1,5 bloc au-dessus de la carte de trafic
     * de Jak 3. La voiture pend sous ce plancher, a la hauteur de la carte.
     */
    public static final int MODE_HAUT = 1;
    /** Retour au rase-sol : on pousse vers le bas jusqu'au contact des sondes. */
    public static final int MODE_DESCENTE = 2;
    /**
     * Voie haute, pendant la montee (drapeau flight-level-transition du jeu).
     *
     * UN MODE A PART, ET NON UN COMPTEUR DU SERVEUR : c'est le client du
     * conducteur qui simule la voiture, et la montee change la loi de vol (poussee
     * meme au-dessus du plancher, freinage de la montee pres de lui). Le mode est
     * une donnee d'entite, donc les deux cotes appliquent la meme loi.
     */
    public static final int MODE_MONTEE = 3;

    /** Lissage des commandes, par seconde : gaz 4, frein et direction 8 (hvehicle.gc:349-399). */
    public static final double THROTTLE_RATE = 4.0;
    public static final double BRAKE_RATE = 8.0;
    public static final double STEER_RATE = 8.0;

    /** Sous 2 m/s, le frein tenu enclenche la marche arriere, a 50 % de poussee (hvehicle.gc:363-391). */
    public static final double REVERSE_BELOW = 2.0 * VehicleSpec.TICK;
    public static final double REVERSE_SHARE = 0.5;

    /**
     * Freinage moteur quand on ne touche a rien, par tick.
     *
     * Le jeu ralentit par la trainee de ses stabilisateurs, qu'on ne reproduit
     * pas piece a piece : sans elle, une voiture lachee glisserait sans fin dans
     * la rue. 0,98 par tick : la vitesse est divisee par deux en 1,7 s.
     */
    public static final double COAST = 0.98;
    /** Amortissement lineaire du jeu, 0,995 par pas de 1/30 s (car.gc:68). */
    public static final double LINEAR_DAMPING = Math.pow(0.995, 1.5);
    /**
     * Part de la vitesse laterale gardee a chaque tick. Les propulseurs de
     * direction du jeu tiennent la voiture dans l'axe ; 0,8 laisse une legere
     * glissade en virage sans la faire deraper comme sur de la glace.
     */
    public static final double LATERAL_GRIP = 0.8;
    /** Direction reduite quand aucune sonde ne touche (air-steering-factor, valeur non lue). */
    public static final double AIR_STEERING = 0.5;

    /** Poussee de la voie haute au plus, en g (facteur 1 + 2 index, hvehicle-physics.gc:140). */
    public static final double HIGH_THRUST_G = 3.0;
    /** Portee de la correction de la voie haute, en blocs (16384 = 4 m, hvehicle-physics.gc:150). */
    public static final double HIGH_RANGE = 4.0;
    /** Poussee vers le bas pendant la descente, en g (0,5 m g par propulseur, index 0). */
    public static final double DESCENT_PUSH_G = 1.0;
    /** Montee de la voie haute au plus, en ticks : au-dela, retour au rase-sol (hvehicle.gc:586-589). */
    public static final int TRANSITION_TICKS = 40;
    /** Chute la plus rapide, en blocs par tick : le move ne saute jamais un sol. */
    public static final double MAX_FALL = 3.0;
    /** Pas du jeu contenus dans un tick. */
    private static final double STEPS_PER_TICK = VehicleSpec.TICK * 30.0;

    /** Roulis visuel au plus, en degres, et roulis par degre de lacet par tick. */
    public static final double ROLL_MAX = 12.0;
    public static final double ROLL_PER_YAW = 3.0;
    /**
     * Vitesse ou le roulis est entier, en blocs par tick : 20 m/s, comme le
     * trafic du jeu (fmin 81920, hvehicle.gc:639), et non la vitesse maximale.
     */
    public static final double ROLL_FULL_SPEED = 20.0 * VehicleSpec.TICK;

    private VehicleDynamics() {
    }

    /** Vrai en voie haute, montee comprise. */
    public static boolean isHigh(int mode) {
        return mode == MODE_HAUT || mode == MODE_MONTEE;
    }

    /**
     * De combien les propulseurs pendent sous le plancher de la voie haute, a
     * l'equilibre, en blocs.
     *
     * Sous le plancher, la poussee vaut 3 g clamp(-f28 / 4) ; elle equilibre le
     * poids g (m + pilote) / m quand -f28 = 4 (m + pilote) / (3 m). car-a pilotee :
     * 1,5 bloc, soit les propulseurs pile sur la carte de trafic ; une moto
     * pilotee, plus legere, 2 blocs ; sans pilote, 4/3 pour tous les vehicules.
     * Calcul, que l'autotest compare a la mesure.
     */
    public static double highHang(VehicleSpec spec, boolean driver) {
        double load = (spec.mass + (driver ? VehicleSpec.DRIVER_WEIGHT : 0.0)) / spec.mass;
        return HIGH_RANGE * load / HIGH_THRUST_G;
    }

    /** Ce que le conducteur demande : gaz, frein, et direction (+1 a gauche, -1 a droite). */
    public record Input(boolean throttle, boolean brake, int steer) {
        public static final Input NONE = new Input(false, false, 0);
    }

    /** Les commandes lissees et le regime du moteur, gardes d'un tick a l'autre. */
    public static final class Controls {
        public double throttle;
        public double brake;
        public double steer;
        public double engine;

        public void reset() {
            this.throttle = 0.0;
            this.brake = 0.0;
            this.steer = 0.0;
            this.engine = 0.0;
        }
    }

    /** Filtre exponentiel : la valeur suit sa cible a {@code rate} par seconde. */
    public static double smooth(double current, double target, double rate) {
        return current + (target - current) * (1.0 - Math.exp(-rate * VehicleSpec.TICK));
    }

    /**
     * La force du ressort d'un propulseur, de 0 a 1.
     *
     * f = 1 - (clamp(d, 1, P) - 1) / (P - 1) (hvehicle-physics.gc:182-184) : pleine
     * a un bloc du sol ou moins, nulle a la portee P de la sonde (4,5 blocs pour
     * les voitures, 5 pour les motos). Pas de sol trouve (NaN) : nulle, comme le
     * sol pose vingt metres plus bas par le jeu.
     */
    public static double springFactor(VehicleSpec spec, double distance) {
        if (Double.isNaN(distance)) {
            return 0.0;
        }
        double d = Math.max(1.0, Math.min(distance, spec.probeDistance));
        return 1.0 - (d - 1.0) / (spec.probeDistance - 1.0);
    }

    public static boolean grounded(double front, double rear) {
        return !Double.isNaN(front) || !Double.isNaN(rear);
    }

    /**
     * La vitesse verticale du tick suivant.
     *
     * @param front  distance de la sonde avant au sol, NaN sans sol a portee
     * @param rear   idem pour la sonde arriere
     * @param probeY hauteur des propulseurs (origine + thrusterY)
     * @param floorY plancher de la voie haute, 1,5 bloc au-dessus de la carte de trafic
     *               (ignore hors voie haute, MODE_HAUT ou MODE_MONTEE)
     * @param driver un pilote pese sur la voiture
     */
    public static double verticalVelocity(VehicleSpec spec, double vy, double front, double rear, int mode,
                                          double probeY, double floorY, boolean driver) {
        double g = spec.gravity;
        double load = (spec.mass + (driver ? VehicleSpec.DRIVER_WEIGHT : 0.0)) / spec.mass;
        double fFront = springFactor(spec, front);
        double fRear = springFactor(spec, rear);

        double ay = -g * load;
        // le ressort : 8 m g 0,5 spring f par propulseur, divise par la masse
        ay += 8.0 * g * spec.springLift * 0.5 * (fFront + fRear);

        double f28 = probeY - floorY;
        boolean climbing = mode == MODE_MONTEE;
        if (isHigh(mode) && (climbing || f28 < 0.0)) {
            // La loi du jeu, telle quelle (hvehicle-physics.gc:129-160) : sous le
            // plancher, ou pendant la montee, 3 g x clamp(-(f28/4 m + vy/40 m/s)),
            // de -3 g a +3 g. Rien n'est ajoute pour le poids : l'equilibre pend
            // sous le plancher de highHang, a la hauteur de la carte de trafic, la
            // ou le jeu tient ses voitures. Au-dessus, seule la gravite agit.
            double correction = -(f28 / HIGH_RANGE + vy / 2.0);
            ay += HIGH_THRUST_G * g * Math.max(-1.0, Math.min(1.0, correction));
        } else if (mode == MODE_DESCENTE && !grounded(front, rear)) {
            ay -= DESCENT_PUSH_G * g;
        }

        // LES AMORTISSEMENTS PARTENT DE LA VITESSE DU DEBUT DU PAS, comme dans le
        // jeu : chaque propulseur lit sa vitesse avant toute force
        // (hvehicle-physics.gc:76), apply-impact! ne fait qu'additionner les
        // forces (rigid-body.gc:613-622), et la gravite s'y ajoute avant
        // l'integration (hvehicle-physics.gc:500-510). Le poids du tick n'est
        // donc jamais amorti.
        //
        // Le portage amortissait APRES la gravite, avec un plafond de 0,95 : la
        // moto pilotee, dont les deux ressorts retirent plus que toute la vitesse
        // pres de sa hauteur, perdait a chaque tick presque tout ce que son poids
        // venait de lui donner et restait figee vers 3,1 blocs au lieu de 2,5
        // (banc hors jeu : encore 2,96 au bout de 30 s). Dans l'ordre du jeu, elle
        // tient 2,500 des 2 s, et les voitures tombent pile sur leur calcul.
        //
        // Le ressort retire f de la vitesse vers le sol par propulseur
        // (2 f / dt x 1/2 x m x -vy, hvehicle-physics.gc:198-212) ; la voie haute
        // en retire un quart, a la montee a moins d'un bloc sous le plancher
        // pendant la montee, a la chute a plus d'un bloc dessous (164-179). Les
        // impulsions d'un pas s'additionnent ; au-dela de toute la vitesse, le jeu
        // la renverserait, on s'arrete a zero. Puissance 1,5 : les pas dans un tick.
        double share = 0.0;
        if (vy < 0.0) {
            share += fFront + fRear;
        }
        if ((climbing && f28 > -1.0 && vy > 0.0) || (isHigh(mode) && f28 < -1.0 && vy < 0.0)) {
            share += 0.25;
        }
        vy = vy * Math.pow(Math.max(0.0, 1.0 - share), STEPS_PER_TICK) + ay;
        return Math.max(vy, -MAX_FALL);
    }

    /**
     * Gaz, frein, marche arriere et direction pour un tick.
     *
     * @param motion {vitesse avant, vitesse laterale} en blocs par tick, mis a jour
     * @return le changement de lacet en degres (negatif : vers la gauche)
     */
    public static double horizontal(VehicleSpec spec, Controls c, Input in, double[] motion, boolean grounded) {
        c.throttle = smooth(c.throttle, in.throttle() ? 1.0 : 0.0, THROTTLE_RATE);
        c.brake = smooth(c.brake, in.brake() ? 1.0 : 0.0, BRAKE_RATE);
        c.steer = smooth(c.steer, Math.max(-1, Math.min(1, in.steer())), STEER_RATE);

        double vf = motion[0];
        double vl = motion[1];
        double vmax = spec.maxSpeed;

        if (in.brake()) {
            if (vf > REVERSE_BELOW) {
                vf = Math.max(0.0, vf - spec.brake * c.brake);
            } else {
                vf = Math.max(-REVERSE_SHARE * vmax, vf - REVERSE_SHARE * spec.thrust * c.brake);
            }
        }

        // le moteur suit les gaz, bride aux basses vitesses : 0,8333 (0,5 + v/vmax)
        double intake = 0.8333 * (0.5 + Math.max(0.0, vf) * spec.engineIntake / vmax);
        double target = in.throttle() ? Math.min(c.throttle, intake) : 0.0;
        c.engine = smooth(c.engine, target, spec.engineResponse);
        vf += c.engine * spec.thrust;

        if (!in.throttle() && !in.brake()) {
            vf *= COAST;
        }
        vf *= LINEAR_DAMPING;
        vl *= LATERAL_GRIP;
        // un plafond franc : la voiture ne depasse jamais la vitesse reglee
        vf = Math.max(-vmax, Math.min(vmax, vf));

        motion[0] = vf;
        motion[1] = vl;

        // lacet vise : steer x gain x 15 / (15 + v), en rad/s puis en degres par tick
        double gain = spec.steerGain * VehicleSpec.TICK * spec.halfGainSpeed / (spec.halfGainSpeed + Math.abs(vf));
        if (!grounded) {
            gain *= AIR_STEERING;
        }
        return -Math.toDegrees(c.steer * gain);
    }

    /** Le roulis vise, en degres, a partir du lacet du tick et de la vitesse. */
    public static double rollTarget(VehicleSpec spec, double yawDelta, double speed) {
        double roll = Math.max(-ROLL_MAX, Math.min(ROLL_MAX, yawDelta * ROLL_PER_YAW));
        return roll * Math.min(1.0, Math.abs(speed) / ROLL_FULL_SPEED);
    }

    public static boolean finite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return false;
            }
        }
        return true;
    }
}
