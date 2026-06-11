package com.emerald.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/*
 * KG Deathbot — robot militaire hostile gardant Haven City.
 *
 * Inspiré des KG Death Bots de Jak 3 :
 *   - Blindé : résistance aux dégâts physiques (70% de réduction)
 *   - Attaque au corps à corps lourde (8 dégâts) + knockback important
 *   - Scanne la zone toutes les 3 secondes : révèle les joueurs en furtivité
 *     (applique Glowing au joueur proche)
 *   - À la mort : petite explosion de particules métalliques + son
 *   - Immunisé au feu et aux chutes
 *
 * Variantes (NBT "KGVariant") :
 *   0 = Standard  — stats de base
 *   1 = Elite     — +50% PV, dégâts +3, scan plus fréquent
 *   2 = Commandant — comme Elite + aura de buff pour les KG proches
 */
public class KGDeathbotEntity extends Monster {

    private static final String NBT_VARIANT = "KGVariant";
    private static final int SCAN_COOLDOWN_TICKS = 60; // 3 secondes

    private int variant = 0;
    private int scanCooldown = 0;

    public KGDeathbotEntity(EntityType<? extends KGDeathbotEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH,      40.0)
                .add(Attributes.ARMOR,           10.0) // blindage
                .add(Attributes.ARMOR_TOUGHNESS,  4.0)
                .add(Attributes.MOVEMENT_SPEED,   0.22)
                .add(Attributes.ATTACK_DAMAGE,    8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE,    24.0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
                this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(
                this, WastelanderEntity.class, true));
    }

    // -------------------------------------------------------------------------
    // Tick — scan + aura commandant
    // -------------------------------------------------------------------------
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        // Particules d'yeux rouges (LED du robot)
        if (this.level() instanceof ServerLevel sl && this.tickCount % 10 == 0) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getEyeY(), this.getZ(),
                    2, 0.1, 0.05, 0.1, 0.02);
        }

        // Scan radar
        if (scanCooldown > 0) {
            scanCooldown--;
        } else {
            performScan();
            scanCooldown = (variant == 1 || variant == 2)
                    ? SCAN_COOLDOWN_TICKS / 2  // Elite : scan 2× plus vite
                    : SCAN_COOLDOWN_TICKS;
        }

        // Aura commandant : buff les KG Deathbots dans un rayon de 10 blocs
        if (variant == 2 && this.tickCount % 40 == 0) {
            applyCommanderAura();
        }
    }

    /*
     * Scan radar : détecte les joueurs dans un rayon de 12 blocs (16 pour Elite/Commandant)
     * et leur applique l'effet Glowing 3 secondes — les révèle même derrière les murs.
     */
    private void performScan() {
        double scanRadius = (variant >= 1) ? 16.0 : 12.0;
        java.util.List<Player> nearby = this.level().getEntitiesOfClass(
                Player.class,
                this.getBoundingBox().inflate(scanRadius)
        );

        for (Player p : nearby) {
            p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 3 * 20, 0, false, false));
        }

        // Son de scan (bip électronique)
        this.level().playSound(null, this.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE,
                0.4F, 1.8F);
    }

    /*
     * Aura commandant : applique Force 1 et Résistance 1 (5 secondes) aux
     * autres KG Deathbots dans un rayon de 10 blocs.
     */
    private void applyCommanderAura() {
        java.util.List<KGDeathbotEntity> allies = this.level().getEntitiesOfClass(
                KGDeathbotEntity.class,
                this.getBoundingBox().inflate(10.0),
                e -> e != this
        );

        for (KGDeathbotEntity ally : allies) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,    5 * 20, 0, false, false));
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5 * 20, 0, false, false));
        }
    }

    // -------------------------------------------------------------------------
    // Mort — explosion de débris métalliques
    // -------------------------------------------------------------------------
    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel sl) {
            // Explosion de particules métalliques
            sl.sendParticles(ParticleTypes.EXPLOSION,
                    this.getX(), this.getY() + 1, this.getZ(),
                    1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    this.getX(), this.getY() + 1, this.getZ(),
                    30, 0.5, 0.5, 0.5, 0.2);
            sl.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY() + 1, this.getZ(),
                    20, 0.3, 0.3, 0.3, 0.05);

            sl.playSound(null, this.blockPosition(),
                    SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.3F);
        }
    }

    // -------------------------------------------------------------------------
    // Résistances — immunité feu + chutes
    // -------------------------------------------------------------------------
    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        // Pas de dégâts de chute pour un robot blindé
    }

    // -------------------------------------------------------------------------
    // Knockback sur attaque
    // -------------------------------------------------------------------------
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result && target instanceof LivingEntity le) {
            // Knockback fort vers l'arrière
            Vec3 knockDir = le.position().subtract(this.position()).normalize().scale(1.8);
            le.push(knockDir.x, 0.5, knockDir.z);

            // Elite/Commandant : applique aussi ralentissement
            if (variant >= 1) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3 * 20, 1));
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(NBT_VARIANT, variant);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt(NBT_VARIANT));
    }

    public void setVariant(int variant) {
        this.variant = variant;
        // Ajuste les stats selon la variante
        if (variant == 1 || variant == 2) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0);
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(11.0);
            this.setHealth(60.0f);
        }
    }

    public int getVariant() {
        return variant;
    }

    // -------------------------------------------------------------------------
    // Sons
    // -------------------------------------------------------------------------
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

}
