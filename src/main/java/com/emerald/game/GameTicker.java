package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.GameSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Fait avancer la partie et tient les clients informes.
 *
 * L'etat n'est diffuse qu'une fois par seconde : le chronometre s'affiche a la
 * seconde, un envoi par tick serait vingt fois trop bavard pour rien.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class GameTicker {

    private static final int SYNC_INTERVAL = 20;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(
                net.minecraft.world.level.Level.OVERWORLD)) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.status() == GameState.Status.RUNNING && state.remaining(level) <= 0L) {
            state.finish(false);
        }
        if (level.getGameTime() % SYNC_INTERVAL != 0) {
            return;
        }
        // LES ANCRES NE SE MONTRENT QU'UNE FOIS LE VILLAGE TENU.
        //
        // Leurs positions sont choisies des la mise en place, parce qu'il faut
        // savoir ou l'on batira. Mais les envoyer tout de suite les affichait
        // dans l'interface pendant le prologue : on connaissait les trois
        // objectifs avant d'avoir gagne le droit de les connaitre, et la
        // revelation qui suit la defense du village ne revelait plus rien.
        java.util.List<net.minecraft.core.BlockPos> anchors =
                state.status() == GameState.Status.RUNNING
                        ? state.anchors() : java.util.List.of();
        int held = 0;
        java.util.List<Long> packed = new java.util.ArrayList<>();
        for (int i = 0; i < anchors.size(); i++) {
            packed.add(anchors.get(i).asLong());
            if (state.isActivated(anchors.get(i))) {
                held |= 1 << i;
            }
        }
        GameSyncPayload payload = new GameSyncPayload(
                state.status().ordinal(),
                state.remaining(level),
                state.phase(level).ordinal(),
                state.anchorsActive(),
                packed, held);
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
