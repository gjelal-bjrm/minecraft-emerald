package com.emerald.haven.quest;

import com.emerald.network.QuestMarkersPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les reperes d'une quete, cote serveur : ce qui est affiche a chaque joueur de l'equipe
 * (QuestMarkersPayload). Un envoi seulement quand la liste change, et au joueur qui rejoint.
 */
public final class QuestMarkers {

    /** Les couleurs des reperes. */
    public static final int GOLD = 0xD0FFC030;
    public static final int GREEN = 0xC050FF70;
    public static final int RED = 0xC0FF4040;
    public static final int BLUE = 0xC050A0FF;
    public static final int PURPLE = 0xC0B060FF;
    public static final int WHITE = 0xB0FFFFFF;

    private static final Map<UUID, List<QuestMarkersPayload.Marker>> SENT = new HashMap<>();

    private QuestMarkers() {
    }

    public static QuestMarkersPayload.Marker beacon(Vec3 at, int argb) {
        return new QuestMarkersPayload.Marker(QuestMarkersPayload.BEACON, at.x, at.y, at.z, 0.0F, 48.0F, argb);
    }

    public static QuestMarkersPayload.Marker ring(Vec3 center, float yaw, float radius, int argb) {
        return new QuestMarkersPayload.Marker(QuestMarkersPayload.RING, center.x, center.y, center.z, yaw, radius, argb);
    }

    public static QuestMarkersPayload.Marker zone(Vec3 center, float radius, int argb) {
        return new QuestMarkersPayload.Marker(QuestMarkersPayload.ZONE, center.x, center.y, center.z, 0.0F, radius, argb);
    }

    /** Montre ces reperes a ces joueurs (seulement si cela change pour eux). */
    public static void show(List<ServerPlayer> players, List<QuestMarkersPayload.Marker> markers) {
        for (ServerPlayer player : players) {
            List<QuestMarkersPayload.Marker> before = SENT.get(player.getUUID());
            if (markers.equals(before)) {
                continue;
            }
            SENT.put(player.getUUID(), new ArrayList<>(markers));
            if (!player.isFakePlayer()) {
                PacketDistributor.sendToPlayer(player, new QuestMarkersPayload(List.copyOf(markers)));
            }
        }
    }

    /** Efface les reperes de ces joueurs. */
    public static void clear(List<ServerPlayer> players) {
        show(players, List.of());
    }

    /** Ce qu'on a montre a ce joueur (banc). */
    public static List<QuestMarkersPayload.Marker> shown(UUID player) {
        return SENT.getOrDefault(player, List.of());
    }

    static void forget(UUID player) {
        SENT.remove(player);
    }

    static void reset() {
        SENT.clear();
    }
}
