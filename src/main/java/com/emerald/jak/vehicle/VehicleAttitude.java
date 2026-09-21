package com.emerald.jak.vehicle;

/**
 * L'equilibre d'un vehicule de Haven -- tangage, roulis, vrille --, un tick a la fois, sans Minecraft.
 *
 * LE JOUEUR (21 sept.) : « quand j'ai parle de collision, c'etait aussi [...] qu'il y ait un
 * systeme de gravite et d'impact qui change l'equilibre du vehicule, un peu comme sur le jeu
 * original ». Jak 3 fait de chaque vehicule un corps rigide (rigid-body.gc) : un choc
 * l'incline et le fait tourner, ses propulseurs le remettent d'aplomb. Nos vehicules ne
 * connaissaient que leur lacet.
 *
 * CE QUI TIENT LE VEHICULE, comme le jeu le fait (levels/factory/car/hvehicle-physics.gc) :
 * - LE TANGAGE : les deux propulseurs de sustentation, devant et derriere, a egale distance
 *   du centre de masse. Chacun pousse selon SA distance au sol (le ressort, 181-223) ou au
 *   plancher de la voie haute (129-180) : le nez qui plonge rapproche le propulseur avant, qui
 *   pousse plus fort et le releve. Chaque propulseur retire, a chaque pas du jeu, une part de
 *   la vitesse de son point quand celui-ci descend (198-212), et le controle du tangage
 *   (pitch-control-factor, 290-321) freine toute rotation. Sur une pente ou une marche, les
 *   deux distances different : le vehicule epouse le relief.
 * - LE ROULIS : deux propulseurs lateraux, a 1,7 m de l'axe (car.gc:294-300), commandes par
 *   sin(roulis)^3 + 0,075 x vitesse de roulis (330-367) : mous pres de l'aplomb, fermes quand
 *   ça penche. D'ou la gite qui se balance apres un choc. Un petit rappel lineaire, ajoute
 *   ({@link #ROLL_LEVEL}), finit de le remettre a plat.
 * - LE POIDS DU PILOTE (512-521) : il pese une masse, et se deplace de 0,6 m du cote ou l'on
 *   tourne (player-shift-x). Le vehicule se couche dans le virage jusqu'a ce que les
 *   propulseurs de roulis equilibrent ce poids : sin3 = 0,6 / (1,02 m), 25 degres pour une
 *   car-a, 42 pour une moto (22 et 40 avec le rappel ajoute). C'est la gravite qui fait
 *   l'equilibre.
 * - LA VRILLE : les propulseurs de direction (370-392) ramenent la vitesse de lacet a celle
 *   que demande le volant ; un choc de biais fait tourner le vehicule, ils l'arretent.
 * - L'AMORTISSEMENT du corps rigide : 0,995 par 1/60 s (car.gc:69, rigid-body.gc:217-231).
 *
 * LES CHOCS ({@link #impulse}) : une impulsion J appliquee au point r du vehicule change sa
 * vitesse de rotation de r x J / I, avec les moments d'inertie de la boite du jeu
 * (VehicleSpec.Balance). Heurte de cote sous son centre de masse, il gite ; de face, il pique
 * du nez ; de biais, il vrille. VehicleImpacts et VehiclePhysics trouvent le point et
 * l'impulsion.
 *
 * TOUT EST ICI EN METRES, SECONDES ET RADIANS, comme dans le jeu : les formules se lisent
 * telles quelles. Trois sous-pas par tick : les amortisseurs des propulseurs sont raides.
 *
 * CE QUI NE CHANGE PAS : la hauteur, la vitesse et la route du vehicule (VehicleDynamics),
 * reglees et mesurees depuis des semaines. L'equilibre n'y touche que par la vrille, qui
 * tourne le vehicule. Les boites de collision restent droites : Minecraft ne les tourne pas.
 */
public final class VehicleAttitude {

    /** Sous-pas par tick : l'amortisseur d'un propulseur retire jusqu'aux deux tiers d'une rotation par pas du jeu. */
    public static final int SUBSTEPS = 3;
    /** Le pas du jeu, 1/30 s : ses amortisseurs sont des impulsions par pas. */
    private static final double GAME_STEP = 1.0 / 30.0;
    private static final double G = VehicleSpec.GRAVITY_MS2;

    /** Les propulseurs de roulis : a 1,7 m de l'axe (6963,2), leur normale (+-0,3 ; -0,6 ; 0) pousse a 0,6 vers le haut. */
    public static final double ROLL_THRUSTER_X = 1.7;
    public static final double ROLL_THRUSTER_UP = 0.6;
    /** Le terme de vitesse de la commande de roulis, par rad/s (hvehicle-physics.gc:344). */
    public static final double ROLL_RATE = 0.075;
    /** roll-control-factor : 1 pour les six vehicules. */
    public static final double ROLL_CONTROL = 1.0;
    /**
     * UN RAPPEL LINEAIRE, AJOUTE AU JEU : 0,05 sin(roulis) dans la commande.
     *
     * Le cube du jeu est si mou pres de l'aplomb qu'un vehicule restait penche de 2 a 4
     * degres des secondes apres un virage (banc hors jeu : car-a encore a -2,0 au bout de
     * 10 s, moto a +3,8) -- en jeu, on le prendrait pour un defaut. Ce terme le ramene a
     * plat en moins de deux secondes (banc hors jeu : car-a a moins de 2 degres 1,8 s apres
     * le volant lache), et la gite du virage change peu : 22,3 degres au lieu de 24,8 pour
     * la car-a, 39,8 au lieu de 41,7 pour la moto.
     */
    public static final double ROLL_LEVEL = 0.05;
    /** Le controle du tangage : 0,2 x pitch-control-factor x vitesse de tangage, un g par propulseur au plus (:305-313). */
    public static final double PITCH_CONTROL = 0.2;
    /** Le poids du pilote se deplace de 0,6 m du cote du virage (player-shift-x, car.gc:137, bike.gc:139). */
    public static final double DRIVER_SHIFT = 0.6;
    /** Les propulseurs de direction : 2 x 8192 unites (4 m) par rad/s d'ecart, a 1,9 m devant (7782,4 ; :370-392). */
    public static final double STEER_FORCE = 4.0;
    public static final double STEER_ARM = 1.9;
    /** Amortissement angulaire du corps rigide, par 1/60 s. */
    public static final double ANGULAR_DAMPING = 0.995;
    /** Le terme de vitesse de la voie haute, en s/m : 0,5 / 20 m/s (hvehicle-physics.gc:146-150). */
    public static final double HIGH_RATE = 0.5 / 20.0;
    /**
     * Bornes de securite. Le jeu laisse un vehicule se retourner ; ici ses boites de
     * collision ne tournent pas, et un vehicule a l'envers n'aurait plus rien de vrai.
     */
    public static final double MAX_TILT = Math.toRadians(75.0);
    public static final double MAX_RATE = 12.0;

    private VehicleAttitude() {
    }

    /**
     * L'equilibre d'un vehicule. Tangage positif : le nez en haut ; roulis positif : le cote
     * gauche (+x du modele) en haut ; lacet positif : vers la gauche (le lacet de Minecraft
     * tourne dans l'autre sens, voir {@link #step}).
     */
    public static final class State {
        public double pitch;
        public double roll;
        public double pitchRate;
        public double rollRate;
        /** La vrille : ce qui s'ajoute a la vitesse de lacet voulue par le volant, rad/s. */
        public double yawRate;

        public void reset() {
            this.set(0.0, 0.0);
        }

        /** Pose une inclinaison, immobile : un vehicule repris par l'autre cote. */
        public void set(double pitch, double roll) {
            this.pitch = pitch;
            this.roll = roll;
            this.pitchRate = 0.0;
            this.rollRate = 0.0;
            this.yawRate = 0.0;
        }
    }

    /**
     * Ce que le monde dit a l'equilibre, pour un tick.
     *
     * @param front  distance du propulseur avant au sol, vehicule a plat, en blocs ; NaN sans sol a portee
     * @param rear   idem pour le propulseur arriere
     * @param mode   le mode de vol (VehicleDynamics.MODE_*)
     * @param f28    hauteur des propulseurs, vehicule a plat, au-dessus du plancher de la voie haute (negatif :
     *               dessous) ; ignore hors voie haute
     * @param vy     vitesse verticale du vehicule au debut du tick, en blocs par tick
     * @param driver un pilote pese sur le vehicule
     * @param steer  la direction lissee, de -1 (droite) a +1 (gauche)
     * @param inAir  ni sol ni voie haute sous les propulseurs
     */
    public record Inputs(double front, double rear, int mode, double f28, double vy, boolean driver, double steer,
                         boolean inAir) {
    }

    /**
     * Un tick d'equilibre.
     *
     * @return la vrille du tick, en degres du lacet de Minecraft (negatif : vers la gauche)
     */
    public static double step(VehicleSpec spec, State s, Inputs in) {
        double h = VehicleSpec.TICK / SUBSTEPS;
        double m = spec.mass;
        VehicleSpec.Balance balance = spec.balance;
        double[] arm = {spec.thrusterFrontZ - balance.cmZ(), spec.thrusterRearZ - balance.cmZ()};
        double[] ground = {in.front(), in.rear()};
        double vy = in.vy() / VehicleSpec.TICK;
        boolean high = VehicleDynamics.isHigh(in.mode());
        boolean climbing = in.mode() == VehicleDynamics.MODE_MONTEE;
        double decay = Math.max(0.0, 1.0 - (1.0 - ANGULAR_DAMPING) * h * 60.0);
        double steering = STEER_FORCE * STEER_ARM * m * balance.steerThrusters() / spec.inertiaYaw
                * (in.inAir() ? VehicleDynamics.AIR_STEERING : 1.0);
        double yaw = 0.0;
        for (int k = 0; k < SUBSTEPS; k++) {
            // ---- le tangage : les deux propulseurs de sustentation
            double sin = Math.sin(s.pitch);
            double cos = Math.cos(s.pitch);
            double torque = 0.0;
            double damping = 0.0;
            for (int i = 0; i < 2; i++) {
                double a = arm[i];
                double lever = a * cos;
                double v = vy + lever * s.pitchRate;
                if (!Double.isNaN(ground[i])) {
                    // le ressort, a la distance du propulseur incline : 8 m g 0,5 spring f
                    double f = VehicleDynamics.springFactor(spec, ground[i] + a * sin);
                    torque += lever * 8.0 * m * G * 0.5 * spec.springLift * f;
                    if (v < 0.0) {
                        // son amortisseur : f de la vitesse du point qui descend, par pas du jeu
                        damping += lever * lever * f * m / GAME_STEP;
                    }
                }
                if (high) {
                    double f28 = in.f28() + a * sin;
                    if (climbing || f28 < 0.0) {
                        double command = clamp(-(f28 / VehicleDynamics.HIGH_RANGE + v * HIGH_RATE), -1.0, 1.0);
                        torque += lever * VehicleDynamics.HIGH_THRUST_G * 0.5 * m * G * command;
                    }
                    if ((climbing && f28 > -1.0 && v > 0.0) || (f28 < -1.0 && v < 0.0)) {
                        damping += lever * lever * m / 8.0 / GAME_STEP;
                    }
                }
                torque -= Math.abs(a) * clamp(PITCH_CONTROL * balance.pitchControl() * s.pitchRate, -1.0, 1.0) * m * G;
            }
            // amortisseurs implicites : raides, ils ne doivent jamais renverser la rotation
            s.pitchRate = (s.pitchRate + h * torque / spec.inertiaPitch) / (1.0 + h * damping / spec.inertiaPitch);

            // ---- le roulis : les propulseurs lateraux, et le poids du pilote
            double sinRoll = Math.sin(s.roll);
            double command = sinRoll * sinRoll * sinRoll + ROLL_LEVEL * sinRoll + ROLL_RATE * s.rollRate;
            double rollTorque = -Math.signum(command) * Math.min(1.0, Math.abs(command))
                    * ROLL_CONTROL * m * G * ROLL_THRUSTER_UP * ROLL_THRUSTER_X;
            if (in.driver()) {
                // a gauche (steer > 0), le poids passe a gauche : le cote gauche descend
                rollTorque -= DRIVER_SHIFT * in.steer() * VehicleSpec.DRIVER_WEIGHT * G;
            }
            s.rollRate += h * rollTorque / spec.inertiaRoll;

            // ---- la vrille : les propulseurs de direction la tuent
            s.yawRate /= 1.0 + h * steering;

            s.pitchRate = clamp(s.pitchRate * decay, -MAX_RATE, MAX_RATE);
            s.rollRate = clamp(s.rollRate * decay, -MAX_RATE, MAX_RATE);
            s.yawRate = clamp(s.yawRate * decay, -MAX_RATE, MAX_RATE);
            s.pitch += h * s.pitchRate;
            s.roll += h * s.rollRate;
            yaw += h * s.yawRate;
            if (Math.abs(s.pitch) > MAX_TILT) {
                s.pitch = Math.copySign(MAX_TILT, s.pitch);
                s.pitchRate = 0.0;
            }
            if (Math.abs(s.roll) > MAX_TILT) {
                s.roll = Math.copySign(MAX_TILT, s.roll);
                s.rollRate = 0.0;
            }
        }
        return -Math.toDegrees(yaw);
    }

    /**
     * Un choc : l'impulsion J (masse x m/s) appliquee au point r (m), tous deux dans le
     * repere du vehicule -- x a gauche, y en haut, z vers l'avant --, r depuis la racine du
     * modele. Seule la rotation change ici ; la vitesse, l'appelant la regle.
     */
    public static void impulse(VehicleSpec spec, State s, double rx, double ry, double rz,
                               double jx, double jy, double jz) {
        double az = rz - spec.balance.cmZ();
        double lx = ry * jz - az * jy;
        double ly = az * jx - rx * jz;
        double lz = rx * jy - ry * jx;
        // une rotation positive autour de +x tourne y vers z : le nez PLONGE, d'ou le signe
        s.pitchRate = clamp(s.pitchRate - lx / spec.inertiaPitch, -MAX_RATE, MAX_RATE);
        s.rollRate = clamp(s.rollRate + lz / spec.inertiaRoll, -MAX_RATE, MAX_RATE);
        s.yawRate = clamp(s.yawRate + ly / spec.inertiaYaw, -MAX_RATE, MAX_RATE);
    }

    /**
     * La part d'une impulsion de choc qui passe vraiment, quand le point touche est loin du
     * centre de masse : un vehicule heurte au coin tourne au lieu de reculer. C'est le terme
     * de rotation de l'impulsion d'un choc de corps rigides, 1 / (1 + m (r x n)2 / I), axe par
     * axe (repere et point comme {@link #impulse}, n unitaire).
     */
    public static double leverShare(VehicleSpec spec, double rx, double ry, double rz, double nx, double ny, double nz) {
        double az = rz - spec.balance.cmZ();
        double cx = ry * nz - az * ny;
        double cy = az * nx - rx * nz;
        double cz = rx * ny - ry * nx;
        double m = spec.mass;
        return 1.0 / (1.0 + m * (cx * cx / spec.inertiaPitch + cy * cy / spec.inertiaYaw + cz * cz / spec.inertiaRoll));
    }

    /**
     * Le roulis d'equilibre sous le poids du pilote, volant a fond, en radians : la
     * commande sin3 + 0,05 sin egale 0,6 / (1,02 m) (calcul, que l'autotest compare).
     */
    public static double bankAngle(VehicleSpec spec) {
        double ratio = Math.min(1.0, DRIVER_SHIFT * VehicleSpec.DRIVER_WEIGHT
                / (ROLL_CONTROL * spec.mass * ROLL_THRUSTER_UP * ROLL_THRUSTER_X));
        double low = 0.0;
        double high = 1.0;
        for (int i = 0; i < 60; i++) {
            double mid = (low + high) / 2.0;
            if (mid * mid * mid + ROLL_LEVEL * mid < ratio) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return Math.asin(low);
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
