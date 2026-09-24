package com.emerald.jak.vehicle;

import com.emerald.particles.ModParticles;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
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
 *
 * L'EQUILIBRE (retour du joueur du 21 sept., VehicleAttitude) : chaque choc porte en un
 * point, et ce point decide de la suite. Contre un vehicule, au centre de la zone ou leurs
 * boites se recoupent ; contre le decor, sur la face de la boite qui a bute
 * ({@link #wall}) ; une explosion, au point du vehicule le plus proche d'elle
 * ({@link #blast}). Heurte sous son centre de masse, le vehicule gite ; de face, il pique
 * du nez ; de biais, il vrille -- puis ses propulseurs le remettent d'aplomb.
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

    /** Sous ce changement de vitesse, en blocs par tique, un appui contre le decor n'incline rien. */
    public static final double WALL_MIN = 0.05;

    /**
     * Le souffle des explosions sur les vehicules : l'impulsion au centre, en masse x
     * blocs par tique, qui decroit jusqu'a zero au rayon. Le Plasmite RPG pousse une car-a
     * de 0,5 bloc par tique a bout portant ; la Super Nova balaie une place entiere.
     */
    public static final double BLAST_PLASMITE = 4.0;
    public static final double BLAST_PLASMITE_RADIUS = 10.0;
    public static final double BLAST_PEACE = 3.0;
    public static final double BLAST_PEACE_RADIUS = 8.0;
    public static final double BLAST_NOVA = 12.0;
    public static final double BLAST_NOVA_RADIUS = 40.0;
    /** Le souffle souleve : la part verticale ajoutee a sa direction, avant normalisation. */
    public static final double BLAST_LIFT = 0.6;
    /** Aucun vehicule ne part a plus de 0,8 bloc par tique (16 m/s) : une moto pese quatre fois moins qu'une voiture. */
    public static final double BLAST_MAX_SPEED = 0.8;

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
            Vec3 at = contact(car, other, motion);
            if (at == null) {
                continue;
            }
            double hit = resolve(car, other, at);
            worst = Math.max(worst, hit);
            motion = car.impactVelocity();
            // UNE VOITURE DU TRAFIC HEURTEE PAR UN JOUEUR AU VOLANT : le seul calcul ou le serveur
            // voit les deux (la voiture du joueur est simulee par son client) -- pour le chauffard
            // de Keira (quetes de Haven, cahier §86)
            if (hit > 0.0 && !level.isClientSide() && car.traffic() != null
                    && other.getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer driver) {
                com.emerald.haven.quest.HavenQuests.onRam(driver, car, hit);
            }
        }
        return worst;
    }

    /**
     * Le point du choc : le centre de la plus grande zone ou une boite de l'un, deplacement
     * de la tique compris, recoupe une boite de l'autre ; null s'ils ne se touchent pas.
     */
    @Nullable
    private static Vec3 contact(JakVehicleEntity car, JakVehicleEntity other, Vec3 motion) {
        Vec3 best = null;
        double bestVolume = -1.0;
        for (AABB mine : car.collisionBoxes()) {
            AABB swept = mine.expandTowards(motion.x, motion.y, motion.z).inflate(0.05);
            for (AABB theirs : other.collisionBoxes()) {
                if (!swept.intersects(theirs)) {
                    continue;
                }
                AABB overlap = swept.intersect(theirs);
                double volume = overlap.getXsize() * overlap.getYsize() * overlap.getZsize();
                if (volume > bestVolume) {
                    bestVolume = volume;
                    best = overlap.getCenter();
                }
            }
        }
        return best;
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
        Vec3 at = contact(car, other, car.impactVelocity());
        return resolve(car, other, at != null ? at : car.position().add(other.position()).scale(0.5));
    }

    /** Comme {@link #resolve(JakVehicleEntity, JakVehicleEntity)}, le choc portant au point {@code at} (equilibre). */
    public static double resolve(JakVehicleEntity car, JakVehicleEntity other, Vec3 at) {
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
        car.tilt(new Vec3(nx * impulse * mA, 0.0, nz * impulse * mA), at);
        // LES DEUX PARTENT DANS LA MEME TIQUE, quand le meme cote les simule tous les deux.
        // Chacun de son cote, le percuteur voyait l'approche et rebondissait AVANT que le
        // percute ne tique : celui-ci ne voyait plus personne s'approcher et ne bougeait pas
        // (banc du 20 sept. : la moto ping-pongait contre une car-c immobile). On donne donc
        // sa part a l'autre tout de suite, et on lui dit que son choc est regle.
        if (other.isControlledByLocalInstance() && !other.impactHandled(other.level().getGameTime())) {
            double back = (1.0 + RESTITUTION) * approach * mA / (mA + mB);
            other.setDeltaMovement(theirs.x - nx * back, theirs.y, theirs.z - nz * back);
            other.hasImpulse = true;
            other.tilt(new Vec3(-nx * back * mB, 0.0, -nz * back * mB), at);
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
            double dx = speed < 1.0E-4 ? 0.0 : motion.x / speed;
            double dz = speed < 1.0E-4 ? 0.0 : motion.z / speed;
            if (com.emerald.haven.fauna.HavenFauna.sparedByVehicles(mob) || mob instanceof com.emerald.haven.quest.HavenNpcEntity) {
                // UN ANIMAL DE LA VILLE NE SE RENVERSE PAS (cahier §85) : ecarte sur le cote,
                // sans un point de degat -- un chat, une mouette, un phoque
                double side = (mob.getX() - car.getX()) * -dz + (mob.getZ() - car.getZ()) * dx >= 0.0 ? 1.0 : -1.0;
                mob.setDeltaMovement(-dz * side * 0.6, 0.25, dx * side * 0.6);
                mob.hurtMarked = true;
                continue;
            }
            double launch = Math.min(RAM_LAUNCH_MAX, momentum * RAM_LAUNCH);
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
    static DamageSource source(ServerLevel level, JakVehicleEntity car) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE), null, null, car.position());
    }

    // ================================================================ l'equilibre : le decor, le souffle

    /**
     * Le choc contre le decor, pour l'equilibre : la vitesse perdue sur un axe bloque,
     * appliquee sur la face de la boite qui a bute la premiere (VehiclePhysics.move).
     *
     * Le mur de face arrete la boite avant, sous le centre de masse : le nez pique. Le mur
     * aborde de biais arrete l'avant et pas l'arriere : la voiture vrille et repart dans
     * l'angle du rebond. La vitesse elle-meme est deja reglee (VehiclePhysics.bounce).
     *
     * @param wanted   le deplacement demande dans la tique
     * @param after    la vitesse d'apres le choc
     * @param stoppers par axe, l'indice de la boite qui a bute, ou -1
     */
    public static void wall(JakVehicleEntity car, Vec3 wanted, Vec3 after, int[] stoppers) {
        List<AABB> boxes = car.collisionBoxes();
        double mass = car.spec().mass;
        for (int axis = 0; axis < 3; axis++) {
            int index = stoppers[axis];
            if (index < 0 || index >= boxes.size()) {
                continue;
            }
            double change = component(after, axis) - component(wanted, axis);
            if (Math.abs(change) < WALL_MIN) {
                continue;
            }
            AABB box = boxes.get(index);
            Vec3 c = box.getCenter();
            boolean positive = component(wanted, axis) > 0.0;
            Vec3 at = switch (axis) {
                case 0 -> new Vec3(positive ? box.maxX : box.minX, c.y, c.z);
                case 1 -> new Vec3(c.x, positive ? box.maxY : box.minY, c.z);
                default -> new Vec3(c.x, c.y, positive ? box.maxZ : box.minZ);
            };
            double j = change * mass;
            car.tilt(axis == 0 ? new Vec3(j, 0.0, 0.0) : axis == 1 ? new Vec3(0.0, j, 0.0) : new Vec3(0.0, 0.0, j), at);
            walls++;
        }
    }

    private static double component(Vec3 v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    /**
     * Le souffle d'une explosion sur les vehicules, cote serveur : chacun est pousse,
     * souleve et incline depuis son point le plus proche d'elle, d'autant plus qu'il en est
     * pres ; le trafic touche perd le volant.
     *
     * La voiture d'un joueur, c'est SON client qui la simule : il recoit le choc
     * (VehicleImpulsePayload) et l'applique lui-meme.
     *
     * @param strength l'impulsion au centre, en masse x blocs par tique
     * @return le nombre de vehicules touches
     */
    public static int blast(ServerLevel level, Vec3 center, double radius, double strength) {
        int touched = 0;
        for (JakVehicleEntity car : level.getEntitiesOfClass(JakVehicleEntity.class,
                new AABB(center, center).inflate(radius + SEARCH), c -> !c.isRemoved())) {
            Vec3 near = null;
            double best = Double.MAX_VALUE;
            for (AABB box : car.collisionBoxes()) {
                Vec3 p = new Vec3(Math.max(box.minX, Math.min(box.maxX, center.x)),
                        Math.max(box.minY, Math.min(box.maxY, center.y)),
                        Math.max(box.minZ, Math.min(box.maxZ, center.z)));
                double d = p.distanceToSqr(center);
                if (d < best) {
                    best = d;
                    near = p;
                }
            }
            double distance = Math.sqrt(best);
            if (near == null || distance > radius) {
                continue;
            }
            Vec3 away = car.position().subtract(center);
            Vec3 flat = new Vec3(away.x, 0.0, away.z);
            flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
            Vec3 direction = flat.add(0.0, BLAST_LIFT, 0.0).normalize();
            double force = Math.min(strength * (1.0 - distance / radius), BLAST_MAX_SPEED * car.spec().mass);
            Vec3 impulse = direction.scale(force);
            if (car.isControlledByLocalInstance()) {
                car.applyImpulse(impulse, near);
                if (car.traffic() != null) {
                    car.stun(stunTicks(force));
                }
            } else if (car.getControllingPassenger() instanceof ServerPlayer driver) {
                PacketDistributor.sendToPlayer(driver, new VehicleImpulsePayload(car.getId(), impulse, near));
            }
            touched++;
        }
        blasts += touched;
        return touched;
    }

    /** Chocs contre le decor qui ont incline un vehicule, et vehicules souffles, depuis le demarrage (banc d'essai). */
    public static int walls() {
        return walls;
    }

    public static int blasts() {
        return blasts;
    }

    private static int walls;
    private static int blasts;

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
        if (car.remoteDriven()) {
            // LA VOITURE D'UN JOUEUR : C'EST SON CLIENT QUI ENTEND LE CHOC (22 sept., cahier
            // §83). Le serveur n'a que les positions recues : une tique ou n'arrive aucun
            // paquet du conducteur (les deux horloges derivent) lui montrait une voiture
            // arretee net, et le choc sonnait en plein ciel -- « des collisions avec rien du
            // tout ». Le client, lui, a la vraie vitesse (watchDriver).
            car.setLastFlatSpeed(0.0);
            return 0.0;
        }
        double force = drop(car, car.serverMotion());
        if (force > 0.0) {
            crash(level, car, force);
        }
        return force;
    }

    /**
     * Le choc vu par le client qui conduit, sur la vitesse de sa propre simulation : il
     * l'annonce au serveur, qui le joue pour tous (VehicleCrashPayload, {@link #reported}).
     * A appeler a chaque tique du client du conducteur, apres le deplacement.
     */
    public static void watchDriver(JakVehicleEntity car) {
        double force = drop(car, car.getDeltaMovement());
        if (force > 0.0) {
            PacketDistributor.sendToServer(new VehicleCrashPayload((float) force));
        }
    }

    /** La vitesse a plat perdue depuis la tique precedente, en force de choc (0 : aucun). */
    private static double drop(JakVehicleEntity car, Vec3 motion) {
        double speed = Math.hypot(motion.x, motion.z);
        double before = car.lastFlatSpeed();
        car.setLastFlatSpeed(speed);
        double drop = before - speed;
        if (drop < CRASH_DROP || car.tickCount < 5) {
            return 0.0;
        }
        return Math.min(1.0, (drop - CRASH_DROP) / (CRASH_FULL - CRASH_DROP));
    }

    /** Le plus court ecart entre deux chocs annonces par un client, en tiques. */
    private static final int REPORT_GAP = 4;

    /** Le choc annonce par le client du conducteur : verifie, puis joue pour tous. */
    public static void reported(ServerPlayer player, float force) {
        if (!(player.getVehicle() instanceof JakVehicleEntity car) || car.getControllingPassenger() != player
                || !(force > 0.0F) || !(car.level() instanceof ServerLevel level)) {
            return;                                     // !(force > 0) ecarte aussi NaN
        }
        long now = level.getGameTime();
        if (now - car.lastReportedCrash() < REPORT_GAP) {
            return;
        }
        car.setLastReportedCrash(now);
        crash(level, car, Math.min(1.0, force));
    }

    /** Le son et les eclats d'un choc, pour tous ceux qui sont pres ; et ce qu'il abime (VehicleDamage). */
    private static void crash(ServerLevel level, JakVehicleEntity car, double force) {
        crashes++;
        VehicleDamage.collision(car, force);
        Vec3 at = car.position().add(0.0, car.spec().boxTop * 0.5, 0.0);
        SoundEvent sound = force > 0.5 ? SoundEvents.ANVIL_LAND : SoundEvents.IRON_GOLEM_DAMAGE;
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL,
                0.5F + 0.5F * (float) force, 0.8F + 0.4F * level.random.nextFloat());
        level.sendParticles(ModParticles.JAK_VEHICLE_SPARK.get(), at.x, at.y, at.z,
                4 + (int) (force * 16.0), 0.5, 0.3, 0.5, 0.25);
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
