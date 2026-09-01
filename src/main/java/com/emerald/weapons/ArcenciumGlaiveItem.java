package com.emerald.weapons;

import com.emerald.artifact.Artifact;
import com.emerald.artifact.Artifacts;
import com.emerald.particles.ModParticles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Glaive d'Arcencium -- la Rage.
 *
 * Quatrieme membre de la famille, et le seul qui ne se gouverne pas. L'epee
 * est la Fureur, l'arc la Tension, le sceptre la Concorde ; le glaive ne
 * demande ni adresse ni patience, il demande de ne pas reculer.
 *
 * Sa texture vient d'une reference fournie par le joueur, integree telle
 * quelle : une lame dentelee bordee d'or, parcourue de foudre. Cinq dessins
 * proceduraux avaient echoue avant elle, ce qui vaut la lecon -- pour une
 * piece d'IDENTITE, on integre la reference ; le procedural est bon pour ce
 * qui est systematique.
 *
 * Il est batti sur une idee simple et eprouvee : une jauge qui ne se remplit
 * qu'au contact, et qui achete deux choses opposees -- se soigner, ou clouer
 * l'adversaire sur place. La ressource n'est donc pas un bonus passif mais un
 * choix, et c'est ce choix qui fait l'arme.
 *
 * LA RAGE monte de zero a cinq, un cran par coup porte. Six secondes sans
 * toucher personne et elle retombe ENTIEREMENT : ce n'est pas une jauge qui
 * s'use, c'est une jauge qui s'eteint, et cela interdit de la thesauriser en
 * attendant le gros ennemi.
 *
 * Clic gauche : on frappe, et la Rage monte. A CINQ, le coup devient la
 *               Curee : tout ce qui entoure est fauche, et chaque corps
 *               touche vous rend de la vie. La Rage repart alors de zero --
 *               le soin se paie.
 * Clic droit  : la Ruee. Un bond qui tranche ce qu'il traverse. S'il TOUCHE,
 *               un SECOND bond s'ouvre aussitot pour quatre secondes : on
 *               entre et l'on ressort, ou l'on poursuit. A partir de trois de
 *               Rage, la Ruee cloue en plus sur place ce qu'elle percute.
 *
 * Ces deux depenses ne se cumulent pas volontiers : ce qu'on garde pour se
 * soigner, on ne l'a pas pour immobiliser. C'est la tension qu'on cherche.
 */
public class ArcenciumGlaiveItem extends Item {

    // --- la Rage
    public static final int RAGE_MAX = 5;
    /** Sans toucher personne pendant ce delai, la Rage retombe ENTIEREMENT. */
    public static final int RAGE_DECAY = 6 * 20;

    // --- la Curee, a rage pleine
    /** Degats ajoutes par cran de rage, au-dela de l'attaque de base. */
    private static final float RAGE_PER_STEP = 1.2F;
    private static final double CULL_RADIUS = 5.0;
    private static final float CULL_DAMAGE = 8.0F;
    /** Vie rendue par corps fauche, et son plafond. */
    private static final float CULL_HEAL = 3.0F;
    private static final float CULL_HEAL_CAP = 12.0F;

    // --- la Ruee
    private static final int DASH_COOLDOWN = 9 * 20;
    private static final int DASH_PER_RAGE = 24;         // 1,2 s par cran
    private static final int DASH_FLOOR = 3 * 20;
    /**
     * Ce qu'il reste du rechargement quand la Ruee a TUE.
     *
     * La Rage la raccourcit deja, mais elle se gagne au corps a corps : rien
     * ne recompensait le bond lui-meme, et un bond manque coutait autant qu'un
     * bond qui emporte tout.
     */
    private static final double DASH_KILL_KEEP = 0.35;
    /** La fenetre du SECOND bond, ouverte par un premier qui a touche. */
    private static final int SECOND_WINDOW = 4 * 20;
    private static final int SECOND_GRACE = 8;           // de quoi relancer
    /** A partir de ce cran de Rage, la Ruee cloue sur place. */
    private static final int STUN_FROM = 3;
    private static final int STUN_TICKS = 30;
    private static final double DASH_POWER = 1.9;
    private static final double DASH_LIFT = 0.3;
    private static final float DASH_DAMAGE = 7.0F;
    private static final double DASH_WIDTH = 1.7;

    private static final String TAG_RAGE = "ArcenciumRage";
    private static final String TAG_LAST_HIT = "ArcenciumRageLastHit";
    private static final String TAG_SECOND = "ArcenciumSecondDash";
    /** Combien de bonds la serie compte deja, pour la Ruee en Chaine. */
    private static final String TAG_CHAIN = "ArcenciumDashChain";
    /** Ce que la Ruee en Chaine ne depasse pas. */
    private static final int CHAIN_MAX = 3;
    /** Ce que l'Onde de Curee multiplie : la portee, puis la vie rendue. */
    private static final double WAVE_REACH = 1.7;
    private static final float WAVE_HEAL = 2.0F;
    /** La portee a laquelle l'Etau de Gangue propage l'immobilisation. */
    private static final double CLAMP_SPREAD = 4.0;

    /** Le second bond reste sur le JOUEUR : il ne suit pas l'arme, il suit la main. */

    public ArcenciumGlaiveItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- la Rage

    /**
     * La Rage telle qu'elle est MAINTENANT.
     *
     * ELLE VIT SUR L'OBJET, et non plus sur le joueur. Rangee dans les donnees
     * persistantes du joueur, elle n'existait que cote serveur : le jeu ne
     * synchronise pas ce sac-la. Le predicat du modele, qui s'evalue chez le
     * client, lisait donc toujours zero -- la jauge des cinq crans n'a jamais
     * rien affiche depuis qu'elle existe, et aucune barre a l'ecran n'aurait
     * pu marcher davantage. Les composants d'un ItemStack, eux, voyagent
     * d'eux-memes.
     *
     * On ne fait pas retomber la Rage dans un tick : on date le dernier coup et
     * l'on relit la date au moment ou l'on en a besoin. Un compteur decremente
     * chaque tick couterait un abonnement au tick du monde pour une valeur que
     * deux soustractions donnent exactement.
     */
    public static int rage(ItemStack stack, Level level) {
        if (level == null) {
            return 0;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        int stored = Math.min(RAGE_MAX, tag.getInt(TAG_RAGE));
        long idle = level.getGameTime() - tag.getLong(TAG_LAST_HIT);
        if (idle <= RAGE_DECAY) {
            return stored;
        }
        // LE CRAN D'ARRET change la NATURE de la retombee, pas sa vitesse.
        //
        // Sans lui, six secondes sans toucher personne effacent tout : l'arme
        // exige qu'on reste au contact, ce qui est son caractere. Avec lui, on
        // perd un cran par fenetre -- de quoi contourner un mur, remonter une
        // muraille ou changer de cible sans repartir de zero. C'est le meme
        // budget de temps, depense autrement.
        if (!Artifacts.has(stack, Artifact.CRAN_D_ARRET)) {
            return 0;
        }
        return Math.max(0, stored - (int) (idle / RAGE_DECAY));
    }

    private static void setRage(ItemStack stack, Level level, int value) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        tag.putInt(TAG_RAGE, Math.max(0, Math.min(RAGE_MAX, value)));
        tag.putLong(TAG_LAST_HIT, level.getGameTime());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ------------------------------------------------------------- clic droit

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = level.getGameTime();
        boolean second = now <= player.getPersistentData().getLong(TAG_SECOND);
        if (!second && player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        int rage = rage(stack, level);
        Vec3 look = player.getLookAngle().normalize();
        Vec3 from = player.position();

        int touched = 0;
        boolean killed = false;
        if (level instanceof ServerLevel server) {
            long[] tally = charge(server, player, stack, from, look, rage);
            touched = (int) tally[0];
            killed = tally[1] != 0L;
            stack.hurtAndBreak(3, player, LivingEntity.getSlotForHand(hand));
        }

        // La poussee s'applique des deux cotes.
        //
        // Cote serveur seul, le client corrigerait la position au tick suivant
        // et le bond partirait en saccade ; hurtMarked ordonne l'envoi du
        // paquet de vitesse, et le client garde la main sur son deplacement
        // pour que le geste reponde immediatement.
        player.setDeltaMovement(look.x * DASH_POWER,
                Math.max(look.y * DASH_POWER, 0.0) + DASH_LIFT,
                look.z * DASH_POWER);
        player.hurtMarked = true;
        player.resetFallDistance();          // bondir n'est pas tomber

        // LE SECOND BOND.
        //
        // Un premier bond qui touche en ouvre un autre pour quatre secondes ;
        // un bond dans le vide se paie plein tarif. C'est ce qui separe l'arme
        // d'une simple mobilite : elle ne deplace bien que celui qui vise.
        // Le second, lui, referme toujours -- sans quoi on tiendrait a distance
        // indefiniment en enchainant sur le premier venu.
        if (!level.isClientSide) {
            // LA RUEE EN CHAINE prolonge la serie tant qu'on touche, jusqu'a
            // trois maillons. Le plafond n'est pas une precaution mais la
            // condition pour que l'artefact reste un artefact : sans lui, un
            // troupeau de zombies devient un moteur de deplacement infini, et
            // l'arme ne serait plus un corps-a-corps mais une monture.
            int links = player.getPersistentData().getInt(TAG_CHAIN);
            boolean chains = Artifacts.has(stack, Artifact.RUEE_EN_CHAINE)
                    && links < CHAIN_MAX;
            if (touched > 0 && (!second || chains)) {
                player.getPersistentData().putLong(TAG_SECOND, now + SECOND_WINDOW);
                player.getPersistentData().putInt(TAG_CHAIN, second ? links + 1 : 1);
                player.getCooldowns().addCooldown(this, SECOND_GRACE);
            } else {
                player.getPersistentData().putLong(TAG_SECOND, 0L);
                player.getPersistentData().putInt(TAG_CHAIN, 0);
                int cooldown = Math.max(DASH_FLOOR, DASH_COOLDOWN - rage * DASH_PER_RAGE);
                if (killed) {
                    cooldown = (int) Math.round(cooldown * DASH_KILL_KEEP);
                }
                player.getCooldowns().addCooldown(this, cooldown);
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                second ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_KNOCKBACK,
                SoundSource.PLAYERS, 0.9F, 0.8F + rage * 0.05F);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Ce que la Ruee laisse derriere elle.
     *
     * On balaie le couloir du bond EN UNE FOIS, au depart, plutot que de suivre
     * le joueur tick par tick. Le bond dure trois ou quatre ticks : le suivre
     * demanderait un etat par joueur, une file a purger et un cas de sortie
     * pour la deconnexion, tout cela pour une boite que l'on sait deja tracer.
     *
     * @return {nombre de corps touches, 1 si l'un d'eux est mort}
     */
    private long[] charge(ServerLevel level, Player player, ItemStack stack,
                          Vec3 from, Vec3 look, int rage) {
        Vec3 to = from.add(look.scale(DASH_POWER * 7.0));
        AABB corridor = new AABB(from, to).inflate(DASH_WIDTH);
        float damage = DASH_DAMAGE + rage * RAGE_PER_STEP;

        long touched = 0;
        long killed = 0;
        for (Entity entity : level.getEntities(player, corridor,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isAlliedTo(player))) {
            entity.hurt(level.damageSources().playerAttack(player), damage);
            touched++;
            if (entity instanceof LivingEntity hit) {
                // CLOUER SUR PLACE, a partir de trois de Rage.
                //
                // Minecraft n'a pas d'etourdissement ; la lenteur a un niveau
                // eleve en tient lieu, et la fatigue l'empeche de riposter au
                // pic. Ce n'est pas un vrai stun, mais cela en fait le travail
                // sans introduire d'etat que le jeu ne saurait pas afficher.
                if (rage >= STUN_FROM) {
                    clamp(hit);
                    // L'ETAU DE GANGUE fait de l'immobilisation un outil de
                    // controle et non un simple bonus au duel : ce qui se
                    // pressait autour de la cible se fige avec elle. C'est la
                    // reponse a la seule facon de mourir avec cette arme --
                    // etre encercle au moment ou l'on bondit.
                    if (Artifacts.has(stack, Artifact.ETAU_DE_GANGUE)) {
                        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class,
                                hit.getBoundingBox().inflate(CLAMP_SPREAD),
                                e -> e.isAlive() && e != player && e != hit
                                        && !e.isAlliedTo(player))) {
                            clamp(near);
                        }
                    }
                }
                if (hit.isDeadOrDying()) {
                    killed = 1;
                }
            }
            burst(level, entity.position().add(0, entity.getBbHeight() * 0.5, 0), 10);
        }

        // la trainee, une gerbe tous les demi-blocs du couloir traverse
        for (double t = 0.0; t <= 1.0; t += 0.06) {
            Vec3 at = from.lerp(to, t);
            level.sendParticles(ModParticles.CRYSTALLINE_FISSURE.get(),
                    at.x, at.y + 1.0, at.z, 2, 0.2, 0.32, 0.2, 0.02);
        }
        return new long[]{touched, killed};
    }

    /**
     * Clouer sur place.
     *
     * Minecraft n'a pas d'etourdissement ; la lenteur a un niveau eleve en
     * tient lieu, et la fatigue empeche de riposter au pic. Ce n'est pas un
     * vrai stun, mais cela en fait le travail sans introduire d'etat que le
     * jeu ne saurait pas afficher.
     */
    private static void clamp(LivingEntity victim) {
        victim.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 6, false, true));
        victim.addEffect(new MobEffectInstance(
                MobEffects.DIG_SLOWDOWN, STUN_TICKS, 3, false, true));
    }

    // ------------------------------------------------------------ clic gauche

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player) || !(attacker.level() instanceof ServerLevel level)) {
            return true;
        }
        int rage = rage(stack, attacker.level());

        // A RAGE PLEINE, le coup devient la Curee.
        //
        // C'est la seule depense qui rende de la vie, et elle vide la jauge :
        // on ne peut pas soigner ET clouer sur le meme plein. Ce partage force
        // est tout l'interet -- une ressource qui achete deux choses a la fois
        // n'est pas une ressource, c'est un bonus.
        if (rage >= RAGE_MAX) {
            // L'ONDE DE CUREE fait de la depense de soin une depense de survie.
            //
            // La portee ET le plafond montent ensemble, a dessein : elargir
            // seulement le cercle ferait faucher plus de monde pour la meme
            // vie, ce qui est un gain de degats deguise. Ici l'artefact
            // recompense ce qu'il promet -- tenir au milieu de la meute.
            boolean wave = Artifacts.has(stack, Artifact.ONDE_DE_CUREE);
            double reach = wave ? CULL_RADIUS * WAVE_REACH : CULL_RADIUS;
            float cap = wave ? CULL_HEAL_CAP * WAVE_HEAL : CULL_HEAL_CAP;
            float healed = 0.0F;
            for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(reach),
                    e -> e.isAlive() && e != player && !e.isAlliedTo(player))) {
                caught.hurt(level.damageSources().playerAttack(player), CULL_DAMAGE);
                healed = Math.min(cap, healed + CULL_HEAL);
                burst(level, caught.position().add(0, caught.getBbHeight() * 0.5, 0), 14);
            }
            ring(level, player, reach);
            if (healed > 0.0F) {
                player.heal(healed);
                level.sendParticles(ParticleTypes.HEART,
                        player.getX(), player.getY() + 1.2, player.getZ(), 6, 0.4, 0.4, 0.4, 0.0);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
            setRage(stack, level, 0);
            return true;
        }

        // sinon, le coup nourrit la Rage, et ce qu'elle vaut deja s'y ajoute
        if (rage > 0) {
            target.hurt(level.damageSources().playerAttack(player), rage * RAGE_PER_STEP);
        }
        setRage(stack, level, rage + 1);
        Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
        burst(level, hit, 8 + rage * 3);
        crescent(level, player, hit, rage);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS,
                0.4F, 0.7F + rage * 0.08F);
        return true;
    }

    // -------------------------------------------------------------- outillage

    private static void burst(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(ModParticles.CRYSTALLINE_FISSURE.get(),
                at.x, at.y, at.z, count, 0.35, 0.35, 0.35, 0.04);
    }

    /**
     * LE CROISSANT : la trace du coup, dessinee en l'air.
     *
     * Une gerbe sur la cible dit qu'on a touche, pas qu'on a FRAPPE. Le glaive
     * est une arme de fauchage : son geste doit se voir, et se voir d'autant
     * plus loin que la Rage est haute. On trace donc l'arc du coup entre le
     * porteur et sa cible, dans le plan horizontal.
     *
     * Les couleurs sont celles des veines de la lame -- les deux signaux
     * doivent se reconnaitre comme un seul, sans quoi l'arme a deux identites.
     */
    private static void crescent(ServerLevel level, Player player, Vec3 target, int rage) {
        Vec3 from = player.position().add(0, player.getBbHeight() * 0.55, 0);
        Vec3 axis = target.subtract(from);
        double reach = Math.max(1.2, axis.horizontalDistance());
        double base = Math.atan2(axis.z, axis.x);
        int arc = 9 + rage * 3;
        for (int i = 0; i <= arc; i++) {
            double sweep = base + (i / (double) arc - 0.5) * (1.1 + rage * 0.12);
            double radius = reach * (0.55 + 0.45 * Math.cos((i / (double) arc - 0.5) * 3.0));
            Vec3 at = from.add(Math.cos(sweep) * radius,
                    (i / (double) arc - 0.5) * 0.5, Math.sin(sweep) * radius);
            level.sendParticles(ModParticles.CRYSTALLINE_FISSURE.get(),
                    at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * L'ANNEAU de la Curee : une onde au sol, qui dit la portee du fauchage.
     *
     * Sans elle, on ne sait pas ce que la Curee a ratisse -- on voit des degats
     * sans en voir la cause, et un pouvoir dont on ne mesure pas la portee ne
     * s'apprend jamais.
     */
    private static void ring(ServerLevel level, Player player, double radius) {
        Vec3 mid = player.position();
        int steps = (int) (radius * 12);
        for (int i = 0; i < steps; i++) {
            double a = i * 2.0 * Math.PI / steps;
            level.sendParticles(ModParticles.CRYSTALLINE_FISSURE.get(),
                    mid.x + Math.cos(a) * radius, mid.y + 0.15,
                    mid.z + Math.sin(a) * radius, 1, 0.0, 0.03, 0.0, 0.01);
        }
    }
}
