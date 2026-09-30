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
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/**
 * RANGER D'UN CLIC, cote client (cahier 112).
 *
 * TROIS COMMANDES, reglables dans Options > Commandes (categorie « sac et poubelle ») : une touche du
 * clavier, meme celles des claviers de jeu (F13 a F24, touches sans nom lues par leur code), ou un
 * bouton de la souris, avec ou sans Ctrl, Maj ou Alt.
 * - RANGER (Alt+clic gauche par defaut) : la pile sous la souris va dans le sac porte, sur tout ecran
 *   de conteneur (menu/bag/QuickStash) ; tenue avec Maj, tout le meme objet de ce conteneur.
 * - TOUT RANGER (aucune touche par defaut) : tout le meme objet, pour qui veut un bouton a part.
 * - JETER (Suppr par defaut) : la pile a la poubelle de l'inventaire d'Arcencium (un bouton du menu,
 *   ArcInventoryMenu.BUTTON_TRASH). Son contexte ne vaut que sur cet ecran : elle ne se dispute pas
 *   Suppr avec TrashSlot, qui jette dans sa propre poubelle partout ailleurs.
 *
 * Le client ne predit rien : il envoie la case et laisse le clic ou la touche du jeu de cote ; l'ecran
 * se corrige a l'envoi suivant. Les boutons de la souris passent avant les autres mods (Inventory
 * Essentials et Mouse Tweaks agissent des l'appui) ; les touches du clavier apres eux, pour qu'une
 * barre de recherche qui a la main (celle de JEI) garde ce qu'on y tape.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class QuickStashClient {

    public static final String CATEGORY = "key.categories.emeraldweapons.bag";

    /** Le contexte de la poubelle : l'inventaire d'Arcencium, et rien d'autre. */
    private static final IKeyConflictContext ARC_INVENTORY = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return Minecraft.getInstance().screen instanceof ArcInventoryScreen;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return other == this;
        }
    };

    public static final KeyMapping STASH = new KeyMapping("key.emeraldweapons.stash", KeyConflictContext.GUI,
            KeyModifier.ALT, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT, CATEGORY);
    public static final KeyMapping STASH_ALL = new KeyMapping("key.emeraldweapons.stash_all", KeyConflictContext.GUI,
            InputConstants.UNKNOWN, CATEGORY);
    public static final KeyMapping TRASH = new KeyMapping("key.emeraldweapons.trash", ARC_INVENTORY,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_DELETE, CATEGORY);

    private QuickStashClient() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen
                && act(screen, InputConstants.Type.MOUSE.getOrCreate(event.getButton()),
                slotAt(screen, event.getMouseX(), event.getMouseY()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen
                && act(screen, InputConstants.getKey(event.getKeyCode(), event.getScanCode()), screen.getSlotUnderMouse())) {
            event.setCanceled(true);
        }
    }

    /** Une touche ou un bouton sur un ecran de conteneur, la souris sur cette case. Vrai s'il a servi. */
    private static boolean act(AbstractContainerScreen<?> screen, InputConstants.Key key, @Nullable Slot slot) {
        if (slot == null || !slot.hasItem() || !screen.getMenu().getCarried().isEmpty()
                || screen instanceof CreativeModeInventoryScreen
                || screen.getMenu().getClass().getName().startsWith(QuickStash.BACKPACK_MENUS)) {
            return false;
        }
        if (screen instanceof ArcInventoryScreen inventory && TRASH.isActiveAndMatches(key)) {
            if (slot.index == ArcInventoryMenu.SLOT_TRASH) {
                return false;
            }
            trash(inventory, slot);
            return true;
        }
        if (pressed(STASH_ALL, key)) {
            request(screen, slot, true);
            return true;
        }
        if (pressed(STASH, key)) {
            request(screen, slot, Screen.hasShiftDown() && STASH.getKeyModifier() != KeyModifier.SHIFT);
            return true;
        }
        return false;
    }

    /**
     * La touche de cette commande, avec son modificateur s'il en a un, quels que soient les autres
     * tenus : Maj y ajoute « tout le meme objet », meme sur une touche choisie sans modificateur.
     */
    private static boolean pressed(KeyMapping mapping, InputConstants.Key key) {
        if (mapping.isUnbound() || !key.equals(mapping.getKey()) || !mapping.getKeyConflictContext().isActive()) {
            return false;
        }
        KeyModifier modifier = mapping.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(mapping.getKeyConflictContext());
    }

    /** La case sous ce point de l'ecran (un clic porte sa position ; une touche prend le survol). */
    @Nullable
    private static Slot slotAt(AbstractContainerScreen<?> screen, double x, double y) {
        Slot hovered = screen.getSlotUnderMouse();
        if (hovered != null && inside(screen, hovered, x, y)) {
            return hovered;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot.isActive() && inside(screen, slot, x, y)) {
                return slot;
            }
        }
        return null;
    }

    private static boolean inside(AbstractContainerScreen<?> screen, Slot slot, double x, double y) {
        double left = screen.getGuiLeft() + slot.x;
        double top = screen.getGuiTop() + slot.y;
        return x >= left - 1 && x < left + 17 && y >= top - 1 && y < top + 17;
    }

    /** Ranger cette case : au serveur de la prendre et de la poser dans le sac. */
    public static void request(AbstractContainerScreen<?> screen, Slot slot, boolean bulk) {
        PacketDistributor.sendToServer(new QuickStashPayload(screen.getMenu().containerId, slot.index, bulk));
    }

    /** Jeter cette case de l'inventaire d'Arcencium a la poubelle. */
    public static void trash(ArcInventoryScreen screen, Slot slot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(screen.getMenu().containerId,
                    ArcInventoryMenu.BUTTON_TRASH + slot.index);
        }
    }

    /** Evenements du bus du mod. BUS = MOD EXPLICITE (voir MorphGunClient.Setup). */
    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(STASH);
            event.register(STASH_ALL);
            event.register(TRASH);
        }
    }
}
