package com.emerald.haven;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * EN VILLE, L'EQUIPEMENT DU DEHORS NE SERT A RIEN (cahier §96). « Les niveaux heroiques, les
 * statistiques et tous les equipements qu'on obtient en dehors de la ville n'ont aucun impact sur
 * les degats des armes dans la ville. Et on ne peut pas utiliser les equipements dans la ville, ni
 * les armes, a part les ailes, pour le double saut et pour planer avec l'elytre » (le joueur,
 * 24 sept.).
 *
 * Les armes de la ville -- le Morph Gun -- blessaient deja sans attaquant, donc sans aucun bonus du
 * dehors (GunImpacts.source), et les voitures aussi. Dans Haven, desormais :
 * <ul>
 * <li>UN COUP PORTE PAR UN JOUEUR NE BLESSE PERSONNE : une arme, un outil, une fleche, un trident,
 *     un sort, une potion -- une epee, un arc, un sceptre du dehors ne servent a rien. Seul le
 *     POING reste (la main vide, ou un objet qui n'est ni arme ni outil) : un coup d'un point,
 *     jamais plus, sans aucun bonus du dehors -- de quoi lever l'armee de mouettes de Jak 3, pas
 *     de quoi se battre ;</li>
 * <li>L'ARMURE NE PROTEGE PLUS UN JOUEUR, ni ses enchantements, qu'elle vienne des pieces ou des
 *     niveaux ; un bouclier ne pare plus ;</li>
 * <li>LES ARTEFACTS ET LES RUNES de l'equipement se taisent (Artifacts.wearing, RuneEvents.apply).</li>
 * </ul>
 * Les ailes restent entieres : double saut, vol plane, vol d'elytre, et leurs bonus d'apparence.
 * Les coeurs gagnes aux niveaux heroiques restent aussi : ce ne sont pas des equipements.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class HavenGear {

    /** Un coup de poing en ville : la main nue du jeu, sans rien du dehors. */
    public static final float FIST = 1.0F;

    private HavenGear() {
    }

    /** Un coup de la main nue : au corps a corps, sans arme ni outil en main. */
    private static boolean fist(DamageSource source) {
        return source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof Player player
                && !player.getMainHandItem().isDamageableItem();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (!Haven.is(victim.level())) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player) {
            if (!fist(source)) {
                event.setCanceled(true);
            }
            return;
        }
        if (victim instanceof Player) {
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0.0F);
            event.addReductionModifier(DamageContainer.Reduction.ENCHANTMENTS, (container, reduction) -> 0.0F);
        }
    }

    /** Le poing, en dernier : un point, quoi qu'aient ajoute les bonus du dehors. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFist(LivingIncomingDamageEvent event) {
        if (Haven.is(event.getEntity().level()) && fist(event.getSource())) {
            event.setAmount(Math.min(event.getAmount(), FIST));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onShield(LivingShieldBlockEvent event) {
        if (event.getEntity() instanceof Player && Haven.is(event.getEntity().level())) {
            event.setBlocked(false);
        }
    }
}
