package com.emerald.haven.quest;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenRules;
import com.emerald.haven.fauna.HavenFauna;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenMonsterKilledEvent;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.vehicle.JakVehicleEntity;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LES QUETES DE HAVEN EN COURS (lot 3, cahier §86).
 *
 * « Il faudra qu'on definisse combien de quetes on met a disposition et a quel PNJ on les
 * donne. [...] L'idee, c'est de faire voyager les joueurs un peu partout sur la map. »
 *
 * Six heros (HavenHero), dix-huit quetes (HavenQuestBook). On parle a un heros (clic droit) :
 * sa carte du chat liste ses quetes -- faite, a faire (bouton « Accepter »), en cours (bouton
 * « Rejoindre »), ou fermee (apres la precedente). Les decisions du joueur (§85.1) :
 *
 *  - EN EQUIPE : celui qui accepte la propose aux autres joueurs de la ville (carte
 *    « Rejoindre », une minute) ; la reussite paie tous ceux qui l'ont rejointe ;
 *  - L'INVASION SEULEMENT POUR LE COMBAT : les quetes de Torn, Sig et Samos envahissent la
 *    ville le temps de la faire ; la conduite, le tir et l'eau se font en ville paisible ;
 *  - LES ORBES : chaque quete paie en orbes a la premiere reussite ; qui aide a refaire une
 *    quete deja faite touche un coup de main ; le contrat de Torn paie a chaque fois ; les
 *    epreuves de tir paient a la medaille (et la difference si l'on fait mieux).
 *
 * UNE QUETE A LA FOIS PAR JOUEUR, et un seul deroulement de chaque quete a la fois : qui
 * arrive pendant qu'elle court la rejoint. La barre d'objectif du guide (HavenJourney) montre
 * la quete tant qu'elle dure. Rien n'est sauvegarde : un arret, une fermeture de la ville ou
 * un depart annule les quetes en cours, qui retirent ce qu'elles avaient pose.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenQuests {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /** Ce que paie un coup de main sur une quete deja faite. */
    public static final int HELPER_REWARD = 10;
    /** La carte « Rejoindre » vaut une minute. */
    public static final int JOIN_TTL = 20 * 60;
    private static final Component DASH = Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY);

    private static final List<QuestRun> RUNS = new ArrayList<>();
    private static final Map<UUID, QuestRun> BY_PLAYER = new HashMap<>();
    /** La ville a ete envahie par une quete (et non par le bouton) : la paix revient apres. */
    private static boolean invadedByQuests;
    /** Les cobayes des bancs, qui ne sont pas dans la liste des joueurs. */
    static final Map<UUID, ServerPlayer> TEST_PLAYERS = new HashMap<>();

    private HavenQuests() {
    }

    // ================================================================ lecture

    @Nullable
    public static QuestRun runOf(UUID player) {
        return BY_PLAYER.get(player);
    }

    @Nullable
    public static QuestRun running(HavenQuest quest) {
        for (QuestRun run : RUNS) {
            if (run.quest == quest) {
                return run;
            }
        }
        return null;
    }

    public static List<QuestRun> runs() {
        return List.copyOf(RUNS);
    }

    /** La chasse aux mouettes court-elle ? (les armes du Morph Gun visent alors les mouettes des quais) */
    public static boolean gullHunt() {
        for (QuestRun run : RUNS) {
            if (run instanceof com.emerald.haven.quest.runs.GullsRun && run.status() == QuestRun.Status.RUNNING) {
                return true;
            }
        }
        return false;
    }

    /** Une quete de combat court-elle ? (le bouton du QG ne rend pas la paix pendant ce temps) */
    public static boolean combatRunning() {
        for (QuestRun run : RUNS) {
            if (run.quest.invades()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static ServerPlayer playerOf(ServerLevel level, UUID id) {
        ServerPlayer test = TEST_PLAYERS.get(id);
        return test != null ? test : level.getServer().getPlayerList().getPlayer(id);
    }

    /** La barre d'objectif d'un joueur en quete : texte, couleur, avancement ; null sinon. */
    @Nullable
    public static Bar bar(ServerPlayer player) {
        QuestRun run = BY_PLAYER.get(player.getUUID());
        if (run == null || run.status() != QuestRun.Status.RUNNING) {
            return null;
        }
        Component text = Component.empty().append(run.quest.giver().displayName()).append(" — ")
                .append(run.quest.title()).append(" : ").append(run.objective());
        return new Bar(text, run.color(), Math.max(0.0F, Math.min(1.0F, run.progress())));
    }

    public record Bar(Component text, BossEvent.BossBarColor color, float progress) {
    }

    // ================================================================ le dialogue

    /** On parle a un heros : sa carte du chat. */
    public static void talk(ServerPlayer player, HavenHero hero) {
        UUID id = player.getUUID();
        streetsCatchUp(player);
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.empty()
                .append(hero.displayName().copy().withStyle(hero.color, ChatFormatting.BOLD))
                .append(Component.literal(" : « ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("game.emeraldweapons.haven.quete.parle." + hero.id)
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" »").withStyle(ChatFormatting.GRAY)));
        HavenProgress.Entry entry = HavenProgress.get(id);
        if (!entry.reprise && hero != HavenHero.TORN) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.verrou.reprise")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        for (HavenQuest quest : HavenQuestBook.of(hero)) {
            player.sendSystemMessage(line(player, quest));
        }
        if (hero == HavenHero.TESS) {
            player.sendSystemMessage(Component.literal("  ").append(HavenCards.button(player,
                    Component.translatable("game.emeraldweapons.haven.quete.bouton.boutique"),
                    Component.translatable("game.emeraldweapons.haven.quete.bouton.boutique.info"),
                    ChatFormatting.LIGHT_PURPLE, HavenShop::open)));
        }
        QuestRun mine = BY_PLAYER.get(id);
        if (mine != null && mine.quest.giver() == hero) {
            player.sendSystemMessage(Component.literal("  ").append(HavenCards.button(player,
                    Component.translatable("game.emeraldweapons.haven.quete.bouton.abandonner"),
                    Component.translatable("game.emeraldweapons.haven.quete.bouton.abandonner.info", mine.quest.title()),
                    ChatFormatting.RED, HavenQuests::abandon)));
        }
    }

    /** Une ligne de la carte : faite, en cours, a faire, fermee. */
    private static MutableComponent line(ServerPlayer player, HavenQuest quest) {
        UUID id = player.getUUID();
        boolean done = HavenProgress.done(id, quest.id());
        MutableComponent title = quest.title().copy().withStyle(style -> style.withHoverEvent(
                new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                        quest.description())));
        MutableComponent out = Component.literal("  ");
        if (quest.factory() == null) {
            // les rues : faites par la ville elle-meme, au retour du Defi
            return out.append(Component.literal(done ? "✓ " : "▶ ").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.YELLOW))
                    .append(title.withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE))
                    .append(DASH).append(Component.translatable(done ? "game.emeraldweapons.haven.quete.rues.faite"
                            : "game.emeraldweapons.haven.quete.rues.a_faire").withStyle(ChatFormatting.GRAY));
        }
        QuestRun running = running(quest);
        if (running != null && running.member(id)) {
            return out.append(Component.literal("⟳ ").withStyle(ChatFormatting.AQUA)).append(title.withStyle(ChatFormatting.AQUA))
                    .append(DASH).append(Component.translatable("game.emeraldweapons.haven.quete.en_cours").withStyle(ChatFormatting.GRAY));
        }
        if (done && !quest.repeatable() && !(quest.medals() && HavenProgress.medal(id, quest.id()) < 3)) {
            out.append(Component.literal("✓ ").withStyle(ChatFormatting.GREEN)).append(title.withStyle(ChatFormatting.GREEN));
            if (quest.medals()) {
                out.append(Component.literal(" ").append(medalName(HavenProgress.medal(id, quest.id()))));
            }
            return out;
        }
        HavenQuest before = HavenQuestBook.before(quest);
        if (before != null && !HavenProgress.done(id, before.id())) {
            return out.append(Component.literal("✗ ").withStyle(ChatFormatting.DARK_GRAY)).append(title.withStyle(ChatFormatting.DARK_GRAY))
                    .append(DASH).append(Component.translatable("game.emeraldweapons.haven.quete.apres", before.title())
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        out.append(Component.literal(done ? "↻ " : "▶ ").withStyle(ChatFormatting.YELLOW)).append(title.withStyle(ChatFormatting.WHITE));
        if (quest.medals() && done) {
            out.append(Component.literal(" ").append(medalName(HavenProgress.medal(id, quest.id()))));
        }
        out.append(DASH).append(Component.translatable(quest.medals() ? "game.emeraldweapons.haven.quete.paie.medailles"
                : "game.emeraldweapons.haven.quete.paie", quest.medals() ? quest.rewardFor(1) : quest.reward(),
                quest.rewardFor(3)).withStyle(ChatFormatting.GOLD));
        out.append(" ");
        if (running != null) {
            return out.append(HavenCards.button(player, Component.translatable("game.emeraldweapons.haven.quete.bouton.rejoindre"),
                    quest.description(), ChatFormatting.AQUA, p -> join(p, quest)));
        }
        return out.append(HavenCards.button(player, Component.translatable("game.emeraldweapons.haven.quete.bouton.accepter"),
                quest.description(), ChatFormatting.GREEN, p -> accept(p, quest)));
    }

    public static Component medalName(int medal) {
        return switch (medal) {
            case 3 -> Component.translatable("game.emeraldweapons.haven.quete.medaille.or").withStyle(ChatFormatting.GOLD);
            case 2 -> Component.translatable("game.emeraldweapons.haven.quete.medaille.argent").withStyle(ChatFormatting.GRAY);
            case 1 -> Component.translatable("game.emeraldweapons.haven.quete.medaille.bronze").withStyle(ChatFormatting.RED);
            default -> Component.empty();
        };
    }

    // ================================================================ accepter, rejoindre, abandonner

    /** Peut-il commencer (ou rejoindre) cette quete ? Sinon, il en est prevenu. */
    static boolean allowed(ServerPlayer player, HavenQuest quest) {
        UUID id = player.getUUID();
        Component refusal = null;
        if (!Haven.is(player.level()) || !HavenInvasion.cityOpen(player.server)) {
            refusal = Component.translatable("game.emeraldweapons.haven.quete.refus.ville");
        } else if (!HavenProgress.get(id).reprise) {
            refusal = Component.translatable("game.emeraldweapons.haven.quete.verrou.reprise");
        } else if (BY_PLAYER.containsKey(id)) {
            refusal = Component.translatable("game.emeraldweapons.haven.quete.refus.occupe", BY_PLAYER.get(id).quest.title());
        } else if (quest.factory() == null) {
            refusal = Component.translatable("game.emeraldweapons.haven.quete.refus.rues");
        } else {
            HavenQuest before = HavenQuestBook.before(quest);
            if (before != null && !HavenProgress.done(id, before.id())) {
                refusal = Component.translatable("game.emeraldweapons.haven.quete.refus.apres", before.title());
            }
        }
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.GRAY));
            return false;
        }
        return true;
    }

    /** Il accepte : la quete commence (ou il rejoint celle qui court deja). */
    public static QuestRun accept(ServerPlayer player, HavenQuest quest) {
        QuestRun running = running(quest);
        if (running != null) {
            join(player, quest);
            return running;
        }
        if (!allowed(player, quest)) {
            return null;
        }
        ServerLevel level = (ServerLevel) player.level();
        QuestRun run = quest.factory().create(quest, level);
        run.team.add(player.getUUID());
        RUNS.add(run);
        BY_PLAYER.put(player.getUUID(), run);
        if (quest.invades()) {
            invade(level, quest);
        }
        run.begin();
        Component start = Component.translatable("game.emeraldweapons.haven.quete.debut",
                quest.giver().displayName(), quest.title()).withStyle(quest.giver().color);
        player.sendSystemMessage(start);
        player.sendSystemMessage(quest.description().copy().withStyle(ChatFormatting.GRAY));
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        com.emerald.haven.journey.HavenJourney.award(player, "haven_heros");
        LOGGER.info("quetes de Haven : {} commence « {} »", player.getGameProfile().getName(), quest.id());
        // la carte « Rejoindre » aux autres joueurs de la ville
        for (ServerPlayer other : level.players()) {
            if (other == player || other.isFakePlayer() || BY_PLAYER.containsKey(other.getUUID())
                    || !HavenProgress.get(other.getUUID()).reprise) {
                continue;
            }
            other.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.propose",
                    player.getDisplayName(), quest.title(), quest.giver().displayName()).withStyle(ChatFormatting.AQUA)
                    .append(" ").append(HavenCards.button(other,
                            Component.translatable("game.emeraldweapons.haven.quete.bouton.rejoindre"),
                            quest.description(), ChatFormatting.AQUA, p -> join(p, quest), JOIN_TTL)));
        }
        return run;
    }

    /** Il rejoint la quete qui court. */
    public static void join(ServerPlayer player, HavenQuest quest) {
        QuestRun run = running(quest);
        if (run == null || run.status() != QuestRun.Status.RUNNING) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.refus.finie", quest.title())
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        if (run.member(player.getUUID())) {
            return;
        }
        if (!allowedToJoin(player, quest)) {
            return;
        }
        run.team.add(player.getUUID());
        BY_PLAYER.put(player.getUUID(), run);
        for (ServerPlayer member : run.members()) {
            member.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.rejoint",
                    player.getDisplayName(), quest.title()).withStyle(ChatFormatting.AQUA));
        }
    }

    private static boolean allowedToJoin(ServerPlayer player, HavenQuest quest) {
        UUID id = player.getUUID();
        Component refusal = null;
        if (!Haven.is(player.level())) {
            refusal = Component.translatable("game.emeraldweapons.haven.quete.refus.ville");
        } else if (!HavenProgress.get(id).reprise) {
            refusal = Component.translatable("game.emeraldweapons.haven.quete.verrou.reprise");
        } else if (BY_PLAYER.containsKey(id)) {
            refusal = Component.translatable("game.emeraldweapons.haven.quete.refus.occupe", BY_PLAYER.get(id).quest.title());
        }
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.GRAY));
            return false;
        }
        return true;
    }

    /** Il abandonne sa quete ; si l'equipe se vide, la quete echoue. */
    public static void abandon(ServerPlayer player) {
        QuestRun run = BY_PLAYER.remove(player.getUUID());
        if (run == null) {
            return;
        }
        run.team.remove(player.getUUID());
        QuestMarkers.clear(List.of(player));
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.abandon", run.quest.title())
                .withStyle(ChatFormatting.GRAY));
        if (run.team.isEmpty()) {
            run.fail("abandon");
        }
    }

    // ================================================================ la tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (RUNS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = Haven.level(server);
        if (level == null || !HavenInvasion.cityOpen(server)) {
            cancelAll(level, "ville");
            return;
        }
        long now = level.getGameTime();
        for (QuestRun run : List.copyOf(RUNS)) {
            // les absents quittent l'equipe
            for (UUID id : List.copyOf(run.team)) {
                ServerPlayer player = playerOf(level, id);
                if (player == null || player.hasDisconnected() || !Haven.is(player.level()) || HavenRules.chantier(player)) {
                    run.team.remove(id);
                    BY_PLAYER.remove(id, run);
                    if (player != null && !player.hasDisconnected()) {
                        QuestMarkers.clear(List.of(player));
                    }
                }
            }
            if (run.team.isEmpty() && run.status() == QuestRun.Status.RUNNING) {
                run.fail("abandon");
            }
            if (run.status() == QuestRun.Status.RUNNING) {
                run.tick(now);
                if (run.status() == QuestRun.Status.RUNNING && run.quest.timeLimit() > 0
                        && run.elapsed() >= run.quest.timeLimit()) {
                    run.fail("temps");
                }
            }
            if (run.status() != QuestRun.Status.RUNNING) {
                end(level, run);
            }
        }
    }

    /** Une quete finit : recompense ou message, nettoyage, et la paix si elle avait envahi la ville. */
    private static void end(@Nullable ServerLevel level, QuestRun run) {
        try {
            run.cleanup();
        } catch (RuntimeException e) {
            LOGGER.error("quetes de Haven : nettoyage de « {} »", run.quest.id(), e);
        }
        RUNS.remove(run);
        List<ServerPlayer> members = level == null ? List.of() : run.members();
        for (UUID id : run.team) {
            BY_PLAYER.remove(id, run);
        }
        if (run.status() == QuestRun.Status.DONE) {
            for (ServerPlayer member : members) {
                reward(member, run.quest, run.medal());
            }
            LOGGER.info("quetes de Haven : « {} » reussie par {} (medaille {})", run.quest.id(), run.team, run.medal());
        } else if (run.failKey() != null && !"silence".equals(run.failKey())) {
            for (ServerPlayer member : members) {
                member.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.echec." + run.failKey(),
                        run.quest.title()).withStyle(ChatFormatting.RED));
                member.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            LOGGER.info("quetes de Haven : « {} » echouee ({})", run.quest.id(), run.failKey());
        }
        if (level != null && run.quest.invades() && invadedByQuests && !combatRunning()) {
            invadedByQuests = false;
            HavenInvasion.setMode(level.getServer(), HavenInvasion.Mode.PAISIBLE, null);
        }
    }

    /** Toutes les quetes s'arretent (fermeture de la ville, depart, arret). */
    public static void cancelAll(@Nullable ServerLevel level, String why) {
        if (!RUNS.isEmpty()) {
            LOGGER.info("quetes de Haven : les {} quete(s) en cours s'arretent ({})", RUNS.size(), why);
        }
        for (QuestRun run : List.copyOf(RUNS)) {
            run.fail("silence");
            end(level, run);
        }
        RUNS.clear();
        BY_PLAYER.clear();
        invadedByQuests = false;
    }

    private static void invade(ServerLevel level, HavenQuest quest) {
        MinecraftServer server = level.getServer();
        if (HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE) {
            invadedByQuests = true;
            HavenInvasion.setMode(server, HavenInvasion.Mode.INVASION, null);
            Component line = Component.translatable("game.emeraldweapons.haven.quete.invasion", quest.title())
                    .withStyle(ChatFormatting.RED);
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(line);
            }
        }
    }

    // ================================================================ recompenses

    /** Ce qu'il touche a la reussite. */
    static void reward(ServerPlayer player, HavenQuest quest, int medal) {
        UUID id = player.getUUID();
        boolean hadMastery = HavenProgress.mastery(id);
        boolean first = !HavenProgress.done(id, quest.id());
        int gain;
        if (quest.medals()) {
            int before = HavenProgress.setMedal(id, quest.id(), medal);
            HavenProgress.completeQuest(id, quest.id());
            gain = medal > before ? quest.rewardFor(medal) - (before > 0 ? quest.rewardFor(before) : 0) : 0;
        } else if (first || quest.repeatable()) {
            HavenProgress.completeQuest(id, quest.id());
            gain = quest.reward();
        } else {
            gain = HELPER_REWARD;
        }
        if (gain > 0) {
            HavenProgress.addOrbs(id, gain);
        }
        MutableComponent line = Component.translatable("game.emeraldweapons.haven.quete.reussie", quest.title())
                .withStyle(ChatFormatting.GREEN);
        if (quest.medals()) {
            line.append(" ").append(medalName(medal));
        }
        if (gain > 0) {
            line.append(Component.translatable("game.emeraldweapons.haven.quete.gain", gain).withStyle(ChatFormatting.GOLD));
        } else if (quest.medals()) {
            line.append(Component.translatable("game.emeraldweapons.haven.quete.pas_mieux").withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(line);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.9F, 1.1F);
        HavenOrbs.sync(player);
        if (!hadMastery && HavenProgress.mastery(id)) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.haven.quete.maitrise")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            com.emerald.haven.journey.HavenJourney.award(player, "haven_maitrise");
        }
    }

    /**
     * Les rues reprises (lot 2) sont la premiere quete de Torn. Un joueur qui les avait reprises
     * avant l'arrivee des quetes les voit comptees, avec leur recompense, a sa premiere visite.
     */
    public static void streetsCatchUp(ServerPlayer player) {
        UUID id = player.getUUID();
        if (HavenProgress.get(id).reprise && !HavenProgress.done(id, "rues")) {
            HavenQuest streets = HavenQuestBook.byId("rues");
            if (streets != null) {
                reward(player, streets, 0);
            }
        }
    }

    // ================================================================ evenements

    @SubscribeEvent
    public static void onMonsterKilled(HavenMonsterKilledEvent event) {
        ServerPlayer killer = event.getKiller();
        if (killer == null) {
            return;
        }
        QuestRun run = BY_PLAYER.get(killer.getUUID());
        if (run != null && run.status() == QuestRun.Status.RUNNING) {
            run.onKill(event, killer);
        }
        // les monstres poses par une quete comptent pour elle, qui qu'il soit
        for (QuestRun other : List.copyOf(RUNS)) {
            if (other != run && other.status() == QuestRun.Status.RUNNING && event.getMonster().getTags().contains(other.tag())) {
                other.onKill(event, killer);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRun run = BY_PLAYER.get(player.getUUID());
            if (run != null && run.status() == QuestRun.Status.RUNNING) {
                run.onDeath(player);
            }
            return;
        }
        if (event.getEntity() instanceof Mob mob && HavenFauna.isFauna(mob)) {
            ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p ? p
                    : mob.getKillCredit() instanceof ServerPlayer credited ? credited : null;
            if (killer != null) {
                QuestRun run = BY_PLAYER.get(killer.getUUID());
                if (run != null && run.status() == QuestRun.Status.RUNNING) {
                    run.onFaunaKill(killer, mob);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestRun run = BY_PLAYER.get(player.getUUID());
            if (run != null && run.status() == QuestRun.Status.RUNNING) {
                run.onFish(player, event.getDrops());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        QuestRun run = BY_PLAYER.get(player.getUUID());
        if (run != null && run.status() == QuestRun.Status.RUNNING && run.onInteract(player, event.getTarget())) {
            event.setCanceled(true);
        }
    }

    /** Un clic sur un bloc (un coffre englouti...) : la quete le prend, le coffre ne s'ouvre pas. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        QuestRun run = BY_PLAYER.get(player.getUUID());
        if (run != null && run.status() == QuestRun.Status.RUNNING && run.onBlock(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Un eco touche (GunEco) : pour les quetes de Samos. */
    public static void onEco(ServerPlayer player, GunForm.Family family) {
        QuestRun run = BY_PLAYER.get(player.getUUID());
        if (run != null && run.status() == QuestRun.Status.RUNNING) {
            run.onEco(player, family);
        }
    }

    /** Une voiture du trafic percutee par un joueur au volant (VehicleImpacts). */
    public static void onRam(ServerPlayer driver, JakVehicleEntity car, double momentum) {
        QuestRun run = BY_PLAYER.get(driver.getUUID());
        if (run != null && run.status() == QuestRun.Status.RUNNING) {
            run.onRam(driver, car, momentum);
        }
    }

    /** A l'arret, avant la sauvegarde : ce que les quetes ont pose s'en va. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStopping(ServerStoppingEvent event) {
        cancelAll(Haven.level(event.getServer()), "arret");
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RUNS.clear();
        BY_PLAYER.clear();
        TEST_PLAYERS.clear();
        invadedByQuests = false;
    }

    /** Pour le banc : un cobaye joue comme un joueur. */
    public static void addTestPlayer(ServerPlayer player) {
        TEST_PLAYERS.put(player.getUUID(), player);
    }

    public static void removeTestPlayer(ServerPlayer player) {
        TEST_PLAYERS.remove(player.getUUID());
    }

    /** Pour les quetes : un point du monde en coordonnees de la ville. */
    public static BlockPos cell(ServerLevel level, int x, int y, int z) {
        return com.emerald.haven.HavenState.get(level.getServer()).origin().offset(x, y, z);
    }

}
