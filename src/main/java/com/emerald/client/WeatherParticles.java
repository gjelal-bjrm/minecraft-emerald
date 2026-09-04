package com.emerald.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Les particules des six meteos : treize comportements, chacun pour un usage.
 *
 * LA REGLE : aucune meteo n'emprunte a une autre, ni aux armes, ni aux plantes.
 * Une nappe de brume ne ressemble pas a une cendre, une goutte de Nuit ne
 * ressemble pas a une pluie d'Orage -- et surtout, rien ici n'est la mote de
 * Prisme qu'on voyait partout.
 *
 * Deux familles de rendu :
 *
 *   - la plupart sont des PANNEAUX face camera, comme toute particule ;
 *   - les ANNEAUX (eclatement de goutte, onde de choc) sont POSES A PLAT sur le
 *     sol. Un anneau face camera se lirait comme un disque flottant ; couche,
 *     il devient une trace sur le sol, et c'est cela qu'on reconnait comme un
 *     impact. Voir {@link FlatParticle}.
 *
 * La couleur vient de l'EMETTEUR, par la vitesse initiale ou par une methode
 * dediee : c'est ce qui permet a une goutte de prendre la teinte de l'eclair
 * qu'elle annonce sans qu'il faille une texture par couleur.
 */
public final class WeatherParticles {

    private WeatherParticles() {
    }

    /** Plein feu : la particule luit dans le noir. */
    private static final int BRIGHT = 0xF000F0;

    // =================================================================== PRISME

    /**
     * La nappe : une grande tache douce qui rampe au ras du sol.
     *
     * Enorme et presque transparente, c'est le CUMUL qui fait la brume, pas la
     * nappe seule. Elle vit longtemps et derive a peine : une brume qui bouge
     * vite est un nuage, pas une brume.
     */
    public static class MistSheet extends TextureSheetParticle {
        private final float peak;

        MistSheet(ClientLevel level, double x, double y, double z, double dx, double dz,
                  SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = dx;
            this.yd = 0.0;
            this.zd = dz;
            this.gravity = 0.0F;
            this.friction = 1.0F;
            this.hasPhysics = false;
            this.lifetime = 180 + this.random.nextInt(120);
            this.quadSize = 2.6F + this.random.nextFloat() * 1.6F;
            this.peak = 0.22F + this.random.nextFloat() * 0.14F;
            this.alpha = 0.0F;
            this.setColor(0.74F, 0.79F, 0.85F);
            this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) this.age / this.lifetime;
            // monte en un quart, tient, s'efface sur le dernier tiers
            this.alpha = this.peak * (t < 0.25F ? t / 0.25F : t > 0.66F ? (1.0F - t) / 0.34F : 1.0F);
            this.oRoll = this.roll;
            this.roll += 0.0015F;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new MistSheet(level, x, y, z, dx, dz, this.sprites);
            }
        }
    }

    /**
     * La forme fantomatique : une silhouette verticale qui SE DEFAIT QUAND ON
     * APPROCHE.
     *
     * C'est ce qui fait de la brume autre chose qu'un filtre gris : quelque
     * chose s'y tient, a la limite du visible, et recule devant vous. On ne la
     * voit jamais de pres -- son opacite est proportionnelle a la distance, et
     * elle disparait a moins de quatre blocs.
     */
    public static class MistWraith extends TextureSheetParticle {
        private final float peak;

        MistWraith(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = (this.random.nextDouble() - 0.5) * 0.006;
            this.yd = 0.002;
            this.zd = (this.random.nextDouble() - 0.5) * 0.006;
            this.gravity = 0.0F;
            this.friction = 1.0F;
            this.hasPhysics = false;
            this.lifetime = 140 + this.random.nextInt(90);
            this.quadSize = 1.4F + this.random.nextFloat() * 0.6F;
            this.peak = 0.30F + this.random.nextFloat() * 0.15F;
            this.alpha = 0.0F;
            this.setColor(0.80F, 0.84F, 0.90F);
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) this.age / this.lifetime;
            float life = t < 0.2F ? t / 0.2F : t > 0.7F ? (1.0F - t) / 0.3F : 1.0F;
            // la distance au joueur commande l'opacite : loin, elle existe ;
            // pres, elle n'a jamais ete la
            Vec3 eye = net.minecraft.client.Minecraft.getInstance().gameRenderer
                    .getMainCamera().getPosition();
            double d = Math.sqrt((this.x - eye.x) * (this.x - eye.x)
                    + (this.z - eye.z) * (this.z - eye.z));
            float near = (float) Mth.clamp((d - 4.0) / 8.0, 0.0, 1.0);
            this.alpha = this.peak * life * near;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new MistWraith(level, x, y, z, this.sprites);
            }
        }
    }

    // ================================================================== AURORE

    /**
     * La luciole de cristal : un point vif qui erre et scintille.
     *
     * Elle nait pres des filons d'Arcencium, et c'est tout son sens : l'aurore
     * REPOND aux veines qui chantent. Sa marche est un pas aleatoire lisse --
     * pas une ligne droite, pas une secousse -- et son scintillement a sa
     * propre phase, pour que deux lucioles ne clignotent jamais ensemble.
     */
    public static class CrystalFirefly extends TextureSheetParticle {
        private final float phase;

        CrystalFirefly(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.gravity = 0.0F;
            this.friction = 0.96F;
            this.hasPhysics = false;
            this.lifetime = 90 + this.random.nextInt(70);
            this.quadSize = 0.07F + this.random.nextFloat() * 0.06F;
            this.phase = this.random.nextFloat() * Mth.TWO_PI;
            // entre le cyan et le vert de l'aurore, jamais exactement l'un ou l'autre
            float mix = this.random.nextFloat();
            this.setColor(0.45F + 0.2F * mix, 0.95F, 0.85F - 0.25F * mix);
            this.yd = 0.012;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.xd += (this.random.nextDouble() - 0.5) * 0.004;
            this.yd += (this.random.nextDouble() - 0.45) * 0.002;
            this.zd += (this.random.nextDouble() - 0.5) * 0.004;
            float t = (float) this.age / this.lifetime;
            float life = t < 0.15F ? t / 0.15F : t > 0.75F ? (1.0F - t) / 0.25F : 1.0F;
            this.alpha = life * (0.55F + 0.45F * Mth.sin(this.age * 0.35F + this.phase));
        }

        @Override
        public int getLightColor(float partialTick) {
            return BRIGHT;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new CrystalFirefly(level, x, y, z, this.sprites);
            }
        }
    }

    // ==================================================================== NUIT

    /** Les cinq couleurs des eclairs de la Nuit : rouge, bleu, jaune, rose, vert. */
    public static final float[][] LIGHTNING = {
            {1.00F, 0.36F, 0.40F},
            {0.40F, 0.72F, 1.00F},
            {1.00F, 0.92F, 0.40F},
            {1.00F, 0.50F, 0.85F},
            {0.50F, 1.00F, 0.62F},
    };

    /**
     * La goutte prismatique : fine, longue, colorée, elle tombe vite et
     * ECLATE EN ANNEAU en touchant le sol.
     *
     * L'eclatement est ce qui manque a toute pluie de particules : sans lui,
     * les gouttes disparaissent dans l'herbe et la pluie n'a pas de sol. Ici,
     * chaque goutte laisse sa trace, de sa couleur, une fraction de seconde.
     */
    public static class PrismDrop extends TextureSheetParticle {
        private final int colour;
        private final float[] tint;

        PrismDrop(ClientLevel level, double x, double y, double z, int colour, SpriteSet sprites) {
            super(level, x, y, z);
            this.colour = Math.floorMod(colour, LIGHTNING.length);
            this.tint = LIGHTNING[this.colour];
            this.setColor(this.tint[0], this.tint[1], this.tint[2]);
            this.xd = 0.0;
            this.yd = -0.55 - this.random.nextDouble() * 0.25;
            this.zd = 0.0;
            this.gravity = 0.0F;
            this.friction = 1.0F;
            this.hasPhysics = true;
            this.lifetime = 90;
            this.quadSize = 0.22F;                      // plus grosse : un pack de shaders la noyait
            this.alpha = 1.0F;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.onGround || this.yd == 0.0) {
                // l'eclatement : la goutte est du cristal, elle se BRISE en trois
                // ou quatre eclats presque blancs qui gardent un reflet de sa
                // couleur (voir PrismShard). On passe par le moteur de particules
                // plutot que de construire l'eclat ici : la goutte n'a pas acces
                // a son jeu de sprites.
                int shards = 3 + this.random.nextInt(2);
                for (int i = 0; i < shards; i++) {
                    this.level.addParticle(com.emerald.particles.ModParticles.PRISM_SHARD.get(),
                            this.x, this.y + 0.03, this.z, this.colour, 0.0, 0.0);
                }
                this.remove();
            }
        }

        @Override
        public int getLightColor(float partialTick) {
            return BRIGHT;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                // la couleur voyage dans dx, en entier
                return new PrismDrop(level, x, y, z, (int) Math.round(dx), this.sprites);
            }
        }
    }

    /**
     * Une particule POSEE A PLAT sur le sol.
     *
     * Le rendu ordinaire dresse un panneau face a la camera. Ici on ecrit nous-
     * memes les quatre sommets, dans le plan horizontal, autour de la position :
     * l'anneau est alors une trace au sol qu'on voit de haut comme un anneau
     * et de cote comme un trait -- exactement ce qu'est un impact.
     */
    public abstract static class FlatParticle extends TextureSheetParticle {

        FlatParticle(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.xd = this.yd = this.zd = 0.0;
        }

        @Override
        public void render(VertexConsumer buffer, Camera camera, float partialTick) {
            Vec3 cam = camera.getPosition();
            float px = (float) (Mth.lerp(partialTick, this.xo, this.x) - cam.x);
            float py = (float) (Mth.lerp(partialTick, this.yo, this.y) - cam.y);
            float pz = (float) (Mth.lerp(partialTick, this.zo, this.z) - cam.z);
            float s = getQuadSize(partialTick);
            int light = getLightColor(partialTick);
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            buffer.addVertex(px - s, py, pz + s).setUv(u0, v1)
                    .setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
            buffer.addVertex(px + s, py, pz + s).setUv(u1, v1)
                    .setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
            buffer.addVertex(px + s, py, pz - s).setUv(u1, v0)
                    .setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
            buffer.addVertex(px - s, py, pz - s).setUv(u0, v0)
                    .setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /**
     * L'eclat de cristal : ce qu'une goutte de Nuit devient en touchant le sol.
     *
     * La goutte est de l'Arcencium, donc du cristal : elle ne s'etale pas, elle
     * SE BRISE. Trois ou quatre eclats minuscules qui rebondissent a hauteur de
     * cheville et s'eteignent en un tiers de seconde. Presque blancs, avec un
     * seul reflet de la couleur de l'eclair que la goutte annoncait : de la
     * lumiere vue a travers du verre, pas de la peinture. L'anneau colore qui
     * les precedait avait l'air d'un splash de dessin anime.
     */
    public static class PrismShard extends TextureSheetParticle {
        /** Le sol : un eclat qui repasse dessous a fini de rebondir. */
        private final double floor;

        PrismShard(ClientLevel level, double x, double y, double z, int colour, SpriteSet sprites) {
            super(level, x, y, z);
            float[] tint = LIGHTNING[Math.floorMod(colour, LIGHTNING.length)];
            float k = 0.40F;                               // la part de couleur, le reste est blanc
            this.setColor(1.0F - k * (1.0F - tint[0]), 1.0F - k * (1.0F - tint[1]),
                    1.0F - k * (1.0F - tint[2]));
            double a = this.random.nextDouble() * Math.PI * 2;
            double sp = 0.03 + this.random.nextDouble() * 0.06;
            this.xd = Math.cos(a) * sp;
            this.zd = Math.sin(a) * sp;
            this.yd = 0.10 + this.random.nextDouble() * 0.07;      // 20 a 30 cm de rebond
            this.gravity = 1.0F;
            this.friction = 1.0F;
            this.hasPhysics = false;
            this.floor = y;
            this.lifetime = 6 + this.random.nextInt(4);
            this.quadSize = 0.025F + this.random.nextFloat() * 0.025F;
            this.alpha = 1.0F;
            this.roll = this.random.nextFloat() * (float) Math.PI * 2;
            this.oRoll = this.roll;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.oRoll = this.roll;
            this.roll += 0.45F;                                    // il tourne en l'air
            if (this.y < this.floor) {
                this.remove();
                return;
            }
            int left = this.lifetime - this.age;
            if (left < 3) {
                this.alpha = left / 3.0F;
            }
        }

        @Override
        public int getLightColor(float partialTick) {
            return BRIGHT;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                // la couleur voyage dans dx, en entier, comme pour la goutte
                return new PrismShard(level, x, y, z, (int) Math.round(dx), this.sprites);
            }
        }
    }

    /**
     * La poussiere de secousse : ce que le sol exhale quand il tremble.
     *
     * Des bouffees terreuses qui montent a peine et se dissipent en une
     * seconde, semees au sol autour de chaque joueur pendant qu'une secousse
     * passe. Sans elles, une secousse n'est qu'une camera qui bouge ; avec
     * elles, c'est la TERRE qui bouge. Eclairee comme le monde : c'est de la
     * matiere, pas de la lumiere.
     */
    public static class QuakeDust extends TextureSheetParticle {

        QuakeDust(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            float shade = 0.42F + this.random.nextFloat() * 0.16F;
            this.setColor(shade + 0.10F, shade + 0.04F, shade - 0.03F);   // brun-gris
            this.xd = (this.random.nextDouble() - 0.5) * 0.02;
            this.yd = 0.015 + this.random.nextDouble() * 0.02;
            this.zd = (this.random.nextDouble() - 0.5) * 0.02;
            this.gravity = 0.0F;
            this.friction = 0.96F;
            this.hasPhysics = false;
            this.lifetime = 18 + this.random.nextInt(14);
            this.quadSize = 0.22F + this.random.nextFloat() * 0.12F;
            this.alpha = 0.0F;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) this.age / this.lifetime;
            this.alpha = 0.55F * (float) Math.sin(t * Math.PI);    // elle nait, elle se dissipe
            this.quadSize += 0.012F;                                 // et s'etale en montant
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new QuakeDust(level, x, y, z, this.sprites);
            }
        }
    }

    // ================================================================ METEORES

    /** La tete du meteore : un coeur blanc-jaune, gros, qui ne vit qu'un instant. */
    public static class MeteorHead extends TextureSheetParticle {

        MeteorHead(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.xd = this.yd = this.zd = 0.0;
            this.lifetime = 6 + this.random.nextInt(4);
            this.quadSize = 0.9F + this.random.nextFloat() * 0.5F;
            this.setColor(1.0F, 0.97F, 0.85F);
            this.alpha = 1.0F;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = 1.0F - (float) this.age / this.lifetime;
        }

        @Override
        public int getLightColor(float partialTick) {
            return BRIGHT;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new MeteorHead(level, x, y, z, this.sprites);
            }
        }
    }

    /**
     * La braise : elle REFROIDIT. Orange vif a la naissance, elle vire au rouge
     * sombre puis s'eteint en gris. C'est ce refroidissement qui fait la
     * trainee d'un meteore -- une trainee d'une seule couleur est un trait,
     * une trainee qui refroidit est du feu qui s'eloigne.
     */
    public static class MeteorEmber extends TextureSheetParticle {

        MeteorEmber(ClientLevel level, double x, double y, double z, double dx, double dy,
                    double dz, SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.gravity = -0.004F;                  // elle monte, tres lentement
            this.friction = 0.94F;
            this.hasPhysics = false;
            this.lifetime = 28 + this.random.nextInt(22);
            this.quadSize = 0.22F + this.random.nextFloat() * 0.22F;
            this.alpha = 1.0F;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            float t = (float) this.age / this.lifetime;
            // orange -> rouge sombre -> gris cendre
            if (t < 0.4F) {
                float k = t / 0.4F;
                this.setColor(1.0F, 0.58F - 0.30F * k, 0.16F - 0.10F * k);
            } else {
                float k = (t - 0.4F) / 0.6F;
                this.setColor(1.0F - 0.75F * k, 0.28F - 0.10F * k, 0.06F + 0.18F * k);
            }
            this.alpha = t < 0.7F ? 1.0F : (1.0F - t) / 0.3F;
            this.quadSize *= 0.985F;
        }

        @Override
        public int getLightColor(float partialTick) {
            return (float) this.age / this.lifetime < 0.5F ? BRIGHT : super.getLightColor(partialTick);
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new MeteorEmber(level, x, y, z, dx, dy, dz, this.sprites);
            }
        }
    }

    /** La cendre : un flocon gris qui tombe en se balancant, longtemps. */
    public static class AshFlake extends TextureSheetParticle {
        private final float phase;

        AshFlake(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.gravity = 0.005F;
            this.friction = 0.99F;
            this.hasPhysics = true;
            this.lifetime = 220 + this.random.nextInt(120);
            this.quadSize = 0.06F + this.random.nextFloat() * 0.05F;
            float g = 0.32F + this.random.nextFloat() * 0.14F;
            this.setColor(g, g, g * 0.98F);
            this.alpha = 0.75F;
            this.phase = this.random.nextFloat() * Mth.TWO_PI;
            this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.xd = Mth.sin(this.age * 0.12F + this.phase) * 0.006;
            this.zd = Mth.cos(this.age * 0.09F + this.phase) * 0.006;
            this.oRoll = this.roll;
            this.roll += 0.02F;
            if (this.onGround) {
                this.remove();
            }
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new AshFlake(level, x, y, z, this.sprites);
            }
        }
    }

    /**
     * L'onde de choc : un grand anneau a plat qui s'ecarte de l'impact en une
     * demi-seconde. C'est lui qui donne son poids au meteore -- l'explosion se
     * voit, l'onde se RESSENT.
     */
    public static class GroundShock extends FlatParticle {

        GroundShock(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.setColor(1.0F, 0.80F, 0.55F);
            this.lifetime = 12;
            this.alpha = 0.95F;
            this.pickSprite(sprites);
        }

        @Override
        public float getQuadSize(float partialTick) {
            float t = (this.age + partialTick) / this.lifetime;
            return 0.6F + 7.0F * (1.0F - (1.0F - t) * (1.0F - t));   // vite d'abord, puis freine
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = 0.95F * (1.0F - (float) this.age / this.lifetime);
        }

        @Override
        public int getLightColor(float partialTick) {
            return BRIGHT;
        }

        public static class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            public Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new GroundShock(level, x, y, z, this.sprites);
            }
        }
    }

    // =============================================================== DECHIRURE

    /**
     * L'eclat de terre qui decolle : il monte lentement en tournant sur lui-
     * meme. C'est le seul moyen de faire sentir que la gravite a lache -- on ne
     * voit pas sa propre legerete, mais on voit le sol partir.
     */
    public static class FloatDebris extends TextureSheetParticle {
        private final float spin;

        FloatDebris(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.gravity = 0.0F;
            this.friction = 0.995F;
            this.hasPhysics = false;
            this.yd = 0.014 + this.random.nextDouble() * 0.012;
            this.xd = (this.random.nextDouble() - 0.5) * 0.004;
            this.zd = (this.random.nextDouble() - 0.5) * 0.004;
            this.lifetime = 130 + this.random.nextInt(70);
            this.quadSize = 0.10F + this.random.nextFloat() * 0.09F;
            // les tons de terre : brun, gris pierre, ocre
            float[][] earth = {{0.42F, 0.30F, 0.20F}, {0.50F, 0.50F, 0.50F}, {0.55F, 0.42F, 0.25F}};
            float[] c = earth[this.random.nextInt(earth.length)];
            this.setColor(c[0], c[1], c[2]);
            this.spin = (this.random.nextFloat() - 0.5F) * 0.16F;
            this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.oRoll = this.roll;
            this.roll += this.spin;
            float t = (float) this.age / this.lifetime;
            this.alpha = t > 0.8F ? (1.0F - t) / 0.2F : 1.0F;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new FloatDebris(level, x, y, z, this.sprites);
            }
        }
    }

    /** Le brin d'herbe qui monte : plus leger que la terre, il tourne plus vite. */
    public static class FloatBlade extends TextureSheetParticle {
        private final float spin;

        FloatBlade(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.gravity = 0.0F;
            this.friction = 0.995F;
            this.hasPhysics = false;
            this.yd = 0.022 + this.random.nextDouble() * 0.014;
            this.xd = (this.random.nextDouble() - 0.5) * 0.006;
            this.zd = (this.random.nextDouble() - 0.5) * 0.006;
            this.lifetime = 100 + this.random.nextInt(70);
            this.quadSize = 0.12F + this.random.nextFloat() * 0.06F;
            this.setColor(0.32F + this.random.nextFloat() * 0.1F, 0.62F, 0.24F);
            this.spin = (this.random.nextFloat() - 0.5F) * 0.30F;
            this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.oRoll = this.roll;
            this.roll += this.spin;
            float t = (float) this.age / this.lifetime;
            this.alpha = t > 0.8F ? (1.0F - t) / 0.2F : 1.0F;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new FloatBlade(level, x, y, z, this.sprites);
            }
        }
    }

    // =================================================================== ORAGE

    /**
     * L'etincelle statique : minuscule, blanc-violet, elle s'accroche au metal
     * et gresille. Elle ne bouge pas -- une etincelle qui vole est une braise.
     */
    public static class StaticSpark extends TextureSheetParticle {

        StaticSpark(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.xd = this.yd = this.zd = 0.0;
            this.lifetime = 4 + this.random.nextInt(5);
            this.quadSize = 0.04F + this.random.nextFloat() * 0.04F;
            this.setColor(0.88F, 0.80F, 1.0F);
            this.alpha = 1.0F;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = this.random.nextFloat() < 0.3F ? 0.2F : 1.0F;   // gresillement
        }

        @Override
        public int getLightColor(float partialTick) {
            return BRIGHT;
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new StaticSpark(level, x, y, z, this.sprites);
            }
        }
    }

    /**
     * La pluie oblique : poussee par le vent, elle tombe en biais, vite, et
     * s'efface au sol sans eclabousser -- l'Orage n'a pas la douceur de la Nuit.
     */
    public static class WindRain extends TextureSheetParticle {

        WindRain(ClientLevel level, double x, double y, double z, double windX, double windZ,
                 SpriteSet sprites) {
            super(level, x, y, z);
            this.xd = windX;
            this.yd = -0.7;
            this.zd = windZ;
            this.gravity = 0.0F;
            this.friction = 1.0F;
            this.hasPhysics = true;
            this.lifetime = 45;
            this.quadSize = 0.07F;
            this.setColor(0.72F, 0.78F, 0.92F);
            this.alpha = 0.55F;
            this.pickSprite(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.onGround) {
                this.remove();
            }
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
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                           double y, double z, double dx, double dy, double dz) {
                return new WindRain(level, x, y, z, dx, dz, this.sprites);
            }
        }
    }
}
