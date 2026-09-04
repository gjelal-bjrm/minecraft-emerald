package com.emerald.hero;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Le critique, l'esquive et la resistance : ce que Minecraft n'a pas.
 *
 * Aucune de ces trois n'existe comme attribut. Le jeu connait bien un coup
 * critique, mais il est purement geometrique -- on tombe sur sa cible -- et
 * n'obeit a aucune probabilite. Les paliers de la fiche les appliquent donc a
 * la main, au moment ou le coup passe.
 *
 * TOUT SE JOUE SUR L'EVENEMENT DE DEGATS ENTRANT, et pour une raison precise :
 * c'est le seul endroit qui voie a la fois qui frappe, qui encaisse, et le
 * montant avant reduction. Un critique pose sur l'attaquant et une esquive
 * posee sur la victime seraient deux systemes a synchroniser ; ici il n'y en a
 * qu'un, et l'ordre y est explicite -- on esquive d'abord, on critique
 * ensuite, on resiste en dernier.
 *
 * Les monstres ne beneficient de rien de tout ceci : la fiche est au joueur.
 *
 * LES RUNES SE VERSENT DANS CES MEMES TOTAUX, elles ne tirent pas a part. La
 * fiche et les runes touchent aux memes quatre grandeurs -- chance de critique,
 * degats critiques, esquive, critiques subis -- et deux systemes qui tireraient
 * chacun le leur donneraient deux coups forts par frappe et deux chances
 * d'esquiver le meme coup. Un joueur ne saurait plus ce qu'il possede.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HeroCombat {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /**
     * Ce qu'un critique multiplie AVANT tout bonus de palier.
     *
     * Un et demi, comme le critique vanilla, pour que les deux se ressemblent :
     * un joueur qui voit son coup partir plus fort ne doit pas avoir a deviner
     * lequel des deux systemes vient de se declencher.
     */
    private static final double CRIT_BASE = 1.5;
    /** Le tick du dernier critique recu, note sur la victime le temps d'un evenement. */
    private static final String TAG_CRIT_AT = "ArcenciumCritAt";

    private HeroCombat() {
    }

    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();

        // 1. L'ESQUIVE, avant tout le reste.
        //
        // Elle annule le coup entier, degats ET effets attaches, ce qui n'aurait
        // aucun sens apres reduction. On ne l'applique pas aux degats dont on ne
        // peut pas se soustraire -- chute, noyade, vide -- car une esquive qui
        // sauve du vide ne se lit pas comme une esquive mais comme un bogue.
        if (victim instanceof Player defender && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && source.getEntity() != null) {
            // Les runes s'ajoutent au meme total : un seul jet d'esquive, pas
            // deux. Deux systemes qui tireraient chacun le leur donneraient
            // deux chances d'annuler le meme coup, et l'affichage ne pourrait
            // plus dire au joueur quelle esquive il possede vraiment.
            double dodge = HeroStat.DEFENSE.bonus(HeroBonus.DODGE,
                    HeroLevel.effective(defender, HeroStat.DEFENSE))
                    + com.emerald.rune.RuneEvents.dodge(defender)
                    + com.emerald.specialization.SkinBonus.dodge(defender);
            if (dodge > 0.0 && defender.getRandom().nextDouble() * 100.0 < dodge) {
                event.setCanceled(true);
                if (defender.level() instanceof ServerLevel level) {
                    level.playSound(null, defender.blockPosition(),
                            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.8F);
                }
                return;
            }
        }

        float amount = event.getAmount();

        // 2. LE CRITIQUE de l'attaquant.
        if (source.getEntity() instanceof Player attacker) {
            int level = HeroLevel.effective(attacker, HeroStat.ATTAQUE);
            // TROIS SOURCES, UN SEUL TOTAL : l'arme, la fiche, les runes.
            //
            // L'arme vient en premier parce que c'est elle qui rend le systeme
            // visible des la premiere minute. Sans elle, un joueur sans point
            // d'Attaque et sans rune ne critiquait jamais, et le critique
            // semblait greffe sur rien.
            net.minecraft.world.item.ItemStack held = attacker.getMainHandItem();
            double chance = com.emerald.element.WeaponProfile.critChance(held)
                    + HeroStat.ATTAQUE.bonus(HeroBonus.CRIT_CHANCE, level)
                    + com.emerald.rune.RuneEvents.critChance(attacker)
                    + com.emerald.specialization.SkinBonus.critChance(attacker);
            // LA CONSTELLATION PLEINE force le critique : c'est tout son propos.
            // DEUX FACONS DE FORCER UN CRITIQUE, ET UNE SEULE FAIT LA NOVA.
            //
            // La Constellation pleine eclate ; l'embuscade du Prisme Eteint ne
            // fait que garantir le coup. Les confondre aurait donne l'explosion
            // astrale a chaque creature surprise, sans aile ni etoile.
            boolean constellation = com.emerald.specialization.SkinBonus.constellationReady(attacker);
            boolean forced = constellation || com.emerald.weather.WeatherEffects.ambush(victim);
            if (forced || chance > 0.0 && attacker.getRandom().nextDouble() * 100.0 < chance) {
                double multiplier = CRIT_BASE
                        + (com.emerald.element.WeaponProfile.critDamage(held)
                           + HeroStat.ATTAQUE.bonus(HeroBonus.CRIT_DAMAGE, level)
                           + com.emerald.rune.RuneEvents.critDamage(attacker)
                           + com.emerald.specialization.SkinBonus.critDamage(attacker)) / 100.0;

                // La victime peut REPRENDRE une part du critique. C'est le seul
                // endroit ou les deux fiches se rencontrent, et il fallait bien
                // qu'il y en ait un : sinon la Defense ne repondrait a rien de
                // ce que l'Attaque construit.
                if (victim instanceof Player defender) {
                    double soak = (HeroStat.DEFENSE.bonus(HeroBonus.CRIT_TAKEN,
                            HeroLevel.effective(defender, HeroStat.DEFENSE))
                            + com.emerald.rune.RuneEvents.critSoak(defender)) / 100.0;
                    multiplier = 1.0 + (multiplier - 1.0) * Math.max(0.0, 1.0 - soak);
                }
                amount = (float) (amount * multiplier);
                // On NOTE le critique sur la victime, pour l'affichage. Le chiffre
                // reel n'est connu qu'apres l'armure, dans un autre evenement ;
                // c'est la que le paquet part, et il doit savoir si ce coup-ci
                // etait critique.
                victim.getPersistentData().putLong(TAG_CRIT_AT, victim.level().getGameTime());
                if (constellation) {
                    com.emerald.specialization.SkinBonus.nova(attacker, victim, amount);
                }
                // ce que les ailes posent sur un critique : brulure de la Braise, givre du Givre
                com.emerald.specialization.SkinBonus.onCrit(attacker, victim);
                if (attacker.level() instanceof ServerLevel world) {
                    world.playSound(null, victim.blockPosition(),
                            SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.1F);
                    world.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                            victim.getX(), victim.getY() + victim.getBbHeight() * 0.6,
                            victim.getZ(), 12, 0.3, 0.3, 0.3, 0.25);
                }
            }
        }

        // 3. LA RESISTANCE, en dernier, et seulement sur l'INDIRECT.
        //
        // L'Element renforce les effets qu'on lance ; il est juste qu'il
        // protege des effets qu'on subit. Restreindre aux degats indirects --
        // magie, projectiles, explosions, feu -- lui evite d'etre une seconde
        // armure, role qui appartient a la Defense.
        if (victim instanceof Player defender) {
            double resist = (HeroStat.ELEMENT.bonus(HeroBonus.RESISTANCE,
                    HeroLevel.effective(defender, HeroStat.ELEMENT))
                    + com.emerald.specialization.SkinBonus.resistance(defender)) / 100.0;
            // les ailes d'Aurore de l'attaquant percent une part de cette resistance
            if (source.getEntity() instanceof Player piercer) {
                resist -= com.emerald.specialization.SkinBonus.pierce(piercer) / 100.0;
            }
            if (resist > 0.0 && indirect(source)) {
                amount *= (float) Math.max(0.0, 1.0 - resist);
            }
        }

        // 3 bis. LE DECLENCHEMENT : « avec une probabilite de X, la force
        // d'attaque augmente de Y ». Present sur les trois armes du releve, et
        // sur l'arme secondaire. Deux jets separes, arme et casque : les deux
        // peuvent tomber sur le meme coup, et ce sont ces coups-la dont on se
        // souvient.
        if (source.getEntity() instanceof Player surger) {
            net.minecraft.world.item.ItemStack held2 = surger.getMainHandItem();
            double surge = com.emerald.element.WeaponProfile.surge(held2, surger.getRandom())
                    * com.emerald.element.SecondaryProfile.surge(surger, surger.getRandom())
                    * com.emerald.specialization.SkinBonus.surge(surger, surger.getRandom());
            amount *= (float) surge;
        }

        // 4. L'ELEMENT, tout a la fin.
        //
        // Apres le critique, et calcule sur les degats BRUTS et non sur le
        // total : sans cela le sceptre, qui n'a pas de critique, verrait son
        // element inchange pendant que les autres armes verraient le leur
        // multiplie par leur coup critique. L'arme sans critique serait punie
        // deux fois.
        if (source.getEntity() instanceof Player striker) {
            net.minecraft.world.item.ItemStack weapon = striker.getMainHandItem();
            float element = com.emerald.element.ElementCombat.bonus(
                    striker, victim, weapon, event.getAmount());
            // les ailes : plus d'element (la Braise pour le Feu seulement), et le givre subi
            element *= (float) com.emerald.specialization.SkinBonus.elementMultiplier(striker, victim);
            if (element > 0.0F) {
                amount += element;
            }
        }

        if (amount != event.getAmount()) {
            event.setAmount(amount);
        }
    }

    /**
     * Le chiffre qui flotte au-dessus de la cible.
     *
     * Il part d'ICI et non de l'evenement precedent, parce que c'est ici que
     * les degats sont connus APRES armure et resistances -- le chiffre qu'on
     * montre doit etre celui qu'on a inflige, pas celui qu'on a demande. Le
     * critique, lui, a ete decide plus tot et note sur la victime : on relit la
     * note, et l'on ne la garde que si elle date de ce tick-ci.
     *
     * Seuls les coups portes par un JOUEUR s'affichent. Un chiffre pour chaque
     * morsure de zombie sur un villageois noierait le sien.
     */
    @SubscribeEvent
    public static void onDealt(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof Player)
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        float dealt = event.getNewDamage();
        if (dealt <= 0.0F) {
            return;
        }
        boolean crit = victim.getPersistentData().getLong(TAG_CRIT_AT) == level.getGameTime();
        victim.getPersistentData().remove(TAG_CRIT_AT);

        com.emerald.network.DamagePopPayload pop = new com.emerald.network.DamagePopPayload(
                victim.getX(), victim.getY() + victim.getBbHeight() + 0.25, victim.getZ(),
                dealt, crit);
        for (net.minecraft.server.level.ServerPlayer near : level.players()) {
            if (near.distanceToSqr(victim) < 48.0 * 48.0) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(near, pop);
            }
        }
        LOGGER.debug("Chiffre de degats envoye : {} sur {} (critique {})", dealt, victim.getName().getString(), crit);
    }

    /** Ce qui ne vient pas d'un coup porte de la main : magie, projectile, souffle, feu. */
    private static boolean indirect(DamageSource source) {
        return source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
                || source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)
                // Le coup dont l'auteur n'est pas ce qui frappe : un sort, une
                // fleche, un souffle. C'est le test standard de l'indirect --
                // DamageSource n'expose rien de plus direct.
                || (source.getEntity() != null
                    && source.getDirectEntity() != source.getEntity());
    }
}
