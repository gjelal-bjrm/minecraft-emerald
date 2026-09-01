package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * L'Onde de Purge : l'outil qui vide le terrain d'essai.
 *
 * Elle n'appartient pas au jeu -- aucun joueur ne la declenchera jamais --
 * mais a la mise au point : essayer un sanctuaire avec quarante morts-vivants
 * aux trousses fait perdre plus de temps que de le batir.
 *
 * Elle balaie plutot que d'effacer. Un /kill instantane laisse le doute : on
 * ne sait pas ce qui est mort, ni jusqu'ou. Un front qui s'ecarte a vue d'oeil
 * montre sa portee, et l'on voit tomber ce qu'il touche -- c'est un outil qui
 * rend compte de lui-meme.
 *
 * Elle ne touche QUE les hostiles. Les villageois, les golems et les animaux
 * sont souvent ce qu'on est justement en train d'essayer.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class PurgeWave {

    /**
     * Ce dont le front avance a chaque tick.
     *
     * Il s'adapte a la portee : sur deux cent cinquante blocs, une vitesse fixe
     * mettrait plus d'une minute a finir et l'on aurait range la commande avant
     * qu'elle ait balaye. Le balayage entier dure donc toujours environ trois
     * secondes, quelle que soit l'etendue.
     */
    private static final int SWEEP_TICKS = 60;
    private static final double MIN_SPEED = 3.5;
    /** Le nombre de gerbes qui dessinent le cercle. */
    private static final int SPOKES = 48;

    /**
     * Une onde en cours.
     *
     * Les victimes sont relevees UNE FOIS, au depart, et non a chaque tick.
     * Interroger le monde soixante fois sur une boite de cinq cents blocs de
     * cote couterait bien plus cher que l'effet lui-meme -- et les monstres qui
     * apparaitraient pendant le balayage n'ont de toute facon pas a etre
     * emportes par une onde partie avant eux.
     */
    private record Wave(ServerLevel level, Vec3 centre, double reach, double front,
                        double speed, List<LivingEntity> doomed) {
    }

    private static final List<Wave> waves = new ArrayList<>();

    private PurgeWave() {
    }

    /** Lance une onde. @return combien d'hostiles se trouvent dans sa portee */
    public static int start(ServerLevel level, Vec3 centre, double reach) {
        List<LivingEntity> doomed = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre, centre).inflate(reach),
                e -> e instanceof Enemy && e.isAlive());
        waves.add(new Wave(level, centre, reach, 0.0,
                Math.max(MIN_SPEED, reach / SWEEP_TICKS), doomed));
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        return doomed.size();
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (waves.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Wave> next = new ArrayList<>();
        for (Wave wave : waves) {
            if (wave.level() != level) {
                next.add(wave);
                continue;
            }
            double from = wave.front();
            double to = from + wave.speed();
            sweep(level, wave.centre(), from, to, wave.doomed());
            ring(level, wave.centre(), to);
            if (to < wave.reach()) {
                next.add(new Wave(level, wave.centre(), wave.reach(), to,
                        wave.speed(), wave.doomed()));
            }
        }
        waves.clear();
        waves.addAll(next);
    }

    /**
     * Ce que le front emporte en passant.
     *
     * On ne prend que la COURONNE entre l'ancien rayon et le nouveau : sans
     * cela, l'onde tuerait tout des le premier tick et le balayage ne serait
     * qu'une decoration posee sur un /kill.
     */
    private static void sweep(ServerLevel level, Vec3 centre, double from, double to,
                              List<LivingEntity> doomed) {
        for (LivingEntity victim : doomed) {
            if (!victim.isAlive()) {
                continue;
            }
            double d = victim.position().distanceTo(centre);
            if (d < from || d > to) {
                continue;
            }
            Vec3 at = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
            level.sendParticles(ModParticles.CRYSTALLINE_FISSURE.get(),
                    at.x, at.y, at.z, 8, 0.25, 0.35, 0.25, 0.03);
            victim.hurt(level.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
        }
    }

    /** Le front lui-meme, un cercle au sol qui s'ecarte. */
    private static void ring(ServerLevel level, Vec3 centre, double radius) {
        int spokes = (int) Math.max(SPOKES, Math.min(360, radius * 1.2));
        for (int i = 0; i < spokes; i++) {
            double a = i * 2.0 * Math.PI / spokes;
            double x = centre.x + Math.cos(a) * radius;
            double z = centre.z + Math.sin(a) * radius;
            int ground = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) x, (int) z);
            level.sendParticles(ParticleTypes.END_ROD, x, ground + 0.2, z,
                    1, 0.0, 0.02, 0.0, 0.0);
        }
    }
}
