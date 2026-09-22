package com.emerald.jak.gun;

import com.emerald.haven.invasion.HavenDestruction;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Ce que touche une arme du Morph Gun : les MONSTRES DE HAVEN et le decor, rien d'autre.
 *
 * LES GARDE-FOUS, pour les quatre armes :
 *  - une seule espece de cible : un monstre de l'invasion vivant
 *    (HavenInvasion.isHavenMonster). Un joueur, un FakePlayer, un habitant de la
 *    ville paisible, une voiture, un decor ne sont JAMAIS vises : les rayons et
 *    les projectiles les traversent, ni degat, ni recul ;
 *  - aucune explosion vanilla (elle pousserait les joueurs) : les zones du Peace
 *    Maker sont des listes de monstres ;
 *  - le decor ne se casse que par HavenDestruction.tryBreak, qui refuse tout ce
 *    qui est protege (appartements, bar, borne, bouton, places, rideau, sous
 *    l'eau) et reconstruit le reste a l'identique en 10 a 15 s.
 *
 * LES DEGATS : points de Jak x {@value GunSpec#HP_PER_JAK} (GunSpec), type
 * emeraldweapons:morph_gun sans entite auteur (voir {@link #source}). Le temps
 * d'invulnerabilite du monstre est remis a zero avant chaque coup : la Vulcan
 * Fury tire toutes les deux tiques, et la protection de dix tiques de Minecraft
 * avalerait sinon quatre balles sur cinq.
 *
 * QUI CASSE QUOI (cote serveur, par tir) :
 *  - Blaster : a l'impact sur un bloc, les blocs dont le centre est a moins de
 *    {@value GunSpec#BLASTER_BREAK_RADIUS} du point d'impact, {@value GunSpec#BLASTER_BLOCKS} au plus ;
 *  - Vulcan Fury : le bloc touche par la balle, un seul ;
 *  - Scatter Gun : le bloc touche par chaque sonde, {@value GunSpec#SCATTER_BLOCKS} au plus par tir ;
 *  - Peace Maker : a l'explosion, les blocs a moins de {@value GunSpec#PEACE_BREAK_RADIUS}
 *    du centre, du plus proche au plus loin, {@value GunSpec#PEACE_BLOCKS} au plus.
 * HavenDestruction plafonne en plus a 96 casses par tique pour toute la ville.
 */
public final class GunImpacts {

    private GunImpacts() {
    }

    /** Une cible des armes : un monstre de l'invasion, ou un danger du large (cahier §85), vivant, dans Haven. */
    public static boolean isTarget(@Nullable Entity entity) {
        return entity instanceof Mob mob && mob.isAlive() && !mob.isRemoved()
                && (HavenInvasion.isHavenMonster(mob) || com.emerald.haven.fauna.HavenFauna.isDanger(mob));
    }

    /** Le type de degat des armes du Morph Gun (data/emeraldweapons/damage_type/morph_gun.json). */
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "morph_gun"));

    /**
     * La source d'un coup d'arme : le type dedie, SANS entite auteur ni entite directe.
     *
     * Un joueur pour auteur (playerAttack, thrown) faisait passer le coup par tous
     * les systemes qui gonflent ou decorent les coups d'un joueur -- critique et nova
     * des heros (HeroCombat), Acharnement, Ravage, Execution, Percee et afflictions
     * des runes (RuneEvents), Drain de Cristal, onde des armes ameliorees, et ceux
     * des autres mods du profil. La regle « 1 point de Jak = {@value GunSpec#HP_PER_JAK} PV »
     * ne tenait plus, et un tir aurait fait naitre les particules d'un autre systeme.
     * Le point d'origine (le tireur, ou le projectile) garde le recul vanilla.
     */
    static DamageSource source(ServerPlayer shooter, @Nullable Entity direct) {
        Holder<DamageType> type = shooter.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE);
        return new DamageSource(type, null, null, direct != null ? direct.position() : shooter.position());
    }

    /**
     * Blesse un monstre de Haven.
     *
     * Le coup est CREDITE au tireur sans passer par l'auteur de la source :
     * setLastHurtByPlayer fait de lui le getKillCredit du monstre (lu par
     * HavenInvasion pour HavenMonsterKilledEvent, donc pour la munition lachee), et
     * setLastHurtByMob garde la riposte du monstre contre lui, comme avant. La mort
     * a lieu dans hurt : le credit est encore la.
     *
     * @param direct le projectile, ou null pour un rayon (coup du joueur)
     * @return vrai si le coup a porte
     */
    public static boolean hurt(ServerPlayer shooter, @Nullable Entity direct, Entity target, float jakDamage) {
        if (!isTarget(target) || !(target instanceof LivingEntity living)) {
            return false;
        }
        DamageSource source = source(shooter, direct);
        living.setLastHurtByPlayer(shooter);
        living.setLastHurtByMob(shooter);
        living.invulnerableTime = 0;
        return living.hurt(source, jakDamage * GunSpec.HP_PER_JAK);
    }

    /** Le centre d'une cible, la ou visent les sondes et la foudre. */
    public static Vec3 center(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    // ================================================================ rayons

    /**
     * Ce qu'un rayon a touche.
     *
     * @param end    la fin du rayon (point touche, ou portee)
     * @param block  le bloc touche, ou null
     * @param target la cible touchee (avant le bloc), ou null
     */
    public record Ray(Vec3 end, @Nullable BlockPos block, @Nullable Mob target) {
        public int flag() {
            return this.target != null ? GunTracePayload.TARGET : this.block != null ? GunTracePayload.BLOCK : GunTracePayload.MISS;
        }
    }

    /**
     * Un rayon : les blocs d'abord (collision, l'eau ne l'arrete pas), puis la
     * cible la plus proche sur le segment, boite gonflee de `inflate`.
     */
    public static Ray ray(ServerLevel level, Entity shooter, Vec3 from, Vec3 direction, double range, float inflate) {
        Vec3 to = from.add(direction.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                shooter));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        Predicate<Entity> filter = GunImpacts::isTarget;
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(level, shooter, from, stop,
                new AABB(from, stop).inflate(1.0 + inflate), filter, inflate);
        if (entity != null && entity.getEntity() instanceof Mob mob) {
            Vec3 at = mob.getBoundingBox().inflate(inflate).clip(from, stop).orElse(center(mob));
            return new Ray(at, null, mob);
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            return new Ray(stop, block.getBlockPos(), null);
        }
        return new Ray(to, null, null);
    }

    // ================================================================ decor

    /**
     * Casse les blocs dont le centre est dans la sphere, du plus proche au plus
     * loin, jusqu'a `cap` casses (HavenDestruction.tryBreak).
     *
     * @return le nombre de blocs casses
     */
    public static int breakSphere(ServerLevel level, Vec3 center, double radius, int cap, @Nullable Entity source) {
        if (cap <= 0) {
            return 0;
        }
        int r = (int) Math.ceil(radius);
        BlockPos c = BlockPos.containing(center);
        double r2 = radius * radius;
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (Vec3.atCenterOf(p).distanceToSqr(center) <= r2) {
                        cells.add(p);
                    }
                }
            }
        }
        cells.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(center)));
        int broken = 0;
        for (BlockPos p : cells) {
            if (broken >= cap) {
                break;
            }
            if (!level.isLoaded(p) || level.getBlockState(p).isAir()) {
                continue;
            }
            if (HavenDestruction.tryBreak(level, p, source)) {
                broken++;
            }
        }
        return broken;
    }

    /** Casse un bloc ; vrai s'il l'est. */
    public static boolean breakOne(ServerLevel level, BlockPos pos, @Nullable Entity source) {
        return level.isLoaded(pos) && !level.getBlockState(pos).isAir() && HavenDestruction.tryBreak(level, pos, source);
    }

    /** Les cibles d'une sphere, de la plus proche a la plus loin, `max` au plus. */
    public static List<Mob> targetsAround(ServerLevel level, Vec3 center, double radius, int max) {
        List<Mob> found = level.getEntitiesOfClass(Mob.class, new AABB(center, center).inflate(radius),
                mob -> isTarget(mob) && center(mob).distanceTo(center) <= radius);
        found.sort(Comparator.comparingDouble(mob -> center(mob).distanceToSqr(center)));
        return found.size() > max ? new ArrayList<>(found.subList(0, max)) : found;
    }
}
