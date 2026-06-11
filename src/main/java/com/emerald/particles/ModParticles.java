package com.emerald.particles;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "emeraldweapons");

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRYSTALLINE_FISSURE =
            PARTICLES.register("crystalline_fissure", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRYSTAL_GREEN =
            PARTICLES.register("crystal_green", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRYSTAL_ORANGE =
            PARTICLES.register("crystal_orange", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRYSTAL_PINK =
            PARTICLES.register("crystal_pink", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRYSTAL_RED =
            PARTICLES.register("crystal_red", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRYSTAL_YELLOW =
            PARTICLES.register("crystal_yellow", () -> new SimpleParticleType(true));
}
