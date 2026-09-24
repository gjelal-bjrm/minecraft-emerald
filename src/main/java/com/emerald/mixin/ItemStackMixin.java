package com.emerald.mixin;

import com.emerald.item.GearWear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Le seul greffon du mod (cahier §96) : un equipement s'use sans jamais se casser. NeoForge laisse
 * un objet du mode ajuster son usure (damageItem), mais aucun evenement ne retient une piece du jeu
 * ou du modpack qui se casse ; GearWear refait donc le chemin d'ItemStack.hurtAndBreak pour les
 * equipements, sans la derniere marche.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void emeraldweapons$wearWithoutBreaking(int amount, ServerLevel level, @Nullable LivingEntity entity,
                                                  Consumer<Item> onBreak, CallbackInfo callback) {
        if (GearWear.wear((ItemStack) (Object) this, amount, level, entity, onBreak)) {
            callback.cancel();
        }
    }
}
