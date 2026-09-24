package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.network.QuestMarkersPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * LES BRUTES de Sig (§71) : trois brutes en armure rodent autour de la place du bras ouest.
 * Des zombies plus grands, en armure de diamant, trois fois plus solides, qui brillent a
 * travers les murs ; une colonne de lumiere rouge au-dessus de chacune. Si une brute se perd
 * (troncon decharge), une autre la remplace pres de la place.
 */
public class BrutesRun extends QuestRun {

    public static final int GOAL = 3;
    /** La place du bras ouest (point d'eco 3), en cellules. */
    private static final BlockPos PLAZA = new BlockPos(150, 66, 300);
    private int killed;

    public BrutesRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
    }

    @Override
    public void tick(long now) {
        if (now % 20 != 0) {
            return;
        }
        List<ServerPlayer> team = members();
        List<Mob> brutes = QuestMonsters.alive(this.level, this);
        Vec3 plaza = Vec3.atBottomCenterOf(HavenState.get(this.level.getServer()).origin().offset(PLAZA));
        if (brutes.size() + this.killed < GOAL && this.level.isLoaded(BlockPos.containing(plaza))) {
            BlockPos feet = QuestMonsters.ground(this.level, plaza, 6, 40, team);
            if (feet != null) {
                QuestMonsters.spawnBrute(this.level, this, feet, team, 60.0, 1.35, "game.emeraldweapons.haven.quete.brute", false);
            }
        }
        List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
        for (Mob brute : brutes) {
            markers.add(QuestMarkers.beacon(brute.position(), QuestMarkers.RED));
        }
        if (brutes.isEmpty() && this.killed < GOAL) {
            markers.add(QuestMarkers.beacon(plaza, QuestMarkers.RED));
        }
        QuestMarkers.show(team, markers);
        QuestMonsters.keepHunting(this.level, this, team);
    }

    @Override
    public void onKill(HavenMonsterKilledEvent event, ServerPlayer killer) {
        if (event.getMonster().getTags().contains(tag())) {
            this.killed++;
            if (this.killed >= GOAL) {
                succeed();
            }
        }
    }

    @Override
    public Component objective() {
        return Component.translatable("game.emeraldweapons.haven.quete.brutes.objectif", this.killed, GOAL,
                clock(secondsLeft()));
    }

    @Override
    public float progress() {
        return this.killed / (float) GOAL;
    }

    @Override
    public void cleanup() {
        QuestMonsters.removeAll(this.level, this);
        QuestMarkers.clear(members());
    }
}
