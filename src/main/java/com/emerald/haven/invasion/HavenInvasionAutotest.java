package com.emerald.haven.invasion;

import com.emerald.block.HavenInvasionButtonBlock;
import com.emerald.block.ModBlocks;
import com.emerald.game.GameState;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenAutotest;
import com.emerald.haven.HavenRooms;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.haven.traffic.HavenTraffic;
import com.emerald.haven.traffic.HavenTrafficData;
import com.emerald.haven.traffic.TrafficDriver;
import com.emerald.jak.vehicle.JakVehicleEntity;
import com.emerald.haven.HavenVote;
import com.emerald.jak.JakBuilder;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Le banc d'essai de l'invasion, INERTE sans EMERALDWEAPONS_AUTOTEST=invasion.
 *
 * Il lit la carte, essaie la protection et la destruction sur le monde (bloc
 * protege refuse, bloc casse puis reconstruit a l'identique avec son NBT, rien
 * sous l'eau, registre sauve puis relu), fait vivre l'invasion autour de quatre
 * JOUEURS SIMULES (ancres de HavenInvasion, pas de vrais joueurs : un FakePlayer
 * n'est pas dans le niveau), mesure le MSPT au repos, en combat et en ville
 * paisible, verifie types, casques, plafonds, zones sures et distances, l'absence
 * de brulure sur soixante secondes, les degats (un « cobaye » : FakePlayer rendu
 * vulnerable), le point d'accroche de la mort, la garde des poches, le bouton du
 * QG et les habitants, puis le depart. Rapport dans invasion_autotest.txt, dans
 * le dossier du serveur, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenInvasionAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "invasion".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    private static final int SETTLE_TICKS = 40;
    private static final int TIMEOUT_TICKS = 20 * 60 * 20;
    private static final int TICKET_DISTANCE = 6;

    /** Les ancres : quatre points d'eco au sol, a plus de cent blocs les uns des autres (cellules). */
    private static final BlockPos[] ANCHOR_CELLS = {
            new BlockPos(150, 66, 300), new BlockPos(488, 67, 215), new BlockPos(625, 62, 120), new BlockPos(1070, 66, 300)};

    private enum Stage { READY, BASELINE, SPAWN, SUN, COMBAT, BUTTON, FINAL, END }

    private static final StringBuilder OUT = new StringBuilder();
    private static Stage stage = Stage.READY;
    private static int waited;
    private static int t;
    private static int passed;
    private static int failed;

    private static final List<ChunkPos> HELD = new ArrayList<>();
    /**
     * Trois zones de voies loin des ancres (bras ouest, bras est, pont entre les tours) :
     * le trafic n'apparait qu'a plus de 64 blocs d'un joueur, et hors de sa vue a moins
     * de 96 ; autour des ancres, les voies en plein ciel se voient presque toutes.
     */
    private static final BlockPos[] TRAFFIC_CELLS = {
            new BlockPos(300, 76, 520), new BlockPos(930, 76, 520), new BlockPos(650, 76, 610)};
    private static final int TRAFFIC_TICKET_DISTANCE = 4;
    private static final List<ChunkPos> HELD_TRAFFIC = new ArrayList<>();
    private static ChunkPos forced;
    /** Au releve des apparitions : tique de vie et position de chaque monstre, pour prouver qu'ils vivent. */
    private static final Map<UUID, Vec3> SPAWN_POS = new HashMap<>();
    private static final Map<UUID, Integer> SPAWN_TICK = new HashMap<>();
    private static final List<Vec3> ANCHORS = new ArrayList<>();
    private static final List<Cobaye> COBAYES = new ArrayList<>();
    private static final List<HavenMonsterKilledEvent> KILLS = new ArrayList<>();
    private static boolean listening;

    // mesures
    private static final long[] WINDOW = new long[3];          // somme, max, echantillons
    private static String baseline = "";
    private static int maxFireTicks;
    private static int fireSamples;
    private static int sampledMonsters;
    private static int openSkyMonsters;
    private static Mob control;
    private static double combatDamage;
    private static int combatHits;
    private static final Map<UUID, Vec3> VILLAGER_START = new HashMap<>();
    /** Le trafic, tique par tique pendant la ville paisible : derniere position, distance parcourue, tiques observees. */
    private static final Map<UUID, Vec3> TRAFFIC_LAST = new HashMap<>();
    private static final Map<UUID, Double> TRAFFIC_TRAVELLED = new HashMap<>();
    private static final Map<UUID, Integer> TRAFFIC_TICKS = new HashMap<>();

    // destruction
    private static BlockPos streetPos;
    private static BlockState streetState;
    private static BlockPos chestPos;
    private static CompoundTag chestTag;
    private static BlockPos stonePos;
    private static boolean rebuildChecked;

    private HavenInvasionAutotest() {
    }

    /** Un FakePlayer qui prend les coups : le vrai chemin des degats jusqu'aux regles de la ville. */
    static final class Cobaye extends FakePlayer {
        Cobaye(ServerLevel level, GameProfile profile) {
            super(level, profile);
            try {
                Field field = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                field.setAccessible(true);
                field.setInt(this, 0);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("autotest invasion : spawnInvulnerableTime inaccessible", e);
            }
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }

        @Override
        public boolean canHarmPlayer(Player other) {
            return true;
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
                    line("autotest de l'invasion, " + LocalDateTime.now().withNano(0));
                    if (level == null || !HavenInvasion.cityOpen(server)) {
                        check("ville ouverte (lobby ouvert, posee, sans pose)", false, "niveau " + level + ", phase "
                                + HavenState.get(server).phase() + ", posee " + HavenState.get(server).built()
                                + ", lobby " + HavenArrival.lobbyOpen(server));
                        end(server);
                        return;
                    }
                    if (!listening) {
                        listening = true;
                        NeoForge.EVENT_BUS.addListener((HavenMonsterKilledEvent e) -> KILLS.add(e));
                    }
                    data(server);
                    protection(server, level);
                    breaks(server, level);
                    hold(server, level);
                    next(Stage.BASELINE);
                }
                case BASELINE -> {
                    sample(server);
                    if (t >= 200) {
                        baseline = window("sans monstre, tickets tenus");
                        startInvasion(server, level);
                        next(Stage.SPAWN);
                    }
                }
                case SPAWN -> {
                    if (t % 20 == 0) {
                        fire(level);
                    }
                    if (t >= 400) {
                        spawnChecks(server, level);
                        next(Stage.SUN);
                    }
                }
                case SUN -> {
                    sample(server);
                    if (t % 20 == 0) {
                        fire(level);
                    }
                    if (!rebuildChecked && t >= 100) {
                        rebuildChecked = true;
                        rebuildChecks(server, level);
                    }
                    if (t >= 1200) {
                        sunChecks(server, level);
                        line("MSPT au repos avec l'invasion : " + window("monstres sans cible, " + HavenInvasion.monsters().size()
                                + " monstres, 60 s") + " ; reference " + baseline);
                        startCombat(level);
                        next(Stage.COMBAT);
                    }
                }
                case COMBAT -> {
                    sample(server);
                    if (t % 20 == 0) {
                        tally();
                    }
                    // les cibles sont donnees par cinquieme a chaque tique, comme des buts qui se
                    // reveillent chacun a son tour, et non toutes dans la meme tique
                    combat(level, t % 5);
                    if (t >= 600) {
                        double combatMs = averageMs();
                        int fighting = HavenInvasion.monsters().size();
                        line("MSPT en combat : " + window(fighting
                                + " monstres lances sur 4 cobayes, 30 s") + " ; reference " + baseline);
                        check("MSPT moyen en combat sous 30 ms, a " + fighting + " monstres (cinq zones de 13 x 13 troncons et trois de 9 x 9 chargees)",
                                !Double.isNaN(combatMs) && combatMs < 30.0,
                                String.format(Locale.ROOT, "%.2f ms", combatMs));
                        check("en combat, les monstres blessent les cobayes (joueurs hors zone sure)",
                                combatHits > 0 && combatDamage > 0.0,
                                combatHits + " coups portes, " + String.format(Locale.ROOT, "%.1f", combatDamage) + " points de vie");
                        caps(level, "apres 30 s de combat", false);
                        damage(server, level);
                        kill(level);
                        keep(server, level);
                        next(Stage.BUTTON);
                    }
                }
                case BUTTON -> {
                    if (t == 0) {
                        pressPeaceful(server, level);
                    }
                    if (t >= 200) {
                        sample(server);
                        trackTraffic(level);
                    }
                    if (t == 300) {
                        for (Villager villager : HavenInvasion.villagers()) {
                            VILLAGER_START.put(villager.getUUID(), villager.position());
                        }
                    }
                    if (t >= 600) {
                        double peacefulMs = averageMs();
                        int living = HavenInvasion.villagers().size();
                        line("MSPT ville paisible : " + window(living + " habitants et " + HavenTraffic.loaded(level).size()
                                + " vehicules du trafic, 20 s") + " ; reference " + baseline);
                        check("MSPT moyen en ville paisible sous 30 ms, a " + living + " habitants (cinq zones de 13 x 13 troncons et trois de 9 x 9 chargees)",
                                !Double.isNaN(peacefulMs) && peacefulMs < 30.0,
                                String.format(Locale.ROOT, "%.2f ms", peacefulMs));
                        villagerChecks(server, level);
                        trafficChecks(server, level);
                        pressInvasion(server, level);
                        next(Stage.FINAL);
                    }
                }
                case FINAL -> {
                    if (t == 200) {
                        limits(server, level);
                    }
                    // le depart a la tique suivante : les 96 casses du plafond comptent pour la tique du test des limites
                    if (t >= 201) {
                        depart(server, level);
                        end(server);
                    }
                }
                case END -> {
                }
            }
            t++;
        } catch (RuntimeException e) {
            check("banc sans exception (etape " + stage + ")", false, e.toString());
            LOGGER.error("autotest invasion : exception", e);
            end(server);
        }
    }

    private static void next(Stage to) {
        stage = to;
        t = -1;
    }

    // ================================================================ carte

    private static void data(MinecraftServer server) {
        line("--- carte (haven_invasion.json)");
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        if (data == null) {
            check("carte lue", false, "absente ou illisible");
            return;
        }
        Map<HavenInvasionData.EcoColor, Integer> colors = new EnumMap<>(HavenInvasionData.EcoColor.class);
        for (HavenInvasionData.EcoPoint p : data.ecoPoints()) {
            colors.merge(p.color(), 1, Integer::sum);
        }
        check("carte : 958 tuiles au sol (179803 cellules), 1099 tuiles de phantoms, 12 points d'eco, 19 zones protegees,"
                        + " 4 zones sures, bouton, sha1 du volume pose",
                data.groundTiles().size() == 958 && data.groundCells() == 179803 && data.phantomTiles().size() == 1099
                        && data.ecoPoints().size() == 12 && data.protectedZones().size() == 19 && data.safeZones().size() == 4
                        && data.button() != null && data.sha1().equals(HavenState.get(server).sha1()),
                data.groundTiles().size() + " / " + data.groundCells() + " / " + data.phantomTiles().size() + " / "
                        + data.ecoPoints().size() + " / " + data.protectedZones().size() + " / " + data.safeZones().size()
                        + ", couleurs " + colors + ", sha1 carte " + data.sha1() + " pose " + HavenState.get(server).sha1());
        int inSafe = 0;
        int nearDoor = 0;
        int underWater = 0;
        double closest = Double.MAX_VALUE;
        for (HavenInvasionData.GroundTile tile : data.groundTiles()) {
            for (int cell : tile.cells()) {
                int x = HavenInvasionData.unpackX(cell);
                int y = HavenInvasionData.unpackY(cell);
                int z = HavenInvasionData.unpackZ(cell);
                for (HavenInvasionData.Zone zone : data.safeZones()) {
                    if (zone.box().contains(x, y, z)) {
                        inSafe++;
                    }
                }
                for (HavenInvasionData.Center c : data.centers()) {
                    double d = Math.hypot(x - c.x(), z - c.z());
                    closest = Math.min(closest, d);
                    if (d <= data.exclusionRadius()) {
                        nearDoor++;
                    }
                }
                if (y <= data.waterMaxY()) {
                    underWater++;
                }
            }
        }
        check("cellules d'apparition : aucune en zone sure, aucune a 24 blocs ou moins d'une porte ou de l'entree, aucune sous l'eau",
                inSafe == 0 && nearDoor == 0 && underWater == 0,
                inSafe + " en zone sure, " + nearDoor + " trop pres, " + underWater + " sous l'eau ; plus proche "
                        + String.format(Locale.ROOT, "%.1f", closest));
        int badBand = 0;
        for (HavenInvasionData.PhantomTile tile : data.phantomTiles()) {
            if (tile.floor() + 5 < 100 || tile.ceiling() + 5 > 155 || tile.floor() > tile.ceiling() - 20) {
                badBand++;
            }
        }
        check("bande de vol des phantoms entre Y monde 100 et 155, 20 blocs au moins", badBand == 0, badBand + " tuiles hors bande");
    }

    // ================================================================ protection

    private static void protection(MinecraftServer server, ServerLevel level) {
        line("--- protection");
        BlockPos o = HavenState.get(server).origin();
        HavenArrival.Layout layout = HavenArrival.layout(server);
        HavenRooms.Data rooms = HavenRooms.get(server);
        BlockPos button = HavenInvasionButton.position(server);
        record Probe(String what, BlockPos pos) {
        }
        List<Probe> guarded = new ArrayList<>(List.of(
                new Probe("mur de l'appartement 1", o.offset(88, 70, 185)),
                new Probe("eau de la rade (cellule 57, Y 62)", o.offset(600, 57, 400)),
                new Probe("pierre sous la mer (cellule 40)", o.offset(600, 40, 400)),
                new Probe("rideau ouest", o.offset(0, 100, 300)),
                new Probe("comptoir du Hip Hog", o.offset(333, 68, 167)),
                new Probe("parvis du bar", o.offset(362, 65, 210)),
                new Probe("hors de la grille", o.offset(-3, 70, 100))));
        if (layout != null) {
            guarded.add(new Probe("borne de vote (moitie basse)", layout.votePos(o)));
        }
        if (button != null) {
            guarded.add(new Probe("bouton du QG", button));
        }
        if (rooms != null && !rooms.rooms().isEmpty() && rooms.rooms().get(0).car() != null) {
            guarded.add(new Probe("sol de la place de voiture 1", o.offset(rooms.rooms().get(0).car().floor())));
        }
        StringBuilder detail = new StringBuilder();
        boolean all = true;
        for (Probe probe : guarded) {
            level.getChunkAt(probe.pos());
            String why = HavenProtection.reason(level, probe.pos());
            all &= why != null;
            detail.append(probe.what()).append(" -> ").append(why).append(" ; ");
        }
        check("blocs proteges : appartement, eau, dessous de la mer, rideau, comptoir, parvis, hors grille, borne, bouton, place",
                all, detail.toString());
        BlockPos street = o.offset(392, 65, 226);
        level.getChunkAt(street);
        String why = HavenProtection.reason(level, street);
        check("sol de la rue devant le Hip Hog (point d'eco 1) : cassable", why == null,
                "raison " + why + ", bloc " + name(level.getBlockState(street)));
        check("zone sure : centre du Hip Hog dedans, la rue du point d'eco 1 dehors",
                HavenProtection.inSafeZone(server, o.getX() + 347.5, o.getY() + 67, o.getZ() + 180.5)
                        && !HavenProtection.inSafeZone(server, street.getX() + 0.5, street.getY() + 1, street.getZ() + 0.5),
                "bar " + HavenProtection.safeZoneAt(server, o.getX() + 347.5, o.getY() + 67, o.getZ() + 180.5));
        HavenDestruction.Result water = HavenDestruction.breakBlock(level, o.offset(600, 57, 400), null);
        HavenDestruction.Result deep = HavenDestruction.breakBlock(level, o.offset(600, 40, 400), null);
        HavenDestruction.Result wall = HavenDestruction.breakBlock(level, o.offset(88, 70, 185), null);
        HavenDestruction.Result air = HavenDestruction.breakBlock(level, o.offset(392, 90, 226), null);
        HavenDestruction.Result elsewhere = HavenDestruction.breakBlock(server.overworld(), BlockPos.ZERO, null);
        check("casses refusees : eau et dessous de la mer PROTECTED, mur d'appartement PROTECTED, air AIR, hors de Haven NOT_HAVEN",
                water == HavenDestruction.Result.PROTECTED && deep == HavenDestruction.Result.PROTECTED
                        && wall == HavenDestruction.Result.PROTECTED && air == HavenDestruction.Result.AIR
                        && elsewhere == HavenDestruction.Result.NOT_HAVEN,
                water + ", " + deep + ", " + wall + ", " + air + ", " + elsewhere);
    }

    // ================================================================ destruction

    private static void breaks(MinecraftServer server, ServerLevel level) {
        line("--- destruction : casse, registre, rechargement simule");
        BlockPos o = HavenState.get(server).origin();
        int before = HavenDestruction.pendingCount(server);

        streetPos = o.offset(392, 65, 226);
        streetState = level.getBlockState(streetPos);
        HavenDestruction.Result street = HavenDestruction.breakBlock(level, streetPos, null);
        HavenDestruction.Result again = HavenDestruction.breakBlock(level, streetPos, null);

        // un coffre garni : l'etat et le NBT doivent revenir, et rien ne tombe
        chestPos = o.offset(488, 67, 215);
        level.getChunkAt(chestPos);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,
                Direction.EAST), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
            chest.setItem(13, new ItemStack(Items.EMERALD, 7));
            chest.setChanged();
            chestTag = chest.saveWithoutMetadata(level.registryAccess());
        }
        BlockState chestState = level.getBlockState(chestPos);
        HavenDestruction.Result chest = HavenDestruction.breakBlock(level, chestPos, null);
        int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(chestPos).inflate(3)).size();

        // une pierre et sa lanterne : la lanterne ne tient plus, elle part avec
        stonePos = o.offset(150, 66, 300);
        level.getChunkAt(stonePos);
        level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(stonePos.above(), Blocks.LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        HavenDestruction.Result stone = HavenDestruction.breakBlock(level, stonePos, null);
        int lanternDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(stonePos).inflate(3)).size();

        HavenInvasionState state = HavenInvasionState.get(level);
        check("casse : sol de la rue BROKEN puis PENDING ; coffre garni BROKEN sans objet au sol ; pierre et lanterne parties ensemble",
                street == HavenDestruction.Result.BROKEN && again == HavenDestruction.Result.PENDING
                        && chest == HavenDestruction.Result.BROKEN && drops == 0
                        && stone == HavenDestruction.Result.BROKEN && lanternDrops == 0
                        && level.getBlockState(streetPos).isAir() && level.getBlockState(chestPos).isAir()
                        && level.getBlockState(stonePos).isAir() && level.getBlockState(stonePos.above()).isAir()
                        && HavenDestruction.isPending(level, stonePos.above())
                        && HavenDestruction.pendingCount(server) == before + 4,
                "rue " + street + "/" + again + ", coffre " + chest + " (" + drops + " objets), pierre " + stone
                        + " (" + lanternDrops + " objets), en attente " + before + " -> " + HavenDestruction.pendingCount(server));
        HavenInvasionState.Pending entry = state.pendingAt(chestPos);
        long now = level.getGameTime();
        check("registre du coffre : etat complet, NBT d'entite avec ses objets, retour entre 10 et 15 s",
                entry != null && entry.state() == chestState && entry.blockEntity() != null
                        && entry.blockEntity().toString().contains("minecraft:diamond")
                        && entry.due() - now >= HavenDestruction.DELAY_MIN_TICKS && entry.due() - now <= HavenDestruction.DELAY_MAX_TICKS,
                entry == null ? "absent" : "etat " + entry.state() + ", echeance dans " + (entry.due() - now) + " tiques, NBT "
                        + (entry.blockEntity() == null ? "absent" : entry.blockEntity().getList("Items", 10).size() + " piles"));

        int size = HavenDestruction.pendingCount(server);
        int reread = HavenDestruction.reloadForTest(level);
        HavenInvasionState.Pending after = state.pendingAt(chestPos);
        check("registre sauve puis relu (redemarrage simule) : memes entrees, NBT intact, trous toujours vides",
                reread == size && after != null && after.state() == chestState && after.blockEntity() != null
                        && after.blockEntity().equals(entry == null ? null : entry.blockEntity())
                        && level.getBlockState(chestPos).isAir() && level.getBlockState(streetPos).isAir(),
                size + " avant, " + reread + " relues");
    }

    /** Les casses du debut, apres leur echeance : l'identique, du bas vers le haut, NBT compris. */
    private static void rebuildChecks(MinecraftServer server, ServerLevel level) {
        line("--- reconstruction");
        BlockState chest = level.getBlockState(chestPos);
        CompoundTag got = level.getBlockEntity(chestPos) instanceof ChestBlockEntity be
                ? be.saveWithoutMetadata(level.registryAccess()) : null;
        BlockState lantern = level.getBlockState(stonePos.above());
        check("apres 15 s : sol de la rue, coffre (etat et NBT identiques), pierre et lanterne reposes, registre vide",
                level.getBlockState(streetPos) == streetState && chest.is(Blocks.CHEST) && got != null && got.equals(chestTag)
                        && level.getBlockState(stonePos).is(Blocks.STONE) && lantern.is(Blocks.LANTERN)
                        && !lantern.getValue(LanternBlock.HANGING) && !HavenDestruction.isPending(level, chestPos)
                        && HavenDestruction.pendingCount(server) == 0,
                "rue " + name(level.getBlockState(streetPos)) + " (attendu " + name(streetState) + "), coffre " + chest
                        + ", NBT egal " + (got != null && got.equals(chestTag)) + ", pierre " + name(level.getBlockState(stonePos))
                        + ", lanterne " + lantern + ", en attente " + HavenDestruction.pendingCount(server));
        // le banc rend la ville comme il l'a trouvee
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity be) {
            be.clearContent();
        }
        level.setBlock(chestPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(stonePos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(stonePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    // ================================================================ invasion

    private static void hold(MinecraftServer server, ServerLevel level) {
        BlockPos o = HavenState.get(server).origin();
        List<BlockPos> centers = new ArrayList<>();
        for (BlockPos cell : ANCHOR_CELLS) {
            centers.add(o.offset(cell));
        }
        centers.add(o.offset(392, 66, 226));
        for (BlockPos pos : centers) {
            ChunkPos chunk = new ChunkPos(pos);
            level.getChunkSource().addRegionTicket(JakBuilder.TICKET, chunk, TICKET_DISTANCE, chunk);
            HELD.add(chunk);
        }
        // UN TRONCON FORCE : ServerLevel.tick cesse de faire tiquer les entites d'un niveau
        // sans joueur ni troncon force apres 300 tiques (emptyTime). Sans lui, les monstres
        // du banc seraient figes : ni marche, ni combat, ni soleil a mesurer.
        for (BlockPos cell : TRAFFIC_CELLS) {
            ChunkPos chunk = new ChunkPos(o.offset(cell));
            level.getChunkSource().addRegionTicket(JakBuilder.TICKET, chunk, TRAFFIC_TICKET_DISTANCE, chunk);
            HELD_TRAFFIC.add(chunk);
        }
        forced = new ChunkPos(centers.get(0));
        level.setChunkForced(forced.x, forced.z, true);
        line("tickets tenus autour de " + centers.size() + " points (distance " + TICKET_DISTANCE + ") et de "
                + TRAFFIC_CELLS.length + " zones de voies (distance " + TRAFFIC_TICKET_DISTANCE + "), troncon "
                + forced + " force pour que le niveau sans joueur fasse tiquer ses entites");
    }

    private static void startInvasion(MinecraftServer server, ServerLevel level) {
        line("--- apparitions autour de 4 joueurs simules");
        HavenInvasion.setMode(server, HavenInvasion.Mode.INVASION, null);
        BlockPos o = HavenState.get(server).origin();
        for (BlockPos cell : ANCHOR_CELLS) {
            BlockPos feet = o.offset(cell);
            ANCHORS.add(Vec3.atBottomCenterOf(feet));
        }
        HavenInvasion.TEST_ANCHORS.addAll(ANCHORS);
        HavenInvasion.RECENT.clear();
        // le temoin sans casque, a ciel ouvert, a 30 blocs de l'ancre de la cour du bras central
        // sans la regle de vue : a ciel ouvert pres de l'ancre, presque tout se voit
        BlockPos spot = HavenSpawner.findGround(level, Objects.requireNonNull(HavenInvasionData.get(server)), o,
                ANCHORS.get(2), ANCHORS, level.random, false);
        for (int i = 0; i < 40 && spot != null && !level.canSeeSky(spot.above()); i++) {
            spot = HavenSpawner.findGround(level, HavenInvasionData.get(server), o, ANCHORS.get(2), ANCHORS, level.random, false);
        }
        if (spot != null) {
            control = HavenSpawner.spawnMonster(level, HavenInvasion.Kind.ZOMBIE, Vec3.atBottomCenterOf(spot), false);
        }
        line("temoin : zombie SANS casque en " + (spot == null ? "aucune place" : spot.toShortString() + ", ciel "
                + level.canSeeSky(spot.above())) + " ; level.isDay() = " + level.isDay()
                + " (dimension a heure fixe " + level.dimensionType().hasFixedTime() + ")");
    }

    private static void fire(ServerLevel level) {
        for (Mob mob : HavenInvasion.monsters()) {
            fireSamples++;
            maxFireTicks = Math.max(maxFireTicks, mob.getRemainingFireTicks());
            if (mob.isOnFire()) {
                maxFireTicks = Math.max(maxFireTicks, 1);
            }
        }
    }

    private static void spawnChecks(MinecraftServer server, ServerLevel level) {
        List<Mob> monsters = HavenInvasion.monsters();
        Map<HavenInvasion.Kind, Integer> kinds = new EnumMap<>(HavenInvasion.Kind.class);
        for (HavenSpawner.Spawned s : HavenInvasion.RECENT) {
            if (s.kind() != null) {
                kinds.merge(s.kind(), 1, Integer::sum);
            }
        }
        check("les quatre especes apparaissent : zombie, villageois zombie, squelette, phantom",
                kinds.size() == 4, "apparitions " + kinds + ", presents " + HavenInvasion.countByKind());
        caps(level, "apres 20 s d'apparitions", true);

        int helmets = 0;
        int ground = 0;
        StringBuilder bad = new StringBuilder();
        for (Mob mob : monsters) {
            HavenInvasion.Kind kind = HavenInvasion.kindOf(mob);
            if (kind == null || kind == HavenInvasion.Kind.PHANTOM || mob == control) {
                continue;
            }
            ground++;
            ItemStack head = mob.getItemBySlot(EquipmentSlot.HEAD);
            if (head.is(Items.IRON_HELMET) && head.has(DataComponents.UNBREAKABLE) && !head.isDamageableItem()) {
                helmets++;
            } else if (bad.length() < 200) {
                bad.append(kind).append(' ').append(head).append(" ; ");
            }
        }
        check("chaque zombie, villageois zombie et squelette porte un casque de fer incassable",
                ground > 0 && helmets == ground, helmets + " sur " + ground + (bad.isEmpty() ? "" : " ; " + bad));

        HavenInvasionData.Data data = HavenInvasionData.get(server);
        BlockPos o = HavenState.get(server).origin();
        int unsafe = 0;
        int nearDoor = 0;
        int badBand = 0;
        int phantoms = 0;
        double closest = Double.MAX_VALUE;
        for (HavenSpawner.Spawned s : HavenInvasion.RECENT) {
            Vec3 p = s.position();
            if (HavenProtection.inSafeZone(server, p.x, p.y, p.z)) {
                unsafe++;
            }
            for (HavenInvasionData.Center c : Objects.requireNonNull(data).centers()) {
                double d = Math.hypot(p.x - o.getX() - c.x(), p.z - o.getZ() - c.z());
                closest = Math.min(closest, d);
                if (d <= data.exclusionRadius()) {
                    nearDoor++;
                }
            }
            if (s.kind() == HavenInvasion.Kind.PHANTOM) {
                phantoms++;
                if (p.y < 100 || p.y > 156) {
                    badBand++;
                }
            }
        }
        int nowUnsafe = 0;
        for (Mob mob : monsters) {
            if (HavenProtection.inSafeZone(server, mob.getX(), mob.getY(), mob.getZ())) {
                nowUnsafe++;
            }
        }
        check("apparitions : aucune en zone sure, toutes a plus de 24 blocs des portes et de l'entree, phantoms entre Y 100 et 155",
                unsafe == 0 && nearDoor == 0 && badBand == 0 && nowUnsafe == 0 && !HavenInvasion.RECENT.isEmpty(),
                HavenInvasion.RECENT.size() + " apparitions, " + unsafe + " en zone sure (" + nowUnsafe
                        + " maintenant), " + nearDoor + " trop pres (plus proche " + String.format(Locale.ROOT, "%.1f", closest)
                        + "), " + phantoms + " phantoms dont " + badBand + " hors bande");
        int closeGround = 0;
        int seenGround = 0;
        int tooNear = 0;
        double nearestSpawn = Double.MAX_VALUE;
        for (HavenSpawner.Spawned s : HavenInvasion.RECENT) {
            if (s.kind() == HavenInvasion.Kind.PHANTOM) {
                continue;
            }
            Vec3 p = s.position();
            double d = Double.MAX_VALUE;
            for (Vec3 anchor : ANCHORS) {
                d = Math.min(d, HavenInvasion.horizontal(anchor, p.x, p.z));
            }
            nearestSpawn = Math.min(nearestSpawn, d);
            if (d < HavenInvasion.SPAWN_MIN - 0.01) {
                tooNear++;
            }
            if (d <= HavenInvasion.SIGHT_RADIUS) {
                closeGround++;
                if (HavenSpawner.seen(level, ANCHORS, BlockPos.containing(p))) {
                    seenGround++;
                }
            }
        }
        check("apparitions au sol hors de la vue des joueurs a moins de " + (int) HavenInvasion.SIGHT_RADIUS
                        + " blocs, et a " + (int) HavenInvasion.SPAWN_MIN + " blocs d'eux au moins",
                tooNear == 0 && seenGround == 0 && closeGround > 0,
                closeGround + " a moins de " + (int) HavenInvasion.SIGHT_RADIUS + " blocs d'un joueur, " + seenGround
                        + " visibles, " + tooNear + " trop pres (plus proche "
                        + String.format(Locale.ROOT, "%.1f", nearestSpawn) + ")");
        check("monstres persistants (le jeu ne les retire jamais lui-meme), etiquetes", monsters.stream().allMatch(
                m -> m.isPersistenceRequired() && m.getTags().contains(HavenInvasion.MONSTER_TAG)), monsters.size() + " monstres");
        // LE RECHARGEMENT D'UN TRONCON : un monstre de la ville d'aujourd'hui revient en
        // invasion ; un habitant, ou un monstre d'une ville fermee (autre generation), non.
        Mob sample = monsters.stream().filter(m -> m != control && HavenInvasion.kindOf(m) != HavenInvasion.Kind.PHANTOM)
                .findFirst().orElse(null);
        Villager stray = new Villager(EntityType.VILLAGER, level);
        stray.addTag(HavenInvasion.VILLAGER_TAG);
        stray.addTag(HavenInvasion.generationTag(level));
        Zombie stale = new Zombie(level);
        stale.addTag(HavenInvasion.MONSTER_TAG);
        stale.addTag(HavenInvasion.GENERATION_TAG + "-1");
        boolean backOk = sample != null && HavenInvasion.welcome(level, sample);
        boolean strayOk = !HavenInvasion.welcome(level, stray);
        boolean staleOk = !HavenInvasion.welcome(level, stale);
        check("rechargement d'un troncon : un monstre de la ville d'aujourd'hui revient en invasion, un habitant ou un monstre"
                        + " d'une ville fermee est refuse",
                backOk && strayOk && staleOk,
                "monstre accepte " + backOk + ", habitant refuse " + strayOk + ", ancienne generation refusee " + staleOk);
        for (Mob mob : monsters) {
            SPAWN_POS.put(mob.getUUID(), mob.position());
            SPAWN_TICK.put(mob.getUUID(), mob.tickCount);
        }
    }

    /**
     * La population par troncon : les troncons charges (blocs et entites) remplis a
     * 80 % de leur quota au moins, aucun bien au-dessus (les monstres marchent : +4
     * toleres), phantoms <= 4 + 1 par ancre, et le garde-fou des entites chargees.
     * En combat les monstres convergent sur les cobayes : on ne juge plus que le
     * remplissage, a 50 %.
     */
    private static void caps(ServerLevel level, String when, boolean settled) {
        MinecraftServer server = level.getServer();
        List<Mob> monsters = HavenInvasion.monsters();
        int[] phantoms = new int[ANCHORS.size()];
        Map<Long, Integer> counts = new HashMap<>();
        int ground = 0;
        int nearBar = 0;
        for (Mob mob : monsters) {
            if (mob == control) {
                continue;
            }
            if (HavenInvasion.kindOf(mob) == HavenInvasion.Kind.PHANTOM) {
                phantoms[HavenInvasion.nearestIndex(ANCHORS, mob)]++;
            } else {
                counts.merge(HavenInvasion.homeOf(mob), 1, Integer::sum);
                ground++;
                if (HavenInvasion.horizontal(ANCHORS.get(1), mob.getX(), mob.getZ()) <= 160.0) {
                    nearBar++;
                }
            }
        }
        Fill fill = fill(level, Objects.requireNonNull(HavenInvasionData.get(server)), HavenState.get(server).origin(),
                counts, HavenInvasion.MONSTER_CELLS);
        boolean phantomsOk = true;
        StringBuilder per = new StringBuilder();
        for (int p : phantoms) {
            phantomsOk &= p <= HavenInvasion.PHANTOMS_PER_PLAYER + 1;
            per.append(p).append(' ');
        }
        double least = settled ? 0.8 : 0.5;
        boolean ok = !monsters.isEmpty() && ground <= HavenInvasion.LOADED_MAX_MONSTERS && phantomsOk
                && fill.ratio() >= least && (!settled || fill.maxOver() <= 4);
        check("population par troncon " + when + " : troncons charges remplis a " + Math.round(100 * least)
                        + " % de leur quota" + (settled ? ", aucun a plus de 4 au-dessus" : "") + ", phantoms <= "
                        + HavenInvasion.PHANTOMS_PER_PLAYER + " par ancre, <= " + HavenInvasion.LOADED_MAX_MONSTERS + " charges",
                ok, ground + " au sol dans " + fill.tiles() + " troncons charges a quota (quota " + fill.quota() + ", remplis "
                        + Math.round(100 * fill.ratio()) + " %, au plus " + fill.maxOver() + " au-dessus, " + fill.empty()
                        + " vides) ; phantoms par ancre " + per + "; dans 160 blocs de l'ancre 2 (dix troncons de vue) : "
                        + nearBar + " au sol");
    }

    /** Le remplissage des troncons charges a quota : quota total, presents comptes jusqu'au quota, depassement maximal, vides. */
    private record Fill(int tiles, int quota, int inQuota, int maxOver, int empty) {
        double ratio() {
            return this.quota == 0 ? 1.0 : this.inQuota / (double) this.quota;
        }
    }

    private static Fill fill(ServerLevel level, HavenInvasionData.Data data, BlockPos origin, Map<Long, Integer> counts,
                             int cellsPer) {
        int tiles = 0;
        int quota = 0;
        int inQuota = 0;
        int maxOver = 0;
        int empty = 0;
        for (HavenInvasionData.GroundTile tile : data.groundTiles()) {
            long key = HavenInvasion.chunkKey(origin, tile);
            if (!level.getChunkSource().hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)) || !level.areEntitiesLoaded(key)) {
                continue;
            }
            int want = HavenInvasion.quota(tile, cellsPer);
            int have = counts.getOrDefault(key, 0);
            if (want > 0) {
                tiles++;
                quota += want;
                inQuota += Math.min(have, want);
                if (have == 0) {
                    empty++;
                }
            }
            maxOver = Math.max(maxOver, have - want);
        }
        return new Fill(tiles, quota, inQuota, maxOver, empty);
    }

    private static void sunChecks(MinecraftServer server, ServerLevel level) {
        line("--- soleil fige");
        openSkyMonsters = 0;
        sampledMonsters = 0;
        int hurt = 0;
        for (Mob mob : HavenInvasion.monsters()) {
            sampledMonsters++;
            if (level.canSeeSky(BlockPos.containing(mob.getX(), mob.getEyeY(), mob.getZ()))) {
                openSkyMonsters++;
            }
            if (mob.getHealth() < mob.getMaxHealth()) {
                hurt++;
            }
        }
        int alive = 0;
        int ticked = 0;
        int moved = 0;
        int frozen = 0;
        int phantoms = 0;
        int phantomsTicked = 0;
        for (Mob mob : HavenInvasion.monsters()) {
            Integer tick0 = SPAWN_TICK.get(mob.getUUID());
            if (tick0 == null) {
                continue;
            }
            boolean lived = mob.tickCount - tick0 >= 1000;
            if (HavenInvasion.kindOf(mob) == HavenInvasion.Kind.PHANTOM) {
                // un phantom tourne loin de son point d'apparition et peut sortir des troncons
                // que le banc fait tiquer (quatre autour de chaque ancre) : releve, sans exigence
                phantoms++;
                phantomsTicked += lived ? 1 : 0;
                continue;
            }
            // charge sans tiquer (troncon au bord des tickets) : gele, comme au-dela de la
            // distance de simulation en jeu ; on ne juge que ceux qui tiquent
            if (!level.isPositionEntityTicking(mob.blockPosition())) {
                frozen++;
                continue;
            }
            alive++;
            ticked += lived ? 1 : 0;
            if (SPAWN_POS.get(mob.getUUID()).distanceTo(mob.position()) > 1.0) {
                moved++;
            }
        }
        // SANS VRAI JOUEUR, UN MONSTRE FLANE QUAND MEME. En pleine lumiere, noActionTime
        // montait (Monster.updateNoActionTime) jusqu'a ce que RandomStrollGoal refuse de
        // flaner, et seul un joueur a moins de 32 blocs le remettait a zero : le premier
        // releve donnait 0 deplace sur 48, et le joueur a trouve les rues vides. Les
        // monstres sont persistants : Mob.checkDespawn le remet a zero a chaque tique.
        check("les monstres au sol vivent pendant la minute et flanent sans joueur proche : ils tiquent,"
                        + " la moitie au moins s'est deplacee de plus d'un bloc",
                alive > 0 && ticked == alive && moved * 2 >= alive,
                alive + " au sol dans des troncons qui tiquent, " + ticked + " ont tique ; " + moved
                        + " deplaces de plus d'un bloc ; " + frozen + " geles dans des troncons charges sans tique ; phantoms " + phantomsTicked + " sur " + phantoms
                        + " ont tique (les autres sortis des troncons du banc)");
        boolean controlOk = control != null && control.isAlive() && !control.isOnFire()
                && control.getHealth() == control.getMaxHealth() && control.tickCount >= 1400;
        check("60 s au soleil fige de Haven : aucun monstre en feu, y compris le zombie temoin sans casque",
                maxFireTicks <= 0 && fireSamples > 0 && controlOk,
                fireSamples + " releves, feu max " + maxFireTicks + " tiques ; " + openSkyMonsters + " monstres sur "
                        + sampledMonsters + " a ciel ouvert maintenant ; " + hurt + " blesses ; temoin "
                        + (control == null ? "absent" : "vivant " + control.isAlive() + ", sante " + control.getHealth()
                        + ", tiques " + control.tickCount + ", ciel " + level.canSeeSky(control.blockPosition().above())));
    }

    private static void startCombat(ServerLevel level) {
        for (int i = 0; i < ANCHORS.size(); i++) {
            Cobaye cobaye = new Cobaye(level, new GameProfile(UUID.nameUUIDFromBytes(("invasion:cobaye" + i)
                    .getBytes(StandardCharsets.UTF_8)), "[Cobaye" + i + "]"));
            Vec3 a = ANCHORS.get(i);
            cobaye.moveTo(a.x, a.y, a.z, 0.0F, 0.0F);
            cobaye.heal();
            COBAYES.add(cobaye);
        }
    }

    /** Les points de vie perdus par les cobayes depuis le releve precedent, puis soin. */
    private static void tally() {
        for (Cobaye cobaye : COBAYES) {
            float lost = cobaye.getMaxHealth() - cobaye.getHealth();
            if (lost > 0.0F) {
                combatHits++;
                combatDamage += lost;
            }
            cobaye.heal();
        }
    }

    /** Lance sur le cobaye le plus proche le cinquieme des monstres dont l'identifiant tombe sur cette part. */
    private static void combat(ServerLevel level, int part) {
        for (Mob mob : HavenInvasion.monsters()) {
            if (Math.floorMod(mob.getId(), 5) != part) {
                continue;
            }
            Cobaye best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Cobaye cobaye : COBAYES) {
                double d = mob.distanceToSqr(cobaye);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = cobaye;
                }
            }
            if (best != null && mob.getTarget() != best) {
                mob.setTarget(best);
            }
        }
    }

    // ================================================================ degats

    private static void damage(MinecraftServer server, ServerLevel level) {
        line("--- degats");
        Cobaye victim = COBAYES.get(0);
        Cobaye other = COBAYES.get(1);
        Vec3 street = ANCHORS.get(0);
        victim.moveTo(street.x, street.y, street.z);
        other.moveTo(street.x + 2, street.y, street.z);
        Zombie zombie = (Zombie) HavenSpawner.spawnMonster(level, HavenInvasion.Kind.ZOMBIE, street.add(1, 0, 0), true);
        Skeleton skeleton = (Skeleton) HavenSpawner.spawnMonster(level, HavenInvasion.Kind.SKELETON, street.add(-1, 0, 0), true);
        if (zombie == null || skeleton == null) {
            check("degats : monstres de test poses", false, "zombie " + zombie + ", squelette " + skeleton);
            return;
        }
        victim.heal();
        boolean zombieHit = zombie.doHurtTarget(victim);
        float afterZombie = victim.getHealth();
        victim.heal();
        Arrow skeletonArrow = new Arrow(level, skeleton, new ItemStack(Items.ARROW), null);
        boolean arrowHit = victim.hurt(level.damageSources().arrow(skeletonArrow, skeleton), 4.0F);
        float afterArrow = victim.getHealth();
        check("un monstre blesse un joueur dans la rue : coup de zombie, fleche de squelette",
                zombieHit && afterZombie < victim.getMaxHealth() && arrowHit && afterArrow < victim.getMaxHealth(),
                "zombie " + zombieHit + " (sante " + afterZombie + "), fleche " + arrowHit + " (sante " + afterArrow + ")");

        BlockPos o = HavenState.get(server).origin();
        victim.heal();
        victim.moveTo(o.getX() + 347.5, o.getY() + 67, o.getZ() + 180.5);
        boolean safeHit = victim.hurt(level.damageSources().mobAttack(zombie), 3.0F);
        check("dans le Hip Hog (zone sure), un monstre ne blesse pas", !safeHit && victim.getHealth() == victim.getMaxHealth(),
                "coup " + safeHit + ", sante " + victim.getHealth());
        victim.moveTo(street.x, street.y, street.z);

        victim.heal();
        boolean melee = victim.hurt(level.damageSources().playerAttack(other), 5.0F);
        Arrow playerArrow = new Arrow(level, other, new ItemStack(Items.ARROW), null);
        boolean arrow = victim.hurt(level.damageSources().arrow(playerArrow, other), 5.0F);
        boolean explosion = victim.hurt(level.damageSources().explosion(other, other), 8.0F);
        boolean projectileNoOwner = victim.hurt(level.damageSources().explosion(playerArrow, other), 8.0F);
        boolean fall = victim.hurt(level.damageSources().fall(), 6.0F);
        check("aucun degat entre joueurs : coup, fleche, explosion causee par un joueur ; ni chute",
                !melee && !arrow && !explosion && !projectileNoOwner && !fall && victim.getHealth() == victim.getMaxHealth(),
                "coup " + melee + ", fleche " + arrow + ", explosion " + explosion + ", explosion par projectile "
                        + projectileNoOwner + ", chute " + fall + ", sante " + victim.getHealth());
        check("la regle pure : monstre oui, joueur non",
                HavenRules.playerMayBeHurt(victim, level.damageSources().mobAttack(zombie))
                        && !HavenRules.playerMayBeHurt(victim, level.damageSources().playerAttack(other)), "");
        zombie.discard();
        skeleton.discard();
    }

    private static void kill(ServerLevel level) {
        line("--- mort d'un monstre de Haven");
        Cobaye killer = COBAYES.get(0);
        Zombie zombie = (Zombie) HavenSpawner.spawnMonster(level, HavenInvasion.Kind.ZOMBIE, ANCHORS.get(0).add(3, 0, 0), true);
        if (zombie == null) {
            check("mort : zombie pose", false, "");
            return;
        }
        int before = KILLS.size();
        AABB around = zombie.getBoundingBox().inflate(4);
        int itemsBefore = level.getEntitiesOfClass(ItemEntity.class, around).size();
        int orbsBefore = level.getEntitiesOfClass(ExperienceOrb.class, around).size();
        zombie.hurt(level.damageSources().playerAttack(killer), 1000.0F);
        HavenMonsterKilledEvent event = KILLS.size() > before ? KILLS.get(KILLS.size() - 1) : null;
        int items = level.getEntitiesOfClass(ItemEntity.class, around).size() - itemsBefore;
        int orbs = level.getEntitiesOfClass(ExperienceOrb.class, around).size() - orbsBefore;
        check("HavenMonsterKilledEvent poste une fois, avec l'espece et le tueur ; ni butin ni experience au sol",
                zombie.isDeadOrDying() && KILLS.size() == before + 1 && event != null && event.getMonster() == zombie
                        && event.getKind() == HavenInvasion.Kind.ZOMBIE && event.getKiller() == killer && items == 0 && orbs == 0,
                "mort " + zombie.isDeadOrDying() + ", evenements " + (KILLS.size() - before) + ", espece "
                        + (event == null ? null : event.getKind()) + ", tueur " + (event == null ? null : event.getKiller())
                        + ", objets " + items + ", orbes " + orbs);
    }

    private static void keep(MinecraftServer server, ServerLevel level) {
        line("--- mort d'un joueur : poches gardees, reapparition dans l'appartement");
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes("invasion:mort".getBytes(StandardCharsets.UTF_8)),
                "[InvasionMort]");
        Cobaye dying = new Cobaye(level, profile);
        dying.moveTo(ANCHORS.get(0).x, ANCHORS.get(0).y, ANCHORS.get(0).z);
        dying.getInventory().add(new ItemStack(Items.DIAMOND, 5));
        dying.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
        dying.getInventory().offhand.set(0, new ItemStack(Items.TORCH, 9));
        dying.experienceLevel = 7;
        dying.experienceProgress = 0.25F;
        dying.totalExperience = 150;
        HavenRules.onPlayerDeath(new LivingDeathEvent(dying, level.damageSources().generic()));
        LivingExperienceDropEvent xp = new LivingExperienceDropEvent(dying, null, 49);
        HavenRules.onPlayerExperienceDrop(xp);
        boolean emptied = dying.getInventory().isEmpty();
        Cobaye reborn = new Cobaye(level, profile);
        HavenRules.onClone(new PlayerEvent.Clone(reborn, dying, true));
        check("mort dans Haven : poches videes avant la chute des objets, experience non lachee, tout rendu au joueur reapparu",
                emptied && xp.isCanceled() && reborn.getInventory().countItem(Items.DIAMOND) == 5
                        && reborn.getInventory().armor.get(3).is(Items.IRON_HELMET)
                        && reborn.getInventory().offhand.get(0).is(Items.TORCH) && reborn.experienceLevel == 7
                        && reborn.totalExperience == 150 && !HavenKeep.has(server, profile.getId()),
                "videes " + emptied + ", xp annulee " + xp.isCanceled() + ", diamants " + reborn.getInventory().countItem(Items.DIAMOND)
                        + ", casque " + reborn.getInventory().armor.get(3) + ", niveau " + reborn.experienceLevel);

        HavenState state = HavenState.get(server);
        try {
            HavenArrival.Placement place = HavenArrival.place(server, profile.getId(), profile.getName());
            if (place == null) {
                check("reapparition : place d'appartement", false, "aucune");
                return;
            }
            HavenArrival.setRespawn(reborn, place);
            DimensionTransition respawn = reborn.findRespawnPositionAndUseSpawnBlock(false, DimensionTransition.DO_NOTHING);
            BlockPos o = state.origin();
            boolean inRoom = HavenArrival.inside(o.offset(place.room().boxMin()), o.offset(place.room().boxMax()),
                    respawn.pos().x, respawn.pos().y, respawn.pos().z);
            check("a la mort, le jeu fait reapparaitre dans l'appartement (point force dans Haven)",
                    respawn.newLevel() == level && inRoom && !respawn.missingRespawnBlock(),
                    respawn.newLevel().dimension().location() + " " + respawn.pos() + ", appartement " + place.room().number());
        } finally {
            state.removeApartment(profile.getId());
            state.forgetMode(profile.getId());
        }
    }

    // ================================================================ bouton et habitants

    private static void pressPeaceful(MinecraftServer server, ServerLevel level) {
        line("--- bouton du QG : paisible");
        BlockPos pos = HavenInvasionButton.keep(server, true);
        BlockState before = pos == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos);
        check("bouton pose sur le comptoir, voyant rouge en invasion",
                pos != null && before.is(ModBlocks.HAVEN_INVASION_BUTTON.get()) && !before.getValue(HavenInvasionButtonBlock.PEACEFUL)
                        && before.getValue(HavenInvasionButtonBlock.FACING) == Direction.SOUTH
                        && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP),
                (pos == null ? "sans place" : pos.toShortString()) + " : " + before + ", dessous " + (pos == null ? "" : name(level.getBlockState(pos.below()))));
        if (pos == null || !before.is(ModBlocks.HAVEN_INVASION_BUTTON.get())) {
            return;
        }
        check("bouton incassable, sans butin, hors des armes",
                before.getDestroySpeed(level, pos) < 0 && HavenDestruction.breakBlock(level, pos, null) == HavenDestruction.Result.PROTECTED,
                "durete " + before.getDestroySpeed(level, pos));
        int monsters = HavenInvasion.monsters().size();
        HavenInvasion.resetButtonCooldown();
        Cobaye presser = COBAYES.get(0);
        presser.moveTo(pos.getX() + 0.5, pos.getY() - 1, pos.getZ() + 1.5);
        before.useWithoutItem(level, presser, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        long left = level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Mob.class),
                m -> m.getTags().contains(HavenInvasion.MONSTER_TAG)).stream().filter(m -> !m.isRemoved()).count();
        BlockState lit = level.getBlockState(pos);
        check("clic sur le bouton : mode PAISIBLE, tous les monstres retires, voyant bleu",
                HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE && left == 0 && HavenInvasion.monsters().isEmpty()
                        && lit.getValue(HavenInvasionButtonBlock.PEACEFUL),
                "mode " + HavenInvasion.mode(server) + ", monstres " + monsters + " -> " + left + ", voyant " + lit);
        before.useWithoutItem(level, presser, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        check("second clic dans les 2 s : refuse (le bouton se recharge)", HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE,
                "mode " + HavenInvasion.mode(server));
    }

    private static void villagerChecks(MinecraftServer server, ServerLevel level) {
        line("--- ville paisible : habitants");
        List<Villager> villagers = HavenInvasion.villagers();
        Set<VillagerType> types = new HashSet<>();
        int invulnerable = 0;
        int noJob = 0;
        int unsafe = 0;
        int moved = 0;
        int compared = 0;
        StringBuilder per = new StringBuilder();
        for (Villager villager : villagers) {
            types.add(villager.getVillagerData().getType());
            if (villager.isInvulnerable()) {
                invulnerable++;
            }
            if (villager.getVillagerData().getProfession() == VillagerProfession.NONE) {
                noJob++;
            }
            if (HavenProtection.inSafeZone(server, villager.getX(), villager.getY(), villager.getZ())) {
                unsafe++;
            }
            Vec3 start = VILLAGER_START.get(villager.getUUID());
            if (start != null && level.isPositionEntityTicking(villager.blockPosition())) {
                compared++;
                if (start.distanceTo(villager.position()) > 1.5) {
                    moved++;
                }
            }
        }
        Map<Long, Integer> counts = new HashMap<>();
        for (Villager villager : villagers) {
            counts.merge(HavenInvasion.homeOf(villager), 1, Integer::sum);
        }
        Fill fill = fill(level, Objects.requireNonNull(HavenInvasionData.get(server)), HavenState.get(server).origin(),
                counts, HavenInvasion.VILLAGER_CELLS);
        Villager first = villagers.isEmpty() ? null : villagers.get(0);
        check("habitants des 7 regions presents, par troncon (une cellule sur " + HavenInvasion.VILLAGER_CELLS
                        + "), troncons charges remplis a 80 % au moins, persistants et acceptes au rechargement,"
                        + " sans metier, hors zones sures, aucun monstre",
                types.size() == 7 && villagers.size() <= HavenInvasion.LOADED_MAX_VILLAGERS && fill.ratio() >= 0.8
                        && fill.maxOver() <= 4 && noJob == villagers.size() && unsafe == 0 && HavenInvasion.monsters().isEmpty()
                        && first != null && first.isPersistenceRequired() && HavenInvasion.welcome(level, first),
                villagers.size() + " habitants dans " + fill.tiles() + " troncons charges a quota (quota " + fill.quota()
                        + ", remplis " + Math.round(100 * fill.ratio()) + " %, au plus " + fill.maxOver() + " au-dessus, "
                        + fill.empty() + " vides), regions " + types.size() + " " + types + ", sans metier " + noJob
                        + ", en zone sure " + unsafe + per);
        check("les habitants qui tiquent marchent dans les rues (deplaces de plus de 1,5 bloc en 15 s)",
                compared > 0 && moved * 2 >= compared, moved + " sur " + compared);
        if (villagers.isEmpty()) {
            return;
        }
        Villager villager = villagers.get(0);
        Cobaye player = COBAYES.get(0);
        float health = villager.getHealth();
        boolean hit = villager.hurt(level.damageSources().playerAttack(player), 10.0F);
        boolean blast = villager.hurt(level.damageSources().explosion(player, player), 20.0F);
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        boolean shot = villager.hurt(level.damageSources().arrow(arrow, player), 10.0F);
        PlayerInteractEvent.EntityInteract trade = new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, villager);
        NeoForge.EVENT_BUS.post(trade);
        check("habitants invulnerables aux joueurs et aux armes, et sans commerce (clic annule)",
                invulnerable == villagers.size() && !hit && !blast && !shot && villager.getHealth() == health && trade.isCanceled()
                        && villager.getOffers().isEmpty(),
                "invulnerables " + invulnerable + "/" + villagers.size() + ", coup " + hit + ", explosion " + blast + ", fleche "
                        + shot + ", sante " + villager.getHealth() + ", clic annule " + trade.isCanceled());
    }

    private static void pressInvasion(MinecraftServer server, ServerLevel level) {
        line("--- bouton du QG : retour de l'invasion");
        BlockPos pos = HavenInvasionButton.position(server);
        if (pos == null) {
            return;
        }
        BlockState button = level.getBlockState(pos);
        button.useWithoutItem(level, COBAYES.get(0), new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        long left = level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Villager.class),
                v -> v.getTags().contains(HavenInvasion.VILLAGER_TAG)).stream().filter(v -> !v.isRemoved()).count();
        check("nouveau clic apres le delai : INVASION, habitants retires, voyant rouge",
                HavenInvasion.mode(server) == HavenInvasion.Mode.INVASION && left == 0
                        && !level.getBlockState(pos).getValue(HavenInvasionButtonBlock.PEACEFUL),
                "mode " + HavenInvasion.mode(server) + ", habitants restants " + left + ", voyant " + level.getBlockState(pos));
        check("retour de l'invasion : vehicules du trafic et pilotes retires",
                HavenTraffic.loaded(level).isEmpty() && HavenTraffic.drivers(level).isEmpty(),
                HavenTraffic.loaded(level).size() + " vehicules, " + HavenTraffic.drivers(level).size() + " pilotes");
    }

    /** La distance parcourue par chaque vehicule du trafic, tique par tique. */
    private static void trackTraffic(ServerLevel level) {
        for (JakVehicleEntity car : HavenTraffic.loaded(level)) {
            Vec3 last = TRAFFIC_LAST.get(car.getUUID());
            if (last != null) {
                TRAFFIC_TRAVELLED.merge(car.getUUID(), last.distanceTo(car.position()), Double::sum);
            }
            TRAFFIC_LAST.put(car.getUUID(), car.position());
            TRAFFIC_TICKS.merge(car.getUUID(), 1, Integer::sum);
        }
    }

    /**
     * Le trafic de la ville paisible : des vehicules sur les branches chargees, chacun
     * sur sa voie et a son altitude, avec un pilote, qui roulent, et acceptes au
     * rechargement.
     */
    private static void trafficChecks(MinecraftServer server, ServerLevel level) {
        line("--- ville paisible : le trafic");
        HavenTrafficData.Data data = HavenTrafficData.get(server);
        if (data == null) {
            check("trafic : voies lues (haven_traffic.json)", false, "absentes");
            return;
        }
        BlockPos o = HavenState.get(server).origin();
        List<JakVehicleEntity> cars = HavenTraffic.loaded(level);
        int branchesLoaded = 0;
        int quotaLoaded = 0;
        for (HavenTrafficData.Branch branch : data.branches()) {
            long chunk = HavenTraffic.spawnChunk(data, branch, o);
            if (level.getChunkSource().hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk)) && level.areEntitiesLoaded(chunk)) {
                branchesLoaded++;
                quotaLoaded += HavenTraffic.quota(branch);
            }
        }
        int onLane = 0;
        int atHeight = 0;
        int withDriver = 0;
        int compared = 0;
        int moving = 0;
        int cruising = 0;
        int frozen = 0;
        double worstLateral = 0.0;
        double worstHeight = 0.0;
        Villager anyDriver = null;
        List<JakVehicleEntity> stuck = new ArrayList<>();
        for (JakVehicleEntity car : cars) {
            TrafficDriver driver = Objects.requireNonNull(car.traffic());
            HavenTrafficData.Branch branch = data.branch(driver.branch());
            Vec3 a = data.start(branch, o);
            Vec3 e = data.end(branch, o);
            double length = Math.hypot(e.x - a.x, e.z - a.z);
            double dx = (e.x - a.x) / length;
            double dz = (e.z - a.z) / length;
            double s = (car.getX() - a.x) * dx + (car.getZ() - a.z) * dz;
            double lateral = Math.abs((car.getX() - a.x) * dz - (car.getZ() - a.z) * dx);
            worstLateral = Math.max(worstLateral, lateral);
            if (lateral <= branch.width() + 4.0 && s >= -8.0 && s <= length + 8.0) {
                onLane++;
            }
            double laneY = HavenTraffic.laneY(o, car.getX(), car.getZ());
            worstHeight = Math.max(worstHeight, Math.abs(car.getY() - laneY));
            if (Math.abs(car.getY() - laneY) <= 3.0) {
                atHeight++;
            }
            if (car.occupant(0) instanceof Villager villager && villager.getTags().contains(HavenTraffic.DRIVER_TAG)) {
                withDriver++;
                anyDriver = villager;
            }
            if (!level.isPositionEntityTicking(car.blockPosition())) {
                frozen++;                 // retire a la prochaine seconde par HavenTraffic.update
            }
            // observe deux secondes au moins : a 15 m/s, dix blocs se font en 0,7 s
            if (TRAFFIC_TICKS.getOrDefault(car.getUUID(), 0) >= 40) {
                compared++;
                if (TRAFFIC_TRAVELLED.getOrDefault(car.getUUID(), 0.0) > 10.0) {
                    moving++;
                } else if (stuck.size() < 8) {
                    stuck.add(car);
                }
            }
            Vec3 v = car.getDeltaMovement();
            double speed = Math.hypot(v.x, v.z) * 20.0;
            if (speed >= 5.0 && speed <= 22.0) {
                cruising++;
            }
        }
        check("trafic : des vehicules sur les voies chargees (30 % du quota au moins, " + HavenTraffic.LOADED_MAX
                        + " au plus), chacun sur sa branche, a l'altitude de la voie, avec un pilote",
                !cars.isEmpty() && cars.size() * 10 >= quotaLoaded * 3 && cars.size() <= HavenTraffic.LOADED_MAX
                        && onLane == cars.size() && atHeight == cars.size() && withDriver == cars.size(),
                cars.size() + " vehicules pour un quota de " + quotaLoaded + " sur " + branchesLoaded + " branches chargees ;"
                        + " sur leur voie " + onLane + " (ecart maximal " + String.format(Locale.ROOT, "%.1f", worstLateral)
                        + "), a l'altitude " + atHeight + " (ecart maximal " + String.format(Locale.ROOT, "%.1f", worstHeight)
                        + "), avec pilote " + withDriver);
        for (JakVehicleEntity car : stuck) {
            TrafficDriver driver = Objects.requireNonNull(car.traffic());
            HavenTrafficData.Branch branch = data.branch(driver.branch());
            Vec3 a = data.start(branch, o);
            Vec3 e = data.end(branch, o);
            double length = Math.hypot(e.x - a.x, e.z - a.z);
            Vec3 dir = new Vec3((e.x - a.x) / length, 0.0, (e.z - a.z) / length);
            Vec3 v = car.getDeltaMovement();
            Vec3 allowed = com.emerald.jak.vehicle.VehiclePhysics.collide(car, dir.scale(0.5));
            JakVehicleEntity nearest = null;
            double best = Double.MAX_VALUE;
            for (JakVehicleEntity other : level.getEntitiesOfClass(JakVehicleEntity.class, car.getBoundingBox().inflate(48.0), x -> x != car)) {
                double d = other.distanceTo(car);
                if (d < best) {
                    best = d;
                    nearest = other;
                }
            }
            String neighbour = nearest == null ? "aucun voisin a 48 blocs" : String.format(Locale.ROOT,
                    "voisin %s a %.1f (devant %.1f, de cote %.1f, trafic %b)", nearest.model(), best,
                    (nearest.getX() - car.getX()) * dir.x + (nearest.getZ() - car.getZ()) * dir.z,
                    Math.abs((nearest.getX() - car.getX()) * dir.z - (nearest.getZ() - car.getZ()) * dir.x), nearest.traffic() != null);
            line(String.format(Locale.ROOT, "    immobile : %s branche %d -> %d en (%.1f ; %.1f ; %.1f), vitesse %.1f m/s, passage libre %.2f sur 0,50, limite de devant %.1f m/s, %s",
                    car.model(), driver.branch(), driver.next(), car.getX(), car.getY(), car.getZ(), Math.hypot(v.x, v.z) * 20.0,
                    allowed.length(), Math.min(99.0, TrafficDriver.leaderLimit(car, dir, v) * 20.0), neighbour));
        }
        check("trafic : les vehicules roulent (les trois quarts de ceux observes deux secondes au moins ont parcouru plus de"
                        + " 10 blocs ; les trois quarts entre 5 et 22 m/s)",
                compared > 0 && moving * 4 >= compared * 3 && cruising * 4 >= cars.size() * 3,
                moving + " sur " + compared + " ont parcouru plus de 10 blocs (" + TRAFFIC_TICKS.size()
                        + " vehicules observes en tout, " + frozen + " geles en attente de retrait) ; "
                        + cruising + " sur " + cars.size() + " entre 5 et 22 m/s");
        if (!cars.isEmpty() && anyDriver != null) {
            check("trafic : vehicule et pilote persistants, acceptes au rechargement en paisible",
                    cars.get(0).getTags().contains(HavenTraffic.TRAFFIC_TAG) && HavenInvasion.welcome(level, cars.get(0))
                            && anyDriver.isPersistenceRequired() && HavenInvasion.welcome(level, anyDriver),
                    "vehicule " + HavenInvasion.welcome(level, cars.get(0)) + ", pilote " + HavenInvasion.welcome(level, anyDriver));
        }
    }

    // ================================================================ limites et depart

    private static void limits(MinecraftServer server, ServerLevel level) {
        line("--- limites de la destruction");
        BlockPos o = HavenState.get(server).origin();
        // un bloc pose juste au-dessus de la mer : il toucherait l'eau
        BlockPos shore = o.offset(620, 58, 400);
        level.getChunkAt(shore);
        boolean seaBelow = !level.getFluidState(shore.below()).isEmpty();
        level.setBlock(shore, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        HavenDestruction.Result wet = HavenDestruction.breakBlock(level, shore, null);
        level.setBlock(shore, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        check("un bloc qui touche l'eau (cellule 58 sur la mer) : LIQUID, jamais casse", seaBelow && wet == HavenDestruction.Result.LIQUID,
                "eau dessous " + seaBelow + ", resultat " + wet);

        // cent pierres dans le ciel de la rade : 96 casses par tique au plus
        List<BlockPos> stones = new ArrayList<>();
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                BlockPos p = o.offset(600 + x * 2, 100, 400 + z * 2);
                level.getChunkAt(p);
                level.setBlock(p, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                stones.add(p);
            }
        }
        int broken = 0;
        int limited = 0;
        for (BlockPos p : stones) {
            HavenDestruction.Result r = HavenDestruction.breakBlock(level, p, null);
            if (r == HavenDestruction.Result.BROKEN) {
                broken++;
            } else if (r == HavenDestruction.Result.TICK_LIMIT) {
                limited++;
            }
        }
        int rebuilt = HavenDestruction.rebuildAll(level);
        boolean back = stones.stream().limit(broken).allMatch(p -> level.getBlockState(p).is(Blocks.STONE));
        for (BlockPos p : stones) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        check("plafond de casses par tique (" + HavenDestruction.BREAKS_PER_TICK + "), puis reconstruction immediate de tout le registre",
                broken <= HavenDestruction.BREAKS_PER_TICK && limited == stones.size() - broken && rebuilt == broken && back
                        && HavenDestruction.pendingCount(server) == 0,
                broken + " cassees, " + limited + " refusees pour la tique, " + rebuilt + " reconstruites");
    }

    private static void depart(MinecraftServer server, ServerLevel level) {
        line("--- depart vers la partie");
        HavenState state = HavenState.get(server);
        GameState game = GameState.get(server.overworld());
        GameState.Mode modeBefore = game.mode();
        boolean chosenBefore = game.modeChosen();
        BlockPos o = state.origin();
        BlockPos street = o.offset(392, 65, 226);
        BlockState streetBefore = level.getBlockState(street);
        HavenDestruction.Result broke = HavenDestruction.breakBlock(level, street, null);
        int monsters = HavenInvasion.monsters().size();
        BlockPos button = HavenInvasionButton.position(server);
        try {
            HavenVote.depart(server, modeBefore);
            HavenInvasion.update(server);
            HavenInvasionButton.keep(server, true);
            long tagged = level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Mob.class),
                    m -> (m.getTags().contains(HavenInvasion.MONSTER_TAG) || m.getTags().contains(HavenInvasion.VILLAGER_TAG))
                            && !m.isRemoved()).size();
            check("depart : tous les monstres retires, registre reconstruit (sol de la rue revenu), bouton retire",
                    broke == HavenDestruction.Result.BROKEN && monsters > 0 && tagged == 0 && HavenInvasion.monsters().isEmpty()
                            && HavenDestruction.pendingCount(server) == 0 && level.getBlockState(street) == streetBefore
                            && button != null && !level.getBlockState(button).is(ModBlocks.HAVEN_INVASION_BUTTON.get())
                            && !HavenInvasion.cityOpen(server),
                    "casse " + broke + ", monstres " + monsters + " -> " + tagged + ", en attente "
                            + HavenDestruction.pendingCount(server) + ", rue " + name(level.getBlockState(street)) + ", bouton "
                            + (button == null ? "?" : name(level.getBlockState(button))) + ", ville ouverte " + HavenInvasion.cityOpen(server));
            HavenDestruction.Result closed = HavenDestruction.breakBlock(level, street, null);
            check("ville fermee : aucune casse", closed == HavenDestruction.Result.CLOSED, String.valueOf(closed));
        } finally {
            state.setPhase(HavenState.Phase.ACCUEIL);
            state.clearVotes();
            if (chosenBefore) {
                game.chooseMode(modeBefore);
            } else {
                game.forgetModeChoice();
            }
            HavenVote.keepVoteBlock(server);
            HavenInvasion.update(server);
            HavenInvasionButton.keep(server, true);
            line("etat rendu : phase " + state.phase() + ", mode " + HavenInvasion.mode(server) + ", bouton "
                    + (button == null ? "?" : name(level.getBlockState(button))));
        }
    }

    // ================================================================ mesures et rapport

    private static void sample(MinecraftServer server) {
        if (t % 100 != 99) {
            return;
        }
        long[] times = server.getTickTimesNanos();
        long sum = 0;
        long max = 0;
        int over20 = 0;
        for (long v : times) {
            sum += v;
            max = Math.max(max, v);
            if (v > 20_000_000L) {
                over20++;
            }
        }
        WINDOW[0] += sum / times.length;
        WINDOW[1] = Math.max(WINDOW[1], max);
        WINDOW[2]++;
        PER_WINDOW.append(String.format(Locale.ROOT, "%.1f/%.1f", sum / (double) times.length / 1.0e6, max / 1.0e6));
        if (over20 > 0) {
            PER_WINDOW.append('(').append(over20).append(" > 20 ms)");
        }
        PER_WINDOW.append(' ');
    }

    private static final StringBuilder PER_WINDOW = new StringBuilder();

    private static String window(String what) {
        String out = WINDOW[2] == 0 ? what + " : pas de mesure"
                : String.format(Locale.ROOT, "%s : moyenne %.2f ms, pire tique %.2f ms (%d fenetres de 100 tiques ; moyenne/pire par fenetre : %s)",
                what, WINDOW[0] / (double) WINDOW[2] / 1.0e6, WINDOW[1] / 1.0e6, WINDOW[2], PER_WINDOW.toString().trim());
        WINDOW[0] = 0;
        WINDOW[1] = 0;
        WINDOW[2] = 0;
        PER_WINDOW.setLength(0);
        return out;
    }

    /** La moyenne de la fenetre en cours, en millisecondes, avant que window ne la remette a zero. */
    private static double averageMs() {
        return WINDOW[2] == 0 ? Double.NaN : WINDOW[0] / (double) WINDOW[2] / 1.0e6;
    }

    private static void end(MinecraftServer server) {
        stage = Stage.END;
        ServerLevel level = Haven.level(server);
        HavenInvasion.TEST_ANCHORS.clear();
        if (level != null) {
            int removed = HavenInvasion.removeAll(level);
            int rebuilt = HavenDestruction.rebuildAll(level);
            HavenInvasion.setMode(server, HavenInvasion.Mode.INVASION, null);
            for (ChunkPos pos : HELD) {
                level.getChunkSource().removeRegionTicket(JakBuilder.TICKET, pos, TICKET_DISTANCE, pos);
            }
            for (ChunkPos pos : HELD_TRAFFIC) {
                level.getChunkSource().removeRegionTicket(JakBuilder.TICKET, pos, TRAFFIC_TICKET_DISTANCE, pos);
            }
            if (forced != null) {
                level.setChunkForced(forced.x, forced.z, false);
                line("troncon " + forced + " libere ; troncons forces restants dans Haven : " + level.getForcedChunks().size());
            }
            line("nettoyage : " + removed + " entites retirees, " + rebuilt + " blocs reconstruits, mode "
                    + HavenInvasion.mode(server));
        }
        HELD.clear();
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("invasion_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest invasion : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest invasion : {} OK, {} KO, rapport dans {} ; arret du serveur", passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest invasion : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }

    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    @SuppressWarnings("unused")
    private static BlockEntity be(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos);
    }
}
