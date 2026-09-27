package com.emerald.client;

import com.emerald.block.entity.EclipsePortalBlockEntity;
import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.weather.Eclipse;
import com.emerald.weather.Weather;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * CE QU'ON VOIT ET CE QU'ON ENTEND PENDANT L'ECLIPSE (cahier §88, refait au §108).
 *
 * Le joueur, apres une Eclipse jouee (27 sept.) : « l'espece de cercle autour de moi qui est cense
 * mettre une ambiance sombre, je le vois tres facilement, et au loin des lignes comme un quadrille » ;
 * « j'entends quand meme les bruits de la nature, alors que dans ce genre de moment les oiseaux se
 * taisent et tous les animaux se cachent -- le vent, oui, l'eau, oui » ; « ca manque de son
 * inquietant ». D'ou :
 *
 *   - L'OBSCURITE, sans rien dessiner : l'effet Obscurite du Gardien, garde sur le joueur (cache), et
 *     dose (MobEffectInstanceMixin) -- le pack de shaders en fait un noir qui monte avec la distance,
 *     sans bord ni facette, le jeu de base un brouillard. Elle RESPIRE (dix secondes), et SE REFERME
 *     quand un portail ou une horreur approche. Les quatre coupoles du premier brouillard sont parties.
 *   - LE SILENCE DE LA NATURE : pendant l'Eclipse, rien de vivant et d'inoffensif ne se fait entendre
 *     -- les animaux (la categorie « neutre », quand une creature paisible est la ou le son part : les
 *     vehicules, wagonnets et bateaux gardent leur voix) et les oiseaux, grillons, grenouilles, hiboux
 *     et loups d'AmbientSounds. Le vent, l'eau, la pluie et les grottes restent.
 *   - LES SONS INQUIETANTS, puises dans les mods d'horreur du modpack (un son absent est saute) :
 *     un souffle continu a deux couches ; des CRIS lointains, souvent derriere, toutes les 7 a 16 s ;
 *     des CHUCHOTEMENTS tout pres ; des PAS derriere le joueur, qui s'arretent ; une CLOCHE au loin ;
 *     un souffle haletant et le battement de coeur quand une horreur approche.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class EclipseClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

    // ================================================================ l'obscurite

    /** La force de l'Obscurite hors danger, et sa respiration. */
    private static final float BASE = 0.50F;
    private static final float BREATH = 0.07F;
    private static final int BREATH_TICKS = 200;
    /** Au plus pres d'un danger. */
    private static final float CLOSED = 0.90F;
    private static final double HORROR_NEAR = 18.0;
    private static final double RIFT_NEAR = 24.0;

    private static float dark;
    private static boolean ours;

    // ================================================================ les sons

    /** Un son : son nom, sa force (au-dela de 1 : il porte plus loin), sa hauteur. */
    private record Cue(String id, float volume, float pitchLow, float pitchHigh) {
    }

    /** Des cris, loin, souvent derriere. */
    private static final Cue[] CRIES = {
            new Cue("graveyard:entity.lich.scare", 2.2F, 0.7F, 0.9F),
            new Cue("graveyard:entity.lich.hunt", 2.0F, 0.8F, 0.95F),
            new Cue("graveyard:entity.nightmare.ambient", 2.2F, 0.6F, 0.8F),
            new Cue("graveyard:entity.reaper.ambient", 2.0F, 0.7F, 0.9F),
            new Cue("graveyard:entity.wraith.ambient", 2.0F, 0.6F, 0.8F),
            new Cue("graveyard:entity.ghoul.roar", 2.4F, 0.6F, 0.8F),
            new Cue("deeperdarker:entity.stalker.ambient", 2.4F, 0.7F, 0.9F),
            new Cue("deeperdarker:entity.stalker.notice", 2.2F, 0.7F, 0.9F),
            new Cue("deeperdarker:entity.shattered.notice", 2.0F, 0.8F, 1.0F),
            new Cue("eternal_starlight:entity.tangled_skull.moan", 2.2F, 0.6F, 0.8F),
            new Cue("eternal_starlight:entity.tangled_hatred.ambient", 2.2F, 0.6F, 0.8F),
            new Cue("eternal_starlight:entity.lunar_monstrosity.roar", 2.6F, 0.5F, 0.7F),
            new Cue("evilcraft:mob_vengeancespirit_ambient", 2.0F, 0.6F, 0.8F),
            new Cue("endermanoverhaul:tall_enderman_stare", 2.0F, 0.6F, 0.8F),
            new Cue("endermanoverhaul:dark_oak_enderman_stare", 2.0F, 0.6F, 0.8F),
            new Cue("alexsmobs:murmur_idle", 2.0F, 0.6F, 0.8F),
            new Cue("alexsmobs:farseer_emerge", 2.2F, 0.6F, 0.8F),
            new Cue("minecraft:entity.ghast.scream", 1.8F, 0.45F, 0.55F),
            new Cue("minecraft:entity.enderman.scream", 1.8F, 0.5F, 0.6F),
            new Cue("minecraft:block.sculk_shrieker.shriek", 2.2F, 0.55F, 0.7F),
            new Cue("minecraft:entity.wither.ambient", 1.5F, 0.45F, 0.5F)};
    /** Des chuchotements, tout pres, tres bas. */
    private static final Cue[] WHISPERS = {
            new Cue("minecraft:ambient.soul_sand_valley.additions", 0.5F, 0.6F, 0.8F),
            new Cue("minecraft:entity.vex.ambient", 0.35F, 0.45F, 0.55F),
            new Cue("minecraft:entity.allay.ambient_without_item", 0.3F, 0.35F, 0.45F),
            new Cue("minecraft:entity.warden.tendril_clicks", 0.5F, 0.6F, 0.8F),
            new Cue("minecraft:entity.warden.listening", 0.45F, 0.7F, 0.8F),
            new Cue("graveyard:entity.nameless_hanged.breath", 0.6F, 0.8F, 1.0F),
            new Cue("deeperdarker:entity.shattered.ambient", 0.5F, 0.7F, 0.9F)};
    /** Des pas, derriere : l'un de ces pas, quatre a six fois. */
    private static final Cue[] STEPS = {
            new Cue("minecraft:entity.warden.step", 0.6F, 0.85F, 0.95F),
            new Cue("graveyard:entity.revenant.step", 0.6F, 0.8F, 0.9F),
            new Cue("minecraft:entity.zombie.step", 0.5F, 0.65F, 0.75F),
            new Cue("minecraft:block.gravel.step", 0.45F, 0.75F, 0.85F)};
    /** La cloche, tres loin. */
    private static final Cue[] BELLS = {
            new Cue("minecraft:block.bell.use", 4.0F, 0.5F, 0.55F),
            new Cue("minecraft:block.bell.resonate", 3.0F, 0.5F, 0.55F)};
    /** Le souffle haletant, quand une horreur est tout pres. */
    private static final Cue[] BREATHS = {
            new Cue("graveyard:entity.nameless_hanged.breath", 0.55F, 0.95F, 1.05F)};
    /** Les couches du souffle continu. */
    private static final Cue[] DRONES = {
            new Cue("minecraft:ambient.soul_sand_valley.loop", 0.9F, 0.62F, 0.62F),
            new Cue("undergarden:ambient.abyss", 0.55F, 0.8F, 0.8F),
            new Cue("minecraft:ambient.basalt_deltas.loop", 0.35F, 0.5F, 0.5F)};

    private static final List<Drone> DRONE_LAYERS = new ArrayList<>();
    private static long nextCry;
    private static long nextWhisper;
    private static long nextSteps;
    private static long nextBell;
    private static long nextBeat;
    private static long nextBreath;
    private static int stepsLeft;
    private static long nextStep;
    private static Vec3 stepAt = Vec3.ZERO;
    private static Cue stepCue;
    /** Les sons d'animaux d'AmbientSounds, lus une fois dans le jeu : ceux qui jouent deja sont arretes. */
    private static List<ResourceLocation> natureIds;
    /** Les sons de la nature tus pendant cette Eclipse : le journal le dit a la fin. */
    private static int silencedNature;
    private static int silencedAmbient;
    private static boolean running;

    private EclipseClient() {
    }

    // ================================================================ l'obscurite

    /**
     * La force de l'Obscurite pour cette entite (MobEffectInstanceMixin), ou -1 : hors de l'Eclipse,
     * l'effet garde la sienne.
     */
    public static float darkness(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!ours || (entity != mc.player && entity != mc.getCameraEntity())) {
            return -1.0F;
        }
        return dark;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            ours = false;
            dark = 0.0F;
            return;
        }
        boolean on = WeatherClient.current() == Weather.ECLIPSE;
        float fade = WeatherClient.intensity();
        if (on || (ours && fade > 0.01F)) {
            if (!player.hasEffect(MobEffects.DARKNESS)) {
                // cachee : ni icone, ni particule ; elle ne vit que chez ce client
                player.forceAddEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 60 * 30, 0, true, false, false), null);
            }
            ours = true;
            if (!mc.isPaused()) {
                float target = target(mc.level, player) * (on ? Math.max(fade, 0.2F) : fade);
                dark += (target - dark) * 0.05F;
            }
            if (on && !mc.isPaused() && mc.level.getGameTime() % 100 == 0) {
                stopNature(mc);
            }
            if (on && !running) {
                running = true;
                stopNature(mc);
                silencedNature = 0;
                silencedAmbient = 0;
                LOGGER.info("Eclipse (client) : sons connus -- cris {}/{}, chuchotements {}/{}, pas {}/{}, cloches {}/{},"
                                + " souffle {}/{}, fonds {}/{}", known(CRIES), CRIES.length, known(WHISPERS), WHISPERS.length,
                        known(STEPS), STEPS.length, known(BELLS), BELLS.length, known(BREATHS), BREATHS.length,
                        known(DRONES), DRONES.length);
            }
        } else if (ours) {
            ours = false;
            dark = 0.0F;
            MobEffectInstance effect = player.getEffect(MobEffects.DARKNESS);
            if (effect != null && effect.isAmbient() && !effect.isVisible()) {
                player.removeEffectNoUpdate(MobEffects.DARKNESS);
            }
        }
        if (!on && running) {
            running = false;
            LOGGER.info("Eclipse (client) : {} sons de la nature tus, dont {} d'AmbientSounds", silencedNature,
                    silencedAmbient);
        }
    }

    /** Ou en est le noir : il respire, et se referme a l'approche d'une horreur ou d'un portail. */
    private static float target(ClientLevel level, LocalPlayer player) {
        long time = level.getGameTime();
        float breath = BASE + BREATH * Mth.sin((float) (time % BREATH_TICKS) / BREATH_TICKS * Mth.TWO_PI);
        double horror = nearestEnemy(level, player);
        double rift = Double.MAX_VALUE;
        for (BlockPos pos : EclipsePortalBlockEntity.CLIENT_OPEN) {
            rift = Math.min(rift, Math.sqrt(pos.distToCenterSqr(player.position())));
        }
        float danger = 0.0F;
        if (horror < HORROR_NEAR) {
            danger = Math.max(danger, (float) (1.0 - horror / HORROR_NEAR));
        }
        if (rift < RIFT_NEAR) {
            danger = Math.max(danger, (float) (1.0 - rift / RIFT_NEAR) * 0.8F);
        }
        return Math.min(CLOSED, breath + (CLOSED - breath) * danger);
    }

    /** La distance de l'ennemi le plus proche, horreur de l'Eclipse ou non. */
    private static double nearestEnemy(ClientLevel level, LocalPlayer player) {
        double nearest = Double.MAX_VALUE;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity.isAlive() && (entity instanceof Enemy || entity.getType().is(Eclipse.HORRORS))) {
                nearest = Math.min(nearest, entity.distanceToSqr(player));
            }
        }
        return nearest == Double.MAX_VALUE ? nearest : Math.sqrt(nearest);
    }

    // ================================================================ le silence de la nature

    /**
     * AmbientSounds tient ses ambiances en boucle : un chant d'oiseaux lance avant l'Eclipse ne
     * repasse pas par onPlaySound. On l'arrete donc, au debut, puis toutes les cinq secondes.
     */
    private static void stopNature(Minecraft mc) {
        if (natureIds == null) {
            natureIds = new ArrayList<>();
            for (ResourceLocation id : mc.getSoundManager().getAvailableSounds()) {
                if ("ambientsounds".equals(id.getNamespace()) && id.getPath().startsWith("animals.")) {
                    natureIds.add(id);
                }
            }
            LOGGER.info("Eclipse (client) : {} sons d'animaux d'AmbientSounds a faire taire", natureIds.size());
        }
        for (ResourceLocation id : natureIds) {
            mc.getSoundManager().stop(id, null);
        }
    }

    /**
     * Un son « neutre » vient-il d'une creature ? La categorie sert aussi aux vehicules (les notres :
     * chocs, explosion, tremplins du JET-Board), aux wagonnets et aux bateaux, qui ne se taisent pas :
     * on ne coupe que ce qui sonne la ou se tient une creature vivante et paisible.
     */
    private static boolean fromCreature(SoundInstance sound) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || sound.isRelative()) {
            return false;
        }
        AABB around = new AABB(sound.getX() - 1.0, sound.getY() - 1.0, sound.getZ() - 1.0,
                sound.getX() + 1.0, sound.getY() + 2.0, sound.getZ() + 1.0);
        return !level.getEntitiesOfClass(Mob.class, around, mob -> mob.isAlive() && !(mob instanceof Enemy)).isEmpty();
    }

    /** La musique se tait pendant l'Eclipse. */
    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (WeatherClient.current() == Weather.ECLIPSE) {
            event.setMusic(null);
        }
    }

    /** Les animaux et les oiseaux se taisent ; le vent, l'eau, la pluie et les grottes restent. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || WeatherClient.current() != Weather.ECLIPSE) {
            return;
        }
        ResourceLocation id = sound.getLocation();
        boolean ambient = "ambientsounds".equals(id.getNamespace()) && id.getPath().startsWith("animals.");
        if (ambient || (sound.getSource() == SoundSource.NEUTRAL && fromCreature(sound))) {
            event.setSound(null);
            silencedNature++;
            if (ambient) {
                silencedAmbient++;
            }
            if (silencedNature <= 5) {
                LOGGER.info("Eclipse (client) : son de la nature tu : {}", id);
            }
        }
    }

    // ================================================================ les sons

    /** Appele a chaque tique client pendant l'Eclipse (WeatherClient.ambience). */
    static void tick(ClientLevel level, LocalPlayer player, RandomSource random, long time) {
        Minecraft mc = Minecraft.getInstance();
        DRONE_LAYERS.removeIf(Drone::isStopped);
        if (DRONE_LAYERS.isEmpty()) {
            mc.getMusicManager().stopPlaying();
            for (Cue cue : DRONES) {
                if (exists(cue)) {
                    Drone drone = new Drone(cue);
                    DRONE_LAYERS.add(drone);
                    mc.getSoundManager().play(drone);
                    if (DRONE_LAYERS.size() >= 2) {
                        break;
                    }
                }
            }
            nextCry = time + 80 + random.nextInt(120);
            nextWhisper = time + 400 + random.nextInt(400);
            nextSteps = time + 600 + random.nextInt(500);
            nextBell = time + 60;
            nextBeat = time;
            nextBreath = time;
            stepsLeft = 0;
        }
        if (time >= nextCry) {
            nextCry = time + 140 + random.nextInt(180);
            Cue cue = pick(CRIES, random);
            if (cue != null) {
                play(level, cue, around(player, random, 20.0, 38.0, random.nextInt(10) < 6), random);
            }
        }
        if (time >= nextWhisper) {
            nextWhisper = time + 400 + random.nextInt(400);
            Cue cue = pick(WHISPERS, random);
            if (cue != null) {
                play(level, cue, around(player, random, 2.5, 5.0, random.nextBoolean()), random);
            }
        }
        if (time >= nextSteps && stepsLeft == 0) {
            nextSteps = time + 600 + random.nextInt(500);
            stepCue = pick(STEPS, random);
            if (stepCue != null) {
                stepsLeft = 4 + random.nextInt(3);
                stepAt = around(player, random, 5.0, 7.0, true);
                nextStep = time;
            }
        }
        if (stepsLeft > 0 && time >= nextStep) {
            // les pas se rapprochent un peu, a chaque fois
            Vec3 toward = player.position().subtract(stepAt).normalize().scale(0.45);
            stepAt = stepAt.add(toward.x, 0.0, toward.z);
            play(level, stepCue, stepAt, random);
            stepsLeft--;
            nextStep = time + 7 + random.nextInt(3);
        }
        if (time >= nextBell) {
            nextBell = time + 1200 + random.nextInt(800);
            Cue cue = pick(BELLS, random);
            if (cue != null) {
                play(level, cue, around(player, random, 50.0, 64.0, false), random);
            }
        }
        double near = nearestEnemy(level, player);
        heartbeat(level, player, time, near);
        if (near < 10.0 && time >= nextBreath) {
            nextBreath = time + 50 + random.nextInt(20);
            Cue cue = pick(BREATHS, random);
            if (cue != null) {
                mc.getSoundManager().play(new SimpleSoundInstance(ResourceLocation.parse(cue.id()), SoundSource.PLAYERS,
                        cue.volume(), cue.pitchLow(), random, false, 0, SoundInstance.Attenuation.NONE,
                        0.0, 0.0, 0.0, true));
            }
        }
    }

    /** Un point autour du joueur, entre deux distances ; « behind » : dans son dos. */
    private static Vec3 around(LocalPlayer player, RandomSource random, double min, double max, boolean behind) {
        double angle = behind ? Math.toRadians(player.getYRot() + 180.0 + (random.nextDouble() - 0.5) * 100.0)
                : random.nextDouble() * Math.PI * 2.0;
        double distance = min + random.nextDouble() * (max - min);
        return new Vec3(player.getX() - Math.sin(angle) * distance, player.getY() + 0.5 + random.nextDouble() * 2.5,
                player.getZ() + Math.cos(angle) * distance);
    }

    /** Un son de la liste, parmi ceux que le jeu connait ; null si aucun. */
    private static Cue pick(Cue[] cues, RandomSource random) {
        List<Cue> known = new ArrayList<>();
        for (Cue cue : cues) {
            if (exists(cue)) {
                known.add(cue);
            }
        }
        return known.isEmpty() ? null : known.get(random.nextInt(known.size()));
    }

    private static int known(Cue[] cues) {
        int n = 0;
        for (Cue cue : cues) {
            if (exists(cue)) {
                n++;
            }
        }
        return n;
    }

    private static boolean exists(Cue cue) {
        ResourceLocation id = ResourceLocation.tryParse(cue.id());
        return id != null && Minecraft.getInstance().getSoundManager().getSoundEvent(id) != null;
    }

    private static void play(ClientLevel level, Cue cue, Vec3 at, RandomSource random) {
        float pitch = cue.pitchLow() + random.nextFloat() * (cue.pitchHigh() - cue.pitchLow());
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ResourceLocation.parse(cue.id()),
                SoundSource.HOSTILE, cue.volume(), pitch, random, false, 0, SoundInstance.Attenuation.LINEAR,
                at.x, at.y, at.z, false));
    }

    /** Le coeur bat quand un ennemi approche : une fois par seconde et demie loin, deux fois par seconde tout pres. */
    private static void heartbeat(ClientLevel level, LocalPlayer player, long time, double near) {
        if (near > 20.0 || time < nextBeat) {
            return;
        }
        int every = (int) (10 + (near / 20.0) * 20);   // 10 a 30 tiques
        nextBeat = time + every;
        float volume = (float) (1.0 - near / 24.0);
        level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                SoundSource.PLAYERS, Math.max(0.35F, volume), 0.9F, false);
    }

    /** Une couche du souffle : une boucle, qui monte doucement, et s'eteint quand l'Eclipse passe. */
    private static final class Drone extends AbstractTickableSoundInstance {

        private final float top;

        Drone(Cue cue) {
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.parse(cue.id())), SoundSource.AMBIENT,
                    SoundInstance.createUnseededRandom());
            this.top = cue.volume();
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = cue.pitchLow();
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            boolean on = WeatherClient.current() == Weather.ECLIPSE;
            this.volume = on ? Math.min(this.top, this.volume + 0.01F) : this.volume - 0.02F;
            if (this.volume <= 0.0F) {
                this.stop();
            }
        }
    }
}
