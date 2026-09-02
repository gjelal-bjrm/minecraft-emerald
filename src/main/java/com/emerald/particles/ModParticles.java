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

    /** Mote prismatique : point lumineux teinte au hasard parmi les 5 cristaux,
     *  emis par les plantes et l'arbre de Prisme (voir ModClient.PrismMoteParticle). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PRISM_MOTE =
            PARTICLES.register("prism_mote", () -> new SimpleParticleType(false));

    // ------------------------------------------------- les particules des meteos
    //
    // Treize types, un par usage, et AUCUN partage avec le reste du mod : les
    // meteos puisaient dans les memes particules que les armes et les plantes,
    // et tout finissait par se ressembler. Voir tools/weather_particles.py pour
    // les textures, et client/WeatherParticles pour les comportements.

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST_SHEET =
            PARTICLES.register("mist_sheet", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST_WRAITH =
            PARTICLES.register("mist_wraith", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CRYSTAL_FIREFLY =
            PARTICLES.register("crystal_firefly", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PRISM_DROP =
            PARTICLES.register("prism_drop", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PRISM_SHARD =
            PARTICLES.register("prism_shard", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> METEOR_HEAD =
            PARTICLES.register("meteor_head", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> METEOR_EMBER =
            PARTICLES.register("meteor_ember", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ASH_FLAKE =
            PARTICLES.register("ash_flake", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GROUND_SHOCK =
            PARTICLES.register("ground_shock", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> QUAKE_DUST =
            PARTICLES.register("quake_dust", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLOAT_DEBRIS =
            PARTICLES.register("float_debris", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLOAT_BLADE =
            PARTICLES.register("float_blade", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STATIC_SPARK =
            PARTICLES.register("static_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_RAIN =
            PARTICLES.register("wind_rain", () -> new SimpleParticleType(true));
}
