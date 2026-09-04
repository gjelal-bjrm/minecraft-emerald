package com.emerald.specialization;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ce que les ailes laissent derriere elles : les pierres du Vitrail, les
 * etoiles du Souverain Astral.
 *
 * LA PREMIERE VERSION NE SE VOYAIT PAS. Les pierres etaient la poussiere qui
 * tombe d'un bloc (FALLING_DUST) : un grain de deux pixels qui chute en une
 * demi-seconde, teinte du bloc. Le joueur ne l'a jamais vu, et il avait
 * raison de le dire. On prend maintenant la particule d'OBJET -- celle qui
 * eclate quand on mange ou qu'on casse un outil -- nourrie d'une vraie gemme :
 * emeraude, diamant, eclat d'amethyste, lapis, poudre de redstone, pepite
 * d'or. Elle a la taille et la couleur d'un eclat de pierre, elle obeit a la
 * gravite, rebondit une fois et s'efface. C'est l'image demandee.
 *
 * Pour les Ailes du Souverain Astral, trois choses, toutes cote serveur pour
 * que l'equipe les voie :
 *
 *  - des ETOILES qui scintillent aux pointes des ailes, en continu ;
 *  - la CONSTELLATION : chaque etoile allumee (voir SkinBonus) tourne autour
 *    des epaules ; a cinq, une couronne blanche ;
 *  - une TRAINEE d'astres quand on plane.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class WingGems {

    /** Les six pierres, prises a des objets qui existent deja. */
    private static final ItemStack[] GEMS = {
            new ItemStack(Items.EMERALD),
            new ItemStack(Items.DIAMOND),
            new ItemStack(Items.AMETHYST_SHARD),
            new ItemStack(Items.LAPIS_LAZULI),
            new ItemStack(Items.REDSTONE),
            new ItemStack(Items.GOLD_NUGGET),
    };

    private WingGems() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || player.isInvisible()
                || Specialization.level(player) <= 0) {
            return;
        }
        WingSkin skin = Specialization.skin(player);
        if (skin == null) {
            return;
        }
        long time = level.getGameTime();
        if (skin.gems && time % 4 == 0) {
            gems(level, player);
        }
        if (skin == WingSkin.SOUVERAIN_ASTRAL) {
            stars(level, player, time);
        }
    }

    // ------------------------------------------------------------ le vitrail

    private static void gems(ServerLevel level, ServerPlayer player) {
        Vec3 at = onWing(level, player, 0.25 + level.random.nextDouble() * 0.9,
                1.0 + level.random.nextDouble() * 0.8);
        ItemStack gem = GEMS[level.random.nextInt(GEMS.length)];
        // UNE particule, avec sa propre vitesse : le compte a zero fait de
        // (dx, dy, dz) une vitesse et non un etalement. Un leger jet vers
        // l'exterieur et le bas, pour que la pierre se DETACHE de l'aile.
        Vec3 out = at.subtract(player.position()).multiply(1.0, 0.0, 1.0).normalize();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, gem),
                at.x, at.y, at.z, 0, out.x * 0.05, -0.02, out.z * 0.05, 1.0);
    }

    // ------------------------------------------------------ le souverain astral

    private static void stars(ServerLevel level, ServerPlayer player, long time) {
        // LES POINTES SCINTILLENT : une etoile toutes les trois tiques, a
        // l'extremite d'une aile, qui monte doucement et s'eteint.
        if (time % 3 == 0) {
            Vec3 tip = onWing(level, player, 0.9 + level.random.nextDouble() * 0.4,
                    1.5 + level.random.nextDouble() * 0.6);
            level.sendParticles(ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 0,
                    0.0, 0.012, 0.0, 1.0);
        }
        // LA CONSTELLATION TOURNE AUTOUR DES EPAULES : une etoile par etoile
        // allumee, sur un cercle qui avance d'un dixieme de tour par tique.
        int lit = SkinBonus.stars(player);
        if (lit > 0 && time % 2 == 0) {
            for (int i = 0; i < lit; i++) {
                double angle = time * 0.1 + i * (Math.PI * 2 / SkinBonus.STARS_FULL);
                double x = player.getX() + Math.cos(angle) * 0.9;
                double z = player.getZ() + Math.sin(angle) * 0.9;
                level.sendParticles(ParticleTypes.END_ROD, x, player.getY() + 1.9, z, 0,
                        0.0, 0.0, 0.0, 0.0);
            }
            // pleine : la couronne, blanche et dense, au-dessus de la tete
            if (lit >= SkinBonus.STARS_FULL && time % 4 == 0) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        player.getX(), player.getY() + 2.3, player.getZ(), 6, 0.35, 0.05, 0.35, 0.0);
            }
        }
        // LA TRAINEE EN PLANE : en l'air, en descente, une etoile par tique
        // derriere chaque aile
        if (!player.onGround() && player.getDeltaMovement().y < -0.02 && !player.isInWater()) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 behind = onWing(level, player, side * 0.7, 1.4);
                level.sendParticles(ParticleTypes.END_ROD, behind.x, behind.y, behind.z, 0,
                        0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    // ------------------------------------------------------------- l'aile

    /**
     * Un point SUR l'aile : {@code along} est la distance a l'epaule le long de
     * l'envergure (negative pour l'aile gauche, positive pour la droite ; le
     * signe est tire au sort si l'on passe une valeur positive sans intention),
     * {@code height} la hauteur au-dessus des pieds. Les ailes sont dans le dos,
     * un quart de bloc en arriere.
     */
    private static Vec3 onWing(ServerLevel level, ServerPlayer player, double along, double height) {
        float yaw = (float) Math.toRadians(player.yBodyRot);
        double side = along;
        if (side > 0 && level.random.nextBoolean()) {
            side = -side;
        }
        double back = -0.25;
        // le repere du joueur : l'avant est vers -sin(yaw), +cos(yaw)
        double dx = -Math.sin(yaw) * back + Math.cos(yaw) * side;
        double dz = Math.cos(yaw) * back + Math.sin(yaw) * side;
        return new Vec3(player.getX() + dx, player.getY() + height, player.getZ() + dz);
    }
}
