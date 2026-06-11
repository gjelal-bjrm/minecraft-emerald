package com.emerald.datagen;

import net.neoforged.fml.common.Mod;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.data.event.GatherDataEvent;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.bus.api.EventBusSubscriber;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModDataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper fileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();

        generator.addProvider(event.includeClient(), new ModItemModelProvider(packOutput, existingFileHelper));
        /*generator.addProvider(event.includeServer(), new LootTableProvider(
                packOutput,
                Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootTables::new, LootContextParamSets.BLOCK)),
                lookupProvider
        ));

        generator.addProvider(event.includeServer(), new ModRecipeProvider(packOutput, lookupProvider));*/

        var blockTags = new ModBlockTagProvider(packOutput, lookupProvider, fileHelper);
        generator.addProvider(event.includeServer(), blockTags);
        generator.addProvider(event.includeServer(), new ModItemTagProvider(packOutput, lookupProvider, blockTags.contentsGetter(), fileHelper));

        generator.addProvider(event.includeServer(), new ModWorldGenProvider(packOutput, lookupProvider));

        //generator.addProvider(event.includeClient(), new ModItemModelProvider(packOutput, fileHelper));
        //generator.addProvider(event.includeClient(), new ModBlockStateProvider(packOutput, fileHelper));*/
    }
}
