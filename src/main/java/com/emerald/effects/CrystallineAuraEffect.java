package com.emerald.effects;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class CrystallineAuraEffect extends MobEffect {
    public CrystallineAuraEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x80FFDA); // Couleur turquoise
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof Player player)) return false;

        // Vitesse déplacement +10%
        applyModifierOnce(player, Attributes.MOVEMENT_SPEED, "crystalline_speed", 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        // Vitesse d'attaque +10%
        applyModifierOnce(player, Attributes.ATTACK_SPEED, "crystalline_attack_speed", 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        // Crit chance & damage, lifesteal will be implemented later

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier){
        return true;
    }

    private void applyModifierOnce(LivingEntity entity, Holder<Attribute> attributeHolder, String name, double amount, AttributeModifier.Operation op) {
        var instance = entity.getAttribute(attributeHolder);
        //System.out.println("Has attribute? " + (instance != null));
        if (instance != null) {
            ResourceLocation id = ResourceLocation.parse("emeraldweapons:" + name);

            if (instance.getModifier(id) == null) {
                instance.addTransientModifier(new AttributeModifier(id, amount, op));
            }
        }
    }
}
