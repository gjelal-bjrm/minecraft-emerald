package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.network.WeatherSyncPayload;
import com.emerald.particles.ModParticles;
import com.emerald.weather.ClientWeatherHolder;
import com.emerald.weather.Weather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

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
        boolean foggy = w == Weather.BRUME || w == Weather.NUIT
                || w == Weather.ORAGE || w == Weather.DECHIRURE
                || w == Weather.METEORES;
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
            case BRUME -> {
                // une teinte pastel qui derive : la brume est prismatique, pas grise
                float hue = (float) ((time * 0.0008) % 1.0);
                int rgb = Color.HSBtoRGB(hue, 0.22F, 0.78F);
                yield new float[]{((rgb >> 16) & 0xFF) / 255F,
                        ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F};
            }
            case NUIT -> new float[]{0.03F, 0.02F, 0.08F};
            case ORAGE -> new float[]{0.20F, 0.10F, 0.30F};
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
        float far = switch (current()) {
            case BRUME -> 56.0F;
            case NUIT -> 96.0F;
            case ORAGE -> 84.0F;
            case DECHIRURE -> 140.0F;
            case METEORES -> 180.0F;       // une brume de cendre, legere
            default -> -1.0F;
        };
        if (far < 0.0F) {
            return;
        }
        event.setFarPlaneDistance(lerp(event.getFarPlaneDistance(), far, intensity));
        if (current() == Weather.BRUME) {
            event.setNearPlaneDistance(lerp(event.getNearPlaneDistance(), 6.0F, intensity));
        }
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
        for (int i = 0; i < 14; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 30;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 30;
            double y = player.getY() + 3 + random.nextDouble() * 14;
            float hue = (float) (((x + z) * 0.02 + level.getGameTime() * 0.004) % 1.0);
            int rgb = Color.HSBtoRGB(hue < 0 ? hue + 1 : hue, 0.75F, 1.0F);
            level.addParticle(new DustParticleOptions(new Vector3f(
                            ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                            (rgb & 0xFF) / 255F), 0.9F),
                    x, y, z, 0.0, -0.9, 0.0);
        }
        // de rares gouttes lourdes, qui eclatent au sol
        if (random.nextInt(4) == 0) {
            level.addParticle(ModParticles.PRISM_MOTE.get(),
                    player.getX() + (random.nextDouble() - 0.5) * 20,
                    player.getY() + 2 + random.nextDouble() * 10,
                    player.getZ() + (random.nextDouble() - 0.5) * 20,
                    0.0, -0.5, 0.0);
        }
    }

    // -------------------------------------------------------------- ambiance

    private static void ambience(Weather w) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.isPaused()) {
            return;
        }
        RandomSource random = level.random;
        switch (w) {
            case BRUME -> {
                for (int i = 0; i < 2; i++) {
                    level.addParticle(ModParticles.PRISM_MOTE.get(),
                            player.getX() + (random.nextDouble() - 0.5) * 24,
                            player.getY() + random.nextDouble() * 6 - 1,
                            player.getZ() + (random.nextDouble() - 0.5) * 24,
                            0.0, 0.004, 0.0);
                }
            }
            case AURORE -> {
                // des rubans hauts : la couleur suit la position, le rideau ondule
                for (int i = 0; i < 3; i++) {
                    double x = player.getX() + (random.nextDouble() - 0.5) * 80;
                    double z = player.getZ() + (random.nextDouble() - 0.5) * 80;
                    double y = player.getY() + 28 + Math.sin(x * 0.05 + level.getGameTime() * 0.01) * 6
                            + random.nextDouble() * 8;
                    float hue = (float) (((x + z) * 0.004 + level.getGameTime() * 0.0015) % 1.0);
                    int rgb = Color.HSBtoRGB(hue < 0 ? hue + 1 : hue, 0.55F, 1.0F);
                    level.addParticle(new DustParticleOptions(new Vector3f(
                                    ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                                    (rgb & 0xFF) / 255F), 2.2F),
                            x, y, z, 0.0, 0.01, 0.0);
                }
            }
            case DECHIRURE -> {
                // tout MONTE : poussiere, eclats, brins d'herbe. C'est le seul
                // moyen de faire sentir que la gravite a lache, puisqu'on ne
                // voit pas sa propre legerete tant qu'on ne saute pas
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ParticleTypes.END_ROD,
                            player.getX() + (random.nextDouble() - 0.5) * 20,
                            player.getY() + random.nextDouble() * 5 - 2,
                            player.getZ() + (random.nextDouble() - 0.5) * 20,
                            0.0, 0.05 + random.nextDouble() * 0.05, 0.0);
                }
                for (int i = 0; i < 3; i++) {
                    float hue = (float) ((level.getGameTime() * 0.002 + i * 0.3) % 1.0);
                    int rgb = Color.HSBtoRGB(hue * 0.35F + 0.62F, 0.5F, 1.0F);
                    level.addParticle(new DustParticleOptions(new Vector3f(
                                    ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                                    (rgb & 0xFF) / 255F), 1.1F),
                            player.getX() + (random.nextDouble() - 0.5) * 16,
                            player.getY() + random.nextDouble() * 4 - 2,
                            player.getZ() + (random.nextDouble() - 0.5) * 16,
                            0.0, 0.08, 0.0);
                }
                // le bourdonnement de l'air : l'oreille comprend avant l'oeil
                if (level.getGameTime() % 70 == 0) {
                    level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sounds.SoundEvents.PORTAL_AMBIENT,
                            net.minecraft.sounds.SoundSource.AMBIENT, 0.5F, 0.45F, false);
                }
            }
            case NUIT -> prismaticRain(level, player, random);
            case METEORES -> {
                // des braises qui montent et de la cendre qui descend : le ciel
                // brule quelque part au-dessus, et ca doit se sentir au sol
                for (int i = 0; i < 2; i++) {
                    level.addParticle(ParticleTypes.SMALL_FLAME,
                            player.getX() + (random.nextDouble() - 0.5) * 26,
                            player.getY() + random.nextDouble() * 5 - 1,
                            player.getZ() + (random.nextDouble() - 0.5) * 26,
                            0.0, 0.03, 0.0);
                }
                if (random.nextInt(3) == 0) {
                    level.addParticle(new DustParticleOptions(
                                    new Vector3f(0.35F, 0.30F, 0.28F), 1.4F),
                            player.getX() + (random.nextDouble() - 0.5) * 30,
                            player.getY() + 10 + random.nextDouble() * 8,
                            player.getZ() + (random.nextDouble() - 0.5) * 30,
                            0.0, -0.12, 0.0);
                }
            }
            case ORAGE -> {
                if (level.getGameTime() % 90 == 0) {
                    level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                            net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(),
                            net.minecraft.sounds.SoundSource.AMBIENT, 0.7F, 0.35F, false);
                }
                for (int i = 0; i < 3; i++) {
                    level.addParticle(new DustParticleOptions(
                                    new Vector3f(0.62F, 0.30F, 0.90F), 1.8F),
                            player.getX() + (random.nextDouble() - 0.5) * 26,
                            player.getY() + random.nextDouble() * 7 - 2,
                            player.getZ() + (random.nextDouble() - 0.5) * 26,
                            0.0, 0.01, 0.0);
                }
                if (random.nextInt(8) == 0) {
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                            player.getX() + (random.nextDouble() - 0.5) * 20,
                            player.getY() + random.nextDouble() * 8,
                            player.getZ() + (random.nextDouble() - 0.5) * 20,
                            0.0, -0.05, 0.0);
                }
            }
            default -> {
            }
        }
    }
}
