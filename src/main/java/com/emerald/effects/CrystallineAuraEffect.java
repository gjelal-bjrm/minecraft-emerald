package com.emerald.effects;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Fureur Cristalline : la fenetre d'elan de l'Epee d'Emeraude.
 *
 * Les bonus passent par addAttributeModifier plutot que par un ajout manuel
 * dans applyEffectTick : le moteur les retire alors tout seul a l'expiration
 * (un ajout manuel de modificateur transitoire n'est jamais nettoye) et les
 * met a l'echelle du niveau, +10 % par cran.
 *
 * Le niveau est ce sur quoi s'appuie le bonus de panoplie d'Arcencium, qui
 * fait redescendre la Fureur d'un cran au lieu de l'eteindre
 * (voir {@link com.emerald.item.ArcenciumSetBonus}).
 */
public class CrystallineAuraEffect extends MobEffect {

    private static final ResourceLocation SPEED_ID =
            ResourceLocation.parse("emeraldweapons:crystalline_speed");
    private static final ResourceLocation ATTACK_SPEED_ID =
            ResourceLocation.parse("emeraldweapons:crystalline_attack_speed");

    public CrystallineAuraEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x80FFDA);   // turquoise
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_ID, 0.10,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        this.addAttributeModifier(Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, 0.10,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }
}
