package com.emerald.haven.fauna;

import com.emerald.haven.Haven;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenProtection;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L'ARMEE DE MOUETTES (22 sept., cahier §85).
 *
 * « Une quete qui consiste a en tuer ; et une reaction drole -- des qu'une mouette est
 * touchee, une armee de mouettes s'envole dans tous les sens et attaque parfois les
 * joueurs (comme les cocottes de Zelda). »
 *
 * Qui frappe une mouette de la ville voit arriver {@value #SIZE} mouettes de tous les
 * cotes. Elles tournent autour de lui, a trois ou six blocs, et tour a tour l'une FOND
 * SUR LUI : un coup de bec d'un demi-coeur, et elle le BOUSCULE -- « les mouettes doivent
 * pouvoir bousculer, et pas plus de trois coups toutes les une seconde et demie » (le
 * joueur, 22 sept. au soir) : au plus {@value #PECK_BURST} coups sur toute fenetre de
 * {@value #PECK_WINDOW} tiques. Au bout de {@value #DURATION} tiques, ou des qu'il se met a
 * l'abri (appartement, Hip Hog), ou s'il meurt, elles repartent vers le large et s'en
 * vont. Puis {@value #COOLDOWN} tiques de repit avant une autre armee pour lui. Les
 * mouettes de l'armee sont invulnerables, comme les cocottes ; les autres mouettes du coin
 * s'envolent aussi.
 *
 * LE VOL EST CELUI DES MOUETTES D'ALEX'S MOBS : sans buts (HavenFauna.prepare), en vol
 * (setFlying), menees par leur propre controle de vol, qui prend un point voulu. On lui
 * donne ce point a chaque tique : une place sur l'orbite, la tete du joueur pendant un
 * pique, le large au depart.
 *
 * Une version du soir meme les avait mises dans une equipe sans collision, pour qu'elles
 * ne poussent plus personne ; le joueur l'a refusee. Cette equipe, si elle traine dans un
 * monde, est retiree a la premiere armee ({@link #retireOldTeam}).
 */
final class SeagullArmy {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    static final int SIZE = 18;
    /** Vingt secondes de colere. */
    static final int DURATION = 20 * 20;
    /** Trois secondes pour s'en aller. */
    static final int LEAVE = 60;
    /** Trente secondes de repit apres. */
    static final int COOLDOWN = 20 * 30;
    static final float PECK_DAMAGE = 1.0F;
    /** Au plus trois coups de bec... */
    static final int PECK_BURST = 3;
    /** ...sur toute fenetre d'une seconde et demie. */
    static final int PECK_WINDOW = 30;
    private static final double REACH = 1.4;
    private static final int DIVE = 30;
    /** L'equipe sans collision d'une version refusee, a retirer si elle traine. */
    private static final String OLD_TEAM = "emeraldweapons.armee";

    private static final class Army {
        final ServerPlayer target;
        final long start;
        final List<Mob> gulls = new ArrayList<>();
        final Map<UUID, Long> diveUntil = new HashMap<>();
        final Map<UUID, Long> nextDive = new HashMap<>();
        /** Les tiques des coups portes dans la fenetre en cours. */
        final Deque<Long> recent = new ArrayDeque<>();
        /** Toutes les tiques des coups portes (banc d'essai). */
        final List<Long> history = new ArrayList<>();
        long leaveAt = -1L;

        Army(ServerPlayer target, long start) {
            this.target = target;
            this.start = start;
        }
    }

    private static final Map<UUID, Army> ARMIES = new HashMap<>();
    private static final Map<UUID, Long> RESTED_UNTIL = new HashMap<>();
    private static final Set<UUID> ENLISTED = new HashSet<>();

    private SeagullArmy() {
    }

    /** Une mouette de l'armee en cours. */
    static boolean enlisted(Mob mob) {
        return ENLISTED.contains(mob.getUUID());
    }

    /**
     * Une mouette frappee par un joueur : l'armee se leve, si ce joueur n'en a pas deja
     * une et n'est pas dans son repit.
     *
     * @return le nombre de mouettes levees (0 si rien)
     */
    static int trigger(ServerLevel level, ServerPlayer player, Mob hit) {
        long now = level.getGameTime();
        if (ARMIES.containsKey(player.getUUID()) || now < RESTED_UNTIL.getOrDefault(player.getUUID(), Long.MIN_VALUE)) {
            return 0;
        }
        retireOldTeam(level);
        Army army = new Army(player, now);
        EntityType<?> type = hit.getType();
        String generation = HavenInvasion.generationTag(level);
        int budget = HavenFauna.Role.ARMY.loadedMax - HavenFauna.loaded(level, HavenFauna.Role.ARMY).size();
        for (int i = 0; i < SIZE && budget > 0; i++) {
            Vec3 at = launchSpot(level, type, player, i);
            if (at == null) {
                continue;
            }
            Mob gull = HavenFauna.create(level, type, at, HavenFauna.Role.ARMY, ChunkPos.asLong(BlockPos.containing(at)), generation);
            if (gull == null) {
                continue;
            }
            army.gulls.add(gull);
            ENLISTED.add(gull.getUUID());
            army.nextDive.put(gull.getUUID(), now + 40 + level.random.nextInt(120));
            budget--;
        }
        if (army.gulls.isEmpty()) {
            return 0;
        }
        ARMIES.put(player.getUUID(), army);
        // les autres mouettes du coin s'envolent aussi
        for (Mob other : HavenFauna.loaded(level, HavenFauna.Role.GULL)) {
            if (other.distanceToSqr(player) < 24.0 * 24.0) {
                HavenFauna.call(other, "setFlying", true);
            }
        }
        player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.faune.armee")
                .withStyle(ChatFormatting.RED), true);
        for (int i = 0; i < Math.min(4, army.gulls.size()); i++) {
            army.gulls.get(i).playAmbientSound();
        }
        LOGGER.info("faune de Haven : {} frappe une mouette, {} mouettes a sa poursuite",
                player.getGameProfile().getName(), army.gulls.size());
        return army.gulls.size();
    }

    /**
     * Un point d'envol dans tous les sens, en l'air libre : a quinze ou vingt blocs d'abord ;
     * entre les murs d'une rue etroite, plus pres ; a defaut, au-dessus de lui.
     */
    private static Vec3 launchSpot(ServerLevel level, EntityType<?> type, ServerPlayer player, int i) {
        double[][] rings = {{15.0, 6.0, 6.0, 8.0}, {8.0, 6.0, 4.0, 6.0}, {0.0, 3.0, 5.0, 5.0}};
        for (double[] ring : rings) {
            for (int tries = 0; tries < 8; tries++) {
                double angle = (i + level.random.nextDouble()) * Math.PI * 2.0 / SIZE;
                double radius = ring[0] + level.random.nextDouble() * ring[1];
                double x = player.getX() + Math.cos(angle) * radius;
                double z = player.getZ() + Math.sin(angle) * radius;
                double y = player.getY() + ring[2] + level.random.nextDouble() * ring[3];
                BlockPos block = BlockPos.containing(x, y, z);
                if (level.isLoaded(block) && level.noCollision(type.getSpawnAABB(x, y, z).inflate(0.3))
                        && level.getFluidState(block).isEmpty()) {
                    return new Vec3(x, y, z);
                }
            }
        }
        return null;
    }

    /** Chaque tique : l'orbite, les piques, le depart. */
    static void tick(ServerLevel level) {
        if (ARMIES.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Army>> it = ARMIES.entrySet().iterator();
        while (it.hasNext()) {
            Army army = it.next().getValue();
            army.gulls.removeIf(g -> g.isRemoved() || !g.isAlive());
            // LA COLERE SE COMBAT PENDANT LA QUETE DU PECHEUR, ET SEULEMENT LA (cahier §86) :
            // l'invulnerabilite de l'entite tombe, sans quoi le coup serait refuse avant meme
            // que la regle des degats (HavenFauna.onDamage) ne soit consultee.
            boolean hunted = com.emerald.haven.quest.HavenQuests.gullHunt();
            for (Mob gull : army.gulls) {
                if (gull.isInvulnerable() == hunted) {
                    gull.setInvulnerable(!hunted);
                }
            }
            ServerPlayer target = army.target;
            boolean lost = target.isRemoved() || !target.isAlive() || target.isSpectator() || target.level() != level
                    || !Haven.is(target.level())
                    || HavenProtection.inSafeZone(level.getServer(), target.getX(), target.getY(), target.getZ());
            if (army.leaveAt < 0 && (lost || now - army.start >= DURATION || army.gulls.isEmpty())) {
                army.leaveAt = now;
            }
            if (army.leaveAt >= 0) {
                if (now - army.leaveAt >= LEAVE || army.gulls.isEmpty()) {
                    for (Mob gull : army.gulls) {
                        ENLISTED.remove(gull.getUUID());
                        gull.discard();
                    }
                    RESTED_UNTIL.put(target.getUUID(), now + COOLDOWN);
                    it.remove();
                    continue;
                }
                for (Mob gull : army.gulls) {
                    Vec3 away = gull.position().subtract(target.position());
                    Vec3 dir = away.horizontalDistanceSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
                    fly(gull, gull.getX() + dir.x * 20.0, gull.getY() + 8.0, gull.getZ() + dir.z * 20.0, 3.0, now);
                }
                continue;
            }
            while (!army.recent.isEmpty() && now - army.recent.peekFirst() >= PECK_WINDOW) {
                army.recent.pollFirst();
            }
            Vec3 eyes = target.getEyePosition();
            int n = army.gulls.size();
            for (int i = 0; i < n; i++) {
                Mob gull = army.gulls.get(i);
                UUID id = gull.getUUID();
                long diving = army.diveUntil.getOrDefault(id, Long.MIN_VALUE);
                if (diving < now && now >= army.nextDive.getOrDefault(id, Long.MAX_VALUE)) {
                    army.diveUntil.put(id, now + DIVE);
                    army.nextDive.put(id, now + 80 + level.random.nextInt(120));
                    diving = now + DIVE;
                }
                if (diving >= now) {
                    // droit sur la tete : le corps heurte le joueur et le bouscule
                    fly(gull, eyes.x, eyes.y - 0.2, eyes.z, 4.0, now);
                    if (gull.position().distanceTo(eyes) < REACH && army.recent.size() < PECK_BURST
                            && peck(level, army, gull, now)) {
                        army.diveUntil.put(id, now - 1);
                    }
                    continue;
                }
                long t = now - army.start;
                double side = (i % 2 == 0) ? 1.0 : -1.0;
                double angle = i * Math.PI * 2.0 / Math.max(1, n) + side * t * 0.06;
                double radius = 3.5 + (i % 3) * 1.4;
                double y = target.getY() + 2.4 + (i % 4) * 0.6 + Mth.sin((t + i * 7) * 0.12F) * 0.8;
                fly(gull, target.getX() + Math.cos(angle) * radius, y, target.getZ() + Math.sin(angle) * radius, 2.6, now);
                if (level.random.nextInt(160) == 0) {
                    gull.playAmbientSound();
                }
            }
        }
    }

    private static void fly(Mob gull, double x, double y, double z, double speed, long now) {
        if (now % 20 == 0) {
            HavenFauna.call(gull, "setFlying", true);
        }
        gull.getMoveControl().setWantedPosition(x, y, z, speed);
    }

    /**
     * Un coup de bec. Il ne compte (fenetre des trois coups) que s'il porte : pendant
     * l'invulnerabilite d'un demi-coeur du joueur, la mouette reste sur lui et reessaie.
     */
    private static boolean peck(ServerLevel level, Army army, Mob gull, long now) {
        HavenFauna.call(gull, "peck");
        if (!army.target.hurt(level.damageSources().mobAttack(gull), PECK_DAMAGE)) {
            return false;
        }
        army.recent.addLast(now);
        army.history.add(now);
        level.playSound(null, army.target.blockPosition(), SoundEvents.PARROT_HURT, SoundSource.HOSTILE, 0.6F, 1.6F);
        return true;
    }

    /** L'equipe sans collision d'une version refusee : retiree si elle traine dans le monde. */
    private static void retireOldTeam(ServerLevel level) {
        Scoreboard board = level.getServer().getScoreboard();
        PlayerTeam old = board.getPlayerTeam(OLD_TEAM);
        if (old != null) {
            board.removePlayerTeam(old);
            LOGGER.info("faune de Haven : ancienne equipe sans collision de l'armee retiree");
        }
    }

    /** Pour le banc : l'armee d'un joueur (taille, coups portes, depart), ou null. */
    static int[] stateForTest(UUID player) {
        Army army = ARMIES.get(player);
        return army == null ? null : new int[]{army.gulls.size(), army.history.size(), army.leaveAt >= 0 ? 1 : 0};
    }

    /** Pour le banc : les tiques des coups portes par l'armee d'un joueur. */
    static List<Long> pecksForTest(UUID player) {
        Army army = ARMIES.get(player);
        return army == null ? List.of() : List.copyOf(army.history);
    }

    /** Pour le banc : les mouettes de l'armee d'un joueur. */
    static List<Mob> gullsForTest(UUID player) {
        Army army = ARMIES.get(player);
        return army == null ? List.of() : List.copyOf(army.gulls);
    }

    /** Pour le banc : oublie le repit. */
    static void restForTest(UUID player) {
        RESTED_UNTIL.remove(player);
    }

    /** Une mouette de l'armee orpheline (rechargee, ou d'une armee finie) : partie. */
    static void dismiss(ServerLevel level, Mob gull) {
        gull.discard();
    }

    /** Retire toutes les armees (fermeture de la ville). */
    static void clear(ServerLevel level) {
        for (Army army : ARMIES.values()) {
            for (Mob gull : army.gulls) {
                gull.discard();
            }
        }
        ARMIES.clear();
        ENLISTED.clear();
        retireOldTeam(level);
    }

    static void reset() {
        ARMIES.clear();
        RESTED_UNTIL.clear();
        ENLISTED.clear();
    }
}
