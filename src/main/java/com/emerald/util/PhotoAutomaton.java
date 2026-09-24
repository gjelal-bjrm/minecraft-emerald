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
 * EN MAIN (« nom@main:premiere », « leve », « face », « face_leve », « inventaire »,
 * « livre ») : l'objet EMERALDWEAPONS_PHOTOS_MAIN (un bouclier dans la main gauche, le
 * reste dans la droite), tenu par le joueur debout sur l'estrade de la vitrine, a midi,
 * INTERFACE VISIBLE (sans elle, le jeu ne dessine pas la main) : a la premiere personne,
 * leve (clic droit tenu), vu de face (troisieme personne), l'inventaire ouvert, ou le
 * livre ouvert (l'agenda de Haven : ses pages du moment).
 *
 * LES PORTES DOREES (« nom@porte:village », « nom@porte:village@18000 » la nuit) : l'Heure
 * Doree se leve pour de vrai (GoldenGate), et la camera cadre l'arche du village, l'atelier
 * derriere elle -- pour voir OU elle se pose, et non plus seulement a quoi elle ressemble.
 *
 * LES SANCTUAIRES (« nom@sanctuaire:givre_vol », « _porte », « _cour », « _tour », cahier §90) :
 * chaque theme demande est bati une fois, par le chantier de la partie (a la tique, pas
 * d'une traite), a cinq cents blocs a l'est du precedent, le spectateur au loin ; puis il
 * vient au-dessus de la cour et l'on attend quarante-cinq secondes (troncons recus, lointains
 * de Distant Horizons refaits) ; la camera le regarde alors d'en haut, depuis la porte sud,
 * depuis la cour, ou au pied de la tour sud-ouest. Mode eteint : la garnison reste a son
 * poste et le spectateur ne la derange pas.
 *
 * L'ARENE DU BOSS (« nom@arene:vol », « sol », « gradins », « lave », cahier §91) : l'Arc-en-ciel
 * se leve pour de vrai a cinq cents blocs a l'est (Finale.begin, avec Ignis), l'arene de
 * Spargus se pose ; puis le spectateur vient au-dessus et l'on attend quarante-cinq secondes,
 * comme pour les sanctuaires. Les vues : d'en haut, depuis le sol face au boss, depuis les
 * gradins, et de pres sur une rigole de lave ; « tailles » et « geants » alignent les boss a
 * cote d'un mannequin de la taille d'un joueur, a leur taille d'origine puis agrandis ;
 * « combat0 », « combat1 »... mettent le vrai joueur, en survie, face a chacun (voir COMBAT).
 *
 * L'ECLIPSE (« nom@eclipse:portail », « vague », « brume », cahier §88) : l'Eclipse se
 * leve pour de vrai la ou se tient le joueur, un portail s'ouvre a une dizaine de blocs,
 * et la camera le regarde de face (« portail »), puis quand sa vague est sortie
 * (« vague »), puis vers le lointain, ou le brouillard mange tout sauf les fentes rouges
 * des autres portails (« brume »).
 *
 * LES PRISES DE HAVEN (« nom@haven:accueil », « nom@haven:qg ») regardent le parcours
 * du joueur, INTERFACE VISIBLE : titre et barre d'objectif. Les animaux de la ville
 * (« faune_port », « faune_rue », « faune_bassin », « faune_large », « faune_armee ») sont
 * poses devant la camera, interface masquee (HavenFaunaPhotos). Rien n'est prepare -- ni
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
    /** L'objet des prises en main (« nom@main:vue »). */
    private static final String HELD = Objects.requireNonNullElse(System.getenv(VARIABLE + "_MAIN"), "").trim();
    /** La vue de la prise en main en cours, ou null. */
    @Nullable
    private static volatile String handsView;
    /** La camera libre de Haven : « x,y,z,lacet,tangage » en cellules du volume (prise « nom@haven:camera »). */
    private static final String CAMERA = Objects.requireNonNullElse(System.getenv(VARIABLE + "_CAMERA"), "").trim();
    /**
     * La vitrine « en direct » (EMERALDWEAPONS_PHOTOS_VITRINE_DIRECT=1) : l'estrade est batie
     * a la premiere prise, les blocs poses a la suivante, le joueur deja la -- comme un
     * portail qui s'ouvre pres de lui en partie, et non comme un troncon qui arrive tout fait.
     */
    private static final boolean SHOWCASE_LIVE = "1".equals(System.getenv(VARIABLE + "_VITRINE_DIRECT"));
    private static boolean showcaseFilled;
    private static boolean stageSeen;
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

        /** Une prise d'un objet en main. */
        boolean hands() {
            return "main".equals(this.biome.getNamespace());
        }

        /** Une prise d'une porte doree posee par le jeu. */
        boolean gate() {
            return "porte".equals(this.biome.getNamespace());
        }

        /** Une prise de l'Eclipse. */
        boolean eclipse() {
            return "eclipse".equals(this.biome.getNamespace());
        }

        /** Une prise de l'arene du boss, levee pour elle. */
        boolean arena() {
            return "arene".equals(this.biome.getNamespace());
        }

        /** Une prise d'un sanctuaire bati pour elle. */
        boolean sanctuary() {
            return "sanctuaire".equals(this.biome.getNamespace());
        }

        /** Le theme d'une prise de sanctuaire : « givre » dans « givre_vol ». */
        String sanctuaryTheme() {
            String path = this.biome.getPath();
            int cut = path.indexOf('_');
            return cut < 0 ? path : path.substring(0, cut);
        }

        /** Tiques d'attente d'une prise de Haven : le titre visible, ou deja parti ; le bout de la ville, charge. */
        int havenSettle() {
            String path = this.biome.getPath();
            if ("accueil".equals(path) || "envahie".equals(path)) {
                return 30;
            }
            return "arche".equals(path) ? 200 : 140;
        }

        /**
         * Une prise de Haven qui montre l'interface (titre, barre) ; la camera libre, les
         * animaux et les quetes la masquent -- sauf celles des quetes qui montrent justement
         * un ecran ou un compteur (« _ui »).
         */
        boolean havenGui() {
            String path = this.biome.getPath();
            if (path.startsWith("quete_")) {
                return path.endsWith("_ui");
            }
            return !path.startsWith("camera") && !path.startsWith("faune_");
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

    /** La vue de la prise en main en cours (« premiere », « leve »...), ou null : pour le client. */
    @Nullable
    public static String handsView() {
        return handsView;
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
        if (pending == null && shot.sanctuary() && !sanctuaryBuilt(overworld, player, shot)) {
            return;                       // le chantier avance ; la prise attend qu'il soit fini
        }
        if (pending == null && shot.arena() && !arenaRaised(overworld, player)) {
            return;                       // l'arene se pose ; la prise attend
        }
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
        if (waited >= pendingSettle && taken) {
            if (shot.arena() && shot.biome().getPath().startsWith("combat")) {
                combatReport(shot);
                pendingSettle = SETTLE;
            }
            LOGGER.info("photos : prise {} faite apres {} tiques{}", shot.name(), waited,
                    waited >= MAX_WAIT ? " (terrain pas entierement dessine)" : "");
            pending = null;
            pendingGui = false;
            handsView = null;
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
            stage = center;
            LOGGER.info("photos : estrade batie en {}", center.toShortString());
        }
        // les blocs : tout de suite, ou a la prise suivante si la vitrine est « en direct »
        boolean firstCall = !showcaseFilled && STAGED.isEmpty() && !stageSeen;
        stageSeen = true;
        if (!showcaseFilled && !(SHOWCASE_LIVE && firstCall)) {
            showcaseFilled = true;
            BlockPos center = stage;
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
            LOGGER.info("photos : vitrine garnie en {}, {} bloc(s){}", center.toShortString(), STAGED.size(),
                    SHOWCASE_LIVE ? ", en direct" : "");
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

    /**
     * Une prise en main : le joueur debout au centre de l'estrade (batie par la vitrine),
     * regard au nord, en survie ; l'objet dans la bonne main. Pour « livre », l'agenda de
     * Haven s'ouvre (ses pages du moment) ; le client fait le reste (camera, clic tenu,
     * inventaire).
     */
    private static boolean placeHands(ServerLevel level, ServerPlayer player, Shot shot) {
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(
                HELD.isEmpty() ? "emeraldweapons:arcencium_shield" : HELD));
        if (stage == null) {
            placeShowcase(level, player, new Shot(shot.name(), ResourceLocation.fromNamespaceAndPath("vitrine", "face"), 0));
        }
        run(level.getServer(), "time set 6000");
        String view = shot.biome().getPath();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
        boolean offhand = item instanceof net.minecraft.world.item.ShieldItem;
        player.getInventory().selected = 0;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                offhand ? net.minecraft.world.item.ItemStack.EMPTY : stack.copy());
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                offhand ? stack.copy() : net.minecraft.world.item.ItemStack.EMPTY);
        if (offhand) {
            player.getInventory().setItem(1, stack.copy());     // l'icone dans la barre d'objets aussi
        }
        player.teleportTo(level, stage.getX() + 0.5, stage.getY(), stage.getZ() - 2.5, 180.0F, 8.0F);
        if ("livre".equals(view) && item == com.emerald.item.ModItems.HAVEN_AGENDA.get()) {
            com.emerald.haven.journey.HavenAgenda.open(player);
        }
        handsView = view;
        pendingGui = true;
        LOGGER.info("photos : {} (en main : {}, {})", shot.name(), BuiltInRegistries.ITEM.getKey(item), view);
        return true;
    }

    /**
     * La porte doree du village, posee par le jeu : l'Heure Doree se leve une fois, puis la
     * camera se met derriere l'arche, un peu en hauteur, et regarde l'atelier a travers elle.
     */
    private static boolean placeGate(ServerLevel level, ServerPlayer player, Shot shot) {
        if (PREPARED.add("porte")) {
            com.emerald.weather.GoldenGate.begin(level);
        }
        run(level.getServer(), "time set " + (shot.height() > 0 ? shot.height() : 6000));
        BlockPos gate = com.emerald.weather.GoldenGate.villageGate();
        net.minecraft.core.Direction facing = gate == null ? null : com.emerald.weather.GoldenGate.facing(gate);
        BlockPos workshop = com.emerald.game.GameState.get(level).workshop();
        if (gate == null || facing == null) {
            LOGGER.warn("photos : pas d'arche du village (pas de village dans ce monde ?)");
            return false;
        }
        net.minecraft.world.phys.Vec3 target = workshop.equals(BlockPos.ZERO)
                ? net.minecraft.world.phys.Vec3.atBottomCenterOf(gate).add(0.0, 1.5, 0.0)
                : net.minecraft.world.phys.Vec3.atBottomCenterOf(gate).add(net.minecraft.world.phys.Vec3.atBottomCenterOf(workshop))
                .scale(0.5).add(0.0, 1.0, 0.0);
        net.minecraft.core.Direction side = facing.getClockWise();
        net.minecraft.world.phys.Vec3 eye = net.minecraft.world.phys.Vec3.atBottomCenterOf(gate)
                .add(-facing.getStepX() * 7.0 + side.getStepX() * 3.0, 4.5, -facing.getStepZ() * 7.0 + side.getStepZ() * 3.0);
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.sqrt(dx * dx + dz * dz)));
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        LOGGER.info("photos : {} (porte doree du village en {}, regarde {}, atelier en {})", shot.name(), gate, facing, workshop);
        return true;
    }

    /** Le portail de l'Eclipse ouvert pour les photos, et d'ou la camera le regarde. */
    @Nullable
    private static BlockPos eclipseRift;
    @Nullable
    private static net.minecraft.core.Direction eclipseFacing;
    @Nullable
    private static BlockPos eclipseStand;

    /**
     * L'Eclipse : levee une fois, la ou se tient le joueur ; un portail a une dizaine de
     * blocs, tourne vers lui. « portail » le regarde de face ; « vague » laisse d'abord
     * sortir sa vague ; « brume » regarde au loin, vers le portail le plus lointain.
     */
    private static boolean placeEclipse(ServerLevel level, ServerPlayer player, Shot shot) {
        String view = shot.biome().getPath();
        if (PREPARED.add("eclipse")) {
            eclipseStand = player.blockPosition();
            com.emerald.weather.WeatherManager.force(level, com.emerald.weather.Weather.ECLIPSE, 20 * 280);
            com.emerald.block.ArcPortals.Placement spot = com.emerald.block.ArcPortals.find(level, eclipseStand,
                    9.0, 13.0, 6, 10, 60, eclipseStand, level.random);
            if (spot == null || !com.emerald.weather.Eclipse.open(level, spot.anchor(), spot.facing())) {
                LOGGER.warn("photos : pas de place pour un portail de l'Eclipse pres de {}", eclipseStand);
                return false;
            }
            eclipseRift = spot.anchor();
            eclipseFacing = spot.facing();
            // et les trois portails lointains d'un joueur (le spectateur n'en recoit pas)
            com.emerald.weather.Eclipse.openAround(level, eclipseStand, null, 3);
            LOGGER.info("photos : Eclipse levee, portail en {} tourne {}, portails lointains {}", eclipseRift,
                    eclipseFacing, com.emerald.weather.Eclipse.rifts());
        }
        if (eclipseRift == null || eclipseFacing == null || eclipseStand == null) {
            return false;
        }
        if ("vague".equals(view)) {
            // la vague sort d'un coup : les tiques de l'Eclipse, en avance
            for (int t = 0; t < 60 + 30 * 4 + 5; t++) {
                com.emerald.weather.Eclipse.tickForAutotest(level);
            }
        }
        net.minecraft.world.phys.Vec3 rift = net.minecraft.world.phys.Vec3.atBottomCenterOf(eclipseRift);
        net.minecraft.world.phys.Vec3 eye;
        net.minecraft.world.phys.Vec3 target;
        if ("brume".equals(view)) {
            BlockPos far = eclipseRift;
            for (BlockPos p : com.emerald.weather.Eclipse.rifts()) {
                if (p.distSqr(eclipseStand) > far.distSqr(eclipseStand)) {
                    far = p;
                }
            }
            eye = net.minecraft.world.phys.Vec3.atBottomCenterOf(eclipseStand).add(0.0, 1.62, 0.0);
            target = net.minecraft.world.phys.Vec3.atBottomCenterOf(far).add(0.0, 2.0, 0.0);
        } else {
            double back = "vague".equals(view) ? 11.0 : 8.5;
            net.minecraft.core.Direction side = eclipseFacing.getClockWise();
            double lateral = "vague".equals(view) ? 3.0 : 1.2;
            eye = rift.add(eclipseFacing.getStepX() * back + side.getStepX() * lateral, 1.9,
                    eclipseFacing.getStepZ() * back + side.getStepZ() * lateral);
            target = rift.add(0.0, 2.2, 0.0);
        }
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.sqrt(dx * dx + dz * dz)));
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        LOGGER.info("photos : {} (Eclipse, {}) camera en {}", shot.name(), view, eye);
        return true;
    }

    /** Les sanctuaires batis pour les prises : le centre de leur cour, par theme. */
    private static final java.util.Map<String, BlockPos> SANCTUARIES = new java.util.HashMap<>();
    @Nullable
    private static com.emerald.game.Sanctuary.Job sanctuaryJob;
    @Nullable
    private static BlockPos sanctuaryGround;
    /** Le chantier des photos va plus vite que celui de la partie : personne ne joue. */
    private static final long SANCTUARY_BUDGET = 40_000_000L;
    /** Tiques d'attente apres le chantier, le spectateur au-dessus de la cour. */
    private static final int SANCTUARY_SETTLE = 900;
    private static int sanctuaryWait;
    /** Les vues d'un sanctuaire : l'oeil puis le point vise, par rapport au centre de la cour. */
    private static final java.util.Map<String, double[]> SANCTUARY_VIEWS = java.util.Map.of(
            "vol", new double[]{-120, 85, 150, 0, 12, -10},
            "porte", new double[]{10, 4, 150, 0, 20, 70},
            "cour", new double[]{62, 2.6, 78, 0, 18, 0},
            "tour", new double[]{-150, 25, 140, -96, 25, 96});

    /**
     * Le sanctuaire du theme de la prise est-il debout ? Sinon, son chantier avance d'une
     * bouchee -- le premier appel le lance, a cinq cents blocs a l'est du precedent.
     */
    private static boolean sanctuaryBuilt(ServerLevel level, ServerPlayer player, Shot shot) {
        String theme = shot.sanctuaryTheme();
        if (SANCTUARIES.containsKey(theme)) {
            return true;
        }
        if (sanctuaryJob == null && sanctuaryWait <= 0) {
            BlockPos from = player.blockPosition();
            if (!ORIGIN.isEmpty()) {
                String[] xz = ORIGIN.split(",");
                from = new BlockPos(Integer.parseInt(xz[0].trim()), 64, Integer.parseInt(xz[1].trim()));
            }
            int x = from.getX() + 500 * (SANCTUARIES.size() + 1);
            int z = from.getZ();
            sanctuaryGround = new BlockPos(x, com.emerald.game.WorldSetup.surfaceY(level, x, z) - 1, z);
            // LE SPECTATEUR RESTE AU LOIN pendant le chantier : pose sous ses yeux, chaque bloc
            // partait vers le client, qui rebatissait ses troncons sans fin -- la fenetre a gele
            // et Windows l'a fermee (24 sept.). Il recoit ensuite les troncons finis.
            sanctuaryJob = com.emerald.game.Sanctuary.job(level, null, sanctuaryGround, 1,
                    com.emerald.game.SanctuaryTheme.byId(theme));
            LOGGER.info("photos : sanctuaire {} en chantier en {}", theme, sanctuaryGround);
            return false;
        }
        if (sanctuaryJob != null) {
            sanctuaryJob.advance(SANCTUARY_BUDGET);
            if (!sanctuaryJob.done()) {
                return false;
            }
            LOGGER.info("photos : sanctuaire {} bati, cour en {}, ancre en {}", theme, sanctuaryGround,
                    sanctuaryJob.anchor());
            sanctuaryJob = null;
            // PUIS LE SPECTATEUR ARRIVE, et l'on attend : le client recoit les troncons finis, et
            // Distant Horizons refait ses lointains. Sans cette attente, la foret d'avant le
            // chantier -- les lointains de DH, calcules sur le terrain vierge -- recouvrait la
            // cour et la pyramide sur les photos.
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, sanctuaryGround.getX() + 0.5, sanctuaryGround.getY() + 70,
                    sanctuaryGround.getZ() + 60.5, 180.0F, 45.0F);
            sanctuaryWait = SANCTUARY_SETTLE;
            return false;
        }
        if (--sanctuaryWait > 0) {
            return false;
        }
        SANCTUARIES.put(theme, sanctuaryGround);
        return true;
    }

    /** La camera devant un sanctuaire bati : d'en haut, a la porte, dans la cour ou au pied d'une tour. */
    private static boolean placeSanctuary(ServerLevel level, ServerPlayer player, Shot shot) {
        BlockPos centre = SANCTUARIES.get(shot.sanctuaryTheme());
        String path = shot.biome().getPath();
        double[] view = SANCTUARY_VIEWS.get(path.substring(path.indexOf('_') + 1));
        if (centre == null || view == null) {
            LOGGER.warn("photos : vue de sanctuaire inconnue {}", path);
            return false;
        }
        net.minecraft.world.phys.Vec3 base = net.minecraft.world.phys.Vec3.atBottomCenterOf(centre.above());
        net.minecraft.world.phys.Vec3 eye = base.add(view[0], view[1], view[2]);
        net.minecraft.world.phys.Vec3 target = base.add(view[3], view[4], view[5]);
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.sqrt(dx * dx + dz * dz)));
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        LOGGER.info("photos : {} (sanctuaire {}) camera en {}", shot.name(), path, eye);
        return true;
    }

    /** Le centre de l'arene levee pour les prises, ou null avant. */
    @Nullable
    private static BlockPos arenaCentre;
    private static boolean arenaSettling;
    private static int arenaWait;
    /** Les vues de l'arene : l'oeil puis le point vise, par rapport au centre de son sol. */
    private static final java.util.Map<String, double[]> ARENA_VIEWS = java.util.Map.of(
            "vol", new double[]{0, 44, 22, 0, 0, -28},
            "sol", new double[]{0, 2.6, 46, 0, 5, 0},
            "gradins", new double[]{-50, 12, 30, 0, 2, 0},
            "lave", new double[]{31, 5.5, 38, 22, 0, 22},
            "tailles", new double[]{0, 5, 44, 0, 4, 18},
            "geants", new double[]{0, 8, 50, 0, 7, 18});
    /**
     * LE COMBAT (« nom@arene:combat0 », « combat1 »...) : le vrai joueur, en survie, face a un
     * boss de l'arene -- le boss numero N/2, a sa taille d'origine si N est pair, geant sinon.
     * Un golem de fer ne servait a rien : trois boss sur quatre ne le regardaient meme pas. Et
     * mille points de vie ne protegeaient pas le joueur -- Ignis l'a tue en moins de vingt
     * secondes, les boss de Cataclysm frappent en proportion de la vie de leur cible. Chaque
     * coup recu est donc COMPTE PUIS ANNULE : le joueur garde ses vingt points de vie, ne meurt
     * pas, ne recule pas. La prise se fait apres vingt secondes, et le journal dit ce qu'il a
     * encaisse.
     */
    private static final int COMBAT = 400;
    /** Le combat en cours : ce que le joueur a encaisse, et en combien de coups. */
    private static boolean combatActive;
    private static float combatDamage;
    private static int combatHits;

    /**
     * L'arene est-elle posee ? Le premier appel leve l'Arc-en-ciel a cinq cents blocs a l'est,
     * le spectateur au loin ; quand le dernier bloc est pose, il vient au-dessus du sol et l'on
     * attend (voir les sanctuaires).
     */
    private static boolean arenaRaised(ServerLevel level, ServerPlayer player) {
        if (arenaCentre == null) {
            BlockPos site = player.blockPosition().offset(500, 0, 0);
            arenaCentre = com.emerald.game.Finale.begin(level, site, "cataclysm:ignis");
            if (arenaCentre == null) {
                arenaCentre = BlockPos.ZERO;
            }
            LOGGER.info("photos : arene levee en {}", arenaCentre);
            return false;
        }
        if (com.emerald.game.Finale.arenaRising()) {
            return false;
        }
        if (!arenaSettling) {
            arenaSettling = true;
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level, arenaCentre.getX() + 0.5, arenaCentre.getY() + 60, arenaCentre.getZ() + 50.5,
                    180.0F, 45.0F);
            arenaWait = SANCTUARY_SETTLE;
            return false;
        }
        return --arenaWait <= 0;
    }

    /** La camera dans l'arene : d'en haut, sur le sol face au boss, dans les gradins, pres d'une rigole. */
    private static boolean placeArena(ServerLevel level, ServerPlayer player, Shot shot) {
        if (shot.biome().getPath().startsWith("combat")) {
            return placeCombat(level, player, shot);
        }
        double[] view = ARENA_VIEWS.get(shot.biome().getPath());
        if (arenaCentre == null || arenaCentre.equals(BlockPos.ZERO) || view == null) {
            LOGGER.warn("photos : pas d'arene, ou vue inconnue {}", shot.biome().getPath());
            return false;
        }
        String lineupView = shot.biome().getPath();
        if (("tailles".equals(lineupView) || "geants".equals(lineupView)) && PREPARED.add(lineupView)) {
            boolean giants = "geants".equals(lineupView);
            List<String> lineup = new ArrayList<>(com.emerald.game.Finale.bosses());
            lineup.add(0, "minecraft:armor_stand");       // un mannequin de la taille d'un joueur
            int step = giants ? 13 : 8;
            clearArena(level);
            for (int i = 0; i < lineup.size(); i++) {
                final int slot = i;
                net.minecraft.world.entity.EntityType.byString(lineup.get(i)).ifPresent(type -> {
                    BlockPos at = arenaCentre.offset(-(lineup.size() - 1) * step / 2 + slot * step, 0, 18);
                    net.minecraft.world.entity.Entity e = type.spawn(level, at, net.minecraft.world.entity.MobSpawnType.COMMAND);
                    if (e != null) {
                        e.setYRot(0.0F);
                        e.setYHeadRot(0.0F);
                        if (giants && slot > 0 && e instanceof net.minecraft.world.entity.LivingEntity living) {
                            com.emerald.game.Finale.giant(living);
                        }
                        if (e instanceof net.minecraft.world.entity.Mob mob) {
                            mob.setNoAi(true);
                            mob.setPersistenceRequired();
                            mob.setYBodyRot(0.0F);
                        }
                    }
                });
            }
        }
        net.minecraft.world.phys.Vec3 base = net.minecraft.world.phys.Vec3.atBottomCenterOf(arenaCentre);
        net.minecraft.world.phys.Vec3 eye = base.add(view[0], view[1], view[2]);
        net.minecraft.world.phys.Vec3 target = base.add(view[3], view[4], view[5]);
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.sqrt(dx * dx + dz * dz)));
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
        LOGGER.info("photos : {} (arene, {}) camera en {}", shot.name(), shot.biome().getPath(), eye);
        return true;
    }

    /** Le boss de l'Arc-en-ciel, son Sculk, le rang d'avant : l'arene videe de ses monstres. */
    private static void clearArena(ServerLevel level) {
        for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(arenaCentre).inflate(90), e -> e instanceof net.minecraft.world.entity.Mob)) {
            e.discard();
        }
    }

    /**
     * Le combat numero N : l'arene videe, le joueur en survie avec mille points de vie a huit blocs
     * au sud du centre, le boss N/2 face a lui, a sa taille d'origine ou geant, et pour cible.
     */
    private static boolean placeCombat(ServerLevel level, ServerPlayer player, Shot shot) {
        int n = Integer.parseInt(shot.biome().getPath().substring("combat".length()));
        List<String> bosses = com.emerald.game.Finale.bosses();
        if (arenaCentre == null || arenaCentre.equals(BlockPos.ZERO) || n / 2 >= bosses.size()) {
            return false;
        }
        String id = bosses.get(n / 2);
        boolean giant = n % 2 == 1;
        clearArena(level);
        player.setGameMode(GameType.SURVIVAL);
        player.removeAllEffects();
        player.clearFire();
        player.setHealth(player.getMaxHealth());
        combatActive = true;
        combatDamage = 0.0F;
        combatHits = 0;
        player.teleportTo(level, arenaCentre.getX() + 0.5, arenaCentre.getY(), arenaCentre.getZ() + 8.5, 180.0F, -8.0F);
        net.minecraft.world.entity.Entity e = net.minecraft.world.entity.EntityType.byString(id)
                .map(type -> type.spawn(level, arenaCentre.offset(0, 0, -6), net.minecraft.world.entity.MobSpawnType.EVENT))
                .orElse(null);
        if (e instanceof net.minecraft.world.entity.LivingEntity living) {
            if (giant) {
                com.emerald.game.Finale.giant(living);
            }
            if (living instanceof net.minecraft.world.entity.Mob mob) {
                mob.setPersistenceRequired();
                mob.setTarget(player);
            }
        }
        pendingSettle = COMBAT;
        LOGGER.info("photos : {} ({} {}) face au joueur", shot.name(), id, giant ? "geant" : "a sa taille d'origine");
        return true;
    }

    /** Apres la prise d'un combat : ce que le joueur a encaisse. */
    private static void combatReport(Shot shot) {
        int n = Integer.parseInt(shot.biome().getPath().substring("combat".length()));
        List<String> bosses = com.emerald.game.Finale.bosses();
        String id = n / 2 < bosses.size() ? bosses.get(n / 2) : "?";
        combatActive = false;
        LOGGER.info("photos : COMBAT {} {} : {} coups, {} PV encaisses en {} s", id, n % 2 == 1 ? "geant" : "d'origine",
                combatHits, Math.round(combatDamage), COMBAT / 20);
    }

    /** Pendant un combat, chaque coup que recoit le joueur est compte, puis annule. */
    @SubscribeEvent
    public static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (combatActive && event.getEntity() instanceof ServerPlayer) {
            combatDamage += event.getAmount();
            combatHits++;
            event.setCanceled(true);
        }
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
        if ("depart".equals(path)) {
            // l'arche du depart (HavenDeparture) : ouverte une fois, puis le joueur debout
            // devant elle, a cinq blocs, qui la regarde -- interface visible (le rappel s'y lit)
            if (PREPARED.add(shot.name())) {
                com.emerald.haven.journey.HavenDeparture.open(server, com.emerald.game.GameState.Mode.DEFI, false);
            }
            BlockPos gate = com.emerald.haven.journey.HavenDeparture.gate();
            if (gate == null) {
                return false;
            }
            net.minecraft.core.Direction facing = com.emerald.haven.journey.HavenDeparture.facing();
            ServerLevel level = (ServerLevel) player.level();
            BlockPos stand = gate.relative(facing, 5);
            for (int back = 5; back >= 3; back--) {
                BlockPos at = gate.relative(facing, back);
                if (com.emerald.haven.HavenArrival.standable(level, at)) {
                    stand = at;
                    break;
                }
            }
            player.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                    facing.getOpposite().toYRot(), 8.0F);
            return true;
        }
        if ("arche".equals(path) || "portail".equals(path)) {
            return gateReady(server, player, shot, path);
        }
        if (path.startsWith("faune_")) {
            // les animaux de la ville (cahier §85), poses devant la camera
            return com.emerald.haven.fauna.HavenFaunaPhotos.ready(server, player, path, PREPARED.add(shot.name()));
        }
        if (path.startsWith("quete_")) {
            // les quetes des heros (cahier §86) : heros, bateau, orbe, cibles, reperes, boutique
            return com.emerald.haven.quest.HavenQuestPhotos.ready(server, player, path, PREPARED.add(shot.name()));
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
        if (shot.hands()) {
            return placeHands(level, player, shot);
        }
        if (shot.gate()) {
            return placeGate(level, player, shot);
        }
        if (shot.eclipse()) {
            return placeEclipse(level, player, shot);
        }
        if (shot.sanctuary()) {
            return placeSanctuary(level, player, shot);
        }
        if (shot.arena()) {
            return placeArena(level, player, shot);
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
