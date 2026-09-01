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
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Set<UUID> watchers = new HashSet<>();
    private static final Set<UUID> picking = new HashSet<>();
    private static final Map<UUID, List<BlockPos>> picks = new HashMap<>();

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

    /** @return vrai si l'inspection vient de s'allumer */
    public static boolean toggleWatch(ServerPlayer player) {
        if (!watchers.remove(player.getUUID())) {
            watchers.add(player.getUUID());
            return true;
        }
        return false;
    }

    /** @return vrai si la selection vient de s'allumer */
    public static boolean togglePick(ServerPlayer player) {
        if (!picking.remove(player.getUUID())) {
            picking.add(player.getUUID());
            picks.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
            return true;
        }
        return false;
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
     * Le mode inspection : ce qu'on regarde s'affiche tout seul.
     *
     * Une commande a taper par bloc suffit pour verifier un point, pas pour en
     * parcourir vingt. En continu, on longe la construction et l'on voit les
     * noms defiler -- c'est ainsi qu'on trouve la frontiere entre deux
     * routines, qui est presque toujours l'endroit du defaut.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (watchers.isEmpty() || !(event.getLevel() instanceof ServerLevel level)
                || level.getGameTime() % EVERY != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!watchers.contains(player.getUUID())
                    || !(player.pick(LOOK_RANGE, 0.0F, false) instanceof BlockHitResult hit)) {
                continue;
            }
            BlockPos at = hit.getBlockPos();
            String known = describe(at);
            player.displayClientMessage(Component.literal(known != null ? known
                    : name(level.getBlockState(at)) + "  |  hors sanctuaire"), true);
        }
    }

    /**
     * La selection au clic droit.
     *
     * On annule l'interaction tant que le mode est actif : sans cela, designer
     * un bloc poserait un bloc, ouvrirait un coffre ou activerait un sceau --
     * et l'on abimerait ce qu'on essaie justement de montrer.
     */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !picking.contains(player.getUUID())) {
            return;
        }
        event.setCanceled(true);
        BlockPos at = event.getPos();
        List<BlockPos> list = picks.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        boolean removed = list.remove(at);
        if (!removed) {
            list.add(at.immutable());
        }
        String known = describe(at);
        player.displayClientMessage(Component.literal(String.format("%s [%d] %s",
                removed ? "retire" : "retenu", list.size(),
                known != null ? known : "hors sanctuaire")), true);
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
