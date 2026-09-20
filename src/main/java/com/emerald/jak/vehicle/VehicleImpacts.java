package com.emerald.jak.vehicle;

import com.emerald.particles.ModParticles;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Les chocs des vehicules : contre un autre vehicule, contre un mur, contre quelqu'un.
 *
 * LE JOUEUR (19 sept.) : « s'il y a une collision, rien ne se passe ». Une voiture
 * s'arretait net contre une autre, comme contre un mur, et ne faisait rien a personne.
 *
 * TOUT SE DECIDE PAR LA QUANTITE DE MOUVEMENT, masse x vitesse (VehicleSpec) : une
 * car-c lancee (masse 9) pese quatre fois et demie une moto (masse 2), et deux fois
 * plus vite compte deux fois plus. Le plus lourd et le plus rapide garde sa route ;
 * l'autre part. C'est la regle de Jak 3, ou la masse et la vitesse du choc decident
 * de tout (rigid-body.gc, vehicle-damage-factor).
 *
 * QUI CALCULE QUOI. Le client du conducteur simule SA voiture (VehiclePhysics.tick),
 * le serveur simule le trafic et les voitures a l'arret : chacun calcule le choc du
 * vehicule qu'il simule, avec la vitesse de l'autre telle qu'il la connait
 * ({@link #vehicles}). Les deux arrivent au meme choc, chacun de son cote, sans
 * paquet ni autorite partagee -- et la voiture du conducteur ne subit jamais une
 * vitesse venue du serveur, qui la figeait (voir JakVehicleEntity.lerpMotion).
 *
 * CE QUI EST SUR LE SERVEUR SEUL : renverser quelqu'un ({@link #ram}, les degats et
 * les vies n'existent que la) et le bruit du choc ({@link #watch}, qui regarde la
 * vitesse perdue dans la tique -- un mur, un vehicule, peu importe).
 */
public final class VehicleImpacts {

    /** Rebond d'un choc entre vehicules : celui du jeu (car.gc:70). */
    public static final double RESTITUTION = VehicleSpec.BOUNCE;
    /** Vitesse d'approche sous laquelle deux vehicules se frolent sans choc, en blocs par tique. */
    public static final double MIN_APPROACH = 0.06;
    /** De combien on cherche autour de soi : la moitie du plus long vehicule, plus une marge. */
    public static final double SEARCH = 8.0;
    /** Le vehicule heurte est etourdi tant de tiques par unite de quantite de mouvement, 2 s au plus. */
    public static final int STUN_PER_MOMENTUM = 4;
    public static final int STUN_MAX = 40;

    /**
     * Les degats d'un renversement, en PV par unite de quantite de mouvement.
     *
     * Une car-a (masse 8) a pleine vitesse (2 blocs par tique) : 16 x 2,5 = 40 PV,
     * deux zombies casques. Une moto (masse 2) : 10 PV, un zombie a moitie. C'est
     * l'avantage que le joueur attend du poids et de la vitesse.
     */
    public static final float RAM_HP = 2.5F;
    /** Sous cette quantite de mouvement, on pousse sans blesser (une voiture qui manoeuvre). */
    public static final double RAM_MIN = 0.6;
    /** La vitesse donnee a qui est renverse, en blocs par tique et par unite de quantite de mouvement. */
    public static final double RAM_LAUNCH = 0.08;
    public static final double RAM_LAUNCH_MAX = 1.6;
    /** Ce que le vehicule perd a renverser quelqu'un, par renversement. */
    public static final double RAM_DRAG = 0.04;

    /** Perte de vitesse a plat, en blocs par tique, a partir de laquelle c'est un choc et non un freinage. */
    public static final double CRASH_DROP = 0.5;
    /** La perte qui donne le choc le plus fort (son et particules a fond). */
    public static final double CRASH_FULL = 2.5;

    /**
     * Le type de degat d'un renversement (data/emeraldweapons/damage_type/jak_vehicle.json).
     *
     * Il est dans la balise bypasses_invulnerability : les habitants de la ville paisible
     * naissent invulnerables (HavenSpawner.spawnVillager, pour qu'aucune arme ni aucune
     * chute ne les touche), et un vehicule doit pouvoir les renverser malgre tout --
     * decision du joueur du 20 sept., comme dans Jak 3. Seul ce type traverse : rien
     * d'autre ne peut les blesser.
     */
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("emeraldweapons", "jak_vehicle"));

    private VehicleImpacts() {
    }

    // ================================================================ vehicule contre vehicule

    /**
     * Le choc contre les autres vehicules, pour celui-ci seulement.
     *
     * A appeler par le cote qui simule {@code car}, AVANT son deplacement de la tique.
     * La vitesse qui en sort est celle d'apres le choc ; le deplacement se fait ensuite
     * normalement, et les boites font le reste.
     *
     * @return la quantite de mouvement du choc le plus fort de la tique, 0 sans choc
     */
    public static double vehicles(JakVehicleEntity car) {
        Level level = car.level();
        if (car.impactHandled(level.getGameTime())) {
            return 0.0;               // l'autre vehicule a deja regle ce choc pour nous, cette tique
        }
        Vec3 motion = car.impactVelocity();
        List<AABB> boxes = car.collisionBoxes();
        AABB reach = boxes.get(0);
        for (AABB box : boxes) {
            reach = reach.minmax(box);
        }
        reach = reach.expandTowards(motion.x, motion.y, motion.z).inflate(0.2);
        // LA RECHERCHE PORTE PLUS LOIN QUE LE CHOC. getEntitiesOfClass regarde la boite
        // d'entite de l'autre -- 3 blocs de cote, bien plus petite que le vehicule, dont
        // les vraies boites font huit blocs de long : cherchee sur sa boite d'entite, une
        // voiture pile devant restait introuvable (banc du 20 sept.). On cherche donc
        // large, et c'est touching() qui tranche, sur les vraies boites.
        double worst = 0.0;
        for (JakVehicleEntity other : level.getEntitiesOfClass(JakVehicleEntity.class, reach.inflate(SEARCH),
                o -> o != car && !o.isRemoved())) {
            if (!touching(car, other, motion)) {
                continue;
            }
            worst = Math.max(worst, resolve(car, other));
            motion = car.impactVelocity();
        }
        return worst;
    }

    /** Vrai si une boite de l'un recoupe une boite de l'autre, le deplacement de la tique compris. */
    private static boolean touching(JakVehicleEntity car, JakVehicleEntity other, Vec3 motion) {
        for (AABB mine : car.collisionBoxes()) {
            AABB swept = mine.expandTowards(motion.x, motion.y, motion.z).inflate(0.05);
            for (AABB theirs : other.collisionBoxes()) {
                if (swept.intersects(theirs)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Le choc de {@code car} contre {@code other} : la quantite de mouvement echangee,
     * appliquee A CAR SEUL (l'autre la calcule de son cote).
     *
     * La normale va de l'autre vers nous, a plat -- un vehicule qui vole ne se fait pas
     * enfoncer dans le sol par un choc de cote. Impulsion d'un choc a restitution e :
     * j = (1 + e) u mA mB / (mA + mB), ou u est la vitesse d'approche le long de la
     * normale ; chacun en recoit j / sa masse. Le lourd garde sa route, le leger part.
     *
     * @return la quantite de mouvement du choc, 0 s'ils ne se rapprochaient pas
     */
    public static double resolve(JakVehicleEntity car, JakVehicleEntity other) {
        Vec3 mine = car.impactVelocity();
        Vec3 theirs = other.impactBase();
        double nx = car.getX() - other.getX();
        double nz = car.getZ() - other.getZ();
        double length = Math.sqrt(nx * nx + nz * nz);
        if (length < 1.0E-4) {
            // pile l'un sur l'autre : on se separe dans l'axe de notre propre vitesse
            nx = mine.x;
            nz = mine.z;
            length = Math.sqrt(nx * nx + nz * nz);
            if (length < 1.0E-4) {
                return 0.0;
            }
        }
        nx /= length;
        nz /= length;
        double approach = (theirs.x - mine.x) * nx + (theirs.z - mine.z) * nz;
        if (approach <= MIN_APPROACH) {
            return 0.0;               // ils s'eloignent, ou se frolent
        }
        double mA = car.spec().mass;
        double mB = other.spec().mass;
        double impulse = (1.0 + RESTITUTION) * approach * mB / (mA + mB);
        car.setDeltaMovement(mine.x + nx * impulse, mine.y, mine.z + nz * impulse);
        car.hasImpulse = true;
        // LES DEUX PARTENT DANS LA MEME TIQUE, quand le meme cote les simule tous les deux.
        // Chacun de son cote, le percuteur voyait l'approche et rebondissait AVANT que le
        // percute ne tique : celui-ci ne voyait plus personne s'approcher et ne bougeait pas
        // (banc du 20 sept. : la moto ping-pongait contre une car-c immobile). On donne donc
        // sa part a l'autre tout de suite, et on lui dit que son choc est regle.
        if (other.isControlledByLocalInstance() && !other.impactHandled(other.level().getGameTime())) {
            double back = (1.0 + RESTITUTION) * approach * mA / (mA + mB);
            other.setDeltaMovement(theirs.x - nx * back, theirs.y, theirs.z - nz * back);
            other.hasImpulse = true;
            other.setImpactHandled(other.level().getGameTime());
            other.stun(stunTicks(approach * mA * mB / (mA + mB)));
        }
        return approach * mA * mB / (mA + mB);
    }

    // ================================================================ renverser quelqu'un

    /**
     * Ce que le vehicule renverse sur son passage, cote serveur.
     *
     * TOUT CE QUI MARCHE DANS LA RUE prend le choc : {@value #RAM_HP} PV par unite de
     * quantite de mouvement, et le corps part devant le vehicule. Monstres de
     * l'invasion comme habitants de la ville paisible -- decision du joueur du
     * 20 sept., comme dans Jak 3, ou l'on renverse les civils. LES JOUEURS ne sont
     * jamais touches (seulement ecartes, JakVehicleEntity.pushAside), et CEUX QUI SONT
     * A BORD d'un autre vehicule non plus : on heurte la voiture, pas son pilote.
     *
     * @return le nombre de creatures blessees
     */
    public static int ram(JakVehicleEntity car) {
        if (!(car.level() instanceof ServerLevel level)) {
            return 0;
        }
        Vec3 motion = car.serverMotion();
        double speed = Math.hypot(motion.x, motion.z);
        double momentum = speed * car.spec().mass;
        if (momentum < RAM_MIN) {
            return 0;
        }
        List<AABB> boxes = car.collisionBoxes();
        AABB reach = boxes.get(0);
        for (AABB box : boxes) {
            reach = reach.minmax(box);
        }
        Entity driver = car.getControllingPassenger();
        int hit = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, reach.inflate(0.3),
                m -> m.isAlive() && !m.isPassengerOfSameVehicle(car) && m != driver
                        && !(m.getVehicle() instanceof JakVehicleEntity))) {
            boolean inside = false;
            for (AABB box : boxes) {
                if (box.inflate(0.2).intersects(mob.getBoundingBox())) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                continue;
            }
            double launch = Math.min(RAM_LAUNCH_MAX, momentum * RAM_LAUNCH);
            double dx = speed < 1.0E-4 ? 0.0 : motion.x / speed;
            double dz = speed < 1.0E-4 ? 0.0 : motion.z / speed;
            // LA VITESSE EST POSEE, PAS AJOUTEE : sous le vehicule, le corps est repris a
            // chaque tique, et l'addition l'envoyait a 130 m/s au bout de six (banc du
            // 20 sept.). Pose, il file devant le vehicule a la vitesse du choc.
            mob.setDeltaMovement(dx * launch, launch * 0.35 + 0.1, dz * launch);
            mob.hurtMarked = true;
            mob.hasImpulse = true;
            rams++;
            lastLaunch = launch;
            // l'invulnerabilite vanilla de dix tiques n'est PAS remise a zero : un
            // vehicule qui traine un corps ne le frappe que deux fois par seconde
            hit += mob.hurt(source(level, car), (float) momentum * RAM_HP) ? 1 : 0;
            car.setDeltaMovement(car.getDeltaMovement().multiply(1.0 - RAM_DRAG, 1.0, 1.0 - RAM_DRAG));
        }
        return hit;
    }

    /**
     * Le coup porte par un vehicule : son type de degat, sans entite auteur.
     *
     * Le conducteur n'est PAS l'auteur : le coup passerait par tout ce qui gonfle les
     * coups d'un joueur (heros, runes, autres mods), et la regle de la masse ne
     * tiendrait plus -- c'est la meme raison qu'au Morph Gun (GunImpacts.source). Le
     * credit de la mise a mort, lui, revient bien au conducteur.
     */
    private static DamageSource source(ServerLevel level, JakVehicleEntity car) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE), null, null, car.position());
    }

    // ================================================================ le bruit du choc

    /**
     * Le choc vu du serveur : la vitesse a plat perdue en une tique.
     *
     * Un mur, un autre vehicule, une pile de monstres : peu importe la cause, ce qui se
     * voit et s'entend, c'est la vitesse qui disparait d'un coup. Le freinage le plus
     * dur ne retire que 0,14 bloc par tique ({@value VehicleSpec#GRAVITY_MS2} m/s2 de
     * frein), tres loin de {@value #CRASH_DROP} : les deux ne se confondent pas.
     *
     * A appeler a chaque tique du serveur, apres le deplacement.
     *
     * @return la force du choc, de 0 (aucun) a 1 (le plus fort)
     */
    public static double watch(JakVehicleEntity car) {
        if (!(car.level() instanceof ServerLevel level)) {
            return 0.0;
        }
        Vec3 motion = car.serverMotion();
        double speed = Math.hypot(motion.x, motion.z);
        double before = car.lastFlatSpeed();
        car.setLastFlatSpeed(speed);
        double drop = before - speed;
        if (drop < CRASH_DROP || car.tickCount < 5) {
            return 0.0;
        }
        double force = Math.min(1.0, (drop - CRASH_DROP) / (CRASH_FULL - CRASH_DROP));
        crashes++;
        Vec3 at = car.position().add(0.0, car.spec().boxTop * 0.5, 0.0);
        SoundEvent sound = force > 0.5 ? SoundEvents.ANVIL_LAND : SoundEvents.IRON_GOLEM_DAMAGE;
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL,
                0.5F + 0.5F * (float) force, 0.8F + 0.4F * level.random.nextFloat());
        level.sendParticles(ModParticles.JAK_VEHICLE_SPARK.get(), at.x, at.y, at.z,
                4 + (int) (force * 16.0), 0.5, 0.3, 0.5, 0.25);
        return force;
    }

    /** Chocs assez forts pour s'entendre depuis le demarrage (banc d'essai). */
    public static int crashes() {
        return crashes;
    }

    private static int crashes;
    private static int rams;
    private static double lastLaunch;

    /** Creatures renversees depuis le demarrage, et la derniere vitesse donnee (banc d'essai). */
    public static int rams() {
        return rams;
    }

    public static double lastLaunch() {
        return lastLaunch;
    }

    /** Les tiques d'etourdissement d'un vehicule heurte, d'apres la quantite de mouvement recue. */
    public static int stunTicks(double momentum) {
        return (int) Math.min(STUN_MAX, Math.round(momentum * STUN_PER_MOMENTUM));
    }
}
