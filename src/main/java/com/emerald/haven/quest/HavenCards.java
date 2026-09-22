package com.emerald.haven.quest;

import com.emerald.main.EmeraldWeaponsMod;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * LES CARTES CLIQUABLES DE LA VILLE, pour tous les joueurs (lot 3, cahier §86).
 *
 * Le choix du regime (§41) passait par une commande /arcencium, reservee aux operateurs :
 * un joueur ordinaire ne pouvait pas cliquer. Les dialogues des heros, la carte
 * « Rejoindre » d'une quete, « Abandonner », la boutique sont pour tout le monde. Chaque
 * bouton est donc un JETON : dix chiffres hexadecimaux tires au hasard, lies cote serveur a
 * UN joueur, a UNE action et a une date d'expiration. Le clic lance « /carte <jeton> » ; la
 * commande est ouverte a tous, mais un jeton ne sert qu'a son destinataire, une fois, avant
 * d'expirer. Taper la commande a la main sans le jeton ne mene a rien.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenCards {

    /** Cinq minutes : un dialogue qu'on relit un peu plus tard marche encore. */
    public static final int DEFAULT_TTL = 20 * 60 * 5;

    private record Offer(UUID player, Consumer<ServerPlayer> action, long expires) {
    }

    private static final Map<String, Offer> OFFERS = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static long now;

    private HavenCards() {
    }

    /** Un jeton pour ce joueur et cette action. */
    public static String offer(ServerPlayer player, Consumer<ServerPlayer> action, int ttl) {
        byte[] bytes = new byte[5];
        String token;
        do {
            RANDOM.nextBytes(bytes);
            token = HexFormat.of().formatHex(bytes);
        } while (OFFERS.containsKey(token));
        OFFERS.put(token, new Offer(player.getUUID(), action, now + ttl));
        return token;
    }

    /** Un bouton « [libelle] » du chat, avec son infobulle, qui lance l'action pour ce joueur. */
    public static MutableComponent button(ServerPlayer player, Component label, Component hover, ChatFormatting color,
                                          Consumer<ServerPlayer> action) {
        return button(player, label, hover, color, action, DEFAULT_TTL);
    }

    public static MutableComponent button(ServerPlayer player, Component label, Component hover, ChatFormatting color,
                                          Consumer<ServerPlayer> action, int ttl) {
        String token = offer(player, action, ttl);
        return Component.literal("[").append(label).append("]").withStyle(style -> style.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/carte " + token))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    /** Le clic : le jeton est-il a ce joueur, et encore valable ? Il ne sert qu'une fois. */
    static boolean use(ServerPlayer player, String token) {
        Offer offer = OFFERS.get(token);
        if (offer == null || !offer.player().equals(player.getUUID()) || now > offer.expires()) {
            player.displayClientMessage(Component.translatable("game.emeraldweapons.haven.carte.perimee")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        OFFERS.remove(token);
        offer.action().accept(player);
        return true;
    }

    /** Pour le banc : utiliser un jeton comme un clic. */
    public static boolean useForTest(ServerPlayer player, String token) {
        return use(player, token);
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("carte")
                .then(Commands.argument("jeton", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            return use(player, StringArgumentType.getString(ctx, "jeton")) ? 1 : 0;
                        })));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        now = event.getServer().getTickCount();
        if (now % 600 == 0) {
            OFFERS.values().removeIf(offer -> now > offer.expires());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        OFFERS.clear();
        now = 0;
    }
}
