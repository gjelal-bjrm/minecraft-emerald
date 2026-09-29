package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.bag.BagMenu;
import com.emerald.network.BagTabsPayload;
import com.emerald.network.OpenArcInventoryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * La touche E ouvre l'inventaire d'Arcencium (cahier §111).
 *
 * Le jeu ouvre son inventaire cote client, sans rien demander au serveur. Le notre est
 * un menu du serveur (le sac et les cases d'artefacts y vivent) : quand le jeu veut
 * afficher son inventaire, on l'en empeche et l'on demande le notre. Seul l'ecran de
 * l'inventaire du jeu est detourne -- pas celui du mode creatif, pas les ecrans des
 * autres mods qui en heriteraient --, et le bouton du livre vert le laisse passer une
 * fois (openClassic).
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class ArcInventoryClient {

    /** La prochaine ouverture de l'inventaire du jeu passe : on l'a demandee. */
    private static boolean classic;

    private ArcInventoryClient() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next == null || next.getClass() != InventoryScreen.class) {
            return;
        }
        if (classic) {
            classic = false;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null || minecraft.getConnection() == null
                || minecraft.gameMode.hasInfiniteItems() || minecraft.player.isSpectator()) {
            return;
        }
        event.setCanceled(true);
        PacketDistributor.sendToServer(OpenArcInventoryPayload.INSTANCE);
    }

    /** Le livre vert : fermer notre ecran, ouvrir celui du jeu (livre de recettes, effets). */
    public static void openClassic() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.closeContainer();
        classic = true;
        minecraft.setScreen(new InventoryScreen(minecraft.player));
        classic = false;
    }

    /** Les icones des onglets du panneau du sac, pour le menu ouvert. */
    public static void acceptTabs(BagTabsPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof BagMenu menu
                && menu.containerId == payload.containerId() && menu.bag() != null) {
            menu.bag().setIcons(payload.icons());
        }
    }
}
