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
        event.registerSpriteSet(ModParticles.JAK_VEHICLE_SMOKE.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Smoke(level, x, y, z, dx, dy, dz, sprites, false));
        event.registerSpriteSet(ModParticles.JAK_VEHICLE_FIRE.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Smoke(level, x, y, z, dx, dy, dz, sprites, true));
    }

    /**
     * La fumee d'un vehicule abime, ou son feu (cahier §98) : une volute d'un demi-metre a
     * trois quarts (vehicle-part.gc), qui monte en colonne, grandit a peine et s'efface en un
     * peu plus d'une seconde, a demi transparente -- opaque et grossissant de trois et demi pour
     * cent par tique, elle faisait un gros nuage de paves sur l'arriere de la voiture (photos).
     * Sa teinte vient de VehicleDamageClient. Le feu est la flamme du jeu, en grand : elle
     * s'allume blanche, passe a l'orange puis au noir, retrecit, et luit dans sa premiere moitie.
     */
    static final class Smoke extends TextureSheetParticle {

        /** A demi transparente : la voiture se devine derriere. */
        private static final float SMOKE_ALPHA = 0.55F;

        private final SpriteSet sprites;
        private final boolean fire;

        Smoke(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
              boolean fire) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.fire = fire;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.friction = 0.96F;
            this.gravity = fire ? -0.02F : -0.01F;
            this.hasPhysics = false;
            this.lifetime = fire ? 8 + level.random.nextInt(7) : 26 + level.random.nextInt(11);
            this.quadSize = (fire ? 0.5F : 0.4F) + level.random.nextFloat() * 0.2F;
            this.alpha = fire ? 0.95F : SMOKE_ALPHA;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) {
                return;
            }
            this.setSpriteFromAge(this.sprites);
            float t = this.age / (float) this.lifetime;
            this.quadSize *= this.fire ? 0.97F : 1.012F;
            if (this.fire) {
                // la flamme, puis l'orange, puis la fumee noire (vehicle-part.gc, part 925)
                float g = t < 0.4F ? 1.0F - 1.1F * t : 0.56F - 0.8F * (t - 0.4F);
                float b = t < 0.4F ? 1.0F - 1.9F * t : 0.24F - 0.3F * (t - 0.4F);
                float r = t < 0.6F ? 1.0F : 1.0F - 1.9F * (t - 0.6F);
                this.setColor(Math.max(0.12F, r), Math.max(0.1F, g), Math.max(0.08F, b));
                this.alpha = 0.95F * (1.0F - t * t);
            } else {
                this.alpha = SMOKE_ALPHA * (1.0F - t * t);
            }
        }

        @Override
        public int getLightColor(float partialTick) {
            return this.fire && this.age < this.lifetime / 2 ? 0xF000F0 : super.getLightColor(partialTick);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
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
