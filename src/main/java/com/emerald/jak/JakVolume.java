package com.emerald.jak;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
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
 *
 * Deux versions du fichier se lisent. La premiere ne donne que la taille. La
 * seconde porte aussi l'origine de la grille dans le repere du jeu, la taille
 * d'une cellule et le sha1 de ses donnees : une salle relevee sur une version
 * du port peut ainsi refuser de se poser sur une autre, au lieu de tomber a
 * cote sans rien dire.
 */
public final class JakVolume {

    /** Les huit premiers octets disent ce qu'on lit ; sinon on refuse. */
    private static final int MAGIC = 0x4A414B56;          // "JAKV"

    private final int version;
    private final int width;
    private final int height;
    private final int depth;

    /** Le coin de la grille dans le repere du jeu ; absent en version 1. */
    @Nullable
    private final double[] origin;
    private final double cellSize;
    @Nullable
    private final String sha1;

    private final String[] names;
    private final BlockState[] palette;

    /** Les plages, aplaties : index de palette puis longueur, en alternance. */
    private final int[] runs;

    /**
     * L'index lineaire de la premiere cellule de chaque plage.
     *
     * C'est ce qui permet de lire une cellule au hasard sans refaire tout le
     * parcours : une recherche dichotomique dans ce tableau trouve sa plage en
     * une vingtaine de comparaisons. Pour le port, sept cent mille plages, soit
     * cinq megaoctets et demi.
     */
    private final long[] starts;

    private JakVolume(int version, int width, int height, int depth,
                      @Nullable double[] origin, double cellSize, @Nullable String sha1,
                      String[] names, BlockState[] palette, int[] runs) {
        this.version = version;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.origin = origin;
        this.cellSize = cellSize;
        this.sha1 = sha1;
        this.names = names;
        this.palette = palette;
        this.runs = runs;

        int count = runs.length / 2;
        this.starts = new long[count];
        long at = 0;
        for (int i = 0; i < count; i++) {
            this.starts[i] = at;
            at += runs[i * 2 + 1];
        }
        // un fichier coupe ne plante pas a la pose : il laisse un trou. On le
        // refuse ici, ou la cause se lit encore
        if (at != cells()) {
            throw new IllegalStateException("volume tronque : " + at + " cellules sur " + cells());
        }
    }

    public int version() {
        return this.version;
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

    /** Le coin (minx, miny, minz) de la grille en unites du jeu, ou null en version 1. */
    @Nullable
    public double[] origin() {
        return this.origin == null ? null : this.origin.clone();
    }

    /** La taille d'une cellule en unites du jeu, ou NaN en version 1. */
    public double cellSize() {
        return this.cellSize;
    }

    /** Le sha1 des donnees, en hexadecimal, ou null en version 1. */
    @Nullable
    public String sha1() {
        return this.sha1;
    }

    public int runCount() {
        return this.runs.length / 2;
    }

    public BlockState state(int index) {
        return this.palette[index];
    }

    /** Le nom du bloc d'une entree de palette, tel qu'ecrit dans le fichier. */
    public String paletteName(int index) {
        return this.names[index];
    }

    public int paletteSize() {
        return this.names.length;
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

    // ------------------------------------------------------ lecture au hasard

    /** L'etat pose dans la cellule (x, y, z), comptee depuis le coin du volume. */
    public BlockState stateAt(int x, int y, int z) {
        return this.palette[blockAt(x, y, z)];
    }

    /**
     * L'entree de palette de la cellule (x, y, z).
     *
     * L'index lineaire suit l'ordre d'ecriture, y puis z puis x, comme dans
     * {@link JakBuilder}. La plage cherchee est la derniere qui commence avant
     * lui ou sur lui.
     */
    public int blockAt(int x, int y, int z) {
        if (x < 0 || x >= this.width || y < 0 || y >= this.height || z < 0 || z >= this.depth) {
            throw new IndexOutOfBoundsException("cellule hors du volume : " + x + " " + y + " " + z);
        }
        long index = ((long) y * this.depth + z) * this.width + x;
        int lo = 0;
        int hi = this.starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (this.starts[mid] <= index) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return this.runs[lo * 2];
    }

    /**
     * Les entrees de palette d'une rangee : les cellules x0 .. x0 + count - 1 de la
     * rangee (y, z), dans {@code out}.
     *
     * Une seule recherche, puis on suit les plages : le releve de la ville entiere
     * (JakCityCapture) lit ses cent trente-cinq millions de cellules ainsi, au lieu
     * d'une recherche dichotomique par cellule.
     */
    public void row(int y, int z, int x0, int count, int[] out) {
        if (count <= 0) {
            return;
        }
        blockAt(x0, y, z);                           // controle des bornes du debut
        blockAt(x0 + count - 1, y, z);               // et de la fin
        long index = ((long) y * this.depth + z) * this.width + x0;
        int lo = 0;
        int hi = this.starts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (this.starts[mid] <= index) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        int run = lo;
        for (int i = 0; i < count; i++) {
            long cell = index + i;
            while (run + 1 < this.starts.length && this.starts[run + 1] <= cell) {
                run++;
            }
            out[i] = this.runs[run * 2];
        }
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
            return parse(readAll(in), true);
        } catch (IOException | IllegalStateException e) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .error("quartier {} illisible", name, e);
            return null;
        }
    }

    /**
     * Le sha1 des donnees d'un quartier, lu dans son seul en-tete.
     *
     * C'est la seule empreinte du volume : celle que portent l'en-tete,
     * {@link #sha1()} et haven_rooms.json. Celle des octets du fichier ne vaut
     * rien -- elle change avec la version de zlib qui l'a compresse, a donnees
     * identiques. On ne decompresse que les soixante-neuf premiers octets :
     * assez pour comparer a chaque demarrage sans relire tout le port. Le sha1
     * n'est pas recalcule ici ; {@link #load} le verifie a la pose.
     *
     * @return le sha1 en hexadecimal, ou null si le fichier manque, est
     *         illisible ou n'est pas en version 2
     */
    @Nullable
    public static String dataSha1(MinecraftServer server, String name) {
        ResourceLocation key = ResourceLocation.fromNamespaceAndPath(
                EmeraldWeaponsMod.MODID, "jak/" + name + ".jakv");
        Optional<Resource> found = server.getResourceManager().getResource(key);
        if (found.isEmpty()) {
            return null;
        }
        // magie, version, trois tailles, origine et cellule, puis le sha1
        int headerLength = 4 + 1 + 12 + 32 + 20;
        try (InputStream raw = found.get().open();
             InputStream in = new InflaterInputStream(raw)) {
            byte[] header = in.readNBytes(headerLength);
            if (header.length < 5) {
                return null;
            }
            Cursor c = new Cursor(header);
            if (c.int32() != MAGIC || c.byte8() != 2 || header.length < headerLength) {
                return null;
            }
            return HexFormat.of().formatHex(header, headerLength - 20, headerLength);
        } catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                    .error("en-tete du quartier {} illisible", name, e);
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

    /**
     * Lit un volume deja decompresse.
     *
     * Sans {@code resolveBlocks}, la palette reste en noms et {@link #state}
     * rend null : c'est ce qui permet de verifier la lecture hors du jeu, sans
     * les registres de Minecraft.
     */
    static JakVolume parse(byte[] data, boolean resolveBlocks) {
        Cursor c = new Cursor(data);
        if (c.int32() != MAGIC) {
            throw new IllegalStateException("ce n'est pas un volume Jak");
        }
        int version = c.byte8();
        if (version != 1 && version != 2) {
            throw new IllegalStateException("version de format inconnue : " + version);
        }
        int w = c.int32();
        int h = c.int32();
        int d = c.int32();

        double[] origin = null;
        double cell = Double.NaN;
        String sha1 = null;
        if (version >= 2) {
            origin = new double[]{c.float64(), c.float64(), c.float64()};
            cell = c.float64();
            byte[] expected = c.bytes(20);
            // le sha1 couvre tout ce qui suit l'en-tete : palette et plages
            if (!MessageDigest.isEqual(expected, digest(data, c.at, data.length - c.at))) {
                throw new IllegalStateException("volume abime : le sha1 ne correspond pas aux donnees");
            }
            sha1 = HexFormat.of().formatHex(expected);
        }

        int paletteSize = c.short16();
        String[] names = new String[paletteSize];
        BlockState[] palette = new BlockState[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            names[i] = c.string();
            if (resolveBlocks) {
                palette[i] = resolve(i, names[i]);
            }
        }

        int count = c.int32();
        int[] runs = new int[count * 2];
        for (int i = 0; i < count; i++) {
            runs[i * 2] = c.byte8();
            runs[i * 2 + 1] = c.varint();
        }
        return new JakVolume(version, w, h, d, origin, cell, sha1, names, palette, runs);
    }

    /**
     * L'etat d'une entree de palette.
     *
     * Un nom peut porter des proprietes, comme dans une commande :
     * « minecraft:barrier[waterlogged=true] ». C'est le rideau du bord pose dans
     * la mer : une barriere seche, par defaut, y laissait une fente d'eau.
     */
    private static BlockState resolve(int index, String name) {
        if (name.indexOf('[') >= 0) {
            return WithProperties.parse(name);
        }
        ResourceLocation id = ResourceLocation.tryParse(name);
        // un bloc absent ne doit pas faire echouer tout le quartier : on le
        // remplace par de la pierre, ce qui se voit et se corrige
        return id != null && BuiltInRegistries.BLOCK.containsKey(id)
                ? BuiltInRegistries.BLOCK.get(id).defaultBlockState()
                : (index == 0 ? Blocks.AIR.defaultBlockState()
                              : Blocks.STONE.defaultBlockState());
    }

    private static byte[] digest(byte[] data, int from, int length) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-1");
            sha.update(data, from, length);
            return sha.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 indisponible", e);
        }
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

    /**
     * La lecture d'un nom a proprietes, a part.
     *
     * Dans sa propre classe, pour que {@link JakVolume} se charge encore hors du
     * jeu : le verificateur de Java charge les types d'exception attrapes et
     * les arguments de l'analyseur des sa premiere lecture de la classe, et
     * {@link #parse} sans registres ne serait plus verifiable a la main.
     */
    private static final class WithProperties {
        static BlockState parse(String name) {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), name, false)
                        .blockState();
            } catch (CommandSyntaxException e) {
                org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID)
                        .error("entree de palette {} illisible : {}", name, e.getMessage());
                return Blocks.STONE.defaultBlockState();
            }
        }
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

        double float64() {
            long high = int32() & 0xFFFFFFFFL;
            long low = int32() & 0xFFFFFFFFL;
            return Double.longBitsToDouble((high << 32) | low);
        }

        byte[] bytes(int length) {
            byte[] out = new byte[length];
            System.arraycopy(this.data, this.at, out, 0, length);
            this.at += length;
            return out;
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
