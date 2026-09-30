package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.ArcInventoryMenu;
import com.emerald.menu.bag.QuickStash;
import com.emerald.network.QuickStashPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * RANGER D'UN CLIC, cote client (cahier 112).
 *
 * ALT+CLIC gauche sur une pile, sur tout ecran de conteneur : le serveur la range dans le sac porte
 * (menu/bag/QuickStash) ; Alt+Maj+clic, tout le meme objet de ce conteneur. Le client ne predit
 * rien : il envoie la case et laisse le clic du jeu de cote ; l'ecran se corrige a l'envoi suivant.
 * Ctrl reste a Inventory Essentials (Ctrl+clic : un seul objet), d'ou « Alt sans Ctrl » -- AltGr,
 * que Windows envoie comme Ctrl+Alt, ne range donc pas.
 *
 * SUPPR au survol, sur l'inventaire d'Arcencium : la pile a notre poubelle (un bouton du menu,
 * ArcInventoryMenu.BUTTON_TRASH). C'est la touche « supprimer » de TrashSlot, que le joueur a pu
 * changer ; ailleurs, TrashSlot fait lui-meme la meme chose avec sa propre poubelle.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class QuickStashClient {

    private static final String TRASHSLOT_DELETE = "key.trashslot.delete";

    private QuickStashClient() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !Screen.hasAltDown() || Screen.hasControlDown()
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        Slot slot = hovered(screen);
        if (slot != null) {
            request(screen, slot, Screen.hasShiftDown());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof ArcInventoryScreen screen) || !trashKey(event.getKeyCode(), event.getScanCode())) {
            return;
        }
        Slot slot = hovered(screen);
        if (slot != null && slot.index != ArcInventoryMenu.SLOT_TRASH) {
            trash(screen, slot);
            event.setCanceled(true);
        }
    }

    /** Alt+clic sur cette case : au serveur de la ranger. */
    public static void request(AbstractContainerScreen<?> screen, Slot slot, boolean bulk) {
        PacketDistributor.sendToServer(new QuickStashPayload(screen.getMenu().containerId, slot.index, bulk));
    }

    /** Suppr sur cette case de l'inventaire d'Arcencium : a la poubelle. */
    public static void trash(ArcInventoryScreen screen, Slot slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(screen.getMenu().containerId,
                    ArcInventoryMenu.BUTTON_TRASH + slot.index);
        }
    }

    /**
     * La case sous la souris, si elle tient quelque chose et que le curseur est vide. Pas dans
     * l'inventaire creatif (son menu n'est pas celui du serveur), ni sur l'ecran d'un sac de
     * Sophisticated Backpacks.
     */
    @Nullable
    private static Slot hovered(AbstractContainerScreen<?> screen) {
        if (screen instanceof CreativeModeInventoryScreen || !screen.getMenu().getCarried().isEmpty()
                || screen.getMenu().getClass().getName().startsWith(QuickStash.BACKPACK_MENUS)) {
            return null;
        }
        Slot slot = screen.getSlotUnderMouse();
        return slot != null && slot.hasItem() ? slot : null;
    }

    /** La touche « supprimer » de TrashSlot ; sans lui, Suppr seule. */
    private static boolean trashKey(int keyCode, int scanCode) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (TRASHSLOT_DELETE.equals(mapping.getName())) {
                return mapping.isActiveAndMatches(key);
            }
        }
        return keyCode == GLFW.GLFW_KEY_DELETE && !Screen.hasShiftDown() && !Screen.hasControlDown()
                && !Screen.hasAltDown();
    }
}
