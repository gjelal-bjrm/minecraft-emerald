package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import com.mojang.brigadier.arguments.StringArgumentType;
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

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Arrete ou relance l'horloge de la partie. */
    private static int setPaused(CommandSourceStack source, boolean value) {
        ServerLevel level = source.getServer().overworld();
        GameState state = GameState.get(level);
        if (!state.setPaused(level, value)) {
            source.sendFailure(Component.translatable(state.status() != GameState.Status.RUNNING
                    ? "command.emeraldweapons.pause.nogame"
                    : (value ? "command.emeraldweapons.pause.already"
                             : "command.emeraldweapons.pause.notpaused")));
            return 0;
        }
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.translatable(value
                            ? "game.emeraldweapons.pause.on"
                            : "game.emeraldweapons.pause.off")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        return 1;
    }

    /** Le regime du monde, choisi a la commande ou par un bouton du chat. */
    private static int chooseRegime(CommandSourceStack source, GameState.Mode mode) {
        return com.emerald.game.ModeChoice.choose(source.getServer().overworld(), mode) ? 1 : 0;
    }

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
            // ce chemin court-circuite openTheGame : on rearme l'ordonnanceur
            // ici aussi, sans quoi la partie d'essai ne commence pas par l'Aurore
            com.emerald.weather.WeatherManager.resetSchedule();
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
        // pour l'essai : declenche le TIRAGE tout de suite, au lieu d'attendre
        // les deux a quatre minutes de l'ecart normal
        weatherNode.then(Commands.literal("suivante").executes(ctx -> {
            com.emerald.weather.WeatherManager.rollNow();
            ctx.getSource().sendSuccess(() -> Component.literal("Tirage force a la tique suivante."), false);
            return 1;
        }));
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

        // ------------------------------------------------------------- finale
        // pour l'essai : leve l'Arc-en-ciel devant le joueur, avec le boss voulu
        // ou tire au sort ; "win" et "lose" jouent les deux fins sans attendre
        var finaleNode = Commands.literal("finale")
                .executes(ctx -> raiseFinale(ctx.getSource(), null))
                .then(Commands.literal("win").executes(ctx -> {
                    Finale.victory(ctx.getSource().getServer().overworld());
                    return 1;
                }))
                .then(Commands.literal("lose").executes(ctx -> {
                    Finale.defeat(ctx.getSource().getServer().overworld());
                    return 1;
                }))
                .then(Commands.argument("boss",
                                com.mojang.brigadier.arguments.StringArgumentType.string())
                        .executes(ctx -> raiseFinale(ctx.getSource(),
                                com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "boss"))));
        root.then(finaleNode);

        // -------------------------------------------------------------- autel
        // pour l'essai : ouvre l'Autel de Specialisation sans avoir a poser le bloc
        root.then(Commands.literal("autel").executes(ctx -> {
            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) ->
                        new com.emerald.menu.SpecializationAltarMenu(id, inventory,
                                net.minecraft.world.inventory.ContainerLevelAccess.NULL),
                        com.emerald.block.SpecializationAltarBlock.TITLE));
                return 1;
            }
            return 0;
        }));

        // ------------------------------------------------------------- etabli
        // pour l'essai : l'etabli a sertir sans son ecran. La piece en main, ce
        // qu'on pose en main gauche ; le menu reel calcule et prend, le journal
        // dit ce qui en sort. `/arcencium etabli` ouvre l'ecran, `essai` le joue.
        var bench = Commands.literal("etabli");
        bench.executes(ctx -> {
            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) ->
                        new com.emerald.menu.SocketBenchMenu(id, inventory,
                                net.minecraft.world.inventory.ContainerLevelAccess.NULL),
                        com.emerald.block.SocketBenchBlock.TITLE));
                return 1;
            }
            return 0;
        });
        bench.then(Commands.literal("essai").executes(ctx -> benchTrial(ctx.getSource())));
        root.then(bench);

        // -------------------------------------------------------------- forge
        // pour l'essai : ouvre la Forge d'Arcencium sans avoir a poser le bloc
        root.then(Commands.literal("forge").executes(ctx -> {
            if (ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) ->
                        new com.emerald.menu.ArcenciumForgeMenu(id, inventory,
                                net.minecraft.world.inventory.ContainerLevelAccess.NULL),
                        com.emerald.block.ArcenciumForgeBlock.TITLE));
                return 1;
            }
            return 0;
        }));

        // -------------------------------------------------------------- ailes
        // pour l'essai : le palier de specialisation (0-20) et l'apparence des ailes
        var wingsNode = Commands.literal("ailes")
                .then(Commands.argument("palier",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 20))
                        .executes(ctx -> setWings(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "palier"), null)));
        for (com.emerald.specialization.WingSkin skin : com.emerald.specialization.WingSkin.values()) {
            wingsNode.then(Commands.argument("palier_",
                            com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 20))
                    .then(Commands.literal(skin.id())
                            .executes(ctx -> setWings(ctx.getSource(),
                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "palier_"),
                                    skin))));
        }
        // l'essai de la mecanique : une tentative, des plumes, une plume d'apparence
        wingsNode.then(Commands.literal("tenter").executes(ctx -> attemptWings(ctx.getSource())));
        wingsNode.then(Commands.literal("plumes")
                .then(Commands.argument("nombre",
                                com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 999))
                        .executes(ctx -> giveFeathers(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "nombre")))));
        // POUR LES ESSAIS : porter une apparence sans passer par la plume.
        // `apparence` DONNE la plume, ce qui est juste en jeu mais impraticable
        // quand on veut simplement regarder une aile.
        var wearNode = Commands.literal("porte");
        for (com.emerald.specialization.WingSkin skin : com.emerald.specialization.WingSkin.values()) {
            wearNode.then(Commands.literal(skin.id()).executes(ctx -> {
                if (!(ctx.getSource().getEntity()
                        instanceof net.minecraft.server.level.ServerPlayer player)) {
                    return 0;
                }
                com.emerald.specialization.Specialization.set(player,
                        com.emerald.specialization.Specialization.level(player), skin);
                ctx.getSource().sendSuccess(() -> Component.literal("Ailes : " + skin.id()), false);
                return 1;
            }));
        }
        wingsNode.then(wearNode);

        var skinNode = Commands.literal("apparence");
        for (com.emerald.specialization.WingSkin skin : com.emerald.specialization.WingSkin.values()) {
            skinNode.then(Commands.literal(skin.id()).executes(ctx -> giveSkinFeather(ctx.getSource(), skin)));
        }
        wingsNode.then(skinNode);
        root.then(wingsNode);

        // OUVRIR LA PARTIE SANS JOUER LE PROLOGUE. Tout ce qui ne vit qu'en
        // phase RUNNING -- la Traque, la Maree, les meteos tardives -- etait
        // invisible aux essais : apres `setup` on reste en Prologue tant que
        // le siege du village n'est pas gagne.
        root.then(Commands.literal("open").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            GameManager.openNow(level);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Partie ouverte : les ancres sont levees.")
                    .withStyle(net.minecraft.ChatFormatting.AQUA), true);
            return 1;
        }));
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

        // LE SOUS-SOL, a la demande : de quoi voir une percee ou un echo sans
        // casser quarante-cinq pierres en esperant.
        var percee = Commands.literal("percee");
        for (com.emerald.mine.Breakthrough.Shape shape : com.emerald.mine.Breakthrough.Shape.values()) {
            final com.emerald.mine.Breakthrough.Shape wanted = shape;
            percee.then(Commands.literal(shape.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                boolean ok = com.emerald.mine.Breakthrough.open(player.serverLevel(), player,
                        player.blockPosition().relative(player.getDirection()), wanted);
                ctx.getSource().sendSuccess(() -> Component.literal(ok
                        ? "Percee : " + wanted + " ouverte"
                        : "Percee refusee : l'emprise n'est pas que de la roche"), false);
                return ok ? 1 : 0;
            }));
        }
        root.then(percee);
        // Les grottes de l'Aurore, a la demande : ouvrir, puis refermer.
        // la Chambre d'Aurore, sans casser quarante pierres : ouvre-la devant soi
        root.then(Commands.literal("chambre").executes(ctx -> {
            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
            boolean ok = com.emerald.mine.AuroreChamber.open(player.serverLevel(), player);
            ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Chambre : la roche cede."
                    : "Chambre : l'emprise n'est pas de la roche pleine."), false);
            return ok ? 1 : 0;
        }));
        root.then(Commands.literal("grottes")
                .then(Commands.literal("ouvrir").executes(ctx -> {
                    com.emerald.mine.AuroreCaves.begin(ctx.getSource().getServer().overworld());
                    return 1;
                }))
                .then(Commands.literal("fermer").executes(ctx -> {
                    com.emerald.mine.AuroreCaves.end(ctx.getSource().getServer().overworld());
                    return 1;
                })));
        var echo = Commands.literal("echo");
        for (com.emerald.mine.Echoes.Kind kind : com.emerald.mine.Echoes.Kind.values()) {
            final com.emerald.mine.Echoes.Kind wanted = kind;
            echo.then(Commands.literal(kind.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                com.emerald.mine.Echoes.fire(player.serverLevel(), player, wanted);
                return 1;
            }));
        }
        root.then(echo);
        var poche = Commands.literal("poche");
        for (com.emerald.mine.Pockets.Kind kind : com.emerald.mine.Pockets.Kind.values()) {
            final com.emerald.mine.Pockets.Kind wanted = kind;
            poche.then(Commands.literal(kind.name().toLowerCase(java.util.Locale.ROOT)).executes(ctx -> {
                net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
                boolean ok = com.emerald.mine.Pockets.open(player.serverLevel(),
                        player.blockPosition().relative(player.getDirection()), player.getDirection(), wanted);
                ctx.getSource().sendSuccess(() -> Component.literal(ok
                        ? "Poche : " + wanted + " ouverte"
                        : "Poche refusee : l'emprise n'est pas que de la roche"), false);
                return ok ? 1 : 0;
            }));
        }
        root.then(poche);
        root.then(Commands.literal("resonance").executes(ctx -> {
            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
            BlockPos at = player.blockPosition().relative(player.getDirection(), 2);
            int echoes = com.emerald.mine.Resonance.echo(player.serverLevel(), player, at,
                    net.minecraft.world.level.block.Blocks.DIAMOND_ORE);
            ctx.getSource().sendSuccess(() -> Component.literal("Resonance : " + echoes + " echo(s)"), false);
            return echoes;
        }));

        // LA PAUSE : « parfois je dois m'absenter temporairement ».
        root.then(Commands.literal("pause").executes(ctx -> setPaused(ctx.getSource(), true)));
        root.then(Commands.literal("reprendre")
                .executes(ctx -> setPaused(ctx.getSource(), false)));

        // LE REGIME DU MONDE : defi chronometre, ou monde ouvert.
        //
        // Distinct de « mode on/off », qui est l'interrupteur de fabrication.
        // Celui-ci se choisit une fois par monde, avant de tirer la Lame, et
        // les deux boutons du chat n'appellent rien d'autre que cette commande.
        root.then(Commands.literal("partie")
                .then(Commands.literal("defi").executes(ctx -> chooseRegime(
                        ctx.getSource(), GameState.Mode.DEFI)))
                .then(Commands.literal("libre").executes(ctx -> chooseRegime(
                        ctx.getSource(), GameState.Mode.LIBRE)))
                .executes(ctx -> {
                    com.emerald.game.ModeChoice.ask(ctx.getSource().getPlayerOrException());
                    return 1;
                }));

        // Reprendre ICI le personnage global d'avant la separation par monde.
        root.then(Commands.literal("personnage")
                .then(Commands.literal("importer").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getServer().overworld();
                    boolean done = com.emerald.specialization.SpecializationStore
                            .importLegacy(level.getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal(done
                            ? "Personnage global repris dans ce monde."
                            : "Rien a reprendre : aucun personnage global en attente."),
                            true);
                    return done ? 1 : 0;
                })));

        // L'interrupteur du mode : c'est ce qui permet d'aller EXPLORER sans
        // avoir a gagner le prologue d'abord.
        root.then(Commands.literal("mode")
                .then(Commands.literal("on").executes(ctx -> switchMode(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> switchMode(ctx.getSource(), false))));

        // Le sanctuaire, bati sur place : c'est l'outil pour le regarder en
        // monde plat sans jouer une partie entiere pour l'atteindre.
        // LE CYCLE SUIVANT, A LA DEMANDE.
        //
        // `/arcencium sanctuary` batit d'un seul tenant, sous les pieds du
        // joueur, sur du terrain qu'il vient de charger : c'est commode pour
        // regarder un batiment, et cela ne mesure RIEN du chantier reel, qui
        // se fait par etapes a quatre cent cinquante blocs de la, sur du sol
        // vierge. La seule autre facon d'y arriver etait de gagner la finale.
        root.then(Commands.literal("cycle").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            GameManager.raiseNextCycle(level);
            return 1;
        }));

        // LE CARNET, RELU A LA DEMANDE : l'etape en cours, avec sa recette.
        root.then(Commands.literal("quete").executes(ctx -> {
            if (!(ctx.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) {
                ctx.getSource().sendFailure(Component.literal("A executer en jeu."));
                return 0;
            }
            com.emerald.quest.Quests.tell(p, com.emerald.quest.Quests.step(p));
            return 1;
        }));

        // Les quartiers de Jak 3, convertis en blocs par tools/jak_voxelize.py.
        // C'est un outil d'essai : on les pose ou l'on se trouve, en monde plat.
        root.then(Commands.literal("jak")
                .then(Commands.literal("stop").executes(ctx -> {
                    com.emerald.jak.JakBuilder.cancel();
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            "command.emeraldweapons.jak.stopped"), true);
                    return 1;
                }))
                .then(Commands.argument("quartier", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (String name : com.emerald.jak.JakVolume.available(
                                    ctx.getSource().getServer())) {
                                builder.suggest(name);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> placeJak(ctx.getSource(),
                                StringArgumentType.getString(ctx, "quartier")))));

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
            String where = com.emerald.game.SanctuarySeals.describe(level, found);
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
        // LE SAC D'ARCENCIUM : le redonner, compter ce qu'on porte (poches
        // comprises), et payer un cran de Forge sur la piece en main par le
        // meme chemin que la Forge. Les deux derniers sont ce que le banc lit.
        var sac = Commands.literal("sac").executes(ctx -> {
            if (!(ctx.getSource().getEntity()
                    instanceof net.minecraft.server.level.ServerPlayer player)) {
                ctx.getSource().sendFailure(Component.literal("A executer en jeu."));
                return 0;
            }
            if (!net.neoforged.fml.ModList.get().isLoaded("sophisticatedbackpacks")) {
                ctx.getSource().sendFailure(Component.literal(
                        "Sophisticated Backpacks n'est pas charge : pas de sac."));
                return 0;
            }
            ArcenciumBackpack.give(player);
            return 1;
        });
        sac.then(Commands.literal("compte").executes(ctx -> {
            if (!(ctx.getSource().getEntity()
                    instanceof net.minecraft.server.level.ServerPlayer player)) {
                ctx.getSource().sendFailure(Component.literal("A executer en jeu."));
                return 0;
            }
            StringBuilder line = new StringBuilder("POCHES");
            for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[]{
                    com.emerald.item.ModItems.FATE_SHARD.get(),
                    com.emerald.item.ModItems.FORGE_STONE.get(),
                    com.emerald.item.ModItems.ARCENCIUM_INGOT.get(),
                    com.emerald.item.ModItems.RAW_ARCENCIUM.get(),
                    com.emerald.item.ModItems.ARCENCIUM_FEATHER.get(),
                    net.minecraft.world.item.Items.IRON_INGOT,
                    net.minecraft.world.item.Items.COAL,
                    net.minecraft.world.item.Items.ROTTEN_FLESH}) {
                line.append(' ').append(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(item).getPath())
                        .append('=').append(com.emerald.item.Stash.count(player, item));
            }
            line.append(" sacs=").append(com.emerald.item.Stash.bags(player).size());
            final String text = line.toString();
            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
            return 1;
        }));
        sac.then(Commands.literal("payer").executes(ctx -> {
            if (!(ctx.getSource().getEntity()
                    instanceof net.minecraft.server.level.ServerPlayer player)) {
                ctx.getSource().sendFailure(Component.literal("A executer en jeu."));
                return 0;
            }
            net.minecraft.world.item.ItemStack gear = player.getMainHandItem();
            boolean can = com.emerald.menu.ArcenciumForgeMenu.canPay(player, gear);
            boolean paid = can && com.emerald.menu.ArcenciumForgeMenu.pay(player, gear);
            final String text = "PAYER " + (can ? "possible" : "impossible") + " "
                    + (paid ? "paye" : "non_paye");
            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
            return paid ? 1 : 0;
        }));
        root.then(sac);

        root.then(Commands.literal("what").executes(ctx -> {
            if (!(ctx.getSource().getEntity()
                    instanceof net.minecraft.server.level.ServerPlayer player)) {
                ctx.getSource().sendFailure(Component.literal("A executer en jeu."));
                return 0;
            }
            ServerLevel level = player.serverLevel();
            // ET LA REGLE DU SOUS-SOL, A CET ENDROIT PRECIS.
            //
            // « J'ai mine dix minutes, il ne s'est rien passe. » La reponse
            // etait dans une regle qu'aucune commande ne disait : il minait
            // sous le village, dans la zone de paix. On la rend lisible : une
            // ligne dit si la roche a le droit de vivre ici, et sinon
            // pourquoi.
            BlockPos feet = player.blockPosition();
            var gs = com.emerald.game.GameState.get(level);
            int toVillage = (int) Math.round(
                    com.emerald.mine.Underground.flat(gs.village(), feet));
            final String rule = String.format(
                    "sous-sol a vos pieds (y=%d) : %s | village a %d blocs | plafond y=%d",
                    feet.getY(),
                    com.emerald.mine.Underground.allowed(level, feet) ? "AUTORISE" : "interdit",
                    toVillage, com.emerald.mine.Underground.CEILING);
            ctx.getSource().sendSuccess(() -> Component.literal(rule), false);
            // ET CE QUE LES ETABLIS PENSENT DE CE QU'ON TIENT.
            //
            // « Est-ce que cette epee-la passe ? » ne se repondait qu'en
            // traversant le village avec, et en regardant si l'etabli refusait.
            // La commande le dit d'un coup : la famille de rune qui l'accepte,
            // et les deux plafonds qui s'y appliquent.
            net.minecraft.world.item.ItemStack held = player.getMainHandItem();
            String family = "refusee par les etablis";
            for (com.emerald.rune.RuneFamily f : com.emerald.rune.RuneFamily.values()) {
                if (f.accepts(held)) {
                    family = family.startsWith("refusee") ? f.name() : family + "+" + f.name();
                }
            }
            final String gear = held.isEmpty() ? "en main : rien"
                    : String.format("en main : %s | %s | plafond +%d, %s",
                            net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getKey(held.getItem()),
                            family,
                            com.emerald.item.GearEligibility.upgradeMax(held),
                            com.emerald.item.GearRarity.values()[
                                    com.emerald.item.GearEligibility.rarityMax(held)]
                                    .label().getString());
            ctx.getSource().sendSuccess(() -> Component.literal(gear), false);
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
        // DEUX NOMS POUR CHAQUE FAMILLE : celui du code (weapon / armor) et
        // celui qu'on tape (arme / armure). Le joueur ecrit en francais.
        var rune = Commands.literal("rune");
        for (com.emerald.rune.RuneFamily family : com.emerald.rune.RuneFamily.values()) {
            String french = family == com.emerald.rune.RuneFamily.WEAPON ? "arme" : "armure";
            for (String word : new String[]{family.getSerializedName(), french}) {
                rune.then(Commands.literal(word)
                        .then(Commands.argument("rarete",
                                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 8))
                                .executes(ctx -> giveRune(ctx.getSource(), family,
                                        com.mojang.brigadier.arguments.IntegerArgumentType
                                                .getInteger(ctx, "rarete"), 1))
                                .then(Commands.argument("combien",
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .integer(1, 64))
                                        .executes(ctx -> giveRune(ctx.getSource(), family,
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(ctx, "rarete"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType
                                                        .getInteger(ctx, "combien"))))));
            }
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

    /**
     * Pose un quartier de Jak 3 au pied du joueur.
     *
     * On refuse d'en lancer deux a la fois : la pose s'etale sur plusieurs
     * secondes, et deux chantiers concurrents se marcheraient dessus sans
     * qu'on puisse dire lequel a ecrit quoi.
     */
    private static int placeJak(CommandSourceStack source, String name) {
        if (com.emerald.jak.JakBuilder.busy()) {
            source.sendFailure(Component.translatable("command.emeraldweapons.jak.busy"));
            return 0;
        }
        ServerLevel level = source.getServer().overworld();
        com.emerald.jak.JakVolume volume =
                com.emerald.jak.JakVolume.load(source.getServer(), name);
        if (volume == null) {
            source.sendFailure(Component.translatable(
                    "command.emeraldweapons.jak.missing", name));
            return 0;
        }
        net.minecraft.core.BlockPos origin =
                net.minecraft.core.BlockPos.containing(source.getPosition());
        com.emerald.jak.JakBuilder.start(level, volume, origin,
                source.getEntity() instanceof net.minecraft.server.level.ServerPlayer p ? p : null);
        source.sendSuccess(() -> Component.translatable(
                "command.emeraldweapons.jak.started", name,
                volume.width(), volume.height(), volume.depth()), true);
        return 1;
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
    /** Joue l'etabli sur la piece en main et l'objet en main gauche, et dit ce qui en sort. */
    private static int benchTrial(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("A executer en jeu."));
            return 0;
        }
        net.minecraft.world.item.ItemStack gear = player.getMainHandItem().copy();
        net.minecraft.world.item.ItemStack fee = player.getOffhandItem().copy();
        com.emerald.menu.SocketBenchMenu menu = new com.emerald.menu.SocketBenchMenu(
                0, player.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.NULL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        menu.getSlot(com.emerald.menu.SocketBenchMenu.SLOT_GEAR).set(gear);
        menu.getSlot(com.emerald.menu.SocketBenchMenu.SLOT_ARTIFACT).set(fee);
        net.minecraft.world.item.ItemStack result =
                menu.getSlot(com.emerald.menu.SocketBenchMenu.SLOT_RESULT).getItem();
        String outcome;
        if (result.isEmpty()) {
            outcome = "refuse";
        } else {
            net.minecraft.world.item.ItemStack taken = result.copy();
            menu.getSlot(com.emerald.menu.SocketBenchMenu.SLOT_RESULT).onTake(player, taken);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, taken);
            outcome = "pris";
        }
        // ce qui reste pose revient au joueur, comme a la fermeture de l'ecran
        menu.removed(player);
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        LOGGER.info("Etabli : {} ; piece {} avec {} rune(s), cran +{}, rarete {} ; main gauche {}",
                outcome, held.getItem(), com.emerald.rune.Runes.on(held).size(),
                com.emerald.item.Upgrade.of(held), com.emerald.item.GearRarity.of(held).rank(),
                player.getOffhandItem().isEmpty() ? "vide" : player.getOffhandItem().getItem());
        source.sendSuccess(() -> Component.literal("Etabli : " + outcome), false);
        return 1;
    }

    private static int giveRune(CommandSourceStack source,
                                com.emerald.rune.RuneFamily family, int rank, int count) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("A executer en jeu."));
            return 0;
        }
        for (int i = 0; i < count; i++) {
            com.emerald.rune.RuneMark mark = com.emerald.rune.RuneMark.roll(family, rank, player.getRandom());
            // le journal dit ce qui a ete tire : c'est ce que lit le banc d'essai
            StringBuilder lines = new StringBuilder();
            for (com.emerald.rune.RuneMark.Option option : mark.options()) {
                lines.append(option.grade().name()).append(':')
                        .append(option.stat().getSerializedName()).append('=')
                        .append(String.format(java.util.Locale.ROOT, "%.2f", option.value())).append(' ');
            }
            LOGGER.info("Rune {} rang {} : {} option(s) {}", family.getSerializedName(), rank,
                    mark.options().size(), lines.toString().trim());
            // DANS LE SAC, ou aux pieds s'il est plein : `add` rend faux et
            // JETTE la pile en silence, ce qui perdait les runes au-dela de la
            // trente-sixieme sans le dire.
            net.minecraft.world.item.ItemStack stack =
                    com.emerald.rune.RuneItem.stack(mark, com.emerald.item.ModItems.RUNE.get());
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        com.emerald.item.GearRarity rarity = com.emerald.item.GearRarity.values()[rank];
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.rune.given",
                count,
                Component.translatable("rune.emeraldweapons.family." + family.getSerializedName() + ".short"),
                rarity.label(), com.emerald.rune.RuneMark.pattern(rank))
                .withStyle(style -> style.withColor(rarity.colour())), false);
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


    private static int setWings(CommandSourceStack source, int level,
                                com.emerald.specialization.WingSkin skin) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Il faut un joueur."));
            return 0;
        }
        com.emerald.specialization.WingSkin chosen = skin != null ? skin
                : com.emerald.specialization.Specialization.skin(player);
        com.emerald.specialization.Specialization.set(player, level, chosen);
        source.sendSuccess(() -> Component.literal("Ailes +" + level + " (" + chosen.id() + ")"), true);
        return 1;
    }


    private static int attemptWings(CommandSourceStack source) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Il faut un joueur."));
            return 0;
        }
        com.emerald.specialization.Specialization.Attempt result =
                com.emerald.specialization.Specialization.tryUpgrade(player);
        source.sendSuccess(() -> Component.literal("Tentative : " + result + ", palier "
                + com.emerald.specialization.Specialization.level(player)), true);
        return result == com.emerald.specialization.Specialization.Attempt.SUCCESS ? 1 : 0;
    }

    private static int giveFeathers(CommandSourceStack source, int count) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Il faut un joueur."));
            return 0;
        }
        player.getInventory().placeItemBackInInventory(
                new net.minecraft.world.item.ItemStack(com.emerald.item.ModItems.ARCENCIUM_FEATHER.get(), count));
        source.sendSuccess(() -> Component.literal(count + " plume(s) d'Arcencium"), true);
        return 1;
    }

    private static int giveSkinFeather(CommandSourceStack source, com.emerald.specialization.WingSkin skin) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Il faut un joueur."));
            return 0;
        }
        player.getInventory().placeItemBackInInventory(com.emerald.item.SkinFeatherItem.stack(
                skin, com.emerald.item.ModItems.SKIN_FEATHER.get()));
        source.sendSuccess(() -> Component.literal("Plume d'ailes : " + skin.id()), true);
        return 1;
    }

    /** L'Arc-en-ciel se leve a 120 blocs devant le joueur : on le voit se poser. */
    private static int raiseFinale(CommandSourceStack source, @javax.annotation.Nullable String boss) {
        ServerLevel level = source.getServer().overworld();
        BlockPos site = null;
        if (source.getEntity() != null) {
            var look = source.getEntity().getLookAngle();
            double len = Math.max(0.05, Math.hypot(look.x, look.z));
            site = BlockPos.containing(source.getPosition().x + look.x / len * 120.0, 0,
                    source.getPosition().z + look.z / len * 120.0);
        }
        BlockPos center = Finale.begin(level, site, boss);
        if (center == null) {
            source.sendFailure(Component.translatable("command.emeraldweapons.finale.unknown",
                    boss == null ? "?" : boss));
            return 0;
        }
        String chosen = GameState.get(level).finaleBoss();
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.finale",
                center.getX(), center.getY(), center.getZ(), chosen), true);
        return 1;
    }
}
