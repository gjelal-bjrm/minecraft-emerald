package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.util.PhotoAutomaton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

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
    /** La derniere rafale finie : elle ne repart pas. */
    private static String burstDone;
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
        boolean wantsScreen = "inventaire".equals(hands) || (hands != null && hands.startsWith("livre")) || "curios".equals(hands)
                || wanted.startsWith("menu_");     // un menu que le serveur ouvre (le coffre d'amenagement, §107)
        if (hands == null && (mc.options.keyUp.isDown() || mc.options.keyDown.isDown())) {
            // la prise d'avant tenait Z ou S (en planche) : on les lache
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
        }
        if (hands != null && mc.player != null) {
            // LA PRISE EN MAIN : la camera, le clic tenu (bouclier leve), l'inventaire ; les ailes
            // (« dos », « vol ») se prennent de dos
            boolean front = hands.startsWith("face") || hands.endsWith("_face");
            boolean back = hands.startsWith("dos") || hands.startsWith("vol") || (hands.startsWith("planche") && !front);
            mc.options.setCameraType(front ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                    : back ? net.minecraft.client.CameraType.THIRD_PERSON_BACK
                    : net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.keyUse.setDown(hands.endsWith("leve"));
            // en planche : S tenu jusqu'a la prise (lachee, elle repart seule, comme dans Jak 3), Z pendant la
            // rafale d'elan seulement (cahier §100)
            boolean going = hands.endsWith("avance") && burst >= 0;
            mc.options.keyUp.setDown(going);
            mc.options.keyDown.setDown(hands.startsWith("planche") && !going);
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
            if (hands.startsWith("livre") && hands.length() > 5
                    && mc.screen instanceof net.minecraft.client.gui.screens.inventory.BookViewScreen book) {
                book.setPage(Integer.parseInt(hands.substring(5).replaceAll("[^0-9].*$", "")));
            }
            if ("curios".equals(hands) && mc.screen == null) {
                // l'ecran de Curios s'ouvre par sa touche : on la presse comme le joueur
                for (net.minecraft.client.KeyMapping key : mc.options.keyMappings) {
                    if ("key.curios.open.desc".equals(key.getName())) {
                        net.minecraft.client.KeyMapping.click(key.getKey());
                    }
                }
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
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> shown) {
            if (screenTicks == 10) {
                quickGesture(wanted, shown);
            }
            if (wanted.endsWith("_touche")) {
                rebound(wanted, shown, screenTicks);
            }
            if (wanted.endsWith("_aide") && screenTicks == 10) {
                pointAt(shown, shown.getMenu().getSlot(com.emerald.menu.ArcInventoryMenu.SLOT_TRASH));
            }
        }
        if (burst >= 0) {
            // LA RAFALE (« _rafale ») : une image toutes les deux tiques, pour voir bouger les ailes
            if (++burstTicks % BURST_STEP == 0) {
                Screenshot.grab(mc.gameDirectory, String.format(java.util.Locale.ROOT, "%s_%02d.png", wanted, burst),
                        mc.getMainRenderTarget(), message -> { });
                if (++burst >= BURST_FRAMES) {
                    burst = -1;
                    burstDone = wanted;
                    PhotoAutomaton.taken(wanted);
                }
            }
            return;
        }
        if (PhotoAutomaton.readyToShoot() || (screenTicks >= 20 && !PhotoAutomaton.holdsScreen())) {
            screenTicks = 0;
            if (wanted.endsWith("_rafale")) {
                if (wanted.equals(burstDone)) {
                    // la rafale est finie, le serveur n'a pas encore passe a la prise suivante : sans
                    // cette garde, une seconde rafale partait et prenait le nom de la suivante
                    return;
                }
                burst = 0;
                burstTicks = BURST_STEP - 1;
                PhotoAutomaton.burstStarted();
                return;
            }
            Screenshot.grab(mc.gameDirectory, wanted + ".png", mc.getMainRenderTarget(), message -> { });
            PhotoAutomaton.taken(wanted);
            if (wantsScreen && mc.screen != null) {
                mc.setScreen(null);                       // la prise suivante part sans ecran
            }
        }
    }

    /**
     * RANGER D'UN CLIC (cahier 112), le geste du joueur rejoue par le client, l'ecran ouvert : « _alt »,
     * Alt+Maj+clic sur la premiere pierre du coffre puis Alt+clic sur ses diamants ; « _suppr », Suppr
     * sur la viande cuite de l'inventaire (case 13). Par les memes appels que la souris et la touche.
     */
    private static void quickGesture(String wanted,
                                     net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
        if (wanted.endsWith("_alt")) {
            gesture(screen, net.minecraft.world.item.Items.COBBLESTONE, true);
            gesture(screen, net.minecraft.world.item.Items.DIAMOND, false);
        }
        if (wanted.endsWith("_suppr") && screen instanceof ArcInventoryScreen inventory) {
            QuickStashClient.trash(inventory, screen.getMenu().getSlot(13));
        }
    }

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    /**
     * LES COMMANDES CHANGEES (cahier 112, D) : les touches passent par les evenements du jeu, comme un
     * vrai clic ou une vraie touche -- les autres mods les voient aussi --, puis les commandes sont
     * remises a leur valeur (jamais enregistrees). « coffre_touche » : ranger sur le bouton 4 de la
     * souris, puis un clic gauche sans Alt, qui ne doit plus rien ranger. « inventaire_touche » : Suppr
     * jette les planches ; jeter sur F13 (une touche des claviers de jeu) : Suppr ne jette plus la
     * viande, F13 la jette.
     */
    private static void rebound(String wanted, net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen,
                                int tick) {
        net.minecraft.client.KeyMapping stash = QuickStashClient.STASH;
        net.minecraft.client.KeyMapping trash = QuickStashClient.TRASH;
        if (wanted.contains("coffre")) {
            net.minecraft.world.inventory.Slot stone = screen.getMenu().getSlot(0);
            net.minecraft.world.inventory.Slot diamonds = screen.getMenu().getSlot(10);
            if (tick == 10) {
                stash.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.NONE,
                        com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_4));
                net.minecraft.client.KeyMapping.resetMapping();
                boolean used = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(screen,
                        centerX(screen, stone), centerY(screen, stone), GLFW.GLFW_MOUSE_BUTTON_4);
                LOGGER.info("photos (client) : ranger sur « {} », bouton 4 sur la pierre du coffre : pris {}",
                        stash.getTranslatedKeyMessage().getString(), used);
            }
            if (tick == 16) {
                LOGGER.info("photos (client) : apres le bouton 4, case 0 {}, case 4 {} (une pile, pas tout)",
                        stone.getItem(), screen.getMenu().getSlot(4).getItem());
                stash.setKeyModifierAndCode(stash.getDefaultKeyModifier(), stash.getDefaultKey());
                net.minecraft.client.KeyMapping.resetMapping();
                boolean used = net.neoforged.neoforge.client.ClientHooks.onScreenMouseClickedPre(screen,
                        centerX(screen, diamonds), centerY(screen, diamonds), GLFW.GLFW_MOUSE_BUTTON_LEFT);
                LOGGER.info("photos (client) : ranger remis sur « {} », clic gauche sans Alt sur les diamants : pris {}",
                        stash.getTranslatedKeyMessage().getString(), used);
            }
            if (tick == 22) {
                LOGGER.info("photos (client) : les diamants apres le clic sans Alt : {}", diamonds.getItem());
            }
            return;
        }
        net.minecraft.world.inventory.Slot planks = screen.getMenu().getSlot(10);
        net.minecraft.world.inventory.Slot beef = screen.getMenu().getSlot(13);
        int delete = GLFW.GLFW_KEY_DELETE;
        if (tick == 10) {
            pointAt(screen, planks);
        }
        if (tick == 13) {
            boolean used = net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPre(screen, delete,
                    GLFW.glfwGetKeyScancode(delete), 0);
            LOGGER.info("photos (client) : survol des planches {}, Suppr (« {} ») : pris {}",
                    screen.getSlotUnderMouse() == planks, trash.getTranslatedKeyMessage().getString(), used);
        }
        if (tick == 18) {
            LOGGER.info("photos (client) : apres Suppr, case des planches {}", planks.getItem());
            trash.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.NONE,
                    com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F13));
            net.minecraft.client.KeyMapping.resetMapping();
            pointAt(screen, beef);
        }
        if (tick == 21) {
            boolean used = net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPre(screen, delete,
                    GLFW.glfwGetKeyScancode(delete), 0);
            LOGGER.info("photos (client) : jeter sur « {} », survol de la viande {}, Suppr : pris {}",
                    trash.getTranslatedKeyMessage().getString(), screen.getSlotUnderMouse() == beef, used);
        }
        if (tick == 26) {
            LOGGER.info("photos (client) : la viande apres Suppr (plus la touche) : {}", beef.getItem());
            boolean used = net.neoforged.neoforge.client.ClientHooks.onScreenKeyPressedPre(screen, GLFW.GLFW_KEY_F13,
                    GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_F13), 0);
            LOGGER.info("photos (client) : F13 sur la viande : pris {}", used);
        }
        if (tick == 31) {
            LOGGER.info("photos (client) : la viande apres F13 : {}", beef.getItem());
            trash.setKeyModifierAndCode(trash.getDefaultKeyModifier(), trash.getDefaultKey());
            net.minecraft.client.KeyMapping.resetMapping();
            LOGGER.info("photos (client) : jeter remis sur « {} »", trash.getTranslatedKeyMessage().getString());
        }
    }

    private static double centerX(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen,
                                  net.minecraft.world.inventory.Slot slot) {
        return screen.getGuiLeft() + slot.x + 8;
    }

    private static double centerY(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen,
                                  net.minecraft.world.inventory.Slot slot) {
        return screen.getGuiTop() + slot.y + 8;
    }

    /**
     * La souris du jeu posee sur cette case (sa position, pas le curseur du systeme : on ne le
     * deplace pas sous la main du joueur). L'ecran relit le survol a l'image suivante.
     */
    private static void pointAt(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen,
                                net.minecraft.world.inventory.Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        try {
            java.lang.reflect.Field x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, centerX(screen, slot) * scale);
            y.setDouble(mc.mouseHandler, centerY(screen, slot) * scale);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("photos (client) : souris non posee ({})", e.toString());
        }
    }

    private static void gesture(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen,
                                net.minecraft.world.item.Item item, boolean bulk) {
        for (net.minecraft.world.inventory.Slot slot : screen.getMenu().slots) {
            if (slot.index < 27 && slot.getItem().is(item)) {
                QuickStashClient.request(screen, slot, bulk);
                return;
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
