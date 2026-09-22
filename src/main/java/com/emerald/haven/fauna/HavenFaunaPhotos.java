package com.emerald.haven.fauna;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenSpawner;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Les prises de l'automate de photos pour les animaux de Haven (« nom@haven:faune_... »).
 *
 * POSEES DEVANT LA CAMERA, EN DIRECT : les animaux de la ville naissent hors de la vue
 * des joueurs, a seize blocs au moins -- une photo « naturelle » ne montrerait qu'eux au
 * loin. Chaque prise pose donc, en plus de ce que la ville fait naitre, quelques animaux
 * a quelques blocs de la camera, avec les vrais reglages de leur role (HavenFauna.create).
 * On regarde ainsi ce que le joueur verra de pres : robes, colliers, tailles, nage, vol.
 *
 *  - faune_port : sur un quai du bassin, face au port ; des mouettes au bord, un phoque et
 *    un crabe sur le ponton ;
 *  - faune_rue : dans la rue des appartements, deux chats et un chien ;
 *  - faune_bassin : sous l'eau, au milieu du bassin : bancs de poissons, raie, hippocampe,
 *    ctenophores ;
 *  - faune_large : sous l'eau, au large : requins, meduses ;
 *  - faune_armee : au milieu de la place du bras ouest, en aventure ; le joueur frappe une
 *    mouette, l'armee arrive.
 */
public final class HavenFaunaPhotos {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static long armyAt = -1L;
    private static long placedAt;

    private HavenFaunaPhotos() {
    }

    /**
     * La prise est-elle prete ? La premiere fois ({@code first}), la scene est posee.
     *
     * @return vrai quand la camera peut prendre
     */
    public static boolean ready(MinecraftServer server, ServerPlayer player, String path, boolean first) {
        ServerLevel level = Haven.level(server);
        HavenFaunaData.Data data = HavenFaunaData.get(server);
        if (level == null || data == null || player.level() != level) {
            return false;
        }
        BlockPos o = HavenState.get(server).origin();
        switch (path) {
            case "faune_port" -> {
                if (first) {
                    // un quai du bassin a hauteur de rue, sous l'arc nord-ouest : la camera a un
                    // bloc et demi au-dessus, le regard sur le port ; des mouettes au bord du quai,
                    // un phoque et un crabe pres de l'eau
                    BlockPos quay = quay(data, o, new BlockPos(500, 64, 250), true, 63);
                    if (quay == null) {
                        return false;
                    }
                    Vec3 view = Vec3.atBottomCenterOf(o.offset(580, 58, 380));
                    player.setGameMode(GameType.SPECTATOR);
                    Vec3 feet = Vec3.atBottomCenterOf(quay).add(0, 1.5, 0);
                    look(player, level, feet, view);
                    Vec3 forward = view.subtract(feet).multiply(1, 0, 1).normalize();
                    stageOnQuays(level, data, o, feet, forward, "alexsmobs:seagull", HavenFauna.Role.GULL, 7, 3, 16);
                    stageOnQuays(level, data, o, feet, forward, "alexsmobs:seal", HavenFauna.Role.SHORE, 2, 3, 14);
                    stageOnQuays(level, data, o, feet, forward, "livingthings:crab", HavenFauna.Role.SHORE, 2, 3, 10);
                }
                return true;
            }
            case "faune_rue" -> {
                if (first) {
                    List<BlockPos> cells = HavenFauna.petCellsForTest(server, 0);
                    BlockPos center = o.offset(62, 62, 166);
                    List<BlockPos> spots = new ArrayList<>();
                    for (BlockPos cell : cells) {
                        double d = Math.sqrt(cell.distSqr(center));
                        if (d >= 3.0 && d <= 7.0 && cell.getZ() > center.getZ() && HavenSpawner.standable(level, cell)) {
                            spots.add(cell);
                        }
                    }
                    if (spots.size() < 3) {
                        return false;
                    }
                    player.setGameMode(GameType.SPECTATOR);
                    Vec3 mid = Vec3.ZERO;
                    for (int i = 0; i < 3; i++) {
                        BlockPos at = spots.get(level.random.nextInt(spots.size()));
                        stage(level, i == 2 ? "minecraft:wolf" : "minecraft:cat", Vec3.atBottomCenterOf(at), HavenFauna.Role.PET, 0);
                        mid = mid.add(Vec3.atBottomCenterOf(at).scale(1.0 / 3.0));
                    }
                    look(player, level, Vec3.atBottomCenterOf(center).add(0, 0.3, -2), mid.add(0, 0.4, 0));
                }
                return true;
            }
            case "faune_bassin", "faune_large" -> {
                if (first) {
                    boolean basin = "faune_bassin".equals(path);
                    BlockPos column = openWater(data, o, basin ? new BlockPos(620, 0, 420) : new BlockPos(250, 0, 640),
                            basin ? HavenFaunaData.Water.BASIN : HavenFaunaData.Water.OPEN_SEA);
                    if (column == null) {
                        return false;
                    }
                    player.setGameMode(GameType.SPECTATOR);
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
                    Vec3 eye = new Vec3(column.getX() + 0.5, o.getY() + data.surface() - 2.2, column.getZ() + 0.5);
                    Vec3 target = eye.add(6.0, -0.4, 0.0);
                    look(player, level, eye.subtract(0, player.getEyeHeight(), 0), target);
                    String[] kinds = basin
                            ? new String[]{"minecraft:tropical_fish", "minecraft:tropical_fish", "minecraft:tropical_fish",
                            "minecraft:cod", "minecraft:cod", "aquaculture:atlantic_herring", "aquaculture:atlantic_herring",
                            "livingthings:mantaray", "livingthings:seahorse", "alexsmobs:comb_jelly", "alexsmobs:flying_fish",
                            "aquaculture:red_grouper"}
                            : new String[]{"livingthings:shark", "alexsmobs:hammerhead_shark", "aquaculture:jellyfish",
                            "aquaculture:jellyfish", "alexsmobs:skelewag"};
                    for (int i = 0; i < kinds.length; i++) {
                        double ahead = 3.5 + level.random.nextDouble() * 3.5;
                        double side = (level.random.nextDouble() - 0.5) * 5.0;
                        double up = -1.2 + level.random.nextDouble() * 2.0;
                        Vec3 at = eye.add(ahead, up, side);
                        if (!level.getFluidState(BlockPos.containing(at)).is(FluidTags.WATER)) {
                            at = eye.add(ahead, -0.6, side);
                        }
                        stage(level, kinds[i], at, basin ? HavenFauna.Role.FISH : HavenFauna.Role.DANGER, 0);
                    }
                }
                return true;
            }
            case "faune_armee" -> {
                // en trois temps : le joueur au milieu de la place du bras ouest (point d'eco 3),
                // en aventure ; cinq secondes pour que le coin se charge ; il frappe une mouette ;
                // deux secondes et demie pour que l'armee arrive
                long now = level.getGameTime();
                if (first) {
                    BlockPos feet = o.offset(150, 66, 300);
                    player.setGameMode(GameType.ADVENTURE);
                    player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 180.0F, 0.0F);
                    armyAt = -1L;
                    placedAt = now;
                    return false;
                }
                if (armyAt < 0) {
                    if (now - placedAt < 100) {
                        return false;
                    }
                    Vec3 forward = new Vec3(0, 0, 1);
                    Mob gull = stage(level, "alexsmobs:seagull", player.position().add(forward.scale(2.0)).add(0, 0.2, 0),
                            HavenFauna.Role.GULL, 0);
                    int size = 0;
                    if (gull != null && gull.hurt(level.damageSources().playerAttack(player), 1.0F)) {
                        int[] state = SeagullArmy.stateForTest(player.getUUID());
                        size = state == null ? 0 : state[0];
                    }
                    armyAt = now;
                    look(player, level, player.position(), player.getEyePosition().add(forward.scale(6.0)).add(0, 2.4, 0));
                    LOGGER.info("photos : mouette frappee, armee de {} mouettes", size);
                    return false;
                }
                return now - armyAt >= 50;
            }
            default -> {
                return true;
            }
        }
    }

    /** Pose un animal de la ville, avec les reglages de son role. */
    @Nullable
    private static Mob stage(ServerLevel level, String id, Vec3 at, HavenFauna.Role role, int zone) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(id)).orElse(null);
        if (type == null) {
            LOGGER.warn("photos : espece {} absente", id);
            return null;
        }
        Mob mob = HavenFauna.create(level, type, at, role, ChunkPos.asLong(BlockPos.containing(at)),
                HavenInvasion.generationTag(level));
        if (mob != null && role == HavenFauna.Role.PET) {
            mob.getPersistentData().putInt(HavenFauna.ZONE_KEY, zone);
        }
        return mob;
    }

    /** Quelques animaux sur les quais devant la camera, entre {@code min} et {@code max} blocs. */
    private static void stageOnQuays(ServerLevel level, HavenFaunaData.Data data, BlockPos o, Vec3 from, Vec3 forward,
                                     String id, HavenFauna.Role role, int count, double min, double max) {
        int placed = 0;
        for (HavenFaunaData.Tile tile : data.list()) {
            for (int i = 0; i < tile.quays().length && placed < count; i++) {
                int cell = tile.quays()[i];
                BlockPos feet = o.offset(HavenFaunaData.unpackX(cell), HavenFaunaData.unpackY(cell), HavenFaunaData.unpackZ(cell));
                Vec3 at = Vec3.atBottomCenterOf(feet);
                Vec3 rel = at.subtract(from);
                double d = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
                if (d < min || d > max || (rel.x * forward.x + rel.z * forward.z) / d < 0.3
                        || Math.abs(at.y - from.y) > 6 || !HavenSpawner.standable(level, feet)
                        || level.random.nextInt(3) != 0) {
                    continue;
                }
                if (stage(level, id, at, role, 0) != null) {
                    placed++;
                }
            }
        }
        LOGGER.info("photos : {} {} poses sur les quais", placed, id);
    }

    /** Le quai du bassin le plus proche d'un point (cellules), a {@code minY} (cellule) ou plus haut. */
    @Nullable
    private static BlockPos quay(HavenFaunaData.Data data, BlockPos o, BlockPos near, boolean basin, int minY) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (HavenFaunaData.Tile tile : data.list()) {
            for (int i = 0; i < tile.quays().length; i++) {
                int cell = tile.quays()[i];
                if ((tile.quaySide()[i] == 1) != basin || HavenFaunaData.unpackY(cell) < minY) {
                    continue;
                }
                double dx = HavenFaunaData.unpackX(cell) - near.getX();
                double dz = HavenFaunaData.unpackZ(cell) - near.getZ();
                double d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = o.offset(HavenFaunaData.unpackX(cell), HavenFaunaData.unpackY(cell), HavenFaunaData.unpackZ(cell));
                }
            }
        }
        return best;
    }

    /** Une colonne d'eau libre du genre voulu, avec de l'eau libre sur huit blocs a l'est, pres d'un point. */
    @Nullable
    private static BlockPos openWater(HavenFaunaData.Data data, BlockPos o, BlockPos near, HavenFaunaData.Water kind) {
        for (int r = 0; r <= 60; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    int x = near.getX() + dx;
                    int z = near.getZ() + dz;
                    boolean clear = true;
                    for (int ex = 0; ex <= 8 && clear; ex++) {
                        for (int ez = -3; ez <= 3 && clear; ez++) {
                            clear = data.water(x + ex, z + ez) == kind;
                        }
                    }
                    if (clear) {
                        return o.offset(x, 0, z);
                    }
                }
            }
        }
        return null;
    }

    /** Pose le joueur (pieds en {@code feet}) et tourne son regard vers {@code target}. */
    private static void look(ServerPlayer player, ServerLevel level, Vec3 feet, Vec3 target) {
        Vec3 eye = feet.add(0, player.getEyeHeight(), 0);
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, feet.x, feet.y, feet.z, yaw, pitch);
    }
}
