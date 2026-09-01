package com.emerald.rune;

import com.emerald.hero.HeroBonus;
import com.emerald.hero.HeroLevel;
import com.emerald.hero.HeroStat;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Ce que les runes font reellement.
 *
 * Deux sortes d'effets, traites differemment :
 *
 *  - les PERMANENTS passent par des modificateurs d'attribut, reposes toutes
 *    les deux secondes. On les repose plutot que de les ajuster : un
 *    modificateur porte un identifiant, le remplacer est sans risque, et l'on
 *    evite ainsi la classe de bogues ou un bonus s'empile a chaque changement
 *    d'equipement ;
 *
 *  - les CONDITIONNELS -- toute la famille secondaire, plus quelques options
 *    d'arme -- se lisent au moment ou leur condition se realise. Ils ne peuvent
 *    pas etre des attributs : un attribut ne sait pas ce qu'est « sous trente
 *    pour cent de vie ».
 *
 * C'est exactement la distinction qui justifie la famille secondaire, et on la
 * retrouve donc jusque dans le code.
 *
 * LE CRITIQUE DES RUNES PASSE PAR CELUI DE LA FICHE. Les options Chance et
 * Fureur ne produisent pas leur propre jet : elles s'ajoutent a celui de
 * {@link com.emerald.hero.HeroCombat}. Deux systemes qui tireraient chacun leur
 * critique donneraient deux coups forts par frappe, et un joueur ne saurait
 * plus lequel vient de tomber ni ce qui l'a cause.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class RuneEvents {

    private static final int REFRESH = 40;

    /** Sous ce rapport de vie, l'Acharnement s'allume. */
    private static final double DESPERATE = 0.30;
    /** A partir de tant d'ennemis proches, la rune Cerne s'allume. */
    private static final int SURROUNDED = 3;
    private static final double SURROUND_RANGE = 5.0;
    /** Portee a laquelle le Cataclysme se propage. */
    private static final double CATACLYSM_RANGE = 4.0;
    /** Duree du saignement et de la syncope. */
    private static final int BLEED_TICKS = 5 * 20;
    private static final int STUN_TICKS = 30;

    private RuneEvents() {
    }

    private static ResourceLocation id(String what) {
        return ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "rune_" + what);
    }

    // ------------------------------------------------------- les permanents

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getGameTime() % REFRESH == 0) {
            apply(player);
        }
        regenerate(player);
    }

    public static void apply(Player player) {
        // La rune Cerne s'invite parmi les permanents : son bonus est une
        // valeur d'armure, et la reposer avec les autres evite un second
        // modificateur qui ferait double emploi.
        double crowded = surrounded(player) ? Runes.total(player, Rune.CERNE) : 0.0;
        // La Sauvegarde est un POURCENTAGE sur l'armure deja acquise : on la
        // convertit ici, une fois, plutot que de l'appliquer au moment du coup.
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        double bulwark = armour == null ? 0.0
                : armour.getBaseValue() * Runes.total(player, Rune.SAUVEGARDE) / 100.0;

        set(player.getAttribute(Attributes.ATTACK_DAMAGE), id("tranchant"),
                Runes.total(player, Rune.TRANCHANT));
        set(player.getAttribute(Attributes.ATTACK_SPEED), id("cadence"),
                Runes.total(player, Rune.CADENCE));
        set(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), id("allonge"),
                Runes.total(player, Rune.ALLONGE));
        set(armour, id("carapace"),
                Runes.total(player, Rune.CARAPACE) + crowded + bulwark);
        set(player.getAttribute(Attributes.ARMOR_TOUGHNESS), id("absorption"),
                Runes.total(player, Rune.ABSORPTION));

        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        set(health, id("endurance"), Runes.total(player, Rune.ENDURANCE));
        if (health != null && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double value) {
        if (attribute == null) {
            return;
        }
        AttributeModifier had = attribute.getModifier(id);
        if (had != null && had.amount() == value) {
            return;                        // rien a refaire : on ne remue pas pour rien
        }
        attribute.removeModifier(id);
        if (value > 0.0) {
            attribute.addPermanentModifier(new AttributeModifier(id, value,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * La Regeneration, une seconde a la fois.
     *
     * Une valeur par tick serait invisible a l'affichage et vingt fois trop
     * forte au total.
     */
    private static void regenerate(ServerPlayer player) {
        if (player.level().getGameTime() % 20 != 0) {
            return;
        }
        double heal = Runes.total(player, Rune.REGENERATION);
        if (heal > 0.0 && player.getHealth() < player.getMaxHealth()) {
            player.heal((float) heal);
        }
    }

    // ------------------------------------------- ce que la fiche vient lire

    /** La chance de critique ajoutee par les runes, en pour cent. */
    public static double critChance(Player player) {
        return Runes.total(player, Rune.CHANCE);
    }

    /** Les degats critiques ajoutes par les runes, en pour cent. */
    public static double critDamage(Player player) {
        return Runes.total(player, Rune.FUREUR);
    }

    /** L'esquive ajoutee par les runes, en pour cent. */
    public static double dodge(Player player) {
        return Runes.total(player, Rune.ESQUIVE);
    }

    /** La reduction des critiques subis ajoutee par les runes, en pour cent. */
    public static double critSoak(Player player) {
        return Runes.total(player, Rune.EGIDE);
    }

    // ----------------------------------------------------- les conditionnels

    @SubscribeEvent
    public static void onOutgoing(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        float amount = event.getAmount();

        // ACHARNEMENT : plus on est bas, plus on frappe fort.
        double fury = Runes.total(attacker, Rune.ACHARNEMENT);
        if (fury > 0.0 && attacker.getHealth() <= attacker.getMaxHealth() * DESPERATE) {
            amount *= (float) (1.0 + fury / 100.0);
        }

        // RAVAGE : un pourcentage sur le total. Applique APRES l'Acharnement,
        // de sorte que les deux se multiplient au lieu de s'additionner --
        // c'est ce qui fait qu'une option S vaut mieux que la somme de ses
        // parties, et donc qu'elle vaut d'etre cherchee.
        double ravage = Runes.total(attacker, Rune.RAVAGE);
        if (ravage > 0.0) {
            amount *= (float) (1.0 + ravage / 100.0);
        }

        // PERCEE : une part de l'armure adverse ne compte plus. On la simule en
        // ajoutant des degats plutot qu'en touchant l'attribut de la victime :
        // modifier la cible reviendrait a la laisser affaiblie apres le coup.
        double pierce = Runes.total(attacker, Rune.PERCEE);
        if (pierce > 0.0) {
            double armour = victim.getAttributeValue(Attributes.ARMOR);
            amount += (float) (amount * Math.min(0.60, pierce / 100.0)
                    * Math.min(1.0, armour / 20.0));
        }

        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
        if (attacker.level() instanceof ServerLevel level) {
            afflict(level, attacker, victim, amount);
        }
    }

    /**
     * Le saignement, la syncope et le cataclysme.
     *
     * Trois jets separes, et non un seul : chacun a sa propre probabilite, et
     * les grouper obligerait a inventer un ordre de priorite entre trois effets
     * qui n'ont aucune raison de s'exclure.
     */
    private static void afflict(ServerLevel level, Player attacker,
                                LivingEntity victim, float amount) {
        double bleed = Runes.total(attacker, Rune.SAIGNEE);
        if (bleed > 0.0 && attacker.getRandom().nextDouble() * 100.0 < bleed) {
            // Le poison tient lieu de saignement : Minecraft n'a pas de degats
            // sur la duree qui ne soient pas un effet, et le poison est le seul
            // qui ne tue pas tout seul -- ce qui evite qu'une rune commune
            // devienne une condamnation a mort.
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, BLEED_TICKS, 0, false, true));
        }
        double stun = Runes.total(attacker, Rune.SYNCOPE);
        if (stun > 0.0 && attacker.getRandom().nextDouble() * 100.0 < stun) {
            victim.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 6, false, true));
            victim.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, STUN_TICKS, 3, false, true));
        }
        double blast = Runes.total(attacker, Rune.CATACLYSME);
        if (blast > 0.0 && attacker.getRandom().nextDouble() * 100.0 < blast) {
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class,
                    victim.getBoundingBox().inflate(CATACLYSM_RANGE),
                    e -> e.isAlive() && e != attacker && e != victim
                            && !e.isAlliedTo(attacker))) {
                near.hurt(level.damageSources().playerAttack(attacker), amount * 0.5F);
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    8, 1.2, 0.4, 1.2, 0.0);
            level.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 0.9F, 0.7F);
        }
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player killer)) {
            return;
        }
        // CUREE : la mise a mort rend de la vie.
        double heal = Runes.total(killer, Rune.CUREE);
        if (heal > 0.0) {
            killer.heal((float) heal);
        }
        // AUBAINE : elle efface ce qui recharge. Le Glaive est le seul objet du
        // mode dont la recharge se voie vraiment ; le sceptre y gagne aussi
        // sans qu'il faille les citer un par un.
        double haste = Runes.total(killer, Rune.AUBAINE);
        if (haste > 0.0 && killer.getRandom().nextDouble() * 100.0 < haste * 10.0) {
            for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[]{
                    com.emerald.item.ModItems.ARCENCIUM_GLAIVE.get(),
                    com.emerald.item.ModItems.ARCENCIUM_SCEPTER.get()}) {
                if (killer.getCooldowns().isOnCooldown(item)) {
                    killer.getCooldowns().removeCooldown(item);
                }
            }
        }
    }

    /** Vrai si assez d'ennemis pressent le porteur pour allumer la rune Cerne. */
    private static boolean surrounded(Player player) {
        if (Runes.total(player, Rune.CERNE) <= 0.0) {
            return false;                   // on ne compte pas pour rien
        }
        return player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SURROUND_RANGE),
                e -> e.isAlive() && e != player && !e.isAlliedTo(player)
                        && e instanceof net.minecraft.world.entity.monster.Enemy)
                .size() >= SURROUNDED;
    }

    /** Sert a rappeler que la fiche du Heros et les runes partagent le meme jet. */
    static double heroCrit(Player player) {
        return HeroStat.ATTAQUE.bonus(HeroBonus.CRIT_CHANCE,
                HeroLevel.path(player, HeroStat.ATTAQUE));
    }
}
