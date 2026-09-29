package com.emerald.client.compat;

import com.emerald.client.ArcInventoryScreen;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.menu.bag.BagMenu;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

/**
 * Trois mods du modpack, sur les ecrans du panneau du sac (cahier 111).
 *
 * INVENTORY TWEAKS trie, cote serveur, toutes les cases « de conteneur » d'un menu comme
 * un seul coffre. Nos menus melangent une grille d'artisanat, des cases d'artefacts, une
 * poubelle et la fenetre du sac : son tri y aurait deplace le contenu du sac dans les
 * artefacts ou la poubelle. Dans le profil, le clic molette est lie a son tri « de ce qui
 * est sous la souris ». Sur nos ecrans, ses boutons sont retires et ses touches de tri
 * ignorees ; les boutons de tri de l'ecran font le travail, case par case, sans rien
 * perdre (BagPanel, item/Sorting).
 *
 * TRASHSLOT pose sa case de poubelle sur tout ecran de conteneur, par defaut dans le coin
 * haut droit -- sur l'inventaire d'Arcencium, c'etait sur le titre du sac, a cote de notre
 * propre poubelle. Son API permet d'enregistrer une disposition par ecran : celle de notre
 * inventaire est « desactivee par defaut » (le joueur peut toujours la rallumer par la
 * touche de TrashSlot). La Forge, l'Autel et l'Etabli gardent la sienne, comme avant.
 * Par reflexion : TrashSlot n'est pas une dependance de compilation.
 *
 * FTB LIBRARY pose sa grille de boutons (quetes, equipe, jour et nuit, mode de jeu, reglages) dans
 * un coin de tout ecran de conteneur, sans eviter l'interface, et prend tous les clics de son cadre,
 * meme sur une cellule vide (un clic droit la passe en edition). Sur un petit ecran -- le client du
 * dev, plein ecran a l'echelle 6 : 426 x 240 --, elle couvrait les deux premieres rangees
 * d'artefacts, et la case de l'arme sur la Forge. Elle est retiree de nos ecrans quand son cadre
 * chevauche leur image ; a l'echelle 4 du profil (640 x 360), elle reste. L'inventaire du jeu (le
 * livre vert) la garde toujours.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class InventoryModsCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    private static final String INVTWEAKS = "invtweaks.";
    /** La grille de boutons de FTB Library (un seul widget pour toute la grille). */
    private static final String FTB_SIDEBAR = "dev.ftb.mods.ftblibrary.sidebar.SidebarGroupGuiButton";
    /** Les touches de tri d'Inventory Tweaks (options.txt : key_key.invtweaks_...). */
    private static final Set<String> INVTWEAKS_SORT = Set.of(
            "key.invtweaks_sort_inventory.desc", "key.invtweaks_sort_either.desc", "key.invtweaks_sort_player.desc");

    private InventoryModsCompat() {
    }

    private static boolean ours(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> container && container.getMenu() instanceof BagMenu;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!ours(event.getScreen())) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        for (GuiEventListener listener : List.copyOf(event.getListenersList())) {
            String name = listener.getClass().getName();
            if (name.startsWith(INVTWEAKS) || (FTB_SIDEBAR.equals(name) && covers(listener, screen))) {
                event.removeListener(listener);
            }
        }
    }

    /**
     * Le cadre de ce widget chevauche-t-il l'image de l'ecran ? Celui de FTB est calcule des sa
     * construction : il est juste ici, avant le premier dessin.
     */
    private static boolean covers(GuiEventListener listener, AbstractContainerScreen<?> screen) {
        if (!(listener instanceof AbstractWidget widget) || widget.getWidth() <= 0 || widget.getHeight() <= 0) {
            return false;
        }
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        return widget.getX() < left + screen.getXSize() && widget.getX() + widget.getWidth() > left
                && widget.getY() < top + screen.getYSize() && widget.getY() + widget.getHeight() > top;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (ours(event.getScreen()) && foreignSort(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (ours(event.getScreen()) && foreignSort(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) {
            event.setCanceled(true);
        }
    }

    private static boolean foreignSort(InputConstants.Key key) {
        if (!ModList.get().isLoaded("invtweaks")) {
            return false;
        }
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (INVTWEAKS_SORT.contains(mapping.getName()) && mapping.isActiveAndMatches(key)) {
                return true;
            }
        }
        return false;
    }

    /** La mise en route du client, sur le bus du mod. */
    @EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(InventoryModsCompat::setup);
        }
    }

    /** A la mise en route du client : la case de TrashSlot eteinte par defaut sur notre inventaire. */
    static void setup() {
        if (!ModList.get().isLoaded("trashslot")) {
            return;
        }
        try {
            ClassLoader loader = InventoryModsCompat.class.getClassLoader();
            Class<?> api = Class.forName("net.blay09.mods.trashslot.api.TrashSlotAPI", true, loader);
            Class<?> layoutType = Class.forName("net.blay09.mods.trashslot.api.IGuiContainerLayout", true, loader);
            Object fallback = Class.forName("net.blay09.mods.trashslot.client.gui.layout.SimpleGuiContainerLayout",
                    true, loader).getField("DEFAULT").get(null);
            InvocationHandler handler = (proxy, method, args) -> {
                if ("isEnabledByDefault".equals(method.getName())) {
                    return false;
                }
                if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                }
                return method.invoke(fallback, args);
            };
            Object layout = Proxy.newProxyInstance(loader, new Class<?>[]{layoutType}, handler);
            Method register = api.getMethod("registerLayout", Class.class, layoutType);
            register.invoke(null, ArcInventoryScreen.class, layout);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("TrashSlot : disposition de l'inventaire d'Arcencium non enregistree ({})", e.toString());
        }
    }
}
