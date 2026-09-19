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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * La soucoupe du Gyro Burster (gun-yellow-3-saucer, gun-yellow-shot.gc:64-928).
 *
 * UNE TOURELLE VOLANTE. Le premier appui la LANCE : {@value GunSpec#GYRO_SPEED} blocs
 * par tique, a 31 degres vers le haut (y force a {@value GunSpec#GYRO_LAUNCH_UP}).
 * Apres une tique elle FREINE jusqu'a {@value GunSpec#GYRO_DRIFT} bloc par tique, sa
 * montee s'eteint, et elle DERIVE en tournant sur elle-meme ; un mur a moins de
 * {@value GunSpec#GYRO_WALL_PROBE} blocs devant elle la renvoie. Une seconde plus
 * tard ELLE TIRE, {@value GunSpec#GYRO_SHOTS_PER_TICK} tirs par tique pendant
 * {@value GunSpec#GYRO_FIRE_TICKS} tiques, sur des monstres de Haven tires au sort
 * parmi ceux qu'elle voit a {@value GunSpec#GYRO_RANGE} blocs (ceux que le tireur a
 * devant lui pesent trois fois plus) ; faute de cible, le tir part vers le bas, au
 * hasard. Chaque tir vaut {@value GunSpec#GYRO_DAMAGE} points de Jak. UN SECOND APPUI
 * avant qu'elle ne tire la REVEILLE sur place : elle se fige et tire tout de suite.
 *
 * LE COUT : il faut 10 eco jaunes pour la lancer, et elle en boit
 * {@value GunSpec#GYRO_DRAIN} sur toute sa rafale, au fil des tiques ; reserve
 * vide, elle s'arrete. Puis elle TOMBE, se pose, attend une seconde, retrecit et
 * s'eteint. Une seule soucoupe par tireur (GunFire) ; elle disparait si son tireur
 * quitte Haven.
 *
 * Les tirs sont des RAYONS, pas des projectiles : quarante entites par seconde et par
 * soucoupe auraient pese sur le serveur et le reseau pour un trajet de deux ou trois
 * tiques. Leur trace part en un paquet par salve (GunTracePayload.GYRO).
 */
public class GunSaucerEntity extends Projectile {

    public static final int FLY = 0;
    public static final int DRIFT = 1;
    public static final int SPIN = 2;
    public static final int FALL = 3;
    public static final int SIT = 4;
    public static final int SHRINK = 5;

    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(GunSaucerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FIRING =
            SynchedEntityData.defineId(GunSaucerEntity.class, EntityDataSerializers.BOOLEAN);

    private int phaseTicks;
    private int fired;
    private int wallCooldown;
    private double drain;
    // releves
    private int spent;
    private int shots;
    private int strays;
    private int hits;
    /** Cote client : la tique (tickCount) ou le retrecissement a commence. */
    public int shrinkAge = -1;

    public GunSaucerEntity(EntityType<? extends GunSaucerEntity> type, Level level) {
        super(type, level);
    }

    public GunSaucerEntity(Level level, LivingEntity shooter) {
        this(Jak3Registry.GUN_SAUCER.get(), level);
        this.setOwner(shooter);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PHASE, FLY);
        builder.define(FIRING, false);
    }

    public int phase() {
        return this.entityData.get(PHASE);
    }

    public boolean firing() {
        return this.entityData.get(FIRING);
    }

    /** Eco jaune bu par cette soucoupe (banc d'essai). */
    public int spent() {
        return this.spent;
    }

    public int shots() {
        return this.shots;
    }

    public int strays() {
        return this.strays;
    }

    public int hits() {
        return this.hits;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (PHASE.equals(key) && this.phase() == SHRINK && this.shrinkAge < 0) {
            this.shrinkAge = this.tickCount;
        }
    }

    /** Pose la soucoupe au depart, cote serveur, AVANT addFreshEntity. */
    public void launch(Vec3 origin, Vec3 look) {
        Vec3 d = new Vec3(look.x, GunSpec.GYRO_LAUNCH_UP, look.z).normalize();
        this.setPos(origin.x, origin.y, origin.z);
        this.setDeltaMovement(d.scale(GunSpec.GYRO_SPEED));
        this.hasImpulse = true;
    }

    /** Le second appui : vrai si la soucoupe s'est reveillee (elle ne tirait pas encore). */
    public boolean activate() {
        int phase = this.phase();
        if ((phase != FLY && phase != DRIFT) || this.firing()) {
            return false;
        }
        phase(SPIN);
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(FIRING, true);
        return true;
    }

    /** Encore en l'air et pas eteinte : un nouveau lancer est refuse. */
    public boolean busy() {
        int phase = this.phase();
        return !this.isRemoved() && (phase == FLY || phase == DRIFT || phase == SPIN);
    }

    private void phase(int next) {
        this.entityData.set(PHASE, next);
        this.phaseTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) {
            clientLights();
            return;
        }
        if (!(this.getOwner() instanceof ServerPlayer owner) || owner.isRemoved() || !MorphGunKeeper.allowed(owner)
                || this.tickCount > GunSpec.GYRO_LIFE) {
            this.discard();
            return;
        }
        this.phaseTicks++;
        if (this.wallCooldown > 0) {
            this.wallCooldown--;
        }
        switch (this.phase()) {
            case FLY -> {
                move(server, this.getDeltaMovement(), true);
                if (this.phaseTicks >= GunSpec.GYRO_FLOAT_TICKS) {
                    phase(DRIFT);
                }
            }
            case DRIFT -> drift(server, owner);
            case SPIN -> fire(server, owner);
            case FALL -> {
                Vec3 v = this.getDeltaMovement();
                v = new Vec3(v.x * 0.9, Math.max(-3.0, v.y - GunSpec.PLASMITE_GRAVITY), v.z * 0.9);
                if (move(server, v, false)) {
                    this.setDeltaMovement(Vec3.ZERO);
                    phase(SIT);
                    server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                            0.3F, 1.4F);
                }
            }
            case SIT -> {
                if (this.phaseTicks >= GunSpec.GYRO_SIT_TICKS) {
                    phase(SHRINK);
                    server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH,
                            SoundSource.PLAYERS, 0.6F, 1.8F);
                }
            }
            default -> {
                if (this.phaseTicks >= GunSpec.GYRO_SHRINK_TICKS) {
                    server.sendParticles(ModParticles.GUN_GYRO_SPARK.get(), this.getX(), this.getY() + 0.1, this.getZ(), 12,
                            0.3, 0.1, 0.3, 0.2);
                    this.discard();
                }
            }
        }
    }

    /** La derive : freinage, montee eteinte, murs, puis le tir une seconde apres. */
    private void drift(ServerLevel level, ServerPlayer owner) {
        Vec3 v = this.getDeltaMovement();
        double flat = Math.hypot(v.x, v.z);
        double f = Math.min(1.0, this.phaseTicks / (double) GunSpec.GYRO_SLOW_TICKS);
        double wanted = GunSpec.GYRO_SPEED + (GunSpec.GYRO_DRIFT - GunSpec.GYRO_SPEED) * f;
        if (this.phaseTicks > GunSpec.GYRO_SLOW_TICKS) {
            wanted = Math.min(flat, GunSpec.GYRO_DRIFT);
        }
        Vec3 heading = flat < 1.0e-4 ? Vec3.ZERO : new Vec3(v.x / flat, 0.0, v.z / flat);
        if (this.wallCooldown == 0 && flat > 1.0e-4) {
            BlockHitResult ahead = level.clip(new ClipContext(this.position(), this.position().add(heading.scale(GunSpec.GYRO_WALL_PROBE)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (ahead.getType() == HitResult.Type.BLOCK) {
                heading = GunReflexor.reflect(heading, ahead.getDirection());
                wanted = GunSpec.GYRO_WALL_SPEED;
                this.wallCooldown = GunSpec.GYRO_WALL_COOLDOWN;
                level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS,
                        0.4F, 0.9F);
            }
        }
        move(level, new Vec3(heading.x * wanted, v.y * 0.7, heading.z * wanted), true);
        if (this.phaseTicks >= GunSpec.GYRO_ARM_TICKS) {
            if (!this.firing()) {
                this.entityData.set(FIRING, true);
            }
            fire(level, owner);
        }
    }

    /**
     * Avance d'une vitesse ; un bloc arrete le pas.
     *
     * @param bounce le mur renvoie la soucoupe (vol) ; sinon elle s'y pose (chute)
     * @return vrai si un bloc a ete touche
     */
    private boolean move(ServerLevel level, Vec3 velocity, boolean bounce) {
        Vec3 from = this.position();
        Vec3 to = from.add(velocity);
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() == HitResult.Type.BLOCK) {
            Vec3 stop = block.getLocation();
            Vec3 back = velocity.lengthSqr() < 1.0e-9 ? Vec3.ZERO : velocity.normalize().scale(0.3);
            this.setPos(stop.x - back.x, stop.y - back.y, stop.z - back.z);
            this.setDeltaMovement(bounce ? GunReflexor.reflect(velocity, block.getDirection()).scale(0.2) : Vec3.ZERO);
            this.hasImpulse = true;
            return true;
        }
        this.setPos(to.x, to.y, to.z);
        this.setDeltaMovement(velocity);
        return false;
    }

    /** Une tique de rafale : l'eco, deux tirs, la trace ; la chute a la fin. */
    private void fire(ServerLevel level, ServerPlayer owner) {
        if (this.fired >= GunSpec.GYRO_FIRE_TICKS) {
            stop(level);
            return;
        }
        this.drain += GunSpec.GYRO_DRAIN / GunSpec.GYRO_FIRE_TICKS;
        while (this.drain >= 1.0) {
            ItemStack gun = MorphGunKeeper.find(owner);
            if (gun == null || !MorphGunData.spend(gun, GunForm.Family.YELLOW, 1)) {
                stop(level);
                return;
            }
            this.drain -= 1.0;
            this.spent++;
        }
        this.fired++;
        Vec3 from = this.position().add(0.0, 0.1, 0.0);
        List<Mob> picked = pick(level, owner, from);
        RandomSource random = this.random;
        float[] ends = new float[GunSpec.GYRO_SHOTS_PER_TICK * 4];
        boolean broke = false;
        for (int i = 0; i < GunSpec.GYRO_SHOTS_PER_TICK; i++) {
            Vec3 direction;
            double range;
            if (i < picked.size()) {
                Vec3 aim = GunImpacts.center(picked.get(i)).add((random.nextDouble() - 0.5) * 2.0 * 0.5,
                        (random.nextDouble() - 0.5) * 2.0 * 0.5, (random.nextDouble() - 0.5) * 2.0 * 0.5);
                Vec3 to = aim.subtract(from);
                range = to.length() + 2.0;
                direction = to.normalize();
            } else {
                direction = new Vec3(random.nextDouble() * 2.0 - 1.0, -0.3 - random.nextDouble() * 0.55,
                        random.nextDouble() * 2.0 - 1.0).normalize();
                range = GunSpec.GYRO_STRAY_RANGE;
                this.strays++;
            }
            GunImpacts.Ray ray = GunImpacts.ray(level, this, from, direction, range, 0.25F);
            if (ray.target() != null) {
                if (GunImpacts.hurt(owner, this, ray.target(), GunSpec.GYRO_DAMAGE)) {
                    this.hits++;
                }
            } else if (ray.block() != null && !broke) {
                broke = GunImpacts.breakOne(level, ray.block(), owner);
            }
            ends[i * 4] = (float) ray.end().x;
            ends[i * 4 + 1] = (float) ray.end().y;
            ends[i * 4 + 2] = (float) ray.end().z;
            ends[i * 4 + 3] = ray.flag();
            this.shots++;
        }
        PacketDistributor.sendToPlayersNear(level, null, from.x, from.y, from.z, 128.0,
                new GunTracePayload(-1, GunTracePayload.GYRO, from.x, from.y, from.z, ends));
        if (this.fired % 2 == 0) {
            level.playSound(null, from.x, from.y, from.z, SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.35F, 2.0F);
        }
    }

    private void stop(ServerLevel level) {
        this.entityData.set(FIRING, false);
        phase(FALL);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    /**
     * Deux cibles distinctes, tirees au sort parmi les monstres vus a portee : poids 3
     * pour ceux que le tireur a devant lui (le « a l'ecran » du jeu), 1 pour les autres.
     */
    private List<Mob> pick(ServerLevel level, ServerPlayer owner, Vec3 from) {
        List<Mob> seen = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        for (Mob mob : GunImpacts.targetsAround(level, from, GunSpec.GYRO_RANGE, 48)) {
            Vec3 at = GunImpacts.center(mob);
            if (level.clip(new ClipContext(from, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType()
                    != HitResult.Type.MISS) {
                continue;
            }
            Vec3 to = at.subtract(eye);
            double length = to.length();
            boolean inFront = length > 1.0e-3 && to.dot(look) / length > 0.5;
            seen.add(mob);
            weights.add(inFront ? 3.0 : 1.0);
        }
        List<Mob> out = new ArrayList<>();
        for (int k = 0; k < GunSpec.GYRO_SHOTS_PER_TICK && !seen.isEmpty(); k++) {
            double total = 0.0;
            for (double w : weights) {
                total += w;
            }
            double roll = this.random.nextDouble() * total;
            int index = 0;
            while (index < seen.size() - 1 && roll >= weights.get(index)) {
                roll -= weights.get(index);
                index++;
            }
            out.add(seen.remove(index));
            weights.remove(index);
        }
        return out;
    }

    /** Les feux de la soucoupe, cote client : des eclats sur sa jante pendant la rafale. */
    private void clientLights() {
        if (!this.firing() || this.tickCount % 2 != 0) {
            return;
        }
        double a = this.random.nextDouble() * Math.PI * 2.0;
        double r = 0.25 * GunSpec.GYRO_SCALE;
        this.level().addParticle(ModParticles.GUN_GYRO_SPARK.get(), true, this.getX() + Math.cos(a) * r, this.getY() + 0.1,
                this.getZ() + Math.sin(a) * r, Math.cos(a) * 0.1, 0.05, Math.sin(a) * 0.1);
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
}
