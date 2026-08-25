package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import javax.annotation.Nullable;

/**
 * Prepare le monde tout seul, au premier chargement.
 *
 * Le mode doit se lancer sans qu'aucune commande soit tapee : on cherche donc
 * un VRAI village genere plutot que de poser des villageois dans un champ. Un
 * village existe forcement dans un biome habitable, avec ses maisons et sa
 * lumiere -- deux choses qu'on ne saurait pas improviser aussi bien, et dont
 * l'absence transforme le prologue en survie dans le noir.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class WorldSetup {

    /** Rayon de recherche, en chunks. Au-dela, la generation coute trop cher. */
    private static final int SEARCH_CHUNKS = 96;

    /** Zone nettoyee de ses monstres autour du village avant le depart. */
    private static final int CLEAR_RADIUS = 64;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        GameState state = GameState.get(level);
        if (state.isPrepared()) {
            return;
        }
        BlockPos village = findVillage(level);
        if (village == null) {
            // aucun village a portee : on se rabat sur le point d'apparition,
            // qui reste un endroit viable meme s'il est moins pittoresque
            village = level.getSharedSpawnPos();
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID).info(
                    "Aucun village trouve dans un rayon de {} chunks, repli sur le spawn",
                    SEARCH_CHUNKS);
        }
        GameManager.setup(level, village);
        state.markPrepared();
    }

    /**
     * Le village genere le plus proche de l'origine.
     *
     * On interroge le tag des villages plutot qu'une structure precise : il
     * couvre les cinq villages vanilla et tous ceux qu'ajoutent les mods du
     * modpack, y compris le notre.
     */
    @Nullable
    private static BlockPos findVillage(ServerLevel level) {
        BlockPos from = level.getSharedSpawnPos();
        BlockPos found = level.findNearestMapStructure(StructureTags.VILLAGE, from,
                SEARCH_CHUNKS, false);
        if (found == null) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, found.getX(), found.getZ());
        return new BlockPos(found.getX(), y, found.getZ());
    }

    /**
     * Fait le calme autour du village avant que la partie commence.
     *
     * Sans cela, les monstres deja presents se melent au siege et brouillent le
     * compteur : on ne sait plus ce qu'il reste a tuer.
     */
    public static void clearHostiles(ServerLevel level, BlockPos center) {
        AABB box = new AABB(center).inflate(CLEAR_RADIUS);
        for (Entity entity : level.getEntities(null, box)) {
            if (entity instanceof Enemy) {
                entity.discard();
            }
        }
        level.setDayTime(1000L);          // plein jour : le prologue se joue a la lumiere
    }

    /**
     * Un joueur qui arrive avant le depart est place au village.
     *
     * C'est ce qui permet a un retardataire de rejoindre sans rien manquer :
     * il retrouve la Lame encore plantee, et l'equipe au complet.
     */
    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.status() != GameState.Status.LOBBY
                && state.status() != GameState.Status.PROLOGUE) {
            return;
        }
        BlockPos village = state.village();
        if (village.equals(BlockPos.ZERO)) {
            return;
        }
        player.teleportTo(village.getX() + 0.5, village.getY() + 1, village.getZ() + 0.5);
        player.setRespawnPosition(level.dimension(), village, 0.0F, true, false);
        GameManager.equipStarter(player);
    }
}
