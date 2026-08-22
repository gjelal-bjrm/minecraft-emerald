package com.emerald.effects;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Marque Prismatique : posee par une Fleche Prismatique (tir a pleine tension
 * de l'Arcencium Bow). Pendant sa duree, les coups de l'Emerald Sword sur la
 * cible ont leurs chances de proc doublees (voir EmeraldWindblade.hurtEnemy).
 * L'effet lui-meme est passif : c'est un simple drapeau visible.
 */
public class PrismaticMarkEffect extends MobEffect {
    public PrismaticMarkEffect() {
        super(MobEffectCategory.HARMFUL, 0xE478FF);
    }
}
