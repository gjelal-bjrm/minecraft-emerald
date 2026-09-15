package com.emerald.haven.invasion;

import com.emerald.haven.Haven;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /arcencium haven invasion etat | invasion | paisible | reconstruire
 *
 * Pour l'operateur : le meme basculement que le bouton du QG, l'etat de la ville,
 * et la reconstruction immediate des blocs casses. Brigadier fusionne ces
 * litteraux avec ceux de HavenCommands.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenInvasionCommands {

    private HavenInvasionCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcencium")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("haven").then(Commands.literal("invasion")
                .then(Commands.literal("etat").executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("invasion").executes(ctx -> set(ctx.getSource(), HavenInvasion.Mode.INVASION)))
                .then(Commands.literal("paisible").executes(ctx -> set(ctx.getSource(), HavenInvasion.Mode.PAISIBLE)))
                .then(Commands.literal("reconstruire").executes(ctx -> rebuild(ctx.getSource())))));
        event.getDispatcher().register(root);
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        HavenInvasion.Mode mode = HavenInvasion.mode(server);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.invasion.status",
                HavenInvasion.modeName(mode), HavenInvasion.monsters().size(), HavenInvasion.villagers().size(),
                HavenDestruction.pendingCount(server)), false);
        return 1;
    }

    private static int set(CommandSourceStack source, HavenInvasion.Mode mode) {
        MinecraftServer server = source.getServer();
        if (!HavenInvasion.setMode(server, mode, source.getDisplayName())) {
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.invasion.same",
                    HavenInvasion.modeName(mode)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.invasion.set",
                HavenInvasion.modeName(mode)), true);
        return 1;
    }

    private static int rebuild(CommandSourceStack source) {
        ServerLevel level = Haven.level(source.getServer());
        if (level == null) {
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.missing_level"));
            return 0;
        }
        int rebuilt = HavenDestruction.rebuildAll(level);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.invasion.rebuilt", rebuilt), true);
        return 1;
    }
}
