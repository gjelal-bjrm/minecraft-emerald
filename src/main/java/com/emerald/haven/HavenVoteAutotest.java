package com.emerald.haven;

import com.emerald.block.HavenVoteBlock;
import com.emerald.block.ModBlocks;
import com.emerald.game.GameState;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Le banc d'essai de l'arrivee et du vote, INERTE sans EMERALDWEAPONS_AUTOTEST=vote.
 *
 * Il fait tourner la logique du vote avec des electeurs SIMULES (unanimite,
 * changement d'avis, sortie du bar, deconnexion pendant le compte a rebours,
 * operateur en chantier, un joueur, quatre joueurs), la regle de repartition
 * des appartements de 1 a 11 joueurs, puis lit le MONDE : l'urne a sa place,
 * les points d'apparition libres. Un FakePlayer de NeoForge passe par
 * l'inscription et la reapparition ; le depart puis la reouverture sont joues
 * sur l'etat du monde. Rapport dans vote_autotest.txt, dans le dossier du
 * serveur (run-server/ pour runServer), puis arret du serveur.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenVoteAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "vote".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    /** On laisse le serveur s'installer (et une pose reprise finir) avant de lire le monde. */
    private static final int SETTLE_TICKS = 40;
    private static final int TIMEOUT_TICKS = 20 * 60 * 20;

    private static final StringBuilder OUT = new StringBuilder();
    private static boolean done;
    private static int waited;
    private static int passed;
    private static int failed;

    private HavenVoteAutotest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || done) {
            return;
        }
        MinecraftServer server = event.getServer();
        waited++;
        if (waited < SETTLE_TICKS || (HavenSite.busy() && waited < TIMEOUT_TICKS)) {
            return;
        }
        done = true;
        line("autotest de l'arrivee et du vote, " + LocalDateTime.now().withNano(0));
        try {
            logic();
            apartments(server);
            world(server);
            glass(server);
            fakePlayer(server);
            cycle(server);
        } catch (RuntimeException e) {
            check("banc sans exception", false, e.toString());
            LOGGER.error("autotest vote : exception", e);
        }
        end(server);
    }

    // ================================================================ vote simule

    private static HavenVote.Elector e(String name, boolean inBar, GameState.Mode vote) {
        return new HavenVote.Elector(id(name), name, inBar, vote);
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("autotest:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static final GameState.Mode D = GameState.Mode.DEFI;
    private static final GameState.Mode L = GameState.Mode.LIBRE;

    /** Fait avancer n tiques avec la meme liste ; rend les pas vus, dans l'ordre, sans repetition. */
    private static List<HavenVote.Step> run(HavenVote.Countdown countdown, List<HavenVote.Elector> electors, int n) {
        List<HavenVote.Step> seen = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            HavenVote.Step step = countdown.step(electors, true);
            if (seen.isEmpty() || seen.get(seen.size() - 1) != step) {
                seen.add(step);
            }
        }
        return seen;
    }

    /** Le nombre de tiques jusqu'au depart, ou -1 s'il n'a pas lieu en max tiques. */
    private static int ticksToDepart(HavenVote.Countdown countdown, List<HavenVote.Elector> electors, int max) {
        for (int i = 1; i <= max; i++) {
            if (countdown.step(electors, true) == HavenVote.Step.DEPART) {
                return i;
            }
        }
        return -1;
    }

    private static void logic() {
        line("--- logique du vote (electeurs simules, " + HavenVote.COUNTDOWN_TICKS + " tiques de compte a rebours)");

        // unanimite
        HavenVote.Countdown c = new HavenVote.Countdown();
        List<HavenVote.Elector> ab = List.of(e("A", true, D), e("B", true, D));
        HavenVote.Step first = c.step(ab, true);
        int toDepart = ticksToDepart(c, ab, 500);
        check("unanimite a 2 dans le bar : depart 100 tiques apres le debut",
                first == HavenVote.Step.STARTED && toDepart == HavenVote.COUNTDOWN_TICKS && c.mode() == D,
                "premier pas " + first + ", depart apres " + toDepart + " tiques, mode " + c.mode());

        c = new HavenVote.Countdown();
        List<HavenVote.Step> split = run(c, List.of(e("A", true, D), e("B", true, L)), 300);
        check("votes differents : rien ne part en 300 tiques", split.equals(List.of(HavenVote.Step.NONE)),
                "pas vus " + split);

        // changement d'avis
        c = new HavenVote.Countdown();
        c.step(ab, true);
        run(c, ab, 30);
        HavenVote.Step change = c.step(List.of(e("A", true, D), e("B", true, L)), true);
        HavenVote.Step back = c.step(ab, true);
        int remainingAfterBack = c.remaining();
        check("changement d'avis pendant le compte a rebours : annule, puis repart de 5 s",
                change == HavenVote.Step.CANCELLED && back == HavenVote.Step.STARTED
                        && remainingAfterBack == HavenVote.COUNTDOWN_TICKS,
                "changement " + change + ", retour " + back + ", reste " + remainingAfterBack);

        // sortie du bar
        c = new HavenVote.Countdown();
        c.step(ab, true);
        run(c, ab, 20);
        List<HavenVote.Elector> out = List.of(e("A", true, D), e("B", false, D));
        HavenVote.Step leaveBar = c.step(out, true);
        List<HavenVote.Step> whileOut = run(c, out, 200);
        HavenVote.Step backIn = c.step(ab, true);
        check("sortie du bar : annule, rien ne part tant qu'il est dehors, repart a son retour",
                leaveBar == HavenVote.Step.CANCELLED && whileOut.equals(List.of(HavenVote.Step.NONE))
                        && backIn == HavenVote.Step.STARTED,
                "sortie " + leaveBar + ", dehors " + whileOut + ", retour " + backIn);

        // deconnexion d'un partisan pendant le compte a rebours
        c = new HavenVote.Countdown();
        List<HavenVote.Elector> abc = List.of(e("A", true, D), e("B", true, D), e("C", true, D));
        c.step(abc, true);
        run(c, abc, 30);
        List<HavenVote.Elector> withoutC = HavenVote.electors(abc, Set.of(), id("C"));
        HavenVote.Step logout = c.step(withoutC, false);
        int afterLogout = c.remaining();
        int toDepart2 = ticksToDepart(c, withoutC, 500);
        check("deconnexion pendant le compte a rebours : le partant exclu, le compte repart de 5 s sans lui",
                withoutC.size() == 2 && logout == HavenVote.Step.RESTARTED
                        && afterLogout == HavenVote.COUNTDOWN_TICKS && toDepart2 == HavenVote.COUNTDOWN_TICKS,
                "electeurs " + withoutC.size() + ", pas " + logout + ", reste " + afterLogout
                        + ", depart apres " + toDepart2);

        // deconnexion du seul opposant
        c = new HavenVote.Countdown();
        List<HavenVote.Elector> opposed = List.of(e("A", true, D), e("B", true, D), e("C", true, L));
        List<HavenVote.Step> blocked = run(c, opposed, 50);
        HavenVote.Step opponentLeaves = c.step(HavenVote.electors(opposed, Set.of(), id("C")), false);
        check("deconnexion du seul opposant : le compte a rebours part, sans depart dans l'evenement",
                blocked.equals(List.of(HavenVote.Step.NONE)) && opponentLeaves == HavenVote.Step.STARTED
                        && c.remaining() == HavenVote.COUNTDOWN_TICKS,
                "avant " + blocked + ", a la deconnexion " + opponentLeaves + ", reste " + c.remaining());

        // un recalcul (vote, deconnexion) ne mange pas de temps
        c = new HavenVote.Countdown();
        c.step(ab, true);
        for (int i = 0; i < 50; i++) {
            c.step(ab, false);
        }
        check("un recalcul sans tique ne fait pas avancer le compte a rebours",
                c.remaining() == HavenVote.COUNTDOWN_TICKS, "reste " + c.remaining() + " apres 50 recalculs");

        // operateur en chantier
        List<HavenVote.Elector> withOp = List.of(e("A", true, D), e("Op", false, null));
        HavenVote.Countdown withoutExclusion = new HavenVote.Countdown();
        HavenVote.Step notExcluded = withoutExclusion.step(HavenVote.electors(withOp, Set.of(), null), true);
        c = new HavenVote.Countdown();
        List<HavenVote.Elector> excluded = HavenVote.electors(withOp, Set.of(id("Op")), null);
        HavenVote.Step opStep = c.step(excluded, true);
        check("operateur en chantier : hors electeurs, il ne bloque pas le depart",
                excluded.size() == 1 && notExcluded == HavenVote.Step.NONE && opStep == HavenVote.Step.STARTED,
                "electeurs " + excluded.size() + ", sans exclusion " + notExcluded + ", avec " + opStep);

        // un joueur
        c = new HavenVote.Countdown();
        List<HavenVote.Step> alone = run(c, List.of(e("A", true, null)), 100);
        List<HavenVote.Elector> aloneLibre = List.of(e("A", true, L));
        HavenVote.Step aloneStart = c.step(aloneLibre, true);
        int aloneDepart = ticksToDepart(c, aloneLibre, 500);
        check("un seul joueur : rien sans vote ; son vote Libre dans le bar part en 5 s",
                alone.equals(List.of(HavenVote.Step.NONE)) && aloneStart == HavenVote.Step.STARTED
                        && aloneDepart == HavenVote.COUNTDOWN_TICKS && c.mode() == L,
                "sans vote " + alone + ", puis " + aloneStart + ", depart apres " + aloneDepart + ", mode " + c.mode());

        // quatre joueurs
        c = new HavenVote.Countdown();
        List<HavenVote.Step> fourA = run(c, List.of(e("A", true, D), e("B", true, D), e("C", true, D),
                e("D", true, null)), 60);
        List<HavenVote.Step> fourB = run(c, List.of(e("A", true, D), e("B", true, D), e("C", true, D),
                e("D", false, D)), 60);
        List<HavenVote.Elector> four = List.of(e("A", true, D), e("B", true, D), e("C", true, D), e("D", true, D));
        HavenVote.Step fourStart = c.step(four, true);
        run(c, four, 50);
        HavenVote.Step fourChange = c.step(List.of(e("A", true, D), e("B", true, D), e("C", true, D),
                e("D", true, L)), true);
        HavenVote.Step fourAgain = c.step(four, true);
        int fourDepart = ticksToDepart(c, four, 500);
        check("quatre joueurs : un sans vote, puis un hors du bar, bloquent ; un changement annule ; puis depart",
                fourA.equals(List.of(HavenVote.Step.NONE)) && fourB.equals(List.of(HavenVote.Step.NONE))
                        && fourStart == HavenVote.Step.STARTED && fourChange == HavenVote.Step.CANCELLED
                        && fourAgain == HavenVote.Step.STARTED && fourDepart == HavenVote.COUNTDOWN_TICKS,
                "sans vote " + fourA + ", hors du bar " + fourB + ", entree " + fourStart + ", changement "
                        + fourChange + ", retour " + fourAgain + ", depart apres " + fourDepart);

        // arrivee d'un joueur deja d'accord pendant le compte a rebours
        c = new HavenVote.Countdown();
        c.step(ab, true);
        run(c, ab, 40);
        HavenVote.Step arrival = c.step(abc, true);
        check("arrivee d'un joueur pendant le compte a rebours : annule et repart de 5 s",
                arrival == HavenVote.Step.RESTARTED && c.remaining() == HavenVote.COUNTDOWN_TICKS,
                "pas " + arrival + ", reste " + c.remaining());

        // ville vide
        c = new HavenVote.Countdown();
        List<HavenVote.Step> empty = run(c, List.of(), 200);
        check("ville vide : pas de depart pour personne", empty.equals(List.of(HavenVote.Step.NONE)),
                "pas vus " + empty);
    }

    // ================================================================ appartements

    private static void apartments(MinecraftServer server) {
        line("--- repartition des appartements");
        HavenArrival.Layout layout = HavenArrival.layout(server);
        if (layout == null) {
            check("haven_rooms.json lu", false, "absent ou illisible");
            return;
        }
        int[] capacities = layout.capacities();
        check("haven_rooms.json : 3 appartements de 3 places, 3 points d'apparition chacun",
                layout.rooms().size() == 3 && Arrays.equals(capacities, new int[]{3, 3, 3})
                        && layout.rooms().stream().allMatch(r -> r.spawns().size() == 3),
                layout.rooms().size() + " salles, capacites " + Arrays.toString(capacities));

        int[] counts = new int[capacities.length];
        StringBuilder sequence = new StringBuilder();
        List<Integer> rooms = new ArrayList<>();
        for (int player = 1; player <= 11; player++) {
            int room = HavenArrival.chooseRoom(counts, capacities);
            counts[room]++;
            rooms.add(room + 1);
            sequence.append(String.format(Locale.ROOT, "%d joueur%s -> %s ; ", player, player > 1 ? "s" : "",
                    Arrays.toString(counts)));
        }
        line("repartition : " + sequence);
        check("remplir jusqu'a 3 puis la suivante, au-dela de 9 la moins remplie",
                rooms.equals(List.of(1, 1, 1, 2, 2, 2, 3, 3, 3, 1, 2)),
                "salles des joueurs 1 a 11 : " + rooms);
    }

    // ================================================================ monde

    private static void world(MinecraftServer server) {
        line("--- monde");
        ServerLevel level = Haven.level(server);
        HavenArrival.Layout layout = HavenArrival.layout(server);
        HavenState state = HavenState.get(server);
        line("phase " + state.phase() + ", ville posee " + state.built() + ", origine "
                + state.origin().toShortString() + ", appartements inscrits " + state.apartmentCount());
        if (level == null || layout == null) {
            check("dimension et salles presentes", false, "niveau " + level + ", salles " + layout);
            return;
        }
        BlockPos origin = state.origin();

        // les points d'apparition, lus dans le monde
        int good = 0;
        int total = 0;
        StringBuilder bad = new StringBuilder();
        for (HavenArrival.Room room : layout.rooms()) {
            for (BlockPos spawn : room.spawns()) {
                total++;
                BlockPos feet = origin.offset(spawn).above();
                if (HavenArrival.standable(level, feet)) {
                    good++;
                } else {
                    bad.append(room.id()).append(' ').append(feet.toShortString()).append(" : sol ")
                            .append(name(level.getBlockState(feet.below()))).append(", ")
                            .append(name(level.getBlockState(feet))).append(" / ")
                            .append(name(level.getBlockState(feet.above()))).append(" ; ");
                }
            }
        }
        check("points d'apparition : un sol et deux cellules d'air dans le monde",
                state.built() && good == total && total > 0, good + " sur " + total + (bad.isEmpty() ? "" : " ; " + bad));

        // la place du milieu est en face de la porte (z 187 a 189) : le regard doit etre plein ouest
        HavenArrival.Room first = layout.rooms().get(0);
        float facing = first.yawToDoor(first.spawns().get(1));
        float corner = first.yawToDoor(first.spawns().get(0));
        check("regard vers la porte depuis l'appartement 1 : plein ouest (90) en face d'elle, en biais (0 a 90) depuis un cote",
                Math.abs(facing - 90.0F) < 0.01F && corner > 0.0F && corner < 90.0F,
                "en face " + facing + ", depuis " + first.spawns().get(0).toShortString() + " " + corner);

        BlockPos hq = layout.hqCenter(origin);
        check("boite du Hip Hog en coordonnees du monde : le centre dedans, un bloc au-dela du bord dehors",
                layout.inHq(origin, hq.getX() + 0.5, hq.getY() + 1.0, hq.getZ() + 0.5)
                        && !layout.inHq(origin, origin.getX() + layout.hqMax().getX() + 1.0, hq.getY() + 1.0,
                        hq.getZ() + 0.5),
                "centre " + hq.toShortString());

        // la borne : deux moities sur hq.vote.floor + 1
        Block vote = ModBlocks.HAVEN_VOTE.get();
        BlockPos expected = origin.offset(layout.voteFloor()).above();
        BlockPos top = expected.above();
        level.getChunk(expected);                 // charge le troncon : la pose est paresseuse
        check("les deux cases au-dessus du sol de la borne sont libres (air, ou deja la borne)",
                HavenVoteBlock.fits(level, expected),
                name(level.getBlockState(expected)) + " en " + expected.toShortString() + ", "
                        + name(level.getBlockState(top)) + " en " + top.toShortString());
        BlockPos pos = HavenVote.keepVoteBlock(server);
        BlockState lower = level.getBlockState(expected);
        BlockState upper = level.getBlockState(top);
        boolean wanted = HavenVote.voteBlockWanted(state);
        if (wanted) {
            check("borne posee apres la pose de la ville : moitie basse a hq.vote.floor + 1, moitie haute dessus, vitres vers "
                            + HavenVote.VOTE_FACING.getName(),
                    expected.equals(pos) && lower == HavenVote.voteState(DoubleBlockHalf.LOWER)
                            && upper == HavenVote.voteState(DoubleBlockHalf.UPPER),
                    "attendue en " + expected.toShortString() + " (cellule " + layout.voteFloor().above().toShortString()
                            + "), trouvees " + lower + " / " + upper);
        } else {
            check("borne absente hors de la phase ACCUEIL", !lower.is(vote) && !upper.is(vote),
                    "phase " + state.phase() + ", " + name(lower) + " / " + name(upper) + " en " + expected.toShortString());
        }
        BlockState floor = level.getBlockState(expected.below());
        check("la borne repose sur un sol", !floor.getCollisionShape(level, expected.below()).isEmpty(),
                name(floor) + " en " + expected.below().toShortString());

        // incassables, sans butin, non poussables : les DEUX moities, avec un joueur de survie
        FakePlayer breaker = FakePlayerFactory.getMinecraft(level);
        StringBuilder halves = new StringBuilder();
        boolean solid = vote.getExplosionResistance() >= 3600000.0F && vote.getLootTable() == BuiltInLootTables.EMPTY;
        for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
            BlockState s = HavenVote.voteState(half);
            BlockPos at = half == DoubleBlockHalf.LOWER ? expected : top;
            float speed = s.getDestroySpeed(level, at);
            float progress = s.getDestroyProgress(breaker, level, at);
            solid &= speed < 0 && progress == 0.0F && s.getPistonPushReaction() == PushReaction.BLOCK;
            halves.append(half).append(" : durete ").append(speed).append(", progres ").append(progress)
                    .append(", piston ").append(s.getPistonPushReaction()).append(" ; ");
        }
        check("borne incassable, sans butin, non poussable, pour ses deux moities", solid,
                halves + "resistance " + vote.getExplosionResistance() + ", butin " + vote.getLootTable().location());

        int lowerLight = HavenVote.voteState(DoubleBlockHalf.LOWER).getLightEmission(level, expected);
        int upperLight = HavenVote.voteState(DoubleBlockHalf.UPPER).getLightEmission(level, top);
        check("lumiere douce : " + HavenVoteBlock.LIGHT + " par la tete, rien par le pied",
                upperLight == HavenVoteBlock.LIGHT && lowerLight == 0, "haute " + upperLight + ", basse " + lowerLight);
        check("une moitie seule disparait quand l'autre manque (updateShape)",
                HavenVote.voteState(DoubleBlockHalf.UPPER).updateShape(Direction.DOWN,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), level, top, expected).isAir()
                        && HavenVote.voteState(DoubleBlockHalf.LOWER).updateShape(Direction.UP,
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), level, expected, top).isAir()
                        && HavenVote.voteState(DoubleBlockHalf.LOWER).updateShape(Direction.UP,
                        HavenVote.voteState(DoubleBlockHalf.UPPER), level, expected, top)
                        == HavenVote.voteState(DoubleBlockHalf.LOWER),
                "haute sans basse, basse sans haute : air ; basse sous sa haute : gardee");

        shapes(level, expected);
    }

    // ================================================================ formes

    private static boolean inside(VoxelShape shape, Vec3 local) {
        for (AABB box : shape.toAabbs()) {
            if (box.contains(local)) {
                return true;
            }
        }
        return false;
    }

    private static double volume(VoxelShape shape) {
        double sum = 0.0;
        for (AABB box : shape.toAabbs()) {
            sum += box.getXsize() * box.getYsize() * box.getZsize();
        }
        return sum;
    }

    /**
     * Selection et collision, dans les quatre directions.
     *
     * Les formes suivent la pente de la tete : les deux vitres (un dixieme
     * d'unite derriere leur surface) sont DEDANS ; le vide devant le haut de
     * l'ecran et derriere le bas de la tete est DEHORS ; les marches ne
     * remplissent qu'une partie de leur boite englobante.
     */
    private static void shapes(ServerLevel level, BlockPos at) {
        line("--- formes de la borne (selection et collision)");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState lowerState = HavenVote.voteState(DoubleBlockHalf.LOWER).setValue(HavenVoteBlock.FACING, facing);
            BlockState upperState = HavenVote.voteState(DoubleBlockHalf.UPPER).setValue(HavenVoteBlock.FACING, facing);
            VoxelShape lower = lowerState.getShape(level, at);
            VoxelShape upper = upperState.getShape(level, at.above());
            VoxelShape lowerCollision = lowerState.getCollisionShape(level, at);
            VoxelShape upperCollision = upperState.getCollisionShape(level, at.above());
            Vec3 down = new Vec3(0, -1, 0);
            Vec3 libre = HavenVoteBlock.toWorld(facing, BlockPos.ZERO, HavenVoteBlock.glassPoint(5.0, 24.0, -0.1)).add(down);
            Vec3 defi = HavenVoteBlock.toWorld(facing, BlockPos.ZERO, HavenVoteBlock.glassPoint(11.0, 24.0, -0.1)).add(down);
            Vec3 aboveScreen = HavenVoteBlock.toWorld(facing, BlockPos.ZERO, new Vec3(8.0, 30.5, 11.0)).add(down);
            Vec3 behindChin = HavenVoteBlock.toWorld(facing, BlockPos.ZERO, new Vec3(8.0, 17.5, 1.0)).add(down);
            Vec3 pedestal = HavenVoteBlock.toWorld(facing, BlockPos.ZERO, new Vec3(8.0, 8.0, 8.0));
            Vec3 besidePedestal = HavenVoteBlock.toWorld(facing, BlockPos.ZERO, new Vec3(1.0, 8.0, 1.0));
            AABB bounds = upper.bounds();
            double fill = volume(upper) / (bounds.getXsize() * bounds.getYsize() * bounds.getZsize());
            boolean ok = inside(upper, libre) && inside(upper, defi) && !inside(upper, aboveScreen)
                    && !inside(upper, behindChin) && inside(lower, pedestal) && !inside(lower, besidePedestal)
                    && fill > 0.3 && fill < 0.8
                    && lower.toAabbs().equals(lowerCollision.toAabbs()) && upper.toAabbs().equals(upperCollision.toAabbs())
                    && bounds.minY >= 0.0 && bounds.maxY <= 1.0 && lower.bounds().maxY <= 1.0;
            check("forme " + facing.getName() + " : vitres dedans, vide de la pente dehors, pied plein, collision = selection",
                    ok, "vitres " + inside(upper, libre) + "/" + inside(upper, defi) + ", au-dessus de l'ecran "
                            + inside(upper, aboveScreen) + ", sous le menton " + inside(upper, behindChin) + ", pied "
                            + inside(lower, pedestal) + "/" + inside(lower, besidePedestal) + ", remplissage "
                            + String.format(Locale.ROOT, "%.2f", fill) + ", boites " + upper.toAabbs().size());
            if (facing == Direction.SOUTH) {
                line("forme sud, moitie basse (unites de modele) : " + boxes(lower));
                line("forme sud, moitie haute (unites de modele) : " + boxes(upper));
            }
        }
    }

    private static String boxes(VoxelShape shape) {
        StringBuilder out = new StringBuilder();
        for (AABB b : shape.toAabbs()) {
            out.append(String.format(Locale.ROOT, "[%.1f %.1f %.1f %.1f %.1f %.1f] ",
                    b.minX * 16, b.minY * 16, b.minZ * 16, b.maxX * 16, b.maxY * 16, b.maxZ * 16));
        }
        return out.toString().trim();
    }

    // ================================================================ vitres

    /**
     * Le vote par les vitres.
     *
     * D'abord la geometrie seule, dans les quatre directions : un joueur debout
     * devant la borne vise le centre d'une vitre, son bord, l'espace entre les
     * deux, le cadre, le pied ; puis de dos et de cote. Ensuite le vrai chemin
     * sur la borne du monde : un FakePlayer place devant elle, et
     * BlockState.useWithoutItem avec le point touche, comme le serveur le recoit
     * d'un client. Le clic hors vitre ouvre l'ecran (le FakePlayer n'ouvre pas de
     * menu : on verifie seulement que le vote ne change pas).
     */
    private static void glass(MinecraftServer server) {
        line("--- vote par les vitres");
        BlockPos base = new BlockPos(1000, 64, -2000);
        Vec3 standing = new Vec3(0, 26.0, 36.0);         // oeil a 1,62 bloc, 1,6 bloc devant la borne
        record Aim(String what, double eyeX, double eyeY, double eyeZ, Vec3 target, boolean upperHalf,
                   GameState.Mode expected) {
        }
        List<Aim> aims = List.of(
                new Aim("centre de la vitre bleue", 5.0, standing.y, standing.z, HavenVoteBlock.glassPoint(5.0, 24.0, 0), true, L),
                new Aim("centre de la vitre rouge", 11.0, standing.y, standing.z, HavenVoteBlock.glassPoint(11.0, 24.0, 0), true, D),
                new Aim("vitre bleue, de face au ras", 5.0, 0, 0, HavenVoteBlock.glassPoint(5.0, 24.0, 0), true, L),
                new Aim("coin haut de la vitre bleue", 5.0, standing.y, standing.z, HavenVoteBlock.glassPoint(2.6, 27.9, 0), true, L),
                new Aim("coin bas de la vitre rouge", 11.0, standing.y, standing.z, HavenVoteBlock.glassPoint(13.4, 20.1, 0), true, D),
                new Aim("vitre rouge vue de biais, depuis la gauche", -24.0, standing.y, 28.0, HavenVoteBlock.glassPoint(11.0, 24.0, 0), true, D),
                new Aim("entre les deux vitres", 8.0, standing.y, standing.z, HavenVoteBlock.glassPoint(8.0, 24.0, 0), true, null),
                new Aim("cadre a gauche de la vitre bleue", 5.0, standing.y, standing.z, HavenVoteBlock.glassPoint(1.4, 24.0, 0), true, null),
                new Aim("cadre sous les vitres", 8.0, standing.y, standing.z, HavenVoteBlock.glassPoint(5.0, 19.0, 0), true, null),
                new Aim("pied (moitie basse)", 8.0, standing.y, standing.z, new Vec3(8.0, 10.0, 12.0), false, null),
                new Aim("de dos, a travers la tete", 5.0, standing.y, -20.0, HavenVoteBlock.glassPoint(5.0, 24.0, 0), true, null));
        int good = 0;
        StringBuilder bad = new StringBuilder();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Aim aim : aims) {
                BlockState s = HavenVote.voteState(aim.upperHalf() ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                        .setValue(HavenVoteBlock.FACING, facing);
                BlockPos clicked = aim.upperHalf() ? base.above() : base;
                // « de face au ras » : l'oeil sur la normale de la vitre, 20 unites devant
                Vec3 eyeModel = aim.eyeZ() == 0 ? HavenVoteBlock.glassPoint(aim.eyeX(), 24.0, 20.0)
                        : new Vec3(aim.eyeX(), aim.eyeY(), aim.eyeZ());
                Vec3 eye = HavenVoteBlock.toWorld(facing, base, eyeModel);
                Vec3 target = HavenVoteBlock.toWorld(facing, base, aim.target());
                GameState.Mode got = HavenVoteBlock.glassAt(s, clicked, eye, target);
                if (got == aim.expected()) {
                    good++;
                } else {
                    bad.append(facing.getName()).append(' ').append(aim.what()).append(" -> ").append(got).append(" ; ");
                }
            }
        }
        check("geometrie du clic, 4 directions x " + aims.size() + " visees : bleue = Monde ouvert, rouge = Defi, ailleurs rien",
                good == aims.size() * 4, good + " sur " + aims.size() * 4 + (bad.isEmpty() ? "" : " ; " + bad));

        // le vrai chemin, sur la borne du monde
        ServerLevel level = Haven.level(server);
        HavenArrival.Layout layout = HavenArrival.layout(server);
        HavenState state = HavenState.get(server);
        if (level == null || layout == null || !HavenArrival.lobbyOpen(server)) {
            check("vote par les vitres dans le monde : lobby ouvert", false,
                    "niveau " + level + ", salles " + layout + ", lobby " + HavenArrival.lobbyOpen(server));
            return;
        }
        BlockPos lowerPos = layout.votePos(state.origin());
        BlockState upper = level.getBlockState(lowerPos.above());
        BlockState lower = level.getBlockState(lowerPos);
        if (!upper.is(ModBlocks.HAVEN_VOTE.get()) || !lower.is(ModBlocks.HAVEN_VOTE.get())) {
            check("vote par les vitres dans le monde : borne posee", false, name(lower) + " / " + name(upper));
            return;
        }
        Direction facing = upper.getValue(HavenVoteBlock.FACING);
        FakePlayer fake = FakePlayerFactory.get(level, new GameProfile(id("vitres"), "[VoteVitres]"));
        UUID uuid = fake.getUUID();
        try {
            StringBuilder seen = new StringBuilder();
            GameState.Mode afterBlue = click(level, fake, lowerPos, facing, upper, true, HavenVoteBlock.glassPoint(5.0, 24.0, 0), seen);
            GameState.Mode vote1 = state.vote(uuid);
            GameState.Mode afterRed = click(level, fake, lowerPos, facing, upper, true, HavenVoteBlock.glassPoint(11.0, 24.0, 0), seen);
            GameState.Mode vote2 = state.vote(uuid);
            click(level, fake, lowerPos, facing, upper, true, HavenVoteBlock.glassPoint(8.0, 24.0, 0), seen);
            GameState.Mode vote3 = state.vote(uuid);
            click(level, fake, lowerPos, facing, lower, false, new Vec3(8.0, 10.0, 12.0), seen);
            GameState.Mode vote4 = state.vote(uuid);
            check("dans le monde (useWithoutItem, FakePlayer devant la borne) : bleue vote Monde ouvert, rouge vote Defi,"
                            + " entre les vitres et sur le pied le vote ne change pas",
                    afterBlue == L && vote1 == L && afterRed == D && vote2 == D && vote3 == D && vote4 == D,
                    seen + "votes " + vote1 + ", " + vote2 + ", " + vote3 + ", " + vote4);
        } catch (RuntimeException e) {
            check("vote par les vitres : sans exception", false, e.toString());
            LOGGER.error("autotest vote : vitres", e);
        } finally {
            state.clearVotes();
            HavenVote.reset();
            line("votes du FakePlayer effaces ; votes restants : " + state.vote(uuid));
        }
    }

    /** Place l'oeil du FakePlayer debout devant la borne, et clique ou vise le modele. */
    @javax.annotation.Nullable
    private static GameState.Mode click(ServerLevel level, FakePlayer fake, BlockPos base, Direction facing,
                                        BlockState state, boolean upperHalf, Vec3 targetModel, StringBuilder seen) {
        Vec3 eye = HavenVoteBlock.toWorld(facing, base, new Vec3(targetModel.x, 26.0, 36.0));
        fake.moveTo(eye.x, eye.y - fake.getEyeHeight(), eye.z, 0.0F, 0.0F);
        Vec3 target = HavenVoteBlock.toWorld(facing, base, targetModel);
        BlockPos clicked = upperHalf ? base.above() : base;
        GameState.Mode glass = HavenVoteBlock.glassAt(state, clicked, fake.getEyePosition(), target);
        InteractionResult result = state.useWithoutItem(level, fake, new BlockHitResult(target, facing, clicked, false));
        seen.append(String.format(Locale.ROOT, "oeil (%.2f %.2f %.2f) -> vitre %s, %s ; ", eye.x, eye.y, eye.z, glass, result));
        return result == InteractionResult.CONSUME ? glass : null;
    }

    // ================================================================ FakePlayer

    /**
     * L'appartement et la reapparition, sur un FakePlayer de NeoForge.
     *
     * CE QU'IL NE PEUT PAS FAIRE : changer de dimension. Mesure au premier
     * essai : ServerPlayer.changeDimension appelle PlayerList.sendLevelInfo, qui
     * demande a NeoForge si la connexion porte un canal (NetworkRegistry.hasChannel) ;
     * la connexion factice n'a pas de canal netty, d'ou une NullPointerException
     * AU MILIEU du changement, le joueur deja ajoute au nouveau niveau. Et une
     * teleportation dans la meme dimension ne le deplace pas (teleport vide de sa
     * connexion, FakePlayer.java:223). La teleportation et le retour au village
     * ne se verifient donc qu'avec de vrais clients.
     *
     * CE QU'IL PEUT : l'inscription, la meme place a la reconnexion, le point de
     * reapparition force, et ce que le jeu en ferait a la mort
     * (findRespawnPositionAndUseSpawnBlock, sans teleportation). On nettoie derriere lui.
     */
    private static void fakePlayer(MinecraftServer server) {
        line("--- FakePlayer : inscription et reapparition, sans changer de dimension");
        ServerLevel haven = Haven.level(server);
        HavenState state = HavenState.get(server);
        if (haven == null || !state.built()) {
            check("FakePlayer : ville posee", false, "niveau " + haven + ", posee " + state.built());
            return;
        }
        GameProfile profile = new GameProfile(id("fake"), "[VoteAutotest]");
        FakePlayer fake = FakePlayerFactory.get(haven, profile);
        UUID uuid = fake.getUUID();
        int before = state.apartmentCount();
        try {
            HavenArrival.Placement place = HavenArrival.place(server, uuid, profile.getName());
            int[] apartment = state.apartment(uuid);
            check("FakePlayer : appartement inscrit, pieds sur un sol libre",
                    place != null && place.fresh() && apartment != null && state.apartmentCount() == before + 1
                            && HavenArrival.standable(haven, place.feet()),
                    place == null ? "aucune place" : "salle " + place.room().number() + ", place " + (place.slot() + 1)
                            + ", pieds " + place.feet().toShortString() + ", lacet " + place.yaw());
            if (place == null) {
                return;
            }
            HavenArrival.Placement again = HavenArrival.place(server, uuid, profile.getName());
            check("FakePlayer : la meme place a la reconnexion, sans nouvelle inscription",
                    again != null && !again.fresh() && again.feet().equals(place.feet())
                            && state.apartmentCount() == before + 1,
                    again == null ? "aucune" : "pieds " + again.feet().toShortString() + ", nouvelle " + again.fresh()
                            + ", inscriptions " + state.apartmentCount());

            HavenArrival.setRespawn(fake, place);
            check("FakePlayer : reapparition forcee dans la ville, a sa place",
                    Haven.LEVEL.equals(fake.getRespawnDimension()) && place.feet().equals(fake.getRespawnPosition())
                            && fake.isRespawnForced(),
                    fake.getRespawnDimension().location() + " " + fake.getRespawnPosition() + " forcee "
                            + fake.isRespawnForced());
            DimensionTransition respawn = fake.findRespawnPositionAndUseSpawnBlock(false,
                    DimensionTransition.DO_NOTHING);
            BlockPos feet = place.feet();
            Vec3 wanted = new Vec3(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
            check("FakePlayer : a la mort, le jeu le ferait reapparaitre dans son appartement",
                    respawn.newLevel() == haven && !respawn.missingRespawnBlock()
                            && respawn.pos().distanceTo(wanted) < 1.0e-6,
                    respawn.newLevel().dimension().location() + " " + respawn.pos() + ", bloc manquant "
                            + respawn.missingRespawnBlock());
        } catch (RuntimeException e) {
            check("FakePlayer : sans exception", false, e.toString());
            LOGGER.error("autotest vote : FakePlayer", e);
        } finally {
            state.removeApartment(uuid);
            state.forgetMode(uuid);
            line("FakePlayer nettoye ; inscriptions restantes " + state.apartmentCount());
        }
    }

    // ================================================================ depart et reouverture

    /**
     * Le depart puis la reouverture, sur l'etat du monde, sans joueur connecte.
     *
     * Personne a teleporter : on verifie ce qui ne depend pas d'un client --
     * la phase, le regime applique, les votes effaces, l'urne retiree puis
     * reposee, le regime a revoter. L'etat d'avant est rendu a la fin.
     */
    private static void cycle(MinecraftServer server) {
        line("--- depart puis reouverture, sans joueur");
        HavenState state = HavenState.get(server);
        GameState game = GameState.get(server.overworld());
        ServerLevel level = Haven.level(server);
        HavenArrival.Layout layout = HavenArrival.layout(server);
        if (state.phase() != HavenState.Phase.ACCUEIL || level == null || layout == null || HavenSite.busy()) {
            check("depart et reouverture : lobby ouvert au depart de l'essai", false,
                    "phase " + state.phase() + ", niveau " + level + ", salles " + layout + ", pose " + HavenSite.busy());
            return;
        }
        GameState.Mode modeBefore = game.mode();
        boolean chosenBefore = game.modeChosen();
        Block vote = ModBlocks.HAVEN_VOTE.get();
        BlockPos urn = layout.votePos(state.origin());
        BlockPos top = urn.above();
        level.getChunk(urn);
        UUID voter = id("voter");
        state.setVote(voter, modeBefore);
        try {
            HavenVote.depart(server, modeBefore);
            HavenVote.followVoteBlock(server);           // ce que la tique suivante ferait
            boolean statusLobby = game.status() == GameState.Status.LOBBY;
            // la place de la borne peut accueillir l'arche du depart (HavenDeparture, §84) : on
            // verifie que la BORNE est partie, pas que la case est vide
            check("depart : phase PARTI, votes effaces, les DEUX moities de la borne retirees"
                            + (statusLobby ? ", regime applique" : ""),
                    state.phase() == HavenState.Phase.PARTI && state.vote(voter) == null
                            && !level.getBlockState(urn).is(vote) && !level.getBlockState(top).is(vote)
                            && (!statusLobby || (game.modeChosen() && game.mode() == modeBefore)),
                    "phase " + state.phase() + ", vote " + state.vote(voter) + ", borne "
                            + name(level.getBlockState(urn)) + " / " + name(level.getBlockState(top)) + ", statut "
                            + game.status() + ", regime " + game.mode() + " choisi " + game.modeChosen());
            check("apres le depart, le lobby est ferme et la Lame ne renvoie plus au vote",
                    !HavenArrival.lobbyOpen(server), "lobbyOpen " + HavenArrival.lobbyOpen(server));

            int moved = HavenArrival.reopen(server);
            boolean absentBefore = !level.getBlockState(urn).is(vote) && !level.getBlockState(top).is(vote);
            HavenVote.followVoteBlock(server);           // la tique qui voit la reouverture
            BlockPos placed = HavenVote.keepVoteBlock(server);
            check("reouverture : phase ACCUEIL, regime a revoter, inscriptions oubliees, borne reposee des deux moities"
                            + " par le suivi de phase",
                    state.phase() == HavenState.Phase.ACCUEIL && !game.modeChosen() && state.apartmentCount() == 0
                            && absentBefore && urn.equals(placed)
                            && level.getBlockState(urn) == HavenVote.voteState(DoubleBlockHalf.LOWER)
                            && level.getBlockState(top) == HavenVote.voteState(DoubleBlockHalf.UPPER)
                            && HavenArrival.lobbyOpen(server) && moved == 0,
                    "phase " + state.phase() + ", choisi " + game.modeChosen() + ", inscriptions "
                            + state.apartmentCount() + ", absente avant le suivi " + absentBefore + ", borne "
                            + level.getBlockState(urn) + " / " + level.getBlockState(top) + ", joueurs " + moved);
        } finally {
            state.setPhase(HavenState.Phase.ACCUEIL);
            state.clearVotes();
            if (chosenBefore) {
                game.chooseMode(modeBefore);
            } else {
                game.forgetModeChoice();
            }
            HavenVote.keepVoteBlock(server);
            line("etat rendu : phase " + state.phase() + ", regime " + game.mode() + " choisi " + game.modeChosen());
        }
    }

    // ================================================================ rapport

    private static void end(MinecraftServer server) {
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("vote_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest vote : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest vote : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest vote : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }

    private static String name(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }
}
