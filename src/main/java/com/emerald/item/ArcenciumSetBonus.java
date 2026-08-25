package com.emerald.item;

import com.emerald.effects.ModEffects;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Bonus de panoplie « Resonance Prismatique ».
 *
 * Avec les quatre pieces d'Arcencium, la Fureur Cristalline ne retombe plus a
 * zero quand elle expire : elle redescend d'un cran. Le porteur qui reste
 * agressif conserve donc son elan au lieu de repartir de rien a chaque fenetre
 * de vingt secondes -- c'est ce qui recompense le style de jeu offensif que
 * l'epee encourage deja.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class ArcenciumSetBonus {

    /** Duree du cran retabli, plus courte que la fenetre initiale. */
    private static final int STEP_DOWN_TICKS = 12 * 20;

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance expiring = event.getEffectInstance();
        if (expiring == null || !expiring.getEffect().equals(ModEffects.CRYSTALLINE_AURA)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !hasFullSet(entity)) {
            return;
        }
        // amplifier 0 est le dernier cran : la aura s'eteint pour de bon
        int nextAmplifier = expiring.getAmplifier() - 1;
        if (nextAmplifier < 0) {
            return;
        }
        entity.addEffect(new MobEffectInstance(
                ModEffects.CRYSTALLINE_AURA, STEP_DOWN_TICKS, nextAmplifier));
        entity.level().playSound(null, entity.blockPosition(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    /** Vrai si les quatre pieces portees sont en Arcencium. */
    public static boolean hasFullSet(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor)
                    || !armor.getMaterial().equals(ModArmorMaterials.ARCENCIUM)) {
                return false;
            }
        }
        return true;
    }
}
