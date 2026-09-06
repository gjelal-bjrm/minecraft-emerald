package com.emerald.mine;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L'AURORE SOUS TERRE : les grottes s'ouvrent, puis se referment en prevenant.
 *
 * « Rien a poser : les grottes prennent vie, le temps d'une meteo. » Pendant
 * l'Aurore -- la fenetre de mine -- deux choses se levent d'elles-memes dans
 * les grottes a moins de 64 blocs du joueur, et repartent avec elle :
 *
 *  - LES PUITS D'AURORE : deux a quatre colonnes de lumiere, la ou il y a un
 *    vrai vide (six blocs d'air au moins, de preference dix), qui MONTENT bloc
 *    par bloc du sol au plafond. Dedans on monte, on descend accroupi, on ne
 *    tombe jamais. Un gouffre devient une descente ;
 *  - LES BRUMES ETOILEES : une ou deux paires de nuages d'etoiles, a 30-120
 *    blocs l'un de l'autre. Entrer dans l'un, c'est se dissoudre et etre porte
 *    sur un arc, a travers la roche s'il le faut, jusqu'a l'autre. Dans les
 *    deux sens. Techniquement on chevauche un porteur invisible qui suit une
 *    courbe : c'est ce qui rend le vol FLUIDE a l'ecran, sans a-coups.
 *
 * LA FIN, ANNONCEE : a 45 s de la fin de l'Aurore, un message ; a 15 s, un
 * second. Puis chaque colonne s'eteint DE HAUT EN BAS, bloc par bloc, et chaque
 * brume se disperse. Deux garde-fous absolus : qui est encore dans une colonne
 * recoit dix secondes de chute lente, et un voyage en cours va jusqu'au bout.
 *
 * C'est la tension cherchee : on plonge pendant l'Aurore, et l'on a jusqu'a sa
 * fin pour remonter par les etoiles -- ou l'on reste en bas et l'on creuse sa
 * remontee a l'ancienne, avec les Percees.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class AuroreCaves {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Ou l'on cherche des grottes, et combien de colonnes on sonde. */
    private static final int RADIUS = 64;
    private static final int SAMPLES = 400;
    /** On sonde du plafond d'Underground jusqu'ici : sous le joueur, jusqu'au fond. */
    private static final int FLOOR = -60;
    /** Qui descend sans rien autour de lui reessaie toutes les dix secondes, et pas dans la derniere minute. */
    private static final int RETRY = 10 * 20;
    private static final int LATE = 60 * 20;
    /** Un vide digne d'un puits : six blocs d'air au moins ; dix pour un vrai gouffre. */
    private static final int MIN_AIR = 4;
    private static final int BIG_AIR = 10;
    private static final int COLUMN_CAP = 28;
    private static final int WELLS_MIN = 4;
    private static final int WELLS_MAX = 6;
    private static final int PAIRS_MIN = 2;
    private static final int PAIRS_MAX = 3;
    /** Une nouvelle levee des qu'on s'est eloigne d'autant de la precedente. */
    private static final double REFRESH = 48.0;
    /** La brume de rappel de fin d'Aurore reste tant de tiques. */
    private static final int RECALL_TICKS = 45 * 20;
    private static final double PAIR_MIN = 30.0;
    private static final double PAIR_MAX = 120.0;
    /** Les avertissements, en tiques avant la fin. */
    private static final int WARN_FIRST = 45 * 20;
    private static final int WARN_SECOND = 15 * 20;
    /** Le voyage : sa duree, et la hauteur de son arc. */
    private static final int TRANSIT_TICKS = 70;
    private static final double TRANSIT_ARC = 6.0;
    private static final String TAG_CARRIER = "emeraldweapons_star_carrier";

    /** Un endroit ou un puits peut se lever : son sol, et l'air au-dessus. */
    private record Spot(BlockPos floor, int air) {
    }

    /** Un voyage en cours : le porteur, la courbe, la sortie, l'avancement. */
    private static final class Transit {
        final UUID carrier;
        final Vec3 from;
        final Vec3 control;
        final Vec3 to;
        final BlockPos exit;
        int tick;

        Transit(UUID carrier, Vec3 from, Vec3 control, Vec3 to, BlockPos exit) {
            this.carrier = carrier;
            this.from = from;
            this.control = control;
            this.to = to;
            this.exit = exit;
        }
    }

    private static boolean active;
    private static boolean warnedFirst;
    private static boolean warnedSecond;
    /** Tout ce qu'on a pose, pour le reprendre ; et ce qui reste a poser ou a eteindre. */
    private static final List<BlockPos> lit = new ArrayList<>();
    private static final ArrayDeque<BlockPos> rising = new ArrayDeque<>();
    private static final ArrayDeque<BlockPos> fading = new ArrayDeque<>();
    /** Chaque bloc de brume, et l'ancre de sa paire ; chaque ancre, sa jumelle. */
    private static final Map<BlockPos, BlockPos> mistBlocks = new HashMap<>();
    private static final Map<BlockPos, BlockPos> links = new HashMap<>();
    private static final Map<UUID, Transit> transits = new HashMap<>();
    /** Qui touche une brume CETTE tique, et qui la touchait a la precedente. */
    private static final Set<UUID> touching = new HashSet<>();
    private static final Set<UUID> wasTouching = new HashSet<>();
    private static final Map<UUID, Long> arrivedAt = new HashMap<>();
    /** Les pieds des puits leves : pour le journal, et donc pour les essais. */
    private static final List<BlockPos> wellFloors = new ArrayList<>();
    /** Les joueurs autour de qui quelque chose s'est leve, et quand les autres peuvent reessayer. */
    private static final Map<UUID, BlockPos> servedAt = new HashMap<>();
    /** Les brumes de rappel de la fin : d'ou, vers ou, et jusqu'a quand. */
    private static final Map<BlockPos, BlockPos> recalls = new HashMap<>();
    private static final List<BlockPos> recallBlocks = new ArrayList<>();
    private static long recallUntil = -1L;
    private static final Map<UUID, Long> nextTry = new HashMap<>();

    private AuroreCaves() {
    }

    // -------------------------------------------------------------- cycle

    /** L'Aurore commence : les grottes s'ouvrent autour de chaque joueur. */
    public static void begin(ServerLevel level) {
        forget(level);
        active = true;
        int wells = 0;
        int pairs = 0;
        for (ServerPlayer player : level.players()) {
            int[] got = raiseAround(level, player);
            wells += got[0];
            pairs += got[1];
        }
        if (wells + pairs > 0) {
            Component line = Component.translatable("mine.emeraldweapons.aurore.opened", wells, pairs)
                    .withStyle(style -> style.withColor(0x9CE8FF));
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(line);
            }
        }
        LOGGER.info("Aurore : {} puits et {} paire(s) de brumes leves ; puits {} ; brumes {}",
                wells, pairs, wellFloors, links.keySet());
    }

    /** Leve puits et brumes autour d'un joueur, et le retient s'il a ete servi. */
    private static int[] raiseAround(ServerLevel level, ServerPlayer player) {
        List<Spot> spots = spots(level, player.blockPosition());
        int wells = raiseWells(level, spots);
        int pairs = raiseMists(level, spots);
        if (wells + pairs > 0) {
            servedAt.put(player.getUUID(), player.blockPosition());
        }
        return new int[]{wells, pairs};
    }

    /**
     * QUI DESCEND TROUVE. Un joueur autour de qui rien ne s'est leve (en
     * surface sans grotte dessous, ou parti miner ailleurs) recoit ses puits
     * et ses brumes en passant sous terre -- une fois, et pas dans la
     * derniere minute, ou ils n'auraient pas le temps de servir.
     */
    private static void descents(ServerLevel level) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            // QUI S'ELOIGNE RETROUVE : une levee ne vaut que pour ses 48 blocs.
            // Le joueur creusait a cent blocs de ce qui s'etait leve au depart.
            BlockPos last = servedAt.get(player.getUUID());
            if ((last != null && Underground.flat(last, pos) < REFRESH) || !Underground.allowed(level, pos)
                    || level.canSeeSky(pos.above()) || now < nextTry.getOrDefault(player.getUUID(), 0L)) {
                continue;
            }
            nextTry.put(player.getUUID(), now + RETRY);
            int[] got = raiseAround(level, player);
            if (got[0] + got[1] == 0) {
                continue;
            }
            player.sendSystemMessage(Component.translatable("mine.emeraldweapons.aurore.opened", got[0], got[1])
                    .withStyle(style -> style.withColor(0x9CE8FF)));
            LOGGER.info("Aurore : a la descente de {}, {} puits et {} paire(s) de brumes ; puits {} ; brumes {}",
                    player.getName().getString(), got[0], got[1], wellFloors, links.keySet());
        }
    }

    /** Chaque tique de l'Aurore : qui descend, puis les avertissements de fin. */
    public static void tick(ServerLevel level, int remaining) {
        if (!active) {
            return;
        }
        if (level.getGameTime() % 40 == 0 && remaining > LATE) {
            descents(level);
        }
        if (lit.isEmpty() && links.isEmpty()) {
            return;
        }
        if (!warnedFirst && remaining <= WARN_FIRST) {
            warnedFirst = true;
            warn(level, "mine.emeraldweapons.aurore.closing", 45);
        }
        if (!warnedSecond && remaining <= WARN_SECOND) {
            warnedSecond = true;
            warn(level, "mine.emeraldweapons.aurore.closing", 15);
        }
    }

    private static void warn(ServerLevel level, String key, int seconds) {
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.translatable(key, seconds).withStyle(ChatFormatting.YELLOW));
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.9F, 0.6F);
        }
    }

    /**
     * L'Aurore finit : tout s'eteint, de haut en bas, et personne ne tombe.
     *
     * Les colonnes ne disparaissent pas d'un coup : leurs blocs entrent dans une
     * file, du plus haut au plus bas, un par tique. Qui est encore dedans
     * recoit dix secondes de chute lente. Un voyage en cours continue : ses
     * positions sont deja dans le voyage, il n'a plus besoin des brumes.
     */
    public static void end(ServerLevel level) {
        active = false;
        for (ServerPlayer player : level.players()) {
            BlockState feet = level.getBlockState(player.blockPosition());
            if (feet.is(ModBlocks.AURORE_LIGHT.get())) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, true, false, true));
            }
        }
        rising.clear();
        lit.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        fading.addAll(lit);
        lit.clear();
        for (BlockPos pos : mistBlocks.keySet()) {
            if (level.getBlockState(pos).is(ModBlocks.STAR_MIST.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.0);
            }
        }
        if (!mistBlocks.isEmpty()) {
            for (ServerPlayer player : level.players()) {
                player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.8F, 1.4F);
            }
        }
        mistBlocks.clear();
        links.clear();
        arrivedAt.clear();
        // LE RAPPEL : qui est encore sous terre voit une brume se lever a ses
        // pieds. Y entrer, c'est le voyage a travers la roche, droit vers le
        // haut, jusqu'au premier bloc a ciel ouvert. L'Aurore ne laisse
        // personne au fond.
        clearRecalls(level);
        for (ServerPlayer player : level.players()) {
            placeRecall(level, player);
        }
    }

    /** La brume de rappel d'un joueur, s'il est sous terre et qu'il y a un jour au-dessus. */
    private static void placeRecall(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        if (feet.getY() >= Underground.CEILING || level.canSeeSky(feet.above())
                || !level.getBlockState(feet).isAir()) {
            return;
        }
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet);
        if (top.getY() <= feet.getY() + 3) {
            return;
        }
        recalls.put(feet, top);
        links.put(feet, top);                          // un seul sens : on ne redescend pas par la
        BlockPos[] cloud = {feet, feet.above(), feet.north(), feet.south(), feet.east(), feet.west()};
        for (BlockPos pos : cloud) {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, ModBlocks.RECALL_MIST.get().defaultBlockState(), 3);
                mistBlocks.put(pos, feet);
                recallBlocks.add(pos);
            }
        }
        recallUntil = level.getGameTime() + RECALL_TICKS;
        player.sendSystemMessage(Component.translatable("mine.emeraldweapons.aurore.recall")
                .withStyle(style -> style.withColor(0xFFD24A)));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.0F, 1.6F);
        LOGGER.info("Aurore : brume de rappel pour {} en {} vers {}", player.getName().getString(), feet, top);
    }

    private static void clearRecalls(ServerLevel level) {
        for (BlockPos pos : recallBlocks) {
            if (level.getBlockState(pos).is(ModBlocks.RECALL_MIST.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            mistBlocks.remove(pos);
        }
        for (BlockPos anchor : recalls.keySet()) {
            links.remove(anchor);
        }
        recallBlocks.clear();
        recalls.clear();
        recallUntil = -1L;
    }

    /**
     * UNE PAIRE A LA DEMANDE : la Chambre d'Aurore pose la sienne ici. Les deux
     * ancres doivent etre de l'air avec de l'air au-dessus.
     */
    public static boolean placePair(ServerLevel level, BlockPos anchorA, BlockPos anchorB) {
        if (!level.getBlockState(anchorA).isAir() || !level.getBlockState(anchorB).isAir()) {
            return false;
        }
        links.put(anchorA, anchorB);
        links.put(anchorB, anchorA);
        placeMist(level, anchorA);
        placeMist(level, anchorB);
        active = true;
        return true;
    }

    /** La plus grande grotte connue autour d'un point : son sol, ou rien. */
    @javax.annotation.Nullable
    public static BlockPos bestCave(ServerLevel level, BlockPos centre) {
        Spot best = null;
        for (Spot spot : spots(level, centre)) {
            if (best == null || spot.air() > best.air()) {
                best = spot;
            }
        }
        return best == null ? null : best.floor().above();
    }

    /** Les pieds des puits leves, pour la boussole. */
    public static List<BlockPos> wells() {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos floor : wellFloors) {
            out.add(floor.above());
        }
        return out;
    }

    /** Oublie tout sans rien retirer du monde : au debut d'une Aurore, ou a l'arret. */
    private static void forget(ServerLevel level) {
        servedAt.clear();
        nextTry.clear();
        clearRecalls(level);
        // ce qu'une Aurore precedente aurait laisse (serveur arrete en pleine meteo)
        for (BlockPos pos : lit) {
            if (level.getBlockState(pos).is(ModBlocks.AURORE_LIGHT.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        lit.clear();
        rising.clear();
        wellFloors.clear();
        mistBlocks.clear();
        links.clear();
        warnedFirst = false;
        warnedSecond = false;
        touching.clear();
        wasTouching.clear();
        arrivedAt.clear();
    }

    /** Les ancres des brumes, pour la boussole : ou sont les sorties. */
    public static List<BlockPos> mists() {
        return new ArrayList<>(links.keySet());
    }

    // ------------------------------------------------------------ les lieux

    /**
     * Ou un puits peut se lever : on sonde des colonnes au hasard dans le
     * rayon, jamais dans un chunk qu'il faudrait charger, jamais sous le ciel.
     * Un lieu est un SOL naturel avec assez d'air au-dessus.
     */
    private static List<Spot> spots(ServerLevel level, BlockPos centre) {
        RandomSource random = level.random;
        List<Spot> found = new ArrayList<>();
        Set<Long> columns = new HashSet<>();
        // SOUS LE JOUEUR, JUSQU'AU FOND. L'Aurore est annoncee en surface, et
        // c'est en bas qu'elle doit avoir prepare quelque chose : sonder
        // autour de la hauteur du joueur donnait une plage vide a y = 99.
        int top = Underground.CEILING - 1;
        int bottom = Math.max(level.getMinBuildHeight() + 4, FLOOR);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SAMPLES; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 6.0 + random.nextDouble() * (RADIUS - 6.0);
            int x = (int) Math.round(centre.getX() + Math.cos(angle) * distance);
            int z = (int) Math.round(centre.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunk(x >> 4, z >> 4) || !columns.add(BlockPos.asLong(x, 0, z))) {
                continue;
            }
            int air = 0;
            for (int y = top; y >= bottom; y--) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.isAir()) {
                    air++;
                    continue;
                }
                if (air >= MIN_AIR && Underground.natural(state) && Underground.allowed(level, cursor)
                        && !level.canSeeSky(cursor.above())) {
                    found.add(new Spot(cursor.immutable(), air));
                }
                air = 0;
            }
        }
        return found;
    }

    /** Les puits : les plus grands vides d'abord, deux a quatre, jamais deux au meme endroit. */
    private static int raiseWells(ServerLevel level, List<Spot> spots) {
        List<Spot> pool = new ArrayList<>(spots);
        pool.sort((a, b) -> Integer.compare(b.air(), a.air()));
        int wanted = WELLS_MIN + level.random.nextInt(WELLS_MAX - WELLS_MIN + 1);
        List<BlockPos> taken = new ArrayList<>();
        int raised = 0;
        for (Spot spot : pool) {
            if (raised >= wanted) {
                break;
            }
            boolean crowded = false;
            for (BlockPos other : taken) {
                if (Underground.flat(other, spot.floor()) < 12.0) {
                    crowded = true;
                    break;
                }
            }
            if (crowded) {
                continue;
            }
            taken.add(spot.floor());
            wellFloors.add(spot.floor());
            int height = Math.min(spot.air(), COLUMN_CAP);
            for (int h = 1; h <= height; h++) {
                rising.add(spot.floor().above(h));
            }
            raised++;
        }
        return raised;
    }

    /**
     * Les brumes, par paires : deux lieux a 30-120 blocs l'un de l'autre, et
     * de preference l'un plus haut que l'autre -- c'est une remontee.
     */
    private static int raiseMists(ServerLevel level, List<Spot> spots) {
        int wanted = PAIRS_MIN + level.random.nextInt(PAIRS_MAX - PAIRS_MIN + 1);
        List<Spot> pool = new ArrayList<>(spots);
        java.util.Collections.shuffle(pool, new java.util.Random(level.random.nextLong()));
        int pairs = 0;
        Set<BlockPos> used = new HashSet<>();
        for (Spot a : pool) {
            if (pairs >= wanted) {
                break;
            }
            if (used.contains(a.floor())) {
                continue;
            }
            Spot best = null;
            int bestGain = Integer.MIN_VALUE;
            for (Spot b : pool) {
                if (b == a || used.contains(b.floor())) {
                    continue;
                }
                double distance = Math.sqrt(a.floor().distSqr(b.floor()));
                if (distance < PAIR_MIN || distance > PAIR_MAX) {
                    continue;
                }
                int gain = Math.abs(b.floor().getY() - a.floor().getY());
                if (gain > bestGain) {
                    bestGain = gain;
                    best = b;
                }
            }
            if (best == null) {
                continue;
            }
            used.add(a.floor());
            used.add(best.floor());
            BlockPos anchorA = a.floor().above();
            BlockPos anchorB = best.floor().above();
            links.put(anchorA, anchorB);
            links.put(anchorB, anchorA);
            placeMist(level, anchorA);
            placeMist(level, anchorB);
            pairs++;
        }
        return pairs;
    }

    /** Un nuage de six blocs : une croix au sol et deux de haut au centre. */
    private static void placeMist(ServerLevel level, BlockPos anchor) {
        BlockPos[] cloud = {anchor, anchor.above(), anchor.north(), anchor.south(), anchor.east(), anchor.west()};
        for (BlockPos pos : cloud) {
            if (level.getBlockState(pos).isAir()) {
                level.setBlock(pos, ModBlocks.STAR_MIST.get().defaultBlockState(), 3);
                mistBlocks.put(pos, anchor);
            }
        }
        level.playSound(null, anchor, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.0F, 1.5F);
    }

    // ------------------------------------------------------------ le voyage

    /** Un joueur est dans une brume cette tique (StarMistBlock). */
    public static void touch(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (mistBlocks.containsKey(pos)) {
            touching.add(player.getUUID());
        }
    }

    private static void depart(ServerLevel level, ServerPlayer player) {
        BlockPos anchor = mistBlocks.get(player.blockPosition());
        if (anchor == null) {
            // il touche un bloc du nuage sans etre sur l'ancre : on cherche celui-ci
            for (Map.Entry<BlockPos, BlockPos> entry : mistBlocks.entrySet()) {
                if (entry.getKey().distSqr(player.blockPosition()) <= 2.0) {
                    anchor = entry.getValue();
                    break;
                }
            }
        }
        if (anchor == null) {
            return;
        }
        BlockPos exit = links.get(anchor);
        if (exit == null) {
            return;
        }
        Vec3 from = player.position();
        Vec3 to = Vec3.atBottomCenterOf(exit);
        Vec3 mid = from.add(to).scale(0.5);
        Vec3 control = new Vec3(mid.x, Math.max(from.y, to.y) + TRANSIT_ARC, mid.z);
        ArmorStand carrier = new ArmorStand(level, from.x, from.y, from.z);
        carrier.setInvisible(true);
        carrier.setNoGravity(true);
        carrier.setSilent(true);
        carrier.setInvulnerable(true);
        carrier.noPhysics = true;
        carrier.addTag(TAG_CARRIER);
        CompoundTag tag = new CompoundTag();
        carrier.saveWithoutId(tag);
        tag.putBoolean("Marker", true);
        tag.putBoolean("Small", true);
        carrier.load(tag);
        level.addFreshEntity(carrier);
        player.setInvulnerable(true);
        player.startRiding(carrier, true);
        transits.put(player.getUUID(), new Transit(carrier.getUUID(), from, control, to, exit));
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.8F);
        level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                from.x, from.y + 1.0, from.z, 24, 0.5, 0.8, 0.5, 0.02);
    }

    private static Vec3 bezier(Transit transit, double t) {
        double u = 1.0 - t;
        return transit.from.scale(u * u).add(transit.control.scale(2.0 * u * t)).add(transit.to.scale(t * t));
    }

    private static void tickTransits(ServerLevel level) {
        if (transits.isEmpty()) {
            return;
        }
        var it = transits.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            Transit transit = entry.getValue();
            Entity carrier = level.getEntity(transit.carrier);
            if (player == null || carrier == null || player.level() != level) {
                if (carrier != null) {
                    carrier.discard();
                }
                if (player != null) {
                    player.setInvulnerable(false);
                }
                it.remove();
                continue;
            }
            transit.tick++;
            double t = Math.min(1.0, transit.tick / (double) TRANSIT_TICKS);
            Vec3 at = bezier(transit, t);
            carrier.setPos(at.x, at.y, at.z);
            if (!player.isPassengerOfSameVehicle(carrier)) {
                player.startRiding(carrier, true);       // on ne descend pas en route
            }
            // la poussiere d'etoiles : autour du voyageur, a chaque tique
            level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                    at.x, at.y + 1.0, at.z, 3, 0.4, 0.5, 0.4, 0.0);
            if (t >= 1.0) {
                player.stopRiding();
                carrier.discard();
                player.teleportTo(transit.to.x, transit.to.y, transit.to.z);
                player.setInvulnerable(false);
                arrivedAt.put(player.getUUID(), level.getGameTime());
                wasTouching.add(player.getUUID());            // il est dans la brume d'arrivee : pas de retour immediat
                level.playSound(null, transit.exit, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
                level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                        transit.to.x, transit.to.y + 1.0, transit.to.z, 30, 0.6, 0.9, 0.6, 0.02);
                it.remove();
            }
        }
    }

    // --------------------------------------------------------------- tique

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        // les puits montent, un bloc par tique, avec une note qui grimpe
        BlockPos up = rising.poll();
        if (up != null && level.getBlockState(up).isAir()) {
            level.setBlock(up, ModBlocks.AURORE_LIGHT.get().defaultBlockState(), 3);
            lit.add(up);
            if (up.getY() % 4 == 0) {
                level.playSound(null, up, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.6F,
                        1.0F + (up.getY() & 15) * 0.05F);
            }
        }
        // et s'eteignent de meme, de haut en bas
        BlockPos down = fading.poll();
        if (down != null && level.getBlockState(down).is(ModBlocks.AURORE_LIGHT.get())) {
            level.setBlock(down, Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                    down.getX() + 0.5, down.getY() + 0.5, down.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
        }
        // la brume de rappel se dissipe au bout de son temps
        if (recallUntil >= 0 && level.getGameTime() > recallUntil) {
            clearRecalls(level);
        }
        // les departs : qui vient d'ENTRER dans une brume, et n'en revient pas a l'instant
        if (active || !recalls.isEmpty()) {
            long now = level.getGameTime();
            for (UUID id : touching) {
                if (wasTouching.contains(id) || transits.containsKey(id)
                        || now - arrivedAt.getOrDefault(id, -1000L) < 60) {
                    continue;
                }
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    depart(level, player);
                }
            }
        }
        wasTouching.clear();
        wasTouching.addAll(touching);
        touching.clear();
        tickTransits(level);
    }
}
