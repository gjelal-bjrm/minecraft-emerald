package com.emerald.game;

import com.emerald.haven.HavenAutotest;
import com.emerald.item.ArcenciumShieldItem;
import com.emerald.item.GearWear;
import com.emerald.item.ModItems;
import com.emerald.item.SkinFeatherItem;
import com.emerald.item.Upgrade;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.specialization.Specialization;
import com.emerald.specialization.WingSkin;
import com.emerald.specialization.WingsFlight;
import com.emerald.weather.AuroreCold;
import com.emerald.weather.BattueHunt;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Le banc d'essai de la partie (cahier §83, retours du 22 sept.), INERTE sans
 * EMERALDWEAPONS_AUTOTEST=partie.
 *
 * Sur le serveur d'essai, SANS LES MODS DU PACK (run-server) : des creatures du jeu et des
 * joueurs factices, pres du point d'apparition.
 *   1. la forge : les chances des trois derniers crans, l'Heure Doree a +5, le metal perdu ;
 *   2. les coffres des sanctuaires : le butin suit l'avancee, et les anciennes tables y renvoient ;
 *   3. les ailes +20 : le vol d'elytre s'ouvre, tient apres la coupure du jeu, se pose au sol,
 *      et reste ferme sous +20 ;
 *   4. le bouclier d'Arcencium : pare des qu'il est leve (le bouclier du jeu non), riposte
 *      une fois par seconde ;
 *   5. le Grand Froid : on gele dehors, pas sous terre ni pres d'un feu de camp ;
 *   6. la Battue : la Proie ne brille que de pres, sa garde ne brille pas, tout s'en va a
 *      la fin, et les lueurs d'avant s'eteignent au rechargement ;
 *   7. les arches (cahier §84) : le voile seul emporte (pas les piliers, ni derriere) ; la
 *      porte doree du village loin des etablis ; une paire de brumes posee puis retiree ;
 *   8. le cycle d'ouverture des meteos (24 sept.) : Aurore, Battue, Heure Doree, Nuit
 *      d'Arcencium, dans cet ordre, sauvegarde avec la partie, puis le tirage au sort ;
 *   9. l'Eclipse (cahier §88) : le verrou des horreurs, trois portails, leur vague, la
 *      fermeture contre des Eclats, l'implosion et la dissolution a la fin. Demande
 *      The Graveyard et Alex's Mobs au serveur des bancs (tools/dev_mods.py --server) ;
 *  10. les bestiaires des meteos (cahier §89) : chaque monstre cite est-il la, hostile, et
 *      jamais une horreur de l'Eclipse ? (avec les mods des bestiaires au serveur des bancs) ;
 *  11. les trois sanctuaires (cahier §90) : trois garnisons sans monstre commun, presentes et
 *      hostiles, quatre especes au moins a chaque palier ; trois matieres qui gardent la forme
 *      des blocs ; les themes tires au sort, un par ancre, sauvegardes avec les paliers de
 *      garnison ; des gardes du bon theme, et coiffes s'ils brulent au soleil (memes mods que le
 *      banc 10) ;
 *  12. l'arene du boss (cahier §91) : le volume de Spargus et ses reperes vont ensemble, le boss
 *      nait sur le sol loin de la lave, les gardes jamais dans une rigole, toute la lave est
 *      tenue (ni air a cote ni dessous), l'on marche de la porte sud au boss sans lave, et les
 *      quatre boss sont des geants (leur combat contre le vrai joueur : l'automate de photos) ;
 *  13. les monstres alignes sur les joueurs (cahier §104) : la reference d'un groupe, les
 *      bornes de l'habillage, le boss final, les boss des autres mods, la Breche, et les
 *      COUPS POUR TUER avant et apres, contre un joueur en +7.
 * Rapport dans partie_autotest.txt, puis arret.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class ArcenciumAutotest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final boolean ENABLED = "partie".equalsIgnoreCase(
            Objects.requireNonNullElse(System.getenv(HavenAutotest.VARIABLE), "").trim());

    private static final int START_DELAY = 60;

    private static int waited;
    private static boolean done;
    private static final StringBuilder OUT = new StringBuilder();
    private static int passed;
    private static int failed;
    private static final List<Entity> SPAWNED = new ArrayList<>();

    private ArcenciumAutotest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || done || ++waited < START_DELAY) {
            return;
        }
        done = true;
        MinecraftServer server = event.getServer();
        line("autotest de la partie (cahier §83), " + LocalDateTime.now().withNano(0));
        try {
            ServerLevel level = server.overworld();
            BlockPos spawn = level.getSharedSpawnPos();
            forge();
            loot(server, level, spawn);
            wings(level, spawn);
            shield(level, spawn);
            skins(level, spawn);
            wear(level, spawn);
            cold(level, spawn);
            battue(level, spawn);
            arches(level, spawn);
            opening(level);
            eclipse(level, spawn);
            bestiary(level);
            sanctuaries(level, spawn);
            arena(server);
            scaling(level, spawn);
        } catch (RuntimeException e) {
            LOGGER.error("autotest partie : exception", e);
            check("deroulement sans exception", false, e.toString());
        } finally {
            for (Entity entity : SPAWNED) {
                if (!entity.isRemoved()) {
                    entity.discard();
                }
            }
            SanctuariesTakenCondition.forcedProgress = null;
            end(server);
        }
    }

    // ================================================================ 1. la forge

    private static void forge() {
        line("--- la forge");
        check("vers +8, +9, +10 : 18, 9 et 4 % (26, 18 et 10 avant) ; les crans d'avant ne bougent pas",
                Upgrade.odds(7, false) == 18 && Upgrade.odds(8, false) == 9 && Upgrade.odds(9, false) == 4
                        && Upgrade.odds(6, false) == 36 && Upgrade.odds(0, false) == 90,
                Upgrade.odds(6, false) + " / " + Upgrade.odds(7, false) + " / " + Upgrade.odds(8, false)
                        + " / " + Upgrade.odds(9, false));
        check("l'Heure Doree : +15 jusqu'a +7, +5 seulement vers +8, +9 et +10",
                Upgrade.odds(6, true) == 51 && Upgrade.odds(7, true) == 23 && Upgrade.odds(8, true) == 14
                        && Upgrade.odds(9, true) == 9,
                Upgrade.odds(6, true) + " / " + Upgrade.odds(7, true) + " / " + Upgrade.odds(8, true)
                        + " / " + Upgrade.odds(9, true));
        check("un echec rend le metal jusqu'a +7, plus vers +8, +9 et +10",
                Upgrade.refunds(0) && Upgrade.refunds(6) && !Upgrade.refunds(7) && !Upgrade.refunds(9),
                "depuis +6 : " + Upgrade.refunds(6) + ", depuis +7 : " + Upgrade.refunds(7));
    }

    // ================================================================ 8. le cycle d'ouverture

    /**
     * « Quand on debute une partie, on commence par l'Aurore, apres la Battue,
     * apres l'Heure Doree, et apres une meteo plus agressive, la Nuit
     * d'Arcencium. » On tire comme la tique le ferait, sans attendre trente
     * minutes de jeu ; puis on lit ce que la partie sauvegarde.
     */
    private static void opening(ServerLevel level) {
        line("--- le cycle d'ouverture des meteos");
        GameState state = GameState.get(level);
        state.restartOpening();
        List<String> drawn = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            com.emerald.weather.Weather w = com.emerald.weather.WeatherManager.drawForAutotest(level);
            drawn.add(w == null ? "rien" : w.id());
        }
        check("les quatre premieres : Aurore, Battue, Heure Doree, Nuit d'Arcencium, dans cet ordre",
                drawn.equals(List.of("aurore", "battue", "heure_doree", "nuit")), String.join(", ", drawn));
        net.minecraft.nbt.CompoundTag saved = state.save(new net.minecraft.nbt.CompoundTag(),
                level.registryAccess());
        check("le pas du cycle est sauvegarde avec la partie : une partie reprise ne rejoue pas l'ouverture",
                state.opening() == 4 && saved.getInt("Opening") == 4,
                "en memoire " + state.opening() + ", sauvegarde " + saved.getInt("Opening"));
        List<com.emerald.weather.Weather> pool = com.emerald.weather.Weather.poolFor(state.phase(level));
        java.util.Set<String> after = new java.util.TreeSet<>();
        boolean inPool = true;
        for (int i = 0; i < 40; i++) {
            com.emerald.weather.Weather w = com.emerald.weather.WeatherManager.drawForAutotest(level);
            after.add(w == null ? "rien" : w.id());
            inPool &= w == null ? pool.isEmpty() : pool.contains(w);
        }
        check("ensuite le tirage reprend dans le vivier de la phase, et le cycle ne se rejoue pas",
                state.opening() == 4 && inPool,
                "phase " + state.phase(level) + " ; tires : " + String.join(", ", after));
        state.restartOpening();                       // le monde d'essai repart propre
    }

    // ================================================================ 9. l'Eclipse

    private static void eclipse(ServerLevel level, BlockPos spawn) {
        line("--- l'Eclipse : verrou, portails, vagues, fermeture, fin");
        com.emerald.weather.Eclipse.clearAll();
        java.util.Optional<net.minecraft.world.entity.EntityType<?>> ghoul =
                net.minecraft.world.entity.EntityType.byString("graveyard:ghoul");
        check("The Graveyard est charge (sinon : python tools/dev_mods.py --server graveyard alexsmobs)",
                ghoul.isPresent(), ghoul.map(Object::toString).orElse("absent"));
        if (ghoul.isEmpty()) {
            return;
        }
        check("la goule porte le tag des horreurs", ghoul.get().is(com.emerald.weather.Eclipse.HORRORS), "-");
        BlockPos probe = spawn.above(2);
        net.minecraft.world.entity.Entity natural = ghoul.get().spawn(level, probe,
                net.minecraft.world.entity.MobSpawnType.NATURAL);
        boolean naturalIn = natural != null && level.getEntity(natural.getUUID()) != null;
        if (natural != null) {
            natural.discard();
        }
        net.minecraft.world.entity.Entity command = ghoul.get().spawn(level, probe,
                net.minecraft.world.entity.MobSpawnType.COMMAND);
        boolean commandIn = command != null && level.getEntity(command.getUUID()) != null;
        if (command != null) {
            command.discard();
        }
        check("hors Eclipse : une goule naturelle est refusee, une goule de commande passe (essais)",
                !naturalIn && commandIn, "naturelle " + naturalIn + ", commande " + commandIn);

        // le terrain autour du point d'apparition, charge : les portails s'y cherchent une place
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                level.getChunk((spawn.getX() >> 4) + cx, (spawn.getZ() >> 4) + cz);
            }
        }
        com.emerald.weather.Eclipse.start();
        BlockPos village = spawn.offset(300, 0, 300);   // loin : il ne gene pas la pose
        int opened = com.emerald.weather.Eclipse.openAround(level, spawn, village, 3);
        List<BlockPos> rifts = com.emerald.weather.Eclipse.rifts();
        boolean blocks = rifts.stream().allMatch(p -> level.getBlockState(p).is(
                com.emerald.block.ModBlocks.ECLIPSE_PORTAL.get()));
        double nearest = rifts.stream().mapToDouble(p -> Math.sqrt(p.distSqr(spawn))).min().orElse(0);
        double farthest = rifts.stream().mapToDouble(p -> Math.sqrt(p.distSqr(spawn))).max().orElse(0);
        // DEUX AU MOINS : le terrain d'essai s'est rempli (l'arene, les autres chantiers), et un
        // relief tres accidente peut aussi, en partie, n'en laisser que deux
        check("deux ou trois portails s'ouvrent, a 26-74 blocs (l'anneau s'elargit si la place manque), leurs blocs poses",
                opened >= 2 && blocks && nearest >= 25 && farthest <= 75,
                opened + " ouverts, de " + Math.round(nearest) + " a " + Math.round(farthest) + " blocs, " + rifts);

        net.minecraft.world.entity.Entity naturalDuring = ghoul.get().spawn(level, probe,
                net.minecraft.world.entity.MobSpawnType.NATURAL);
        boolean naturalDuringIn = naturalDuring != null && level.getEntity(naturalDuring.getUUID()) != null;
        if (naturalDuring != null) {
            naturalDuring.discard();
        }
        check("pendant l'Eclipse aussi, pas d'apparition naturelle : les horreurs ne sortent que des portails",
                !naturalDuringIn, "naturelle " + naturalDuringIn);

        for (int t = 0; t < 60 + 30 * 4 + 5; t++) {
            com.emerald.weather.Eclipse.tickForAutotest(level);
        }
        int emerged = 0;
        boolean allHorrors = true;
        boolean allTagged = true;
        java.util.Set<String> kinds = new java.util.TreeSet<>();
        for (BlockPos p : rifts) {
            for (net.minecraft.world.entity.Entity e : com.emerald.weather.Eclipse.aliveAt(level, p)) {
                emerged++;
                kinds.add(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
                allHorrors &= e.getType().is(com.emerald.weather.Eclipse.HORRORS);
                allTagged &= e.getTags().contains(com.emerald.weather.Eclipse.TAG)
                        && e.getTags().contains(com.emerald.weather.WeatherEffects.TAG_STORM);
            }
        }
        check("la vague : quatre horreurs par portail, toutes du tag, marquees Eclipse et tempete",
                emerged == 4 * rifts.size() && allHorrors && allTagged,
                emerged + " sorties : " + String.join(", ", kinds));

        BlockPos first = rifts.get(0);
        List<String> killed = new ArrayList<>();
        FakePlayer hunter = fake(level, "chasseur", first.above());
        for (net.minecraft.world.entity.Entity e : com.emerald.weather.Eclipse.aliveAt(level, first)) {
            // UN VRAI COUP DE JOUEUR : le spectre de The Graveyard est immunise contre tout ce qui
            // ignore l'armure -- /kill, et meme les degats generiques -- et contre les fleches.
            // A l'epee, il meurt comme un autre.
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().playerAttack(hunter), 100000.0F);
            killed.add(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()
                    + (e.isAlive() ? " (vivant)" : ""));
        }
        com.emerald.weather.Eclipse.tickForAutotest(level);
        line("premier portail : tues " + killed + ", encore vivants " + com.emerald.weather.Eclipse.aliveAt(level, first).size());
        boolean closed = !com.emerald.weather.Eclipse.rifts().contains(first)
                && level.getBlockState(first).isAir();
        int shards = 0;
        for (net.minecraft.world.entity.item.ItemEntity item : level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(first).inflate(4))) {
            if (item.getItem().is(com.emerald.item.ModItems.FATE_SHARD.get())) {
                shards += item.getItem().getCount();
                item.discard();
            }
        }
        // AU MOINS les Eclats du portail : tuees par un joueur, les horreurs lachent aussi le
        // butin des monstres de tempete (un Eclat une fois sur deux)
        check("sa vague morte, le portail se referme et laisse ses Eclats du Destin",
                closed && shards >= com.emerald.weather.Eclipse.SHARDS,
                "referme " + closed + ", Eclats " + shards);

        int left = com.emerald.weather.Eclipse.rifts().size();
        com.emerald.weather.Eclipse.endForAutotest(level);
        long horrorsLeft = 0;
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e.getTags().contains(com.emerald.weather.Eclipse.TAG)) {
                horrorsLeft++;
            }
        }
        boolean gone = rifts.stream().allMatch(p -> level.getBlockState(p).isAir());
        int loose = 0;
        for (BlockPos p : rifts) {
            loose += level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(p).inflate(4),
                    i -> i.getItem().is(com.emerald.item.ModItems.FATE_SHARD.get())).size();
        }
        check("fin de l'Eclipse : les " + left + " portails restants implosent sans rien laisser, les horreurs se dissolvent",
                gone && horrorsLeft == 0 && loose == 0 && !com.emerald.weather.Eclipse.active(),
                "blocs retires " + gone + ", horreurs restantes " + horrorsLeft + ", Eclats au sol " + loose);
    }

    // ============================================================ 10. les bestiaires

    private static void bestiary(ServerLevel level) {
        line("--- les bestiaires des meteos : presents, hostiles, jamais d'horreur");
        List<String> absent = new ArrayList<>();
        List<String> passive = new ArrayList<>();
        List<String> horror = new ArrayList<>();
        int present = 0;
        List<String> ids = Bestiary.allIds();
        for (String id : ids) {
            java.util.Optional<net.minecraft.world.entity.EntityType<?>> type =
                    net.minecraft.world.entity.EntityType.byString(id);
            if (type.isEmpty()) {
                absent.add(id);
                continue;
            }
            present++;
            if (type.get().is(com.emerald.weather.Eclipse.HORRORS)) {
                horror.add(id);
            }
            net.minecraft.world.entity.Entity made = type.get().create(level);
            if (!(made instanceof net.minecraft.world.entity.monster.Enemy)) {
                passive.add(id + (made == null ? " (ne se cree pas)" : " (" + made.getClass().getSimpleName() + ")"));
            }
            if (made != null) {
                made.discard();
            }
        }
        line("presents " + present + " / " + ids.size() + " ; absents : " + (absent.isEmpty() ? "aucun" : String.join(", ", absent)));
        check("aucune horreur de l'Eclipse dans les bestiaires", horror.isEmpty(), String.join(", ", horror));
        check("chaque monstre present est un ennemi (Enemy), aucun marchand ni animal neutre",
                passive.isEmpty(), passive.isEmpty() ? present + " ennemis" : String.join(", ", passive));
        for (com.emerald.weather.Weather w : new com.emerald.weather.Weather[]{com.emerald.weather.Weather.NUIT,
                com.emerald.weather.Weather.METEORES, com.emerald.weather.Weather.DECHIRURE,
                com.emerald.weather.Weather.ORAGE, com.emerald.weather.Weather.BATTUE}) {
            StringBuilder sizes = new StringBuilder();
            for (int tier = 1; tier <= 3; tier++) {
                sizes.append(tier == 1 ? "" : " / ").append(Bestiary.resolve(Bestiary.forWeather(w, tier)).size());
            }
            line(w.id() + " : " + sizes + " monstres par palier");
        }
    }

    // ============================================================ 11. les sanctuaires

    /**
     * « Chaque sanctuaire devrait etre different, au moins au niveau des monstres et au mieux
     * aussi au niveau du visuel. » On lit les trois garnisons, on traduit des blocs dans les
     * trois matieres, et l'on tire les themes d'une partie A PART (celle du monde d'essai
     * n'est pas touchee).
     */
    private static void sanctuaries(ServerLevel level, BlockPos spawn) {
        line("--- les trois sanctuaires : trois garnisons, trois matieres, tires au sort");
        List<String> absent = new ArrayList<>();
        List<String> passive = new ArrayList<>();
        List<String> horror = new ArrayList<>();
        List<String> thin = new ArrayList<>();
        List<String> shared = new ArrayList<>();
        List<String> flat = new ArrayList<>();
        int cited = 0;
        for (int tier = 1; tier <= 3; tier++) {
            Map<String, String> owner = new HashMap<>();
            for (SanctuaryTheme theme : SanctuaryTheme.values()) {
                int resolved = 0;
                for (String id : theme.monsters(tier)) {
                    cited++;
                    String before = owner.put(id, theme.id);
                    if (before != null && !before.equals(theme.id)) {
                        shared.add(id + " (" + before + " et " + theme.id + ", palier " + tier + ")");
                    }
                    java.util.Optional<EntityType<?>> type = EntityType.byString(id);
                    if (type.isEmpty()) {
                        absent.add(id);
                        continue;
                    }
                    resolved++;
                    if (type.get().is(com.emerald.weather.Eclipse.HORRORS)) {
                        horror.add(id);
                    }
                    Entity made = type.get().create(level);
                    if (!(made instanceof net.minecraft.world.entity.monster.Enemy)) {
                        passive.add(id + (made == null ? " (ne se cree pas)" : " (" + made.getClass().getSimpleName() + ")"));
                    }
                    if (made != null) {
                        made.discard();
                    }
                }
                if (resolved < 4) {
                    thin.add(theme.id + " palier " + tier + " : " + resolved);
                }
            }
        }
        for (SanctuaryTheme theme : SanctuaryTheme.values()) {
            if (new java.util.HashSet<>(theme.monsters(1)).equals(new java.util.HashSet<>(theme.monsters(3)))) {
                flat.add(theme.id);
            }
        }
        line(cited + " monstres cites ; absents : " + (absent.isEmpty() ? "aucun" : String.join(", ", absent)));
        check("chaque monstre des garnisons est charge (sinon : les mods du banc 10 au serveur des bancs)",
                absent.isEmpty(), absent.isEmpty() ? cited + " presents" : String.join(", ", absent));
        check("chaque garde est un ennemi, jamais une horreur de l'Eclipse",
                passive.isEmpty() && horror.isEmpty(), passive.isEmpty() && horror.isEmpty() ? "-"
                        : String.join(", ", passive) + " ; horreurs : " + String.join(", ", horror));
        check("de la variete : quatre especes au moins par theme et par palier", thin.isEmpty(),
                thin.isEmpty() ? "-" : String.join(", ", thin));
        check("aucun monstre commun a deux sanctuaires au meme palier", shared.isEmpty(),
                shared.isEmpty() ? "-" : String.join(", ", shared));
        check("chaque garnison change entre le premier et le troisieme palier", flat.isEmpty(),
                flat.isEmpty() ? "-" : String.join(", ", flat));

        // LES MATIERES : trois briques differentes, la forme gardee, l'ancre intacte
        java.util.Set<net.minecraft.world.level.block.Block> bricks = new java.util.HashSet<>();
        List<String> shape = new ArrayList<>();
        net.minecraft.world.level.block.state.BlockState stair = com.emerald.block.ModBlocks.GANGUE_BRICK_STAIRS.get()
                .defaultBlockState()
                .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.EAST)
                .setValue(net.minecraft.world.level.block.StairBlock.HALF,
                        net.minecraft.world.level.block.state.properties.Half.TOP);
        net.minecraft.world.level.block.state.BlockState slab = com.emerald.block.ModBlocks.GANGUE_BRICK_SLAB.get()
                .defaultBlockState().setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                        net.minecraft.world.level.block.state.properties.SlabType.TOP);
        net.minecraft.world.level.block.state.BlockState anchor = com.emerald.block.ModBlocks.PRISMATIC_ANCHOR.get()
                .defaultBlockState();
        for (SanctuaryTheme theme : SanctuaryTheme.values()) {
            bricks.add(theme.apply(com.emerald.block.ModBlocks.GANGUE_BRICKS.get().defaultBlockState()).getBlock());
            net.minecraft.world.level.block.state.BlockState s = theme.apply(stair);
            if (!(s.getBlock() instanceof net.minecraft.world.level.block.StairBlock)
                    || s.getValue(net.minecraft.world.level.block.StairBlock.FACING) != net.minecraft.core.Direction.EAST
                    || s.getValue(net.minecraft.world.level.block.StairBlock.HALF)
                    != net.minecraft.world.level.block.state.properties.Half.TOP
                    || s.getBlock() == stair.getBlock()) {
                shape.add(theme.id + " escalier -> " + s);
            }
            net.minecraft.world.level.block.state.BlockState h = theme.apply(slab);
            if (!(h.getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
                    || h.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)
                    != net.minecraft.world.level.block.state.properties.SlabType.TOP) {
                shape.add(theme.id + " dalle -> " + h);
            }
            if (!(theme.apply(com.emerald.block.ModBlocks.GANGUE_BRICK_WALL.get().defaultBlockState()).getBlock()
                    instanceof net.minecraft.world.level.block.WallBlock)) {
                shape.add(theme.id + " muret");
            }
            if (theme.apply(anchor) != anchor) {
                shape.add(theme.id + " touche a l'ancre");
            }
        }
        bricks.remove(com.emerald.block.ModBlocks.GANGUE_BRICKS.get());
        line("briques de la muraille : " + bricks.stream().map(b -> BuiltInRegistries.BLOCK.getKey(b).getPath())
                .sorted().toList());
        check("trois matieres : la brique de gangue devient trois blocs differents", bricks.size() == 3,
                bricks.size() + " blocs");
        check("la traduction garde la forme (escalier tourne et haut, dalle haute, muret) et laisse l'ancre",
                shape.isEmpty(), shape.isEmpty() ? "-" : String.join(" ; ", shape));
        boolean pyramid = SanctuaryTheme.SABLES.apply(Blocks.SANDSTONE.defaultBlockState()).is(Blocks.SANDSTONE)
                && !SanctuaryTheme.GIVRE.apply(Blocks.SANDSTONE.defaultBlockState()).is(Blocks.SANDSTONE)
                && !SanctuaryTheme.BRAISES.apply(Blocks.SANDSTONE.defaultBlockState()).is(Blocks.SANDSTONE);
        check("la pyramide garde son gres aux Sables, le quitte au Givre et aux Braises", pyramid, "-");

        // LE TIRAGE, sur une partie a part
        GameState trial = new GameState();
        List<BlockPos> ring = List.of(spawn.offset(450, 0, 0), spawn.offset(-225, 0, 390), spawn.offset(-225, 0, -390));
        trial.setAnchors(ring);
        trial.assignThemes(level.random);
        java.util.Set<SanctuaryTheme> drawn = java.util.EnumSet.noneOf(SanctuaryTheme.class);
        for (int i = 0; i < 3; i++) {
            drawn.add(trial.themeAt(i));
        }
        check("trois ancres, trois themes", drawn.size() == 3, drawn.toString());
        SanctuaryTheme second = trial.themeAt(1);
        List<BlockPos> raised = new ArrayList<>(ring);
        raised.set(1, ring.get(1).above(42));
        trial.setAnchors(raised);                     // l'ancre monte coiffer sa pyramide
        trial.setGarrisonRank(2, 3);
        GameState back = GameState.load(trial.save(new net.minecraft.nbt.CompoundTag(), level.registryAccess()),
                level.registryAccess());
        check("l'ancre montee au faite garde son theme ; themes et paliers de garnison sauvegardes",
                trial.themeOf(raised.get(1)) == second && back.themeAt(0) == trial.themeAt(0)
                        && back.themeAt(1) == second && back.themeAt(2) == trial.themeAt(2)
                        && back.garrisonRank(2) == 3 && back.garrisonRank(0) == 1,
                "releve : " + back.themeAt(0) + ", " + back.themeAt(1) + ", " + back.themeAt(2)
                        + " ; paliers " + back.garrisonRank(0) + " / " + back.garrisonRank(2));
        int[][] seen = new int[3][3];
        for (int game = 0; game < 90; game++) {
            trial.assignThemes(level.random);
            for (int i = 0; i < 3; i++) {
                seen[i][trial.themeAt(i).ordinal()]++;
            }
        }
        boolean mixed = true;
        StringBuilder grid = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            grid.append(i == 0 ? "" : " | ").append("ancre ").append(i + 1).append(" :");
            for (int t = 0; t < 3; t++) {
                mixed &= seen[i][t] >= 10;
                grid.append(' ').append(seen[i][t]);
            }
        }
        check("tires au sort : sur 90 parties, chaque theme tombe sur chaque ancre", mixed, grid.toString());

        // LES GARDES : du theme demande, et coiffes s'ils brulent au soleil
        BlockPos post = spawn.above(2);
        List<String> strangers = new ArrayList<>();
        List<String> bare = new ArrayList<>();
        int guards = 0;
        for (SanctuaryTheme theme : SanctuaryTheme.values()) {
            for (int i = 0; i < 8; i++) {
                SanctuaryGarrison.postGuard(level, post, 3, theme);
            }
            for (Entity guard : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(post).inflate(6),
                    e -> e.getTags().contains(SanctuaryGarrison.TAG_GUARD))) {
                guards++;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(guard.getType()).toString();
                if (!theme.monsters(1).contains(id)) {
                    strangers.add(theme.id + " : " + id);
                }
                if (guard.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                        && guard instanceof net.minecraft.world.entity.Mob mob
                        && mob.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) {
                    bare.add(id);
                }
                guard.discard();
            }
        }
        check("les gardes postes viennent du premier palier de leur theme", guards >= 18 && strangers.isEmpty(),
                guards + " gardes" + (strangers.isEmpty() ? "" : " ; etrangers : " + String.join(", ", strangers)));
        check("un garde mort-vivant est coiffe : il ne brule pas a midi sur sa tour", bare.isEmpty(),
                bare.isEmpty() ? "-" : String.join(", ", bare));
    }

    // ============================================================ 12. l'arene du boss

    /**
     * « L'arene de Jak 3 avec la lave », a sol praticable (choix du joueur) : on lit le volume
     * que Finale pose, sans le poser -- ou nait le boss, ou vont les gardes, ou coule la lave.
     */
    private static void arena(MinecraftServer server) {
        line("--- l'arene du boss : Spargus, sa lave, ses places");
        com.emerald.jak.JakVolume volume = com.emerald.jak.JakVolume.load(server, Finale.ARENA_VOLUME);
        Finale.ArenaMarks marks = Finale.ArenaMarks.load(server);
        check("le volume de l'arene et ses reperes sont la, du meme sha1",
                volume != null && marks != null && marks.sha1().equals(volume.sha1()),
                (volume == null ? "volume absent" : volume.width() + "x" + volume.height() + "x" + volume.depth())
                        + (marks == null ? ", reperes absents" : ", " + marks.guards().size() + " gardes"));
        if (volume == null || marks == null) {
            return;
        }
        BlockPos c = marks.centre();
        boolean standing = volume.stateAt(c.getX(), c.getY(), c.getZ()).isAir()
                && volume.stateAt(c.getX(), c.getY() + 1, c.getZ()).isAir()
                && volume.stateAt(c.getX(), c.getY() - 1, c.getZ()).is(Blocks.SMOOTH_SANDSTONE);
        int nearest = Integer.MAX_VALUE;
        int lava = 0;
        List<String> leaks = new ArrayList<>();
        for (int y = 0; y < volume.height(); y++) {
            for (int z = 0; z < volume.depth(); z++) {
                for (int x = 0; x < volume.width(); x++) {
                    if (!volume.stateAt(x, y, z).is(Blocks.LAVA)) {
                        continue;
                    }
                    lava++;
                    nearest = Math.min(nearest, Math.max(Math.abs(x - c.getX()), Math.abs(z - c.getZ())));
                    int[][] around = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, -1, 0}};
                    for (int[] d : around) {
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        boolean inside = nx >= 0 && ny >= 0 && nz >= 0 && nx < volume.width() && nz < volume.depth();
                        // sous le sol, l'air du volume devient la terre cuite du socle (Finale) : il tient
                        boolean held = inside && (ny < marks.floor() || !volume.stateAt(nx, ny, nz).isAir());
                        if (!held && leaks.size() < 5) {
                            leaks.add(x + "," + y + "," + z);
                        }
                    }
                }
            }
        }
        check("le boss nait sur le gres du sol, a l'air libre, a plus de dix blocs de toute lave",
                standing && nearest > 10, "centre " + c.toShortString() + ", lave la plus proche a " + nearest);
        check("la lave coule dans ses rigoles et ses fosses : rien ne fuit, ni de cote ni dessous",
                lava >= 400 && leaks.isEmpty(), lava + " blocs de lave" + (leaks.isEmpty() ? "" : " ; fuites en " + leaks));
        List<String> wet = new ArrayList<>();
        for (BlockPos g : marks.guards()) {
            if (!volume.stateAt(g.getX(), g.getY(), g.getZ()).isAir()
                    || !volume.stateAt(g.getX(), g.getY() - 1, g.getZ()).is(Blocks.SMOOTH_SANDSTONE)) {
                wet.add(g.toShortString());
            }
        }
        check("les gardes se postent sur le sol, jamais dans une rigole", !marks.guards().isEmpty() && wet.isEmpty(),
                marks.guards().size() + " places" + (wet.isEmpty() ? "" : " ; mauvaises : " + wet));
        boolean path = true;
        for (int z = c.getZ(); z < volume.depth(); z++) {
            if (volume.stateAt(c.getX(), c.getY() - 2, z).is(Blocks.LAVA)
                    || volume.stateAt(c.getX(), c.getY() - 3, z).is(Blocks.LAVA)) {
                path = false;
            }
        }
        check("de la porte sud au boss, on marche sans enjamber de lave (le passage de l'anneau)", path, "-");
        // LES BOSS GEANTS (le joueur, 24 sept.) : chacun agrandi comme en partie, puis mesure
        ServerLevel overworld = server.overworld();
        List<String> sizes = new ArrayList<>();
        boolean giants = true;
        for (String id : Finale.bosses()) {
            java.util.Optional<EntityType<?>> type = EntityType.byString(id);
            Entity made = type.isEmpty() ? null : type.get().create(overworld);
            if (!(made instanceof net.minecraft.world.entity.LivingEntity boss)) {
                giants = false;
                sizes.add(id + " absent");
                continue;
            }
            Finale.giant(boss);
            boss.refreshDimensions();
            giants &= boss.getBbHeight() >= 8.0F && boss.getMaxHealth() >= 600.0F;
            sizes.add(String.format(Locale.ROOT, "%s %.1f x %.1f, %.0f PV", id.substring(id.indexOf(':') + 1),
                    boss.getBbWidth(), boss.getBbHeight(), boss.getMaxHealth()));
            boss.discard();
        }
        check("quatre boss geants : huit blocs de haut au moins (un joueur en fait 1,8), six cents PV au moins",
                giants && sizes.size() == 4, String.join(" ; ", sizes));
    }

    // ============================================================ 13. l'alignement

    /** Un joueur factice equipe : arme, armure, runes, niveau Heros ; les attributs poses a la main. */
    private static FakePlayer armed(ServerLevel level, String name, BlockPos feet,
                                    net.minecraft.world.item.Item weaponItem, int weaponUp, int weaponRank,
                                    net.minecraft.world.item.Item[] armour, int armourUp, int armourRank,
                                    int runeRank, int heroLevel, int[] paths) {
        FakePlayer fake = fake(level, name, feet);
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(name.hashCode());
        net.minecraft.world.item.ItemStack weapon = new net.minecraft.world.item.ItemStack(weaponItem);
        com.emerald.item.Upgrade.set(weapon, weaponUp);
        com.emerald.item.GearRarity.set(weapon, com.emerald.item.GearRarity.values()[weaponRank]);
        if (runeRank > 0) {
            com.emerald.rune.Runes.engrave(weapon, com.emerald.rune.RuneMark.roll(
                    com.emerald.rune.RuneFamily.WEAPON, runeRank, random));
        }
        fake.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, weapon);
        net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET};
        for (int i = 0; i < 4 && armour != null; i++) {
            net.minecraft.world.item.ItemStack piece = new net.minecraft.world.item.ItemStack(armour[i]);
            com.emerald.item.Upgrade.set(piece, armourUp);
            com.emerald.item.GearRarity.set(piece, com.emerald.item.GearRarity.values()[armourRank]);
            if (runeRank > 0 && i > 0) {
                com.emerald.rune.Runes.engrave(piece, com.emerald.rune.RuneMark.roll(
                        com.emerald.rune.RuneFamily.ARMOR, runeRank, random));
            }
            fake.setItemSlot(slots[i], piece);
        }
        net.minecraft.nbt.CompoundTag data = fake.getPersistentData();
        data.putInt("HeroLevel", heroLevel);
        com.emerald.hero.HeroStat[] order = {com.emerald.hero.HeroStat.ATTAQUE, com.emerald.hero.HeroStat.ELEMENT,
                com.emerald.hero.HeroStat.DEFENSE, com.emerald.hero.HeroStat.VITALITE};
        for (int i = 0; i < 4; i++) {
            data.putInt(order[i].tag(), paths[i]);
        }
        equipNow(fake);
        com.emerald.hero.HeroEvents.apply(fake);
        com.emerald.rune.RuneEvents.apply(fake);
        return fake;
    }

    /** Les attributs de l'equipement porte, poses tout de suite (le jeu le fait a la tique suivante). */
    private static void equipNow(net.minecraft.world.entity.LivingEntity entity) {
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            net.minecraft.world.item.ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            stack.forEachModifier(slot, (attribute, modifier) -> {
                net.minecraft.world.entity.ai.attributes.AttributeInstance instance = entity.getAttribute(attribute);
                if (instance != null) {
                    instance.addOrUpdateTransientModifier(modifier);
                }
            });
        }
    }

    /**
     * Combien de coups du joueur pour tuer ce monstre, en moyenne sur « trials » monstres
     * neufs ; et combien de coups du monstre pour tuer le joueur (armure du joueur comprise).
     */
    private static double[] duel(ServerLevel level, FakePlayer player,
                                 java.util.function.Supplier<net.minecraft.world.entity.Mob> make, int trials) {
        double hits = 0;
        double back = 0;
        double mobHealth = 0;
        double mobArmour = 0;
        float strike = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        for (int t = 0; t < trials; t++) {
            net.minecraft.world.entity.Mob mob = make.get();
            equipNow(mob);
            mob.setHealth(mob.getMaxHealth());
            mobHealth += mob.getMaxHealth();
            mobArmour += mob.getArmorValue();
            int n = 0;
            while (mob.getHealth() > 0.0F && n < 200) {
                mob.invulnerableTime = 0;
                mob.hurt(level.damageSources().playerAttack(player), strike);
                n++;
            }
            hits += n;
            float bite = (float) mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            float taken = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(player, bite,
                    level.damageSources().mobAttack(mob), player.getArmorValue(),
                    (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS));
            back += Math.ceil(player.getMaxHealth() / Math.max(0.01F, taken));
            mob.discard();
        }
        return new double[]{hits / trials, back / trials, mobHealth / trials, mobArmour / trials};
    }

    private static void scaling(ServerLevel level, BlockPos spawn) {
        line("--- les monstres alignes sur les joueurs : reference, bornes, boss, Breche, coups pour tuer");
        BlockPos feet = spawn.above(2);
        net.minecraft.world.item.Item[] diamond = {net.minecraft.world.item.Items.DIAMOND_HELMET,
                net.minecraft.world.item.Items.DIAMOND_CHESTPLATE, net.minecraft.world.item.Items.DIAMOND_LEGGINGS,
                net.minecraft.world.item.Items.DIAMOND_BOOTS};
        net.minecraft.world.item.Item[] iron = {net.minecraft.world.item.Items.IRON_HELMET,
                net.minecraft.world.item.Items.IRON_CHESTPLATE, net.minecraft.world.item.Items.IRON_LEGGINGS,
                net.minecraft.world.item.Items.IRON_BOOTS};
        net.minecraft.world.item.Item sword = com.emerald.item.ModItems.EMERALD_SWORD.get();

        // 1. le groupe : le milieu entre le plus faible et le plus fort
        FakePlayer weak = armed(level, "faible", feet, sword, 3, 2, iron, 3, 2, 2, 30, new int[]{8, 4, 8, 8});
        FakePlayer strong = armed(level, "fort", feet, sword, 7, 6, diamond, 7, 6, 6, 70, new int[]{25, 15, 25, 25});
        MobScaling.Reference group = MobScaling.of(List.of(weak, strong));
        check("a deux joueurs, la reference est le milieu : arme +5 rang 4, armure +5 rang 4, runes 4, niveau 50",
                group.weaponUpgrade() == 5 && group.weaponRarity() == 4 && group.armourUpgrade() == 5
                        && group.armourRarity() == 4 && group.runeRank() == 4 && group.heroLevel() == 50,
                String.format(Locale.ROOT, "arme +%.1f rang %.1f, armure +%.1f rang %.1f, runes %.1f, niveau %.1f, armure %.1f pts",
                        group.weaponUpgrade(), group.weaponRarity(), group.armourUpgrade(), group.armourRarity(),
                        group.runeRank(), group.heroLevel(), group.armourPoints()));

        // 2. les bornes : un joueur en +3, rang 2, runes 2, niveau 40
        MobScaling.Reference ref = MobScaling.of(weak);
        ref = ref.with(3, 2, 3, 2, 2, 40);
        int outOfBounds = 0;
        int levelLow = 999;
        int levelHigh = 0;
        int stronger = 0;
        int offensive = 0;
        for (int i = 0; i < 300; i++) {
            net.minecraft.world.entity.monster.Zombie z = EntityType.ZOMBIE.create(level);
            if (z == null) {
                continue;
            }
            MobScaling.scale(z, ref, level.random);
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                net.minecraft.world.item.ItemStack stack = z.getItemBySlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                boolean bad = com.emerald.item.Upgrade.of(stack) > 3
                        || com.emerald.item.GearRarity.of(stack).rank() > 2;
                for (com.emerald.rune.RuneMark mark : com.emerald.rune.Runes.on(stack)) {
                    bad |= mark.rank() < 1 || mark.rank() > 2;
                }
                if (bad) {
                    outOfBounds++;
                }
            }
            int lv = MobScaling.level(z);
            levelLow = Math.min(levelLow, lv);
            levelHigh = Math.max(levelHigh, lv);
            int atk = MobScaling.path(z, com.emerald.hero.HeroStat.ATTAQUE);
            int vit = MobScaling.path(z, com.emerald.hero.HeroStat.VITALITE);
            if (vit > atk + 3) {
                stronger++;
            } else if (atk > vit + 3) {
                offensive++;
            }
            z.discard();
        }
        check("300 zombies sous un joueur +3, rang 2, runes 2, niveau 40 : rien au-dessus, niveau 34 a 40, des costauds et des offensifs",
                outOfBounds == 0 && levelLow >= 34 && levelHigh <= 40 && stronger > 20 && offensive > 20,
                "hors bornes " + outOfBounds + ", niveaux " + levelLow + "-" + levelHigh + ", costauds " + stronger
                        + ", offensifs " + offensive);

        // 3. le boss final
        net.minecraft.world.entity.monster.Zombie boss = EntityType.ZOMBIE.create(level);
        if (boss != null) {
            boss.addTag(Finale.TAG_BOSS);
            MobScaling.scaleNow(boss, level.random);
            boolean noHelmet = boss.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
            boolean gear = true;
            StringBuilder seen = new StringBuilder();
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                net.minecraft.world.item.ItemStack stack = boss.getItemBySlot(slot);
                int up = com.emerald.item.Upgrade.of(stack);
                int rank = com.emerald.item.GearRarity.of(stack).rank();
                int rune = com.emerald.rune.Runes.on(stack).isEmpty() ? 0 : com.emerald.rune.Runes.on(stack).get(0).rank();
                gear &= up >= 8 && up <= 10 && rank == 8 && rune == 8;
                seen.append(slot.getName()).append(" +").append(up).append(" r").append(rank).append(" rune").append(rune).append(" ; ");
            }
            int[] p = {MobScaling.path(boss, com.emerald.hero.HeroStat.ATTAQUE),
                    MobScaling.path(boss, com.emerald.hero.HeroStat.ELEMENT),
                    MobScaling.path(boss, com.emerald.hero.HeroStat.DEFENSE),
                    MobScaling.path(boss, com.emerald.hero.HeroStat.VITALITE)};
            int spread = java.util.Arrays.stream(p).max().getAsInt() - java.util.Arrays.stream(p).min().getAsInt();
            check("le boss final : sans casque, arme et armure +8 a +10, rarete 8, runes 8, niveau 99 a parts egales",
                    noHelmet && gear && MobScaling.level(boss) == 99 && spread <= 1,
                    seen + "niveau " + MobScaling.level(boss) + ", voies " + java.util.Arrays.toString(p));
            boss.discard();
        }

        // 4. un boss d'un autre mod (le Wither porte le tag commun des boss) : le niveau seul
        net.minecraft.world.entity.Mob wither = EntityType.WITHER.create(level);
        if (wither != null) {
            MobScaling.scale(wither, ref, level.random);   // ce que scaleNow ferait, sans la reference du serveur
            wither.getTags().remove(MobScaling.TAG);
            wither.getPersistentData().remove("EmeraldHero");
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                wither.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
            MobScaling.scaleLevelOnly(wither, ref, level.random);
            boolean bare = true;
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                bare &= wither.getItemBySlot(slot).isEmpty();
            }
            check("un boss d'un autre mod (le Wither) est reconnu boss et ne recoit que le niveau",
                    MobScaling.isBoss(wither) && bare && MobScaling.level(wither) >= 34,
                    "boss " + MobScaling.isBoss(wither) + ", nu " + bare + ", niveau " + MobScaling.level(wither));
            wither.discard();
        }

        // 5. la Breche : le sceptre (sans critique) de rang 4, contre un boss et contre un zombie
        FakePlayer mage = armed(level, "breche", feet, com.emerald.item.ModItems.ARCENCIUM_SCEPTER.get(),
                0, 4, null, 0, 0, 0, 1, new int[]{0, 0, 0, 0});
        double expected = com.emerald.item.Breach.chance(mage.getMainHandItem());
        int[] procs = new int[2];
        for (int who = 0; who < 2; who++) {
            net.minecraft.world.entity.monster.Zombie target = EntityType.ZOMBIE.create(level);
            if (target == null) {
                continue;
            }
            if (who == 0) {
                target.addTag(Finale.TAG_BOSS);
                MobGear.dressFinalBoss(target, level.random);
            } else {
                target.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_CHESTPLATE));
            }
            equipNow(target);
            // des points de vie par MODIFICATEUR : une base d'un million ferait du zombie un boss
            var health = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            health.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("emeraldweapons", "banc_pv"),
                    1000000.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            float strike = (float) mage.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) + 6.0F;
            int before = com.emerald.item.Breach.procs;
            for (int i = 0; i < 600; i++) {
                target.setHealth(target.getMaxHealth());
                target.invulnerableTime = 0;
                target.hurt(level.damageSources().playerAttack(mage), strike);
            }
            procs[who] = com.emerald.item.Breach.procs - before;
            target.discard();
        }
        double rate = procs[0] / 6.0;
        check("la Breche : " + String.format(Locale.ROOT, "%.1f", expected) + " % au rang 4 contre le boss, jamais contre un monstre ordinaire",
                Math.abs(rate - expected) < 5.0 && procs[1] == 0,
                String.format(Locale.ROOT, "boss : %.1f %% des coups, zombie : %d coups sur 600", rate, procs[1]));

        // 6. les coups pour tuer, avant et apres, contre un joueur en +7 (epee rang 4, diamant +7 rang 4, runes 2, niveau 40)
        FakePlayer hero = armed(level, "plus7", feet, sword, 7, 4, diamond, 7, 4, 2, 40, new int[]{12, 6, 12, 12});
        MobScaling.Reference mirror = MobScaling.of(hero);
        double stage = 0.4;                             // vers la 35e minute
        double[] zombieBefore = duel(level, hero, () -> {
            net.minecraft.world.entity.monster.Zombie z = EntityType.ZOMBIE.create(level);
            MobGear.equipByStage(z, stage, level.random);
            return z;
        }, 40);
        double[] zombieAfter = duel(level, hero, () -> {
            net.minecraft.world.entity.monster.Zombie z = EntityType.ZOMBIE.create(level);
            MobScaling.scale(z, mirror, level.random);
            return z;
        }, 40);
        double[] vindBefore = duel(level, hero, () -> {
            net.minecraft.world.entity.monster.Vindicator v = EntityType.VINDICATOR.create(level);
            MobGear.equipByStage(v, stage, level.random);
            return v;
        }, 40);
        double[] vindAfter = duel(level, hero, () -> {
            net.minecraft.world.entity.monster.Vindicator v = EntityType.VINDICATOR.create(level);
            MobScaling.scale(v, mirror, level.random);
            return v;
        }, 40);
        line(String.format(Locale.ROOT, "joueur en +7 : %.1f degats par coup, %.1f PV, %.1f d'armure ; reference : arme +%.0f r%.0f, armure +%.1f r%.1f, runes %.0f, niveau %.0f, armure %.1f pts",
                hero.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), hero.getMaxHealth(),
                (double) hero.getArmorValue(), mirror.weaponUpgrade(), mirror.weaponRarity(), mirror.armourUpgrade(),
                mirror.armourRarity(), mirror.runeRank(), mirror.heroLevel(), mirror.armourPoints()));
        line(String.format(Locale.ROOT, "zombie      avant : %.1f coups pour le tuer (%.0f PV, %.1f armure), %.1f coups pour vous tuer", zombieBefore[0], zombieBefore[2], zombieBefore[3], zombieBefore[1]));
        line(String.format(Locale.ROOT, "zombie      apres : %.1f coups pour le tuer (%.0f PV, %.1f armure), %.1f coups pour vous tuer", zombieAfter[0], zombieAfter[2], zombieAfter[3], zombieAfter[1]));
        line(String.format(Locale.ROOT, "vindicateur avant : %.1f coups pour le tuer (%.0f PV, %.1f armure), %.1f coups pour vous tuer", vindBefore[0], vindBefore[2], vindBefore[3], vindBefore[1]));
        line(String.format(Locale.ROOT, "vindicateur apres : %.1f coups pour le tuer (%.0f PV, %.1f armure), %.1f coups pour vous tuer", vindAfter[0], vindAfter[2], vindAfter[3], vindAfter[1]));
        // 7. le vrai chemin : un zombie qui APPARAIT en pleine partie est habille a la tique suivante,
        //    selon les joueurs presents (ici aucun joueur reel : la reference minimale, niveau 1)
        GameState state = GameState.get(level);
        GameState.Status was = state.status();
        if (was == GameState.Status.LOBBY) {
            state.beginPrologue();
        }
        net.minecraft.world.entity.Entity walker = EntityType.ZOMBIE.spawn(level, feet,
                net.minecraft.world.entity.MobSpawnType.NATURAL);
        boolean tagged = false;
        boolean helmet = false;
        if (walker != null) {
            MobScaling.flushForAutotest(level);
            tagged = walker.getTags().contains(MobScaling.TAG) && MobScaling.level(
                    (net.minecraft.world.entity.LivingEntity) walker) >= 1;
            helmet = !((net.minecraft.world.entity.LivingEntity) walker)
                    .getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty();
            walker.discard();
        }
        if (was == GameState.Status.LOBBY) {
            state.returnToLobby();
        }
        check("un zombie qui apparait en pleine partie est habille a la tique suivante (marque, niveau, casque)",
                tagged && helmet, "marque et niveau " + tagged + ", casque " + helmet);

        check("apres l'alignement, un zombie ou un vindicateur de votre niveau encaisse 3 a 5 de vos coups, et ils frappent plus fort",
                zombieAfter[0] >= 3.0 && zombieAfter[0] <= 5.0 && vindAfter[0] >= 3.0 && vindAfter[0] <= 5.5
                        && zombieAfter[1] <= zombieBefore[1],
                String.format(Locale.ROOT, "zombie %.1f -> %.1f, vindicateur %.1f -> %.1f", zombieBefore[0], zombieAfter[0],
                        vindBefore[0], vindAfter[0]));
    }

    // ================================================================ 2. les coffres

    private static void loot(MinecraftServer server, ServerLevel level, BlockPos at) {
        line("--- les coffres des sanctuaires suivent l'avancee");
        LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "chests/sanctuary")));
        LootTable old = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "chests/sanctuary_tier3")));
        int chests = 400;
        Map<String, Double> first = roll(level, at, table, 0, chests);
        Map<String, Double> second = roll(level, at, table, 1, chests);
        Map<String, Double> third = roll(level, at, table, 2, chests);
        Map<String, Double> oldFirst = roll(level, at, old, 0, chests);
        line("par coffre, en moyenne : 1er " + brief(first) + " ; 2e " + brief(second) + " ; 3e " + brief(third));
        check("le premier sanctuaire visite (aucun pris) : peu de fer, d'or, de diamant et d'Eclats ; ni netherite ni Arcencium",
                get(first, "minecraft:iron_ingot") < 1.5 && get(first, "minecraft:gold_ingot") < 0.6
                        && get(first, "minecraft:diamond") < 0.15 && get(first, "emeraldweapons:fate_shard") < 2.6
                        && get(first, "minecraft:iron_block") == 0.0 && get(first, "minecraft:netherite_ingot") == 0.0
                        && get(first, "emeraldweapons:arcencium_ingot") == 0.0,
                brief(first));
        check("le deuxieme (un pris) : l'ancien palier 2, le diamant pour de bon",
                get(second, "minecraft:diamond") > 2.0 && get(second, "emeraldweapons:fate_shard") > 5.0
                        && get(second, "minecraft:netherite_ingot") == 0.0,
                brief(second));
        check("le troisieme (deux pris) : le plus riche, netherite et Arcencium",
                get(third, "minecraft:netherite_ingot") > 0.5 && get(third, "emeraldweapons:arcencium_ingot") > 1.0
                        && get(third, "minecraft:diamond") > get(first, "minecraft:diamond"),
                brief(third));
        check("un coffre deja pose avec l'ancienne table du palier 3 suit l'avancee lui aussi (le premier : modeste)",
                get(oldFirst, "emeraldweapons:fate_shard") < 2.6 && get(oldFirst, "minecraft:netherite_ingot") == 0.0,
                brief(oldFirst));
    }

    private static Map<String, Double> roll(ServerLevel level, BlockPos at, LootTable table, int progress, int chests) {
        SanctuariesTakenCondition.forcedProgress = progress;
        Map<String, Double> sum = new HashMap<>();
        try {
            for (int i = 0; i < chests; i++) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(at))
                        .create(LootContextParamSets.CHEST);
                for (ItemStack stack : table.getRandomItems(params)) {
                    sum.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), (double) stack.getCount(), Double::sum);
                }
            }
        } finally {
            SanctuariesTakenCondition.forcedProgress = null;
        }
        sum.replaceAll((k, v) -> v / chests);
        return sum;
    }

    private static double get(Map<String, Double> map, String id) {
        return map.getOrDefault(id, 0.0);
    }

    private static String brief(Map<String, Double> m) {
        return String.format(Locale.ROOT, "fer %.2f, bloc de fer %.2f, or %.2f, diamant %.2f, netherite %.2f,"
                        + " Arcencium %.2f, Eclats %.2f",
                get(m, "minecraft:iron_ingot"), get(m, "minecraft:iron_block"), get(m, "minecraft:gold_ingot"),
                get(m, "minecraft:diamond"), get(m, "minecraft:netherite_ingot"),
                get(m, "emeraldweapons:arcencium_ingot"), get(m, "emeraldweapons:fate_shard"));
    }

    // ================================================================ 3. les ailes +20

    private static void wings(ServerLevel level, BlockPos spawn) {
        line("--- les ailes +20 : le vol d'elytre");
        BlockPos ground = surface(level, spawn.getX() + 6, spawn.getZ() + 6);
        FakePlayer fake = fake(level, "ailes", ground.above(12));
        try {
            fake.setOnGround(false);
            Specialization.set(fake, 19, null);
            WingsFlight.start(fake);
            boolean refused = !fake.isFallFlying() && !WingsFlight.flying(fake);
            Specialization.set(fake, Specialization.MAX, null);
            WingsFlight.start(fake);
            boolean opened = fake.isFallFlying() && WingsFlight.flying(fake);
            fake.stopFallFlying();                      // ce que fait le jeu a chaque tique, sans elytre
            WingsFlight.onPlayerTick(new PlayerTickEvent.Post(fake));
            boolean held = fake.isFallFlying();
            fake.setOnGround(true);
            fake.stopFallFlying();
            WingsFlight.onPlayerTick(new PlayerTickEvent.Post(fake));
            boolean landed = !fake.isFallFlying() && !WingsFlight.flying(fake);
            check("a +19 : refuse ; a +20 : le vol s'ouvre, tient apres la coupure du jeu, et se pose au sol",
                    refused && opened && held && landed,
                    "refuse " + refused + ", ouvert " + opened + ", tenu " + held + ", pose " + landed);
        } finally {
            Specialization.set(fake, 0, null);
        }
    }

    // ================================================================ 4. le bouclier

    private static void shield(ServerLevel level, BlockPos spawn) {
        line("--- le bouclier d'Arcencium");
        BlockPos at = surface(level, spawn.getX() - 6, spawn.getZ() - 6);
        Zombie blocker = zombie(level, at, 0.0F);
        Zombie wooden = zombie(level, at.east(3), 0.0F);
        Zombie attacker = zombie(level, at.south(2), 180.0F);
        if (blocker == null || wooden == null || attacker == null) {
            check("creatures d'essai posees", false, "zombie impossible");
            return;
        }
        blocker.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.ARCENCIUM_SHIELD.get()));
        wooden.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        blocker.startUsingItem(InteractionHand.OFF_HAND);
        wooden.startUsingItem(InteractionHand.OFF_HAND);
        check("leve a l'instant : le bouclier d'Arcencium pare deja, celui du jeu pas encore",
                blocker.isBlocking() && !wooden.isBlocking(),
                "Arcencium " + blocker.isBlocking() + ", bois " + wooden.isBlocking());
        ItemStack shield = blocker.getItemInHand(InteractionHand.OFF_HAND);
        check("deux fois plus solide que le bouclier du jeu, repare a l'Arcencium",
                shield.getMaxDamage() == ArcenciumShieldItem.DURABILITY && shield.getMaxDamage() == 2 * new ItemStack(Items.SHIELD).getMaxDamage()
                        && shield.getItem().isValidRepairItem(shield, new ItemStack(ModItems.ARCENCIUM_INGOT.get())),
                "durabilite " + shield.getMaxDamage());
        float before = attacker.getHealth();
        float blockerBefore = blocker.getHealth();
        blocker.hurt(blocker.damageSources().mobAttack(attacker), 4.0F);
        float afterFirst = attacker.getHealth();
        blocker.hurt(blocker.damageSources().mobAttack(attacker), 4.0F);
        float afterSecond = attacker.getHealth();
        check("un coup pare : l'assaillant perd de la vie (riposte), le porteur rien ; un second coup dans la seconde : pas de riposte",
                afterFirst < before && blocker.getHealth() == blockerBefore && afterSecond == afterFirst,
                String.format(Locale.ROOT, "assaillant %.1f -> %.1f -> %.1f, porteur %.1f -> %.1f",
                        before, afterFirst, afterSecond, blockerBefore, blocker.getHealth()));
    }

    // ============================================================ 13. les plumes d'ailes

    /** Cahier §96 : la plume d'apparence est une recompense, au hasard parmi celles qui manquent. */
    private static void skins(ServerLevel level, BlockPos spawn) {
        line("--- les plumes d'ailes : une recompense, plus un butin (cahier §96)");
        FakePlayer fake = fake(level, "plumes", surface(level, spawn.getX() + 9, spawn.getZ() - 9));
        com.emerald.specialization.SpecializationStore.Entry entry =
                com.emerald.specialization.SpecializationStore.get(fake.getUUID());
        entry.unlocked.clear();
        try {
            java.util.Set<WingSkin> drawn = java.util.EnumSet.noneOf(WingSkin.class);
            for (int i = 0; i < 400; i++) {
                drawn.add(SkinFeatherItem.pickReward(fake, level.random));
            }
            boolean fair = !drawn.contains(WingSkin.PRISMATIQUES) && drawn.size() == WingSkin.values().length - 1;
            // toutes debloquees sauf le Rubis : c'est lui qui vient
            for (WingSkin skin : WingSkin.values()) {
                if (skin != WingSkin.RUBIS) {
                    entry.unlocked.add(skin.id());
                }
            }
            boolean missingFirst = true;
            for (int i = 0; i < 50; i++) {
                missingFirst &= SkinFeatherItem.pickReward(fake, level.random) == WingSkin.RUBIS;
            }
            SkinFeatherItem.reward(fake, WingSkin.RUBIS, "game.emeraldweapons.wingskin.won");
            int feathers = 0;
            for (ItemStack stack : fake.getInventory().items) {
                if (stack.is(ModItems.SKIN_FEATHER.get()) && SkinFeatherItem.skinOf(stack) == WingSkin.RUBIS) {
                    feathers += stack.getCount();
                }
            }
            check("au hasard parmi les dix apparences, jamais le Prismatique ; d'abord celles qu'on n'a pas ;"
                            + " la plume arrive dans le sac",
                    fair && missingFirst && feathers == 1,
                    "tirees " + drawn.size() + ", la manquante d'abord " + missingFirst + ", plumes " + feathers);
            check("a la prise d'un sanctuaire : trois chances sur cent pour chacun",
                    SkinFeatherItem.SANCTUARY_CHANCE == 0.03F, String.valueOf(SkinFeatherItem.SANCTUARY_CHANCE));
        } finally {
            entry.unlocked.clear();
        }
    }

    // ============================================================ 14. l'usure sans casse

    /** Cahier §96 : un equipement ne se casse plus ; use au bout, il n'a plus que le dixieme de sa force. */
    private static void wear(ServerLevel level, BlockPos spawn) {
        line("--- l'usure sans casse (cahier §96)");
        BlockPos at = surface(level, spawn.getX() - 9, spawn.getZ() + 9);
        FakePlayer fake = fake(level, "usure", at);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemStack chest = new ItemStack(ModItems.ARCENCIUM_CHESTPLATE.get());
        ItemStack shield = new ItemStack(Items.SHIELD);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        for (ItemStack stack : List.of(sword, chest, shield, pick)) {
            stack.hurtAndBreak(100000, level, fake, item -> {
            });
        }
        boolean kept = !sword.isEmpty() && !chest.isEmpty() && !shield.isEmpty()
                && GearWear.worn(sword) && GearWear.worn(chest) && GearWear.worn(shield)
                && sword.getDamageValue() == sword.getMaxDamage() - 1;
        check("une epee du jeu, un plastron d'Arcencium, un bouclier : uses au bout, jamais casses ;"
                        + " une pioche se casse encore",
                kept && pick.isEmpty(),
                "epee " + sword.getDamageValue() + "/" + sword.getMaxDamage() + ", plastron " + chest.getDamageValue()
                        + "/" + chest.getMaxDamage() + ", bouclier " + shield.getDamageValue() + "/"
                        + shield.getMaxDamage() + ", pioche " + (pick.isEmpty() ? "cassee" : "entiere"));
        double fresh = armor(new ItemStack(ModItems.ARCENCIUM_CHESTPLATE.get()));
        double worn = armor(chest);
        check("un plastron use ne compte plus que le dixieme de son armure",
                fresh > 0.0 && Math.abs(worn - fresh * GearWear.WORN) < 1.0e-6,
                String.format(Locale.ROOT, "neuf %.2f, use %.2f", fresh, worn));

        Zombie target = zombie(level, at.north(3), 0.0F);
        Zombie blocker = zombie(level, at.east(3), 0.0F);
        Zombie attacker = zombie(level, at.east(3).south(2), 180.0F);
        if (target == null || blocker == null || attacker == null) {
            check("creatures d'essai posees", false, "zombie impossible");
            return;
        }
        fake.setItemInHand(InteractionHand.MAIN_HAND, sword);
        float before = target.getHealth();
        target.hurt(level.damageSources().playerAttack(fake), 10.0F);
        float wornHit = before - target.getHealth();
        target.invulnerableTime = 0;
        sword.setDamageValue(0);                           // reparee
        before = target.getHealth();
        target.hurt(level.damageSources().playerAttack(fake), 10.0F);
        float freshHit = before - target.getHealth();
        float ratio = freshHit > 0.0F ? wornHit / freshHit : 1.0F;
        check("une epee usee ne fait plus que le dixieme de ses degats ; reparee, tout revient",
                !GearWear.worn(sword) && ratio > 0.08F && ratio < 0.12F && freshHit > 8.0F,
                String.format(Locale.ROOT, "usee %.2f, reparee %.2f (rapport %.3f)", wornHit, freshHit, ratio));

        ItemStack wornShield = new ItemStack(ModItems.ARCENCIUM_SHIELD.get());
        wornShield.setDamageValue(wornShield.getMaxDamage() - 1);
        blocker.setItemInHand(InteractionHand.OFF_HAND, wornShield);
        blocker.startUsingItem(InteractionHand.OFF_HAND);
        float blockerBefore = blocker.getHealth();
        float attackerBefore = attacker.getHealth();
        blocker.hurt(blocker.damageSources().mobAttack(attacker), 4.0F);
        float took = blockerBefore - blocker.getHealth();
        check("un bouclier d'Arcencium use n'arrete plus que le dixieme d'un coup, et ne riposte plus ;"
                        + " il ne se casse pas",
                blocker.isBlocking() && took > 3.0F && attacker.getHealth() == attackerBefore
                        && !blocker.getItemInHand(InteractionHand.OFF_HAND).isEmpty(),
                String.format(Locale.ROOT, "leve %s, recu %.2f sur 4, assaillant %.1f -> %.1f", blocker.isBlocking(),
                        took, attackerBefore, attacker.getHealth()));
    }

    /** L'armure d'une piece de poitrine, telle que le jeu la compte (evenements des modificateurs compris). */
    private static double armor(ItemStack stack) {
        double[] total = {0.0};
        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.CHEST, (attribute, modifier) -> {
            if (attribute.value() == net.minecraft.world.entity.ai.attributes.Attributes.ARMOR.value()) {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    // ================================================================ 5. le Grand Froid

    private static void cold(ServerLevel level, BlockPos spawn) {
        line("--- le Grand Froid de l'Aurore");
        BlockPos ground = surface(level, spawn.getX() + 12, spawn.getZ() - 12);
        FakePlayer out = fake(level, "froid-dehors", ground);
        FakePlayer under = fake(level, "froid-sous-terre", new BlockPos(ground.getX(), level.getMinBuildHeight() + 12, ground.getZ()));
        long t0 = (level.getGameTime() / 40 + 1) * 40;
        for (long t = t0; t < t0 + 60; t++) {
            AuroreCold.tickPlayer(level, out, t);
            AuroreCold.tickPlayer(level, under, t);
        }
        int outside = out.getTicksFrozen();
        int below = under.getTicksFrozen();
        check("dehors, le gel monte (60 tiques : " + outside + ") ; sous terre, rien",
                outside >= 140 && below == 0, "dehors " + outside + ", sous terre " + below);
        // un feu de camp allume a deux blocs : on se rechauffe
        BlockPos fire = ground.east(2);
        level.setBlock(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true), 3);
        try {
            FakePlayer warm = fake(level, "froid-feu", ground);
            AuroreCold.forget(warm);
            for (long t = t0; t < t0 + 60; t++) {
                AuroreCold.tickPlayer(level, warm, t);
            }
            check("a deux blocs d'un feu de camp allume : pas de gel", warm.getTicksFrozen() == 0
                    && AuroreCold.nearFire(level, warm.blockPosition()), "gel " + warm.getTicksFrozen());
        } finally {
            level.setBlock(fire, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    // ================================================================ 6. la Battue

    private static void battue(ServerLevel level, BlockPos spawn) {
        line("--- la Battue : seule la Proie brille, de pres ; sa garde");
        BlockPos at = surface(level, spawn.getX() - 12, spawn.getZ() + 12);
        Entity made = EntityType.PIG.spawn(level, at, MobSpawnType.COMMAND);
        if (!(made instanceof net.minecraft.world.entity.LivingEntity beast)) {
            check("gibier d'essai pose", false, "cochon impossible");
            return;
        }
        SPAWNED.add(beast);
        BlockPos farSpot = surface(level, at.getX() + 40, at.getZ());
        FakePlayer hunter = fake(level, "battue", farSpot);
        BattueHunt.adoptForTest(level, beast, hunter);
        List<UUID> escort = BattueHunt.escortForTest();
        List<Entity> guards = new ArrayList<>();
        for (UUID id : escort) {
            Entity guard = level.getEntity(id);
            if (guard != null) {
                guards.add(guard);
                SPAWNED.add(guard);
            }
        }
        long t = (level.getGameTime() / 20 + 1) * 20;
        BattueHunt.tickForTest(level, t);
        boolean darkFar = !beast.hasGlowingTag();
        hunter.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        BattueHunt.tickForTest(level, t + 20);
        boolean litNear = beast.hasGlowingTag();
        boolean guardsDark = guards.stream().noneMatch(Entity::hasGlowingTag);
        boolean guardsTagged = guards.stream().allMatch(g -> g.getTags().contains(BattueHunt.TAG_ESCORT));
        check("la Proie : eteinte a 40 blocs, allumee a 8 ; sa garde (" + guards.size() + ") : marquee, jamais allumee",
                darkFar && litNear && guards.size() >= 2 && guardsDark && guardsTagged,
                "loin " + !darkFar + ", pres " + litNear + ", gardes " + guards.size() + ", eteintes " + guardsDark);
        BattueHunt.endForTest(level);
        boolean gone = beast.isRemoved() && guards.stream().allMatch(Entity::isRemoved);
        check("fin de la Battue : la Proie et sa garde s'en vont", gone,
                "Proie retiree " + beast.isRemoved() + ", gardes retirees "
                        + guards.stream().filter(Entity::isRemoved).count() + "/" + guards.size());

        // au rechargement d'un troncon : les lueurs d'avant s'eteignent, sauf les yeux des Echos ;
        // une Proie ou un garde d'une Battue finie ne revient pas
        Zombie old = EntityType.ZOMBIE.create(level);
        Zombie eye = EntityType.ZOMBIE.create(level);
        Zombie stray = EntityType.ZOMBIE.create(level);
        if (old == null || eye == null || stray == null) {
            check("creatures d'essai", false, "zombie impossible");
            return;
        }
        for (Zombie z : List.of(old, eye, stray)) {
            z.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 3.5, 0.0F, 0.0F);
            SPAWNED.add(z);
        }
        old.setGlowingTag(true);
        eye.setGlowingTag(true);
        eye.addTag(com.emerald.mine.Echoes.TAG_EYE);
        stray.addTag(BattueHunt.TAG_ESCORT);
        level.addFreshEntity(old);
        level.addFreshEntity(eye);
        boolean strayAdded = level.addFreshEntity(stray);
        check("rechargement : une lueur d'une ancienne Battue s'eteint, l'oeil des Echos garde la sienne,"
                        + " un garde d'une Battue finie ne revient pas",
                !old.hasGlowingTag() && eye.hasGlowingTag() && !strayAdded,
                "ancienne lueur " + old.hasGlowingTag() + ", oeil " + eye.hasGlowingTag() + ", garde ajoute " + strayAdded);
    }

    // ================================================================ 7. les arches

    private static void arches(ServerLevel level, BlockPos spawn) {
        line("--- les arches d'Arcencium : porte doree, brumes de l'Aurore");
        // une arche posee a la main sur une dalle degagee : ou elle emporte, ou non
        //
        // LES DALLES DES BANCS PRECEDENTS D'ABORD. La dalle n'etait jamais retiree : chaque passage
        // posait la sienne trente blocs au-dessus de la precedente, et la septieme tombait hors du
        // monde (void_air, KO du 24 sept.). On les retire, et la notre a la fin.
        int archX = spawn.getX() + 20;
        int archZ = spawn.getZ() + 20;
        for (BlockPos top = surface(level, archX, archZ);
             level.getBlockState(top.below()).is(net.minecraft.world.level.block.Blocks.SMOOTH_STONE);
             top = surface(level, archX, archZ)) {
            clearSlab(level, top.below());
        }
        BlockPos base = surface(level, archX, archZ).above(30);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(base.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                for (int dy = 0; dy <= 4; dy++) {
                    level.setBlock(base.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        boolean fits = com.emerald.block.ArcPortals.fits(level, base, facing);
        com.emerald.block.ArcPortals.place(level, base, facing, com.emerald.block.ArcPortalBlock.Tint.DOREE);
        boolean placed = level.getBlockState(base).is(com.emerald.block.ModBlocks.ARC_PORTAL.get());
        FakePlayer walker = fake(level, "arche", base);
        java.util.function.BiFunction<Double, Double, Boolean> at = (lateral, depth) -> {
            // l'arche regarde le nord : la profondeur va vers -z, la largeur le long de x
            walker.moveTo(base.getX() + 0.5 - lateral, base.getY(), base.getZ() + 0.5 - depth, 0.0F, 0.0F);
            return com.emerald.block.ArcPortals.inVeil(walker, base, facing);
        };
        boolean centre = at.apply(0.0, 0.0);
        boolean edge = at.apply(0.7, 0.2);
        boolean pillar = at.apply(1.05, 0.0);
        boolean before = at.apply(0.0, 0.9);
        boolean behind = at.apply(0.0, -0.9);
        net.minecraft.world.phys.Vec3 out = com.emerald.block.ArcPortals.exit(base, facing);
        check("l'arche tient sur une dalle degagee ; le voile emporte au centre et au bord du passage, jamais contre"
                        + " un pilier, devant ni derriere ; on ressort devant elle",
                fits && placed && centre && edge && !pillar && !before && !behind && out.z < base.getZ() + 0.5 - 1.0,
                "tient " + fits + ", posee " + placed + ", centre " + centre + ", bord " + edge + ", pilier " + pillar
                        + ", devant " + before + ", derriere " + behind);
        com.emerald.block.ArcPortals.remove(level, base);
        check("retiree : plus de bloc", level.getBlockState(base).isAir(), level.getBlockState(base).toString());
        clearSlab(level, base.below());

        // la porte doree du village : a cote de l'atelier, jamais sur ses etablis
        GameState state = GameState.get(level);
        BlockPos workshop = state.workshop();
        if (workshop.equals(BlockPos.ZERO)) {
            line("pas d'atelier dans ce monde d'essai : porte du village non essayee");
        } else {
            com.emerald.weather.GoldenGate.begin(level);
            BlockPos village = com.emerald.weather.GoldenGate.villageGate();
            double far = village == null ? -1 : Math.sqrt(village.distSqr(workshop));
            boolean clearOfStations = village != null && !nearStationForTest(level, village);
            boolean arch = village != null && level.getBlockState(village).is(com.emerald.block.ModBlocks.ARC_PORTAL.get());
            check("Heure Doree : l'arche du village est posee a cote de l'atelier, a plus de quatre blocs de chaque etabli",
                    arch && clearOfStations && far >= 5.0,
                    "arche " + village + " (" + arch + "), a " + Math.round(far) + " blocs du centre de l'atelier, loin des etablis "
                            + clearOfStations);
            com.emerald.weather.GoldenGate.clear(level);
            check("fin de l'Heure Doree : l'arche s'en va", village == null || level.getBlockState(village).isAir(),
                    village == null ? "-" : level.getBlockState(village).toString());
        }

        // une paire de brumes : deux salles creusees sous terre, une arche dans chacune
        BlockPos roomA = new BlockPos(spawn.getX() + 40, level.getMinBuildHeight() + 30, spawn.getZ() + 40);
        BlockPos roomB = roomA.offset(0, 6, 36);
        for (BlockPos room : List.of(roomA, roomB)) {
            level.getChunkAt(room);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(room.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    for (int dy = 0; dy <= 3; dy++) {
                        level.setBlock(room.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        boolean pair = com.emerald.mine.AuroreCaves.placePair(level, roomA, roomB);
        List<BlockPos> mists = com.emerald.mine.AuroreCaves.mists();
        boolean both = mists.size() >= 2 && mists.stream().allMatch(m ->
                level.getBlockState(m).is(com.emerald.block.ModBlocks.ARC_PORTAL.get())
                        && com.emerald.mine.AuroreCaves.isArch(m));
        com.emerald.mine.AuroreCaves.end(level);
        boolean gone = mists.stream().allMatch(m -> !level.getBlockState(m).is(com.emerald.block.ModBlocks.ARC_PORTAL.get()));
        check("Aurore : une paire de brumes se leve en deux arches, et s'en va a la fin",
                pair && both && gone, "paire " + pair + ", arches " + mists + ", retirees " + gone);

        // LE RAPPEL, au fond d'une galerie d'un bloc : l'arche se taille dans la roche devant lui
        BlockPos tunnel = new BlockPos(spawn.getX() - 40, level.getMinBuildHeight() + 40, spawn.getZ() - 40);
        level.getChunkAt(tunnel);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 4; dy++) {
                    level.setBlock(tunnel.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(tunnel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(tunnel.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        FakePlayer digger = fake(level, "rappel", tunnel);
        digger.setYRot(180.0F);                                  // il regarde le nord
        BlockPos recall = com.emerald.mine.AuroreCaves.recallForTest(level, digger);
        boolean arch = recall != null && level.getBlockState(recall).is(com.emerald.block.ModBlocks.ARC_PORTAL.get())
                && level.getBlockState(recall).getValue(com.emerald.block.ArcPortalBlock.TEINTE)
                == com.emerald.block.ArcPortalBlock.Tint.AUBE;
        com.emerald.mine.AuroreCaves.clearRecallsForTest(level);
        boolean cleared = recall == null || !level.getBlockState(recall).is(com.emerald.block.ModBlocks.ARC_PORTAL.get());
        check("Aurore finie, au fond d'une galerie d'un bloc : l'arche d'aube du rappel se taille dans la roche,"
                        + " a deux blocs, et s'en va a la fin de son temps",
                arch && cleared && recall.distSqr(tunnel) <= 9.0,
                "arche " + recall + " (" + arch + "), retiree " + cleared);
    }

    /** Un etabli a quatre blocs ou moins (la regle de la porte du village). */
    private static boolean nearStationForTest(ServerLevel level, BlockPos at) {
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-4, -1, -4), at.offset(4, 2, 4))) {
            net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
            if (s.is(com.emerald.block.ModBlocks.ARCENCIUM_FORGE.get()) || s.is(com.emerald.block.ModBlocks.SOCKET_BENCH.get())
                    || s.is(com.emerald.block.ModBlocks.SPECIALIZATION_ALTAR.get())) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ outils

    /** La dalle de sept sur sept de l'essai des arches, rendue a l'air. */
    private static void clearSlab(ServerLevel level, BlockPos centre) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                BlockPos p = centre.offset(dx, 0, dz);
                if (level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.SMOOTH_STONE)) {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunkAt(new BlockPos(x, 0, z));
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
    }

    private static FakePlayer fake(ServerLevel level, String name, BlockPos feet) {
        FakePlayer fake = new FakePlayer(level, new GameProfile(
                UUID.nameUUIDFromBytes(("autotest-partie:" + name).getBytes(StandardCharsets.UTF_8)), "[Partie]"));
        level.getChunkAt(feet);
        fake.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 0.0F);
        return fake;
    }

    @javax.annotation.Nullable
    private static Zombie zombie(ServerLevel level, BlockPos feet, float yaw) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            return null;
        }
        zombie.moveTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0.0F);
        zombie.setYHeadRot(yaw);
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        SPAWNED.add(zombie);
        return zombie;
    }

    private static void end(MinecraftServer server) {
        line("RESULTAT : " + passed + " OK, " + failed + " KO");
        Path file = server.getServerDirectory().resolve("partie_autotest.txt");
        try {
            Files.writeString(file, OUT.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("autotest partie : rapport impossible a ecrire dans {}", file, e);
        }
        LOGGER.info("autotest partie : {} OK, {} KO, rapport dans {} ; arret du serveur",
                passed, failed, file.toAbsolutePath());
        server.halt(false);
    }

    private static void line(String text) {
        OUT.append(text).append('\n');
        LOGGER.info("autotest partie : {}", text);
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
