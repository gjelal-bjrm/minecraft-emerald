package com.emerald.haven.quest.runs;

import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.haven.traffic.HavenTraffic;
import com.emerald.jak.vehicle.JakVehicleEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;
import java.util.List;

/**
 * LE CHAUFFARD de Keira : une voiture du trafic seme la panique. Elle brille a travers les murs,
 * une colonne de lumiere la suit ; il faut la percuter trois fois, au volant, en trois minutes
 * (un choc franc : la quantite de mouvement du choc, calculee par VehicleImpacts). Si elle
 * quitte la ville -- ou si un joueur la vole (cahier §99) --, une autre prend sa place.
 */
public class RecklessRun extends QuestRun {

    public static final int GOAL = 3;
    /** Un choc franc, pas une caresse. */
    private static final double MIN_HIT = 0.25;
    private static final int GAP = 20;

    @Nullable
    private JakVehicleEntity target;
    private int hits;
    private long lastHit = Long.MIN_VALUE / 2;

    public RecklessRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
    }

    @Override
    public void tick(long now) {
        List<ServerPlayer> team = members();
        if (this.target != null && !this.target.isRemoved() && this.target.traffic() == null) {
            // volee : ce n'est plus le chauffard
            this.target.setGlowingTag(false);
            this.target.removeTag(tag());
            this.target = null;
        }
        if (this.target == null || this.target.isRemoved()) {
            this.target = pick(team);
        }
        if (now % 10 == 0 && this.target != null) {
            QuestMarkers.show(team, List.of(QuestMarkers.beacon(this.target.position(), QuestMarkers.RED)));
        }
    }

    /** Une voiture du trafic a bonne distance du joueur le plus proche : elle devient le chauffard. */
    @Nullable
    private JakVehicleEntity pick(List<ServerPlayer> team) {
        JakVehicleEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (JakVehicleEntity car : HavenTraffic.loaded(this.level)) {
            double nearest = Double.MAX_VALUE;
            for (ServerPlayer player : team) {
                nearest = Math.min(nearest, car.distanceTo(player));
            }
            double score = Math.abs(nearest - 90.0);
            if (nearest >= 40.0 && score < bestScore) {
                bestScore = score;
                best = car;
            }
        }
        if (best != null) {
            best.setGlowingTag(true);
            best.addTag(tag());
            for (ServerPlayer player : team) {
                player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.chauffard.repere")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
        return best;
    }

    @Override
    public void onRam(ServerPlayer driver, JakVehicleEntity car, double momentum) {
        long now = this.level.getGameTime();
        if (car != this.target || momentum < MIN_HIT || now - this.lastHit < GAP) {
            return;
        }
        this.lastHit = now;
        this.hits++;
        for (ServerPlayer player : members()) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.chauffard.choc",
                    this.hits, GOAL).withStyle(ChatFormatting.GOLD), true);
            player.playNotifySound(SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        if (this.hits >= GOAL) {
            succeed();
        }
    }

    @Override
    public Component objective() {
        return Component.translatable("game.emeraldweapons.haven.quete.chauffard.objectif", this.hits, GOAL,
                clock(secondsLeft()));
    }

    @Override
    public float progress() {
        return this.hits / (float) GOAL;
    }

    @Override
    public void cleanup() {
        if (this.target != null && !this.target.isRemoved()) {
            this.target.setGlowingTag(false);
            this.target.removeTag(tag());
        }
        QuestMarkers.clear(members());
    }
}
