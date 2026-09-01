package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le registre de construction : QUI a pose ce bloc, OU dans la structure, et
 * ce que le joueur en a fait depuis.
 *
 * Ce fichier existe a cause d'un echec repete. Sept fois de suite on a corrige
 * le mauvais escalier : les marches du parvis, celles des rampes, celles des
 * tours et celles de la volee sortent du meme materiau et se ressemblent sur
 * une capture. Le joueur pouvait entourer le defaut en rouge sans que cela
 * designe une ligne de code ; moi lire le code sans savoir a quoi il
 * correspondait a l'ecran. Il manquait le pont entre les deux, et sept
 * allers-retours ont ete depenses a le contourner.
 *
 * Le sanctuaire etant DETERMINISTE, ce pont est simple a batir. On note, a la
 * construction, le nom de la routine qui pose chaque bloc, l'etat pose, et la
 * position RELATIVE au centre du sanctuaire -- jamais a la carte, qui ne veut
 * rien dire d'une partie a l'autre.
 *
 * Trois usages en decoulent :
 *   - INSPECTER : on vise, on lit le nom du chantier et l'adresse ;
 *   - DESIGNER : on clique droit, et l'on constitue une liste de blocs a
 *     montrer, ce qu'une capture ne saura jamais faire sans ambiguite ;
 *   - RELEVER : on compare le monde au registre, et l'on obtient la liste des
 *     corrections faites a la main -- « ici tu avais pose X, j'ai mis Y ».
 *     C'est la forme la plus utile, parce qu'elle transporte l'INTENTION et
 *     pas seulement la plainte.
 *
 * Le registre est volatil, et c'est assez : il se remplit a chaque
 * /arcencium sanctuary, c'est-a-dire quand on est en train de regarder ce
 * qu'on vient de batir.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class SanctuaryLedger {

    /** Ou en est la construction. Chaque routine l'annonce en entrant. */
    private static String part = "?";

    private record Mark(String part, int dx, int dy, int dz, BlockState state) {
    }

    private static final Map<BlockPos, Mark> ledger = new HashMap<>();
    private static BlockPos origin = BlockPos.ZERO;

    private static final Map<UUID, List<BlockPos>> picks = new HashMap<>();
    /** La derniere ligne envoyee a chacun, pour n'emettre que sur changement. */
    private static final Map<UUID, com.emerald.network.ProbeInfoPayload> lastSent =
            new HashMap<>();

    private static final int LOOK_RANGE = 24;
    private static final int EVERY = 4;
    /** Au-dela, une liste de differences cesse d'etre lisible. */
    private static final int DIFF_MAX = 60;

    private SanctuaryLedger() {
    }

    // -------------------------------------------------------------- ecriture

    /** Ouvre un chantier : tout ce qui suit sera impute a ce nom. */
    public static void part(String name) {
        part = name;
    }

    /** Repart de zero, et fixe le repere du sanctuaire qu'on va batir. */
    public static void begin(BlockPos centre) {
        ledger.clear();
        origin = centre.immutable();
        part = "?";
    }

    /** Note un bloc pose, avec son chantier et son etat. */
    public static void record(int x, int y, int z, BlockState state) {
        ledger.put(new BlockPos(x, y, z), new Mark(part,
                x - origin.getX(), y - origin.getY(), z - origin.getZ(), state));
    }

    // --------------------------------------------------------------- lecture

    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    /** Ce qu'on sait de ce bloc, ou null si le sanctuaire ne l'a pas pose. */
    public static String describe(BlockPos pos) {
        Mark mark = ledger.get(pos);
        if (mark == null) {
            return null;
        }
        return String.format("%s  |  cx%+d y%+d cz%+d  |  %s", mark.part(),
                mark.dx(), mark.dy(), mark.dz(), name(mark.state()));
    }

    public static boolean empty() {
        return ledger.isEmpty();
    }

    // ---------------------------------------------------------------- le mode

    /**
     * Retient un bloc, ou le retire s'il l'etait deja.
     *
     * Il n'y a plus de mode a activer : c'est la Sonde tenue en main qui fait
     * office d'interrupteur. Un mode qu'on allume par une commande s'oublie
     * allume, et l'on finit par designer des blocs en croyant en poser.
     *
     * @return vrai si le bloc vient d'etre retenu
     */
    public static boolean pick(ServerPlayer player, BlockPos pos) {
        List<BlockPos> list = picks.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        if (list.remove(pos)) {
            return false;
        }
        list.add(pos.immutable());
        return true;
    }

    public static void clearPicks(ServerPlayer player) {
        picks.remove(player.getUUID());
    }

    /** La selection du joueur, mise en forme. */
    public static List<String> selection(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (BlockPos pos : picks.getOrDefault(player.getUUID(), List.of())) {
            String known = describe(pos);
            out.add(known != null ? known
                    : String.format("hors sanctuaire  |  %d,%d,%d",
                            pos.getX(), pos.getY(), pos.getZ()));
        }
        return out;
    }

    /**
     * Ce que voit la Sonde, envoye a qui la tient.
     *
     * On n'emet que sur CHANGEMENT : la ligne est identique tant qu'on fixe le
     * meme bloc, et renvoyer quatre chaines quatre fois par seconde a chaque
     * joueur pour ne rien dire de neuf serait payer cher un panneau qui ne
     * bouge pas.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || level.getGameTime() % EVERY != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!holdsProbe(player)) {
                if (lastSent.remove(player.getUUID()) != null) {
                    send(player, new com.emerald.network.ProbeInfoPayload("", "", "", ""));
                }
                continue;
            }
            com.emerald.network.ProbeInfoPayload info = look(level, player);
            if (!info.equals(lastSent.get(player.getUUID()))) {
                lastSent.put(player.getUUID(), info);
                send(player, info);
            }
        }
    }

    private static boolean holdsProbe(ServerPlayer player) {
        var probe = com.emerald.item.ModItems.SANCTUARY_PROBE.get();
        return player.getMainHandItem().is(probe) || player.getOffhandItem().is(probe);
    }

    private static void send(ServerPlayer player, com.emerald.network.ProbeInfoPayload info) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, info);
    }

    private static com.emerald.network.ProbeInfoPayload look(ServerLevel level,
                                                             ServerPlayer player) {
        if (!(player.pick(LOOK_RANGE, 0.0F, false) instanceof BlockHitResult hit)) {
            return new com.emerald.network.ProbeInfoPayload("--", "", "", "");
        }
        BlockPos at = hit.getBlockPos();
        BlockState state = level.getBlockState(at);
        String world = String.format("%d, %d, %d", at.getX(), at.getY(), at.getZ());
        Mark mark = ledger.get(at);
        if (mark == null) {
            return new com.emerald.network.ProbeInfoPayload(
                    name(state), world, "hors sanctuaire", "");
        }
        return new com.emerald.network.ProbeInfoPayload(name(state), world, mark.part(),
                String.format("cx%+d  y%+d  cz%+d", mark.dx(), mark.dy(), mark.dz()));
    }

    // ------------------------------------------------------------ le releve

    /**
     * Ce que le joueur a change depuis la construction.
     *
     * C'est l'outil qui vaut tous les autres : plutot que de decrire un defaut
     * et d'esperer que je comprenne, on corrige a la main et l'on releve. Le
     * resultat porte l'intention -- « ici il y avait une marche, tu l'as
     * enlevee » -- et se traduit directement en modification du code, sans
     * passer par une interpretation.
     *
     * On regroupe par chantier : c'est le niveau auquel le code est ecrit, et
     * voir trente lignes toutes marquees « summitStair » dit plus que trente
     * coordonnees eparses.
     */
    public static List<String> diff(ServerLevel level) {
        Map<String, List<String>> byPart = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<BlockPos, Mark> entry : ledger.entrySet()) {
            Mark mark = entry.getValue();
            BlockState now = level.getBlockState(entry.getKey());
            if (now.getBlock() == mark.state().getBlock()) {
                continue;
            }
            total++;
            byPart.computeIfAbsent(mark.part(), k -> new ArrayList<>())
                    .add(String.format("  cx%+d y%+d cz%+d : %s -> %s",
                            mark.dx(), mark.dy(), mark.dz(),
                            name(mark.state()), name(now)));
        }
        List<String> out = new ArrayList<>();
        if (total == 0) {
            out.add("Aucune correction relevee.");
            return out;
        }
        out.add(String.format("%d bloc(s) changes depuis la construction :", total));
        int shown = 0;
        for (Map.Entry<String, List<String>> group : byPart.entrySet()) {
            out.add(String.format("[%s] %d", group.getKey(), group.getValue().size()));
            for (String line : group.getValue()) {
                if (shown++ >= DIFF_MAX) {
                    out.add(String.format("  ... et %d de plus", total - shown + 1));
                    return out;
                }
                out.add(line);
            }
        }
        return out;
    }
}
