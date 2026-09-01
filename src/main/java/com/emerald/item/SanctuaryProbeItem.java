package com.emerald.item;

import com.emerald.game.SanctuaryLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Sonde du Sanctuaire : l'outil de designation.
 *
 * Il remplace trois commandes, et c'est le point : taper une commande par bloc
 * convient pour verifier un point, pas pour parcourir une construction. Ce qui
 * se regarde doit se lire tout seul, et ce qui se designe doit se cliquer.
 *
 * Tant qu'on la tient, un panneau affiche ce que l'on vise : le bloc, sa
 * position dans le monde, le chantier qui l'a pose et son adresse dans la
 * structure. Voir {@link com.emerald.client.ProbeHudClient}.
 *
 * Clic droit sur un bloc          : le retenir, ou le retirer s'il l'etait.
 * Accroupi + clic droit sur bloc  : vider la selection.
 * Clic droit dans le vide         : RELEVER les corrections faites a la main.
 * Accroupi + clic droit dans vide : relire la selection.
 *
 * Le relevé est ce qui compte le plus. Plutot que de decrire un defaut et
 * d'esperer qu'il soit compris, on corrige la construction a la main et l'on
 * releve : « ici il y avait une marche, tu l'as enlevee ». Cela porte
 * l'intention, et non la plainte.
 */
public class SanctuaryProbeItem extends Item {

    public SanctuaryProbeItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------ sur un bloc

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;       // le client ne decide rien
        }
        if (player.isShiftKeyDown()) {
            SanctuaryLedger.clearPicks(player);
            say(player, "Selection videe.", ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }
        boolean kept = SanctuaryLedger.pick(player, context.getClickedPos());
        String known = SanctuaryLedger.describe(context.getClickedPos());
        say(player, String.format("%s [%d]  %s", kept ? "retenu" : "retire",
                        SanctuaryLedger.selection(player).size(),
                        known != null ? known : "hors sanctuaire"),
                kept ? ChatFormatting.AQUA : ChatFormatting.GRAY);
        chime(player, kept);
        return InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------- dans le vide

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player who,
                                                  InteractionHand hand) {
        ItemStack stack = who.getItemInHand(hand);
        if (!(who instanceof ServerPlayer player) || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            List<String> lines = SanctuaryLedger.selection(player);
            if (lines.isEmpty()) {
                say(player, "Rien de retenu.", ChatFormatting.GRAY);
            } else {
                say(player, lines.size() + " bloc(s) retenus :", ChatFormatting.AQUA);
                lines.forEach(line -> say(player, "  " + line, ChatFormatting.WHITE));
            }
            return InteractionResultHolder.success(stack);
        }
        if (SanctuaryLedger.empty()) {
            say(player, "Registre vide : rebatis avec /arcencium sanctuary, "
                    + "corrige a la main, puis reviens.", ChatFormatting.RED);
            return InteractionResultHolder.success(stack);
        }
        SanctuaryLedger.diff(server).forEach(line -> say(player, line, ChatFormatting.WHITE));
        return InteractionResultHolder.success(stack);
    }

    private static void say(ServerPlayer player, String text, ChatFormatting colour) {
        player.sendSystemMessage(Component.literal(text).withStyle(colour));
    }

    /** Un retour sonore : on doit savoir qu'on a clique sans lire le texte. */
    private static void chime(ServerPlayer player, boolean kept) {
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.5F, kept ? 1.6F : 0.9F);
    }
}
