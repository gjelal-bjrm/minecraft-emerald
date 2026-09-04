package com.emerald.specialization;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ce que les ailes laissent voir, et surtout ce qu'elles ne laissent PAS voir.
 *
 * DEUX VERSIONS REFUSEES, et la lecon vaut d'etre ecrite. Les pierres qui
 * tombaient du Vitrail faisaient « pleurer les ailes » ; les etoiles du
 * Souverain Astral -- poussiere blanche en anneau, en trainee, aux pointes --
 * etaient trop visibles et EMPECHAIENT DE CONTEMPLER L'AILE. Le joueur l'a dit
 * en une phrase : « il faut des choses plus discretes et plus petites ».
 *
 * La regle qui en sort : un effet d'aile est un ACCENT, jamais un spectacle.
 * L'aile est le spectacle. Tout ce qu'on ajoute doit se lire du coin de l'oeil
 * et disparaitre quand on regarde en face.
 *
 *  - le Vitrail : un GLINT -- une facette qui accroche la lumiere -- une
 *    seule mote prismatique, minuscule, toutes les deux secondes et demie,
 *    posee SUR l'aile, sans vitesse, qui s'allume et s'eteint sur place.
 *    Rien ne tombe ;
 *  - le Souverain Astral : le scintillement est dans la LUEUR de la texture
 *    (WingsLayer), pas dans des particules. Ici seulement la Constellation,
 *    parce qu'elle est une information de jeu : une mote par etoile allumee,
 *    toutes les vingt tiques, qui tourne lentement au-dessus de la tete. Pas
 *    d'anneau, pas de trainee, pas de couronne.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class WingGems {

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
        if (skin.gems && time % 50 == 0) {
            glint(level, player);
        }
        if (skin == WingSkin.SOUVERAIN_ASTRAL) {
            constellation(level, player, time);
        }
    }

    /** Une facette du Vitrail accroche la lumiere : une mote, sur place, qui s'eteint. */
    private static void glint(ServerLevel level, ServerPlayer player) {
        Vec3 at = onWing(level, player, 0.35 + level.random.nextDouble() * 0.7,
                1.2 + level.random.nextDouble() * 0.7);
        level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                at.x, at.y, at.z, 0, 0.0, 0.0, 0.0, 0.0);
    }

    /** La Constellation : une mote par etoile allumee, lente, au-dessus de la tete. */
    private static void constellation(ServerLevel level, ServerPlayer player, long time) {
        int lit = SkinBonus.stars(player);
        if (lit <= 0 || time % 20 != 0) {
            return;
        }
        for (int i = 0; i < lit; i++) {
            double angle = time * 0.03 + i * (Math.PI * 2 / SkinBonus.STARS_FULL);
            level.sendParticles(com.emerald.particles.ModParticles.PRISM_MOTE.get(),
                    player.getX() + Math.cos(angle) * 0.55, player.getY() + 2.15,
                    player.getZ() + Math.sin(angle) * 0.55, 0, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Un point SUR l'aile : {@code along} la distance a l'epaule le long de
     * l'envergure (le cote est tire au sort), {@code height} la hauteur
     * au-dessus des pieds. Les ailes sont dans le dos, un quart de bloc en arriere.
     */
    private static Vec3 onWing(ServerLevel level, ServerPlayer player, double along, double height) {
        float yaw = (float) Math.toRadians(player.yBodyRot);
        double side = level.random.nextBoolean() ? along : -along;
        double back = -0.25;
        double dx = -Math.sin(yaw) * back + Math.cos(yaw) * side;
        double dz = Math.cos(yaw) * back + Math.sin(yaw) * side;
        return new Vec3(player.getX() + dx, player.getY() + height, player.getZ() + dz);
    }
}
