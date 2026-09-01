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

    /**
     * L'instantane du site, pris une fois la construction finie.
     *
     * Le registre seul ne suffisait pas : il ne connait que les cases OU L'ON
     * A POSE quelque chose, si bien qu'un bloc ajoute a la main dans le vide,
     * ou un chemin trace en terrain vierge, restait invisible au releve. Or
     * ajouter est justement la facon la plus claire de montrer ce qu'on veut.
     *
     * On garde donc tout le volume, en palette et tableau de courts plutot
     * qu'en carte de positions : deux cent mille fois seize octets tiendraient
     * mal en memoire, la ou trois millions de courts font six megaoctets.
     */
    private static short[] snapshot;
    private static final List<BlockState> palette = new ArrayList<>();
    private static final Map<BlockState, Short> paletteIndex = new HashMap<>();
    private static BlockPos low = BlockPos.ZERO;
    private static int spanX;
    private static int spanY;
    private static int spanZ;

    private static final Map<UUID, List<BlockPos>> picks = new HashMap<>();
    /** La derniere ligne envoyee a chacun, pour n'emettre que sur changement. */
    private static final Map<UUID, com.emerald.network.ProbeInfoPayload> lastSent =
            new HashMap<>();

    private static final int LOOK_RANGE = 24;
    private static final int EVERY = 4;

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

    /**
     * Fige l'etat du site apres construction.
     *
     * On prend TOUT le volume, y compris ce que le sanctuaire n'a pas touche :
     * c'est la seule facon de voir ensuite un ajout, et un ajout vaut mieux
     * qu'une description.
     */
    public static void capture(ServerLevel level, BlockPos centre, int half,
                               int down, int up) {
        low = centre.offset(-half, -down, -half);
        spanX = half * 2 + 1;
        spanY = down + up + 1;
        spanZ = half * 2 + 1;
        snapshot = new short[spanX * spanY * spanZ];
        palette.clear();
        paletteIndex.clear();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < spanY; dy++) {
            for (int dz = 0; dz < spanZ; dz++) {
                for (int dx = 0; dx < spanX; dx++) {
                    at.set(low.getX() + dx, low.getY() + dy, low.getZ() + dz);
                    snapshot[index(dx, dy, dz)] = intern(level.getBlockState(at));
                }
            }
        }
    }

    private static int index(int dx, int dy, int dz) {
        return (dy * spanZ + dz) * spanX + dx;
    }

    private static short intern(BlockState state) {
        Short known = paletteIndex.get(state);
        if (known != null) {
            return known;
        }
        short id = (short) palette.size();
        palette.add(state);
        paletteIndex.put(state, id);
        return id;
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
     * et d'esperer qu'il soit compris, on corrige a la main et l'on releve. Le
     * resultat porte l'INTENTION -- « ici il y avait une marche, tu l'as
     * enlevee », « la tu as pose un chemin qui n'existait pas » -- et se
     * traduit en modification du code sans passer par une interpretation.
     *
     * On balaie TOUT LE VOLUME et non les seules cases posees : un chemin
     * ajoute en terrain vierge n'appartient a aucun chantier, et c'est
     * pourtant la facon la plus claire de montrer ce qu'on veut.
     *
     * On regroupe par chantier, qui est le niveau auquel le code est ecrit :
     * trente lignes toutes marquees « summitStair » disent plus que trente
     * coordonnees eparses. Ce qui n'a pas ete pose par nous tombe sous
     * « ajout », et c'est precisement la que se trouvent les propositions.
     */
    public static List<String> diff(ServerLevel level) {
        List<String> out = new ArrayList<>();
        if (snapshot == null) {
            out.add("Aucun instantane : rebatis avec /arcencium sanctuary.");
            return out;
        }
        Map<String, List<String>> byPart = new LinkedHashMap<>();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int total = 0;
        for (int dy = 0; dy < spanY; dy++) {
            for (int dz = 0; dz < spanZ; dz++) {
                for (int dx = 0; dx < spanX; dx++) {
                    BlockState was = palette.get(snapshot[index(dx, dy, dz)]);
                    at.set(low.getX() + dx, low.getY() + dy, low.getZ() + dz);
                    BlockState now = level.getBlockState(at);
                    if (now.getBlock() == was.getBlock()) {
                        continue;
                    }
                    total++;
                    Mark mark = ledger.get(at.immutable());
                    String part = mark != null ? mark.part()
                            : (was.isAir() ? "ajout" : "terrain");
                    byPart.computeIfAbsent(part, k -> new ArrayList<>())
                            .add(String.format("  cx%+d y%+d cz%+d : %s -> %s",
                                    at.getX() - origin.getX(),
                                    at.getY() - origin.getY(),
                                    at.getZ() - origin.getZ(),
                                    name(was), name(now)));
                }
            }
        }
        if (total == 0) {
            out.add("Aucune correction relevee.");
            return out;
        }
        out.add(String.format("%d bloc(s) changes depuis la construction :", total));
        for (Map.Entry<String, List<String>> group : byPart.entrySet()) {
            out.add(String.format("[%s] %d", group.getKey(), group.getValue().size()));
            out.addAll(group.getValue());
        }
        return out;
    }

    /**
     * Ecrit le releve dans un fichier plutot que dans le tchat.
     *
     * Un chemin de cent blocs ne tient pas en huit lignes de tchat, et une
     * capture d'ecran ne se lit pas. Le fichier, lui, est sur le disque : il
     * se lit en entier, sans transcription et sans perte.
     */
    public static java.nio.file.Path writeReport(ServerLevel level, List<String> lines) {
        java.nio.file.Path dest = level.getServer().getServerDirectory()
                .resolve("arcencium_diff.txt");
        try {
            java.nio.file.Files.write(dest, lines);
        } catch (java.io.IOException failed) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .warn("Releve non ecrit : {}", failed.toString());
            return null;
        }
        return dest;
    }
}
