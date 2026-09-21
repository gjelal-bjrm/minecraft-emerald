package com.emerald.haven.journey;

import com.emerald.jak.gun.GunForm;
import com.emerald.jak.gun.MorphGunKeeper;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * /arcencium haven parcours [joueur] | armes <joueur> toutes|aucune|scatter | maitrise <joueur> | remise <joueur>
 *
 * Pour l'operateur : lire la fiche de parcours d'un joueur, lui donner ou retirer des
 * armes, la maitrise (le bouton du QG), ou le remettre a zero comme un nouveau venu.
 * L'arme suit au passage suivant du gardien (une seconde). Brigadier fusionne ces
 * litteraux avec ceux de HavenCommands.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenJourneyCommands {

    private HavenJourneyCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arcencium")
                .requires(source -> source.hasPermission(2));
        root.then(Commands.literal("haven").then(Commands.literal("parcours")
                .executes(ctx -> show(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur"))))
                .then(Commands.literal("armes").then(Commands.argument("joueur", EntityArgument.player())
                        .then(Commands.literal("toutes").executes(ctx -> forms(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "joueur"), GunForm.ALL_MASK)))
                        .then(Commands.literal("aucune").executes(ctx -> forms(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "joueur"), 0)))
                        .then(Commands.literal("scatter").executes(ctx -> forms(ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "joueur"), GunForm.RED_1.bit())))))
                .then(Commands.literal("maitrise").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(ctx -> mastery(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur")))))
                .then(Commands.literal("remise").then(Commands.argument("joueur", EntityArgument.player())
                        .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "joueur")))))));
        event.getDispatcher().register(root);
    }

    private static int show(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
        HavenProgress.Entry entry = HavenProgress.peek(player.getUUID());
        int forms = HavenProgress.forms(player.getUUID());
        List<String> names = new ArrayList<>();
        for (GunForm form : GunForm.values()) {
            if ((forms & form.bit()) != 0) {
                names.add(Component.translatable(form.translationKey()).getString());
            }
        }
        Component weapons = names.isEmpty()
                ? Component.translatable("command.emeraldweapons.haven.parcours.aucune")
                : Component.literal(String.join(", ", names));
        boolean mastery = HavenProgress.mastery(player.getUUID());
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.parcours.etat",
                player.getDisplayName(), Integer.bitCount(forms), weapons,
                yesNo(entry != null && entry.welcomed), yesNo(entry != null && entry.hq),
                entry == null ? 0 : entry.departures, yesNo(mastery)), false);
        return 1;
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value ? "command.emeraldweapons.haven.parcours.oui"
                : "command.emeraldweapons.haven.parcours.non");
    }

    private static int forms(CommandSourceStack source, ServerPlayer player, int mask) {
        HavenProgress.setForms(player.getUUID(), mask);
        MorphGunKeeper.guard(player);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.parcours.armes",
                player.getDisplayName(), Integer.bitCount(mask)), true);
        return 1;
    }

    private static int mastery(CommandSourceStack source, ServerPlayer player) {
        HavenProgress.grantMastery(player.getUUID());
        MorphGunKeeper.guard(player);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.parcours.maitrise",
                player.getDisplayName()), true);
        return 1;
    }

    private static int reset(CommandSourceStack source, ServerPlayer player) {
        HavenProgress.reset(player.getUUID());
        HavenJourney.forgetLobby(player.getUUID());
        HavenJourney.revokeAll(player);
        MorphGunKeeper.guard(player);
        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.haven.parcours.remise",
                player.getDisplayName()), true);
        return 1;
    }
}
