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

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
