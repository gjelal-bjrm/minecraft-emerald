package com.emerald.haven;

import com.emerald.main.EmeraldWeaponsMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LE BAR DU HIP HOG EN BLOCS (cahier §103) : le QG, version Minecraft du bar de Jak 3.
 *
 * « Le vrai decor, mais en version Minecraft » (le joueur, 25 sept.). La forme du QG -- la collision du bar --
 * reste, chaque bloc prend la matiere de son role, et le bar s'y ajoute : la toiture en paliers, le carre
 * rouge et sa table ronde, les huit alcoves a l'entree ronde, le comptoir et son mur de bouteilles
 * (tools/jak_bar.py, blocs dans HipHogBlocks).
 *
 * Les donnees : haven_bar.json, ecrit d'apres le volume tel quel. Comme les cables (HavenCables), on ne
 * touche pas au volume, dont le sha1 tient le releve de l'atelier, les salles, la faune, l'invasion et la
 * ville posee de chaque monde. Chaque cellule : l'etat voulu, et l'etat du volume a cet endroit.
 *
 * QUAND. Apres chaque pose de la ville (HavenSite.done), avant le rejeu des releves -- ceux du joueur
 * passent par-dessus ; et au demarrage sur une ville deja posee, tant que l'etat de Haven n'est pas a la
 * VERSION. Une cellule ne change que si le monde y a encore l'etat du volume : ce que le joueur y a mis
 * reste. Le releve de l'atelier compare chaque cellule du bar a l'etat voulu (JakCityCapture) : sans cela,
 * le bar entier passait pour des retouches du joueur.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenBar {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);
    private static final String PATH = "/data/emeraldweapons/jak/haven_bar.json";
    /** Ce que la ville posee doit etre : le bar en blocs (HavenState.bar). */
    public static final int VERSION = 1;
    /** 2|16 : ni voisins prevenus, ni formes recalculees. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** Les cellules (du volume, en BlockPos.asLong), et la boite qui les contient : le releve n'y cherche que la. */
    private record Data(String sha1, long[] cells, BlockState[] wanted, BlockState[] volume, Map<Long, BlockState> byCell,
                        int x0, int y0, int z0, int x1, int y1, int z1) {
        static final Data NONE = new Data("", new long[0], new BlockState[0], new BlockState[0], Map.of(),
                0, 0, 0, -1, -1, -1);
    }

    private static final class Holder {
        static final Data DATA = load();
    }

    private HavenBar() {
    }

    /** Le nombre de cellules que le bar change (le banc). */
    public static int size() {
        return Holder.DATA.cells().length;
    }

    /** Les cellules du volume que le bar change (le banc). */
    public static List<BlockPos> cells() {
        List<BlockPos> out = new ArrayList<>(Holder.DATA.cells().length);
        for (long cell : Holder.DATA.cells()) {
            out.add(BlockPos.of(cell));
        }
        return out;
    }

    /** L'etat voulu du bar en cette cellule du volume, ou null si elle n'en est pas. */
    @Nullable
    public static BlockState wanted(int x, int y, int z) {
        Data data = Holder.DATA;
        if (x < data.x0() || x > data.x1() || y < data.y0() || y > data.y1() || z < data.z0() || z > data.z1()) {
            return null;
        }
        return data.byCell().get(BlockPos.asLong(x, y, z));
    }

    /** Apres la pose de la ville : le bar, et l'etat le note. */
    public static void afterPose(MinecraftServer server, ServerLevel level, HavenState state) {
        int changed = apply(level, state);
        state.setBar(VERSION);
        LOGGER.info("Haven : bar du Hip Hog pose apres la ville, {} cellules", changed);
    }

    /** Au demarrage, une ville deja posee dont le QG est encore de pierre. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        HavenState state = HavenState.get(server);
        if (!state.built() || state.bar() >= VERSION) {
            return;
        }
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return;
        }
        long start = System.nanoTime();
        int changed = apply(level, state);
        int before = state.bar();
        state.setBar(VERSION);
        LOGGER.info("Haven : bar du Hip Hog pose sur la ville deja posee (version {} -> {}), {} cellules, en {} ms",
                before, VERSION, changed, (System.nanoTime() - start) / 1_000_000L);
    }

    /**
     * Chaque cellule qui a encore l'etat du volume prend celui du bar. Les troncons sont charges au passage
     * (une vingtaine, une fois).
     *
     * @return le nombre de blocs changes
     */
    public static int apply(ServerLevel level, HavenState state) {
        Data data = Holder.DATA;
        if (data.cells().length == 0) {
            return 0;
        }
        if (!data.sha1().equals(state.sha1())) {
            LOGGER.warn("Haven : le bar est tire du volume {}, la ville posee est {} : rien de change",
                    data.sha1(), state.sha1());
            return 0;
        }
        BlockPos origin = state.origin();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int i = 0; i < data.cells().length; i++) {
            long cell = data.cells()[i];
            pos.set(origin.getX() + BlockPos.getX(cell), origin.getY() + BlockPos.getY(cell),
                    origin.getZ() + BlockPos.getZ(cell));
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            BlockState existing = level.getBlockState(pos);
            BlockState volume = data.volume()[i];
            boolean untouched = volume.isAir() ? existing.isAir() : existing == volume;
            if (!untouched || existing == data.wanted()[i]) {
                continue;                     // deja fait, ou ce que le joueur y a mis
            }
            level.setBlock(pos, data.wanted()[i], QUIET);
            changed++;
        }
        return changed;
    }

    private static Data load() {
        try (InputStream in = HavenBar.class.getResourceAsStream(PATH)) {
            if (in == null) {
                LOGGER.error("Haven : {} absent du jar, le QG reste de pierre", PATH);
                return Data.NONE;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                BlockState[] palette = states(root.getAsJsonArray("palette"));
                BlockState[] originals = states(root.getAsJsonArray("volume"));
                JsonArray array = root.getAsJsonArray("cells");
                long[] cells = new long[array.size()];
                BlockState[] wanted = new BlockState[array.size()];
                BlockState[] volume = new BlockState[array.size()];
                Map<Long, BlockState> byCell = new HashMap<>(array.size() * 2);
                int x0 = Integer.MAX_VALUE;
                int y0 = Integer.MAX_VALUE;
                int z0 = Integer.MAX_VALUE;
                int x1 = Integer.MIN_VALUE;
                int y1 = Integer.MIN_VALUE;
                int z1 = Integer.MIN_VALUE;
                for (int i = 0; i < array.size(); i++) {
                    JsonArray c = array.get(i).getAsJsonArray();
                    int x = c.get(0).getAsInt();
                    int y = c.get(1).getAsInt();
                    int z = c.get(2).getAsInt();
                    cells[i] = BlockPos.asLong(x, y, z);
                    wanted[i] = palette[c.get(3).getAsInt()];
                    volume[i] = originals[c.get(4).getAsInt()];
                    byCell.put(cells[i], wanted[i]);
                    x0 = Math.min(x0, x);
                    y0 = Math.min(y0, y);
                    z0 = Math.min(z0, z);
                    x1 = Math.max(x1, x);
                    y1 = Math.max(y1, y);
                    z1 = Math.max(z1, z);
                }
                return new Data(root.get("sha1").getAsString(), cells, wanted, volume, Map.copyOf(byCell),
                        x0, y0, z0, x1, y1, z1);
            }
        } catch (Exception e) {
            LOGGER.error("Haven : {} illisible, le QG reste de pierre", PATH, e);
            return Data.NONE;
        }
    }

    private static BlockState[] states(JsonArray array) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BlockState[] out = new BlockState[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), array.get(i).getAsString(), false)
                    .blockState();
        }
        return out;
    }
}
