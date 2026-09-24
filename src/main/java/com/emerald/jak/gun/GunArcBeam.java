package com.emerald.jak.gun;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * L'arc de l'Arc Wielder (gun-blue-2-lightning-tracker, gun-blue-shot.gc:1326-1950).
 *
 * UNE CORDE DE FOUDRE recalculee a chaque tique tant que la gachette est tenue :
 * {@value GunSpec#ARC_NODES} noeuds espaces de {@value GunSpec#ARC_SEGMENT} blocs, le
 * premier a la bouche du canon, dans le regard du tireur. Chaque noeud S'ACCROCHE au
 * monstre de Haven le plus proche de sa place, s'il est a moins de
 * {@value GunSpec#ARC_HOOK} blocs et dans un cone de 53 degres autour du segment, et
 * la corde repart de lui : l'arc saute de monstre en monstre, un monstre par chaine.
 * Un noeud sans monstre continue tout droit. LE PREMIER MUR COUPE L'ARC.
 *
 * LES DEGATS : {@value GunSpec#ARC_DAMAGE} points de Jak par monstre accroche -- ou
 * traverse par un segment libre --, UNE FOIS PAR {@value GunSpec#ARC_HIT_TICKS}
 * TIQUES ET PAR MONSTRE : dans le jeu l'identifiant d'attaque de l'arc ne change que
 * toutes les 0,4 s, et c'est lui qui retient les coups. Soit 6,25 points par seconde.
 *
 * Le decor : le bloc ou l'arc s'arrete casse, un toutes les deux tiques -- un mur
 * mince ne l'arrete donc qu'un instant. La trace de la tique part en un
 * paquet, ligne brisee de la bouche au bout de la corde (GunTracePayload.ARC).
 */
final class GunArcBeam {

    /** Ce qu'une tique d'arc a fait (banc d'essai). */
    record Result(int nodes, int hooked, int hurt, boolean blocked, double length) {
    }

    private GunArcBeam() {
    }

    /**
     * Une tique d'arc.
     *
     * @param gate par monstre, la tique a partir de laquelle il peut reprendre un coup
     */
    static Result fire(ServerLevel level, ServerPlayer shooter, Map<Integer, Long> gate, long now) {
        gate.values().removeIf(until -> until <= now);
        Vec3 eye = shooter.getEyePosition();
        Vec3 direction = shooter.getLookAngle();
        Vec3 from = eye.add(direction.scale(0.3)).add(0.0, -0.1, 0.0);
        Vec3 start = from;
        Set<Integer> hooked = new HashSet<>();
        List<float[]> points = new ArrayList<>();
        int hurt = 0;
        boolean blocked = false;
        double length = 0.0;
        int nodes = 1;
        for (int k = 1; k < GunSpec.ARC_NODES && !blocked; k++) {
            Vec3 free = from.add(direction.scale(GunSpec.ARC_SEGMENT));
            Mob hook = hook(level, from, direction, free, hooked);
            Vec3 to = hook == null ? free : GunImpacts.center(hook);
            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            if (wall.getType() == HitResult.Type.BLOCK) {
                to = wall.getLocation();
                blocked = true;
                hook = null;
                if (now % 2L == 0L) {
                    // un bloc toutes les deux tiques, la cadence de casse de la Vulcan Fury : l'arc perce, il ne fore pas
                    GunImpacts.breakOne(level, wall.getBlockPos(), shooter);
                }
            }
            Vec3 span = to.subtract(from);
            double reach = span.length();
            if (reach < 1.0e-3) {
                break;
            }
            Vec3 along = span.scale(1.0 / reach);
            if (hook != null) {
                hooked.add(hook.getId());
                hurt += strike(shooter, hook, gate, now) ? 1 : 0;
            } else {
                // un segment libre touche le premier monstre sur son trajet
                GunImpacts.Ray ray = GunImpacts.ray(level, shooter, from, along, reach, GunSpec.ARC_SEGMENT_RADIUS);
                if (ray.target() != null && hooked.add(ray.target().getId())) {
                    hurt += strike(shooter, ray.target(), gate, now) ? 1 : 0;
                }
            }
            points.add(new float[]{(float) to.x, (float) to.y, (float) to.z,
                    hook != null ? GunTracePayload.TARGET : blocked ? GunTracePayload.BLOCK : GunTracePayload.MISS});
            length += reach;
            direction = along;
            from = to;
            nodes++;
        }
        float[] ends = new float[points.size() * 4];
        for (int i = 0; i < points.size(); i++) {
            System.arraycopy(points.get(i), 0, ends, i * 4, 4);
        }
        PacketDistributor.sendToPlayersNear(level, null, start.x, start.y, start.z, 128.0,
                new GunTracePayload(shooter.getId(), GunTracePayload.ARC, start.x, start.y, start.z, ends));
        return new Result(nodes, hooked.size(), hurt, blocked, length);
    }

    /** Le monstre ou s'accroche un noeud : le plus proche de sa place libre, dans le cone du segment, pas deja pris. */
    private static Mob hook(ServerLevel level, Vec3 from, Vec3 direction, Vec3 free, Set<Integer> hooked) {
        Mob best = null;
        double bestDistance = GunSpec.ARC_HOOK;
        for (Mob mob : GunImpacts.targetsAround(level, free, GunSpec.ARC_HOOK, 16)) {
            if (hooked.contains(mob.getId())) {
                continue;
            }
            Vec3 to = GunImpacts.center(mob).subtract(from);
            double reach = to.length();
            if (reach < 0.5 || to.dot(direction) / reach <= GunSpec.ARC_HOOK_COS) {
                continue;
            }
            double distance = GunImpacts.center(mob).distanceTo(free);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mob;
            }
        }
        return best;
    }

    /** Une accroche : un monstre, ou un vehicule -- cinq points du jeu, une fois par vehicule (cahier §98). */
    private static boolean strike(ServerPlayer shooter, net.minecraft.world.entity.Entity target, Map<Integer, Long> gate,
                                  long now) {
        com.emerald.jak.vehicle.JakVehicleEntity car = com.emerald.jak.vehicle.VehicleDamage.vehicleOf(target);
        int key = car != null ? car.getId() : target.getId();
        if (gate.containsKey(key)) {
            return false;
        }
        gate.put(key, now + GunSpec.ARC_HIT_TICKS);
        return car != null ? com.emerald.jak.vehicle.VehicleDamage.shot(car, shooter, com.emerald.jak.vehicle.VehicleDamage.ARC)
                : GunImpacts.hurt(shooter, null, target, GunSpec.ARC_DAMAGE);
    }
}
