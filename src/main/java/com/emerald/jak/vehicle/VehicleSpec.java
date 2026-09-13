package com.emerald.jak.vehicle;

import java.util.List;

/**
 * Les constantes d'une voiture de Haven, reprises du code GOAL de Jak 3 et
 * converties en ticks.
 *
 * TOUT EST EN BLOCS ET EN TICKS. Le jeu compte en metres et en secondes ; un
 * metre vaut un bloc et un tick un vingtieme de seconde, donc une vitesse en
 * m/s se divise par 20 et une acceleration en m/s2 par 400. La gravite de
 * 40 m/s2 des voitures devient 0,1 bloc par tick au carre.
 *
 * AUCUN TYPE MINECRAFT ICI, ni dans VehicleDynamics : les deux se compilent
 * seuls avec le JDK, ce qui permet de mesurer la physique hors du jeu avant de
 * la confier au serveur (voir le rapport du chantier).
 *
 * Sources : levels/city/traffic/vehicle/car.gc, *h-car-a-constants* (ligne 63),
 * *h-car-b-constants* (448), *h-car-c-constants* (827). Unites GOAL : 4096 = 1 m.
 * Les bornes des modeles sont celles de l'en-tete des .bin cuits par
 * tools/jak_vehicle.py (sommets indexes seulement).
 */
public final class VehicleSpec {

    /** Un tick, en secondes. */
    public static final double TICK = 0.05;

    /**
     * LA VITESSE MAXIMALE, en m/s : 40, celle du jeu pour les trois voitures
     * (max-xz-speed, car.gc:96, 481 et 860), soit 144 km/h et DEUX BLOCS PAR
     * TICK. Le joueur a retenu la vitesse de Jak 3.
     *
     * Les autres constantes s'en deduisent comme dans le jeu, sans retouche : le
     * moteur atteint sa pleine poussee a la moitie de cette vitesse (admission
     * 0,8333 (0,5 + v/vmax), hvehicle.gc:550-556), le frein reste a 54 m/s2 et le
     * gain de direction tombe a 15/55 de sa valeur a pleine vitesse. A deux blocs
     * par tick, les collisions passent par des sous-pas (VehiclePhysics.move).
     */
    public static final double MAX_SPEED_MS = 40.0;

    /** Gravite des voitures, m/s2 (car.gc:78). */
    public static final double GRAVITY_MS2 = 40.0;
    /** Portee des sondes sous les propulseurs, en blocs (car.gc:102). */
    public static final double PROBE_DISTANCE = 4.5;
    /** Facteur du ressort de rase-sol (car.gc:104). */
    public static final double SPRING_LIFT = 0.4;
    /** Frein : environ 24 m/s2 fois ce facteur au-dessus de 4 m/s (hvehicle-physics.gc:419-440). */
    public static final double BRAKE_FACTOR = 2.25;
    /** Admission du moteur (car.gc:91). */
    public static final double ENGINE_INTAKE = 1.0;
    /** Vitesse ou le gain de direction tombe de moitie, m/s (car.gc:108). */
    public static final double STEER_HALF_GAIN_MS = 15.0;
    /** Rebond sur un obstacle (car.gc:70). */
    public static final double BOUNCE = 0.4;
    /** Le poids du pilote, en masses de la voiture (player-weight 163840 = 1 masse x g). */
    public static final double DRIVER_WEIGHT = 1.0;

    /**
     * Largeur des boites de collision au plus, en blocs.
     *
     * Une boite Minecraft ne tourne pas : un carre trop large bouche les rues
     * dans les virages, un carre trop etroit laisse passer a travers les flancs.
     * On prend 70 % de la largeur du modele, plafonne a 4.
     */
    public static final double BOX_WIDTH_SHARE = 0.7;
    public static final double BOX_MAX_SIDE = 4.0;
    /**
     * Haut des boites au-dessus du siege du conducteur. L'oeil d'un joueur assis
     * est 1,02 bloc au-dessus du siege (attache de vehicule a 0,6, oeil a 1,62) :
     * une boite qui le couvrirait capterait son viseur en permanence.
     */
    public static final double BOX_ABOVE_SEAT = 0.9;

    public final String model;
    public final double minX, minY, minZ, maxX, maxY, maxZ;
    public final double mass;
    /** Poussee maximale du moteur, m/s2 (max-engine-thrust). */
    public final double engineThrustMs2;
    /** Reponse du moteur, par seconde (engine-response-rate). */
    public final double engineResponse;
    /** Gain de direction maximal, rad/s (steering-thruster-max-gain). */
    public final double steerGain;
    /** Les deux propulseurs de sustentation : avant, arriere, et leur hauteur. */
    public final double thrusterFrontZ, thrusterRearZ, thrusterY;
    /** Trois sieges {x, y, z} dans le repere du modele ; le premier est celui du conducteur. */
    private final double[][] seats;

    // ------------------------------------------------ derives, en blocs et ticks
    public final double gravity;
    public final double maxSpeed;
    public final double thrust;
    public final double brake;
    public final double halfGainSpeed;
    public final double centerX, centerZ;
    public final double boxSide, boxBottom, boxTop;
    /** Centres des trois parties de collision le long de l'axe z du modele. */
    private final double[] partZ;

    private VehicleSpec(String model, double[] min, double[] max, double mass, double engineThrustMs2,
                        double engineResponse, double steerGain, double thrusterFrontZ, double thrusterRearZ,
                        double thrusterY, double[][] seats) {
        this.model = model;
        this.minX = min[0];
        this.minY = min[1];
        this.minZ = min[2];
        this.maxX = max[0];
        this.maxY = max[1];
        this.maxZ = max[2];
        this.mass = mass;
        this.engineThrustMs2 = engineThrustMs2;
        this.engineResponse = engineResponse;
        this.steerGain = steerGain;
        this.thrusterFrontZ = thrusterFrontZ;
        this.thrusterRearZ = thrusterRearZ;
        this.thrusterY = thrusterY;
        this.seats = seats;

        this.gravity = GRAVITY_MS2 * TICK * TICK;
        this.maxSpeed = MAX_SPEED_MS * TICK;
        this.thrust = engineThrustMs2 * TICK * TICK;
        this.brake = 24.0 * BRAKE_FACTOR * TICK * TICK;
        this.halfGainSpeed = STEER_HALF_GAIN_MS * TICK;
        this.centerX = (this.minX + this.maxX) / 2.0;
        this.centerZ = (this.minZ + this.maxZ) / 2.0;
        this.boxSide = Math.min(BOX_MAX_SIDE, BOX_WIDTH_SHARE * (this.maxX - this.minX));
        this.boxBottom = this.minY;
        this.boxTop = Math.min(this.maxY, seats[0][1] + BOX_ABOVE_SEAT);
        double spacing = Math.max(0.0, ((this.maxZ - this.minZ) - this.boxSide) / 2.0);
        this.partZ = new double[]{this.centerZ + spacing, this.centerZ, this.centerZ - spacing};
    }

    /**
     * car-a : masse 8, poussee 39, reponse 20, gain 3,5 (car.gc:66-108) ;
     * propulseurs a z = +-2 m, y = 0,2 (8192 et 819,2, car.gc:282-291) ;
     * sieges car.gc:424-433 -- les deux avant peuvent conduire, on garde le
     * gauche (+x) pour le conducteur.
     */
    public static final VehicleSpec CARA = new VehicleSpec("cara",
            new double[]{-2.513751, -1.799057, -4.041718}, new double[]{2.513751, 1.725123, 4.386743},
            8.0, 39.0, 20.0, 3.5, 2.0, -2.0, 0.2,
            new double[][]{{0.996, 0.2, -0.076}, {-0.996, 0.2, -0.076}, {0.0, 0.916, -2.579}});

    /**
     * car-b : masse 6, poussee 40, reponse 10, gain 3,8 (car.gc:451-493) ;
     * propulseurs a z = +-2,2 m (9011,2, car.gc:667-676) ; sieges car.gc:803-812.
     */
    public static final VehicleSpec CARB = new VehicleSpec("carb",
            new double[]{-2.793297, -0.791022, -3.534881}, new double[]{2.793297, 1.433728, 4.128147},
            6.0, 40.0, 10.0, 3.8, 2.2, -2.2, 0.2,
            new double[][]{{1.03, 0.244, -0.042}, {-0.95, 0.244, -0.042}, {0.0, 0.916, -2.0}});

    /**
     * car-c : masse 9, poussee 35, reponse 10, gain 3 (car.gc:830-872) ;
     * propulseurs a z = +1,2 et -3,2 m, y = 0 (4915,2 et -13107,2, car.gc:1049-1058).
     * Le jeu lui donne quatre sieges (car.gc:1213-1225), conducteur au centre :
     * le joueur a retenu trois places par voiture, on garde les trois premiers.
     */
    public static final VehicleSpec CARC = new VehicleSpec("carc",
            new double[]{-2.276151, -0.898481, -5.091391}, new double[]{2.276151, 1.407620, 2.515746},
            9.0, 35.0, 10.0, 3.0, 1.2, -3.2, 0.0,
            new double[][]{{0.0, 0.186, 0.334}, {-0.7, 0.4, -0.7}, {0.7, 0.4, -0.7}});

    public static final List<VehicleSpec> ALL = List.of(CARA, CARB, CARC);

    /** La fiche d'un modele ; cara pour un nom inconnu, plutot qu'une voiture sans physique. */
    public static VehicleSpec of(String model) {
        for (VehicleSpec spec : ALL) {
            if (spec.model.equals(model)) {
                return spec;
            }
        }
        return CARA;
    }

    public int seatCount() {
        return this.seats.length;
    }

    /** Coordonnee {@code axis} (0 x, 1 y, 2 z) du siege {@code seat}, repere du modele. */
    public double seat(int seat, int axis) {
        return this.seats[seat][axis];
    }

    public int partCount() {
        return this.partZ.length;
    }

    public double partZ(int part) {
        return this.partZ[part];
    }

    /**
     * La distance d'equilibre des propulseurs au sol, en blocs.
     *
     * Le ressort vaut 8 g spring f (deux propulseurs a 0,5 chacun), le poids
     * g (m + pilote) / m : f = (m + pilote) / (8 m spring), et la distance
     * 1 + (1 - f)(P - 1). car-a pilotee : 3,27 blocs. Calcul, que l'autotest
     * compare a la hauteur mesuree.
     */
    public double equilibriumDistance(boolean driver) {
        double load = (this.mass + (driver ? DRIVER_WEIGHT : 0.0)) / this.mass;
        double f = load / (8.0 * SPRING_LIFT);
        return 1.0 + (1.0 - f) * (PROBE_DISTANCE - 1.0);
    }
}
