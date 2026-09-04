package com.emerald.specialization;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Les pierres qui tombent des ailes.
 *
 * Demande du joueur pour les Ailes de Pierres Precieuses : « des pierres
 * precieuses qui tombent des ailes et qui disparaissent, de differentes
 * couleurs ». C'est ce que fait cette classe, et rien d'autre.
 *
 * ON N'INVENTE PAS DE PARTICULE. Minecraft en a une qui tombe, rebondit un peu
 * et s'efface -- la poussiere qui tombe d'un bloc (FALLING_DUST) -- et elle
 * prend la couleur du bloc qu'on lui donne. Six blocs de gemmes donnent donc
 * six couleurs franches, sans une seule texture a peindre : emeraude,
 * redstone, lapis, or, amethyste, diamant.
 *
 * COTE SERVEUR, pour que tout le monde les voie : une aile qui ne scintille
 * que pour son porteur ne sert a rien en equipe. Le cout est d'une particule
 * toutes les trois tiques par joueur concerne, ce qui ne se mesure pas.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class WingGems {

    /** Une pierre toutes les trois tiques : un egrenement, pas une fuite. */
    private static final int INTERVAL = 3;

    /** Les six couleurs, prises a des blocs qui existent deja. */
    private static final BlockState[] GEMS = {
            Blocks.EMERALD_BLOCK.defaultBlockState(),
            Blocks.REDSTONE_BLOCK.defaultBlockState(),
            Blocks.LAPIS_BLOCK.defaultBlockState(),
            Blocks.GOLD_BLOCK.defaultBlockState(),
            Blocks.AMETHYST_BLOCK.defaultBlockState(),
            Blocks.DIAMOND_BLOCK.defaultBlockState(),
    };

    private WingGems() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.isInvisible()
                || level.getGameTime() % INTERVAL != 0) {
            return;
        }
        int level0 = Specialization.level(player);
        if (level0 <= 0) {
            return;
        }
        WingSkin skin = Specialization.skin(player);
        if (skin == null || !skin.gems) {
            return;
        }
        // LE POINT DE CHUTE SUIT L'AILE, pas le joueur. Les ailes sont dans le
        // dos, ecartees de part et d'autre : on tire un point le long de
        // l'envergure, du cote gauche ou du cote droit, et un peu en arriere.
        float yaw = (float) Math.toRadians(player.yBodyRot);
        double back = -0.25;
        double side = (level.random.nextBoolean() ? 1 : -1)
                * (0.25 + level.random.nextDouble() * 0.85);
        // le repere du joueur : x avance vers -sin(yaw), z vers cos(yaw)
        double dx = -Math.sin(yaw) * back + Math.cos(yaw) * side;
        double dz = Math.cos(yaw) * back + Math.sin(yaw) * side;
        Vec3 at = new Vec3(player.getX() + dx,
                player.getY() + 1.1 + level.random.nextDouble() * 0.7,
                player.getZ() + dz);
        BlockState gem = GEMS[level.random.nextInt(GEMS.length)];
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, gem),
                at.x, at.y, at.z, 1, 0.04, 0.0, 0.04, 0.0);
    }
}
