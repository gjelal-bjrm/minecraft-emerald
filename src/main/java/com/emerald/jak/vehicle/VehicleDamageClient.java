package com.emerald.jak.vehicle;

import com.emerald.particles.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * La fumee et le feu d'un vehicule abime (cahier §98), cote client : ce que montre sa sante,
 * comme dans Jak 3 (vehicle-effects.gc:238-319).
 *
 * De 0,75 a 0,5 (MOYEN), des bouffees blanches de temps en temps ; de 0,5 a 0,25 (MAL), une
 * fumee grise continue ; sous 0,25, le feu, une fumee presque noire et, parfois, des etincelles
 * electriques qui gresillent. La fumee noircit avec les degats. L'epave brule et fume noir
 * jusqu'a disparaitre. Deux points fument, ceux du jeu : a l'arriere d'une voiture, a l'avant
 * d'une moto.
 */
public final class VehicleDamageClient {

    /** Les deux points qui fument, repere du modele (vehicle-effects.gc:245-252). */
    private static final double[][] CAR_POINTS = {{0.75, 0.0, -3.4}, {-0.75, 0.0, -3.4}};
    private static final double[][] BIKE_POINTS = {{0.4, -0.5, 1.8}, {-0.4, -0.5, 1.8}};
    /** Au-dela, on ne fait rien fumer : on ne le verrait pas. */
    private static final double RANGE = 96.0;

    private VehicleDamageClient() {
    }

    /** Une tique de fumee et de feu, pour un vehicule abime ou detruit (JakVehicleEntity.tick). */
    public static void effects(JakVehicleEntity car) {
        float health = car.health();
        boolean wreck = car.wrecked() || health <= 0.0F;
        if (health >= 0.75F && !wreck) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null
                || car.distanceToSqr(mc.getCameraEntity()) > RANGE * RANGE) {
            return;
        }
        RandomSource random = car.getRandom();
        double[][] points = car.spec().isBike() ? BIKE_POINTS : CAR_POINTS;
        float yaw = car.getYRot() * Mth.DEG_TO_RAD;
        for (double[] point : points) {
            Vec3 at = car.position().add(new Vec3(point[0], point[1], point[2]).yRot(-yaw));
            // la fumee d'abord, le feu ensuite : les particules se dessinent dans l'ordre ou elles
            // naissent, et le feu doit passer devant la fumee
            if (wreck) {
                smoke(mc, at, random, 0.06F, 1.3F);
                fire(mc, at, random);
            } else if (health < 0.25F) {
                smoke(mc, at, random, grey(health), 1.1F);
                fire(mc, at, random);
                fire(mc, at, random);
                if (random.nextFloat() < 0.015F) {
                    zap(mc, at, random);
                }
            } else if (health < 0.5F) {
                smoke(mc, at, random, grey(health), 1.0F);
            } else if (random.nextFloat() < 0.15F) {
                int puffs = 1 + random.nextInt(4);
                for (int i = 0; i < puffs; i++) {
                    smoke(mc, at, random, grey(health), 0.8F);
                }
            }
        }
    }

    /** La fumee noircit avec les degats : presque blanche a trois quarts, noire a zero. */
    private static float grey(float health) {
        return 0.08F + 0.8F * Mth.clamp(health / 0.75F, 0.0F, 1.0F);
    }

    private static void smoke(Minecraft mc, Vec3 at, RandomSource random, float grey, float size) {
        Particle particle = mc.particleEngine.createParticle(ModParticles.JAK_VEHICLE_SMOKE.get(),
                at.x + (random.nextDouble() - 0.5) * 0.4, at.y + random.nextDouble() * 0.2,
                at.z + (random.nextDouble() - 0.5) * 0.4,
                (random.nextDouble() - 0.5) * 0.03, 0.1 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.03);
        if (particle != null) {
            // un gris a peine froid : sous le soleil de Complementary, un gris neutre tirait sur le brun
            particle.setColor(grey, grey, Math.min(1.0F, grey + 0.04F));
            particle.scale(size);
        }
    }

    private static void fire(Minecraft mc, Vec3 at, RandomSource random) {
        mc.particleEngine.createParticle(ModParticles.JAK_VEHICLE_FIRE.get(),
                at.x + (random.nextDouble() - 0.5) * 0.5, at.y + random.nextDouble() * 0.2,
                at.z + (random.nextDouble() - 0.5) * 0.5,
                (random.nextDouble() - 0.5) * 0.04, 0.08 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.04);
    }

    /** Des etincelles electriques qui gresillent (vehicle-effects.gc:300-310, « damage-zaps »). */
    private static void zap(Minecraft mc, Vec3 at, RandomSource random) {
        for (int i = 0; i < 6; i++) {
            mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + (random.nextDouble() - 0.5) * 0.8,
                    at.y + random.nextDouble() * 0.6, at.z + (random.nextDouble() - 0.5) * 0.8,
                    (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
        }
        mc.level.playLocalSound(at.x, at.y, at.z, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.NEUTRAL,
                0.4F, 1.6F + random.nextFloat() * 0.3F, false);
    }
}
