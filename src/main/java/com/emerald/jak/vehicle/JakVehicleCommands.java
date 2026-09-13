package com.emerald.jak.vehicle;

import com.emerald.init.Jak3Registry;
import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Locale;

/**
 * /arcencium vehicule <cara|carb|carc> : pose une voiture devant soi, pour la regarder.
 *
 * La commande vit a part de GameCommands. Brigadier FUSIONNE deux noeuds
 * « arcencium » enregistres separement (CommandNode.addChild) : le sous-noeud
 * « vehicule » s'ajoute donc aux autres, sous la meme permission 2.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class JakVehicleCommands {

    /**
     * Distance du centre de la voiture devant le joueur, en blocs. La plus
     * longue arriere est celle de carc (5,09 blocs derriere l'origine) : a 7
     * blocs, elle reste a deux blocs du joueur.
     */
    private static final double DISTANCE = 7.0;

    private JakVehicleCommands() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arcencium")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("vehicule")
                        .then(Commands.argument("modele", StringArgumentType.word())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggest(JakVehicleEntity.MODELS, builder))
                                .executes(ctx -> place(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "modele"))))));
    }

    private static int place(CommandSourceStack source, String model) throws CommandSyntaxException {
        if (!JakVehicleEntity.isModel(model)) {
            source.sendFailure(Component.translatable("command.emeraldweapons.vehicule.unknown", model));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        JakVehicleEntity car = Jak3Registry.JAK_VEHICLE.get().create(level);
        if (car == null) {
            return 0;
        }

        // devant le joueur selon SON lacet (le tangage ne compte pas), et
        // tournee comme lui : l'avant du modele regarde ou il regarde
        float yaw = player.getYRot();
        double radians = Math.toRadians(yaw);
        double x = player.getX() - Math.sin(radians) * DISTANCE;
        double z = player.getZ() + Math.cos(radians) * DISTANCE;
        // le bas de la voiture sur le niveau des pieds, pas son origine
        double y = player.getY() + JakVehicleEntity.bottom(model);

        car.setModel(model);
        car.moveTo(x, y, z, yaw, 0.0F);
        level.addFreshEntity(car);

        source.sendSuccess(() -> Component.translatable("command.emeraldweapons.vehicule.placed",
                Component.translatable("entity.emeraldweapons.jak_vehicle." + model),
                String.format(Locale.ROOT, "%.1f", x),
                String.format(Locale.ROOT, "%.1f", y),
                String.format(Locale.ROOT, "%.1f", z)), true);
        return 1;
    }
}
