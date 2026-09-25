package com.emerald.jak.board;

import com.emerald.haven.Haven;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.init.Jak3Registry;
import com.emerald.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * Le JET-Board, cote serveur (cahier §100) : qui l'a -- l'achat chez Tess, dans HavenProgress --,
 * s'il est dans sa case, et la touche qui le sort sous les pieds et le range (JetBoardTogglePayload).
 * Il ne sort que dans Haven : c'est la ville de Jak, ses rails et son port.
 */
public final class JetBoard {

    /** La touche (JetBoardClient) ; son nom sert aussi aux textes, resolu chez le client. */
    public static final String KEY_NAME = "key.emeraldweapons.jet_board";
    /** La cle de l'achat dans HavenProgress, et l'article de la boutique de Tess. */
    public static final String OWNED = "jetboard";
    /** Sa case Curios (data/emeraldweapons/curios/slots/jet_board.json). */
    public static final String SLOT = "jet_board";
    /** Choix du joueur : le prix de la derniere arme. */
    public static final int PRICE = 200;
    /** Le bleu de ses propulseurs, pour la boutique. */
    public static final int COLOR = 0xFF6FD3FF;

    private JetBoard() {
    }

    public static boolean owns(UUID id) {
        return HavenProgress.bonus(id, OWNED) > 0;
    }

    /** La planche est dans sa case -- sans Curios, n'importe ou dans le sac. */
    public static boolean equipped(Player player) {
        if (ModList.get().isLoaded("curios")) {
            return JetBoardCurios.equipped(player);
        }
        return player.getInventory().contains(stack -> stack.is(ModItems.JET_BOARD.get()));
    }

    /** La touche : sortir la planche sous les pieds, ou la ranger. */
    public static void toggle(ServerPlayer player) {
        if (player.getVehicle() instanceof JetBoardEntity board) {
            player.stopRiding();
            board.discard();
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 0.4F, 1.8F);
            return;
        }
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }
        String refusal = !Haven.is(player.level()) ? "hors_haven"
                : !owns(player.getUUID()) ? "pas_achete"
                : !equipped(player) ? "pas_equipe"
                : player.isPassenger() ? "deja_monte" : null;
        if (refusal != null) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.jetboard." + refusal)
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        JetBoardEntity board = Jak3Registry.JET_BOARD.get().create(level);
        if (board == null) {
            return;
        }
        board.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        // l'elan du joueur : on la sort en sautant d'un toit, et elle part avec lui
        board.setDeltaMovement(player.getDeltaMovement());
        level.addFreshEntity(board);
        if (!player.startRiding(board, true)) {
            board.discard();
            return;
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 0.4F, 1.8F);
        player.displayClientMessage(Component.translatable("game.emeraldweapons.jetboard.sortie",
                Component.keybind(KEY_NAME)).withStyle(ChatFormatting.AQUA), true);
    }
}
