package com.emerald.jak.gun;

import com.emerald.init.Jak3Registry;
import com.emerald.particles.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * L'onde de choc du Wave Concussor (gun-red-2-shockwave, gun-red-shot.gc:417-1453).
 *
 * Elle nait au sol sous le tireur quand il relache la gachette, et S'ETEND : le
 * rayon va de {@value GunSpec#WAVE_RADIUS_MIN} a {@value GunSpec#WAVE_RADIUS_MAX}
 * blocs en {@value GunSpec#WAVE_EXPAND_TICKS} tiques, ecrete au rayon final que la
 * charge a gagne (3 blocs sans charge, 18 a charge pleine). L'INTENSITE part de la
 * force de la charge au centre et tombe a zero au rayon final ; un monstre prend
 * max({@value GunSpec#WAVE_DAMAGE_MIN} ; {@value GunSpec#WAVE_DAMAGE} x intensite)
 * points de Jak quand le front le traverse, UNE SEULE FOIS, et il est pousse vers
 * le dehors. A moins de {@value GunSpec#WAVE_SURE} blocs la touche est sure ;
 * au-dela, il faut une ligne de vue depuis le centre. L'onde court au sol : seuls
 * comptent les monstres a {@value GunSpec#WAVE_HEIGHT} blocs de son sol au plus.
 *
 * Ni joueur, ni habitant, ni voiture, ni decor : GunImpacts ne connait que les
 * monstres de Haven. L'onde ne casse rien -- dans le jeu elle ne fait que noircir
 * le sol.
 *
 * LE CLIENT dessine l'anneau d'apres son age et le rayon final synchronise
 * (GunRenderers.Shockwave), et seme ses braises sur le front.
 */
public class GunShockwaveEntity extends Entity {

    private static final EntityDataAccessor<Float> MAX_RADIUS =
            SynchedEntityData.defineId(GunShockwaveEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STRENGTH =
            SynchedEntityData.defineId(GunShockwaveEntity.class, EntityDataSerializers.FLOAT);

    /** L'anneau reste dessine quelques tiques apres son rayon final, le temps de s'eteindre. */
    public static final int FADE_TICKS = 4;

    /** Le tireur, cote serveur (l'onde n'est jamais sauvegardee : la reference suffit, FakePlayer du banc compris). */
    @Nullable
    private ServerPlayer shooter;
    private final Set<Integer> struck = new HashSet<>();
    private int struckCount;

    public GunShockwaveEntity(EntityType<? extends GunShockwaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** L'onde d'un tireur, au sol sous lui, a la force de sa charge (0 a 1). */
    public static GunShockwaveEntity spawn(ServerLevel level, ServerPlayer shooter, double strength) {
        GunShockwaveEntity wave = new GunShockwaveEntity(Jak3Registry.GUN_SHOCKWAVE.get(), level);
        double s = Math.max(0.0, Math.min(1.0, strength));
        Vec3 at = ground(level, shooter);
        wave.setPos(at.x, at.y, at.z);
        wave.shooter = shooter;
        wave.entityData.set(STRENGTH, (float) s);
        wave.entityData.set(MAX_RADIUS, (float) (GunSpec.WAVE_RADIUS_MIN
                + (GunSpec.WAVE_RADIUS_MAX - GunSpec.WAVE_RADIUS_MIN) * s));
        level.addFreshEntity(wave);
        wave.strike(level, shooter, 0.0, GunSpec.WAVE_RADIUS_MIN);
        return wave;
    }

    /** Le sol sous le tireur : sonde de 6 blocs au-dessus de ses pieds a 20 dessous ; ses pieds faute de sol. */
    static Vec3 ground(ServerLevel level, Entity shooter) {
        Vec3 feet = shooter.position();
        BlockHitResult hit = level.clip(new ClipContext(feet.add(0.0, Math.min(GunSpec.WAVE_GROUND_UP, 1.0), 0.0),
                feet.add(0.0, -GunSpec.WAVE_GROUND_DOWN, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, shooter));
        return hit.getType() == HitResult.Type.MISS ? feet : new Vec3(feet.x, hit.getLocation().y, feet.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MAX_RADIUS, (float) GunSpec.WAVE_RADIUS_MIN);
        builder.define(STRENGTH, 0.0F);
    }

    public float maxRadius() {
        return this.entityData.get(MAX_RADIUS);
    }

    public float strength() {
        return this.entityData.get(STRENGTH);
    }

    /** Monstres deja frappes par cette onde (banc d'essai). */
    public int struck() {
        return this.struckCount;
    }

    /** Le rayon du front a l'age donne, en tiques (fractionnaire pour le rendu). */
    public double radiusAt(double age) {
        double grown = GunSpec.WAVE_RADIUS_MIN + (GunSpec.WAVE_RADIUS_MAX - GunSpec.WAVE_RADIUS_MIN)
                * Math.max(0.0, age) / GunSpec.WAVE_EXPAND_TICKS;
        return Math.min(this.maxRadius(), grown);
    }

    /** L'intensite du front au rayon donne : la force de la charge au centre, zero au rayon final. */
    public double intensityAt(double radius) {
        double span = this.maxRadius() - GunSpec.WAVE_RADIUS_MIN;
        double t = span < 1.0e-6 ? 0.0 : Math.max(0.0, Math.min(1.0, (radius - GunSpec.WAVE_RADIUS_MIN) / span));
        return this.strength() * (1.0 - t);
    }

    /** Les tiques que met le front a atteindre son rayon final. */
    public int expandTicks() {
        double span = this.maxRadius() - GunSpec.WAVE_RADIUS_MIN;
        return (int) Math.ceil(span / (GunSpec.WAVE_RADIUS_MAX - GunSpec.WAVE_RADIUS_MIN) * GunSpec.WAVE_EXPAND_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) {
            clientEmbers();
            return;
        }
        ServerPlayer shooter = this.shooter;
        if (shooter == null || shooter.isRemoved() || !MorphGunKeeper.allowed(shooter)) {
            this.discard();
            return;
        }
        int age = this.tickCount;
        if (age <= this.expandTicks()) {
            strike(server, shooter, this.radiusAt(age - 1), this.radiusAt(age));
        } else if (age > this.expandTicks() + FADE_TICKS) {
            this.discard();
        }
    }

    /** Frappe les monstres que le front vient de traverser : distance a plat dans ]inner, outer]. */
    private void strike(ServerLevel level, ServerPlayer shooter, double inner, double outer) {
        Vec3 center = this.position();
        AABB box = new AABB(center, center).inflate(outer + 1.0, GunSpec.WAVE_HEIGHT + 2.0, outer + 1.0);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, GunImpacts::isTarget)) {
            if (this.struck.contains(mob.getId())) {
                continue;
            }
            Vec3 at = GunImpacts.center(mob);
            double flat = Math.hypot(at.x - center.x, at.z - center.z);
            if (flat > outer || (flat <= inner && inner > 0.0) || Math.abs(at.y - center.y) > GunSpec.WAVE_HEIGHT) {
                continue;
            }
            if (flat > GunSpec.WAVE_SURE && !sees(level, center.add(0.0, 1.0, 0.0), at)) {
                continue;
            }
            this.struck.add(mob.getId());
            this.struckCount++;
            double intensity = this.intensityAt(flat);
            float damage = Math.max(GunSpec.WAVE_DAMAGE_MIN, GunSpec.WAVE_DAMAGE * (float) intensity);
            GunImpacts.hurt(shooter, null, mob, damage);
            if (mob.isAlive()) {
                double push = GunSpec.WAVE_PUSH * (0.35 + 0.65 * intensity);
                double nx = flat < 1.0e-3 ? 0.0 : (at.x - center.x) / flat;
                double nz = flat < 1.0e-3 ? 0.0 : (at.z - center.z) / flat;
                mob.setDeltaMovement(mob.getDeltaMovement().add(nx * push, GunSpec.WAVE_LIFT * (0.4 + 0.6 * intensity), nz * push));
                mob.hasImpulse = true;
            }
        }
    }

    private boolean sees(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /** Les braises du front, cote client : un tour de cercle par tique, plus clairseme quand l'onde faiblit. */
    private void clientEmbers() {
        int age = this.tickCount;
        if (age > this.expandTicks() + 1) {
            return;
        }
        double radius = this.radiusAt(age);
        double intensity = Math.max(0.15, this.intensityAt(radius));
        int count = (int) Math.min(64, 10 + radius * 3.0 * intensity);
        for (int i = 0; i < count; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double r = radius - this.random.nextDouble() * 0.6;
            this.level().addParticle(ModParticles.GUN_WAVE_DUST.get(), true, this.getX() + Math.cos(a) * r,
                    this.getY() + 0.1 + this.random.nextDouble() * 0.3, this.getZ() + Math.sin(a) * r,
                    Math.cos(a) * 0.08, 0.08 + this.random.nextDouble() * 0.12 * intensity, Math.sin(a) * 0.08);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = this.maxRadius() + 1.0;
        return this.getBoundingBox().inflate(r, 2.0, r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 160.0 * 160.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
