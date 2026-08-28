package com.emerald.game;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * La garnison : ceux qui tiennent le sanctuaire avant vous.
 *
 * Ils sont poses a la construction, pas apparus a l'approche. La difference se
 * voit : une garnison postee sur ses tours se REPERE de loin, on compte les
 * silhouettes avant d'entrer et on decide par ou passer. Des monstres qui
 * surgissent quand on arrive ne se decident pas, ils se subissent.
 *
 * Ils sont attaches au lieu -- {@code restrictTo} les empeche de partir en
 * chasse a l'autre bout de la carte et de vider le sanctuaire tout seuls. Un
 * gardien qui abandonne son poste n'en est plus un.
 */
public final class SanctuaryGarrison {

    /** Marque un defenseur : c'est aussi ce qui le fait payer a la mort. */
    public static final String TAG_GUARD = "emeraldweapons_sanctuary_guard";

    private SanctuaryGarrison() {
    }

    /**
     * Poste la garnison : les tours d'abord, puis le chemin de ronde, puis la
     * cour. L'ordre compte pour la lecture -- ce qu'on voit en approchant, ce
     * sont les tours.
     */
    public static void populate(ServerLevel level, BlockPos centre, int half) {
        List<EntityType<?>> pool = pool(level);
        if (pool.isEmpty()) {
            return;
        }
        // les quatre tours d'angle, deux gardes chacune
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int i = 0; i < 2; i++) {
                    place(level, centre, pool,
                            centre.offset(sx * half, 14, sz * half), 2);
                }
            }
        }
        // le chemin de ronde, un garde tous les douze blocs
        for (int d = -half + 6; d <= half - 6; d += 12) {
            place(level, centre, pool, centre.offset(d, 8, -half + 1), 1);
            place(level, centre, pool, centre.offset(d, 8, half - 1), 1);
            place(level, centre, pool, centre.offset(-half + 1, 8, d), 1);
            place(level, centre, pool, centre.offset(half - 1, 8, d), 1);
        }
        // la cour, au pied de la pyramide
        for (int i = 0; i < 8; i++) {
            double angle = i / 8.0 * Math.PI * 2;
            place(level, centre, pool, centre.offset(
                    (int) Math.round(Math.cos(angle) * 17), 1,
                    (int) Math.round(Math.sin(angle) * 17)), 3);
        }
    }

    private static void place(ServerLevel level, BlockPos centre,
                              List<EntityType<?>> pool, BlockPos spot, int spread) {
        BlockPos at = spot.offset(
                level.random.nextInt(spread * 2 + 1) - spread, 0,
                level.random.nextInt(spread * 2 + 1) - spread);
        EntityType<?> type = pool.get(level.random.nextInt(pool.size()));
        Entity mob = type.spawn(level, at, MobSpawnType.STRUCTURE);
        if (mob == null) {
            return;
        }
        mob.addTag(TAG_GUARD);
        if (mob instanceof PathfinderMob guard) {
            // attaches au lieu : le rayon couvre le sanctuaire et rien de plus
            guard.restrictTo(centre, 40);
            guard.goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(guard, 1.0));
            guard.setPersistenceRequired();
        }
    }

    /**
     * La garnison est tiree du palier 2 : plus dure qu'un monstre de passage,
     * moins qu'une vague de siege. Le sanctuaire doit se prendre, pas resister
     * a lui seul comme un boss.
     */
    private static List<EntityType<?>> pool(ServerLevel level) {
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : SiegeRoster.forTier(2)) {
            EntityType.byString(id).ifPresent(pool::add);
        }
        if (pool.isEmpty()) {
            for (EntityType<?> type : SiegeRoster.vanillaFallback(2)) {
                pool.add(type);
            }
        }
        return pool;
    }

    /** Le type d'un garde, ou rien : sert au repli quand un mod manque. */
    @Nullable
    public static EntityType<?> any(ServerLevel level) {
        List<EntityType<?>> pool = pool(level);
        return pool.isEmpty() ? null : pool.get(level.random.nextInt(pool.size()));
    }
}
