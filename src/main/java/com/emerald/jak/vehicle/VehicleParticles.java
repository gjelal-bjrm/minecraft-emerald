package com.emerald.jak.vehicle;

import com.emerald.particles.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * L'eclat d'un choc de vehicule (client) : une particule NEUVE, a ce seul usage.
 *
 * Texture : tools/vehicle_particles.py. Un eclat de tole orange-blanc qui retombe
 * et s'eteint en une demi-seconde ; le serveur en envoie de quatre a vingt selon la
 * force du choc (VehicleImpacts.watch).
 */
public final class VehicleParticles {

    private VehicleParticles() {
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.JAK_VEHICLE_SPARK.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Spark(level, x, y, z, dx, dy, dz, sprites));
    }

    static final class Spark extends TextureSheetParticle {

        private final float size0;

        Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.friction = 0.86F;
            this.gravity = 0.6F;
            this.hasPhysics = true;
            this.lifetime = 6 + level.random.nextInt(6);
            this.size0 = 0.12F + level.random.nextFloat() * 0.08F;
            this.quadSize = this.size0;
            this.setColor(1.0F, 0.86F, 0.62F);
            this.roll = level.random.nextFloat() * (float) Math.PI * 2.0F;
            this.oRoll = this.roll;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = Math.min(1.0F, this.age / (float) this.lifetime);
            this.setColor(1.0F, 0.86F - 0.4F * t, 0.62F - 0.5F * t);
            this.alpha = 1.0F - t * t;
            this.quadSize = this.size0 * (1.0F - 0.4F * t);
            this.oRoll = this.roll;
            this.roll += 0.4F;
        }

        @Override
        public int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }
}
