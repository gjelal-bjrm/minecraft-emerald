package com.emerald.datagen;

import com.emerald.block.ModBlocks;
import com.emerald.main.EmeraldWeaponsMod;
// import com.emerald.blocks.ModBlocks; // à décommenter si tu as des blocs
// import com.emerald.util.ModTags;     // idem pour des tags personnalisés
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, EmeraldWeaponsMod.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(BlockTags.NEEDS_DIAMOND_TOOL) // ← personnalisé, pour contrôle visuel
                .add(ModBlocks.ARCENCIUM_ORE.get());
        // 👉 Exemple si tu avais des blocs :
        // tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.EMERALD_BLOCK.get());

        // 👉 Exemple si tu avais des tags custom :
        // tag(ModTags.Blocks.NEEDS_EMERALD_TOOL).add(ModBlocks.EMERALD_BLOCK.get());

        // ⚠️ Actuellement, vide, car ton mod ne semble pas encore avoir de blocs.
    }
}
