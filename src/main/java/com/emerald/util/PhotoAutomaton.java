package com.emerald.util;

import com.emerald.haven.HavenRules;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * L'automate de photos, pour regarder un terrain avant de le livrer -- INERTE sans
 * EMERALDWEAPONS_PHOTOS.
 *
 * « Regarder avant de livrer » : deux ports corriges sur des chiffres sont restes
 * casses en jeu. Pour la generation du monde (arbres, biomes), l'image est la seule
 * preuve : l'automate place le joueur dans chaque biome demande, a midi et par temps
 * clair, et le client prend la capture (PhotoClient), dans run/screenshots/.
 *
 * EMERALDWEAPONS_PHOTOS : des prises separees par « ; », chacune « nom@biome » ou
 * « nom@biome@hauteur » (blocs au-dessus du sol, regard plonge d'autant ; 0 par
 * defaut : a hauteur d'homme). EMERALDWEAPONS_PHOTOS_ORIGINE=« x,z » : d'ou chercher
 * les biomes (loin de tout ce qui est explore, pour des troncons neufs).
 *
 * Le joueur passe en chantier (la ville ne le retient pas), le mode Arcencium est
 * eteint (ni meteo ni confinement), et il vole en spectateur pour chaque prise.
 * Toutes les prises faites, le client se ferme.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class PhotoAutomaton {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final String VARIABLE = "EMERALDWEAPONS_PHOTOS";
    private static final String SPEC = Objects.requireNonNullElse(System.getenv(VARIABLE), "").trim();
    private static final String ORIGIN = Objects.requireNonNullElse(System.getenv(VARIABLE + "_ORIGINE"), "").trim();
    /**
     * Tiques d'attente apres chaque deplacement, au moins : troncons generes, charges
     * et dessines. La premiere serie, a douze secondes sans autre controle, n'a montre
     * que le ciel et des brins d'herbe flottants : des troncons neufs, avec ces mods, se
     * generent et se dessinent plus lentement. On attend donc aussi que le client ait
     * tout dessine (PhotoClient), deux secondes de suite, une minute au plus.
     */
    private static final int SETTLE = 100;
    private static final int READY_TICKS = 40;
    private static final int MAX_WAIT = 1200;
    private static final int START_DELAY = 200;

    private record Shot(String name, ResourceLocation biome, int height) {
    }

    private static final List<Shot> SHOTS = parse();
    private static int index;
    private static int ticks;
    private static volatile int waited;
    private static boolean prepared;
    /** La prise que le client doit faire maintenant, ou null. */
    @Nullable
    private static volatile String pending;
    private static volatile boolean taken;
    private static volatile boolean finished;
    /** Tiques de suite ou le client a dit avoir tout dessine. */
    private static volatile int clientReady;

    private PhotoAutomaton() {
    }

    public static boolean enabled() {
        return !SHOTS.isEmpty();
    }

    @Nullable
    public static String pending() {
        return pending;
    }

    /** Le monde a eu le temps de se generer et de se dessiner : le client peut prendre la photo. */
    public static boolean readyToShoot() {
        return pending != null && waited >= SETTLE && (clientReady >= READY_TICKS || waited >= MAX_WAIT);
    }

    /** Le client dit, a chaque tique, s'il a fini de dessiner le terrain autour de lui. */
    public static void clientTerrain(boolean done) {
        clientReady = done ? clientReady + 1 : 0;
    }

    public static void taken(String name) {
        if (name.equals(pending)) {
            taken = true;
        }
    }

    public static boolean finished() {
        return finished;
    }

    private static List<Shot> parse() {
        List<Shot> out = new ArrayList<>();
        if (SPEC.isEmpty()) {
            return out;
        }
        for (String part : SPEC.split(";")) {
            String[] bits = part.trim().split("@");
            if (bits.length < 2) {
                continue;
            }
            ResourceLocation biome = ResourceLocation.tryParse(bits[1].trim());
            int height = bits.length > 2 ? Integer.parseInt(bits[2].trim()) : 0;
            if (biome != null) {
                out.add(new Shot(bits[0].trim(), biome, height));
            }
        }
        return out;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SHOTS.isEmpty() || finished) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        if (++ticks < START_DELAY) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel overworld = server.overworld();
        if (!prepared) {
            prepared = true;
            HavenRules.setChantier(player, true);
            run(server, "arcencium mode off");
            run(server, "gamerule doDaylightCycle false");
            run(server, "gamerule doWeatherCycle false");
            run(server, "time set 6000");
            run(server, "weather clear 1000000");
            LOGGER.info("photos : {} prises, depuis {}", SHOTS.size(), ORIGIN.isEmpty() ? "le joueur" : ORIGIN);
        }
        if (index >= SHOTS.size()) {
            finished = true;
            LOGGER.info("photos : toutes les prises sont faites, fermeture");
            return;
        }
        Shot shot = SHOTS.get(index);
        if (pending == null) {
            if (!place(server, overworld, player, shot)) {
                LOGGER.warn("photos : biome {} introuvable, prise {} sautee", shot.biome(), shot.name());
                index++;
                return;
            }
            pending = shot.name();
            taken = false;
            waited = 0;
            clientReady = 0;
            return;
        }
        // on laisse le monde se generer et se dessiner, puis le client prend la photo
        ++waited;
        if (waited >= SETTLE && taken) {
            LOGGER.info("photos : prise {} faite apres {} tiques{}", shot.name(), waited,
                    waited >= MAX_WAIT ? " (terrain pas entierement dessine)" : "");
            pending = null;
            index++;
        }
    }

    /** Le joueur au-dessus du biome, sur le sol, regard vers l'est ; plonge s'il est en hauteur. */
    private static boolean place(MinecraftServer server, ServerLevel level, ServerPlayer player, Shot shot) {
        BlockPos from = player.blockPosition();
        if (!ORIGIN.isEmpty()) {
            String[] xz = ORIGIN.split(",");
            from = new BlockPos(Integer.parseInt(xz[0].trim()), 64, Integer.parseInt(xz[1].trim()));
        }
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, shot.biome());
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(holder -> holder.is(key), from, 6400, 32, 64);
        if (found == null) {
            return false;
        }
        BlockPos at = found.getFirst();
        // les troncons autour se generent d'abord : le client les recevra finis
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.getChunk((at.getX() >> 4) + dx, (at.getZ() >> 4) + dz);
            }
        }
        Spot spot = standingSpot(level, at);
        BlockPos feet = spot.feet();
        float pitch = shot.height() > 0 ? (float) Math.min(60.0, 10.0 + shot.height()) : 0.0F;
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, feet.getX() + 0.5, feet.getY() + shot.height(), feet.getZ() + 0.5, spot.yaw(), pitch);
        LOGGER.info("photos : {} ({}) en {} {} {}", shot.name(), shot.biome(), feet.getX(), feet.getY() + shot.height(),
                feet.getZ());
        return true;
    }

    /** Une place et la direction du regard. */
    private record Spot(BlockPos feet, float yaw) {
    }

    /** Huit directions : le lacet de Minecraft et le pas en x et z. */
    private static final int[][] DIRECTIONS = {{0, 0, 1}, {-45, 1, 1}, {-90, 1, 0}, {-135, 1, -1},
            {180, 0, -1}, {135, -1, -1}, {90, -1, 0}, {45, -1, 1}};

    /**
     * Une place ou se tenir, sur le VRAI sol, pres du point : pas sur une cime, pas dans un
     * tronc, et le regard vers le cote le plus degage. Les branches de Dynamic Trees
     * comptent comme des blocs pour les cartes de hauteur : la premiere serie posait le
     * joueur sur les arbres, regard vers le ciel ; la deuxieme l'a colle une fois contre un
     * talus, le nez dans la terre.
     */
    private static Spot standingSpot(ServerLevel level, BlockPos at) {
        Spot fallback = null;
        for (int r = 0; r <= 10; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = at.getX() + dx;
                    int z = at.getZ() + dz;
                    int ground = groundY(level, x, z);
                    BlockPos feet = new BlockPos(x, ground + 1, z);
                    if (!free(level, feet) || !free(level, feet.above())) {
                        continue;
                    }
                    // la direction ou l'oeil voit le plus loin, sur dix blocs
                    int best = -1;
                    float yaw = -90.0F;
                    for (int[] d : DIRECTIONS) {
                        int open = 0;
                        while (open < 10 && free(level, feet.offset(d[1] * (open + 1), 1, d[2] * (open + 1)))) {
                            open++;
                        }
                        if (open > best) {
                            best = open;
                            yaw = d[0];
                        }
                    }
                    if (best >= 6) {
                        return new Spot(feet, yaw);
                    }
                    if (fallback == null) {
                        fallback = new Spot(feet, yaw);
                    }
                }
            }
        }
        return fallback != null ? fallback
                : new Spot(new BlockPos(at.getX(), groundY(level, at.getX(), at.getZ()) + 1, at.getZ()), -90.0F);
    }

    /** Le sol sous les arbres : on descend a travers l'air, les feuilles, les troncs et les branches. */
    private static int groundY(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x,
                level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        int floor = Math.max(level.getMinBuildHeight(), pos.getY() - 80);
        while (pos.getY() > floor) {
            BlockState state = level.getBlockState(pos);
            String namespace = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace();
            boolean tree = state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || namespace.startsWith("dynamictrees");
            if (!state.isAir() && !tree && !state.canBeReplaced() && state.isCollisionShapeFullBlock(level, pos)) {
                return pos.getY();
            }
            if (!state.getFluidState().isEmpty()) {
                return pos.getY();
            }
            pos.move(0, -1, 0);
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || (state.canBeReplaced() && state.getFluidState().isEmpty());
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ticks = 0;
    }

}
