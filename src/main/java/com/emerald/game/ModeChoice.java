package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * LA QUESTION QU'ON POSE UNE FOIS PAR MONDE : defi ou monde ouvert ?
 *
 * Le mode a longtemps eu une seule facon de se jouer -- quatre-vingt-dix
 * minutes, la Maree qui referme la carte, une defaite au bout. C'est une bonne
 * course, mais tout ce qu'on a bati autour (la meteo, les runes, les
 * ameliorations, la specialisation, les ailes) demande plus de temps qu'une
 * course n'en laisse. Le monde ouvert donne ce temps sans rien retirer :
 * memes sanctuaires, meme meteo, meme boss -- seulement, quand il tombe, le
 * cycle repart au lieu de s'arreter.
 *
 * LA QUESTION SE POSE DANS LE CHAT, avec deux boutons cliquables, plutot que
 * dans un ecran a nous. Un ecran aurait exige d'etre ouvert, ferme, traduit et
 * dessine, et surtout de savoir quand l'ouvrir -- alors que le chat arrive tout
 * seul a la connexion, se relit, et laisse la reponse a portee de clic.
 *
 * ET LA LAME REFUSE DE VENIR TANT QU'ON N'A PAS REPONDU. C'est la seule
 * garantie qui compte : on ne lance pas un chronometre de quatre-vingt-dix
 * minutes sur un monde qu'on voulait habiter.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class ModeChoice {

    private ModeChoice() {
    }

    /**
     * La carte de choix, posee dans le chat.
     *
     * Un titre, une ligne par regime avec ce qu'il change vraiment, et deux
     * boutons. Les descriptions disent le COUT de chaque choix et non son
     * argument de vente : c'est le chronometre qui distingue les deux, et le
     * joueur doit savoir lequel il prend.
     */
    public static void ask(ServerPlayer player) {
        player.sendSystemMessage(Component.empty());
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.mode.ask")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.mode.defi.desc")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.translatable("game.emeraldweapons.mode.libre.desc")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(button("game.emeraldweapons.mode.defi.button",
                        "game.emeraldweapons.mode.defi.hover", "/arcencium partie defi", 0xFFC24A)
                .append(Component.literal("   "))
                .append(button("game.emeraldweapons.mode.libre.button",
                        "game.emeraldweapons.mode.libre.hover", "/arcencium partie libre", 0x78E8AE)));
        player.sendSystemMessage(Component.empty());
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
    }

    private static net.minecraft.network.chat.MutableComponent button(
            String label, String hover, String command, int color) {
        return Component.translatable(label).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(hover))));
    }

    /**
     * Applique le choix. Refuse une fois la partie ouverte, et le dit.
     *
     * @return vrai si le regime a change
     */
    public static boolean choose(ServerLevel level, GameState.Mode mode) {
        GameState state = GameState.get(level);
        if (!state.chooseMode(mode)) {
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(Component.translatable("game.emeraldweapons.mode.locked")
                        .withStyle(ChatFormatting.RED));
            }
            return false;
        }
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.translatable(
                            mode == GameState.Mode.DEFI
                                    ? "game.emeraldweapons.mode.defi.chosen"
                                    : "game.emeraldweapons.mode.libre.chosen")
                    .withStyle(mode == GameState.Mode.DEFI
                            ? ChatFormatting.GOLD : ChatFormatting.GREEN));
            player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.3F);
        }
        return true;
    }

    /**
     * Le regime est-il tranche ? Sinon, on repose la question a celui qui vient
     * de toucher la lame -- et la lame ne bouge pas.
     */
    public static boolean ready(ServerLevel level, Player puller) {
        if (GameState.get(level).modeChosen()) {
            return true;
        }
        if (puller instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatable("game.emeraldweapons.mode.first")
                    .withStyle(ChatFormatting.YELLOW));
            ask(player);
        }
        return false;
    }

    /**
     * A la connexion : on pose la question tant qu'elle n'a pas de reponse.
     *
     * Seulement en lobby : dans une partie deja ouverte, la question n'aurait
     * plus d'objet, et un joueur qui rejoint en cours de route n'a pas a
     * choisir a la place de ceux qui jouent depuis une heure.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        GameState state = GameState.get(level);
        if (state.modeChosen() || state.status() != GameState.Status.LOBBY) {
            return;
        }
        ask(player);
    }
}
