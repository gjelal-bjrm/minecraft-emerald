package com.emerald.jak;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.InflaterInputStream;

/**
 * Un quartier de Jak 3 converti en blocs, tel que le lit le mod.
 *
 * Le format vient de {@code tools/jak_voxelize.py} et lui est propre. On n'a
 * pas pris les structures du jeu : elles plafonnent a quarante-huit blocs de
 * cote, et le port de Haven en aurait demande un millier de fichiers. Ici, tout
 * un quartier tient dans un seul fichier de trois cents kilo-octets, parce que
 * la grille est presque entierement vide et que les plages d'air se comptent
 * par milliers de cellules.
 *
 * Ce qui est repris est la FORME, issue de la geometrie de collision. Les
 * materiaux sont les notres : le quartier doit appartenir au meme monde que le
 * reste du mode, et un habillage emprunte n'aurait ni ce merite ni la meme
 * clarte juridique.
 */
public final class JakVolume {

    /** Les huit premiers octets disent ce qu'on lit ; sinon on refuse. */
    private static final int MAGIC = 0x4A414B56;          // "JAKV"

    private final int width;
    private final int height;
    private final int depth;
    private final BlockState[] palette;

    /** Les plages, aplaties : index de palette puis longueur, en alternance. */
    private final int[] runs;

    private JakVolume(int width, int height, int depth, BlockState[] palette, int[] runs) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.palette = palette;
        this.runs = runs;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int depth() {
        return this.depth;
    }

    public int runCount() {
        return this.runs.length / 2;
    }

    public BlockState state(int index) {
        return this.palette[index];
    }

    public int runBlock(int run) {
        return this.runs[run * 2];
    }

    public int runLength(int run) {
        return this.runs[run * 2 + 1];
    }

    /** Le nombre total de cellules : sert a verifier que le fichier est entier. */
    public long cells() {
        return (long) this.width * this.height * this.depth;
    }

    // ------------------------------------------------------------- lecture

    /**
     * Charge un quartier depuis les donnees du mod.
     *
     * On passe par le gestionnaire de ressources du SERVEUR plutot que par le
     * chargeur de classes : le fichier vit dans {@code data/}, donc dans un
     * datapack, ce qui permet de le remplacer sans reconstruire le mod --
     * pratique quand on retaille un quartier vingt fois de suite.
     */
    @Nullable
    public static JakVolume load(MinecraftServer server, String name) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(
                EmeraldWeaponsMod.MODID, "jak/" + name + ".jakv");
        Optional<Resource> found = server.getResourceManager().getResource(key);
        if (found.isEmpty()) {
            return null;
        }
        try (InputStream raw = found.get().open();
             InputStream in = new InflaterInputStream(raw)) {
            return parse(readAll(in));
        } catch (IOException | IllegalStateException e) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .error("quartier {} illisible", name, e);
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
        byte[] chunk = new byte[1 << 16];
        int read;
        while ((read = in.read(chunk)) > 0) {
            out.write(chunk, 0, read);
        }
        return out.toByteArray();
    }

    private static JakVolume parse(byte[] data) {
        Cursor c = new Cursor(data);
        if (c.int32() != MAGIC) {
            throw new IllegalStateException("ce n'est pas un volume Jak");
        }
        int version = c.byte8();
        if (version != 1) {
            throw new IllegalStateException("version de format inconnue : " + version);
        }
        int w = c.int32();
        int h = c.int32();
        int d = c.int32();

        int paletteSize = c.short16();
        BlockState[] palette = new BlockState[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            String name = c.string();
            ResourceLocation id = ResourceLocation.tryParse(name);
            // un bloc absent ne doit pas faire echouer tout le quartier : on le
            // remplace par de la pierre, ce qui se voit et se corrige
            palette[i] = id != null && BuiltInRegistries.BLOCK.containsKey(id)
                    ? BuiltInRegistries.BLOCK.get(id).defaultBlockState()
                    : (i == 0 ? Blocks.AIR.defaultBlockState()
                              : Blocks.STONE.defaultBlockState());
        }

        int count = c.int32();
        int[] runs = new int[count * 2];
        for (int i = 0; i < count; i++) {
            runs[i * 2] = c.byte8();
            runs[i * 2 + 1] = c.varint();
        }
        return new JakVolume(w, h, d, palette, runs);
    }

    /** Les noms des quartiers disponibles, pour l'autocompletion de la commande. */
    public static List<String> available(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        server.getResourceManager()
                .listResources("jak", path -> path.getPath().endsWith(".jakv"))
                .keySet()
                .forEach(key -> {
                    if (!key.getNamespace().equals(EmeraldWeaponsMod.MODID)) {
                        return;
                    }
                    String path = key.getPath();
                    names.add(path.substring("jak/".length(), path.length() - ".jakv".length()));
                });
        return names;
    }

    /** Un curseur sur le tableau : le format est gros-boutiste, comme l'ecrit Python. */
    private static final class Cursor {
        private final byte[] data;
        private int at;

        Cursor(byte[] data) {
            this.data = data;
        }

        int byte8() {
            return this.data[this.at++] & 0xFF;
        }

        int short16() {
            return (byte8() << 8) | byte8();
        }

        int int32() {
            return (short16() << 16) | short16();
        }

        int varint() {
            int result = 0;
            int shift = 0;
            while (true) {
                int part = byte8();
                result |= (part & 0x7F) << shift;
                if ((part & 0x80) == 0) {
                    return result;
                }
                shift += 7;
            }
        }

        String string() {
            int length = short16();
            String value = new String(this.data, this.at, length,
                    java.nio.charset.StandardCharsets.UTF_8);
            this.at += length;
            return value;
        }
    }
}
