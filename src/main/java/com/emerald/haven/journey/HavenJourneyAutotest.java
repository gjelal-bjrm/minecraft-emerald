package com.emerald.haven.journey;

import com.emerald.game.GameState;
import com.emerald.haven.Haven;
import com.emerald.haven.HavenArrival;
import com.emerald.haven.HavenAutotest;
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
 * Le banc d'essai du parcours de Haven, lot 1 (cahier §79), INERTE sans
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
 *      rien en chantier ; plus rien apres le depart.
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
                line("autotest du parcours de Haven (lot 1), " + LocalDateTime.now().withNano(0));
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
        check("aller-retour : formes, arrivee, QG, departs et quetes relus a l'identique",
                read == 2 && ra != null && ra.forms == ea.forms && ra.welcomed && ra.hq && ra.departures == 3
                        && ra.quests.contains("sig_chasse") && rb != null && rb.welcomed && rb.forms == 0,
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
        check("sans la maitrise : le refus dit ce qui manque (12 armes sur 12), et la maitrise est refusee",
                !HavenProgress.mastery(nobody) && args.length == 2 && Integer.valueOf(12).equals(args[0])
                        && Integer.valueOf(12).equals(args[1]),
                Arrays.toString(args));
        HavenProgress.temporary(nobody, GunForm.ALL_MASK);
        check("les douze armes (et aucune quete demandee au lot 1) : la maitrise",
                HavenProgress.mastery(nobody) && HavenProgress.REQUIRED_QUESTS.isEmpty(),
                "armes manquantes " + HavenProgress.missingWeapons(nobody));
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
        check("dans le Hip Hog : objectif « la borne » (barre rouge), QG note pour ce lobby et sur la fiche",
                inside && HavenJourney.shown(fake.getUUID()) == HavenJourney.Objective.BORNE
                        && HavenJourney.reachedHqThisLobby(fake.getUUID()) && HavenProgress.get(fake.getUUID()).hq
                        && red != null && red.getColor() == BossEvent.BossBarColor.RED,
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
