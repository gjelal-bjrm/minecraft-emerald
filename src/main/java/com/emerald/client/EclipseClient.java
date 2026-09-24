package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.Eclipse;
import com.emerald.weather.Weather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * CE QU'ON ENTEND PENDANT L'ECLIPSE (cahier §88).
 *
 * « Il faut vraiment une ambiance effrayante et glauque, aussi au niveau des sons, pas
 * seulement au niveau du visuel. » Tout est du jeu, rien n'est ajoute :
 *   - LE SILENCE D'ABORD : la musique se coupe et ne revient qu'apres ;
 *   - un SOUFFLE continu, la boucle de la Vallee des ames jouee plus grave ;
 *   - les BRUITS DE GROTTE du jeu, a intervalles irreguliers, la ou il n'y a pas de grotte ;
 *   - des CRIS LOINTAINS places DERRIERE le joueur, a une vingtaine de blocs ;
 *   - un BATTEMENT DE COEUR des qu'une horreur approche, de plus en plus rapide.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class EclipseClient {

    private static Drone drone;
    private static long nextCave;
    private static long nextCry;
    private static long nextBeat;

    private EclipseClient() {
    }

    /** La musique se tait pendant l'Eclipse. */
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (WeatherClient.current() == Weather.ECLIPSE) {
            event.setMusic(null);
        }
    }

    /** Appele a chaque tique client pendant l'Eclipse (WeatherClient.ambience). */
    static void tick(ClientLevel level, LocalPlayer player, RandomSource random, long time) {
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || drone.isStopped()) {
            mc.getMusicManager().stopPlaying();
            drone = new Drone();
            mc.getSoundManager().play(drone);
            nextCave = time + 100 + random.nextInt(200);
            nextCry = time + 300 + random.nextInt(300);
            nextBeat = time;
        }
        if (time >= nextCave) {
            nextCave = time + 300 + random.nextInt(360);
            level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(),
                    SoundSource.AMBIENT, 0.9F, 0.8F + random.nextFloat() * 0.2F, false);
        }
        if (time >= nextCry) {
            nextCry = time + 380 + random.nextInt(460);
            cryBehind(level, player, random);
        }
        heartbeat(level, player, time);
    }

    /** Un cri, loin, derriere : on se retourne, et il n'y a rien. */
    private static void cryBehind(ClientLevel level, LocalPlayer player, RandomSource random) {
        Vec3 look = player.getLookAngle();
        double len = Math.max(0.01, Math.hypot(look.x, look.z));
        double back = 18.0 + random.nextDouble() * 8.0;
        double side = (random.nextDouble() - 0.5) * 16.0;
        double x = player.getX() - look.x / len * back - look.z / len * side;
        double z = player.getZ() - look.z / len * back + look.x / len * side;
        double y = player.getY() + 1.0 + random.nextDouble() * 3.0;
        SoundEvent sound;
        float volume;
        float pitch;
        switch (random.nextInt(4)) {
            case 0 -> {
                sound = SoundEvents.GHAST_SCREAM;
                volume = 0.45F;
                pitch = 0.45F + random.nextFloat() * 0.1F;
            }
            case 1 -> {
                sound = SoundEvents.ENDERMAN_SCREAM;
                volume = 0.5F;
                pitch = 0.55F;
            }
            case 2 -> {
                sound = SoundEvents.SCULK_SHRIEKER_SHRIEK;
                volume = 0.55F;
                pitch = 0.6F + random.nextFloat() * 0.15F;
            }
            default -> {
                sound = SoundEvents.WITHER_AMBIENT;
                volume = 0.3F;
                pitch = 0.5F;
            }
        }
        level.playLocalSound(x, y, z, sound, SoundSource.HOSTILE, volume, pitch, false);
    }

    /** Le coeur bat quand une horreur approche : une fois par seconde et demie loin, deux fois par seconde tout pres. */
    private static void heartbeat(ClientLevel level, LocalPlayer player, long time) {
        double nearest = Double.MAX_VALUE;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isAlive() && entity.getType().is(Eclipse.HORRORS)) {
                nearest = Math.min(nearest, entity.distanceToSqr(player));
            }
        }
        if (nearest > 20.0 * 20.0 || time < nextBeat) {
            return;
        }
        double d = Math.sqrt(nearest);
        int every = (int) (10 + (d / 20.0) * 20);   // 10 a 30 tiques
        nextBeat = time + every;
        float volume = (float) (1.0 - d / 24.0);
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.PLAYERS, Math.max(0.35F, volume), 0.9F, false);
    }

    /** Le souffle : une boucle, qui monte doucement, et s'eteint quand l'Eclipse passe. */
    private static final class Drone extends AbstractTickableSoundInstance {

        Drone() {
            super(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP.value(), SoundSource.AMBIENT,
                    SoundInstance.createUnseededRandom());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 0.62F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            boolean on = WeatherClient.current() == Weather.ECLIPSE;
            this.volume = on ? Math.min(0.9F, this.volume + 0.01F) : this.volume - 0.02F;
            if (this.volume <= 0.0F) {
                this.stop();
            }
        }
    }
}
