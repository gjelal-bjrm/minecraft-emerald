package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.haven.invasion.HavenSpawner;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.network.QuestMarkersPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * L'ELITE de Samos (§71) : trois vagues montent a l'assaut de la terrasse de la tour ouest --
 * six, neuf, douze monstres, et des phantoms --, puis leur chef, l'Elite : un zombie geant en
 * armure de netherite, dix fois plus solide qu'un monstre, qui brille. La vague suivante vient
 * quand la precedente est tombee ; la quete est gagnee quand l'Elite tombe.
 */
public class EliteRun extends QuestRun {

    /** La terrasse de la tour ouest, pres du portail (cellules). */
    private static final BlockPos TERRACE = new BlockPos(482, 123, 596);
    private static final int[] WAVES = {6, 9, 12};

    private final List<BlockPos> spawns = new ArrayList<>();
    private Vec3 center = Vec3.ZERO;
    private boolean prepared;
    /** 0..2 : les vagues ; 3 : l'Elite ; -1 : pas commence (l'equipe n'est pas encore sur la terrasse). */
    private int stage = -1;
    private int left;
    private long pause;

    public EliteRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
        this.center = Vec3.atBottomCenterOf(HavenState.get(this.level.getServer()).origin().offset(TERRACE));
    }

    private void prepare() {
        BlockPos c = BlockPos.containing(this.center);
        for (int dx = -14; dx <= 14; dx++) {
            for (int dz = -14; dz <= 14; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < 5 || d > 14) {
                    continue;
                }
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos at = c.offset(dx, dy, dz);
                    if (HavenSpawner.standable(this.level, at)) {
                        this.spawns.add(at);
                        break;
                    }
                }
            }
        }
        this.prepared = true;
    }

    @Override
    public void tick(long now) {
        List<ServerPlayer> team = members();
        if (!this.prepared) {
            if (!this.level.isLoaded(BlockPos.containing(this.center))) {
                QuestMarkers.show(team, List.of(QuestMarkers.beacon(this.center, QuestMarkers.PURPLE)));
                return;
            }
            prepare();
        }
        if (this.stage < 0) {
            // on attend l'equipe sur la terrasse (le portail de la tour y monte)
            for (ServerPlayer player : team) {
                if (player.position().distanceTo(this.center) < 14.0) {
                    this.stage = 0;
                    this.pause = now + 40;
                    announce(team, Component.translatable("game.emeraldweapons.haven.quete.elite.vague", 1, WAVES.length));
                    break;
                }
            }
            if (now % 20 == 0) {
                QuestMarkers.show(team, List.of(QuestMarkers.beacon(this.center, QuestMarkers.PURPLE)));
            }
            return;
        }
        if (now < this.pause) {
            return;
        }
        if (this.left == 0) {
            if (this.stage < WAVES.length) {
                spawnWave(team, WAVES[this.stage]);
            } else if (this.stage == WAVES.length) {
                spawnElite(team);
            }
        }
        if (now % 20 == 0) {
            QuestMonsters.keepHunting(this.level, this, team);
            List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
            markers.add(QuestMarkers.zone(this.center, 12.0F, QuestMarkers.PURPLE));
            if (this.stage == WAVES.length) {
                for (Mob elite : QuestMonsters.alive(this.level, this)) {
                    markers.add(QuestMarkers.beacon(elite.position(), QuestMarkers.RED));
                }
            }
            QuestMarkers.show(team, markers);
            // une vague dont les monstres se sont perdus (tombes de la tour) se complete
            if (this.left > 0 && QuestMonsters.alive(this.level, this).isEmpty()) {
                this.left = 0;
                advance(team, now);
            }
        }
    }

    private void spawnWave(List<ServerPlayer> team, int size) {
        if (this.spawns.isEmpty()) {
            return;
        }
        for (int i = 0; i < size; i++) {
            BlockPos at = this.spawns.get(this.level.random.nextInt(this.spawns.size()));
            if (QuestMonsters.spawn(this.level, this, HavenSpawner.pickGroundKind(this.level.random), at, team) != null) {
                this.left++;
            }
        }
        for (int i = 0; i < 2 + this.stage; i++) {
            Vec3 at = this.center.add(this.level.random.nextInt(21) - 10, 10 + this.level.random.nextInt(6),
                    this.level.random.nextInt(21) - 10);
            Mob phantom = HavenSpawner.spawnMonster(this.level, HavenInvasion.Kind.PHANTOM, at, true);
            if (phantom != null) {
                phantom.addTag(tag());
                phantom.addTag(HavenInvasion.generationTag(this.level));
                QuestMonsters.hunt(phantom, team);
                this.left++;
            }
        }
    }

    private void spawnElite(List<ServerPlayer> team) {
        BlockPos at = this.spawns.isEmpty() ? BlockPos.containing(this.center) : this.spawns.get(this.level.random.nextInt(this.spawns.size()));
        if (!HavenArrival.standable(this.level, at)) {
            at = BlockPos.containing(this.center);
        }
        Mob elite = QuestMonsters.spawnBrute(this.level, this, at, team, 200.0, 1.7,
                "game.emeraldweapons.haven.quete.elite.nom", true);
        if (elite != null) {
            this.left = 1;
            announce(team, Component.translatable("game.emeraldweapons.haven.quete.elite.chef"));
            for (ServerPlayer player : team) {
                player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.8F, 0.8F);
            }
        }
    }

    @Override
    public void onKill(HavenMonsterKilledEvent event, ServerPlayer killer) {
        if (!event.getMonster().getTags().contains(tag()) || this.left <= 0) {
            return;
        }
        this.left--;
        if (this.left == 0) {
            advance(members(), this.level.getGameTime());
        }
    }

    private void advance(List<ServerPlayer> team, long now) {
        if (this.stage >= WAVES.length) {
            succeed();
            return;
        }
        this.stage++;
        this.pause = now + 60;
        if (this.stage < WAVES.length) {
            announce(team, Component.translatable("game.emeraldweapons.haven.quete.elite.vague", this.stage + 1, WAVES.length));
        }
    }

    private static void announce(List<ServerPlayer> team, Component text) {
        for (ServerPlayer player : team) {
            player.displayClientMessage(text.copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    @Override
    public Component objective() {
        if (this.stage < 0) {
            return Component.translatable("game.emeraldweapons.haven.quete.elite.aller");
        }
        if (this.stage >= WAVES.length) {
            return Component.translatable("game.emeraldweapons.haven.quete.elite.objectif.chef");
        }
        return Component.translatable("game.emeraldweapons.haven.quete.elite.objectif", this.stage + 1, WAVES.length, this.left);
    }

    @Override
    public float progress() {
        return Math.max(0, this.stage) / (float) (WAVES.length + 1);
    }

    @Override
    public BossEvent.BossBarColor color() {
        return BossEvent.BossBarColor.PURPLE;
    }

    @Override
    public void cleanup() {
        QuestMonsters.removeAll(this.level, this);
        QuestMarkers.clear(members());
    }
}
