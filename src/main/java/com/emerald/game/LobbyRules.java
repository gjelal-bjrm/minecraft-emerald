package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ce qui est interdit tant que la Lame du Serment n'est pas retiree.
 *
 * Le but n'est pas de brider le joueur mais de garantir que TOUT LE MONDE est
 * present et au meme endroit quand la partie commence. Sans cela, un joueur
 * parti explorer manquerait l'annonce, et un joueur arrive en retard trouverait
 * une partie deja lancee sans savoir ou aller.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class LobbyRules {

    /** Rayon dans lequel les joueurs sont tenus avant le depart. */
    public static final int LOBBY_RADIUS = 48;

    /** Intervalle du rappel a l'ecran quand la lame reste plantee. */
    private static final int REMINDER = 60 * 20;

    private static boolean waiting(ServerLevel level) {
        GameState.Status status = GameState.get(level).status();
        return status == GameState.Status.LOBBY || status == GameState.Status.PROLOGUE;
    }

    /** Aucun minage avant le depart : le village doit rester intact pour le siege. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !waiting(level)) {
            return;
        }
        if (GameManager.prologueRunning()) {
            return;                        // pendant le siege, on laisse se defendre
        }
        event.setCanceled(true);
        if (event.getPlayer() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(
                    "game.emeraldweapons.locked.mine").withStyle(ChatFormatting.RED), true);
        }
    }

    /**
     * La barriere de village, et le rappel periodique.
     *
     * On repousse vers le centre plutot que de teleporter : un joueur repousse
     * comprend qu'il y a un mur, un joueur teleporte croit a un bogue.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level) || !waiting(level)) {
            return;
        }
        GameState state = GameState.get(level);
        BlockPos village = state.village();
        if (village.equals(BlockPos.ZERO)) {
            return;
        }
        if (player.blockPosition().distSqr(village) > (double) LOBBY_RADIUS * LOBBY_RADIUS) {
            Vec3 back = Vec3.atCenterOf(village).subtract(player.position()).normalize().scale(0.55);
            player.setDeltaMovement(back.x, 0.25, back.z);
            player.hurtMarked = true;
            player.displayClientMessage(Component.translatable(
                    "game.emeraldweapons.locked.leave").withStyle(ChatFormatting.RED), true);
        }
        if (!GameManager.prologueRunning() && level.getGameTime() % REMINDER == 0) {
            player.displayClientMessage(Component.translatable(
                    "game.emeraldweapons.locked.hint").withStyle(ChatFormatting.YELLOW), true);
        }
    }

    /** A la reapparition, on renvoie vers l'ancre tenue la plus proche. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.status() == GameState.Status.LOBBY) {
            return;
        }
        BlockPos home = state.respawnFor(player.blockPosition());
        if (!home.equals(BlockPos.ZERO)) {
            player.teleportTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5);
        }
    }
}
