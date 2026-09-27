package com.emerald.mixin;

import com.emerald.client.EclipseClient;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * L'OBSCURITE DE L'ECLIPSE, dosee (cahier §108), le troisieme greffon du mod, cote client.
 *
 * Les quatre coupoles du premier brouillard se voyaient : un cercle au sol la ou chacune coupait
 * le terrain, et le quadrille de leurs facettes au loin (« c'est vraiment pas beau », le joueur).
 * Le pack de shaders sait, lui, assombrir avec la distance sans rien dessiner -- c'est l'Obscurite
 * du Gardien (Complementary : DoDarknessFog, un noir qui monte en douceur, sans bord ni facette) --,
 * mais a une seule force : la sienne, tout ou rien, vingt blocs de vue. Iris lit cette force par
 * MobEffectInstance.getBlendFactor, comme le brouillard et la lumiere du jeu de base : pendant
 * l'Eclipse, c'est EclipseClient.darkness qui la donne -- moderee, qui respire, et qui se referme
 * quand un portail ou une horreur approche.
 */
@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin {

    @Shadow
    @Final
    private Holder<MobEffect> effect;

    @Inject(method = "getBlendFactor", at = @At("RETURN"), cancellable = true)
    private void emeraldweapons$eclipse(LivingEntity entity, float delta, CallbackInfoReturnable<Float> callback) {
        if (entity.level().isClientSide() && this.effect.is(MobEffects.DARKNESS)) {
            float dosed = EclipseClient.darkness(entity);
            if (dosed >= 0.0F) {
                callback.setReturnValue(dosed);
            }
        }
    }
}
