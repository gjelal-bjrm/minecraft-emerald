package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

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
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
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

    /** Le delai avant l'indice, et sa duree. */
    private static final int HINT_AFTER = 90 * 20;
    private static final int HINT_TICKS = 20 * 20;

    /** La borne d'un indice passif, et la colonne d'une revelation demandee. */
    private static final int HINT_HEIGHT = 6;
    private static final int REVEAL_HEIGHT = 30;
    private static final int REVEAL_TICKS = 15 * 20;

    /**
     * Au bout de sept minutes, les sceaux se laissent entrevoir.
     *
     * Un coup de main, pas une reponse : ils s'allument vingt secondes, en
     * bornes courtes visibles a travers la pierre, ce qui dit « fouille par
     * la » sans dire « c'est ce bloc-ci ». Une enigme qui bloque cesse d'etre
     * une enigme et devient un mur, et sept minutes suffisent a savoir qu'on
     * tourne en rond.
     *
     * L'indice ne part que pour les sceaux ENCORE endormis, et seulement aupres
     * de qui est dans l'enceinte : le montrer de l'exterieur reviendrait a
     * poser un panneau sur la carte.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || vaults.isEmpty()
                || level.getGameTime() % HINT_AFTER != 0) {
            return;
        }
        for (Vault vault : vaults) {
            if (vault.lit().size() >= vault.seals().size()) {
                continue;                      // ce tombeau est ouvert
            }
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(vault.anchor().getX(), player.getY(),
                        vault.anchor().getZ()) > 120.0 * 120.0) {
                    continue;
                }
                for (BlockPos seal : vault.seals()) {
                    if (vault.lit().contains(seal)) {
                        continue;
                    }
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new com.emerald.network.AnchorPulsePayload(
                                    seal.getX(), seal.getY(), seal.getZ(), HINT_TICKS, HINT_HEIGHT));
                }
                player.displayClientMessage(Component.translatable(
                                "game.emeraldweapons.seal.hint")
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
            }
        }
    }

    /**
     * Montre les sceaux encore endormis de cette ancre, franchement.
     *
     * L'indice passif attendait qu'on se lasse ; celui-ci repond a un geste.
     * Quiconque touche l'ancre a prouve qu'il cherche, et lui cacher la suite
     * ne fait plus durer l'enigme -- cela fait errer. La colonne est donc haute
     * et non plus une borne : elle sort du monument, ce qu'il faut bien pour
     * s'orienter dans une masse de quarante blocs de haut.
     *
     * Ce n'est pas un cadeau : il faut toujours entrer, descendre et fouiller.
     * On sait seulement vers ou.
     */
    public static void reveal(ServerLevel level, BlockPos anchor, Player player) {
        if (!(player instanceof ServerPlayer served)) {
            return;
        }
        for (Vault vault : vaults) {
            if (!vault.anchor().equals(anchor)) {
                continue;
            }
            for (BlockPos seal : vault.seals()) {
                if (vault.lit().contains(seal)) {
                    continue;
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(served,
                        new com.emerald.network.AnchorPulsePayload(
                                seal.getX(), seal.getY(), seal.getZ(),
                                REVEAL_TICKS, REVEAL_HEIGHT));
            }
            return;
        }
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
