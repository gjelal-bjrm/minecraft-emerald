package com.emerald.main;

import com.emerald.block.ModBlocks;
import com.emerald.client.ModClient;
import com.emerald.datagen.ModDataGenerators;
import com.emerald.entity.KGDeathbotEntity;        // ← ajoute
import com.emerald.entity.WastelanderEntity;        // ← ajoute
import com.emerald.init.Jak3Registry;               // ← ajoute
import com.emerald.item.ModCreativeModeTabs;
import com.emerald.item.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.world.item.CreativeModeTabs;

@Mod(EmeraldWeaponsMod.MODID)
public class EmeraldWeaponsMod {
    public static final String MODID = "emeraldweapons";

    public EmeraldWeaponsMod(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);
        modEventBus.addListener(this::addItemsToCreativeTab);
        com.emerald.effects.ModEffects.EFFECTS.register(modEventBus);
        com.emerald.particles.ModParticles.PARTICLES.register(modEventBus);
        modEventBus.addListener(ModClient::onRegisterParticles);
        modEventBus.addListener(ModDataGenerators::gatherData);
        ModCreativeModeTabs.register(modEventBus);
        ModBlocks.register(modEventBus);
        Jak3Registry.register(modEventBus);         // ← modBus → modEventBus
        modEventBus.addListener(this::registerJak3Attributes); // ← modBus → modEventBus
    }

    private void addItemsToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.EMERALD_SWORD.get());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.ARCENCIUM_INGOT);
            event.accept(ModItems.RAW_ARCENCIUM);
        } else if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.ARCENCIUM_BLOCK);
            event.accept(ModBlocks.ARCENCIUM_ORE);
        }
    }

    @SubscribeEvent
    private void registerJak3Attributes(EntityAttributeCreationEvent event) {
        event.put(Jak3Registry.WASTELANDER.get(),
                WastelanderEntity.createAttributes().build());
        event.put(Jak3Registry.KG_DEATHBOT.get(),
                KGDeathbotEntity.createAttributes().build());
    }
}