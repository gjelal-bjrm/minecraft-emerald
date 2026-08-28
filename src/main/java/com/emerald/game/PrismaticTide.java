package com.emerald.game;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.Artifacts;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.awt.Color;

/**
 * La Maree Prismatique : la zone de jeu se referme sur la fin de partie.
 *
 * A partir de la phase Pression, le rayon vivable descend de 750 a 120 blocs.
 * Dehors, on SURVIT -- mais mal : une corrosion qui s'aggrave avec la
 * profondeur, et la Faiblesse. C'est un arbitrage, pas un mur : sortir de deux
 * blocs pour ramasser quelque chose reste anodin, s'enfoncer de deux cents en
 * devient une expedition qu'on prepare.
 *
 * Les Jambieres de Maree annulent tout : c'est ce qui les rend precieuses en
 * fin de partie. La defaite par le temps est deja portee par GameTicker ; la
 * Maree en est la traduction visible.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class PrismaticTide {

    /** Le rayon plancher : l'espace du dernier quart d'heure. */
    public static final int MIN_RADIUS = 120;

    /** La Maree se met en mouvement au debut de la phase Pression. */
    private static final long START_TICKS = 36L * 60L * 20L;

    private static ServerBossEvent bar;

    private PrismaticTide() {
    }

    /** Le rayon vivable a cet instant, ou -1 tant que la Maree dort. */
    public static int radius(ServerLevel level) {
        GameState state = GameState.get(level);
        if (state.status() != GameState.Status.RUNNING) {
            return -1;
        }
        long elapsed = state.elapsed(level);
        if (elapsed < START_TICKS) {
            return -1;
        }
        double t = Math.min(1.0, (elapsed - START_TICKS)
                / (double) (GameState.GAME_TICKS - START_TICKS));
        return (int) Math.round(GameState.PLAY_RADIUS
                - t * (GameState.PLAY_RADIUS - MIN_RADIUS));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)) {
            return;
        }
        int radius = radius(level);
        if (radius < 0) {
            hideBar();
            return;
        }
        updateBar(level, radius);
        BlockPos center = GameState.get(level).village();
        if (center.equals(BlockPos.ZERO)) {
            return;
        }
        if (level.getGameTime() % 40 == 0) {
            gnaw(level, center, radius);
        }
        if (level.getGameTime() % 10 == 0) {
            drawWall(level, center, radius);
        }
    }

    /**
     * La corrosion. Graduee par la profondeur au-dela du bord : environ un
     * coeur pres de la lisiere, jusqu'a quatre loin dedans, toutes les deux
     * secondes -- avec la Faiblesse, qui coupe l'envie d'y combattre.
     */
    private static void gnaw(ServerLevel level, BlockPos center, int radius) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - (center.getX() + 0.5);
            double dz = player.getZ() - (center.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist <= radius) {
                continue;
            }
            if (Artifacts.wearing(player, Artifact.JAMBIERES_DE_MAREE)) {
                continue;
            }
            double depth = dist - radius;
            float damage = (float) (2.0 + Math.min(6.0, depth * 0.03));
            player.hurt(level.damageSources().magic(), damage);
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, true, false, true));
            player.displayClientMessage(Component.translatable("game.emeraldweapons.tide.gnaw")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
            level.sendParticles(new DustParticleOptions(new Vector3f(0.9F, 0.5F, 1.0F), 1.1F),
                    player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.8, 0.4, 0.02);
        }
    }

    /**
     * Le mur de brume, dessine pres des joueurs qui approchent du bord.
     *
     * On ne dessine jamais dans un chunk non charge : le mur suit les joueurs,
     * il ne force aucune generation.
     */
    private static void drawWall(ServerLevel level, BlockPos center, int radius) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - (center.getX() + 0.5);
            double dz = player.getZ() - (center.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(dist - radius) > 48 || dist < 1.0) {
                continue;
            }
            double angle = Math.atan2(dz, dx);
            for (int k = -3; k <= 3; k++) {
                double a = angle + k * 0.035;
                int bx = (int) Math.round(center.getX() + Math.cos(a) * radius);
                int bz = (int) Math.round(center.getZ() + Math.sin(a) * radius);
                BlockPos pos = new BlockPos(bx, 0, bz);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
                float hue = (float) (((level.getGameTime() * 0.004) + k * 0.05) % 1.0);
                int rgb = Color.HSBtoRGB(hue, 0.6F, 1.0F);
                Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255F,
                        ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F);
                for (int h = 0; h <= 8; h += 2) {
                    level.sendParticles(new DustParticleOptions(color, 1.6F),
                            bx + 0.5, y + h, bz + 0.5, 1, 0.2, 0.6, 0.2, 0.0);
                }
            }
        }
    }

    private static void updateBar(ServerLevel level, int radius) {
        if (bar == null) {
            bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE,
                    BossEvent.BossBarOverlay.PROGRESS);
        }
        bar.setName(Component.translatable("game.emeraldweapons.tide.bar", radius));
        bar.setProgress((radius - MIN_RADIUS)
                / (float) (GameState.PLAY_RADIUS - MIN_RADIUS));
        for (ServerPlayer player : level.players()) {
            bar.addPlayer(player);          // idempotent : deja present = ignore
        }
    }

    private static void hideBar() {
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }
}
