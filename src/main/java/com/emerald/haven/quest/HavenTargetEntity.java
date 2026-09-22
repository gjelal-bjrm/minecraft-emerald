package com.emerald.haven.quest;

import com.emerald.haven.quest.runs.RangeRun;
import com.emerald.jak.gun.GunImpacts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * UNE CIBLE des epreuves de tir de Tess (lot 3, cahier §86) : les cibles en carton du stand
 * de tir de Jak 3 (niveau lgunnorm), cuites par tools/jak_gun.py -- trois gardes KG a
 * abattre, le KG dore qui vaut trois points, et quatre civils qu'on ne tire pas (deux
 * points de moins). Elle se tient debout, face au tireur ; les cibles MOBILES glissent de
 * cote. Une entite vivante, pour que les armes du Morph Gun la visent (GunImpacts.isTarget) ;
 * le premier coup la fait eclater et compte pour l'epreuve (RangeRun). Jamais sauvegardee.
 */
public class HavenTargetEntity extends PathfinderMob {

    /** Les huit cibles, dans l'ordre des modeles (HavenTargetRenderer). */
    public static final String[] MODELS = {"haven_cible_kg_b", "haven_cible_kg_c", "haven_cible_kg_d", "haven_cible_bonus",
            "haven_cible_cit_a", "haven_cible_cit_b", "haven_cible_cit_c", "haven_cible_cit_d"};
    public static final int BONUS = 3;
    public static final int FIRST_CIVILIAN = 4;

    private static final EntityDataAccessor<Boolean> MOVING = SynchedEntityData.defineId(HavenTargetEntity.class,
            EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(HavenTargetEntity.class,
            EntityDataSerializers.INT);
    private Vec3 anchor = Vec3.ZERO;
    private Vec3 axis = new Vec3(1, 0, 0);
    private int life = 60;

    public HavenTargetEntity(EntityType<? extends HavenTargetEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOVING, false);
        builder.define(VARIANT, 0);
    }

    public boolean moving() {
        return this.entityData.get(MOVING);
    }

    public int variant() {
        return Math.floorMod(this.entityData.get(VARIANT), MODELS.length);
    }

    public boolean civilian() {
        return variant() >= FIRST_CIVILIAN;
    }

    public boolean bonus() {
        return variant() == BONUS;
    }

    /**
     * Pose la cible : ses pieds, le point qu'elle regarde (le tireur), son axe de glissement
     * (si elle bouge), sa duree en tiques et sa variante.
     */
    public void setup(Vec3 feet, Vec3 facing, Vec3 slide, boolean moving, int life, int variant) {
        this.anchor = feet;
        this.axis = slide;
        this.life = life;
        this.entityData.set(MOVING, moving);
        this.entityData.set(VARIANT, variant);
        float yaw = (float) Math.toDegrees(Math.atan2(-(facing.x - feet.x), facing.z - feet.z));
        this.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (--this.life <= 0) {
            this.discard();
            return;
        }
        if (this.moving()) {
            double s = Math.sin(this.tickCount * 0.12) * RangeRun.SLIDE;
            this.setPos(this.anchor.x + this.axis.x * s, this.anchor.y, this.anchor.z + this.axis.z * s);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved()) {
            return false;
        }
        // un tir (une arme du Morph Gun, une fleche, un coup de joueur) ; le reste ne compte pas
        boolean shot = source.is(GunImpacts.DAMAGE_TYPE) || source.getEntity() instanceof Player;
        if (!shot) {
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                this.discard();
            }
            return false;
        }
        ServerLevel level = (ServerLevel) this.level();
        for (QuestRun run : HavenQuests.runs()) {
            if (run instanceof RangeRun range && this.getTags().contains(run.tag())) {
                range.hit(this);
            }
        }
        double y = this.getY() + this.getBbHeight() * 0.5;
        level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), y, this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.CRIT, this.getX(), y, this.getZ(), 16, 0.5, 0.5, 0.5, 0.25);
        level.playSound(null, this.blockPosition(), civilian() ? SoundEvents.VILLAGER_HURT : SoundEvents.ARMOR_STAND_BREAK,
                SoundSource.PLAYERS, 1.0F, 1.1F);
        this.discard();
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
