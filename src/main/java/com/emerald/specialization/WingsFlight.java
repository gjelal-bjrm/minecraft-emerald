package com.emerald.specialization;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que le serveur fait du vol des ailes : effacer la chute, et tenir le vol d'elytre
 * du palier +20.
 *
 * Le plane se calcule sur le client (voir WingsFlightClient) ; ici on ne
 * fait que retirer les degats de chute a proportion des ailes, pour qu'un
 * atterrissage apres un plane ne blesse pas. Des +5, un tiers de la chute
 * est pardonne ; a +15 et au-dela, toute la chute.
 *
 * LE VOL D'ELYTRE DES AILES +20 (22 sept., cahier §83). « J'ai monte les ailes a +20 et je
 * n'ai pas eu l'effet des elytres. » Au +20, apres le double saut, un second appui sur Saut
 * en l'air deploie les ailes : le vrai vol d'elytre du jeu, fusees comprises
 * (ArtifactInputClient demande, {@link #start} verifie).
 *
 * Le jeu ne tient ce vol qu'avec une elytre sur le torse : a chaque tique, le serveur
 * (LivingEntity.updateFallFlying) le coupe si la piece de torse ne sait pas voler. On le
 * RETABLIT donc a la fin de chaque tique du joueur, tant qu'il est en l'air ; au sol, dans
 * l'eau, en selle ou en levitation, on le laisse tomber, comme le jeu le fait d'une elytre.
 * Le client, lui, ne le coupe jamais de lui-meme : il suit ce que le serveur lui envoie.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class WingsFlight {

    /** Les joueurs dont les ailes +20 sont deployees. */
    private static final Set<UUID> FLYING = new HashSet<>();

    private WingsFlight() {
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int level = Specialization.level(player);
        if (level < 5) {
            return;
        }
        float kept = level >= Specialization.WINGS_FULL ? 0.0F
                : 1.0F - 0.33F - 0.067F * (level - 5);          // 67 % a +5, 0 % a +15
        if (kept <= 0.0F) {
            event.setCanceled(true);
        } else {
            event.setDamageMultiplier(event.getDamageMultiplier() * kept);
        }
    }

    // ---------------------------------------------------------------- le vol d'elytre

    /** Le client a deploye les ailes : on verifie, puis on tient le vol. */
    public static void start(ServerPlayer player) {
        if (player.isFallFlying()) {
            return;                                      // deja en vol (une vraie elytre, par exemple)
        }
        if (Specialization.level(player) < Specialization.MAX || !canFly(player)) {
            // le client s'est deja deploye de lui-meme : on le redresse (comme le jeu le fait
            // d'une elytre refusee, en renvoyant l'etat)
            player.stopFallFlying();
            return;
        }
        FLYING.add(player.getUUID());
        player.startFallFlying();
        // la poussee d'envol (le client pousse, ArtifactInputClient) : le souffle et l'eclat, pour tous
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LAUNCH, net.minecraft.sounds.SoundSource.PLAYERS,
                0.8F, 1.3F);
        if (player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(), player.getX(), player.getY() + 0.8,
                    player.getZ(), 24, 0.6, 0.3, 0.6, 0.12);
        }
    }

    /** Vrai si ce joueur vole sur ses ailes +20 : pour les essais. */
    public static boolean flying(ServerPlayer player) {
        return FLYING.contains(player.getUUID());
    }

    private static boolean canFly(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.onGround()
                && !player.isInWater() && !player.isInLava() && !player.isPassenger()
                && !player.onClimbable() && !player.getAbilities().flying
                && !player.hasEffect(MobEffects.LEVITATION);
    }

    /** Apres la tique du joueur (donc apres updateFallFlying) : le vol tient, ou se pose. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !FLYING.contains(player.getUUID())) {
            return;
        }
        if (Specialization.level(player) < Specialization.MAX || !canFly(player)) {
            FLYING.remove(player.getUUID());
            return;                                      // le jeu vient de couper le vol : on se pose
        }
        if (!player.isFallFlying()) {
            player.startFallFlying();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FLYING.remove(event.getEntity().getUUID());
    }
}
