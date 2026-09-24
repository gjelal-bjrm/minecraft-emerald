package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.haven.invasion.HavenSpawner;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * NETTOYER LE MARCHE (Sig) : les arcades du bras ouest sont infestees. Dix-huit monstres de la
 * quete y naissent quand l'equipe approche (le troncon charge), et brillent ; les abattre tous,
 * dans le temps donne. Le cercle au sol dit ou est le marche.
 */
public class MarketRun extends QuestRun {

    public static final int GOAL = 18;
    /** Les arcades du bras ouest (point d'eco 4), en cellules. */
    private static final BlockPos MARKET = new BlockPos(286, 66, 268);
    private static final double RADIUS = 30.0;
    private int spawned;
    private int killed;

    public MarketRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    private Vec3 center() {
        return Vec3.atBottomCenterOf(HavenState.get(this.level.getServer()).origin().offset(MARKET));
    }

    @Override
    public void begin() {
    }

    @Override
    public void tick(long now) {
        if (now % 10 != 0) {
            return;
        }
        List<ServerPlayer> team = members();
        Vec3 center = center();
        // ils naissent quand l'equipe est a moins de cent blocs : le troncon est charge, et ils l'attendent
        if (this.spawned < GOAL && this.level.isLoaded(BlockPos.containing(center))) {
            boolean near = false;
            for (ServerPlayer player : team) {
                near |= player.position().distanceTo(center) < 100.0;
            }
            if (near) {
                for (int i = 0; i < 6 && this.spawned < GOAL; i++) {
                    BlockPos feet = QuestMonsters.ground(this.level, center, 3, RADIUS, team);
                    if (feet == null) {
                        break;
                    }
                    Mob mob = QuestMonsters.spawn(this.level, this, HavenSpawner.pickGroundKind(this.level.random), feet, team);
                    if (mob != null) {
                        mob.setGlowingTag(true);
                        mob.setTarget(null);
                        this.spawned++;
                    }
                }
            }
        }
        if (now % 20 == 0) {
            QuestMarkers.show(team, List.of(QuestMarkers.zone(center, (float) RADIUS, QuestMarkers.RED),
                    QuestMarkers.beacon(center, QuestMarkers.RED)));
        }
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
        return Component.translatable("game.emeraldweapons.haven.quete.marche.objectif", GOAL - this.killed,
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
