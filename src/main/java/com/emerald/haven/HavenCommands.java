package com.emerald.haven;

import com.emerald.game.GameState;
import com.emerald.game.WorldSetup;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Les commandes de la ville : /arcencium haven build | rebuild | tp | back | chantier on|off.
 *
 * Enregistrees ici plutot que dans GameCommands. Brigadier FUSIONNE les
 * litteraux de meme nom : le second « arcencium » enregistre ne remplace pas le
 * premier, ses enfants s'y ajoutent (CommandNode.addChild). Les deux racines
 * portent la meme exigence de permission, quel que soit l'ordre.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenCommands {

    private HavenCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcencium")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("haven")
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource(), false)))
                .then(Commands.literal("rebuild").executes(ctx -> build(ctx.getSource(), true)))
                .then(Commands.literal("tp").executes(ctx -> tp(ctx.getSource())))
                .then(Commands.literal("back").executes(ctx -> back(ctx.getSource())))
                .then(Commands.literal("chantier")
                        .then(Commands.literal("on").executes(ctx -> chantier(ctx.getSource(), true)))
                        .then(Commands.literal("off").executes(ctx -> chantier(ctx.getSource(), false)))));
        event.getDispatcher().register(root);
    }

    /**
     * Pose la ville, etalee sur les ticks.
     *
     * « rebuild » remet d'abord la boite a l'etat du generateur et en retire
     * les entites : c'est ce qui permet de reposer une nouvelle version du
     * volume dans le meme monde, sans garder un bloc de l'ancienne.
     */
    private static int build(CommandSourceStack source, boolean reset) {
        Component failure = HavenSite.start(source.getServer(), reset, HavenSite.Mode.ETALE,
                source.getPlayer());
        if (failure != null) {
            source.sendFailure(failure);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(reset
                ? "command.emeraldweapons.haven.rebuild"
                : "command.emeraldweapons.haven.build", Haven.VOLUME), true);
        return 1;
    }

    /** Dans la rue devant le bar, tourne vers sa porte. */
    private static int tp(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel level = Haven.level(server);
        if (level == null) {
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.missing_level"));
            return 0;
        }
        HavenState state = HavenState.get(server);
        BlockPos feet = state.origin().offset(Haven.BAR_FRONT_CELL);
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                Haven.BAR_FRONT_YAW, 0.0F);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.tp",
                feet.getY()), false);
        if (!state.built()) {
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.not_built"));
        }
        return 1;
    }

    /** Au village de l'overworld, ou au point d'apparition s'il n'y en a pas encore. */
    private static int back(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().overworld();
        BlockPos village = GameState.get(overworld).village();
        BlockPos stand = village.equals(BlockPos.ZERO)
                ? WorldSetup.findOpenGround(overworld, overworld.getSharedSpawnPos(), 16)
                : WorldSetup.findOpenGround(overworld, village.offset(4, 0, 4), 12);
        player.teleportTo(overworld, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.back"), false);
        return 1;
    }

    private static int chantier(CommandSourceStack source, boolean on) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HavenRules.setChantier(player, on);
        source.sendSuccess(() -> Component.translatable(on
                ? "command.emeraldweapons.haven.chantier.on"
                : "command.emeraldweapons.haven.chantier.off", player.getName()), true);
        return 1;
    }
}
