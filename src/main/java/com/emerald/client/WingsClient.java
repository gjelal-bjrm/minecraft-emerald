package com.emerald.client;

import com.emerald.network.WingsSyncPayload;
import com.emerald.specialization.WingSkin;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * Ce que le client sait des ailes de chaque joueur : palier et apparence,
 * par identifiant d'entite, tels que le serveur les a envoyes.
 */
public final class WingsClient {

    private static final Map<Integer, int[]> STATE = new HashMap<>();

    private WingsClient() {
    }

    public static void accept(WingsSyncPayload payload) {
        if (payload.level() <= 0) {
            STATE.remove(payload.entityId());
        } else {
            STATE.put(payload.entityId(), new int[]{payload.level(), payload.skin()});
        }
    }

    public static int level(Entity entity) {
        int[] s = STATE.get(entity.getId());
        return s == null ? 0 : s[0];
    }

    public static WingSkin skin(Entity entity) {
        int[] s = STATE.get(entity.getId());
        return s == null ? WingSkin.PRISMATIQUES : WingSkin.byOrdinal(s[1]);
    }
}
