package com.emerald.weapons;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Fouet d'Arcencium -- la Morsure d'Orage.
 *
 * Quatrieme membre de la famille, et le premier qui ne soit ni lame ni corde.
 * L'epee est la Fureur, l'arc la Tension, le sceptre la Concorde ; le fouet est
 * l'acharnement. Il ne recompense pas la frappe juste mais la frappe QUI NE
 * S'ARRETE PAS, ce qui en fait une arme d'un temperament oppose aux trois
 * autres : lachez la pression une poignee de secondes et vous tenez un bout de
 * lanière sans force.
 *
 * LA CHARGE D'ORAGE gouverne tout. Chaque coup au corps a corps en ajoute une,
 * jusqu'a cinq ; quatre secondes sans toucher personne et l'orage retombe d'un
 * coup, entierement. Ce n'est pas une jauge qui s'use, c'est une jauge qui
 * s'eteint -- la difference compte, parce qu'elle interdit de thesauriser sa
 * charge avant un gros ennemi.
 *
 * Clic gauche : la morsure. Les degats montent avec la charge, et l'eclair
 *               saute du premier touche a ses voisins, d'autant plus loin que
 *               l'orage est haut. A cinq, la foudre tombe pour de bon.
 * Clic droit  : la Ruee. Un bond dans l'axe du regard qui foudroie tout ce
 *               qu'il traverse. Son rechargement RACCOURCIT avec la charge :
 *               c'est en restant a l'attaque qu'on reste mobile, ce qui ferme
 *               la boucle -- l'arme rend agressif celui qui la porte.
 *
 * Le prix de tout cela est a la forge : quatre lingots d'Arcencium, un bloc
 * d'emeraude, et de quoi tresser la laniere. On ne tombe pas dessus par hasard.
 */
public class ArcenciumLashItem extends Item {

    // --- la Charge d'Orage
    public static final int CHARGE_MAX = 5;
    /** Sans toucher personne pendant ce delai, l'orage retombe ENTIEREMENT. */
    public static final int CHARGE_DECAY = 4 * 20;

    // --- la morsure
    /** Degats ajoutes par cran de charge, au-dela de l'attaque de base. */
    private static final float BITE_PER_CHARGE = 1.15F;
    /** Portee du saut de l'eclair d'un ennemi a l'autre. */
    private static final double ARC_RANGE = 6.0;
    /** Part des degats transmise aux voisins. */
    private static final float ARC_SHARE = 0.55F;

    // --- la Ruee
    private static final int DASH_COOLDOWN = 9 * 20;
    /** Ce que chaque cran de charge retranche au rechargement. */
    private static final int DASH_PER_CHARGE = 24;        // 1,2 s
    private static final int DASH_FLOOR = 3 * 20;
    private static final double DASH_POWER = 1.85;
    private static final double DASH_LIFT = 0.32;
    private static final float DASH_DAMAGE = 7.0F;
    private static final double DASH_WIDTH = 1.6;

    private static final String TAG_CHARGE = "ArcenciumLashCharge";
    private static final String TAG_LAST_HIT = "ArcenciumLashLastHit";

    public ArcenciumLashItem(Properties properties) {
        super(properties);
    }

    // ------------------------------------------------------------ la charge

    /**
     * L'orage tel qu'il est MAINTENANT.
     *
     * On ne fait pas retomber la charge dans un tick : on date le dernier coup
     * et l'on relit la date au moment ou l'on en a besoin. Un compteur decremente
     * chaque tick couterait un abonnement au tick du monde pour une valeur que
     * deux soustractions donnent exactement -- et il continuerait de tourner pour
     * un joueur qui a range l'arme.
     */
    public static int charge(Player player) {
        long since = player.level().getGameTime()
                - player.getPersistentData().getLong(TAG_LAST_HIT);
        if (since > CHARGE_DECAY) {
            return 0;
        }
        return Math.min(CHARGE_MAX, player.getPersistentData().getInt(TAG_CHARGE));
    }

    private static void addCharge(Player player) {
        int next = Math.min(CHARGE_MAX, charge(player) + 1);
        player.getPersistentData().putInt(TAG_CHARGE, next);
        player.getPersistentData().putLong(TAG_LAST_HIT, player.level().getGameTime());
    }

    // ------------------------------------------------------------ clic droit

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        int charge = charge(player);
        Vec3 look = player.getLookAngle().normalize();
        Vec3 from = player.position();

        if (level instanceof ServerLevel server) {
            dashDamage(server, player, from, look, charge);
            stack.hurtAndBreak(3, player, LivingEntity.getSlotForHand(hand));
        }

        // La poussee s'applique des deux cotes.
        //
        // Cote serveur seul, le client corrigerait la position au tick suivant
        // et le bond partirait en saccade ; hurtMarked ordonne l'envoi du
        // paquet de vitesse, et le client garde la main sur son propre
        // deplacement pour que le geste reponde immediatement.
        player.setDeltaMovement(look.x * DASH_POWER,
                Math.max(look.y * DASH_POWER, 0.0) + DASH_LIFT,
                look.z * DASH_POWER);
        player.hurtMarked = true;
        // un bond n'est pas une chute : on efface l'elan accumule
        player.resetFallDistance();

        int cooldown = Math.max(DASH_FLOOR, DASH_COOLDOWN - charge * DASH_PER_CHARGE);
        player.getCooldowns().addCooldown(this, cooldown);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                0.5F, 1.6F + charge * 0.06F);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Ce que la Ruee laisse derriere elle.
     *
     * On balaie le couloir du bond EN UNE FOIS, au depart, plutot que de suivre
     * le joueur tick par tick. Le bond dure trois ou quatre ticks : le suivre
     * demanderait un etat par joueur, une file a purger et un cas de sortie
     * pour la deconnexion, tout cela pour une boite que l'on sait deja tracer.
     */
    private void dashDamage(ServerLevel level, Player player, Vec3 from, Vec3 look, int charge) {
        Vec3 to = from.add(look.scale(DASH_POWER * 7.0));
        AABB corridor = new AABB(from, to).inflate(DASH_WIDTH);
        float damage = DASH_DAMAGE + charge * BITE_PER_CHARGE;

        for (Entity entity : level.getEntities(player, corridor,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isAlliedTo(player))) {
            entity.hurt(level.damageSources().lightningBolt(), damage);
            spark(level, entity);
        }

        // la trainee, une gerbe tous les demi-blocs du couloir traverse
        for (double t = 0.0; t <= 1.0; t += 0.07) {
            Vec3 at = from.lerp(to, t);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    at.x, at.y + 1.0, at.z, 4, 0.25, 0.4, 0.25, 0.06);
        }
    }

    // ------------------------------------------------------------ clic gauche

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player) || !(attacker.level() instanceof ServerLevel level)) {
            return true;
        }
        int charge = charge(player);
        addCharge(player);

        // le supplement de morsure : ce que la charge ajoute au coup lui-meme
        if (charge > 0) {
            target.hurt(level.damageSources().lightningBolt(), charge * BITE_PER_CHARGE);
        }

        // L'eclair saute, d'autant plus loin que l'orage est haut.
        //
        // Un saut de base, puis un de plus tous les deux crans : la montee est
        // lente exprès, sans quoi la premiere moitie de la jauge ne se sentirait
        // pas et la seconde balaierait la salle.
        int arcs = 1 + charge / 2;
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class,
                target.getBoundingBox().inflate(ARC_RANGE),
                e -> e.isAlive() && e != target && e != player && !e.isAlliedTo(player));
        around.sort(Comparator.comparingDouble(e -> e.distanceToSqr(target)));

        for (int i = 0; i < Math.min(arcs, around.size()); i++) {
            LivingEntity next = around.get(i);
            next.hurt(level.damageSources().lightningBolt(),
                    (2.5F + charge * BITE_PER_CHARGE) * ARC_SHARE);
            trail(level, target.position().add(0, 1, 0), next.position().add(0, 1, 0));
            spark(level, next);
        }

        // A PLEINE CHARGE, la foudre tombe.
        //
        // Un eclair purement visuel, et nos degats a cote : le vrai eclair de
        // Minecraft met le feu, frappe a travers les murs et ne distingue pas
        // qui l'a appele. Le spectacle sans les effets de bord.
        if (charge >= CHARGE_MAX) {
            var bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(target.blockPosition()));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            target.hurt(level.damageSources().lightningBolt(), 6.0F);
        }

        spark(level, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS,
                0.35F, 1.5F + charge * 0.08F);
        return true;
    }

    // ------------------------------------------------------------- outillage

    private static void spark(ServerLevel level, Entity at) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                at.getX(), at.getY() + at.getBbHeight() * 0.6, at.getZ(),
                12, 0.3, 0.35, 0.3, 0.12);
    }

    /** Le trait qui relie deux corps, pour qu'on VOIE le saut de l'eclair. */
    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        int steps = (int) Math.max(4, from.distanceTo(to) * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.lerp(to, i / (double) steps);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                    1, 0.05, 0.05, 0.05, 0.0);
        }
    }
}
