package com.emerald.haven.quest;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenState;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.init.Jak3Registry;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.QuestMarkersPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les prises de l'automate de photos pour les quetes de Haven (« nom@haven:quete_... »).
 *
 * ON REGARDE AVANT DE LIVRER (§84) : les heros, leur bateau, un orbe, les cibles du stand
 * de tir et les reperes se posent DEVANT LA CAMERA, en direct, avec leurs vrais reglages.
 *
 *  - quete_torn, quete_tess, quete_sig, quete_keira, quete_samos, quete_pecheur : le heros
 *    de face, a trois pas ;
 *  - quete_bateau : le bateau du Pecheur, vu du quai ;
 *  - quete_orbe : un orbe precurseur a deux pas, dans la rue ;
 *  - quete_cibles : les trois sortes de cibles du stand -- un garde KG, le KG dore, un civil ;
 *  - quete_reperes : les trois reperes -- colonne, anneau, cercle de zone ;
 *  - quete_boutique_ui : l'ecran de la boutique de Tess (interface visible) ;
 *  - quete_hud_ui : le compteur d'orbes et la barre d'objectif d'une quete (interface visible).
 */
public final class HavenQuestPhotos {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static long staged = -1L;

    private HavenQuestPhotos() {
    }

    /**
     * La prise est-elle prete ? La premiere fois ({@code first}), la scene est posee.
     *
     * @return vrai quand la camera peut prendre
     */
    public static boolean ready(MinecraftServer server, ServerPlayer player, String path, boolean first) {
        ServerLevel level = Haven.level(server);
        if (level == null || player.level() != level) {
            return false;
        }
        long now = level.getGameTime();
        if (first) {
            // les heros ne sont la qu'a la deuxieme arrivee, les quetes qu'apres la reprise
            HavenProgress.Entry entry = HavenProgress.get(player.getUUID());
            entry.welcomed = true;
            entry.hq = true;
            entry.departures = Math.max(1, entry.departures);
            entry.invaded = true;
            entry.reprise = true;
            HavenProgress.save();
            staged = now;
            LOGGER.info("photos : scene « {} » des quetes de Haven", path);
        }
        HavenHero hero = hero(path);
        if (hero != null) {
            return heroShot(server, level, player, hero, now);
        }
        return switch (path) {
            case "quete_bateau" -> boatShot(server, level, player);
            case "quete_orbe" -> orbShot(level, player, first);
            case "quete_cibles" -> targetShot(server, level, player, first, now);
            case "quete_reperes" -> markerShot(server, level, player);
            case "quete_boutique_ui" -> shopShot(server, level, player, now);
            case "quete_hud_ui" -> hudShot(server, level, player, first);
            default -> false;
        };
    }

    @Nullable
    private static HavenHero hero(String path) {
        String name = path.endsWith("_ui") ? path.substring(0, path.length() - 3) : path;
        for (HavenHero hero : HavenHero.values()) {
            if (name.equals("quete_" + hero.id)) {
                return hero;
            }
        }
        return null;
    }

    /** Le heros de face, a trois pas, a hauteur de regard. */
    private static boolean heroShot(MinecraftServer server, ServerLevel level, ServerPlayer player, HavenHero hero, long now) {
        Vec3 spot = HavenNpcs.spot(hero);
        if (spot == null || HavenNpcs.loaded(level, hero) == null) {
            // LE TRONCON DOIT ETRE CHARGE pour que le heros soit pose : on s'y met, et on attend.
            // Meme quand sa place est deja connue : sans ce voyage, la camera restait au heros
            // d'avant et le heros suivant n'etait jamais pose (Sig, photo du 22 sept.).
            BlockPos at = spot != null ? BlockPos.containing(spot)
                    : HavenState.get(server).origin().offset(hero == HavenHero.PECHEUR
                    ? new BlockPos(560, 66, 258) : anchor(hero));
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, at.getX() + 0.5, at.getY() + 2.0, at.getZ() + 0.5, 0.0F, 0.0F);
            return false;
        }
        if (now - staged < 60) {
            return false;
        }
        // DEVANT LUI : la camera se met du cote ou il a ete pose (son regard, lui, suit les
        // joueurs et tourne : le prendre donnerait une camera qui saute d'une prise a l'autre)
        float yaw = HavenNpcs.yaw(hero);
        Vec3 ahead = spot.add(-Math.sin(Math.toRadians(yaw)) * 3.0, 0.0, Math.cos(Math.toRadians(yaw)) * 3.0);
        Vec3 camera = HavenArrival.standable(level, BlockPos.containing(ahead)) ? ahead : free(level, spot, 3.0);
        player.setGameMode(GameType.SPECTATOR);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
        look(player, level, camera.add(0.0, 1.5, 0.0), spot.add(0.0, 1.5, 0.0));
        return true;
    }

    private static BlockPos anchor(HavenHero hero) {
        return switch (hero) {
            case TORN -> new BlockPos(334, 69, 166);
            case TESS -> new BlockPos(778, 66, 101);
            case SIG -> new BlockPos(152, 66, 298);
            case KEIRA -> new BlockPos(628, 62, 118);
            case SAMOS -> new BlockPos(477, 123, 593);
            case PECHEUR -> new BlockPos(560, 66, 258);
        };
    }

    /** Le bateau, vu du quai, un peu en hauteur. */
    private static boolean boatShot(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        BlockPos deck = HavenBoat.deck(level);
        if (deck == null) {
            BlockPos at = HavenState.get(server).origin().offset(560, 66, 258);
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, at.getX() + 0.5, at.getY() + 2.0, at.getZ() + 0.5, 0.0F, 0.0F);
            return false;
        }
        Direction facing = Direction.fromYRot(HavenBoat.facing());
        Vec3 target = Vec3.atBottomCenterOf(deck).add(0.0, 1.0, 0.0);
        Vec3 camera = Vec3.atBottomCenterOf(deck.relative(facing, 11)).add(0.0, 5.0, 0.0);
        player.setGameMode(GameType.SPECTATOR);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
        look(player, level, camera, target);
        return true;
    }

    /** Un orbe precurseur a deux pas, dans la rue des appartements. */
    private static boolean orbShot(ServerLevel level, ServerPlayer player, boolean first) {
        if (first) {
            BlockPos at = HavenState.get(level.getServer()).origin().offset(62, 62, 166);
            BlockPos feet = ground(level, at);
            Vec3 orbAt = Vec3.atBottomCenterOf(feet).add(0.0, 1.4, 0.0);
            player.setGameMode(GameType.SPECTATOR);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
            look(player, level, orbAt.add(2.0, 0.0, 0.0), orbAt);
            HavenOrbEntity orb = Jak3Registry.HAVEN_ORB.get().create(level);
            if (orb == null) {
                LOGGER.warn("photos : l'orbe ne se cree pas");
                return false;
            }
            // UN ORBE QUE CE JOUEUR N'A PAS PRIS : un orbe deja trouve ne se montre plus a lui
            int index = 0;
            int total = HavenOrbs.total(level.getServer());
            while (index < total - 1 && HavenProgress.found(player.getUUID(), index)) {
                index++;
            }
            orb.setIndex(index);
            orb.moveTo(orbAt.x, orbAt.y, orbAt.z);
            boolean added = level.addFreshEntity(orb);
            LOGGER.info("photos : orbe {} pose en {} {} {} (montre {})", orb.index(),
                    String.format(Locale.ROOT, "%.1f", orbAt.x), String.format(Locale.ROOT, "%.1f", orbAt.y),
                    String.format(Locale.ROOT, "%.1f", orbAt.z), added && orb.broadcastToPlayer(player));
            return false;
        }
        return true;
    }

    /** Les trois sortes de cibles, face a la camera, sur la place du bras ouest. */
    private static boolean targetShot(MinecraftServer server, ServerLevel level, ServerPlayer player, boolean first, long now) {
        if (first) {
            BlockPos at = HavenState.get(server).origin().offset(150, 66, 300);
            BlockPos feet = ground(level, at);
            Vec3 camera = Vec3.atBottomCenterOf(feet).add(0.0, 1.6, 0.0);
            int[] variants = {0, HavenTargetEntity.BONUS, HavenTargetEntity.FIRST_CIVILIAN + 1};
            for (int i = 0; i < variants.length; i++) {
                Vec3 place = camera.add((i - 1) * 2.6, -1.6, 7.0);
                HavenTargetEntity target = Jak3Registry.HAVEN_TARGET.get().create(level);
                if (target != null) {
                    target.setup(place, camera, new Vec3(1, 0, 0), false, 20 * 120, variants[i]);
                    level.addFreshEntity(target);
                }
            }
            player.setGameMode(GameType.SPECTATOR);
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
            look(player, level, camera, camera.add(0.0, -0.4, 7.0));
            staged = now;
            return false;
        }
        return now - staged > 20;                        // les cibles se dressent en un quart de seconde
    }

    /** Les trois reperes d'une quete, devant la camera. */
    private static boolean markerShot(MinecraftServer server, ServerLevel level, ServerPlayer player) {
        BlockPos at = HavenState.get(server).origin().offset(150, 66, 300);
        BlockPos feet = ground(level, at);
        Vec3 camera = Vec3.atBottomCenterOf(feet).add(0.0, 2.0, 0.0);
        Vec3 middle = camera.add(0.0, -2.0, 16.0);
        List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
        markers.add(QuestMarkers.beacon(middle.add(-8.0, 0.0, 0.0), QuestMarkers.GOLD));
        markers.add(QuestMarkers.ring(middle.add(0.0, 4.0, 0.0), 0.0F, 5.5F, QuestMarkers.GOLD));
        markers.add(QuestMarkers.zone(middle.add(10.0, 0.0, 2.0), 6.0F, QuestMarkers.GREEN));
        QuestMarkers.show(List.of(player), markers);
        player.setGameMode(GameType.SPECTATOR);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
        look(player, level, camera, middle.add(0.0, 2.0, 0.0));
        return true;
    }

    /** L'ecran de la boutique, ouvert chez Tess. */
    private static boolean shopShot(MinecraftServer server, ServerLevel level, ServerPlayer player, long now) {
        Vec3 spot = HavenNpcs.spot(HavenHero.TESS);
        if (spot == null) {
            BlockPos at = HavenState.get(server).origin().offset(anchor(HavenHero.TESS));
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, at.getX() + 0.5, at.getY() + 2.0, at.getZ() + 0.5, 0.0F, 0.0F);
            return false;
        }
        if (HavenProgress.orbs(player.getUUID()) < 500) {
            HavenProgress.addOrbs(player.getUUID(), 500);
        }
        player.setGameMode(GameType.ADVENTURE);
        Vec3 camera = free(level, spot, 2.5);
        look(player, level, camera, spot.add(0.0, 1.2, 0.0));
        if (now - staged < 40) {
            return false;
        }
        HavenShop.send(player, true);
        return now - staged > 60;
    }

    /** Le compteur d'orbes et la barre d'objectif, en pleine rue. */
    private static boolean hudShot(MinecraftServer server, ServerLevel level, ServerPlayer player, boolean first) {
        if (first) {
            HavenShop.close(player);                    // la prise d'avant laisse l'ecran ouvert
            HavenProgress.addOrbs(player.getUUID(), 240 - HavenProgress.orbs(player.getUUID()));
            for (int i = 0; i < 12; i++) {
                HavenProgress.markFound(player.getUUID(), i);
            }
            BlockPos at = HavenState.get(server).origin().offset(62, 62, 166);
            BlockPos feet = ground(level, at);
            player.setGameMode(GameType.ADVENTURE);
            player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 90.0F, 0.0F);
            HavenOrbs.sync(player);
            HavenQuests.accept(player, HavenQuestBook.byId("peche"));
            return false;
        }
        return HavenQuests.runOf(player.getUUID()) != null;
    }

    // ================================================================ outils

    /** Une place libre a tant de pas d'un point, de preference devant le heros. */
    private static Vec3 free(ServerLevel level, Vec3 from, double distance) {
        BlockPos near = BlockPos.containing(from);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos at = near.relative(direction, (int) Math.round(distance));
            if (HavenArrival.standable(level, at)) {
                return Vec3.atBottomCenterOf(at);
            }
        }
        return from.add(distance, 0.0, 0.0);
    }

    /** Le sol sous un point (jusqu'a six blocs plus bas ou plus haut). */
    private static BlockPos ground(ServerLevel level, BlockPos at) {
        for (int dy = 0; dy <= 6; dy++) {
            if (HavenArrival.standable(level, at.offset(0, -dy, 0))) {
                return at.offset(0, -dy, 0);
            }
            if (HavenArrival.standable(level, at.offset(0, dy, 0))) {
                return at.offset(0, dy, 0);
            }
        }
        return at;
    }

    /** Pose le joueur (yeux en {@code eye}) et tourne son regard vers {@code target}. */
    private static void look(ServerPlayer player, ServerLevel level, Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    }
}
