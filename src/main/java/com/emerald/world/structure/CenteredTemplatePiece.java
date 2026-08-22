package com.emerald.world.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Une piece qui pose un template tel quel, sans jigsaw.
 *
 * JigsawReplacementProcessor remplace les blocs jigsaw restants du template
 * par leur final_state : sans lui, les 169 connecteurs de la cathedrale
 * apparaitraient en jeu comme des blocs jigsaw bruts.
 */
public class CenteredTemplatePiece extends TemplateStructurePiece {

    public CenteredTemplatePiece(StructureTemplateManager manager, ResourceLocation template, BlockPos pos) {
        super(ModStructures.CENTERED_TEMPLATE_PIECE.get(), 0, manager, template,
                template.toString(), settings(), pos);
    }

    public CenteredTemplatePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ModStructures.CENTERED_TEMPLATE_PIECE.get(), tag, manager, id -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings()
                .setIgnoreEntities(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        // pas de marqueurs de donnees dans nos templates
    }
}
