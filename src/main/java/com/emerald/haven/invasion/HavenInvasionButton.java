package com.emerald.haven.invasion;

import com.emerald.block.HavenInvasionButtonBlock;
import com.emerald.block.ModBlocks;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenState;
import com.emerald.haven.HavenVote;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Le bouton du QG a sa place : sur le bout du comptoir du Hip Hog (haven_invasion.json, bouton_qg).
 *
 * POSE ET RETIRE COMME LA BORNE, avec la meme condition (HavenVote.voteBlockWanted :
 * ville finie, phase ACCUEIL) : quand ce qu'on veut change -- fin de pose, debut
 * d'un rebuild, reouverture, depart -- on charge le troncon une fois et on pose ou
 * retire tout de suite ; sinon un controle d'une seconde, troncon deja charge. La
 * premiere tique du serveur fait aussi ce passage charge : une pose bloquante finie
 * pendant le demarrage n'a pas vu le bouton.
 *
 * Le voyant (PEACEFUL) suit le mode de la ville.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenInvasionButton {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** 2|16 : ni voisins prevenus, ni formes recalculees. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @Nullable
    private static Boolean lastWanted;
    private static int ticks;
    private static boolean warnedBlocked;

    private HavenInvasionButton() {
    }

    /** La place du bouton dans le monde, ou null si la carte n'en a pas. */
    @Nullable
    public static BlockPos position(MinecraftServer server) {
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        if (data == null || data.button() == null) {
            return null;
        }
        return HavenState.get(server).origin().offset(data.button().cell());
    }

    /** Le bouton doit etre la : ville finie, lobby en attente de joueurs. */
    public static boolean wanted(MinecraftServer server) {
        return HavenVote.voteBlockWanted(HavenState.get(server)) && position(server) != null;
    }

    /** L'etat voulu : tourne comme la carte le dit, voyant selon le mode. */
    public static BlockState wantedState(MinecraftServer server) {
        HavenInvasionData.Data data = HavenInvasionData.get(server);
        BlockState state = ModBlocks.HAVEN_INVASION_BUTTON.get().defaultBlockState()
                .setValue(HavenInvasionButtonBlock.PEACEFUL, HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE);
        return data == null || data.button() == null ? state
                : state.setValue(HavenInvasionButtonBlock.FACING, data.button().facing());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        boolean wanted = wanted(server);
        if (lastWanted == null || lastWanted != wanted) {
            keep(server, true);
        } else if (++ticks % 20 == 0) {
            keep(server, false);
        }
        lastWanted = wanted;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        lastWanted = null;
        ticks = 0;
        warnedBlocked = false;
    }

    /**
     * Pose, met a jour ou retire le bouton.
     *
     * Pose refusee (et signalee une fois) si la case est prise par autre chose que
     * de l'air, un bloc remplacable ou le bouton lui-meme.
     *
     * @param load charger le troncon s'il ne l'est pas
     * @return la place du bouton, ou null si la ville ou la carte manquent
     */
    @Nullable
    public static BlockPos keep(MinecraftServer server, boolean load) {
        ServerLevel level = Haven.level(server);
        BlockPos pos = position(server);
        if (level == null || pos == null) {
            return null;
        }
        if (load) {
            level.getChunkAt(pos);
        } else if (!level.isLoaded(pos)) {
            return pos;
        }
        BlockState current = level.getBlockState(pos);
        Block button = ModBlocks.HAVEN_INVASION_BUTTON.get();
        if (wanted(server)) {
            BlockState want = wantedState(server);
            if (current == want) {
                return pos;
            }
            if (current.is(button) || current.isAir() || current.canBeReplaced()) {
                boolean fresh = !current.is(button);
                level.setBlock(pos, want, QUIET);
                warnedBlocked = false;
                if (fresh) {
                    LOGGER.info("ville de Haven : bouton du QG pose en {}, voyant {}", pos.toShortString(),
                            want.getValue(HavenInvasionButtonBlock.PEACEFUL) ? "bleu" : "rouge");
                }
                return pos;
            }
            if (!warnedBlocked) {
                warnedBlocked = true;
                LOGGER.warn("ville de Haven : bouton du QG non pose, case prise par {} en {}", current, pos.toShortString());
            }
            return pos;
        }
        if (current.is(button)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            LOGGER.info("ville de Haven : bouton du QG retire de {}", pos.toShortString());
        }
        return pos;
    }
}
