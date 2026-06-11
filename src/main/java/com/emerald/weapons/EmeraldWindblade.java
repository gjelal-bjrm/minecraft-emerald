package com.emerald.weapons;

import com.emerald.effects.ModEffects;
import com.emerald.particles.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public class EmeraldWindblade extends SwordItem {

    public EmeraldWindblade(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    private static final Map<UUID, Long> colorEffectCooldowns = new WeakHashMap<>();
    private static final long COOLDOWN_MS = 5000;

    // Zone d'électrification : stocke la position du dernier impact et le timestamp
    // Clé = UUID de l'attaquant, valeur = [x, y, z, timestamp_ms]
    private static final Map<UUID, double[]> electrifiedZones = new WeakHashMap<>();
    private static final long ELECTRIFY_DURATION_MS = 3000; // 3 secondes
    private static final double ELECTRIFY_RADIUS     = 2.0;  // blocs

    public static float getCooldownProgress(UUID uuid) {
        long now = System.currentTimeMillis();
        long lastUse = colorEffectCooldowns.getOrDefault(uuid, 0L);
        long elapsed = now - lastUse;
        if (elapsed >= COOLDOWN_MS) return 0f;
        return 1f - (elapsed / (float) COOLDOWN_MS);
    }

    private static boolean canTriggerColorEffect(LivingEntity attacker) {
        long now = System.currentTimeMillis();
        return colorEffectCooldowns.getOrDefault(attacker.getUUID(), 0L) + COOLDOWN_MS <= now;
    }

    private static void updateColorEffectCooldown(LivingEntity attacker) {
        colorEffectCooldowns.put(attacker.getUUID(), System.currentTimeMillis());
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 22;
    }

    private float applyCrystallineEffects(LivingEntity attacker) {
        float damageMultiplier = 1.0f;
        if (attacker instanceof Player player && player.hasEffect(ModEffects.CRYSTALLINE_AURA)) {
            if (Math.random() < 0.15) {
                damageMultiplier = 1.2f;
            }
            float realDamage = (float) player.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE
            ) * damageMultiplier;
            player.heal(realDamage * 0.05f);
        }
        return damageMultiplier;
    }

    private void procDamageType(LivingEntity target, LivingEntity attacker, ServerLevel serverLevel, Level level) {
        if (canTriggerColorEffect(attacker)) {
            updateColorEffectCooldown(attacker);
            int colorIndex = level.random.nextInt(5);
            Vec3 p = target.position();

            switch (colorIndex) {
                case 0 -> {
                    target.setRemainingFireTicks(4 * 20);
                    serverLevel.sendParticles(ModParticles.CRYSTAL_RED.get(), p.x, p.y + 1, p.z, 8, 0.2, 0.2, 0.2, 0.01);
                }
                case 1 -> {
                    Vec3 knock = p.subtract(attacker.position()).normalize().scale(1.5);
                    target.push(knock.x, 0.4, knock.z);
                    level.playSound(null, target.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 1.0F);
                    serverLevel.sendParticles(ModParticles.CRYSTAL_ORANGE.get(), p.x, p.y + 1, p.z, 8, 0.2, 0.2, 0.2, 0.01);
                }
                case 2 -> {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10 * 20));
                    serverLevel.sendParticles(ModParticles.CRYSTAL_YELLOW.get(), p.x, p.y + 1, p.z, 8, 0.2, 0.2, 0.2, 0.01);
                }
                case 3 -> {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 15 * 20));
                    serverLevel.sendParticles(ModParticles.CRYSTAL_PINK.get(), p.x, p.y + 0.5, p.z, 8, 0.2, 0.2, 0.2, 0.01);
                }
                case 4 -> {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10 * 20, 1));
                    serverLevel.sendParticles(ModParticles.CRYSTAL_GREEN.get(), p.x, p.y + 0.5, p.z, 8, 0.2, 0.2, 0.2, 0.01);
                }
            }
        } else {
            Vec3 pos = target.position();
            for (int i = 0; i < 8; i++) {
                double angle = level.random.nextDouble() * 2 * Math.PI;
                double radius = 0.5 + level.random.nextDouble() * 0.3;
                double dx = Math.cos(angle) * radius;
                double dz = Math.sin(angle) * radius;
                double dy = 0.2 + level.random.nextDouble() * 0.3;

                Vector3f color = switch (i % 6) {
                    case 0 -> new Vector3f(1f, 0f, 0f);
                    case 1 -> new Vector3f(1f, 0.5f, 0f);
                    case 2 -> new Vector3f(1f, 1f, 0f);
                    case 3 -> new Vector3f(0f, 1f, 0f);
                    case 4 -> new Vector3f(0f, 0f, 1f);
                    default -> new Vector3f(0.6f, 0f, 1f);
                };

                serverLevel.sendParticles(
                        new DustParticleOptions(color, 1.3f),
                        pos.x, pos.y + 1.0, pos.z,
                        0, dx, dy, dz, 0.01
                );
            }
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.0F);
            level.playSound(null, attacker.blockPosition(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.9F, 1.2F);
        }
    }

    private void applyCrystallineBuff(LivingEntity attacker, Level level) {
        if (attacker instanceof Player player && Math.random() < 0.08) {
            player.addEffect(new MobEffectInstance(ModEffects.CRYSTALLINE_AURA, 20 * 20));
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
    }

    /*
     * Trace une branche secondaire plus courte depuis un point du rayon principal.
     * Appelée depuis spawnRainbowLightningBeam() à chaque nouveau segment de zigzag.
     *
     * La branche part du point (branchX, branchY, branchZ) et descend sur BRANCH_STEPS
     * étapes avec un angle diagonal aléatoire, en rétrécissant progressivement (size qui
     * diminue de SIZE_START vers SIZE_END) pour donner un aspect effilé.
     */
    private void spawnBranch(ServerLevel serverLevel, Level level,
                             double branchX, double branchY, double branchZ,
                             Vector3f color) {
        final int    BRANCH_STEPS  = 18;
        final float  SIZE_START    = 1.4f;
        final float  SIZE_END      = 0.5f;
        final double STEP_HEIGHT   = 0.35;

        // Direction diagonale aléatoire pour la branche
        double dirX = (level.random.nextDouble() * 2 - 1) * 0.4;
        double dirZ = (level.random.nextDouble() * 2 - 1) * 0.4;

        for (int b = 0; b < BRANCH_STEPS; b++) {
            float t    = b / (float) BRANCH_STEPS;
            float size = SIZE_START + t * (SIZE_END - SIZE_START); // rétrécissement progressif

            double bx = branchX + dirX * b;
            double by = branchY - STEP_HEIGHT * b;
            double bz = branchZ + dirZ * b;

            serverLevel.sendParticles(new DustParticleOptions(color, size),
                    bx, by, bz, 0, 0, 0, 0, 0);
        }
    }

    /*
     * Spawne l'onde de choc au sol : un anneau de particules dont le rayon
     * grandit de 0 à SHOCKWAVE_RADIUS en SHOCKWAVE_RINGS couches concentriques.
     * Chaque anneau est une couleur arc-en-ciel différente et légèrement surélevé
     * pour un effet de "vague" qui monte depuis le sol.
     */
    private void spawnShockwave(ServerLevel serverLevel, double px, double py, double pz,
                                Vector3f[] rainbow) {
        final int    SHOCKWAVE_RINGS    = 6;
        final double SHOCKWAVE_RADIUS   = 3.5;
        final int    POINTS_PER_RING    = 24;

        for (int ring = 1; ring <= SHOCKWAVE_RINGS; ring++) {
            double radius   = SHOCKWAVE_RADIUS * ring / SHOCKWAVE_RINGS;
            double elevation = 0.05 * ring; // légèrement surélevé à chaque anneau
            Vector3f color  = rainbow[ring % rainbow.length];

            for (int p = 0; p < POINTS_PER_RING; p++) {
                double angle = 2 * Math.PI * p / POINTS_PER_RING;
                double rx    = Math.cos(angle) * radius;
                double rz    = Math.sin(angle) * radius;

                serverLevel.sendParticles(new DustParticleOptions(color, 1.0f),
                        px + rx, py + elevation, pz + rz,
                        0, 0, 0, 0, 0);
            }
        }
    }

    /*
     * Spawne le cratère d'impact : ELECTRIC_SPARK éparpillées au sol + quelques
     * FLASH pour le flash d'éblouissement central.
     */
    private void spawnCrater(ServerLevel serverLevel, double px, double py, double pz) {
        // Étincelles électriques dispersées autour du point d'impact
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                px, py + 0.1, pz, 30, 0.6, 0.1, 0.6, 0.15);

        // Flash central éblouissant
        serverLevel.sendParticles(ParticleTypes.FLASH,
                px, py + 0.2, pz, 3, 0.2, 0.1, 0.2, 0.0);

        // Particules cristallines de l'impact
        serverLevel.sendParticles(ModParticles.CRYSTAL_RED.get(),    px, py + 0.5, pz, 10, 0.4, 0.2, 0.4, 0.08);
        serverLevel.sendParticles(ModParticles.CRYSTAL_ORANGE.get(), px, py + 0.5, pz, 10, 0.4, 0.2, 0.4, 0.08);
        serverLevel.sendParticles(ModParticles.CRYSTAL_YELLOW.get(), px, py + 1.0, pz, 10, 0.3, 0.3, 0.3, 0.08);
        serverLevel.sendParticles(ModParticles.CRYSTAL_GREEN.get(),  px, py + 1.0, pz, 10, 0.3, 0.3, 0.3, 0.08);
        serverLevel.sendParticles(ModParticles.CRYSTAL_PINK.get(),   px, py + 1.5, pz, 10, 0.2, 0.4, 0.2, 0.06);
        serverLevel.sendParticles(ModParticles.CRYSTALLINE_FISSURE.get(),
                px, py, pz, 14, 0.3, 0.05, 0.3, 0.02);
    }

    /*
     * Vérifie si des ennemis se trouvent dans la zone d'électrification d'un attaquant
     * et leur applique un mini-choc (2 dégâts + ralentissement court) s'ils y entrent.
     * Appelée à chaque hurtEnemy, pas uniquement lors du proc Thunder.
     *
     * La zone est active ELECTRIFY_DURATION_MS ms après le dernier impact Thunder.
     * Des étincelles sont spawnées sur les ennemis touchés pour le feedback visuel.
     */
    private void tickElectrifiedZone(LivingEntity attacker, ServerLevel serverLevel, Level level) {
        double[] zone = electrifiedZones.get(attacker.getUUID());
        if (zone == null) return;

        long now = System.currentTimeMillis();
        if (now - (long) zone[3] > ELECTRIFY_DURATION_MS) {
            electrifiedZones.remove(attacker.getUUID()); // zone expirée
            return;
        }

        double zx = zone[0], zy = zone[1], zz = zone[2];

        // Particules d'électrification au sol pour montrer que la zone est active
        serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                zx, zy + 0.1, zz, 3, ELECTRIFY_RADIUS * 0.7, 0.05, ELECTRIFY_RADIUS * 0.7, 0.02);

        // Cherche les ennemis dans la zone
        AABB zoneBox = new AABB(
                zx - ELECTRIFY_RADIUS, zy - 0.5, zz - ELECTRIFY_RADIUS,
                zx + ELECTRIFY_RADIUS, zy + 2.5, zz + ELECTRIFY_RADIUS
        );

        List<Entity> nearby = level.getEntities(attacker, zoneBox,
                e -> e instanceof LivingEntity le && le != attacker);

        for (Entity e : nearby) {
            if (e instanceof LivingEntity le) {
                Vec3 ep = le.position();
                double dist = Math.sqrt((ep.x - zx) * (ep.x - zx) + (ep.z - zz) * (ep.z - zz));
                if (dist <= ELECTRIFY_RADIUS) {
                    le.hurt(le.damageSources().magic(), 2.0F);
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2 * 20, 0));

                    // Étincelles sur l'ennemi choqué
                    serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                            ep.x, ep.y + 1.0, ep.z, 8, 0.2, 0.3, 0.2, 0.1);

                    level.playSound(null, le.blockPosition(),
                            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.6F, 1.4F);
                }
            }
        }
    }

    /*
     * Trace le rayon arc-en-ciel principal avec branches secondaires,
     * puis spawne l'onde de choc et le cratère à l'impact.
     * spawnBranch() appelée à chaque changement de segment zigzag (30% de chance)
     * ramifications diagonales effilées depuis le rayon principal
     * spawnShockwave() → anneau concentrique arc-en-ciel au sol
     * spawnCrater() → ELECTRIC_SPARK + FLASH au point d'impact
     */
    private void spawnRainbowLightningBeam(ServerLevel serverLevel, Level level, LivingEntity le) {
        Vec3 pos = le.position();

        final int    STEPS            = 80;
        final double HEIGHT           = 20.0;
        final float  SIZE_LARGE       = 2.0f;
        final float  SIZE_SMALL       = 1.0f;
        final int    COLOR_BAND_SIZE  = 12;
        final int    ZIGZAG_INTERVAL  = 8;
        final double ZIGZAG_AMPLITUDE = 0.18;

        Vector3f[] RAINBOW = {
                new Vector3f(1.0f, 0.0f, 0.0f),
                new Vector3f(1.0f, 0.5f, 0.0f),
                new Vector3f(1.0f, 1.0f, 0.0f),
                new Vector3f(0.0f, 1.0f, 0.0f),
                new Vector3f(0.0f, 0.4f, 1.0f),
                new Vector3f(0.6f, 0.0f, 1.0f)
        };

        double stepHeight = HEIGHT / STEPS;
        double jitterX = 0, jitterZ = 0;

        for (int i = 0; i < STEPS; i++) {
            double dy = pos.y + HEIGHT - (i * stepHeight);

            if (i % ZIGZAG_INTERVAL == 0) {
                jitterX = (level.random.nextDouble() * 2 - 1) * ZIGZAG_AMPLITUDE;
                jitterZ = (level.random.nextDouble() * 2 - 1) * ZIGZAG_AMPLITUDE;

                // 30% de chance de spawner une branche secondaire à ce nœud de zigzag
                if (i > 0 && level.random.nextFloat() < 0.30f) {
                    Vector3f branchColor = RAINBOW[(i / COLOR_BAND_SIZE) % RAINBOW.length];
                    spawnBranch(serverLevel, level,
                            pos.x + jitterX, dy, pos.z + jitterZ,
                            branchColor);
                }
            }

            double cx = pos.x + jitterX;
            double cz = pos.z + jitterZ;

            Vector3f color = RAINBOW[(i / COLOR_BAND_SIZE) % RAINBOW.length];

            serverLevel.sendParticles(new DustParticleOptions(color, SIZE_LARGE),
                    cx, dy, cz, 0, 0, 0, 0, 0);

            double perpX =  jitterZ * 1.5;
            double perpZ = -jitterX * 1.5;
            serverLevel.sendParticles(new DustParticleOptions(color, SIZE_SMALL),
                    cx + perpX, dy, cz + perpZ, 0, 0, 0, 0, 0);
            serverLevel.sendParticles(new DustParticleOptions(color, SIZE_SMALL),
                    cx - perpX, dy, cz - perpZ, 0, 0, 0, 0, 0);
        }

        // Onde de choc concentrique au sol
        spawnShockwave(serverLevel, pos.x, pos.y, pos.z, RAINBOW);

        // Cratère d'impact (ELECTRIC_SPARK + FLASH + cristaux)
        spawnCrater(serverLevel, pos.x, pos.y, pos.z);

        // Éclair blanc natif superposé
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(pos.x, pos.y, pos.z)));
            bolt.setVisualOnly(true);
            serverLevel.addFreshEntity(bolt);
        }
    }

    /*
     * 3% de chance par coup de déclencher Crystalline Thunder.
     * Pitch aléatoire sur LIGHTNING_BOLT_THUNDER (0.8 → 1.1) : chaque frappe
     * sonne différente, évite l'effet "son en boucle" après plusieurs procs rapides
     * Enregistrement de la zone d'électrification dans electrifiedZones après l'impact
     */
    private void summonCrystallineThunder(LivingEntity target, LivingEntity attacker, ServerLevel serverLevel, Level level) {
        if (attacker instanceof Player player && Math.random() < 0.03) { // FIX: 0.93 → 0.03
            Vec3 attackerPos = attacker.position();
            Vec3 lookVec = attacker.getLookAngle();

            List<Entity> targets = level.getEntities(attacker, attacker.getBoundingBox().inflate(6.0), e ->
                    e instanceof LivingEntity le &&
                            le != attacker &&
                            e.position().subtract(attackerPos).normalize().dot(lookVec) > 0.5
            );

            for (Entity e : targets) {
                if (e instanceof LivingEntity le) {

                    spawnRainbowLightningBeam(serverLevel, level, le);

                    le.hurt(le.damageSources().magic(), 15.0F);

                    le.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 15 * 20, 0));
                    le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 15 * 20, 1));
                    le.setRemainingFireTicks(4 * 20);
                    le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15 * 20, 1));
                    le.addEffect(new MobEffectInstance(MobEffects.POISON, 15 * 20));
                    le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10 * 20));

                    // Pitch aléatoire : chaque tonnerre sonne différent (0.8 → 1.1)
                    float thunderPitch = 0.8f + level.random.nextFloat() * 0.3f;
                    serverLevel.playSound(null, le.blockPosition(),
                            SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 2.5F, thunderPitch);

                    serverLevel.playSound(null, le.blockPosition(),
                            SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.8F, 1.1F);

                    level.playSound(null, attacker.blockPosition(),
                            SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2F, 1.3F);

                    // Enregistre la zone d'électrification au point d'impact
                    Vec3 impactPos = le.position();
                    electrifiedZones.put(player.getUUID(), new double[]{
                            impactPos.x, impactPos.y, impactPos.z,
                            System.currentTimeMillis()
                    });
                }
            }
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Level level = attacker.level();
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            applyCrystallineEffects(attacker);
            procDamageType(target, attacker, serverLevel, level);
            applyCrystallineBuff(attacker, level);
            summonCrystallineThunder(target, attacker, serverLevel, level);
            // Vérifie et applique la zone d'électrification active à chaque coup
            tickElectrifiedZone(attacker, serverLevel, level);
        }
        return super.hurtEnemy(stack, target, attacker);
    }
}
