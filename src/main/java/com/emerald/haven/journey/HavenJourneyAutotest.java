package com.emerald.haven.journey;

import com.emerald.block.HavenGunRackBlock;
import com.emerald.block.ModBlocks;
import com.emerald.block.entity.HavenGateBlockEntity;
import com.emerald.game.Finale;
import com.emerald.game.GameState;
import com.emerald.game.WorldSetup;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenAutotest;
import com.emerald.haven.HavenGates;
import com.emerald.haven.HavenRules;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.haven.JakOverlay;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionState;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.gun.MorphGunData;
import com.emerald.jak.gun.MorphGunKeeper;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Le banc d'essai du parcours de Haven, lots 1 et 2 (cahier §79 et §81), INERTE sans
 * EMERALDWEAPONS_AUTOTEST=parcours.
 *
 * Sur la ville du serveur d'essai (posee si elle ne l'est pas), avec des joueurs
 * factices a fiche de parcours temporaire :
 *   1. la fiche sur disque : aller-retour d'un fichier, ni fiche temporaire ni fiche vide ecrite ;
 *   2. l'arme suit les formes du joueur : aucune -> pas d'arme ; le Scatter Gun seul ;
 *      les douze ; une forme tenue perdue -> retour au Scatter Gun ; plus rien -> retiree ;
 *   3. la ville : paisible a neuf, paisible a la reouverture du lobby, et un monde d'avant
 *      le parcours (envahi, sans marque) repasse en paisible a la lecture ;
 *   4. le bouton du QG : le refus dit ce qui manque (12 armes sur 12) ;
 *   5. le guide : titre de premiere arrivee une seule fois, objectif QG avec distance et
 *      barre qui se remplit, puis la borne dans le Hip Hog, puis l'attente apres un vote ;
 *      rien en chantier ; plus rien apres le depart ;
 *   7. le ratelier du QG : pose sur son socle, ferme avant le premier depart, le Scatter
 *      Gun une seule fois ;
 *   8. la deuxieme arrivee : ville envahie a la reouverture, titre une fois, objectifs
 *      « ton arme » puis « reprendre les rues », 25 monstres en equipe, ville paisible ;
 *   9. le retour du Defi : defaite (apres le titre), porte de victoire (attente de
 *      l'equipe, dernier entre, cinq minutes), rien en Monde ouvert ;
 *  10. les transports : les huit stations posees, l'arche (passage, recharge, retour) et le
 *      portail des tours (regard tenu, desarme a l'arrivee, sommet, descente) ;
 *  11. l'agenda (§83) : donne a la premiere arrivee, une seule fois, Alex's Mobs prevenu, son
 *      annonce differee apres le titre ; ses pages ; l'equipe attendue au QG, puis reunie :
 *      la borne pour tous et la question du mode ;
 *  12. l'arche du depart (§84) : ouverte dans le bar, rouge pour le Defi, refermee.
 * Rapport dans parcours_autotest.txt, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenJourneyAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "parcours".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    private static final int START_DELAY = 40;
    private static final int TIMEOUT = 20 * 60 * 10;

    private enum Stage { WAIT, REBUILD, RUN, END }

    private static Stage stage = Stage.WAIT;
    private static final StringBuilder OUT = new StringBuilder();
    private static final List<UUID> SUBJECTS = new ArrayList<>();
    private static int passed;
    private static int failed;
    private static int waited;

    private HavenJourneyAutotest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || stage == Stage.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            tick(server);
        } catch (RuntimeException e) {
            LOGGER.error("autotest parcours : exception", e);
            check("deroulement sans exception", false, e.toString());
            end(server);
        }
    }

    private static void tick(MinecraftServer server) {
        HavenState state = HavenState.get(server);
        switch (stage) {
            case WAIT -> {
                if (++waited < START_DELAY) {
                    return;
                }
                line("autotest du parcours de Haven (lots 1 et 2), " + LocalDateTime.now().withNano(0));
                if (Haven.level(server) == null) {
                    check("dimension de la ville", false, "absente");
                    end(server);
                    return;
                }
                if (!state.built() && !HavenSite.busy()) {
                    net.minecraft.network.chat.Component failure = HavenSite.start(server, true, HavenSite.Mode.ETALE, null);
                    if (failure != null) {
                        check("pose de la ville lancee", false, failure.getString());
                        end(server);
                        return;
                    }
                    line("la ville n'etait pas posee : pose lancee");
                }
                waited = 0;
                stage = Stage.REBUILD;
            }
            case REBUILD -> {
                if (++waited > TIMEOUT) {
                    check("ville posee avant le delai", false, "delai depasse");
                    end(server);
                    return;
                }
                if (HavenSite.busy() || !state.built() || JakOverlay.pending() > 0) {
                    return;
                }
                stage = Stage.RUN;
            }
            case RUN -> {
                run(server);
                end(server);
            }
            case END -> {
            }
        }
    }

    private static void run(MinecraftServer server) {
        ServerLevel level = Haven.level(server);
        HavenState state = HavenState.get(server);
        boolean open = HavenArrival.lobbyOpen(server) && HavenInvasion.cityOpen(server);
        line("ville posee ; lobby " + (HavenArrival.lobbyOpen(server) ? "ouvert" : "ferme") + ", phase "
                + state.phase() + ", partie " + GameState.get(server.overworld()).status());
        store(server);
        sync();
        city(server);
        if (!open) {
            check("lobby ouvert pour les essais de l'arme et du guide", false,
                    "phase " + state.phase() + ", partie " + GameState.get(server.overworld()).status());
            return;
        }
        gun(server, level);
        button();
        guide(server, level);
        agenda(server, level);
        rack(server, level);
        secondArrival(server, level);
        returnDefeat(server);
        returnVictory(server);
        gates(server, level);
        departure(server, level);
        isolation(server);
    }

    // ================================================================ 1. la fiche sur disque

    private static void store(MinecraftServer server) {
        line("--- la fiche de parcours sur disque");
        Path path = server.getServerDirectory().resolve("parcours_autotest_fiche.json");
        Map<UUID, HavenProgress.Entry> out = new HashMap<>();
        UUID a = uuid("fiche-a");
        UUID b = uuid("fiche-b");
        UUID blank = uuid("fiche-vide");
        UUID temp = uuid("fiche-temporaire");
        HavenProgress.Entry ea = new HavenProgress.Entry();
        ea.forms = GunForm.RED_1.bit() | GunForm.BLUE_3.bit();
        ea.welcomed = true;
        ea.hq = true;
        ea.departures = 3;
        ea.invaded = true;
        ea.reprise = true;
        ea.quests.add("sig_chasse");
        HavenProgress.Entry eb = new HavenProgress.Entry();
        eb.welcomed = true;
        HavenProgress.Entry et = new HavenProgress.Entry();
        et.forms = GunForm.ALL_MASK;
        et.temporary = true;
        out.put(a, ea);
        out.put(b, eb);
        out.put(blank, new HavenProgress.Entry());
        out.put(temp, et);
        HavenProgress.write(path, out);
        Map<UUID, HavenProgress.Entry> in = new HashMap<>();
        int read = HavenProgress.read(path, in);
        HavenProgress.Entry ra = in.get(a);
        HavenProgress.Entry rb = in.get(b);
        check("aller-retour : formes, arrivee, QG, departs, deuxieme arrivee, rues reprises et quetes relus a l'identique",
                read == 2 && ra != null && ra.forms == ea.forms && ra.welcomed && ra.hq && ra.departures == 3
                        && ra.invaded && ra.reprise
                        && ra.quests.contains("sig_chasse") && rb != null && rb.welcomed && rb.forms == 0
                        && !rb.invaded && !rb.reprise,
                read + " fiche(s) lue(s)" + (ra == null ? "" : ", A : formes " + Integer.toBinaryString(ra.forms)
                        + ", departs " + ra.departures + ", quetes " + ra.quests));
        check("ni la fiche temporaire (cobaye) ni la fiche vide ne sont ecrites",
                !in.containsKey(temp) && !in.containsKey(blank), "cles lues " + in.keySet().size());
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // un fichier d'essai qui reste n'est rien
        }
    }

    // ================================================================ 2. les formes de l'arme

    private static void sync() {
        line("--- les formes de l'arme suivent le parcours (MorphGunData.syncOwned)");
        MorphGunData blue = MorphGunData.fresh(7L, GunForm.ALL_MASK).withForm(GunForm.BLUE_2, 100L);
        MorphGunData lost = blue.syncOwned(GunForm.RED_1.bit() | GunForm.YELLOW_1.bit());
        MorphGunData onlyYellow = blue.syncOwned(GunForm.YELLOW_1.bit());
        MorphGunData kept = blue.syncOwned(GunForm.BLUE_2.bit() | GunForm.RED_1.bit());
        check("forme tenue perdue : retour au Scatter Gun, sans transformation a jouer ; sans Scatter Gun, la premiere possedee",
                lost.form() == GunForm.RED_1 && lost.previous() == GunForm.RED_1 && lost.changeTick() == MorphGunData.NEVER
                        && lost.owned() == (GunForm.RED_1.bit() | GunForm.YELLOW_1.bit())
                        && onlyYellow.form() == GunForm.YELLOW_1,
                lost.form().id + " / " + onlyYellow.form().id);
        check("forme tenue gardee : rien d'autre ne change que les formes possedees",
                kept.form() == GunForm.BLUE_2 && kept.changeTick() == 100L && kept.lobby() == 7L,
                kept.form().id + ", tique " + kept.changeTick());
    }

    private static void gun(MinecraftServer server, ServerLevel level) {
        line("--- l'arme selon la fiche du joueur (gardien du Morph Gun)");
        FakePlayer fake = subject(level, "arme", HavenState.get(server).origin().offset(Haven.BAR_FRONT_CELL));
        MorphGunKeeper.addSubject(fake);
        HavenProgress.temporary(fake.getUUID(), 0);
        MorphGunKeeper.guard(fake);
        check("aucune forme debloquee (premiere arrivee) : aucune arme", MorphGunKeeper.count(fake) == 0,
                "armes " + MorphGunKeeper.count(fake));

        HavenProgress.temporary(fake.getUUID(), GunForm.RED_1.bit());
        MorphGunKeeper.guard(fake);
        ItemStack gun = MorphGunKeeper.find(fake);
        MorphGunData data = gun == null ? null : MorphGunData.of(gun);
        check("le Scatter Gun seul : une arme, une forme, le Scatter Gun en main, reserves pleines",
                MorphGunKeeper.count(fake) == 1 && data != null && data.owned() == GunForm.RED_1.bit()
                        && data.form() == GunForm.RED_1 && data.full(),
                describe(gun));

        HavenProgress.temporary(fake.getUUID(), GunForm.ALL_MASK);
        MorphGunKeeper.guard(fake);
        ItemStack same = MorphGunKeeper.find(fake);
        MorphGunData all = same == null ? null : MorphGunData.of(same);
        check("les douze debloquees : la MEME arme recoit les douze formes au passage du gardien",
                same == gun && all != null && all.owned() == GunForm.ALL_MASK, describe(same));

        if (same != null && all != null) {
            MorphGunData.write(same, all.withForm(GunForm.DARK_3, level.getGameTime()));
        }
        HavenProgress.temporary(fake.getUUID(), GunForm.RED_1.bit() | GunForm.YELLOW_1.bit());
        MorphGunKeeper.guard(fake);
        MorphGunData back = same == null ? null : MorphGunData.of(same);
        check("la Super Nova tenue, puis retiree de la fiche : l'arme revient au Scatter Gun",
                back != null && back.form() == GunForm.RED_1 && back.owned() == (GunForm.RED_1.bit() | GunForm.YELLOW_1.bit()),
                describe(same));

        HavenProgress.temporary(fake.getUUID(), 0);
        MorphGunKeeper.guard(fake);
        check("plus aucune forme (remise a zero) : l'arme est retiree", MorphGunKeeper.count(fake) == 0,
                "armes " + MorphGunKeeper.count(fake));
        MorphGunKeeper.removeSubject(fake.getUUID());
    }

    // ================================================================ 3. la ville paisible

    private static void city(MinecraftServer server) {
        line("--- la ville paisible");
        check("un etat de ville neuf est PAISIBLE", HavenInvasionState.freshMode() == HavenInvasion.Mode.PAISIBLE,
                HavenInvasionState.freshMode().toString());
        CompoundTag old = new CompoundTag();
        old.putString("Mode", "INVASION");
        old.putLong("Generation", 4L);
        HavenInvasionState.Reading before = HavenInvasionState.readForTest(old, server.registryAccess());
        CompoundTag marked = old.copy();
        marked.putInt("Parcours", 1);
        HavenInvasionState.Reading after = HavenInvasionState.readForTest(marked, server.registryAccess());
        check("un monde d'avant le parcours (envahi, sans marque) repasse en paisible a la lecture, et sera reecrit ;"
                        + " un monde marque garde son invasion",
                before.mode() == HavenInvasion.Mode.PAISIBLE && before.rewrite()
                        && after.mode() == HavenInvasion.Mode.INVASION && !after.rewrite(),
                "sans marque " + before + ", marque " + after);

        HavenState state = HavenState.get(server);
        HavenState.Phase phase = state.phase();
        HavenInvasion.Mode mode = HavenInvasion.mode(server);
        if (Haven.level(server) == null || (phase != HavenState.Phase.ACCUEIL && phase != HavenState.Phase.CHANTIER)) {
            check("reouverture du lobby : essai possible", false, "phase " + phase);
            return;
        }
        try {
            HavenInvasion.setMode(server, HavenInvasion.Mode.INVASION, null);
            state.setPhase(HavenState.Phase.PARTI);
            HavenInvasion.update(server);
            state.setPhase(phase);
            HavenInvasion.update(server);
            check("reouverture du lobby (PARTI puis ACCUEIL) : la ville revient PAISIBLE",
                    HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE, "mode " + HavenInvasion.mode(server));
        } finally {
            state.setPhase(phase);
            HavenInvasion.setMode(server, mode == HavenInvasion.Mode.INVASION ? HavenInvasion.Mode.PAISIBLE : mode, null);
        }
    }

    // ================================================================ 4. le bouton du QG

    private static void button() {
        line("--- le bouton du QG");
        UUID nobody = uuid("bouton-nouveau");
        HavenProgress.temporary(nobody, 0);
        Component refusal = HavenJourney.lockedButton(nobody);
        Object[] args = refusal.getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
        int quests = HavenProgress.REQUIRED_QUESTS.size();
        check("sans la maitrise : le refus dit ce qui manque (12 armes sur 12, " + quests + " quetes), et la maitrise est refusee",
                !HavenProgress.mastery(nobody) && args.length == 3 && Integer.valueOf(12).equals(args[0])
                        && Integer.valueOf(12).equals(args[1]) && Integer.valueOf(quests).equals(args[2]),
                Arrays.toString(args));
        HavenProgress.Entry entry = HavenProgress.temporary(nobody, GunForm.ALL_MASK);
        check("les douze armes sans les quetes des heros (lot 3) : pas encore la maitrise",
                !HavenProgress.mastery(nobody) && HavenProgress.missingQuests(nobody) == quests,
                "quetes manquantes " + HavenProgress.missingQuests(nobody));
        entry.quests.addAll(HavenProgress.REQUIRED_QUESTS);
        check("les douze armes et les " + quests + " quetes : la maitrise",
                HavenProgress.mastery(nobody), "armes manquantes " + HavenProgress.missingWeapons(nobody));
        HavenProgress.dropTemporary(nobody);
    }

    // ================================================================ 5. le guide

    private static void guide(MinecraftServer server, ServerLevel level) {
        line("--- le guide : premiere arrivee, QG, borne");
        HavenState state = HavenState.get(server);
        HavenArrival.Layout rooms = HavenArrival.layout(server);
        if (rooms == null || rooms.rooms().isEmpty()) {
            check("salles de la ville lues", false, "haven_rooms.json absent");
            return;
        }
        BlockPos origin = state.origin();
        HavenArrival.Room room = rooms.rooms().get(0);
        BlockPos far = HavenArrival.standIn(level, origin, room, 0);
        FakePlayer fake = subject(level, "guide", far);
        HavenJourney.addSubject(fake);
        HavenProgress.temporary(fake.getUUID(), 0);
        long now = level.getGameTime();

        HavenJourney.onArrive(fake);
        boolean first = HavenJourney.titlePending(fake.getUUID()) && HavenProgress.get(fake.getUUID()).welcomed;
        HavenJourney.update(fake, now);
        boolean notYet = HavenJourney.titlePending(fake.getUUID());
        HavenJourney.update(fake, now + HavenJourney.TITLE_DELAY);
        boolean played = !HavenJourney.titlePending(fake.getUUID());
        HavenJourney.onArrive(fake);
        check("premiere arrivee : titre prevu " + HavenJourney.TITLE_DELAY + " tiques plus tard, joue a l'heure, "
                        + "et une seule fois (la seconde arrivee n'en prevoit pas)",
                first && notYet && played && !HavenJourney.titlePending(fake.getUUID()),
                "prevu " + first + ", attend " + notYet + ", joue " + played);

        ServerBossEvent bar = HavenJourney.bar(fake.getUUID());
        Object[] args = bar != null && bar.getName().getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
        float farProgress = bar == null ? -1F : bar.getProgress();
        check("loin du QG (appartement 1) : objectif « rejoindre le QG », distance et direction, barre bleue en haut",
                HavenJourney.shown(fake.getUUID()) == HavenJourney.Objective.QG && bar != null
                        && bar.getPlayers().contains(fake) && bar.getColor() == BossEvent.BossBarColor.BLUE
                        && args.length == 2 && args[0] instanceof Integer d && d > 16,
                "objectif " + HavenJourney.shown(fake.getUUID()) + ", arguments " + Arrays.toString(args)
                        + ", barre " + farProgress);

        BlockPos front = origin.offset(Haven.BAR_FRONT_CELL);
        fake.moveTo(front.getX() + 0.5, front.getY(), front.getZ() + 0.5, 0.0F, 0.0F);
        HavenJourney.update(fake, now + HavenJourney.TITLE_DELAY + 1);
        float nearProgress = bar == null ? -1F : bar.getProgress();
        check("devant le bar : la barre s'est remplie en approchant",
                HavenJourney.shown(fake.getUUID()) == HavenJourney.Objective.QG && nearProgress > farProgress,
                farProgress + " -> " + nearProgress + ", objectif " + HavenJourney.shown(fake.getUUID()));

        BlockPos hq = rooms.hqCenter(origin);
        fake.moveTo(hq.getX() + 0.5, hq.getY(), hq.getZ() + 0.5, 0.0F, 0.0F);
        boolean inside = rooms.inHq(origin, fake.getX(), fake.getY(), fake.getZ());
        HavenJourney.update(fake, now + HavenJourney.TITLE_DELAY + 2);
        ServerBossEvent red = HavenJourney.bar(fake.getUUID());
        check("dans le Hip Hog, seul de l'equipe : objectif « la borne » (barre violette), QG note pour ce lobby et sur la fiche",
                inside && HavenJourney.shown(fake.getUUID()) == HavenJourney.Objective.BORNE
                        && HavenJourney.reachedHqThisLobby(fake.getUUID()) && HavenProgress.get(fake.getUUID()).hq
                        && red != null && red.getColor() == BossEvent.BossBarColor.PURPLE,
                "dans la boite " + inside + ", objectif " + HavenJourney.shown(fake.getUUID()));

        state.setVote(fake.getUUID(), GameState.Mode.DEFI);
        HavenJourney.update(fake, now + HavenJourney.TITLE_DELAY + 3);
        check("apres son vote : « la partie part quand tout le monde a vote »",
                HavenJourney.shown(fake.getUUID()) == HavenJourney.Objective.ATTENTE, "objectif " + HavenJourney.shown(fake.getUUID()));
        state.clearVotes();

        fake.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0.0F, 0.0F);
        HavenJourney.update(fake, now + HavenJourney.TITLE_DELAY + 4);
        check("revenu dans l'appartement : le QG reste rejoint pour ce lobby (la borne, pas le QG)",
                HavenJourney.shown(fake.getUUID()) == HavenJourney.Objective.BORNE, "objectif " + HavenJourney.shown(fake.getUUID()));

        HavenRules.setChantier(fake, true);
        try {
            HavenJourney.update(fake, now + HavenJourney.TITLE_DELAY + 5);
            check("en chantier : pas de guide", HavenJourney.shown(fake.getUUID()) == null && HavenJourney.bar(fake.getUUID()) == null,
                    "objectif " + HavenJourney.shown(fake.getUUID()));
        } finally {
            HavenRules.setChantier(fake, false);
            state.forgetMode(fake.getUUID());
        }

        HavenJourney.update(fake, now + HavenJourney.TITLE_DELAY + 6);
        int departures = HavenProgress.get(fake.getUUID()).departures;
        HavenJourney.onDeparture(fake);
        check("depart vers la partie : compte, et plus de barre",
                HavenProgress.get(fake.getUUID()).departures == departures + 1 && HavenJourney.bar(fake.getUUID()) == null,
                "departs " + departures + " -> " + HavenProgress.get(fake.getUUID()).departures);
        HavenJourney.removeSubject(fake.getUUID());
        HavenProgress.dropTemporary(fake.getUUID());
    }

    // ================================================================ 11. l'agenda et l'equipe (§83)

    private static void agenda(MinecraftServer server, ServerLevel level) {
        line("--- l'agenda, l'equipe au QG, le choix du mode (§83)");
        HavenState state = HavenState.get(server);
        HavenArrival.Layout rooms = HavenArrival.layout(server);
        if (rooms == null || rooms.rooms().isEmpty()) {
            check("salles de la ville lues", false, "haven_rooms.json absent");
            return;
        }
        BlockPos origin = state.origin();
        BlockPos far = HavenArrival.standIn(level, origin, rooms.rooms().get(0), 0);
        FakePlayer a = subject(level, "agenda-a", far);
        FakePlayer b = subject(level, "agenda-b", far);
        for (FakePlayer p : List.of(a, b)) {
            HavenJourney.addSubject(p);
            HavenProgress.temporary(p.getUUID(), 0);
        }
        try {
            long now = level.getGameTime();
            HavenJourney.onArrive(a);
            boolean given = HavenAgenda.has(a);
            boolean told = a.getPersistentData().getCompound("PlayerPersisted").getBoolean("alexsmobs_has_book");
            List<Component> later = HavenJourney.laterTexts(a.getUUID());
            boolean deferred = later.stream().anyMatch(c -> key(c).equals("game.emeraldweapons.haven.agenda.recu"));
            long due = HavenJourney.laterDue(a.getUUID());
            check("premiere arrivee : l'agenda donne, Alex's Mobs ne donnera plus son dictionnaire, et « tu as recu ton agenda »"
                            + " attend la fin du titre (" + HavenJourney.AFTER_TITLE + " tiques)",
                    given && told && deferred && due >= now + HavenJourney.AFTER_TITLE,
                    "agenda " + given + ", drapeau " + told + ", differe " + deferred + " a +" + (due - now));
            HavenJourney.onArrive(a);
            int agendas = 0;
            for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
                if (HavenAgenda.isAgenda(a.getInventory().getItem(i))) {
                    agendas += a.getInventory().getItem(i).getCount();
                }
            }
            check("seconde arrivee : toujours un seul agenda", agendas == 1, agendas + " agenda(s)");

            List<Component> pages = HavenAgenda.pages(a);
            check("les pages : le prochain rendez-vous (le QG, discuter avec l'equipe) puis le carnet de route",
                    pages.size() == 2 && mentions(pages.get(0), "game.emeraldweapons.haven.agenda.rdv.qg")
                            && mentions(pages.get(1), "game.emeraldweapons.haven.agenda.etape.qg"),
                    pages.size() + " page(s)");

            HavenJourney.onArrive(b);
            BlockPos hq = rooms.hqCenter(origin);
            a.moveTo(hq.getX() + 0.5, hq.getY(), hq.getZ() + 0.5, 0.0F, 0.0F);
            long later1 = now + HavenJourney.TITLE_DELAY + 1;
            HavenJourney.update(a, later1);
            HavenJourney.update(b, later1);
            ServerBossEvent bar = HavenJourney.bar(a.getUUID());
            Object[] args = bar != null && bar.getName().getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
            check("au QG sans l'equipe : « l'equipe arrive (1 sur 2) », barre jaune ; l'agenda dit d'attendre l'equipe",
                    HavenJourney.shown(a.getUUID()) == HavenJourney.Objective.EQUIPE && bar != null
                            && bar.getColor() == BossEvent.BossBarColor.YELLOW && args.length == 2
                            && Integer.valueOf(1).equals(args[0]) && Integer.valueOf(2).equals(args[1])
                            && mentions(HavenAgenda.pages(a).get(0), "game.emeraldweapons.haven.agenda.rdv.equipe"),
                    "objectif " + HavenJourney.shown(a.getUUID()) + ", arguments " + Arrays.toString(args));

            b.moveTo(hq.getX() + 1.5, hq.getY(), hq.getZ() + 0.5, 0.0F, 0.0F);
            HavenJourney.update(b, later1 + 1);
            HavenJourney.update(a, later1 + 1);
            HavenJourney.reuniteForTest(server);
            boolean both = HavenJourney.shown(a.getUUID()) == HavenJourney.Objective.BORNE
                    && HavenJourney.shown(b.getUUID()) == HavenJourney.Objective.BORNE;
            ServerBossEvent purple = HavenJourney.bar(a.getUUID());
            boolean titles = HavenJourney.titleKind(a.getUUID()) == HavenTitlePayload.REUNION
                    && HavenJourney.titleKind(b.getUUID()) == HavenTitlePayload.REUNION;
            boolean asked = HavenJourney.laterTexts(a.getUUID()).stream()
                    .anyMatch(c -> key(c).equals("game.emeraldweapons.haven.parcours.reunion"))
                    && HavenJourney.laterTexts(b.getUUID()).stream()
                    .anyMatch(c -> key(c).equals("game.emeraldweapons.haven.parcours.reunion"));
            check("l'equipe reunie : la borne pour les deux (barre violette), le titre « L'equipe est reunie »"
                            + " et la question du mode, sans pousser le Defi",
                    both && purple != null && purple.getColor() == BossEvent.BossBarColor.PURPLE && titles && asked
                            && mentions(HavenAgenda.pages(a).get(0), "game.emeraldweapons.haven.agenda.rdv.mode"),
                    "borne " + both + ", titres " + titles + ", question " + asked);
        } finally {
            for (FakePlayer p : List.of(a, b)) {
                HavenJourney.removeSubject(p.getUUID());
                HavenProgress.dropTemporary(p.getUUID());
            }
        }
    }

    /** La cle d'un texte traduit, ou "". */
    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /** Vrai si la cle apparait dans ce texte ou dans l'un de ses morceaux. */
    private static boolean mentions(Component component, String wanted) {
        if (key(component).equals(wanted)) {
            return true;
        }
        for (Component part : component.getSiblings()) {
            if (mentions(part, wanted)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ 12. l'arche du depart (§84)

    private static void departure(MinecraftServer server, ServerLevel level) {
        line("--- l'arche du depart au QG (§84)");
        HavenArrival.Layout rooms = HavenArrival.layout(server);
        BlockPos origin = HavenState.get(server).origin();
        boolean opened = HavenDeparture.open(server, GameState.Mode.DEFI, false);
        BlockPos gate = HavenDeparture.gate();
        net.minecraft.world.level.block.state.BlockState placed = gate == null ? null : level.getBlockState(gate);
        boolean inBar = gate != null && rooms != null
                && rooms.inHq(origin, gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5);
        boolean red = placed != null && placed.is(ModBlocks.ARC_PORTAL.get())
                && placed.getValue(com.emerald.block.ArcPortalBlock.TEINTE) == com.emerald.block.ArcPortalBlock.Tint.DEFI;
        check("le vote fini : l'arche du depart s'ouvre, rouge pour le Defi, dans le bar (sinon devant sa porte)",
                opened && red, "ouverte " + opened + " en " + gate + " (dans le bar " + inBar + "), " + placed);
        HavenDeparture.close(server);
        check("refermee : plus d'arche", gate == null || !level.getBlockState(gate).is(ModBlocks.ARC_PORTAL.get()),
                gate == null ? "-" : level.getBlockState(gate).toString());
    }

    // ================================================================ 7. lot 2 : le ratelier du QG

    private static void rack(MinecraftServer server, ServerLevel level) {
        line("--- lot 2 : le ratelier du QG");
        HavenState state = HavenState.get(server);
        HavenArrival.Layout rooms = HavenArrival.layout(server);
        BlockPos pos = HavenRack.keep(server, true);
        net.minecraft.world.level.block.state.BlockState placed = pos == null ? null : level.getBlockState(pos);
        boolean inHq = pos != null && rooms != null
                && rooms.inHq(state.origin(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        check("pose : le ratelier sur le socle derriere le comptoir, tourne vers les clients, dans la boite du Hip Hog",
                placed != null && placed.is(ModBlocks.HAVEN_GUN_RACK.get())
                        && placed.getValue(HavenGunRackBlock.FACING) == HavenRack.FACING && inHq,
                placed + (pos == null ? "" : " en " + pos.toShortString()) + ", dans le QG " + inHq);
        if (pos == null) {
            return;
        }
        FakePlayer fake = subject(level, "ratelier", pos.south(2));
        UUID id = fake.getUUID();
        HavenProgress.temporary(id, 0);
        HavenRack.Take locked = HavenRack.take(fake, pos);
        HavenProgress.get(id).departures = 1;
        HavenRack.Take taken = HavenRack.take(fake, pos);
        int forms = HavenProgress.forms(id);
        HavenRack.Take again = HavenRack.take(fake, pos);
        check("avant le premier depart : ferme ; au retour du Defi : le Scatter Gun, une seule fois ; ensuite : deja pris",
                locked == HavenRack.Take.LOCKED && taken == HavenRack.Take.TAKEN && forms == GunForm.RED_1.bit()
                        && again == HavenRack.Take.ALREADY,
                locked + ", " + taken + " (formes " + Integer.toBinaryString(forms) + "), " + again);
        HavenProgress.dropTemporary(id);
    }

    // ================================================================ 8. lot 2 : la deuxieme arrivee, la reprise

    private static void secondArrival(MinecraftServer server, ServerLevel level) {
        line("--- lot 2 : la deuxieme arrivee, la ville envahie, les rues reprises");
        HavenState state = HavenState.get(server);
        HavenArrival.Layout rooms = HavenArrival.layout(server);
        if (rooms == null || rooms.rooms().isEmpty()) {
            check("salles de la ville lues", false, "haven_rooms.json absent");
            return;
        }
        BlockPos origin = state.origin();
        BlockPos far = HavenArrival.standIn(level, origin, rooms.rooms().get(0), 0);
        FakePlayer fake = subject(level, "retour", far);
        UUID id = fake.getUUID();
        HavenJourney.addSubject(fake);
        HavenProgress.Entry entry = HavenProgress.temporary(id, 0);
        entry.welcomed = true;
        entry.hq = true;
        entry.departures = 1;
        HavenInvasion.Mode before = HavenInvasion.mode(server);
        HavenInvasionState city = HavenInvasionState.get(server);
        int savedCount = city == null ? 0 : city.reprise();
        HavenState.Phase phase = state.phase();
        try {
            HavenInvasion.Mode back = HavenJourney.reopenMode(server);
            entry.reprise = true;
            HavenInvasion.Mode done = HavenJourney.reopenMode(server);
            entry.reprise = false;
            HavenRules.setChantier(fake, true);
            HavenInvasion.Mode chantier = HavenJourney.reopenMode(server);
            HavenRules.setChantier(fake, false);
            state.forgetMode(id);
            check("mode a la reouverture : ENVAHIE pour un joueur revenu du Defi sans les rues reprises ;"
                            + " PAISIBLE une fois reprises ; l'operateur en chantier ne compte pas",
                    back == HavenInvasion.Mode.INVASION && done == HavenInvasion.Mode.PAISIBLE
                            && chantier == HavenInvasion.Mode.PAISIBLE,
                    back + " / " + done + " / " + chantier);

            HavenInvasion.setMode(server, HavenInvasion.Mode.PAISIBLE, null);
            state.setPhase(HavenState.Phase.PARTI);
            HavenInvasion.update(server);
            state.setPhase(phase);
            HavenInvasion.update(server);
            check("reouverture du lobby avec lui : la ville passe ENVAHIE",
                    HavenInvasion.mode(server) == HavenInvasion.Mode.INVASION, "mode " + HavenInvasion.mode(server));

            HavenJourney.onArrive(fake);
            int kind = HavenJourney.titleKind(id);
            boolean invaded = entry.invaded;
            HavenJourney.forgetLobby(id);
            HavenJourney.onArrive(fake);
            check("son arrivee joue « Haven a ete envahie », une seule fois",
                    kind == HavenTitlePayload.ENVAHIE && invaded && HavenJourney.titleKind(id) == -1,
                    "titre " + kind + ", envahie " + invaded + ", puis " + HavenJourney.titleKind(id));

            long now = level.getGameTime();
            HavenJourney.update(fake, now);
            ServerBossEvent bar = HavenJourney.bar(id);
            TranslatableContents farText = bar != null && bar.getName().getContents() instanceof TranslatableContents t ? t : null;
            check("sans arme, loin du QG : objectif « ton arme, au QG », distance et direction, barre rose",
                    HavenJourney.shown(id) == HavenJourney.Objective.ARME && bar != null
                            && bar.getColor() == BossEvent.BossBarColor.PINK && farText != null
                            && farText.getKey().endsWith("objectif.arme") && farText.getArgs().length == 2,
                    "objectif " + HavenJourney.shown(id) + ", texte " + (farText == null ? "?" : farText.getKey()));

            BlockPos hq = rooms.hqCenter(origin);
            fake.moveTo(hq.getX() + 0.5, hq.getY(), hq.getZ() + 0.5, 0.0F, 0.0F);
            HavenJourney.update(fake, now + 1);
            TranslatableContents nearText = bar != null && bar.getName().getContents() instanceof TranslatableContents t ? t : null;
            check("dans le Hip Hog : « ton arme : le ratelier, derriere le comptoir »",
                    HavenJourney.shown(id) == HavenJourney.Objective.ARME && nearText != null
                            && nearText.getKey().endsWith("objectif.arme.qg"),
                    "texte " + (nearText == null ? "?" : nearText.getKey()));

            HavenProgress.grantForms(id, GunForm.RED_1.bit());
            HavenJourney.update(fake, now + 2);
            ServerBossEvent red = HavenJourney.bar(id);
            Object[] args = red != null && red.getName().getContents() instanceof TranslatableContents t ? t.getArgs() : new Object[0];
            check("l'arme prise : objectif « reprendre les rues », compte et but (" + HavenJourney.REPRISE_GOAL + "), barre rouge",
                    HavenJourney.shown(id) == HavenJourney.Objective.REPRISE && red != null
                            && red.getColor() == BossEvent.BossBarColor.RED && args.length == 2
                            && Integer.valueOf(HavenJourney.REPRISE_GOAL).equals(args[1]),
                    "objectif " + HavenJourney.shown(id) + ", arguments " + Arrays.toString(args));

            if (city != null) {
                city.setReprise(0);
            }
            boolean counted = true;
            for (int i = 0; i < HavenJourney.REPRISE_GOAL - 1; i++) {
                counted &= HavenJourney.countKill(server);
            }
            int almost = HavenJourney.repriseCount(server);
            HavenInvasion.Mode stillInvaded = HavenInvasion.mode(server);
            boolean last = HavenJourney.countKill(server);
            check("24 monstres : le compte avance, la ville reste envahie ; le 25e : rues reprises (fiche, titre),"
                            + " ville paisible, compte remis a zero",
                    counted && almost == HavenJourney.REPRISE_GOAL - 1 && stillInvaded == HavenInvasion.Mode.INVASION
                            && last && entry.reprise && HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE
                            && HavenJourney.repriseCount(server) == 0 && HavenJourney.titleKind(id) == HavenTitlePayload.REPRISE,
                    "a 24 : " + almost + " " + stillInvaded + " ; puis reprise " + entry.reprise + ", mode "
                            + HavenInvasion.mode(server) + ", compte " + HavenJourney.repriseCount(server)
                            + ", titre " + HavenJourney.titleKind(id));

            HavenJourney.update(fake, now + 3);
            check("les rues reprises : retour aux objectifs du lot 1 (la borne, le QG deja rejoint)",
                    HavenJourney.shown(id) == HavenJourney.Objective.BORNE, "objectif " + HavenJourney.shown(id));

            HavenInvasion.setMode(server, HavenInvasion.Mode.INVASION, null);
            boolean extra = HavenJourney.countKill(server);
            check("une invasion lancee au bouton, personne n'attend la reprise : rien ne compte",
                    !extra && HavenJourney.repriseCount(server) == 0, "compte " + HavenJourney.repriseCount(server));

            UUID master = uuid("maitrise");
            HavenProgress.temporary(master, 0);
            HavenProgress.grantMastery(master);
            HavenProgress.Entry m = HavenProgress.get(master);
            check("la maitrise a la commande comprend les rues reprises : pas de ville envahie pour lui",
                    m.reprise && m.invaded && !HavenProgress.awaitsReprise(master),
                    "reprise " + m.reprise + ", envahie " + m.invaded);
            HavenProgress.dropTemporary(master);
        } finally {
            state.setPhase(phase);
            HavenInvasion.setMode(server, before, null);
            if (city != null) {
                city.setReprise(savedCount);
            }
            HavenJourney.removeSubject(id);
            HavenProgress.dropTemporary(id);
        }
    }

    // ================================================================ 9. lot 2 : le retour du Defi

    private static void returnDefeat(MinecraftServer server) {
        line("--- lot 2 : le retour du Defi apres une defaite");
        ServerLevel overworld = server.overworld();
        GameState game = GameState.get(overworld);
        HavenState state = HavenState.get(server);
        GameState.Mode saved = game.mode();
        UUID keeper = uuid("appartement-garde");
        state.assignApartment(keeper, 0, 7);
        try {
            game.chooseMode(GameState.Mode.DEFI);
            game.begin(overworld);
            state.setPhase(HavenState.Phase.PARTI);
            boolean applies = HavenReturn.applies(server);
            long now = overworld.getGameTime();
            Finale.defeat(overworld);
            HavenReturn.Stage first = HavenReturn.stage();
            long due = HavenReturn.due();
            HavenReturn.tick(server);
            HavenReturn.Stage waiting = HavenReturn.stage();
            HavenReturn.expireForTest();
            HavenReturn.tick(server);
            int[] kept = state.apartment(keeper);
            check("defaite : retour prevu " + HavenReturn.DEFEAT_DELAY + " tiques apres (le titre de fin), pas avant ;"
                            + " puis lobby rouvert, Lame replantee, appartements gardes",
                    applies && first == HavenReturn.Stage.DEFAITE && due == now + HavenReturn.DEFEAT_DELAY
                            && waiting == HavenReturn.Stage.DEFAITE && HavenReturn.stage() == HavenReturn.Stage.AUCUN
                            && HavenArrival.lobbyOpen(server) && game.status() == GameState.Status.LOBBY
                            && kept != null && kept[1] == 7,
                    "s'applique " + applies + ", " + first + " a +" + (due - now) + ", puis " + waiting + " -> "
                            + HavenReturn.stage() + ", phase " + state.phase() + ", partie " + game.status()
                            + ", appartement " + Arrays.toString(kept));
        } finally {
            state.removeApartment(keeper);
            restore(server, saved);
        }
    }

    private static void returnVictory(MinecraftServer server) {
        line("--- lot 2 : la porte de la victoire");
        ServerLevel overworld = server.overworld();
        GameState game = GameState.get(overworld);
        HavenState state = HavenState.get(server);
        GameState.Mode saved = game.mode();
        try {
            BlockPos where = WorldSetup.findOpenGround(overworld, overworld.getSharedSpawnPos().offset(48, 0, 0), 12);
            FakePlayer a = new FakePlayer(overworld, new GameProfile(uuid("porte-a"), "[Parcours]"));
            FakePlayer b = new FakePlayer(overworld, new GameProfile(uuid("porte-b"), "[Parcours]"));
            SUBJECTS.add(a.getUUID());
            SUBJECTS.add(b.getUUID());
            a.moveTo(where.getX() + 6.5, where.getY(), where.getZ() + 0.5, 0.0F, 0.0F);
            b.moveTo(where.getX() - 5.5, where.getY(), where.getZ() + 0.5, 0.0F, 0.0F);
            HavenReturn.addSubject(a);
            HavenReturn.addSubject(b);

            game.chooseMode(GameState.Mode.DEFI);
            game.begin(overworld);
            state.setPhase(HavenState.Phase.PARTI);
            Finale.victory(overworld, where);
            BlockPos door = HavenReturn.door();
            boolean placed = door != null && overworld.getBlockState(door).is(ModBlocks.HAVEN_GATE.get())
                    && overworld.getBlockEntity(door) instanceof HavenGateBlockEntity gate && gate.temporary();
            check("victoire : la porte s'ouvre pres de la ou le boss est tombe (8 blocs au plus), porte temporaire",
                    HavenReturn.stage() == HavenReturn.Stage.PORTE && placed
                            && door.distManhattan(where) <= 16 && Math.abs(door.getX() - where.getX()) <= 8
                            && Math.abs(door.getZ() - where.getZ()) <= 8,
                    "etape " + HavenReturn.stage() + ", porte " + (door == null ? "aucune" : door.toShortString())
                            + " pour " + where.toShortString() + ", posee " + placed);
            if (door == null) {
                return;
            }
            HavenReturn.tick(server);
            boolean nobody = HavenReturn.stage() == HavenReturn.Stage.PORTE && !HavenReturn.holding(a);
            a.moveTo(door.getX() + 0.5, door.getY(), door.getZ() + 0.5, 0.0F, 0.0F);
            HavenReturn.tick(server);
            boolean holdA = HavenReturn.holding(a) && HavenReturn.stage() == HavenReturn.Stage.PORTE;
            b.moveTo(door.getX() + 0.8, door.getY(), door.getZ() + 0.3, 0.0F, 0.0F);
            HavenReturn.tick(server);
            boolean gone = !overworld.getBlockState(door).is(ModBlocks.HAVEN_GATE.get());
            check("personne dans la porte : elle attend ; A la passe : il attend l'equipe ; B, le dernier, la passe :"
                            + " porte retiree, lobby rouvert",
                    nobody && holdA && HavenReturn.stage() == HavenReturn.Stage.AUCUN && gone
                            && HavenArrival.lobbyOpen(server) && game.status() == GameState.Status.LOBBY,
                    "attend " + nobody + ", A " + holdA + ", puis " + HavenReturn.stage() + ", porte retiree " + gone
                            + ", phase " + state.phase() + ", partie " + game.status());

            a.moveTo(where.getX() + 6.5, where.getY(), where.getZ() + 0.5, 0.0F, 0.0F);
            b.moveTo(where.getX() - 5.5, where.getY(), where.getZ() + 0.5, 0.0F, 0.0F);
            game.chooseMode(GameState.Mode.DEFI);
            game.begin(overworld);
            state.setPhase(HavenState.Phase.PARTI);
            Finale.victory(overworld, where);
            BlockPos second = HavenReturn.door();
            HavenReturn.expireForTest();
            HavenReturn.tick(server);
            boolean closed = second != null && !overworld.getBlockState(second).is(ModBlocks.HAVEN_GATE.get());
            check("personne ne la passe : au bout de cinq minutes, retour quand meme, porte retiree",
                    HavenReturn.stage() == HavenReturn.Stage.AUCUN && closed && HavenArrival.lobbyOpen(server),
                    HavenReturn.stage() + ", porte retiree " + closed + ", phase " + state.phase());

            game.chooseMode(GameState.Mode.LIBRE);
            game.begin(overworld);
            state.setPhase(HavenState.Phase.PARTI);
            Finale.victory(overworld, where);
            check("en Monde ouvert, la victoire relance un cycle : ni porte ni retour",
                    HavenReturn.stage() == HavenReturn.Stage.AUCUN && HavenReturn.door() == null,
                    "etape " + HavenReturn.stage());
        } finally {
            HavenReturn.resetForTest(server);
            HavenReturn.clearSubjects();
            restore(server, saved);
        }
    }

    /** Remet le lobby comme au debut : Lame replantee, ville rouverte, regime d'avant. */
    private static void restore(MinecraftServer server, GameState.Mode saved) {
        ServerLevel overworld = server.overworld();
        GameState game = GameState.get(overworld);
        com.emerald.game.GameManager.clear();
        com.emerald.game.GameManager.setup(overworld, overworld.getSharedSpawnPos(), false);
        HavenArrival.reopen(server);
        game.chooseMode(saved);
        game.forgetModeChoice();
    }

    // ================================================================ 10. les transports de la ville

    private static void gates(MinecraftServer server, ServerLevel level) {
        line("--- les transports : l'arche d'un bout a l'autre, le portail des tours");
        int placed = HavenGates.keep(server, true);
        boolean states = true;
        StringBuilder wrong = new StringBuilder();
        for (HavenGates.Station station : HavenGates.STATIONS) {
            BlockPos pos = HavenGates.position(server, station);
            if (level.getBlockState(pos) != station.state()) {
                states = false;
                wrong.append(station.id()).append(' ').append(level.getBlockState(pos)).append("; ");
            }
        }
        check("pose : les deux arches et les six plateaux des tours, a leur place, du bon modele",
                placed == HavenGates.STATIONS.size() && states,
                placed + " pose(s) sur " + HavenGates.STATIONS.size() + (wrong.length() == 0 ? "" : ", " + wrong));

        HavenGates.Station west = HavenGates.station("arche_ouest");
        HavenGates.Station east = HavenGates.station("arche_est");
        BlockPos westPos = HavenGates.position(server, west);
        BlockPos eastPos = HavenGates.position(server, east);
        FakePlayer walker = subject(level, "arche", westPos.north(4));
        HavenGates.addSubject(walker);
        try {
            walker.moveTo(westPos.getX() + 0.5, westPos.getY(), westPos.getZ() + 0.5, 0.0F, 0.0F);
            HavenGates.tickForTest(server);
            BlockPos exit = eastPos.relative(east.facing(), 2);
            boolean crossed = walker.blockPosition().equals(exit);
            walker.moveTo(eastPos.getX() + 0.5, eastPos.getY(), eastPos.getZ() + 0.5, 0.0F, 0.0F);
            HavenGates.tickForTest(server);
            boolean held = walker.blockPosition().equals(eastPos);
            HavenGates.rechargeForTest(walker.getUUID());
            HavenGates.tickForTest(server);
            BlockPos back = westPos.relative(west.facing(), 2);
            boolean returned = walker.blockPosition().equals(back);
            check("l'arche ouest mene devant l'arche est, dos a elle ; tout de suite apres, la recharge retient ;"
                            + " rechargee, elle ramene a l'ouest",
                    crossed && held && returned,
                    "passe " + crossed + ", retenu " + held + ", retour " + returned + " (en " + walker.blockPosition().toShortString() + ")");
        } finally {
            HavenGates.removeSubject(walker.getUUID());
        }

        HavenGates.Station foot = HavenGates.station("tour_ouest_pied");
        HavenGates.Station terrace = HavenGates.station("tour_ouest_terrasse");
        HavenGates.Station top = HavenGates.station("tour_ouest_sommet");
        BlockPos footPos = HavenGates.position(server, foot);
        BlockPos terracePos = HavenGates.position(server, terrace);
        BlockPos topPos = HavenGates.position(server, top);
        FakePlayer climber = subject(level, "portail", footPos);
        HavenGates.addSubject(climber);
        try {
            climber.moveTo(footPos.getX() + 0.5, footPos.getY() + 0.22, footPos.getZ() + 0.5, 0.0F, -60.0F);
            for (int i = 0; i < HavenGates.LOOK_TICKS - 1; i++) {
                HavenGates.tickForTest(server);
            }
            boolean waited = climber.blockPosition().equals(footPos);
            HavenGates.tickForTest(server);
            boolean up = climber.blockPosition().equals(terracePos);
            climber.setXRot(-60.0F);
            for (int i = 0; i < HavenGates.LOOK_TICKS + 5; i++) {
                HavenGates.tickForTest(server);
            }
            boolean disarmed = climber.blockPosition().equals(terracePos);
            climber.setXRot(0.0F);
            HavenGates.tickForTest(server);
            climber.setXRot(-60.0F);
            for (int i = 0; i < HavenGates.LOOK_TICKS; i++) {
                HavenGates.tickForTest(server);
            }
            boolean summit = climber.blockPosition().equals(topPos);
            climber.setXRot(0.0F);
            HavenGates.tickForTest(server);
            climber.setXRot(-60.0F);
            for (int i = 0; i < HavenGates.LOOK_TICKS + 5; i++) {
                HavenGates.tickForTest(server);
            }
            boolean ceiling = climber.blockPosition().equals(topPos);
            climber.setXRot(60.0F);
            for (int i = 0; i < 2 * HavenGates.LOOK_TICKS + 2; i++) {
                HavenGates.tickForTest(server);
                if (i == HavenGates.LOOK_TICKS) {
                    climber.setXRot(0.0F);
                    HavenGates.tickForTest(server);
                    climber.setXRot(60.0F);
                }
            }
            boolean down = climber.blockPosition().equals(footPos);
            check("le portail : une seconde a regarder en haut monte du pied a la terrasse, pas avant ;"
                            + " il faut detourner le regard pour repartir ; puis le sommet ; rien au-dessus du sommet ;"
                            + " en regardant en bas, on redescend d'arret en arret jusqu'au pied",
                    waited && up && disarmed && summit && ceiling && down,
                    "attend " + waited + ", terrasse " + up + ", desarme " + disarmed + ", sommet " + summit
                            + ", plafond " + ceiling + ", pied " + down + " (en " + climber.blockPosition().toShortString() + ")");
        } finally {
            HavenGates.removeSubject(climber.getUUID());
        }
    }

    // ================================================================ 6. le vrai fichier

    private static void isolation(MinecraftServer server) {
        line("--- le fichier du monde");
        HavenProgress.save();
        Path path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                .resolve("emeraldweapons").resolve("haven_parcours.json");
        Map<UUID, HavenProgress.Entry> in = new HashMap<>();
        HavenProgress.read(path, in);
        boolean clean = true;
        for (UUID id : SUBJECTS) {
            if (in.containsKey(id)) {
                clean = false;
            }
        }
        check("aucun cobaye du banc dans le parcours du monde", clean, in.size() + " fiche(s) dans " + path.getFileName());
    }

    // ================================================================ outils

    private static FakePlayer subject(ServerLevel level, String name, BlockPos feet) {
        FakePlayer fake = new FakePlayer(level, new GameProfile(uuid(name), "[Parcours]"));
        level.getChunkAt(feet);
        fake.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
        SUBJECTS.add(fake.getUUID());
        return fake;
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(("autotest-parcours:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String describe(@Nullable ItemStack gun) {
        MorphGunData d = gun == null ? null : MorphGunData.of(gun);
        return d == null ? "aucune arme" : d.form().id + ", formes " + Integer.toBinaryString(d.owned())
                + ", eco " + d.ecoRed() + "/" + d.ecoYellow() + "/" + d.ecoBlue() + "/" + d.ecoDark();
    }

    private static void end(MinecraftServer server) {
        if (stage == Stage.END) {
            return;
        }
        stage = Stage.END;
        for (UUID id : SUBJECTS) {
            MorphGunKeeper.removeSubject(id);
            HavenJourney.removeSubject(id);
            HavenProgress.dropTemporary(id);
        }
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("parcours_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest parcours : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest parcours : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest parcours : {}", text);
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        line((ok ? "OK  " : "KO  ") + what + " -- " + detail);
    }
}
