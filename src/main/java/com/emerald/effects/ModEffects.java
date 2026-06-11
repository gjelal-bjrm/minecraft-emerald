package com.emerald.effects;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, "emeraldweapons");

    public static final DeferredHolder<MobEffect, MobEffect> CRYSTALLINE_AURA =
            EFFECTS.register("crystalline_aura", CrystallineAuraEffect::new);
}
