package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenState;
import com.emerald.haven.quest.HavenHero;
import com.emerald.haven.quest.HavenNpcs;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.haven.traffic.HavenTrafficData;
import com.emerald.jak.vehicle.JakVehicleEntity;
import com.emerald.jak.vehicle.VehiclePhysics;
import com.emerald.network.QuestMarkersPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * LA COURSE AUX ANNEAUX de Keira (§71) : dix anneaux sur la voie haute, a passer dans l'ordre,
 * au volant. Ils suivent les voies du trafic de Jak 3 (haven_traffic.json) a partir du noeud le
 * plus proche de Keira, a l'altitude de la voie haute du joueur. Le chrono part au premier
 * anneau -- on a le temps d'aller chercher sa voiture -- et il faut les dix en deux minutes et
 * demie. L'anneau a passer est dore, le suivant blanc.
 */
public class RingsRun extends QuestRun {

    public static final int RINGS = 10;
    public static final int RACE = 20 * 150;
    private static final double REACH = 6.5;
    private static final float RADIUS = 5.5F;

    private final List<Vec3> centers = new ArrayList<>();
    private final List<Float> yaws = new ArrayList<>();
    private int next;
    private long raceStart = -1L;

    public RingsRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
        HavenTrafficData.Data data = HavenTrafficData.get(this.level.getServer());
        BlockPos origin = HavenState.get(this.level.getServer()).origin();
        Vec3 keira = HavenNpcs.spot(HavenHero.KEIRA);
        if (data == null || keira == null) {
            return;
        }
        // le noeud le plus proche de Keira, puis de branche en branche (jamais la sortie de la ville)
        HavenTrafficData.Node node = null;
        double best = Double.MAX_VALUE;
        for (HavenTrafficData.Node n : data.nodes()) {
            double d = n.world(origin).distanceToSqr(keira);
            if (d < best) {
                best = d;
                node = n;
            }
        }
        List<HavenTrafficData.Node> path = new ArrayList<>();
        int guard = 0;
        while (node != null && path.size() < RINGS + 1 && guard++ < 200) {
            path.add(node);
            List<HavenTrafficData.Branch> out = new ArrayList<>();
            for (int b : node.branches()) {
                HavenTrafficData.Branch branch = data.branch(b);
                if (!branch.exit() && branch.length() >= 30.0) {
                    out.add(branch);
                }
            }
            if (out.isEmpty()) {
                break;
            }
            HavenTrafficData.Branch pick = out.get(this.level.random.nextInt(out.size()));
            node = data.node(pick.dest());
        }
        for (int i = 0; i < path.size() && this.centers.size() < RINGS; i++) {
            Vec3 at = path.get(i).world(origin);
            Vec3 center = new Vec3(at.x, VehiclePhysics.havenTrafficY(at.x, at.z) + 1.4, at.z);
            Vec3 toward = i + 1 < path.size() ? path.get(i + 1).world(origin) : at.add(at.subtract(path.get(Math.max(0, i - 1)).world(origin)));
            double dx = toward.x - at.x;
            double dz = toward.z - at.z;
            this.centers.add(center);
            this.yaws.add((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
    }

    @Override
    public void tick(long now) {
        if (this.centers.size() < RINGS) {
            fail("carte");
            return;
        }
        if (this.raceStart >= 0 && now - this.raceStart > RACE) {
            fail("temps");
            return;
        }
        List<ServerPlayer> team = members();
        Vec3 ring = this.centers.get(this.next);
        for (ServerPlayer player : team) {
            if (!(player.getVehicle() instanceof JakVehicleEntity car) || car.getControllingPassenger() != player) {
                continue;
            }
            if (car.position().add(0, 1.0, 0).distanceTo(ring) <= REACH) {
                if (this.next == 0) {
                    this.raceStart = now;
                }
                this.next++;
                for (ServerPlayer member : team) {
                    member.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0F, 0.8F + this.next * 0.06F);
                    member.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.anneaux.passe",
                            this.next, RINGS).withStyle(ChatFormatting.GOLD), true);
                }
                if (this.next >= RINGS) {
                    succeed();
                    return;
                }
                break;
            }
        }
        if (now % 5 == 0) {
            List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
            for (int i = this.next; i < Math.min(RINGS, this.next + 3); i++) {
                int color = i == this.next ? QuestMarkers.GOLD : QuestMarkers.WHITE;
                markers.add(QuestMarkers.ring(this.centers.get(i), this.yaws.get(i), RADIUS, color));
            }
            if (this.next == 0) {
                markers.add(QuestMarkers.beacon(this.centers.get(0).add(0, -RADIUS - 8, 0), QuestMarkers.GOLD));
            }
            QuestMarkers.show(team, markers);
        }
    }

    @Override
    public Component objective() {
        if (this.raceStart < 0) {
            return Component.translatable("game.emeraldweapons.haven.quete.anneaux.depart");
        }
        int left = (int) Math.max(0, (RACE - (this.level.getGameTime() - this.raceStart) + 19) / 20);
        return Component.translatable("game.emeraldweapons.haven.quete.anneaux.objectif", this.next, RINGS, clock(left));
    }

    @Override
    public float progress() {
        return this.next / (float) RINGS;
    }

    @Override
    public void cleanup() {
        QuestMarkers.clear(members());
    }
}
