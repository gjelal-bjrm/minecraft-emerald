package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La derniere partie : l'Arc-en-ciel, son arene, son boss, et la fin.
 *
 * Quand la troisieme ancre est tenue, l'arene se leve a l'ecart des
 * sanctuaires : la Prison Givree de Cataclysm, posee par le meme chemin que
 * la commande /place -- ses pieces s'assemblent toutes seules, avec leurs
 * draugr. Elle se pose PAR MORCEAUX, quelques chunks par tick : d'un seul
 * coup, ses cent trois pieces figeaient le serveur cinq secondes. Un boss
 * tire au sort en garde le sommet, le Sculk grouille autour, et la Maree se
 * recentre sur l'arene : c'est la que tout se referme.
 *
 * Les identifiants des autres mods sont cites en texte et resolus a
 * l'execution : sans Cataclysm, le boss est un Wither sur une butte, et le
 * mode demarre quand meme.
 *
 * Victoire : le boss meurt. Defaite : le temps s'ecoule, la Maree a tout
 * recouvert (GameTicker). Les deux se disent en plein ecran, une fois.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Finale {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final String TAG_BOSS = "emeraldweapons_final_boss";
    public static final String TAG_GUARD = "emeraldweapons_final_guard";

    /** L'arene se leve a cette distance du village, entre deux sanctuaires. */
    private static final int ARENA_DISTANCE = 300;
    /** Jamais plus pres que cela d'un sanctuaire. */
    private static final int KEEP_FROM_ANCHORS = 200;
    private static final String ARENA_STRUCTURE = "cataclysm:frosted_prison";
    private static final String[] BOSSES = {
            "cataclysm:ignis",
            "cataclysm:ender_guardian",
            "twilightforest:lich",
    };
    /** Le Sculk, reserve a l'arene finale (cahier, section 5). */
    private static final String[] GUARDS = {
            "deeperdarker:sculk_snapper",
            "deeperdarker:sculk_centipede",
            "deeperdarker:sculk_leech",
            "deeperdarker:shattered",
    };
    /** Le Traqueur a sa propre barre de boss : un seul, en sentinelle. */
    private static final String SENTINEL = "deeperdarker:stalker";
    private static final int GUARDS_AT_START = 10;
    private static final int GUARD_CAP = 10;
    private static final int PRESSURE_EVERY = 900;
    /** Chunks de l'arene poses par tick : cent vingt chunks en une seconde environ. */
    private static final int CHUNKS_PER_TICK = 6;
    /** Les titres de fin restent cinq secondes : on doit avoir le temps de les lire. */
    private static final int END_TITLE_STAY = 100;

    /** L'arene en train de se poser, chunk par chunk. */
    @Nullable
    private static Pending pending;
    /** Le rappel de fin de partie, quelques secondes apres le titre. */
    private static long hintAt = -1L;

    private record Pending(StructureStart start, ChunkGenerator generator, ArrayDeque<ChunkPos> chunks,
                           BoundingBox box, BlockPos center, EntityType<?> bossType) {
    }

    private Finale() {
    }

    // ---------------------------------------------------------- le lever

    /**
     * Leve l'Arc-en-ciel. Sans site impose, l'arene se place entre deux
     * sanctuaires ; sans boss impose, il est tire au sort parmi ceux qui sont
     * installes. La structure se pose ensuite sur quelques ticks, puis le boss
     * et les gardes apparaissent.
     *
     * @return le centre de l'arene, ou null si l'identifiant de boss impose est inconnu
     */
    @Nullable
    public static BlockPos begin(ServerLevel level, @Nullable BlockPos site, @Nullable String bossId) {
        GameState state = GameState.get(level);
        EntityType<?> bossType = pickBoss(level, bossId);
        if (bossType == null) {
            return null;
        }
        BlockPos center = site != null ? site : chooseSite(level, state);
        center = new BlockPos(center.getX(), WorldSetup.surfaceY(level, center.getX(), center.getZ()),
                center.getZ());
        state.beginFinale(center, EntityType.getKey(bossType).toString(), level.getGameTime());

        GameManager.announce(level, "game.emeraldweapons.rainbow",
                "game.emeraldweapons.rainbow.sub", 0xB98CFF, END_TITLE_STAY);
        for (ServerPlayer player : level.players()) {
            double dx = center.getX() + 0.5 - player.getX();
            double dz = center.getZ() + 0.5 - player.getZ();
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.rainbow.at",
                            (int) Math.sqrt(dx * dx + dz * dz), cardinal(dx, dz))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.finale.boss",
                            bossType.getDescription())
                    .withStyle(ChatFormatting.RED));
            player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 0.9F, 0.7F);
            player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1.0F, 0.8F);
        }

        Pending prepared = prepareArena(level, center, bossType);
        if (prepared != null) {
            pending = prepared;                     // la pose continue au tick
            LOGGER.info("Arc-en-ciel leve en {} ; boss {} ; arene de {} pieces sur {} chunks",
                    center.toShortString(), EntityType.getKey(bossType),
                    prepared.start().getPieces().size(), prepared.chunks().size());
        } else {
            populate(level, raiseKnoll(level, center), center, bossType);
            LOGGER.info("Arc-en-ciel leve en {} ; boss {} ; sans arene",
                    center.toShortString(), EntityType.getKey(bossType));
        }
        return center;
    }

    /**
     * Entre deux sanctuaires, a 300 blocs du village : assez loin pour etre
     * un voyage, assez pres pour rester dans la Maree quand elle se referme.
     */
    private static BlockPos chooseSite(ServerLevel level, GameState state) {
        BlockPos village = state.village();
        List<BlockPos> anchors = state.anchors();
        if (anchors.size() >= 2) {
            for (int i = 0; i < anchors.size(); i++) {
                BlockPos a = anchors.get(i);
                BlockPos b = anchors.get((i + 1) % anchors.size());
                double ax = a.getX() - village.getX(), az = a.getZ() - village.getZ();
                double bx = b.getX() - village.getX(), bz = b.getZ() - village.getZ();
                double la = Math.hypot(ax, az), lb = Math.hypot(bx, bz);
                if (la < 1 || lb < 1) {
                    continue;
                }
                double mx = ax / la + bx / lb, mz = az / la + bz / lb;
                double lm = Math.hypot(mx, mz);
                if (lm < 0.05) {
                    continue;                          // ancres opposees : pas de bissectrice
                }
                BlockPos site = new BlockPos(
                        (int) Math.round(village.getX() + mx / lm * ARENA_DISTANCE), 0,
                        (int) Math.round(village.getZ() + mz / lm * ARENA_DISTANCE));
                boolean clear = true;
                for (BlockPos anchor : anchors) {
                    if (Math.hypot(anchor.getX() - site.getX(), anchor.getZ() - site.getZ())
                            < KEEP_FROM_ANCHORS) {
                        clear = false;
                    }
                }
                if (clear) {
                    return site;
                }
            }
        }
        // a defaut, plein est ; assez loin d'un sanctuaire eventuel pour ne pas le toucher
        return new BlockPos(village.getX() + ARENA_DISTANCE, 0, village.getZ());
    }

    /**
     * Assemble la Prison Givree comme le ferait /place structure, sans encore
     * la poser : la structure choisit ses pieces, on note les chunks a remplir.
     *
     * @return ce qu'il reste a poser, ou null si la structure n'existe pas ou
     *         refuse le site
     */
    @Nullable
    private static Pending prepareArena(ServerLevel level, BlockPos center, EntityType<?> bossType) {
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(ResourceLocation.parse(ARENA_STRUCTURE));
        if (structure == null) {
            return null;
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BlockPos[] tries = {center, center.offset(48, 0, 0), center.offset(0, 0, 48),
                center.offset(-48, 0, 0), center.offset(0, 0, -48)};
        for (BlockPos attempt : tries) {
            StructureStart start = structure.generate(level.registryAccess(), generator,
                    generator.getBiomeSource(), level.getChunkSource().randomState(),
                    level.getStructureManager(), level.getSeed(), new ChunkPos(attempt), 0,
                    level, biome -> true);
            if (!start.isValid()) {
                continue;
            }
            BoundingBox box = start.getBoundingBox();
            ChunkPos from = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()),
                    SectionPos.blockToSectionCoord(box.minZ()));
            ChunkPos to = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()),
                    SectionPos.blockToSectionCoord(box.maxZ()));
            ArrayDeque<ChunkPos> chunks = new ArrayDeque<>();
            ChunkPos.rangeClosed(from, to).forEach(chunks::add);
            return new Pending(start, generator, chunks, box, center, bossType);
        }
        LOGGER.warn("La Prison Givree refuse de se poser pres de {}", center.toShortString());
        return null;
    }

    /** Quelques chunks de l'arene par tick ; le boss vient quand tout est pose. */
    private static void tickPlacement(ServerLevel level) {
        Pending job = pending;
        if (job == null) {
            return;
        }
        for (int i = 0; i < CHUNKS_PER_TICK && !job.chunks().isEmpty(); i++) {
            ChunkPos chunk = job.chunks().poll();
            level.getChunk(chunk.x, chunk.z);        // force le chargement, comme /place
            job.start().placeInChunk(level, level.structureManager(), job.generator(),
                    level.getRandom(),
                    new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(),
                            chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                            level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk);
        }
        if (!job.chunks().isEmpty()) {
            return;
        }
        pending = null;
        LOGGER.info("Prison Givree posee, emprise {}", job.box());
        populate(level, summitOf(level, job.box()), job.center(), job.bossType());
    }

    /** Le boss a sa place, le Sculk autour. */
    private static void populate(ServerLevel level, BlockPos perch, BlockPos center,
                                 EntityType<?> bossType) {
        spawnBoss(level, perch, bossType);
        spawnGuards(level, center, GUARDS_AT_START);
    }

    /** Le point le plus haut de l'arene, pres de son centre : la place du boss. */
    private static BlockPos summitOf(ServerLevel level, BoundingBox box) {
        int cx = box.getCenter().getX();
        int cz = box.getCenter().getZ();
        BlockPos best = null;
        for (int dx = -20; dx <= 20; dx += 2) {
            for (int dz = -20; dz <= 20; dz += 2) {
                int x = cx + dx, z = cz + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (best == null || y > best.getY()) {
                    best = new BlockPos(x, y, z);
                }
            }
        }
        return best == null ? box.getCenter() : best;
    }

    /** Sans structure : une butte de pierre pour que le boss ne naisse pas dans un arbre. */
    private static BlockPos raiseKnoll(ServerLevel level, BlockPos center) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) {
                    continue;
                }
                BlockPos pos = center.offset(dx, 0, dz);
                level.setBlockAndUpdate(pos, Blocks.DEEPSLATE_TILES.defaultBlockState());
                for (int h = 1; h <= 4; h++) {
                    level.setBlockAndUpdate(pos.above(h), Blocks.AIR.defaultBlockState());
                }
            }
        }
        return center.above();
    }

    // ---------------------------------------------------------- le boss

    @Nullable
    private static EntityType<?> pickBoss(ServerLevel level, @Nullable String forced) {
        if (forced != null && !forced.isEmpty()) {
            return EntityType.byString(forced).orElse(null);
        }
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : BOSSES) {
            EntityType.byString(id).ifPresent(pool::add);
        }
        return pool.isEmpty() ? EntityType.WITHER : pool.get(level.random.nextInt(pool.size()));
    }

    private static void spawnBoss(ServerLevel level, BlockPos perch, EntityType<?> type) {
        BlockPos at = perch;
        // deux blocs d'air au-dessus du perchoir, sinon on monte
        for (int i = 0; i < 6 && !level.getBlockState(at).isAir(); i++) {
            at = at.above();
        }
        Entity boss = type.spawn(level, at, MobSpawnType.EVENT);
        if (boss == null) {
            return;
        }
        boss.addTag(TAG_BOSS);
        if (boss instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1.5, boss.getZ(),
                80, 1.5, 2.0, 1.5, 0.08);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.75F, 0.55F, 1.0F), 2.0F),
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 120, 3.0, 2.5, 3.0, 0.02);
        level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.6F);
    }

    // ---------------------------------------------------------- les gardes

    private static void spawnGuards(ServerLevel level, BlockPos center, int count) {
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = 22 + level.random.nextDouble() * 24;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
            spawnGuard(level, new BlockPos(x, WorldSetup.surfaceY(level, x, z), z),
                    i == 0 ? sentinelType(level) : guardType(level));
        }
    }

    private static void spawnGuard(ServerLevel level, BlockPos spot, @Nullable EntityType<?> type) {
        if (type == null || !level.isLoaded(spot)) {
            return;
        }
        Entity guard = type.spawn(level, spot, MobSpawnType.EVENT);
        if (guard == null) {
            return;
        }
        guard.addTag(TAG_GUARD);
        if (guard instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, guard.getX(), guard.getY() + 0.8,
                guard.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
    }

    @Nullable
    private static EntityType<?> guardType(ServerLevel level) {
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : GUARDS) {
            EntityType.byString(id).ifPresent(pool::add);
        }
        if (pool.isEmpty()) {
            EntityType<?>[] fallback = SiegeRoster.vanillaFallback(3);
            return fallback.length == 0 ? null : fallback[level.random.nextInt(fallback.length)];
        }
        return pool.get(level.random.nextInt(pool.size()));
    }

    @Nullable
    private static EntityType<?> sentinelType(ServerLevel level) {
        return EntityType.byString(SENTINEL).orElse(guardType(level));
    }

    /**
     * L'arene reste gardee tant que le boss vit : quand les joueurs y sont et
     * que les gardes se font rares, il en vient d'autres, pres d'eux.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        tickPlacement(level);
        if (hintAt >= 0L && level.getGameTime() >= hintAt) {
            hintAt = -1L;
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(Component.translatable("game.emeraldweapons.end.hint")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (level.getGameTime() % PRESSURE_EVERY != 0) {
            return;
        }
        GameState state = GameState.get(level);
        BlockPos center = state.finale();
        if (center.equals(BlockPos.ZERO) || state.status() != GameState.Status.RUNNING) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.getX() + 0.5, player.getY(), center.getZ() + 0.5) > 120.0 * 120.0) {
                continue;
            }
            long guards = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(90.0), e -> e.getTags().contains(TAG_GUARD)).size();
            if (guards >= GUARD_CAP) {
                continue;
            }
            int wanted = 2 + level.random.nextInt(2);
            for (int i = 0; i < wanted; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double dist = 18 + level.random.nextDouble() * 10;
                int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
                int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
                spawnGuard(level, new BlockPos(x, WorldSetup.surfaceY(level, x, z), z), guardType(level));
            }
        }
    }

    // ---------------------------------------------------------- la fin

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || !event.getEntity().getTags().contains(TAG_BOSS)) {
            return;
        }
        victory(level);
    }

    /** Le boss est tombe : titre, feux d'artifice, les gardes se dissipent. */
    public static void victory(ServerLevel level) {
        GameState state = GameState.get(level);
        String time = clock(state.elapsed(level));
        if (state.status() == GameState.Status.RUNNING) {
            state.finish(true);
        }
        GameManager.announce(level, "game.emeraldweapons.won",
                "game.emeraldweapons.won.sub", 0xFFD36B, END_TITLE_STAY);
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.won.chat", time)
                    .withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < 3; i++) {
                fireworks(level, player.getX() + (level.random.nextDouble() - 0.5) * 6.0,
                        player.getY() + 1.0, player.getZ() + (level.random.nextDouble() - 0.5) * 6.0);
            }
        }
        dissolveGuards(level);
        hintAt = level.getGameTime() + 100L;
    }

    /** Le temps est ecoule : la Maree a tout recouvert. */
    public static void defeat(ServerLevel level) {
        GameState state = GameState.get(level);
        if (state.status() == GameState.Status.RUNNING) {
            state.finish(false);
        }
        GameManager.announce(level, "game.emeraldweapons.lost",
                "game.emeraldweapons.lost.sub", 0xB98CFF, END_TITLE_STAY);
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0F, 0.5F);
            player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0F, 0.6F);
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.lost.chat")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        hintAt = level.getGameTime() + 100L;
    }

    /** Un arret de partie abandonne l'arene en cours de pose. */
    public static void clear() {
        pending = null;
        hintAt = -1L;
    }

    private static void dissolveGuards(ServerLevel level) {
        for (Entity entity : level.getEntities().getAll()) {
            if (entity.getTags().contains(TAG_GUARD) && entity.isAlive()) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, entity.getX(), entity.getY() + 0.8,
                        entity.getZ(), 16, 0.4, 0.6, 0.4, 0.03);
                entity.discard();
            }
        }
    }

    private static void fireworks(ServerLevel level, double x, double y, double z) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(0xFF6B6B, 0xFFD36B, 0x78E8AE, 0x9CE8FF, 0xB98CFF),
                IntList.of(0xFFFFFF), true, true))));
        level.addFreshEntity(new FireworkRocketEntity(level, x, y, z, rocket));
    }

    private static String clock(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    /** Direction cardinale, dans la convention de la boussole du jeu (sud = 0). */
    static String cardinal(double dx, double dz) {
        String[] names = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        int index = (int) Math.round(((angle + 360.0) % 360.0) / 45.0) % 8;
        return names[(index + 6) % 8];
    }
}
