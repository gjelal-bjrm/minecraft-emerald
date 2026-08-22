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

    /** Marque posee par la Fleche Prismatique : synergie arc -> epee. */
    public static final DeferredHolder<MobEffect, MobEffect> PRISMATIC_MARK =
            EFFECTS.register("prismatic_mark", PrismaticMarkEffect::new);
}
