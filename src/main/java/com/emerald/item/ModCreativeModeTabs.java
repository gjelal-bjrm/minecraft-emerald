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
                        output.accept(ModItems.PRISM_BRANCH);
                        output.accept(ModItems.PRISM_FIBER);
                        output.accept(ModItems.EMERALD_SWORD);
                        output.accept(ModItems.ARCENCIUM_BOW);
                        output.accept(ModItems.ARCENCIUM_SCEPTER);
                        output.accept(ModItems.ARCENCIUM_GLAIVE);
                        output.accept(ModItems.FATE_SHARD);
                        output.accept(ModItems.SANCTUARY_PROBE);
                        output.accept(ModItems.ARCENCIUM_HELMET);
                        output.accept(ModItems.ARCENCIUM_CHESTPLATE);
                        output.accept(ModItems.ARCENCIUM_LEGGINGS);
                        output.accept(ModItems.ARCENCIUM_BOOTS);
                        // un exemplaire de chaque artefact, deja serti dans l'objet
                        for (var artifact : com.emerald.artifact.Artifact.values()) {
                            output.accept(com.emerald.artifact.ArtifactItem.stack(
                                    artifact, ModItems.ARTIFACT.get()));
                        }
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
                        output.accept(ModBlocks.ARCENCIUM_CHEST);
                        output.accept(ModBlocks.SOCKET_BENCH);
                        // toute la palette du village, dans l'ordre de declaration
                        for (var block : ModBlocks.VILLAGE_BLOCKS) {
                            output.accept(block.get());
                        }
                    }).build()
    );

    public static void register(IEventBus eventBus){
        CREATIVE_MOD_TAB.register(eventBus);
    }
}
