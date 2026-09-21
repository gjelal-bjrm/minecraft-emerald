package com.emerald.haven;

import com.emerald.game.GameManager;
import com.emerald.game.GameState;
import com.emerald.game.WorldSetup;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'arrivee des joueurs dans la ville : chacun dans son appartement.
 *
 * TANT QUE LE LOBBY EST OUVERT (phase ACCUEIL), un joueur qui se connecte --
 * qu'il vienne du village ou qu'il ait ete sauvegarde dans la ville -- recoit
 * un appartement, s'y tient, et y reapparait apres une mort. Rien ne touche a
 * son inventaire : le kit de depart vide les poches, il n'est donne qu'au
 * depart vers le village.
 *
 * LA REGLE DE REPARTITION est celle du joueur : on remplit un appartement
 * jusqu'a sa capacite, puis le suivant ; au-dela de la capacite totale, le
 * moins rempli. Une place n'est JAMAIS liberee tant que le lobby est ouvert :
 * un joueur qui se deconnecte et revient retrouve la sienne, et personne n'est
 * jamais deplace pour faire de la place.
 *
 * LE PLACEMENT SE FAIT A LA TIQUE SUIVANTE, pas dans l'evenement de connexion.
 * Plusieurs abonnes a PlayerLoggedInEvent, sans ordre garanti entre eux
 * (HavenRules, HeroEvents, Specialization, les quetes), synchronisent le
 * joueur la ou il est ; une teleportation vers une autre dimension au milieu
 * d'eux les ferait travailler sur un joueur en train de changer de monde.
 * Differer d'une tique les laisse tous finir.
 *
 * LA POSE ETALEE : sur un serveur dedie, la pose du monde neuf peut finir sur
 * les tiques, apres l'arrivee des premiers joueurs. Ils attendent alors la ou
 * le monde les a fait apparaitre -- sur la terre ferme, pres de la Lame -- et
 * partent dans la ville quand elle est finie. Personne n'est pose dans le vide.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenArrival {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Le fichier des salles : c'est HavenRooms qui le lit. */
    public static final ResourceLocation ROOMS = HavenRooms.FILE;

    /** Intervalle du rappel d'attente pendant une pose, en tiques. */
    private static final int WAIT_REMINDER = 40;

    /** Joueurs a placer, avec « force » : ramener dans sa salle meme s'il est deja dans la ville. */
    private static final Map<UUID, Boolean> PENDING = new LinkedHashMap<>();

    @Nullable
    private static Layout layout;
    /** La lecture de HavenRooms dont {@link #layout} est tiree ; une autre lecture la refait. */
    @Nullable
    private static HavenRooms.Data layoutFrom;
    private static int ticks;

    private HavenArrival() {
    }

    // ================================================================ salles

    /**
     * Un appartement, en cellules du volume (haven_rooms.json).
     *
     * @param number numero montre au joueur, a partir de 1
     */
    public record Room(int number, String id, int capacity, List<BlockPos> spawns,
                       BlockPos boxMin, BlockPos boxMax, BlockPos doorMin, BlockPos doorMax) {

        /**
         * Le lacet qui regarde la porte depuis une cellule : on arrive tourne vers la sortie.
         *
         * Minecraft : 0 regarde +Z, 90 regarde -X ; la direction du regard est
         * donc (-sin, cos), d'ou atan2(-dx, dz).
         */
        public float yawToDoor(BlockPos cell) {
            double dx = (this.doorMin.getX() + this.doorMax.getX()) / 2.0 - cell.getX();
            double dz = (this.doorMin.getZ() + this.doorMax.getZ()) / 2.0 - cell.getZ();
            return (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
    }

    /** Les salles et le QG, tels que les decrit haven_rooms.json. */
    public record Layout(List<Room> rooms, BlockPos hqMin, BlockPos hqMax, BlockPos voteFloor) {

        /** Vrai si le point du monde est dans la boite du Hip Hog, bornes de cellules incluses. */
        public boolean inHq(BlockPos origin, double x, double y, double z) {
            return inside(origin.offset(this.hqMin), origin.offset(this.hqMax), x, y, z);
        }

        /** La place de l'urne : sur la cellule de sol proposee, donc un cran au-dessus. */
        public BlockPos votePos(BlockPos origin) {
            return origin.offset(this.voteFloor).above();
        }

        public int[] capacities() {
            int[] out = new int[this.rooms.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = this.rooms.get(i).capacity();
            }
            return out;
        }

        /** Le centre du Hip Hog au sol, en coordonnees du monde. */
        public BlockPos hqCenter(BlockPos origin) {
            return origin.offset((this.hqMin.getX() + this.hqMax.getX()) / 2, this.hqMin.getY(),
                    (this.hqMin.getZ() + this.hqMax.getZ()) / 2);
        }
    }

    /**
     * Vrai si le point est dans la boite [min, max] de blocs, bornes incluses.
     *
     * Un bloc (x) couvre [x, x + 1[ : la borne haute se compare donc a max + 1.
     */
    public static boolean inside(BlockPos min, BlockPos max, double x, double y, double z) {
        return x >= min.getX() && x < max.getX() + 1
                && y >= min.getY() && y < max.getY() + 1
                && z >= min.getZ() && z < max.getZ() + 1;
    }

    /**
     * Les salles, telles que l'arrivee et le vote les lisent ; null si le fichier manque ou se lit mal.
     *
     * UN SEUL LECTEUR DU FICHIER : HavenRooms. On en tire ici la forme dont
     * l'arrivee a besoin, et on la refait quand HavenRooms relit le fichier
     * (un /reload) -- sinon le releve des salles suivrait les nouvelles boites
     * pendant que l'arrivee, le vote et l'urne garderaient les anciennes.
     */
    @Nullable
    public static Layout layout(MinecraftServer server) {
        HavenRooms.Data data = HavenRooms.get(server);
        if (data != layoutFrom) {
            layoutFrom = data;
            layout = data == null ? null : fromRooms(data);
        }
        return layout;
    }

    /** La forme de l'arrivee : chaque salle doit avoir sa porte, et le QG la place de son urne. */
    @Nullable
    private static Layout fromRooms(HavenRooms.Data data) {
        if (data.hq() == null || data.hq().voteFloor() == null) {
            LOGGER.error("ville de Haven : {} sans QG ni place d'urne, les joueurs n'auront pas d'appartement",
                    HavenRooms.FILE);
            return null;
        }
        List<Room> rooms = new ArrayList<>();
        for (HavenRooms.Room room : data.rooms()) {
            if (room.door() == null) {
                LOGGER.error("ville de Haven : {} n'a pas de porte dans {}, les joueurs n'auront pas d'appartement",
                        room.id(), HavenRooms.FILE);
                return null;
            }
            rooms.add(new Room(room.number(), room.id(), room.capacity(), room.spawns(),
                    room.box().min(), room.box().max(), room.door().min(), room.door().max()));
        }
        return new Layout(List.copyOf(rooms), data.hq().box().min(), data.hq().box().max(), data.hq().voteFloor());
    }

    /**
     * La regle de repartition, sans serveur.
     *
     * La premiere salle qui n'est pas pleine ; si toutes le sont, la moins
     * remplie (la premiere en cas d'egalite).
     *
     * @param counts     occupants deja inscrits, par salle
     * @param capacities capacite de chaque salle
     */
    public static int chooseRoom(int[] counts, int[] capacities) {
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] < capacities[i]) {
                return i;
            }
        }
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] < counts[best]) {
                best = i;
            }
        }
        return best;
    }

    // ================================================================ phases

    /**
     * Le lobby est-il ouvert ?
     *
     * ACCUEIL, ou une pose de la ville en cours pour l'ouvrir (CHANTIER). Une
     * phase CHANTIER sans pose en cours est une pose qui a echoue : le lobby
     * n'est pas ouvert, et la partie se joue comme avant, au village.
     *
     * ET LA PARTIE EN ATTENTE (LOBBY). La phase de la ville ne suffit pas : un
     * monde du premier jalon passait en ACCUEIL sans empecher de tirer la
     * Lame, et un operateur peut encore trancher le regime a la commande. Une
     * partie commencee n'a plus de lobby, quoi que dise la phase -- sinon chaque
     * connexion aspirerait un joueur dans un appartement en pleine partie, et
     * un vote renverrait tout le serveur au village, kit et inventaire vide.
     */
    public static boolean lobbyOpen(MinecraftServer server) {
        HavenState.Phase phase = HavenState.get(server).phase();
        return (phase == HavenState.Phase.ACCUEIL || (phase == HavenState.Phase.CHANTIER && HavenSite.busy()))
                && gameWaiting(server);
    }

    /** La partie n'a pas commence : la Lame est encore plantee. */
    public static boolean gameWaiting(MinecraftServer server) {
        return GameState.get(server.overworld()).status() == GameState.Status.LOBBY;
    }

    /**
     * Ferme le lobby d'une partie qui a commence par un autre chemin que le vote.
     *
     * La Lame tiree, « /arcencium start » ou « open » : la phase de la ville
     * passe a PARTI, les votes sont oublies, et les joueurs restes dans la
     * ville partent au village a la tique suivante, par le meme chemin que le
     * retardataire. L'operateur en chantier y reste. Appele a chaque tique, a
     * chaque connexion et a la fin d'une pose : c'est aussi ce qui remet
     * d'aplomb, au premier demarrage, un monde du premier jalon ou la partie
     * tournait deja en phase ACCUEIL.
     *
     * @return vrai si le lobby vient d'etre ferme
     */
    public static boolean closeIfStarted(MinecraftServer server) {
        HavenState state = HavenState.get(server);
        HavenState.Phase phase = state.phase();
        if ((phase != HavenState.Phase.ACCUEIL && phase != HavenState.Phase.CHANTIER) || gameWaiting(server)) {
            return false;
        }
        state.setPhase(HavenState.Phase.PARTI);
        state.clearVotes();
        HavenVote.reset();
        int moved = 0;
        ServerLevel haven = Haven.level(server);
        if (haven != null) {
            for (ServerPlayer player : List.copyOf(haven.players())) {
                if (!player.isFakePlayer() && !HavenRules.chantier(player)) {
                    PENDING.put(player.getUUID(), false);
                    moved++;
                }
            }
        }
        LOGGER.warn("ville de Haven : la partie a commence ({}) en phase {} : lobby ferme, {} joueurs vont au village",
                GameState.get(server.overworld()).status(), phase, moved);
        return true;
    }

    /**
     * A la connexion, appele par WorldSetup.onJoin avant la mise au village.
     *
     * @return vrai si la ville prend le joueur en charge : le village ne doit alors rien lui faire
     */
    public static boolean claimLogin(ServerPlayer player) {
        MinecraftServer server = player.server;
        closeIfStarted(server);
        if (com.emerald.haven.journey.HavenReturn.holding(player)) {
            // passe par la porte de victoire, il attend l'equipe dans son appartement
            return true;
        }
        boolean claim = lobbyOpen(server)
                || (HavenState.get(server).phase() == HavenState.Phase.PARTI && Haven.is(player.level()));
        if (claim) {
            PENDING.putIfAbsent(player.getUUID(), false);
        }
        return claim;
    }

    /** La ville peut-elle recevoir un lobby ? Posee, ou en train de l'etre, avec ses salles. */
    public static boolean canOpen(MinecraftServer server) {
        return Haven.level(server) != null && layout(server) != null
                && (HavenState.get(server).built() || HavenSite.busy());
    }

    /**
     * Rouvre le lobby, APRES GameManager.setup (qui a replante la Lame).
     *
     * Le regime n'est plus choisi, les votes et les appartements sont oublies,
     * et tous les joueurs en ligne sont ramenes dans une salle a la tique
     * suivante -- dans l'ordre ou ils sont connectes. Sauf l'operateur en
     * chantier : il ne vote pas, il ne prend donc pas la place d'un joueur dans
     * un appartement, et on ne l'arrache pas a ce qu'il construit.
     *
     * Le mode de la ville est applique AVANT le placement (HavenInvasion.reopened) :
     * l'arrivee de chacun le lit pour choisir son titre.
     *
     * @return le nombre de joueurs ramenes
     */
    public static int reopen(MinecraftServer server) {
        return reopen(server, false);
    }

    /**
     * La meme, en gardant les appartements : le retour du Defi (HavenReturn), ou « chacun
     * retrouve son appartement » -- celui ou la porte de victoire l'a deja mene.
     */
    public static int reopen(MinecraftServer server, boolean keepApartments) {
        HavenState state = HavenState.get(server);
        GameState.get(server.overworld()).forgetModeChoice();
        state.clearVotes();
        if (!keepApartments) {
            state.clearApartments();
        }
        state.setPhase(HavenSite.busy() ? HavenState.Phase.CHANTIER : HavenState.Phase.ACCUEIL);
        HavenVote.reset();
        com.emerald.haven.invasion.HavenInvasion.reopened(server);
        int moved = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!HavenRules.chantier(player)) {
                PENDING.put(player.getUUID(), true);
                moved++;
            }
        }
        LOGGER.info("ville de Haven : lobby rouvert, phase {}, {} joueurs a placer", state.phase(), moved);
        return moved;
    }

    // ================================================================ tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        closeIfStarted(server);
        if (PENDING.isEmpty()) {
            return;
        }
        ticks++;
        HavenState state = HavenState.get(server);
        Iterator<Map.Entry<UUID, Boolean>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Boolean> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            switch (state.phase()) {
                case CHANTIER, ACCUEIL -> {
                    if (HavenSite.busy()) {
                        // la ville se pose : on attend sur la terre ferme, pas dans une boite vide
                        if (ticks % WAIT_REMINDER == 0) {
                            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.waiting")
                                    .withStyle(ChatFormatting.AQUA), true);
                        }
                        continue;
                    }
                    it.remove();
                    if (!state.built()) {
                        LOGGER.error("ville de Haven non posee en phase {} : {} va au village",
                                state.phase(), player.getGameProfile().getName());
                        WorldSetup.placeAtVillage(player);
                        continue;
                    }
                    arrive(player, entry.getValue());
                }
                case PARTI -> {
                    it.remove();
                    if (Haven.is(player.level()) && !com.emerald.haven.journey.HavenReturn.holding(player)) {
                        // la partie a commence sans lui : le village, et le kit si la Lame est encore la
                        GameState.Status status = GameState.get(server.overworld()).status();
                        toVillage(player, status == GameState.Status.LOBBY || status == GameState.Status.PROLOGUE);
                    }
                }
                case ABSENTE -> it.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        layout = null;
        layoutFrom = null;
        ticks = 0;
    }

    // ================================================================ placements

    /**
     * Place un joueur dans son appartement, et l'y fait reapparaitre.
     *
     * Un joueur deja inscrit et deja dans la ville (il se reconnecte) garde sa
     * position : le renvoyer a cinq cents metres du bar ou il attendait ses
     * amis serait une punition. Seuls sa reapparition et le rappel du QG sont
     * refaits. « force » le ramene quand meme : c'est la reouverture du lobby.
     *
     * L'operateur en chantier n'est ni inscrit ni deplace : il ne vote pas, et
     * une place d'appartement prise par lui decalerait la repartition « par
     * trois » des vrais joueurs.
     */
    public static void arrive(ServerPlayer player, boolean force) {
        MinecraftServer server = player.server;
        if (HavenRules.chantier(player)) {
            if (HavenAtelier.on(server) && !Haven.is(player.level())) {
                // l'atelier : on arrive devant le bar, pour retoucher la ville, pas dans un appartement
                HavenAtelier.toBarFront(player);
                LOGGER.info("ville de Haven : {} arrive dans l'atelier, devant le bar", player.getGameProfile().getName());
                return;
            }
            LOGGER.info("ville de Haven : {} est en chantier, ni appartement ni teleportation",
                    player.getGameProfile().getName());
            return;
        }
        Placement place = place(server, player.getUUID(), player.getGameProfile().getName());
        ServerLevel haven = Haven.level(server);
        Layout rooms = layout(server);
        if (place == null || haven == null || rooms == null) {
            LOGGER.error("ville de Haven : ni dimension ni salles, {} reste ou il est",
                    player.getGameProfile().getName());
            return;
        }
        BlockPos feet = place.feet();
        if (place.fresh() || force || !Haven.is(player.level())) {
            player.teleportTo(haven, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, place.yaw(), 0.0F);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }
        setRespawn(player, place);

        BlockPos hq = rooms.hqCenter(HavenState.get(server).origin());
        int dx = hq.getX() - feet.getX();
        int dz = hq.getZ() - feet.getZ();
        int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        Component direction = Component.translatable(direction(dx, dz));
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.arrival",
                place.room().number(), distance, direction).withStyle(ChatFormatting.AQUA));
        // la ligne au-dessus de la barre d'objets est remplacee par la barre d'objectif (HavenJourney)
        // l'etat de la ville : envahie (on arrive en invasion) ou paisible, et le bouton qui la bascule
        boolean invasion = com.emerald.haven.invasion.HavenInvasion.mode(server)
                == com.emerald.haven.invasion.HavenInvasion.Mode.INVASION;
        player.sendSystemMessage(Component.translatable(invasion
                        ? "game.emeraldweapons.haven.invasion.arrival.on"
                        : "game.emeraldweapons.haven.invasion.arrival.off")
                .withStyle(invasion ? ChatFormatting.RED : ChatFormatting.GREEN));
        com.emerald.haven.invasion.HavenInvasion.warnPeacefulDifficulty(player);
        // le parcours : le titre de la premiere arrivee, puis le guide vers le QG
        com.emerald.haven.journey.HavenJourney.onArrive(player);
    }

    /**
     * Ou un joueur se tient dans la ville.
     *
     * @param feet  les pieds, en coordonnees du monde
     * @param fresh vrai si l'inscription vient d'etre faite
     */
    public record Placement(Room room, int slot, BlockPos feet, float yaw, boolean fresh) {
    }

    /**
     * L'appartement d'un joueur -- inscrit s'il n'en avait pas -- et le point ou il s'y tient.
     *
     * Ne deplace personne : c'est la moitie de l'arrivee qui se verifie sans
     * joueur connecte.
     *
     * @return null si la dimension ou les salles manquent
     */
    @Nullable
    public static Placement place(MinecraftServer server, UUID id, String name) {
        ServerLevel haven = Haven.level(server);
        Layout rooms = layout(server);
        if (haven == null || rooms == null || rooms.rooms().isEmpty()) {
            return null;
        }
        HavenState state = HavenState.get(server);
        int[] apartment = state.apartment(id);
        boolean fresh = apartment == null;
        if (fresh) {
            int[] counts = state.roomCounts(rooms.rooms().size());
            int room = chooseRoom(counts, rooms.capacities());
            apartment = new int[]{room, counts[room]};
            state.assignApartment(id, room, counts[room]);
            LOGGER.info("ville de Haven : {} recoit l'appartement {} (place {})", name, room + 1, counts[room] + 1);
        }
        Room room = rooms.rooms().get(Math.floorMod(apartment[0], rooms.rooms().size()));
        BlockPos origin = state.origin();
        BlockPos feet = standIn(haven, origin, room, apartment[1]);
        return new Placement(room, apartment[1], feet, room.yawToDoor(feet.subtract(origin)), fresh);
    }

    /**
     * La reapparition dans l'appartement, FORCEE : une cellule d'air suffit,
     * sans lit ni ancre (ServerPlayer.findRespawnAndUseSpawnBlock, branche forcee).
     */
    public static void setRespawn(ServerPlayer player, Placement place) {
        player.setRespawnPosition(Haven.LEVEL, place.feet(), place.yaw(), true, false);
    }

    /** Le point cardinal dominant : +X est, -X ouest, +Z sud, -Z nord (cle de langue). */
    public static String direction(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? "game.emeraldweapons.haven.dir.east" : "game.emeraldweapons.haven.dir.west";
        }
        return dz >= 0 ? "game.emeraldweapons.haven.dir.south" : "game.emeraldweapons.haven.dir.north";
    }

    /**
     * Ou se tenir dans une salle : les pieds, en coordonnees du monde.
     *
     * D'abord le point d'apparition de sa place, puis les autres, puis le
     * premier sol libre de la boite -- chaque fois en LISANT le monde : un sol
     * sous les pieds, et deux cellules d'air. Une salle amenagee a pu poser un
     * meuble sur un point d'apparition.
     */
    public static BlockPos standIn(ServerLevel level, BlockPos origin, Room room, int slot) {
        List<BlockPos> spawns = room.spawns();
        for (int i = 0; i < spawns.size(); i++) {
            BlockPos feet = origin.offset(spawns.get(Math.floorMod(slot + i, spawns.size()))).above();
            if (standable(level, feet)) {
                return feet;
            }
        }
        BlockPos min = room.boxMin();
        BlockPos max = room.boxMax();
        for (int y = min.getY(); y <= max.getY() - 1; y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos feet = origin.offset(x, y, z);
                    if (standable(level, feet)) {
                        return feet;
                    }
                }
            }
        }
        BlockPos fallback = spawns.isEmpty() ? origin.offset(Haven.BAR_FRONT_CELL)
                : origin.offset(spawns.get(Math.floorMod(slot, spawns.size()))).above();
        LOGGER.warn("ville de Haven : aucun sol libre dans {}, pieds en {} sans garantie",
                room.id(), fallback.toShortString());
        return fallback;
    }

    /** Un sol qui porte, et deux cellules d'air au-dessus. */
    public static boolean standable(ServerLevel level, BlockPos feet) {
        BlockPos below = feet.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty()
                && level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
    }

    /**
     * Au village : teleportation, reapparition dans l'overworld, mode de jeu rendu, et le kit si demande.
     *
     * Le changement de dimension rend deja le mode (HavenRules) ; on le rend
     * encore ici pour un joueur qui ne changeait pas de dimension, et c'est sans
     * effet s'il a deja ete rendu.
     */
    public static void toVillage(ServerPlayer player, boolean kit) {
        MinecraftServer server = player.server;
        com.emerald.haven.journey.HavenJourney.onDeparture(player);
        ServerLevel overworld = server.overworld();
        BlockPos village = GameState.get(overworld).village();
        BlockPos stand = village.equals(BlockPos.ZERO)
                ? WorldSetup.findOpenGround(overworld, overworld.getSharedSpawnPos(), 16)
                : WorldSetup.findOpenGround(overworld, village.offset(4, 0, 4), 12);
        player.teleportTo(overworld, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                player.getYRot(), 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.setRespawnPosition(Level.OVERWORLD, stand, 0.0F, true, false);
        GameType original = HavenState.get(server).forgetMode(player.getUUID());
        if (original != null && player.gameMode.getGameModeForPlayer() != original) {
            player.setGameMode(original);
        }
        if (kit) {
            GameManager.equipStarter(player);
        }
    }
}
