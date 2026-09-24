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
 *
 * CHAQUE SANCTUAIRE A LA SIENNE (cahier §90) : la garnison vient du theme du sanctuaire
 * (les morts des sables, les draugr du givre, les revenants embrases), au palier de son RANG
 * de prise. Tous les sanctuaires se batissent au debut de la partie, avant qu'on en ait pris
 * un seul : ils sont donc postes au premier palier, puis RENFORCES -- a l'approche, s'il en a
 * ete pris d'autres depuis, arrivent des gardes du palier suivant (reinforce).
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
                                int walk, int towerTop, SanctuaryTheme theme, int rank) {
        List<EntityType<?>> pool = pool(level, theme, rank);
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

    @Nullable
    private static Entity place(ServerLevel level, BlockPos centre,
                                List<EntityType<?>> pool, BlockPos spot, int spread) {
        BlockPos at = spot.offset(
                level.random.nextInt(spread * 2 + 1) - spread, 0,
                level.random.nextInt(spread * 2 + 1) - spread);
        // un renfort ne charge pas de chunk : le poste d'un chunk lointain reste vide
        if (!level.isLoaded(at)) {
            return null;
        }
        return spawnGuard(level, pool, at, 12, null);
    }

    /**
     * LES RENFORTS : un sanctuaire bati au premier palier, qu'on aborde apres en avoir pris
     * d'autres, se garnit du palier de son rang. Sur les tours, le chemin de ronde et dans la
     * cour -- ce qu'on voit en arrivant, comme la garnison du debut.
     *
     * CHAQUE SANCTUAIRE PLUS DUR QUE LE PRECEDENT (le joueur, 24 sept.) : le deuxieme pris
     * recoit vingt-huit renforts du palier 2, le troisieme quarante-deux du palier 3 ; et
     * tous sont ARMES comme les vagues de siege de leur palier (MobGear), la ou la garnison du
     * debut se bat nue.
     *
     * @return le nombre de gardes poses
     */
    public static int reinforce(ServerLevel level, BlockPos centre, int half, int walk, int towerTop,
                                SanctuaryTheme theme, int rank) {
        List<EntityType<?>> pool = pool(level, theme, rank);
        if (pool.isEmpty()) {
            return 0;
        }
        double stage = MobGear.stage(level, rank);
        int perTower = rank >= 3 ? 2 : 1;
        int walkStep = rank >= 3 ? 16 : 24;
        int courtPosts = rank >= 3 ? 12 : 8;
        int posted = 0;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int i = 0; i < perTower; i++) {
                    posted += arm(level, place(level, centre, pool,
                            centre.offset(sx * half, towerTop + 1, sz * half), 3), stage);
                }
            }
        }
        for (int d = -half + 10; d <= half - 10; d += walkStep) {
            posted += arm(level, place(level, centre, pool, centre.offset(d, walk + 1, -half + 2), 1), stage);
            posted += arm(level, place(level, centre, pool, centre.offset(d, walk + 1, half - 2), 1), stage);
        }
        // a soixante-six blocs du centre : hors de la pyramide, en deca des tours
        for (int i = 0; i < courtPosts; i++) {
            double angle = (i + 0.5) / courtPosts * Math.PI * 2;
            posted += arm(level, place(level, centre, pool, centre.offset(
                    (int) Math.round(Math.cos(angle) * 66), 1,
                    (int) Math.round(Math.sin(angle) * 66)), 4), stage);
        }
        return posted;
    }

    /** Un renfort pose recoit l'equipement de son palier ; rend 1 s'il est la. */
    private static int arm(ServerLevel level, @Nullable Entity guard, double stage) {
        if (guard instanceof net.minecraft.world.entity.Mob mob) {
            MobGear.equip(mob, stage, level.random);
        }
        return guard == null ? 0 : 1;
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
        postGuard(level, at, radius, (String) null);
    }

    /** Un garde de coffre ou de couloir, dans le theme du sanctuaire, au premier palier. */
    public static void postGuard(ServerLevel level, BlockPos at, int radius, SanctuaryTheme theme) {
        List<EntityType<?>> pool = pool(level, theme, 1);
        if (!pool.isEmpty()) {
            spawnGuard(level, pool, at, radius, null);
        }
    }

    /** Un gardien avec une marque de plus : le Vide des Poches s'en sert. */
    @javax.annotation.Nullable
    public static Entity postGuard(ServerLevel level, BlockPos at, int radius,
                                   @javax.annotation.Nullable String extraTag) {
        List<EntityType<?>> pool = pool(level);
        return pool.isEmpty() ? null : spawnGuard(level, pool, at, radius, extraTag);
    }

    @javax.annotation.Nullable
    private static Entity spawnGuard(ServerLevel level, List<EntityType<?>> pool,
                                     BlockPos at, int radius, @javax.annotation.Nullable String extraTag) {
        EntityType<?> type = pool.get(level.random.nextInt(pool.size()));
        Entity mob = type.spawn(level, at, MobSpawnType.STRUCTURE);
        if (mob == null) {
            return null;
        }
        mob.addTag(TAG_GUARD);
        if (extraTag != null) {
            mob.addTag(extraTag);
        }
        // LE CASQUE : un mort-vivant nu brule a midi sur sa tour -- les strays et les gelides du
        // Givre, les squelettes des Sables. Vanilla epargne un mort-vivant coiffe (voir Prowl).
        if (mob instanceof net.minecraft.world.entity.Mob living
                && mob.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                && living.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
            living.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
            living.setDropChance(net.minecraft.world.entity.EquipmentSlot.HEAD, 0.0F);
        }
        if (mob instanceof PathfinderMob guard) {
            guard.restrictTo(at, radius);
            guard.goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(guard, 1.0));
            guard.setPersistenceRequired();
        }
        return mob;
    }

    /**
     * Le vivier d'un theme a un palier ; a defaut (un mod manque), celui du siege.
     */
    static List<EntityType<?>> pool(ServerLevel level, SanctuaryTheme theme, int rank) {
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : theme.monsters(rank)) {
            EntityType.byString(id).ifPresent(pool::add);
        }
        return pool.size() >= 2 ? pool : pool(level);
    }

    /**
     * Le vivier sans theme -- les gardes des Poches de la mine : le palier 2 du siege, plus
     * dur qu'un monstre de passage, moins qu'une vague de siege.
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
