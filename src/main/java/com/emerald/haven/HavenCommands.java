package com.emerald.haven;

import com.emerald.game.GameState;
import com.emerald.game.WorldSetup;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
                        .then(Commands.literal("off").executes(ctx -> chantier(ctx.getSource(), false))))
                .then(Commands.literal("ouvrir").executes(ctx -> open(ctx.getSource())))
                .then(Commands.literal("skip").executes(ctx -> skip(ctx.getSource())))
                // « salle <n> » : MEME nom et MEME type d'argument que HavenRoomCommands
                // (capture, show, reset) -- Brigadier fusionne les deux noeuds, et
                // l'execution posee ici devient celle de « salle <n> » seul.
                .then(Commands.literal("salle")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> room(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "n")))
                                .then(Commands.literal("tp").executes(ctx -> room(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n")))))));
        event.getDispatcher().register(root);
    }

    /**
     * Rouvre le lobby, comme une nouvelle partie : la Lame est replantee
     * d'abord (GameManager.setup, sans accueil au village), puis le regime
     * redevient a voter et chacun retourne dans son appartement.
     */
    private static int open(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (!HavenArrival.canOpen(server)) {
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.open.unavailable"));
            return 0;
        }
        ServerLevel overworld = server.overworld();
        com.emerald.game.GameManager.clear();
        com.emerald.game.GameManager.setup(overworld, overworld.getSharedSpawnPos(), false);
        int moved = HavenArrival.reopen(server);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.open", moved), true);
        return 1;
    }

    /**
     * Pour les essais : le depart tout de suite, avec le regime courant, sans vote.
     *
     * Seulement lobby ouvert : une phase CHANTIER sans pose en cours (pose
     * echouee) ou une partie deja commencee se jouent au village, et le depart
     * y viderait les poches de tout le serveur.
     */
    private static int skip(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        HavenState.Phase phase = HavenState.get(server).phase();
        if (!HavenArrival.lobbyOpen(server)) {
            String why = HavenArrival.gameWaiting(server) ? phase.name()
                    : phase.name() + ", " + GameState.get(server.overworld()).status().name();
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.skip.closed", why));
            return 0;
        }
        GameState.Mode mode = GameState.get(server.overworld()).mode();
        HavenVote.depart(server, mode);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.skip",
                HavenVote.modeName(mode)), true);
        return 1;
    }

    /** Dans un appartement, tourne vers sa porte. Ni inscription, ni point de reapparition. */
    private static int room(CommandSourceStack source, int number) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel level = Haven.level(server);
        if (level == null) {
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.missing_level"));
            return 0;
        }
        HavenArrival.Layout layout = HavenArrival.layout(server);
        if (layout == null) {
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.layout_missing"));
            return 0;
        }
        if (number > layout.rooms().size()) {
            source.sendFailure(Component.translatable("command.emeraldweapons.haven.room.missing",
                    number, layout.rooms().size()));
            return 0;
        }
        HavenArrival.Room room = layout.rooms().get(number - 1);
        BlockPos origin = HavenState.get(server).origin();
        BlockPos feet = HavenArrival.standIn(level, origin, room, 0);
        player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5,
                room.yawToDoor(feet.subtract(origin)), 0.0F);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.room",
                number, feet.toShortString()), false);
        return 1;
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
