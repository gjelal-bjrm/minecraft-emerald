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
            BlockPos centre = com.emerald.game.SanctuaryMist.nearestCentre(at);
            String where;
            if (centre == null) {
                where = "aucun sanctuaire enregistre";
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

        root.then(Commands.literal("goto").executes(ctx -> {
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
}
