package com.emerald.artifact;

import com.emerald.particles.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Les deux artefacts qui s'activent a la demande du joueur. */
public final class ArtifactActions {

    private static final String TAG_RETURN_AT = "ArcenciumReturnAt";
    private static final int RETURN_COOLDOWN = 2 * 60 * 20;
    private static final double JUMP_IMPULSE = 0.62;

    private ArtifactActions() {
    }

    /**
     * Bottes d'Eclair : une seconde impulsion en plein vol.
     *
     * Le serveur revalide que le joueur est bien en l'air ; le client peut
     * seulement demander, jamais decider.
     */
    public static void doubleJump(ServerPlayer player) {
        if (!Artifacts.wearing(player, Artifact.BOTTES_D_ECLAIR)
                || player.onGround() || player.isInWater()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, JUMP_IMPULSE, motion.z);
        player.hasImpulse = true;
        player.fallDistance = 0.0F;
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7F, 1.8F);
        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.PRISM_MOTE.get(),
                    player.getX(), player.getY(), player.getZ(), 14, 0.35, 0.1, 0.35, 0.06);
        }
    }

    /**
     * Bottes de Retour : ramene au point de reapparition.
     *
     * Dans le mode de jeu, les ancres SONT les points de reapparition : viser le
     * point de reapparition revient donc a viser l'ancre la plus recente, sans
     * qu'il faille attendre l'existence des ancres pour que l'artefact serve.
     */
    public static void returnHome(ServerPlayer player) {
        if (!Artifacts.wearing(player, Artifact.BOTTES_DE_RETOUR)) {
            return;
        }
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLong(TAG_RETURN_AT);
        if (last != 0 && now - last < RETURN_COOLDOWN) {
            return;
        }
        ServerLevel target = player.server.getLevel(player.getRespawnDimension());
        if (target == null) {
            return;
        }
        BlockPos spawn = player.getRespawnPosition();
        if (spawn == null) {
            spawn = target.getSharedSpawnPos();
        }
        player.getPersistentData().putLong(TAG_RETURN_AT, now);
        flash(player);
        player.teleportTo(target, spawn.getX() + 0.5, spawn.getY() + 0.1, spawn.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot());
        flash(player);
    }

    private static void flash(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 1.5F);
        if (player.level() instanceof ServerLevel server) {
            server.sendParticles(ModParticles.PRISM_MOTE.get(),
                    player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.12);
        }
    }
}
