package com.emerald.weather;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LES GUETTEURS DE L'ECLIPSE (cahier §108).
 *
 * « Il faut vraiment que l'evenement soit plus effrayant [...] des choses stressantes » ; et ce qui
 * avait le plus marque le joueur : « un monstre qui avait une tres longue tete et qui me suivait tres
 * loin ». Un guetteur est une silhouette qui se tient au bord du noir, sur le cote du regard, et fixe
 * le joueur sans bouger ni faire de bruit. Qu'on le regarde en face une demi-seconde, qu'on s'en
 * approche, ou qu'il ait assez attendu, et il n'est plus la : un souffle de fumee, un chuchotement.
 *
 * Un par joueur au plus, toutes les 25 a 50 secondes. Il ne se bat pas (sans IA, invulnerable,
 * silencieux) et ne compte dans aucune vague : ce n'est que de la peur. Marque de l'Eclipse, il se
 * dissout avec elle.
 *
 * Depuis le §109, aussi le Rodeur des cauchemars de Born in Chaos (invisible : deux yeux qui
 * luisent dans le noir), le Voleur de vie encapuchonne et le Missionnaire, qui porte un pendu.
 *
 * Il faut le VOIR : noir sur une colline noire, a 22 blocs, la premiere photo le cachait tout a fait.
 * Il essaie donc plusieurs places et garde la plus lisible -- jamais derriere un arbre ou une
 * colline (rien entre les yeux du joueur et lui), decoupe sur le ciel si possible, sinon devant le
 * noir du lointain ; faute de mieux, il reessaie trois secondes plus tard.
 */
public final class EclipseWatchers {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** La marque des guetteurs, en plus de celle de l'Eclipse. */
    public static final String TAG = "emeraldweapons_eclipse_watcher";
    /** Ce qui guette, du plus effrayant au repli du jeu de base. */
    private static final String[] TYPES = {"deeperdarker:stalker", "graveyard:nightmare",
            "endermanoverhaul:dark_oak_enderman", "born_in_chaos_v1:nightmare_stalker",
            "born_in_chaos_v1:lifestealer", "born_in_chaos_v1:missioner", "minecraft:enderman"};
    private static final double NEAR = 15.0;
    private static final double FAR = 24.0;
    /** Tant de places essayees par apparition ; la plus lisible l'emporte. */
    private static final int TRIES = 12;
    /** Rien de lisible autour du joueur : on reessaie un peu plus tard. */
    private static final int RETRY = 20 * 3;
    /** Il s'en va si on le regarde en face (a moins de 11 degres) une demi-seconde... */
    private static final double LOOK_COS = Math.cos(Math.toRadians(11.0));
    private static final int LOOK_TICKS = 10;
    /** ...si on s'en approche a moins de tant de blocs, ou au bout de tant de tiques. */
    private static final double TOO_CLOSE = 12.0;
    private static final int PATIENCE = 20 * 14;
    private static final int GAP_MIN = 20 * 25;
    private static final int GAP_MAX = 20 * 50;

    private static final class Watch {
        final UUID entity;
        int age;
        int seen;

        Watch(UUID entity) {
            this.entity = entity;
        }
    }

    private static final Map<UUID, Watch> WATCHING = new HashMap<>();
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    /** Pourquoi la derniere apparition n'a rien donne (le banc). */
    private static String lastMiss = "";

    private EclipseWatchers() {
    }

    /** Toutes les cinq tiques pendant l'Eclipse (Eclipse.tick). */
    static void tick(ServerLevel level, List<ServerPlayer> players) {
        long now = level.getGameTime();
        for (ServerPlayer player : players) {
            UUID id = player.getUUID();
            Watch watch = WATCHING.get(id);
            if (watch != null) {
                follow(level, player, watch);
                continue;
            }
            long next = NEXT.computeIfAbsent(id, k -> now + GAP_MIN / 2 + level.random.nextInt(GAP_MIN));
            if (now >= next) {
                NEXT.put(id, now + GAP_MIN + level.random.nextInt(GAP_MAX - GAP_MIN));
                Entity made = appear(level, player);
                if (made != null) {
                    WATCHING.put(id, new Watch(made.getUUID()));
                } else {
                    NEXT.put(id, now + RETRY);
                }
            }
        }
    }

    /** Le guetteur regarde son joueur ; il s'en va si l'on le regarde, s'approche, ou s'il a assez attendu. */
    private static void follow(ServerLevel level, ServerPlayer player, Watch watch) {
        Entity entity = level.getEntity(watch.entity);
        if (entity == null || !entity.isAlive() || player.level() != level) {
            WATCHING.remove(player.getUUID());
            return;
        }
        watch.age += 5;
        Vec3 eye = player.getEyePosition();
        Vec3 to = entity.position().add(0.0, entity.getBbHeight() * 0.7, 0.0).subtract(eye);
        double distance = to.length();
        face(entity, player);
        boolean looked = distance > 0.01 && player.getLookAngle().dot(to.normalize()) >= LOOK_COS;
        watch.seen = looked ? watch.seen + 5 : 0;
        if (watch.seen >= LOOK_TICKS || distance < TOO_CLOSE || watch.age >= PATIENCE) {
            vanish(level, entity, watch.seen >= LOOK_TICKS);
            WATCHING.remove(player.getUUID());
        }
    }

    /**
     * Une silhouette au bord du noir : a 16-24 blocs, de 40 a 75 degres du regard (on la devine du
     * coin de l'oeil), sur le sol, a la place la plus lisible (legibility).
     */
    @Nullable
    static Entity appear(ServerLevel level, ServerPlayer player) {
        // les mods d'horreur d'abord : l'enderman du jeu n'est qu'un repli
        List<EntityType<?>> available = new ArrayList<>();
        for (int i = 0; i < TYPES.length - 1; i++) {
            EntityType.byString(TYPES[i]).ifPresent(available::add);
        }
        if (available.isEmpty()) {
            EntityType.byString(TYPES[TYPES.length - 1]).ifPresent(available::add);
        }
        if (available.isEmpty()) {
            return null;
        }
        EntityType<?> type = available.get(level.random.nextInt(available.size()));
        Vec3 eye = player.getEyePosition();
        BlockPos ground = null;
        int best = -1;
        int steep = 0;
        int wet = 0;
        int cramped = 0;
        int hidden = 0;
        for (int attempt = 0; attempt < TRIES && best < 2; attempt++) {
            float yaw = player.getYRot() + (level.random.nextBoolean() ? 1.0F : -1.0F) * (40.0F + level.random.nextFloat() * 35.0F);
            double distance = NEAR + level.random.nextDouble() * (FAR - NEAR);
            double x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
            double z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
            BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(x, 0, z));
            if (Math.abs(spot.getY() - player.getY()) > 12) {
                steep++;                            // un a-pic
                continue;
            }
            if (!level.getFluidState(spot.below()).isEmpty()) {
                wet++;                              // de l'eau
                continue;
            }
            if (!level.noCollision(type.getSpawnAABB(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5))) {
                cramped++;                          // pas la place (sous les feuilles)
                continue;
            }
            int score = legibility(level, player, eye, spot, type.getHeight());
            if (score < 0) {
                hidden++;
            }
            if (score > best) {
                best = score;
                ground = spot;
            }
        }
        if (ground == null || best < 0) {
            lastMiss = TRIES + " places : " + steep + " a-pic, " + wet + " eau, " + cramped + " sans place, "
                    + hidden + " cachees";
            return null;                            // rien d'ou on le verrait : plus tard
        }
        Entity entity;
        Eclipse.spawning(true);
        try {
            entity = type.spawn(level, ground, MobSpawnType.EVENT);
        } finally {
            Eclipse.spawning(false);
        }
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.setInvulnerable(true);
        mob.addTag(Eclipse.TAG);
        mob.addTag(TAG);
        face(mob, player);
        LOGGER.debug("Eclipse : un guetteur ({}) pour {} a {} blocs, lisibilite {}", type, player.getGameProfile().getName(),
                Math.round(Math.sqrt(mob.distanceToSqr(player))), best);
        return mob;
    }

    /**
     * Ce que le joueur en verrait, place la : -1 cache (un arbre, une colline entre eux), 0 devant
     * un talus tout proche, 1 devant le noir du lointain, 2 decoupe sur le ciel -- une silhouette.
     */
    private static int legibility(ServerLevel level, ServerPlayer player, Vec3 eye, BlockPos ground, float height) {
        Vec3 foot = Vec3.atBottomCenterOf(ground);
        Vec3 head = foot.add(0.0, height * 0.85, 0.0);
        if (blocked(level, player, eye, head)) {
            // la tete cachee (une branche, un talus) : lisible encore si le buste se voit, mais pas mieux
            return blocked(level, player, eye, foot.add(0.0, height * 0.5, 0.0)) ? -1 : 0;
        }
        // et derriere sa tete, dans l'axe du regard : le ciel, le lointain, ou un talus ?
        Vec3 beyond = head.add(head.subtract(eye).normalize().scale(40.0));
        if (!level.hasChunkAt(BlockPos.containing(beyond))) {
            return 1;                               // hors des tranches chargees : le noir du lointain
        }
        BlockHitResult behind = level.clip(new ClipContext(head, beyond, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (behind.getType() == HitResult.Type.MISS) {
            return 2;
        }
        return behind.getLocation().distanceTo(head) > 16.0 ? 1 : 0;
    }

    private static boolean blocked(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() != HitResult.Type.MISS;
    }

    private static void face(Entity entity, ServerPlayer player) {
        double dx = player.getX() - entity.getX();
        double dz = player.getZ() - entity.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        if (entity instanceof Mob mob) {
            mob.setYBodyRot(yaw);
        }
    }

    /** Il n'est plus la : un souffle de fumee, et, si on le regardait, un chuchotement. */
    private static void vanish(ServerLevel level, Entity entity, boolean seen) {
        Vec3 c = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 16, 0.3, entity.getBbHeight() * 0.35, 0.3, 0.01);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 8, 0.2, 0.5, 0.2, 0.01);
        level.playSound(null, entity.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 0.4F);
        if (seen) {
            level.playSound(null, entity.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        entity.discard();
    }

    /** Pourquoi la derniere apparition n'a rien donne (le banc). */
    public static String lastMiss() {
        return lastMiss;
    }

    /** Combien de guetteurs sont la (le banc). */
    public static int count() {
        return WATCHING.size();
    }

    /** Pour le banc : le guetteur de ce joueur, tout de suite ; null si rien ne tient. */
    @Nullable
    public static Entity appearForTest(ServerLevel level, ServerPlayer player) {
        Entity made = appear(level, player);
        if (made != null) {
            WATCHING.put(player.getUUID(), new Watch(made.getUUID()));
        }
        return made;
    }

    /** Pour le banc : cinq tiques de guet. */
    public static void tickForTest(ServerLevel level, List<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            Watch watch = WATCHING.get(player.getUUID());
            if (watch != null) {
                follow(level, player, watch);
            }
        }
    }

    static void clear() {
        WATCHING.clear();
        NEXT.clear();
    }
}
