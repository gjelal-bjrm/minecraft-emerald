package com.emerald.jak.gun;

import com.emerald.init.Jak3Registry;
import com.emerald.particles.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * La boule du Peace Maker (gun-dark-shot, Jak 3).
 *
 * LA CHARGE (etat startup, gun-dark-shot.gc:1482-1543) : la boule nait au canon
 * au tir -- l'eco sombre est debitee a ce moment -- et y reste tant que la
 * gachette est tenue, au moins {@value GunSpec#PEACE_CHARGE_MIN} tiques (0,3 s) ;
 * elle grossit en {@value GunSpec#PEACE_GROW} tiques. Si l'arme quitte la main
 * pendant la charge, GunFire l'eteint et rend l'eco (+1, :1508) ; changer d'arme
 * est refuse (MorphGunKeeper.select).
 *
 * LE VOL (etat moving, :1545-1689) : {@value GunSpec#PEACE_SPEED} blocs par tique
 * (1,8 m par image a 60 images/s), dans le regard du tireur. Elle poursuit la cible
 * verrouillee au lancer -- le monstre de Haven le plus proche du regard, a moins de
 * {@value GunSpec#PEACE_LOCK_ANGLE} degres et {@value GunSpec#PEACE_LOCK_RANGE}
 * blocs, visible -- en tournant de 1 degre par image a 15 m jusqu'a 45 a 1 m.
 * La spirale autour de la trajectoire n'est que visuelle (rendu). Impact au contact
 * d'un bloc ou d'un monstre, ou apres {@value GunSpec#PEACE_LIFE} tiques.
 *
 * L'IMPACT (:1856-2121) : les monstres de Haven a moins de
 * {@value GunSpec#PEACE_BLAST} blocs, 16 au plus, tries par distance ; le plus
 * proche prend {@value GunSpec#PEACE_DAMAGE} points tout de suite, puis une foudre
 * saute vers le suivant toutes les {@value GunSpec#PEACE_CHAIN_TICKS} tiques
 * (GunFire.chain, GunArcEntity). Sans cible, rien ne saute. Le decor : les blocs a
 * moins de {@value GunSpec#PEACE_BREAK_RADIUS} du centre, {@value GunSpec#PEACE_BLOCKS}
 * au plus. Aucune explosion vanilla : ni joueur pousse, ni joueur blesse.
 */
public class GunPeaceBallEntity extends Projectile {

    private static final EntityDataAccessor<Boolean> LAUNCHED =
            SynchedEntityData.defineId(GunPeaceBallEntity.class, EntityDataSerializers.BOOLEAN);

    private long born = Long.MIN_VALUE;
    private int flight;
    @Nullable
    private Entity target;
    /** Tique (tickCount) du lancer vue par le client, pour la spirale. */
    public int launchAge = -1;

    public GunPeaceBallEntity(EntityType<? extends GunPeaceBallEntity> type, Level level) {
        super(type, level);
    }

    public GunPeaceBallEntity(ServerLevel level, ServerPlayer shooter) {
        this(Jak3Registry.GUN_PEACE_BALL.get(), level);
        this.setOwner(shooter);
        this.born = level.getGameTime();
        Vec3 at = muzzle(shooter);
        this.setPos(at.x, at.y, at.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LAUNCHED, false);
    }

    public boolean launched() {
        return this.entityData.get(LAUNCHED);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LAUNCHED.equals(key) && this.launched() && this.launchAge < 0) {
            this.launchAge = this.tickCount;
        }
    }

    /** La bouche approchee du canon du Peace Maker (1,2 bloc devant les yeux, un peu a droite et en dessous). */
    public static Vec3 muzzle(Entity shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
        right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        return eye.add(look.scale(1.2)).add(right.scale(0.25)).add(0.0, -0.25, 0.0);
    }

    /** S'eteint sans effet (charge annulee, depart du tireur). */
    public void fizzle() {
        this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) {
            clientTrail();
            return;
        }
        if (!(this.getOwner() instanceof ServerPlayer owner) || owner.isRemoved() || !MorphGunKeeper.allowed(owner)) {
            this.discard();
            return;
        }
        long now = server.getGameTime();
        if (!this.launched()) {
            if (this.born == Long.MIN_VALUE) {
                this.born = now;
            }
            Vec3 at = muzzle(owner);
            this.setPos(at.x, at.y, at.z);
            if (now - this.born >= GunSpec.PEACE_CHARGE_MIN && !GunFire.isDown(owner)) {
                launch(server, owner);
            }
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        if (this.target != null && GunImpacts.isTarget(this.target)) {
            Vec3 toward = GunImpacts.center(this.target).subtract(this.position());
            double distance = toward.length();
            double max = distance >= 15.0 ? GunSpec.PEACE_TURN_FAR : distance <= 1.0 ? GunSpec.PEACE_TURN_NEAR
                    : GunSpec.PEACE_TURN_FAR + (GunSpec.PEACE_TURN_NEAR - GunSpec.PEACE_TURN_FAR) * (15.0 - distance) / 14.0;
            velocity = turn(velocity, toward, Math.toRadians(max)).normalize().scale(GunSpec.PEACE_SPEED);
        }
        Vec3 from = this.position();
        Vec3 to = from.add(velocity);
        BlockHitResult block = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(server, this, from, stop,
                this.getBoundingBox().expandTowards(velocity).inflate(1.0), GunImpacts::isTarget, 0.4F);
        if (hit != null) {
            explode(server, owner, hit.getEntity().getBoundingBox().inflate(0.4).clip(from, stop).orElse(GunImpacts.center(hit.getEntity())));
            return;
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            explode(server, owner, stop.subtract(velocity.normalize().scale(0.3)));
            return;
        }
        this.setPos(to.x, to.y, to.z);
        this.setDeltaMovement(velocity);
        this.hasImpulse = true;
        if (++this.flight >= GunSpec.PEACE_LIFE) {
            explode(server, owner, this.position());
        }
    }

    private void launch(ServerLevel level, ServerPlayer owner) {
        this.entityData.set(LAUNCHED, true);
        Vec3 look = owner.getLookAngle();
        Vec3 start = owner.getEyePosition().add(look.scale(0.6));
        this.setPos(start.x, start.y, start.z);
        this.setDeltaMovement(look.scale(GunSpec.PEACE_SPEED));
        this.hasImpulse = true;
        this.target = lock(level, owner);
        level.playSound(null, start.x, start.y, start.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.2F);
    }

    /** Le monstre de Haven le plus proche du regard, dans le cone et la portee, visible ; null sinon. */
    @Nullable
    static Mob lock(ServerLevel level, Player shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        double cos = Math.cos(Math.toRadians(GunSpec.PEACE_LOCK_ANGLE));
        Mob best = null;
        double bestDot = cos;
        for (Mob mob : GunImpacts.targetsAround(level, eye, GunSpec.PEACE_LOCK_RANGE, 256)) {
            Vec3 to = GunImpacts.center(mob).subtract(eye);
            double length = to.length();
            if (length < 1.0e-3) {
                continue;
            }
            double dot = to.dot(look) / length;
            if (dot < bestDot) {
                continue;
            }
            BlockHitResult sight = level.clip(new ClipContext(eye, GunImpacts.center(mob), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, shooter));
            if (sight.getType() == HitResult.Type.MISS) {
                best = mob;
                bestDot = dot;
            }
        }
        return best;
    }

    /** Tourne `from` vers `toward` d'au plus `max` radians (matrix-from-two-vectors-max-angle!). */
    static Vec3 turn(Vec3 from, Vec3 toward, double max) {
        Vec3 a = from.normalize();
        Vec3 b = toward.normalize();
        double angle = Math.acos(Math.max(-1.0, Math.min(1.0, a.dot(b))));
        if (angle <= max) {
            return b;
        }
        Vec3 axis = a.cross(b);
        if (axis.lengthSqr() < 1.0e-9) {
            return a;
        }
        axis = axis.normalize();
        double c = Math.cos(max);
        double s = Math.sin(max);
        // Rodrigues : a cos + (k x a) sin + k (k . a)(1 - cos), k . a = 0
        return a.scale(c).add(axis.cross(a).scale(s));
    }

    private void explode(ServerLevel level, ServerPlayer owner, Vec3 at) {
        int broken = GunImpacts.breakSphere(level, at, GunSpec.PEACE_BREAK_RADIUS, GunSpec.PEACE_BLOCKS, owner);
        List<Mob> targets = GunImpacts.targetsAround(level, at, GunSpec.PEACE_BLAST, GunSpec.PEACE_TARGETS);
        if (!targets.isEmpty()) {
            Mob first = targets.get(0);
            Vec3 to = GunImpacts.center(first);
            GunArcEntity.spawn(level, at, to);
            GunImpacts.hurt(owner, null, first, GunSpec.PEACE_DAMAGE);
            GunFire.chain(level, owner, to, targets.subList(1, targets.size()));
        }
        level.sendParticles(ModParticles.GUN_PEACE_BLAST.get(), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ModParticles.GUN_PEACE_MOTE.get(), at.x, at.y, at.z, 36, 0.6, 0.6, 0.6, 0.3);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
        GunEco.logExplosion(owner, at, broken, targets.size());
        this.discard();
    }

    /** La trainee cote client : des motes a la place de la boule sur la spirale. */
    private void clientTrail() {
        if (!this.launched()) {
            if (this.tickCount % 2 == 0) {
                Vec3 p = this.position();
                this.level().addParticle(ModParticles.GUN_PEACE_MOTE.get(), p.x + (this.random.nextDouble() - 0.5) * 0.8,
                        p.y + (this.random.nextDouble() - 0.5) * 0.8, p.z + (this.random.nextDouble() - 0.5) * 0.8,
                        0.0, 0.0, 0.0);
            }
            return;
        }
        Vec3 offset = spiral(this, 1.0F);
        for (int i = 0; i < 5; i++) {
            double t = i / 5.0;
            this.level().addParticle(ModParticles.GUN_PEACE_MOTE.get(),
                    this.xo + (this.getX() - this.xo) * t + offset.x, this.yo + (this.getY() - this.yo) * t + offset.y,
                    this.zo + (this.getZ() - this.zo) * t + offset.z, 0.0, 0.0, 0.0);
        }
    }

    /**
     * L'ecart de la boule a sa trajectoire : la spirale de gun-dark-shot.gc:1612-1650,
     * rayon de 0 a 0,5 bloc en 6 tiques puis vers 0,15, 33 degres par tique (2002 unites par image).
     */
    public static Vec3 spiral(GunPeaceBallEntity ball, float partial) {
        if (ball.launchAge < 0) {
            return Vec3.ZERO;
        }
        double t = ball.tickCount - ball.launchAge + partial;
        double radius = t < 6.0 ? 0.5 * t / 6.0 : 0.5 + (0.15 - 0.5) * Math.min(1.0, (t - 6.0) / 24.0);
        Vec3 f = ball.getDeltaMovement().lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : ball.getDeltaMovement().normalize();
        Vec3 u = f.cross(new Vec3(0.0, 1.0, 0.0));
        u = u.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : u.normalize();
        Vec3 v = u.cross(f).normalize();
        double a = Math.toRadians(33.0 * t);
        return u.scale(Math.cos(a) * radius).add(v.scale(Math.sin(a) * radius));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128.0 * 128.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    /** Le proprietaire vivant cote client ou serveur, pour le rendu de la charge. */
    @Nullable
    public LivingEntity shooter() {
        return this.getOwner() instanceof LivingEntity living ? living : null;
    }
}
