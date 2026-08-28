package com.emerald.weather;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.Artifacts;
import com.emerald.block.ModBlocks;
import com.emerald.effects.ModEffects;
import com.emerald.game.GameState;
import com.emerald.game.WorldSetup;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que chaque meteo FAIT, cote serveur.
 *
 * Tout l'etat ici est volatil : une meteo interrompue par un redemarrage
 * disparait simplement, comme un siege. Les regles transverses du cahier
 * passent par deux garde-fous partages :
 *
 *  - {@link #weatherHurt} : le Filtre de Brume immunise les joueurs aux degats
 *    de TOUTES les meteos agressives ;
 *  - {@link #fragile} : les meteores ne brisent jamais un bloc du mod -- c'est
 *    ce qui fait qu'un abri en materiaux d'Arcencium est toujours sur, sans
 *    aucun test de securite explicite.
 */
public final class WeatherEffects {

    private WeatherEffects() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, path);
    }

    /** Reduit la portee de detection des monstres pendant la Brume. */
    private static final ResourceLocation BRUME_ID = id("weather_brume");

    /** Allege la gravite pendant la Dechirure. */
    private static final ResourceLocation GRAVITY_ID = id("weather_gravity");

    private static final String TAG_RIFT_CD = "ArcenciumRiftCd";

    // ------------------------------------------------------------ etat volatil

    private static final class Meteor {
        final BlockPos target;
        /** L'oblique de la chute : un meteore tombe de biais, pas a la verticale. */
        final double driftX;
        final double driftZ;
        int ticks;

        Meteor(BlockPos target, int ticks, double driftX, double driftZ) {
            this.target = target;
            this.ticks = ticks;
            this.driftX = driftX;
            this.driftZ = driftZ;
        }
    }

    private static final class Strike {
        final BlockPos pos;
        int ticks;

        Strike(BlockPos pos, int ticks) {
            this.pos = pos;
            this.ticks = ticks;
        }
    }

    private static final class Wave {
        final Vec3 center;
        float radius = 0.5F;
        final Set<Integer> hit = new HashSet<>();

        Wave(Vec3 center) {
            this.center = center;
        }
    }

    private static final class Rift {
        final BlockPos pos;
        int life;

        Rift(BlockPos pos, int life) {
            this.pos = pos;
            this.life = life;
        }
    }

    private static final List<Meteor> meteors = new ArrayList<>();
    private static final List<Strike> strikes = new ArrayList<>();
    private static final List<Wave> waves = new ArrayList<>();
    private static final Map<BlockPos, Long> scars = new HashMap<>();
    private static final List<Rift> rifts = new ArrayList<>();
    private static final List<UUID> shards = new ArrayList<>();

    /** L'etiquette NBT d'un eclat de Dechirure, seule marque qui survit au disque. */
    private static final String SHARD_TAG = EmeraldWeaponsMod.MODID + "_dechirure_shard";

    /**
     * Rend sa gravite a un eclat qui se recharge hors tempete.
     *
     * ServerLevel.getEntity(UUID) ne voit que les entites CHARGEES : un eclat
     * dont le chunk s'etait decharge -- ce qui arrive des qu'une faille
     * teleporte le joueur a trois cents blocs -- echappait a la restitution de
     * fin et restait suspendu pour toujours. On le rattrape donc quand il
     * revient, a l'etiquette.
     */
    public static void rescueShard(Entity entity) {
        if (!(entity instanceof ItemEntity item) || !item.getTags().contains(SHARD_TAG)) {
            return;
        }
        if (WeatherManager.current() == Weather.DECHIRURE) {
            return;                        // la tempete dure encore : il flotte
        }
        item.setNoGravity(false);
        item.setGlowingTag(false);
        item.removeTag(SHARD_TAG);
    }

    /**
     * Oublie tout : appele a l'arret du serveur.
     *
     * Cet etat est volatil par choix, mais « volatil » ne veut pas dire
     * « efface » : en solo, la machine virtuelle survit au retour au menu et
     * ces listes suivaient dans le monde suivant.
     */
    public static void clearAll() {
        meteors.clear();
        strikes.clear();
        waves.clear();
        scars.clear();
        rifts.clear();
        shards.clear();
    }

    /** Vrai si une cicatrice d'eclair vert vient d'etre minee : elle paie alors. */
    public static boolean claimScar(ServerLevel level, BlockPos pos) {
        Long expiry = scars.remove(pos);
        return expiry != null && level.getGameTime() <= expiry;
    }

    // ---------------------------------------------------------- cycle de vie

    static void begin(ServerLevel level, Weather weather) {
        if (weather == Weather.DECHIRURE) {
            spawnShards(level);
        }
    }

    static void end(ServerLevel level, Weather weather) {
        switch (weather) {
            case BRUME -> sweepModifier(level, Attributes.FOLLOW_RANGE, BRUME_ID);
            case DECHIRURE -> endDechirure(level);
            case METEORES -> meteors.clear();
            case ORAGE -> strikes.clear();
            case NUIT -> waves.clear();     // les cicatrices restent minables jusqu'a expiration
            default -> {
            }
        }
    }

    static void tick(ServerLevel level, Weather weather) {
        switch (weather) {
            case BRUME -> tickBrume(level);
            case AURORE -> tickAurore(level);
            case NUIT -> tickNuit(level);
            case METEORES -> tickMeteores(level);
            case DECHIRURE -> tickDechirure(level);
            case ORAGE -> tickOrage(level);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------ garde-fous

    /**
     * Les degats de meteo passent tous par ici : le Filtre de Brume immunise
     * son porteur, et c'est ce qui rend cet artefact desirable.
     */
    private static void weatherHurt(ServerLevel level, LivingEntity entity,
                                    DamageSource source, float amount) {
        if (entity instanceof Player player && Artifacts.wearing(player, Artifact.FILTRE_DE_BRUME)) {
            return;
        }
        entity.hurt(source, amount);
    }

    /**
     * Un bloc que la meteo a le droit de briser : du VANILLA, et rien d'autre.
     *
     * La liste blanche vaut mieux que la liste noire ici. Exclure le seul
     * espace de noms du mod laissait les meteores percer les constructions de
     * tous les autres -- machines, coffres et reacteurs du modpack compris,
     * dont on ne connait ni la valeur ni la fragilite. On ne casse donc que ce
     * dont on repond : la pierre et la terre du jeu de base.
     */
    private static boolean fragile(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        if (state.getBlock().getExplosionResistance() > 12.0F) {
            return false;
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()
                .equals(ResourceLocation.DEFAULT_NAMESPACE);
    }

    private static void sweepModifier(ServerLevel level,
                                      net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                      ResourceLocation modifierId) {
        for (ServerPlayer player : level.players()) {
            removeModifier(player, attribute, modifierId);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(128))) {
                removeModifier(entity, attribute, modifierId);
            }
        }
    }

    private static void removeModifier(LivingEntity entity,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                       ResourceLocation modifierId) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getModifier(modifierId) != null) {
            instance.removeModifier(modifierId);
        }
    }

    private static void ensureModifier(LivingEntity entity,
                                       net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
                                       ResourceLocation modifierId, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null && instance.getModifier(modifierId) == null) {
            // transitoire : jamais sauvegarde, donc une entite dechargee en
            // pleine tempete revient sans lui -- aucun nettoyage a rattraper
            instance.addTransientModifier(new AttributeModifier(modifierId, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    // ------------------------------------------------------------- la Brume

    /**
     * La Brume coupe la vue DANS LES DEUX SENS : le brouillard du client reduit
     * celle du joueur, et ce modificateur reduit celle des monstres. C'est la
     * fenetre pour traverser ou contourner sans se battre.
     */
    private static void tickBrume(ServerLevel level) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(48), m -> m instanceof Enemy)) {
                ensureModifier(mob, Attributes.FOLLOW_RANGE, BRUME_ID, -0.7);
            }
        }
    }

    // ------------------------------------------------------------- l'Aurore

    /**
     * L'Aurore fait chanter les filons : les veines d'Arcencium proches
     * scintillent et carillonnent. En surface c'est un spectacle ; sous terre,
     * c'est un detecteur -- c'est le moment de descendre miner.
     */
    private static void tickAurore(ServerLevel level) {
        if (level.getGameTime() % 60 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            BlockPos center = player.blockPosition();
            int found = 0;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-12, -12, -12),
                    center.offset(12, 12, 12))) {
                if (!level.getBlockState(pos).is(ModBlocks.ARCENCIUM_ORE.get())) {
                    continue;
                }
                level.sendParticles(ModParticles.PRISM_MOTE.get(),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        4, 0.4, 0.4, 0.4, 0.02);
                if (found == 0) {
                    level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                            SoundSource.AMBIENT, 0.7F, 1.4F);
                }
                if (++found >= 6) {
                    break;
                }
            }
        }
    }

    // -------------------------------------------------- la Nuit d'Arcencium

    /**
     * Les eclairs de la Nuit. La couleur annonce l'effet -- rouge le feu, bleu
     * le gel, jaune l'onde electrique, rose la Marque, vert la cicatrice a
     * miner. Le jaune est contingente : un eclair sur cinq au plus, et jamais
     * deux ondes en meme temps -- l'onde doit rester un moment, pas un bruit
     * de fond.
     */
    private static void tickNuit(ServerLevel level) {
        tickWaves(level);
        tickScars(level);
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (level.random.nextInt(6) != 0) {
                continue;
            }
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 8 + level.random.nextDouble() * 32;
            int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
            BlockPos pos = new BlockPos(x, WorldSetup.surfaceY(level, x, z), z);

            ArcenciumBoltEntity.Variant variant = pickBoltVariant(level);
            strikeBolt(level, pos, variant);
        }
    }

    private static ArcenciumBoltEntity.Variant pickBoltVariant(ServerLevel level) {
        if (waves.isEmpty() && level.random.nextInt(5) == 0) {
            return ArcenciumBoltEntity.Variant.YELLOW;
        }
        ArcenciumBoltEntity.Variant[] calm = {
                ArcenciumBoltEntity.Variant.RED, ArcenciumBoltEntity.Variant.BLUE,
                ArcenciumBoltEntity.Variant.PINK, ArcenciumBoltEntity.Variant.GREEN};
        return calm[level.random.nextInt(calm.length)];
    }

    private static void strikeBolt(ServerLevel level, BlockPos pos,
                                   ArcenciumBoltEntity.Variant variant) {
        level.addFreshEntity(new ArcenciumBoltEntity(level,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, variant));
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                2.0F, 0.9F + level.random.nextFloat() * 0.3F);
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
                1.2F, 1.0F);

        DamageSource source = level.damageSources().lightningBolt();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos).inflate(2.0))) {
            weatherHurt(level, entity, source, 5.0F);
        }

        switch (variant) {
            case RED -> {
                for (int i = 0; i < 6; i++) {
                    BlockPos p = pos.offset(level.random.nextInt(5) - 2, 0,
                            level.random.nextInt(5) - 2);
                    if (level.getBlockState(p).isAir()
                            && Blocks.FIRE.defaultBlockState().canSurvive(level, p)) {
                        level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
                    }
                }
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(pos).inflate(3.0))) {
                    entity.igniteForSeconds(4.0F);
                }
            }
            case BLUE -> {
                for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -2, -3),
                        pos.offset(3, 1, 3))) {
                    if (level.getBlockState(p).is(Blocks.WATER)
                            && level.getFluidState(p).isSource()) {
                        level.setBlockAndUpdate(p, Blocks.ICE.defaultBlockState());
                    } else if (level.random.nextInt(3) == 0
                            && level.getBlockState(p).isAir()
                            && Blocks.SNOW.defaultBlockState().canSurvive(level, p)) {
                        level.setBlockAndUpdate(p, Blocks.SNOW.defaultBlockState());
                    }
                }
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(pos).inflate(3.0))) {
                    entity.setTicksFrozen(Math.max(entity.getTicksFrozen(), 160));
                    entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                }
            }
            case YELLOW -> waves.add(new Wave(Vec3.atBottomCenterOf(pos)));
            case PINK -> {
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(pos).inflate(6.0), e -> !(e instanceof Player))) {
                    entity.addEffect(new MobEffectInstance(ModEffects.PRISMATIC_MARK, 160, 0,
                            false, true, true));
                    entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0,
                            false, false, false));
                }
            }
            case GREEN -> {
                BlockPos ground = pos.below();
                if (!level.getBlockState(ground).isAir()) {
                    scars.put(ground.immutable(), level.getGameTime() + 600);
                }
            }
            default -> {
            }
        }
    }

    /**
     * L'onde electrique du jaune : un anneau qui se propage au ras du sol et
     * frappe TOUT ce qu'il traverse -- monstres et joueurs, comme convenu.
     */
    private static void tickWaves(ServerLevel level) {
        Iterator<Wave> it = waves.iterator();
        while (it.hasNext()) {
            Wave wave = it.next();
            wave.radius += 0.45F;
            if (wave.radius > 10.0F) {
                it.remove();
                continue;
            }
            for (int i = 0; i < 14; i++) {
                double a = i / 14.0 * Math.PI * 2;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        wave.center.x + Math.cos(a) * wave.radius,
                        wave.center.y + 0.3,
                        wave.center.z + Math.sin(a) * wave.radius,
                        1, 0.1, 0.1, 0.1, 0.02);
            }
            DamageSource source = level.damageSources().lightningBolt();
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(wave.center, wave.center).inflate(wave.radius + 1, 2.5, wave.radius + 1))) {
                double dist = Math.sqrt(Math.pow(entity.getX() - wave.center.x, 2)
                        + Math.pow(entity.getZ() - wave.center.z, 2));
                if (Math.abs(dist - wave.radius) > 1.0 || !wave.hit.add(entity.getId())) {
                    continue;
                }
                weatherHurt(level, entity, source, 4.0F);
                entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
        }
    }

    /** Les cicatrices vertes luisent tant qu'elles sont minables. */
    private static void tickScars(ServerLevel level) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Long>> it = scars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            if (now > entry.getValue()) {
                it.remove();
                continue;
            }
            BlockPos pos = entry.getKey();
            level.sendParticles(new DustParticleOptions(new Vector3f(0.47F, 0.91F, 0.68F), 0.9F),
                    pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    2, 0.3, 0.1, 0.3, 0.0);
        }
    }

    // --------------------------------------------------- la Pluie de Meteores

    private static void tickMeteores(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            for (ServerPlayer player : level.players()) {
                if (level.random.nextInt(10) >= 3) {
                    continue;
                }
                double angle = level.random.nextDouble() * Math.PI * 2;
                double dist = 12 + level.random.nextDouble() * 36;
                int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
                int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
                double entry = level.random.nextDouble() * Math.PI * 2;
                double reach = 28 + level.random.nextDouble() * 22;
                meteors.add(new Meteor(
                        new BlockPos(x, WorldSetup.surfaceY(level, x, z), z), 60,
                        Math.cos(entry) * reach, Math.sin(entry) * reach));
            }
        }
        Iterator<Meteor> it = meteors.iterator();
        while (it.hasNext()) {
            Meteor meteor = it.next();
            meteor.ticks--;
            BlockPos t = meteor.target;
            // le cercle d'avertissement : la meteo s'annonce, elle ne piege pas
            if (meteor.ticks % 5 == 0) {
                for (int i = 0; i < 10; i++) {
                    double a = i / 10.0 * Math.PI * 2;
                    level.sendParticles(ParticleTypes.FLAME,
                            t.getX() + 0.5 + Math.cos(a) * 2.5, t.getY() + 0.2,
                            t.getZ() + 0.5 + Math.sin(a) * 2.5, 1, 0.0, 0.02, 0.0, 0.0);
                }
            }
            // le sifflement de l'approche : on leve les yeux avant de chercher
            // ou se mettre, ce qui n'a de sens que si la chute se voit
            if (meteor.ticks == FALL_TICKS) {
                level.playSound(null, t, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR,
                        SoundSource.WEATHER, 3.0F, 0.5F);
            }
            if (meteor.ticks <= FALL_TICKS) {
                drawFall(level, meteor);
            }
            if (meteor.ticks <= 0) {
                it.remove();
                meteorImpact(level, t);
            }
        }
    }

    /** Duree de la chute visible, en ticks. Deux secondes et demie de ciel. */
    private static final int FALL_TICKS = 50;

    /** Hauteur d'entree : assez haut pour barrer le ciel, assez bas pour rester charge. */
    private static final double FALL_HEIGHT = 90.0;

    /**
     * Le meteore lui-meme, dans sa descente.
     *
     * Une simple traine de deux particules ne se voyait pas : on decouvrait le
     * cratere sans avoir rien vu tomber. On dessine donc une tete brillante et
     * une queue derriere elle, le long d'une oblique qui vise le point marque
     * au sol -- le cercle d'avertissement dit OU, la chute dit QUAND.
     */
    private static void drawFall(ServerLevel level, Meteor meteor) {
        BlockPos t = meteor.target;
        // 1 au depart, 0 a l'impact : la position se lit directement dessus
        double k = meteor.ticks / (double) FALL_TICKS;
        double hx = t.getX() + 0.5 + meteor.driftX * k;
        double hy = t.getY() + FALL_HEIGHT * k;
        double hz = t.getZ() + 0.5 + meteor.driftZ * k;

        // la tete : un noyau dense, visible de loin
        level.sendParticles(ParticleTypes.FLAME, hx, hy, hz, 12, 0.5, 0.5, 0.5, 0.02);
        level.sendParticles(ModParticles.CRYSTAL_ORANGE.get(), hx, hy, hz, 6, 0.6, 0.6, 0.6, 0.0);
        level.sendParticles(ParticleTypes.LAVA, hx, hy, hz, 2, 0.3, 0.3, 0.3, 0.0);

        // la queue : huit points echelonnes vers le haut, qui s'effilent
        for (int i = 1; i <= 8; i++) {
            double back = i / (double) FALL_TICKS * 1.4;
            double bk = Math.min(1.0, k + back);
            level.sendParticles(ParticleTypes.SMALL_FLAME,
                    t.getX() + 0.5 + meteor.driftX * bk,
                    t.getY() + FALL_HEIGHT * bk,
                    t.getZ() + 0.5 + meteor.driftZ * bk,
                    1, 0.25, 0.25, 0.25, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        t.getX() + 0.5 + meteor.driftX * bk,
                        t.getY() + FALL_HEIGHT * bk,
                        t.getZ() + 0.5 + meteor.driftZ * bk,
                        1, 0.3, 0.3, 0.3, 0.0);
            }
        }
    }

    private static void meteorImpact(ServerLevel level, BlockPos target) {
        level.playSound(null, target, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER,
                1.4F, 0.8F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1, 0, 0, 0, 0);
        level.sendParticles(ModParticles.PRISM_MOTE.get(),
                target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5,
                20, 1.5, 1.0, 1.5, 0.15);

        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-2, -2, -2),
                target.offset(2, 1, 2))) {
            if (pos.distSqr(target) <= 6.5 && fragile(level, pos)) {
                level.destroyBlock(pos, false);
            }
        }
        // parfois, le cratere perce jusqu'aux grottes : un raccourci vers le minage
        if (level.random.nextInt(10) < 3) {
            int air = 0;
            for (int depth = 1; depth <= 12 && air < 2; depth++) {
                BlockPos below = target.below(depth);
                if (level.getBlockState(below).isAir()) {
                    air++;
                } else if (fragile(level, below)) {
                    level.destroyBlock(below, false);
                } else {
                    break;
                }
            }
        }
        Vec3 center = Vec3.atCenterOf(target);
        ItemEntity drop = new ItemEntity(level, center.x, center.y + 0.5, center.z,
                new ItemStack(ModItems.RAW_ARCENCIUM.get(), 1 + level.random.nextInt(2)));
        level.addFreshEntity(drop);

        DamageSource source = level.damageSources().explosion(null, null);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(target).inflate(4.5))) {
            double dist = Math.sqrt(entity.distanceToSqr(center));
            float damage = (float) Math.max(3.0, 8.0 - dist * 1.2);
            weatherHurt(level, entity, source, damage);
            Vec3 away = entity.position().subtract(center).normalize();
            entity.push(away.x * 0.6, 0.3, away.z * 0.6);
        }
    }

    // ---------------------------------------------- la Dechirure Prismatique

    /**
     * La Dechirure : le lieu se defait. La gravite chute, des eclats
     * d'Arcencium flottent hors de portee normale -- on ne les atteint QUE
     * pendant l'apesanteur, c'est ce qui la justifie -- et des failles
     * deposent pres d'un lieu qui compte. Le danger : tout s'arrete d'un coup.
     */
    private static void tickDechirure(ServerLevel level) {
        // la gravite allegee, joueurs et monstres proches
        if (level.getGameTime() % 20 == 0) {
            for (ServerPlayer player : level.players()) {
                ensureModifier(player, Attributes.GRAVITY, GRAVITY_ID, -0.65);
                for (Mob mob : level.getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(48))) {
                    ensureModifier(mob, Attributes.GRAVITY, GRAVITY_ID, -0.65);
                }
            }
        }
        // pendant la tempete, les bonds ne blessent pas : la chute qui compte
        // est celle de la FIN, quand la gravite revient d'un coup
        for (ServerPlayer player : level.players()) {
            if (player.fallDistance > 2.5F) {
                player.fallDistance = 2.5F;
            }
        }
        if (level.getGameTime() % 300 == 0) {
            spawnShards(level);
        }
        tickShards(level);
        tickRifts(level);
    }

    /** Les grappes d'eclats en suspension, a 12-20 blocs du sol. */
    private static void spawnShards(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (shards.size() >= 24) {
                return;
            }
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 10 + level.random.nextDouble() * 20;
            int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
            int y = WorldSetup.surfaceY(level, x, z) + 12 + level.random.nextInt(8);
            int count = 2 + level.random.nextInt(2);
            for (int i = 0; i < count; i++) {
                ItemEntity shard = new ItemEntity(level,
                        x + level.random.nextDouble() * 3 - 1.5,
                        y + level.random.nextDouble() * 2,
                        z + level.random.nextDouble() * 3 - 1.5,
                        new ItemStack(ModItems.RAW_ARCENCIUM.get()));
                shard.setNoGravity(true);
                shard.setDeltaMovement(Vec3.ZERO);
                shard.setGlowingTag(true);
                shard.setUnlimitedLifetime();
                // l'etiquette survit a la sauvegarde, contrairement a la liste
                // d'UUID : c'est elle qui permet de rattraper un eclat dont le
                // chunk s'etait decharge quand la tempete s'est terminee
                shard.addTag(SHARD_TAG);
                level.addFreshEntity(shard);
                shards.add(shard.getUUID());
            }
        }
    }

    private static void tickShards(ServerLevel level) {
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        Iterator<UUID> it = shards.iterator();
        while (it.hasNext()) {
            Entity entity = level.getEntity(it.next());
            if (entity == null) {
                continue;      // simplement decharge : il reviendra, on le garde
            }
            if (!entity.isAlive()) {
                it.remove();   // cueilli ou detruit : celui-la ne reviendra pas
                continue;
            }
            level.sendParticles(ModParticles.PRISM_MOTE.get(),
                    entity.getX(), entity.getY() + 0.3, entity.getZ(), 1, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private static void tickRifts(ServerLevel level) {
        if (rifts.size() < 3 && level.getGameTime() % 100 == 0 && !level.players().isEmpty()) {
            ServerPlayer player = level.players()
                    .get(level.random.nextInt(level.players().size()));
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 15 + level.random.nextDouble() * 20;
            int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
            rifts.add(new Rift(new BlockPos(x, WorldSetup.surfaceY(level, x, z), z), 900));
        }
        Iterator<Rift> it = rifts.iterator();
        while (it.hasNext()) {
            Rift rift = it.next();
            if (--rift.life <= 0) {
                it.remove();
                continue;
            }
            BlockPos pos = rift.pos;
            if (level.getGameTime() % 2 == 0) {
                level.sendParticles(ParticleTypes.PORTAL,
                        pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                        6, 0.4, 1.0, 0.4, 0.05);
                level.sendParticles(ParticleTypes.END_ROD,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                        1, 0.2, 0.8, 0.2, 0.01);
            }
            if (level.getGameTime() % 10 != 0) {
                continue;
            }
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(pos).inflate(1.2, 2.0, 1.2))) {
                travelRift(level, entity);
            }
        }
    }

    /**
     * La faille ne depose pas n'importe ou : pres d'un lieu qui COMPTE -- une
     * ancre non tenue en priorite. On sait qu'on arrivera quelque part
     * d'utile ; on ne sait pas lequel, ni avec qui.
     */
    private static void travelRift(ServerLevel level, LivingEntity entity) {
        long now = level.getGameTime();
        if (entity.getPersistentData().getLong(TAG_RIFT_CD) > now) {
            return;
        }
        entity.getPersistentData().putLong(TAG_RIFT_CD, now + 300);

        GameState state = GameState.get(level);
        List<BlockPos> targets = state.anchors().stream()
                .filter(anchor -> !state.isActivated(anchor)).toList();
        BlockPos base;
        if (!targets.isEmpty()) {
            base = targets.get(level.random.nextInt(targets.size()));
        } else {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double dist = 250 + level.random.nextDouble() * 200;
            BlockPos village = state.village();
            base = village.offset((int) (Math.cos(angle) * dist), 0,
                    (int) (Math.sin(angle) * dist));
        }
        double angle = level.random.nextDouble() * Math.PI * 2;
        double dist = 40 + level.random.nextDouble() * 80;
        BlockPos near = base.offset((int) (Math.cos(angle) * dist), 0,
                (int) (Math.sin(angle) * dist));
        BlockPos dest = WorldSetup.findOpenGround(level, near, 16);

        level.playSound(null, entity.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.AMBIENT, 1.0F, 0.8F);
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(level, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5,
                    Set.of(), player.getYRot(), player.getXRot());
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1));
        } else {
            entity.teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        }
        level.playSound(null, dest, SoundEvents.ENDERMAN_TELEPORT, SoundSource.AMBIENT, 1.0F, 1.2F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5, 30, 0.5, 1.0, 0.5, 0.1);
    }

    private static void endDechirure(ServerLevel level) {
        sweepModifier(level, Attributes.GRAVITY, GRAVITY_ID);
        // les eclats non cueillis retombent : la tempete reprend ce qu'elle
        // avait suspendu, y compris ses cadeaux
        for (UUID id : shards) {
            Entity entity = level.getEntity(id);
            if (entity != null) {
                entity.setNoGravity(false);
                entity.setGlowingTag(false);
                entity.removeTag(SHARD_TAG);
            }
        }
        shards.clear();
        rifts.clear();
    }

    // --------------------------------------------------- l'Orage Prismatique

    /**
     * L'Orage : des frappes annoncees, de gros degats -- et la Surcharge pour
     * qui encaisse. C'est la seule meteo ou l'on CHERCHE a etre touche. Le
     * Filtre de Brume annule les degats mais laisse la Surcharge : s'exposer
     * aux frappes devient alors un style de jeu.
     */
    private static void tickOrage(ServerLevel level) {
        if (level.getGameTime() % 40 == 0) {
            for (ServerPlayer player : level.players()) {
                if (level.random.nextInt(10) >= 4) {
                    continue;
                }
                double angle = level.random.nextDouble() * Math.PI * 2;
                double dist = 6 + level.random.nextDouble() * 18;
                int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
                int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
                BlockPos pos = new BlockPos(x, WorldSetup.surfaceY(level, x, z), z);
                if (level.canSeeSky(pos)) {
                    strikes.add(new Strike(pos, 50));
                }
            }
        }
        Iterator<Strike> it = strikes.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            strike.ticks--;
            BlockPos pos = strike.pos;
            if (strike.ticks % 4 == 0) {
                double pulse = 3.0 * (strike.ticks / 50.0);
                for (int i = 0; i < 8; i++) {
                    double a = i / 8.0 * Math.PI * 2;
                    level.sendParticles(new DustParticleOptions(new Vector3f(0.73F, 0.55F, 1.0F), 1.2F),
                            pos.getX() + 0.5 + Math.cos(a) * pulse, pos.getY() + 0.2,
                            pos.getZ() + 0.5 + Math.sin(a) * pulse, 1, 0.0, 0.05, 0.0, 0.0);
                }
            }
            if (strike.ticks <= 0) {
                it.remove();
                orageStrike(level, pos);
            }
        }
    }

    private static void orageStrike(ServerLevel level, BlockPos pos) {
        level.addFreshEntity(new ArcenciumBoltEntity(level,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                ArcenciumBoltEntity.Variant.ORAGE));
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                2.5F, 0.7F);

        DamageSource source = level.damageSources().lightningBolt();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos).inflate(3.5))) {
            weatherHurt(level, entity, source, 10.0F);
            if (entity instanceof ServerPlayer player) {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 1));
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
                player.displayClientMessage(net.minecraft.network.chat.Component
                        .translatable("game.emeraldweapons.surcharge")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
                level.sendParticles(ModParticles.PRISM_MOTE.get(),
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        25, 0.4, 0.8, 0.4, 0.12);
            }
        }
    }
}
