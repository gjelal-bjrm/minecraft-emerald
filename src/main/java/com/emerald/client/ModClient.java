package com.emerald.client;

import com.emerald.particles.ModParticles;
import com.emerald.weapons.EmeraldWindblade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

public class ModClient {

    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CRYSTALLINE_FISSURE.get(), FissureParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CRYSTAL_GREEN.get(), CrystalParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CRYSTAL_ORANGE.get(), CrystalParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CRYSTAL_PINK.get(), CrystalParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CRYSTAL_RED.get(), CrystalParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CRYSTAL_YELLOW.get(), CrystalParticle.Provider::new);
    }

    /*public static void clientTick(Minecraft mc) {
        if (mc.player != null && mc.player.getMainHandItem().getItem() instanceof EmeraldWindblade) {
            ClientLevel level = mc.level;
            if (level != null && level.getGameTime() % 5 == 0) {
                double x = mc.player.getX() + (Math.random() - 0.5);
                double y = mc.player.getY() + 1.2;
                double z = mc.player.getZ() + (Math.random() - 0.5);
                level.addParticle(ModParticles.CRYSTAL_PARTICLE.get(), x, y, z, 0, -0.01, 0);
            }
        }
    }*/

    public static class FissureParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final double baseX, baseY, baseZ;
        private final float angularSpeed;
        private float angle;
        private final double radius;
        private final float verticalOffsetSpeed;

        protected FissureParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet) {
            super(level, x, y+1, z);
            this.sprites = spriteSet;
            this.setSpriteFromAge(spriteSet);
            this.setLifetime(40 + random.nextInt(20)); // durée plus longue

            this.gravity = 0;
            this.friction = 0.9f;
            this.alpha = 0.95f;
            this.quadSize = 0.7f + random.nextFloat() * 0.7f; // taille personnalisable

            // Position d'origine pour les oscillations
            this.baseX = x;
            this.baseY = y+1;
            this.baseZ = z;

            // Valeurs randomisées pour éviter l'uniformité
            this.angle = random.nextFloat() * (float) (2 * Math.PI);
            this.angularSpeed = 0.1f + random.nextFloat() * 0.05f;
            this.radius = 0.05 + random.nextFloat() * 0.05;
            this.verticalOffsetSpeed = 0.05f + random.nextFloat() * 0.02f;
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(this.sprites);

            // Mise à jour de l'angle
            this.angle += this.angularSpeed;

            // Mouvement circulaire aléatoire
            double offsetX = Math.cos(this.angle) * radius;
            double offsetZ = Math.sin(this.angle) * radius;

            // Oscillation verticale en sinus
            double offsetY = Math.sin(age * verticalOffsetSpeed) * 0.1;

            // Application
            this.x = baseX + offsetX;
            this.y = baseY + offsetY;
            this.z = baseZ + offsetZ;
        }

        /*@Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(this.sprites); // déjà fait
            this.alpha = 1.0F;
        }*/

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        /*public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }*/

        public static class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                           double dx, double dy, double dz) {
                return new FissureParticle(level, x, y, z, this.sprites);
            }
        }
    }

    public static class CrystalParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float initialSize;
        private final float scaleOscillationSpeed;
        private final float rotationSpeed;

        protected CrystalParticle(ClientLevel level, double x, double y, double z, SpriteSet spriteSet) {
            super(level, x, y, z);
            this.sprites = spriteSet;

            // Apparence et comportement de base
            this.setLifetime(40 + level.random.nextInt(20));
            this.setSpriteFromAge(spriteSet);
            this.gravity = 0.0f; // Pas de gravité pour lévitation
            this.friction = 0.9f;
            this.alpha = 1.0f;

            // Taille de base légèrement aléatoire
            this.initialSize = 0.15f + level.random.nextFloat() * 0.2f;
            this.quadSize = initialSize;

            // Variation douce de taille
            this.scaleOscillationSpeed = 0.1f + level.random.nextFloat() * 0.15f;

            // Rotation aléatoire adoucie
            this.rotationSpeed = (level.random.nextFloat() - 0.5f) * 4f; // ±2° par tick

            // Léger mouvement vertical fluide
            this.yd = 0.01 + level.random.nextGaussian() * 0.003;
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(this.sprites);

            // Disparition progressive
            this.alpha -= 0.015f;
            if (this.alpha <= 0f) this.remove();

            // Taille oscillante subtile (pulsation)
            float ageRatio = (float) age / lifetime;
            this.quadSize = initialSize * (0.8f + 0.2f * (float) Math.sin(ageRatio * Math.PI * 4));

            // Rotation douce
            this.roll += Math.toRadians(rotationSpeed * 0.2f);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        public static class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                           double dx, double dy, double dz) {
                return new CrystalParticle(level, x, y, z, this.sprites);
            }
        }
    }


}
