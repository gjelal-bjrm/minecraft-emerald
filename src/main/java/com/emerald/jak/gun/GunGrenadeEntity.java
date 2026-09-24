package com.emerald.jak.gun;

import com.emerald.init.Jak3Registry;
import com.emerald.jak.vehicle.JakVehicleEntity;
import com.emerald.jak.vehicle.VehicleImpacts;
import com.emerald.particles.ModParticles;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * La grenade du Plasmite RPG (gun-red-3-grenade, gun-red-shot.gc:44-415 et 1527-1822).
 *
 * LE LANCER : {@value GunSpec#PLASMITE_SPEED} blocs par tique (65 m/s), TOUJOURS
 * VERS LE HAUT -- le y du regard est releve a {@value GunSpec#PLASMITE_MIN_UP} au
 * moins, puis la direction est normalisee (:1535-1536). S'il y a un monstre dans
 * une sphere de {@value GunSpec#PLASMITE_AIM_RADIUS} blocs centree a la meme
 * distance devant le tireur, a moins de 45 degres du regard et a moins de
 * {@value GunSpec#PLASMITE_AIM_DROP} blocs de denivele, le jeu vise pour lui
 * (:1551-1634) : cap sur la cible, et angle de tir sin(asin(g d / v2) / 2), la
 * solution balistique tendue pour la gravite de la grenade.
 *
 * LE VOL : gravite de {@value GunSpec#PLASMITE_GRAVITY} bloc par tique2 (45 m/s2),
 * {@value GunSpec#PLASMITE_MAX_SPEED} blocs par tique au plus ; un bloc la fait
 * REBONDIR en gardant 60 % de sa vitesse, et un petit rebond sur le sol la pose.
 *
 * LA MECHE, telle que le joueur la decrit (21 sept.) : « la grenade explose des
 * qu'elle touche quelqu'un. Et si ça ne touche personne, il y a une espece de
 * compte a rebours sonore et la grenade finit par exploser. »
 * - AU CONTACT : une creature -- monstre, habitant, animal -- ou un vehicule,
 *   sur son chemin de la tique OU contre elle quand elle est posee (un monstre
 *   qui marche dessus la fait sauter). Jamais un joueur.
 * - LE COMPTE A REBOURS : des le premier contact avec le decor (au plus tard a
 *   {@value GunSpec#PLASMITE_LIFE} - {@value GunSpec#PLASMITE_COUNTDOWN} tiques de
 *   vol), {@value GunSpec#PLASMITE_COUNTDOWN} tiques de bips de plus en plus serres
 *   et aigus, puis l'explosion.
 *
 * LE SOUFFLE : {@value GunSpec#PLASMITE_DAMAGE} points de Jak a tous les monstres de
 * Haven a moins de {@value GunSpec#PLASMITE_BLAST} blocs -- le Plasmite RPG vide une
 * place, il coute 10 eco rouges. Le decor : les blocs a moins de
 * {@value GunSpec#PLASMITE_BREAK_RADIUS} du centre, {@value GunSpec#PLASMITE_BLOCKS}
 * au plus. Les vehicules proches sont souffles et desequilibres
 * (VehicleImpacts.blast). Aucune explosion vanilla : ni joueur pousse, ni joueur blesse.
 *
 * Sa vitesse reste sous le plafond de 3,9 blocs par tique des paquets de
 * Minecraft : le suivi vanilla suffit (une position par tique), sans le calcul du
 * client qu'il a fallu au Blaster. Le modele : gun-grenade de Jak 3
 * (GunRenderers.Grenade).
 */
public class GunGrenadeEntity extends Projectile {

    /** La tique ou le compte a rebours a commence, -1 avant. */
    private int countdown = -1;
    /** Le bip suivant, en tiques depuis le debut du compte a rebours. */
    private int nextBeep;
    private int beeps;
    private int bounces;
    private int lastBounceSound = -100;

    public GunGrenadeEntity(EntityType<? extends GunGrenadeEntity> type, Level level) {
        super(type, level);
    }

    public GunGrenadeEntity(Level level, LivingEntity shooter) {
        this(Jak3Registry.GUN_GRENADE.get(), level);
        this.setOwner(shooter);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** Rebonds faits (banc d'essai). */
    public int bounces() {
        return this.bounces;
    }

    /** Bips du compte a rebours joues (banc d'essai). */
    public int beeps() {
        return this.beeps;
    }

    /** La tique du debut du compte a rebours, -1 s'il n'a pas commence (banc d'essai). */
    public int countdownStart() {
        return this.countdown;
    }

    /**
     * La direction du lancer : le regard releve, ou la visee balistique du jeu s'il y a une cible.
     *
     * @return la direction normalisee
     */
    public static Vec3 aim(ServerLevel level, ServerPlayer shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        Vec3 raised = new Vec3(look.x, Math.max(look.y, GunSpec.PLASMITE_MIN_UP), look.z).normalize();
        Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
        if (flatLook.lengthSqr() < 1.0e-6) {
            return raised;
        }
        flatLook = flatLook.normalize();
        Vec3 sphere = eye.add(flatLook.scale(GunSpec.PLASMITE_AIM_RADIUS));
        Mob best = null;
        double bestDot = GunSpec.PLASMITE_AIM_COS;
        for (Mob mob : GunImpacts.targetsAround(level, sphere, GunSpec.PLASMITE_AIM_RADIUS, 64)) {
            Vec3 to = GunImpacts.center(mob).subtract(eye);
            double flat = Math.hypot(to.x, to.z);
            if (flat < 1.0 || Math.abs(to.y) >= GunSpec.PLASMITE_AIM_DROP) {
                continue;
            }
            double dot = (to.x * flatLook.x + to.z * flatLook.z) / flat;
            if (dot > bestDot) {
                bestDot = dot;
                best = mob;
            }
        }
        if (best == null) {
            return raised;
        }
        Vec3 to = GunImpacts.center(best).subtract(eye);
        double flat = Math.hypot(to.x, to.z);
        double ratio = GunSpec.PLASMITE_GRAVITY * flat / (GunSpec.PLASMITE_SPEED * GunSpec.PLASMITE_SPEED);
        double up = Math.sin(0.5 * Math.asin(Math.min(1.0, ratio)));
        double side = Math.sqrt(Math.max(0.0, 1.0 - up * up));
        return new Vec3(to.x / flat * side, up, to.z / flat * side);
    }

    /** Pose la grenade au depart, cote serveur, AVANT addFreshEntity. */
    public void launch(Vec3 origin, Vec3 direction) {
        this.setPos(origin.x, origin.y, origin.z);
        this.setDeltaMovement(direction.normalize().scale(GunSpec.PLASMITE_SPEED));
        this.hasImpulse = true;
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
        Vec3 velocity = this.getDeltaMovement().add(0.0, -GunSpec.PLASMITE_GRAVITY, 0.0);
        double speed = velocity.length();
        if (speed > GunSpec.PLASMITE_MAX_SPEED) {
            velocity = velocity.scale(GunSpec.PLASMITE_MAX_SPEED / speed);
        }
        Vec3 from = this.position();
        Vec3 to = from.add(velocity);
        BlockHitResult block = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        Vec3 touched = touched(server, from, stop);
        if (touched != null) {
            explode(server, owner, touched);
            return;
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            bounce(server, velocity, block, stop);
        } else {
            this.setPos(to.x, to.y, to.z);
            this.setDeltaMovement(velocity);
        }
        if (this.countdown < 0 && this.tickCount >= GunSpec.PLASMITE_LIFE - GunSpec.PLASMITE_COUNTDOWN) {
            this.countdown = this.tickCount;       // toujours en vol : le compte a rebours part quand meme
        }
        if (this.countdown >= 0) {
            int elapsed = this.tickCount - this.countdown;
            if (elapsed >= GunSpec.PLASMITE_COUNTDOWN) {
                explode(server, owner, this.position());
                return;
            }
            if (elapsed >= this.nextBeep) {
                beep(server, elapsed);
                // de plus en plus serre : le quart de ce qui reste, deux tiques au moins
                this.nextBeep = elapsed + Math.max(2, (GunSpec.PLASMITE_COUNTDOWN - elapsed) / 4);
            }
        }
    }

    /**
     * Le rebond sur un bloc. Il garde 60 % de la vitesse, reflechie sur la face ;
     * sur le sol, un rebond trop faible pose la grenade, qui glisse et s'arrete.
     * Le premier contact avec le decor lance le compte a rebours.
     */
    private void bounce(ServerLevel level, Vec3 velocity, BlockHitResult block, Vec3 stop) {
        Direction face = block.getDirection();
        Vec3 back = velocity.normalize().scale(0.05);
        this.setPos(stop.x - back.x, stop.y - back.y, stop.z - back.z);
        Vec3 reflected = reflect(velocity, face).scale(GunSpec.PLASMITE_BOUNCE);
        double normal = Math.abs(face.getAxis().choose(velocity.x, velocity.y, velocity.z));
        if (face == Direction.UP && reflected.y < GunSpec.PLASMITE_REST) {
            reflected = new Vec3(reflected.x, 0.0, reflected.z);       // posee : elle ne sautille plus
        } else {
            this.bounces++;
        }
        this.setDeltaMovement(reflected);
        this.hasImpulse = true;
        if (normal > GunSpec.PLASMITE_BOUNCE_SOUND && this.tickCount - this.lastBounceSound >= GunSpec.PLASMITE_BOUNCE_GAP) {
            this.lastBounceSound = this.tickCount;
            level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.7F);
        }
        if (this.countdown < 0) {
            this.countdown = this.tickCount;
        }
    }

    /** Un bip du compte a rebours, de plus en plus aigu, et un eclat qui montre ou est la grenade. */
    private void beep(ServerLevel level, int elapsed) {
        this.beeps++;
        float pitch = 1.0F + (float) elapsed / GunSpec.PLASMITE_COUNTDOWN;
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(),
                SoundSource.PLAYERS, 0.9F, pitch);
        level.sendParticles(ModParticles.GUN_PLASMITE_TRAIL.get(), this.getX(), this.getY() + 0.2, this.getZ(),
                3, 0.05, 0.05, 0.05, 0.02);
    }

    /** La vitesse reflechie sur la face touchee. */
    static Vec3 reflect(Vec3 velocity, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Vec3(-velocity.x, velocity.y, velocity.z);
            case Y -> new Vec3(velocity.x, -velocity.y, velocity.z);
            case Z -> new Vec3(velocity.x, velocity.y, -velocity.z);
        };
    }

    /**
     * Le point ou la grenade touche quelqu'un sur son chemin de la tique, ou null.
     *
     * Une boite qui CONTIENT deja la grenade compte aussi : posee, elle ne bouge plus,
     * et c'est le monstre qui vient a elle. AABB.clip ne voit pas un depart dedans.
     */
    @Nullable
    private Vec3 touched(ServerLevel level, Vec3 from, Vec3 to) {
        AABB path = new AABB(from, to).inflate(GunSpec.PLASMITE_TOUCH + 0.5);
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        // jamais le vehicule du lanceur : il tire de son siege (cahier §98)
        Entity thrower = this.getOwner();
        for (Entity entity : level.getEntities(this, path.inflate(VehicleImpacts.SEARCH),
                e -> someone(e) && (thrower == null || thrower.getRootVehicle() != e))) {
            List<AABB> boxes = entity instanceof JakVehicleEntity car ? car.collisionBoxes() : List.of(entity.getBoundingBox());
            for (AABB box : boxes) {
                AABB grown = box.inflate(GunSpec.PLASMITE_TOUCH);
                Vec3 at = grown.contains(from) ? from : grown.clip(from, to).orElse(null);
                if (at != null && from.distanceToSqr(at) < bestDistance) {
                    bestDistance = from.distanceToSqr(at);
                    best = at;
                }
            }
        }
        return best;
    }

    /** Ce qui fait exploser la grenade au contact : une creature vivante ou un vehicule, jamais un joueur. */
    private static boolean someone(Entity entity) {
        if (entity instanceof JakVehicleEntity) {
            return !entity.isRemoved();
        }
        return entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved() && !(mob.getVehicle() instanceof JakVehicleEntity);
    }

    private void explode(ServerLevel level, ServerPlayer owner, Vec3 at) {
        int broken = GunImpacts.breakSphere(level, at, GunSpec.PLASMITE_BREAK_RADIUS, GunSpec.PLASMITE_BLOCKS, owner);
        List<Mob> targets = GunImpacts.targetsAround(level, at, GunSpec.PLASMITE_BLAST, GunSpec.PLASMITE_TARGETS);
        for (Mob mob : targets) {
            GunImpacts.hurt(owner, this, mob, GunSpec.PLASMITE_DAMAGE);
        }
        VehicleImpacts.blast(level, at, VehicleImpacts.BLAST_PLASMITE_RADIUS, VehicleImpacts.BLAST_PLASMITE);
        com.emerald.jak.vehicle.VehicleDamage.explosion(level, at, VehicleImpacts.BLAST_PLASMITE_RADIUS, com.emerald.jak.vehicle.VehicleDamage.PLASMITE, owner);
        level.sendParticles(ModParticles.GUN_PLASMITE_BLAST.get(), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ModParticles.GUN_PLASMITE_TRAIL.get(), at.x, at.y, at.z, 60, 1.2, 1.2, 1.2, 0.45);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6F, 0.7F);
        GunEco.logExplosion(owner, at, broken, targets.size());
        this.discard();
    }

    /** La trainee cote client : des braises sur le chemin de la tique. */
    private void clientTrail() {
        for (int i = 0; i < 4; i++) {
            double t = i / 4.0;
            this.level().addParticle(ModParticles.GUN_PLASMITE_TRAIL.get(), true,
                    this.xo + (this.getX() - this.xo) * t, this.yo + (this.getY() - this.yo) * t + 0.16,
                    this.zo + (this.getZ() - this.zo) * t, 0.0, 0.0, 0.0);
        }
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

    @Nullable
    public LivingEntity shooter() {
        return this.getOwner() instanceof LivingEntity living ? living : null;
    }
}
