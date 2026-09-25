package com.emerald.haven;

import com.emerald.block.entity.HavenDoorBlockEntity;
import com.emerald.haven.door.HavenDoorFrame;
import com.emerald.haven.door.HavenDoors;
import com.emerald.jak.JakBuilder;
import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Le banc d'essai des salles, INERTE sans EMERALDWEAPONS_AUTOTEST=salles.
 *
 * Il fait, par le code, ce que ferait le joueur : dans l'appartement 1, il
 * pose un jeu d'essai qui reunit chaque piege connu -- escalier retourne, dalle
 * haute, porte, lit, lanterne suspendue, torche murale, panneau ecrit, coffre
 * rempli, vitres liees au mur, bloc du mod, un bloc du mur et un du sol, un
 * tableau, deux cadres garnis et un porte-armure equipe. Puis il releve la
 * salle, lance deux rebuilds de la ville qui la rejouent, et compare le monde
 * au releve apres chacun : cellule par cellule, entite de bloc par entite de
 * bloc, decor par decor ; aucun objet au sol, aucune eau qui coule, et le meme
 * nombre de decors apres les deux poses. Il verifie enfin qu'une salle au sha1
 * faux, ou a l'origine deplacee, est refusee sans rien toucher.
 *
 * La porte d'office de l'appartement (§95) est au mod : cassee avant la remise a
 * zero, elle revient, et reste la tout le banc sans entrer dans aucun releve (§105).
 *
 * LE RELEVE D'ESSAI RESTE HORS DU MOD : il est ecrit dans
 * arcencium_jak/autotest/ du dossier du serveur (run-server/, ignore par git)
 * et passe au rejeu par {@link JakOverlay#setTestZones}, jamais par
 * src/main/resources.
 *
 * Rapport dans salles_autotest.txt du dossier du serveur, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenRoomsAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "salles".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    private static final int ROOM = 1;
    /** Plus de cent tiques : un cadre ou un tableau sans support tombe au controle des cent tiques. */
    private static final int SETTLE = 140;
    /** Au-dela, on rend un rapport d'echec plutot que d'attendre. */
    private static final int TIMEOUT = 20 * 60 * 10;
    /** Troncons de la salle tenus en tick d'entites : les decors doivent vivre pour tomber s'ils tombent. */
    private static final int TICKING_DISTANCE = 2;

    private static final TicketType<ChunkPos> TICKET = TicketType.create("arcencium_salles_test",
            Comparator.comparingLong(ChunkPos::toLong));

    private enum Stage { START, BUILD, LOAD, PLACED, REBUILD, END }

    private static Stage stage = Stage.START;
    private static final StringBuilder OUT = new StringBuilder();
    private static final List<ChunkPos> HELD = new ArrayList<>();
    private static int passed;
    private static int failed;
    private static int waited;
    /** Tiques depuis la fin d'une pose ; -1 tant qu'elle n'est pas finie. */
    private static int settled = -1;
    private static int rebuilds;
    @Nullable
    private static JakVolume volume;
    @Nullable
    private static HavenRooms.Room room;
    @Nullable
    private static CompoundTag captured;
    private static final List<Integer> DECOR_COUNTS = new ArrayList<>();

    /** Les cellules du jeu d'essai, en cellules du volume. */
    private static final Set<BlockPos> EXPECTED = new LinkedHashSet<>();

    private HavenRoomsAutotest() {
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
            LOGGER.error("autotest salles : exception", e);
            check("deroulement sans exception", false, e.toString());
            end(server);
        }
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        HavenState state = HavenState.get(server);
        switch (stage) {
            case START -> {
                line("autotest des salles de la ville, " + LocalDateTime.now().withNano(0));
                if (level == null) {
                    check("dimension haven chargee", false, "absente");
                    end(server);
                    return;
                }
                volume = JakVolume.load(server, Haven.VOLUME);
                room = HavenRooms.room(server, ROOM);
                if (volume == null || room == null) {
                    check("volume et salle " + ROOM + " lus", false, "volume " + volume + ", salle " + room);
                    end(server);
                    return;
                }
                if (!HavenSite.busy() && !state.built()) {
                    Component failure = HavenSite.start(server, false, HavenSite.Mode.ETALE, null);
                    line("ville pas encore posee : pose lancee"
                            + (failure == null ? "" : ", echec " + failure.getString()));
                }
                waited = 0;
                stage = Stage.BUILD;
            }
            case BUILD -> {
                if (timedOut(server, "ville posee")) {
                    return;
                }
                if (!HavenSite.busy() && state.built()) {
                    hold(level, state.origin());
                    waited = 0;
                    stage = Stage.LOAD;
                }
            }
            case LOAD -> {
                if (timedOut(server, "entites de la salle chargees")) {
                    return;
                }
                HavenRooms.Box box = room.envelope();
                BlockPos o = state.origin();
                if (JakDiff.entitiesLoaded(level, JakDiff.worldMin(o, box), JakDiff.worldMax(o, box))) {
                    prepare(level, o);
                    waited = 0;
                    stage = Stage.PLACED;
                }
            }
            case PLACED -> {
                if (++waited >= SETTLE) {
                    captureAndRebuild(server, level, state.origin());
                }
            }
            case REBUILD -> {
                if (timedOut(server, "rebuild " + rebuilds + " fini")) {
                    return;
                }
                if (settled < 0) {
                    if (!HavenSite.busy() && state.built() && JakOverlay.pending() == 0) {
                        settled = 0;
                        JakBuilder.Report report = HavenSite.lastReport();
                        line("rebuild " + rebuilds + " fini" + (report == null ? ""
                                : " : " + report.placed() + " blocs en " + report.totalNanos() / 1_000_000L + " ms"));
                    }
                } else if (++settled >= SETTLE) {
                    compare(level, state.origin());
                    if (rebuilds == 1) {
                        startRebuild(server);
                    } else {
                        refusals(level, state.origin());
                        end(server);
                    }
                }
            }
            case END -> {
            }
        }
    }

    // ------------------------------------------------------------ le jeu d'essai

    private static void prepare(ServerLevel level, BlockPos o) {
        AABB box = JakDiff.aabb(o, room.envelope());
        // la porte d'office de l'appartement est au mod (§105) : cassee avant la remise a zero, elle
        // doit revenir, et rester la tout le banc sans entrer dans aucun releve
        HavenDoorFrame office = HavenDoors.frameOf(room);
        if (office != null) {
            HavenDoors.breakAround(level, office.moved(o.getX(), o.getY(), o.getZ()).controller());
        }
        int[] reset = JakOverlay.resetRoom(level, room, volume, o);
        int items = discardItems(level, box.inflate(8.0));
        line("salle " + ROOM + " (" + room.id() + ") remise a l'etat du volume : " + reset[0] + " blocs, "
                + reset[1] + " decors retires, " + items + " objets au sol retires");
        check("remise a zero : la porte d'office de la salle, cassee, revient (au mod, pas au joueur)",
                office != null && HavenDoors.controllerAt(level, o, office) instanceof HavenDoorBlockEntity entity
                        && entity.kind() == office.kind(),
                office == null ? "pas d'ouverture dans haven_rooms.json" : "porte " + office.kind() + " en "
                        + office.x() + " " + office.y() + " " + office.z());

        // chaque cellule d'interieur doit etre de l'air dans la ville, chaque
        // cellule de coque un bloc plein : sinon le jeu d'essai est mal place
        List<String> misplaced = new ArrayList<>();
        EXPECTED.clear();

        interior(level, misplaced, o, 101, 62, 182, Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP));
        interior(level, misplaced, o, 99, 62, 182, Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP));
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        interior(level, misplaced, o, 97, 62, 185, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        interior(level, misplaced, o, 97, 63, 185, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        interior(level, misplaced, o, 99, 62, 190, bed.setValue(BedBlock.PART, BedPart.FOOT));
        interior(level, misplaced, o, 99, 62, 191, bed.setValue(BedBlock.PART, BedPart.HEAD));
        // sous le plafond, cellule 85
        interior(level, misplaced, o, 99, 84, 188, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        // contre le mur ouest, x 94
        interior(level, misplaced, o, 95, 64, 183, Blocks.WALL_TORCH.defaultBlockState()
                .setValue(WallTorchBlock.FACING, Direction.EAST));
        interior(level, misplaced, o, 97, 62, 182, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 4));
        interior(level, misplaced, o, 102, 62, 190, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST));
        interior(level, misplaced, o, 95, 62, 194, Blocks.GLASS_PANE.defaultBlockState());
        interior(level, misplaced, o, 96, 62, 194, Blocks.GLASS_PANE.defaultBlockState());
        interior(level, misplaced, o, 98, 62, 194, parse("emeraldweapons:arcencium_bricks"));
        shell(level, misplaced, o, 100, 63, 180, Blocks.OAK_PLANKS.defaultBlockState());
        shell(level, misplaced, o, 96, 61, 190, Blocks.OAK_PLANKS.defaultBlockState());

        // les vitres prennent leurs connexions, comme posees a la main
        for (BlockPos cell : List.of(new BlockPos(95, 62, 194), new BlockPos(96, 62, 194))) {
            BlockPos pos = o.offset(cell);
            level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(o.offset(97, 62, 182)) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal("Arcencium"))
                    .setMessage(1, Component.literal("salle 1")), true);
            sign.setChanged();
        }
        if (level.getBlockEntity(o.offset(102, 62, 190)) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
            chest.setItem(13, new ItemStack(Items.EMERALD, 32));
            chest.setChanged();
        }

        // les decors, sur le mur est (x 104) et au sol
        List<String> refusedDecor = new ArrayList<>();
        Painting painting = new Painting(level, o.offset(103, 64, 184), Direction.WEST,
                level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT)
                        .getHolderOrThrow(PaintingVariants.KEBAB));
        addDecor(level, painting, refusedDecor);
        ItemFrame frame = new ItemFrame(level, o.offset(103, 64, 186), Direction.WEST);
        frame.setItem(new ItemStack(Items.EMERALD));
        addDecor(level, frame, refusedDecor);
        GlowItemFrame glow = new GlowItemFrame(level, o.offset(103, 64, 188), Direction.WEST);
        glow.setItem(new ItemStack(Items.DIAMOND));
        addDecor(level, glow, refusedDecor);
        ArmorStand stand = new ArmorStand(level, o.getX() + 100.5, o.getY() + 62, o.getZ() + 193.5);
        stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        stand.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        addDecor(level, stand, refusedDecor);

        check("jeu d'essai pose a sa place (interieur dans l'air de la ville, coque dans le mur)",
                misplaced.isEmpty() && refusedDecor.isEmpty(),
                EXPECTED.size() + " cellules, 4 decors" + (misplaced.isEmpty() ? "" : " ; mal places : " + misplaced)
                        + (refusedDecor.isEmpty() ? "" : " ; decors refuses : " + refusedDecor));
    }

    private static void interior(ServerLevel level, List<String> misplaced, BlockPos o, int x, int y, int z,
                                 BlockState state) {
        if (!JakDiff.reference(volume, o, x, y, z).isAir()) {
            misplaced.add(x + " " + y + " " + z + " n'est pas de l'air");
        }
        put(level, o, x, y, z, state);
    }

    private static void shell(ServerLevel level, List<String> misplaced, BlockPos o, int x, int y, int z,
                              BlockState state) {
        if (JakDiff.reference(volume, o, x, y, z).isAir()) {
            misplaced.add(x + " " + y + " " + z + " est de l'air");
        }
        put(level, o, x, y, z, state);
    }

    /** Pose comme le ferait un joueur : drapeaux 3, voisins prevenus. */
    private static void put(ServerLevel level, BlockPos o, int x, int y, int z, BlockState state) {
        level.setBlock(o.offset(x, y, z), state, Block.UPDATE_ALL);
        EXPECTED.add(new BlockPos(x, y, z));
    }

    private static void addDecor(ServerLevel level, Entity entity, List<String> refused) {
        if (!level.addFreshEntity(entity)) {
            refused.add(EntityType.getKey(entity.getType()).toString());
        }
    }

    private static BlockState parse(String name) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), name, false).blockState();
        } catch (CommandSyntaxException e) {
            line("bloc du mod illisible : " + name + ", remplace par des briques de pierre");
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
    }

    // ------------------------------------------------------------ releve

    private static void captureAndRebuild(MinecraftServer server, ServerLevel level, BlockPos o) {
        AABB box = JakDiff.aabb(o, room.envelope());
        check("avant releve : aucun objet au sol, le jeu d'essai tient seul",
                countItems(level, box.inflate(8.0)) == 0 && JakDiff.decorIn(level, box).size() == 4,
                countItems(level, box.inflate(8.0)) + " objets, " + JakDiff.decorIn(level, box).size() + " decors");

        long t0 = System.nanoTime();
        JakDiff.Capture capture = JakDiff.capture(level, room, volume, o, "autotest");
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        Path directory = JakDiff.directory(server).resolve("autotest");
        Path file;
        try {
            file = JakDiff.write(directory, capture);
        } catch (IOException e) {
            check("releve ecrit", false, e.toString());
            end(server);
            return;
        }
        CompoundTag read = JakDiff.read(file);
        check("releve ecrit en NBT compresse et relu a l'identique", read != null && read.equals(capture.tag()),
                file.toAbsolutePath() + ", " + fileSize(file) + " octets, releve en " + ms + " ms");
        String where = file.toAbsolutePath().normalize().toString().replace('\\', '/');
        check("releve d'essai hors de src/main/resources", !where.contains("src/main/resources"), where);

        Set<BlockPos> got = cellsOf(capture.tag()).keySet();
        Set<BlockPos> missing = new LinkedHashSet<>(EXPECTED);
        missing.removeAll(got);
        Set<BlockPos> extra = new LinkedHashSet<>(got);
        extra.removeAll(EXPECTED);
        check("releve : les cellules du jeu d'essai, et elles seules", missing.isEmpty() && extra.isEmpty(),
                capture.cells() + " cellules relevees pour " + EXPECTED.size() + " posees ; manquantes " + missing
                        + ", en trop " + extra);

        List<String> types = new ArrayList<>();
        ListTag entities = capture.tag().getList("entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            types.add(entities.getCompound(i).getCompound("nbt").getString("id"));
        }
        check("releve : 4 decors, tableau, cadre, cadre lumineux, porte-armure", capture.entities() == 4
                && types.containsAll(List.of("minecraft:painting", "minecraft:item_frame",
                "minecraft:glow_item_frame", "minecraft:armor_stand")), types.toString());

        Map<BlockPos, Cell> cells = cellsOf(capture.tag());
        Cell chest = cells.get(new BlockPos(102, 62, 190));
        Cell sign = cells.get(new BlockPos(97, 62, 182));
        boolean chestOk = chest != null && chest.nbt() != null && chest.nbt().getList("Items", Tag.TAG_COMPOUND).size() == 2;
        boolean signOk = sign != null && sign.nbt() != null && sign.nbt().toString().contains("Arcencium");
        check("releve : NBT du coffre (2 piles) et du panneau (texte) gardes", chestOk && signOk,
                "coffre " + (chest == null ? "absent" : chest.nbt()) + " ; panneau " + (sign == null ? "absent" : sign.nbt()));
        Cell stair = cells.get(new BlockPos(101, 62, 182));
        Cell lantern = cells.get(new BlockPos(99, 84, 188));
        check("releve : etat complet (escalier retourne, lanterne suspendue)",
                stair != null && stair.state().contains("half=top") && lantern != null
                        && lantern.state().contains("hanging=true"),
                (stair == null ? "escalier absent" : stair.state()) + " ; "
                        + (lantern == null ? "lanterne absente" : lantern.state()));

        normalization();

        captured = capture.tag().copy();
        JakOverlay.setTestZones(Map.of(room.id(), captured.copy()));
        line("releve passe au rejeu comme amenagement d'essai (" + room.id() + ")");
        startRebuild(server);
    }

    /** Les proprietes volatiles s'effacent, les autres restent. */
    private static void normalization() {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState closed = Blocks.OAK_DOOR.defaultBlockState();
        BlockState open = closed.setValue(DoorBlock.OPEN, true).setValue(DoorBlock.POWERED, true);
        BlockState lever = Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.POWERED, true);
        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState().setValue(BlockStateProperties.POWER, 15);
        BlockState wet = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true);
        boolean ok = JakDiff.normalize(open, air) == JakDiff.normalize(closed, air)
                && JakDiff.normalize(lever, air).getValue(BlockStateProperties.POWERED)
                && JakDiff.normalize(wire, air).getValue(BlockStateProperties.POWER) == 0
                && !JakDiff.normalize(wet, air).getValue(BlockStateProperties.WATERLOGGED)
                && JakDiff.normalize(wet, water).getValue(BlockStateProperties.WATERLOGGED)
                && JakDiff.normalize(trapdoor, air).getValue(BlockStateProperties.OPEN)
                && JakDiff.normalize(Blocks.CAVE_AIR.defaultBlockState(), air) == air;
        check("normalisation : porte ouverte = fermee, fil alimente = eteint, noye hors eau = sec ;"
                        + " levier, trappe ouverte et noye dans l'eau gardes", ok,
                JakDiff.name(JakDiff.normalize(open, air)) + ", " + JakDiff.name(JakDiff.normalize(wet, air)));
    }

    private static void startRebuild(MinecraftServer server) {
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
        stage = Stage.REBUILD;
    }

    // ------------------------------------------------------------ comparaisons

    private record Cell(String base, String state, @Nullable CompoundTag nbt) {
    }

    /** Les cellules d'un releve, par cellule du volume. */
    private static Map<BlockPos, Cell> cellsOf(CompoundTag tag) {
        Map<BlockPos, Cell> out = new HashMap<>();
        int[] lo = JakDiff.ints(tag, "box_min");
        ListTag palette = tag.getList("palette", Tag.TAG_STRING);
        ListTag cells = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag cell = cells.getCompound(i);
            int[] rel = JakDiff.ints(cell, "pos");
            out.put(new BlockPos(lo[0] + rel[0], lo[1] + rel[1], lo[2] + rel[2]),
                    new Cell(palette.getString(cell.getInt("base")), palette.getString(cell.getInt("state")),
                            cell.contains("nbt", Tag.TAG_COMPOUND) ? cell.getCompound("nbt") : null));
        }
        return out;
    }

    private static void compare(ServerLevel level, BlockPos o) {
        String when = "apres rebuild " + rebuilds + " : ";
        AABB box = JakDiff.aabb(o, room.envelope());
        Map<BlockPos, Cell> wanted = cellsOf(captured);

        List<JakOverlay.Result> results = JakOverlay.lastResults();
        JakOverlay.Result result = results.size() == 1 ? results.get(0) : null;
        check(when + "rejeu de la salle, rien de refuse ni de saute",
                result != null && result.refused() == null && result.unreadable() == 0 && result.flowing() == 0
                        && result.placed() == wanted.size(),
                result == null ? results.size() + " resultats" : "pose " + result.placed() + " blocs, "
                        + result.blockEntities() + " entites de bloc, " + result.entities() + " decors"
                        + (result.deferred() ? " (differes)" : "") + ", " + result.unreadable() + " sautes, "
                        + result.flowing() + " eaux ecartees" + (result.refused() == null ? ""
                        : ", refus " + result.refused().getString()));

        // cellule par cellule : l'etat normalise du monde, et son entite de bloc
        int stateGaps = 0;
        int dataGaps = 0;
        List<String> firstGaps = new ArrayList<>();
        for (Map.Entry<BlockPos, Cell> entry : wanted.entrySet()) {
            BlockPos pos = o.offset(entry.getKey());
            Cell cell = entry.getValue();
            BlockState base = parse(cell.base());
            String world = JakDiff.name(JakDiff.normalize(level.getBlockState(pos), base));
            if (!world.equals(cell.state())) {
                stateGaps++;
                if (firstGaps.size() < 5) {
                    firstGaps.add(entry.getKey().toShortString() + " monde " + world + " / releve " + cell.state());
                }
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            CompoundTag data = null;
            if (blockEntity != null) {
                data = blockEntity.saveWithId(level.registryAccess());
                data.remove("x");
                data.remove("y");
                data.remove("z");
            }
            if (!Objects.equals(data, cell.nbt())) {
                dataGaps++;
                if (firstGaps.size() < 5) {
                    firstGaps.add(entry.getKey().toShortString() + " NBT monde " + data + " / releve " + cell.nbt());
                }
            }
        }
        check(when + "cellule par cellule, 0 ecart d'etat et 0 ecart de NBT", stateGaps == 0 && dataGaps == 0,
                wanted.size() + " cellules ; " + stateGaps + " ecarts d'etat, " + dataGaps + " de NBT" + firstGaps);

        // le releve refait sur le monde : ni cellule en plus, ni en moins
        JakDiff.Capture again = JakDiff.capture(level, room, volume, o, "autotest");
        Map<BlockPos, Cell> now = cellsOf(again.tag());
        check(when + "nouveau releve identique au premier (cellules, etats, NBT)", now.equals(wanted),
                now.size() + " cellules contre " + wanted.size());

        // decor par decor
        List<Entity> decor = JakDiff.decorIn(level, box);
        BlockPos min = JakDiff.worldMin(o, room.envelope());
        ListTag entries = captured.getList("entities", Tag.TAG_COMPOUND);
        int matched = 0;
        List<String> unmatched = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            CompoundTag data = entry.getCompound("nbt");
            int[] block = JakDiff.ints(entry, "block");
            ListTag rel = entry.getList("pos", Tag.TAG_DOUBLE);
            Entity found = null;
            for (Entity entity : decor) {
                BlockPos at = entity instanceof BlockAttachedEntity attached ? attached.getPos() : entity.blockPosition();
                if (EntityType.getKey(entity.getType()).toString().equals(data.getString("id"))
                        && at.equals(min.offset(block[0], block[1], block[2]))
                        && Math.abs(entity.getX() - min.getX() - rel.getDouble(0)) < 1.0e-3
                        && Math.abs(entity.getY() - min.getY() - rel.getDouble(1)) < 1.0e-3
                        && Math.abs(entity.getZ() - min.getZ() - rel.getDouble(2)) < 1.0e-3) {
                    found = entity;
                    break;
                }
            }
            if (found == null) {
                unmatched.add(data.getString("id") + " absent");
                continue;
            }
            CompoundTag live = new CompoundTag();
            found.save(live);
            List<String> differs = new ArrayList<>();
            for (String key : List.of("Facing", "facing", "variant", "Item", "ItemRotation", "Invisible", "Fixed",
                    "ArmorItems", "HandItems", "ShowArms", "Small", "NoBasePlate", "Pose")) {
                if (!Objects.equals(data.get(key), live.get(key))) {
                    differs.add(key);
                }
            }
            if (differs.isEmpty()) {
                matched++;
            } else {
                unmatched.add(data.getString("id") + " differe par " + differs);
            }
        }
        check(when + "decor par decor (type, place, orientation, objet, equipement)",
                matched == entries.size() && decor.size() == entries.size(),
                matched + " sur " + entries.size() + " retrouves, " + decor.size() + " decors dans la salle" + unmatched);
        DECOR_COUNTS.add(decor.size());

        int items = countItems(level, box.inflate(8.0));
        check(when + "0 objet au sol autour de la salle", items == 0, items + " objets");
        long flowing = countFlowing(level, o);
        check(when + "0 eau qui coule dans la salle et autour", flowing == 0, flowing + " blocs");
        BlockState lower = level.getBlockState(o.offset(97, 62, 185));
        BlockState head = level.getBlockState(o.offset(99, 62, 191));
        BlockState torch = level.getBlockState(o.offset(95, 64, 183));
        check(when + "porte, lit et torche entiers", lower.is(Blocks.OAK_DOOR)
                        && level.getBlockState(o.offset(97, 63, 185)).is(Blocks.OAK_DOOR)
                        && head.is(Blocks.RED_BED) && level.getBlockState(o.offset(99, 62, 190)).is(Blocks.RED_BED)
                        && torch.is(Blocks.WALL_TORCH),
                JakDiff.name(lower) + ", " + JakDiff.name(head) + ", " + JakDiff.name(torch));
        if (DECOR_COUNTS.size() == 2) {
            check("meme nombre de decors apres les deux reposes", DECOR_COUNTS.get(0).equals(DECOR_COUNTS.get(1))
                    && DECOR_COUNTS.get(0) == entries.size(), DECOR_COUNTS.toString());
        }
    }

    /** Une salle au sha1 faux, ou relevee a une autre origine, est refusee sans rien toucher. */
    private static void refusals(ServerLevel level, BlockPos o) {
        AABB box = JakDiff.aabb(o, room.envelope());
        String sha1 = volume.sha1() == null ? "" : volume.sha1();

        CompoundTag wrongSha1 = captured.copy();
        wrongSha1.putString("sha1", "0000000000000000000000000000000000000000");
        refusal(level, o, box, "sha1 faux", wrongSha1, sha1);

        CompoundTag moved = captured.copy();
        moved.put("origin", new IntArrayTag(new int[]{o.getX() + 1, o.getY(), o.getZ()}));
        refusal(level, o, box, "origine deplacee", moved, sha1);
    }

    private static void refusal(ServerLevel level, BlockPos o, AABB box, String what, CompoundTag zone, String sha1) {
        // on retire d'abord une piece de la salle : un rejeu accepte la remettrait
        BlockPos probe = o.offset(99, 62, 182);
        level.setBlock(probe, Blocks.AIR.defaultBlockState(), JakBuilder.FLAGS);
        Map<BlockPos, Cell> before = cellsOf(JakDiff.capture(level, room, volume, o, "autotest").tag());
        int decorBefore = JakDiff.decorIn(level, box).size();
        JakOverlay.Result result = JakOverlay.apply(level, room.id() + "_" + what.replace(' ', '_'), zone, volume, o, sha1);
        Map<BlockPos, Cell> after = cellsOf(JakDiff.capture(level, room, volume, o, "autotest").tag());
        int decorAfter = JakDiff.decorIn(level, box).size();
        check("salle au " + what + " refusee, monde inchange",
                result.refused() != null && result.placed() == 0 && before.equals(after) && decorBefore == decorAfter
                        && level.getBlockState(probe).isAir(),
                (result.refused() == null ? "ACCEPTEE" : result.refused().getString()) + " ; " + before.size()
                        + " -> " + after.size() + " cellules, " + decorBefore + " -> " + decorAfter + " decors");
    }

    // ------------------------------------------------------------ outils

    private static int countItems(ServerLevel level, AABB box) {
        return level.getEntitiesOfClass(ItemEntity.class, box).size();
    }

    private static int discardItems(ServerLevel level, AABB box) {
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        items.forEach(Entity::discard);
        return items.size();
    }

    /** L'eau ou la lave qui coulent dans la coque grandie de deux cellules. */
    private static long countFlowing(ServerLevel level, BlockPos o) {
        HavenRooms.Box box = room.envelope().inflate(2);
        long count = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = box.min().getY(); y <= box.max().getY(); y++) {
            for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                for (int x = box.min().getX(); x <= box.max().getX(); x++) {
                    FluidState fluid = level.getBlockState(pos.set(o.getX() + x, o.getY() + y, o.getZ() + z))
                            .getFluidState();
                    if (!fluid.isEmpty() && !fluid.isSource()) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void hold(ServerLevel level, BlockPos o) {
        BlockPos min = JakDiff.worldMin(o, room.envelope());
        BlockPos max = JakDiff.worldMax(o, room.envelope());
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                ChunkPos pos = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(TICKET, pos, TICKING_DISTANCE, pos);
                HELD.add(pos);
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

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return -1;
        }
    }

    private static void end(MinecraftServer server) {
        if (stage == Stage.END) {
            return;
        }
        stage = Stage.END;
        JakOverlay.setTestZones(null);
        ServerLevel level = Haven.level(server);
        if (level != null && room != null && volume != null && HavenState.get(server).built() && !HavenSite.busy()) {
            int[] reset = JakOverlay.resetRoom(level, room, volume, HavenState.get(server).origin());
            int items = discardItems(level, JakDiff.aabb(HavenState.get(server).origin(), room.envelope()).inflate(8.0));
            line("salle " + ROOM + " rendue a l'etat du volume : " + reset[0] + " blocs, " + reset[1] + " decors, "
                    + items + " objets au sol");
        }
        if (level != null) {
            for (ChunkPos pos : HELD) {
                level.getChunkSource().removeRegionTicket(TICKET, pos, TICKING_DISTANCE, pos);
            }
        }
        HELD.clear();
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("salles_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest salles : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest salles : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest salles : {}", text);
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
