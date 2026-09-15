package com.emerald.haven.invasion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.EventHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ou et comment naissent les monstres et les habitants de Haven.
 *
 * LES PLACES viennent de la carte validee (HavenInvasionData) : une cellule de pieds
 * d'une tuile au sol, ou un point de la bande de vol d'une tuile de phantoms. Elles
 * sont REVALIDEES DANS LE MONDE au moment de l'apparition : une arme a pu casser le
 * sol, un habitant peut occuper la case, le troncon peut ne pas tiquer. Au sol, elles
 * doivent aussi echapper a la vue des joueurs proches (seen).
 */
public final class HavenSpawner {

    /** Essais par apparition avant d'abandonner jusqu'a la seconde suivante. */
    private static final int TRIES = 12;

    /** Une apparition, pour le banc d'essai. */
    public record Spawned(@Nullable HavenInvasion.Kind kind, @Nullable VillagerType villagerType, Vec3 position, long tick) {
    }

    private HavenSpawner() {
    }

    // ================================================================ places

    /**
     * Une cellule de pieds au sol pour une apparition pres d'une ancre.
     *
     * @param anchors toutes les ancres : on n'apparait pas a moins de SPAWN_MIN d'aucune
     * @return les pieds en coordonnees du monde, ou null
     */
    @Nullable
    public static BlockPos findGround(ServerLevel level, HavenInvasionData.Data data, BlockPos origin, Vec3 anchor,
                                      List<Vec3> anchors, RandomSource random) {
        return findGround(level, data, origin, anchor, anchors, random, true);
    }

    /**
     * @param hidden faux seulement pour le banc d'essai, qui cherche une place a ciel
     *               ouvert pres d'un joueur simule : la regle de vue les refuse presque toutes
     */
    @Nullable
    public static BlockPos findGround(ServerLevel level, HavenInvasionData.Data data, BlockPos origin, Vec3 anchor,
                                      List<Vec3> anchors, RandomSource random, boolean hidden) {
        double cx = anchor.x - origin.getX();
        double cz = anchor.z - origin.getZ();
        double reach = HavenInvasion.SPAWN_MAX + HavenInvasionData.TILE;
        List<HavenInvasionData.GroundTile> tiles = new ArrayList<>();
        for (HavenInvasionData.GroundTile tile : data.groundTiles()) {
            double tx = tile.tx() * HavenInvasionData.TILE + 8.0;
            double tz = tile.tz() * HavenInvasionData.TILE + 8.0;
            double d = Math.sqrt((tx - cx) * (tx - cx) + (tz - cz) * (tz - cz));
            if (d <= reach && d >= HavenInvasion.SPAWN_MIN - HavenInvasionData.TILE) {
                tiles.add(tile);
            }
        }
        if (tiles.isEmpty()) {
            return null;
        }
        for (int i = 0; i < TRIES; i++) {
            HavenInvasionData.GroundTile tile = tiles.get(random.nextInt(tiles.size()));
            int cell = tile.cells()[random.nextInt(tile.cells().length)];
            BlockPos feet = origin.offset(HavenInvasionData.unpackX(cell), HavenInvasionData.unpackY(cell),
                    HavenInvasionData.unpackZ(cell));
            if (placeOk(level, data, origin, feet.getX() + 0.5, feet.getZ() + 0.5, anchor, anchors, HavenInvasion.SPAWN_MAX)
                    && standable(level, feet) && (!hidden || !seen(level, anchors, feet))) {
                return feet;
            }
        }
        return null;
    }

    /**
     * Un point de la bande de vol des phantoms pres d'une ancre : dans une tuile de
     * vol, entre son plancher et son plafond (au plus trente blocs au-dessus du
     * plancher, pour qu'il voie sa proie), dans de l'air libre.
     */
    @Nullable
    public static Vec3 findPhantom(ServerLevel level, HavenInvasionData.Data data, BlockPos origin, Vec3 anchor,
                                   List<Vec3> anchors, RandomSource random) {
        double cx = anchor.x - origin.getX();
        double cz = anchor.z - origin.getZ();
        List<HavenInvasionData.PhantomTile> tiles = new ArrayList<>();
        for (HavenInvasionData.PhantomTile tile : data.phantomTiles()) {
            double tx = tile.tx() * HavenInvasionData.TILE + 8.0;
            double tz = tile.tz() * HavenInvasionData.TILE + 8.0;
            double d = Math.sqrt((tx - cx) * (tx - cx) + (tz - cz) * (tz - cz));
            if (d <= HavenInvasion.PHANTOM_MAX + HavenInvasionData.TILE && d >= HavenInvasion.SPAWN_MIN - HavenInvasionData.TILE) {
                tiles.add(tile);
            }
        }
        if (tiles.isEmpty()) {
            return null;
        }
        for (int i = 0; i < TRIES; i++) {
            HavenInvasionData.PhantomTile tile = tiles.get(random.nextInt(tiles.size()));
            int band = Math.max(0, Math.min(tile.ceiling() - tile.floor(), 30));
            double x = origin.getX() + tile.tx() * HavenInvasionData.TILE + random.nextInt(HavenInvasionData.TILE) + 0.5;
            double z = origin.getZ() + tile.tz() * HavenInvasionData.TILE + random.nextInt(HavenInvasionData.TILE) + 0.5;
            double y = origin.getY() + tile.floor() + random.nextInt(band + 1);
            BlockPos at = BlockPos.containing(x, y, z);
            if (placeOk(level, data, origin, x, z, anchor, anchors, HavenInvasion.PHANTOM_MAX)
                    && level.noCollision(EntityType.PHANTOM.getSpawnAABB(x, y, z).inflate(0.5))
                    && level.getFluidState(at).isEmpty()) {
                return new Vec3(x, y, z);
            }
        }
        return null;
    }

    /** Distance aux ancres, centres d'exclusion, zone sure, troncon qui tique. */
    private static boolean placeOk(ServerLevel level, HavenInvasionData.Data data, BlockPos origin, double x, double z,
                                   Vec3 anchor, List<Vec3> anchors, double max) {
        if (HavenInvasion.horizontal(anchor, x, z) > max) {
            return false;
        }
        for (Vec3 other : anchors) {
            if (HavenInvasion.horizontal(other, x, z) < HavenInvasion.SPAWN_MIN) {
                return false;
            }
        }
        double cellX = x - origin.getX();
        double cellZ = z - origin.getZ();
        for (HavenInvasionData.Center center : data.centers()) {
            double dx = cellX - center.x();
            double dz = cellZ - center.z();
            if (Math.sqrt(dx * dx + dz * dz) <= data.exclusionRadius()) {
                return false;
            }
        }
        BlockPos probe = BlockPos.containing(x, origin.getY() + 70, z);
        return level.isPositionEntityTicking(probe);
    }

    /**
     * Vrai si un joueur a moins de SIGHT_RADIUS voit ces pieds : un trait libre de
     * ses yeux au bas ou au haut du corps. Les rues de Haven sont etroites : plus pres
     * qu'avant, une apparition doit se faire derriere un mur ou un coin.
     */
    static boolean seen(ServerLevel level, List<Vec3> anchors, BlockPos feet) {
        Vec3 low = Vec3.atBottomCenterOf(feet).add(0.0, 0.3, 0.0);
        Vec3 high = low.add(0.0, 1.5, 0.0);
        for (Vec3 anchor : anchors) {
            if (HavenInvasion.horizontal(anchor, low.x, low.z) > HavenInvasion.SIGHT_RADIUS) {
                continue;
            }
            Vec3 eyes = anchor.add(0.0, 1.62, 0.0);
            if (clear(level, eyes, high) || clear(level, eyes, low)) {
                return true;
            }
        }
        return false;
    }

    private static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    /** Un sol qui porte, deux cases libres et seches au-dessus. */
    public static boolean standable(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockPos below = feet.below();
        BlockState floor = level.getBlockState(below);
        return !floor.getCollisionShape(level, below).isEmpty() && floor.getFluidState().isEmpty()
                && free(level, feet) && free(level, feet.above())
                && HavenProtection.safeZoneAt(level.getServer(), feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5) == null;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    // ================================================================ creatures

    /** Zombie 40 %, villageois zombie 20 %, squelette 40 %. */
    public static HavenInvasion.Kind pickGroundKind(RandomSource random) {
        int roll = random.nextInt(10);
        return roll < 4 ? HavenInvasion.Kind.ZOMBIE : roll < 6 ? HavenInvasion.Kind.ZOMBIE_VILLAGER : HavenInvasion.Kind.SKELETON;
    }

    /**
     * Fait naitre un monstre de l'invasion.
     *
     * Ni bebe ni chevaucheur de poulet (ZombieGroupData faux, faux), ni renforts
     * (chance ramenee a zero, modificateurs de chef compris), ni ramassage d'objets ;
     * casque de fer INCASSABLE sur les trois especes au sol ; aucun equipement ne
     * tombe (chances a zero). Etiquete, non persistant.
     *
     * @param helmet faux seulement pour le temoin sans casque du banc d'essai
     * @return le monstre ajoute au monde, ou null
     */
    @Nullable
    public static Mob spawnMonster(ServerLevel level, HavenInvasion.Kind kind, Vec3 at, boolean helmet) {
        EntityType<? extends Mob> type = switch (kind) {
            case ZOMBIE -> EntityType.ZOMBIE;
            case ZOMBIE_VILLAGER -> EntityType.ZOMBIE_VILLAGER;
            case SKELETON -> EntityType.SKELETON;
            case PHANTOM -> EntityType.PHANTOM;
        };
        Mob mob = type.create(level);
        if (mob == null) {
            return null;
        }
        mob.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
        SpawnGroupData group = mob instanceof Zombie ? new Zombie.ZombieGroupData(false, false) : null;
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, group);
        mob.setCanPickUpLoot(false);
        if (mob instanceof Zombie zombie) {
            zombie.setBaby(false);
            AttributeInstance reinforcements = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
            if (reinforcements != null) {
                reinforcements.removeModifiers();
                reinforcements.setBaseValue(0.0);
            }
        }
        if (mob instanceof AbstractSkeleton skeleton) {
            if (skeleton.getMainHandItem().isEmpty()) {
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            }
            skeleton.reassessWeaponGoal();
        }
        if (mob instanceof Phantom phantom) {
            phantom.setPhantomSize(0);
        } else if (helmet) {
            ItemStack casque = new ItemStack(Items.IRON_HELMET);
            casque.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
            mob.setItemSlot(EquipmentSlot.HEAD, casque);
        } else {
            mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0.0F);
        }
        mob.addTag(HavenInvasion.MONSTER_TAG);
        return level.addFreshEntity(mob) ? mob : null;
    }

    /**
     * Fait naitre un habitant : adulte, de la region demandee, sans metier (donc sans
     * commerce), invulnerable. Pas de finalizeSpawn : il prendrait la region du biome.
     */
    @Nullable
    public static Villager spawnVillager(ServerLevel level, VillagerType type, Vec3 at) {
        Villager villager = new Villager(EntityType.VILLAGER, level, type);
        villager.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360.0F, 0.0F);
        villager.setVillagerData(villager.getVillagerData().setType(type).setProfession(VillagerProfession.NONE));
        villager.setInvulnerable(true);
        villager.setCanPickUpLoot(false);
        villager.addTag(HavenInvasion.VILLAGER_TAG);
        return level.addFreshEntity(villager) ? villager : null;
    }
}
