package com.emerald.haven.quest.runs;

import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.quest.HavenQuest;
import com.emerald.haven.quest.QuestMarkers;
import com.emerald.haven.quest.QuestRun;
import com.emerald.jak.gun.GunForm;
import com.emerald.network.QuestMarkersPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * LES QUATRE ECOS de Samos (§71) : de l'eco rouge, jaune, bleu et sombre, recolte sans mourir,
 * la ville envahie. Toucher une munition d'eco de chaque couleur -- un point de la carte ou ce
 * qu'un monstre lache -- suffit, meme la reserve pleine. Une mort dans l'equipe : on recommence.
 * Les points d'eco des couleurs qui manquent ont leur colonne de lumiere.
 */
public class EcosRun extends QuestRun {

    private final Set<GunForm.Family> gathered = EnumSet.noneOf(GunForm.Family.class);

    public EcosRun(HavenQuest quest, ServerLevel level) {
        super(quest, level);
    }

    @Override
    public void begin() {
    }

    @Override
    public void onEco(ServerPlayer member, GunForm.Family family) {
        if (this.gathered.add(family)) {
            for (ServerPlayer player : members()) {
                player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.quete.ecos.pris",
                        Component.translatable(family.translationKey()), this.gathered.size()).withStyle(ChatFormatting.GREEN), true);
                player.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.4F);
            }
            if (this.gathered.size() == GunForm.Family.values().length) {
                succeed();
            }
        }
    }

    @Override
    public void onDeath(ServerPlayer member) {
        fail("mort");
    }

    @Override
    public void tick(long now) {
        if (now % 20 != 0) {
            return;
        }
        BlockPos origin = HavenState.get(this.level.getServer()).origin();
        List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
        for (HavenInvasionData.EcoPoint point : HavenInvasionData.ecoPoints(this.level.getServer())) {
            GunForm.Family family = GunForm.Family.values()[point.color().ordinal()];
            if (!this.gathered.contains(family)) {
                markers.add(QuestMarkers.beacon(Vec3.atBottomCenterOf(point.feetWorld(origin)), PatrolRun.color(point.color())));
            }
        }
        QuestMarkers.show(members(), markers);
    }

    @Override
    public Component objective() {
        MutableComponent line = Component.empty();
        for (GunForm.Family family : GunForm.Family.values()) {
            boolean got = this.gathered.contains(family);
            line.append(Component.literal(got ? " ✓" : " ✗").withStyle(got ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(family.translationKey()).withStyle(got ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        }
        return Component.translatable("game.emeraldweapons.haven.quete.ecos.objectif", line, clock(secondsLeft()));
    }

    @Override
    public float progress() {
        return this.gathered.size() / (float) GunForm.Family.values().length;
    }

    @Override
    public void cleanup() {
        QuestMarkers.clear(members());
    }
}
