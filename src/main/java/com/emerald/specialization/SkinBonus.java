package com.emerald.specialization;

import com.emerald.element.Attunement;
import com.emerald.element.Element;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Ce que chaque apparence d'ailes DONNE, une fois les ailes a +15.
 *
 * Les chiffres sont ceux du joueur (cahier, section 28). Les grandeurs qui
 * existent deja dans le combat -- critique, esquive, resistance, element,
 * declenchement -- s'ajoutent au meme total que la fiche et les runes, au
 * meme endroit (voir HeroCombat) ; les autres -- attaque, defense, vie,
 * vitesse, cadence -- sont des modificateurs d'attribut (voir
 * Specialization.applyBonuses). La brulure et le givre se posent sur coup
 * critique ; la regeneration de l'Aurore court hors combat.
 */
public final class SkinBonus {

    /** Les bonus d'une apparence, en pour cent sauf mention. */
    public record Bonus(double attack, double defense, double health, double speed, double cadence,
                        double element, double critChance, double critDamage, double dodge,
                        double resistance, double pierce, double surge, boolean burn, boolean frost,
                        boolean regen) {
    }

    public static final Bonus NONE = new Bonus(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false);

    /** Le givre pose sur une victime : jusqu'a quel tick elle subit +15 % d'Eau. */
    private static final String TAG_FROST_UNTIL = "ArcenciumFrostUntil";
    private static final double FROST_TAKEN = 0.15;

    private SkinBonus() {
    }

    public static Bonus of(WingSkin skin) {
        return switch (skin) {
            case PRISMATIQUES -> new Bonus(0, 0, 0, 5, 0, 5, 0, 0, 0, 0, 0, 0, false, false, false);
            case OBSCURES -> new Bonus(0, 0, 0, 5, 0, 0, 6, 15, 0, 0, 0, 0, false, false, false);
            case RUBIS -> new Bonus(10, 0, 0, 7, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false);
            case PIERRES_PRECIEUSES -> new Bonus(4, 4, 4, 5, 0, 4, 0, 0, 0, 0, 0, 0, false, false, false);
            case AURORE -> new Bonus(0, 0, 0, 3, 0, 0, 0, 0, 0, 7, 5, 0, false, false, true);
            case TEMPETE -> new Bonus(0, 0, 0, 3, 10, 0, 0, 0, 0, 0, 0, 5, false, false, false);
            case BRAISE -> new Bonus(0, 0, 0, 5, 0, 10, 0, 0, 0, 0, 0, 0, true, false, false);
            case GIVRE -> new Bonus(0, 8, 0, 5, 0, 0, 0, 0, 6, 0, 0, 0, false, true, false);
            case EMERAUDE -> new Bonus(0, 7, 12, 8, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false);
            case PAPILLON -> new Bonus(0, 0, 0, 6, 0, 0, 0, 0, 4, 0, 0, 0, false, false, false);
        };
    }

    /** Les bonus actifs d'un joueur : ceux de son apparence, a +15 et au-dela seulement. */
    public static Bonus active(Player player) {
        if (Specialization.level(player) < Specialization.WINGS_FULL) {
            return NONE;
        }
        return of(Specialization.skin(player));
    }

    public static double critChance(Player player) {
        return active(player).critChance();
    }

    public static double critDamage(Player player) {
        return active(player).critDamage();
    }

    public static double dodge(Player player) {
        return active(player).dodge();
    }

    /** En points de pourcentage, comme la voie Element. */
    public static double resistance(Player player) {
        return active(player).resistance();
    }

    /** Ce que l'attaquant retire a la resistance elementaire de sa cible, en points. */
    public static double pierce(Player player) {
        return active(player).pierce();
    }

    /**
     * Le declenchement de l'apparence : avec la chance donnee, le coup vaut
     * une fois et demie. Un jet de plus, comme l'arme et le casque.
     */
    public static double surge(Player player, RandomSource random) {
        double chance = active(player).surge();
        return chance > 0.0 && random.nextDouble() * 100.0 < chance ? 1.5 : 1.0;
    }

    /**
     * Le multiplicateur des degats elementaires de l'attaquant sur cette
     * victime : l'apparence (la Braise ne compte que pour le Feu), et le
     * givre pose sur la victime si l'attaque est d'Eau.
     */
    public static double elementMultiplier(Player attacker, LivingEntity victim) {
        Bonus bonus = active(attacker);
        Element mine = Attunement.of(attacker);
        double pct = bonus.element();
        if (Specialization.level(attacker) >= Specialization.WINGS_FULL
                && Specialization.skin(attacker) == WingSkin.BRAISE && mine != Element.FEU) {
            pct = 0.0;
        }
        double mult = 1.0 + pct / 100.0;
        if (mine == Element.EAU && frozen(victim)) {
            mult *= 1.0 + FROST_TAKEN;
        }
        return mult;
    }

    /** Ce qu'un coup critique pose sur la victime : la brulure de la Braise, le givre du Givre. */
    public static void onCrit(Player attacker, LivingEntity victim) {
        Bonus bonus = active(attacker);
        if (bonus.burn()) {
            victim.igniteForSeconds(3.0F);
        }
        if (bonus.frost()) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            victim.getPersistentData().putLong(TAG_FROST_UNTIL, victim.level().getGameTime() + 60);
        }
    }

    public static boolean frozen(LivingEntity victim) {
        return victim.getPersistentData().getLong(TAG_FROST_UNTIL) > victim.level().getGameTime();
    }
}
