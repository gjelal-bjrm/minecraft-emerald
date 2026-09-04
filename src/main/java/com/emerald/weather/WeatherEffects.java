package com.emerald.weather;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.Artifacts;
import com.emerald.block.ModBlocks;
import com.emerald.effects.ModEffects;
import com.emerald.game.GamePhase;
import com.emerald.game.GameState;
import com.emerald.game.SiegeRoster;
import com.emerald.network.FissureSyncPayload;
import com.emerald.network.StormStrikePayload;
import com.emerald.network.WeatherPulsePayload;
import com.emerald.game.WorldSetup;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.util.RandomSource;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.block.Block;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
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
import net.neoforged.neoforge.network.PacketDistributor;

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

    /**
     * Une fissure : une ouverture REELLE dans le sol, creusee par une secousse.
     *
     * Elle a une taille -- de la craquelure d'un bloc de large a l'abime qui
     * avale une colline -- tiree au sort, les grandes bien plus rares que les
     * petites (voir FissureTier). Elle s'ANNONCE d'abord : une fente dessinee
     * au sol par le client (voir FissureRenderer) qui se propage depuis son
     * centre ; puis, une seconde et demie plus tard, le sol cede, du centre
     * vers les bouts. La forme -- principale et ramifications -- est partagee
     * avec le client (voir FissureShape) : la fente annoncee et le trou
     * coincident.
     */
    private static final class Fissure {
        final double x;
        final double z;
        final float dir;
        final float length;
        final float width;
        final int depth;
        final float shake;
        final int maxLife;
        final List<FissureShape.Line> lines;
        /** Par ligne, les points deja effondres : l'effondrement court du centre aux bouts. */
        final boolean[][] carved;
        int life;
        /** Blocs retires, pour le journal : une fissure qui n'enleve rien est un bug. */
        int removed;
        /**
         * Par colonne (x, z) : la surface d'ORIGINE et la profondeur deja creusee.
         * Les disques se recouvrent le long de la ligne ; sans cette memoire,
         * chaque disque repartait du nouveau sol et une grande fissure creusait
         * jusqu'au socle (mille blocs au lieu de deux cents).
         */
        final Map<Long, int[]> columns = new HashMap<>();

        Fissure(double x, double z, float dir, FissureTier tier, RandomSource random) {
            this.x = x;
            this.z = z;
            this.dir = dir;
            this.length = tier.lengthMin + random.nextFloat() * (tier.lengthMax - tier.lengthMin);
            this.width = tier.widthMin + random.nextFloat() * (tier.widthMax - tier.widthMin);
            this.depth = tier.depthMin + random.nextInt(tier.depthMax - tier.depthMin + 1);
            this.shake = tier.shake;
            this.maxLife = 500 + random.nextInt(300);
            this.lines = FissureShape.lines(x, z, dir, this.length, this.width, this.depth);
            this.carved = new boolean[this.lines.size()][FissureShape.POINTS];
        }
    }

    /**
     * Les tailles de fissure et leur poids : sept sur dix sont des
     * craquelures, une sur cinquante est un abime. Largeur et longueur en
     * blocs, profondeur en blocs sous la surface, et la secousse que
     * l'effondrement envoie.
     */
    private enum FissureTier {
        PETITE(70, 1.0F, 1.4F, 2, 4, 4.0F, 8.0F, 0.6F),
        MOYENNE(22, 1.4F, 2.2F, 4, 7, 8.0F, 14.0F, 0.9F),
        GRANDE(6, 2.4F, 3.4F, 8, 14, 14.0F, 22.0F, 1.3F),
        ABIME(2, 3.6F, 5.0F, 16, 30, 22.0F, 34.0F, 1.7F);

        final int weight;
        final float widthMin;
        final float widthMax;
        final int depthMin;
        final int depthMax;
        final float lengthMin;
        final float lengthMax;
        final float shake;

        FissureTier(int weight, float widthMin, float widthMax, int depthMin, int depthMax,
                    float lengthMin, float lengthMax, float shake) {
            this.weight = weight;
            this.widthMin = widthMin;
            this.widthMax = widthMax;
            this.depthMin = depthMin;
            this.depthMax = depthMax;
            this.lengthMin = lengthMin;
            this.lengthMax = lengthMax;
            this.shake = shake;
        }

        static FissureTier roll(RandomSource random) {
            int total = 0;
            for (FissureTier t : values()) {
                total += t.weight;
            }
            int pick = random.nextInt(total);
            for (FissureTier t : values()) {
                pick -= t.weight;
                if (pick < 0) {
                    return t;
                }
            }
            return PETITE;
        }
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final List<Meteor> meteors = new ArrayList<>();
    private static final List<Fissure> fissures = new ArrayList<>();
    /** Le tick de la prochaine secousse, et ce qu'il reste de celle en cours. */
    private static long nextQuake = -1;
    private static int quakeTicks;
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
        if (weather == Weather.AURORE) {
            // chaque Aurore reparle : ce qu'on a dit la fois d'avant est loin
            auroreTold.clear();
            auroreMined.clear();
        }
        if (weather == Weather.DECHIRURE) {
            spawnShards(level);
        }
        if (weather == Weather.METEORES) {
            // jamais de secousse dans les dix premieres secondes
            nextQuake = level.getGameTime() + 200 + level.random.nextInt(300);
            quakeTicks = 0;
        }
    }

    static void end(ServerLevel level, Weather weather) {
        switch (weather) {
            case BRUME -> sweepModifier(level, Attributes.FOLLOW_RANGE, BRUME_ID);
            case DECHIRURE -> endDechirure(level);
            case METEORES -> {
                meteors.clear();
                quakeTicks = 0;               // les fissures en cours finissent leur vie
            }
            case ORAGE -> {
                strikes.clear();
                surcharged.clear();
            }
            case NUIT -> waves.clear();     // les cicatrices restent minables jusqu'a expiration
            default -> {
            }
        }
    }

    static void tick(ServerLevel level, Weather weather) {
        stormPressure(level, weather);
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

    // ------------------------------------------------------- le coup ressenti

    /**
     * Un coup de tempete : eclat d'ecran et secousse, chez ceux qui sont assez
     * pres pour l'avoir vecu.
     *
     * La force decroit avec la distance, ce qui suffit a situer l'evenement :
     * un impact a dix blocs lave l'ecran, le meme a soixante n'est qu'un
     * frisson. Sans cela, une tempete reste un decor qu'on regarde.
     */
    private static void pulse(ServerLevel level, BlockPos pos, int color,
                              float flash, float shake, double radius) {
        for (ServerPlayer player : level.players()) {
            double dist = Math.sqrt(player.distanceToSqr(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (dist > radius) {
                continue;
            }
            float falloff = (float) (1.0 - dist / radius);
            falloff *= falloff;                 // la chute est plus raide que lineaire
            PacketDistributor.sendToPlayer(player, new WeatherPulsePayload(color,
                    Math.round(flash * falloff * 100.0F),
                    Math.round(shake * falloff * 100.0F)));
        }
    }

    // --------------------------------------------------- la pression de monstres

    /** Au-dela, on cesse d'en ajouter : la tempete presse, elle ne submerge pas. */
    private static final int PRESSURE_CAP = 19;

    /** Marque les monstres nes de la tempete -- ce sont eux qui paieront. */
    public static final String TAG_STORM = "emeraldweapons_storm_born";

    /**
     * Une tempete agressive PEUPLE le monde.
     *
     * C'etait le manque le plus cite a l'essai : « on voit quelques monstres,
     * mais au final c'est une nuit normale ». Les apparitions naturelles ne
     * suffisent pas -- elles sont plafonnees par la lumiere et par le nombre de
     * mobs deja charges, si bien qu'une tempete ne changeait rien a ce qu'on
     * croisait. On ajoute donc notre propre pression, par apparition d'EVENEMENT
     * pour qu'elle echappe aux regles de lumiere, plafonnee par joueur.
     */
    private static void stormPressure(ServerLevel level, Weather weather) {
        if (!weather.aggressive || level.getGameTime() % 40 != 0) {
            return;
        }
        int tier = switch (GameState.get(level).phase(level)) {
            case PRESSION -> 2;
            case ASSAUT -> 3;
            default -> 1;
        };
        for (ServerPlayer player : level.players()) {
            AABB around = player.getBoundingBox().inflate(44.0);
            long nearby = level.getEntitiesOfClass(LivingEntity.class, around,
                    e -> e instanceof Enemy).size();
            if (nearby >= PRESSURE_CAP) {
                continue;
            }
            int wanted = 2 + level.random.nextInt(3);
            for (int i = 0; i < wanted && nearby + i < PRESSURE_CAP; i++) {
                spawnStormMob(level, player, tier);
            }
        }
    }

    private static void spawnStormMob(ServerLevel level, ServerPlayer player, int tier) {
        double angle = level.random.nextDouble() * Math.PI * 2;
        double dist = 22 + level.random.nextDouble() * 14;
        int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
        int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
        BlockPos spot = new BlockPos(x, WorldSetup.surfaceY(level, x, z), z);
        if (!level.isLoaded(spot)) {
            return;                            // jamais de generation forcee
        }
        EntityType<?> type = pickStormType(level, tier);
        if (type == null) {
            return;
        }
        Entity mob = type.spawn(level, spot, MobSpawnType.EVENT);
        if (mob == null) {
            return;
        }
        mob.addTag(TAG_STORM);
        // l'arrivee se voit : sans cela les monstres semblent surgir de nulle
        // part. La tempete le CRACHE dans un gresillement d'etincelles -- c'est
        // elle qui l'envoie, et cela doit se lire.
        level.sendParticles(ModParticles.STATIC_SPARK.get(),
                mob.getX(), mob.getY() + 1.0, mob.getZ(), 14, 0.4, 0.7, 0.4, 0.0);
    }

    @javax.annotation.Nullable
    private static EntityType<?> pickStormType(ServerLevel level, int tier) {
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : SiegeRoster.forTier(tier)) {
            EntityType.byString(id).ifPresent(pool::add);
        }
        if (pool.isEmpty()) {
            EntityType<?>[] fallback = SiegeRoster.vanillaFallback(tier);
            return fallback.length == 0 ? null : fallback[level.random.nextInt(fallback.length)];
        }
        return pool.get(level.random.nextInt(pool.size()));
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
    /** Rayon de la sonde, en blocs : deux chunks et demi autour du joueur. */
    private static final int AURORE_RANGE = 40;
    /** Autant de colonnes au plus par joueur : au-dela, le paysage devient une foret de rais. */
    private static final int AURORE_BEAMS = 6;

    /**
     * L'AURORE CHERCHE AUSSI LE DIAMANT, et c'etait une contradiction.
     *
     * Le minerai d'Arcencium se mine a la pioche de DIAMANT. Une meteo qui
     * montre les veines d'Arcencium et paie double sur elles ne sert donc a
     * rien tant qu'on n'a pas de diamant -- c'est-a-dire exactement pendant la
     * phase d'Exploration, la seule ou l'Aurore tombe. On donnait une clef a
     * qui possedait deja la serrure.
     *
     * Le diamant est donc devenu la premiere chose que l'Aurore designe. Meme
     * rai de lumiere, meme portee : ce que la meteo promet, c'est « voila ou
     * creuser », et le premier ou creuser est le diamant.
     */
    private static boolean auroreTarget(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(ModBlocks.ARCENCIUM_ORE.get())
                || state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES_DIAMOND);
    }

    /**
     * A QUI ON A DEJA DIT, pendant cette Aurore-ci.
     *
     * Le sous-titre « Descendez miner » passe en trois secondes, sous un titre,
     * une fois. Les rais de lumiere sortent bien du sol -- encore faut-il
     * regarder par la, et savoir que ces colonnes veulent dire quelque chose.
     *
     * On le dit donc a chacun, UNE FOIS par Aurore, et seulement quand c'est
     * VRAI pour lui : quand des veines chantent effectivement pres de lui. Un
     * conseil qu'on repete devient un bruit ; un conseil qui compte les veines
     * qu'on a sous les pieds est une information.
     */
    private static final java.util.Set<java.util.UUID> auroreTold = new java.util.HashSet<>();
    /** Et a qui l'on a deja confirme que la veine rendait double. */
    private static final java.util.Set<java.util.UUID> auroreMined = new java.util.HashSet<>();

    /** Vrai la premiere fois seulement : c'est le joueur qui vient de casser son premier filon. */
    static boolean firstVeinOfAurore(ServerPlayer player) {
        return auroreMined.add(player.getUUID());
    }

    /**
     * L'AURORE EST LA FENETRE DE LA MINE.
     *
     * Elle ne faisait rien d'utile : la sonde cherchait dans un cube de douze
     * blocs et posait ses lucioles SUR le filon -- c'est-a-dire dans la pierre,
     * ou personne ne les voit. Il fallait deja avoir trouve la veine pour
     * qu'elle vous la signale.
     *
     * Elle envoie maintenant un RAI DE LUMIERE du filon jusqu'au-dessus du sol :
     * depuis la surface on voit des colonnes prismatiques qui sortent de terre,
     * et l'on sait ou creuser. C'est ce que le cahier promettait -- « le moment
     * de descendre miner » -- et ce qui donne enfin sa raison d'etre a la seule
     * meteo douce du debut de partie.
     *
     * La recherche passe par les SECTIONS de chunk : demander a la palette si
     * elle contient seulement notre minerai coute quelques comparaisons, la ou
     * parcourir 40x64x40 blocs par joueur et par seconde en couterait cent mille.
     */
    private static void tickAurore(ServerLevel level) {
        if (level.getGameTime() % 60 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            List<BlockPos> veins = findVeins(level, player.blockPosition());
            for (BlockPos pos : veins) {
                beam(level, pos);
            }
            if (!veins.isEmpty()) {
                level.playSound(null, veins.get(0), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.AMBIENT, 0.7F, 1.4F);
                if (auroreTold.add(player.getUUID())) {
                    BlockPos nearest = veins.get(0);
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                                    "weather.emeraldweapons.aurore.veins", veins.size(),
                                    (int) Math.sqrt(player.blockPosition().distSqr(nearest)),
                                    com.emerald.game.Finale.cardinal(
                                            nearest.getX() - player.getX(),
                                            nearest.getZ() - player.getZ()))
                            .withStyle(style -> style.withColor(Weather.AURORE.color)));
                }
            }
        }
    }

    /** Les filons les plus proches, un par colonne, du plus proche au plus loin. */
    private static List<BlockPos> findVeins(ServerLevel level, BlockPos center) {
        List<BlockPos> found = new ArrayList<>();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - 48);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + 16);
        int chunkRange = AURORE_RANGE >> 4;
        for (int cx = -chunkRange; cx <= chunkRange; cx++) {
            for (int cz = -chunkRange; cz <= chunkRange; cz++) {
                ChunkPos cp = new ChunkPos(SectionPos.blockToSectionCoord(center.getX()) + cx,
                        SectionPos.blockToSectionCoord(center.getZ()) + cz);
                if (!level.hasChunk(cp.x, cp.z)) {
                    continue;                       // jamais de generation forcee
                }
                LevelChunk chunk = level.getChunk(cp.x, cp.z);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    int baseY = chunk.getMinBuildHeight() + (i << 4);
                    if (section.hasOnlyAir() || baseY + 15 < minY || baseY > maxY) {
                        continue;
                    }
                    // la palette repond sans qu'on ouvre la section
                    if (!section.maybeHas(WeatherEffects::auroreTarget)) {
                        continue;
                    }
                    scanSection(level, section, cp, baseY, minY, maxY, center, found);
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distSqr(center), b.distSqr(center)));
        return found.size() > AURORE_BEAMS ? found.subList(0, AURORE_BEAMS) : found;
    }

    /** Un filon par colonne : quatre-vingts rais pour une seule veine ne diraient rien de plus. */
    private static void scanSection(ServerLevel level, LevelChunkSection section, ChunkPos cp,
                                    int baseY, int minY, int maxY, BlockPos center,
                                    List<BlockPos> found) {
        java.util.Set<Long> columns = new java.util.HashSet<>();
        for (BlockPos pos : found) {
            columns.add(ChunkPos.asLong(pos.getX(), pos.getZ()));
        }
        for (int y = 0; y < 16; y++) {
            int wy = baseY + y;
            if (wy < minY || wy > maxY) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    if (!auroreTarget(section.getBlockState(x, y, z))) {
                        continue;
                    }
                    int wx = cp.getMinBlockX() + x;
                    int wz = cp.getMinBlockZ() + z;
                    double dx = wx - center.getX();
                    double dz = wz - center.getZ();
                    if (dx * dx + dz * dz > (double) AURORE_RANGE * AURORE_RANGE) {
                        continue;
                    }
                    if (columns.add(ChunkPos.asLong(wx, wz))) {
                        found.add(new BlockPos(wx, wy, wz));
                    }
                }
            }
        }
    }

    /** Le rai : du filon jusqu'a six blocs au-dessus du sol, visible de loin. */
    private static void beam(ServerLevel level, BlockPos vein) {
        level.sendParticles(ModParticles.CRYSTAL_FIREFLY.get(),
                vein.getX() + 0.5, vein.getY() + 1.2, vein.getZ() + 0.5,
                3, 0.4, 0.3, 0.4, 0.0);
        int surface = WorldSetup.surfaceY(level, vein.getX(), vein.getZ());
        for (int y = vein.getY() + 1; y <= surface + 6; y += 2) {
            level.sendParticles(ModParticles.PRISM_MOTE.get(),
                    vein.getX() + 0.5, y, vein.getZ() + 0.5, 1, 0.06, 0.25, 0.06, 0.0);
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
        distantFlash(level);
        // un eclair toutes les six secondes se lisait comme un ciel calme : la
        // Nuit doit gronder sans repit, c'est ce qui la distingue d'une nuit
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (level.random.nextInt(2) != 0) {
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

    /**
     * Les eclairs de l'horizon : de la lumiere, pas d'evenement.
     *
     * Une tempete n'est pas faite que de ce qui vous tombe dessus. Ces eclats
     * lointains n'ont ni degats ni entite -- juste un ciel qui blanchit et un
     * grondement, ce qui remplit les silences entre deux vraies frappes.
     */
    private static void distantFlash(ServerLevel level) {
        if (level.getGameTime() % 35 != 0 || level.random.nextInt(3) != 0) {
            return;
        }
        int color = switch (level.random.nextInt(4)) {
            case 0 -> 0xFF8C6B;
            case 1 -> 0x8CC4FF;
            case 2 -> 0xFF9CE8;
            default -> 0xC9A0FF;
        };
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player,
                    new WeatherPulsePayload(color, 22 + level.random.nextInt(16), 0));
            level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.WEATHER, 0.55F, 0.45F + level.random.nextFloat() * 0.2F);
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
        pulse(level, pos, variant.color, 0.95F, 0.55F, 46.0);

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
            // le front de l'onde : le sol se brise en eclats JAUNES le long du
            // rayon courant -- le vocabulaire de la Nuit, celui de ses gouttes
            // qui se brisent en cristal. La couleur voyage dans dx.
            for (int i = 0; i < 14; i++) {
                double a = i / 14.0 * Math.PI * 2;
                for (int k = 0; k < 2; k++) {
                    level.sendParticles(ModParticles.PRISM_SHARD.get(),
                            wave.center.x + Math.cos(a) * wave.radius,
                            wave.center.y + 0.05,
                            wave.center.z + Math.sin(a) * wave.radius,
                            0, 2.0, 0.0, 0.0, 1.0);
                }
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
            // des eclats VERTS qui sautent du bloc : la cicatrice signale
            // qu'elle se mine encore, dans la couleur de l'eclair qui l'a faite
            if (now % 2 == 0) {
                level.sendParticles(ModParticles.PRISM_SHARD.get(),
                        pos.getX() + 0.2 + level.random.nextDouble() * 0.6, pos.getY() + 1.05,
                        pos.getZ() + 0.2 + level.random.nextDouble() * 0.6,
                        0, 4.0, 0.0, 0.0, 1.0);
            }
        }
    }

    // --------------------------------------------------- la Pluie de Meteores

    private static void tickMeteores(ServerLevel level) {
        tickQuakes(level);
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
                    level.sendParticles(ModParticles.METEOR_EMBER.get(),
                            t.getX() + 0.5 + Math.cos(a) * 2.5, t.getY() + 0.2,
                            t.getZ() + 0.5 + Math.sin(a) * 2.5, 1, 0.0, 0.04, 0.0, 1.0);
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

        // LA TETE : un coeur blanc-jaune, gros et bref -- c'est lui qu'on voit
        // de loin. Deux par tick, pour qu'il n'y ait jamais de trou.
        level.sendParticles(ModParticles.METEOR_HEAD.get(), hx, hy, hz, 2, 0.15, 0.15, 0.15, 0.0);

        // LA TRAINEE : des braises lachees derriere la tete, avec une vitesse
        // qui pointe VERS L'ARRIERE de la chute. Elles refroidissent d'elles-
        // memes, de l'orange au gris (voir MeteorEmber) : la trainee n'est pas
        // un trait mais du feu qui s'eloigne et s'eteint.
        double bx = meteor.driftX / FALL_TICKS;
        double by = FALL_HEIGHT / FALL_TICKS;
        double bz = meteor.driftZ / FALL_TICKS;
        for (int i = 0; i < 6; i++) {
            double back = i * 0.35;
            level.sendParticles(ModParticles.METEOR_EMBER.get(),
                    hx + bx * back, hy + by * back, hz + bz * back,
                    0, bx * 0.15, by * 0.15, bz * 0.15, 1.0);
        }
        // et de la cendre, plus rare, qui se detache et tombe
        if (meteor.ticks % 3 == 0) {
            level.sendParticles(ModParticles.ASH_FLAKE.get(), hx, hy, hz, 2, 0.6, 0.6, 0.6, 0.0);
        }
    }

    private static void meteorImpact(ServerLevel level, BlockPos target) {
        level.playSound(null, target, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER,
                1.4F, 0.8F);
        // le grondement long qui suit : c'est lui qui donne l'echelle
        level.playSound(null, target, SoundEvents.WITHER_SPAWN, SoundSource.WEATHER, 0.6F, 0.4F);
        pulse(level, target, 0xFF7A2E, 0.85F, 1.6F, 60.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, 1, 0, 0, 0, 0);
        // L'ONDE DE CHOC : un anneau a plat qui s'ecarte du cratere en une
        // demi-seconde. L'explosion se voit, l'onde se RESSENT -- c'est elle
        // qui donne son poids au meteore.
        level.sendParticles(ModParticles.GROUND_SHOCK.get(),
                target.getX() + 0.5, target.getY() + 0.15, target.getZ() + 0.5, 1, 0, 0, 0, 0);
        // les braises projetees en gerbe, et la cendre qui retombe longtemps apres
        for (int i = 0; i < 28; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double sp = 0.15 + level.random.nextDouble() * 0.35;
            level.sendParticles(ModParticles.METEOR_EMBER.get(),
                    target.getX() + 0.5, target.getY() + 0.8, target.getZ() + 0.5,
                    0, Math.cos(a) * sp, 0.25 + level.random.nextDouble() * 0.3, Math.sin(a) * sp, 1.0);
        }
        level.sendParticles(ModParticles.ASH_FLAKE.get(),
                target.getX() + 0.5, target.getY() + 3.0, target.getZ() + 0.5,
                40, 3.0, 2.0, 3.0, 0.0);

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
                new ItemStack(ModItems.RAW_ARCENCIUM.get(), 3 + level.random.nextInt(3)));
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

    // ------------------------------------------ les secousses et les fissures

    /** Delai entre deux secousses, en ticks : 35 a 65 secondes. Rare, a dessein. */
    private static final int QUAKE_GAP_MIN = 700;
    private static final int QUAKE_GAP_SPAN = 600;
    /** Duree d'une secousse. */
    private static final int QUAKE_TICKS = 50;
    /** Trois secousses sur quatre ouvrent une fissure, jamais plus de trois a la fois. */
    private static final int FISSURE_CHANCE = 75;
    private static final int FISSURE_CAP = 3;
    /** Aucune fissure pres du village -- plus loin quand elle est grande : on n'eventre pas le refuge. */
    private static final double VILLAGE_GUARD_BASE = 12.0;

    /**
     * Les secousses de la Pluie de Meteores : de temps en temps, le sol tremble
     * pour tout le monde -- deux secondes et demie, un grondement, et de la
     * poussiere qui monte de la terre autour de chaque joueur. Parfois la
     * secousse ouvre une FISSURE (voir openFissure).
     *
     * C'est rare, a dessein : une camera qui tremble gene vite certains
     * joueurs. Une secousse toutes les 35 a 65 secondes, jamais dans les
     * dix premieres secondes de la meteo, et jamais au-dela d'une demi-
     * force : on sent le sol, on ne perd pas l'equilibre.
     */
    private static void tickQuakes(ServerLevel level) {
        long time = level.getGameTime();
        if (nextQuake < 0) {
            nextQuake = time + 200 + level.random.nextInt(300);
        }
        if (time >= nextQuake) {
            nextQuake = time + QUAKE_GAP_MIN + level.random.nextInt(QUAKE_GAP_SPAN);
            quakeTicks = QUAKE_TICKS;
            for (ServerPlayer player : level.players()) {
                // le grondement : le tonnerre rendu grave, c'est le sol qui parle
                level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.WEATHER, 1.8F, 0.45F);
            }
            if (level.random.nextInt(100) < FISSURE_CHANCE && fissures.size() < FISSURE_CAP
                    && !level.players().isEmpty()) {
                openFissure(level, level.players().get(level.random.nextInt(level.players().size())),
                        FissureTier.roll(level.random), false, null);
            }
        }
        if (quakeTicks > 0) {
            quakeTicks--;
            // la secousse monte puis retombe : une cloche, pas un mur
            float k = (float) Math.sin(Math.PI * (QUAKE_TICKS - quakeTicks) / (double) QUAKE_TICKS);
            if (quakeTicks % 4 == 0) {
                for (ServerPlayer player : level.players()) {
                    PacketDistributor.sendToPlayer(player,
                            new WeatherPulsePayload(0, 0, Math.round(50.0F * k)));
                }
            }
            if (quakeTicks % 2 == 0) {
                for (ServerPlayer player : level.players()) {
                    for (int i = 0; i < 3; i++) {
                        double a = level.random.nextDouble() * Math.PI * 2;
                        double d = 2.0 + level.random.nextDouble() * 9.0;
                        int x = (int) Math.floor(player.getX() + Math.cos(a) * d);
                        int z = (int) Math.floor(player.getZ() + Math.sin(a) * d);
                        level.sendParticles(ModParticles.QUAKE_DUST.get(),
                                x + 0.5, WorldSetup.surfaceY(level, x, z) + 0.1, z + 0.5,
                                1, 0.3, 0.0, 0.3, 0.0);
                    }
                    // la pierre qui craque, ici et la
                    if (level.random.nextInt(5) == 0) {
                        level.playSound(null, player.blockPosition().offset(
                                        level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4),
                                SoundEvents.STONE_BREAK, SoundSource.WEATHER, 0.7F,
                                0.5F + level.random.nextFloat() * 0.2F);
                    }
                }
            }
        }
    }

    /**
     * Une fissure s'ouvre pres d'un joueur : a six a quinze blocs -- plus loin
     * pour les grandes, personne ne doit etre dessus --, dans une direction
     * quelconque, d'une taille tiree au sort. Jamais sous un toit, jamais
     * pres du village. Elle s'annonce d'abord : la pierre craque et la fente
     * se dessine ; le sol ne cede qu'une seconde et demie plus tard.
     */
    private static boolean openFissure(ServerLevel level, ServerPlayer near, FissureTier tier,
                                       boolean force, @javax.annotation.Nullable Double angle) {
        // au hasard autour du joueur ; ou droit devant lui, pour l'essai
        double a = angle != null ? angle : level.random.nextDouble() * Math.PI * 2;
        double d = angle != null ? 8.0 + tier.widthMax
                : 6.0 + tier.widthMax + level.random.nextDouble() * 9.0;
        double x = near.getX() + Math.cos(a) * d;
        double z = near.getZ() + Math.sin(a) * d;
        int sx = (int) Math.floor(x);
        int sz = (int) Math.floor(z);
        BlockPos at = new BlockPos(sx, WorldSetup.surfaceY(level, sx, sz), sz);
        if (!level.isLoaded(at) || !level.canSeeSky(at)) {
            LOGGER.info("Fissure {} refusee en {} : sous un toit ou hors des chunks charges", tier, at);
            return false;
        }
        BlockPos village = GameState.get(level).village();
        double guard = VILLAGE_GUARD_BASE + tier.lengthMax;
        if (!force && village != null && village.distSqr(at) < guard * guard) {
            LOGGER.info("Fissure {} refusee en {} : trop pres du village", tier, at);
            return false;
        }
        Fissure f = new Fissure(x, z, (float) (level.random.nextDouble() * Math.PI * 2), tier,
                level.random);
        fissures.add(f);
        LOGGER.info("Fissure {} ouverte en {} : largeur {}, longueur {}, profondeur {}, {} ligne(s)",
                tier, at, String.format("%.1f", f.width), String.format("%.1f", f.length), f.depth,
                f.lines.size());
        level.playSound(null, at, SoundEvents.STONE_BREAK, SoundSource.WEATHER, 2.0F, 0.4F);
        level.playSound(null, at, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.WEATHER,
                0.7F, 0.35F);
        syncFissures(level);
        return true;
    }

    /**
     * Pour l'essai : ouvre une fissure pres du joueur, de la taille demandee
     * (petite, moyenne, grande, abime) ou tiree au sort, sans egard pour le
     * village. C'est la seule facon d'en voir une a coup sur.
     */
    public static boolean debugFissure(ServerLevel level, ServerPlayer player,
                                       @javax.annotation.Nullable String size) {
        FissureTier tier = null;
        if (size != null) {
            for (FissureTier t : FissureTier.values()) {
                if (t.name().equalsIgnoreCase(size)) {
                    tier = t;
                }
            }
        }
        // droit devant le joueur : la commande sert a VOIR la fissure
        double yaw = Math.toRadians(player.getYRot());
        double ahead = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
        return openFissure(level, player, tier == null ? FissureTier.roll(level.random) : tier, true,
                ahead);
    }

    /**
     * Les fissures vivent leur vie quelle que soit la meteo -- y compris
     * quand il n'y en a aucune : c'est WeatherManager qui appelle ceci a
     * chaque tick, avant meme de savoir s'il y a une tempete. Une fissure
     * ouverte a la fin des Meteores finit de s'effondrer ; celle de la
     * commande d'essai aussi.
     */
    static void tickFissures(ServerLevel level) {
        if (fissures.isEmpty()) {
            return;
        }
        boolean changed = false;
        Iterator<Fissure> it = fissures.iterator();
        while (it.hasNext()) {
            Fissure f = it.next();
            f.life++;
            if (f.life >= f.maxLife) {
                it.remove();
                changed = true;
                continue;
            }
            int bx = (int) Math.floor(f.x);
            int bz = (int) Math.floor(f.z);
            BlockPos at = new BlockPos(bx, WorldSetup.surfaceY(level, bx, bz), bz);
            if (f.life == FissureShape.COLLAPSE_AT) {
                // le sol cede : le grondement, et la secousse a la mesure de la taille
                level.playSound(null, at, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                        2.2F, 0.4F);
                pulse(level, at, 0xFF7A2E, 0.0F, f.shake, 30.0 + f.width * 10.0);
            }
            if (f.life >= FissureShape.COLLAPSE_AT
                    && f.life <= FissureShape.COLLAPSE_AT + FissureShape.CARVE_TICKS) {
                carve(level, f);
                if (f.life == FissureShape.COLLAPSE_AT + FissureShape.CARVE_TICKS) {
                    LOGGER.info("Fissure en {} : effondrement termine, {} bloc(s) retire(s)", at, f.removed);
                }
            }
            boolean deep = f.width >= 2.4F;
            if (deep && f.life > FissureShape.COLLAPSE_AT && f.life % 3 == 0
                    && f.maxLife - f.life > 40) {
                // les grandes chauffent : des braises montent du fond
                FissureShape.Line line = f.lines.get(0);
                double[] p = line.point(level.random.nextInt(FissureShape.POINTS));
                int px = (int) Math.floor(p[0]);
                int pz = (int) Math.floor(p[1]);
                level.sendParticles(ModParticles.METEOR_EMBER.get(),
                        p[0], WorldSetup.surfaceY(level, px, pz) + 0.15, p[1],
                        0, (level.random.nextDouble() - 0.5) * 0.02,
                        0.08 + level.random.nextDouble() * 0.06,
                        (level.random.nextDouble() - 0.5) * 0.02, 1.0);
            }
            if (deep && f.life > FissureShape.COLLAPSE_AT && f.life % 40 == 0) {
                level.playSound(null, at, SoundEvents.LAVA_AMBIENT, SoundSource.WEATHER, 0.8F, 0.6F);
            }
        }
        if (changed || level.getGameTime() % 20 == 0) {
            syncFissures(level);
        }
    }

    /**
     * L'effondrement : du centre vers les bouts, sur CARVE_TICKS, ligne par
     * ligne. Chaque point atteint creuse le sol autour de lui -- plus large et
     * plus profond au milieu de la fente qu'a ses bouts, et les bouts ne
     * s'ouvrent pas du tout : une vraie fissure finit en cheveu. On ne touche
     * qu'aux blocs que la meteo a le droit de briser (voir fragile), jamais a
     * l'eau, et jamais sous les pieds d'un joueur : le pont qui reste sous lui
     * vaut mieux qu'une chute qu'il n'a pas vue venir.
     */
    private static void carve(ServerLevel level, Fissure f) {
        double reach = (f.life - FissureShape.COLLAPSE_AT) / (double) FissureShape.CARVE_TICKS;
        for (int li = 0; li < f.lines.size(); li++) {
            FissureShape.Line line = f.lines.get(li);
            for (int i = 0; i < FissureShape.POINTS; i++) {
                double prog = line.progress(i);
                if (f.carved[li][i] || prog > reach || prog > FissureShape.CARVED_SPAN) {
                    continue;
                }
                f.carved[li][i] = true;
                double radius = line.width() * 0.5 * line.taper(i);
                if (radius < 0.35) {
                    continue;                  // trop fin pour s'ouvrir : reste une fente
                }
                radius = Math.max(0.5, radius);
                int depth = Math.max(1, (int) Math.round(line.depth() * line.depthAt(i)));
                // la ligne d'ici au point suivant, par pas d'un demi-bloc
                double[] p0 = line.point(i);
                double[] p1 = line.point(Math.min(i + 1, FissureShape.POINTS - 1));
                double segLen = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]);
                int steps = Math.max(1, (int) Math.ceil(segLen / 0.5));
                for (int st = 0; st <= steps; st++) {
                    double k = st / (double) steps;
                    f.removed += carveDisc(level, f, p0[0] + (p1[0] - p0[0]) * k,
                            p0[1] + (p1[1] - p0[1]) * k, radius, depth);
                }
                if (i == FissureShape.POINTS / 2 && li == 0) {
                    int cx = (int) Math.floor(p0[0]);
                    int cz = (int) Math.floor(p0[1]);
                    int top = WorldSetup.surfaceY(level, cx, cz) - 1;
                    BlockPos tp = new BlockPos(cx, top, cz);
                    LOGGER.info("Fissure : colonne centrale {} -> sommet {} ({}), fragile={}, rayon {}, profondeur {}",
                            tp, top, level.getBlockState(tp).getBlock(), fragile(level, tp),
                            String.format("%.2f", radius), depth);
                }
                // la poussiere qui monte du trou, et l'eboulis qu'on entend
                int px = (int) Math.floor(p0[0]);
                int pz = (int) Math.floor(p0[1]);
                level.sendParticles(ModParticles.QUAKE_DUST.get(),
                        p0[0], WorldSetup.surfaceY(level, px, pz) + 0.5, p0[1],
                        4, radius, 0.3, radius, 0.0);
                if (level.random.nextInt(3) == 0) {
                    level.playSound(null, new BlockPos(px, WorldSetup.surfaceY(level, px, pz), pz),
                            SoundEvents.GRAVEL_BREAK, SoundSource.WEATHER, 1.2F,
                            0.6F + level.random.nextFloat() * 0.3F);
                }
            }
        }
    }

    /**
     * Un disque de sol qui cede, en V : profond au milieu, a peine entame au
     * bord, si bien que les parois sont des gradins et non des murs. Des
     * gravats restent au fond, faits de ce qu'on vient d'oter ; quelques
     * pierres deplacees se posent sur la bordure.
     */
    private static int carveDisc(ServerLevel level, Fissure f, double cx, double cz, double radius,
                                 int depth) {
        int total = 0;
        int r = (int) Math.ceil(radius) + 1;
        int bcx = (int) Math.floor(cx);
        int bcz = (int) Math.floor(cz);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int bx = bcx + dx;
                int bz = bcz + dz;
                double d = Math.hypot(bx + 0.5 - cx, bz + 0.5 - cz);
                if (d > radius) {
                    if (d <= radius + 1.0 && level.random.nextInt(14) == 0) {
                        rimStone(level, bx, bz);
                    }
                    continue;
                }
                if (underfoot(level, bx, bz)) {
                    continue;
                }
                long key = BlockPos.asLong(bx, 0, bz);
                int[] col = f.columns.get(key);
                if (col == null) {
                    int top = WorldSetup.surfaceY(level, bx, bz) - 1;
                    if (!level.getBlockState(new BlockPos(bx, top, bz)).getFluidState().isEmpty()) {
                        f.columns.put(key, new int[]{top, Integer.MAX_VALUE});   // jamais l'eau
                        continue;
                    }
                    // ce qui COUVRE le sol sans le porter -- couche de neige, herbe,
                    // fleurs -- tombe avec lui : sinon une nappe de neige restait
                    // suspendue au-dessus du trou et le cachait tout entier
                    for (int y = top + 1; y <= top + 2; y++) {
                        BlockPos over = new BlockPos(bx, y, bz);
                        BlockState cover = level.getBlockState(over);
                        if (cover.isAir() || !fragile(level, over)) {
                            break;
                        }
                        if (cover.canBeReplaced() || !cover.isCollisionShapeFullBlock(level, over)) {
                            level.removeBlock(over, false);
                        } else {
                            break;
                        }
                    }
                    col = new int[]{top, 0};
                    f.columns.put(key, col);
                }
                // le profil en V : profond au milieu, a peine entame au bord --
                // et une colonne ne descend jamais plus bas que le disque le plus
                // exigeant qui la touche, mesure depuis sa surface d'origine
                int wanted = Math.max(1, (int) Math.ceil(depth * (1.0 - Math.pow(d / radius, 1.6))));
                if (wanted <= col[1]) {
                    continue;
                }
                int origTop = col[0];
                BlockState lowest = null;
                int removed = 0;
                for (int y = origTop - col[1]; y > origTop - wanted; y--) {
                    BlockPos pos = new BlockPos(bx, y, bz);
                    if (!fragile(level, pos)) {
                        break;                 // on s'arrete sur ce qu'on ne casse pas
                    }
                    BlockState state = level.getBlockState(pos);
                    if (level.random.nextInt(12) == 0) {
                        level.levelEvent(2001, pos, Block.getId(state));
                    }
                    level.removeBlock(pos, false);
                    lowest = state;
                    removed++;
                }
                col[1] = wanted;
                if (removed >= 2 && lowest != null && level.random.nextInt(5) == 0) {
                    level.setBlock(new BlockPos(bx, origTop - wanted + 1, bz), rubble(level, lowest), 3);
                }
                total += removed;
            }
        }
        return total;
    }

    /** Les gravats : de la matiere qu'on vient d'oter, sous sa forme brisee. */
    private static BlockState rubble(ServerLevel level, BlockState from) {
        if (from.is(Blocks.DEEPSLATE) || from.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        if (from.is(BlockTags.BASE_STONE_OVERWORLD) || from.is(BlockTags.STONE_ORE_REPLACEABLES)) {
            return Blocks.COBBLESTONE.defaultBlockState();
        }
        if (from.is(BlockTags.SAND)) {
            return from;
        }
        return level.random.nextBoolean() ? Blocks.COARSE_DIRT.defaultBlockState()
                : Blocks.GRAVEL.defaultBlockState();
    }

    /** Une pierre deplacee sur la bordure : posee sur un sol plein, et vanilla. */
    private static void rimStone(ServerLevel level, int bx, int bz) {
        BlockPos pos = new BlockPos(bx, WorldSetup.surfaceY(level, bx, bz), bz);
        BlockPos below = pos.below();
        if (!level.getBlockState(pos).isAir() || !fragile(level, below)
                || !level.getBlockState(below).isCollisionShapeFullBlock(level, below)) {
            return;
        }
        level.setBlock(pos, level.random.nextBoolean() ? Blocks.COBBLESTONE_SLAB.defaultBlockState()
                : Blocks.GRAVEL.defaultBlockState(), 3);
    }

    private static boolean underfoot(ServerLevel level, int bx, int bz) {
        for (ServerPlayer player : level.players()) {
            if (Math.hypot(player.getX() - (bx + 0.5), player.getZ() - (bz + 0.5)) < 2.5) {
                return true;
            }
        }
        return false;
    }

    private static void syncFissures(ServerLevel level) {
        List<FissureSyncPayload.Entry> entries = new ArrayList<>(fissures.size());
        for (Fissure f : fissures) {
            entries.add(new FissureSyncPayload.Entry(f.x, f.z, f.dir, f.length, f.width,
                    f.life, f.maxLife));
        }
        FissureSyncPayload payload = new FissureSyncPayload(entries);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
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
                ensureModifier(player, Attributes.GRAVITY, GRAVITY_ID, -0.78);
                for (Mob mob : level.getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(48))) {
                    ensureModifier(mob, Attributes.GRAVITY, GRAVITY_ID, -0.78);
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
            // Douze a vingt blocs etaient hors de portee meme en apesanteur : un
            // bond allege culmine vers quatre blocs, pas vingt. On les pose
            // juste au-dessus de ce qu'un saut normal atteint -- inaccessibles
            // sans la tempete, accessibles avec, ce qui est tout leur interet.
            int y = WorldSetup.surfaceY(level, x, z) + 4 + level.random.nextInt(4);
            int count = 4 + level.random.nextInt(3);
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
            // Un objet de trente centimetres a huit blocs de haut ne se
            // remarque pas tout seul. On le signale par ce que la Dechirure
            // fait de mieux : le SOL QUI MONTE VERS LUI -- une colonne de terre
            // et d'herbe qui decolle sous l'eclat et s'eleve jusqu'a lui. C'est
            // le vocabulaire de la meteo, pas un halo emprunte a une autre.
            double ground = WorldSetup.surfaceY(level, entity.getBlockX(), entity.getBlockZ());
            for (int i = 0; i < 3; i++) {
                level.sendParticles(ModParticles.FLOAT_DEBRIS.get(),
                        entity.getX(), ground + 0.2 + level.random.nextDouble() * 0.5, entity.getZ(),
                        1, 0.6, 0.0, 0.6, 0.0);
            }
            level.sendParticles(ModParticles.FLOAT_BLADE.get(),
                    entity.getX(), ground + 0.3, entity.getZ(), 2, 0.8, 0.0, 0.8, 0.0);
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
            rifts.add(new Rift(new BlockPos(x, WorldSetup.surfaceY(level, x, z), z), RIFT_LIFE));
        }
        Iterator<Rift> it = rifts.iterator();
        while (it.hasNext()) {
            Rift rift = it.next();
            if (--rift.life <= 0) {
                it.remove();
                continue;
            }
            BlockPos pos = rift.pos;
            drawRift(level, pos, rift.life);
            if (level.getGameTime() % 10 != 0) {
                continue;
            }
            syncRifts(level);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(pos).inflate(1.2, 2.0, 1.2))) {
                travelRift(level, entity);
            }
        }
    }

    /**
     * Une faille qui RESSEMBLE a une faille.
     *
     * Trois particules de portail par tick ne disaient rien : « j'ai devine
     * parce qu'on l'a fait ensemble, sinon je ne l'aurais jamais su ». On
     * dessine donc une porte -- un ovale vertical de deux blocs de large et
     * trois de haut, un tourbillon dedans, une colonne qui monte pour la
     * reperer de loin -- et on l'annonce a qui s'en approche.
     */
    private static void drawRift(ServerLevel level, BlockPos pos, int life) {
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 1.6;
        double cz = pos.getZ() + 0.5;

        // LA FAILLE ELLE-MEME EST DESSINEE PAR LE CLIENT, en geometrie : une
        // fente noire bordee d'une lueur (voir RiftRenderer). Le serveur ne
        // lui envoie plus des nuages de portail -- il lui envoie sa POSITION,
        // toutes les dix ticks (voir syncRifts). Ce qui reste ici est ce que
        // le sol fait autour : il se souleve, aspire vers la dechirure.
        if (level.getGameTime() % 3 == 0) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double r = 1.2 + level.random.nextDouble() * 2.0;
            level.sendParticles(ModParticles.FLOAT_DEBRIS.get(),
                    cx + Math.cos(a) * r, pos.getY() + 0.2, cz + Math.sin(a) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        if (level.getGameTime() % 60 == 0) {
            level.playSound(null, pos, SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT,
                    0.9F, 0.7F);
        }
        // l'invitation, a qui est assez pres pour la lire
        if (level.getGameTime() % 20 == 0) {
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(cx, cy, cz) < 64.0) {
                    player.displayClientMessage(net.minecraft.network.chat.Component
                            .translatable("game.emeraldweapons.rift.hint")
                            .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
                }
            }
        }
    }

    /** Duree de vie d'une faille, en ticks. Sert au fondu chez le client. */
    private static final int RIFT_LIFE = 900;

    /**
     * Envoie les failles au client, qui les dessine.
     *
     * La liste entiere, toutes les dix ticks, a tous les joueurs : quelques
     * failles a cinq nombres chacune, le cout est nul. Le client fait le fondu
     * a partir de l'age ; l'age est ce qui reste a vivre soustrait de la duree
     * totale, puisque le serveur compte a rebours.
     */
    private static void syncRifts(ServerLevel level) {
        java.util.List<com.emerald.network.RiftSyncPayload.Entry> entries =
                new java.util.ArrayList<>(rifts.size());
        for (Rift rift : rifts) {
            entries.add(new com.emerald.network.RiftSyncPayload.Entry(
                    rift.pos.getX() + 0.5, rift.pos.getY() + 1.6, rift.pos.getZ() + 0.5,
                    RIFT_LIFE - rift.life, RIFT_LIFE));
        }
        com.emerald.network.RiftSyncPayload payload =
                new com.emerald.network.RiftSyncPayload(entries);
        for (ServerPlayer player : level.players()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
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
        // l'arrivee : la terre decolle autour de qui vient d'etre recrache --
        // le vocabulaire de la Dechirure, pas celui d'un portail
        level.sendParticles(ModParticles.FLOAT_DEBRIS.get(),
                dest.getX() + 0.5, dest.getY() + 0.2, dest.getZ() + 0.5, 24, 0.9, 0.2, 0.9, 0.0);
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
     * L'Orage : la charge qui rampe au sol, et la Surcharge pour qui encaisse.
     *
     * Son identite, par opposition a la Nuit : la Nuit fait tomber des eclairs
     * colores du ciel ; l'Orage fait monter la decharge DU SOL. Une frappe
     * s'annonce par l'air qui se charge -- un souffle qui aspire, un son grave
     * qui enfle -- et, chez les clients, par des arcs qui convergent vers le
     * point, de plus en plus vite (voir StormArcRenderer). Pas de cercle, pas
     * de carillon : on lit ou ca va tomber a ce que l'electricite y court.
     *
     * C'est la seule meteo ou l'on CHERCHE a etre touche. Le Filtre de Brume
     * annule les degats mais laisse la Surcharge : s'exposer aux frappes
     * devient alors un style de jeu. Et la Surcharge se voit : celui qui la
     * porte gresille, et l'orage lui court autour du corps.
     */
    private static void tickOrage(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 40 == 0) {
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
                    // l'annonce : l'air aspire, et les arcs convergent
                    level.playSound(null, pos, SoundEvents.BREEZE_CHARGE, SoundSource.WEATHER,
                            1.6F, 0.55F);
                    tell(level, pos, new StormStrikePayload(pos.getX() + 0.5, pos.getY(),
                            pos.getZ() + 0.5, StormStrikePayload.WARN, 50), 96.0);
                }
            }
        }
        Iterator<Strike> it = strikes.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            strike.ticks--;
            if (strike.ticks == 24) {
                // la charge monte d'un cran : le son grave qui enfle
                level.playSound(null, strike.pos, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                        SoundSource.WEATHER, 1.2F, 1.5F);
            }
            if (strike.ticks <= 0) {
                it.remove();
                orageStrike(level, strike.pos);
            }
        }
        // la Surcharge se voit : etincelles sur le corps, arcs autour
        Iterator<Map.Entry<UUID, Long>> su = surcharged.entrySet().iterator();
        while (su.hasNext()) {
            Map.Entry<UUID, Long> e = su.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || now > e.getValue() || player.level() != level) {
                su.remove();
                continue;
            }
            if (now % 2 == 0) {
                level.sendParticles(ModParticles.STATIC_SPARK.get(),
                        player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.35, 0.6, 0.35, 0.0);
            }
            if (now % 12 == 0) {
                tell(level, player.blockPosition(), new StormStrikePayload(player.getX(),
                        player.getY(), player.getZ(), StormStrikePayload.CRACKLE, 0), 48.0);
            }
        }
    }

    /** Les joueurs en Surcharge, et jusqu'a quel tick. */
    private static final Map<UUID, Long> surcharged = new HashMap<>();

    /** Dit quelque chose aux clients assez pres pour que ca les concerne. */
    private static void tell(ServerLevel level, BlockPos at, StormStrikePayload payload,
                             double radius) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at.getX(), at.getY(), at.getZ()) <= radius * radius) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void orageStrike(ServerLevel level, BlockPos pos) {
        level.addFreshEntity(new ArcenciumBoltEntity(level,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                ArcenciumBoltEntity.Variant.ORAGE));
        // la decharge : le claquement du trident, puis le tonnerre
        level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.WEATHER,
                2.0F, 0.7F);
        level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                2.5F, 0.7F);
        pulse(level, pos, 0xE0B0FF, 1.0F, 1.2F, 55.0);
        // chez les clients : les arcs eclatent en etoile depuis le point
        tell(level, pos, new StormStrikePayload(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                StormStrikePayload.IMPACT, 0), 96.0);

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
                level.sendParticles(ModParticles.STATIC_SPARK.get(),
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        30, 0.5, 0.9, 0.5, 0.0);
                // et il gresille pendant toute la Surcharge (voir tickOrage)
                surcharged.put(player.getUUID(), level.getGameTime() + 600);
            }
        }
    }

}
