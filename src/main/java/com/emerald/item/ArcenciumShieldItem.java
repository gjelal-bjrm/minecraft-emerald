package com.emerald.item;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * LE BOUCLIER D'ARCENCIUM (22 sept., cahier §83). « Un bouclier avec notre nouveau
 * materiau [...] ne rends pas le bouclier trop puissant avec son pouvoir, rends-le juste
 * bien pour qu'on puisse le preferer au bouclier de base. »
 *
 * Le metal noir aux fissures d'arc-en-ciel de l'armure, sur la silhouette du bouclier du
 * jeu (tools/shield_texture.py). Trois choses de plus que le bouclier de bois, aucune
 * ecrasante :
 *
 *   - LA PARADE IMMEDIATE : le bouclier du jeu ne pare qu'un quart de seconde apres
 *     avoir ete leve (ShieldItem.EFFECTIVE_BLOCK_DELAY) ; celui-ci pare des qu'il est
 *     leve. On relève au dernier moment, et ca passe ;
 *   - LA RIPOSTE PRISMATIQUE : un coup de melee pare renvoie un eclat a l'assaillant --
 *     un coeur de degats et une poussee -- au plus une fois par seconde ;
 *   - DEUX FOIS PLUS SOLIDE (672 contre 336), et il se repare a l'Arcencium.
 *
 * La hache le desarme comme n'importe quel bouclier.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public class ArcenciumShieldItem extends ShieldItem {

    public static final int DURABILITY = 672;
    /** Les degats de la riposte : un coeur. */
    public static final float RIPOSTE_DAMAGE = 2.0F;
    /** La poussee de la riposte, en plus de celle du bouclier du jeu (0,5). */
    public static final double RIPOSTE_PUSH = 0.6;
    /** Au plus une riposte par seconde. */
    public static final int RIPOSTE_COOLDOWN = 20;
    /** Au-dela, ce n'est pas un coup de melee : pas de riposte. */
    private static final double RIPOSTE_REACH = 5.0;

    /** La derniere riposte de chaque porteur (tique du monde). */
    private static final Map<UUID, Long> LAST_RIPOSTE = new WeakHashMap<>();

    public ArcenciumShieldItem(Item.Properties properties) {
        super(properties);
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArcenciumShieldItem;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        return this.getDescriptionId();                 // pas de variante de couleur de banniere
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(ModItems.ARCENCIUM_INGOT.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.emeraldweapons.arcencium_shield.parade").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.emeraldweapons.arcencium_shield.riposte").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ================================================================ la parade immediate

    /**
     * Le jeu ne tient le bouclier pour leve (LivingEntity.isBlocking) que cinq tiques apres
     * le debut de l'usage : getUseDuration - useItemRemaining >= 5. On commence donc l'usage
     * avec cinq tiques d'avance. Des deux cotes : le client le montre leve, le serveur pare.
     */
    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (is(event.getItem())) {
            event.setDuration(Math.max(1, event.getDuration() - ShieldItem.EFFECTIVE_BLOCK_DELAY));
        }
    }

    // ================================================================ la riposte

    @SubscribeEvent
    public static void onBlock(LivingShieldBlockEvent event) {
        LivingEntity blocker = event.getEntity();
        if (!event.getBlocked() || !(blocker.level() instanceof ServerLevel level) || !is(blocker.getUseItem())
                || GearWear.worn(blocker.getUseItem())) {
            return;                                     // use au bout, il ne riposte plus (GearWear)
        }
        DamageSource source = event.getDamageSource();
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof LivingEntity attacker) || attacker == blocker || !attacker.isAlive()
                || attacker.distanceToSqr(blocker) > RIPOSTE_REACH * RIPOSTE_REACH) {
            return;                                     // une fleche, une explosion : le bouclier pare, c'est tout
        }
        long now = level.getGameTime();
        Long last = LAST_RIPOSTE.get(blocker.getUUID());
        if (last != null && now - last < RIPOSTE_COOLDOWN) {
            return;
        }
        LAST_RIPOSTE.put(blocker.getUUID(), now);
        attacker.hurt(blocker.damageSources().thorns(blocker), RIPOSTE_DAMAGE);
        attacker.knockback(RIPOSTE_PUSH, blocker.getX() - attacker.getX(), blocker.getZ() - attacker.getZ());
        // l'eclat : du prisme entre les deux, et le cristal qui tinte
        double x = (blocker.getX() + attacker.getX()) / 2.0;
        double y = blocker.getY() + blocker.getBbHeight() * 0.6;
        double z = (blocker.getZ() + attacker.getZ()) / 2.0;
        level.sendParticles(com.emerald.particles.ModParticles.PRISM_SHARD.get(), x, y, z, 10, 0.3, 0.3, 0.3, 0.15);
        level.playSound(null, blocker.getX(), blocker.getY(), blocker.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.9F, 1.3F + level.random.nextFloat() * 0.3F);
    }
}
