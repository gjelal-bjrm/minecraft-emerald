package com.emerald.haven.fauna;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenAutotest;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.invasion.HavenProtection;
import com.emerald.jak.JakBuilder;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Le banc d'essai des animaux de Haven, INERTE sans EMERALDWEAPONS_AUTOTEST=faune.
 *
 * LES MODS DES ANIMAUX : le serveur des bancs tourne sans les mods du modpack
 * (build.gradle). Pour ce banc, on les y pose, puis on les retire :
 *
 *     python tools/dev_mods.py --server alexsmobs aquaculture livingthings
 *     EMERALDWEAPONS_AUTOTEST=faune ./gradlew runServer
 *     python tools/dev_mods.py --server --clean
 *
 * Il lit la carte de l'eau, fait vivre la faune autour de quatre JOUEURS SIMULES (ancres
 * de HavenInvasion), mesure le MSPT, verifie les places de chaque role (bassin, large,
 * quais, lieux des compagnons), les plafonds, la persistance ; puis, avec un « cobaye »
 * (FakePlayer rendu vulnerable) : un danger ramene du bassin au large, l'agressivite
 * reservee au large, la piqure des meduses, la regle des degats, l'armee de mouettes
 * (vol, coups de bec, depart, repit, dispersion a l'abri), les compagnons a l'invasion,
 * l'accueil au rechargement. Rapport dans faune_autotest.txt, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenFaunaAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "faune".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    private static final int SETTLE_TICKS = 40;
    private static final int TIMEOUT_TICKS = 20 * 60 * 20;
    private static final int TICKET_DISTANCE = 6;

    /** Quatre ancres : la rue des appartements, l'arc nord-ouest au bord du bassin, la tour ouest, la place du bras est. */
    private static final BlockPos[] ANCHOR_CELLS = {
            new BlockPos(62, 62, 166), new BlockPos(488, 67, 215), new BlockPos(455, 66, 650), new BlockPos(1070, 66, 300)};

    private enum Stage { READY, BASELINE, SPAWN, SEA, ARMY, SHELTER, MODE, END }

    private static final StringBuilder OUT = new StringBuilder();
    private static Stage stage = Stage.READY;
    private static int waited;
    private static int t;
    private static int passed;
    private static int failed;

    private static final List<ChunkPos> HELD = new ArrayList<>();
    private static ChunkPos forced;
    private static final List<Vec3> ANCHORS = new ArrayList<>();
    private static Cobaye cobaye;

    private static final long[] WINDOW = new long[3];
    private static String baseline = "";

    // la mer
    private static Mob biter;
    private static float bitten;
    private static Mob moved;
    private static Vec3 movedFrom;
    // l'armee
    private static Mob hitGull;
    private static float hitHealth;
    private static float cobayeStart;
    private static double firstDistance;
    private static double closest = Double.MAX_VALUE;
    private static int maxPecks;
    private static int armySize;
    private static float pecked;
    private static List<Long> peckTicks = new ArrayList<>();
    private static List<UUID> armyIds = new ArrayList<>();
    // l'abri
    private static int petsBefore;

    private HavenFaunaAutotest() {
    }

    /** Un FakePlayer qui prend les coups, et qui sait s'il est dans l'eau (il ne tique pas). */
    static final class Cobaye extends FakePlayer {
        Cobaye(ServerLevel level, GameProfile profile) {
            super(level, profile);
            try {
                Field field = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                field.setAccessible(true);
                field.setInt(this, 0);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("autotest faune : spawnInvulnerableTime inaccessible", e);
            }
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        @Override
        public boolean isInWater() {
            return this.level().getFluidState(this.blockPosition()).is(FluidTags.WATER);
        }

        void heal() {
            this.setHealth(this.getMaxHealth());
            this.invulnerableTime = 0;
            this.hurtTime = 0;
        }
    }

    // ================================================================ tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || stage == Stage.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = Haven.level(server);
        try {
            switch (stage) {
                case READY -> {
                    waited++;
                    if (waited < SETTLE_TICKS || (HavenSite.busy() && waited < TIMEOUT_TICKS)) {
                        return;
                    }
                    line("autotest de la faune de Haven, " + LocalDateTime.now().withNano(0));
                    if (level == null || !HavenInvasion.cityOpen(server)) {
                        check("ville ouverte (lobby ouvert, posee, sans pose)", false, "niveau " + level + ", phase "
                                + HavenState.get(server).phase() + ", lobby " + HavenArrival.lobbyOpen(server));
                        end(server);
                        return;
                    }
                    HavenInvasion.setMode(server, HavenInvasion.Mode.PAISIBLE, null);
                    HavenFauna.removeAll(level);
                    data(server);
                    hold(server, level);
                    next(Stage.BASELINE);
                }
                case BASELINE -> {
                    sample(server);
                    if (t >= 200) {
                        baseline = window("sans joueur simule");
                        BlockPos o = HavenState.get(server).origin();
                        for (BlockPos cell : ANCHOR_CELLS) {
                            ANCHORS.add(Vec3.atBottomCenterOf(o.offset(cell)));
                        }
                        HavenInvasion.TEST_ANCHORS.addAll(ANCHORS);
                        HavenFauna.RECENT.clear();
                        line("--- la faune autour de 4 joueurs simules, 40 s");
                        next(Stage.SPAWN);
                    }
                }
                case SPAWN -> {
                    sample(server);
                    if (t >= 800) {
                        double ms = averageMs();
                        line("MSPT avec la faune : " + window(HavenFauna.loaded(level, null).size() + " animaux charges, "
                                + HavenInvasion.villagers().size() + " habitants") + " ; reference " + baseline);
                        check("MSPT moyen sous 30 ms avec la faune, les habitants et le trafic (quatre zones de 13 x 13 troncons)",
                                !Double.isNaN(ms) && ms < 30.0, String.format(Locale.ROOT, "%.2f ms", ms));
                        population(server, level);
                        next(Stage.SEA);
                    }
                }
                case SEA -> sea(server, level);
                case ARMY -> army(server, level);
                case SHELTER -> shelter(server, level);
                case MODE -> mode(server, level);
                case END -> {
                }
            }
            t++;
        } catch (RuntimeException e) {
            check("banc sans exception (etape " + stage + ")", false, e.toString());
            LOGGER.error("autotest faune : exception", e);
            end(server);
        }
    }

    private static void next(Stage to) {
        stage = to;
        t = -1;
    }

    // ================================================================ carte

    private static void data(MinecraftServer server) {
        HavenFaunaData.Data data = HavenFaunaData.get(server);
        HavenInvasionData.Data invasion = HavenInvasionData.get(server);
        if (data == null || invasion == null) {
            check("carte de la faune lue", false, "absente ou illisible");
            return;
        }
        check("carte de la faune : meme volume que la carte de l'invasion, grille 1227 x 695, surface a la cellule 57",
                data.sha1().equals(invasion.sha1()) && data.width() == 1227 && data.depth() == 695 && data.surface() == 57,
                data.volume() + " " + data.sha1().substring(0, 10) + ", " + data.width() + " x " + data.depth() + ", surface " + data.surface());
        check("l'eau : plus de 200 000 colonnes de bassin, plus de 400 000 de large, plus de 15 000 cellules de quai",
                data.basinColumns() > 200_000 && data.seaColumns() > 400_000 && data.quayCells() > 15_000,
                data.basinColumns() + " bassin, " + data.seaColumns() + " large, " + data.quayCells() + " quais, "
                        + data.list().size() + " tuiles");
        BlockPos o = HavenState.get(server).origin();
        HavenFaunaData.Water basin = HavenFauna.waterAt(server, o.getX() + 620.5, o.getZ() + 420.5);
        HavenFaunaData.Water sea = HavenFauna.waterAt(server, o.getX() + 60.5, o.getZ() + 640.5);
        HavenFaunaData.Water north = HavenFauna.waterAt(server, o.getX() + 200.5, o.getZ() + 20.5);
        HavenFaunaData.Water pool = HavenFauna.waterAt(server, o.getX() + 480.5, o.getZ() + 620.5);
        HavenFaunaData.Water street = HavenFauna.waterAt(server, o.getX() + 150.5, o.getZ() + 300.5);
        check("reperes : le milieu du port est BASSIN ; le sud-ouest et le nord hors de la jetee sont LARGE ; le bassin de la tour et la place du bras ouest n'en sont pas",
                basin == HavenFaunaData.Water.BASIN && sea == HavenFaunaData.Water.OPEN_SEA && north == HavenFaunaData.Water.OPEN_SEA
                        && pool == HavenFaunaData.Water.NONE && street == HavenFaunaData.Water.NONE,
                "port " + basin + ", sud-ouest " + sea + ", nord " + north + ", tour " + pool + ", place " + street);
        List<String> gulls = HavenFauna.presentSpecies(HavenFauna.Role.GULL);
        check("Alex's Mobs charge (mouettes, phoques, requins) -- sinon : python tools/dev_mods.py --server alexsmobs aquaculture livingthings",
                !gulls.isEmpty(), "mouettes " + gulls);
        Map<HavenFauna.Role, List<String>> species = new EnumMap<>(HavenFauna.Role.class);
        for (HavenFauna.Role role : HavenFauna.Role.values()) {
            species.put(role, HavenFauna.presentSpecies(role));
        }
        line("especes presentes : " + species);
        check("especes des trois mods : poissons d'Aquaculture et de Living Things, requins de Living Things, meduses",
                species.get(HavenFauna.Role.FISH).contains("aquaculture:atlantic_herring")
                        && species.get(HavenFauna.Role.FISH).contains("livingthings:seahorse")
                        && species.get(HavenFauna.Role.DANGER).contains("livingthings:shark")
                        && species.get(HavenFauna.Role.DANGER).contains("aquaculture:jellyfish"),
                "poissons " + species.get(HavenFauna.Role.FISH).size() + ", dangers " + species.get(HavenFauna.Role.DANGER));
        StringBuilder zones = new StringBuilder();
        boolean allZones = true;
        for (int z = 0; z < HavenFauna.PET_ZONES.size(); z++) {
            int n = HavenFauna.petCellsForTest(server, z).size();
            zones.append(HavenFauna.PET_ZONES.get(z).name()).append(' ').append(n).append(" ; ");
            allZones &= n >= 20;
        }
        check("chaque lieu de compagnons a au moins 20 places au sol", allZones, zones.toString());
        line(String.format(Locale.ROOT, "quotas de la ville : %d compagnons, %d mouettes, %d poissons, %d au rivage, %d dangers",
                HavenFauna.totalQuota(server, HavenFauna.Role.PET), HavenFauna.totalQuota(server, HavenFauna.Role.GULL),
                HavenFauna.totalQuota(server, HavenFauna.Role.FISH), HavenFauna.totalQuota(server, HavenFauna.Role.SHORE),
                HavenFauna.totalQuota(server, HavenFauna.Role.DANGER)));
    }

    private static void hold(MinecraftServer server, ServerLevel level) {
        BlockPos o = HavenState.get(server).origin();
        for (BlockPos cell : ANCHOR_CELLS) {
            ChunkPos chunk = new ChunkPos(o.offset(cell));
            level.getChunkSource().addRegionTicket(JakBuilder.TICKET, chunk, TICKET_DISTANCE, chunk);
            HELD.add(chunk);
        }
        // UN TRONCON FORCE : sans joueur, le niveau cesse de faire tiquer ses entites apres 300 tiques
        forced = new ChunkPos(o.offset(ANCHOR_CELLS[1]));
        level.setChunkForced(forced.x, forced.z, true);
        line("tickets tenus autour de " + ANCHOR_CELLS.length + " points (distance " + TICKET_DISTANCE + "), troncon "
                + forced + " force");
    }

    // ================================================================ population

    private static void population(MinecraftServer server, ServerLevel level) {
        BlockPos o = HavenState.get(server).origin();
        HavenFaunaData.Data data = Objects.requireNonNull(HavenFaunaData.get(server));
        Map<HavenFauna.Role, Integer> counts = new EnumMap<>(HavenFauna.Role.class);
        Map<String, Integer> bySpecies = new java.util.TreeMap<>();
        boolean tagged = true;
        for (Mob mob : HavenFauna.loaded(level, null)) {
            HavenFauna.Role role = HavenFauna.role(mob);
            counts.merge(role, 1, Integer::sum);
            bySpecies.merge(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(), 1, Integer::sum);
            tagged &= mob.isPersistenceRequired() && mob.getTags().contains(HavenInvasion.generationTag(level));
        }
        line("charges : " + counts + " ; par espece " + bySpecies);
        check("des animaux de chaque role autour des ancres : compagnons, mouettes, poissons, rivage, dangers",
                counts.getOrDefault(HavenFauna.Role.PET, 0) >= 3 && counts.getOrDefault(HavenFauna.Role.GULL, 0) >= 8
                        && counts.getOrDefault(HavenFauna.Role.FISH, 0) >= 30 && counts.getOrDefault(HavenFauna.Role.SHORE, 0) >= 2
                        && counts.getOrDefault(HavenFauna.Role.DANGER, 0) >= 6, String.valueOf(counts));
        boolean capped = true;
        for (HavenFauna.Role role : HavenFauna.Role.values()) {
            capped &= counts.getOrDefault(role, 0) <= role.loadedMax;
        }
        check("plafonds de ce qui est charge respectes (compagnons 16, mouettes 40, poissons 110, rivage 12, dangers 30)",
                capped, String.valueOf(counts));
        check("animaux persistants, etiquetes de la generation de la ville", tagged, "");
        int pets = counts.getOrDefault(HavenFauna.Role.PET, 0);
        int collared = 0;
        int dogs = 0;
        for (Mob mob : HavenFauna.loaded(level, HavenFauna.Role.PET)) {
            if (mob instanceof net.minecraft.world.entity.animal.Wolf wolf) {
                dogs++;
                if (wolf.isTame()) {
                    collared++;
                }
            }
        }
        check("les chiens sont apprivoises (collier), les compagnons invulnerables", dogs > 0 && collared == dogs
                        && HavenFauna.loaded(level, HavenFauna.Role.PET).stream().allMatch(Mob::isInvulnerable),
                dogs + " chiens, " + collared + " a collier, " + pets + " compagnons");

        // les places de naissance
        int bad = 0;
        int seen = 0;
        StringBuilder why = new StringBuilder();
        for (HavenFauna.Spawned s : HavenFauna.RECENT) {
            seen++;
            HavenFaunaData.Water water = data.waterAt(o, s.position().x, s.position().z);
            boolean ok = switch (s.role()) {
                case FISH -> water == HavenFaunaData.Water.BASIN;
                case DANGER -> water == HavenFaunaData.Water.OPEN_SEA;
                case GULL, SHORE, PET -> !HavenProtection.inSafeZone(server, s.position().x, s.position().y, s.position().z);
                default -> true;
            };
            double nearest = HavenInvasion.nearestDistance(ANCHORS, s.position().x, s.position().z);
            if (!ok || nearest < s.role().spawnMin - 0.01) {
                bad++;
                if (why.length() < 300) {
                    why.append(s.role()).append(' ').append(s.species()).append(' ')
                            .append(BlockPos.containing(s.position()).toShortString()).append(' ').append(water)
                            .append(String.format(Locale.ROOT, " a %.1f ; ", nearest));
                }
            }
        }
        check("naissances a leur place : poissons au bassin, dangers au large, les autres hors des zones sures ; toutes assez loin des joueurs",
                seen > 0 && bad == 0, seen + " naissances, " + bad + " hors place " + why);
    }

    // ================================================================ la mer

    private static void sea(MinecraftServer server, ServerLevel level) {
        BlockPos o = HavenState.get(server).origin();
        HavenFaunaData.Data data = Objects.requireNonNull(HavenFaunaData.get(server));
        if (t == 0) {
            cobaye = new Cobaye(level, new GameProfile(UUID.randomUUID(), "[Faune]"));
            List<Mob> dangers = HavenFauna.loaded(level, HavenFauna.Role.DANGER);
            moved = dangers.isEmpty() ? null : dangers.get(0);
            Vec3 basinSpot = basinWater(level, data, o, ANCHORS.get(1));
            if (moved == null || basinSpot == null) {
                check("un danger ramene du bassin au large", false, "danger " + moved + ", place du bassin " + basinSpot);
                return;
            }
            movedFrom = moved.position();
            moved.teleportTo(basinSpot.x, basinSpot.y, basinSpot.z);
        }
        if (t == 30 && moved != null) {
            HavenFaunaData.Water water = data.waterAt(o, moved.getX(), moved.getZ());
            check("un danger pose dans le bassin est ramene au large a la demi-seconde suivante (meme s'il file sous un quai)",
                    moved.isRemoved() || water == HavenFaunaData.Water.OPEN_SEA,
                    "parti de " + BlockPos.containing(movedFrom).toShortString() + ", maintenant "
                            + BlockPos.containing(moved.position()).toShortString() + " " + water + (moved.isRemoved() ? " (retire)" : ""));
        }
        if (t == 40) {
            // le cobaye nage au large, pres d'un danger
            // un attaquant direct de preference : le requin marteau tourne 18 a 22 s autour de sa
            // proie avant de mordre, et ne la rejoint pas toujours dans la fenetre du banc
            biter = null;
            for (int pass = 0; pass < 2 && biter == null; pass++) {
                for (Mob mob : HavenFauna.loaded(level, HavenFauna.Role.DANGER)) {
                    boolean circler = "alexsmobs:hammerhead_shark".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
                    if (mob != moved && mob.isInWater() && mob.getTarget() == null && !HavenFauna.stings(mob)
                            && (pass == 1 || !circler)
                            && data.waterAt(o, mob.getX(), mob.getZ()) == HavenFaunaData.Water.OPEN_SEA) {
                        biter = mob;
                        break;
                    }
                }
            }
            Vec3 swim = biter == null ? null : seaWaterNear(level, data, o, biter.position());
            if (swim == null) {
                check("agressivite au large", false, "aucun danger libre, ou pas d'eau du large pres de lui");
                biter = null;
            } else {
                cobaye.moveTo(swim.x, swim.y, swim.z);
                cobaye.heal();
                bitten = 0.0F;
                int aimed = HavenFauna.aggroForTest(level, cobaye);
                boolean locked = biter.getTarget() == cobaye;
                check("un joueur qui nage au large est vise par les dangers a moins de 24 blocs",
                        aimed >= 1 && locked, aimed + " danger(s) tourne(s) vers lui, "
                                + BuiltInRegistries.ENTITY_TYPE.getKey(biter.getType()) + " a "
                                + String.format(Locale.ROOT, "%.1f", biter.distanceTo(cobaye)) + " blocs");
                check("la morsure d'un danger porte (HavenRules), celle d'un compagnon non",
                        HavenRules.playerMayBeHurt(cobaye, level.damageSources().mobAttack(biter))
                                && !HavenRules.playerMayBeHurt(cobaye, level.damageSources().mobAttack(
                                Objects.requireNonNull(EntityType.CAT.create(level)))), "");
            }
        }
        if (t > 40 && t < 640 && biter != null) {
            // le cobaye ne tique pas : son invulnerabilite apres un coup ne retomberait jamais
            float before = cobaye.getHealth();
            cobaye.invulnerableTime = 0;
            if (before < cobaye.getMaxHealth()) {
                bitten += cobaye.getMaxHealth() - before;
                cobaye.setHealth(cobaye.getMaxHealth());
            }
            if (biter.getTarget() == null && biter.isAlive()) {
                HavenFauna.aggroForTest(level, cobaye);
            }
        }
        if (t == 640 && biter != null) {
            // le requin marteau tourne 18 a 22 s autour de sa proie avant de mordre (CirclePreyGoal)
            check("et il se fait mordre : au moins un coup porte en trente secondes",
                    bitten > 0.0F, String.format(Locale.ROOT, "%.1f points de vie perdus, %s a %.1f blocs",
                            bitten, BuiltInRegistries.ENTITY_TYPE.getKey(biter.getType()), biter.distanceTo(cobaye)));
            Vec3 inBasin = basinWater(level, data, o, ANCHORS.get(1));
            if (inBasin != null) {
                cobaye.moveTo(inBasin.x, inBasin.y, inBasin.z);
                int released = HavenFauna.aggroForTest(level, cobaye);
                check("rentre au bassin, il est lache : plus aucun danger ne le vise",
                        released <= -1 && biter.getTarget() != cobaye, released + " (negatif : laches)");
            }
        }
        if (t == 660) {
            // la piqure d'une meduse
            Vec3 swim = seaWaterNear(level, data, o, ANCHORS.get(0));
            EntityType<?> jelly = BuiltInRegistries.ENTITY_TYPE.getOptional(
                    net.minecraft.resources.ResourceLocation.parse("aquaculture:jellyfish")).orElse(null);
            if (swim == null || jelly == null) {
                check("la piqure d'une meduse", false, "eau du large " + swim + ", meduse " + jelly);
            } else {
                cobaye.moveTo(swim.x, swim.y, swim.z);
                cobaye.heal();
                Mob mob = HavenFauna.create(level, jelly, swim.add(0.6, 0, 0), HavenFauna.Role.DANGER,
                        ChunkPos.asLong(BlockPos.containing(swim)), HavenInvasion.generationTag(level));
                float before = cobaye.getHealth();
                boolean stung = mob != null && HavenFauna.stingPlayer(level, mob, cobaye, level.getGameTime());
                check("une meduse du large pique : " + HavenFauna.STING_DAMAGE + " points et un ralentissement",
                        stung && cobaye.getHealth() <= before - HavenFauna.STING_DAMAGE + 0.01F
                                && cobaye.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
                        "vie " + before + " -> " + cobaye.getHealth());
                if (mob != null) {
                    mob.discard();
                }
            }
            next(Stage.ARMY);
        }
    }

    /** Une place d'eau du bassin, dans un troncon charge, pres d'un point. */
    @Nullable
    private static Vec3 basinWater(ServerLevel level, HavenFaunaData.Data data, BlockPos o, Vec3 near) {
        return water(level, data, o, near, HavenFaunaData.Water.BASIN);
    }

    @Nullable
    private static Vec3 seaWaterNear(ServerLevel level, HavenFaunaData.Data data, BlockPos o, Vec3 near) {
        return water(level, data, o, near, HavenFaunaData.Water.OPEN_SEA);
    }

    @Nullable
    private static Vec3 water(ServerLevel level, HavenFaunaData.Data data, BlockPos o, Vec3 near, HavenFaunaData.Water kind) {
        for (int r = 2; r <= 64; r += 2) {
            for (int a = 0; a < 16; a++) {
                double angle = a * Math.PI / 8.0;
                double x = near.x + Math.cos(angle) * r;
                double z = near.z + Math.sin(angle) * r;
                if (data.waterAt(o, x, z) != kind) {
                    continue;
                }
                BlockPos pos = BlockPos.containing(x, o.getY() + data.surface() - 1, z);
                if (level.isLoaded(pos) && level.getFluidState(pos).is(FluidTags.WATER)) {
                    return Vec3.atBottomCenterOf(pos);
                }
            }
        }
        return null;
    }

    // ================================================================ l'armee

    private static void army(MinecraftServer server, ServerLevel level) {
        if (t == 0) {
            List<Mob> gulls = HavenFauna.loaded(level, HavenFauna.Role.GULL);
            hitGull = null;
            for (Mob gull : gulls) {
                if (gull.distanceToSqr(ANCHORS.get(1)) < 80 * 80) {
                    hitGull = gull;
                    break;
                }
            }
            if (hitGull == null && !gulls.isEmpty()) {
                hitGull = gulls.get(0);
            }
            if (hitGull == null) {
                check("l'armee de mouettes", false, "aucune mouette chargee");
                next(Stage.MODE);
                return;
            }
            // le cobaye se tient au sol, pres de la mouette
            BlockPos feet = hitGull.blockPosition();
            cobaye.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            cobaye.heal();
            cobayeStart = cobaye.getHealth();
            hitHealth = hitGull.getHealth();
            boolean hurt = hitGull.hurt(level.damageSources().playerAttack(cobaye), 1.0F);
            int[] state = SeagullArmy.stateForTest(cobaye.getUUID());
            armySize = state == null ? 0 : state[0];
            List<Mob> army = SeagullArmy.gullsForTest(cobaye.getUUID());
            armyIds = new ArrayList<>();
            double sum = 0;
            for (Mob gull : army) {
                armyIds.add(gull.getUUID());
                sum += gull.distanceTo(cobaye);
            }
            firstDistance = army.isEmpty() ? 0 : sum / army.size();
            check("frapper une mouette la blesse et leve l'armee : " + SeagullArmy.SIZE + " mouettes a 15-21 blocs",
                    hurt && hitGull.getHealth() < hitHealth && armySize >= SeagullArmy.SIZE - 3,
                    "blessee " + hurt + " (" + hitHealth + " -> " + hitGull.getHealth() + "), armee " + armySize
                            + String.format(Locale.ROOT, ", a %.1f blocs en moyenne", firstDistance));
            boolean armored = true;
            boolean jostle = true;
            for (Mob gull : army) {
                armored &= !gull.hurt(level.damageSources().playerAttack(cobaye), 5.0F);
                jostle &= gull.getTeam() == null && gull.isPushable();
            }
            check("les mouettes de l'armee sont invulnerables (comme les cocottes)", !army.isEmpty() && armored, "");
            check("et bousculent (choix du joueur) : aucune equipe sans collision, corps qui heurtent",
                    !army.isEmpty() && jostle && server.getScoreboard().getPlayerTeam("emeraldweapons.armee") == null, "");
            check("pendant sa colere, une seconde mouette frappee ne leve pas une seconde armee",
                    SeagullArmy.trigger(level, cobaye, hitGull) == 0, "");
        }
        if (t > 0) {
            // le cobaye ne tique pas : son invulnerabilite apres un coup ne retomberait jamais.
            // Sans elle, seule la regle des trois coups en une seconde et demie limite l'armee ;
            // et on le soigne a chaque tique, en comptant ce qu'il a perdu
            cobaye.invulnerableTime = 0;
            if (cobaye.getHealth() < cobaye.getMaxHealth()) {
                pecked += cobaye.getMaxHealth() - cobaye.getHealth();
                cobaye.setHealth(cobaye.getMaxHealth());
            }
            List<Mob> army = SeagullArmy.gullsForTest(cobaye.getUUID());
            if (!army.isEmpty() && t % 20 == 0) {
                double sum = 0;
                for (Mob gull : army) {
                    sum += gull.distanceTo(cobaye);
                }
                closest = Math.min(closest, sum / army.size());
            }
            int[] state = SeagullArmy.stateForTest(cobaye.getUUID());
            if (state != null) {
                maxPecks = Math.max(maxPecks, state[1]);
                peckTicks = SeagullArmy.pecksForTest(cobaye.getUUID());
            }
        }
        if (t == SeagullArmy.DURATION - 20) {
            check("l'armee vole autour du joueur : distance moyenne tombee sous 9 blocs",
                    closest < 9.0, String.format(Locale.ROOT, "%.1f au depart, %.1f au plus pres", firstDistance, closest));
            int worst = 0;
            for (int i = 0; i < peckTicks.size(); i++) {
                int inWindow = 0;
                for (int j = i; j < peckTicks.size() && peckTicks.get(j) - peckTicks.get(i) < SeagullArmy.PECK_WINDOW; j++) {
                    inWindow++;
                }
                worst = Math.max(worst, inWindow);
            }
            check("et le pique : des coups de bec d'un demi-coeur, jamais plus de trois sur une seconde et demie",
                    maxPecks >= 3 && worst <= SeagullArmy.PECK_BURST
                            && Math.abs(pecked - maxPecks * SeagullArmy.PECK_DAMAGE) < 0.01F,
                    maxPecks + " coups en 19 s, au plus " + worst + " sur 30 tiques, " + pecked + " points de vie perdus");
        }
        if (t == SeagullArmy.DURATION + SeagullArmy.LEAVE + 20) {
            int left = 0;
            for (Mob gull : HavenFauna.loaded(level, HavenFauna.Role.ARMY)) {
                if (armyIds.contains(gull.getUUID())) {
                    left++;
                }
            }
            check("au bout de 20 s, l'armee s'en va et disparait",
                    SeagullArmy.stateForTest(cobaye.getUUID()) == null && left == 0,
                    left + " mouettes de l'armee encore la");
            check("repit de 30 s : frapper une mouette ne leve pas d'armee tout de suite",
                    SeagullArmy.trigger(level, cobaye, hitGull) == 0, "");
            next(Stage.SHELTER);
        }
    }

    /** L'armee se disperse quand le joueur se met a l'abri. */
    private static void shelter(MinecraftServer server, ServerLevel level) {
        if (t == 0) {
            SeagullArmy.restForTest(cobaye.getUUID());
            cobaye.heal();
            int size = hitGull == null || hitGull.isRemoved() ? 0 : SeagullArmy.trigger(level, cobaye, hitGull);
            if (size == 0) {
                check("a l'abri, l'armee se disperse", false, "pas d'armee levee");
                next(Stage.MODE);
                return;
            }

            // le Hip Hog, zone sure
            BlockPos o = HavenState.get(server).origin();
            BlockPos inside = o.offset(347, 66, 180);
            cobaye.moveTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
        }
        if (t == SeagullArmy.LEAVE + 30) {
            check("a l'abri (Hip Hog), l'armee se disperse sans attendre la fin de sa colere",
                    SeagullArmy.stateForTest(cobaye.getUUID()) == null,
                    "abri " + HavenProtection.inSafeZone(server, cobaye.getX(), cobaye.getY(), cobaye.getZ()));
            next(Stage.MODE);
        }
    }

    // ================================================================ l'invasion, l'accueil

    private static void mode(MinecraftServer server, ServerLevel level) {
        if (t == 0) {
            petsBefore = HavenFauna.loaded(level, HavenFauna.Role.PET).size();
            int gullsBefore = HavenFauna.loaded(level, HavenFauna.Role.GULL).size();
            HavenInvasion.setMode(server, HavenInvasion.Mode.INVASION, null);
            int pets = HavenFauna.loaded(level, HavenFauna.Role.PET).size();
            int gulls = HavenFauna.loaded(level, HavenFauna.Role.GULL).size();
            check("a l'invasion, les chats et les chiens rentrent ; les mouettes restent",
                    petsBefore > 0 && pets == 0 && gulls == gullsBefore, petsBefore + " compagnons -> " + pets
                            + ", mouettes " + gullsBefore + " -> " + gulls);
            welcomeChecks(server, level);
        }
        if (t == 100) {
            check("pendant l'invasion, aucun compagnon ne revient", HavenFauna.loaded(level, HavenFauna.Role.PET).isEmpty(), "");
            HavenInvasion.setMode(server, HavenInvasion.Mode.PAISIBLE, null);
        }
        if (t == 300) {
            int pets = HavenFauna.loaded(level, HavenFauna.Role.PET).size();
            check("la paix revenue, les compagnons reviennent (hors de la vue des joueurs)", pets > 0, pets + " compagnons");
            end(server);
        }
    }

    private static void welcomeChecks(MinecraftServer server, ServerLevel level) {
        String generation = HavenInvasion.generationTag(level);
        Mob cat = Objects.requireNonNull(EntityType.CAT.create(level));
        cat.addTag(HavenFauna.TAG);
        cat.addTag(generation);
        cat.getPersistentData().putString(HavenFauna.ROLE_KEY, HavenFauna.Role.PET.id);
        boolean petInvasion = HavenFauna.welcome(level, cat);
        Mob gull = Objects.requireNonNull(EntityType.PARROT.create(level));
        gull.addTag(HavenFauna.TAG);
        gull.addTag(generation);
        gull.getPersistentData().putString(HavenFauna.ROLE_KEY, HavenFauna.Role.GULL.id);
        boolean gullNow = HavenFauna.welcome(level, gull);
        gull.getPersistentData().putString(HavenFauna.ROLE_KEY, HavenFauna.Role.ARMY.id);
        boolean armyNow = HavenFauna.welcome(level, gull);
        gull.getPersistentData().putString(HavenFauna.ROLE_KEY, HavenFauna.Role.GULL.id);
        gull.removeTag(generation);
        gull.addTag(HavenInvasion.GENERATION_TAG + "old");
        boolean oldGen = HavenFauna.welcome(level, gull);
        check("au rechargement : une mouette d'aujourd'hui revient ; un compagnon pendant l'invasion, une mouette de l'armee, un animal d'une autre generation non",
                gullNow && !petInvasion && !armyNow && !oldGen,
                "mouette " + gullNow + ", compagnon a l'invasion " + petInvasion + ", armee " + armyNow + ", ancienne generation " + oldGen);
        cat.discard();
        gull.discard();
    }

    // ================================================================ mesures et rapport

    private static void sample(MinecraftServer server) {
        if (t % 100 != 99) {
            return;
        }
        long[] times = server.getTickTimesNanos();
        long sum = 0;
        long max = 0;
        for (long v : times) {
            sum += v;
            max = Math.max(max, v);
        }
        WINDOW[0] += sum / times.length;
        WINDOW[1] = Math.max(WINDOW[1], max);
        WINDOW[2]++;
    }

    private static String window(String what) {
        String out = WINDOW[2] == 0 ? what + " : pas de mesure"
                : String.format(Locale.ROOT, "%s : moyenne %.2f ms, pire tique %.2f ms (%d fenetres de 100 tiques)",
                what, WINDOW[0] / (double) WINDOW[2] / 1.0e6, WINDOW[1] / 1.0e6, WINDOW[2]);
        WINDOW[0] = 0;
        WINDOW[1] = 0;
        WINDOW[2] = 0;
        return out;
    }

    private static double averageMs() {
        return WINDOW[2] == 0 ? Double.NaN : WINDOW[0] / (double) WINDOW[2] / 1.0e6;
    }

    private static void end(MinecraftServer server) {
        stage = Stage.END;
        ServerLevel level = Haven.level(server);
        HavenInvasion.TEST_ANCHORS.clear();
        if (level != null) {
            int removed = HavenFauna.removeAll(level) + HavenInvasion.removeAll(level);
            HavenInvasion.setMode(server, HavenInvasion.Mode.PAISIBLE, null);
            for (ChunkPos pos : HELD) {
                level.getChunkSource().removeRegionTicket(JakBuilder.TICKET, pos, TICKET_DISTANCE, pos);
            }
            if (forced != null) {
                level.setChunkForced(forced.x, forced.z, false);
            }
            line("nettoyage : " + removed + " entites retirees, mode " + HavenInvasion.mode(server));
        }
        HELD.clear();
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("faune_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest faune : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest faune : {} OK, {} KO, rapport dans {} ; arret du serveur", passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest faune : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }
}
