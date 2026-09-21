package com.emerald.client;

import com.emerald.haven.journey.HavenTitlePayload;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Le titre de la premiere arrivee dans Haven, joue par le client QUAND IL PEUT LE MONTRER :
 * monde charge, joueur present, aucun ecran ouvert (« Chargement du terrain... » compris),
 * puis trois secondes de monde a l'ecran avant le titre (HavenTitlePayload dit pourquoi).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class HavenJourneyClient {

    /**
     * Trois secondes de monde a l'ecran avant le titre : a la connexion, le chat se remplit
     * de messages (l'arrivee, JourneyMap, Distant Horizons) qui s'effacent au bout de dix
     * secondes ; a une seconde, ils couvraient le sous-titre (photo de controle du 21 sept.).
     */
    private static final int CLEAR_TICKS = 60;
    /** Duree du titre : apparition, tenue, disparition, en tiques. */
    private static final int FADE_IN = 10;
    private static final int STAY = 100;
    private static final int FADE_OUT = 20;

    private static boolean pending;
    private static int clear;
    /** Tiques depuis que le titre a ete joue ; -1 s'il ne l'a pas ete. */
    private static int age = -1;

    private HavenJourneyClient() {
    }

    public static void accept(HavenTitlePayload payload) {
        pending = true;
        clear = 0;
    }

    /** Tiques depuis que le titre s'affiche ; -1 s'il ne s'est pas encore affiche. */
    public static int titleAge() {
        return age;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (age >= 0) {
            age = age > FADE_IN + STAY + FADE_OUT ? -1 : age + 1;
        }
        if (!pending) {
            return;
        }
        if (mc.level == null || mc.player == null || mc.screen != null) {
            clear = 0;
            return;
        }
        if (++clear < CLEAR_TICKS) {
            return;
        }
        pending = false;
        age = 0;
        mc.gui.setTimes(FADE_IN, STAY, FADE_OUT);
        mc.gui.setSubtitle(Component.translatable("game.emeraldweapons.haven.parcours.titre.sous")
                .withStyle(ChatFormatting.AQUA));
        mc.gui.setTitle(Component.translatable("game.emeraldweapons.haven.parcours.titre")
                .withStyle(ChatFormatting.GOLD));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.9F, 0.8F));
    }
}
