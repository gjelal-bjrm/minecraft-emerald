package com.emerald.weapons;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.Artifacts;
import com.emerald.effects.ModEffects;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

import javax.annotation.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Effets d'impact des fleches tirees par l'Arcencium Bow.
 * Le stade de charge est lu dans les donnees persistantes de la fleche
 * (pose par {@link ArcenciumBowItem}), ce qui evite une entite custom.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class ArcenciumBowEvents {

    private static final String TAG_DONE = "ArcenciumImpactDone";
    private static final double BURST_RADIUS = 5.0;
    private static final int SHARDS = 3;
    private static final float SHARD_DAMAGE = 4.0F;
    private static final double HOMING_RANGE = 12.0;
    private static final double HOMING_STRENGTH = 0.12;

    private static final int MARK_TICKS = 160;          // 8 s

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.level() instanceof ServerLevel level)) return;
        CompoundTag data = arrow.getPersistentData();
        if (!data.contains(ArcenciumBowItem.TAG_STAGE) || data.getBoolean(TAG_DONE)) return;
        HitResult hit = event.getRayTraceResult();
        if (hit.getType() == HitResult.Type.MISS) return;
        data.putBoolean(TAG_DONE, true);

        int stage = data.getInt(ArcenciumBowItem.TAG_STAGE);
        Entity owner = arrow.getOwner();
        LivingEntity target = (hit instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le) ? le : null;
        Vec3 pos = hit.getLocation();

        if (target != null) {
            applyStageEffects(level, arrow, target, stage);
        }
        if (stage >= ArcenciumBowItem.MAX_STAGE) {
            prismaticBurst(level, arrow, pos, target, owner);
        }
    }

    /** Effets cumulatifs des crans 2 a 4 sur la cible touchee directement. */
    private static void applyStageEffects(ServerLevel level, AbstractArrow arrow,
                                          LivingEntity target, int stage) {
        Vec3 p = target.position();
        // cran 1 (rouge) : la fleche brule deja, vanilla transmet le feu a l'impact
        if (stage >= 2) {                                       // orange : repulsion
            Vec3 dir = arrow.getDeltaMovement();
            if (dir.lengthSqr() > 1.0E-4) {
                dir = dir.normalize();
                target.knockback(1.4, -dir.x, -dir.z);
            }
            level.sendParticles(ModParticles.CRYSTAL_ORANGE.get(), p.x, p.y + 1, p.z, 8, 0.2, 0.2, 0.2, 0.01);
            level.playSound(null, target.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.1F);
        }
        if (stage >= 3) {                                       // bleu : gel temporaire
            freeze(level, target);
        }
        if (stage >= 4) {                                       // rose : poison
            target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            level.sendParticles(ModParticles.CRYSTAL_PINK.get(), p.x, p.y + 0.6, p.z, 8, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** Gel : givre visuel (ticksFrozen) + quasi-immobilisation 3 s. */
    private static void freeze(ServerLevel level, LivingEntity target) {
        Vec3 p = target.position();
        target.setTicksFrozen(Math.max(target.getTicksFrozen(), 280));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5, false, true));
        level.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y + 1, p.z, 18, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    /**
     * Fleche Prismatique (cran 5) : marque la cible, puis 3 eclats frappent
     * jusqu'a 3 ennemis autour du point d'impact. L'eclat 0 porte toujours un
     * effet cristal (colore) ; les deux autres une fois sur deux.
     */
    private static void prismaticBurst(ServerLevel level, AbstractArrow arrow, Vec3 pos,
                                       LivingEntity mainTarget, Entity owner) {
        RandomSource rng = level.getRandom();
        if (mainTarget != null) {
            mark(level, mainTarget, arrow.getOwner());
        }
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos, pos).inflate(BURST_RADIUS),
                e -> e != owner && e != mainTarget && e.isAlive() && !e.isSpectator());
        candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(pos)));

        level.sendParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 14, 0.3, 0.3, 0.3, 0.06);
        BlockPos bp = BlockPos.containing(pos);
        level.playSound(null, bp, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5F, 0.9F);
        level.playSound(null, bp, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.6F, 1.5F);

        for (int i = 0; i < SHARDS; i++) {
            int effect = (i == 0 || rng.nextBoolean()) ? rng.nextInt(5) : -1;
            LivingEntity victim = i < candidates.size() ? candidates.get(i) : null;
            if (victim == null) {
                // pas de cible : l'eclat file dans le vide, trace visuelle seulement
                Vec3 dir = new Vec3(rng.nextDouble() - 0.5, rng.nextDouble() * 0.6,
                        rng.nextDouble() - 0.5).normalize();
                trail(level, pos, pos.add(dir.scale(3.0)), effect);
                continue;
            }
            Vec3 vp = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
            trail(level, pos, vp, effect);
            DamageSource src = owner != null
                    ? level.damageSources().indirectMagic(arrow, owner)
                    : level.damageSources().magic();
            victim.hurt(src, SHARD_DAMAGE);
            mark(level, victim, arrow.getOwner());
            applyShardEffect(level, victim, effect, pos);
        }
    }

    /** Les 5 effets possibles d'un eclat (meme esprit que les procs de l'epee). */
    private static void applyShardEffect(ServerLevel level, LivingEntity victim, int effect, Vec3 from) {
        Vec3 p = victim.position();
        switch (effect) {
            case 0 -> {                                                     // rouge : feu
                victim.igniteForSeconds(4);
                level.sendParticles(ModParticles.CRYSTAL_RED.get(), p.x, p.y + 1, p.z, 8, 0.2, 0.2, 0.2, 0.01);
            }
            case 1 -> {                                                     // orange : repulsion
                Vec3 away = p.subtract(from);
                victim.knockback(1.2, -away.x, -away.z);
                level.sendParticles(ModParticles.CRYSTAL_ORANGE.get(), p.x, p.y + 1, p.z, 8, 0.2, 0.2, 0.2, 0.01);
            }
            case 2 -> freeze(level, victim);                                // bleu : gel
            case 3 -> {                                                     // rose : poison
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                level.sendParticles(ModParticles.CRYSTAL_PINK.get(), p.x, p.y + 0.6, p.z, 8, 0.2, 0.2, 0.2, 0.01);
            }
            case 4 -> {                                                     // vert : drain
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                level.sendParticles(ModParticles.CRYSTAL_GREEN.get(), p.x, p.y + 0.6, p.z, 8, 0.2, 0.2, 0.2, 0.01);
            }
            default -> { }                                                  // eclat neutre : degats seuls
        }
    }

    /**
     * Fleche Tracante : la fleche corrige doucement sa course.
     *
     * L'inflexion est volontairement faible et ne s'applique qu'a une cible
     * DEVANT la fleche : une correction forte transformerait l'arc en arme
     * automatique, et viser cesserait d'avoir un sens.
     */
    @SubscribeEvent
    public static void onArrowTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)
                || arrow.level().isClientSide
                || !arrow.getPersistentData().getBoolean(ArcenciumBowItem.TAG_HOMING)
                || arrow.onGround()) {
            return;
        }
        Vec3 motion = arrow.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-4) {
            return;
        }
        Vec3 heading = motion.normalize();
        LivingEntity best = null;
        double bestScore = 0.55;                       // cone d'environ 57 degres
        for (LivingEntity candidate : arrow.level().getEntitiesOfClass(LivingEntity.class,
                arrow.getBoundingBox().inflate(HOMING_RANGE),
                e -> e.isAlive() && e != arrow.getOwner())) {
            Vec3 toward = candidate.getEyePosition().subtract(arrow.position()).normalize();
            double score = heading.dot(toward);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            return;
        }
        Vec3 toward = best.getEyePosition().subtract(arrow.position()).normalize();
        Vec3 steered = heading.add(toward.subtract(heading).scale(HOMING_STRENGTH)).normalize();
        arrow.setDeltaMovement(steered.scale(motion.length()));
    }

    /**
     * Marque Prismatique : 8 s, visible (glowing) -- les procs de l'epee doublent.
     *
     * L'artefact Marque Prolongee triple cette duree. On lit l'arc du tireur
     * plutot que la fleche : une fleche ne conserve pas l'equipement d'origine,
     * et le tireur tient encore son arc a l'impact dans l'immense majorite des cas.
     */
    private static void mark(ServerLevel level, LivingEntity target, @Nullable Entity shooter) {
        int ticks = MARK_TICKS;
        if (shooter instanceof LivingEntity living
                && Artifacts.has(living.getMainHandItem(), Artifact.MARQUE_PROLONGEE)) {
            ticks *= 3;
        }
        target.addEffect(new MobEffectInstance(ModEffects.PRISMATIC_MARK, ticks, 0, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, false));
        Vec3 p = target.position();
        level.sendParticles(ParticleTypes.ENCHANT, p.x, p.y + 1, p.z, 12, 0.3, 0.5, 0.3, 0.5);
    }

    /** Trainee de particules entre deux points, couleur de l'effet porte. */
    private static void trail(ServerLevel level, Vec3 from, Vec3 to, int effect) {
        ParticleOptions type = switch (effect) {
            case 0 -> ModParticles.CRYSTAL_RED.get();
            case 1 -> ModParticles.CRYSTAL_ORANGE.get();
            case 2 -> ParticleTypes.SNOWFLAKE;
            case 3 -> ModParticles.CRYSTAL_PINK.get();
            case 4 -> ModParticles.CRYSTAL_GREEN.get();
            default -> ParticleTypes.END_ROD;
        };
        Vec3 d = to.subtract(from);
        int steps = Math.max(4, (int) (d.length() * 3));
        for (int s = 0; s <= steps; s++) {
            Vec3 q = from.add(d.scale(s / (double) steps));
            level.sendParticles(type, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }
}
