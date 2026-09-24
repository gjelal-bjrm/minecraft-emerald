package com.emerald.item;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * L'USURE SANS CASSE (cahier §96). « J'aimerais qu'on empeche la destruction des equipements.
 * Meme s'ils sont uses, ils ne doivent pas se detruire. S'ils sont uses au minimum, au lieu de les
 * detruire, on les rend extremement nerfes, et il faut les reparer pour qu'ils redeviennent
 * forts » (le joueur, 24 sept.).
 *
 * UN EQUIPEMENT -- une arme, une piece d'armure, un bouclier, du mode, du jeu ou du modpack (les
 * memes familles que les etablis, GearEligibility) -- ne se casse plus : son usure s'arrete a un
 * point de la fin (le greffon {@code mixin/ItemStackMixin}, sur ItemStack.hurtAndBreak ; aucun
 * evenement de NeoForge ne retient un objet qui se casse). La, il est USE :
 * <ul>
 * <li>ses coups ne font plus que le dixieme de leurs degats -- au corps a corps, par ses
 *     projectiles (une fleche connait son arc), par ses pouvoirs qui passent par son porteur ;</li>
 * <li>une piece d'armure ne compte plus que le dixieme de son armure, de sa robustesse et de sa
 *     resistance au recul (son infobulle le montre) ;</li>
 * <li>un bouclier n'arrete plus que le dixieme d'un coup, et celui d'Arcencium ne riposte plus.</li>
 * </ul>
 * Repare (enclume, raccommodage), il retrouve tout d'un coup. Les outils -- pioche, pelle, houe --
 * s'usent et se cassent comme avant : ce ne sont pas des equipements.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class GearWear {

    /** Ce qui reste d'un equipement use : le dixieme. */
    public static final float WORN = 0.1F;

    private GearWear() {
    }

    /** Un equipement qui s'use sans se casser : arme, armure ou bouclier, et qui s'use. */
    public static boolean protectedGear(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem()
                && (GearEligibility.isWeapon(stack) || GearEligibility.isArmor(stack, null)
                || stack.canPerformAction(ItemAbilities.SHIELD_BLOCK));
    }

    /** Use au bout : il ne lui reste qu'un point. */
    public static boolean worn(@Nullable ItemStack stack) {
        return stack != null && protectedGear(stack) && stack.getDamageValue() >= stack.getMaxDamage() - 1;
    }

    /**
     * L'usure d'un equipement, a la place d'ItemStack.hurtAndBreak : le meme chemin que le jeu
     * (l'objet, l'Unbreaking, le progres), mais la derniere marche n'est jamais franchie.
     *
     * @return vrai si c'etait un equipement : le jeu n'a plus rien a faire
     */
    public static boolean wear(ItemStack stack, int amount, ServerLevel level, @Nullable LivingEntity entity,
                               Consumer<Item> onBreak) {
        if (!protectedGear(stack)) {
            return false;
        }
        int damage = stack.getItem().damageItem(stack, amount, entity, onBreak);
        if (entity != null && entity.hasInfiniteMaterials()) {
            return true;
        }
        if (damage > 0) {
            damage = EnchantmentHelper.processDurabilityChange(level, stack, damage);
            if (damage <= 0) {
                return true;
            }
        }
        int next = Math.min(stack.getDamageValue() + damage, stack.getMaxDamage() - 1);
        if (entity instanceof ServerPlayer player && damage != 0) {
            CriteriaTriggers.ITEM_DURABILITY_CHANGED.trigger(player, stack, next);
        }
        boolean was = worn(stack);
        stack.setDamageValue(next);
        if (!was && worn(stack) && entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("gear.emeraldweapons.worn", stack.getHoverName())
                    .withStyle(ChatFormatting.RED), true);
            player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8F, 0.7F);
        }
        return true;
    }

    /** Les coups d'une arme usee : le dixieme, en dernier (apres tous les bonus). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        Entity direct = source.getDirectEntity();
        boolean carried = direct != null && (direct == source.getEntity() || direct instanceof Projectile);
        if (carried && worn(source.getWeaponItem())) {
            event.setAmount(event.getAmount() * WORN);
        }
    }

    /** Un bouclier use n'arrete plus que le dixieme du coup. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShield(LivingShieldBlockEvent event) {
        if (event.getBlocked() && worn(event.getEntity().getUseItem())) {
            event.setBlockedDamage(event.getBlockedDamage() * WORN);
        }
    }

    /** L'armure d'une piece usee : le dixieme de ce qu'elle donne, bonus de rarete compris. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onModifiers(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!worn(stack) || !GearEligibility.isArmor(stack, null)) {
            return;
        }
        for (ItemAttributeModifiers.Entry entry : List.copyOf(event.getModifiers())) {
            AttributeModifier modifier = entry.modifier();
            if (protective(entry.attribute()) && modifier.amount() > 0.0) {
                event.replaceModifier(entry.attribute(), new AttributeModifier(modifier.id(),
                        modifier.amount() * WORN, modifier.operation()), entry.slot());
            }
        }
    }

    private static boolean protective(Holder<Attribute> attribute) {
        Attribute value = attribute.value();
        return value == Attributes.ARMOR.value() || value == Attributes.ARMOR_TOUGHNESS.value()
                || value == Attributes.KNOCKBACK_RESISTANCE.value();
    }

    /** L'infobulle le dit, juste sous le nom. */
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (worn(event.getItemStack())) {
            event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                    Component.translatable("gear.emeraldweapons.worn.tooltip").withStyle(ChatFormatting.RED));
        }
    }
}
