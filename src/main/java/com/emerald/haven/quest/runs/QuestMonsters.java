package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.invasion.HavenSpawner;
import com.emerald.haven.quest.QuestRun;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Les monstres qu'une quete fait naitre (lot 3, cahier §86) : des monstres de l'invasion
 * (HavenSpawner : les armes les visent, leur mort poste HavenMonsterKilledEvent), etiquetes de
 * la quete, lances sur l'equipe. Les BRUTES et l'ELITE sont des zombies plus grands, en armure,
 * aux points de vie multiplies, qui brillent -- on les trouve dans la melee.
 */
public final class QuestMonsters {

    private QuestMonsters() {
    }

    /** Une place au sol pres d'un point, a {@code min}..{@code max} blocs, loin des joueurs de l'equipe. */
    @Nullable
    public static BlockPos ground(ServerLevel level, Vec3 near, double min, double max, List<? extends Player> avoid) {
        HavenInvasionData.Data data = HavenInvasionData.get(level.getServer());
        if (data == null) {
            return null;
        }
        BlockPos origin = HavenState.get(level.getServer()).origin();
        double cx = near.x - origin.getX();
        double cz = near.z - origin.getZ();
        List<HavenInvasionData.GroundTile> tiles = new ArrayList<>();
        for (HavenInvasionData.GroundTile tile : data.groundTiles()) {
            double tx = tile.tx() * HavenInvasionData.TILE + 8.0 - cx;
            double tz = tile.tz() * HavenInvasionData.TILE + 8.0 - cz;
            double d = Math.sqrt(tx * tx + tz * tz);
            if (d <= max + HavenInvasionData.TILE && d >= min - HavenInvasionData.TILE) {
                tiles.add(tile);
            }
        }
        if (tiles.isEmpty()) {
            return null;
        }
        for (int i = 0; i < 40; i++) {
            HavenInvasionData.GroundTile tile = tiles.get(level.random.nextInt(tiles.size()));
            int cell = tile.cells()[level.random.nextInt(tile.cells().length)];
            BlockPos feet = origin.offset(HavenInvasionData.unpackX(cell), HavenInvasionData.unpackY(cell),
                    HavenInvasionData.unpackZ(cell));
            double dx = feet.getX() + 0.5 - near.x;
            double dz = feet.getZ() + 0.5 - near.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < min || d > max || Math.abs(feet.getY() - near.y) > 8 || !HavenSpawner.standable(level, feet)) {
                continue;
            }
            boolean close = false;
            for (Player player : avoid) {
                if (player.position().distanceToSqr(Vec3.atBottomCenterOf(feet)) < 64.0) {
                    close = true;
                    break;
                }
            }
            if (!close) {
                return feet;
            }
        }
        return null;
    }

    /** Un monstre de la quete : de l'invasion, etiquete, sur la trace du joueur le plus proche. */
    @Nullable
    public static Mob spawn(ServerLevel level, QuestRun run, HavenInvasion.Kind kind, BlockPos feet, List<ServerPlayer> team) {
        Mob mob = HavenSpawner.spawnMonster(level, kind, Vec3.atBottomCenterOf(feet), true);
        if (mob == null) {
            return null;
        }
        mob.addTag(run.tag());
        mob.addTag(HavenInvasion.generationTag(level));
        hunt(mob, team);
        return mob;
    }

    /**
     * Une brute (ou l'elite) : un zombie plus grand, en armure incassable, aux points de vie
     * multiplies, qui brille, nomme.
     */
    @Nullable
    public static Mob spawnBrute(ServerLevel level, QuestRun run, BlockPos feet, List<ServerPlayer> team, double health,
                                 double scale, String nameKey, boolean netherite) {
        Mob mob = spawn(level, run, HavenInvasion.Kind.ZOMBIE, feet, team);
        if (mob == null) {
            return null;
        }
        AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) {
            max.setBaseValue(health);
            mob.setHealth((float) health);
        }
        AttributeInstance size = mob.getAttribute(Attributes.SCALE);
        if (size != null) {
            size.setBaseValue(scale);
        }
        AttributeInstance knock = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knock != null) {
            knock.setBaseValue(0.8);
        }
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        net.minecraft.world.item.Item[] armor = netherite
                ? new net.minecraft.world.item.Item[]{Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}
                : new net.minecraft.world.item.Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        for (int i = 0; i < slots.length; i++) {
            ItemStack piece = new ItemStack(armor[i]);
            piece.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
            mob.setItemSlot(slots[i], piece);
            mob.setDropChance(slots[i], 0.0F);
        }
        mob.setCustomName(Component.translatable(nameKey));
        mob.setCustomNameVisible(true);
        mob.setGlowingTag(true);
        return mob;
    }

    /** Lance un monstre sur le joueur de l'equipe le plus proche. */
    public static void hunt(Mob mob, List<ServerPlayer> team) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer player : team) {
            double d = player.distanceToSqr(mob);
            if (d < bestD) {
                bestD = d;
                best = player;
            }
        }
        if (best != null) {
            mob.setTarget(best);
        }
    }

    /** Les monstres de la quete encore vivants et charges. */
    public static List<Mob> alive(ServerLevel level, QuestRun run) {
        return new ArrayList<>(level.getEntities(EntityTypeTest.forClass(Mob.class),
                m -> m.isAlive() && !m.isRemoved() && m.getTags().contains(run.tag())));
    }

    /** Retire les monstres de la quete (fin, echec). */
    public static void removeAll(ServerLevel level, QuestRun run) {
        for (Mob mob : alive(level, run)) {
            mob.discard();
        }
    }

    /** Un monstre qui erre relance sur l'equipe (les cibles se perdent derriere un mur). */
    public static void keepHunting(ServerLevel level, QuestRun run, List<ServerPlayer> team) {
        for (Mob mob : alive(level, run)) {
            if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                hunt(mob, team);
            }
        }
    }
}
