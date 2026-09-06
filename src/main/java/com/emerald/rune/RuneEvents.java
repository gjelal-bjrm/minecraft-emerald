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
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);

        set(player.getAttribute(Attributes.ATTACK_DAMAGE), id("tranchant"),
                Runes.total(player, Rune.TRANCHANT));
        set(player.getAttribute(Attributes.ATTACK_SPEED), id("cadence"),
                Runes.total(player, Rune.CADENCE));
        set(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), id("allonge"),
                Runes.total(player, Rune.ALLONGE));
        set(armour, id("carapace"), Runes.total(player, Rune.CARAPACE));
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

        // EXECUTION : la CIBLE est basse, on l'acheve. Le miroir de l'Acharnement.
        double execution = Runes.total(attacker, Rune.EXECUTION);
        if (execution > 0.0 && victim.getHealth() <= victim.getMaxHealth() * DESPERATE) {
            amount *= (float) (1.0 + execution / 100.0);
        }
        // PERCEE : une part de l'armure adverse ne compte plus -- POUR DE VRAI.
        // On calcule ce que le coup donnerait apres l'armure entiere, puis apres
        // l'armure amputee de cette part, et l'on gonfle le coup du rapport des
        // deux : le jeu applique ensuite l'armure entiere, et il en sort
        // exactement ce qu'aurait donne l'armure reduite. Contre une cible sans
        // armure, le rapport vaut un, et la rune ne fait rien -- c'est juste.
        double pierce = Runes.total(attacker, Rune.PERCEE);
        if (pierce > 0.0) {
            float armour = (float) victim.getAttributeValue(Attributes.ARMOR);
            float toughness = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            float full = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(
                    victim, amount, event.getSource(), armour, toughness);
            float reduced = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(
                    victim, amount, event.getSource(),
                    armour * (float) (1.0 - Math.min(1.0, pierce / 100.0)), toughness);
            if (full > 0.0F && reduced > full) {
                amount *= reduced / full;
            }
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
        // Le chiffre affiche EST la chance : plus de « x 10 » cache.
        double haste = Runes.total(killer, Rune.AUBAINE);
        if (haste > 0.0 && killer.getRandom().nextDouble() * 100.0 < haste) {
            for (net.minecraft.world.item.Item item : new net.minecraft.world.item.Item[]{
                    com.emerald.item.ModItems.ARCENCIUM_GLAIVE.get(),
                    com.emerald.item.ModItems.ARCENCIUM_SCEPTER.get()}) {
                if (killer.getCooldowns().isOnCooldown(item)) {
                    killer.getCooldowns().removeCooldown(item);
                }
            }
        }
    }

    /**
     * CE QUE L'ARMURE RETIENT, coup par coup.
     *
     * Trois reductions FIXES selon la nature du coup -- la Garde contre la
     * melee, le Pavois contre ce qui vole, le Sceau contre la magie -- puis le
     * Bastion, en pour cent de tout ce qui reste, puis la Riposte, qui renvoie
     * une part de ce qu'on a pris. Les fixes avant le pour cent : c'est l'ordre
     * qui rend les petites runes utiles sur les petits coups, et le Bastion
     * utile sur les gros.
     */
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player defender) || event.getAmount() <= 0.0F) {
            return;
        }
        net.minecraft.world.damagesource.DamageSource source = event.getSource();
        if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;                         // le vide, et ce qui lui ressemble
        }
        float amount = event.getAmount();
        double flat;
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) {
            flat = Runes.total(defender, Rune.PAVOIS);
        } else if (source.is(net.neoforged.neoforge.common.Tags.DamageTypes.IS_MAGIC)) {
            flat = Runes.total(defender, Rune.SCEAU);
        } else if (source.getEntity() instanceof LivingEntity) {
            flat = Runes.total(defender, Rune.GARDE);
        } else {
            flat = 0.0;                     // chute, feu, noyade : aucune rune ne les connait
        }
        if (flat > 0.0) {
            amount = Math.max(0.0F, amount - (float) flat);
        }
        double bastion = Runes.total(defender, Rune.SAUVEGARDE);
        if (bastion > 0.0) {
            amount *= (float) (1.0 - Math.min(0.90, bastion / 100.0));
        }
        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
        // RIPOSTE : jamais sur une riposte, sinon deux porteurs se renverraient
        // le meme coup jusqu'a ce qu'il ne reste rien a renvoyer.
        double riposte = Runes.total(defender, Rune.RIPOSTE);
        if (riposte > 0.0 && amount > 0.0F
                && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)
                && source.getEntity() instanceof LivingEntity attacker && attacker != defender) {
            attacker.hurt(defender.damageSources().thorns(defender), amount * (float) (riposte / 100.0));
        }
    }

    /** Sert a rappeler que la fiche du Heros et les runes partagent le meme jet. */
    static double heroCrit(Player player) {
        return HeroStat.ATTAQUE.bonus(HeroBonus.CRIT_CHANCE,
                HeroLevel.path(player, HeroStat.ATTAQUE));
    }
}
