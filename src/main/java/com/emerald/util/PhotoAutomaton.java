package com.emerald.util;

import com.emerald.haven.HavenRules;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.datafixers.util.Pair;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
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
import net.minecraft.world.level.block.Blocks;
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
 *
 * LA VITRINE (« nom@vitrine:face », « nom@vitrine:biais », « nom@vitrine:proche0 »...)
 * montre des BLOCS avant de les poser dans le monde : une estrade de pierre lisse en
 * plein ciel (y = 200, au-dessus du joueur ou de l'origine), les etats de bloc de
 * EMERALDWEAPONS_PHOTOS_VITRINE (« etat|etat|... », en syntaxe de commande, face au
 * nord) poses cote a cote tous les deux blocs, a midi ; la camera les regarde de face,
 * de biais, ou de pres (le bloc numero N) ; « nom@vitrine:face@18000 » la prend a
 * cette heure du jour (la nuit, pour les lueurs). Pour de grands objets (la porte de victoire,
 * trois blocs de large) : EMERALDWEAPONS_PHOTOS_VITRINE_PAS (l'ecart entre deux blocs),
 * _RECUL (la camera recule d'autant de fois) et _HAUTEUR (ou elle vise, en blocs).
 *
 * LES PRISES DE HAVEN (« nom@haven:accueil », « nom@haven:qg ») regardent le parcours
 * du joueur, INTERFACE VISIBLE : titre et barre d'objectif. Rien n'est prepare -- ni
 * chantier, ni mode eteint : le joueur arrive dans la ville comme n'importe qui.
 * « accueil » attend la premiere arrivee et son titre ; « qg » pose le joueur dans le
 * Hip Hog, face a la borne, et attend que le titre soit parti. Le lot 2 : « envahie »
 * met le joueur au retour de son premier Defi et rouvre la ville (la deuxieme arrivee et
 * son titre) ; « ratelier » le pose devant le comptoir, face au ratelier ; « reprise »
 * lui fait prendre l'arme, meme place (la barre de la reprise) ; « victoire » le pose
 * dans l'overworld, attend que le terrain soit dessine, puis gagne un Defi a six blocs
 * devant lui : la porte de victoire se pose EN DIRECT, comme en partie. Les transports :
 * « arche » le pose dans l'arche ouest (il ressort a l'autre bout de la ville), « portail »
 * sur le plateau du pied de la tour ouest, le regard en haut (il monte a la terrasse) --
 * le vrai client, le vrai declenchement. « camera » le pose, en
 * spectateur et en vision nocturne, a la place et dans l'axe de
 * EMERALDWEAPONS_PHOTOS_CAMERA (« x,y,z,lacet,tangage », en cellules du volume ;
 * plusieurs places separees par « | », prises « camera0 », « camera1 »...), pour
 * regarder un coin sombre de la ville, interface masquee. Il faut un monde ou la
 * ville est posee et le lobby ouvert (EMERALDWEAPONS_PHOTOS_MONDE choisit la sauvegarde
 * du run « photos »).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class PhotoAutomaton {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    public static final String VARIABLE = "EMERALDWEAPONS_PHOTOS";
    private static final String SPEC = Objects.requireNonNullElse(System.getenv(VARIABLE), "").trim();
    private static final String ORIGIN = Objects.requireNonNullElse(System.getenv(VARIABLE + "_ORIGINE"), "").trim();
    private static final String SHOWCASE = Objects.requireNonNullElse(System.getenv(VARIABLE + "_VITRINE"), "").trim();
    /** La camera libre de Haven : « x,y,z,lacet,tangage » en cellules du volume (prise « nom@haven:camera »). */
    private static final String CAMERA = Objects.requireNonNullElse(System.getenv(VARIABLE + "_CAMERA"), "").trim();
    /** L'ecart entre deux blocs de la vitrine, en blocs. */
    private static final int SHOWCASE_STEP = intEnv(VARIABLE + "_VITRINE_PAS", 2);
    /** La camera de la vitrine recule d'autant de fois. */
    private static final double SHOWCASE_BACK = doubleEnv(VARIABLE + "_VITRINE_RECUL", 1.0);
    /** Ou vise la camera de la vitrine, en blocs au-dessus de l'estrade. */
    private static final double SHOWCASE_AIM = doubleEnv(VARIABLE + "_VITRINE_HAUTEUR", 0.5);
    /** Les prises de Haven deja preparees (une seule fois chacune). */
    private static final java.util.Set<String> PREPARED = new java.util.HashSet<>();
    /** La prise « victoire » : tiques depuis l'arrivee dans l'overworld, puis depuis la victoire. */
    private static int victoryTicks;
    private static boolean victoryDone;
    /** Le centre de l'estrade de la vitrine, une fois batie. */
    @Nullable
    private static BlockPos stage;
    private static final List<BlockPos> STAGED = new ArrayList<>();
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
        /** Une prise du parcours de Haven, interface visible. */
        boolean haven() {
            return "haven".equals(this.biome.getNamespace());
        }

        /** Une prise de la vitrine de blocs. */
        boolean showcase() {
            return "vitrine".equals(this.biome.getNamespace());
        }

        /** Tiques d'attente d'une prise de Haven : le titre visible, ou deja parti ; le bout de la ville, charge. */
        int havenSettle() {
            String path = this.biome.getPath();
            if ("accueil".equals(path) || "envahie".equals(path)) {
                return 30;
            }
            return "arche".equals(path) ? 200 : 140;
        }

        /** Une prise de Haven qui montre l'interface (titre, barre) ; la camera libre la masque. */
        boolean havenGui() {
            return !this.biome.getPath().startsWith("camera");
        }
    }

    /** Une prise de Haven attend au plus une minute que le joueur soit arrive. */
    private static final int HAVEN_MAX_WAIT = 1200;
    private static int havenWait;
    /** La prise en cours montre l'interface (prises de Haven). */
    private static volatile boolean pendingGui;
    private static volatile int pendingSettle = SETTLE;

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

    private static int intEnv(String name, int fallback) {
        try {
            return Integer.parseInt(Objects.requireNonNullElse(System.getenv(name), "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double doubleEnv(String name, double fallback) {
        try {
            return Double.parseDouble(Objects.requireNonNullElse(System.getenv(name), "").trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
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
        int ready = pendingGui ? 10 : READY_TICKS;
        return pending != null && waited >= pendingSettle && (clientReady >= ready || waited >= MAX_WAIT);
    }

    /** La prise en cours montre-t-elle l'interface (titre, barre d'objectif) ? */
    public static boolean pendingGui() {
        return pendingGui;
    }

    /** La prise attend depuis plus d'une minute : le client la fait quand meme. */
    public static boolean overdue() {
        return pending != null && waited >= MAX_WAIT;
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
        boolean havenNext = index < SHOTS.size() && SHOTS.get(index).haven();
        if (++ticks < START_DELAY && !havenNext) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        ServerLevel overworld = server.overworld();
        if (havenNext && !finished) {
            havenShot(server, player, SHOTS.get(index));
            return;
        }
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

    /**
     * La vitrine : l'estrade batie une fois, puis la camera placee pour la prise -- de
     * face, de biais, ou de pres du bloc numero N (« proche2 »).
     */
    private static boolean placeShowcase(ServerLevel level, ServerPlayer player, Shot shot) {
        // l'heure de la prise : midi, ou celle qu'elle demande
        run(level.getServer(), "time set " + (shot.height() > 0 ? shot.height() : 6000));
        if (stage == null) {
            BlockPos from = player.blockPosition();
            if (!ORIGIN.isEmpty()) {
                String[] xz = ORIGIN.split(",");
                from = new BlockPos(Integer.parseInt(xz[0].trim()), 64, Integer.parseInt(xz[1].trim()));
            }
            BlockPos center = new BlockPos(from.getX(), 200, from.getZ());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.getChunk((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz);
                }
            }
            for (int x = -7; x <= 7; x++) {
                for (int z = -5; z <= 4; z++) {
                    level.setBlock(center.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                    for (int y = 0; y <= 7; y++) {
                        level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
            String[] states = SHOWCASE.isEmpty() ? new String[0] : SHOWCASE.split("\\|");
            for (int i = 0; i < states.length; i++) {
                BlockPos at = center.offset((int) Math.round((i - (states.length - 1) / 2.0) * SHOWCASE_STEP), 0, 0);
                try {
                    BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),
                            states[i].trim(), false).blockState();
                    level.setBlock(at, state, 3);
                    STAGED.add(at);
                } catch (Exception e) {
                    LOGGER.warn("photos : vitrine, etat illisible « {} » : {}", states[i], e.getMessage());
                }
            }
            stage = center;
            LOGGER.info("photos : vitrine batie en {}, {} bloc(s)", center.toShortString(), STAGED.size());
        }
        double cx = stage.getX() + 0.5;
        double cy = stage.getY();
        double cz = stage.getZ() + 0.5;
        String view = shot.biome().getPath();
        double ex;
        double ey;
        double ez;
        double tx = cx;
        double ty = cy + SHOWCASE_AIM;
        double tz = cz;
        double k = SHOWCASE_BACK;
        if (view.startsWith("proche") && !STAGED.isEmpty()) {
            int n = Math.max(0, Math.min(STAGED.size() - 1, Integer.parseInt(view.substring(6).isEmpty() ? "0" : view.substring(6))));
            BlockPos at = STAGED.get(n);
            tx = at.getX() + 0.5;
            ty = cy + SHOWCASE_AIM + 0.05;
            ex = tx + 0.8 * k;
            ey = ty + 0.5 * k;
            ez = cz - 1.6 * k;
        } else if ("biais".equals(view)) {
            ex = cx + 2.8 * k;
            ey = ty + 1.0 * k;
            ez = cz - 3.0 * k;
        } else {
            ex = cx;
            ey = ty + 0.5 * k;
            ez = cz - 3.8 * k;
        }
        double dx = tx - ex;
        double dz = tz - ez;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(ty - ey, Math.sqrt(dx * dx + dz * dz)));
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, ex, ey - player.getEyeHeight(), ez, yaw, pitch);
        List<String> staged = new ArrayList<>();
        for (BlockPos at : STAGED) {
            staged.add(at.toShortString() + " " + level.getBlockState(at) + (level.getBlockEntity(at) == null ? "" : " +entite"));
        }
        LOGGER.info("photos : {} (vitrine, {}) camera {} {} {} ; blocs {}", shot.name(), view,
                Math.round(ex * 10) / 10.0, Math.round(ey * 10) / 10.0, Math.round(ez * 10) / 10.0, staged);
        return true;
    }

    /** Une prise du parcours de Haven : attendre le bon moment, puis la laisser au client, interface visible. */
    private static void havenShot(MinecraftServer server, ServerPlayer player, Shot shot) {
        if (pending == null) {
            if (!havenReady(server, player, shot)) {
                if (++havenWait > HAVEN_MAX_WAIT) {
                    LOGGER.warn("photos : {} jamais prete (Haven : ville posee, lobby ouvert, joueur arrive ?), sautee",
                            shot.name());
                    havenWait = 0;
                    index++;
                }
                return;
            }
            havenWait = 0;
            pending = shot.name();
            pendingGui = shot.havenGui();
            pendingSettle = shot.havenSettle();
            taken = false;
            waited = 0;
            clientReady = 0;
            LOGGER.info("photos : {} ({}), interface visible", shot.name(), shot.biome());
            return;
        }
        ++waited;
        if (waited >= pendingSettle && taken) {
            LOGGER.info("photos : prise {} faite apres {} tiques, en {} {} {}, lacet {}, tangage {}", shot.name(), waited,
                    Math.round(player.getX() * 10) / 10.0, Math.round(player.getY() * 10) / 10.0,
                    Math.round(player.getZ() * 10) / 10.0, Math.round(player.getYRot()), Math.round(player.getXRot()));
            pending = null;
            pendingGui = false;
            pendingSettle = SETTLE;
            index++;
            if (index >= SHOTS.size()) {
                finished = true;
                LOGGER.info("photos : toutes les prises sont faites, fermeture");
            }
        }
    }

    /**
     * Le moment d'une prise de Haven. « accueil » : le joueur est arrive et son titre
     * vient d'etre joue. « qg » : le joueur est pose dans le Hip Hog, face a la borne.
     */
    private static boolean havenReady(MinecraftServer server, ServerPlayer player, Shot shot) {
        if ("victoire".equals(shot.biome().getPath())) {
            return victoryReady(server, player, shot);
        }
        if (!com.emerald.haven.Haven.is(player.level()) || !com.emerald.haven.HavenArrival.lobbyOpen(server)) {
            return false;
        }
        if ("accueil".equals(shot.biome().getPath())) {
            return com.emerald.haven.journey.HavenProgress.get(player.getUUID()).welcomed
                    && !com.emerald.haven.journey.HavenJourney.titlePending(player.getUUID());
        }
        String path = shot.biome().getPath();
        if ("envahie".equals(path)) {
            // la deuxieme arrivee : revenu de son premier Defi, la ville rouvre, envahie
            if (PREPARED.add(shot.name())) {
                com.emerald.haven.journey.HavenProgress.markReturned(player.getUUID());
                ServerLevel overworld = server.overworld();
                com.emerald.game.GameManager.clear();
                com.emerald.game.GameManager.setup(overworld, overworld.getSharedSpawnPos(), false);
                com.emerald.haven.HavenArrival.reopen(server);
                LOGGER.info("photos : {} au retour de son premier Defi, ville rouverte", player.getGameProfile().getName());
                return false;
            }
            return com.emerald.haven.journey.HavenProgress.get(player.getUUID()).invaded
                    && !com.emerald.haven.journey.HavenJourney.titlePending(player.getUUID());
        }
        if ("arche".equals(path) || "portail".equals(path)) {
            return gateReady(server, player, shot, path);
        }
        if ("ratelier".equals(path) || "reprise".equals(path)) {
            BlockPos rack = com.emerald.haven.journey.HavenRack.position(server);
            if ("reprise".equals(path) && PREPARED.add(shot.name())) {
                com.emerald.haven.journey.HavenRack.take(player, rack);
            }
            return faceRack(server, player, rack);
        }
        if (shot.biome().getPath().startsWith("camera")) {
            // « camera2 » : la troisieme place de EMERALDWEAPONS_PHOTOS_CAMERA (« place|place|... »)
            String rest = shot.biome().getPath().substring(6);
            String[] places = CAMERA.split("[|]");
            String[] v = places[Math.max(0, Math.min(places.length - 1, rest.isEmpty() ? 0 : Integer.parseInt(rest)))].split(",");
            if (v.length < 5) {
                LOGGER.warn("photos : camera sans place (EMERALDWEAPONS_PHOTOS_CAMERA=\"x,y,z,lacet,tangage\")");
                return false;
            }
            BlockPos o = com.emerald.haven.HavenState.get(server).origin();
            player.setGameMode(GameType.SPECTATOR);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.NIGHT_VISION, 20 * 120, 0, false, false));
            player.teleportTo((ServerLevel) player.level(), o.getX() + Double.parseDouble(v[0].trim()),
                    o.getY() + Double.parseDouble(v[1].trim()) - player.getEyeHeight(),
                    o.getZ() + Double.parseDouble(v[2].trim()), Float.parseFloat(v[3].trim()), Float.parseFloat(v[4].trim()));
            return true;
        }
        com.emerald.haven.HavenArrival.Layout rooms = com.emerald.haven.HavenArrival.layout(server);
        if (rooms == null) {
            return false;
        }
        BlockPos origin = com.emerald.haven.HavenState.get(server).origin();
        BlockPos center = rooms.hqCenter(origin);
        BlockPos vote = rooms.votePos(origin);
        ServerLevel level = (ServerLevel) player.level();
        BlockPos feet = null;
        for (int r = 0; r <= 8 && feet == null; r++) {
            for (int dx = -r; dx <= r && feet == null; dx++) {
                for (int dz = -r; dz <= r && feet == null; dz++) {
                    for (int dy = -3; dy <= 3 && feet == null; dy++) {
                        BlockPos at = center.offset(dx, dy, dz);
                        if (rooms.inHq(origin, at.getX() + 0.5, at.getY(), at.getZ() + 0.5)
                                && com.emerald.haven.HavenArrival.standable(level, at)) {
                            feet = at;
                        }
                    }
                }
            }
        }
        if (feet == null) {
            LOGGER.warn("photos : aucune place debout dans le Hip Hog pres de {}", center.toShortString());
            return false;
        }
        double dx = vote.getX() + 0.5 - (feet.getX() + 0.5);
        double dz = vote.getZ() + 0.5 - (feet.getZ() + 0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 10.0F);
        return true;
    }

    /**
     * La porte de victoire posee en direct : le joueur dans l'overworld, regard a l'est ; dix
     * secondes pour que le terrain se dessine ; un Defi gagne (partie lancee, ville partie,
     * Finale.victory a six blocs devant lui) ; sept secondes pour que le titre de victoire
     * s'efface. Le monde de photos garde ensuite une partie gagnee : au demarrage suivant,
     * le retour du Defi rouvre la ville (HavenReturn).
     */
    private static boolean victoryReady(MinecraftServer server, ServerPlayer player, Shot shot) {
        ServerLevel overworld = server.overworld();
        if (PREPARED.add(shot.name())) {
            BlockPos stand = com.emerald.game.WorldSetup.findOpenGround(overworld,
                    overworld.getSharedSpawnPos().offset(60, 0, 30), 16);
            player.teleportTo(overworld, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, -90.0F, 12.0F);
            victoryTicks = 0;
            victoryDone = false;
            LOGGER.info("photos : {} dans l'overworld en {}, la victoire dans dix secondes", shot.name(), stand.toShortString());
            return false;
        }
        victoryTicks++;
        if (!victoryDone && victoryTicks >= 200) {
            com.emerald.game.GameState game = com.emerald.game.GameState.get(overworld);
            game.chooseMode(com.emerald.game.GameState.Mode.DEFI);
            game.begin(overworld);
            com.emerald.haven.HavenState.get(server).setPhase(com.emerald.haven.HavenState.Phase.PARTI);
            BlockPos where = player.blockPosition().east(6);
            com.emerald.game.Finale.victory(overworld, where);
            victoryDone = true;
            victoryTicks = 0;
            BlockPos door = com.emerald.haven.journey.HavenReturn.door();
            LOGGER.info("photos : victoire, porte en {}", door);
            // la camera face a la porte (devant ou derriere : l'anneau est le meme des deux
            // cotes), a cinq a dix blocs, sur un sol, et rien entre l'oeil et le centre de l'anneau
            BlockState placed = door == null ? null : overworld.getBlockState(door);
            if (placed != null && placed.hasProperty(com.emerald.block.HavenGateBlock.FACING)) {
                net.minecraft.core.Direction facing = placed.getValue(com.emerald.block.HavenGateBlock.FACING);
                net.minecraft.world.phys.Vec3 target = net.minecraft.world.phys.Vec3.atBottomCenterOf(door).add(0.0, 1.9, 0.0);
                boolean placedCamera = false;
                for (int dist = 7; dist <= 12 && !placedCamera; dist++) {
                    for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[]{facing, facing.getOpposite()}) {
                        for (int dy = 0; dy <= 6 && !placedCamera; dy++) {
                            BlockPos at = door.relative(side, dist).above(dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2);
                            if (!com.emerald.haven.HavenArrival.standable(overworld, at)) {
                                continue;
                            }
                            net.minecraft.world.phys.Vec3 eye = net.minecraft.world.phys.Vec3.atBottomCenterOf(at)
                                    .add(0.0, player.getEyeHeight(), 0.0);
                            net.minecraft.world.phys.BlockHitResult hit = overworld.clip(new net.minecraft.world.level.ClipContext(
                                    eye, target, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
                            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS && !hit.getBlockPos().equals(door)) {
                                continue;
                            }
                            double ddx = target.x - eye.x;
                            double ddz = target.z - eye.z;
                            float yaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
                            float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.sqrt(ddx * ddx + ddz * ddz)));
                            player.teleportTo(overworld, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, pitch);
                            LOGGER.info("photos : camera en {} face a la porte", at.toShortString());
                            placedCamera = true;
                        }
                        if (placedCamera) {
                            break;
                        }
                    }
                }
                if (!placedCamera) {
                    LOGGER.warn("photos : aucune vue degagee sur la porte {}", door.toShortString());
                }
            }
            return false;
        }
        return victoryDone && victoryTicks >= 140;
    }

    /**
     * Les transports, par le vrai client : une fois pose dans l'arche ouest (ou sur le plateau
     * du pied de la tour ouest, le regard en haut), c'est la tique de HavenGates qui le deplace ;
     * la prise est prete quand il est arrive (devant l'arche est ; sur la terrasse).
     */
    private static boolean gateReady(MinecraftServer server, ServerPlayer player, Shot shot, String path) {
        ServerLevel level = (ServerLevel) player.level();
        boolean arch = "arche".equals(path);
        com.emerald.haven.HavenGates.Station from = com.emerald.haven.HavenGates.station(arch ? "arche_ouest" : "tour_ouest_pied");
        com.emerald.haven.HavenGates.Station to = com.emerald.haven.HavenGates.station(arch ? "arche_est" : "tour_ouest_terrasse");
        if (from == null || to == null) {
            return false;
        }
        if (PREPARED.add(shot.name())) {
            BlockPos start = com.emerald.haven.HavenGates.position(server, from);
            player.setGameMode(GameType.ADVENTURE);
            // l'arche : dedans, face au nord ; le portail : sur le plateau, le regard en haut, face a la ville
            player.teleportTo(level, start.getX() + 0.5, start.getY() + (arch ? 0.0 : 0.25), start.getZ() + 0.5,
                    180.0F, arch ? 0.0F : -60.0F);
            LOGGER.info("photos : {} pose dans {} ({})", player.getGameProfile().getName(), from.id(), start.toShortString());
            return false;
        }
        BlockPos goal = com.emerald.haven.HavenGates.position(server, to);
        return player.blockPosition().distManhattan(goal) <= (arch ? 4 : 2);
    }

    /** Devant le comptoir, cote clients, face au ratelier : le Morph Gun pose, a hauteur d'yeux. */
    private static boolean faceRack(MinecraftServer server, ServerPlayer player, BlockPos rack) {
        com.emerald.haven.HavenArrival.Layout rooms = com.emerald.haven.HavenArrival.layout(server);
        if (rooms == null) {
            return false;
        }
        BlockPos origin = com.emerald.haven.HavenState.get(server).origin();
        ServerLevel level = (ServerLevel) player.level();
        BlockPos wanted = rack.south(3);
        BlockPos feet = null;
        for (int r = 0; r <= 3 && feet == null; r++) {
            for (int dx = -r; dx <= r && feet == null; dx++) {
                for (int dz = 0; dz <= r && feet == null; dz++) {
                    for (int dy = -3; dy <= 3 && feet == null; dy++) {
                        BlockPos at = wanted.offset(dx, dy, dz);
                        if (rooms.inHq(origin, at.getX() + 0.5, at.getY(), at.getZ() + 0.5)
                                && com.emerald.haven.HavenArrival.standable(level, at)) {
                            feet = at;
                        }
                    }
                }
            }
        }
        if (feet == null) {
            LOGGER.warn("photos : aucune place debout devant le ratelier {}", rack.toShortString());
            return false;
        }
        double dx = rack.getX() + 0.5 - (feet.getX() + 0.5);
        double dz = rack.getZ() + 0.5 - (feet.getZ() + 0.5);
        double dy = rack.getY() + 0.85 - (feet.getY() + player.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, pitch);
        return true;
    }

    /** Le joueur au-dessus du biome, sur le sol, regard vers l'est ; plonge s'il est en hauteur. */
    private static boolean place(MinecraftServer server, ServerLevel level, ServerPlayer player, Shot shot) {
        if (shot.showcase()) {
            return placeShowcase(level, player, shot);
        }
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
