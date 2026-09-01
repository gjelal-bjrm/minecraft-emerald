package com.emerald.rune;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Les deux composants du systeme de runes.
 *
 * Deux et non un : une piece d'equipement porte une LISTE de runes gravees,
 * un objet rune porte UNE rune a graver. Les confondre obligerait a lire une
 * liste d'un element partout, et surtout a se demander a chaque fois si l'on
 * regarde ce qui est grave ou ce qui reste a graver.
 */
public class ModRuneComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(EmeraldWeaponsMod.MODID);

    /** Ce qu'une piece d'equipement porte de grave. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<RuneMark>>>
            RUNES = COMPONENTS.registerComponentType("runes", builder -> builder
                    .persistent(RuneMark.LIST_CODEC)
                    .networkSynchronized(RuneMark.LIST_STREAM_CODEC));

    /** Ce qu'un objet rune contient. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RuneMark>>
            RUNE = COMPONENTS.registerComponentType("rune", builder -> builder
                    .persistent(RuneMark.CODEC)
                    .networkSynchronized(RuneMark.STREAM_CODEC));

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
