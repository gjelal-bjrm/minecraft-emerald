package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasionData;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LA PATROUILLE de Torn (§71) : toucher les douze points d'eco de la ville dans le temps donne,
 * la ville envahie. Une colonne de lumiere de la couleur de l'eco marque chaque point qui reste.
 */
public class PatrolRun extends QuestRun {

    private static final double REACH = 3.5;
    private final Set<Integer> visited = new HashSet<>();
    private List<HavenInvasionData.EcoPoint> points = List.of();

    public PatrolRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
        this.points = HavenInvasionData.ecoPoints(this.level.getServer());
    }

    @Override
    public void tick(long now) {
        if (this.points.isEmpty()) {
            fail("carte");
            return;
        }
        if (now % 5 != 0) {
            return;
        }
        BlockPos origin = HavenState.get(this.level.getServer()).origin();
        List<ServerPlayer> team = members();
        for (HavenInvasionData.EcoPoint point : this.points) {
            if (this.visited.contains(point.number())) {
                continue;
            }
            Vec3 at = Vec3.atBottomCenterOf(point.feetWorld(origin));
            for (ServerPlayer player : team) {
                double dx = player.getX() - at.x;
                double dz = player.getZ() - at.z;
                if (dx * dx + dz * dz <= REACH * REACH && Math.abs(player.getY() - at.y) <= 3.0) {
                    this.visited.add(point.number());
                    for (ServerPlayer member : team) {
                        member.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.patrouille.point",
                                point.name(), this.visited.size(), this.points.size()).withStyle(ChatFormatting.GOLD), true);
                        member.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.9F,
                                0.8F + this.visited.size() * 0.08F);
                    }
                    break;
                }
            }
        }
        List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
        for (HavenInvasionData.EcoPoint point : this.points) {
            if (!this.visited.contains(point.number())) {
                markers.add(QuestMarkers.beacon(Vec3.atBottomCenterOf(point.feetWorld(origin)), color(point.color())));
            }
        }
        QuestMarkers.show(team, markers);
        if (this.visited.size() >= this.points.size()) {
            succeed();
        }
    }

    static int color(HavenInvasionData.EcoColor color) {
        return switch (color) {
            case RED -> QuestMarkers.RED;
            case YELLOW -> QuestMarkers.GOLD;
            case BLUE -> QuestMarkers.BLUE;
            case DARK -> QuestMarkers.PURPLE;
        };
    }

    @Override
    public Component objective() {
        return Component.translatable("game.emeraldweapons.haven.quete.patrouille.objectif", this.visited.size(),
                this.points.size(), clock(secondsLeft()));
    }

    @Override
    public float progress() {
        return this.points.isEmpty() ? 0.0F : this.visited.size() / (float) this.points.size();
    }

    @Override
    public void cleanup() {
        QuestMarkers.clear(members());
    }
}
