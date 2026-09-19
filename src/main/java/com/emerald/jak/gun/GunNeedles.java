package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Les aiguilles du Needle Lazer (gun-blue-shot-3, gun-blue-shot.gc:127-623 et 2166-2193).
 *
 * TROIS AIGUILLES A TETE CHERCHEUSE PAR SALVE. Chacune tire sa cible au sort parmi
 * les monstres de Haven vus a {@value GunSpec#NEEDLE_RANGE} blocs de la bouche, jamais
 * a plus de 30 degres au-dessus (poids 1, +2 devant le tireur, +4 a moins de
 * {@value GunSpec#NEEDLE_NEAR} blocs) ; sans monstre, un point devant le tireur. Elle
 * PART EXPRES DE TRAVERS -- {@value GunSpec#NEEDLE_SIDE_DEGREES} degres du cote oppose
 * a sa cible, plus ou moins 15 au hasard, un peu vers le haut --, vole libre de 3 a 10
 * blocs, puis VIRE vers sa cible : {@value GunSpec#NEEDLE_TURN} degres par tique,
 * {@value GunSpec#NEEDLE_TURN_NEAR} a moins de 12 blocs, le double apres une seconde
 * de poursuite. Sa vitesse suit son alignement : {@value GunSpec#NEEDLE_SPEED_MIN} bloc
 * par tique de travers, {@value GunSpec#NEEDLE_SPEED_MAX} dans l'axe. Une aiguille vaut
 * {@value GunSpec#NEEDLE_DAMAGE} point de Jak, MEURT AU PREMIER MUR (aucun rebond :
 * c'est le Beam Reflexor qui rebondit), et vit {@value GunSpec#NEEDLE_LIFE} tiques.
 *
 * PAS DES ENTITES, pour la meme raison que le Beam Reflexor et une de plus : a
 * pleine cadence l'arme en tire trente par seconde. Elles vivent en memoire du
 * serveur, et chaque tique envoie PAR TIREUR un seul paquet, le petit segment que
 * chacune vient de parcourir (GunTracePayload.NEEDLES, des paires debut-fin).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class GunNeedles {

    /** Une aiguille en vol. */
    public static final class Needle {
        final ServerLevel level;
        final ServerPlayer shooter;
        Vec3 position;
        Vec3 direction;
        double speed = GunSpec.NEEDLE_LAUNCH_SPEED;
        @Nullable
        Mob target;
        Vec3 destination;
        final double freeFlight;
        double travelled;
        int age;
        int pursuit;
        boolean homing;
        boolean dead;
        boolean hit;

        Needle(ServerLevel level, ServerPlayer shooter, Vec3 position, Vec3 direction, @Nullable Mob target,
               Vec3 destination, double freeFlight) {
            this.level = level;
            this.shooter = shooter;
            this.position = position;
            this.direction = direction.normalize();
            this.target = target;
            this.destination = destination;
            this.freeFlight = freeFlight;
        }

        @Nullable
        public Mob target() {
            return this.target;
        }

        public boolean homing() {
            return this.homing;
        }

        public boolean hit() {
            return this.hit;
        }

        public boolean dead() {
            return this.dead;
        }

        public Vec3 direction() {
            return this.direction;
        }

        public double speed() {
            return this.speed;
        }
    }

    private static final List<Needle> NEEDLES = new ArrayList<>();
    /** Sous-pas de guidage par tique (voir {@link #step}). */
    private static final int SUBSTEPS = 4;
    /**
     * Une aiguille qui passe a moins d'un bloc du centre de SA cible la touche. Dans le jeu les ennemis ont de
     * grosses spheres de collision, et un passage a un metre y est une touche ; contre la boite etroite d'un zombie
     * (0,6 bloc), l'aiguille frolait sa cible et se mettait EN ORBITE autour d'elle, a 2,2 blocs, sans jamais la
     * toucher (banc du 19 sept. : un monstre survivait a trente-deux aiguilles).
     */
    private static final double PROXIMITY = 1.0;
    /** Tout pres de sa cible et de travers, l'aiguille pique sur elle au lieu de lui tourner autour. */
    private static final double ORBIT_BREAK = 3.0;

    private GunNeedles() {
    }

    /** Les aiguilles en vol d'un tireur. */
    public static List<Needle> of(@Nullable Entity shooter) {
        List<Needle> out = new ArrayList<>();
        for (Needle needle : NEEDLES) {
            if (needle.shooter == shooter && !needle.dead) {
                out.add(needle);
            }
        }
        return out;
    }

    /** Une salve : {@value GunSpec#NEEDLE_PER_SALVO} aiguilles. Rend celles qui sont parties. */
    public static List<Needle> salvo(ServerLevel level, ServerPlayer shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        Vec3 muzzle = eye.add(look.scale(0.4)).add(0.0, -0.1, 0.0);
        RandomSource random = shooter.getRandom();
        List<Needle> mine = of(shooter);
        for (int i = 0; i + GunSpec.NEEDLE_MAX - GunSpec.NEEDLE_PER_SALVO < mine.size(); i++) {
            mine.get(i).dead = true;
        }
        List<Mob> seen = candidates(level, shooter, muzzle);
        List<Needle> out = new ArrayList<>();
        for (int i = 0; i < GunSpec.NEEDLE_PER_SALVO; i++) {
            Mob target = pick(shooter, muzzle, seen, random);
            Vec3 destination = target != null ? GunImpacts.center(target)
                    : muzzle.add(look.scale(GunSpec.NEEDLE_RANGE)).add((random.nextDouble() - 0.5) * 16.0,
                    (random.nextDouble() - 0.5) * 8.0, (random.nextDouble() - 0.5) * 16.0);
            Vec3 direction = askew(look, destination.subtract(muzzle), random);
            double free = GunSpec.NEEDLE_FREE_MIN + random.nextDouble() * (GunSpec.NEEDLE_FREE_MAX - GunSpec.NEEDLE_FREE_MIN);
            Needle needle = new Needle(level, shooter, muzzle, direction, target, destination, free);
            NEEDLES.add(needle);
            out.add(needle);
        }
        return out;
    }

    /** Les monstres vus a portee de la bouche, jamais a plus de 30 degres au-dessus du tireur. */
    private static List<Mob> candidates(ServerLevel level, ServerPlayer shooter, Vec3 muzzle) {
        List<Mob> seen = new ArrayList<>();
        for (Mob mob : GunImpacts.targetsAround(level, muzzle, GunSpec.NEEDLE_RANGE, 64)) {
            Vec3 to = GunImpacts.center(mob).subtract(muzzle);
            double reach = to.length();
            if (reach < 1.0e-3 || to.y / reach >= GunSpec.NEEDLE_MAX_UP) {
                continue;
            }
            if (level.clip(new ClipContext(muzzle, GunImpacts.center(mob), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    shooter)).getType() == HitResult.Type.MISS) {
                seen.add(mob);
            }
        }
        return seen;
    }

    /** Le tirage pondere de la cible : 1, +2 devant le tireur, +4 a moins de 12 blocs a plat. */
    @Nullable
    private static Mob pick(ServerPlayer shooter, Vec3 muzzle, List<Mob> seen, RandomSource random) {
        if (seen.isEmpty()) {
            return null;
        }
        Vec3 look = shooter.getLookAngle();
        double[] weights = new double[seen.size()];
        double total = 0.0;
        for (int i = 0; i < seen.size(); i++) {
            Vec3 to = GunImpacts.center(seen.get(i)).subtract(muzzle);
            double reach = to.length();
            double w = 1.0;
            if (reach > 1.0e-3 && to.dot(look) / reach > 0.5) {
                w += 2.0;
            }
            if (Math.hypot(to.x, to.z) < GunSpec.NEEDLE_NEAR) {
                w += 4.0;
            }
            weights[i] = w;
            total += w;
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < seen.size(); i++) {
            roll -= weights[i];
            if (roll < 0.0) {
                return seen.get(i);
            }
        }
        return seen.get(seen.size() - 1);
    }

    /** La direction de depart : le regard tourne de 15 degres du cote OPPOSE a la cible, +-15 au hasard, y de -0,1 a +0,4. */
    static Vec3 askew(Vec3 look, Vec3 toTarget, RandomSource random) {
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        side = side.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        double away = toTarget.dot(side) >= 0.0 ? -1.0 : 1.0;
        double yaw = Math.toRadians(away * GunSpec.NEEDLE_SIDE_DEGREES + (random.nextDouble() * 2.0 - 1.0) * GunSpec.NEEDLE_SIDE_DEGREES);
        Vec3 flat = look.scale(Math.cos(yaw)).add(side.scale(Math.sin(yaw)));
        return new Vec3(flat.x, flat.y - 0.1 + random.nextDouble() * 0.5, flat.z).normalize();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (NEEDLES.isEmpty()) {
            return;
        }
        Map<ServerPlayer, List<float[]>> traces = new HashMap<>();
        for (Iterator<Needle> it = NEEDLES.iterator(); it.hasNext(); ) {
            Needle needle = it.next();
            if (!needle.dead) {
                step(needle, traces.computeIfAbsent(needle.shooter, k -> new ArrayList<>()));
            }
            if (needle.dead) {
                it.remove();
            }
        }
        for (Map.Entry<ServerPlayer, List<float[]>> entry : traces.entrySet()) {
            send(entry.getKey(), entry.getValue());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        NEEDLES.clear();
    }

    /** Eteint toutes les aiguilles en vol (banc d'essai : celles d'un essai piquaient les monstres du suivant). */
    static void clearAll() {
        NEEDLES.clear();
    }

    /**
     * Une tique de vol, en {@value #SUBSTEPS} SOUS-PAS. Le jeu guide ses aiguilles deux fois par image, 120 fois
     * par seconde ; a un pas par tique, une aiguille a 5 blocs par tique depassait sa cible, lui tournait autour et
     * la manquait (banc du 19 sept. : une touche sur trois). Chaque sous-pas vire, ajuste la vitesse a
     * l'alignement, avance, et cherche mur et monstre sur son petit trajet.
     */
    private static void step(Needle needle, List<float[]> trace) {
        if (needle.age++ >= GunSpec.NEEDLE_LIFE || needle.shooter.isRemoved() || !MorphGunKeeper.allowed(needle.shooter)) {
            needle.dead = true;
            return;
        }
        ServerLevel level = needle.level;
        if (needle.target != null && !GunImpacts.isTarget(needle.target)) {
            needle.target = null;
        }
        Vec3 start = needle.position;
        int flag = GunTracePayload.MISS;
        if (needle.homing) {
            needle.pursuit++;
        }
        // apres 1 s de poursuite le virage grandit, jusqu'au double a 1,5 s (gun-blue-shot.gc:247-251)
        double boost = 1.0 + Math.max(0.0, Math.min(1.0, (needle.pursuit - GunSpec.NEEDLE_TURN_BOOST_AFTER) / 10.0));
        for (int k = 0; k < SUBSTEPS && !needle.dead; k++) {
            Vec3 goal = needle.target != null ? GunImpacts.center(needle.target) : needle.destination;
            Vec3 to = goal.subtract(needle.position);
            double distance = to.length();
            double dot = distance < 1.0e-3 ? 1.0 : to.dot(needle.direction) / distance;
            if (!needle.homing && (needle.travelled >= needle.freeFlight || distance <= GunSpec.NEEDLE_FREE_NEAR)) {
                needle.homing = true;
            }
            if (needle.homing && distance > 1.0e-3) {
                // La loi du jeu (:241-251) : 360 degres/s ; a moins de 12 m, de 360 a 720 selon la distance, et
                // jusqu'au double quand l'aiguille est de travers (2 - alignement) ; puis le renfort d'apres 1 s.
                double turn = GunSpec.NEEDLE_TURN;
                if (distance < GunSpec.NEEDLE_NEAR) {
                    turn = (GunSpec.NEEDLE_TURN + (GunSpec.NEEDLE_TURN_NEAR - GunSpec.NEEDLE_TURN) * distance / GunSpec.NEEDLE_NEAR)
                            * (2.0 - (1.0 + dot) / 2.0) * boost;
                }
                boolean snap = dot > 0.99 || (needle.target != null && distance < ORBIT_BREAK && dot < 0.5);
                needle.direction = snap ? to.scale(1.0 / distance)
                        : GunPeaceBallEntity.turn(needle.direction, to, Math.toRadians(turn / SUBSTEPS)).normalize();
                dot = to.dot(needle.direction) / distance;
            }
            double aligned = (1.0 + dot) / 2.0;
            needle.speed = GunSpec.NEEDLE_SPEED_MIN + (GunSpec.NEEDLE_SPEED_MAX - GunSpec.NEEDLE_SPEED_MIN) * aligned * aligned;
            Vec3 from = needle.position;
            Vec3 next = from.add(needle.direction.scale(needle.speed / SUBSTEPS));
            if (!level.isLoaded(BlockPos.containing(next))) {
                needle.dead = true;
                break;
            }
            BlockHitResult block = level.clip(new ClipContext(from, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    needle.shooter));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? next : block.getLocation();
            EntityHitResult struck = ProjectileUtil.getEntityHitResult(level, needle.shooter, from, stop,
                    new AABB(from, stop).inflate(1.0), GunImpacts::isTarget, 0.3F);
            Entity victim = struck == null ? null : struck.getEntity();
            if (victim == null && needle.target != null && closest(from, stop, goal) <= PROXIMITY) {
                victim = needle.target;
            }
            if (victim != null) {
                stop = victim.getBoundingBox().inflate(0.3).clip(from, stop).orElse(stop);
                GunImpacts.hurt(needle.shooter, null, victim, GunSpec.NEEDLE_DAMAGE);
                needle.hit = true;
                needle.dead = true;
                flag = GunTracePayload.TARGET;
            } else if (block.getType() == HitResult.Type.BLOCK) {
                GunImpacts.breakOne(level, block.getBlockPos(), needle.shooter);
                needle.dead = true;
                flag = GunTracePayload.BLOCK;
            } else if (needle.target == null && distance <= needle.speed / SUBSTEPS) {
                // sans cible, l'aiguille s'eteint a sa destination
                needle.dead = true;
            }
            needle.travelled += stop.distanceTo(from);
            needle.position = stop;
        }
        Vec3 stop = needle.position;
        trace.add(new float[]{(float) start.x, (float) start.y, (float) start.z, GunTracePayload.MISS});
        trace.add(new float[]{(float) stop.x, (float) stop.y, (float) stop.z, flag});
        if (flag == GunTracePayload.TARGET) {
            level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.AMETHYST_CLUSTER_HIT, SoundSource.PLAYERS, 0.4F, 2.0F);
        }
    }

    /** La plus courte distance d'un point au segment. */
    private static double closest(Vec3 from, Vec3 to, Vec3 point) {
        Vec3 span = to.subtract(from);
        double length2 = span.lengthSqr();
        double f = length2 < 1.0e-9 ? 0.0 : Math.max(0.0, Math.min(1.0, point.subtract(from).dot(span) / length2));
        return from.add(span.scale(f)).distanceTo(point);
    }

    private static void send(ServerPlayer shooter, List<float[]> points) {
        if (points.isEmpty()) {
            return;
        }
        float[] ends = new float[points.size() * 4];
        for (int i = 0; i < points.size(); i++) {
            System.arraycopy(points.get(i), 0, ends, i * 4, 4);
        }
        Vec3 at = shooter.position();
        PacketDistributor.sendToPlayersNear((ServerLevel) shooter.level(), null, at.x, at.y, at.z, 160.0,
                new GunTracePayload(-1, GunTracePayload.NEEDLES, at.x, at.y, at.z, ends));
    }
}
