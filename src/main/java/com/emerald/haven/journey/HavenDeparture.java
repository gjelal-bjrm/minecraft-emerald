package com.emerald.haven.journey;

import com.emerald.block.ArcPortalBlock;
import com.emerald.block.ArcPortals;
import com.emerald.game.GameState;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenState;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * LE PORTAIL DU DEPART, au QG de Haven (22 sept., cahier §84).
 *
 * « Plutot que de juste les teleporter quand ils ont choisi le defi, il faudrait faire
 * apparaitre un portail qui les permettra de se teleporter dans le village de depart. Ca
 * serait plus joli. Et maintenant, on a les portails, alors on pourrait les exploiter. »
 *
 * A la fin du compte a rebours du vote (HavenVote.depart), une ARCHE s'ouvre dans le Hip
 * Hog, a la place de la borne ou tout pres : son voile ROUGE pour le Defi, BLEU pour le
 * Monde ouvert -- les couleurs des vitres de la borne. Chacun la traverse quand il est pret
 * et arrive au village, kit compris (HavenArrival.toVillage). Quand tous les joueurs de la
 * ville l'ont passee, elle se referme ; au bout de {@link #TIMEOUT} tiques (trois minutes),
 * ceux qui trainent partent d'eux-memes. Le chrono du Defi ne part qu'au retrait de la Lame :
 * rien ne presse.
 *
 * La phase est deja PARTI : qui se connecte pendant ce temps va au village, les voitures
 * s'en vont, la borne aussi. Rien n'est sauvegarde : apres un arret, l'arche s'efface
 * (ArcPortalBlockEntity) et les joueurs de la ville partent au village a la connexion.
 * Sans place pour l'arche -- une ville posee autrement --, le depart se fait d'un coup,
 * comme avant.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenDeparture {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Trois minutes pour passer l'arche, puis ceux qui restent partent d'eux-memes. */
    public static final int TIMEOUT = 20 * 60 * 3;
    /** Le rappel dans la barre d'action, en tiques. */
    private static final int REMINDER = 100;
    /** L'arche reste au moins cela, meme sans personne a faire passer. */
    private static final int MIN_OPEN = 20;

    @Nullable
    private static BlockPos gate;
    private static Direction facing = Direction.SOUTH;
    private static boolean kit;
    private static long openedAt;
    private static int passed;
    /** Qui etait dans le voile a la tique precedente (on part en y ENTRANT). */
    private static final Set<UUID> wasInside = new HashSet<>();

    private HavenDeparture() {
    }

    /** L'arche du depart est-elle ouverte ? */
    public static boolean isOpen() {
        return gate != null;
    }

    /** Cette arche est-elle celle du depart ? (ArcPortalBlockEntity efface les autres.) */
    public static boolean isGate(Level level, BlockPos pos) {
        return gate != null && gate.equals(pos) && Haven.is(level);
    }

    /** Pour les essais : l'arche, ou null. */
    @Nullable
    public static BlockPos gate() {
        return gate;
    }

    /** Pour les essais : le cote vers lequel elle regarde. */
    public static Direction facing() {
        return facing;
    }

    /**
     * Ouvre l'arche du depart, APRES le retrait de la borne (sa place peut accueillir l'arche).
     *
     * @return faux s'il n'y a pas de place : le depart se fait alors d'un coup (HavenVote)
     */
    public static boolean open(MinecraftServer server, GameState.Mode mode, boolean withKit) {
        ServerLevel haven = Haven.level(server);
        HavenArrival.Layout rooms = HavenArrival.layout(server);
        if (haven == null || rooms == null) {
            return false;
        }
        close(server);
        ArcPortals.Placement where = spot(haven, server, rooms);
        if (where == null) {
            LOGGER.warn("ville de Haven : aucune place pour l'arche du depart au QG ; depart d'un coup");
            return false;
        }
        gate = where.anchor();
        facing = where.facing();
        kit = withKit;
        openedAt = haven.getGameTime();
        passed = 0;
        wasInside.clear();
        ArcPortals.place(haven, gate, facing, mode == GameState.Mode.DEFI ? ArcPortalBlock.Tint.DEFI : ArcPortalBlock.Tint.LIBRE);
        Component line = Component.translatable("game.emeraldweapons.haven.depart.open")
                .withStyle(mode == GameState.Mode.DEFI ? ChatFormatting.GOLD : ChatFormatting.AQUA);
        for (ServerPlayer player : cityPlayers(haven)) {
            // qui se trouve deja la ou l'arche s'ouvre n'est pas emporte d'emblee : il en sort, il y revient
            if (ArcPortals.inVeil(player, gate, facing)) {
                wasInside.add(player.getUUID());
            }
            player.sendSystemMessage(line);
            player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        LOGGER.info("ville de Haven : arche du depart ouverte en {} (regarde {}), vers le village en {}",
                gate, facing, mode);
        return true;
    }

    /** Referme l'arche (tous passes, delai ecoule, reouverture du lobby, arret). */
    public static void close(MinecraftServer server) {
        if (gate == null) {
            return;
        }
        ServerLevel haven = Haven.level(server);
        if (haven != null) {
            ArcPortals.remove(haven, gate);
        }
        LOGGER.info("ville de Haven : arche du depart refermee, {} joueur(s) passe(s)", passed);
        gate = null;
        wasInside.clear();
    }

    /**
     * La place de l'arche : dans le bar, du plus pres de la borne au plus loin ; a defaut,
     * dans la rue devant sa porte. Elle regarde le centre du bar : on la voit en entrant.
     */
    @Nullable
    private static ArcPortals.Placement spot(ServerLevel level, MinecraftServer server, HavenArrival.Layout rooms) {
        BlockPos origin = HavenState.get(server).origin();
        BlockPos vote = rooms.votePos(origin);
        BlockPos centre = rooms.hqCenter(origin);
        for (int r = 0; r <= 14; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    ArcPortals.Placement found = ArcPortals.at(level, vote.offset(dx, 0, dz), 2, 2, centre);
                    if (found != null && rooms.inHq(origin, found.anchor().getX() + 0.5, found.anchor().getY(),
                            found.anchor().getZ() + 0.5)) {
                        return found;
                    }
                }
            }
        }
        BlockPos street = origin.offset(Haven.BAR_FRONT_CELL);
        return ArcPortals.nearest(level, street, 6, 2, origin.offset(Haven.BAR_DOOR_CELL));
    }

    /** Ceux que l'arche doit emmener : les joueurs de la ville, sauf l'operateur en chantier. */
    private static List<ServerPlayer> cityPlayers(ServerLevel haven) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : haven.players()) {
            if (!player.isFakePlayer() && !player.isSpectator() && !HavenRules.chantier(player)) {
                out.add(player);
            }
        }
        return out;
    }

    /** Un joueur passe l'arche : au village, kit compris, avec l'annonce du village pour lui. */
    private static void pass(MinecraftServer server, ServerPlayer player) {
        HavenArrival.toVillage(player, kit);
        passed++;
        ServerLevel overworld = server.overworld();
        BlockPos village = GameState.get(overworld).village();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("game.emeraldweapons.village_intro")
                .withStyle(style -> style.withColor(0x9CE8FF))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("game.emeraldweapons.village_intro.sub")
                .withStyle(ChatFormatting.GRAY)));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.MASTER, 0.8F, 1.2F);
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.locked.where",
                village.getX(), village.getY(), village.getZ(), 0).withStyle(ChatFormatting.AQUA));
        LOGGER.info("ville de Haven : {} passe l'arche du depart", player.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (gate == null) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel haven = Haven.level(server);
        if (haven == null) {
            gate = null;
            return;
        }
        long now = haven.getGameTime();
        List<ServerPlayer> remaining = new ArrayList<>();
        for (ServerPlayer player : cityPlayers(haven)) {
            UUID id = player.getUUID();
            boolean inside = ArcPortals.inVeil(player, gate, facing);
            if (inside && !wasInside.contains(id)) {
                wasInside.remove(id);
                pass(server, player);
                continue;
            }
            if (inside) {
                wasInside.add(id);
            } else {
                wasInside.remove(id);
            }
            remaining.add(player);
        }
        if (remaining.isEmpty() && now - openedAt >= MIN_OPEN) {
            close(server);
            return;
        }
        if (now - openedAt >= TIMEOUT) {
            for (ServerPlayer player : remaining) {
                player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.depart.late")
                        .withStyle(ChatFormatting.GRAY));
                pass(server, player);
            }
            close(server);
            return;
        }
        if (now % REMINDER == 0) {
            Component line = Component.translatable("game.emeraldweapons.haven.depart.reminder", passed,
                    passed + remaining.size()).withStyle(ChatFormatting.YELLOW);
            for (ServerPlayer player : remaining) {
                player.displayClientMessage(line, true);
            }
        }
        if (now % 10 == 0) {
            haven.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                    gate.getX() + 0.5, gate.getY() + 1.6, gate.getZ() + 0.5, 3, 0.35, 0.8, 0.35, 0.01);
        }
    }

    /** A l'arret, l'arche s'en va AVANT la sauvegarde : elle ne reste pas dans le monde. */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        close(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        gate = null;
        wasInside.clear();
    }
}
