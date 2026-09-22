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
 * Le cote client de l'automate de photos (PhotoAutomaton) : sans interface a l'ecran
 * (sauf pour les prises du parcours de Haven, qui regardent le titre et la barre),
 * sans pause quand la fenetre perd la main, il prend chaque photo demandee dans
 * run/screenshots/, et ferme le jeu a la fin. Inerte sans EMERALDWEAPONS_PHOTOS.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class PhotoClient {

    private static final Set<String> TAKEN = new HashSet<>();
    private static boolean stopping;
    /** Tiques du client avec l'ecran voulu ouvert (inventaire, livre). */
    private static int screenTicks;

    private PhotoClient() {
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!PhotoAutomaton.enabled() || stopping) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = !PhotoAutomaton.pendingGui();
        if (PhotoAutomaton.finished()) {
            stopping = true;
            AutomatonExit.stop("photos", 30);          // jamais d'ecran noir laisse derriere soi
            return;
        }
        String wanted = PhotoAutomaton.pending();
        if (wanted == null || mc.level == null) {
            if (mc.options.keyUse.isDown() && !TAKEN.isEmpty()) {
                mc.options.keyUse.setDown(false);
            }
            return;
        }
        String hands = PhotoAutomaton.handsView();
        // l'inventaire et le livre se prennent OUVERTS : leur ecran ne retient pas la prise
        boolean wantsScreen = "inventaire".equals(hands) || "livre".equals(hands);
        if (hands != null && mc.player != null) {
            // LA PRISE EN MAIN : la camera, le clic tenu (bouclier leve), l'inventaire
            boolean front = hands.startsWith("face");
            mc.options.setCameraType(front ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                    : net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.keyUse.setDown(hands.endsWith("leve"));
            if ("inventaire".equals(hands) && mc.screen == null) {
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            }
        }
        // le terrain autour est-il entierement dessine ? (Sodium repond ici aussi) -- et le troncon
        // du joueur est-il la ? Juste apres un long teleport, rien n'est encore arrive : « tout est
        // dessine » repondait oui sous « Chargement du terrain » (photo de l'arche du 21 sept.)
        boolean here = mc.player != null && mc.level.getChunkSource().hasChunk(mc.player.getBlockX() >> 4,
                mc.player.getBlockZ() >> 4);
        PhotoAutomaton.clientTerrain(here && (mc.screen == null || wantsScreen)
                && mc.levelRenderer.hasRenderedAllSections());
        if (TAKEN.add(wanted)) {
            return;                       // premiere tique avec cette prise : on laisse le serveur la preparer
        }
        if (PhotoAutomaton.pendingGui() && !PhotoAutomaton.overdue()) {
            // l'interface a l'ecran : jamais un ecran de chargement, et le titre d'accueil pendant
            // qu'il se voit ; l'inventaire et le livre, eux, se prennent ouverts
            if (wantsScreen ? mc.screen == null
                    || mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen : mc.screen != null) {
                return;
            }
            int age = HavenJourneyClient.titleAge();
            if ((wanted.contains("accueil") || wanted.contains("envahie")) && (age < 25 || age > 100)) {
                return;
            }
        }
        // UN ECRAN QUI MET LE JEU EN PAUSE (le livre) arrete le serveur integre : il ne compte
        // plus ses tiques et la prise attendrait toujours. Ecran ouvert depuis une seconde : on la prend.
        screenTicks = wantsScreen && mc.screen != null ? screenTicks + 1 : 0;
        if (PhotoAutomaton.readyToShoot() || screenTicks >= 20) {
            screenTicks = 0;
            Screenshot.grab(mc.gameDirectory, wanted + ".png", mc.getMainRenderTarget(), message -> { });
            PhotoAutomaton.taken(wanted);
            if (wantsScreen && mc.screen != null) {
                mc.setScreen(null);                       // la prise suivante part sans ecran
            }
        }
    }
}
