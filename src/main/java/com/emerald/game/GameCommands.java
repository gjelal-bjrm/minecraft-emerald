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
}
