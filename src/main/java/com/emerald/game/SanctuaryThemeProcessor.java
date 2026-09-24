package com.emerald.game;

import com.emerald.world.structure.ModStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;

/**
 * La pyramide dans la matiere du sanctuaire, des la pose (cahier §90).
 *
 * Les onze morceaux de la Pyramide Maudite arrivent en gres ; ce processeur les traduit bloc
 * par bloc au moment ou le modele se pose (SanctuaryTheme.apply), sans second passage sur ses
 * sept cent mille cases. Le rhabillage de la peau vient ensuite, par-dessus.
 */
public final class SanctuaryThemeProcessor extends StructureProcessor {

    public static final MapCodec<SanctuaryThemeProcessor> CODEC = Codec.STRING.fieldOf("theme")
            .xmap(id -> new SanctuaryThemeProcessor(SanctuaryTheme.byId(id)), p -> p.theme.id);

    private final SanctuaryTheme theme;

    public SanctuaryThemeProcessor(SanctuaryTheme theme) {
        this.theme = theme;
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
                                                             StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo current,
                                                             StructurePlaceSettings settings) {
        var themed = this.theme.apply(current.state());
        return themed == current.state() ? current
                : new StructureTemplate.StructureBlockInfo(current.pos(), themed, current.nbt());
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.SANCTUARY_THEME.get();
    }
}
