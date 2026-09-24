package com.emerald.weather;

import com.emerald.block.ArcPortals;
import com.emerald.block.EclipsePortalBlock;
import com.emerald.block.ModBlocks;
import com.emerald.game.GamePhase;
import com.emerald.game.GameState;
import com.emerald.item.ModItems;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'ECLIPSE, la meteo d'horreur (24 sept. 2026, cahier §88).
 *
 * « Une meteo un peu speciale avec le theme horreur, durant laquelle il ferait sombre avec
 * un brouillard, et des monstres avec un theme horreur, qui n'apparaitront que pendant
 * cette meteo, nulle part ailleurs. Des portails sombres, et on supposerait que les
 * monstres passent a travers. Les joueurs ne pourraient pas les utiliser. » Puis : « pas
 * des bouches -- rien de vivant, de vrais portails d'horreur » ; « fermer un portail en
 * tuant sa vague, qui donne des eclats » ; « a la fin, les portails implosent ».
 *
 * TROIS PORTAILS PAR JOUEUR, a 26-44 blocs de lui, jamais a moins de 48 blocs du village
 * (EclipsePortalBlock, place trouvee comme pour les arches : ArcPortals.find). Chacun lache
 * UNE VAGUE d'horreurs, une a une, toutes les 1,5 s. Quand la vague est morte, le portail
 * se referme et laisse ses Eclats du Destin. S'il n'est pas referme en 75 s, il en lache
 * une autre : ignorer un portail, c'est le laisser peupler la nuit. A la fin de l'Eclipse,
 * les portails encore ouverts IMPLOSENT et toutes les horreurs se dissolvent.
 *
 * LE VERROU. Les horreurs (tag emeraldweapons:eclipse_horrors) n'apparaissent QUE par les
 * portails : toute autre apparition (naturelle, structure, cage, patrouille, horde) est
 * annulee, pendant l'Eclipse comme en dehors. Seules exceptions : la commande et l'oeuf,
 * pour les essais, et -- pendant l'Eclipse -- ce qu'une horreur appelle elle-meme. Une
 * horreur de l'Eclipse sauvegardee dans un troncon (un joueur parti en pleine Eclipse)
 * ne revient pas au rechargement.
 *
 * Rien de ceci ne se sauvegarde : un redemarrage en pleine Eclipse ferme tout, et les
 * blocs des portails s'effacent d'eux-memes (EclipsePortalBlockEntity).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Eclipse {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Marque les horreurs sorties d'un portail : elles paient comme la tempete, et se dissolvent a la fin. */
    public static final String TAG = "emeraldweapons_eclipse_born";
    public static final TagKey<EntityType<?>> HORRORS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "eclipse_horrors"));

    public static final int PER_PLAYER = 3;
    private static final double RING_MIN = 26.0;
    private static final double RING_MAX = 44.0;
    private static final double VILLAGE_CLEAR = 48.0;
    private static final double RIFT_SPACING = 12.0;
    /** Une horreur toutes les 1,5 s ; la premiere trois secondes apres l'ouverture. */
    private static final int EMERGE_EVERY = 30;
    private static final int FIRST_EMERGE = 60;
    /** Une vague de plus si le portail n'est pas referme a temps. */
    private static final int WAVE_TIMEOUT = 20 * 75;
    /** Jamais plus de dix horreurs vivantes par portail. */
    private static final int MAX_ALIVE = 10;
    public static final int SHARDS = 4;
    private static final int HERO_XP = 40;
    /** Pres d'un portail ouvert, la nuit se referme : l'Obscurite du Gardien, par bouffees. */
    private static final double DARK_RADIUS = 20.0;

    /**
     * Les horreurs d'une vague, et leur poids. The Graveyard d'abord (goules, revenants,
     * squelettes-creepers, spectres, acolytes et illageois corrompus), le Murmur d'Alex's
     * Mobs ; en fin de vague, a partir de la Pression, une horreur d'elite : la Faucheuse,
     * le Cauchemar, le Farseer. Un mod absent, et ses monstres sont simplement sautes.
     */
    private static final String[][] COMMON = {
            {"graveyard:ghoul", "5"}, {"graveyard:revenant", "4"}, {"graveyard:skeleton_creeper", "3"},
            {"graveyard:wraith", "3"}, {"graveyard:acolyte", "2"}, {"graveyard:corrupted_vindicator", "2"},
            {"graveyard:corrupted_pillager", "2"}, {"alexsmobs:murmur", "2"}};
    private static final String[][] ELITE = {
            {"graveyard:reaper", "3"}, {"graveyard:nightmare", "2"}, {"alexsmobs:farseer", "1"}};
    /** Si aucun mod d'horreur n'est la : de quoi que l'Eclipse ne soit pas vide. */
    private static final String[][] FALLBACK = {{"minecraft:wither_skeleton", "2"}, {"minecraft:stray", "3"}};

    private static final class Rift {
        final BlockPos pos;
        final Direction facing;
        final List<UUID> alive = new ArrayList<>();
        int toSpawn;
        boolean elite;
        int cooldown = FIRST_EMERGE;
        int sinceWave;
        int waves = 1;
        boolean hadAny;

        Rift(BlockPos pos, Direction facing) {
            this.pos = pos;
            this.facing = facing;
        }
    }

    private static final Map<BlockPos, Rift> RIFTS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_BURN = new HashMap<>();
    private static boolean active;
    /** Vrai le temps d'une de NOS apparitions : elle passe le verrou. */
    private static boolean spawning;
    private static boolean warnedEmpty;

    private Eclipse() {
    }

    public static boolean active() {
        return active;
    }

    /** L'Eclipse connait-elle ce portail ? Sinon son bloc s'efface. */
    public static boolean isRift(BlockPos pos) {
        return active && RIFTS.containsKey(pos);
    }

    public static List<BlockPos> rifts() {
        return new ArrayList<>(RIFTS.keySet());
    }

    /** Les horreurs vivantes sorties de ce portail (pour le banc). */
    public static List<Entity> aliveAt(ServerLevel level, BlockPos pos) {
        List<Entity> out = new ArrayList<>();
        Rift rift = RIFTS.get(pos);
        if (rift != null) {
            for (UUID id : rift.alive) {
                Entity e = level.getEntity(id);
                if (e != null && e.isAlive()) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    // ================================================================ ouverture

    static void begin(ServerLevel level) {
        start();
        BlockPos village = GameState.get(level).village();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                openAround(level, player.blockPosition(), village, PER_PLAYER);
            }
        }
        LOGGER.info("Eclipse : {} portails ouverts", RIFTS.size());
    }

    /** L'Eclipse commence sans ouvrir de portail (le banc, les photos, puis openAround / open). */
    public static void start() {
        active = true;
        RIFTS.clear();
        LAST_BURN.clear();
    }

    /**
     * Ouvre jusqu'a « count » portails autour d'un point, a 26-44 blocs, loin du village
     * et a douze blocs au moins l'un de l'autre. Si la place manque, on elargit un peu.
     *
     * @return combien se sont ouverts
     */
    public static int openAround(ServerLevel level, BlockPos around, @Nullable BlockPos village, int count) {
        int opened = 0;
        for (int i = 0; i < count; i++) {
            ArcPortals.Placement spot = null;
            for (int attempt = 0; attempt < 6 && spot == null; attempt++) {
                double extra = attempt * 6.0;
                ArcPortals.Placement found = ArcPortals.find(level, around, RING_MIN + extra, RING_MAX + extra,
                        6, 10, 24, around, level.random);
                if (found == null) {
                    continue;
                }
                if (village != null && !village.equals(BlockPos.ZERO)
                        && found.anchor().closerThan(village, VILLAGE_CLEAR)) {
                    continue;                       // jamais dans le village ni a ses portes
                }
                if (crowded(found.anchor())) {
                    continue;
                }
                spot = found;
            }
            if (spot != null && open(level, spot.anchor(), spot.facing())) {
                opened++;
            }
        }
        return opened;
    }

    private static boolean crowded(BlockPos pos) {
        for (BlockPos other : RIFTS.keySet()) {
            if (other.closerThan(pos, RIFT_SPACING)) {
                return true;
            }
        }
        return false;
    }

    /** Ouvre un portail ici, tourne vers « facing ». Faux si l'Eclipse n'est pas en cours. */
    public static boolean open(ServerLevel level, BlockPos anchor, Direction facing) {
        if (!active) {
            return false;
        }
        Direction side = facing.getClockWise();
        // les herbes du passage s'en vont : sinon elles traversent le vide
        for (int l = -1; l <= 1; l++) {
            for (int y = 0; y <= 4; y++) {
                BlockPos pos = anchor.relative(side, l).above(y);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && state.canBeReplaced() && state.getFluidState().isEmpty()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(anchor, ModBlocks.ECLIPSE_PORTAL.get().defaultBlockState()
                .setValue(EclipsePortalBlock.FACING, facing), 3);
        Rift rift = new Rift(anchor.immutable(), facing);
        newWave(level, rift);
        RIFTS.put(rift.pos, rift);
        level.playSound(null, anchor, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 1.4F, 0.5F);
        level.playSound(null, anchor, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 0.9F, 0.45F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, anchor.getX() + 0.5, anchor.getY() + 2.2,
                anchor.getZ() + 0.5, 40, 0.8, 1.4, 0.3, 0.02);
        return true;
    }

    private static void newWave(ServerLevel level, Rift rift) {
        int tier = tier(level);
        rift.toSpawn = switch (tier) {
            case 3 -> 7;
            case 2 -> 6;
            default -> 4;
        };
        rift.elite = tier >= 2;                     // la derniere de la vague, a partir de la Pression
        rift.sinceWave = 0;
    }

    private static int tier(ServerLevel level) {
        GamePhase phase = GameState.get(level).phase(level);
        return switch (phase) {
            case ASSAUT -> 3;
            case PRESSION -> 2;
            default -> 1;
        };
    }

    // ==================================================================== tique

    static void tick(ServerLevel level) {
        if (!active) {
            return;
        }
        List<Rift> closed = new ArrayList<>();
        List<Rift> lost = new ArrayList<>();
        for (Rift rift : RIFTS.values()) {
            if (!level.isLoaded(rift.pos)) {
                continue;                           // jamais de chargement force
            }
            if (!level.getBlockState(rift.pos).is(ModBlocks.ECLIPSE_PORTAL.get())) {
                lost.add(rift);                     // le bloc n'est plus la (une commande, un essai)
                continue;
            }
            rift.alive.removeIf(id -> {
                Entity e = level.getEntity(id);
                return e == null || !e.isAlive();
            });
            rift.sinceWave++;
            if (rift.toSpawn > 0 && --rift.cooldown <= 0 && rift.alive.size() < MAX_ALIVE) {
                emerge(level, rift);
                rift.cooldown = EMERGE_EVERY;
            }
            if (rift.toSpawn == 0 && rift.alive.isEmpty()) {
                closed.add(rift);                   // la vague est morte : le portail se referme
            } else if (rift.toSpawn == 0 && rift.sinceWave >= WAVE_TIMEOUT) {
                newWave(level, rift);               // ignore : il en lache une autre
                rift.waves++;
                rift.cooldown = EMERGE_EVERY;
            }
            if (level.getGameTime() % 4 == 0) {
                ambience(level, rift);
            }
        }
        for (Rift rift : lost) {
            RIFTS.remove(rift.pos);
        }
        for (Rift rift : closed) {
            RIFTS.remove(rift.pos);
            implode(level, rift, rift.hadAny);
        }
        repel(level);
        if (level.getGameTime() % 40 == 0) {
            darken(level);
        }
    }

    /** Une horreur sort du portail, dans un souffle de fumee noire. */
    private static void emerge(ServerLevel level, Rift rift) {
        boolean elite = rift.elite && rift.toSpawn == 1;
        rift.toSpawn--;
        EntityType<?> type = pick(level, elite);
        if (type == null) {
            return;
        }
        Vec3 out = Vec3.atBottomCenterOf(rift.pos).add(rift.facing.getStepX() * 1.6, 0.0, rift.facing.getStepZ() * 1.6);
        BlockPos at = BlockPos.containing(out);
        Entity entity;
        spawning = true;
        try {
            entity = type.spawn(level, at, MobSpawnType.EVENT);
        } finally {
            spawning = false;
        }
        if (entity == null) {
            return;
        }
        entity.addTag(TAG);
        entity.addTag(WeatherEffects.TAG_STORM);    // elles paient comme les monstres de tempete
        float yaw = rift.facing.toYRot();
        entity.setYRot(yaw);
        if (entity instanceof Mob mob) {
            mob.setYHeadRot(yaw);
            mob.setYBodyRot(yaw);
        }
        rift.alive.add(entity.getUUID());
        rift.hadAny = true;
        level.sendParticles(ParticleTypes.LARGE_SMOKE, out.x, out.y + 1.0, out.z, 18, 0.35, 0.7, 0.35, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, out.x, out.y + 1.2, out.z, 10, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, at, SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.3F, 0.5F);
        level.playSound(null, at, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.7F, 0.45F);
    }

    @Nullable
    private static EntityType<?> pick(ServerLevel level, boolean elite) {
        EntityType<?> type = elite ? weighted(level, ELITE) : null;
        if (type == null) {
            type = weighted(level, COMMON);
        }
        if (type == null) {
            if (!warnedEmpty) {
                warnedEmpty = true;
                LOGGER.warn("Eclipse : aucun monstre d'horreur installe (The Graveyard, Alex's Mobs) ; repli sur le jeu");
            }
            type = weighted(level, FALLBACK);
        }
        return type;
    }

    @Nullable
    private static EntityType<?> weighted(ServerLevel level, String[][] table) {
        List<EntityType<?>> types = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (String[] row : table) {
            var found = EntityType.byString(row[0]);
            if (found.isPresent()) {
                int w = Integer.parseInt(row[1]);
                types.add(found.get());
                weights.add(w);
                total += w;
            }
        }
        if (total == 0) {
            return null;
        }
        int roll = level.random.nextInt(total);
        for (int i = 0; i < types.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return types.get(i);
            }
        }
        return types.get(types.size() - 1);
    }

    /** Le portail aspire : cendres et fumee qui tombent vers le vide. */
    private static void ambience(ServerLevel level, Rift rift) {
        double cx = rift.pos.getX() + 0.5;
        double cz = rift.pos.getZ() + 0.5;
        Direction side = rift.facing.getClockWise();
        for (int i = 0; i < 2; i++) {
            double lateral = (level.random.nextDouble() - 0.5) * 2.4;
            double y = rift.pos.getY() + 0.3 + level.random.nextDouble() * 4.0;
            double ahead = 0.6 + level.random.nextDouble() * 1.4;
            double x = cx + side.getStepX() * lateral + rift.facing.getStepX() * ahead;
            double z = cz + side.getStepZ() * lateral + rift.facing.getStepZ() * ahead;
            // une vitesse vers le plan du portail : la fumee y est aspiree
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 0,
                    -rift.facing.getStepX() * 0.06, 0.0, -rift.facing.getStepZ() * 0.06, 1.0);
        }
        if (level.random.nextInt(3) == 0) {
            level.sendParticles(ParticleTypes.ASH, cx, rift.pos.getY() + 2.0, cz, 6, 1.2, 1.6, 1.2, 0.0);
        }
    }

    /**
     * ON NE TRAVERSE PAS. Qui entre dans le vide est rejete du cote d'ou il vient, blesse
     * et aveugle un instant. Les horreurs, elles, en sortent librement.
     */
    private static void repel(ServerLevel level) {
        for (Rift rift : RIFTS.values()) {
            Vec3 c = Vec3.atBottomCenterOf(rift.pos);
            Direction side = rift.facing.getClockWise();
            double hx = Math.abs(side.getStepX()) * (EclipsePortalBlock.VOID_HALF + 0.1)
                    + Math.abs(rift.facing.getStepX()) * 0.45;
            double hz = Math.abs(side.getStepZ()) * (EclipsePortalBlock.VOID_HALF + 0.1)
                    + Math.abs(rift.facing.getStepZ()) * 0.45;
            AABB veil = new AABB(c.x - hx, c.y, c.z - hz, c.x + hx, c.y + EclipsePortalBlock.VOID_TOP, c.z + hz);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, veil,
                    p -> !p.isSpectator() && !p.isCreative())) {
                double along = (player.getX() - c.x) * rift.facing.getStepX()
                        + (player.getZ() - c.z) * rift.facing.getStepZ();
                double sign = along >= 0 ? 1.0 : -1.0;
                player.setDeltaMovement(rift.facing.getStepX() * 0.9 * sign, 0.35, rift.facing.getStepZ() * 0.9 * sign);
                player.hurtMarked = true;
                long now = level.getGameTime();
                Long last = LAST_BURN.get(player.getUUID());
                if (last == null || now - last >= 10) {
                    LAST_BURN.put(player.getUUID(), now);
                    player.hurt(level.damageSources().magic(), 3.0F);
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
                    level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.HOSTILE,
                            1.0F, 0.5F);
                }
            }
        }
    }

    /** Pres d'un portail ouvert, l'Obscurite revient par bouffees. */
    private static void darken(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            for (BlockPos pos : RIFTS.keySet()) {
                if (player.blockPosition().closerThan(pos, DARK_RADIUS)) {
                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 70, 0, true, false, false));
                    break;
                }
            }
        }
    }

    // ================================================================ fermeture

    /**
     * Le portail implose : la fumee retourne au vide, le cadre s'efface. Refermé par sa
     * vague morte, il laisse ses Eclats du Destin et de l'experience aux joueurs proches ;
     * a la fin de l'Eclipse, il implose sans rien laisser.
     */
    private static void implode(ServerLevel level, Rift rift, boolean rewarded) {
        Vec3 c = Vec3.atBottomCenterOf(rift.pos).add(0.0, 2.2, 0.0);
        if (level.getBlockState(rift.pos).is(ModBlocks.ECLIPSE_PORTAL.get())) {
            level.setBlock(rift.pos, Blocks.AIR.defaultBlockState(), 3);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 70, 0.9, 1.6, 0.9, 0.35);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.6, 1.2, 0.6, 0.06);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 24, 0.5, 1.0, 0.5, 0.05);
        level.playSound(null, rift.pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.HOSTILE, 1.6F, 0.4F);
        level.playSound(null, rift.pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2F, 0.5F);
        if (!rewarded) {
            return;
        }
        ItemEntity shards = new ItemEntity(level, c.x, c.y - 1.5, c.z, new ItemStack(ModItems.FATE_SHARD.get(), SHARDS));
        shards.setDefaultPickUpDelay();
        level.addFreshEntity(shards);
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.position().closerThan(c, 32.0)) {
                com.emerald.hero.HeroEvents.award(player, HERO_XP);
                player.displayClientMessage(Component.translatable("eclipse.emeraldweapons.closed", SHARDS)
                        .withStyle(ChatFormatting.DARK_RED), true);
            }
        }
        LOGGER.info("Eclipse : portail {} referme apres {} vague(s)", rift.pos, rift.waves);
    }

    /** La fin de l'Eclipse : les portails implosent, les horreurs se dissolvent. */
    static void end(ServerLevel level) {
        for (Rift rift : new ArrayList<>(RIFTS.values())) {
            implode(level, rift, false);
        }
        RIFTS.clear();
        dissolve(level);
        active = false;
        LAST_BURN.clear();
    }

    /** @return combien d'horreurs se sont dissoutes */
    public static int dissolve(ServerLevel level) {
        List<Entity> gone = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(TAG)) {
                gone.add(entity);
            }
        }
        for (Entity entity : gone) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5,
                    entity.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            entity.discard();
        }
        return gone.size();
    }

    /** Pour le banc et l'arret du serveur : tout oublier, sans rien toucher au monde. */
    public static void clearAll() {
        RIFTS.clear();
        LAST_BURN.clear();
        active = false;
    }

    /** Pour le banc : une tique d'Eclipse, hors de la meteo. */
    public static void tickForAutotest(ServerLevel level) {
        tick(level);
    }

    /** Pour le banc et les photos : la fin de l'Eclipse, hors de la meteo. */
    public static void endForAutotest(ServerLevel level) {
        end(level);
    }

    // =================================================================== verrou

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        Mob mob = event.getEntity();
        if (spawning || !mob.getType().is(HORRORS)) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.COMMAND || type == MobSpawnType.SPAWN_EGG
                || type == MobSpawnType.DISPENSER || type == MobSpawnType.BUCKET) {
            return;                                 // les essais a la main
        }
        if (active && (type == MobSpawnType.MOB_SUMMONED || type == MobSpawnType.REINFORCEMENT
                || type == MobSpawnType.JOCKEY || type == MobSpawnType.CONVERSION)) {
            return;                                 // une horreur de l'Eclipse en appelle d'autres
        }
        event.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk() || active) {
            return;
        }
        if (event.getEntity().getTags().contains(TAG)) {
            event.setCanceled(true);                // une horreur d'une Eclipse passee ne revient pas
        }
    }
}
