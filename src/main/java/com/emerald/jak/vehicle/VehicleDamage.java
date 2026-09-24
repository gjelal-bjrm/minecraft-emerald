package com.emerald.jak.vehicle;

import com.emerald.haven.HavenGear;
import com.emerald.haven.traffic.HavenTraffic;
import com.emerald.jak.gun.GunSpec;
import com.emerald.particles.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Locale;

/**
 * LES DEGATS DES VEHICULES DE HAVEN (cahier §98), repris de Jak 3.
 *
 * Le joueur (24 sept.) : des etats -- parfait, bon, moyen, mal, detruit --, de la fumee des
 * « moyen », de plus en plus noire, une explosion ; la destruction par les chocs, les coups et
 * les armes. Jusqu'ici tous les vehicules etaient indestructibles.
 *
 * LA SANTE est une part, de 1 (neuf) a 0, comme dans le jeu (vehicle.hit-points,
 * vehicle-h.gc:573) : chaque coup en retire ses points divises par ceux du vehicule, 24 pour une
 * voiture, 15 pour une moto (car.gc:252, bike.gc:261), et un quart de moins quand un joueur
 * conduit (hvehicle.gc:1099). Les points sont ceux des armes de Jak : un tir de Blaster en vaut
 * deux, douze tirs pour une voiture vide.
 *
 * LES CHOCS (vehicle.gc:41-171) comptent la vitesse perdue d'un coup : moins de 25 m/s, presque
 * rien ; 25, cinq points ; 32, dix ; 70, la mort. Un mur pris a pleine vitesse, dix points : trois
 * ou quatre suffisent a la voiture d'un joueur. UN COUP DE POING en vaut quatre (vehicle.gc:1056),
 * un toutes les demi-secondes, et seulement a mains nues, comme toute la ville (HavenGear). LES
 * ARMES : les points de chaque tir, ceux du jeu (VehicleDamage.shot) ; la grenade Plasmite
 * detruit d'un coup (36), l'onde du Wave Concussor vaut 8 fois sa charge, l'Arc 5 par
 * accroche, la Super Nova detruit tout ce qu'elle souffle. Ses propres tirs ne touchent jamais
 * le vehicule ou l'on est (vehicle.gc:1140-1145).
 *
 * DETRUIT (vehicle-states.gc:225-331) : les occupants sautent, le pilote du trafic disparait, le
 * vehicule tombe ; sa sante coule d'une demi-part par seconde et il EXPLOSE a -0,25 -- tout de
 * suite apres un choc mortel. L'explosion (7,5 m pour une voiture, 5,6 pour une moto) blesse ce
 * qui est pres de deux points de Jak -- un quart de sa vie pour un joueur, comme Jak -- et souffle
 * et abime les autres vehicules : les explosions en chaine sont possibles. L'epave, noircie,
 * saute, tourne, retombe et fume cinq secondes, puis disparait ; une voiture d'appartement revient
 * alors neuve a sa place (HavenCars). Pas de reparation : le jeu n'en a pas.
 */
public final class VehicleDamage {

    /** Les points de Jak d'une voiture neuve, les trois (car.gc:252, 637, 1019). */
    public static final float CAR_HIT_POINTS = 24.0F;
    /** Ceux d'une moto (bike.gc:261, 602, 945). */
    public static final float BIKE_HIT_POINTS = 15.0F;
    /** Sous un joueur, un vehicule prend un quart de degats en moins (hvehicle.gc:1099). */
    public static final float DRIVEN = 0.7518797F;

    /** Les paliers d'un choc, en m/s de vitesse changee (vehicle.gc:79-115 : 102400, 131072, 286720). */
    public static final double SMALL_MS = 25.0;
    public static final double BIG_MS = 32.0;
    public static final double LETHAL_MS = 70.0;
    public static final float SMALL_POINTS = 5.0F;
    public static final float BIG_POINTS = 10.0F;
    /** Sous le premier palier, un point par 100 m/s (s / 409600). */
    public static final double SOFT_MS_PER_POINT = 100.0;

    /** Un coup de poing (vehicle.gc:1056). */
    public static final float PUNCH = 4.0F;
    /** Entre deux coups de poing qui comptent, en tiques : le temps d'un coup de Jak. */
    public static final int PUNCH_TICKS = 10;

    /** La grenade Plasmite : 36 points, la mort d'un coup (gun-red-shot.gc:1920-1928). */
    public static final float PLASMITE = 36.0F;
    /** L'onde du Wave Concussor : 8 points a pleine charge (vehicle.gc:1040-1054). */
    public static final float WAVE = 8.0F;
    /** L'Arc Wielder : 5 points par accroche (gun-blue-shot.gc:2514). */
    public static final float ARC = 5.0F;
    /** Ce que la Super Nova souffle est detruit ; dans le jeu, il disparait sans exploser. */
    public static final float NOVA = 1000.0F;

    /** Sous zero, la sante coule d'une demi-part par seconde (vehicle-states.gc:236-240)... */
    public static final float DRAIN_PER_TICK = 0.025F;
    /** ... et le vehicule explose a -0,25 : une demi-seconde au plus. */
    public static final float EXPLODE_AT = -0.25F;
    /** Le rayon de l'explosion, 3 m plus la sphere du vehicule (hvehicle.gc:1127-1135). */
    public static final double CAR_BLAST_RADIUS = 7.5;
    public static final double BIKE_BLAST_RADIUS = 5.6;
    /** Ce qu'elle fait a ce qui est dans son rayon, en points de Jak (hvehicle.gc:1150-1160). */
    public static final float BLAST_POINTS = 2.0F;
    /** Un joueur n'a pas les huit points de vie de Jak : il perd le quart des siens, comme lui. */
    public static final float BLAST_PLAYER_SHARE = 0.25F;
    /** Le souffle sur les autres vehicules, en masse x blocs par tique au centre (VehicleImpacts.blast). */
    public static final double BLAST_STRENGTH = 2.4;
    /** Ce qui est souffle recule d'autant, en blocs par tique, au centre. */
    public static final double BLAST_PUSH = 0.7;
    /** L'epave saute : 5 m/s pour une voiture, 20 pour une moto (163840 sur la masse, hvehicle.gc:1170). */
    public static final double CAR_KICK = 0.25;
    public static final double BIKE_KICK = 1.0;
    /** Elle tourne sur elle-meme, jusqu'a tant de radians par tique, tant qu'elle est en l'air. */
    public static final float SPIN = 0.12F;
    /** L'epave reste tant de tiques apres l'explosion, puis disparait. */
    public static final int WRECK_TICKS = 100;
    /** Les occupants sautent en descendant : 2,5 blocs (pilot-states.gc:752-798). */
    public static final double EJECT_HOP = 0.63;

    private static int explosions;

    private VehicleDamage() {
    }

    // ================================================================ les etats

    /** L'etat d'un vehicule, vu de sa sante : ce que dit la fumee, et l'indice du conducteur. */
    public enum State {
        PARFAIT, BON, MOYEN, MAL, DETRUIT;

        public static State of(float health) {
            return health >= 1.0F ? PARFAIT : health >= 0.75F ? BON : health >= 0.5F ? MOYEN
                    : health > 0.0F ? MAL : DETRUIT;
        }

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    // ================================================================ les coups

    /** Le vehicule d'une entite touchee : lui-meme, ou celui d'une de ses boites ; null sinon. */
    @Nullable
    public static JakVehicleEntity vehicleOf(@Nullable Entity entity) {
        if (entity instanceof JakVehicleEntity car) {
            return car;
        }
        return entity instanceof VehiclePart part ? part.getParent() : null;
    }

    /** Un vehicule que ce tireur peut toucher : pas detruit, et pas celui ou il est. */
    public static boolean shootable(Entity entity, Entity shooter) {
        JakVehicleEntity car = vehicleOf(entity);
        return car != null && !car.isRemoved() && car.health() > 0.0F && shooter.getRootVehicle() != car;
    }

    /**
     * Un tir d'arme sur un vehicule : ses points de Jak, sauf sur le vehicule du tireur.
     *
     * @return vrai si le tir a compte
     */
    public static boolean shot(JakVehicleEntity car, @Nullable ServerPlayer shooter, float points) {
        if (shooter != null && shooter.getRootVehicle() == car) {
            return false;
        }
        return damage(car, points);
    }

    /**
     * Un coup de poing sur un vehicule (JakVehicleEntity.hurt) : quatre points, a mains nues
     * seulement -- en ville, rien du dehors ne blesse (HavenGear) --, un toutes les demi-secondes.
     */
    public static boolean punch(JakVehicleEntity car, DamageSource source) {
        if (!HavenGear.fist(source) || !(source.getEntity() instanceof Player player)
                || player.getRootVehicle() == car) {
            return false;
        }
        long now = car.level().getGameTime();
        if (now - car.lastPunch() < PUNCH_TICKS) {
            return false;
        }
        car.setLastPunch(now);
        car.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.IRON_GOLEM_HURT,
                SoundSource.NEUTRAL, 0.6F, 1.3F);
        return damage(car, PUNCH);
    }

    /**
     * Un choc de force {@code force} (VehicleImpacts.crash) : la vitesse perdue d'un coup, puis
     * le palier du jeu. Jak 3 compte la vitesse CHANGEE, rebond compris -- 1 + 0,4 fois la vitesse
     * contre un mur ; la vitesse perdue n'en voit que 1 - 0,4 : on la remet a l'echelle du jeu.
     */
    public static void collision(JakVehicleEntity car, double force) {
        double lost = (VehicleImpacts.CRASH_DROP + force * (VehicleImpacts.CRASH_FULL - VehicleImpacts.CRASH_DROP))
                / VehicleSpec.TICK;
        double severity = lost * (1.0 + VehicleSpec.BOUNCE) / (1.0 - VehicleSpec.BOUNCE);
        float points = severity >= LETHAL_MS ? 2.0F * hitPoints(car) : severity >= BIG_MS ? BIG_POINTS
                : severity >= SMALL_MS ? SMALL_POINTS : (float) (severity / SOFT_MS_PER_POINT);
        damage(car, points);
    }

    /**
     * Une explosion d'arme sur les vehicules : chacun, s'il a un point a moins de {@code radius}
     * d'elle, prend ses points -- sauf celui du tireur.
     *
     * @return le nombre de vehicules touches
     */
    public static int explosion(ServerLevel level, Vec3 center, double radius, float points,
                                @Nullable ServerPlayer shooter) {
        int touched = 0;
        for (JakVehicleEntity car : around(level, center, radius)) {
            touched += shot(car, shooter, points) ? 1 : 0;
        }
        return touched;
    }

    /** Les vehicules intacts dont un point est a moins de {@code radius} du centre. */
    private static java.util.List<JakVehicleEntity> around(ServerLevel level, Vec3 center, double radius) {
        java.util.List<JakVehicleEntity> found = new ArrayList<>();
        for (JakVehicleEntity car : level.getEntitiesOfClass(JakVehicleEntity.class,
                new AABB(center, center).inflate(radius + VehicleImpacts.SEARCH), c -> !c.isRemoved() && c.health() > 0.0F)) {
            for (AABB box : car.collisionBoxes()) {
                Vec3 p = new Vec3(Math.max(box.minX, Math.min(box.maxX, center.x)),
                        Math.max(box.minY, Math.min(box.maxY, center.y)),
                        Math.max(box.minZ, Math.min(box.maxZ, center.z)));
                if (p.distanceToSqr(center) <= radius * radius) {
                    found.add(car);
                    break;
                }
            }
        }
        return found;
    }

    /** Les points de Jak de ce vehicule neuf. */
    public static float hitPoints(JakVehicleEntity car) {
        return car.spec().isBike() ? BIKE_HIT_POINTS : CAR_HIT_POINTS;
    }

    /**
     * Retire des points a un vehicule, cote serveur. Sous zero, il est detruit.
     *
     * @return vrai si le coup a compte
     */
    public static boolean damage(JakVehicleEntity car, float points) {
        if (car.level().isClientSide || car.isRemoved() || car.health() <= 0.0F || !(points > 0.0F)) {
            return false;
        }
        float scale = car.getControllingPassenger() instanceof Player ? DRIVEN : 1.0F;
        float health = car.health() - points * scale / hitPoints(car);
        car.setHealth(health);
        if (health <= 0.0F) {
            breakDown(car, health);
        }
        return true;
    }

    // ================================================================ la destruction

    /**
     * Le vehicule est detruit : ses occupants sautent, le pilote du trafic disparait, et
     * l'explosion part quand la sante, qui coule, atteint -0,25.
     */
    private static void breakDown(JakVehicleEntity car, float health) {
        Vec3 carried = car.serverMotion();
        for (Entity passenger : new ArrayList<>(car.getPassengers())) {
            if (passenger.getTags().contains(HavenTraffic.DRIVER_TAG)) {
                passenger.stopRiding();
                passenger.discard();
                continue;
            }
            passenger.stopRiding();
            passenger.setDeltaMovement(carried.x, EJECT_HOP, carried.z);
            passenger.hurtMarked = true;
        }
        car.setDeltaMovement(carried);
        int ticks = (int) Math.ceil(Math.max(0.0F, health - EXPLODE_AT) / DRAIN_PER_TICK);
        car.startWreck(ticks);
    }

    /**
     * Une tique d'un vehicule detruit, cote serveur (JakVehicleEntity.tick) : la sante coule
     * jusqu'a l'explosion, puis l'epave tombe, tourne, fume, et disparait.
     */
    public static void wreckTick(JakVehicleEntity car) {
        ServerLevel level = (ServerLevel) car.level();
        int clock = car.advanceWreck();
        if (!car.wrecked()) {
            car.setHealth(car.health() - DRAIN_PER_TICK);
            if (clock >= car.explodeTick()) {
                explode(level, car);
            }
        } else if (clock >= car.explodeTick() + WRECK_TICKS) {
            if (car.traffic() != null) {
                HavenTraffic.remove(car);
            } else {
                car.discard();
            }
            return;
        }
        fall(car);
    }

    /**
     * L'epave tombe (la gravite des vehicules), glisse, rebondit a peine, et tourne tant qu'elle
     * vole. Elle bute avec toutes ses boites, en sous-pas (VehiclePhysics.move) : la boite
     * centrale seule laissait son nez entrer dans les murs (banc).
     */
    private static void fall(JakVehicleEntity car) {
        Vec3 v = car.getDeltaMovement().add(0.0, -VehicleSpec.GRAVITY_MS2 * VehicleSpec.TICK * VehicleSpec.TICK, 0.0);
        Vec3 moved = VehiclePhysics.move(car, v);
        if (v.y < 0.0 && moved.y > v.y + 1.0E-6) {
            v = new Vec3(v.x * 0.6, 0.0, v.z * 0.6);
            car.stopSpin();
        } else {
            v = v.scale(0.99);                          // l'amortissement de l'epave (hvehicle.gc:1175)
        }
        car.setDeltaMovement(v);
        car.syncParts();
        car.spinWreck();
    }

    /** L'explosion : le feu, le bruit, ce qu'elle blesse et souffle, et l'epave qui saute. */
    private static void explode(ServerLevel level, JakVehicleEntity car) {
        explosions++;
        boolean bike = car.spec().isBike();
        double radius = bike ? BIKE_BLAST_RADIUS : CAR_BLAST_RADIUS;
        Vec3 center = car.position().add(0.0, (car.spec().boxTop + car.spec().boxBottom) * 0.5, 0.0);
        if (bike) {
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 3, 0.6, 0.4, 0.6, 0.0);
        } else {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 5, 1.6, 0.6, 1.6, 0.0);
        }
        // les debris : seize eclats noirs qui retombent (hvehicle.gc:1180), et des flammeches
        level.sendParticles(ParticleTypes.LARGE_SMOKE, center.x, center.y, center.z, 16, 1.2, 0.6, 1.2, 0.08);
        level.sendParticles(ModParticles.JAK_VEHICLE_SPARK.get(), center.x, center.y, center.z, 24, 0.8, 0.5, 0.8, 0.6);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL,
                bike ? 2.5F : 4.0F, 0.8F + 0.2F * level.random.nextFloat());

        DamageSource source = VehicleImpacts.source(level, car);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && e.getBoundingBox().getCenter().distanceTo(center) <= radius)) {
            float hp = victim instanceof Player ? victim.getMaxHealth() * BLAST_PLAYER_SHARE : BLAST_POINTS * GunSpec.HP_PER_JAK;
            victim.hurt(source, hp);
            Vec3 away = victim.position().subtract(center);
            double distance = Math.max(0.5, away.length());
            Vec3 push = new Vec3(away.x, 0.0, away.z).normalize().scale(BLAST_PUSH * (1.0 - distance / radius));
            victim.push(push.x, 0.3 * (1.0 - distance / radius), push.z);
            victim.hurtMarked = true;
        }
        VehicleImpacts.blast(level, center, radius, BLAST_STRENGTH);
        for (JakVehicleEntity other : around(level, center, radius)) {
            if (other != car) {
                damage(other, BLAST_POINTS);
            }
        }

        car.setWrecked();
        double kick = bike ? BIKE_KICK : CAR_KICK;
        car.setDeltaMovement(car.getDeltaMovement().add((level.random.nextDouble() - 0.5) * 0.2, kick,
                (level.random.nextDouble() - 0.5) * 0.2));
        car.startSpin((level.random.nextFloat() - 0.5F) * 2.0F * SPIN, (level.random.nextFloat() - 0.5F) * 2.0F * SPIN);
    }

    /** Vehicules explose depuis le demarrage (banc d'essai). */
    public static int explosions() {
        return explosions;
    }
}
