package com.emerald.haven.quest;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.init.Jak3Registry;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * LES HEROS A LEUR PLACE (lot 3, cahier §86).
 *
 * Les places viennent d'une serie de photos de reperage (22 sept.) : Torn derriere le
 * comptoir du Hip Hog, pres du ratelier ; Tess a l'entree du stand de tir ; Sig au milieu de
 * la place du bras ouest ; Keira dans la cour du bras central ; Samos sur la terrasse de la
 * tour ouest, pres du portail ; le Pecheur sur son bateau (HavenBoat), amarre au pied de
 * l'escalier qui descend dans le bassin, sous l'arc nord. Chaque place est un point de
 * depart : on y cherche le sol le plus proche ou l'on tient debout.
 *
 * « Les PNJ n'apparaissent qu'a la DEUXIEME arrivee » (le joueur, §79.7) : les heros sont
 * la tant qu'un joueur de la ville est revenu d'un Defi ; sinon, ni heros ni bateau.
 * Toutes les vingt tiques, chaque heros dont le troncon est charge est pose s'il manque, et
 * ramene a sa place s'il s'en est ecarte.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenNpcs {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final String TAG = "emeraldweapons.haven_pnj";

    /** Le point de depart de chaque place (cellules du volume) et le regard (lacet). */
    private record Anchor(BlockPos cell, float yaw) {
    }

    private static final Map<HavenHero, Anchor> ANCHORS = new EnumMap<>(Map.of(
            HavenHero.TORN, new Anchor(new BlockPos(334, 69, 166), 0.0F),
            HavenHero.TESS, new Anchor(new BlockPos(778, 66, 101), 0.0F),
            HavenHero.SIG, new Anchor(new BlockPos(152, 66, 298), 0.0F),
            HavenHero.KEIRA, new Anchor(new BlockPos(628, 62, 118), 0.0F),
            HavenHero.SAMOS, new Anchor(new BlockPos(477, 123, 593), 0.0F)));

    private static final Map<HavenHero, Vec3> SPOTS = new EnumMap<>(HavenHero.class);
    /** Le cap de pose de chaque heros (son regard, lui, suit les joueurs). */
    private static final Map<HavenHero, Float> YAWS = new EnumMap<>(HavenHero.class);
    private static int ticks;
    private static boolean present;

    private HavenNpcs() {
    }

    /** Les heros sont-ils dans la ville ? */
    public static boolean present() {
        return present;
    }

    /** La place d'un heros dans le monde (pieds), ou null tant qu'elle n'est pas trouvee. */
    @Nullable
    public static Vec3 spot(HavenHero hero) {
        return SPOTS.get(hero);
    }

    /** Le cap de pose d'un heros (0 s'il n'est pas pose). */
    public static float yaw(HavenHero hero) {
        return YAWS.getOrDefault(hero, 0.0F);
    }

    /** Les heros sont voulus : ville ouverte, et un joueur de la ville revenu d'un Defi. */
    static boolean wanted(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        if (level == null || !HavenInvasion.cityOpen(server)) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isFakePlayer() && HavenProgress.get(player.getUUID()).departures > 0) {
                return true;
            }
        }
        for (ServerPlayer test : HavenQuests.TEST_PLAYERS.values()) {
            if (HavenProgress.get(test.getUUID()).departures > 0) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 20 != 3) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return;
        }
        boolean want = wanted(server);
        if (!want) {
            if (present) {
                removeAll(level);
            }
            present = false;
            return;
        }
        present = true;
        HavenBoat.keep(level);
        for (HavenHero hero : HavenHero.values()) {
            Vec3 spot = SPOTS.get(hero);
            if (spot == null) {
                spot = find(level, hero);
                if (spot == null) {
                    continue;
                }
                SPOTS.put(hero, spot);
            }
            BlockPos at = BlockPos.containing(spot);
            if (!level.isLoaded(at) || !level.areEntitiesLoaded(ChunkPos.asLong(at))) {
                continue;
            }
            keep(level, hero, spot);
        }
    }

    /** La place d'un heros : le sol le plus proche du point de depart ; le Pecheur, son bateau. */
    @Nullable
    private static Vec3 find(ServerLevel level, HavenHero hero) {
        if (hero == HavenHero.PECHEUR) {
            BlockPos deck = HavenBoat.deck(level);
            return deck == null ? null : Vec3.atBottomCenterOf(deck);
        }
        Anchor anchor = ANCHORS.get(hero);
        BlockPos origin = HavenState.get(level.getServer()).origin();
        BlockPos start = origin.offset(anchor.cell());
        // LA PLACE LA PLUS DEGAGEE, ET NON LA PREMIERE VENUE : Tess s'etait retrouvee le nez
        // dans un angle de la salle des armes (photo du 22 sept.). On garde la place la plus
        // proche de l'ancre parmi celles qui ont du vide autour, a hauteur de tete.
        BlockPos best = null;
        int bestScore = -1;
        for (int r = 0; r <= 5 && bestScore < 0; r++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int sign : new int[]{1, -1}) {
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                continue;
                            }
                            BlockPos feet = start.offset(dx, dy * sign, dz);
                            if (!level.isLoaded(feet) || !HavenArrival.standable(level, feet)) {
                                continue;
                            }
                            int score = openness(level, feet);
                            if (score > bestScore) {
                                bestScore = score;
                                best = feet;
                            }
                        }
                    }
                }
            }
            if (best != null && bestScore >= 8) {
                break;                                  // assez degagee : inutile de chercher plus loin
            }
        }
        if (best != null) {
            return Vec3.atBottomCenterOf(best);
        }
        LOGGER.warn("quetes de Haven : aucune place debout pour {} pres de {}", hero.id, start.toShortString());
        return null;
    }

    /** Le vide autour d'une place, a hauteur de tete : douze cellules au plus. */
    private static int openness(ServerLevel level, BlockPos feet) {
        int open = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int d = 1; d <= 3; d++) {
                BlockPos at = feet.relative(direction, d).above();
                if (!level.isLoaded(at) || !level.getBlockState(at).isAir()) {
                    break;
                }
                open++;
            }
        }
        return open;
    }

    /** Le cap du plus grand vide : un heros ne regarde pas un mur. */
    private static float facing(ServerLevel level, BlockPos feet, float fallback) {
        Direction best = null;
        int bestOpen = 1;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int open = 0;
            for (int d = 1; d <= 5; d++) {
                BlockPos at = feet.relative(direction, d).above();
                if (!level.isLoaded(at) || !level.getBlockState(at).isAir()) {
                    break;
                }
                open++;
            }
            if (open > bestOpen) {
                bestOpen = open;
                best = direction;
            }
        }
        return best == null ? fallback : best.toYRot();
    }

    private static void keep(ServerLevel level, HavenHero hero, Vec3 spot) {
        List<HavenNpcEntity> found = new ArrayList<>(level.getEntities(EntityTypeTest.forClass(HavenNpcEntity.class),
                npc -> npc.getTags().contains(TAG) && npc.hero() == hero && !npc.isRemoved()));
        HavenNpcEntity npc = found.isEmpty() ? null : found.remove(0);
        for (HavenNpcEntity extra : found) {
            extra.discard();
        }
        float yaw = YAWS.computeIfAbsent(hero, h -> h == HavenHero.PECHEUR ? HavenBoat.facing()
                : facing(level, BlockPos.containing(spot), ANCHORS.get(h).yaw()));
        if (npc == null) {
            npc = Jak3Registry.HAVEN_NPC.get().create(level);
            if (npc == null) {
                return;
            }
            npc.setHero(hero);
            npc.addTag(TAG);
            npc.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
            level.addFreshEntity(npc);
            return;
        }
        if (npc.position().distanceToSqr(spot) > 2.25) {
            npc.teleportTo(spot.x, spot.y, spot.z);
            npc.setYRot(yaw);
            npc.setYBodyRot(yaw);
        }
    }

    /** Retire les heros charges et le bateau. */
    public static void removeAll(ServerLevel level) {
        for (HavenNpcEntity npc : level.getEntities(EntityTypeTest.forClass(HavenNpcEntity.class),
                npc -> npc.getTags().contains(TAG))) {
            npc.discard();
        }
        HavenBoat.remove(level);
    }

    /** Le heros d'une quete, s'il est charge : pour les distances (boutique de Tess). */
    @Nullable
    public static HavenNpcEntity loaded(ServerLevel level, HavenHero hero) {
        for (HavenNpcEntity npc : level.getEntities(EntityTypeTest.forClass(HavenNpcEntity.class),
                npc -> npc.getTags().contains(TAG) && npc.hero() == hero && !npc.isRemoved())) {
            return npc;
        }
        return null;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerLevel level = Haven.level(event.getServer());
        if (level != null) {
            HavenBoat.remove(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SPOTS.clear();
        YAWS.clear();
        present = false;
        ticks = 0;
        HavenBoat.reset();
    }
}
