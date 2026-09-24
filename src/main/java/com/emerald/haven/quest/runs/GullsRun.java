package com.emerald.haven.quest.runs;

import com.emerald.haven.fauna.HavenFauna;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.network.QuestMarkersPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * L'ARMEE DE MOUETTES du Pecheur (§85.1) : « une quete qui consiste a en tuer » (le joueur,
 * §79.7). Les mouettes des quais volent son poisson : en abattre dix. Chaque mouette touchee
 * appelle son armee (SeagullArmy) -- dix-huit mouettes qui bousculent et piquent ; et
 * PENDANT CETTE QUETE, ET ELLE SEULE, l'armee s'abat aussi et compte. Ailleurs, sa colere ne
 * se combat pas (§85.2) ; ici, c'est la bataille. Le temps de la quete, les armes du Morph
 * Gun visent les mouettes (GunImpacts.isTarget).
 *
 * Les trois mouettes des quais les plus proches ont leur colonne de lumiere.
 */
public class GullsRun extends QuestRun {

    public static final int GOAL = 10;

    private int kills;

    public GullsRun(HavenQuest quest, ServerLevel level) {
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
        List<Mob> gulls = new ArrayList<>(HavenFauna.loaded(this.level, HavenFauna.Role.GULL));
        List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
        if (!team.isEmpty() && !gulls.isEmpty()) {
            gulls.sort(Comparator.comparingDouble(gull -> {
                double best = Double.MAX_VALUE;
                for (ServerPlayer player : team) {
                    best = Math.min(best, gull.distanceToSqr(player));
                }
                return best;
            }));
            for (int i = 0; i < Math.min(3, gulls.size()); i++) {
                markers.add(QuestMarkers.beacon(gulls.get(i).position(), QuestMarkers.WHITE));
            }
        }
        QuestMarkers.show(team, markers);
    }

    @Override
    public void onFaunaKill(ServerPlayer member, Mob mob) {
        if (!HavenFauna.gullOrArmy(mob)) {
            return;
        }
        this.kills++;
        for (ServerPlayer player : members()) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.mouettes.abattue",
                    this.kills, GOAL).withStyle(ChatFormatting.GOLD), true);
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        if (this.kills >= GOAL) {
            succeed();
        }
    }

    @Override
    public Component objective() {
        return Component.translatable("game.emeraldweapons.haven.quete.mouettes.objectif", this.kills, GOAL);
    }

    @Override
    public float progress() {
        return this.kills / (float) GOAL;
    }

    @Override
    public BossEvent.BossBarColor color() {
        return BossEvent.BossBarColor.WHITE;
    }

    @Override
    public void cleanup() {
        QuestMarkers.clear(members());
    }
}
