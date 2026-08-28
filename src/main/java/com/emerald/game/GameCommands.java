package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
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

        // Le sanctuaire, bati sur place : c'est l'outil pour le regarder en
        // monde plat sans jouer une partie entiere pour l'atteindre.
        root.then(Commands.literal("sanctuary").executes(ctx -> {
            ServerLevel level = ctx.getSource().getServer().overworld();
            var pos = net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition());
            var ground = new net.minecraft.core.BlockPos(pos.getX(),
                    WorldSetup.surfaceY(level, pos.getX(), pos.getZ()) - 1, pos.getZ());
            var anchor = Sanctuary.build(level, ctx.getSource(), ground);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.emeraldweapons.sanctuary",
                    anchor.getX(), anchor.getY(), anchor.getZ()), true);
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

    private static int forceWeather(CommandSourceStack source,
                                    com.emerald.weather.Weather weather, int seconds) {
        com.emerald.weather.WeatherManager.force(source.getServer().overworld(),
                weather, seconds * 20);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.weather.set",
                Component.translatable(weather.translationKey())), true);
        return 1;
    }
}
