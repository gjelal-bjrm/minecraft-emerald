package com.emerald.game;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Les Sceaux du Tombeau : ce qui oblige a entrer dans la pyramide.
 *
 * Le probleme etait de conception, pas de construction. L'escalier exterieur
 * mene au sommet, ce qui est bien -- on voit ou l'on va et l'on y monte -- mais
 * il rendait l'interieur facultatif : on prenait l'ancre sans jamais descendre,
 * et tout le tombeau ne servait plus a rien.
 *
 * Y mettre du butin ne suffisait pas : un tresor facultatif reste facultatif.
 * Il fallait mettre l'interieur sur le chemin de l'objectif. Trois sceaux
 * dorment donc dans le tombeau, et l'ancre refuse de s'allumer tant qu'ils ne
 * sont pas tous eveilles.
 *
 * L'ordre de la partie s'en trouve dit tout seul, sans un mot d'explication :
 * on entre, on eveille, on ressort, on monte, on tient. Personne n'a besoin
 * qu'on lui donne la marche a suivre -- une ancre qui refuse en donnant le
 * compte des sceaux l'enseigne en une fois.
 */
public final class SanctuarySeals {

    /** Nombre de sceaux par sanctuaire. Trois : assez pour fouiller, pas pour lasser. */
    public static final int PER_SANCTUARY = 3;

    private record Vault(BlockPos anchor, List<BlockPos> seals, Set<BlockPos> lit) {
    }

    /** Volatil, comme les sieges et la brume : cela se rebatit, cela ne se sauve pas. */
    private static final List<Vault> vaults = new ArrayList<>();

    private SanctuarySeals() {
    }

    public static void register(BlockPos anchor, List<BlockPos> seals) {
        vaults.add(new Vault(anchor.immutable(),
                seals.stream().map(BlockPos::immutable).toList(), new HashSet<>()));
    }

    public static void clearAll() {
        vaults.clear();
    }

    /**
     * Eveille un sceau, et dit ou l'on en est.
     *
     * @return vrai si celui-ci vient de s'eveiller
     */
    public static boolean light(ServerLevel level, BlockPos pos, Player player) {
        for (Vault vault : vaults) {
            if (!vault.seals().contains(pos) || !vault.lit().add(pos)) {
                continue;
            }
            int lit = vault.lit().size();
            player.displayClientMessage(Component.translatable(
                            "game.emeraldweapons.seal.lit", lit, PER_SANCTUARY)
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
            if (lit >= vault.seals().size()) {
                // l'annonce vaut recompense : on a fini de fouiller
                GameManager.announce(level,
                        Component.translatable("game.emeraldweapons.seal.done")
                                .withStyle(style -> style.withColor(0x9CE8FF)),
                        Component.translatable("game.emeraldweapons.seal.done.sub")
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            return true;
        }
        return false;
    }

    /** Combien de sceaux restent a eveiller pour cette ancre, ou zero. */
    public static int remaining(BlockPos anchor) {
        for (Vault vault : vaults) {
            if (vault.anchor().equals(anchor)) {
                return vault.seals().size() - vault.lit().size();
            }
        }
        // aucune inscription : c'est une ancre sans tombeau, elle ne doit rien
        return 0;
    }
}
