package com.emerald.jak.gun;

import com.emerald.init.Jak3Registry;
import com.emerald.particles.ModParticles;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
 * REBONDIR en gardant 60 % de sa vitesse ; elle vit {@value GunSpec#PLASMITE_LIFE}
 * tiques, puis explose. LA MECHE DE PROXIMITE (:293-400) : des qu'un monstre est
 * devant elle a moins de {@value GunSpec#PLASMITE_FUSE_RADIUS} blocs ET QU'ELLE VA
 * PASSER A MOINS DE {@value GunSpec#PLASMITE_FUSE_MISS} BLOCS DE LUI, elle explose au
 * passage au plus pres, dans {@value GunSpec#PLASMITE_FUSE_MAX} tiques au plus ; tout
 * de suite a moins de {@value GunSpec#PLASMITE_FUSE_NOW} blocs ou au contact. Sinon
 * elle poursuit sa route et tombe sur le sol.
 *
 * LE SOUFFLE : {@value GunSpec#PLASMITE_DAMAGE} points de Jak a tous les monstres de
 * Haven a moins de {@value GunSpec#PLASMITE_BLAST} blocs -- le Plasmite RPG vide une
 * place, il coute 10 eco rouges. Le decor : les blocs a moins de
 * {@value GunSpec#PLASMITE_BREAK_RADIUS} du centre, {@value GunSpec#PLASMITE_BLOCKS}
 * au plus. Aucune explosion vanilla : ni joueur pousse, ni joueur blesse.
 *
 * Sa vitesse reste sous le plafond de 3,9 blocs par tique des paquets de
 * Minecraft : le suivi vanilla suffit (une position par tique), sans le calcul du
 * client qu'il a fallu au Blaster. Le modele : gun-grenade de Jak 3
 * (GunRenderers.Grenade).
 */
public class GunGrenadeEntity extends Projectile {

    private int fuse = -1;
    private int bounces;

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
        if (this.tickCount > GunSpec.PLASMITE_LIFE || (this.fuse >= 0 && --this.fuse <= 0)) {
            explode(server, owner, this.position());
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
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(server, this, from, stop,
                this.getBoundingBox().expandTowards(velocity).inflate(1.0), GunImpacts::isTarget, 0.4F);
        if (hit != null) {
            explode(server, owner, hit.getEntity().getBoundingBox().inflate(0.4).clip(from, stop).orElse(GunImpacts.center(hit.getEntity())));
            return;
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            Vec3 back = velocity.normalize().scale(0.05);
            this.setPos(stop.x - back.x, stop.y - back.y, stop.z - back.z);
            this.setDeltaMovement(reflect(velocity, block.getDirection()).scale(GunSpec.PLASMITE_BOUNCE));
            this.hasImpulse = true;
            this.bounces++;
            server.playSound(null, stop.x, stop.y, stop.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25F, 1.7F);
            // une grenade posee qui ne bouge plus n'attend pas sa vie entiere
            if (this.getDeltaMovement().lengthSqr() < 0.02 * 0.02 && this.fuse < 0) {
                this.fuse = GunSpec.PLASMITE_FUSE_MAX;
            }
        } else {
            this.setPos(to.x, to.y, to.z);
            this.setDeltaMovement(velocity);
        }
        if (this.fuse < 0 && this.tickCount > 1) {
            this.fuse = proximity(server);
            if (this.fuse == 0) {
                explode(server, owner, this.position());
            }
        }
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
     * La meche : -1 sans monstre a portee, 0 pour exploser tout de suite, sinon les
     * tiques jusqu'au passage au plus pres du monstre, bornees.
     *
     * LE JEU N'ARME QUE SUR UN MONSTRE DEVANT (:333-339) et fait exploser au passage au
     * plus pres : delai = (distance / vitesse) x cosinus, une demi-seconde au plus. Chez
     * lui les ennemis ont de grosses spheres de collision et la grenade vole vers l'un
     * d'eux ; chez nous, N'IMPORTE QUEL monstre devant, meme a dix blocs de la
     * trajectoire, armait la meche -- et la grenade lobee explosait en l'air, loin de
     * tout (« ça explose souvent dans le ciel », 19 sept.). On ajoute donc la seule
     * question qui manquait : VA-T-ELLE PASSER PRES DE LUI ? L'ecart au plus pres doit
     * rester sous {@value GunSpec#PLASMITE_FUSE_MISS} blocs, sinon elle continue sa
     * route et va tomber sur le sol.
     */
    private int proximity(ServerLevel level) {
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0e-3) {
            return -1;
        }
        Vec3 heading = velocity.scale(1.0 / speed);
        int best = -1;
        for (Mob mob : GunImpacts.targetsAround(level, this.position(), GunSpec.PLASMITE_FUSE_RADIUS, 16)) {
            Vec3 to = GunImpacts.center(mob).subtract(this.position());
            double distance = to.length();
            if (distance < GunSpec.PLASMITE_FUSE_NOW) {
                return 0;
            }
            double along = to.dot(heading);
            if (along <= 0.0) {
                continue;                   // derriere la grenade : elle s'en eloigne
            }
            double miss = Math.sqrt(Math.max(0.0, distance * distance - along * along));
            if (miss > GunSpec.PLASMITE_FUSE_MISS) {
                continue;                   // elle passera trop loin de lui
            }
            int ticks = (int) Math.max(1L, Math.min(GunSpec.PLASMITE_FUSE_MAX, Math.round(along / speed)));
            best = best < 0 ? ticks : Math.min(best, ticks);
        }
        return best;
    }

    private void explode(ServerLevel level, ServerPlayer owner, Vec3 at) {
        int broken = GunImpacts.breakSphere(level, at, GunSpec.PLASMITE_BREAK_RADIUS, GunSpec.PLASMITE_BLOCKS, owner);
        List<Mob> targets = GunImpacts.targetsAround(level, at, GunSpec.PLASMITE_BLAST, GunSpec.PLASMITE_TARGETS);
        for (Mob mob : targets) {
            GunImpacts.hurt(owner, this, mob, GunSpec.PLASMITE_DAMAGE);
        }
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
