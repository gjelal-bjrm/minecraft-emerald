package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.util.PhotoAutomaton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Le cote client de l'automate de photos (PhotoAutomaton) : sans interface a l'ecran,
 * sans pause quand la fenetre perd la main, il prend chaque photo demandee dans
 * run/screenshots/, et ferme le jeu a la fin. Inerte sans EMERALDWEAPONS_PHOTOS.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class PhotoClient {

    private static final Set<String> TAKEN = new HashSet<>();
    private static boolean stopping;

    private PhotoClient() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!PhotoAutomaton.enabled() || stopping) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        if (PhotoAutomaton.finished()) {
            stopping = true;
            mc.stop();
            return;
        }
        String wanted = PhotoAutomaton.pending();
        if (wanted == null || mc.level == null) {
            return;
        }
        // le terrain autour est-il entierement dessine ? (Sodium repond ici aussi)
        PhotoAutomaton.clientTerrain(mc.levelRenderer.hasRenderedAllSections());
        if (TAKEN.add(wanted)) {
            return;                       // premiere tique avec cette prise : on laisse le serveur la preparer
        }
        if (PhotoAutomaton.readyToShoot()) {
            Screenshot.grab(mc.gameDirectory, wanted + ".png", mc.getMainRenderTarget(), message -> { });
            PhotoAutomaton.taken(wanted);
        }
    }
}
