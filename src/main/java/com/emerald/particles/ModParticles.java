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

    // ------------------------------------------------- les particules du Morph Gun (Haven)
    //
    // Neuf types, un par usage, qu'aucun autre systeme n'emploie : ni le sceptre,
    // ni l'arc, ni les meteos, ni les plantes. Textures : tools/gun_particles.py ;
    // comportements : jak/gun/GunParticles. Toutes forcees (portee des traces de
    // la Vulcan Fury : 80 blocs).

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_SCATTER_PELLET =
            PARTICLES.register("gun_scatter_pellet", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_SCATTER_FLASH =
            PARTICLES.register("gun_scatter_flash", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_BLASTER_BOLT =
            PARTICLES.register("gun_blaster_bolt", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_BLASTER_SPARK =
            PARTICLES.register("gun_blaster_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_VULCAN_TRACER =
            PARTICLES.register("gun_vulcan_tracer", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_VULCAN_SPARK =
            PARTICLES.register("gun_vulcan_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_PEACE_MOTE =
            PARTICLES.register("gun_peace_mote", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_PEACE_BLAST =
            PARTICLES.register("gun_peace_blast", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_ECO_GLINT =
            PARTICLES.register("gun_eco_glint", () -> new SimpleParticleType(true));

    // Les ameliorations rouges et jaunes (jalon B) : huit types de plus, toujours un par usage.

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_WAVE_CHARGE =
            PARTICLES.register("gun_wave_charge", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_WAVE_DUST =
            PARTICLES.register("gun_wave_dust", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_PLASMITE_TRAIL =
            PARTICLES.register("gun_plasmite_trail", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_PLASMITE_BLAST =
            PARTICLES.register("gun_plasmite_blast", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_REFLEXOR_BOLT =
            PARTICLES.register("gun_reflexor_bolt", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_REFLEXOR_SPARK =
            PARTICLES.register("gun_reflexor_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_GYRO_TRACER =
            PARTICLES.register("gun_gyro_tracer", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_GYRO_SPARK =
            PARTICLES.register("gun_gyro_spark", () -> new SimpleParticleType(true));

    // Les ameliorations bleues et sombres : huit de plus.

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_ARC_BOLT =
            PARTICLES.register("gun_arc_bolt", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_ARC_SPARK =
            PARTICLES.register("gun_arc_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_NEEDLE_TRAIL =
            PARTICLES.register("gun_needle_trail", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_NEEDLE_SPARK =
            PARTICLES.register("gun_needle_spark", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_INVERTER_RISE =
            PARTICLES.register("gun_inverter_rise", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_INVERTER_MOTE =
            PARTICLES.register("gun_inverter_mote", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_NOVA_MOTE =
            PARTICLES.register("gun_nova_mote", () -> new SimpleParticleType(true));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GUN_NOVA_BLAST =
            PARTICLES.register("gun_nova_blast", () -> new SimpleParticleType(true));
}
