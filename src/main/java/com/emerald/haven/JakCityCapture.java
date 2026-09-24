package com.emerald.haven;

import com.emerald.block.ModBlocks;
import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Le releve de la ville ENTIERE : tout ce que le joueur a change dans Haven, et rien d'autre.
 *
 * LE JOUEUR (21 sept.) : « je vais mettre des meubles, corriger des petits
 * details, il y a encore quelques trous dans les murs [...] un peu dans toute la
 * map. Je voudrais que tu puisses recuperer ces changements facilement. » Le
 * releve d'une salle (JakDiff) ne portait que sur les trois appartements : celui-ci
 * porte sur toute la grille, appartements compris.
 *
 * LA MEME REGLE QUE LES SALLES : la reference est le VOLUME (ctyport.jakv), relu a
 * la demande ; chaque cellule du monde qui en differe, une fois normalisee
 * (JakDiff.normalize : une porte ouverte vaut une porte fermee), est relevee avec
 * son etat complet et le NBT de son entite de bloc ; puis les decors-entites --
 * cadres, tableaux, porte-armures. Le fichier a le format d'une salle, sous le nom
 * « ville » et avec la grille entiere pour boite : JakOverlay le rejoue tel quel.
 * Chaque releve est la difference COMPLETE, et remplace le precedent.
 *
 * CE QUI N'EST JAMAIS RELEVE : la borne du QG et le bouton de l'invasion, que le
 * mod pose et retire lui-meme (HavenVote, HavenInvasionButton) -- les figer dans
 * l'amenagement les ressusciterait a leur ancienne place.
 *
 * ETALE SUR LES TIQUES, comme la pose : les troncons de la grille sont tenus par un
 * ticket (3 388 pour ctyport), on attend qu'ils soient la avec leurs entites, puis
 * on les lit {@value #BUDGET_MS} ms par tique. La reference se lit par RANGEE
 * (JakVolume.row) et le monde par SECTION : une cellule egale a sa reference -- la
 * quasi-totalite -- ne coute qu'une comparaison.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class JakCityCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Le nom du releve de la ville entiere, et de son amenagement dans le mod. */
    public static final String NAME = "ville";

    /** Le ticket qui tient la grille chargee pendant le releve. */
    public static final TicketType<ChunkPos> TICKET = TicketType.create("arcencium_haven_releve",
            Comparator.comparingLong(ChunkPos::toLong));

    /** Temps de lecture par tique : le serveur garde de quoi tiquer. */
    public static final long BUDGET_MS = 25;
    /** Tiques d'attente des entites avant de passer outre, comme la pose. */
    private static final int ENTITY_WAIT = 400;

    /** Ce qu'a donne un releve. */
    public record Result(boolean ok, @Nullable Path file, @Nullable CompoundTag tag, int cells, int blockEntities,
                         int entities, int managed, int chunks, long millis, int ticks, @Nullable String error) {
    }

    private enum Phase { AWAIT, SCAN, WRITE }

    @Nullable
    private static Job job;

    private JakCityCapture() {
    }

    /** Un releve est-il en cours ? */
    public static boolean busy() {
        return job != null;
    }

    /**
     * Lance le releve de la ville.
     *
     * @param watcher   qui suit l'avancement dans le tchat, ou null
     * @param directory ou ecrire ville.nbt et ville.txt
     * @param onDone    appele a la fin, reussie ou non
     * @return null si le releve est lance, sinon la raison du refus
     */
    @Nullable
    public static Component start(MinecraftServer server, @Nullable ServerPlayer watcher, Path directory,
                                  String author, @Nullable Consumer<Result> onDone) {
        if (job != null) {
            return Component.translatable("command.emeraldweapons.haven.atelier.releve.running");
        }
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return Component.translatable("command.emeraldweapons.haven.missing_level");
        }
        if (HavenSite.busy()) {
            return Component.translatable("command.emeraldweapons.haven.busy");
        }
        HavenState state = HavenState.get(server);
        if (!state.built()) {
            return Component.translatable("command.emeraldweapons.haven.not_built");
        }
        JakVolume volume = JakVolume.load(server, Haven.VOLUME);
        if (volume == null) {
            return Component.translatable("command.emeraldweapons.haven.missing_volume", Haven.VOLUME);
        }
        String sha1 = volume.sha1() == null ? "" : volume.sha1();
        if (sha1.isEmpty() || !sha1.equals(state.sha1())) {
            // la ville posee n'est pas celle du volume : tout le port serait releve comme un changement
            return Component.translatable("command.emeraldweapons.haven.salle.stale", state.sha1(), sha1);
        }
        job = new Job(server, level, volume, state.origin(), sha1, directory, author,
                watcher == null ? null : watcher.getUUID(), onDone);
        job.hold();
        LOGGER.info("releve de la ville : lance par {}, {} troncons", author, job.chunks());
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Job current = job;
        if (current == null) {
            return;
        }
        boolean done;
        try {
            done = current.step(System.nanoTime() + BUDGET_MS * 1_000_000L);
        } catch (RuntimeException e) {
            LOGGER.error("releve de la ville : echec", e);
            current.error = e.toString();
            done = true;
        }
        if (done) {
            job = null;
            current.release();
            current.finish();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        job = null;
    }

    /** Les blocs que le mod pose et retire lui-meme dans la ville : jamais releves. */
    public static boolean managed(BlockState state) {
        return state.is(ModBlocks.HAVEN_VOTE.get()) || state.is(ModBlocks.HAVEN_INVASION_BUTTON.get())
                || state.is(ModBlocks.HAVEN_GUN_RACK.get()) || state.is(ModBlocks.HAVEN_GATE.get());
    }

    // ================================================================ le travail

    private static final class Job {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final JakVolume volume;
        private final BlockPos origin;
        private final String sha1;
        private final Path directory;
        private final String author;
        @Nullable
        private final UUID watcher;
        @Nullable
        private final Consumer<Result> onDone;
        private final int width;
        private final int height;
        private final int depth;
        private final int cx0;
        private final int cz0;
        private final int spanX;
        private final int spanZ;
        /** L'etat du generateur a chaque couche de la grille : la reference de l'air et de l'eau du volume. */
        private final BlockState[] generator;
        private final int[] row = new int[16];
        private final long started = System.nanoTime();

        private Phase phase = Phase.AWAIT;
        private boolean held;
        private int ticks;
        private int entityWaited;
        private int chunk;
        private int lastTenth;

        private final Map<BlockState, Integer> palette = new LinkedHashMap<>();
        private final Map<String, Integer> perBlock = new TreeMap<>();
        private final ListTag cells = new ListTag();
        private final List<String> cellLines = new ArrayList<>();
        private int blockEntities;
        private int managedCells;
        @Nullable
        private String error;
        @Nullable
        private Result result;

        Job(MinecraftServer server, ServerLevel level, JakVolume volume, BlockPos origin, String sha1, Path directory,
            String author, @Nullable UUID watcher, @Nullable Consumer<Result> onDone) {
            this.server = server;
            this.level = level;
            this.volume = volume;
            this.origin = origin;
            this.sha1 = sha1;
            this.directory = directory;
            this.author = author;
            this.watcher = watcher;
            this.onDone = onDone;
            this.width = volume.width();
            this.height = volume.height();
            this.depth = volume.depth();
            this.cx0 = origin.getX() >> 4;
            this.cz0 = origin.getZ() >> 4;
            this.spanX = ((origin.getX() + this.width - 1) >> 4) - this.cx0 + 1;
            this.spanZ = ((origin.getZ() + this.depth - 1) >> 4) - this.cz0 + 1;
            this.generator = new BlockState[this.height];
            for (int y = 0; y < this.height; y++) {
                this.generator[y] = Haven.generatorState(origin.getY() + y);
            }
        }

        int chunks() {
            return this.spanX * this.spanZ;
        }

        private ChunkPos chunkPos(int n) {
            return new ChunkPos(this.cx0 + n % this.spanX, this.cz0 + n / this.spanX);
        }

        void hold() {
            for (int n = 0; n < chunks(); n++) {
                ChunkPos pos = chunkPos(n);
                // distance 0 : le troncon a l'etat FINI, sans le faire tiquer, entites comprises
                this.level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
            }
            this.held = true;
        }

        void release() {
            if (!this.held) {
                return;
            }
            this.held = false;
            for (int n = 0; n < chunks(); n++) {
                ChunkPos pos = chunkPos(n);
                this.level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            }
        }

        /** @return vrai quand le releve est fini, ecrit ou non */
        boolean step(long deadline) {
            this.ticks++;
            while (true) {
                switch (this.phase) {
                    case AWAIT -> {
                        if (!ready()) {
                            return false;
                        }
                        this.phase = Phase.SCAN;
                        tell(Component.translatable("command.emeraldweapons.haven.atelier.releve.loaded", chunks()));
                    }
                    case SCAN -> {
                        while (this.chunk < chunks()) {
                            if (System.nanoTime() > deadline) {
                                return false;
                            }
                            scan(this.chunk++);
                            int tenth = this.chunk * 10 / chunks();
                            if (tenth > this.lastTenth && tenth < 10) {
                                this.lastTenth = tenth;
                                tell(Component.translatable("command.emeraldweapons.haven.atelier.releve.progress",
                                        tenth * 10, this.cells.size()));
                            }
                        }
                        this.phase = Phase.WRITE;
                    }
                    case WRITE -> {
                        write();
                        return true;
                    }
                }
            }
        }

        /** Tous les troncons de la grille sont-ils charges, et leurs entites ? */
        private boolean ready() {
            int missing = 0;
            int withoutEntities = 0;
            for (int n = 0; n < chunks(); n++) {
                int cx = this.cx0 + n % this.spanX;
                int cz = this.cz0 + n / this.spanX;
                if (this.level.getChunkSource().getChunkNow(cx, cz) == null) {
                    missing++;
                } else if (!this.level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    withoutEntities++;
                }
            }
            if (missing == 0 && (withoutEntities == 0 || this.entityWaited >= ENTITY_WAIT)) {
                if (withoutEntities > 0) {
                    LOGGER.warn("releve de la ville : {} troncons toujours sans leurs entites apres {} tiques,"
                            + " leurs decors manqueront", withoutEntities, ENTITY_WAIT);
                }
                return true;
            }
            if (missing == 0) {
                this.entityWaited++;
            }
            if (this.ticks % 200 == 0) {
                LOGGER.info("releve de la ville : attente, {} troncons manquants et {} sans leurs entites sur {}",
                        missing, withoutEntities, chunks());
            }
            return false;
        }

        /** Une colonne de troncon : chaque cellule de la grille, comparee a sa reference. */
        private void scan(int n) {
            int cx = this.cx0 + n % this.spanX;
            int cz = this.cz0 + n / this.spanX;
            LevelChunk column = this.level.getChunkSource().getChunkNow(cx, cz);
            if (column == null) {
                column = this.level.getChunk(cx, cz);
            }
            int wx0 = Math.max(this.origin.getX(), cx << 4);
            int wx1 = Math.min(this.origin.getX() + this.width - 1, (cx << 4) + 15);
            int wz0 = Math.max(this.origin.getZ(), cz << 4);
            int wz1 = Math.min(this.origin.getZ() + this.depth - 1, (cz << 4) + 15);
            if (wx0 > wx1 || wz0 > wz1) {
                return;
            }
            int count = wx1 - wx0 + 1;
            for (int y = 0; y < this.height; y++) {
                int wy = this.origin.getY() + y;
                if (wy < this.level.getMinBuildHeight() || wy >= this.level.getMaxBuildHeight()) {
                    continue;
                }
                LevelChunkSection section = column.getSection(column.getSectionIndex(wy));
                BlockState generated = this.generator[y];
                for (int wz = wz0; wz <= wz1; wz++) {
                    this.volume.row(y, wz - this.origin.getZ(), wx0 - this.origin.getX(), count, this.row);
                    for (int i = 0; i < count; i++) {
                        int wx = wx0 + i;
                        int index = this.row[i];
                        BlockState raw = index == 0 ? generated : this.volume.state(index);
                        if (raw.is(Blocks.WATER)) {
                            raw = generated;             // l'eau du volume n'est pas posee : c'est la mer du generateur
                        }
                        BlockState world = section.getBlockState(wx & 15, wy & 15, wz & 15);
                        if (world == raw) {
                            continue;
                        }
                        compare(column, raw, world, wx, wy, wz);
                    }
                }
            }
        }

        private void compare(LevelChunk column, BlockState raw, BlockState world, int wx, int wy, int wz) {
            BlockState base = JakDiff.normalize(raw, raw);
            BlockState got = JakDiff.normalize(world, base);
            if (got == base) {
                return;
            }
            if (managed(world)) {
                this.managedCells++;
                return;
            }
            int x = wx - this.origin.getX();
            int y = wy - this.origin.getY();
            int z = wz - this.origin.getZ();
            if (HavenCables.isCable(x, y, z)
                    || com.emerald.haven.door.HavenDoors.isDefaultCell(this.server, x, y, z)) {
                this.managedCells++;          // un cable vide ou une porte d'office : le mod, pas le joueur
                return;
            }
            CompoundTag cell = new CompoundTag();
            cell.put("pos", new IntArrayTag(new int[]{x, y, z}));
            cell.putInt("base", this.palette.computeIfAbsent(base, s -> this.palette.size()));
            cell.putInt("state", this.palette.computeIfAbsent(got, s -> this.palette.size()));
            BlockPos pos = new BlockPos(wx, wy, wz);
            BlockEntity blockEntity = column.getBlockEntity(pos);
            String extra = "";
            if (blockEntity != null) {
                CompoundTag data = blockEntity.saveWithId(this.level.registryAccess());
                data.remove("x");
                data.remove("y");
                data.remove("z");
                cell.put("nbt", data);
                extra = "  " + data;
                this.blockEntities++;
            }
            this.cells.add(cell);
            this.perBlock.merge(got.isAir() ? "(retire) " + JakDiff.blockId(base) : JakDiff.blockId(got), 1, Integer::sum);
            this.cellLines.add(String.format(Locale.ROOT, "  c %d %d %d (monde %d %d %d) : %s -> %s%s",
                    x, y, z, wx, wy, wz, JakDiff.name(base), JakDiff.name(got), extra));
        }

        /** Les decors de la ville entiere, puis le fichier. */
        private void write() {
            AABB box = new AABB(this.origin.getX(), this.origin.getY(), this.origin.getZ(),
                    this.origin.getX() + this.width, this.origin.getY() + this.height, this.origin.getZ() + this.depth);
            ListTag entities = new ListTag();
            List<String> entityLines = new ArrayList<>();
            for (Entity entity : JakDiff.decorIn(this.level, box)) {
                CompoundTag data = new CompoundTag();
                if (!entity.save(data)) {
                    continue;
                }
                data.remove("UUID");
                BlockPos block = entity instanceof BlockAttachedEntity attached ? attached.getPos() : entity.blockPosition();
                ListTag rel = new ListTag();
                rel.add(DoubleTag.valueOf(entity.getX() - this.origin.getX()));
                rel.add(DoubleTag.valueOf(entity.getY() - this.origin.getY()));
                rel.add(DoubleTag.valueOf(entity.getZ() - this.origin.getZ()));
                CompoundTag entry = new CompoundTag();
                entry.put("pos", rel);
                entry.put("block", new IntArrayTag(new int[]{block.getX() - this.origin.getX(),
                        block.getY() - this.origin.getY(), block.getZ() - this.origin.getZ()}));
                entry.put("nbt", data);
                entities.add(entry);
                entityLines.add(String.format(Locale.ROOT, "  %s en %.3f %.3f %.3f, bloc %d %d %d : %s",
                        EntityType.getKey(entity.getType()), entity.getX(), entity.getY(), entity.getZ(),
                        block.getX(), block.getY(), block.getZ(), data));
            }

            ListTag names = new ListTag();
            for (BlockState state : this.palette.keySet()) {
                names.add(StringTag.valueOf(JakDiff.name(state)));
            }
            String date = LocalDateTime.now().withNano(0).toString();
            CompoundTag tag = new CompoundTag();
            tag.putInt("format", JakDiff.FORMAT);
            tag.putString("kind", NAME);
            tag.putString("room", NAME);
            tag.putInt("number", 0);
            tag.putString("volume", Haven.VOLUME);
            tag.putString("sha1", this.sha1);
            tag.put("origin", new IntArrayTag(new int[]{this.origin.getX(), this.origin.getY(), this.origin.getZ()}));
            tag.put("box_min", new IntArrayTag(new int[]{0, 0, 0}));
            tag.put("box_max", new IntArrayTag(new int[]{this.width - 1, this.height - 1, this.depth - 1}));
            tag.putString("captured", date);
            tag.putString("author", this.author);
            tag.put("palette", names);
            tag.put("cells", this.cells);
            tag.put("entities", entities);
            NbtUtils.addCurrentDataVersion(tag);

            long millis = (System.nanoTime() - this.started) / 1_000_000L;
            List<String> text = new ArrayList<>();
            text.add("Releve de la ville entiere, " + date + ", par " + this.author);
            text.add("volume " + Haven.VOLUME + ", sha1 " + this.sha1 + ", origine " + this.origin.toShortString()
                    + ", grille " + this.width + " x " + this.height + " x " + this.depth + ", " + chunks()
                    + " troncons lus en " + millis + " ms (" + this.ticks + " tiques)");
            text.add(this.cells.size() + " cellule(s) changee(s), dont " + this.blockEntities
                    + " avec entite de bloc ; " + entities.size() + " decor(s) ; " + this.managedCells
                    + " cellule(s) du mod ignoree(s) (borne, bouton, cables vides, portes d'office)");
            text.add("Par bloc :");
            this.perBlock.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(e -> text.add(String.format(Locale.ROOT, "  %6d  %s", e.getValue(), e.getKey())));
            text.add("Chaque ligne : cellule du volume (et bloc du monde) : etat de la ville -> etat releve.");
            text.addAll(this.cellLines);
            text.add(entities.size() + " decor(s) :");
            text.addAll(entityLines);

            Path file = this.directory.resolve(NAME + ".nbt");
            try {
                Files.createDirectories(this.directory);
                NbtIo.writeCompressed(tag, file);
                Files.write(this.directory.resolve(NAME + ".txt"), text, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LOGGER.error("releve de la ville : ecriture impossible dans {}", this.directory, e);
                this.error = e.toString();
                file = null;
            }
            this.result = new Result(this.error == null, file, tag, this.cells.size(), this.blockEntities,
                    entities.size(), this.managedCells, chunks(), millis, this.ticks, this.error);
        }

        void finish() {
            Result done = this.result != null ? this.result
                    : new Result(false, null, null, this.cells.size(), this.blockEntities, 0, this.managedCells,
                    chunks(), (System.nanoTime() - this.started) / 1_000_000L, this.ticks, this.error);
            if (done.ok()) {
                LOGGER.info("releve de la ville : {} cellules, {} entites de bloc, {} decors, {} ms, dans {}",
                        done.cells(), done.blockEntities(), done.entities(), done.millis(), done.file());
                tell(Component.translatable("command.emeraldweapons.haven.atelier.releve.done", done.cells(),
                        done.blockEntities(), done.entities(), String.valueOf(done.file())));
            } else {
                tell(Component.translatable("command.emeraldweapons.haven.atelier.releve.failed",
                        String.valueOf(done.error())), ChatFormatting.RED);
            }
            if (this.onDone != null) {
                this.onDone.accept(done);
            }
        }

        private void tell(Component message) {
            tell(message, ChatFormatting.AQUA);
        }

        private void tell(Component message, ChatFormatting color) {
            if (this.watcher == null) {
                return;
            }
            ServerPlayer player = this.server.getPlayerList().getPlayer(this.watcher);
            if (player != null) {
                player.sendSystemMessage(message.copy().withStyle(color));
            }
        }
    }
}
