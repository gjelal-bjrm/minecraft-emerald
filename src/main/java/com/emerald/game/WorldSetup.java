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

    /**
     * Notre propre village, cherche en priorite.
     *
     * On lui accorde un rayon plus large qu'aux autres : c'est celui qu'on veut,
     * et le trouver une fois vaut la depense d'une generation un peu plus large
     * au tout premier chargement.
     */
    private static final int OWN_SEARCH_CHUNKS = 160;

    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure>
            ARCENCIUM_VILLAGE = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.STRUCTURE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            EmeraldWeaponsMod.MODID, "arcencium_village"));

    /** Zone nettoyee de ses monstres autour du village avant le depart. */
    private static final int CLEAR_RADIUS = 64;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        GameState state = GameState.get(level);
        if (state.isPrepared() && isVillageValid(level, state)) {
            return;
        }
        if (state.isPrepared()) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID).info(
                    "Village invalide en {}, nouvelle mise en place", state.village());
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
     * Une mise en place anterieure tient-elle encore debout ?
     *
     * Les regles de placement se sont durcies au fil des versions : un monde
     * prepare avant elles peut porter sa lame sous terre, ou sans son socle. On
     * la refait dans ce cas, plutot que d'obliger a taper une commande dont
     * personne ne devine l'existence.
     */
    private static boolean isVillageValid(ServerLevel level, GameState state) {
        BlockPos village = state.village();
        if (village.equals(BlockPos.ZERO) || village.getY() < level.getSeaLevel()) {
            return false;
        }
        // la lame doit encore etre la, ou la partie doit avoir depasse le prologue
        if (state.status() == GameState.Status.LOBBY
                && !level.getBlockState(village).is(
                        com.emerald.block.ModBlocks.OATH_BLADE.get())) {
            return false;
        }
        return true;
    }

    /**
     * Le village d'accueil : le NOTRE en priorite, un autre a defaut.
     *
     * Le mode doit commencer dans son propre decor -- palette d'Arcencium,
     * Arbres de Prisme, lanternes. On ne se rabat sur un village vanilla ou d'un
     * autre mod que si aucun des notres n'est a portee, ce qui reste possible
     * malgre les vingt et un biomes ou il apparait.
     */
    @Nullable
    private static BlockPos findVillage(ServerLevel level) {
        BlockPos from = level.getSharedSpawnPos();
        BlockPos found = level.findNearestMapStructure(ARCENCIUM_VILLAGE, from,
                OWN_SEARCH_CHUNKS, false);
        if (found == null) {
            org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID).info(
                    "Aucun village d'Arcencium a portee, recherche d'un village ordinaire");
            found = level.findNearestMapStructure(StructureTags.VILLAGE, from,
                    SEARCH_CHUNKS, false);
        }
        return found == null ? null : findOpenGround(level, found, 24);
    }

    /**
     * Un emplacement REELLEMENT degage, en spirale autour d'un point.
     *
     * Le centre d'un village tombe le plus souvent dans une maison : y poser le
     * socle et y faire apparaitre les joueurs les emmurait. On exige donc un sol
     * dur, deux blocs d'air au-dessus, et le ciel degage -- ce dernier critere
     * ecarte a lui seul les interieurs, les caves et les surplombs.
     */
    /**
     * La hauteur du sol, en FORCANT le chargement du chunk.
     *
     * Level.getHeight() ne consulte le relief que si le chunk est deja charge ;
     * sinon il rend getMinBuildHeight(), soit -64. Or findNearestMapStructure
     * designe une position dans un chunk qui ne l'est pas encore : toutes les
     * verifications de placement s'appliquaient donc a une hauteur de -64,
     * aucune ne passait, et la lame finissait plantee au fond du monde.
     */
    public static int surfaceY(ServerLevel level, int x, int z) {
        var chunk = level.getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(x),
                                   net.minecraft.core.SectionPos.blockToSectionCoord(z));
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    public static BlockPos findOpenGround(ServerLevel level, BlockPos around, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;                  // on ne teste que le bord de l'anneau
                    }
                    int x = around.getX() + dx;
                    int z = around.getZ() + dz;
                    BlockPos feet = new BlockPos(x, surfaceY(level, x, z), z);
                    if (isStandable(level, feet)) {
                        return feet;
                    }
                }
            }
        }
        return new BlockPos(around.getX(), surfaceY(level, around.getX(), around.getZ()),
                around.getZ());
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        // jamais sous le niveau de la mer : une grotte peut voir le ciel par un
        // puits et satisferait les autres criteres, mais y planter la lame la
        // rendrait introuvable
        if (feet.getY() < level.getSeaLevel()) {
            return false;
        }
        if (!level.canSeeSky(feet)) {
            return false;
        }
        // Trois blocs pleins sous les pieds, et pas un seul.
        // Un toit de maison offre un sol dur et le ciel degage : il satisfaisait
        // les deux autres criteres, et les joueurs apparaissaient dessus.
        for (int depth = 1; depth <= 3; depth++) {
            BlockPos under = feet.below(depth);
            if (!level.getBlockState(under).isSolidRender(level, under)) {
                return false;
            }
        }
        if (!level.getFluidState(feet.below()).isEmpty()) {
            return false;               // ni eau ni lave sous les pieds
        }
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
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
        // a cote du socle, jamais dessus : le bloc de la lame emmurerait le joueur
        BlockPos stand = findOpenGround(level, village.offset(4, 0, 4), 12);
        player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        player.setRespawnPosition(level.dimension(), stand, 0.0F, true, false);
        GameManager.equipStarter(player);
    }
}
