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
    public static void populate(ServerLevel level, BlockPos centre, int half,
                                int walk, int towerTop) {
        List<EntityType<?>> pool = pool(level);
        if (pool.isEmpty()) {
            return;
        }
        // Les hauteurs sont DONNEES, plus ecrites en dur.
        //
        // Elles valaient huit et quatorze, du temps ou le rempart faisait huit
        // blocs. Il en fait vingt-quatre et les tours quarante-deux : la
        // garnison apparaissait donc au coeur de la maconnerie, ou elle
        // etouffait aussitot. D'ou l'impression que les batiments tuaient les
        // monstres -- ils etaient simplement poses dedans.
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int i = 0; i < 3; i++) {
                    place(level, centre, pool,
                            centre.offset(sx * half, towerTop + 1, sz * half), 3);
                }
                // les etages sont tenus par les gardes de coffre, poses avec
                // les coffres eux-memes (voir postGuard, appele par Sanctuary)
            }
        }
        // le chemin de ronde, un garde tous les huit blocs
        for (int d = -half + 6; d <= half - 6; d += 8) {
            place(level, centre, pool, centre.offset(d, walk + 1, -half + 2), 1);
            place(level, centre, pool, centre.offset(d, walk + 1, half - 2), 1);
            place(level, centre, pool, centre.offset(-half + 2, walk + 1, d), 1);
            place(level, centre, pool, centre.offset(half - 2, walk + 1, d), 1);
        }
        // la cour, en deux cercles
        for (int ring : new int[]{24, 52}) {
            for (int i = 0; i < 12; i++) {
                double angle = i / 12.0 * Math.PI * 2;
                place(level, centre, pool, centre.offset(
                        (int) Math.round(Math.cos(angle) * ring), 1,
                        (int) Math.round(Math.sin(angle) * ring)), 4);
            }
        }
    }

    private static void place(ServerLevel level, BlockPos centre,
                              List<EntityType<?>> pool, BlockPos spot, int spread) {
        BlockPos at = spot.offset(
                level.random.nextInt(spread * 2 + 1) - spread, 0,
                level.random.nextInt(spread * 2 + 1) - spread);
        spawnGuard(level, pool, at, 12);
    }

    /**
     * Un garde POSTE, attache a son poste et non au sanctuaire.
     *
     * C'etait la faille : tous les defenseurs etaient retenus dans un rayon de
     * quarante blocs autour du CENTRE, et leur MoveTowardsRestrictionGoal les y
     * ramenait. Ils quittaient donc les tours pour s'agglutiner dans la cour,
     * et l'on montait vider les coffres sans croiser personne. Chacun garde
     * maintenant l'endroit ou on l'a mis.
     */
    public static void postGuard(ServerLevel level, BlockPos at, int radius) {
        List<EntityType<?>> pool = pool(level);
        if (!pool.isEmpty()) {
            spawnGuard(level, pool, at, radius);
        }
    }

    private static void spawnGuard(ServerLevel level, List<EntityType<?>> pool,
                                   BlockPos at, int radius) {
        EntityType<?> type = pool.get(level.random.nextInt(pool.size()));
        Entity mob = type.spawn(level, at, MobSpawnType.STRUCTURE);
        if (mob == null) {
            return;
        }
        mob.addTag(TAG_GUARD);
        if (mob instanceof PathfinderMob guard) {
            guard.restrictTo(at, radius);
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
