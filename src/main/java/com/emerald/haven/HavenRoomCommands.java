package com.emerald.haven;

import com.emerald.jak.JakVolume;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Les commandes des salles : /arcencium haven salle &lt;n&gt; capture | show | reset.
 *
 * Dans leur propre classe, a cote de HavenCommands : Brigadier FUSIONNE les
 * noeuds de meme nom (CommandNode.addChild), si bien que « arcencium », « haven »,
 * « salle » et l'argument « n » enregistres ici recoivent aussi les enfants
 * qu'une autre classe y accroche -- « salle &lt;n&gt; tp », par exemple. L'argument
 * garde donc ce nom et ce type d'entier.
 *
 * Le releve se fait aussi a la Sonde : clic droit dans le vide, debout dans la
 * salle. La commande sert quand on ne tient pas la Sonde, ou depuis la console.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenRoomCommands {

    private HavenRoomCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcencium")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("haven")
                .then(Commands.literal("salle")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .then(Commands.literal("capture").executes(ctx -> capture(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n"))))
                                .then(Commands.literal("show").executes(ctx -> show(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n"))))
                                .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "n")))))));
        event.getDispatcher().register(root);
    }

    /** Ou vont les reponses : le tchat d'une commande, ou celui du joueur qui tient la Sonde. */
    private record Out(Consumer<Component> ok, Consumer<Component> fail) {

        static Out of(CommandSourceStack source) {
            return new Out(c -> source.sendSuccess(() -> c, false), source::sendFailure);
        }

        static Out of(ServerPlayer player) {
            return new Out(c -> player.sendSystemMessage(c.copy().withStyle(ChatFormatting.AQUA)),
                    c -> player.sendSystemMessage(c.copy().withStyle(ChatFormatting.RED)));
        }
    }

    // ------------------------------------------------------------ commandes

    private static int capture(CommandSourceStack source, int number) {
        HavenRooms.Room room = room(source.getServer(), number, Out.of(source));
        return room != null && capture(source.getServer(), room, source.getTextName(), Out.of(source)) ? 1 : 0;
    }

    private static int show(CommandSourceStack source, int number) {
        MinecraftServer server = source.getServer();
        Out out = Out.of(source);
        HavenRooms.Room room = room(server, number, out);
        if (room == null) {
            return 0;
        }
        Path file = JakDiff.directory(server).resolve(room.id() + ".nbt");
        CompoundTag tag = JakDiff.read(file);
        if (tag == null) {
            out.ok().accept(Component.translatable("command.emeraldweapons.haven.salle.show.none",
                    number, file.toString()));
        } else {
            out.ok().accept(Component.translatable("command.emeraldweapons.haven.salle.show", number,
                    tag.getList("cells", Tag.TAG_COMPOUND).size(), tag.getList("entities", Tag.TAG_COMPOUND).size(),
                    tag.getString("captured"), tag.getString("author"), file.toString()));
        }
        CompoundTag zone = JakOverlay.zone(server, room.id());
        if (zone == null) {
            out.ok().accept(Component.translatable("command.emeraldweapons.haven.salle.show.no_zone"));
        } else {
            out.ok().accept(Component.translatable("command.emeraldweapons.haven.salle.show.zone",
                    Haven.VOLUME + "/" + room.id() + ".nbt", zone.getList("cells", Tag.TAG_COMPOUND).size(),
                    zone.getList("entities", Tag.TAG_COMPOUND).size(), zone.getString("captured")));
        }
        return 1;
    }

    /**
     * Remet la salle a l'etat du volume dans le monde, et efface son releve.
     *
     * L'amenagement du mod (zones/) n'est pas touche : il se rejouera a la
     * prochaine pose. On repart ainsi d'une salle vide pour un nouvel essai.
     */
    private static int reset(CommandSourceStack source, int number) {
        MinecraftServer server = source.getServer();
        Out out = Out.of(source);
        HavenRooms.Room room = room(server, number, out);
        if (room == null) {
            return 0;
        }
        Ready ready = ready(server, room, out);
        if (ready == null) {
            return 0;
        }
        int[] done = JakOverlay.resetRoom(ready.level(), room, ready.volume(), ready.origin());
        Path directory = JakDiff.directory(server);
        try {
            Files.deleteIfExists(directory.resolve(room.id() + ".nbt"));
            Files.deleteIfExists(directory.resolve(room.id() + ".txt"));
        } catch (IOException e) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.salle.write_failed",
                    number, e.toString()));
        }
        out.ok().accept(Component.translatable("command.emeraldweapons.haven.salle.reset", number, done[0], done[1]));
        return 1;
    }

    // ------------------------------------------------------------ la Sonde

    /**
     * Le clic droit de la Sonde dans le vide, dans la ville.
     *
     * Debout dans une salle, on la releve ; ailleurs dans la ville, on le dit.
     * Le registre des sanctuaires n'a rien a faire ici : la reference est le
     * volume, qui ne depend d'aucune construction recente.
     */
    public static void probe(ServerPlayer player) {
        Out out = Out.of(player);
        if (!player.hasPermissions(2)) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.salle.probe.denied"));
            return;
        }
        MinecraftServer server = player.server;
        BlockPos cell = player.blockPosition().subtract(HavenState.get(server).origin());
        HavenRooms.Room room = HavenRooms.roomAtCell(server, cell);
        if (room == null) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.salle.probe.outside"));
            return;
        }
        capture(server, room, player.getName().getString(), out);
    }

    // ------------------------------------------------------------ commun

    private static boolean capture(MinecraftServer server, HavenRooms.Room room, String author, Out out) {
        Ready ready = ready(server, room, out);
        if (ready == null) {
            return false;
        }
        JakDiff.Capture capture = JakDiff.capture(ready.level(), room, ready.volume(), ready.origin(), author);
        Path file;
        try {
            file = JakDiff.write(JakDiff.directory(server), capture);
        } catch (IOException e) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.salle.write_failed",
                    room.number(), e.toString()));
            return false;
        }
        out.ok().accept(Component.translatable("command.emeraldweapons.haven.salle.captured", room.number(),
                capture.cells(), capture.entities(), file.toString()));
        return true;
    }

    @Nullable
    private static HavenRooms.Room room(MinecraftServer server, int number, Out out) {
        HavenRooms.Data data = HavenRooms.get(server);
        if (data == null) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.salle.missing_rooms"));
            return null;
        }
        HavenRooms.Room room = HavenRooms.room(server, number);
        if (room == null) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.salle.unknown",
                    number, data.rooms().size()));
        }
        return room;
    }

    /** Ce qu'il faut pour lire ou toucher une salle : la ville posee, son volume, ses entites chargees. */
    private record Ready(ServerLevel level, JakVolume volume, BlockPos origin) {
    }

    /**
     * Les conditions d'un releve ou d'une remise a zero.
     *
     * LA VILLE POSEE DOIT ETRE CELLE DU VOLUME. Sinon la comparaison releverait
     * tout ce qui differe entre deux versions du port, et non ce que le joueur
     * a amenage.
     */
    @Nullable
    private static Ready ready(MinecraftServer server, HavenRooms.Room room, Out out) {
        ServerLevel level = Haven.level(server);
        if (level == null) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.missing_level"));
            return null;
        }
        if (HavenSite.busy()) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.busy"));
            return null;
        }
        HavenState state = HavenState.get(server);
        if (!state.built()) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.not_built"));
            return null;
        }
        JakVolume volume = JakVolume.load(server, Haven.VOLUME);
        if (volume == null) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.missing_volume", Haven.VOLUME));
            return null;
        }
        String sha1 = volume.sha1() == null ? "" : volume.sha1();
        if (sha1.isEmpty() || !sha1.equals(state.sha1())) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.salle.stale",
                    state.sha1(), sha1));
            return null;
        }
        BlockPos origin = state.origin();
        HavenRooms.Box box = JakDiff.clamp(room.envelope(), volume);
        if (!JakDiff.entitiesLoaded(level, JakDiff.worldMin(origin, box), JakDiff.worldMax(origin, box))) {
            out.fail().accept(Component.translatable("command.emeraldweapons.haven.salle.entities_not_loaded",
                    room.number()));
            return null;
        }
        return new Ready(level, volume, origin);
    }
}
