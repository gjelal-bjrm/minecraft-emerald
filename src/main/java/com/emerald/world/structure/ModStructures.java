package com.emerald.world.structure;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Types de structure et de piece propres au mod. */
public class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, EmeraldWeaponsMod.MODID);

    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, EmeraldWeaponsMod.MODID);

    /** Pose un unique template centre sur le chunk de depart. */
    public static final DeferredHolder<StructureType<?>, StructureType<CenteredTemplateStructure>>
            CENTERED_TEMPLATE = STRUCTURE_TYPES.register("centered_template",
                    () -> () -> CenteredTemplateStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType>
            CENTERED_TEMPLATE_PIECE = STRUCTURE_PIECES.register("centered_template",
                    () -> (StructurePieceType.StructureTemplateType) CenteredTemplatePiece::new);

    public static void register(IEventBus eventBus) {
        STRUCTURE_TYPES.register(eventBus);
        STRUCTURE_PIECES.register(eventBus);
    }
}
