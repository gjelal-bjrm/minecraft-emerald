package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Commandes de pilotage du mode, pour les tests et l'arbitrage.
 *
 * Elles existent surtout pour pouvoir eprouver la boucle sans avoir a rejouer
 * le prologue a chaque fois. Le declencheur normal reste la Lame du Serment.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class GameCommands {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcencium")
                .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("setup").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            GameManager.clear();
            GameManager.setup(level, level.getSharedSpawnPos());
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("command.emeraldweapons.setup"), true);
            return 1;
        }));

        root.then(Commands.literal("start").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            GameState.get(level).begin(level);
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("command.emeraldweapons.started"), true);
            return 1;
        }));

        root.then(Commands.literal("stop").executes(ctx -> {
            GameManager.clear();
            GameState.get(ctx.getSource().getServer().overworld()).reset();
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("command.emeraldweapons.stopped"), true);
            return 1;
        }));

        root.then(Commands.literal("find").requires(source -> true).executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            var village = GameState.get(level).village();
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "game.emeraldweapons.locked.where", village.getX(), village.getY(),
                    village.getZ(), 0), false);
            return 1;
        }));

        // ------------------------------------------------------------- meteo
        var weatherNode = Commands.literal("weather");
        weatherNode.then(Commands.literal("stop").executes(ctx -> {
            com.emerald.weather.WeatherManager.stop(ctx.getSource().getServer().overworld());
            ctx.getSource().sendSuccess(() ->
                    Component.translatable("command.emeraldweapons.weather.stopped"), true);
            return 1;
        }));
        for (com.emerald.weather.Weather weather : com.emerald.weather.Weather.values()) {
            if (weather == com.emerald.weather.Weather.CLEAR) {
                continue;
            }
            final com.emerald.weather.Weather target = weather;
            weatherNode.then(Commands.literal(weather.id())
                    .executes(ctx -> forceWeather(ctx.getSource(), target, 0))
                    .then(Commands.argument("secondes",
                                    com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 3600))
                            .executes(ctx -> forceWeather(ctx.getSource(), target,
                                    com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(ctx, "secondes")))));
        }
        root.then(weatherNode);

        // ----------------------------------------------------------- fissure
        // pour l'essai : ouvre une fissure pres du joueur, de la taille voulue ou au sort
        var fissureNode = Commands.literal("fissure")
                .executes(ctx -> openFissure(ctx.getSource(), null));
        for (String size : new String[]{"petite", "moyenne", "grande", "abime"}) {
            fissureNode.then(Commands.literal(size)
                    .executes(ctx -> openFissure(ctx.getSource(), size)));
        }
        root.then(fissureNode);

        root.then(Commands.literal("skip")
                .then(Commands.argument("minutes",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 60))
                        .executes(ctx -> {
                            int minutes = com.mojang.brigadier.arguments.IntegerArgumentType
                                    .getInteger(ctx, "minutes");
                            ServerLevel level = ctx.getSource().getServer().overworld();
                            GameState.get(level).skip(minutes * 60L * 20L);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "command.emeraldweapons.skip", minutes), true);
                            return 1;
                        })));

        // L'interrupteur du mode : c'est ce qui permet d'aller EXPLORER sans
        // avoir a gagner le prologue d'abord.
        root.then(Commands.literal("mode")
                .then(Commands.literal("on").executes(ctx -> switchMode(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> switchMode(ctx.getSource(), false))));

        // Le sanctuaire, bati sur place : c'est l'outil pour le regarder en
        // monde plat sans jouer une partie entiere pour l'atteindre.
        root.then(Commands.literal("sanctuary")
                .then(Commands.argument("palier",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3))
                        .executes(ctx -> buildSanctuary(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType
                                        .getInteger(ctx, "palier"))))
                .executes(ctx -> buildSanctuary(ctx.getSource(), 1)));


        // Retrouver l'ancre du sanctuaire le plus proche, et s'y rendre.
        // Chercher a la main un bloc pose au sommet d'une pyramide de
        // quatre-vingt-dix blocs est une perte de temps a chaque essai.
        root.then(Commands.literal("anchor").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            var found = com.emerald.game.SanctuaryMist.nearestAnchor(level,
                    net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
            if (found == null) {
                ctx.getSource().sendFailure(Component.translatable(
                        "command.emeraldweapons.anchor.none"));
                return 0;
            }
            boolean here = level.getBlockState(found)
                    .is(com.emerald.block.ModBlocks.PRISMATIC_ANCHOR.get());
            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.teleportTo(found.getX() + 0.5, found.getY() + 1, found.getZ() + 0.5);
            }
            final BlockPos at = found;
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    here ? "command.emeraldweapons.anchor.found"
                         : "command.emeraldweapons.anchor.missing",
                    at.getX(), at.getY(), at.getZ()), false);
            return 1;
        }));

        // Declencher l'indice a la main.
        //
        // L'indice automatique n'arrive qu'au bout de quatre-vingt-dix
        // secondes, ce qui est le bon rythme en partie et le mauvais en test :
        // on ne va pas attendre a chaque construction pour verifier ou les
        // sceaux sont tombes. La commande donne le meme signal, plus les
        // coordonnees en clair -- une capture d'ecran de trop a ete perdue a
        // chercher un sceau que le code savait situer.
        root.then(Commands.literal("seals").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            var found = com.emerald.game.SanctuaryMist.nearestAnchor(level,
                    net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
            if (found == null) {
                ctx.getSource().sendFailure(Component.translatable(
                        "command.emeraldweapons.anchor.none"));
                return 0;
            }
            String where = com.emerald.game.SanctuarySeals.describe(found);
            if (where.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal(
                        "Cette ancre n'a pas de tombeau enregistre."));
                return 0;
            }
            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer p) {
                com.emerald.game.SanctuarySeals.reveal(level, found, p);
            }
            final String line = where;
            ctx.getSource().sendSuccess(() -> Component.literal("Sceaux : " + line), false);
            return 1;
        }));

        // DIRE QUI A POSE CE BLOC.
        //
        // Cinq allers-retours ont ete perdus a corriger le mauvais escalier,
        // faute d'un moyen de designer un bloc autrement qu'en l'entourant sur
        // une capture. On vise, on tape la commande, et l'on obtient le bloc et
        // sa position RELATIVE au sanctuaire -- c'est-a-dire dans le repere ou
        // le code est ecrit, le seul qui permette de retrouver la ligne
        // fautive. Une capture montre un symptome ; ceci donne une adresse.
        root.then(Commands.literal("what").executes(ctx -> {
            if (!(ctx.getSource().getEntity()
                    instanceof net.minecraft.server.level.ServerPlayer player)) {
                ctx.getSource().sendFailure(Component.literal("A executer en jeu."));
                return 0;
            }
            ServerLevel level = player.serverLevel();
            var hit = player.pick(20.0, 0.0F, false);
            if (!(hit instanceof net.minecraft.world.phys.BlockHitResult block)) {
                ctx.getSource().sendFailure(Component.literal("Vise un bloc."));
                return 0;
            }
            BlockPos at = block.getBlockPos();
            String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(at).getBlock()).toString();
            // Le registre des sanctuaires est VOLATIL : il se vide au
            // rechargement du monde, et la commande repondait alors « aucun
            // sanctuaire », c'est-a-dire rien. On se rabat donc sur ce qui,
            // lui, est ecrit dans les blocs : l'ancre. Elle se trouve en
            // (cx, sommet, cz-3), ce qui suffit a reconstituer le repere.
            BlockPos centre = com.emerald.game.SanctuaryMist.nearestCentre(at);
            String where;
            if (centre == null) {
                // Le sceau de l'entree est pose en (cx-1, y+1, fromZ-13) :
                // un seul bloc suffit donc a retrouver tout le repere.
                BlockPos witness = frameWitness(level, at);
                if (witness == null) {
                    where = "sanctuaire introuvable (ni registre, ni sceau a 100 blocs)";
                } else {
                    int cx = witness.getX() + 1;
                    int gy = witness.getY() - 1;
                    int fromZ = witness.getZ() + 13;
                    where = String.format("cx%+d | y%+d | cz%+d  (fromZ%+d)  [d'apres un sceau]",
                            at.getX() - cx, at.getY() - gy, at.getZ() - (fromZ - 47),
                            at.getZ() - fromZ);
                }
            } else {
                int fromZ = centre.getZ() + 47;
                where = String.format("cx%+d | y%+d | cz%+d  (fromZ%+d)",
                        at.getX() - centre.getX(), at.getY() - centre.getY(),
                        at.getZ() - centre.getZ(), at.getZ() - fromZ);
            }
            final String line = String.format("%s en %d,%d,%d -> %s",
                    id, at.getX(), at.getY(), at.getZ(), where);
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
            return 1;
        }));

        // Vider le terrain d'essai.
        //
        // Rien a voir avec le jeu : c'est pour la mise au point. Essayer un
        // sanctuaire avec quarante morts-vivants aux trousses fait perdre plus
        // de temps que de le batir.
        root.then(Commands.literal("purge")
                .executes(ctx -> purge(ctx.getSource(), 256))
                .then(Commands.argument("rayon",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 512))
                        .executes(ctx -> purge(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType
                                        .getInteger(ctx, "rayon")))));

        // « goto 1 », « goto 2 », « goto 3 » : chaque ancre a son numero.
        //
        // C'est celui de l'interface et des messages de partie, donc rien de
        // nouveau a retenir. Sans lui il fallait recopier des coordonnees a la
        // main pour se rendre a quatre cent cinquante blocs, ce qu'on fait
        // vingt fois par seance de mise au point.
        // Eveiller tous les sceaux du sanctuaire le plus proche.
        //
        // Comme l'Onde de Purge : verifier un siege ne doit pas coûter cinq
        // allers-retours dans le tombeau.
        root.then(Commands.literal("wake").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            var found = com.emerald.game.SanctuaryMist.nearestAnchor(level,
                    net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
            if (found == null) {
                ctx.getSource().sendFailure(Component.translatable(
                        "command.emeraldweapons.anchor.none"));
                return 0;
            }
            int woke = com.emerald.game.SanctuarySeals.lightAll(level, found);
            if (woke < 0) {
                ctx.getSource().sendFailure(Component.literal(
                        "Cette ancre n'a pas de tombeau enregistre."));
                return 0;
            }
            final int n = woke;
            ctx.getSource().sendSuccess(() -> Component.literal(n == 0
                    ? "Tous les sceaux etaient deja eveilles."
                    : n + " sceau(x) eveille(s) : l'ancre accepte l'arcencium."), false);
            return 1;
        }));

        // LA FICHE DE PERSONNAGE, en attendant son ecran.
        //
        // Elle merite une interface, et elle en aura une. Mais une fiche qu'on
        // ne peut pas lire ne se regle pas, et une progression qu'on ne peut
        // pas depenser ne se ressent pas : la commande la rend utilisable des
        // maintenant, ce qui permet d'en eprouver les chiffres avant d'y mettre
        // des pixels.
        var hero = Commands.literal("hero").executes(ctx -> heroSheet(ctx.getSource()));
        for (com.emerald.hero.HeroStat stat : com.emerald.hero.HeroStat.values()) {
            hero.then(Commands.literal(stat.name().toLowerCase(java.util.Locale.ROOT))
                    .then(Commands.argument("points",
                                    com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100))
                            .executes(ctx -> heroSpend(ctx.getSource(), stat,
                                    com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(ctx, "points")))));
        }
        hero.then(Commands.literal("level")
                .then(Commands.argument("niveaux",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 99))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity()
                                    instanceof net.minecraft.server.level.ServerPlayer player)) {
                                return 0;
                            }
                            com.emerald.hero.HeroEvents.awardLevels(player,
                                    com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(ctx, "niveaux"));
                            return heroSheet(ctx.getSource());
                        })));
        hero.then(Commands.literal("reset").executes(ctx -> {
            if (!(ctx.getSource().getEntity()
                    instanceof net.minecraft.server.level.ServerPlayer player)) {
                return 0;
            }
            int back = com.emerald.hero.HeroLevel.reset(player);
            com.emerald.hero.HeroEvents.apply(player);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    back + " point(s) rendus."), false);
            return 1;
        }));
        hero.then(Commands.literal("xp")
                .then(Commands.argument("montant",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 100000))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity()
                                    instanceof net.minecraft.server.level.ServerPlayer player)) {
                                return 0;
                            }
                            com.emerald.hero.HeroEvents.award(player,
                                    com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(ctx, "montant"));
                            return heroSheet(ctx.getSource());
                        })));
        root.then(hero);

        // LES RUNES, EN BANC D'ESSAI.
        //
        // Le drop est rare a dessein -- dix-huit runes pour une partie entiere --
        // ce qui le rend intestable en jeu : verifier un schema de rang huit
        // demanderait des heures. Ces trois commandes le rendent immediat.
        var rune = Commands.literal("rune");
        for (com.emerald.rune.RuneFamily family : com.emerald.rune.RuneFamily.values()) {
            rune.then(Commands.literal(family.getSerializedName())
                    .then(Commands.argument("rang",
                                    com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8))
                            .executes(ctx -> giveRune(ctx.getSource(), family,
                                    com.mojang.brigadier.arguments.IntegerArgumentType
                                            .getInteger(ctx, "rang"), 1))
                            .then(Commands.argument("combien",
                                            com.mojang.brigadier.arguments.IntegerArgumentType
                                                    .integer(1, 36))
                                    .executes(ctx -> giveRune(ctx.getSource(), family,
                                            com.mojang.brigadier.arguments.IntegerArgumentType
                                                    .getInteger(ctx, "rang"),
                                            com.mojang.brigadier.arguments.IntegerArgumentType
                                                    .getInteger(ctx, "combien"))))));
        }
        rune.then(Commands.literal("drop")
                .then(Commands.argument("pv",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5000))
                        .then(Commands.argument("morts",
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                                .integer(1, 100000))
                                .executes(ctx -> simulateDrops(ctx.getSource(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                                .getInteger(ctx, "pv"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                                .getInteger(ctx, "morts"))))));
        root.then(rune);

        // L'ELEMENT, EN BANC D'ESSAI.
        //
        // Accorder une arme demande de chasser des creatures du bon element ;
        // eprouver les quatre couples demanderait donc une partie par couple.
        var element = Commands.literal("element");
        for (com.emerald.element.Element which : com.emerald.element.Element.values()) {
            if (which == com.emerald.element.Element.NEUTRE) {
                continue;
            }
            element.then(Commands.literal(which.getSerializedName())
                    // PAS D'ACCORD DIRECT, meme au banc d'essai : la commande
                    // donne des PIERRES, et l'on s'accorde en s'en servant.
                    // Un raccourci ici aurait fini par etre le seul chemin
                    // qu'on emprunte, et la boucle -- chasser l'element qu'on
                    // veut porter -- ne serait jamais eprouvee.
                    .executes(ctx -> giveStone(ctx.getSource(), which, 8)));
        }
        element.then(Commands.literal("table")
                .executes(ctx -> elementTable(ctx.getSource())));
        element.then(Commands.literal("here")
                .executes(ctx -> elementHere(ctx.getSource())));
        root.then(element);

        // L'AMELIORATION, EN BANC D'ESSAI.
        //
        // Un +10 coute une trentaine de pierres et soixante lingots
        // d'Arcencium : l'eprouver en jouant demanderait une partie entiere par
        // essai. Ces quatre commandes le rendent immediat.
        var upgrade = Commands.literal("upgrade");
        upgrade.then(Commands.argument("niveau",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 10))
                .executes(ctx -> setUpgrade(ctx.getSource(),
                        com.mojang.brigadier.arguments.IntegerArgumentType
                                .getInteger(ctx, "niveau"))));
        upgrade.then(Commands.literal("try")
                .then(Commands.argument("essais",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 500))
                        .executes(ctx -> tryUpgrade(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType
                                        .getInteger(ctx, "essais")))));
        upgrade.then(Commands.literal("sim")
                .then(Commands.argument("cible",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 10))
                        .executes(ctx -> simulateUpgrade(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType
                                        .getInteger(ctx, "cible")))));
        upgrade.then(Commands.literal("kit")
                .executes(ctx -> upgradeKit(ctx.getSource())));
        root.then(upgrade);

        root.then(Commands.literal("goto")
                .then(Commands.argument("ancre",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3))
                        .executes(ctx -> gotoAnchor(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType
                                        .getInteger(ctx, "ancre"))))
                .executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            var village = GameState.get(level).village();
            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                var stand = com.emerald.game.WorldSetup.findOpenGround(level,
                        village.offset(3, 0, 0), 6);
                player.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            }
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "game.emeraldweapons.locked.where", village.getX(), village.getY(),
                    village.getZ(), 0), false);
            return 1;
        }));

        root.then(Commands.literal("status").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            GameState state = GameState.get(level);
            long seconds = state.remaining(level) / 20L;
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.emeraldweapons.status",
                    state.status().name(),
                    Component.translatable(state.phase(level).translationKey()),
                    String.format("%d:%02d", seconds / 60L, seconds % 60L),
                    state.anchorsActive(),
                    state.nextTier()), false);
            return 1;
        }));

        event.getDispatcher().register(root);
    }

    private static int buildSanctuary(CommandSourceStack source, int tier) {
        ServerLevel level = source.getServer().overworld();
        var pos = net.minecraft.core.BlockPos.containing(source.getPosition());
        var ground = new net.minecraft.core.BlockPos(pos.getX(),
                WorldSetup.surfaceY(level, pos.getX(), pos.getZ()) - 1, pos.getZ());
        var anchor = Sanctuary.build(level, source, ground, tier);
        source.sendSuccess(() -> Component.translatable(
                "command.emeraldweapons.sanctuary",
                anchor.getX(), anchor.getY(), anchor.getZ()), true);
        source.sendSuccess(() -> Component.translatable(
                "command.emeraldweapons.sanctuary.hint"), false);
        return 1;
    }

    private static int switchMode(CommandSourceStack source, boolean on) {
        ServerLevel level = source.getServer().overworld();
        ModeSwitch.set(level, on);
        source.sendSuccess(() -> Component.translatable(
                on ? "command.emeraldweapons.mode.on" : "command.emeraldweapons.mode.off"), true);
        return 1;
    }

    private static int forceWeather(CommandSourceStack source,
                                    com.emerald.weather.Weather weather, int seconds) {
        com.emerald.weather.WeatherManager.force(source.getServer().overworld(),
                weather, seconds * 20);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.weather.set",
                Component.translatable(weather.translationKey())), true);
        return 1;
    }

    /**
     * Reconstitue le repere du sanctuaire a partir des blocs, et non du registre.
     *
     * Le meilleur temoin n'est pas l'ancre mais un SCEAU DU TOMBEAU : le
     * premier des trois est pose en (cx-1, y+1, fromZ-13), ce qui donne d'un
     * coup les trois coordonnees du repere a partir d'un seul bloc. Et comme
     * les trois s'echelonnent le long du couloir, celui dont le z est le plus
     * grand est forcement celui de l'entree.
     *
     * Le premier jet balayait DE DEUX EN DEUX pour aller plus vite : il ne
     * testait donc que les decalages pairs, et manquait a coup sur tout temoin
     * situe a un decalage impair. Une recherche qui saute une case sur deux ne
     * cherche pas, elle tire au sort.
     */
    private static BlockPos frameWitness(ServerLevel level, BlockPos near) {
        var seal = com.emerald.block.ModBlocks.TOMB_SEAL.get();
        BlockPos best = null;
        for (int dx = -100; dx <= 100; dx++) {
            for (int dz = -100; dz <= 100; dz++) {
                for (int dy = -12; dy <= 24; dy++) {
                    BlockPos probe = near.offset(dx, dy, dz);
                    if (level.getBlockState(probe).is(seal)
                            && (best == null || probe.getZ() > best.getZ())) {
                        best = probe;
                    }
                }
            }
        }
        return best;
    }



    private static int purge(CommandSourceStack source, int reach) {
        ServerLevel level = source.getServer().overworld();
        int doomed = com.emerald.game.PurgeWave.start(level, source.getPosition(), reach);
        source.sendSuccess(() -> Component.literal(String.format(
                "Onde de purge : %d hostile(s) dans %d blocs.", doomed, reach)), false);
        return 1;
    }

    /**
     * Se rendre a une ancre par son numero.
     *
     * On atterrit A COTE et non dessus : l'ancre coiffe le faite de la
     * pyramide, et s'y materialiser reviendrait a se poser dans le bloc.
     */
    private static int gotoAnchor(CommandSourceStack source, int index) {
        ServerLevel level = source.getServer().overworld();
        var anchors = GameState.get(level).anchors();
        if (index > anchors.size()) {
            source.sendFailure(Component.literal(
                    "Cette partie n'a que " + anchors.size() + " ancre(s)."));
            return 0;
        }
        BlockPos anchor = anchors.get(index - 1);
        if (source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            player.teleportTo(anchor.getX() + 2.5, anchor.getY() + 1, anchor.getZ() + 0.5);
        }
        source.sendSuccess(() -> Component.translatable(
                "game.emeraldweapons.anchor.at", index,
                anchor.getX(), anchor.getY(), anchor.getZ()), false);
        return 1;
    }

    private static int heroSheet(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("A executer en jeu."));
            return 0;
        }
        int level = com.emerald.hero.HeroLevel.level(player);
        source.sendSuccess(() -> Component.literal(String.format(
                "Heros %d  |  %d / %d xp  |  %d point(s) libre(s)",
                level, com.emerald.hero.HeroLevel.xp(player),
                com.emerald.hero.HeroLevel.needed(level),
                com.emerald.hero.HeroLevel.free(player))), false);
        for (com.emerald.hero.HeroStat stat : com.emerald.hero.HeroStat.values()) {
            int at = com.emerald.hero.HeroLevel.path(player, stat);
            int gift = com.emerald.hero.HeroLevel.effective(player, stat) - at;
            final String line = String.format(
                    "  %-9s niveau %3d / %d%s   %d palier(s)   %d pt(s) verses",
                    stat.name().toLowerCase(java.util.Locale.ROOT), at,
                    com.emerald.hero.HeroStat.MAX_PATH,
                    gift > 0 ? String.format(" (+%d runes)", gift) : "",
                    com.emerald.hero.HeroStat.tiers(at + gift),
                    com.emerald.hero.HeroLevel.spent(player, stat));
            source.sendSuccess(() -> Component.literal(line)
                    .withStyle(stat.colour()), false);
        }
        return 1;
    }

    private static int heroSpend(CommandSourceStack source, com.emerald.hero.HeroStat stat,
                                 int wanted) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("A executer en jeu."));
            return 0;
        }
        int placed = com.emerald.hero.HeroLevel.spend(player, stat, wanted);
        if (placed == 0) {
            source.sendFailure(Component.literal(
                    "Aucun niveau achete : pas assez de points, ou voie au plafond."));
            return 0;
        }
        com.emerald.hero.HeroEvents.apply(player);
        return heroSheet(source);
    }

    /**
     * Donne des runes tirees pour de vrai.
     *
     * On passe par le MEME tirage que le drop, et non par une pile fabriquee a
     * la main : c'est le seul moyen que le banc d'essai teste le code qui
     * tourne en partie. Une commande qui construirait ses propres runes
     * validerait la commande, pas le jeu.
     */
    private static int giveRune(CommandSourceStack source,
                                com.emerald.rune.RuneFamily family, int rank, int count) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("A executer en jeu."));
            return 0;
        }
        for (int i = 0; i < count; i++) {
            player.getInventory().add(com.emerald.rune.RuneItem.stack(
                    com.emerald.rune.RuneMark.roll(family, rank, player.getRandom()),
                    com.emerald.item.ModItems.RUNE.get()));
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "%d rune(s) %s de rang %d  (schema %s)", count,
                family.getSerializedName(), rank,
                com.emerald.rune.RuneMark.pattern(rank))), false);
        return count;
    }

    /**
     * Simule des mises a mort et rend la distribution des rangs.
     *
     * Rejoue exactement la loi du drop : meme probabilite, meme plafond par
     * points de vie, meme tirage. On lit donc en une seconde ce qu'il faudrait
     * des heures a constater -- et surtout, on le lit sur les chiffres du jeu
     * plutot que sur une feuille de calcul qui pourrait avoir divergé.
     */
    private static int simulateDrops(CommandSourceStack source, int health, int kills) {
        net.minecraft.util.RandomSource random = source.getLevel().getRandom();
        int[] byRank = new int[9];
        int total = 0;
        for (int i = 0; i < kills; i++) {
            com.emerald.rune.RuneMark mark =
                    com.emerald.rune.RuneDrops.simulate(health, random);
            if (mark != null) {
                byRank[Math.min(8, Math.max(0, mark.rank()))]++;
                total++;
            }
        }
        final int dropped = total;
        // On rend AUSSI les deux autres butins, parce qu'ils tombent de la meme
        // creature : mesurer les runes sans les pierres ni les cristaux ne
        // dirait pas ce qu'une partie rapporte reellement.
        int stones = 0;
        int crystals = 0;
        for (int i = 0; i < kills; i++) {
            if (random.nextDouble() < com.emerald.rune.RuneDrops.stoneChance()) {
                stones++;
            }
            if (random.nextDouble() < com.emerald.rune.RuneDrops.stoneDropChance()) {
                crystals++;
            }
        }
        final int gotStones = stones;
        final int gotCrystals = crystals;
        source.sendSuccess(() -> Component.literal(String.format(
                        "%d morts a %d PV  ->  %d rune(s) (%.1f %%), %d pierre(s), %d cristal(aux)",
                        kills, health, dropped, 100.0 * dropped / kills,
                        gotStones, gotCrystals))
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        for (int rank = 1; rank <= 8; rank++) {
            if (byRank[rank] == 0) {
                continue;
            }
            com.emerald.item.GearRarity rarity = com.emerald.item.GearRarity.values()[rank];
            final int n = byRank[rank];
            source.sendSuccess(() -> Component.empty()
                    .append(rarity.label())
                    .append(Component.literal(String.format(
                            "  %d  (%.2f %% des morts)", n, 100.0 * n / kills)))
                    .withStyle(style -> style.withColor(rarity.colour())), false);
        }
        return dropped;
    }

    /** Donne des cristaux, pour accorder sans chasser. */
    private static int giveStone(CommandSourceStack source,
                                   com.emerald.element.Element element, int count) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("A executer en jeu."));
            return 0;
        }
        player.getInventory().add(com.emerald.element.ElementStoneItem.stack(
                element, com.emerald.item.ModItems.ELEMENT_STONE.get(), count));
        source.sendSuccess(() -> Component.empty()
                .append(Component.literal(count + " cristaux de "))
                .append(element.label()), false);
        return count;
    }

    /**
     * La table des affinites, telle que le code la calcule.
     *
     * Elle est LUE et non recopiee : c'est Element.against qui repond, de sorte
     * qu'un changement de bareme se voie ici sans qu'on ait a mettre la
     * commande a jour. Une table recopiee finirait par mentir.
     */
    private static int elementTable(CommandSourceStack source) {
        com.emerald.element.Element[] all = com.emerald.element.Element.values();
        for (com.emerald.element.Element attacker : all) {
            if (attacker == com.emerald.element.Element.NEUTRE) {
                continue;
            }
            net.minecraft.network.chat.MutableComponent line =
                    Component.empty().append(attacker.label()).append(Component.literal(" ->"));
            for (com.emerald.element.Element defender : all) {
                if (defender == com.emerald.element.Element.NEUTRE) {
                    continue;
                }
                line.append(Component.literal(String.format("  %s x%.2f",
                        defender.getSerializedName().substring(0, 3),
                        attacker.against(defender))));
            }
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    /** L'element des creatures alentour : sert a verifier la deduction par traits. */
    private static int elementHere(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("A executer en jeu."));
            return 0;
        }
        var found = player.level().getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class,
                player.getBoundingBox().inflate(24.0),
                e -> e.isAlive() && e != player);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("Aucune creature a 24 blocs."));
            return 0;
        }
        for (net.minecraft.world.entity.LivingEntity entity : found) {
            com.emerald.element.Element first = com.emerald.element.Attunement.of(entity);
            com.emerald.element.Element other = com.emerald.element.Attunement.second(entity);
            source.sendSuccess(() -> Component.empty()
                    .append(entity.getDisplayName())
                    .append(Component.literal(String.format(" (%.0f PV, resistance %.0f %%)  ",
                            entity.getMaxHealth(),
                            com.emerald.element.ElementResist.of(entity, first.opposite()))))
                    .append(first.label())
                    .append(other == com.emerald.element.Element.NEUTRE
                            ? Component.empty()
                            : Component.literal(" + ").append(other.label())), false);
        }
        return found.size();
    }

    /** L'arme ou l'armure en main, ou une pile vide. */
    private static net.minecraft.world.item.ItemStack held(CommandSourceStack source) {
        return source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                ? player.getMainHandItem()
                : net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** Pose directement un cran, sans tirage : pour comparer deux niveaux cote a cote. */
    private static int setUpgrade(CommandSourceStack source, int level) {
        net.minecraft.world.item.ItemStack stack = held(source);
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Tenez l'equipement en main."));
            return 0;
        }
        com.emerald.item.Upgrade.set(stack, level);
        source.sendSuccess(() -> Component.literal(String.format(
                        "Ameliore a +%d  (+%d %% aux degats ou a l'armure)",
                        level, Math.round(com.emerald.item.Upgrade.bonus(level) * 100)))
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        return 1;
    }

    /**
     * Tente vraiment, avec la loi du jeu, mais sans payer.
     *
     * Sans paiement : le banc d'essai sert a eprouver les PROBABILITES, et
     * exiger soixante lingots d'Arcencium pour les mesurer reviendrait a ne
     * jamais pouvoir les mesurer.
     */
    private static int tryUpgrade(CommandSourceStack source, int attempts) {
        net.minecraft.world.item.ItemStack stack = held(source);
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Tenez l'equipement en main."));
            return 0;
        }
        var random = source.getLevel().getRandom();
        int level = com.emerald.item.Upgrade.of(stack);
        int won = 0;
        for (int i = 0; i < attempts && level < com.emerald.item.Upgrade.MAX; i++) {
            int after = com.emerald.item.Upgrade.attempt(level, random);
            if (after > level) {
                won++;
            }
            level = after;
        }
        com.emerald.item.Upgrade.set(stack, level);
        final int reached = level;
        final int hits = won;
        source.sendSuccess(() -> Component.literal(String.format(
                        "%d tentative(s), %d reussite(s)  ->  +%d", attempts, hits, reached))
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        return 1;
    }

    /**
     * Simule mille montees jusqu'a la cible et rend le cout median.
     *
     * Rejoue la MEME loi que l'etabli -- memes chances, memes couts -- de sorte
     * qu'une mesure faite ici vaille pour le jeu.
     */
    private static int simulateUpgrade(CommandSourceStack source, int target) {
        var random = source.getLevel().getRandom();
        int trials = 1000;
        int[] stones = new int[trials];
        java.util.Map<String, Integer> mats = new java.util.LinkedHashMap<>();
        for (int t = 0; t < trials; t++) {
            int level = 0;
            int used = 0;
            while (level < target && used < 20000) {
                used++;
                com.emerald.item.Upgrade.Cost cost = com.emerald.item.Upgrade.cost(level + 1);
                String name = cost.material().getDescriptionId();
                mats.merge(name, cost.amount(), Integer::sum);
                level = com.emerald.item.Upgrade.attempt(level, random);
            }
            stones[t] = used;
        }
        java.util.Arrays.sort(stones);
        final int median = stones[trials / 2];
        final int p90 = stones[(int) (trials * 0.9)];
        source.sendSuccess(() -> Component.literal(String.format(
                        "Vers +%d : %d pierres en median, %d au 90e centile",
                        target, median, p90))
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        for (var entry : mats.entrySet()) {
            final String line = String.format("   %5d  ", entry.getValue() / trials);
            source.sendSuccess(() -> Component.literal(line)
                    .append(Component.translatable(entry.getKey()))
                    .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        }
        return 1;
    }

    /** De quoi tout tenter : pierres et metaux, en quantite. */
    private static int upgradeKit(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("A executer en jeu."));
            return 0;
        }
        player.getInventory().add(new net.minecraft.world.item.ItemStack(
                com.emerald.item.ModItems.FORGE_STONE.get(), 64));
        for (net.minecraft.world.item.Item metal : new net.minecraft.world.item.Item[]{
                net.minecraft.world.item.Items.IRON_INGOT,
                net.minecraft.world.item.Items.GOLD_INGOT,
                net.minecraft.world.item.Items.DIAMOND,
                net.minecraft.world.item.Items.NETHERITE_INGOT,
                com.emerald.item.ModItems.ARCENCIUM_INGOT.get()}) {
            player.getInventory().add(new net.minecraft.world.item.ItemStack(metal, 64));
        }
        source.sendSuccess(() -> Component.literal(
                "64 pierres et 64 de chaque metal.").withStyle(
                        net.minecraft.ChatFormatting.GOLD), false);
        return 1;
    }


    private static int openFissure(CommandSourceStack source, String size) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Il faut un joueur."));
            return 0;
        }
        boolean opened = com.emerald.weather.WeatherEffects.debugFissure(source.getLevel(), player, size);
        source.sendSuccess(() -> Component.literal(opened
                ? "Fissure annoncee : le sol cede dans une seconde et demie."
                : "Pas de place ici (sous un toit, ou chunk non charge)."), true);
        return opened ? 1 : 0;
    }

}
