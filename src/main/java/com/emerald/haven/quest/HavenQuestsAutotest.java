package com.emerald.haven.quest;

import com.emerald.haven.Haven;
import com.emerald.haven.HavenAutotest;
import com.emerald.haven.HavenSite;
import com.emerald.haven.HavenState;
import com.emerald.haven.fauna.HavenFauna;
import com.emerald.haven.invasion.HavenInvasion;
import com.emerald.haven.invasion.HavenInvasionData;
import com.emerald.haven.journey.HavenProgress;
import com.emerald.haven.quest.runs.ChestsRun;
import com.emerald.haven.quest.runs.FishingRun;
import com.emerald.haven.quest.runs.PatrolRun;
import com.emerald.haven.quest.runs.RangeRun;
import com.emerald.jak.JakBuilder;
import com.emerald.jak.gun.GunForm;
import com.emerald.jak.gun.GunImpacts;
import com.emerald.jak.gun.MorphGunData;
import com.emerald.jak.gun.MorphGunKeeper;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.HavenShopPayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Le banc d'essai des QUETES DE HAVEN (lot 3, cahier §86), INERTE sans
 * EMERALDWEAPONS_AUTOTEST=quetes.
 *
 * Les mouettes demandent Alex's Mobs, que le serveur des bancs n'a pas :
 *
 *     python tools/dev_mods.py --server alexsmobs aquaculture livingthings
 *     EMERALDWEAPONS_AUTOTEST=quetes ./gradlew runServer
 *     python tools/dev_mods.py --server --clean
 *
 * Il pose les heros et le bateau, parle a Torn avec un cobaye (les cartes du chat sont
 * cliquees pour de vrai, par leur jeton), puis FAIT les quetes : l'epreuve de tir de Tess
 * jusqu'a la medaille, la peche au poids, les coffres engloutis, la chasse aux mouettes au
 * Morph Gun, la patrouille des points d'eco, la tenue du port (et l'invasion qui va avec),
 * les orbes caches, la boutique de Tess (armes, eco illimite, sceaux, provisions) -- puis le
 * nettoyage, qui doit tout rendre a la ville. Rapport dans quetes_autotest.txt, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenQuestsAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "quetes".equalsIgnoreCase(
            java.util.Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    private static final int SETTLE_TICKS = 40;
    private static final int TIMEOUT_TICKS = 20 * 60 * 20;
    private static final int TICKET_DISTANCE = 3;

    private enum Stage { READY, LIVRE, PNJ, CARTES, TIR, PECHE, COFFRES, MOUETTES, PATROUILLE, PORT, ORBES, BOUTIQUE, COURSE, MENAGE, END }

    private static final StringBuilder OUT = new StringBuilder();
    private static Stage stage = Stage.READY;
    private static int waited;
    private static int t;
    private static int passed;
    private static int failed;

    private static final List<ChunkPos> HELD = new ArrayList<>();
    private static ChunkPos forced;
    private static Cobaye cobaye;
    private static Cobaye ami;
    private static int step;
    private static int orbsBefore;
    private static final List<BlockPos> CHESTS = new ArrayList<>();
    private static final List<Mob> GULLS = new ArrayList<>();
    private static int gullKills;
    private static final StringBuilder missed = new StringBuilder();
    private static int targetsSeen;
    private static int targetsBad;

    private HavenQuestsAutotest() {
    }

    /** Un cobaye qui garde ce qu'on lui dit : les cartes du chat se cliquent par leur jeton. */
    static final class Cobaye extends FakePlayer {
        final List<Component> said = new ArrayList<>();
        /**
         * Sa monture, pour la course du JET-Board : un joueur factice de NeoForge ne monte sur rien
         * (FakePlayer.startRiding refuse toujours) ; il dit donc sur quoi il est.
         */
        @Nullable
        net.minecraft.world.entity.Entity mount;

        Cobaye(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void sendSystemMessage(Component message) {
            this.said.add(message);
        }

        @Nullable
        @Override
        public net.minecraft.world.entity.Entity getVehicle() {
            return this.mount != null ? this.mount : super.getVehicle();
        }

        /** Il a recu un message de cette cle (le serveur traduit en anglais : on regarde la cle). */
        boolean saidKey(String key) {
            for (Component line : this.said) {
                if (hasKey(line, key)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean hasKey(Component component, String key) {
            if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
                if (t.getKey().equals(key)) {
                    return true;
                }
                for (Object arg : t.getArgs()) {
                    if (arg instanceof Component inner && hasKey(inner, key)) {
                        return true;
                    }
                }
            }
            for (Component sibling : component.getSiblings()) {
                if (hasKey(sibling, key)) {
                    return true;
                }
            }
            return false;
        }

        /** Il ne tique pas : l'eau se lit sous ses pieds. */
        @Override
        public boolean isInWater() {
            return this.level().getFluidState(this.blockPosition()).is(net.minecraft.tags.FluidTags.WATER);
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            this.said.add(message);
        }

        /**
         * Le jeton de la derniere carte dont le libelle porte ce mot-cle.
         *
         * SUR UN SERVEUR, RIEN N'EST TRADUIT : les textes des mods sont des ressources du
         * client. getString() rend donc la CLE (« ...quete.bouton.accepter ») et non
         * « Accepter » -- c'est la cle qu'on cherche.
         */
        @Nullable
        String card(@Nullable String label) {
            for (int i = this.said.size() - 1; i >= 0; i--) {
                String token = token(this.said.get(i), label);
                if (token != null) {
                    return token;
                }
            }
            return null;
        }

        /** Ce qui a ete dit, en clair, pour le rapport. */
        String heard() {
            StringBuilder out = new StringBuilder();
            for (Component line : this.said) {
                out.append('[').append(line.getString()).append(']');
            }
            return out.length() > 400 ? out.substring(0, 400) : out.toString();
        }

        @Nullable
        private static String token(Component component, @Nullable String label) {
            ClickEvent click = component.getStyle().getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.RUN_COMMAND
                    && click.getValue().startsWith("/carte ")
                    && (label == null || component.getString().contains(label))) {
                return click.getValue().substring("/carte ".length());
            }
            for (Component child : component.getSiblings()) {
                String token = token(child, label);
                if (token != null) {
                    return token;
                }
            }
            return null;
        }
    }

    // ================================================================ tique

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || stage == Stage.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel level = Haven.level(server);
        try {
            switch (stage) {
                case READY -> {
                    waited++;
                    if (waited < SETTLE_TICKS || (HavenSite.busy() && waited < TIMEOUT_TICKS)) {
                        return;
                    }
                    line("autotest des quetes de Haven, " + LocalDateTime.now().withNano(0));
                    if (level == null || !HavenInvasion.cityOpen(server)) {
                        check("ville ouverte (lobby ouvert, ville posee)", false, "niveau " + level);
                        end(server);
                        return;
                    }
                    HavenInvasion.setMode(server, HavenInvasion.Mode.PAISIBLE, null);
                    // la faune ne nait qu'autour d'un joueur : deux ancres, au port et au bassin
                    BlockPos anchor = HavenState.get(server).origin();
                    HavenInvasion.TEST_ANCHORS.add(Vec3.atBottomCenterOf(anchor.offset(560, 66, 258)));
                    HavenInvasion.TEST_ANCHORS.add(Vec3.atBottomCenterOf(anchor.offset(560, 58, 268)));
                    cobaye = subject(level, "cobaye");
                    ami = subject(level, "ami");
                    next(Stage.LIVRE);
                }
                case LIVRE -> {
                    book();
                    hold(server, level);
                    next(Stage.PNJ);
                }
                case PNJ -> npcs(server, level);
                case CARTES -> cards(level);
                case TIR -> range(server, level);
                case PECHE -> fishing(level);
                case COFFRES -> chests(server, level);
                case MOUETTES -> gulls(server, level);
                case PATROUILLE -> patrol(server, level);
                case PORT -> port(server, level);
                case ORBES -> orbs(server, level);
                case BOUTIQUE -> shop(server, level);
                case COURSE -> race(level);
                case MENAGE -> cleanup(server, level);
                case END -> {
                }
            }
            t++;
        } catch (RuntimeException e) {
            check("banc sans exception (etape " + stage + ")", false, e.toString());
            LOGGER.error("autotest quetes : exception", e);
            end(server);
        }
    }

    private static void next(Stage to) {
        stage = to;
        t = -1;
        step = 0;
    }

    /** Un cobaye revenu d'un Defi, les rues reprises, les douze armes, dans la ville. */
    private static Cobaye subject(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("autotest-quetes:" + name).getBytes(StandardCharsets.UTF_8));
        Cobaye fake = new Cobaye(level, new GameProfile(id, "[Quetes]"));
        BlockPos feet = HavenState.get(level.getServer()).origin().offset(Haven.BAR_FRONT_CELL);
        level.getChunkAt(feet);
        fake.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
        HavenProgress.Entry entry = HavenProgress.temporary(id, GunForm.ALL_MASK);
        entry.welcomed = true;
        entry.hq = true;
        entry.departures = 1;
        entry.invaded = true;
        entry.reprise = true;
        HavenQuests.addTestPlayer(fake);
        MorphGunKeeper.addSubject(fake);
        return fake;
    }

    // ================================================================ 1. le livre des quetes

    private static void book() {
        line("--- le livre des quetes");
        check("dix-neuf quetes, six heros, trois chacun et une quatrieme chez Tess (la course du JET-Board)",
                HavenQuestBook.ALL.size() == 19 && HavenHero.values().length == 6
                        && HavenQuestBook.of(HavenHero.TORN).size() == 3 && HavenQuestBook.of(HavenHero.PECHEUR).size() == 3
                        && HavenQuestBook.of(HavenHero.TESS).size() == 4,
                HavenQuestBook.ALL.size() + " quetes, " + HavenHero.values().length + " heros");
        HavenQuest course = HavenQuestBook.byId("course");
        check("la course du JET-Board : apres le tireur d'elite, a la medaille (40, 60, 80 orbes), rejouable, facultative,"
                        + " sur la planche",
                course != null && course.giver() == HavenHero.TESS && HavenQuestBook.before(course) == HavenQuestBook.byId("tir3")
                        && course.medals() && course.rewardFor(1) == 40 && course.rewardFor(3) == 80 && course.repeatable()
                        && course.needsBoard() && !HavenProgress.REQUIRED_QUESTS.contains("course") && !course.invades(),
                course == null ? "absente" : course.rewardFor(1) + "/" + course.rewardFor(2) + "/" + course.rewardFor(3));
        boolean chain = true;
        boolean factories = true;
        StringBuilder detail = new StringBuilder();
        for (HavenQuest quest : HavenQuestBook.ALL) {
            HavenQuest before = HavenQuestBook.before(quest);
            chain &= (quest.order() == 0) == (before == null);
            factories &= quest.factory() != null || quest.id().equals("rues");
            if (quest.reward() < 20 || quest.reward() > 80) {
                detail.append(quest.id()).append(' ').append(quest.reward()).append(" ; ");
            }
        }
        check("chaque quete suit la precedente du meme heros, et sait se derouler (sauf les rues)",
                chain && factories, detail.toString());
        check("les quetes de combat sont celles de Torn, Sig et Samos ; la conduite, le tir et l'eau sont paisibles",
                HavenQuestBook.ALL.stream().filter(HavenQuest::invades).count() == 9
                        && HavenQuestBook.byId("anneaux") != null && !HavenQuestBook.byId("anneaux").invades()
                        && !HavenQuestBook.byId("peche").invades() && !HavenQuestBook.byId("tir1").invades(),
                HavenQuestBook.ALL.stream().filter(HavenQuest::invades).count() + " quetes envahissent");
        HavenQuest tir = HavenQuestBook.byId("tir1");
        check("les epreuves de tir paient a la medaille : bronze 30, argent 45, or 60 orbes",
                tir != null && tir.medals() && tir.rewardFor(1) == 30 && tir.rewardFor(2) == 45 && tir.rewardFor(3) == 60,
                tir == null ? "absente" : tir.rewardFor(1) + "/" + tir.rewardFor(2) + "/" + tir.rewardFor(3));
        check("la maitrise demande les dix-sept quetes des heros, sans le contrat qui se refait",
                HavenProgress.REQUIRED_QUESTS.size() == 17 && !HavenProgress.REQUIRED_QUESTS.contains("port")
                        && HavenQuestBook.byId("port") != null && HavenQuestBook.byId("port").repeatable(),
                HavenProgress.REQUIRED_QUESTS.size() + " quetes demandees");
    }

    private static void hold(MinecraftServer server, ServerLevel level) {
        BlockPos origin = HavenState.get(server).origin();
        List<BlockPos> cells = new ArrayList<>(List.of(
                new BlockPos(334, 69, 166), new BlockPos(778, 66, 101), new BlockPos(152, 66, 298),
                new BlockPos(628, 62, 118), new BlockPos(477, 123, 593), new BlockPos(560, 58, 268),
                new BlockPos(560, 66, 258)));
        for (BlockPos cell : cells) {
            ChunkPos chunk = new ChunkPos(origin.offset(cell));
            if (!HELD.contains(chunk)) {
                level.getChunkSource().addRegionTicket(JakBuilder.TICKET, chunk, TICKET_DISTANCE, chunk);
                HELD.add(chunk);
            }
        }
        // UN TRONCON FORCE AU PORT : sans joueur, le niveau ne fait pas tiquer ses entites, et
        // les mouettes abattues restent des corps que la ville compte encore (lecon du banc).
        forced = new ChunkPos(origin.offset(560, 66, 258));
        level.setChunkForced(forced.x, forced.z, true);
        line("tickets tenus autour des " + HELD.size() + " places des heros et du port, troncon "
                + forced + " force");
    }

    // ================================================================ 2. les heros et le bateau

    private static void npcs(MinecraftServer server, ServerLevel level) {
        if (t == 0) {
            line("--- les heros, le bateau");
        }
        if (t < 120) {
            return;
        }
        check("les heros sont voulus (un joueur revenu d'un Defi) et poses", HavenNpcs.present(), "");
        StringBuilder where = new StringBuilder();
        boolean all = true;
        boolean named = true;
        for (HavenHero hero : HavenHero.values()) {
            HavenNpcEntity npc = HavenNpcs.loaded(level, hero);
            Vec3 spot = HavenNpcs.spot(hero);
            all &= npc != null && spot != null && npc.position().distanceTo(spot) < 2.0;
            named &= npc != null && npc.hero() == hero && npc.isCustomNameVisible();
            where.append(hero.id).append(' ').append(npc == null ? "absent"
                    : String.format(Locale.ROOT, "%.0f %.0f %.0f", npc.getX(), npc.getY(), npc.getZ())).append(" ; ");
        }
        check("les six heros sont a leur place, nommes au-dessus de la tete", all && named, where.toString());
        HavenNpcEntity torn = HavenNpcs.loaded(level, HavenHero.TORN);
        boolean hurt = torn != null && torn.hurt(level.damageSources().playerAttack(cobaye), 10.0F);
        check("un heros ne se blesse pas, ne se pousse pas, ne se sauvegarde pas",
                torn != null && !hurt && !torn.isPushable() && !torn.shouldBeSaved(), "");
        BlockPos deck = HavenBoat.deck(level);
        boolean built = deck != null && level.getBlockState(deck.below()).is(Blocks.DARK_OAK_PLANKS);
        HavenNpcEntity fisher = HavenNpcs.loaded(level, HavenHero.PECHEUR);
        check("le bateau du Pecheur est amarre au pied de l'escalier, et le Pecheur est sur le pont",
                built && fisher != null && deck != null
                        && fisher.position().distanceTo(Vec3.atBottomCenterOf(deck)) < 3.0,
                deck == null ? "pas de place" : deck.toShortString() + ", pont " + level.getBlockState(deck.below()));
        next(Stage.CARTES);
    }

    // ================================================================ 3. les cartes du chat

    private static void cards(ServerLevel level) {
        line("--- la carte de dialogue, les jetons");
        cobaye.said.clear();
        HavenQuests.talk(cobaye, HavenHero.SIG);
        String accept = cobaye.card(null);
        check("parler a Sig donne une carte cliquable (jeton /carte)", accept != null,
                cobaye.said.size() + " lignes dites : " + cobaye.heard());
        boolean started = accept != null && HavenCards.useForTest(cobaye, accept);
        QuestRun run = HavenQuests.runOf(cobaye.getUUID());
        check("le jeton lance la premiere quete de Sig : la chasse", started && run != null
                        && run.quest.id().equals("chasse"), run == null ? "aucune quete" : run.quest.id());
        check("une quete de combat envahit la ville, et le bouton du QG ne rend pas la paix",
                HavenInvasion.mode(level.getServer()) == HavenInvasion.Mode.INVASION && HavenQuests.combatRunning(),
                String.valueOf(HavenInvasion.mode(level.getServer())));
        check("le jeton ne sert qu'une fois", accept == null || !HavenCards.useForTest(cobaye, accept), "");
        HavenQuests.Bar bar = HavenQuests.bar(cobaye);
        check("la barre d'objectif suit la quete", bar != null && bar.text().getString().contains("Sig"),
                bar == null ? "pas de barre" : bar.text().getString());
        // une deuxieme quete tant que la premiere court : refusee
        HavenQuest patrol = HavenQuestBook.byId("patrouille");
        HavenQuests.accept(cobaye, patrol);
        check("une seule quete a la fois par joueur",
                HavenQuests.runOf(cobaye.getUUID()) == run, String.valueOf(HavenQuests.runOf(cobaye.getUUID())));
        // l'ami rejoint, puis tout le monde abandonne
        HavenQuests.join(ami, HavenQuestBook.byId("chasse"));
        check("un autre joueur rejoint l'equipe de la quete en cours",
                run != null && run.member(ami.getUUID()) && run.members().size() == 2,
                run == null ? "" : run.members().size() + " membres");
        HavenQuests.abandon(ami);
        HavenQuests.abandon(cobaye);
        next(Stage.TIR);
    }

    // ================================================================ 4. l'epreuve de tir

    private static void range(MinecraftServer server, ServerLevel level) {
        Vec3 tess = HavenNpcs.spot(HavenHero.TESS);
        if (t == 0) {
            line("--- l'epreuve de tir de Tess");
            check("la quete abandonnee rend la paix a la ville",
                    HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE && !HavenQuests.combatRunning(),
                    String.valueOf(HavenInvasion.mode(server)));
            if (tess == null) {
                check("Tess est a sa place", false, "");
                next(Stage.PECHE);
                return;
            }
            Vec3 stand = free(level, tess);
            cobaye.moveTo(stand.x, stand.y, stand.z, 180.0F, 0.0F);
            line("le tireur se place en " + String.format(Locale.ROOT, "%.1f %.1f %.1f", stand.x, stand.y, stand.z));
            orbsBefore = HavenProgress.orbs(cobaye.getUUID());
            HavenQuests.accept(cobaye, HavenQuestBook.byId("tir1"));
            check("l'epreuve de tir commence", HavenQuests.runOf(cobaye.getUUID()) instanceof RangeRun, "");
            return;
        }
        QuestRun run = HavenQuests.runOf(cobaye.getUUID());
        if (!(run instanceof RangeRun range)) {
            // finie : la medaille et le gain
            int medal = HavenProgress.medal(cobaye.getUUID(), "tir1");
            int gain = HavenProgress.orbs(cobaye.getUUID()) - orbsBefore;
            check("les cibles touchees donnent une medaille et paient (or 60 orbes)", medal >= 1 && gain >= 30,
                    "medaille " + medal + ", " + gain + " orbes, " + targetsSeen + " cibles vues");
            check("les cibles sont dans le stand, en vue du tireur, trois au plus a la fois", targetsBad == 0,
                    targetsBad + " cibles hors des regles");
            check("la quete faite est notee", HavenProgress.done(cobaye.getUUID(), "tir1"), "");
            next(Stage.PECHE);
            return;
        }
        // on tire sur tout ce qui se dresse, sauf les civils
        List<? extends HavenTargetEntity> targets = level.getEntities(EntityTypeTest.forClass(HavenTargetEntity.class),
                target -> target.getTags().contains(range.tag()) && !target.isRemoved());
        if (targets.size() > 3) {
            targetsBad++;
        }
        for (HavenTargetEntity target : targets) {
            double distance = target.position().distanceTo(cobaye.position());
            if (distance < 5.0 || distance > 20.0) {
                targetsBad++;
            }
            targetsSeen++;
            if (!target.civilian()) {
                GunImpacts.hurt(cobaye, null, target, 10.0F);
            } else if (t % 40 == 0) {
                // un civil de temps en temps : la penalite doit se voir
                int before = range.scoreForTest();
                GunImpacts.hurt(cobaye, null, target, 10.0F);
                if (range.scoreForTest() > before) {
                    targetsBad++;
                }
            }
        }
        if (t > 20 * 100) {
            check("l'epreuve de tir se termine d'elle-meme", false, "toujours en cours apres 100 s");
            HavenQuests.abandon(cobaye);
            next(Stage.PECHE);
        }
    }

    /** Un sol libre a deux ou trois pas d'un point : la ou se tiendrait un joueur. */
    private static Vec3 free(ServerLevel level, Vec3 from) {
        BlockPos near = BlockPos.containing(from);
        for (int r = 2; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos at = near.offset(dx, dy, dz);
                        if (com.emerald.haven.HavenArrival.standable(level, at)
                                && level.getBlockState(at.above(2)).isAir()) {
                            return Vec3.atBottomCenterOf(at);
                        }
                    }
                }
            }
        }
        return from;
    }

    // ================================================================ 5. la peche

    private static void fishing(ServerLevel level) {
        if (t == 0) {
            line("--- la peche du Pecheur");
            double cod = FishingRun.weigh(new ItemStack(Items.COD), 0.5);
            double junk = FishingRun.weigh(new ItemStack(Items.LEATHER_BOOTS), 0.5);
            double salmon = FishingRun.weigh(new ItemStack(Items.SALMON), 0.99);
            check("chaque espece a son poids : la morue quelques livres, le saumon davantage, les bottes rien",
                    cod >= 5.0 && cod <= 16.0 && salmon > cod && salmon <= 24.0 && junk == 0.0,
                    String.format(Locale.ROOT, "morue %.1f, saumon %.1f, bottes %.1f", cod, salmon, junk));
            orbsBefore = HavenProgress.orbs(cobaye.getUUID());
            HavenQuests.accept(cobaye, HavenQuestBook.byId("peche"));
            return;
        }
        QuestRun run = HavenQuests.runOf(cobaye.getUUID());
        if (t == 25) {
            boolean rod = false;
            for (ItemStack stack : cobaye.getInventory().items) {
                CustomData data = stack.get(DataComponents.CUSTOM_DATA);
                rod |= stack.is(Items.FISHING_ROD) && data != null && data.copyTag().getBoolean(FishingRun.ROD_MARK);
            }
            check("le Pecheur prete sa canne", rod, "");
        }
        if (run instanceof FishingRun fishing && t >= 30 && t % 2 == 0) {
            fishing.onFish(cobaye, List.of(new ItemStack(Items.COD)));
        }
        if (run == null) {
            int gain = HavenProgress.orbs(cobaye.getUUID()) - orbsBefore;
            check("deux cents livres de poisson paient la quete (50 orbes)", gain == 50
                            && HavenProgress.done(cobaye.getUUID(), "peche"), gain + " orbes");
            next(Stage.COFFRES);
        } else if (t > 20 * 60) {
            check("la peche finit en moins d'une minute de prises", false, "toujours en cours");
            HavenQuests.abandon(cobaye);
            next(Stage.COFFRES);
        }
    }

    // ================================================================ 6. les coffres engloutis

    private static void chests(MinecraftServer server, ServerLevel level) {
        if (t == 0) {
            line("--- les coffres engloutis");
            orbsBefore = HavenProgress.orbs(cobaye.getUUID());
            HavenQuests.accept(cobaye, HavenQuestBook.byId("coffres"));
            QuestRun run = HavenQuests.runOf(cobaye.getUUID());
            if (run instanceof ChestsRun chests) {
                for (BlockPos column : chests.columnsForTest(server)) {
                    ChunkPos chunk = new ChunkPos(column);
                    if (!HELD.contains(chunk)) {
                        level.getChunkSource().addRegionTicket(JakBuilder.TICKET, chunk, 2, chunk);
                        HELD.add(chunk);
                    }
                }
            }
            return;
        }
        QuestRun run = HavenQuests.runOf(cobaye.getUUID());
        if (!(run instanceof ChestsRun chests)) {
            if (t <= 90) {
                check("les coffres engloutis : la quete tient jusqu'au bout", false, "quete perdue a la tique " + t);
                next(Stage.MOUETTES);
                return;
            }
            closeChests(level);
            return;
        }
        if (t == 60) {
            CHESTS.clear();
            CHESTS.addAll(chests.chestsForTest());
            boolean sunk = !CHESTS.isEmpty();
            for (BlockPos pos : CHESTS) {
                sunk &= level.getBlockState(pos).is(Blocks.CHEST)
                        && level.getBlockState(pos.above()).getFluidState().isSource()
                        && !level.getBlockState(pos.below()).isAir();
            }
            check("les coffres sont poses au fond de l'eau, gorges d'eau, sur un sol franc",
                    sunk && CHESTS.size() >= 4, CHESTS.size() + " coffres poses");
            // le souffle du Pecheur : dans l'eau, la respiration
            if (!CHESTS.isEmpty()) {
                BlockPos water = CHESTS.get(0).above();
                cobaye.moveTo(water.getX() + 0.5, water.getY(), water.getZ() + 0.5);
            }
        }
        if (t == 90) {
            check("le Pecheur prete son souffle : respiration aquatique dans l'eau",
                    cobaye.hasEffect(MobEffects.WATER_BREATHING), "");
            for (BlockPos pos : CHESTS) {
                chests.onBlock(cobaye, pos);
            }
        }
        if (t > 95) {
            closeChests(level);
        }
    }

    private static void closeChests(ServerLevel level) {
        int gain = HavenProgress.orbs(cobaye.getUUID()) - orbsBefore;
        boolean water = true;
        for (BlockPos pos : CHESTS) {
            water &= level.getBlockState(pos).is(Blocks.WATER);
        }
        check("ouvrir les coffres paie cinq orbes chacun, la quete cinquante, et rend l'eau",
                gain == 50 + 5 * CHESTS.size() && water && HavenQuests.runOf(cobaye.getUUID()) == null,
                gain + " orbes pour " + CHESTS.size() + " coffres");
        next(Stage.MOUETTES);
    }

    // ================================================================ 7. les mouettes

    private static void gulls(MinecraftServer server, ServerLevel level) {
        if (t == 0) {
            line("--- l'armee de mouettes");
        }
        if (t < 20 * 40 && HavenFauna.loaded(level, HavenFauna.Role.GULL).stream().filter(Mob::isAlive).count() < 10) {
            return;                                   // elles naissent autour des ancres
        }
        if (t <= 20 * 40) {
            GULLS.clear();
            HavenFauna.loaded(level, HavenFauna.Role.GULL).stream().filter(Mob::isAlive).forEach(GULLS::add);
            if (GULLS.isEmpty()) {
                check("des mouettes sur les quais (Alex's Mobs) -- sinon : python tools/dev_mods.py --server alexsmobs",
                        false, "aucune mouette chargee");
                next(Stage.PATROUILLE);
                return;
            }
            check("hors de la quete, les armes du Morph Gun ne visent aucune mouette",
                    GULLS.stream().noneMatch(GunImpacts::isTarget), GULLS.size() + " mouettes chargees");
            HavenQuests.accept(cobaye, HavenQuestBook.byId("mouettes"));
            long visables = GULLS.stream().filter(GunImpacts::isTarget).count();
            Mob gull = GULLS.get(0);
            check("pendant la quete, elles deviennent des cibles", visables == GULLS.size(),
                    visables + " visables sur " + GULLS.size() + " ; la premiere : chasse "
                            + HavenQuests.gullHunt() + ", role " + HavenFauna.role(gull) + ", vivante "
                            + gull.isAlive() + ", retiree " + gull.isRemoved() + ", invulnerable "
                            + gull.isInvulnerable() + ", " + gull.getType().getDescriptionId());
            orbsBefore = HavenProgress.orbs(cobaye.getUUID());
            gullKills = 0;
            t = 20 * 40 + 1;
            return;
        }
        QuestRun run = HavenQuests.runOf(cobaye.getUUID());
        if (run == null) {
            int gain = HavenProgress.orbs(cobaye.getUUID()) - orbsBefore;
            check("dix mouettes abattues au Morph Gun paient la quete (40 orbes)",
                    gain == 40 && HavenProgress.done(cobaye.getUUID(), "mouettes"), gain + " orbes");
            next(Stage.PATROUILLE);
            return;
        }
        if (t % 10 == 0) {
            List<Mob> prey = new ArrayList<>(HavenFauna.loaded(level, HavenFauna.Role.GULL));
            prey.addAll(HavenFauna.loaded(level, HavenFauna.Role.ARMY));
            for (Mob gull : prey) {
                if (gull.isAlive() && gullKills < 10) {
                    cobaye.moveTo(gull.getX(), gull.getY(), gull.getZ());
                    boolean hit = GunImpacts.hurt(cobaye, null, gull, 100.0F);
                    if (hit && !gull.isAlive()) {
                        gullKills++;
                    } else if (missed.length() < 200) {
                        missed.append(hit ? "coup sans mort" : "coup refuse").append(" (cible ")
                                .append(GunImpacts.isTarget(gull)).append(", vie ").append((int) gull.getHealth())
                                .append(", invulnerable ").append(gull.isInvulnerable()).append(") ; ");
                    }
                    break;
                }
            }
        }
        if (t > 20 * 130) {
            check("dix mouettes abattues en moins d'une minute et demie", false,
                    gullKills + " mouettes abattues ; " + missed);
            HavenQuests.abandon(cobaye);
            next(Stage.PATROUILLE);
        }
    }

    // ================================================================ 8. la patrouille

    private static void patrol(MinecraftServer server, ServerLevel level) {
        if (t == 0) {
            line("--- la patrouille des points d'eco");
            orbsBefore = HavenProgress.orbs(cobaye.getUUID());
            HavenQuests.accept(cobaye, HavenQuestBook.byId("patrouille"));
            check("la patrouille commence, et la ville est envahie",
                    HavenQuests.runOf(cobaye.getUUID()) instanceof PatrolRun
                            && HavenInvasion.mode(server) == HavenInvasion.Mode.INVASION, "");
            return;
        }
        QuestRun run = HavenQuests.runOf(cobaye.getUUID());
        if (run == null) {
            int gain = HavenProgress.orbs(cobaye.getUUID()) - orbsBefore;
            check("les douze points d'eco visites paient la patrouille (60 orbes), et la paix revient",
                    gain == 60 && HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE, gain + " orbes");
            next(Stage.PORT);
            return;
        }
        if (t % 10 == 0) {
            BlockPos origin = HavenState.get(server).origin();
            List<HavenInvasionData.EcoPoint> points = HavenInvasionData.ecoPoints(server);
            if (step < points.size()) {
                BlockPos at = points.get(step).feetWorld(origin);
                cobaye.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                step++;
            }
        }
        if (t > 20 * 60) {
            check("la patrouille se finit en visitant les points", false, step + " points visites");
            HavenQuests.abandon(cobaye);
            next(Stage.PORT);
        }
    }

    // ================================================================ 9. tenir le port

    private static void port(MinecraftServer server, ServerLevel level) {
        if (t == 0) {
            line("--- tenir le port (le contrat de Torn, qui se refait)");
            HavenProgress.get(cobaye.getUUID()).quests.add("rues");
            HavenProgress.get(cobaye.getUUID()).quests.add("patrouille");
            orbsBefore = HavenProgress.orbs(cobaye.getUUID());
            HavenQuests.accept(cobaye, HavenQuestBook.byId("port"));
            QuestRun run = HavenQuests.runOf(cobaye.getUUID());
            check("le contrat du port commence", run != null && run.quest.id().equals("port"), "");
            return;
        }
        QuestRun run = HavenQuests.runOf(cobaye.getUUID());
        if (run != null && t % 5 == 0) {
            BlockPos zone = HavenState.get(server).origin().offset(560, 66, 258);
            cobaye.moveTo(zone.getX() + 0.5, zone.getY(), zone.getZ() + 0.5);
            if (t % 40 == 0 && !QuestMarkers.shown(cobaye.getUUID()).isEmpty()) {
                step = 1;
            }
        }
        if (run == null) {
            int gain = HavenProgress.orbs(cobaye.getUUID()) - orbsBefore;
            check("tenir le port une minute paie le contrat (25 orbes)", gain == 25, gain + " orbes");
            check("les reperes de quete sont envoyes aux joueurs de l'equipe", step == 1, "");
            next(Stage.ORBES);
            return;
        }
        if (t > 20 * 100) {
            check("le port se tient en une minute", false, "toujours en cours apres 100 s");
            HavenQuests.abandon(cobaye);
            next(Stage.ORBES);
        }
    }

    // ================================================================ 10. les orbes caches

    private static void orbs(MinecraftServer server, ServerLevel level) {
        line("--- les orbes caches");
        List<HavenOrbs.Spot> spots = HavenOrbs.spots(server);
        long rails = spots.stream().filter(s -> "rail".equals(s.place())).count();
        boolean railsLast = spots.stream().allMatch(s -> "rail".equals(s.place()) == (s.index() >= 150));
        check("cent cinquante orbes caches dans la ville, de la rue aux toits, du bassin au large, puis trente le long"
                        + " des rails du JET-Board, apres eux : leur rang est leur numero",
                spots.size() == 180 && HavenOrbs.total(server) == 180 && rails == 30 && railsLast,
                spots.size() + " places, dont " + rails + " sur les rails");
        HavenOrbs.Spot spot = spots.isEmpty() ? null : spots.get(0);
        if (spot == null) {
            next(Stage.BOUTIQUE);
            return;
        }
        Vec3 at = HavenOrbs.position(server, spot);
        HavenOrbEntity orb = com.emerald.init.Jak3Registry.HAVEN_ORB.get().create(level);
        boolean collected = false;
        int before = HavenProgress.orbs(cobaye.getUUID());
        if (orb != null) {
            orb.setIndex(spot.index());
            orb.moveTo(at.x, at.y, at.z);
            level.addFreshEntity(orb);
            check("un orbe ne se montre qu'a qui ne l'a pas pris", orb.broadcastToPlayer(cobaye), "");
            HavenOrbs.collect(level, orb, cobaye);
            collected = HavenProgress.found(cobaye.getUUID(), spot.index());
            check("l'orbe pris ne se montre plus a qui l'a pris, et reste pour les autres",
                    !orb.isRemoved() && !orb.broadcastToPlayer(cobaye) && orb.broadcastToPlayer(ami), "");
        }
        check("ramasser un orbe le note et paie un orbe", collected
                && HavenProgress.orbs(cobaye.getUUID()) == before + 1 && HavenProgress.foundCount(cobaye.getUUID()) == 1,
                HavenProgress.foundCount(cobaye.getUUID()) + " trouves");
        next(Stage.BOUTIQUE);
    }

    // ================================================================ 11. la boutique de Tess

    private static void shop(MinecraftServer server, ServerLevel level) {
        line("--- la boutique de Tess");
        UUID id = cobaye.getUUID();
        Vec3 tess = HavenNpcs.spot(HavenHero.TESS);
        // il n'a aucune arme, et de quoi acheter
        HavenProgress.setForms(id, GunForm.RED_1.bit());
        HavenProgress.addOrbs(id, 2000 - HavenProgress.orbs(id));
        cobaye.moveTo(0.0, 0.0, 0.0);
        HavenShop.buy(cobaye, "arme.blaster");
        check("on n'achete rien loin de Tess", (HavenProgress.forms(id) & GunForm.YELLOW_1.bit()) == 0, "");
        if (tess != null) {
            cobaye.moveTo(tess.x, tess.y, tess.z + 1.0);
        }
        int before = HavenProgress.orbs(id);
        HavenShop.buy(cobaye, "arme.blaster");
        check("le Blaster coute 80 orbes (prix de la famille jaune) et rejoint le Morph Gun",
                (HavenProgress.forms(id) & GunForm.YELLOW_1.bit()) != 0 && HavenProgress.orbs(id) == before - 80,
                (before - HavenProgress.orbs(id)) + " orbes");
        HavenProgress.setForms(id, GunForm.YELLOW_1.bit());
        HavenShop.buy(cobaye, "arme.wave_concussor");
        boolean locked = (HavenProgress.forms(id) & GunForm.RED_2.bit()) == 0;
        HavenProgress.setForms(id, GunForm.YELLOW_1.bit() | GunForm.RED_1.bit());
        HavenShop.buy(cobaye, "arme.wave_concussor");
        check("une arme de rang 2 demande celle de rang 1 : le Deferlonator se ferme sans le Pulverisator, s'ouvre avec",
                locked && (HavenProgress.forms(id) & GunForm.RED_2.bit()) != 0, "");
        before = HavenProgress.orbs(id);
        HavenShop.buy(cobaye, "illimite_yellow");
        check("l'eco jaune illimite coute 200 orbes", HavenShop.unlimited(id, GunForm.Family.YELLOW)
                && HavenProgress.orbs(id) == before - 200, (before - HavenProgress.orbs(id)) + " orbes");
        MorphGunKeeper.ensure(cobaye);
        ItemStack gun = MorphGunKeeper.find(cobaye);
        boolean refilled = false;
        if (gun != null && !gun.isEmpty()) {
            MorphGunData.spend(gun, GunForm.Family.YELLOW, 50);
            MorphGunData low = MorphGunData.of(gun);
            HavenShop.keepFull(cobaye);
            MorphGunData full = MorphGunData.of(gun);
            refilled = low != null && full != null && low.eco(GunForm.Family.YELLOW) < GunForm.Family.YELLOW.capacity
                    && full.eco(GunForm.Family.YELLOW) == GunForm.Family.YELLOW.capacity;
        }
        check("l'eco illimite garde la reserve pleine dans la ville", refilled, "");
        // les sceaux et les bonus du Defi
        HavenShop.buy(cobaye, HavenShop.SEAL_FORGE);
        HavenShop.buy(cobaye, HavenShop.SEAL_FORGE);
        check("un sceau s'achete plusieurs fois et s'accumule",
                HavenProgress.bonus(id, HavenShop.SEAL_FORGE) == 2, String.valueOf(HavenProgress.bonus(id, HavenShop.SEAL_FORGE)));
        boolean used = HavenShop.useSeal(cobaye, HavenShop.SEAL_FORGE);
        check("un sceau qui sert part, et laisse les autres", used && HavenProgress.bonus(id, HavenShop.SEAL_FORGE) == 1, "");
        HavenShop.buy(cobaye, HavenShop.PROVISIONS);
        HavenShop.buy(cobaye, HavenShop.RUNE);
        cobaye.getInventory().clearContent();
        HavenShop.onDeparture(cobaye);
        int food = 0;
        int runes = 0;
        Inventory inventory = cobaye.getInventory();
        for (ItemStack stack : inventory.items) {
            if (stack.is(Items.COOKED_BEEF) || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE)) {
                food += stack.getCount();
            }
            if (stack.is(com.emerald.item.ModItems.RUNE.get())) {
                runes++;
            }
        }
        check("les provisions et la rune achetees sont remises au depart du Defi",
                food == 26 && runes == 1 && HavenProgress.bonus(id, HavenShop.PROVISIONS) == 0,
                food + " vivres, " + runes + " rune");
        int prices = 0;
        for (HavenShopPayload.Article article : HavenShop.catalogForTest(cobaye)) {
            // les armes seules : le JET-Board est dans le meme onglet (cahier §100)
            prices += article.section() == 0 && article.id().startsWith("arme.") ? article.price() : 0;
        }
        check("les onze armes valent environ 1 300 orbes (rouge 40, jaune 80, bleue 120, sombre 200)",
                prices == 1280, prices + " orbes pour les armes");
        next(Stage.COURSE);
    }

    // ================================================================ 12. la course du JET-Board (cahier §100)

    @Nullable
    private static com.emerald.jak.board.JetBoardEntity raceBoard;
    private static boolean raceAgain;

    /**
     * La course de Tess, sans la physique -- le banc des vehicules fait le trace en vraie planche :
     * refusee sans le JET-Board ; puis le cobaye « sur » une planche (il ne peut pas monter, voir
     * Cobaye.mount), pose a chaque tique sur l'anneau que la course attend, jusqu'a l'arrivee. En
     * quelques secondes : l'or, 80 orbes. Refaite : elle se relance (rejouable), et l'or deja pris ne
     * paie plus.
     */
    private static void race(ServerLevel level) {
        UUID id = cobaye.getUUID();
        HavenQuest course = HavenQuestBook.byId("course");
        List<com.emerald.jak.board.JetBoardCourse.Ring> rings = com.emerald.jak.board.JetBoardCourse.rings();
        if (t == 0) {
            line("--- la course du JET-Board de Tess");
            if (course == null || rings.isEmpty()) {
                check("la course du JET-Board et son trace", false, course == null ? "pas de quete" : "trace vide");
                next(Stage.MENAGE);
                return;
            }
            for (String tir : new String[]{"tir1", "tir2", "tir3"}) {
                HavenProgress.completeQuest(id, tir);
                HavenProgress.completeQuest(ami.getUUID(), tir);
            }
            ami.said.clear();
            QuestRun refused = HavenQuests.accept(ami, course);
            check("sans le JET-Board, la course est refusee, et on le dit",
                    refused == null && HavenQuests.runOf(ami.getUUID()) == null
                            && ami.saidKey("game.emeraldweapons.haven.quete.refus.planche"), ami.heard());
            HavenProgress.addBonus(id, com.emerald.jak.board.JetBoard.OWNED, 1);
            orbsBefore = HavenProgress.orbs(id);
            raceAgain = false;
            // une planche qui n'est pas dans le monde : sans joueur dessus, elle s'y rangerait aussitot
            raceBoard = com.emerald.init.Jak3Registry.JET_BOARD.get().create(level);
            cobaye.mount = raceBoard;
            QuestRun run = HavenQuests.accept(cobaye, course);
            check("avec la planche, la course commence (" + rings.size() + " anneaux)",
                    run instanceof com.emerald.haven.quest.runs.JetRaceRun && cobaye.getVehicle() == raceBoard, "");
            return;
        }
        QuestRun run = HavenQuests.runOf(id);
        if (run instanceof com.emerald.haven.quest.runs.JetRaceRun jet && raceBoard != null) {
            // le cobaye sur l'anneau attendu, son torse a son centre
            Vec3 center = rings.get(Math.min(jet.ringsPassed(), rings.size() - 1)).center();
            cobaye.setPos(center.x, center.y - 0.9, center.z);
            if (t > 20 * 60) {
                check("la course s'acheve quand les anneaux sont passes", false,
                        "toujours en cours apres 60 s, anneau " + jet.ringsPassed() + " sur " + rings.size());
                HavenQuests.abandon(cobaye);
                raceDone();
                next(Stage.MENAGE);
            }
            return;
        }
        int medal = HavenProgress.medal(id, "course");
        int gain = HavenProgress.orbs(id) - orbsBefore;
        if (!raceAgain) {
            check("les anneaux passes vite : l'or, qui paie 80 orbes la premiere fois", medal == 3 && gain == 80,
                    "medaille " + medal + ", " + gain + " orbes");
            raceAgain = true;
            orbsBefore = HavenProgress.orbs(id);
            QuestRun again = HavenQuests.accept(cobaye, course);
            check("la course se refait", again instanceof com.emerald.haven.quest.runs.JetRaceRun, "");
            if (again == null) {
                raceDone();
                next(Stage.MENAGE);
            }
            return;
        }
        check("refaite, l'or deja pris ne paie plus", medal == 3 && gain == 0, gain + " orbes");
        int gold = com.emerald.haven.quest.runs.JetRaceRun.GOLD_SECONDS * 20;
        int silver = com.emerald.haven.quest.runs.JetRaceRun.SILVER_SECONDS * 20;
        check("les medailles de la course : l'or jusqu'a " + gold / 20 + " s, l'argent jusqu'a " + silver / 20
                        + " s, le bronze au-dela",
                com.emerald.haven.quest.runs.JetRaceRun.medalFor(gold) == 3
                        && com.emerald.haven.quest.runs.JetRaceRun.medalFor(gold + 1) == 2
                        && com.emerald.haven.quest.runs.JetRaceRun.medalFor(silver + 1) == 1, "");
        raceDone();
        next(Stage.MENAGE);
    }

    private static void raceDone() {
        cobaye.mount = null;
        raceBoard = null;
    }

    // ================================================================ 12. le menage

    private static void cleanup(MinecraftServer server, ServerLevel level) {
        line("--- le menage : la ville rendue telle qu'elle etait");
        HavenQuests.accept(cobaye, HavenQuestBook.byId("coffres"));
        QuestRun run = HavenQuests.runOf(cobaye.getUUID());
        HavenQuests.cancelAll(level, "banc");
        check("tout s'arrete d'un coup, et la paix revient",
                HavenQuests.runs().isEmpty() && HavenQuests.runOf(cobaye.getUUID()) == null
                        && HavenInvasion.mode(server) == HavenInvasion.Mode.PAISIBLE,
                run == null ? "aucune quete a arreter" : "quete " + run.quest.id());
        BlockPos deck = HavenBoat.deck(level);
        HavenNpcs.removeAll(level);
        boolean gone = HavenNpcs.loaded(level, HavenHero.TORN) == null && HavenNpcs.loaded(level, HavenHero.PECHEUR) == null;
        boolean water = deck == null || !level.getBlockState(deck.below()).is(Blocks.DARK_OAK_PLANKS);
        check("les heros et leur bateau s'en vont, et l'eau revient", gone && water,
                deck == null ? "" : String.valueOf(level.getBlockState(deck.below())));
        int targets = level.getEntities(EntityTypeTest.forClass(HavenTargetEntity.class), target -> true).size();
        HavenOrbs.removeAll(level);
        int orbs = level.getEntities(EntityTypeTest.forClass(HavenOrbEntity.class), orb -> true).size();
        check("aucune cible ni aucun orbe ne reste dans la ville", targets == 0 && orbs == 0,
                targets + " cibles, " + orbs + " orbes");
        end(server);
    }

    // ================================================================ rapport

    private static void end(MinecraftServer server) {
        stage = Stage.END;
        ServerLevel level = Haven.level(server);
        if (level != null) {
            HavenQuests.cancelAll(level, "fin du banc");
            HavenNpcs.removeAll(level);
            HavenOrbs.removeAll(level);
            for (ChunkPos pos : HELD) {
                level.getChunkSource().removeRegionTicket(JakBuilder.TICKET, pos, TICKET_DISTANCE, pos);
            }
            if (forced != null) {
                level.setChunkForced(forced.x, forced.z, false);
            }
        }
        HELD.clear();
        HavenInvasion.TEST_ANCHORS.clear();
        if (cobaye != null) {
            HavenQuests.removeTestPlayer(cobaye);
            MorphGunKeeper.removeSubject(cobaye.getUUID());
            HavenProgress.dropTemporary(cobaye.getUUID());
        }
        if (ami != null) {
            HavenQuests.removeTestPlayer(ami);
            MorphGunKeeper.removeSubject(ami.getUUID());
            HavenProgress.dropTemporary(ami.getUUID());
        }
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("quetes_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest quetes : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest quetes : {} OK, {} KO, rapport dans {} ; arret du serveur", passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest quetes : {}", text);
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
