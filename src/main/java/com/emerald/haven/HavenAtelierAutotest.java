package com.emerald.haven;

import com.emerald.block.entity.HavenDoorBlockEntity;
import com.emerald.haven.door.HavenDoorFrame;
import com.emerald.haven.door.HavenDoorKind;
import com.emerald.haven.door.HavenDoors;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Le banc d'essai de l'atelier, INERTE sans EMERALDWEAPONS_AUTOTEST=atelier.
 *
 * Il fait, par le code, ce que ferait le joueur dans son monde d'atelier :
 *   1. la ville reposee (rebuild, sans aucun amenagement) puis relevee entiere :
 *      le releve doit etre VIDE -- c'est la preuve que la reference (le volume)
 *      et la normalisation tiennent sur les cent trente-cinq millions de cellules,
 *      et la mesure du temps d'un releve ;
 *   2. l'atelier ouvert : la ville se vide, un faux joueur n'y batit pas ;
 *   3. des retouches, dans la rue devant le bar et dans l'appartement 1 : un coffre
 *      garni, un escalier retourne, un trou bouche, un mur perce, un bloc du mod,
 *      un cadre garni et un tableau ; le releve doit les porter, elles et elles seules ;
 *   4. deux poses de la ville qui rejouent le releve (avec une salle relevee a part,
 *      que la ville entiere doit remplacer) : chaque retouche retrouvee, contenu et
 *      decors compris, et pas un decor en double.
 *
 * Le releve d'essai reste hors du mod : arcencium_jak/autotest/ du dossier du
 * serveur, passe au rejeu par JakOverlay.setTestZones. Rapport dans
 * atelier_autotest.txt, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenAtelierAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "atelier".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    /** Plus de cent tiques : un cadre ou un tableau sans support tombe au controle des cent tiques. */
    private static final int SETTLE = 140;
    private static final int TIMEOUT = 20 * 60 * 10;
    private static final int TICKING_DISTANCE = 2;
    /** Autour du bar : ou chercher les places du jeu d'essai, en cellules. */
    private static final int SEARCH = 14;
    /** La cellule de l'appartement 1 retouchee (de l'air dans la ville, comme au banc des salles). */
    private static final BlockPos ROOM_CELL = new BlockPos(101, 62, 182);
    /** La cellule de la salle relevee a part, que la ville entiere doit remplacer. */
    private static final BlockPos ROOM_ONLY_CELL = new BlockPos(99, 62, 182);

    private static final TicketType<ChunkPos> TICKET = TicketType.create("arcencium_atelier_test",
            Comparator.comparingLong(ChunkPos::toLong));

    private enum Stage { START, REBUILD0, CAPTURE0, LOAD, SETTLE, CAPTURE1, REBUILD, END }

    private static Stage stage = Stage.START;
    private static final StringBuilder OUT = new StringBuilder();
    private static final List<ChunkPos> HELD = new ArrayList<>();
    private static int passed;
    private static int failed;
    private static int waited;
    private static int settled = -1;
    private static int rebuilds;
    @Nullable
    private static JakVolume volume;
    @Nullable
    private static JakCityCapture.Result result;
    @Nullable
    private static CompoundTag captured;
    /** Le jeu d'essai : cellule du volume -> etat pose (normalise), et les places des decors. */
    private static final Map<BlockPos, String> EXPECTED = new LinkedHashMap<>();
    @Nullable
    private static BlockPos chestCell;
    @Nullable
    private static BlockPos frameCell;
    @Nullable
    private static BlockPos paintingCell;
    /** La petite porte de Jak 3 du jeu d'essai, dans le monde : relevee et rejouee comme le reste. */
    @Nullable
    private static HavenDoorFrame doorFrame;

    private HavenAtelierAutotest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || stage == Stage.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            tick(server);
        } catch (RuntimeException e) {
            LOGGER.error("autotest atelier : exception", e);
            check("deroulement sans exception", false, e.toString());
            end(server);
        }
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        HavenState state = HavenState.get(server);
        switch (stage) {
            case START -> {
                line("autotest de l'atelier de la ville, " + LocalDateTime.now().withNano(0));
                volume = JakVolume.load(server, Haven.VOLUME);
                if (level == null || volume == null) {
                    check("dimension et volume de la ville", false, "niveau " + level + ", volume " + volume);
                    end(server);
                    return;
                }
                // la ville telle que le volume la pose, sans aucun amenagement : la reference nue
                JakOverlay.setTestZones(Map.of());
                startRebuild(server, Stage.REBUILD0);
            }
            case REBUILD0 -> {
                if (timedOut(server, "pose de la ville nue")) {
                    return;
                }
                if (!rebuilt(state)) {
                    return;
                }
                HavenAtelier.set(server, true);
                FakePlayer fake = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(
                        "autotest-atelier".getBytes(StandardCharsets.UTF_8)), "[Atelier]"));
                check("atelier ouvert : la ville ferme ses portes a l'invasion (ni monstres, ni habitants, ni trafic),"
                                + " et un faux joueur n'y batit pas",
                        state.atelier() && !HavenInvasion.cityOpen(server) && !HavenAtelier.builder(fake)
                                && !HavenRules.chantier(fake),
                        "atelier " + state.atelier() + ", ville ouverte " + HavenInvasion.cityOpen(server));
                startCapture(server, "releve de la ville nue");
                stage = Stage.CAPTURE0;
            }
            case CAPTURE0 -> {
                if (timedOut(server, "releve de la ville nue")) {
                    return;
                }
                if (result == null) {
                    return;
                }
                JakCityCapture.Result nude = result;
                result = null;
                check("releve de la ville nue : population de l'invasion retiree par l'atelier",
                        HavenInvasion.loadedMonsters(level).isEmpty() && HavenInvasion.loadedVillagers(level).isEmpty(),
                        HavenInvasion.loadedMonsters(level).size() + " monstres, "
                                + HavenInvasion.loadedVillagers(level).size() + " habitants");
                check("releve de la ville nue : VIDE -- la reference et la normalisation tiennent sur toute la grille",
                        nude.ok() && nude.cells() == 0 && nude.entities() == 0,
                        nude.cells() + " cellule(s), " + nude.entities() + " decor(s), " + nude.managed()
                                + " du mod ignoree(s) (borne, bouton, cables vides, portes d'office)" + firstCells(nude.tag()));
                check("releve de la ville entiere en moins d'une minute et demie",
                        nude.ok() && nude.millis() < 90_000L,
                        nude.chunks() + " troncons en " + nude.millis() + " ms, " + nude.ticks() + " tiques");
                hold(level, state.origin());
                waited = 0;
                stage = Stage.LOAD;
            }
            case LOAD -> {
                if (timedOut(server, "troncons du jeu d'essai charges")) {
                    return;
                }
                for (ChunkPos pos : HELD) {
                    if (!level.areEntitiesLoaded(pos.toLong())) {
                        return;
                    }
                }
                place(level, state.origin());
                waited = 0;
                stage = Stage.SETTLE;
            }
            case SETTLE -> {
                if (++waited < SETTLE) {
                    return;
                }
                BlockPos o = state.origin();
                AABB near = new AABB(o.offset(Haven.BAR_FRONT_CELL)).inflate(SEARCH + 4);
                check("avant releve : les decors tiennent seuls, aucun objet au sol",
                        decorNear(level, near).size() == 2 && countItems(level, near) == 0,
                        decorNear(level, near).size() + " decors, " + countItems(level, near) + " objets au sol");
                startCapture(server, "autotest");
                stage = Stage.CAPTURE1;
            }
            case CAPTURE1 -> {
                if (timedOut(server, "releve des retouches")) {
                    return;
                }
                if (result == null) {
                    return;
                }
                judgeCapture(server, level);
                if (stage == Stage.END) {
                    return;
                }
                JakOverlay.setTestZones(Map.of(JakCityCapture.NAME, captured.copy(),
                        "appartement_1", roomOnlyZone(server, state.origin())));
                line("releve passe au rejeu comme amenagement d'essai, avec une salle relevee a part");
                startRebuild(server, Stage.REBUILD);
            }
            case REBUILD -> {
                if (timedOut(server, "rebuild " + rebuilds)) {
                    return;
                }
                if (settled < 0) {
                    if (rebuilt(state)) {
                        settled = 0;
                    }
                    return;
                }
                if (++settled < SETTLE) {
                    return;
                }
                compare(level, state.origin());
                if (rebuilds < 3) {
                    startRebuild(server, Stage.REBUILD);
                } else {
                    end(server);
                }
            }
            case END -> {
            }
        }
    }

    // ------------------------------------------------------------ poses et releves

    private static void startRebuild(MinecraftServer server, Stage next) {
        rebuilds++;
        settled = -1;
        waited = 0;
        Component failure = HavenSite.start(server, true, HavenSite.Mode.ETALE, null);
        if (failure != null) {
            check("rebuild " + rebuilds + " lance", false, failure.getString());
            end(server);
            return;
        }
        line("rebuild " + rebuilds + " lance");
        stage = next;
    }

    /** La pose est finie, et les decors rejoues avec. */
    private static boolean rebuilt(HavenState state) {
        return !HavenSite.busy() && state.built() && JakOverlay.pending() == 0;
    }

    private static void startCapture(MinecraftServer server, String author) {
        result = null;
        waited = 0;
        Path directory = JakDiff.directory(server).resolve("autotest");
        Component refused = JakCityCapture.start(server, null, directory, author, done -> result = done);
        if (refused != null) {
            check("releve de la ville lance", false, refused.getString());
            end(server);
        }
    }

    // ------------------------------------------------------------ le jeu d'essai

    private static boolean solid(BlockState state) {
        return !state.isAir() && !state.is(Blocks.BARRIER) && !state.is(Blocks.WATER);
    }

    private static BlockState ref(BlockPos o, int x, int y, int z) {
        return JakDiff.reference(volume, o, x, y, z);
    }

    /** Pose les retouches, comme un joueur : drapeaux 3, voisins prevenus. */
    private static void place(ServerLevel level, BlockPos o) {
        EXPECTED.clear();
        BlockPos bar = Haven.BAR_FRONT_CELL;
        List<BlockPos> floor = new ArrayList<>();
        BlockPos carve = null;
        Direction[] faces = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        List<BlockPos[]> walls = new ArrayList<>();       // {cellule d'air devant le mur, direction du cadre}
        List<Direction> wallFacing = new ArrayList<>();
        Set<BlockPos> taken = new LinkedHashSet<>();
        for (int dz = -SEARCH; dz <= SEARCH; dz++) {
            for (int dx = -SEARCH; dx <= SEARCH; dx++) {
                int x = bar.getX() + dx;
                int y = bar.getY();
                int z = bar.getZ() + dz;
                if (dx == 0 && dz == 0) {
                    continue;
                }
                boolean free = ref(o, x, y, z).isAir() && ref(o, x, y + 1, z).isAir() && solid(ref(o, x, y - 1, z));
                if (free && floor.size() < 5 && farFrom(taken, x, y, z)) {
                    floor.add(new BlockPos(x, y, z));
                    taken.add(new BlockPos(x, y, z));
                }
                // un mur : plein a y + 1, avec de l'air devant lui, sur sol plein
                if (solid(ref(o, x, y + 1, z)) && solid(ref(o, x, y + 2, z))) {
                    for (Direction face : faces) {
                        int ax = x + face.getStepX();
                        int az = z + face.getStepZ();
                        if (ref(o, ax, y + 1, az).isAir() && ref(o, ax, y + 2, az).isAir() && farFrom(taken, ax, y + 1, az)) {
                            if (walls.size() < 2) {
                                walls.add(new BlockPos[]{new BlockPos(ax, y + 1, az)});
                                wallFacing.add(face);
                                taken.add(new BlockPos(ax, y + 1, az));
                                taken.add(new BlockPos(x, y + 1, z));
                            } else if (carve == null && farFrom(taken, x, y + 2, z)) {
                                carve = new BlockPos(x, y + 2, z);
                                taken.add(carve);
                            }
                            break;
                        }
                    }
                }
            }
        }
        if (floor.size() < 5 || walls.size() < 2 || carve == null) {
            check("places du jeu d'essai trouvees autour du bar", false, floor.size() + " sols, " + walls.size()
                    + " murs, trou " + carve);
            end(level.getServer());
            return;
        }
        chestCell = floor.get(0);
        put(level, o, chestCell, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(o.offset(chestCell)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            chest.setItem(13, new ItemStack(Items.EMERALD, 32));
            chest.setChanged();
        }
        put(level, o, floor.get(1), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.TOP));
        // un « trou bouche » : de la brique de mur dans l'air de la rue
        put(level, o, floor.get(2), Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        put(level, o, floor.get(3), parse("emeraldweapons:arcencium_bricks"));
        // un mur perce
        put(level, o, carve, Blocks.AIR.defaultBlockState());
        // dans l'appartement 1
        put(level, o, ROOM_CELL, Blocks.BOOKSHELF.defaultBlockState());
        // une petite porte de Jak 3, posee comme avec l'objet : ses deux cellules et son controleur
        BlockPos doorCell = floor.get(4);
        doorFrame = new HavenDoorFrame(HavenDoorKind.PETITE, o.getX() + doorCell.getX() + 0.5,
                o.getY() + doorCell.getY(), o.getZ() + doorCell.getZ() + 0.5, 0.0F);
        boolean door = HavenDoors.place(level, doorFrame, false);
        for (BlockPos cell : HavenDoors.withController(doorFrame)) {
            BlockPos local = cell.subtract(o);
            BlockState base = JakDiff.normalize(ref(o, local.getX(), local.getY(), local.getZ()),
                    ref(o, local.getX(), local.getY(), local.getZ()));
            EXPECTED.put(local, JakDiff.name(JakDiff.normalize(level.getBlockState(cell), base)));
        }

        frameCell = walls.get(0)[0];
        ItemFrame frame = new ItemFrame(level, o.offset(frameCell), wallFacing.get(0));
        frame.setItem(new ItemStack(Items.EMERALD));
        paintingCell = walls.get(1)[0];
        Painting painting = new Painting(level, o.offset(paintingCell), wallFacing.get(1),
                level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT)
                        .getHolderOrThrow(PaintingVariants.KEBAB));
        boolean decor = level.addFreshEntity(frame) & level.addFreshEntity(painting);
        check("jeu d'essai pose : coffre garni, escalier retourne, trou bouche, bloc du mod, mur perce, appartement 1,"
                        + " petite porte de Jak 3, cadre garni et tableau",
                decor && door && EXPECTED.size() == 8,
                EXPECTED.size() + " cellules " + EXPECTED.keySet() + ", decors poses " + decor + " (cadre en "
                        + frameCell + " vers " + wallFacing.get(0) + ", tableau en " + paintingCell + ")");
    }

    private static boolean farFrom(Set<BlockPos> taken, int x, int y, int z) {
        for (BlockPos p : taken) {
            if (Math.abs(p.getX() - x) <= 2 && Math.abs(p.getZ() - z) <= 2 && Math.abs(p.getY() - y) <= 2) {
                return false;
            }
        }
        return true;
    }

    private static void put(ServerLevel level, BlockPos o, BlockPos cell, BlockState state) {
        level.setBlock(o.offset(cell), state, Block.UPDATE_ALL);
        BlockState base = JakDiff.normalize(ref(o, cell.getX(), cell.getY(), cell.getZ()),
                ref(o, cell.getX(), cell.getY(), cell.getZ()));
        EXPECTED.put(cell, JakDiff.name(JakDiff.normalize(level.getBlockState(o.offset(cell)), base)));
    }

    private static BlockState parse(String name) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), name, false).blockState();
        } catch (CommandSyntaxException e) {
            line("bloc du mod illisible : " + name + ", remplace par des briques de pierre");
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
    }

    // ------------------------------------------------------------ jugements

    private static void judgeCapture(MinecraftServer server, ServerLevel level) {
        JakCityCapture.Result done = result;
        result = null;
        if (done == null || !done.ok() || done.tag() == null) {
            check("releve des retouches ecrit", false, done == null ? "aucun" : String.valueOf(done.error()));
            end(server);
            return;
        }
        captured = done.tag().copy();
        CompoundTag read = JakDiff.read(done.file());
        String where = done.file().toAbsolutePath().normalize().toString().replace('\\', '/');
        check("releve ecrit en NBT compresse, relu a l'identique, hors de src/main/resources",
                read != null && read.equals(done.tag()) && !where.contains("src/main/resources")
                        && Files.isRegularFile(done.file().resolveSibling(JakCityCapture.NAME + ".txt")),
                where + ", " + done.millis() + " ms");
        check("releve : format d'une salle, sous le nom « ville », la grille entiere pour boite",
                "ville".equals(captured.getString("kind")) && "ville".equals(captured.getString("room"))
                        && java.util.Arrays.equals(JakDiff.ints(captured, "box_min"), new int[]{0, 0, 0})
                        && java.util.Arrays.equals(JakDiff.ints(captured, "box_max"),
                        new int[]{volume.width() - 1, volume.height() - 1, volume.depth() - 1}),
                captured.getString("kind") + ", boite " + java.util.Arrays.toString(JakDiff.ints(captured, "box_min"))
                        + " -> " + java.util.Arrays.toString(JakDiff.ints(captured, "box_max")));

        Map<BlockPos, String[]> cells = cellsOf(captured);
        Set<BlockPos> missing = new LinkedHashSet<>(EXPECTED.keySet());
        missing.removeAll(cells.keySet());
        Set<BlockPos> extra = new LinkedHashSet<>(cells.keySet());
        extra.removeAll(EXPECTED.keySet());
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<BlockPos, String> e : EXPECTED.entrySet()) {
            String[] cell = cells.get(e.getKey());
            if (cell != null && !cell[1].equals(e.getValue())) {
                wrong.add(e.getKey().toShortString() + " : " + cell[1] + " au lieu de " + e.getValue());
            }
        }
        check("releve : les retouches, elles seules, avec leur etat complet (la rue ET l'appartement 1)",
                missing.isEmpty() && extra.isEmpty() && wrong.isEmpty(),
                cells.size() + " cellules pour " + EXPECTED.size() + " ; manquantes " + missing + ", en trop "
                        + extra + ", fausses " + wrong);
        CompoundTag chest = chestCell == null ? null : nbtOf(captured, chestCell);
        check("releve : le contenu du coffre (2 piles)",
                chest != null && chest.getList("Items", Tag.TAG_COMPOUND).size() == 2, String.valueOf(chest));
        List<String> types = new ArrayList<>();
        ListTag entities = captured.getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            types.add(entities.getCompound(i).getCompound("nbt").getString("id"));
        }
        check("releve : les deux decors, cadre et tableau, et aucun autre",
                types.size() == 2 && types.containsAll(List.of("minecraft:item_frame", "minecraft:painting")),
                types.toString());
    }

    /** La salle relevee a part : une seule cellule d'or dans l'appartement 1, que la ville doit ignorer. */
    private static CompoundTag roomOnlyZone(MinecraftServer server, BlockPos o) {
        HavenRooms.Room room = HavenRooms.room(server, 1);
        HavenRooms.Box box = JakDiff.clamp(room.envelope(), volume);
        CompoundTag tag = new CompoundTag();
        tag.putInt("format", JakDiff.FORMAT);
        tag.putString("kind", "salle");
        tag.putString("room", room.id());
        tag.putString("volume", Haven.VOLUME);
        tag.putString("sha1", volume.sha1());
        tag.put("origin", new IntArrayTag(new int[]{o.getX(), o.getY(), o.getZ()}));
        tag.put("box_min", new IntArrayTag(new int[]{box.min().getX(), box.min().getY(), box.min().getZ()}));
        tag.put("box_max", new IntArrayTag(new int[]{box.max().getX(), box.max().getY(), box.max().getZ()}));
        ListTag palette = new ListTag();
        palette.add(StringTag.valueOf("minecraft:air"));
        palette.add(StringTag.valueOf("minecraft:gold_block"));
        tag.put("palette", palette);
        CompoundTag cell = new CompoundTag();
        cell.put("pos", new IntArrayTag(new int[]{ROOM_ONLY_CELL.getX() - box.min().getX(),
                ROOM_ONLY_CELL.getY() - box.min().getY(), ROOM_ONLY_CELL.getZ() - box.min().getZ()}));
        cell.putInt("base", 0);
        cell.putInt("state", 1);
        ListTag cells = new ListTag();
        cells.add(cell);
        tag.put("cells", cells);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static void compare(ServerLevel level, BlockPos o) {
        String when = "apres rebuild " + rebuilds + " : ";
        List<JakOverlay.Result> results = JakOverlay.lastResults();
        JakOverlay.Result city = results.size() == 1 ? results.get(0) : null;
        check(when + "seule la ville entiere est rejouee (la salle relevee a part est remplacee), rien de refuse",
                city != null && JakCityCapture.NAME.equals(city.room()) && city.refused() == null && city.unreadable() == 0
                        && level.getBlockState(o.offset(ROOM_ONLY_CELL)).isAir(),
                results.size() + " rejeu(x)" + (city == null ? "" : " : " + city.room() + ", " + city.placed()
                        + " blocs, " + city.entities() + " decors, " + city.unreadable() + " sautes")
                        + " ; cellule de la salle a part : " + JakDiff.name(level.getBlockState(o.offset(ROOM_ONLY_CELL))));
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<BlockPos, String> e : EXPECTED.entrySet()) {
            BlockPos cell = e.getKey();
            BlockState base = JakDiff.normalize(ref(o, cell.getX(), cell.getY(), cell.getZ()),
                    ref(o, cell.getX(), cell.getY(), cell.getZ()));
            String got = JakDiff.name(JakDiff.normalize(level.getBlockState(o.offset(cell)), base));
            if (!got.equals(e.getValue())) {
                wrong.add(cell.toShortString() + " : " + got + " au lieu de " + e.getValue());
            }
        }
        boolean chestOk = chestCell != null && level.getBlockEntity(o.offset(chestCell)) instanceof ChestBlockEntity chest
                && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 5
                && chest.getItem(13).is(Items.EMERALD) && chest.getItem(13).getCount() == 32;
        // la porte rejouee garde son centre et son lacet : son modele se dessine la ou elle etait
        boolean doorOk = doorFrame != null && level.getBlockEntity(doorFrame.controller()) instanceof HavenDoorBlockEntity door
                && door.kind() == doorFrame.kind() && Math.abs(door.frame().x() - doorFrame.x()) < 1.0e-6
                && Math.abs(door.frame().y() - doorFrame.y()) < 1.0e-6 && Math.abs(door.frame().z() - doorFrame.z()) < 1.0e-6
                && door.frame().yaw() == doorFrame.yaw();
        check(when + "chaque retouche retrouvee, cellule par cellule, le coffre garni et la porte a sa place",
                wrong.isEmpty() && chestOk && doorOk, EXPECTED.size() + " cellules ; ecarts " + wrong + " ; coffre "
                        + chestOk + " ; porte " + doorOk);
        AABB near = new AABB(o.offset(Haven.BAR_FRONT_CELL)).inflate(SEARCH + 4);
        List<Entity> decor = decorNear(level, near);
        boolean frameOk = false;
        boolean paintingOk = false;
        for (Entity entity : decor) {
            if (entity instanceof ItemFrame frame && frame.getPos().equals(o.offset(frameCell))
                    && frame.getItem().is(Items.EMERALD)) {
                frameOk = true;
            }
            if (entity instanceof Painting painting && painting.getPos().equals(o.offset(paintingCell))) {
                paintingOk = true;
            }
        }
        check(when + "les decors a leur place (cadre garni, tableau), deux et pas un de plus, aucun objet au sol",
                decor.size() == 2 && frameOk && paintingOk && countItems(level, near) == 0,
                decor.size() + " decor(s), cadre " + frameOk + ", tableau " + paintingOk + ", "
                        + countItems(level, near) + " objet(s) au sol");
    }

    // ------------------------------------------------------------ outils

    private static Map<BlockPos, String[]> cellsOf(CompoundTag tag) {
        Map<BlockPos, String[]> out = new HashMap<>();
        int[] lo = JakDiff.ints(tag, "box_min");
        ListTag palette = tag.getList("palette", Tag.TAG_STRING);
        ListTag cells = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag cell = cells.getCompound(i);
            int[] rel = JakDiff.ints(cell, "pos");
            out.put(new BlockPos(lo[0] + rel[0], lo[1] + rel[1], lo[2] + rel[2]),
                    new String[]{palette.getString(cell.getInt("base")), palette.getString(cell.getInt("state"))});
        }
        return out;
    }

    @Nullable
    private static CompoundTag nbtOf(CompoundTag tag, BlockPos wanted) {
        ListTag cells = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag cell = cells.getCompound(i);
            int[] rel = JakDiff.ints(cell, "pos");
            if (rel != null && rel[0] == wanted.getX() && rel[1] == wanted.getY() && rel[2] == wanted.getZ()) {
                return cell.contains("nbt", Tag.TAG_COMPOUND) ? cell.getCompound("nbt") : null;
            }
        }
        return null;
    }

    /** Les dix premieres cellules d'un releve, pour comprendre un releve qui n'est pas vide. */
    private static String firstCells(@Nullable CompoundTag tag) {
        if (tag == null) {
            return "";
        }
        ListTag cells = tag.getList("cells", Tag.TAG_COMPOUND);
        ListTag palette = tag.getList("palette", Tag.TAG_STRING);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(10, cells.size()); i++) {
            CompoundTag cell = cells.getCompound(i);
            out.append(i == 0 ? " ; premieres : " : ", ").append(java.util.Arrays.toString(JakDiff.ints(cell, "pos")))
                    .append(' ').append(palette.getString(cell.getInt("base"))).append(" -> ")
                    .append(palette.getString(cell.getInt("state")));
        }
        return out.toString();
    }

    private static List<Entity> decorNear(ServerLevel level, AABB box) {
        return JakDiff.decorIn(level, box);
    }

    private static int countItems(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(ItemEntity.class, box).size();
    }

    private static void hold(ServerLevel level, BlockPos o) {
        List<BlockPos> sites = List.of(o.offset(Haven.BAR_FRONT_CELL), o.offset(ROOM_CELL));
        for (BlockPos site : sites) {
            for (int cx = (site.getX() - SEARCH - 4) >> 4; cx <= (site.getX() + SEARCH + 4) >> 4; cx++) {
                for (int cz = (site.getZ() - SEARCH - 4) >> 4; cz <= (site.getZ() + SEARCH + 4) >> 4; cz++) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    if (!HELD.contains(pos)) {
                        level.getChunkSource().addRegionTicket(TICKET, pos, TICKING_DISTANCE, pos);
                        HELD.add(pos);
                    }
                }
            }
        }
    }

    private static boolean timedOut(MinecraftServer server, String what) {
        if (++waited > TIMEOUT) {
            check(what + " avant le delai", false, "delai depasse (" + TIMEOUT + " tiques)");
            end(server);
            return true;
        }
        return false;
    }

    private static void end(MinecraftServer server) {
        if (stage == Stage.END) {
            return;
        }
        stage = Stage.END;
        JakOverlay.setTestZones(null);
        HavenAtelier.set(server, false);
        ServerLevel level = Haven.level(server);
        cleanUp(server, level);
        if (level != null) {
            for (ChunkPos pos : HELD) {
                level.getChunkSource().removeRegionTicket(TICKET, pos, TICKING_DISTANCE, pos);
            }
        }
        HELD.clear();
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("atelier_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest atelier : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest atelier : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    /**
     * Rend la ville du serveur d'essai telle que le volume la pose : les retouches
     * d'essai sont devant le bar, dans la rue ou roulent les voitures des autres bancs.
     */
    private static void cleanUp(MinecraftServer server, @Nullable ServerLevel level) {
        HavenState state = HavenState.get(server);
        if (level == null || volume == null || !state.built() || HavenSite.busy() || EXPECTED.isEmpty()) {
            return;
        }
        BlockPos o = state.origin();
        int blocks = 0;
        for (BlockPos cell : EXPECTED.keySet()) {
            BlockPos pos = o.offset(cell);
            net.minecraft.world.Clearable.tryClear(level.getBlockEntity(pos));
            level.setBlock(pos, ref(o, cell.getX(), cell.getY(), cell.getZ()), com.emerald.jak.JakBuilder.FLAGS);
            blocks++;
        }
        AABB near = new AABB(o.offset(Haven.BAR_FRONT_CELL)).inflate(SEARCH + 4);
        int decor = 0;
        for (Entity entity : decorNear(level, near)) {
            entity.discard();
            decor++;
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, near)) {
            item.discard();
        }
        line("ville du serveur d'essai rendue au volume : " + blocks + " cellules, " + decor + " decors retires");
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest atelier : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }
}
