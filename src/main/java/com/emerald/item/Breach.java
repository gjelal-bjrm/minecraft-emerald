package com.emerald.item;

import com.emerald.element.WeaponProfile;
import com.emerald.game.MobScaling;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * LA BRECHE (25 sept. 2026, cahier §104).
 *
 * Les monstres s'alignent maintenant sur les joueurs (MobScaling), et Minecraft plafonne la
 * reduction d'armure : un boss en netherite +10 s'en approche. Le joueur a propose la
 * reponse : « ca donnerait tout un interet a avoir des equipements en Arcencium. Sur chacune
 * des armes, une probabilite entre 10 et 20 % d'ignorer une partie de la defense, mais
 * uniquement pour les boss et pas sur les monstres normaux. »
 *
 * Nos armes seulement (celles qui ont un profil : l'epee, le glaive, le sceptre, l'arc). La
 * chance monte avec la RARETE : 10 % a rang 0, 20 % au rang 8 -- ce qui donne une raison de
 * plus de monter la rarete. Le coup qui perce ignore LA MOITIE de l'armure du boss, calculee
 * comme la Percee des runes (RuneEvents) : le rapport entre les degats apres l'armure
 * reduite et apres l'armure entiere.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class Breach {

    /** La chance au rang 0, et ce que chaque rang de rarete y ajoute, en pour cent. */
    public static final double BASE = 10.0;
    public static final double PER_RANK = 1.25;
    /** La part de l'armure du boss qu'un coup qui perce ignore. */
    public static final double IGNORED = 0.5;
    /** Combien de coups ont perce depuis le demarrage : ce que le banc compte. */
    public static int procs;

    private Breach() {
    }

    /** La chance de Breche de cette arme, en pour cent ; zero si ce n'est pas une arme du mode. */
    public static double chance(ItemStack weapon) {
        if (!WeaponProfile.applies(weapon)) {
            return 0.0;
        }
        return BASE + GearRarity.of(weapon).rank() * PER_RANK;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!MobScaling.isBoss(victim)) {
            return;                                 // les monstres ordinaires : jamais
        }
        double chance = chance(attacker.getMainHandItem());
        if (chance <= 0.0 || attacker.getRandom().nextDouble() * 100.0 >= chance) {
            return;
        }
        procs++;
        float amount = event.getAmount();
        float armour = (float) victim.getAttributeValue(Attributes.ARMOR);
        float toughness = (float) victim.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float full = CombatRules.getDamageAfterAbsorb(victim, amount, event.getSource(), armour, toughness);
        float pierced = CombatRules.getDamageAfterAbsorb(victim, amount, event.getSource(),
                armour * (float) (1.0 - IGNORED), toughness);
        if (full > 0.0F && pierced > full) {
            event.setAmount(amount * pierced / full);
        }
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.getX(),
                    victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 16, 0.4, 0.4, 0.4, 0.3);
            level.playSound(null, victim.blockPosition(), SoundEvents.SHIELD_BREAK, SoundSource.PLAYERS,
                    0.9F, 1.4F);
        }
    }
}
