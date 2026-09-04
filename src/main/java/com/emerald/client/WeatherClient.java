package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.WeatherSyncPayload;
import com.emerald.block.ModBlocks;
import com.emerald.particles.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import com.emerald.weather.ClientWeatherHolder;
import com.emerald.weather.Weather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

import java.awt.Color;

/**
 * Ce que la meteo FAIT VOIR : brouillard, teinte du ciel, ambiance.
 *
 * L'intensite est lissee cote client plutot que envoyee par le serveur : une
 * meteo qui s'installe en trois secondes se sent, une qui claque d'un coup se
 * remarque comme un bogue.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class WeatherClient {

    private static int weather;
    private static int remaining;
    private static int pending = -1;
    private static int warning;

    /** 0 -> 1 en douceur quand une meteo a brouillard s'installe. */
    private static float intensity;

    private WeatherClient() {
    }

    public static void accept(WeatherSyncPayload payload) {
        weather = payload.weather();
        remaining = payload.remaining();
        pending = payload.pending();
        warning = payload.warning();
        ClientWeatherHolder.current = weather;
    }

    public static Weather current() {
        return Weather.values()[Math.floorMod(weather, Weather.values().length)];
    }

    public static int remainingTicks() {
        return remaining;
    }

    public static int pendingOrdinal() {
        return pending;
    }

    public static int warningTicks() {
        return warning;
    }

    // ------------------------------------------------------------ transition

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Weather w = current();
        // LE PRISME N'EST PLUS UN BROUILLARD : il ne compte plus parmi eux.
        boolean foggy = w == Weather.NUIT
                || w == Weather.ORAGE || w == Weather.DECHIRURE
                || w == Weather.METEORES || w == Weather.AURORE;
        intensity = clamp(intensity + (foggy ? 0.015F : -0.015F));
        // le tremblement de fond des tempetes : imperceptible a l'arret, mais
        // c'est lui qui empeche l'image d'etre tout a fait stable, et donc
        // tout a fait rassurante
        WeatherAtmosphere.setRumble(switch (w) {
            case ORAGE -> 0.055F * intensity;
            case METEORES -> 0.035F * intensity;
            case DECHIRURE -> 0.025F * intensity;
            default -> 0.0F;
        });
        ambience(w);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** L'intensite de la meteo en cours, 0 -> 1 : le voile de ciel monte avec elle. */
    public static float intensity() {
        return intensity;
    }

    /**
     * Le voile de ciel d'une meteo : {rouge, vert, bleu, opacite, zenith,
     * distance}, ou null s'il n'y en a pas (voir SkyVeilRenderer). Le zenith
     * dit combien le voile s'amincit en haut : une brume laisse passer un peu
     * de lumiere, un ciel de cendres ou d'orage non. La distance est celle du
     * mur de brouillard, en blocs : la brume est proche, l'aurore lointaine.
     */
    public static float[] veilFor(Weather w, double time) {
        return switch (w) {
            case AURORE -> new float[]{0.07F, 0.10F, 0.20F, 0.45F, 0.90F, 210.0F};
            case NUIT -> new float[]{0.03F, 0.02F, 0.08F, 0.55F, 1.0F, 96.0F};
            case METEORES -> new float[]{0.30F, 0.13F, 0.07F, 0.88F, 1.0F, 150.0F};
            case DECHIRURE -> new float[]{0.30F, 0.18F, 0.38F, 0.72F, 0.90F, 140.0F};
            case ORAGE -> {
                // les eclairs de chaleur allument le voile lui-meme
                float k = WeatherAtmosphere.flash() * 0.8F;
                yield new float[]{0.16F + 0.39F * k, 0.10F + 0.30F * k, 0.24F + 0.51F * k,
                        0.85F, 1.0F, 84.0F};
            }
            default -> null;
        };
    }

    // ------------------------------------------------------------ brouillard

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (intensity <= 0.02F
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float[] target = fogColorFor(current(), event.getCamera().getEntity().level().getGameTime());
        if (target == null) {
            return;
        }
        event.setRed(lerp(event.getRed(), target[0], intensity));
        event.setGreen(lerp(event.getGreen(), target[1], intensity));
        event.setBlue(lerp(event.getBlue(), target[2], intensity));
    }

    private static float[] fogColorFor(Weather w, long time) {
        return switch (w) {
            // un indigo leger : il assombrit juste assez pour que les rideaux
            // ressortent, y compris en plein jour ou un melange additif se
            // noierait dans un ciel clair
            case AURORE -> new float[]{0.07F, 0.10F, 0.20F};
            case NUIT -> new float[]{0.03F, 0.02F, 0.08F};
            case ORAGE -> {
                // les eclairs de chaleur allument l'horizon : la brume s'eclaircit avec l'eclat
                float k = WeatherAtmosphere.flash() * 0.8F;
                yield new float[]{0.20F + 0.35F * k, 0.10F + 0.30F * k, 0.30F + 0.45F * k};
            }
            case DECHIRURE -> new float[]{0.30F, 0.18F, 0.38F};
            case METEORES -> new float[]{0.32F, 0.16F, 0.10F};
            default -> null;
        };
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (intensity <= 0.02F
                || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        // NOTRE BROUILLARD ETOUFFAIT DISTANT HORIZONS.
        //
        // Mesure : DH affiche 4 096 blocs, et l'on coupait a 56-210 -- deux a
        // sept pour cent de ce qu'il sait montrer. Le joueur voyait « pas si
        // loin que ca malgre Distant Horizons », et il avait raison : ce
        // n'etait pas DH, c'etait nous.
        //
        // Une seule meteo est VRAIMENT un brouillard, et c'est son nom qui le
        // dit : la Brume Prismatique. Elle garde sa vue courte, c'est tout son
        // propos. Les autres teintent le ciel et assombrissent -- elles n'ont
        // aucune raison de fermer l'horizon, et leurs distances passent donc a
        // l'echelle du terrain lointain.
        float far = switch (current()) {
            case ORAGE -> 420.0F;
            case NUIT -> 520.0F;           // il fait NOIR, il n'y a pas de brouillard
            case DECHIRURE -> 620.0F;
            case METEORES -> 780.0F;       // une brume de cendre, legere
            case AURORE -> 1400.0F;        // a peine une brume : on voit loin
            default -> -1.0F;
        };
        if (far < 0.0F) {
            return;
        }
        event.setFarPlaneDistance(lerp(event.getFarPlaneDistance(), far, intensity));
        // sans l'annulation, les distances posees ici sont ignorees
        event.setCanceled(true);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * La pluie d'Arcencium : des filets de couleur dans l'averse.
     *
     * La pluie du jeu est dessinee en blanc pur sur sa propre texture, sans
     * aucun point d'entree pour la teinter -- la repeindre voudrait dire
     * remplacer l'image pour TOUTES les pluies du monde, y compris celles qui
     * n'ont rien a voir avec la Nuit. On superpose donc nos propres gouttes :
     * l'averse grise reste dessous et donne le bruit, la couleur passe devant.
     *
     * La teinte suit la HAUTEUR de la goutte, pas le hasard : chaque filet
     * garde donc sa couleur en tombant au lieu de scintiller.
     */
    private static void prismaticRain(ClientLevel level, LocalPlayer player,
                                      RandomSource random) {
        // Trente gouttes par tick, chacune d'une des cinq couleurs d'eclair.
        // La goutte se brise en eclats au sol d'elle-meme (voir PrismDrop).
        // Douze ne suffisaient pas sous un pack de shaders, qui rend sa propre
        // pluie grise par-dessus et noyait les notres ; vu en capture, vingt-
        // deux restaient clairsemees.
        for (int i = 0; i < 30; i++) {
            level.addParticle(ModParticles.PRISM_DROP.get(),
                    player.getX() + (random.nextDouble() - 0.5) * 26,
                    player.getY() + 4 + random.nextDouble() * 12,
                    player.getZ() + (random.nextDouble() - 0.5) * 26,
                    random.nextInt(5), 0.0, 0.0);
        }
    }

    // -------------------------------------------------------------- ambiance

    /** L'angle du vent de l'Orage : il tourne lentement, la pluie le suit. */
    private static double wind;
    /** La rafale en cours et celle qu'elle rejoint, 0 -> 1, et le temps avant la suivante. */
    private static double gust;
    private static double gustTarget = 0.3;
    private static int gustTimer;
    /** Les eclairs de chaleur : le prochain, et le tonnerre qui le suit de loin. */
    private static int heatTimer = 80;
    private static int rumbleIn;
    private static double rumbleX;
    private static double rumbleZ;

    private static double ground(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(x), (int) Math.floor(z));
    }

    /**
     * L'ambiance de chaque meteo : ses particules, ses sons.
     *
     * CHAQUE METEO A SON VOCABULAIRE, et n'emprunte a aucune autre. C'etait
     * le reproche, et il etait juste : la mote de Prisme, la tige d'End et la
     * poussiere de redstone servaient a tout, et tout finissait par se
     * ressembler. Voir WeatherParticles pour ce que chaque particule fait.
     */
    private static void ambience(Weather w) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            return;
        }
        RandomSource random = level.random;
        long time = level.getGameTime();
        switch (w) {
            case AURORE -> aurore(level, player, random, time);
            case NUIT -> prismaticRain(level, player, random);
            case METEORES -> meteores(level, player, random, time);
            case DECHIRURE -> dechirure(level, player, random, time);
            case ORAGE -> orage(level, player, random, time);
            default -> {
            }
        }
    }


    /**
     * L'Aurore : les rideaux sont de la geometrie (AuroraRenderer). Au sol,
     * des LUCIOLES DE CRISTAL montent des filons d'Arcencium -- l'aurore
     * repond aux veines qui chantent, et c'est ainsi qu'on les trouve.
     */
    private static void aurore(ClientLevel level, LocalPlayer player, RandomSource random, long time) {
        if (time % 5 == 0) {
            BlockPos centre = player.blockPosition();
            int found = 0;
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-7, -5, -7), centre.offset(7, 5, 7))) {
                if (!level.getBlockState(pos).is(ModBlocks.ARCENCIUM_ORE.get())) {
                    continue;
                }
                if (random.nextInt(3) == 0) {
                    level.addParticle(ModParticles.CRYSTAL_FIREFLY.get(),
                            pos.getX() + random.nextDouble(), pos.getY() + 1.1 + random.nextDouble() * 0.6,
                            pos.getZ() + random.nextDouble(), 0, 0, 0);
                }
                if (++found >= 12) {
                    break;
                }
            }
        }
        // et quelques-unes, libres, pour que l'aurore vive meme loin des filons
        if (time % 6 == 0) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 28;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 28;
            level.addParticle(ModParticles.CRYSTAL_FIREFLY.get(),
                    x, ground(level, x, z) + 0.5 + random.nextDouble() * 3.5, z, 0, 0, 0);
        }
        if (time % 110 == 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.35F, 0.6F, false);
        }
    }

    /**
     * Les Meteores : de la CENDRE qui tombe, lente et balancee, et des braises
     * qui montent d'un sol qui brule quelque part. Un grondement lointain, de
     * loin en loin : le ciel n'est pas vide, il travaille.
     */
    private static void meteores(ClientLevel level, LocalPlayer player, RandomSource random, long time) {
        for (int i = 0; i < 3; i++) {
            level.addParticle(ModParticles.ASH_FLAKE.get(),
                    player.getX() + (random.nextDouble() - 0.5) * 30,
                    player.getY() + 8 + random.nextDouble() * 9,
                    player.getZ() + (random.nextDouble() - 0.5) * 30, 0, 0, 0);
        }
        if (random.nextInt(2) == 0) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 20;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 20;
            level.addParticle(ModParticles.METEOR_EMBER.get(),
                    x, ground(level, x, z) + 0.2, z, 0.0, 0.03 + random.nextDouble() * 0.02, 0.0);
        }
        if (time % 140 == 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.WEATHER, 0.30F, 0.45F, false);
        }
    }

    /**
     * La Dechirure : tout MONTE. La terre decolle en eclats qui tournent,
     * l'herbe s'arrache et s'eleve. On ne voit pas sa propre legerete tant
     * qu'on ne saute pas ; on voit le sol partir.
     */
    private static void dechirure(ClientLevel level, LocalPlayer player, RandomSource random, long time) {
        for (int i = 0; i < 3; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 18;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 18;
            level.addParticle(ModParticles.FLOAT_DEBRIS.get(), x, ground(level, x, z) + 0.15, z, 0, 0, 0);
        }
        for (int i = 0; i < 2; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 18;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 18;
            level.addParticle(ModParticles.FLOAT_BLADE.get(), x, ground(level, x, z) + 0.2, z, 0, 0, 0);
        }
        if (time % 70 == 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.PORTAL_AMBIENT,
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.5F, 0.45F, false);
        }
    }

    /**
     * L'Orage : une pluie OBLIQUE poussee par un vent qui tourne, des
     * etincelles qui gresillent sur tout ce qui est en metal, et les arcs qui
     * courent au sol (StormArcRenderer, qui se declenche tout seul).
     *
     * Le vent tourne lentement : une pluie toujours penchee du meme cote
     * finit par se lire comme un decor fixe. Celle-ci vire au fil des
     * minutes, et l'orage semble bouger autour du joueur.
     */
    /**
     * L'Orage, cote ambiance : ce qu'un orage FAIT quand rien ne tombe.
     *
     * Le ciel est couvert par le serveur (pluie et tonnerre du jeu de base :
     * l'obscurite, la pluie grise, son bruit). Par-dessus, trois choses que
     * le jeu de base n'a pas :
     *   - des RAFALES : le vent ne souffle pas, il souffle par a-coups. La
     *     pluie s'epaissit et se couche dans la rafale, et on l'entend venir ;
     *   - des ECLAIRS DE CHALEUR : le ciel clignote sans que rien ne tombe, la
     *     brume s'allume a l'horizon (voir fogColorFor), et le tonnerre roule
     *     loin, un peu apres -- c'est le son d'un orage, pas ses coups ;
     *   - le METAL qui gresille, et les arcs au sol (voir StormArcRenderer).
     */
    private static void orage(ClientLevel level, LocalPlayer player, RandomSource random, long time) {
        wind += 0.0015;
        // une cible de rafale qui change toutes les trois a onze secondes, une
        // valeur qui la rejoint en douceur ; une rafale sur trois est forte
        if (--gustTimer <= 0) {
            boolean strong = random.nextInt(3) == 0;
            gustTarget = strong ? 0.75 + random.nextDouble() * 0.25
                    : 0.12 + random.nextDouble() * 0.35;
            gustTimer = 60 + random.nextInt(160);
            if (strong) {
                // elle arrive d'ou vient le vent
                level.playLocalSound(player.getX() - Math.cos(wind) * 8, player.getY() + 3,
                        player.getZ() - Math.sin(wind) * 8,
                        net.minecraft.sounds.SoundEvents.BREEZE_WHIRL,
                        net.minecraft.sounds.SoundSource.WEATHER, 0.9F,
                        0.55F + random.nextFloat() * 0.2F, false);
            }
        }
        gust += (gustTarget - gust) * 0.04;
        double mag = 0.10 + 0.32 * gust;
        double wx = Math.cos(wind) * mag;
        double wz = Math.sin(wind) * mag;
        int drops = 6 + (int) (16 * gust);
        for (int i = 0; i < drops; i++) {
            // semees un peu au vent, pour qu'elles traversent le regard
            level.addParticle(ModParticles.WIND_RAIN.get(),
                    player.getX() + (random.nextDouble() - 0.5) * 22 - wx * 14,
                    player.getY() + 5 + random.nextDouble() * 7,
                    player.getZ() + (random.nextDouble() - 0.5) * 22 - wz * 14, wx, 0.0, wz);
        }
        if (gust > 0.6 && time % 45 == 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.BREEZE_IDLE_AIR,
                    net.minecraft.sounds.SoundSource.WEATHER, (float) (0.5 * gust), 0.5F, false);
        }
        // le metal gresille : on cherche ce qui conduit, autour du joueur
        if (time % 3 == 0) {
            BlockPos centre = player.blockPosition();
            int found = 0;
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-7, -3, -7), centre.offset(7, 4, 7))) {
                if (!conducts(level.getBlockState(pos))) {
                    continue;
                }
                level.addParticle(ModParticles.STATIC_SPARK.get(),
                        pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(),
                        pos.getZ() + random.nextDouble(), 0, 0, 0);
                if (++found >= 10) {
                    break;
                }
            }
        }
        // les eclairs de chaleur
        if (--heatTimer <= 0) {
            heatTimer = 60 + random.nextInt(120);
            WeatherAtmosphere.flashLocal(0xC8A8FF, 0.22F + random.nextFloat() * 0.18F);
            double a = random.nextDouble() * Math.PI * 2;
            rumbleX = player.getX() + Math.cos(a) * 70;
            rumbleZ = player.getZ() + Math.sin(a) * 70;
            rumbleIn = 12 + random.nextInt(30);
        }
        if (rumbleIn > 0 && --rumbleIn == 0) {
            level.playLocalSound(rumbleX, player.getY() + 12, rumbleZ,
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                    net.minecraft.sounds.SoundSource.WEATHER, 0.7F,
                    0.5F + random.nextFloat() * 0.2F, false);
        }
        if (time % 90 == 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(),
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.7F, 0.35F, false);
        }
    }

    /** Ce sur quoi l'electricite s'accroche : le metal, et ce qui y ressemble. */
    private static boolean conducts(BlockState state) {
        return state.is(Blocks.IRON_BLOCK) || state.is(Blocks.IRON_BARS) || state.is(Blocks.CHAIN)
                || state.is(Blocks.CAULDRON) || state.is(Blocks.HOPPER) || state.is(Blocks.LIGHTNING_ROD)
                || state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_TRAPDOOR)
                || state.is(BlockTags.ANVIL) || state.is(BlockTags.RAILS);
    }
}
