package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /**
     * Rayon dans lequel les joueurs sont tenus avant le depart.
     *
     * Large : il s'agit d'empecher de partir explorer, pas d'interdire de faire
     * le tour du village.
     */
    public static final int LOBBY_RADIUS = 96;

    /** Intervalle du rappel a l'ecran quand la lame reste plantee. */
    private static final int REMINDER = 60 * 20;

    private static boolean waiting(ServerLevel level) {
        GameState.Status status = GameState.get(level).status();
        return status == GameState.Status.LOBBY || status == GameState.Status.PROLOGUE;
    }

    /**
     * La barriere ne vaut que TANT QUE LA LAME EST PLANTEE.
     *
     * Pendant le siege, les monstres apparaissent jusqu'a 26 blocs et se
     * poursuivent : enfermer les defenseurs les empecherait de se battre.
     */
    private static boolean confined(ServerLevel level) {
        return waiting(level) && !GameManager.prologueRunning();
    }

    /** Aucun minage avant le depart : le village doit rester intact pour le siege. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !confined(level)) {
            return;
        }
        // l'echafaudage qu'on a pose reste recuperable : autrement une erreur de
        // placement serait definitive, et le village finirait couvert de poteaux
        if (event.getState().is(net.minecraft.world.level.block.Blocks.SCAFFOLDING)) {
            return;
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
     * On TELEPORTE au bord plutot que de repousser par la vitesse. Une premiere
     * version imposait une velocite a chaque tick, composante verticale
     * comprise : le joueur montait de 0,25 par tick sans jamais reprendre la
     * main, puisque sa vitesse etait ecrasee vingt fois par seconde. Une
     * teleportation ne peut pas boucler.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level) || !confined(level)) {
            return;
        }
        // un controle par demi-seconde suffit, et evite toute lutte avec le client
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        GameState state = GameState.get(level);
        BlockPos village = state.village();
        if (village.equals(BlockPos.ZERO)) {
            return;
        }
        double dx = player.getX() - (village.getX() + 0.5);
        double dz = player.getZ() - (village.getZ() + 0.5);
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > LOBBY_RADIUS) {
            // on le ramene juste en deca du bord, dans la direction d'ou il vient
            double scale = (LOBBY_RADIUS - 4) / dist;
            BlockPos edge = WorldSetup.findOpenGround(level, new BlockPos(
                    (int) Math.round(village.getX() + dx * scale), village.getY(),
                    (int) Math.round(village.getZ() + dz * scale)), 8);
            player.teleportTo(edge.getX() + 0.5, edge.getY(), edge.getZ() + 0.5);
            player.displayClientMessage(Component.translatable(
                    "game.emeraldweapons.locked.leave").withStyle(ChatFormatting.RED), true);
        }
        if (level.getGameTime() % REMINDER == 0) {
            // les coordonnees des la premiere relance : un joueur qui cherche
            // depuis une minute a besoin d'une direction, pas d'un rappel
            player.displayClientMessage(Component.translatable(
                    "game.emeraldweapons.locked.where", village.getX(), village.getY(),
                    village.getZ(), (int) Math.sqrt(player.blockPosition().distSqr(village)))
                    .withStyle(ChatFormatting.YELLOW), false);
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
        if (home.equals(BlockPos.ZERO)) {
            return;
        }
        // l'ancre comme la lame sont des blocs pleins : on reapparait a cote,
        // sur un appui verifie, sinon on ressuscite dans la pierre
        BlockPos stand = WorldSetup.findOpenGround(level, home.offset(2, 0, 2), 12);
        player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
    }
}
