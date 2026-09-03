package com.emerald.util;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import org.joml.Vector3f;

import java.util.List;

/**
 * La fete d'une reussite.
 *
 * Une amelioration qui passe, une rarete qui monte, un palier de
 * specialisation gagne : jusqu'ici une ligne grise dans le chat et un son
 * d'enclume. Le joueur l'a dit -- « rien qui nous rejouit ». Ici, la meme
 * chose partout : un titre plein ecran dans la couleur de l'evenement, une
 * gerbe de particules autour du joueur, deux sons superposes, et pour les
 * grandes marches -- +8 et au-dela, les hautes raretes, les paliers ronds --
 * un feu d'artifice. Le tout cote serveur : tout le monde autour le voit.
 */
public final class Celebration {

    private Celebration() {
    }

    /** Une piece qui passe un cran : +N en or, feu d'artifice a partir de +8. */
    public static void upgrade(Player player, int level) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        int color = level >= 8 ? 0xFFD36B : 0x78E8AE;
        title(sp, Component.translatable("celebrate.emeraldweapons.upgrade", level)
                        .withStyle(style -> style.withColor(color)),
                Component.translatable("celebrate.emeraldweapons.upgrade.sub")
                        .withStyle(ChatFormatting.GRAY));
        burst(sp, ParticleTypes.TOTEM_OF_UNDYING, 40, 0.6);
        burst(sp, ParticleTypes.END_ROD, 16, 0.3);
        sound(sp, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.1F + level * 0.03F);
        sound(sp, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.9F);
        if (level >= 8) {
            sound(sp, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
            firework(sp, IntList.of(0xFFD36B, 0xFFF3C4, color));
        }
    }

    /** Une rarete qui monte : le nom du rang dans sa couleur ; feu d'artifice des hauts rangs. */
    public static void rarity(Player player, Component label, int colour, int rank) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        title(sp, label.copy().withStyle(style -> style.withColor(colour)),
                Component.translatable("celebrate.emeraldweapons.rarity.sub")
                        .withStyle(ChatFormatting.GRAY));
        burst(sp, ParticleTypes.ENCHANT, 60, 1.0);
        burst(sp, ParticleTypes.ELECTRIC_SPARK, 24, 0.5);
        burst(sp, new DustParticleOptions(new Vector3f(
                ((colour >> 16) & 0xFF) / 255F, ((colour >> 8) & 0xFF) / 255F, (colour & 0xFF) / 255F),
                1.4F), 30, 0.7);
        sound(sp, SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F + rank * 0.06F);
        sound(sp, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.0F);
        if (rank >= 5) {
            sound(sp, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
            firework(sp, IntList.of(colour, 0xFFFFFF, colour));
        }
    }

    /** Un palier de specialisation : violet prismatique ; feu d'artifice aux paliers ronds. */
    public static void specialization(Player player, int tier, int points) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        title(sp, Component.translatable("celebrate.emeraldweapons.spec", tier)
                        .withStyle(style -> style.withColor(0xB98CFF)),
                Component.translatable("celebrate.emeraldweapons.spec.sub", points)
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
        burst(sp, ParticleTypes.END_ROD, 40, 0.8);
        for (int i = 0; i < 6; i++) {
            int rgb = java.awt.Color.HSBtoRGB(i / 6F, 0.6F, 1.0F);
            burst(sp, new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F,
                    ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F), 1.5F), 10, 0.9);
        }
        sound(sp, SoundEvents.PLAYER_LEVELUP, 1.0F, 1.1F);
        sound(sp, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.8F);
        if (tier % 5 == 0) {
            sound(sp, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.0F);
            firework(sp, IntList.of(0xB98CFF, 0xFF6B6B, 0xFFD36B, 0x78E8AE, 0x9CE8FF));
        }
    }

    // ---------------------------------------------------------- les briques

    private static void title(ServerPlayer player, Component top, Component bottom) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 90, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(top));
        player.connection.send(new ClientboundSetSubtitleTextPacket(bottom));
    }

    private static void burst(ServerPlayer player, ParticleOptions particle, int count, double spread) {
        ((ServerLevel) player.level()).sendParticles(particle,
                player.getX(), player.getY() + 1.0, player.getZ(),
                count, spread, 0.8, spread, 0.15);
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound,
                              float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS,
                volume, pitch);
    }

    private static void firework(ServerPlayer player, IntList colors) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL, colors, IntList.of(0xFFFFFF), true, true))));
        player.level().addFreshEntity(new FireworkRocketEntity(player.level(),
                player.getX(), player.getY() + 1.0, player.getZ(), rocket));
    }
}
