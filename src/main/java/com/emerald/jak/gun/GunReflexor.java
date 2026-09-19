package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
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
 * Les tirs du Beam Reflexor (gun-yellow-shot-2, gun-yellow-shot.gc:931-958 et 1271-1723).
 *
 * Un tir part a {@value GunSpec#REFLEXOR_SPEED} blocs par tique et REBONDIT : sur le
 * decor autant qu'il veut pendant ses {@value GunSpec#REFLEXOR_LIFE} tiques de vie,
 * sur {@value GunSpec#REFLEXOR_ENEMIES} monstres au plus. Apres un rebond il ne file
 * plus qu'a 6,67 blocs par tique (133 m/s), il ne repart jamais vers le ciel (y
 * plafonne a {@value GunSpec#REFLEXOR_MAX_UP}), et trois fois sur quatre IL SE REVISE
 * vers un monstre : celui qui est le plus dans son axe, a plat, parmi ceux d'une
 * sphere de {@value GunSpec#REFLEXOR_REAIM_RADIUS} blocs centree
 * {@value GunSpec#REFLEXOR_REAIM_AHEAD} blocs devant lui, et qu'il voit. La premiere
 * touche vaut {@value GunSpec#REFLEXOR_DAMAGE} points de Jak, les suivantes
 * {@value GunSpec#REFLEXOR_DAMAGE_AFTER} ; le 2e et le 3e monstre coutent un eco
 * jaune de plus chacun, et le tir s'eteint si la reserve est vide. Un monstre
 * touche est ignore {@value GunSpec#REFLEXOR_IGNORE_TICKS} tiques.
 *
 * PAS UNE ENTITE. A dix blocs par tique et avec un chemin que seul le serveur
 * connait (le hasard de la re-visee, la place des monstres), le client ne peut ni
 * suivre une vitesse -- Minecraft la plafonne a 3,9 blocs par tique dans ses
 * paquets -- ni la recalculer comme il le fait pour le Blaster. Le tir vit donc
 * ici, en memoire du serveur, et chaque tique envoie SON CHEMIN, une ligne brisee,
 * a ceux qui sont pres (GunTracePayload.REFLEXOR) : le client y seme le trait et
 * les eclats des rebonds. Le decor : le bloc touche casse, {@value GunSpec#REFLEXOR_BLOCKS}
 * au plus par tir.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class GunReflexor {

    /** Un tir en vol. */
    public static final class Shot {
        final ServerLevel level;
        final ServerPlayer shooter;
        Vec3 position;
        Vec3 direction;
        double speed = GunSpec.REFLEXOR_SPEED;
        int age;
        boolean first = true;
        boolean dead;
        final Map<Integer, Long> ignored = new HashMap<>();
        // releves
        int enemies;
        int bounces;
        int blocks;
        int extraPaid;
        int reaims;
        @Nullable
        Vec3 lastCapped;

        Shot(ServerLevel level, ServerPlayer shooter, Vec3 position, Vec3 direction) {
            this.level = level;
            this.shooter = shooter;
            this.position = position;
            this.direction = direction.normalize();
        }

        public int enemies() {
            return this.enemies;
        }

        public int bounces() {
            return this.bounces;
        }

        public int extraPaid() {
            return this.extraPaid;
        }

        public int reaims() {
            return this.reaims;
        }

        public int age() {
            return this.age;
        }

        public boolean dead() {
            return this.dead;
        }

        /** La direction du dernier rebond, plafonnee, avant toute re-visee (banc d'essai). */
        @Nullable
        public Vec3 lastCapped() {
            return this.lastCapped;
        }

        public Vec3 position() {
            return this.position;
        }

        public Vec3 direction() {
            return this.direction;
        }

        public double speed() {
            return this.speed;
        }
    }

    private static final List<Shot> SHOTS = new ArrayList<>();

    private GunReflexor() {
    }

    /** Lance un tir ; il avance des la prochaine tique du serveur. */
    public static Shot launch(ServerLevel level, ServerPlayer shooter, Vec3 origin, Vec3 direction) {
        Shot shot = new Shot(level, shooter, origin, direction);
        SHOTS.add(shot);
        return shot;
    }

    /** Les tirs en vol d'un tireur (banc d'essai, depart du joueur). */
    public static List<Shot> of(@Nullable Entity shooter) {
        List<Shot> out = new ArrayList<>();
        for (Shot shot : SHOTS) {
            if (shot.shooter == shooter) {
                out.add(shot);
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SHOTS.isEmpty()) {
            return;
        }
        for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext(); ) {
            Shot shot = it.next();
            if (!shot.dead) {
                step(shot);
            }
            if (shot.dead) {
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SHOTS.clear();
    }

    /** Eteint tous les tirs en vol (banc d'essai). */
    static void clearAll() {
        SHOTS.clear();
    }

    // ================================================================ une tique de vol

    private static void step(Shot shot) {
        if (shot.age >= GunSpec.REFLEXOR_LIFE || shot.shooter.isRemoved() || !MorphGunKeeper.allowed(shot.shooter)) {
            shot.dead = true;
            return;
        }
        ServerLevel level = shot.level;
        long now = level.getGameTime();
        shot.ignored.values().removeIf(until -> until <= now);
        Vec3 start = shot.position;
        List<float[]> points = new ArrayList<>();
        double remaining = shot.speed;
        for (int turn = 0; turn <= GunSpec.REFLEXOR_BOUNCES_PER_TICK && remaining > 1.0e-3 && !shot.dead; turn++) {
            Vec3 from = shot.position;
            Vec3 to = from.add(shot.direction.scale(remaining));
            if (!level.isLoaded(BlockPos.containing(to))) {
                // hors des troncons charges, un rayon les ferait charger : le tir s'eteint au bord du monde connu
                shot.dead = true;
                break;
            }
            BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    shot.shooter));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shot.shooter, from, stop,
                    new AABB(from, stop).inflate(1.5), e -> GunImpacts.isTarget(e) && !shot.ignored.containsKey(e.getId()), 0.3F);
            if (hit != null && hit.getEntity() instanceof Mob mob) {
                Vec3 at = mob.getBoundingBox().inflate(0.3).clip(from, stop).orElse(GunImpacts.center(mob));
                remaining -= at.distanceTo(from);
                shot.position = at;
                points.add(point(at, GunTracePayload.TARGET));
                strike(shot, mob, now);
                if (!shot.dead) {
                    deflect(shot, shot.direction, mob);
                    remaining = Math.min(remaining, shot.speed);
                }
            } else if (block.getType() == HitResult.Type.BLOCK) {
                remaining -= stop.distanceTo(from);
                Direction face = block.getDirection();
                // un rien devant la face : le prochain rayon ne repart pas de l'interieur du bloc
                shot.position = stop.add(face.getStepX() * 0.02, face.getStepY() * 0.02, face.getStepZ() * 0.02);
                points.add(point(stop, GunTracePayload.BLOCK));
                if (shot.blocks < GunSpec.REFLEXOR_BLOCKS && GunImpacts.breakOne(level, block.getBlockPos(), shot.shooter)) {
                    shot.blocks++;
                }
                shot.bounces++;
                deflect(shot, reflect(shot.direction, face), null);
                remaining = Math.min(remaining, shot.speed);
                level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.5F, 1.8F);
            } else {
                shot.position = to;
                points.add(point(to, GunTracePayload.MISS));
                remaining = 0.0;
            }
        }
        send(shot, start, points);
        shot.first = false;
        shot.age++;
    }

    /** La touche d'un monstre : degats, cout du 2e et du 3e, fin au 4e. */
    private static void strike(Shot shot, Mob mob, long now) {
        GunImpacts.hurt(shot.shooter, null, mob, shot.enemies == 0 ? GunSpec.REFLEXOR_DAMAGE : GunSpec.REFLEXOR_DAMAGE_AFTER);
        shot.enemies++;
        shot.ignored.put(mob.getId(), now + GunSpec.REFLEXOR_IGNORE_TICKS);
        Vec3 at = shot.position;
        GunFire.sparks(shot.level, at, GunFire.Sparks.REFLEXOR);
        shot.level.playSound(null, at.x, at.y, at.z, SoundEvents.SHULKER_BULLET_HIT, SoundSource.PLAYERS, 0.5F, 1.9F);
        if (shot.enemies >= GunSpec.REFLEXOR_ENEMIES) {
            shot.dead = true;
            return;
        }
        if (shot.enemies >= 2 && shot.extraPaid < GunSpec.REFLEXOR_EXTRA_COSTS) {
            ItemStack gun = MorphGunKeeper.find(shot.shooter);
            if (gun == null || !MorphGunData.spend(gun, GunForm.Family.YELLOW, 1)) {
                shot.dead = true;
                return;
            }
            shot.extraPaid++;
        }
    }

    /**
     * Apres un rebond : la vitesse retombe, le tir ne repart pas vers le ciel, et trois fois sur quatre il se revise.
     *
     * Le plafond du jeu (:1277-1279) : si le y reflechi depasse 0,2, il est ramene a
     * max(0,2 ; y d'arrivee), PUIS la direction est normalisee -- un tir qui tombe sur
     * le sol en repart donc rasant, pas a la verticale.
     *
     * @param reflected la direction reflechie, ou celle d'arrivee apres un monstre
     */
    private static void deflect(Shot shot, Vec3 reflected, @Nullable Mob justHit) {
        shot.speed = GunSpec.REFLEXOR_SPEED_AFTER;
        Vec3 d = reflected;
        if (d.y > GunSpec.REFLEXOR_MAX_UP) {
            d = new Vec3(d.x, Math.max(GunSpec.REFLEXOR_MAX_UP, shot.direction.y), d.z);
        }
        d = d.normalize();
        shot.lastCapped = d;
        if (shot.shooter.getRandom().nextDouble() < GunSpec.REFLEXOR_REAIM_CHANCE) {
            Vec3 aimed = reaim(shot, d, justHit);
            if (aimed != null) {
                d = aimed;
                shot.reaims++;
            }
        }
        shot.direction = d;
    }

    /** La direction vers le monstre le plus dans l'axe, a plat, parmi ceux que le tir voit ; null s'il n'y en a pas. */
    @Nullable
    private static Vec3 reaim(Shot shot, Vec3 direction, @Nullable Mob justHit) {
        Vec3 from = shot.position;
        Vec3 sphere = from.add(direction.scale(GunSpec.REFLEXOR_REAIM_AHEAD));
        double flat = Math.hypot(direction.x, direction.z);
        if (flat < 1.0e-6) {
            return null;
        }
        Mob best = null;
        double bestDot = 0.0;
        for (Mob mob : GunImpacts.targetsAround(shot.level, sphere, GunSpec.REFLEXOR_REAIM_RADIUS, 64)) {
            if (mob == justHit || shot.ignored.containsKey(mob.getId())) {
                continue;
            }
            Vec3 to = GunImpacts.center(mob).subtract(from);
            double toFlat = Math.hypot(to.x, to.z);
            if (toFlat < 0.5) {
                continue;
            }
            double dot = (to.x * direction.x + to.z * direction.z) / (toFlat * flat);
            if (dot <= bestDot) {
                continue;
            }
            BlockHitResult sight = shot.level.clip(new ClipContext(from, GunImpacts.center(mob), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, shot.shooter));
            if (sight.getType() == HitResult.Type.MISS) {
                best = mob;
                bestDot = dot;
            }
        }
        return best == null ? null : GunImpacts.center(best).subtract(from).normalize();
    }

    static Vec3 reflect(Vec3 direction, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(-direction.x, direction.y, direction.z);
            case Y -> new Vec3(direction.x, -direction.y, direction.z);
            case Z -> new Vec3(direction.x, direction.y, -direction.z);
        };
    }

    // ================================================================ traces

    private static float[] point(Vec3 at, int flag) {
        return new float[]{(float) at.x, (float) at.y, (float) at.z, flag};
    }

    private static void send(Shot shot, Vec3 start, List<float[]> points) {
        if (points.isEmpty()) {
            return;
        }
        float[] ends = new float[points.size() * 4];
        for (int i = 0; i < points.size(); i++) {
            System.arraycopy(points.get(i), 0, ends, i * 4, 4);
        }
        // a la premiere tique le trait part de la bouche du canon : le client la connait mieux que nous
        GunTracePayload payload = new GunTracePayload(shot.first ? shot.shooter.getId() : -1, GunTracePayload.REFLEXOR,
                start.x, start.y, start.z, ends);
        PacketDistributor.sendToPlayersNear(shot.level, null, start.x, start.y, start.z, 128.0, payload);
    }
}
