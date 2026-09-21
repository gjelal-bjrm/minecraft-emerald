package com.emerald.haven.journey;

import com.emerald.block.HavenGunRackBlock;
import com.emerald.block.ModBlocks;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenState;
import com.emerald.haven.HavenVote;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.gun.MorphGunKeeper;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import java.util.UUID;

/**
 * Le ratelier d'armes du QG (cahier §81) : il donne le Scatter Gun, une seule fois par
 * joueur -- sa fiche de parcours le retient --, a qui revient de son premier Defi. Avant
 * le premier depart, il reste ferme : la premiere arrivee se fait sans arme (§79).
 *
 * SA PLACE, choisie sur une vue du dessus du Hip Hog en vision nocturne (21 sept.) : sur
 * le socle d'andesite collé derriere le comptoir, deux cellules a l'ouest du bouton
 * (333, 69, 167), tourne vers les clients. Pose sur ce socle, il depasse du comptoir, et
 * on l'atteint d'un clic par-dessus.
 *
 * POSE ET RETIRE COMME LE BOUTON ET LA BORNE (HavenVote.voteBlockWanted : ville finie,
 * phase ACCUEIL) ; le releve de l'atelier l'ignore (JakCityCapture.managed).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenRack {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** La cellule du ratelier, dans le volume de la ville : sur le socle derriere le comptoir. */
    public static final BlockPos CELL = new BlockPos(331, 69, 166);
    /** Tourne vers les clients, de l'autre cote du comptoir. */
    public static final Direction FACING = Direction.SOUTH;

    /** 2|16 : ni voisins prevenus, ni formes recalculees. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @Nullable
    private static Boolean lastWanted;
    private static int ticks;
    private static boolean warnedBlocked;

    private HavenRack() {
    }

    /** Ce que le clic sur le ratelier donne a ce joueur. */
    public enum Take { TAKEN, ALREADY, LOCKED }

    // ================================================================ le clic

    /** Le clic : on revalide, on donne, on le dit. */
    public static Take take(ServerPlayer player, BlockPos pos) {
        UUID id = player.getUUID();
        HavenProgress.Entry entry = HavenProgress.get(id);
        if ((entry.forms & GunForm.RED_1.bit()) != 0) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.ratelier.deja")
                    .withStyle(ChatFormatting.GRAY), true);
            return Take.ALREADY;
        }
        if (entry.departures == 0 && !HavenRules.chantier(player)) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.ratelier.ferme")
                    .withStyle(ChatFormatting.GOLD), true);
            player.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
            return Take.LOCKED;
        }
        HavenProgress.grantForms(id, GunForm.RED_1.bit());
        MorphGunKeeper.guard(player);
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.ratelier.pris")
                .withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        HavenJourney.award(player, "haven_arme");
        return Take.TAKEN;
    }

    // ================================================================ la pose

    /** La place du ratelier dans le monde. */
    public static BlockPos position(MinecraftServer server) {
        return HavenState.get(server).origin().offset(CELL);
    }

    /** Le ratelier doit etre la : ville finie, lobby en attente de joueurs. */
    public static boolean wanted(MinecraftServer server) {
        return Haven.level(server) != null && HavenVote.voteBlockWanted(HavenState.get(server));
    }

    public static BlockState wantedState() {
        return ModBlocks.HAVEN_GUN_RACK.get().defaultBlockState().setValue(HavenGunRackBlock.FACING, FACING);
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
     * Pose ou retire le ratelier. Pose refusee (et signalee une fois) si la case est prise
     * par autre chose que de l'air, un bloc remplacable ou le ratelier lui-meme.
     *
     * @param load charger le troncon s'il ne l'est pas
     * @return la place du ratelier, ou null sans ville
     */
    @Nullable
    public static BlockPos keep(MinecraftServer server, boolean load) {
        ServerLevel level = Haven.level(server);
        if (level == null) {
            return null;
        }
        BlockPos pos = position(server);
        if (load) {
            level.getChunkAt(pos);
        } else if (!level.isLoaded(pos)) {
            return pos;
        }
        BlockState current = level.getBlockState(pos);
        Block rack = ModBlocks.HAVEN_GUN_RACK.get();
        if (wanted(server)) {
            BlockState want = wantedState();
            if (current == want) {
                return pos;
            }
            if (current.is(rack) || current.isAir() || current.canBeReplaced()) {
                boolean fresh = !current.is(rack);
                level.setBlock(pos, want, QUIET);
                warnedBlocked = false;
                if (fresh) {
                    LOGGER.info("ville de Haven : ratelier du QG pose en {}", pos.toShortString());
                }
                return pos;
            }
            if (!warnedBlocked) {
                warnedBlocked = true;
                LOGGER.warn("ville de Haven : ratelier du QG non pose, case prise par {} en {}", current, pos.toShortString());
            }
            return pos;
        }
        if (current.is(rack)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            LOGGER.info("ville de Haven : ratelier du QG retire de {}", pos.toShortString());
        }
        return pos;
    }
}
