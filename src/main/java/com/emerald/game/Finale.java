package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * La derniere partie : le Faisceau, son arene, son boss, et la fin.
 *
 * Quand la troisieme ancre est tenue, l'arene se leve a l'ecart des
 * sanctuaires : L'ARENE DE SPARGUS de Jak 3 (cahier §91), convertie par
 * tools/jak_arena.py -- un sol de sable praticable cerne de mesas et de gradins,
 * un anneau de rigoles de lave coupe de quatre passages, des fosses. Elle
 * se pose par le chantier des quartiers (JakBuilder), etalee sur les ticks. Le
 * boss tire au sort nait AU CENTRE DU SOL, le Sculk garde la couronne entre
 * l'anneau et les fosses, et la Maree se recentre sur l'arene : c'est la que
 * tout se referme.
 *
 * Elle remplace la Prison Givree de Cataclysm : son boss naissait sur la plus
 * haute fleche, un pilier d'un bloc en plein ciel -- « ce n'est pas du tout
 * logique ni realiste » (le joueur, 24 sept.).
 *
 * Les identifiants des autres mods sont cites en texte et resolus a
 * l'execution : sans eux, le boss est un Wither sur une butte, et le mode
 * demarre quand meme.
 *
 * Victoire : le boss meurt. Defaite : le temps s'ecoule, la Maree a tout
 * recouvert (GameTicker). Les deux se disent en plein ecran, une fois.
 *
 * UN DEFI PARTI DE LA VILLE REVIENT A HAVEN (parcours, lot 2, HavenReturn) : la
 * defaite ramene tout le monde apres le titre ; la victoire ouvre une porte
 * precurseur la ou le boss est tombe. Le rappel « /arcencium stop » ne sert
 * alors plus : il n'est dit qu'aux mondes sans ville.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Finale {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public static final String TAG_BOSS = "emeraldweapons_final_boss";
    public static final String TAG_GUARD = "emeraldweapons_final_guard";

    /** L'arene se leve a cette distance du village, entre deux sanctuaires. */
    private static final int ARENA_DISTANCE = 300;
    /**
     * Jamais plus pres que cela d'un sanctuaire. Deux cents suffisaient a l'ancienne enceinte de
     * soixante-sept blocs ; le sanctuaire en fait deux cent dix, talus compris il deborde de cent
     * cinquante-quatre blocs de son centre, l'arene de cent vingt-cinq (cahier §92).
     */
    public static final int KEEP_FROM_ANCHORS = 300;
    /**
     * Ni plus pres que cela du village : la recherche du site peut deriver jusqu'a 256 blocs, et
     * un village est pose sur du plat et du sec -- le site reve. Son rayon, plus l'arene et ses
     * talus.
     */
    private static final int KEEP_FROM_VILLAGE = 240;
    /** Le demi-cote de l'arene pour chercher son site (le volume fait 170 x 159). */
    public static final int ARENA_HALF = 85;
    /** La largeur maximale des talus autour de l'arene. */
    private static final int ARENA_TALUS = 40;
    /** Le volume de l'arene et ses reperes (data/emeraldweapons/jak/arene_spargus.jakv et .json). */
    static final String ARENA_VOLUME = "arene_spargus";
    /**
     * LES CINQ BOSS, ET LEUR ECHELLE : tous vers neuf ou dix blocs de haut (cahier §91).
     *
     * « Celui-ci me parait vraiment petit. [...] Compare a l'arene, il parait vraiment
     * ridicule » (le joueur, 24 sept.). Mesure : Ignis fait 3,5 blocs de haut, le Gardien de
     * l'Ender 3,8, la Liche 2,5 -- a taille humaine, avec cent points de vie --, dans une
     * arene de cent quarante blocs. Choix du joueur : des boss GEANTS tires au sort, les
     * deux colosses de Cataclysm (Monstruosite de Netherite, Reste Ancien) en plus, tous
     * agrandis par l'attribut d'echelle du jeu, et six cents points de vie au moins -- puis
     * « supprime la liche de la liste des boss ».
     */
    private static final String[][] BOSSES = {
            {"cataclysm:ignis", "2.5"},
            {"cataclysm:ender_guardian", "2.5"},
            {"cataclysm:netherite_monstrosity", "1.7"},
            {"cataclysm:ancient_remnant", "1.8"},
    };
    /** Aucun boss sous ce nombre de points de vie (le Gardien de l'Ender n'en a que 333). */
    private static final double BOSS_HEALTH_FLOOR = 600.0;
    /** Le Sculk, reserve a l'arene finale (cahier, section 5). */
    private static final String[] GUARDS = {
            "deeperdarker:sculk_snapper",
            "deeperdarker:sculk_centipede",
            "deeperdarker:sculk_leech",
            "deeperdarker:shattered",
    };
    /** Le Traqueur a sa propre barre de boss : un seul, en sentinelle. */
    private static final String SENTINEL = "deeperdarker:stalker";
    private static final int GUARDS_AT_START = 10;
    private static final int GUARD_CAP = 10;
    private static final int PRESSURE_EVERY = 900;
    /** Les titres de fin restent cinq secondes : on doit avoir le temps de les lire. */
    private static final int END_TITLE_STAY = 100;

    /** L'arene en train de se poser, ou null. */
    @Nullable
    private static com.emerald.jak.JakBuilder.Job arenaJob;
    /** Avant elle, le terrain : son emprise videe et fondee, les talus autour (cahier §92). */
    @Nullable
    private static SiteTerrain.Blend arenaGround;
    @Nullable
    private static com.emerald.jak.JakBuilder.Plan arenaPlan;
    /** Le rappel de fin de partie, quelques secondes apres le titre. */
    private static long hintAt = -1L;
    /** En monde ouvert : quand relancer le cycle, une fois la victoire savouree. */
    private static long cycleAt = -1L;
    /** Le temps qu'on laisse au joueur entre le boss abattu et les trois suivants. */
    private static final int CYCLE_DELAY = 20 * 20;

    /**
     * Les reperes de l'arene, en cellules du volume : le niveau du sol, la place du boss et
     * celles des gardes, et le sha1 du volume dont ils sont tires (tools/jak_arena.py).
     */
    record ArenaMarks(int floor, BlockPos centre, List<BlockPos> guards, String sha1) {

        @Nullable
        static ArenaMarks load(net.minecraft.server.MinecraftServer server) {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                    "jak/" + ARENA_VOLUME + ".json");
            java.util.Optional<net.minecraft.server.packs.resources.Resource> found =
                    server.getResourceManager().getResource(key);
            if (found.isEmpty()) {
                return null;
            }
            try (java.io.Reader reader = found.get().openAsReader()) {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                List<BlockPos> guards = new ArrayList<>();
                for (com.google.gson.JsonElement spot : json.getAsJsonArray("gardes")) {
                    guards.add(cell(spot.getAsJsonArray()));
                }
                return new ArenaMarks(json.get("sol").getAsInt(), cell(json.getAsJsonArray("centre")), guards,
                        json.get("sha1").getAsString());
            } catch (java.io.IOException | RuntimeException e) {
                LOGGER.error("reperes de l'arene illisibles", e);
                return null;
            }
        }

        private static BlockPos cell(com.google.gson.JsonArray xyz) {
            return new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt());
        }
    }

    private Finale() {
    }

    // ---------------------------------------------------------- le lever

    /**
     * Leve le Faisceau. Sans site impose, l'arene se place entre deux
     * sanctuaires ; sans boss impose, il est tire au sort parmi ceux qui sont
     * installes. La structure se pose ensuite sur quelques ticks, puis le boss
     * et les gardes apparaissent.
     *
     * @return le centre de l'arene, ou null si l'identifiant de boss impose est inconnu
     */
    @Nullable
    public static BlockPos begin(ServerLevel level, @Nullable BlockPos site, @Nullable String bossId) {
        GameState state = GameState.get(level);
        EntityType<?> bossType = pickBoss(level, bossId);
        if (bossType == null) {
            return null;
        }
        // LE SITE LE PLUS PLAT a portee, au sol median (cahier §92) ; un site impose (la commande,
        // l'automate de photos) garde sa place et ne prend que la hauteur mediane
        BlockPos wanted = site != null ? site : chooseSite(level, state);
        SiteTerrain.Site flat = SiteTerrain.flattest(level, wanted.getX(), wanted.getZ(), ARENA_HALF,
                site != null ? 0 : 48, (x, z) -> farEnough(state, x, z));
        BlockPos center = new BlockPos(flat.x(), flat.y() + 1, flat.z());
        state.beginFinale(center, EntityType.getKey(bossType).toString(), level.getGameTime());

        GameManager.announce(level, "game.emeraldweapons.rainbow",
                "game.emeraldweapons.rainbow.sub", 0xB98CFF, END_TITLE_STAY);
        for (ServerPlayer player : level.players()) {
            double dx = center.getX() + 0.5 - player.getX();
            double dz = center.getZ() + 0.5 - player.getZ();
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.rainbow.at",
                            (int) Math.sqrt(dx * dx + dz * dz), cardinal(dx, dz))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.finale.boss",
                            bossType.getDescription())
                    .withStyle(ChatFormatting.RED));
            player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.MASTER, 0.9F, 0.7F);
            player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 1.0F, 0.8F);
        }

        if (raiseArena(level, center, bossType)) {
            LOGGER.info("Faisceau leve en {} ; boss {} ; l'arene de Spargus se pose",
                    center.toShortString(), EntityType.getKey(bossType));
        } else {
            populate(level, raiseKnoll(level, center), center, bossType);
            LOGGER.info("Faisceau leve en {} ; boss {} ; sans arene",
                    center.toShortString(), EntityType.getKey(bossType));
        }
        return center;
    }

    /**
     * Entre deux sanctuaires, a 300 blocs du village : assez loin pour etre
     * un voyage, assez pres pour rester dans la Maree quand elle se referme.
     */
    private static BlockPos chooseSite(ServerLevel level, GameState state) {
        BlockPos village = state.village();
        List<BlockPos> anchors = state.anchors();
        if (anchors.size() >= 2) {
            for (int i = 0; i < anchors.size(); i++) {
                BlockPos a = anchors.get(i);
                BlockPos b = anchors.get((i + 1) % anchors.size());
                double ax = a.getX() - village.getX(), az = a.getZ() - village.getZ();
                double bx = b.getX() - village.getX(), bz = b.getZ() - village.getZ();
                double la = Math.hypot(ax, az), lb = Math.hypot(bx, bz);
                if (la < 1 || lb < 1) {
                    continue;
                }
                double mx = ax / la + bx / lb, mz = az / la + bz / lb;
                double lm = Math.hypot(mx, mz);
                if (lm < 0.05) {
                    continue;                          // ancres opposees : pas de bissectrice
                }
                BlockPos site = new BlockPos(
                        (int) Math.round(village.getX() + mx / lm * ARENA_DISTANCE), 0,
                        (int) Math.round(village.getZ() + mz / lm * ARENA_DISTANCE));
                boolean clear = true;
                for (BlockPos anchor : anchors) {
                    if (Math.hypot(anchor.getX() - site.getX(), anchor.getZ() - site.getZ())
                            < KEEP_FROM_ANCHORS) {
                        clear = false;
                    }
                }
                if (clear) {
                    return site;
                }
            }
        }
        // a defaut, plein est ; assez loin d'un sanctuaire eventuel pour ne pas le toucher
        return new BlockPos(village.getX() + ARENA_DISTANCE, 0, village.getZ());
    }

    /**
     * Pose l'arene de Spargus autour du site : le centre de son sol sur le sol du monde. Tout
     * ce que la boite contient sous ce niveau devient de la terre cuite -- la pierre des
     * gradins -- et tout ce qui est au-dessus de l'air : l'arene se pose sur un socle plat,
     * sans arbre dans ses gradins ni vide sous ses rigoles. Le boss et le Sculk viennent
     * quand le dernier bloc est pose.
     *
     * @return faux si le volume manque, ne correspond pas a ses reperes, ou si un autre
     *         chantier occupe deja la place : on se rabat alors sur la butte
     */
    private static boolean raiseArena(ServerLevel level, BlockPos center, EntityType<?> bossType) {
        com.emerald.jak.JakVolume volume = com.emerald.jak.JakVolume.load(level.getServer(), ARENA_VOLUME);
        ArenaMarks marks = ArenaMarks.load(level.getServer());
        if (volume == null || marks == null || !marks.sha1().equals(volume.sha1())) {
            LOGGER.warn("Arene de Spargus absente ou sans ses reperes (volume {}, reperes {})",
                    volume == null ? "absent" : volume.sha1(), marks == null ? "absents" : marks.sha1());
            return false;
        }
        int floorY = center.getY() - 1;                  // le sol du monde, ou le sol de l'arene se pose
        BlockPos origin = new BlockPos(center.getX() - marks.centre().getX(), floorY - marks.floor(),
                center.getZ() - marks.centre().getZ());
        BlockPos perch = origin.offset(marks.centre());
        List<BlockPos> posts = marks.guards().stream().map(origin::offset).toList();
        // LE TERRAIN D'ABORD (cahier §92). La boite n'est plus remise a plat d'un bord a
        // l'autre -- terre cuite dessous, air dessus, des bords droits : seules les colonnes de
        // l'arene sont videes et fondees, et le terrain autour se raccorde en pente douce au pied
        // de ses rochers, un bloc sous son sol.
        int talus = ARENA_TALUS;
        int w = volume.width() + 2 * talus;
        int d = volume.depth() + 2 * talus;
        java.util.BitSet inside = new java.util.BitSet(w * d);
        int[] row = new int[volume.width()];
        for (int y = 0; y < volume.height(); y++) {
            for (int z = 0; z < volume.depth(); z++) {
                volume.row(y, z, 0, volume.width(), row);
                for (int x = 0; x < volume.width(); x++) {
                    if (row[x] != 0) {
                        inside.set((x + talus) + (z + talus) * w);
                    }
                }
            }
        }
        arenaGround = new SiteTerrain.Blend(level, origin.getX() - talus, origin.getZ() - talus, w, d, inside,
                floorY - 1, talus, origin.getY() - 1);
        arenaPlan = new com.emerald.jak.JakBuilder.Plan(level, volume, origin)
                .clear(false)
                .awaitEntities(true)
                .beforePlace(world -> clearBox(world, origin, volume))
                .clearFluidTicks(true)
                .label("arene du boss")
                .onDone(report -> {
                    arenaJob = null;
                    LOGGER.info("Arene de Spargus posee en {} s ; boss en {}", report.seconds(),
                            perch.toShortString());
                    spawnBoss(level, perch, bossType);
                    spawnGuardsAt(level, posts, GUARDS_AT_START);
                });
        return true;
    }

    /** Le terrain de l'arene par bouchees, puis sa pose. */
    private static void tickArenaGround(ServerLevel level) {
        SiteTerrain.Blend ground = arenaGround;
        if (ground == null || !ground.step(System.nanoTime() + 15_000_000L)) {
            return;
        }
        LOGGER.info("Arene : {} colonnes du terrain videes, fondees ou raccordees", ground.moved());
        arenaGround = null;
        com.emerald.jak.JakBuilder.Plan plan = arenaPlan;
        arenaPlan = null;
        if (plan != null) {
            arenaJob = com.emerald.jak.JakBuilder.start(plan);
            if (arenaJob == null) {
                LOGGER.warn("La pose de l'arene attend : un autre chantier occupe la place");
            }
        }
    }

    /** Un centre d'arene assez loin de tous les sanctuaires, et du village. */
    private static boolean farEnough(GameState state, int x, int z) {
        BlockPos village = state.village();
        if (!village.equals(BlockPos.ZERO) && Math.hypot(village.getX() - x, village.getZ() - z) < KEEP_FROM_VILLAGE) {
            return false;
        }
        for (BlockPos anchor : state.anchors()) {
            if (Math.hypot(anchor.getX() - x, anchor.getZ() - z) < KEEP_FROM_ANCHORS) {
                return false;
            }
        }
        return true;
    }

    /** Ce qui vivait dans la boite -- arbres abattus, betes, objets -- ne reste pas mure dans les gradins. */
    private static void clearBox(ServerLevel level, BlockPos origin, com.emerald.jak.JakVolume volume) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(origin.getX(), origin.getY(),
                origin.getZ(), origin.getX() + volume.width(), origin.getY() + volume.height(),
                origin.getZ() + volume.depth());
        for (Entity entity : level.getEntities((Entity) null, box,
                e -> e instanceof Mob || e instanceof net.minecraft.world.entity.item.ItemEntity)) {
            entity.discard();
        }
    }

    /** Le boss a sa place, le Sculk autour : sur la butte, quand l'arene manque. */
    private static void populate(ServerLevel level, BlockPos perch, BlockPos center,
                                 EntityType<?> bossType) {
        spawnBoss(level, perch, bossType);
        spawnGuards(level, center, GUARDS_AT_START);
    }

    /** Sans structure : une butte de pierre pour que le boss ne naisse pas dans un arbre. */
    private static BlockPos raiseKnoll(ServerLevel level, BlockPos center) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10) {
                    continue;
                }
                BlockPos pos = center.offset(dx, 0, dz);
                level.setBlockAndUpdate(pos, Blocks.DEEPSLATE_TILES.defaultBlockState());
                for (int h = 1; h <= 4; h++) {
                    level.setBlockAndUpdate(pos.above(h), Blocks.AIR.defaultBlockState());
                }
            }
        }
        return center.above();
    }

    /** L'arene est-elle encore en train de se poser ? (l'automate de photos attend la fin) */
    public static boolean arenaRising() {
        return arenaGround != null || arenaJob != null;
    }

    // ---------------------------------------------------------- le boss

    @Nullable
    private static EntityType<?> pickBoss(ServerLevel level, @Nullable String forced) {
        if (forced != null && !forced.isEmpty()) {
            return EntityType.byString(forced).orElse(null);
        }
        List<EntityType<?>> pool = new ArrayList<>();
        for (String[] boss : BOSSES) {
            EntityType.byString(boss[0]).ifPresent(pool::add);
        }
        return pool.isEmpty() ? EntityType.WITHER : pool.get(level.random.nextInt(pool.size()));
    }

    /** Les identifiants des boss de l'arene. */
    public static List<String> bosses() {
        List<String> ids = new ArrayList<>();
        for (String[] boss : BOSSES) {
            ids.add(boss[0]);
        }
        return ids;
    }

    /** L'echelle d'un boss de l'arene ; 1 pour un boss hors de la liste (impose par la commande). */
    public static double scaleOf(String id) {
        for (String[] boss : BOSSES) {
            if (boss[0].equals(id)) {
                return Double.parseDouble(boss[1]);
            }
        }
        return 1.0;
    }

    /**
     * Fait d'un boss un geant : son echelle, et six cents points de vie au moins, a plein.
     * L'attribut d'echelle agrandit la boite, le modele et la portee de ses coups.
     */
    public static void giant(LivingEntity boss) {
        double scale = scaleOf(EntityType.getKey(boss.getType()).toString());
        var size = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (size != null && scale != 1.0) {
            size.setBaseValue(scale);
        }
        var health = boss.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (health != null && health.getBaseValue() < BOSS_HEALTH_FLOOR) {
            health.setBaseValue(BOSS_HEALTH_FLOOR);
        }
        boss.setHealth(boss.getMaxHealth());
    }

    private static void spawnBoss(ServerLevel level, BlockPos perch, EntityType<?> type) {
        BlockPos at = perch;
        // deux blocs d'air au-dessus du perchoir, sinon on monte
        for (int i = 0; i < 6 && !level.getBlockState(at).isAir(); i++) {
            at = at.above();
        }
        Entity boss = type.spawn(level, at, MobSpawnType.EVENT);
        if (boss == null) {
            return;
        }
        boss.addTag(TAG_BOSS);
        if (boss instanceof LivingEntity living) {
            giant(living);
        }
        if (boss instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.sendParticles(ParticleTypes.END_ROD, boss.getX(), boss.getY() + 1.5, boss.getZ(),
                80, 1.5, 2.0, 1.5, 0.08);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.75F, 0.55F, 1.0F), 2.0F),
                boss.getX(), boss.getY() + 1.0, boss.getZ(), 120, 3.0, 2.5, 3.0, 0.02);
        level.playSound(null, at, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.6F);
    }

    // ---------------------------------------------------------- les gardes

    private static void spawnGuards(ServerLevel level, BlockPos center, int count) {
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = 22 + level.random.nextDouble() * 24;
            int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
            int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);
            spawnGuard(level, new BlockPos(x, WorldSetup.surfaceY(level, x, z), z),
                    i == 0 ? sentinelType(level) : guardType(level));
        }
    }

    /** Le Sculk aux places de la couronne, la sentinelle d'abord. */
    private static void spawnGuardsAt(ServerLevel level, List<BlockPos> posts, int count) {
        List<BlockPos> shuffled = new ArrayList<>(posts);
        java.util.Collections.shuffle(shuffled, new java.util.Random(level.random.nextLong()));
        for (int i = 0; i < Math.min(count, shuffled.size()); i++) {
            spawnGuard(level, shuffled.get(i), i == 0 ? sentinelType(level) : guardType(level));
        }
    }

    private static void spawnGuard(ServerLevel level, BlockPos spot, @Nullable EntityType<?> type) {
        // jamais dans une rigole : la surface d'une colonne de lave est la lave elle-meme
        if (type == null || !level.isLoaded(spot) || !level.getFluidState(spot.below()).isEmpty()) {
            return;
        }
        Entity guard = type.spawn(level, spot, MobSpawnType.EVENT);
        if (guard == null) {
            return;
        }
        guard.addTag(TAG_GUARD);
        if (guard instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, guard.getX(), guard.getY() + 0.8,
                guard.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
    }

    @Nullable
    private static EntityType<?> guardType(ServerLevel level) {
        List<EntityType<?>> pool = new ArrayList<>();
        for (String id : GUARDS) {
            EntityType.byString(id).ifPresent(pool::add);
        }
        if (pool.isEmpty()) {
            EntityType<?>[] fallback = SiegeRoster.vanillaFallback(3);
            return fallback.length == 0 ? null : fallback[level.random.nextInt(fallback.length)];
        }
        return pool.get(level.random.nextInt(pool.size()));
    }

    @Nullable
    private static EntityType<?> sentinelType(ServerLevel level) {
        return EntityType.byString(SENTINEL).orElse(guardType(level));
    }

    /**
     * L'arene reste gardee tant que le boss vit : quand les joueurs y sont et
     * que les gardes se font rares, il en vient d'autres, pres d'eux.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        tickArenaGround(level);
        // LE CYCLE SUIVANT, en monde ouvert : trois nouveaux sanctuaires.
        if (cycleAt >= 0L && level.getGameTime() >= cycleAt) {
            cycleAt = -1L;
            GameManager.raiseNextCycle(level);
        }
        if (hintAt >= 0L && level.getGameTime() >= hintAt) {
            hintAt = -1L;
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(Component.translatable("game.emeraldweapons.end.hint")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        if (level.getGameTime() % PRESSURE_EVERY != 0) {
            return;
        }
        GameState state = GameState.get(level);
        BlockPos center = state.finale();
        if (center.equals(BlockPos.ZERO) || state.status() != GameState.Status.RUNNING) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center.getX() + 0.5, player.getY(), center.getZ() + 0.5) > 120.0 * 120.0) {
                continue;
            }
            long guards = level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(90.0), e -> e.getTags().contains(TAG_GUARD)).size();
            if (guards >= GUARD_CAP) {
                continue;
            }
            int wanted = 2 + level.random.nextInt(2);
            for (int i = 0; i < wanted; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double dist = 18 + level.random.nextDouble() * 10;
                int x = (int) Math.round(player.getX() + Math.cos(angle) * dist);
                int z = (int) Math.round(player.getZ() + Math.sin(angle) * dist);
                spawnGuard(level, new BlockPos(x, WorldSetup.surfaceY(level, x, z), z), guardType(level));
            }
        }
    }

    // ---------------------------------------------------------- la fin

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || !event.getEntity().getTags().contains(TAG_BOSS)) {
            return;
        }
        victory(level, event.getEntity().blockPosition());
    }

    /** La victoire a la commande : sans boss, la porte de retour se cherche une place. */
    public static void victory(ServerLevel level) {
        victory(level, null);
    }

    /**
     * Le boss est tombe : titre, feux d'artifice, les gardes se dissipent.
     *
     * @param where la ou il est tombe : la porte de retour a Haven s'y ouvre ; null a la commande
     */
    public static void victory(ServerLevel level, @Nullable BlockPos where) {
        GameState state = GameState.get(level);
        String time = clock(state.elapsed(level));
        // EN MONDE OUVERT, LA VICTOIRE N'EST PAS UNE FIN.
        //
        // On garde tout ce qui la rend bonne -- le titre, les feux -- et on retire
        // la seule chose qui n'a pas de sens ici : l'ecran de fin. La partie ne se
        // ferme pas, elle repart. (La Plume d'ailes est la recompense du Defi.)
        boolean endless = !state.timed();
        if (state.status() == GameState.Status.RUNNING && !endless) {
            state.finish(true);
        }
        GameManager.announce(level,
                endless ? "game.emeraldweapons.cycle.won" : "game.emeraldweapons.won",
                endless ? "game.emeraldweapons.cycle.won.sub" : "game.emeraldweapons.won.sub",
                0xFFD36B, END_TITLE_STAY);
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
            player.sendSystemMessage(endless
                    ? Component.translatable("game.emeraldweapons.cycle.won.chat", state.cycle())
                            .withStyle(ChatFormatting.GOLD)
                    : Component.translatable("game.emeraldweapons.won.chat", time)
                            .withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < 3; i++) {
                fireworks(level, player.getX() + (level.random.nextDouble() - 0.5) * 6.0,
                        player.getY() + 1.0, player.getZ() + (level.random.nextDouble() - 0.5) * 6.0);
            }
        }
        if (!endless) {
            awardWingSkins(level);
        }
        dissolveGuards(level);
        boolean haven = !endless && com.emerald.haven.journey.HavenReturn.onVictory(level, where);
        hintAt = endless || haven ? -1L : level.getGameTime() + 100L;
        cycleAt = endless ? level.getGameTime() + CYCLE_DELAY : -1L;
    }

    /**
     * LA RECOMPENSE DU DEFI REUSSI (cahier §96) : une Plume d'ailes pour chaque joueur, au
     * hasard parmi les apparences qu'il n'a pas encore. Avec les sanctuaires (trois chances
     * sur cent), c'est la seule facon d'en avoir. Le monde ouvert n'en donne pas a la
     * victoire : sa partie ne finit pas, elle repart.
     */
    public static void awardWingSkins(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            com.emerald.item.SkinFeatherItem.reward(player,
                    com.emerald.item.SkinFeatherItem.pickReward(player, level.random), "game.emeraldweapons.wingskin.won");
        }
    }

    /** Le temps est ecoule : la Maree a tout recouvert. */
    public static void defeat(ServerLevel level) {
        GameState state = GameState.get(level);
        if (state.status() == GameState.Status.RUNNING) {
            state.finish(false);
        }
        GameManager.announce(level, "game.emeraldweapons.lost",
                "game.emeraldweapons.lost.sub", 0xB98CFF, END_TITLE_STAY);
        for (ServerPlayer player : level.players()) {
            player.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0F, 0.5F);
            player.playNotifySound(SoundEvents.BEACON_DEACTIVATE, SoundSource.MASTER, 1.0F, 0.6F);
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.lost.chat")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        boolean haven = com.emerald.haven.journey.HavenReturn.onDefeat(level);
        hintAt = haven ? -1L : level.getGameTime() + 100L;
    }

    /** Un arret de partie abandonne l'arene en cours de pose. */
    public static void clear() {
        if (arenaGround != null) {
            arenaGround.release();
            arenaGround = null;
            arenaPlan = null;
        }
        if (arenaJob != null) {
            com.emerald.jak.JakBuilder.cancel(arenaJob);
            arenaJob = null;
        }
        hintAt = -1L;
        cycleAt = -1L;
    }

    private static void dissolveGuards(ServerLevel level) {
        // ON RELEVE D'ABORD, ON EFFACE ENSUITE : la meme faute que dans le
        // balayage des jalons de l'Aurore, qui a fait tomber le serveur en une
        // minute. Retirer une entite pendant qu'on parcourt la vue troue ses
        // sections, et la vue rend alors des nulls.
        List<Entity> doomed = new ArrayList<>();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity != null && entity.getTags().contains(TAG_GUARD) && entity.isAlive()) {
                doomed.add(entity);
            }
        }
        for (Entity entity : doomed) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, entity.getX(), entity.getY() + 0.8,
                    entity.getZ(), 16, 0.4, 0.6, 0.4, 0.03);
            entity.discard();
        }
    }

    private static void fireworks(ServerLevel level, double x, double y, double z) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(0xFF6B6B, 0xFFD36B, 0x78E8AE, 0x9CE8FF, 0xB98CFF),
                IntList.of(0xFFFFFF), true, true))));
        level.addFreshEntity(new FireworkRocketEntity(level, x, y, z, rocket));
    }

    private static String clock(long ticks) {
        long seconds = Math.max(0L, ticks / 20L);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    /** Direction cardinale, dans la convention de la boussole du jeu (sud = 0). */
    public static String cardinal(double dx, double dz) {
        String[] names = {"S", "SO", "O", "NO", "N", "NE", "E", "SE"};
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        int index = (int) Math.round(((angle + 360.0) % 360.0) / 45.0) % 8;
        return names[(index + 6) % 8];
    }
}
