package com.emerald.item;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MOD_TAB =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, EmeraldWeaponsMod.MODID);

    public static final Supplier<CreativeModeTab> ARCENCIUM_ITEMS_TAB = CREATIVE_MOD_TAB.register(
            "arcencium_items_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.ARCENCIUM_INGOT.get()))
                    .title(Component.translatable("creativetab.emeraldweaponsmod.arcencium_items"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModItems.RAW_ARCENCIUM);
                        output.accept(ModItems.ARCENCIUM_INGOT);
                        output.accept(ModItems.EMERALD_SWORD);
                    }).build()
    );

    public static final Supplier<CreativeModeTab> ARCENCIUM_BLOCK_TAB = CREATIVE_MOD_TAB.register(
            "arcencium_blocks_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.ARCENCIUM_ORE.get()))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "arcencium_items_tab"))
                    .title(Component.translatable("creativetab.emeraldweaponsmod.arcencium_blocks"))
                    .displayItems((itemDisplayParameters, output) -> {
                        output.accept(ModBlocks.ARCENCIUM_ORE);
                        output.accept(ModBlocks.ARCENCIUM_BLOCK);
                    }).build()
    );

    public static void register(IEventBus eventBus){
        CREATIVE_MOD_TAB.register(eventBus);
    }
}
