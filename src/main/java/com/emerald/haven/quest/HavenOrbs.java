package com.emerald.haven.quest;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenState;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.init.Jak3Registry;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.HavenOrbsPayload;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * LES ORBES PRECURSEURS (lot 3, cahier §86) : la monnaie de Haven.
 *
 * « Les quetes paient en orbes -- la fameuse monnaie de Jak, les gemmes orange rondes. Au lieu
 * des secrets, les orbes achetent les armes, puis des ameliorations. Des orbes partout dans la
 * ville, a ramasser, pour pousser a explorer. » (le joueur, §79.7)
 *
 * Le solde de chaque joueur est sur sa fiche (HavenProgress), comme les orbes caches deja
 * trouves. Les 150 orbes caches (jak/haven_orbs.json, tools/haven_orbs_map.py) sont des
 * entites HavenOrbEntity posees quand leur troncon est charge ; chacun ne se montre qu'a qui
 * ne l'a pas encore pris, et vaut un orbe. Le compteur s'affiche en haut a droite de l'ecran
 * dans Haven (HavenOrbsHud), avec les orbes caches trouves.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenOrbs {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
            "jak/haven_orbs.json");

    /** Un orbe cache : son numero, sa cellule, son genre de lieu. */
    public record Spot(int index, BlockPos cell, String place) {
    }

    @Nullable
    private static ResourceManager readFrom;
    private static List<Spot> cached = List.of();
    /** L'entite de chaque orbe pose. */
    private static final Map<Integer, UUID> ALIVE = new HashMap<>();
    private static int ticks;

    private HavenOrbs() {
    }

    /** Les orbes caches de la ville. */
    public static synchronized List<Spot> spots(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        if (manager != readFrom) {
            readFrom = manager;
            cached = read(manager);
        }
        return cached;
    }

    public static int total(MinecraftServer server) {
        return spots(server).size();
    }

    private static List<Spot> read(ResourceManager manager) {
        Optional<Resource> found = manager.getResource(FILE);
        if (found.isEmpty()) {
            LOGGER.error("orbes de Haven : {} absent", FILE);
            return List.of();
        }
        try (Reader reader = found.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            List<Spot> out = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("orbes")) {
                JsonObject o = e.getAsJsonObject();
                var cell = o.getAsJsonArray("cellule");
                out.add(new Spot(out.size(), new BlockPos(cell.get(0).getAsInt(), cell.get(1).getAsInt(), cell.get(2).getAsInt()),
                        o.get("lieu").getAsString()));
            }
            LOGGER.info("orbes de Haven : {} lu, {} orbes caches", FILE, out.size());
            return List.copyOf(out);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("orbes de Haven : {} illisible", FILE, e);
            return List.of();
        }
    }

    /** La place d'un orbe dans le monde (le bas de l'entite). */
    public static Vec3 position(MinecraftServer server, Spot spot) {
        BlockPos origin = HavenState.get(server).origin();
        return Vec3.atBottomCenterOf(origin.offset(spot.cell())).add(0.0, 0.25, 0.0);
    }

    // ================================================================ la tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks % 20 != 11) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return;
        }
        if (!HavenInvasion.cityOpen(server)) {
            if (!ALIVE.isEmpty()) {
                removeAll(level);
            }
            return;
        }
        List<ServerPlayer> seekers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                seekers.add(player);
            }
        }
        seekers.addAll(HavenQuests.TEST_PLAYERS.values());
        if (seekers.isEmpty()) {
            return;
        }
        for (Spot spot : spots(server)) {
            Vec3 at = position(server, spot);
            BlockPos block = BlockPos.containing(at);
            if (!level.isLoaded(block) || !level.areEntitiesLoaded(ChunkPos.asLong(block))) {
                continue;
            }
            UUID id = ALIVE.get(spot.index());
            Entity existing = id == null ? null : level.getEntity(id);
            boolean wanted = false;
            for (ServerPlayer seeker : seekers) {
                if (!HavenProgress.found(seeker.getUUID(), spot.index())) {
                    wanted = true;
                    break;
                }
            }
            if (existing != null && !existing.isRemoved()) {
                if (!wanted) {
                    existing.discard();
                    ALIVE.remove(spot.index());
                }
                continue;
            }
            if (!wanted) {
                continue;
            }
            HavenOrbEntity orb = Jak3Registry.HAVEN_ORB.get().create(level);
            if (orb == null) {
                continue;
            }
            orb.setIndex(spot.index());
            orb.moveTo(at.x, at.y, at.z, spot.index() * 37.0F % 360.0F, 0.0F);
            if (level.addFreshEntity(orb)) {
                ALIVE.put(spot.index(), orb.getUUID());
            }
        }
    }

    /** Un joueur touche un orbe : s'il ne l'avait pas, il le prend -- lui seul le voit disparaitre. */
    static void collect(ServerLevel level, HavenOrbEntity orb, ServerPlayer player) {
        int index = orb.index();
        if (index < 0 || !Haven.is(player.level()) || !HavenProgress.markFound(player.getUUID(), index)) {
            return;
        }
        int balance = HavenProgress.addOrbs(player.getUUID(), 1);
        if (!player.isFakePlayer()) {
            player.connection.send(new ClientboundRemoveEntitiesPacket(orb.getId()));
        }
        com.emerald.haven.journey.HavenJourney.award(player, "haven_orbe");
        int found = HavenProgress.foundCount(player.getUUID());
        int total = total(level.getServer());
        player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.orbe.pris", found, total, balance)
                .withStyle(ChatFormatting.GOLD), true);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.6F);
        level.sendParticles(player, ParticleTypes.END_ROD, true, orb.getX(), orb.getY() + 0.3, orb.getZ(), 12,
                0.2, 0.3, 0.2, 0.05);
        sync(player);
        if (found == total) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.orbe.tous", total)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        }
    }

    /** Le compteur du joueur, pour son ecran. */
    public static void sync(ServerPlayer player) {
        if (player.isFakePlayer() || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new HavenOrbsPayload(HavenProgress.orbs(player.getUUID()),
                HavenProgress.foundCount(player.getUUID()), total(player.server)));
    }

    /** Tous les orbes du niveau s'en vont -- ceux que l'on suit, et ceux qu'on ne suit plus. */
    public static void removeAll(ServerLevel level) {
        for (HavenOrbEntity orb : level.getEntities(
                net.minecraft.world.level.entity.EntityTypeTest.forClass(HavenOrbEntity.class), orb -> true)) {
            orb.discard();
        }
        ALIVE.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ALIVE.clear();
        readFrom = null;
        cached = List.of();
        ticks = 0;
    }
}
