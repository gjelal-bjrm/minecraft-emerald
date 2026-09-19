package com.emerald.jak.gun;

import com.emerald.init.Jak3Registry;
import com.emerald.particles.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Le champ du Mass Inverter (gun-gravity, gun-dark-shot.gc:3229-3567).
 *
 * Il nait AU SOL SOUS LE TIREUR et s'etend tout autour de lui : de 0 a
 * {@value GunSpec#INVERTER_RADIUS} blocs en {@value GunSpec#INVERTER_GROW_TICKS}
 * tiques, puis il tient jusqu'a {@value GunSpec#INVERTER_FIELD_TICKS} tiques. Tout
 * monstre de Haven qui s'y trouve -- ou qui y entre -- est souleve, une seule fois
 * par champ, pour 7 a 9 secondes moins le temps deja ecoule du champ, deux secondes
 * au moins (GunLevitation). Le champ lui-meme ne blesse personne et ne touche ni
 * joueur, ni habitant, ni voiture, ni decor.
 *
 * LE CLIENT dessine son bord violet d'apres son age (GunRenderers.GravityField) et
 * seme ses colonnes montantes.
 */
public class GunGravityFieldEntity extends Entity {

    /** La tranche verticale du champ autour de son sol. */
    private static final double HEIGHT = 8.0;

    @Nullable
    private ServerPlayer shooter;
    private int lifted;

    public GunGravityFieldEntity(EntityType<? extends GunGravityFieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static GunGravityFieldEntity spawn(ServerLevel level, ServerPlayer shooter) {
        GunGravityFieldEntity field = new GunGravityFieldEntity(Jak3Registry.GUN_GRAVITY_FIELD.get(), level);
        Vec3 at = GunShockwaveEntity.ground(level, shooter);
        field.setPos(at.x, at.y, at.z);
        field.shooter = shooter;
        level.addFreshEntity(field);
        return field;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    /** Monstres souleves par ce champ (banc d'essai). */
    public int lifted() {
        return this.lifted;
    }

    /** Le rayon du champ a l'age donne, en tiques. */
    public static double radiusAt(double age) {
        return GunSpec.INVERTER_RADIUS * Math.max(0.0, Math.min(1.0, age / GunSpec.INVERTER_GROW_TICKS));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel server)) {
            clientColumns();
            return;
        }
        ServerPlayer owner = this.shooter;
        if (owner == null || owner.isRemoved() || !MorphGunKeeper.allowed(owner) || this.tickCount > GunSpec.INVERTER_FIELD_TICKS) {
            this.discard();
            return;
        }
        double radius = radiusAt(this.tickCount);
        Vec3 center = this.position();
        long now = server.getGameTime();
        int left = Math.max(40, GunSpec.INVERTER_FLOAT_MIN + this.random.nextInt(GunSpec.INVERTER_FLOAT_SPAN + 1) - this.tickCount);
        AABB box = new AABB(center, center).inflate(radius + 1.0, HEIGHT, radius + 1.0);
        int taken = 0;
        for (Mob mob : server.getEntitiesOfClass(Mob.class, box, GunImpacts::isTarget)) {
            if (taken >= GunSpec.INVERTER_TARGETS) {
                break;
            }
            Vec3 at = mob.position();
            if (Math.hypot(at.x - center.x, at.z - center.z) <= radius && GunLevitation.lift(mob, owner, now, left)) {
                this.lifted++;
                taken++;
            }
        }
    }

    /** Les colonnes montantes du champ, cote client : quatorze par tique, au hasard dans le disque (six se perdaient dans 2 800 blocs carres : planche C). */
    private void clientColumns() {
        double radius = radiusAt(this.tickCount);
        for (int i = 0; i < 14; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double r = Math.sqrt(this.random.nextDouble()) * radius;
            this.level().addParticle(ModParticles.GUN_INVERTER_RISE.get(), true, this.getX() + Math.cos(a) * r, this.getY() + 0.1,
                    this.getZ() + Math.sin(a) * r, 0.0, 0.12 + this.random.nextDouble() * 0.1, 0.0);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        double r = GunSpec.INVERTER_RADIUS + 1.0;
        return this.getBoundingBox().inflate(r, 3.0, r);
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
