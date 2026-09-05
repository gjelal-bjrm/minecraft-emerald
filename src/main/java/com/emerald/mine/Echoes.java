package com.emerald.mine;

import com.emerald.game.GameState;
import com.emerald.game.MobGear;
import com.emerald.game.SiegeRoster;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LES ECHOS : ce qui vous arrive sous terre.
 *
 * Aujourd'hui rien ne se passe dans une galerie. Tant qu'un joueur est sous
 * terre (sous y = 50, sans ciel au-dessus, dans le perimetre d'Underground),
 * un evenement lui arrive TOUTES LES TROIS A CINQ MINUTES, jamais deux fois le
 * meme d'affilee, annonce par une ligne courte dans le style des presages :
 *
 *   le Souffle    « Un souffle passe. »           les torches a 12 blocs tombent
 *                                                 (l'objet, au sol, recuperable)
 *   la Secousse   « La paroi se fend. »           une secousse, et 2 a 4 minerais
 *                                                 apparaissent sur les parois a 8 blocs
 *   le Chant      « Quelque chose chante dans     une cache est plantee dans la roche
 *                 la pierre. »                    a 15-25 blocs ; un carillon guide,
 *                                                 chaud-froid, a l'oreille seule, 90 s
 *   les Yeux      « Quelque chose vous regarde. » trois hostiles du vivier, dans le
 *   (rare)                                        noir a 16-24 blocs, deja detoures
 *
 * Le compte ne tourne QUE sous terre : remonter le fige, redescendre le
 * reprend. Le premier echo vient plus tot (deux a trois minutes) pour que la
 * premiere descente en rencontre un.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Echoes {

    public enum Kind { SOUFFLE, SECOUSSE, CHANT, YEUX }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Les hostiles des Yeux, pour les reconnaitre (et les essais). */
    public static final String TAG_EYE = "arc_echo_eye";

    /** Sous cette hauteur, et sans ciel, on est sous terre. */
    private static final int UNDERGROUND = 50;
    /** Entre deux echos : trois a cinq minutes ; le premier, deux a trois. */
    private static final int GAP_MIN = 3 * 60 * 20;
    private static final int GAP_SPAN = 2 * 60 * 20;
    private static final int FIRST_MIN = 2 * 60 * 20;
    private static final int FIRST_SPAN = 60 * 20;
    private static final int SOUFFLE_RADIUS = 12;
    private static final int SECOUSSE_RADIUS = 8;
    private static final int SONG_TICKS = 90 * 20;
    private static final double SONG_MIN = 15.0;
    private static final double SONG_SPAN = 10.0;
    private static final int EYES_COUNT = 3;
    private static final double EYES_MIN = 16.0;
    private static final double EYES_SPAN = 8.0;
    private static final int EYES_GLOW = 200;

    /** Le Chant en cours d'un joueur : ou est la cache, et jusqu'a quand elle chante. */
    private record Song(BlockPos target, long until) {
    }

    /** Ce qu'il reste a un joueur avant son prochain echo, en tiques SOUS TERRE. */
    private static final Map<UUID, Integer> remaining = new HashMap<>();
    private static final Map<UUID, Kind> last = new HashMap<>();
    private static final Map<UUID, Song> songs = new HashMap<>();
    private static final Map<UUID, Long> eyes = new HashMap<>();

    private Echoes() {
    }

    // -------------------------------------------------------------- tique

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 20 != 0
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            tickSong(level, player, now);
            if (!underground(level, player)) {
                continue;
            }
            int left = remaining.computeIfAbsent(player.getUUID(),
                    id -> FIRST_MIN + level.random.nextInt(FIRST_SPAN)) - 20;
            if (left > 0) {
                remaining.put(player.getUUID(), left);
                continue;
            }
            remaining.put(player.getUUID(), GAP_MIN + level.random.nextInt(GAP_SPAN));
            fire(level, player, pick(level.random, last.get(player.getUUID())));
        }
        // les Yeux cessent de luire
        var it = eyes.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now < entry.getValue()) {
                continue;
            }
            Entity eye = level.getEntity(entry.getKey());
            if (eye != null) {
                eye.setGlowingTag(false);
            }
            it.remove();
        }
    }

    private static boolean underground(ServerLevel level, ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return pos.getY() < UNDERGROUND && !level.canSeeSky(pos.above())
                && !player.isSpectator() && Underground.allowed(level, pos);
    }

    /** Jamais deux fois le meme d'affilee ; les Yeux sont rares. */
    private static Kind pick(RandomSource random, @Nullable Kind previous) {
        for (int i = 0; i < 8; i++) {
            int r = random.nextInt(100);
            Kind kind = r < 30 ? Kind.SOUFFLE : r < 60 ? Kind.SECOUSSE : r < 88 ? Kind.CHANT : Kind.YEUX;
            if (kind != previous) {
                return kind;
            }
        }
        return previous == Kind.CHANT ? Kind.SECOUSSE : Kind.CHANT;
    }

    /** Declenche un echo pour un joueur. Sert aussi a la commande d'essai. */
    public static void fire(ServerLevel level, ServerPlayer player, Kind kind) {
        last.put(player.getUUID(), kind);
        switch (kind) {
            case SOUFFLE -> souffle(level, player);
            case SECOUSSE -> secousse(level, player);
            case CHANT -> chant(level, player);
            case YEUX -> yeux(level, player);
        }
    }

    private static void whisper(ServerPlayer player, String key, int colour) {
        player.sendSystemMessage(Component.translatable(key)
                .withStyle(style -> style.withColor(colour).withItalic(true)));
    }

    // ------------------------------------------------------------ le Souffle

    /** Les torches tombent : l'objet au sol, recuperable. Le noir, et ce qui vient avec. */
    private static void souffle(ServerLevel level, ServerPlayer player) {
        whisper(player, "mine.emeraldweapons.echo.souffle", 0xB9C6D6);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WHIRL,
                SoundSource.AMBIENT, 1.2F, 0.55F);
        BlockPos centre = player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int fallen = 0;
        for (int dx = -SOUFFLE_RADIUS; dx <= SOUFFLE_RADIUS; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                for (int dz = -SOUFFLE_RADIUS; dz <= SOUFFLE_RADIUS; dz++) {
                    if (dx * dx + dz * dz > SOUFFLE_RADIUS * SOUFFLE_RADIUS) {
                        continue;
                    }
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                            || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
                        level.destroyBlock(cursor, true);
                        fallen++;
                    }
                }
            }
        }
        LOGGER.info("Echo : souffle, {} torche(s) tombee(s) autour de {}", fallen, centre);
        if (fallen == 0) {
            // rien a souffler : on ne laisse pas un echo vide, la paroi se fend a la place
            secousse(level, player);
        }
    }

    // ----------------------------------------------------------- la Secousse

    /** La secousse des Meteores, et deux a quatre minerais sur les parois a portee. */
    private static void secousse(ServerLevel level, ServerPlayer player) {
        whisper(player, "mine.emeraldweapons.echo.secousse", 0xC9A26B);
        PacketDistributor.sendToPlayer(player, new com.emerald.network.WeatherPulsePayload(0xC9A26B, 0, 55));
        level.playSound(null, player.blockPosition(), SoundEvents.ANCIENT_DEBRIS_BREAK,
                SoundSource.AMBIENT, 1.2F, 0.45F);
        BlockPos centre = player.blockPosition();
        List<BlockPos> walls = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SECOUSSE_RADIUS; dx <= SECOUSSE_RADIUS; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -SECOUSSE_RADIUS; dz <= SECOUSSE_RADIUS; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (cursor.distSqr(centre) < 4.0 || !Underground.natural(level.getBlockState(cursor))) {
                        continue;
                    }
                    for (Direction side : Direction.values()) {
                        if (level.getBlockState(cursor.relative(side)).isAir()) {
                            walls.add(cursor.immutable());
                            break;
                        }
                    }
                }
            }
        }
        Collections.shuffle(walls, new java.util.Random(level.random.nextLong()));
        int count = Math.min(walls.size(), 2 + level.random.nextInt(3));
        for (int i = 0; i < count; i++) {
            BlockPos wall = walls.get(i);
            level.setBlock(wall, Breakthrough.oreFor(level.random, wall.getY()), 3);
            level.levelEvent(2001, wall, net.minecraft.world.level.block.Block.getId(Blocks.STONE.defaultBlockState()));
            Jalons.place(level, wall, 160);
        }
        LOGGER.info("Echo : secousse, {} minerai(s) sur {} paroi(s) autour de {}", count, walls.size(), centre);
    }

    // ------------------------------------------------------------- le Chant

    /**
     * Une cache dans la roche pleine, et un carillon pour la trouver.
     *
     * Pas de boussole ici : c'est L'OREILLE qui guide, chaud-froid, pendant
     * quatre-vingt-dix secondes. Trouvee (a moins de trois blocs), elle s'ouvre
     * en Poche-cache. Trop tard, « le chant s'est tu ».
     */
    private static void chant(ServerLevel level, ServerPlayer player) {
        BlockPos target = null;
        for (int i = 0; i < 40 && target == null; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = SONG_MIN + level.random.nextDouble() * SONG_SPAN;
            BlockPos candidate = new BlockPos(
                    (int) Math.round(player.getX() + Math.cos(angle) * distance),
                    player.blockPosition().getY() + level.random.nextInt(13) - 6,
                    (int) Math.round(player.getZ() + Math.sin(angle) * distance));
            if (!Underground.natural(level.getBlockState(candidate)) || candidate.getY() >= Underground.CEILING) {
                continue;
            }
            boolean buried = true;
            for (Direction side : Direction.values()) {
                if (!Underground.natural(level.getBlockState(candidate.relative(side)))) {
                    buried = false;
                    break;
                }
            }
            if (buried) {
                target = candidate;
            }
        }
        if (target == null) {
            secousse(level, player);              // pas de roche pleine autour : autre chose
            return;
        }
        whisper(player, "mine.emeraldweapons.echo.chant", 0x9CE8FF);
        songs.put(player.getUUID(), new Song(target, level.getGameTime() + SONG_TICKS));
        LOGGER.info("Echo : chant, cache a {} ({} blocs de {})", target,
                (int) Math.sqrt(player.blockPosition().distSqr(target)), player.getName().getString());
        chime(level, player, target);
    }

    private static void chime(ServerLevel level, ServerPlayer player, BlockPos target) {
        double distance = Math.sqrt(player.blockPosition().distSqr(target));
        float near = (float) Math.max(0.0, 1.0 - distance / (SONG_MIN + SONG_SPAN + 5.0));
        level.playSound(null, target, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT,
                0.6F + 0.8F * near, 0.7F + 1.1F * near);
    }

    private static void tickSong(ServerLevel level, ServerPlayer player, long now) {
        Song song = songs.get(player.getUUID());
        if (song == null) {
            return;
        }
        double distance = Math.sqrt(player.blockPosition().distSqr(song.target()));
        if (distance <= 3.0) {
            songs.remove(player.getUUID());
            Direction away = Direction.getNearest(song.target().getX() + 0.5 - player.getX(),
                    song.target().getY() + 0.5 - player.getEyeY(), song.target().getZ() + 0.5 - player.getZ());
            BlockPos from = song.target().relative(away.getOpposite());
            boolean opened = Pockets.open(level, from, away, Pockets.Kind.CACHE);
            if (opened) {
                // le bloc devant la gorge se fend aussi : la pierre s'ouvre devant vous
                level.destroyBlock(from, false);
            } else {
                // la roche ne veut pas s'ouvrir la : la cache tombe quand meme
                level.setBlock(song.target(), Blocks.CHEST.defaultBlockState(), 3);
            }
            LOGGER.info("Echo : chant trouve a {} ({})", song.target(), opened ? "poche ouverte" : "coffre pose");
            whisper(player, "mine.emeraldweapons.echo.chant.found", 0xFFC46B);
            return;
        }
        if (now >= song.until()) {
            songs.remove(player.getUUID());
            whisper(player, "mine.emeraldweapons.echo.chant.lost", 0x9AA0A6);
            return;
        }
        // le carillon accelere quand on approche : toutes les 2 s loin, chaque seconde pres
        long period = distance > 12.0 ? 40 : 20;
        if (now % period == 0) {
            chime(level, player, song.target());
        }
    }

    // -------------------------------------------------------------- les Yeux

    /** Trois hostiles du vivier, dans le noir, deja detoures : le combat qui vient a vous. */
    private static void yeux(ServerLevel level, ServerPlayer player) {
        List<String> roster = SiegeRoster.forTier(tier(level));
        if (roster.isEmpty()) {
            secousse(level, player);
            return;
        }
        whisper(player, "mine.emeraldweapons.echo.yeux", 0xFF616B);
        player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.8F);
        int spawned = 0;
        for (int i = 0; i < 30 && spawned < EYES_COUNT; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = EYES_MIN + level.random.nextDouble() * EYES_SPAN;
            int x = (int) Math.round(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(player.getZ() + Math.sin(angle) * distance);
            BlockPos spot = standing(level, x, player.blockPosition().getY(), z);
            if (spot == null) {
                continue;
            }
            EntityType<?> type = EntityType.byString(roster.get(level.random.nextInt(roster.size()))).orElse(null);
            if (type == null) {
                continue;
            }
            Entity entity = type.spawn(level, spot, MobSpawnType.EVENT);
            if (!(entity instanceof Mob mob)) {
                continue;
            }
            MobGear.equip(mob, MobGear.stage(level, tier(level)), level.random);
            mob.setTarget(player);
            mob.setGlowingTag(true);
            mob.addTag(TAG_EYE);
            eyes.put(mob.getUUID(), level.getGameTime() + EYES_GLOW);
            spawned++;
        }
        LOGGER.info("Echo : yeux, {} hostile(s) autour de {}", spawned, player.blockPosition());
    }

    /** Un endroit ou se tenir : de l'air sur deux blocs, du plein dessous, a moins de six de haut. */
    @Nullable
    private static BlockPos standing(ServerLevel level, int x, int y, int z) {
        for (int dy = 0; dy <= 6; dy++) {
            for (int sign : new int[]{1, -1}) {
                BlockPos pos = new BlockPos(x, y + dy * sign, z);
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).isSolid()) {
                    return pos;
                }
            }
        }
        return null;
    }

    /** Le palier du vivier suit les ancres tenues, comme les sieges. */
    private static int tier(ServerLevel level) {
        return Math.min(3, GameState.get(level).anchorsActive() + 1);
    }
}
