package com.emerald.game;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.particles.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * La brume qui baigne un sanctuaire.
 *
 * Elle ne fait rien : ni degats, ni ralentissement, ni la moindre regle. Elle
 * dit seulement qu'on entre quelque part. C'est ce qui manquait le plus a la
 * premiere version -- une place forte sans atmosphere n'est qu'un tas de blocs
 * bien ranges, et on la traverse sans jamais sentir qu'on y est arrive.
 *
 * Elle se densifie vers le sol et s'accroche aux murs, ce qui donne du relief
 * a la maconnerie de nuit. Sa teinte derive lentement, comme tout ce que le
 * mode fait luire.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class SanctuaryMist {

    private record Site(BlockPos centre, int radius, BlockPos anchor) {
    }

    /** Volatil : une brume se retrouve avec son sanctuaire, elle ne se sauve pas. */
    private static final List<Site> sites = new ArrayList<>();

    private SanctuaryMist() {
    }

    public static void register(BlockPos centre, int radius, BlockPos anchor) {
        sites.add(new Site(centre.immutable(), radius, anchor.immutable()));
    }

    /** Duree d'une pulsation, et intervalle entre deux. */
    private static final int PULSE_TICKS = 200;
    private static final int PULSE_EVERY = 600;

    /** L'ancre du sanctuaire le plus proche, pour la commande de verification. */
    @javax.annotation.Nullable
    public static BlockPos nearestAnchor(BlockPos near) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Site site : sites) {
            double dist = site.centre().distSqr(near);
            if (dist < bestDist) {
                bestDist = dist;
                best = site.anchor();
            }
        }
        return best;
    }

    public static void clearAll() {
        sites.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(Level.OVERWORLD)
                || sites.isEmpty()
                || level.getGameTime() % 4 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            for (Site site : sites) {
                double dx = player.getX() - site.centre().getX();
                double dz = player.getZ() - site.centre().getZ();
                // un peu au-dela des murs : la brume deborde, elle annonce
                if (dx * dx + dz * dz > Math.pow(site.radius() + 24, 2)) {
                    continue;
                }
                breathe(level, player, site);
                // La pulsation ne part QUE dans l'enceinte : dehors, elle
                // reviendrait a poser un panneau sur la carte, ce qui oterait
                // tout interet a chercher l'entree.
                boolean inside = dx * dx + dz * dz <= Math.pow(site.radius(), 2);
                if (inside && level.getGameTime() % PULSE_EVERY == 0) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new com.emerald.network.AnchorPulsePayload(
                                    site.anchor().getX(), site.anchor().getY(),
                                    site.anchor().getZ(), PULSE_TICKS));
                    level.playSound(null, player.blockPosition(),
                            net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,
                            net.minecraft.sounds.SoundSource.AMBIENT, 0.6F, 1.6F);
                }
                break;
            }
        }
    }

    /**
     * Les nappes, autour du joueur plutot que sur tout le site.
     *
     * Remplir soixante mille metres carres de particules serait aussi cher
     * qu'inutile : on n'en voit jamais que ce qui nous entoure. La brume suit
     * donc le joueur, a l'interieur du sanctuaire.
     */
    private static void breathe(ServerLevel level, ServerPlayer player, Site site) {
        var random = level.random;
        for (int i = 0; i < 5; i++) {
            double x = player.getX() + (random.nextDouble() - 0.5) * 34;
            double z = player.getZ() + (random.nextDouble() - 0.5) * 34;
            // basse : la brume tient au sol, elle ne flotte pas a hauteur d'yeux
            double y = player.getY() + random.nextDouble() * 2.2 - 0.6;
            float hue = (float) (((x + z) * 0.004 + level.getGameTime() * 0.0006) % 1.0);
            int rgb = Color.HSBtoRGB(hue * 0.35F + 0.55F, 0.30F, 0.85F);
            level.sendParticles(new DustParticleOptions(new Vector3f(
                            ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                            (rgb & 0xFF) / 255F), 3.4F),
                    x, y, z, 1, 1.6, 0.25, 1.6, 0.0);
        }
        if (random.nextInt(3) == 0) {
            level.sendParticles(ModParticles.PRISM_MOTE.get(),
                    player.getX() + (random.nextDouble() - 0.5) * 26,
                    player.getY() + random.nextDouble() * 5,
                    player.getZ() + (random.nextDouble() - 0.5) * 26,
                    1, 0.5, 0.5, 0.5, 0.01);
        }
        // le souffle froid, rare : c'est lui qui fait baisser la voix
        if (level.getGameTime() % 160 == 0) {
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    player.getX(), player.getY() + 1.2, player.getZ(),
                    12, 3.0, 1.0, 3.0, 0.01);
        }
    }
}
