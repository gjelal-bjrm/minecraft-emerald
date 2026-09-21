package com.emerald.client;

import com.emerald.haven.journey.HavenTitlePayload;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Les titres du parcours de Haven, joues par le client QUAND IL PEUT LES MONTRER : monde
 * charge, joueur present, aucun ecran ouvert (« Chargement du terrain... » compris), puis
 * quelques instants de monde a l'ecran avant le titre (HavenTitlePayload dit pourquoi).
 *
 * TROIS TITRES : la premiere arrivee (« Bienvenue a Haven »), la deuxieme, au retour du
 * Defi (« Haven a ete envahie » : une arme attend au QG), et les rues reprises. Les
 * textes sont mesures pour tenir a l'echelle d'interface 4 du joueur : le titre s'ecrit
 * quatre fois plus gros que le texte et ne revient pas a la ligne.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class HavenJourneyClient {

    /**
     * Trois secondes de monde a l'ecran avant un titre d'arrivee : a la connexion, le chat se
     * remplit de messages (l'arrivee, JourneyMap, Distant Horizons) qui s'effacent au bout de
     * dix secondes ; a une seconde, ils couvraient le sous-titre (photo de controle du 21 sept.).
     */
    private static final int CLEAR_TICKS = 60;
    /** Les rues reprises se jouent en pleine rue : une demi-seconde suffit. */
    private static final int CLEAR_TICKS_REPRISE = 10;
    /** Duree du titre : apparition, tenue, disparition, en tiques. */
    private static final int FADE_IN = 10;
    private static final int STAY = 100;
    private static final int FADE_OUT = 20;

    /** Le titre a jouer ; -1 s'il n'y en a pas. */
    private static int pending = -1;
    private static int clear;
    /** Tiques depuis que le titre a ete joue ; -1 s'il ne l'a pas ete. */
    private static int age = -1;
    /** Le dernier titre joue ; -1 avant le premier. */
    private static int lastKind = -1;

    private HavenJourneyClient() {
    }

    public static void accept(HavenTitlePayload payload) {
        pending = payload.kind();
        clear = 0;
    }

    /** Tiques depuis que le titre s'affiche ; -1 s'il ne s'est pas encore affiche. */
    public static int titleAge() {
        return age;
    }

    /** Le dernier titre joue (HavenTitlePayload.ARRIVEE...), -1 avant le premier : pour l'automate de photos. */
    public static int lastKind() {
        return lastKind;
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (age >= 0) {
            age = age > FADE_IN + STAY + FADE_OUT ? -1 : age + 1;
        }
        if (pending < 0) {
            return;
        }
        if (mc.level == null || mc.player == null || mc.screen != null) {
            clear = 0;
            return;
        }
        int wait = pending == HavenTitlePayload.REPRISE ? CLEAR_TICKS_REPRISE : CLEAR_TICKS;
        if (++clear < wait) {
            return;
        }
        int kind = pending;
        pending = -1;
        age = 0;
        lastKind = kind;
        String key = switch (kind) {
            case HavenTitlePayload.ENVAHIE -> "game.emeraldweapons.haven.parcours.envahie";
            case HavenTitlePayload.REPRISE -> "game.emeraldweapons.haven.parcours.reprise.titre";
            default -> "game.emeraldweapons.haven.parcours.titre";
        };
        ChatFormatting top = switch (kind) {
            case HavenTitlePayload.ENVAHIE -> ChatFormatting.RED;
            case HavenTitlePayload.REPRISE -> ChatFormatting.GREEN;
            default -> ChatFormatting.GOLD;
        };
        ChatFormatting bottom = kind == HavenTitlePayload.ENVAHIE ? ChatFormatting.GOLD : ChatFormatting.AQUA;
        SoundEvent sound = switch (kind) {
            case HavenTitlePayload.ENVAHIE -> SoundEvents.RAID_HORN.value();
            case HavenTitlePayload.REPRISE -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            default -> SoundEvents.NOTE_BLOCK_CHIME.value();
        };
        mc.gui.setTimes(FADE_IN, STAY, FADE_OUT);
        mc.gui.setSubtitle(Component.translatable(key + ".sous").withStyle(bottom));
        mc.gui.setTitle(Component.translatable(key).withStyle(top));
        // forUI(son, hauteur, volume)
        float pitch = kind == HavenTitlePayload.ARRIVEE ? 0.9F : 1.0F;
        float volume = switch (kind) {
            case HavenTitlePayload.ENVAHIE -> 0.6F;
            case HavenTitlePayload.REPRISE -> 0.9F;
            default -> 0.8F;
        };
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
