package com.emerald.datagen;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.item.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

public class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, EmeraldWeaponsMod.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // Items simples
        /*basicItem(ModItems.RAW_EMERALD.get());
        basicItem(ModItems.CRYSTAL_DUST.get());*/

        // Armes et outils (modèle "handheld")
        handheldItem(ModItems.EMERALD_SWORD);
        /*handheldItem(ModItems.EMERALD_AXE);
        handheldItem(ModItems.EMERALD_PICKAXE);
        handheldItem(ModItems.EMERALD_SHOVEL);
        handheldItem(ModItems.EMERALD_HOE);*/

        // Blocs spéciaux
        /*buttonItem(ModBlocks.EMERALD_BUTTON, ModBlocks.EMERALD_BLOCK);
        fenceItem(ModBlocks.EMERALD_FENCE, ModBlocks.EMERALD_BLOCK);
        wallItem(ModBlocks.EMERALD_WALL, ModBlocks.EMERALD_BLOCK);
        basicItem(ModBlocks.EMERALD_DOOR.asItem());*/
    }

    public void buttonItem(DeferredBlock<?> block, DeferredBlock<Block> baseBlock) {
        this.withExistingParent(block.getId().getPath(), mcLoc("block/button_inventory"))
                .texture("texture", ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                        "block/" + baseBlock.getId().getPath()));
    }

    public void fenceItem(DeferredBlock<?> block, DeferredBlock<Block> baseBlock) {
        this.withExistingParent(block.getId().getPath(), mcLoc("block/fence_inventory"))
                .texture("texture", ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                        "block/" + baseBlock.getId().getPath()));
    }

    public void wallItem(DeferredBlock<?> block, DeferredBlock<Block> baseBlock) {
        this.withExistingParent(block.getId().getPath(), mcLoc("block/wall_inventory"))
                .texture("wall", ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,
                        "block/" + baseBlock.getId().getPath()));
    }

    private ItemModelBuilder handheldItem(DeferredItem<?> item) {
        return withExistingParent(item.getId().getPath(),
                ResourceLocation.parse("item/handheld")).texture("layer0",
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,"item/" + item.getId().getPath()));
    }

    private ItemModelBuilder basicItem(DeferredItem<?> item) {
        return withExistingParent(item.getId().getPath(),
                ResourceLocation.parse("item/generated")).texture("layer0",
                ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID,"item/" + item.getId().getPath()));
    }
}
