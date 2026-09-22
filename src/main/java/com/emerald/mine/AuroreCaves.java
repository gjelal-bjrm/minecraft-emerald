package com.emerald.mine;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 *  - LES BRUMES ETOILEES : une ou deux paires d'arches au voile de nuit etoilee,
 *    a 30-120 blocs l'une de l'autre. Passer dans l'une, c'est se dissoudre et
 *    etre porte sur un arc, a travers la roche s'il le faut, jusqu'a l'autre.
 *    Dans les deux sens. Techniquement on chevauche un porteur invisible qui
 *    suit une courbe : c'est ce qui rend le vol FLUIDE a l'ecran, sans a-coups.
 *
 * DES ARCHES, ET PLUS DES NUAGES (22 sept., cahier §84) : six cubes translucides en croix,
 * dont chaque bras emportait, « tres embetant a prendre [...] je ne sais pas quelle partie
 * je dois contourner ». Ce sont des arches d'Arcencium (ArcPortals) : seul le voile
 * emporte, et une brume ne se leve que la ou son arche tient.
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
    /** Chaque arche de brume levee et le cote vers lequel elle regarde ; chaque ancre, sa jumelle. */
    private static final Map<BlockPos, Direction> arches = new HashMap<>();
    private static final Map<BlockPos, BlockPos> links = new HashMap<>();
    private static final Map<UUID, Transit> transits = new HashMap<>();
    /** Qui est dans le voile d'une brume CETTE tique (et laquelle), et qui l'etait a la precedente. */
    private static final Map<UUID, BlockPos> touching = new HashMap<>();
    private static final Set<UUID> wasTouching = new HashSet<>();
    private static final Map<UUID, Long> arrivedAt = new HashMap<>();
    /** Les pieds des puits leves : pour le journal, et donc pour les essais. */
    private static final List<BlockPos> wellFloors = new ArrayList<>();
    /** Les joueurs autour de qui quelque chose s'est leve, et quand les autres peuvent reessayer. */
    private static final Map<UUID, BlockPos> servedAt = new HashMap<>();
    /** Les brumes de rappel de la fin : d'ou, vers ou, et jusqu'a quand. */
    private static final Map<BlockPos, BlockPos> recalls = new HashMap<>();
    private static final List<BlockPos> recallArches = new ArrayList<>();
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
        for (BlockPos pos : arches.keySet()) {
            com.emerald.block.ArcPortals.remove(level, pos);
        }
        if (!arches.isEmpty()) {
            for (ServerPlayer player : level.players()) {
                player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.8F, 1.4F);
            }
        }
        arches.clear();
        links.clear();
        arrivedAt.clear();
        // LE RAPPEL : qui est encore sous terre voit une brume se lever a ses
        // pieds. Y entrer, c'est le voyage a travers la roche, droit vers le
        // haut, jusqu'au premier bloc a ciel ouvert. L'Aurore ne laisse
        // personne au fond.
        clearRecalls(level);
        // A L'ARRET DU SERVEUR (WeatherManager y finit la meteo en cours), pas de rappel :
        // l'arche serait posee -- la roche taillee -- pour etre retiree aussitot
        if (!level.getServer().isRunning()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            placeRecall(level, player);
        }
    }

    /**
     * La brume de rappel d'un joueur, s'il est sous terre et qu'il y a un jour
     * au-dessus : une arche au voile d'aube, a cote de lui.
     *
     * A COTE DE LUI, JAMAIS SUR LUI. Elle se levait « a ses pieds », au sens
     * propre : sur son bloc et sur celui de sa tete. Le joueur etait donc
     * DEDANS des la premiere tique, et le depart partait tout seul -- « ca me
     * fait remonter directement ». Le rappel doit etre une porte offerte, pas
     * une trappe : on la voit, on finit sa veine, et l'on entre quand on veut.
     *
     * ET L'ON CREUSE S'IL LE FAUT. Au fond d'un puits d'un bloc de large, il
     * n'y a pas de « a cote » : les six voisins sont de la roche. On taille
     * alors une alcove d'un bloc de large et de deux de haut dans la paroi, ce
     * qui est exactement ce que le joueur demandait. Elle ne mange que de la
     * roche naturelle -- jamais un bloc pose, jamais un minerai.
     */
    private static void placeRecall(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        if (feet.getY() >= Underground.CEILING || level.canSeeSky(feet.above())) {
            return;
        }
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet);
        if (top.getY() <= feet.getY() + 3) {
            return;
        }
        com.emerald.block.ArcPortals.Placement where = recallSpot(level, player);
        if (where == null) {
            return;                         // ni place ni roche a tailler : on n'insiste pas
        }
        BlockPos anchor = where.anchor();
        recalls.put(anchor, top);
        links.put(anchor, top);                        // un seul sens : on ne redescend pas par la
        com.emerald.block.ArcPortals.place(level, anchor, where.facing(),
                com.emerald.block.ArcPortalBlock.Tint.AUBE);
        arches.put(anchor, where.facing());
        recallArches.add(anchor);
        // ET IL N'Y EST PAS DEJA. Meme posee a cote, la brume pourrait toucher
        // sa boite : on le declare « touchait deja », ce qui repousse le depart
        // au moment ou il en sortira et y reviendra de lui-meme.
        wasTouching.add(player.getUUID());
        recallUntil = level.getGameTime() + RECALL_TICKS;
        player.sendSystemMessage(Component.translatable("mine.emeraldweapons.aurore.recall")
                .withStyle(style -> style.withColor(0xFFD24A)));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.0F, 1.6F);
        LOGGER.info("Aurore : brume de rappel pour {} en {} vers {}", player.getName().getString(), feet, top);
    }

    /**
     * Ou poser l'arche de rappel : a cote du joueur, ou dans la paroi.
     *
     * D'abord une place ou l'arche tient deja, a deux ou trois blocs de lui, tournee vers
     * lui : rien a casser. Sinon la taille : on ouvre dans la premiere paroi de roche
     * naturelle venue, a deux blocs devant lui, le passage de l'arche (trois de large, quatre
     * de haut) et le pas qui y mene. Null si rien ne s'y prete -- un coffre, une porte, un
     * bloc pose -- car on ne casse jamais ce que le joueur a mis la.
     */
    @Nullable
    private static com.emerald.block.ArcPortals.Placement recallSpot(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        for (int r = 2; r <= 4; r++) {
            for (Direction d : com.emerald.block.ArcPortals.facings(feet, feet.relative(player.getDirection(), 4))) {
                BlockPos anchor = feet.relative(d, r);
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos at = anchor.above(dy);
                    if (com.emerald.block.ArcPortals.fitsFront(level, at, d.getOpposite())
                            && !at.equals(feet)) {
                        return new com.emerald.block.ArcPortals.Placement(at, d.getOpposite());
                    }
                }
            }
        }
        // LA TAILLE : devant lui d'abord, puis sur les cotes
        for (Direction d : com.emerald.block.ArcPortals.facings(feet, feet.relative(player.getDirection(), 4))) {
            BlockPos anchor = feet.relative(d, 2);
            Direction facing = d.getOpposite();                // l'arche regarde le joueur
            Direction side = facing.getClockWise();
            List<BlockPos> carve = new ArrayList<>();
            boolean ok = true;
            for (int l = -1; l <= 1 && ok; l++) {
                BlockPos base = anchor.relative(side, l);
                BlockState floor = level.getBlockState(base.below());
                if (!floor.isFaceSturdy(level, base.below(), Direction.UP)) {
                    ok = false;
                }
                for (int y = 0; y <= 3 && ok; y++) {
                    BlockPos cell = base.above(y);
                    BlockState state = level.getBlockState(cell);
                    if (state.isAir()) {
                        continue;
                    }
                    if (Underground.natural(state) && !Underground.ore(state)) {
                        carve.add(cell);
                    } else {
                        ok = false;
                    }
                }
            }
            BlockPos step = feet.relative(d);
            for (BlockPos cell : new BlockPos[]{step, step.above()}) {
                BlockState state = level.getBlockState(cell);
                if (!ok || state.isAir()) {
                    continue;
                }
                if (Underground.natural(state) && !Underground.ore(state)) {
                    carve.add(cell);
                } else {
                    ok = false;
                }
            }
            if (!ok) {
                continue;
            }
            for (BlockPos cell : carve) {
                level.destroyBlock(cell, false);
            }
            level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                    anchor.getX() + 0.5, anchor.getY() + 1.5, anchor.getZ() + 0.5, 20, 0.9, 1.2, 0.4, 0.02);
            return new com.emerald.block.ArcPortals.Placement(anchor, facing);
        }
        return null;
    }

    /**
     * A l'arret, les arches s'en vont AVANT la sauvegarde : elles ne restent pas dans le monde.
     * En dernier, apres WeatherManager qui finit la meteo en cours.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        ServerLevel level = event.getServer().overworld();
        for (BlockPos pos : arches.keySet()) {
            com.emerald.block.ArcPortals.remove(level, pos);
        }
        arches.clear();
        recallArches.clear();
    }

    /** Pour le banc d'essai (ArcenciumAutotest) : la brume de rappel de ce joueur, et son arche. */
    @Nullable
    public static BlockPos recallForTest(ServerLevel level, ServerPlayer player) {
        placeRecall(level, player);
        for (Map.Entry<BlockPos, BlockPos> entry : recalls.entrySet()) {
            if (entry.getKey().distSqr(player.blockPosition()) <= 25.0) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Pour le banc d'essai : retire les brumes de rappel. */
    public static void clearRecallsForTest(ServerLevel level) {
        clearRecalls(level);
    }

    private static void clearRecalls(ServerLevel level) {
        for (BlockPos anchor : recallArches) {
            com.emerald.block.ArcPortals.remove(level, anchor);
            arches.remove(anchor);
        }
        for (BlockPos anchor : recalls.keySet()) {
            links.remove(anchor);
        }
        recallArches.clear();
        recalls.clear();
        recallUntil = -1L;
    }

    /**
     * UNE PAIRE A LA DEMANDE : la Chambre d'Aurore pose la sienne ici -- ou a trois blocs
     * pres, la ou une arche tient.
     */
    public static boolean placePair(ServerLevel level, BlockPos anchorA, BlockPos anchorB) {
        com.emerald.block.ArcPortals.Placement a = com.emerald.block.ArcPortals.nearest(level, anchorA, 3, 2, anchorB);
        com.emerald.block.ArcPortals.Placement b = com.emerald.block.ArcPortals.nearest(level, anchorB, 3, 2, anchorA);
        if (a == null || b == null || a.anchor().equals(b.anchor())) {
            return false;
        }
        links.put(a.anchor(), b.anchor());
        links.put(b.anchor(), a.anchor());
        placeMist(level, a);
        placeMist(level, b);
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
        arches.clear();
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
            // les deux arches doivent tenir, chacune a deux blocs pres de son lieu
            com.emerald.block.ArcPortals.Placement archA = com.emerald.block.ArcPortals.nearest(
                    level, a.floor().above(), 2, 1, best.floor());
            com.emerald.block.ArcPortals.Placement archB = archA == null ? null : com.emerald.block.ArcPortals.nearest(
                    level, best.floor().above(), 2, 1, a.floor());
            if (archA == null || archB == null) {
                used.add(archA == null ? a.floor() : best.floor());
                continue;
            }
            used.add(a.floor());
            used.add(best.floor());
            links.put(archA.anchor(), archB.anchor());
            links.put(archB.anchor(), archA.anchor());
            placeMist(level, archA);
            placeMist(level, archB);
            pairs++;
        }
        return pairs;
    }

    /** Une arche au voile de nuit etoilee. */
    private static void placeMist(ServerLevel level, com.emerald.block.ArcPortals.Placement where) {
        com.emerald.block.ArcPortals.place(level, where.anchor(), where.facing(),
                com.emerald.block.ArcPortalBlock.Tint.ETOILEE);
        arches.put(where.anchor(), where.facing());
        level.playSound(null, where.anchor(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 1.0F, 1.5F);
    }

    // ------------------------------------------------------------ le voyage

    /** Cette arche est-elle une brume en service ? (ArcPortalBlockEntity efface les autres.) */
    public static boolean isArch(BlockPos pos) {
        return arches.containsKey(pos);
    }

    private static void depart(ServerLevel level, ServerPlayer player, BlockPos anchor) {
        BlockPos exit = links.get(anchor);
        if (exit == null) {
            return;
        }
        Vec3 from = player.position();
        // devant l'arche jumelle, le dos tourne a elle ; le rappel, lui, mene au jour
        Direction exitFacing = arches.get(exit);
        Vec3 to = exitFacing == null ? Vec3.atBottomCenterOf(exit) : com.emerald.block.ArcPortals.exit(exit, exitFacing);
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
                Direction exitFacing = arches.get(transit.exit);
                if (exitFacing != null) {
                    player.teleportTo(level, transit.to.x, transit.to.y, transit.to.z,
                            com.emerald.block.ArcPortals.exitYaw(exitFacing), player.getXRot());
                } else {
                    player.teleportTo(transit.to.x, transit.to.y, transit.to.z);
                }
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
        // et s'eteignent de meme, de haut en bas, HUIT PAR TIQUE.
        //
        // Un bloc par tique suffisait quand l'Aurore durait deux minutes ; a
        // cinq, les colonnes montent a plusieurs centaines de blocs et l'on
        // voyait des puits orphelins briller vingt secondes apres la fin.
        for (int n = 0; n < 8; n++) {
            BlockPos down = fading.poll();
            if (down == null) {
                break;
            }
            if (level.getBlockState(down).is(ModBlocks.AURORE_LIGHT.get())) {
                level.setBlock(down, Blocks.AIR.defaultBlockState(), 3);
                level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                        down.getX() + 0.5, down.getY() + 0.5, down.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
            }
        }
        // la brume de rappel se dissipe au bout de son temps
        if (recallUntil >= 0 && level.getGameTime() > recallUntil) {
            clearRecalls(level);
        }
        // QUI EST DANS UN VOILE : les pieds entre les piliers d'une arche, pres de son plan
        if (!arches.isEmpty()) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || transits.containsKey(player.getUUID())) {
                    continue;
                }
                for (Map.Entry<BlockPos, Direction> arch : arches.entrySet()) {
                    if (arch.getKey().distSqr(player.blockPosition()) <= 16.0
                            && com.emerald.block.ArcPortals.inVeil(player, arch.getKey(), arch.getValue())) {
                        touching.put(player.getUUID(), arch.getKey());
                        break;
                    }
                }
            }
        }
        // les departs : qui vient d'ENTRER dans un voile, et n'en revient pas a l'instant
        if (active || !recalls.isEmpty()) {
            long now = level.getGameTime();
            for (Map.Entry<UUID, BlockPos> entry : touching.entrySet()) {
                UUID id = entry.getKey();
                if (wasTouching.contains(id) || transits.containsKey(id)
                        || now - arrivedAt.getOrDefault(id, -1000L) < 60) {
                    continue;
                }
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
                if (player != null) {
                    depart(level, player, entry.getValue());
                }
            }
        }
        wasTouching.clear();
        wasTouching.addAll(touching.keySet());
        touching.clear();
        tickTransits(level);
    }
}
