package com.emerald.weather;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.server.ServerScoreboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'AMBIANCE DE LA BATTUE : tout ce qu'on entend et voit d'elle.
 *
 * La lecon des meteos precedentes est ecrite en toutes lettres : le
 * brouillard meurt sous Distant Horizons, la geometrie dans le monde meurt
 * sous Iris, le filtre d'ecran a lasse. Ce qui reste marche a coup sur et
 * ne peut pas mal se dessiner : L'HORLOGE, LE SON, LA LUEUR D'ENTITE, et des
 * CREATURES VIVANTES. La Battue n'est faite que de cela.
 *
 *  - L'AUBE. Une battue se fait a l'aube, c'est meme sa definition. L'horloge
 *    saute a 23 000 (WeatherManager.clockFor) : lune encore la, premiere
 *    lueur a l'est, lumiere bleue et froide -- l'exact oppose de l'Heure
 *    Doree, impossible de les confondre.
 *  - LES CORBEAUX. Cinq a huit corbeaux du Twilight Forest se levent et
 *    TOURNENT au-dessus du joueur, haut, contre l'aube. Pas une particule :
 *    des oiseaux dans le ciel, qui repartent avec la meteo.
 *  - LE COR. Un appel de corne (le jeu en embarque huit, dont deux sonnent
 *    exactement comme un cor de chasse), lointain au debut, puis un rappel
 *    toutes les trente secondes, de plus en plus proche.
 *  - LES HURLEMENTS. Des loups au loin, espaces, dans une direction au sort.
 *  - LE TAMBOUR. En approchant d'une proie qui ne vous a pas vu, un battement
 *    sourd dont le tempo monte avec la proximite. Il se tait des qu'elle vous
 *    repere. On ENTEND l'embuscade se preparer.
 *
 * ET LE BLANC / ROUGE. Le detourage prend la couleur de l'etat de la
 * creature : BLANC, elle ne vous a pas pris pour cible -- le critique
 * d'embuscade est garanti ; ROUGE, elle vous a repere ; OR, c'est la Proie.
 * La couleur d'une lueur est celle de l'EQUIPE de scoreboard de l'entite :
 * trois equipes, et l'on y range les creatures chaque seconde. Gratuit, et
 * fiable sous n'importe quel pack de shaders.
 */
public final class BattueScene {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final String TAG_RAVEN = "emeraldweapons_battue_raven";
    /** Marque la Proie (etape suivante) : jamais rangee en blanc ni en rouge. */
    public static final String TAG_PREY = "emeraldweapons_battue_prey";

    private static final String TEAM_WHITE = "arc_battue_white";
    private static final String TEAM_RED = "arc_battue_red";
    private static final String TEAM_GOLD = "arc_battue_gold";

    private static final String RAVEN_ID = "twilightforest:raven";
    private static final int RAVENS_MIN = 5;
    private static final int RAVENS_MAX = 8;
    /** Le vol : hauteur au-dessus du joueur, rayon du cercle, vitesse angulaire. */
    private static final double RAVEN_HEIGHT = 14.0;
    private static final double RAVEN_RADIUS = 9.0;
    private static final double RAVEN_TURN = 0.045;

    /** Le cor : un rappel toutes les trente secondes, qui se rapproche. */
    private static final int HORN_EVERY = 600;
    /** Les hurlements : entre quinze et vingt-cinq secondes. */
    private static final int HOWL_MIN = 300;
    private static final int HOWL_SPAN = 200;
    /** Le tambour : portee, et tempo entre le plus lent et le plus rapide. */
    private static final double DRUM_RANGE = 16.0;
    private static final int DRUM_SLOW = 30;
    private static final int DRUM_FAST = 8;

    /** Le rang de chaque corbeau sur son cercle : ils ne se suivent pas. */
    private static final Map<UUID, Double> ravenPhase = new HashMap<>();
    /** Le prochain battement de tambour, par joueur. */
    private static final Map<UUID, Long> nextDrum = new HashMap<>();
    private static long startedAt;
    private static long nextHowl;
    private static int hornCalls;

    private BattueScene() {
    }

    // ------------------------------------------------------------ cycle

    static void begin(ServerLevel level) {
        startedAt = level.getGameTime();
        nextHowl = startedAt + 120;
        hornCalls = 0;
        ravenPhase.clear();
        nextDrum.clear();
        teams(level);
        for (ServerPlayer player : level.players()) {
            raiseRavens(level, player);
        }
        horn(level, 0);
    }

    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        long since = now - startedAt;
        if (since > 0 && since % HORN_EVERY == 0) {
            horn(level, ++hornCalls);
        }
        if (now >= nextHowl) {
            howl(level);
            nextHowl = now + HOWL_MIN + level.random.nextInt(HOWL_SPAN);
        }
        flyRavens(level);
        for (ServerPlayer player : level.players()) {
            drum(level, player, now);
        }
        if (now % 20 == 0) {
            paint(level);
        }
    }

    static void end(ServerLevel level) {
        List<Entity> gone = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity != null && entity.getTags().contains(TAG_RAVEN)) {
                gone.add(entity);
            }
        }
        for (Entity entity : gone) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,
                    entity.getX(), entity.getY(), entity.getZ(), 4, 0.3, 0.2, 0.3, 0.01);
            entity.discard();
        }
        ravenPhase.clear();
        nextDrum.clear();
        clearTeams(level);
        // le cor de retraite : deux notes basses, la chasse est finie
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(hornSound(level, 5), SoundSource.WEATHER, 0.6F, 0.7F);
        }
    }

    // ---------------------------------------------------------- les corbeaux

    private static void raiseRavens(ServerLevel level, ServerPlayer player) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
                ResourceLocation.parse(RAVEN_ID)).orElse(null);
        if (type == null) {
            LOGGER.info("Battue : pas de corbeau ({}) dans ce pack, le ciel reste vide", RAVEN_ID);
            return;
        }
        int count = RAVENS_MIN + level.random.nextInt(RAVENS_MAX - RAVENS_MIN + 1);
        for (int i = 0; i < count; i++) {
            double phase = (Math.PI * 2 / count) * i;
            double x = player.getX() + Math.cos(phase) * RAVEN_RADIUS;
            double z = player.getZ() + Math.sin(phase) * RAVEN_RADIUS;
            Entity raven = type.create(level);
            if (!(raven instanceof Mob bird)) {
                return;
            }
            bird.moveTo(x, player.getY() + RAVEN_HEIGHT, z, 0.0F, 0.0F);
            bird.setNoGravity(true);
            bird.setInvulnerable(true);
            bird.setPersistenceRequired();
            bird.addTag(TAG_RAVEN);
            bird.finalizeSpawn(level, level.getCurrentDifficultyAt(bird.blockPosition()),
                    MobSpawnType.EVENT, null);
            level.addFreshEntity(bird);
            ravenPhase.put(bird.getUUID(), phase);
        }
    }

    /**
     * Le vol en cercle, tenu par nous et non par leur intelligence.
     *
     * Les corbeaux du Twilight Forest se posent des qu'ils le peuvent ; livres
     * a eux-memes, ils seraient au sol en dix secondes. On leur impose le
     * cercle : une position par tique, et la vitesse qui va avec pour que le
     * client interpole et que les ailes battent.
     */
    private static void flyRavens(ServerLevel level) {
        if (ravenPhase.isEmpty()) {
            return;
        }
        ServerPlayer anchor = level.players().isEmpty() ? null : level.players().get(0);
        if (anchor == null) {
            return;
        }
        for (Map.Entry<UUID, Double> entry : ravenPhase.entrySet()) {
            Entity raven = level.getEntity(entry.getKey());
            if (raven == null || !raven.isAlive()) {
                continue;
            }
            double phase = entry.getValue() + RAVEN_TURN;
            entry.setValue(phase);
            // le cercle respire : le rayon et la hauteur ondulent doucement
            double radius = RAVEN_RADIUS + Math.sin(phase * 0.7) * 2.5;
            double height = RAVEN_HEIGHT + Math.sin(phase * 1.3) * 2.0;
            double x = anchor.getX() + Math.cos(phase) * radius;
            double y = anchor.getY() + height;
            double z = anchor.getZ() + Math.sin(phase) * radius;
            double vx = x - raven.getX();
            double vz = z - raven.getZ();
            raven.setDeltaMovement(vx, y - raven.getY(), vz);
            raven.setPos(x, y, z);
            raven.setYRot((float) Math.toDegrees(Math.atan2(vz, vx)) - 90.0F);
            raven.setYBodyRot(raven.getYRot());
        }
    }

    // ------------------------------------------------------------- les sons

    private static SoundEvent hornSound(ServerLevel level, int variant) {
        var variants = SoundEvents.GOAT_HORN_SOUND_VARIANTS;
        return variants.get(Math.floorMod(variant, variants.size())).value();
    }

    /**
     * Le cor, lointain puis proche.
     *
     * Le premier appel vient de soixante-dix blocs, le suivant de cinquante,
     * puis de trente : la chasse se rapproche. « Call » et « Seek » (les
     * variantes 5 et 2 de la corne du jeu) sonnent comme un vrai cor.
     */
    private static void horn(ServerLevel level, int call) {
        double distance = Math.max(20.0, 70.0 - call * 20.0);
        int variant = call % 2 == 0 ? 5 : 2;
        for (ServerPlayer player : level.players()) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            level.playSound(null, player.getX() + Math.cos(angle) * distance, player.getY() + 6,
                    player.getZ() + Math.sin(angle) * distance, hornSound(level, variant),
                    SoundSource.WEATHER, 4.0F, 0.85F);
        }
    }

    private static void howl(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double distance = 40.0 + level.random.nextDouble() * 30.0;
            level.playSound(null, player.getX() + Math.cos(angle) * distance, player.getY(),
                    player.getZ() + Math.sin(angle) * distance, SoundEvents.WOLF_HOWL,
                    SoundSource.WEATHER, 2.5F, 0.8F + level.random.nextFloat() * 0.3F);
        }
    }

    /** Le tambour du chasseur : plus la proie ignorante est proche, plus il bat vite. */
    private static void drum(ServerLevel level, ServerPlayer player, long now) {
        if (now < nextDrum.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        double best = Double.MAX_VALUE;
        for (Mob mob : level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(DRUM_RANGE), m -> m instanceof Enemy && m.isAlive()
                        && !(m.getTarget() instanceof Player))) {
            best = Math.min(best, mob.distanceTo(player));
        }
        if (best > DRUM_RANGE) {
            nextDrum.put(player.getUUID(), now + 10);
            return;
        }
        double t = best / DRUM_RANGE;                                  // 0 pres, 1 loin
        int interval = (int) Math.round(DRUM_FAST + (DRUM_SLOW - DRUM_FAST) * t);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.AMBIENT,
                0.55F, 0.5F);
        nextDrum.put(player.getUUID(), now + interval);
    }

    // ------------------------------------------------------- blanc / rouge

    private static PlayerTeam team(ServerScoreboard board, String name, ChatFormatting colour) {
        PlayerTeam team = board.getPlayerTeam(name);
        if (team == null) {
            team = board.addPlayerTeam(name);
        }
        team.setColor(colour);
        team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
        return team;
    }

    private static void teams(ServerLevel level) {
        ServerScoreboard board = level.getScoreboard();
        team(board, TEAM_WHITE, ChatFormatting.WHITE);
        team(board, TEAM_RED, ChatFormatting.RED);
        team(board, TEAM_GOLD, ChatFormatting.GOLD);
    }

    /** Range chaque creature detouree dans l'equipe de son etat. */
    private static void paint(ServerLevel level) {
        ServerScoreboard board = level.getScoreboard();
        PlayerTeam white = team(board, TEAM_WHITE, ChatFormatting.WHITE);
        PlayerTeam red = team(board, TEAM_RED, ChatFormatting.RED);
        PlayerTeam gold = team(board, TEAM_GOLD, ChatFormatting.GOLD);
        for (ServerPlayer player : level.players()) {
            for (Mob mob : level.getEntitiesOfClass(Mob.class,
                    player.getBoundingBox().inflate(WeatherEffects.PRISME_RANGE), Mob::isAlive)) {
                if (mob.getTags().contains(TAG_RAVEN)) {
                    continue;                              // les corbeaux ne sont pas du gibier
                }
                PlayerTeam wanted = mob.getTags().contains(TAG_PREY) ? gold
                        : mob.getTarget() instanceof Player ? red : white;
                PlayerTeam current = board.getPlayersTeam(mob.getStringUUID());
                if (current != wanted) {
                    board.addPlayerToTeam(mob.getStringUUID(), wanted);
                }
            }
        }
    }

    private static void clearTeams(ServerLevel level) {
        ServerScoreboard board = level.getScoreboard();
        for (String name : new String[]{TEAM_WHITE, TEAM_RED, TEAM_GOLD}) {
            PlayerTeam team = board.getPlayerTeam(name);
            if (team == null) {
                continue;
            }
            for (String member : new ArrayList<>(team.getPlayers())) {
                board.removePlayerFromTeam(member, team);
            }
        }
    }

    /** Le cercle des corbeaux suit le premier joueur : utile aux essais. */
    @Nullable
    static BlockPos ravenCentre(ServerLevel level) {
        return level.players().isEmpty() ? null : level.players().get(0).blockPosition();
    }
}
