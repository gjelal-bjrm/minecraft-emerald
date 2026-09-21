package com.emerald.haven;

import com.emerald.jak.JakBuilder;
import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.Clearable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Le rejeu des amenagements : chaque salle relevee, reposee apres la ville.
 *
 * C'est le calque des sanctuaires (SanctuaryOverlay) sans ses deux limites :
 * un tableau Java plafonnait vers deux mille trois cents cases et ne portait ni
 * NBT ni entites. Ici, une salle est un fichier .nbt de
 * data/emeraldweapons/jak/zones/ctyport/, copie depuis le releve par
 * tools/jak_zone_apply.py, lu par le gestionnaire de ressources.
 *
 * L'ORDRE COMPTE, et chaque etape repond a un piege mesure dans les sources :
 *   1. les decors deja la sont RETIRES : une pose sans vidage (build) ou un
 *      rejeu repete les dupliquerait sinon ;
 *   2. les CONTENEURS SONT VIDES avant d'etre remplaces : LevelChunk appelle
 *      onRemove quels que soient les drapeaux, et un coffre remplace lache
 *      son contenu au sol (Containers.dropContentsOnDestroy) ;
 *   3. les etats sont poses au drapeau 2|16 : sans le 16, la pose recalcule la
 *      forme des voisins, et une moitie de porte, un lit ou une torche poses
 *      avant leur support seraient detruits en lachant leur objet ;
 *   4. le NBT des entites de bloc est recharge, et le client prevenu ;
 *   5. une passe de LIAISON refait la forme des murets, vitres, barreaux et
 *      barrieres d'apres leurs voisins, sans prevenir personne -- le modele est
 *      Sanctuary.linkWalls ; les etats noyes sont laisses tels quels, parce que
 *      leur mise a jour de forme planifierait l'eau ;
 *   6. les ecoulements et ticks planifies par la pose dans la boite sont
 *      oublies : sable ou eau poses la ne partiront pas au premier passage ;
 *   7. les decors sont recrees, a leur place exacte.
 *
 * UNE SALLE SE REFUSE, ELLE NE SE POSE PAS A COTE. Si le sha1 du volume ou
 * l'origine de pose ne sont pas ceux du releve, les cellules ne designent plus
 * les memes blocs : la salle n'est pas posee, et les operateurs sont prevenus.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class JakOverlay {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /**
     * Le ticket qui tient une salle chargee le temps que ses entites arrivent.
     *
     * Distinct de celui de JakBuilder : deux tickets de meme type, meme niveau
     * et meme troncon n'en font qu'un, et le chantier rend le sien juste apres
     * le rappel de fin -- il aurait emporte le notre.
     */
    public static final TicketType<ChunkPos> TICKET = TicketType.create("arcencium_haven_salle",
            Comparator.comparingLong(ChunkPos::toLong));

    /** Tiques d'attente des entites avant de passer outre, comme JakBuilder. */
    private static final int ENTITY_WAIT = 400;

    /** Ce qu'a donne le rejeu d'une salle. */
    public record Result(String room, @Nullable Component refused, int placed, int blockEntities,
                         int entities, int unreadable, int flowing, boolean deferred) {
    }

    /** Une salle posee dont les decors attendent le chargement des entites de leurs troncons. */
    private static final class Pending {
        final ServerLevel level;
        final String room;
        final CompoundTag zone;
        final BlockPos min;
        final BlockPos max;
        final AABB box;
        final List<ChunkPos> chunks = new ArrayList<>();
        int waited;

        Pending(ServerLevel level, String room, CompoundTag zone, BlockPos min, BlockPos max, AABB box) {
            this.level = level;
            this.room = room;
            this.zone = zone;
            this.min = min;
            this.max = max;
            this.box = box;
            for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
                for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                    ChunkPos pos = new ChunkPos(cx, cz);
                    this.chunks.add(pos);
                    level.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
                }
            }
        }

        void release() {
            for (ChunkPos pos : this.chunks) {
                this.level.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            }
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /** Les salles du banc d'essai, a la place de celles du mod ; null en jeu. */
    @Nullable
    private static Map<String, CompoundTag> testZones;

    private static List<Result> lastResults = List.of();

    private JakOverlay() {
    }

    /**
     * Remplace les salles du mod par celles du banc d'essai, ou les rend (null).
     *
     * Le releve d'essai ne doit pas passer par src/main/resources : il resterait
     * dans le mod et se rejouerait dans toutes les parties.
     */
    public static void setTestZones(@Nullable Map<String, CompoundTag> zones) {
        testZones = zones;
    }

    /** Le compte rendu du dernier rejeu, salle par salle. */
    public static List<Result> lastResults() {
        return lastResults;
    }

    /** Le nombre de salles dont les decors attendent encore. */
    public static int pending() {
        return PENDING.size();
    }

    // ------------------------------------------------------------ lecture

    /**
     * Les salles du mod pour un volume, par nom : data/emeraldweapons/jak/zones/<volume>/*.nbt.
     */
    public static Map<String, CompoundTag> zones(MinecraftServer server, String volume) {
        if (testZones != null) {
            return new TreeMap<>(testZones);
        }
        Map<String, CompoundTag> out = new TreeMap<>();
        String folder = "jak/zones/" + volume;
        Map<ResourceLocation, Resource> found = server.getResourceManager()
                .listResources(folder, path -> path.getPath().endsWith(".nbt"));
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            if (!entry.getKey().getNamespace().equals(EmeraldWeaponsMod.MODID)) {
                continue;
            }
            String path = entry.getKey().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".nbt".length());
            try (InputStream in = entry.getValue().open()) {
                out.put(name, NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap()));
            } catch (IOException | RuntimeException e) {
                LOGGER.error("amenagement {} illisible : {}", entry.getKey(), e.toString());
            }
        }
        return out;
    }

    /** L'amenagement du mod pour une salle, ou null. */
    @Nullable
    public static CompoundTag zone(MinecraftServer server, String room) {
        return zones(server, Haven.VOLUME).get(room);
    }

    // ------------------------------------------------------------ rejeu

    /**
     * Rejoue toutes les salles, a la fin d'une pose de la ville.
     *
     * Appele par HavenSite dans le rappel de fin de JakBuilder : les troncons de
     * la boite sont encore tenus, les blocs se posent sans rien charger.
     */
    public static void replayAll(MinecraftServer server, ServerLevel level, JakVolume volume, BlockPos origin,
                                 String sha1, @Nullable ServerPlayer watcher) {
        Map<String, CompoundTag> zones = zones(server, Haven.VOLUME);
        CompoundTag city = zones.get(JakCityCapture.NAME);
        if (city != null && zones.size() > 1) {
            // LA VILLE ENTIERE REMPLACE LES SALLES : son releve (JakCityCapture) couvre toute la
            // grille, appartements compris, et il est complet. Une salle relevee a part serait plus
            // ancienne ou redondante ; rejouee apres, elle defairait ce que la ville a de plus recent.
            LOGGER.info("amenagements : le releve de la ville entiere remplace {} salle(s) : {}",
                    zones.size() - 1, zones.keySet());
            zones = new TreeMap<>(Map.of(JakCityCapture.NAME, city));
        }
        List<Result> results = new ArrayList<>();
        int placed = 0;
        int entities = 0;
        for (Map.Entry<String, CompoundTag> entry : zones.entrySet()) {
            Result result;
            try {
                result = apply(level, entry.getKey(), entry.getValue(), volume, origin, sha1);
            } catch (RuntimeException e) {
                LOGGER.error("amenagement {} : le rejeu a echoue", entry.getKey(), e);
                result = new Result(entry.getKey(), Component.translatable(
                        "command.emeraldweapons.haven.salle.refused.error", entry.getKey(), e.toString()),
                        0, 0, 0, 0, 0, false);
            }
            results.add(result);
            if (result.refused() != null) {
                tellOperators(server, result.refused(), watcher);
            } else {
                placed += result.placed();
                entities += result.entities();
            }
        }
        lastResults = List.copyOf(results);
        if (!zones.isEmpty()) {
            LOGGER.info("amenagements rejoues : {} salle(s), {} bloc(s), {} decor(s) poses tout de suite",
                    zones.size(), placed, entities);
            if (watcher != null) {
                watcher.sendSystemMessage(Component.translatable("command.emeraldweapons.haven.salle.replayed",
                        zones.size(), placed, entities).withStyle(ChatFormatting.AQUA));
            }
        }
    }

    /**
     * Rejoue une salle.
     *
     * Tous les controles passent AVANT la premiere ecriture : une salle refusee
     * ne touche rien, pas meme ses decors.
     *
     * @param sha1   le sha1 du volume pose
     * @param origin l'origine de la pose
     */
    public static Result apply(ServerLevel level, String name, CompoundTag zone, JakVolume volume,
                               BlockPos origin, String sha1) {
        if (zone.getInt("format") != JakDiff.FORMAT) {
            return refuse(name, Component.translatable("command.emeraldweapons.haven.salle.refused.format",
                    name, zone.getInt("format")));
        }
        String zoneSha1 = zone.getString("sha1");
        if (sha1.isEmpty() || !zoneSha1.equals(sha1)) {
            return refuse(name, Component.translatable("command.emeraldweapons.haven.salle.refused.sha1",
                    name, zoneSha1, sha1));
        }
        int[] zoneOrigin = JakDiff.ints(zone, "origin");
        if (zoneOrigin == null || zoneOrigin[0] != origin.getX() || zoneOrigin[1] != origin.getY()
                || zoneOrigin[2] != origin.getZ()) {
            return refuse(name, Component.translatable("command.emeraldweapons.haven.salle.refused.origin",
                    name, zoneOrigin == null ? "?" : zoneOrigin[0] + " " + zoneOrigin[1] + " " + zoneOrigin[2],
                    origin.toShortString()));
        }
        int[] lo = JakDiff.ints(zone, "box_min");
        int[] hi = JakDiff.ints(zone, "box_max");
        if (lo == null || hi == null || lo[0] < 0 || lo[1] < 0 || lo[2] < 0 || hi[0] >= volume.width()
                || hi[1] >= volume.height() || hi[2] >= volume.depth() || lo[0] > hi[0] || lo[1] > hi[1]
                || lo[2] > hi[2]) {
            return refuse(name, Component.translatable("command.emeraldweapons.haven.salle.refused.box", name));
        }
        HavenRooms.Box box = new HavenRooms.Box(new BlockPos(lo[0], lo[1], lo[2]), new BlockPos(hi[0], hi[1], hi[2]));
        BlockPos min = JakDiff.worldMin(origin, box);
        BlockPos max = JakDiff.worldMax(origin, box);
        AABB aabb = JakDiff.aabb(origin, box);

        // la palette : un etat illisible est journalise et sa cellule sautee
        ListTag names = zone.getList("palette", Tag.TAG_STRING);
        BlockState[] palette = new BlockState[names.size()];
        int unreadable = 0;
        for (int i = 0; i < names.size(); i++) {
            String state = names.getString(i);
            if (state.indexOf(':') < 0) {
                LOGGER.warn("amenagement {} : etat sans espace de noms « {} », saute", name, state);
                continue;
            }
            try {
                palette[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false)
                        .blockState();
            } catch (CommandSyntaxException e) {
                LOGGER.warn("amenagement {} : etat illisible « {} » ({}), saute", name, state, e.getMessage());
            }
        }

        boolean loaded = JakDiff.entitiesLoaded(level, min, max);
        if (loaded) {
            removeDecor(level, aabb);
        }

        // les etats
        ListTag cells = zone.getList("cells", Tag.TAG_COMPOUND);
        List<BlockPos> posed = new ArrayList<>();
        List<CompoundTag> withData = new ArrayList<>();
        int flowing = 0;
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag cell = cells.getCompound(i);
            int[] rel = JakDiff.ints(cell, "pos");
            int index = cell.getInt("state");
            if (rel == null || rel[0] < 0 || rel[1] < 0 || rel[2] < 0 || rel[0] > hi[0] - lo[0]
                    || rel[1] > hi[1] - lo[1] || rel[2] > hi[2] - lo[2]
                    || index < 0 || index >= palette.length || palette[index] == null) {
                unreadable++;
                continue;
            }
            BlockState state = palette[index];
            if (!state.getFluidState().isEmpty() && !state.getFluidState().isSource()) {
                // l'eau qui coule n'est jamais un amenagement : elle noierait la salle
                flowing++;
                continue;
            }
            BlockPos pos = min.offset(rel[0], rel[1], rel[2]);
            Clearable.tryClear(level.getBlockEntity(pos));
            level.setBlock(pos, state, JakBuilder.FLAGS);
            posed.add(pos);
            if (cell.contains("nbt", Tag.TAG_COMPOUND)) {
                withData.add(cell);
            }
        }

        // les entites de bloc : contenu des coffres, texte des panneaux...
        int blockEntities = 0;
        for (CompoundTag cell : withData) {
            int[] rel = JakDiff.ints(cell, "pos");
            BlockPos pos = min.offset(rel[0], rel[1], rel[2]);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            CompoundTag data = cell.getCompound("nbt").copy();
            if (blockEntity == null) {
                LOGGER.warn("amenagement {} : pas d'entite de bloc en {} pour {}", name, pos.toShortString(),
                        data.getString("id"));
                unreadable++;
                continue;
            }
            ResourceLocation type = BlockEntityType.getKey(blockEntity.getType());
            if (data.contains("id") && type != null && !type.toString().equals(data.getString("id"))) {
                LOGGER.warn("amenagement {} : entite de bloc {} en {}, le releve dit {} ; NBT saute", name, type,
                        pos.toShortString(), data.getString("id"));
                unreadable++;
                continue;
            }
            data.putInt("x", pos.getX());
            data.putInt("y", pos.getY());
            data.putInt("z", pos.getZ());
            blockEntity.loadWithComponents(data, level.registryAccess());
            blockEntity.setChanged();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            blockEntities++;
        }

        link(level, posed);
        forgetTicks(level, min, max);

        int entities = 0;
        if (loaded) {
            entities = spawn(level, name, zone, min);
        } else {
            PENDING.add(new Pending(level, name, zone.copy(), min, max, aabb));
        }
        if (unreadable > 0) {
            LOGGER.warn("amenagement {} : {} cellule(s) ou entite(s) de bloc sautee(s), voir plus haut", name,
                    unreadable);
        }
        LOGGER.info("amenagement {} rejoue : {} bloc(s), {} entite(s) de bloc, {} decor(s){}{}", name,
                posed.size(), blockEntities, entities, loaded ? "" : " (decors en attente du chargement)",
                flowing > 0 ? ", " + flowing + " eau(x) qui coule ecartee(s)" : "");
        return new Result(name, null, posed.size(), blockEntities, entities, unreadable, flowing, !loaded);
    }

    private static Result refuse(String name, Component reason) {
        LOGGER.warn("amenagement {} refuse : {}", name, reason.getString());
        return new Result(name, reason, 0, 0, 0, 0, 0, false);
    }

    /**
     * La passe de liaison, sans prevenir les voisins.
     *
     * Un muret, une vitre ou une barriere portent leurs connexions dans leur
     * etat ; on les recalcule d'apres ce qui les entoure maintenant, et on ne
     * pose que ce qui change, au drapeau 2|16.
     */
    private static void link(ServerLevel level, List<BlockPos> posed) {
        for (BlockPos pos : posed) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof WallBlock || state.getBlock() instanceof CrossCollisionBlock)) {
                continue;
            }
            if (state.hasProperty(BlockStateProperties.WATERLOGGED)
                    && state.getValue(BlockStateProperties.WATERLOGGED)) {
                continue;
            }
            BlockState linked = Block.updateFromNeighbourShapes(state, level, pos);
            if (linked != state) {
                level.setBlock(pos, linked, JakBuilder.FLAGS);
            }
        }
    }

    /** Oublie les ticks de blocs et de fluides planifies dans la boite. */
    private static void forgetTicks(ServerLevel level, BlockPos min, BlockPos max) {
        BoundingBox area = new BoundingBox(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ());
        level.getBlockTicks().clearArea(area);
        level.getFluidTicks().clearArea(area);
    }

    /** Retire les decors de la boite, sans rien lacher. */
    private static int removeDecor(ServerLevel level, AABB box) {
        int removed = 0;
        for (Entity entity : JakDiff.decorIn(level, box)) {
            entity.discard();
            removed++;
        }
        return removed;
    }

    /**
     * Recree les decors d'une salle.
     *
     * La position est reecrite dans le NBT -- Pos, et TileX/Y/Z pour ce qui
     * s'accroche -- avant la lecture, et l'on n'appelle PAS moveTo : pour un
     * tableau, moveTo recalcule le bloc d'accroche depuis le centre, qui tombe
     * sur le bloc voisin pour les tableaux de largeur paire. L'UUID est retire :
     * deux decors ne partagent pas un identifiant.
     */
    private static int spawn(ServerLevel level, String name, CompoundTag zone, BlockPos min) {
        ListTag list = zone.getList("entities", Tag.TAG_COMPOUND);
        int spawned = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ListTag rel = entry.getList("pos", Tag.TAG_DOUBLE);
            if (rel.size() != 3 || !entry.contains("nbt", Tag.TAG_COMPOUND)) {
                LOGGER.warn("amenagement {} : decor {} illisible", name, i);
                continue;
            }
            CompoundTag data = entry.getCompound("nbt").copy();
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(min.getX() + rel.getDouble(0)));
            pos.add(DoubleTag.valueOf(min.getY() + rel.getDouble(1)));
            pos.add(DoubleTag.valueOf(min.getZ() + rel.getDouble(2)));
            data.put("Pos", pos);
            data.remove("UUID");
            int[] block = JakDiff.ints(entry, "block");
            if (block != null && data.contains("TileX")) {
                data.putInt("TileX", min.getX() + block[0]);
                data.putInt("TileY", min.getY() + block[1]);
                data.putInt("TileZ", min.getZ() + block[2]);
            }
            Optional<Entity> created = EntityType.create(data, level);
            if (created.isEmpty() || !JakDiff.isDecor(created.get())) {
                LOGGER.warn("amenagement {} : decor {} ({}) non recree", name, i, data.getString("id"));
                continue;
            }
            if (level.addFreshEntity(created.get())) {
                spawned++;
            }
        }
        return spawned;
    }

    // ------------------------------------------------------------ remise a zero

    /**
     * Remet une salle a l'etat de la ville : coque et interieur tels que la pose
     * les laisse, sans decors.
     *
     * @return {blocs remis, decors retires}
     */
    public static int[] resetRoom(ServerLevel level, HavenRooms.Room room, JakVolume volume, BlockPos origin) {
        HavenRooms.Box box = JakDiff.clamp(room.envelope(), volume);
        int decor = removeDecor(level, JakDiff.aabb(origin, box));
        int blocks = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = box.min().getY(); y <= box.max().getY(); y++) {
            for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                for (int x = box.min().getX(); x <= box.max().getX(); x++) {
                    BlockState base = JakDiff.reference(volume, origin, x, y, z);
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState world = level.getBlockState(pos);
                    if (world == base || (world.isAir() && base.isAir())) {
                        continue;
                    }
                    Clearable.tryClear(level.getBlockEntity(pos));
                    level.setBlock(pos, base, JakBuilder.FLAGS);
                    blocks++;
                }
            }
        }
        forgetTicks(level, JakDiff.worldMin(origin, box), JakDiff.worldMax(origin, box));
        return new int[]{blocks, decor};
    }

    // ------------------------------------------------------------ attente des entites

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Pending pending : List.copyOf(PENDING)) {
            boolean ready = JakDiff.entitiesLoaded(pending.level, pending.min, pending.max);
            if (!ready && ++pending.waited < ENTITY_WAIT) {
                continue;
            }
            if (!ready) {
                LOGGER.warn("amenagement {} : entites toujours pas chargees apres {} tiques, decors poses quand meme",
                        pending.room, ENTITY_WAIT);
            }
            PENDING.remove(pending);
            try {
                int removed = removeDecor(pending.level, pending.box);
                int spawned = spawn(pending.level, pending.room, pending.zone, pending.min);
                LOGGER.info("amenagement {} : {} decor(s) poses apres chargement des entites, {} ancien(s) retire(s)",
                        pending.room, spawned, removed);
            } finally {
                pending.release();
            }
        }
    }

    private static void tellOperators(MinecraftServer server, Component message, @Nullable ServerPlayer watcher) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == watcher || server.getPlayerList().isOp(player.getGameProfile())) {
                player.sendSystemMessage(message.copy().withStyle(ChatFormatting.RED));
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        testZones = null;
        lastResults = List.of();
    }
}
