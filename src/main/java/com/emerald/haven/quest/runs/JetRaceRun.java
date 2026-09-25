package com.emerald.haven.quest.runs;

import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.HavenQuests;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.jak.board.JetBoardCourse;
import com.emerald.jak.board.JetBoardEntity;
import com.emerald.network.QuestMarkersPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * LA COURSE DU JET-BOARD, quatrieme quete de Tess (cahier §100) : les anneaux du trace
 * (JetBoardCourse), a passer dans l'ordre, sur la planche. Le chrono part au tremplin de l'est --
 * on a le temps d'y aller -- et la medaille se joue au temps de l'arrivee, comme au stand de tir :
 * l'or, l'argent, et le bronze pour qui finit dans les trois minutes. Facultative (hors de la
 * maitrise, HavenProgress.REQUIRED_QUESTS) et rejouable : on la refait pour une meilleure medaille,
 * qui paie la difference.
 *
 * LES TEMPS : au banc des vehicules, la planche fait le trace seule en 40 s -- bouton tenu, droit
 * sur chaque anneau, sauts lances au bout des rails a la charge qu'il faut. L'or est un cinquieme
 * au-dessus ; l'argent laisse de quoi rater un saut ou deux et repartir du tremplin.
 */
public class JetRaceRun extends QuestRun {

    /** L'or, l'argent, en secondes depuis le tremplin du depart ; le bronze, c'est de finir. */
    public static final int GOLD_SECONDS = 48;
    public static final int SILVER_SECONDS = 65;
    /** Au-dela de trois minutes, la course est perdue. */
    public static final int LIMIT = 20 * 180;

    private final List<JetBoardCourse.Ring> rings = JetBoardCourse.rings();
    private int next;
    private long raceStart = -1L;

    public JetRaceRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
    }

    /** La medaille d'une course de tant de tiques : 3 l'or, 2 l'argent, 1 le bronze. */
    public static int medalFor(long ticks) {
        return ticks <= GOLD_SECONDS * 20L ? 3 : ticks <= SILVER_SECONDS * 20L ? 2 : 1;
    }

    @Override
    public void tick(long now) {
        if (this.rings.isEmpty()) {
            fail("carte");
            return;
        }
        if (this.raceStart >= 0 && now - this.raceStart > LIMIT) {
            fail("temps");
            return;
        }
        List<ServerPlayer> team = members();
        JetBoardCourse.Ring ring = this.rings.get(this.next);
        for (ServerPlayer player : team) {
            if (!(player.getVehicle() instanceof JetBoardEntity) || !ring.passedBy(player.position().add(0.0, 0.9, 0.0))) {
                continue;
            }
            if (this.next == 0) {
                this.raceStart = now;
            }
            this.next++;
            String time = clock((int) ((now - this.raceStart) / 20));
            for (ServerPlayer member : team) {
                member.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F,
                        0.7F + 0.8F * this.next / this.rings.size());
                member.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.course.passe",
                        this.next, this.rings.size(), time).withStyle(ChatFormatting.GOLD), true);
            }
            if (this.next >= this.rings.size()) {
                succeed(medalFor(now - this.raceStart));
                return;
            }
            break;
        }
        if (now % 5 == 0) {
            QuestMarkers.show(team, markers(this.rings, this.next));
        }
    }

    /** Les reperes : l'anneau a passer en or, les deux suivants en blanc, l'arrivee en vert. */
    public static List<QuestMarkersPayload.Marker> markers(List<JetBoardCourse.Ring> rings, int next) {
        List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
        for (int i = next; i < Math.min(rings.size(), next + 3); i++) {
            JetBoardCourse.Ring r = rings.get(i);
            int color = r.kind() == JetBoardCourse.Kind.FINISH ? QuestMarkers.GREEN
                    : i == next ? QuestMarkers.GOLD : QuestMarkers.WHITE;
            if (r.kind() == JetBoardCourse.Kind.PAD) {
                // un tremplin : un cercle au sol, et sa colonne tant qu'on n'est pas parti
                Vec3 ground = r.center().add(0.0, -1.5, 0.0);
                markers.add(QuestMarkers.zone(ground, r.radius() + 0.4F, color));
                if (i == next) {
                    markers.add(QuestMarkers.beacon(ground, color));
                }
            } else {
                markers.add(QuestMarkers.ring(r.center(), r.yaw(), r.radius(), color));
            }
        }
        return markers;
    }

    @Override
    public Component objective() {
        if (this.raceStart < 0) {
            return Component.translatable("game.emeraldweapons.haven.quete.course.depart");
        }
        int elapsed = (int) ((this.level.getGameTime() - this.raceStart) / 20);
        int medal = elapsed <= GOLD_SECONDS ? 3 : elapsed <= SILVER_SECONDS ? 2 : 1;
        int goal = medal == 3 ? GOLD_SECONDS : medal == 2 ? SILVER_SECONDS : LIMIT / 20;
        return Component.translatable("game.emeraldweapons.haven.quete.course.objectif", this.next, this.rings.size(),
                clock(elapsed), HavenQuests.medalName(medal), clock(goal));
    }

    @Override
    public float progress() {
        return this.next / (float) Math.max(1, this.rings.size());
    }

    /** Les anneaux deja passes : l'index de celui a passer (le banc y pose la planche). */
    public int ringsPassed() {
        return this.next;
    }

    @Override
    public void cleanup() {
        QuestMarkers.clear(members());
    }
}
