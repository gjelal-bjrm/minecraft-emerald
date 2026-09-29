package com.emerald.menu;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, EmeraldWeaponsMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<SocketBenchMenu>> SOCKET_BENCH =
            MENUS.register("socket_bench", () -> new MenuType<>(
                    SocketBenchMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<ArcenciumForgeMenu>> ARCENCIUM_FORGE =
            MENUS.register("arcencium_forge", () -> new MenuType<>(
                    ArcenciumForgeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<SpecializationAltarMenu>> SPECIALIZATION_ALTAR =
            MENUS.register("specialization_altar", () -> new MenuType<>(
                    SpecializationAltarMenu::new, FeatureFlags.DEFAULT_FLAGS));

    /** L'urne du QG : les noms des electeurs viennent avec l'ouverture, d'ou la fabrique de NeoForge. */
    public static final DeferredHolder<MenuType<?>, MenuType<HavenVoteMenu>> HAVEN_VOTE =
            MENUS.register("haven_vote", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension
                    .create(HavenVoteMenu::new));

    /** L'inventaire d'Arcencium (touche E) : les cases d'artefacts du joueur viennent avec l'ouverture. */
    public static final DeferredHolder<MenuType<?>, MenuType<ArcInventoryMenu>> ARC_INVENTORY =
            MENUS.register("arc_inventory", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension
                    .create(ArcInventoryMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
