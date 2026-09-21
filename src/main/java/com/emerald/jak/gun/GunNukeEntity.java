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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Le missile de la Super Nova (gun-dark-3-nuke, gun-dark-shot.gc:265-1421).
 *
 * LE VOL, PAR PALIERS (:509-691) : {@value GunSpec#NOVA_PHASE0_TICKS} tiques a
 * {@value GunSpec#NOVA_PHASE0_SPEED} bloc par tique -- le missile sort lentement du
 * canon --, puis il accelere jusqu'a {@value GunSpec#NOVA_PHASE1_TO} blocs par tique en
 * {@value GunSpec#NOVA_PHASE1_TICKS} tiques, puis jusqu'a {@value GunSpec#NOVA_PHASE2_TO}
 * en {@value GunSpec#NOVA_PHASE2_TICKS} tiques en decrivant UN ARC de
 * {@value GunSpec#NOVA_ARC} blocs au-dessus de sa ligne (sinus de 0 a 270 degres), et
 * file enfin tout droit, {@value GunSpec#NOVA_PHASE3_TICKS} tiques au plus.
 *
 * L'IMPACT. S'il touche un bloc pendant les deux premiers paliers, il SE PLANTE : deux
 * bips, et la detonation {@value GunSpec#NOVA_EMBEDDED_TICKS} tiques plus tard -- le
 * temps de s'eloigner, pour le plaisir seulement : aucun joueur n'est jamais blesse.
 * Sinon il detone au contact d'un bloc ou d'un monstre, ou en fin de vol. LA FRAPPE
 * tombe {@value GunSpec#NOVA_STRIKE_DELAY} tiques apres la detonation :
 * {@value GunSpec#NOVA_DAMAGE} points de Jak a {@value GunSpec#NOVA_TARGETS} monstres
 * de Haven au plus, les plus proches, dans {@value GunSpec#NOVA_RADIUS} blocs (voir
 * GunSpec pour ce rayon) ; le decor casse dans {@value GunSpec#NOVA_BREAK_RADIUS} blocs.
 * Les clients proches recoivent l'eclair blanc et la secousse (GunNovaPayload).
 *
 * Suivi a chaque tique : a 6,75 blocs par tique la vitesse envoyee serait
 * plafonnee, mais les positions ne le sont pas. Le modele : gun-nuke de Jak 3.
 */
public class GunNukeEntity extends Projectile {

    public static final int FLYING = 0;
    public static final int EMBEDDED = 1;
    public static final int DETONATED = 2;

    private static final EntityDataAccessor<Integer> STATE =
            SynchedEntityData.defineId(GunNukeEntity.class, EntityDataSerializers.INT);

    private Vec3 heading = new Vec3(0.0, 0.0, 1.0);
    private int stateTicks;
    private double arcBefore;
    private int struck = -1;
    /** Cote client : la tique (tickCount) de la detonation, pour la boule de feu. */
    public int detonationAge = -1;

    public GunNukeEntity(EntityType<? extends GunNukeEntity> type, Level level) {
        super(type, level);
    }

    public GunNukeEntity(Level level, LivingEntity shooter) {
        this(Jak3Registry.GUN_NUKE.get(), level);
        this.setOwner(shooter);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, FLYING);
    }

    public int state() {
        return this.entityData.get(STATE);
    }

    /** Monstres frappes par la detonation, -1 avant la frappe (banc d'essai). */
    public int struck() {
        return this.struck;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STATE.equals(key) && this.state() == DETONATED && this.detonationAge < 0) {
            this.detonationAge = this.tickCount;
        }
    }

    /** Pose le missile au depart, cote serveur, AVANT addFreshEntity. */
    public void launch(Vec3 origin, Vec3 direction) {
        this.heading = direction.normalize();
        this.setPos(origin.x, origin.y, origin.z);
        this.setDeltaMovement(this.heading.scale(GunSpec.NOVA_PHASE0_SPEED));
        this.hasImpulse = true;
    }

    /** La vitesse le long de la ligne de tir a la tique de vol donnee. */
    public static double speedAt(int tick) {
        if (tick < GunSpec.NOVA_PHASE0_TICKS) {
            return GunSpec.NOVA_PHASE0_SPEED;
        }
        int t1 = tick - GunSpec.NOVA_PHASE0_TICKS;
        if (t1 < GunSpec.NOVA_PHASE1_TICKS) {
            return GunSpec.NOVA_PHASE1_FROM + (GunSpec.NOVA_PHASE1_TO - GunSpec.NOVA_PHASE1_FROM) * t1 / GunSpec.NOVA_PHASE1_TICKS;
        }
        int t2 = t1 - GunSpec.NOVA_PHASE1_TICKS;
        if (t2 < GunSpec.NOVA_PHASE2_TICKS) {
            return GunSpec.NOVA_PHASE1_TO + (GunSpec.NOVA_PHASE2_TO - GunSpec.NOVA_PHASE1_TO) * t2 / GunSpec.NOVA_PHASE2_TICKS;
        }
        return GunSpec.NOVA_PHASE2_TO;
    }

    /** La hauteur de l'arc au-dessus de la ligne de tir : sinus de 0 a 270 degres pendant le troisieme palier. */
    public static double arcAt(int tick) {
        int t2 = tick - GunSpec.NOVA_PHASE0_TICKS - GunSpec.NOVA_PHASE1_TICKS;
        if (t2 <= 0) {
            return 0.0;
        }
        double f = Math.min(1.0, t2 / (double) GunSpec.NOVA_PHASE2_TICKS);
        return GunSpec.NOVA_ARC * Math.sin(Math.toRadians(270.0 * f));
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
        this.stateTicks++;
        switch (this.state()) {
            case FLYING -> fly(server, owner);
            case EMBEDDED -> {
                if (this.stateTicks == 6 || this.stateTicks == 10) {
                    server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(),
                            SoundSource.PLAYERS, 1.0F, 1.8F);
                }
                if (this.stateTicks >= GunSpec.NOVA_EMBEDDED_TICKS) {
                    detonate(server);
                }
            }
            default -> {
                if (this.stateTicks == GunSpec.NOVA_STRIKE_DELAY) {
                    strike(server, owner);
                } else if (this.stateTicks > GunSpec.NOVA_STRIKE_DELAY + 24) {
                    this.discard();
                }
            }
        }
    }

    private void fly(ServerLevel level, ServerPlayer owner) {
        int tick = this.stateTicks - 1;
        int launchTicks = GunSpec.NOVA_PHASE0_TICKS + GunSpec.NOVA_PHASE1_TICKS;
        if (tick >= launchTicks + GunSpec.NOVA_PHASE2_TICKS + GunSpec.NOVA_PHASE3_TICKS) {
            detonate(level);
            return;
        }
        double arc = arcAt(tick + 1);
        Vec3 step = this.heading.scale(speedAt(tick)).add(0.0, arc - this.arcBefore, 0.0);
        this.arcBefore = arc;
        Vec3 from = this.position();
        Vec3 to = from.add(step);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, from, stop,
                this.getBoundingBox().expandTowards(step).inflate(1.0), GunImpacts::isTarget, 0.4F);
        if (hit != null) {
            Vec3 at = hit.getEntity().getBoundingBox().inflate(0.4).clip(from, stop).orElse(GunImpacts.center(hit.getEntity()));
            this.setPos(at.x, at.y, at.z);
            detonate(level);
            return;
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            Vec3 back = step.normalize().scale(0.2);
            this.setPos(stop.x - back.x, stop.y - back.y, stop.z - back.z);
            if (tick < launchTicks) {
                this.entityData.set(STATE, EMBEDDED);
                this.stateTicks = 0;
                this.setDeltaMovement(Vec3.ZERO);
                level.playSound(null, stop.x, stop.y, stop.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5F, 0.7F);
            } else {
                detonate(level);
            }
            return;
        }
        this.setPos(to.x, to.y, to.z);
        this.setDeltaMovement(step);
        this.hasImpulse = true;
    }

    private void detonate(ServerLevel level) {
        this.entityData.set(STATE, DETONATED);
        this.stateTicks = 0;
        this.setDeltaMovement(Vec3.ZERO);
        Vec3 at = this.position();
        // les vehicules de la place sont souffles et desequilibres
        com.emerald.jak.vehicle.VehicleImpacts.blast(level, at, com.emerald.jak.vehicle.VehicleImpacts.BLAST_NOVA_RADIUS,
                com.emerald.jak.vehicle.VehicleImpacts.BLAST_NOVA);
        level.sendParticles(ModParticles.GUN_NOVA_BLAST.get(), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ModParticles.GUN_NOVA_MOTE.get(), at.x, at.y, at.z, 120, 2.5, 2.5, 2.5, 0.9);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.6F);
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, GunSpec.NOVA_FLASH_RANGE,
                new GunNovaPayload(at.x, at.y, at.z));
    }

    /** La frappe : les monstres les plus proches, 64 au plus, et le decor autour du point d'impact. */
    private void strike(ServerLevel level, ServerPlayer owner) {
        Vec3 at = this.position();
        int broken = GunImpacts.breakSphere(level, at, GunSpec.NOVA_BREAK_RADIUS, GunSpec.NOVA_BLOCKS, owner);
        List<Mob> targets = GunImpacts.targetsAround(level, at, GunSpec.NOVA_RADIUS, GunSpec.NOVA_TARGETS);
        for (Mob mob : targets) {
            GunImpacts.hurt(owner, this, mob, GunSpec.NOVA_DAMAGE);
        }
        this.struck = targets.size();
        GunEco.logExplosion(owner, at, broken, targets.size());
    }

    /** La trainee du missile, cote client. */
    private void clientTrail() {
        if (this.state() != FLYING) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double t = i / 3.0;
            this.level().addParticle(ModParticles.GUN_NOVA_MOTE.get(), true, this.xo + (this.getX() - this.xo) * t,
                    this.yo + (this.getY() - this.yo) * t, this.zo + (this.getZ() - this.zo) * t, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192.0 * 192.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
