package com.emerald.artifact;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Composants de donnees portes par les objets du mod. */
public class ModDataComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(EmeraldWeaponsMod.MODID);

    /**
     * L'artefact serti dans une piece d'equipement.
     *
     * Un composant plutot qu'un tag NBT libre : il est valide a la lecture,
     * synchronise au client sans code supplementaire, et survit aux copies de
     * pile faites par le jeu.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Artifact>> ARTIFACT =
            COMPONENTS.registerComponentType("artifact", builder -> builder
                    .persistent(Artifact.CODEC)
                    .networkSynchronized(Artifact.STREAM_CODEC));

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
