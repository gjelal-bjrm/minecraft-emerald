package com.emerald.jak.gun;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les munitions d'eco de Haven : les douze points de la carte, et ce que lachent les monstres.
 *
 * LES POINTS (HavenInvasionData.ecoPoints, couleur de la carte validee) : une
 * munition posee a chaque point dont le troncon tique, ville ouverte. Ramassee,
 * elle revient apres {@value #RESPAWN_TICKS} tiques (30 s). Pourquoi 30 s : les
 * points voisins sont a 110-160 blocs l'un de l'autre (mesure sur la carte, voir
 * le rapport du banc), soit 20 a 30 s de course ; un joueur qui fait la navette
 * entre deux points retrouve le premier en revenant, deux qui se suivent non.
 * Dans Jak 3 le delai vient de chaque acteur (fact fade-time, donnees du niveau,
 * collectables.gc:1170) et n'est pas dans les sources.
 *
 * LES MONSTRES TUES (HavenMonsterKilledEvent) lachent UNE munition, toujours, de
 * la quantite de Jak 3 (10 jaune, 5 rouge, 10 bleu, 1 sombre). LA COULEUR suit la
 * regle « ammo-random » du jeu (collectables.gc:2686-2806) : tirage pondere par la
 * part manquante de chaque reserve possedee DU TUEUR ; toutes pleines, ou sans
 * tueur, au hasard parmi les quatre. Ecarts voulus : ni rouge divise par deux, ni
 * « rien » tire au sort -- la decision du joueur fixe les quantites. Elle tombe,
 * vit {@value #DROP_LIFE} tiques, {@value #DROPS_MAX} au plus en meme temps (la
 * plus ancienne part), et disparait au passage en paisible.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class GunEco {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final int RESPAWN_TICKS = 600;
    public static final int DROP_LIFE = 400;
    public static final int DROPS_MAX = 64;
    private static final int PERIOD = 10;

    private static final Map<Integer, GunEcoEntity> POINTS = new HashMap<>();
    private static final Map<Integer, Long> NEXT = new HashMap<>();
    private static final List<GunEcoEntity> DROPS = new ArrayList<>();
    private static int ticks;
    @Nullable
    private static HavenInvasion.Mode lastMode;

    /** Une explosion du Peace Maker, pour le banc d'essai. */
    public record Explosion(UUID shooter, Vec3 at, int broken, int targets, long tick) {
    }

    private static final List<Explosion> EXPLOSIONS = new ArrayList<>();

    private GunEco() {
    }

    // ================================================================ ramassage

    /**
     * Un joueur touche une munition : sa reserve monte, plafonnee a la capacite.
     *
     * @return vrai si la munition a ete ramassee (et retiree)
     */
    public static boolean pickup(GunEcoEntity eco, ServerPlayer player) {
        if (eco.isRemoved() || !player.isAlive() || player.isSpectator() || !MorphGunKeeper.allowed(player)) {
            return false;
        }
        ItemStack gun = MorphGunKeeper.find(player);
        if (gun == null) {
            return false;
        }
        int added = MorphGunData.refill(gun, eco.family(), eco.amount());
        if (added <= 0) {
            return false;
        }
        eco.picked = true;
        eco.level().broadcastEntityEvent(eco, GunEcoEntity.EVENT_PICKED);
        float pitch = switch (eco.family()) {
            case RED -> 0.8F;
            case YELLOW -> 1.25F;
            case BLUE -> 1.05F;
            case DARK -> 0.6F;
        };
        eco.level().playSound(null, eco.getX(), eco.getY(), eco.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.5F, pitch);
        eco.discard();
        return true;
    }

    // ================================================================ lachers

    @SubscribeEvent
    public static void onKilled(HavenMonsterKilledEvent event) {
        ServerLevel level = event.getLevel();
        if (!HavenInvasion.cityOpen(level.getServer())) {
            return;
        }
        GunForm.Family family = dropFamily(event.getKiller(), level.random);
        Vec3 at = event.getPosition();
        GunEcoEntity eco = GunEcoEntity.create(level, family, true, new Vec3(at.x, at.y + 0.2, at.z));
        DROPS.removeIf(Entity::isRemoved);
        while (DROPS.size() >= DROPS_MAX) {
            DROPS.remove(0).discard();
        }
        if (level.addFreshEntity(eco)) {
            DROPS.add(eco);
        }
    }

    /** La couleur d'une munition lachee (voir l'en-tete). */
    public static GunForm.Family dropFamily(@Nullable ServerPlayer killer, RandomSource random) {
        ItemStack gun = killer == null ? null : MorphGunKeeper.find(killer);
        MorphGunData data = gun == null ? null : MorphGunData.of(gun);
        GunForm.Family[] all = GunForm.Family.values();
        double[] weights = new double[all.length];
        double sum = 0.0;
        if (data != null) {
            for (GunForm.Family family : all) {
                if (ownsFamily(data, family)) {
                    weights[family.ordinal()] = (family.capacity - data.eco(family)) / (double) family.capacity;
                    sum += weights[family.ordinal()];
                }
            }
        }
        if (sum <= 0.0) {
            return all[random.nextInt(all.length)];
        }
        double pick = random.nextDouble() * sum;
        for (GunForm.Family family : all) {
            pick -= weights[family.ordinal()];
            if (pick < 0.0 && weights[family.ordinal()] > 0.0) {
                return family;
            }
        }
        for (int i = all.length - 1; i >= 0; i--) {
            if (weights[i] > 0.0) {
                return all[i];
            }
        }
        return GunForm.Family.YELLOW;
    }

    private static boolean ownsFamily(MorphGunData data, GunForm.Family family) {
        for (int rank = 1; rank <= 3; rank++) {
            if (data.owns(GunForm.of(family, rank))) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = Haven.level(server);
        if (level == null || ++ticks % PERIOD != 0) {
            return;
        }
        if (!HavenInvasion.cityOpen(server)) {
            if (!POINTS.isEmpty() || !DROPS.isEmpty()) {
                int removed = removeAll(level, false);
                LOGGER.info("Morph Gun : ville fermee, {} munitions d'eco retirees", removed);
            }
            POINTS.clear();
            NEXT.clear();
            DROPS.clear();
            lastMode = null;
            return;
        }
        HavenInvasion.Mode mode = HavenInvasion.mode(server);
        if (mode == HavenInvasion.Mode.PAISIBLE && lastMode != HavenInvasion.Mode.PAISIBLE) {
            removeAll(level, true);
            DROPS.clear();
        }
        lastMode = mode;
        long now = level.getGameTime();
        List<HavenInvasionData.EcoPoint> points = HavenInvasionData.ecoPoints(server);
        BlockPos origin = HavenState.get(server).origin();
        for (int i = 0; i < points.size(); i++) {
            GunEcoEntity eco = POINTS.get(i);
            if (eco != null && eco.isRemoved()) {
                if (eco.picked) {
                    NEXT.put(i, now + RESPAWN_TICKS);
                }
                POINTS.remove(i);
                eco = null;
            }
            if (eco == null && now >= NEXT.getOrDefault(i, 0L)) {
                HavenInvasionData.EcoPoint point = points.get(i);
                BlockPos feet = point.feetWorld(origin);
                if (level.isLoaded(feet) && level.isPositionEntityTicking(feet)) {
                    GunForm.Family family = GunForm.Family.byIndex(point.color().ordinal());
                    GunEcoEntity placed = GunEcoEntity.create(level, family == null ? GunForm.Family.YELLOW : family, false,
                            new Vec3(feet.getX() + 0.5, feet.getY() + 0.25, feet.getZ() + 0.5));
                    if (level.addFreshEntity(placed)) {
                        POINTS.put(i, placed);
                    }
                }
            }
        }
    }

    /**
     * Retire les munitions d'eco de la ville, suivies ou non.
     *
     * @param dropsOnly seulement celles des monstres
     */
    public static int removeAll(ServerLevel level, boolean dropsOnly) {
        int removed = 0;
        for (GunEcoEntity eco : level.getEntities(EntityTypeTest.forClass(GunEcoEntity.class),
                e -> !dropsOnly || e.isDrop())) {
            eco.discard();
            removed++;
        }
        return removed;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        POINTS.clear();
        NEXT.clear();
        DROPS.clear();
        EXPLOSIONS.clear();
        ticks = 0;
        lastMode = null;
    }

    // ================================================================ banc d'essai

    @Nullable
    static GunEcoEntity point(int index) {
        return POINTS.get(index);
    }

    static long nextSpawn(int index) {
        return NEXT.getOrDefault(index, 0L);
    }

    /** Rend un point disponible tout de suite (le banc n'attend pas 30 s). */
    static void respawnNow(int index) {
        NEXT.put(index, 0L);
    }

    static void logExplosion(ServerPlayer shooter, Vec3 at, int broken, int targets) {
        EXPLOSIONS.add(new Explosion(shooter.getUUID(), at, broken, targets, shooter.level().getGameTime()));
        if (EXPLOSIONS.size() > 64) {
            EXPLOSIONS.remove(0);
        }
    }

    static List<Explosion> explosions() {
        return List.copyOf(EXPLOSIONS);
    }
}
