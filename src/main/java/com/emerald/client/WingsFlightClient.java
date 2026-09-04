package com.emerald.client;

import com.emerald.main.EmeraldWeaponsMod;
import com.emerald.specialization.Specialization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Le VOL des ailes : un plane qui grandit avec la specialisation.
 *
 * Le joueur l'a demande : des ailes qui ne portent pas ne sont qu'un dessin.
 * Sans elytre -- on n'en tient pas -- on donne au porteur, en l'air et la
 * touche de saut maintenue, une CHUTE RALENTIE et une POUSSEE vers ou il
 * regarde. Le tout se calcule ICI, sur le client : le mouvement d'un joueur
 * est pilote par son propre client, et un serveur qui le pousserait se ferait
 * contredire au tick suivant. Le serveur ne fait que constater la chute
 * douce et effacer ses degats (voir WingsFlight).
 *
 * L'echelle, par palier de specialisation :
 *
 *    +5   la chute se freine       (on descend a un tiers de la vitesse)
 *    +10  on plane                 (une poussee douce vers l'avant)
 *    +15  on plane fort            (l'envergure pleine : presque une elytre)
 *    +20  le double saut           (voir ArtifactInputClient)
 *
 * On ne vole jamais vers le HAUT : un plane descend toujours, meme au +20.
 * C'est ce qui le distingue du vol creatif, et ce qui le laisse honnete.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID, value = Dist.CLIENT)
public final class WingsFlightClient {

    /** Palier a partir duquel les ailes portent. */
    public static final int FROM = 5;

    private WingsFlightClient() {
    }

    /** La part de la chute retenue : 0 = rien, 1 = suspendu. Zero sous +5. */
    public static float brake(int level) {
        if (level < FROM) {
            return 0.0F;
        }
        return Mth.clamp(0.55F + 0.03F * (level - FROM), 0.55F, 0.92F);
    }

    /** La poussee vers l'avant, par tick. Zero sous +10. */
    public static float thrust(int level) {
        if (level < 10) {
            return 0.0F;
        }
        return Mth.clamp(0.010F + 0.0035F * (level - 10), 0.010F, 0.045F);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused()) {
            return;
        }
        int level = WingsClient.level(player);
        if (level < FROM || player.onGround() || player.isInWater() || player.isInLava()
                || player.onClimbable() || player.getAbilities().flying || player.isFallFlying()
                || player.isPassenger() || !mc.options.keyJump.isDown()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y >= 0.0) {
            return;                          // on ne porte que la chute, jamais la montee
        }
        float brake = brake(level);
        float thrust = thrust(level);
        // LA CHUTE RETENUE : la vitesse verticale est ramenee vers un plancher
        // doux, d'autant plus haut que les ailes sont grandes
        double floor = -0.40 * (1.0 - brake);
        double vy = Math.max(motion.y, floor);
        // LA POUSSEE : vers ou l'on regarde, a plat -- et jamais plus vite
        // qu'une course, pour que le plane reste un plane
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() > 1.0E-4) {
            flat = flat.normalize();
        }
        double vx = motion.x + flat.x * thrust;
        double vz = motion.z + flat.z * thrust;
        double speed = Math.sqrt(vx * vx + vz * vz);
        double cap = 0.28 + 0.02 * Math.min(10, level - FROM);
        if (speed > cap) {
            vx *= cap / speed;
            vz *= cap / speed;
        }
        player.setDeltaMovement(vx, vy, vz);
        player.fallDistance = 0.0F;
    }

    /** Vrai si ce joueur plane en ce moment, pour le rendu des ailes. */
    public static boolean gliding(net.minecraft.world.entity.player.Player player) {
        return WingsClient.level(player) >= Specialization.MAX / 4
                && !player.onGround() && player.getDeltaMovement().y < 0.0
                && Minecraft.getInstance().options.keyJump.isDown();
    }
}
