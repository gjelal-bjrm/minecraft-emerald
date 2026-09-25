package com.emerald.haven;

import com.emerald.block.ModBlocks;
import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneOreBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Le releve d'une salle : ce que le joueur a change dans la ville, et rien d'autre.
 *
 * C'est le releve des sanctuaires (SanctuaryLedger.diff), transporte dans la
 * ville, avec les trois pertes que l'enquete y avait mesurees corrigees :
 *
 *   - L'ETAT COMPLET, ET NON LE SEUL BLOC. Le sanctuaire comparait getBlock() :
 *     un escalier retourne, une dalle haute, une lanterne accrochee, une porte
 *     tournee n'y apparaissaient pas. Dans une salle meublee, c'est tout ce qui
 *     compte. On compare donc l'etat entier, apres avoir efface les proprietes
 *     qui bougent toutes seules (voir {@link #normalize}).
 *   - LES ENTITES DE BLOC. Seul l'etat etait ecrit : un coffre revenait vide et
 *     un panneau vierge. Le NBT de chaque cellule changee est garde.
 *   - LES DECORS-ENTITES. Cadres, cadres lumineux, tableaux et porte-armures ne
 *     sont pas des blocs ; ils sont releves avec leur NBT. Rien d'autre : ni
 *     joueurs, ni voitures, ni objets au sol, ni monstres, qui n'ont rien a
 *     faire dans un amenagement et se dupliqueraient a chaque pose.
 *
 * LA REFERENCE EST LE VOLUME, PAS UN INSTANTANE. Le port est deterministe : ce
 * que la pose met dans une cellule se lit dans le .jakv, a la demande
 * ({@link JakVolume#stateAt}). Pas de copie de la ville en memoire -- elle
 * pesait deux cent soixante-dix megaoctets --, et un releve qui survit au
 * redemarrage. La reference ne contient pas l'amenagement deja rejoue : chaque
 * releve est la difference COMPLETE, et remplace le precedent sans fusion.
 */
public final class JakDiff {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Version du format du fichier de releve. */
    public static final int FORMAT = 1;

    /** Le dossier des releves, dans le dossier de jeu du serveur (run/ en dev). */
    public static final String FOLDER = "arcencium_jak";

    /** Ce qu'un releve contient, et ce qu'on en dit dans le tchat. */
    public record Capture(HavenRooms.Room room, CompoundTag tag, List<String> text, int cells, int entities) {
    }

    private JakDiff() {
    }

    // ------------------------------------------------------------ la reference

    /**
     * Ce que la pose de la ville laisse dans une cellule.
     *
     * Le volume, sauf la ou la pose n'ecrit rien : l'air du fichier (l'entree 0,
     * sautee) et son eau (sautee parce que le generateur fournit la mer). La,
     * c'est le generateur qui decide -- air au-dessus de la mer, eau, pierre.
     * Comparer a l'air brut du fichier releverait toute la mer comme un ajout.
     */
    public static BlockState reference(JakVolume volume, BlockPos origin, int x, int y, int z) {
        int block = volume.blockAt(x, y, z);
        BlockState state = volume.state(block);
        if (block == 0 || state.is(Blocks.WATER)) {
            return Haven.generatorState(origin.getY() + y);
        }
        return state;
    }

    /**
     * Ce qu'une cellule a pour un releve, sans ce que le mod y pose lui-meme ; null si elle est
     * au mod tout entiere (jamais relevee). x, y, z : en cellules du volume.
     *
     * La regle du releve de la ville (JakCityCapture), et depuis le 26 sept. celle d'une salle
     * aussi -- chaque appartement a sa porte de Jak 3 depuis le 24 sept., et le releve d'une salle
     * la comptait comme un amenagement du joueur (cahier §105) :
     *   - la borne, le bouton d'invasion, le ratelier et les grilles, un cable vide : null ;
     *   - une porte d'office : la porte est au mod, la cellule au joueur. Un bloc de porte y compte
     *     pour ce que la cellule aurait sans elle : la reference si la porte la prend telle quelle
     *     (air, eau), de l'air sinon -- la porte ne prend que des cellules libres, quelqu'un a donc
     *     vide celle-la. Le linteau du bar, retire par le joueur au-dessus de sa porte, se perdait :
     *     la ville reposee le remettait, et la porte s'ouvrait sous deux blocs jaunes. Autre chose
     *     qu'une porte, dans l'ouverture, est releve tel quel.
     *
     * @param base la reference de la cellule, normalisee
     */
    @Nullable
    public static BlockState forCapture(MinecraftServer server, BlockState world, BlockState base,
                                        int x, int y, int z) {
        if (JakCityCapture.managed(world) || HavenCables.isCable(x, y, z)) {
            return null;
        }
        if (world.is(ModBlocks.HAVEN_DOOR.get()) && com.emerald.haven.door.HavenDoors.isDefaultCell(server, x, y, z)) {
            return base.isAir() || base.canBeReplaced() ? base : Blocks.AIR.defaultBlockState();
        }
        return world;
    }

    /**
     * L'etat tel qu'on le compare et tel qu'on le rejoue.
     *
     * Les proprietes remises a leur valeur par defaut sont celles qu'un
     * circuit, un joueur de passage ou le temps changent sans que personne
     * n'ait amenage quoi que ce soit (liste tiree de BlockStateProperties et
     * des blocs qui les portent) :
     *   - power, et powered sauf pour le levier, dont la position est un choix ;
     *   - triggered (distributeur, lanceur), crafting (fabricateur), enabled
     *     (entonnoir), locked (repeteur), sculk_sensor_phase ;
     *   - occupied (lit) ;
     *   - lit des blocs que la redstone ou le feu allument : fourneaux, lampe et
     *     torche de redstone, minerai de redstone. Une bougie ou un feu de camp
     *     allumes restent allumes : c'est un decor ;
     *   - open des portes, portillons et tonneaux, qu'on ouvre en passant. Les
     *     trappes gardent leur position : ouvertes, ce sont souvent des volets ;
     *   - moisture de la terre labouree ;
     *   - waterlogged, hors de l'eau : un bloc noye au-dessus de la mer est une
     *     source, qui coulerait au premier voisin change.
     * Toutes les formes d'air valent l'air.
     *
     * @param base l'etat de reference de la cellule, qui dit s'il y a de l'eau
     */
    public static BlockState normalize(BlockState state, BlockState base) {
        if (state.isAir()) {
            return Blocks.AIR.defaultBlockState();
        }
        Block block = state.getBlock();
        BlockState out = state;
        out = toDefault(out, BlockStateProperties.POWER);
        if (!(block instanceof LeverBlock)) {
            out = toDefault(out, BlockStateProperties.POWERED);
        }
        out = toDefault(out, BlockStateProperties.TRIGGERED);
        out = toDefault(out, BlockStateProperties.CRAFTING);
        out = toDefault(out, BlockStateProperties.ENABLED);
        out = toDefault(out, BlockStateProperties.LOCKED);
        out = toDefault(out, BlockStateProperties.SCULK_SENSOR_PHASE);
        out = toDefault(out, BlockStateProperties.OCCUPIED);
        out = toDefault(out, BlockStateProperties.MOISTURE);
        if (block instanceof AbstractFurnaceBlock || block instanceof RedstoneLampBlock
                || block instanceof RedstoneTorchBlock || block instanceof RedStoneOreBlock) {
            out = toDefault(out, BlockStateProperties.LIT);
        }
        if (block instanceof DoorBlock || block instanceof FenceGateBlock || block instanceof BarrelBlock) {
            out = toDefault(out, BlockStateProperties.OPEN);
        }
        if (out.hasProperty(BlockStateProperties.WATERLOGGED) && !base.getFluidState().is(FluidTags.WATER)) {
            out = out.setValue(BlockStateProperties.WATERLOGGED, false);
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState toDefault(BlockState state, Property<T> property) {
        if (!state.hasProperty(property)) {
            return state;
        }
        return state.setValue(property, state.getBlock().defaultBlockState().getValue(property));
    }

    /** Cadres, cadres lumineux, tableaux, porte-armures : les seuls decors releves et rejoues. */
    public static boolean isDecor(Entity entity) {
        return entity instanceof ItemFrame || entity instanceof Painting || entity instanceof ArmorStand;
    }

    /**
     * Le nom complet d'un etat, proprietes comprises, au format des commandes.
     *
     * « minecraft:oak_stairs[facing=east,half=top,...] » se relit tel quel par
     * BlockStateParser : c'est ce qui permet de REJOUER, et non de paraphraser.
     */
    public static String name(BlockState state) {
        return BlockStateParser.serialize(state);
    }

    // ------------------------------------------------------------ geometrie

    /** Le coin bas de la boite, dans le monde. */
    public static BlockPos worldMin(BlockPos origin, HavenRooms.Box box) {
        return origin.offset(box.min());
    }

    /** Le coin haut de la boite, dans le monde, borne incluse. */
    public static BlockPos worldMax(BlockPos origin, HavenRooms.Box box) {
        return origin.offset(box.max());
    }

    /** La boite en blocs pleins, pour chercher les entites dedans. */
    public static AABB aabb(BlockPos origin, HavenRooms.Box box) {
        BlockPos min = worldMin(origin, box);
        BlockPos max = worldMax(origin, box);
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /**
     * Les decors dont le CENTRE est dans la boite.
     *
     * Pas ceux qui la touchent seulement : un tableau accroche dehors, sur la
     * face exterieure du mur, depasse dans la coque par sa boite de collision,
     * et ne fait pas partie de la salle.
     */
    public static List<Entity> decorIn(ServerLevel level, AABB box) {
        return level.getEntities((Entity) null, box.inflate(1.0), e -> isDecor(e) && box.contains(e.position()));
    }

    /**
     * Les entites des troncons de la boite sont-elles chargees ?
     *
     * Une entite qui n'est pas chargee ne se voit pas : un releve la manquerait,
     * un retrait la laisserait, et elle reviendrait en double au chargement.
     */
    public static boolean entitiesLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------ le releve

    /**
     * Releve une salle : sa coque entiere, cellule par cellule, puis ses decors.
     *
     * L'appelant s'assure que la ville posee est bien celle du volume donne
     * (meme sha1) : sinon tout le port serait releve comme un changement.
     *
     * @param author le nom de qui releve, pour le fichier lisible
     */
    public static Capture capture(ServerLevel level, HavenRooms.Room room, JakVolume volume,
                                  BlockPos origin, String author) {
        HavenRooms.Box box = clamp(room.envelope(), volume);
        BlockPos min = worldMin(origin, box);
        String sha1 = volume.sha1() == null ? "" : volume.sha1();
        String date = LocalDateTime.now().withNano(0).toString();

        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        ListTag cells = new ListTag();
        List<String> cellLines = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = box.min().getY(); y <= box.max().getY(); y++) {
            for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                for (int x = box.min().getX(); x <= box.max().getX(); x++) {
                    BlockState raw = reference(volume, origin, x, y, z);
                    BlockState base = normalize(raw, raw);
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState found = level.getBlockState(pos);
                    BlockState own = forCapture(level.getServer(), found, base, x, y, z);
                    if (own == null) {
                        continue;
                    }
                    BlockState world = normalize(own, base);
                    if (world == base) {
                        continue;
                    }
                    CompoundTag cell = new CompoundTag();
                    cell.put("pos", new IntArrayTag(new int[]{x - box.min().getX(), y - box.min().getY(),
                            z - box.min().getZ()}));
                    cell.putInt("base", palette.computeIfAbsent(base, s -> palette.size()));
                    cell.putInt("state", palette.computeIfAbsent(world, s -> palette.size()));
                    BlockEntity blockEntity = own == found ? level.getBlockEntity(pos) : null;
                    String extra = "";
                    if (blockEntity != null) {
                        CompoundTag data = blockEntity.saveWithId(level.registryAccess());
                        data.remove("x");
                        data.remove("y");
                        data.remove("z");
                        cell.put("nbt", data);
                        extra = "  " + data;
                    }
                    cells.add(cell);
                    cellLines.add(String.format(Locale.ROOT, "  c %d %d %d (monde %d %d %d) : %s -> %s%s",
                            x, y, z, pos.getX(), pos.getY(), pos.getZ(), name(base), name(world), extra));
                }
            }
        }

        ListTag entities = new ListTag();
        List<String> entityLines = new ArrayList<>();
        for (Entity entity : decorIn(level, aabb(origin, box))) {
            CompoundTag data = new CompoundTag();
            if (!entity.save(data)) {
                continue;                          // passager, ou entite qui ne se sauve pas
            }
            data.remove("UUID");
            BlockPos block = entity instanceof BlockAttachedEntity attached ? attached.getPos() : entity.blockPosition();
            ListTag rel = new ListTag();
            rel.add(DoubleTag.valueOf(entity.getX() - min.getX()));
            rel.add(DoubleTag.valueOf(entity.getY() - min.getY()));
            rel.add(DoubleTag.valueOf(entity.getZ() - min.getZ()));
            CompoundTag entry = new CompoundTag();
            entry.put("pos", rel);
            entry.put("block", new IntArrayTag(new int[]{block.getX() - min.getX(), block.getY() - min.getY(),
                    block.getZ() - min.getZ()}));
            entry.put("nbt", data);
            entities.add(entry);
            entityLines.add(String.format(Locale.ROOT, "  %s en %.3f %.3f %.3f, bloc %d %d %d : %s",
                    EntityType.getKey(entity.getType()), entity.getX(), entity.getY(), entity.getZ(),
                    block.getX(), block.getY(), block.getZ(), data));
        }

        ListTag names = new ListTag();
        for (BlockState state : palette.keySet()) {
            names.add(StringTag.valueOf(name(state)));
        }

        CompoundTag tag = new CompoundTag();
        tag.putInt("format", FORMAT);
        tag.putString("kind", "salle");
        tag.putString("room", room.id());
        tag.putInt("number", room.number());
        tag.putString("volume", Haven.VOLUME);
        tag.putString("sha1", sha1);
        tag.put("origin", new IntArrayTag(new int[]{origin.getX(), origin.getY(), origin.getZ()}));
        tag.put("box_min", new IntArrayTag(new int[]{box.min().getX(), box.min().getY(), box.min().getZ()}));
        tag.put("box_max", new IntArrayTag(new int[]{box.max().getX(), box.max().getY(), box.max().getZ()}));
        tag.putString("captured", date);
        tag.putString("author", author);
        tag.put("palette", names);
        tag.put("cells", cells);
        tag.put("entities", entities);
        NbtUtils.addCurrentDataVersion(tag);

        List<String> text = new ArrayList<>();
        text.add("Releve de la salle " + room.id() + " (salle " + room.number() + "), " + date + ", par " + author);
        text.add("volume " + Haven.VOLUME + ", sha1 " + sha1 + ", origine " + origin.toShortString()
                + ", coque en cellules " + box.min().toShortString() + " -> " + box.max().toShortString());
        text.add("Chaque ligne : cellule du volume (et bloc du monde) : etat de la ville -> etat releve.");
        text.add(cells.size() + " cellule(s) changee(s) :");
        text.addAll(cellLines);
        text.add(entities.size() + " decor(s) :");
        text.addAll(entityLines);
        return new Capture(room, tag, text, cells.size(), entities.size());
    }

    /** La boite, rognee au volume : une salle au bord de la grille ne lit pas hors du fichier. */
    public static HavenRooms.Box clamp(HavenRooms.Box box, JakVolume volume) {
        BlockPos min = new BlockPos(Math.max(0, box.min().getX()), Math.max(0, box.min().getY()),
                Math.max(0, box.min().getZ()));
        BlockPos max = new BlockPos(Math.min(volume.width() - 1, box.max().getX()),
                Math.min(volume.height() - 1, box.max().getY()), Math.min(volume.depth() - 1, box.max().getZ()));
        return new HavenRooms.Box(min, max);
    }

    // ------------------------------------------------------------ fichiers

    public static Path directory(MinecraftServer server) {
        return server.getServerDirectory().resolve(FOLDER);
    }

    /**
     * Ecrit le releve : les donnees en NBT compresse, et le meme en texte.
     *
     * Chaque releve REMPLACE le precedent. La reference ne contient pas
     * l'amenagement deja rejoue, donc le nouveau releve le contient deja tout
     * entier : fusionner ne ferait que ressusciter ce qu'on a retire depuis.
     *
     * @return le chemin du fichier NBT
     */
    public static Path write(Path directory, Capture capture) throws IOException {
        Files.createDirectories(directory);
        Path nbt = directory.resolve(capture.room().id() + ".nbt");
        NbtIo.writeCompressed(capture.tag(), nbt);
        Files.write(directory.resolve(capture.room().id() + ".txt"), capture.text(), StandardCharsets.UTF_8);
        return nbt;
    }

    /** Relit un fichier de releve, ou null s'il manque ou ne se lit pas. */
    @Nullable
    public static CompoundTag read(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            LOGGER.error("releve {} illisible", file, e);
            return null;
        }
    }

    /**
     * Trois entiers ranges sous une cle.
     *
     * En tableau d'entiers tel que le jeu l'ecrit, ou en liste d'entiers, au cas
     * ou un outil aurait reecrit le fichier sans garder le type.
     */
    @Nullable
    public static int[] ints(CompoundTag tag, String key) {
        if (tag.contains(key, Tag.TAG_INT_ARRAY)) {
            int[] values = tag.getIntArray(key);
            return values.length == 3 ? values : null;
        }
        if (tag.contains(key, Tag.TAG_LIST)) {
            ListTag list = tag.getList(key, Tag.TAG_INT);
            return list.size() == 3 ? new int[]{list.getInt(0), list.getInt(1), list.getInt(2)} : null;
        }
        return null;
    }

    /** L'identifiant d'un bloc, pour les messages. */
    public static String blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
