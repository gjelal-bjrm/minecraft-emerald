package com.emerald.jak.gun;

import com.emerald.particles.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * Les particules du Morph Gun : neuf types NEUFS, un par usage, qu'aucun autre
 * systeme du mod n'emploie (ni le sceptre, ni l'arc, ni les meteos, ni les
 * plantes). Textures blanches ou grises dessinees par tools/gun_particles.py ; la
 * couleur vient d'ici. Toutes luisent (pleine lumiere) et se melangent en
 * transparence.
 *
 *  - gun_scatter_pellet : les plombs rouge-orange du Scatter Gun. Avec une vitesse,
 *    un plomb qui file en 3 tiques jusqu'au bout de la sonde ; sans, un point de
 *    trainee de 2 tiques ;
 *  - gun_scatter_flash  : l'eclair orange a la bouche du canon ;
 *  - gun_blaster_bolt   : le trait jaune du Blaster, semé sur le segment de chaque tique ;
 *  - gun_blaster_spark  : les eclats jaunes de son impact (emis par le serveur) ;
 *  - gun_vulcan_tracer  : la trainee cyan de la balle de la Vulcan Fury (tete mobile, points fixes) ;
 *  - gun_vulcan_spark   : ses eclats cyan a l'impact ;
 *  - gun_peace_mote     : les motes violettes de la boule du Peace Maker (charge, vol, explosion) ;
 *  - gun_peace_blast    : l'anneau violet de l'explosion, qui grandit jusqu'a 5 blocs ;
 *  - gun_eco_glint      : l'etincelle d'une munition d'eco, a la couleur de sa famille
 *    (famille dans la vitesse x, montee dans la vitesse y).
 * Huit de plus pour les ameliorations rouges et jaunes :
 *  - gun_wave_charge    : les braises de la charge du Wave Concussor, au canon (emises par le serveur) ;
 *  - gun_wave_dust      : les braises du front de son onde, qui montent et retombent ;
 *  - gun_plasmite_trail : la trainee de la grenade du Plasmite RPG, et les eclats de son souffle ;
 *  - gun_plasmite_blast : la boule de feu de son explosion, qui grandit jusqu'a 9 blocs ;
 *  - gun_reflexor_bolt  : le trait pale du Beam Reflexor, seme sur sa ligne brisee ;
 *  - gun_reflexor_spark : l'eclat de ses rebonds ;
 *  - gun_gyro_tracer    : le trait dore des tirs de la soucoupe du Gyro Burster ;
 *  - gun_gyro_spark     : leurs eclats, et les feux de la soucoupe.
 */
public final class GunParticles {

    private static final int BRIGHT = 0xF000F0;

    private GunParticles() {
    }

    public static void register(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GUN_SCATTER_PELLET.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Pellet(level, x, y, z, dx, dy, dz, sprites));
        event.registerSpriteSet(ModParticles.GUN_SCATTER_FLASH.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Flash(level, x, y, z, sprites));
        event.registerSpriteSet(ModParticles.GUN_BLASTER_BOLT.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Bolt(level, x, y, z, sprites));
        event.registerSpriteSet(ModParticles.GUN_BLASTER_SPARK.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Spark(level, x, y, z, dx, dy, dz, sprites,
                        1.0F, 0.85F, 0.3F));
        event.registerSpriteSet(ModParticles.GUN_VULCAN_TRACER.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Tracer(level, x, y, z, dx, dy, dz, sprites));
        event.registerSpriteSet(ModParticles.GUN_VULCAN_SPARK.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Spark(level, x, y, z, dx, dy, dz, sprites,
                        0.55F, 0.9F, 1.0F));
        event.registerSpriteSet(ModParticles.GUN_PEACE_MOTE.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Mote(level, x, y, z, dx, dy, dz, sprites));
        event.registerSpriteSet(ModParticles.GUN_PEACE_BLAST.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Blast(level, x, y, z, sprites));
        event.registerSpriteSet(ModParticles.GUN_ECO_GLINT.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Glint(level, x, y, z, dx, dy, sprites));
        event.registerSpriteSet(ModParticles.GUN_WAVE_CHARGE.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Ember(level, x, y, z, dx, dy, dz, sprites, 4, 0.2F, false));
        event.registerSpriteSet(ModParticles.GUN_WAVE_DUST.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Ember(level, x, y, z, dx, dy, dz, sprites, 9, 0.16F, true));
        event.registerSpriteSet(ModParticles.GUN_PLASMITE_TRAIL.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Ember(level, x, y, z, dx, dy, dz, sprites, 7, 0.26F, false));
        event.registerSpriteSet(ModParticles.GUN_PLASMITE_BLAST.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Fireball(level, x, y, z, sprites));
        event.registerSpriteSet(ModParticles.GUN_REFLEXOR_BOLT.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Beam(level, x, y, z, sprites));
        event.registerSpriteSet(ModParticles.GUN_REFLEXOR_SPARK.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Spark(level, x, y, z, dx, dy, dz, sprites,
                        1.0F, 0.97F, 0.7F));
        event.registerSpriteSet(ModParticles.GUN_GYRO_TRACER.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Dart(level, x, y, z, dx, dy, dz, sprites));
        event.registerSpriteSet(ModParticles.GUN_GYRO_SPARK.get(),
                sprites -> (type, level, x, y, z, dx, dy, dz) -> new Spark(level, x, y, z, dx, dy, dz, sprites,
                        1.0F, 0.75F, 0.2F));
    }

    /** La base : sans physique ni frottement, pleine lumiere, transparente. */
    abstract static class Glow extends TextureSheetParticle {
        protected final float size0;

        Glow(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
             int lifetime, float size) {
            super(level, x, y, z);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.friction = 1.0F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.lifetime = Math.max(1, lifetime);
            this.size0 = size;
            this.quadSize = size;
            this.pickSprite(sprites);
        }

        protected float progress() {
            return Math.min(1.0F, this.age / (float) this.lifetime);
        }

        @Override
        public int getLightColor(float partialTick) {
            return BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    static final class Pellet extends Glow {
        Pellet(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz, sprites, dx * dx + dy * dy + dz * dz > 1.0e-8 ? 3 : 2,
                    dx * dx + dy * dy + dz * dz > 1.0e-8 ? 0.18F : 0.09F);
            this.setColor(1.0F, 0.55F, 0.18F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.setColor(1.0F, 0.55F - 0.3F * t, 0.18F - 0.1F * t);
            this.alpha = 1.0F - 0.7F * t;
            this.quadSize = this.size0 * (1.0F - 0.4F * t);
        }
    }

    static final class Flash extends Glow {
        Flash(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0.0, 0.0, 0.0, sprites, 3, 0.4F);
            this.setColor(1.0F, 0.6F, 0.25F);
            this.roll = level.random.nextFloat() * (float) Math.PI * 2.0F;
            this.oRoll = this.roll;
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.quadSize = this.size0 * (1.0F + 0.8F * t);
            this.alpha = 1.0F - t;
        }
    }

    static final class Bolt extends Glow {
        Bolt(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0.0, 0.0, 0.0, sprites, 2, 0.22F);
            this.setColor(1.0F, 0.93F, 0.45F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.quadSize = this.size0 * (1.0F - 0.6F * t);
            this.alpha = 1.0F - 0.5F * t;
            this.setColor(1.0F, 0.93F - 0.25F * t, 0.45F - 0.3F * t);
        }
    }

    static final class Spark extends Glow {
        Spark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
              float r, float g, float b) {
            super(level, x, y, z, dx, dy, dz, sprites, 5 + level.random.nextInt(4), 0.09F + level.random.nextFloat() * 0.04F);
            this.friction = 0.82F;
            this.gravity = 0.3F;
            this.setColor(r, g, b);
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = 1.0F - progress();
        }
    }

    static final class Tracer extends Glow {
        Tracer(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz, sprites, 2, dx * dx + dy * dy + dz * dz > 1.0e-8 ? 0.15F : 0.11F);
            this.setColor(0.2F, 0.75F, 1.0F);
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = 1.0F - 0.6F * progress();
        }
    }

    static final class Mote extends Glow {
        Mote(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz, sprites, 10 + level.random.nextInt(5), 0.18F);
            this.friction = 0.88F;
            this.setColor(0.62F, 0.32F, 1.0F);
            this.roll = level.random.nextFloat() * (float) Math.PI * 2.0F;
            this.oRoll = this.roll;
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.oRoll = this.roll;
            this.roll += 0.25F;
            this.quadSize = this.size0 * (1.0F - t);
            this.setColor(0.62F - 0.35F * t, 0.32F - 0.25F * t, 1.0F - 0.45F * t);
        }
    }

    static final class Blast extends Glow {
        Blast(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0.0, 0.0, 0.0, sprites, 8, 0.5F);
            this.setColor(0.75F, 0.5F, 1.0F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.quadSize = 0.5F + 4.5F * t;
            this.alpha = 1.0F - t * t;
        }
    }

    /**
     * Une braise rouge : la charge du Wave Concussor (au canon), le front de son onde
     * (elle monte et retombe), la trainee et les eclats de la grenade du Plasmite RPG.
     * Trois types de particule, une seule allure : du rouge-orange qui vire au sombre.
     */
    static final class Ember extends Glow {
        Ember(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
              int life, float size, boolean falls) {
            super(level, x, y, z, dx, dy, dz, sprites, life + level.random.nextInt(3), size * (0.8F + level.random.nextFloat() * 0.4F));
            this.friction = 0.9F;
            this.gravity = falls ? 0.35F : 0.0F;
            this.setColor(1.0F, 0.45F, 0.12F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.setColor(1.0F - 0.3F * t, 0.45F - 0.35F * t, 0.12F - 0.1F * t);
            this.alpha = 1.0F - t;
            this.quadSize = this.size0 * (1.0F - 0.5F * t);
        }
    }

    /** La boule de feu du Plasmite RPG : un anneau rouge qui grandit jusqu'a 9 blocs. */
    static final class Fireball extends Glow {
        Fireball(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0.0, 0.0, 0.0, sprites, 10, 1.0F);
            this.setColor(1.0F, 0.4F, 0.1F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.quadSize = 1.0F + 8.0F * (float) Math.sqrt(t);
            this.setColor(1.0F, 0.4F + 0.3F * (1.0F - t), 0.1F + 0.3F * (1.0F - t));
            this.alpha = 1.0F - t * t;
        }
    }

    /** Le trait du Beam Reflexor : jaune pale, presque blanc, et plus tenace que celui du Blaster (la planche le montrait trop fin a 0,17). */
    static final class Beam extends Glow {
        Beam(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, 0.0, 0.0, 0.0, sprites, 4, 0.22F);
            this.setColor(1.0F, 0.98F, 0.72F);
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.quadSize = this.size0 * (1.0F - 0.7F * t);
            this.alpha = 1.0F - 0.6F * t;
            this.setColor(1.0F, 0.98F - 0.2F * t, 0.72F - 0.4F * t);
        }
    }

    /** Le trait d'un tir de la soucoupe du Gyro Burster : orange dore, tete mobile et points fixes (invisible sur la planche a 0,1 : grossi). */
    static final class Dart extends Glow {
        Dart(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
            super(level, x, y, z, dx, dy, dz, sprites, 3, dx * dx + dy * dy + dz * dz > 1.0e-8 ? 0.24F : 0.16F);
            this.setColor(1.0F, 0.72F, 0.18F);
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = 1.0F - 0.6F * progress();
        }
    }

    static final class Glint extends Glow {
        private final float twinkle;

        Glint(ClientLevel level, double x, double y, double z, double family, double rise, SpriteSet sprites) {
            super(level, x, y, z, 0.0, rise, 0.0, sprites, 14 + level.random.nextInt(8), 0.07F);
            GunForm.Family f = GunForm.Family.byIndex((int) Math.round(family));
            int rgb = MorphGunItem.textColor(f == null ? GunForm.Family.YELLOW : f);
            this.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
            this.friction = 0.92F;
            this.twinkle = level.random.nextFloat() * 6.0F;
        }

        @Override
        public void tick() {
            super.tick();
            float t = progress();
            this.alpha = (1.0F - t) * (0.6F + 0.4F * (float) Math.sin(this.age * 0.9F + this.twinkle));
            this.quadSize = this.size0 * (1.0F - 0.5F * t);
        }
    }
}
