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
    /** La rafale en cours : l'image suivante (-1 : aucune), et les tiques ecoulees. */
    private static int burst = -1;
    private static int burstTicks;
    private static final int BURST_FRAMES = 30;
    private static final int BURST_STEP = 2;

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
            AutomatonExit.stop("photos", 20);          // jamais d'ecran noir laisse derriere soi
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
            // LA PRISE EN MAIN : la camera, le clic tenu (bouclier leve), l'inventaire ; les ailes
            // (« dos », « vol ») se prennent de dos
            boolean front = hands.startsWith("face");
            boolean back = hands.startsWith("dos") || hands.startsWith("vol");
            mc.options.setCameraType(front ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                    : back ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                    : net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.keyUse.setDown(hands.endsWith("leve"));
            if (hands.startsWith("vol")) {
                // le vol en rond des ailes : le regard suit la vitesse, un peu vers le bas, comme en vrai
                net.minecraft.world.phys.Vec3 motion = mc.player.getDeltaMovement();
                if (motion.horizontalDistanceSqr() > 1.0e-4) {
                    // six degres par tique au plus : le cercle tourne de trois, jamais de demi-tour
                    float yaw = (float) Math.toDegrees(Math.atan2(-motion.x, motion.z));
                    float turn = net.minecraft.util.Mth.clamp(
                            net.minecraft.util.Mth.wrapDegrees(yaw - mc.player.getYRot()), -6.0F, 6.0F);
                    mc.player.setYRot(mc.player.getYRot() + turn);
                    mc.player.setXRot(12.0F);
                }
            }
            if ("inventaire".equals(hands) && mc.screen == null) {
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            }
        }
        // le terrain autour est-il entierement dessine ? (Sodium repond ici aussi) -- et le troncon
        // du joueur est-il la ? Juste apres un long teleport, rien n'est encore arrive : « tout est
        // dessine » repondait oui sous « Chargement du terrain » (photo de l'arche du 21 sept.)
        //
        // ET TOUT LE TOUR, PAS SEULEMENT SON TRONCON. Apres un saut de cinq cents blocs, les troncons
        // du premier plan n'etaient pas encore arrives quand « tout est dessine » repondait oui : les
        // lointains de Distant Horizons remplissaient le trou, et le bas des photos des sanctuaires
        // montrait le dessous du terrain (24 sept.). On attend donc tous ceux a neuf troncons.
        boolean here = mc.player != null && aroundLoaded(mc);
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
        if (burst >= 0) {
            // LA RAFALE (« _rafale ») : une image toutes les deux tiques, pour voir bouger les ailes
            if (++burstTicks % BURST_STEP == 0) {
                Screenshot.grab(mc.gameDirectory, String.format(java.util.Locale.ROOT, "%s_%02d.png", wanted, burst),
                        mc.getMainRenderTarget(), message -> { });
                if (++burst >= BURST_FRAMES) {
                    burst = -1;
                    PhotoAutomaton.taken(wanted);
                }
            }
            return;
        }
        if (PhotoAutomaton.readyToShoot() || screenTicks >= 20) {
            screenTicks = 0;
            if (wanted.endsWith("_rafale")) {
                burst = 0;
                burstTicks = BURST_STEP - 1;
                return;
            }
            Screenshot.grab(mc.gameDirectory, wanted + ".png", mc.getMainRenderTarget(), message -> { });
            PhotoAutomaton.taken(wanted);
            if (wantsScreen && mc.screen != null) {
                mc.setScreen(null);                       // la prise suivante part sans ecran
            }
        }
    }

    /** Tous les troncons a neuf troncons du joueur sont-ils arrives chez le client ? */
    private static boolean aroundLoaded(Minecraft mc) {
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                if (dx * dx + dz * dz <= 81 && !mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) {
                    return false;
                }
            }
        }
        return true;
    }
}
