package com.emerald.jak.gun;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * L'apesanteur du Mass Inverter (gravity-spinner, gun-dark-shot.gc:2123-3025).
 *
 * Un monstre de Haven pris par le champ LEVITE 7 a 9 secondes : sans poids, tenu
 * entre {@value GunSpec#INVERTER_BAND_LOW} et {@value GunSpec#INVERTER_BAND_HIGH}
 * blocs du sol, il tourne sur lui-meme, perd sa cible et son chemin.
 *
 * LE MASS INVERTER NE BLESSE PRESQUE PAS : IL MULTIPLIE. Tout coup qu'un monstre
 * recoit en l'air n'est PAS applique : il est GARDE (en points de Jak). A la
 * retombee le monstre prend d'un coup {@value GunSpec#INVERTER_MULTIPLIER} fois ce
 * qui a ete garde, plus sa chute : max({@value GunSpec#INVERTER_FALL_MIN} ; hauteur /
 * 2) points (:2937-3000). On souleve, on mitraille, et tout retombe double.
 *
 * LE BILLARD (:2411-2571) : un monstre frappe en l'air est projete -- vers un autre
 * monstre s'il y en a un dans {@value GunSpec#INVERTER_BILLIARD_RANGE} blocs, a 45
 * degres de la direction du coup et en vue, sinon dans la direction du coup. S'il en
 * percute un autre a plus de 10 m/s, le choc s'ajoute a ce que chacun garde : la
 * vitesse / 10 m/s, en points de Jak (:2817-2828) ; un monstre au sol le prend
 * tout de suite.
 *
 * L'etat vit ici, en memoire du serveur, par monstre. Un monstre qui sort de la
 * memoire sans retomber retrouve son poids : a l'arret du serveur AVANT la
 * sauvegarde ({@link #onServerStopping}), ou a son retour si son troncon a ete
 * decharge pendant sa levitation ({@link #onJoin}).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class GunLevitation {

    private static final class Floating {
        final Mob mob;
        final ServerPlayer owner;
        final long until;
        float cached;
        boolean falling;
        double dropHeight;
        int fallTicks;
        boolean releasing;
        int hits;

        Floating(Mob mob, ServerPlayer owner, long until) {
            this.mob = mob;
            this.owner = owner;
            this.until = until;
        }
    }

    private static final Map<Integer, Floating> FLOATING = new HashMap<>();

    private GunLevitation() {
    }

    /** Souleve un monstre, s'il ne levite pas deja. Vrai s'il vient d'etre pris. */
    public static boolean lift(Mob mob, ServerPlayer owner, long now, int ticks) {
        if (!GunImpacts.isTarget(mob) || FLOATING.containsKey(mob.getId())) {
            return false;
        }
        FLOATING.put(mob.getId(), new Floating(mob, owner, now + ticks));
        mob.setNoGravity(true);
        mob.setDeltaMovement(mob.getDeltaMovement().x * 0.3, 0.25, mob.getDeltaMovement().z * 0.3);
        mob.hasImpulse = true;
        return true;
    }

    public static boolean floating(@Nullable Entity entity) {
        Floating f = entity == null ? null : FLOATING.get(entity.getId());
        return f != null && f.mob == entity && !f.falling;
    }

    /** Les points de Jak gardes pour ce monstre (banc d'essai) ; -1 s'il ne levite pas. */
    public static float cached(@Nullable Entity entity) {
        Floating f = entity == null ? null : FLOATING.get(entity.getId());
        return f == null || f.mob != entity ? -1.0F : f.cached;
    }

    public static int count() {
        return FLOATING.size();
    }

    // ================================================================ les coups gardes

    /** Un coup sur un monstre en l'air : garde, pas applique ; et le billard. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Floating f = FLOATING.get(event.getEntity().getId());
        if (f == null || f.mob != event.getEntity() || f.releasing || f.falling) {
            return;
        }
        f.cached += event.getAmount() / GunSpec.HP_PER_JAK;
        f.hits++;
        Vec3 source = event.getSource().getSourcePosition();
        event.setCanceled(true);
        if (source != null && f.mob.level() instanceof ServerLevel level) {
            billiard(level, f, source);
        }
    }

    /** Projete le monstre frappe : vers un autre monstre dans le cone du coup, sinon dans la direction du coup. */
    private static void billiard(ServerLevel level, Floating f, Vec3 source) {
        Vec3 at = GunImpacts.center(f.mob);
        Vec3 push = new Vec3(at.x - source.x, 0.0, at.z - source.z);
        if (push.lengthSqr() < 1.0e-6) {
            return;
        }
        push = push.normalize();
        Mob best = null;
        double bestDot = GunSpec.INVERTER_BILLIARD_COS;
        for (Mob other : GunImpacts.targetsAround(level, at, GunSpec.INVERTER_BILLIARD_RANGE, 32)) {
            if (other == f.mob) {
                continue;
            }
            Vec3 to = GunImpacts.center(other).subtract(at);
            double reach = to.length();
            if (reach < 1.0) {
                continue;
            }
            double dot = (to.x * push.x + to.z * push.z) / reach;
            if (dot <= bestDot) {
                continue;
            }
            BlockHitResult sight = level.clip(new ClipContext(at, GunImpacts.center(other), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, f.mob));
            if (sight.getType() == HitResult.Type.MISS) {
                best = other;
                bestDot = dot;
            }
        }
        Vec3 direction = best == null ? push : GunImpacts.center(best).subtract(at).normalize();
        f.mob.setDeltaMovement(direction.scale(0.9));
        f.mob.hasImpulse = true;
    }

    // ================================================================ la tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (FLOATING.isEmpty()) {
            return;
        }
        for (Iterator<Floating> it = FLOATING.values().iterator(); it.hasNext(); ) {
            Floating f = it.next();
            Mob mob = f.mob;
            if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
                mob.setNoGravity(false);
                it.remove();
                continue;
            }
            if (f.falling) {
                if (mob.onGround() || ++f.fallTicks > 60) {
                    land(f);
                    it.remove();
                }
                continue;
            }
            long now = level.getGameTime();
            if (now >= f.until || f.owner.isRemoved() || !MorphGunKeeper.allowed(f.owner)) {
                f.falling = true;
                f.dropHeight = Math.max(0.0, mob.getY() - groundBelow(level, mob));
                mob.setNoGravity(false);
                mob.fallDistance = 0.0F;
                continue;
            }
            hover(level, f);
        }
    }

    /** Tient le monstre dans sa bande, le fait tourner, lui retire cible et chemin ; et les chocs du billard. */
    private static void hover(ServerLevel level, Floating f) {
        Mob mob = f.mob;
        double ground = groundBelow(level, mob);
        double wobble = Math.sin((mob.tickCount + mob.getId() * 7) * 0.15) * 0.5;
        double wanted = ground + 0.5 * (GunSpec.INVERTER_BAND_LOW + GunSpec.INVERTER_BAND_HIGH) + wobble;
        Vec3 v = mob.getDeltaMovement();
        double vy = Math.max(-0.3, Math.min(0.3, (wanted - mob.getY()) * 0.2));
        double speed = Math.hypot(v.x, v.z);
        if (speed > GunSpec.INVERTER_IMPACT_SPEED) {
            collide(level, f, speed);
            v = mob.getDeltaMovement();
        }
        mob.setDeltaMovement(v.x * 0.96, vy, v.z * 0.96);
        mob.hasImpulse = true;
        mob.fallDistance = 0.0F;
        mob.setTarget(null);
        mob.getNavigation().stop();
        float yaw = mob.getYRot() + 24.0F;
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.yHeadRot = yaw;
        if (mob.tickCount % 4 == 0) {
            level.sendParticles(ModParticles.GUN_INVERTER_MOTE.get(), mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
                    1, 0.3, 0.4, 0.3, 0.0);
        }
    }

    /** Un monstre lance qui en percute un autre : le choc s'ajoute a ce que chacun garde, ou blesse celui qui est au sol. */
    private static void collide(ServerLevel level, Floating f, double speed) {
        Vec3 at = GunImpacts.center(f.mob);
        for (Mob other : GunImpacts.targetsAround(level, at, 1.4, 4)) {
            if (other == f.mob) {
                continue;
            }
            float impact = (float) (speed / GunSpec.INVERTER_IMPACT_SPEED);
            f.cached += impact;
            Floating hit = FLOATING.get(other.getId());
            if (hit != null && hit.mob == other && !hit.falling) {
                hit.cached += impact;
                other.setDeltaMovement(f.mob.getDeltaMovement().scale(0.6));
                other.hasImpulse = true;
            } else {
                GunImpacts.hurt(f.owner, null, other, impact);
            }
            f.mob.setDeltaMovement(f.mob.getDeltaMovement().scale(0.3));
            return;
        }
    }

    /** La retombee : le double de ce qui a ete garde, plus la chute. */
    private static void land(Floating f) {
        float fall = Math.max(GunSpec.INVERTER_FALL_MIN, (float) (f.dropHeight / 2.0));
        f.releasing = true;
        f.mob.fallDistance = 0.0F;
        GunImpacts.hurt(f.owner, null, f.mob, GunSpec.INVERTER_MULTIPLIER * f.cached + fall);
    }

    private static double groundBelow(ServerLevel level, Mob mob) {
        Vec3 from = mob.position().add(0.0, 0.2, 0.0);
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(0.0, -16.0, 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, mob));
        return hit.getType() == HitResult.Type.MISS ? mob.getY() - 2.0 : hit.getLocation().y;
    }

    /** Rend leur poids a tous les monstres en l'air (arret du serveur, banc d'essai). */
    public static void releaseAll() {
        for (Floating f : FLOATING.values()) {
            f.mob.setNoGravity(false);
        }
        FLOATING.clear();
    }

    /** AVANT la sauvegarde : le drapeau « sans poids » d'une entite s'ecrit dans le monde. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        releaseAll();
    }

    /** Un monstre sauvegarde en l'air (troncon decharge pendant sa levitation) retrouve son poids a son retour. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity() instanceof Mob mob && mob.isNoGravity()
                && GunImpacts.isTarget(mob) && !FLOATING.containsKey(mob.getId())) {
            mob.setNoGravity(false);
        }
    }
}
