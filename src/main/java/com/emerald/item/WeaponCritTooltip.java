package com.emerald.item;

import com.emerald.element.Attunement;
import com.emerald.element.Element;
import com.emerald.element.WeaponProfile;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/**
 * Le caractere de l'arme, ecrit sur l'arme : critique et element.
 *
 * Sans cette ligne, la rarete d'une arme resterait un mot colore. Le joueur
 * verrait « Legendaire » sans jamais savoir ce que le rang lui rapporte, et
 * n'aurait aucun moyen de comparer deux armes de rangs voisins autrement qu'en
 * les essayant.
 *
 * On affiche les DEUX chiffres et pas un seul : une arme qui critique souvent
 * pour peu ne se joue pas comme une arme qui critique rarement pour beaucoup,
 * et c'est exactement ce que le Glaive et le Sceptre opposent.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class WeaponCritTooltip {

    private WeaponCritTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!WeaponProfile.applies(stack)) {
            return;
        }
        // Le critique ne s'affiche QUE s'il existe. Ecrire « 0 % de chance »
        // sur un sceptre laisserait croire a un defaut ; ne rien ecrire dit
        // que ce n'est pas son terrain.
        if (WeaponProfile.critChance(stack) > 0.0) {
            event.getToolTip().add(Component.translatable("weapon.emeraldweapons.crit",
                            String.format(Locale.ROOT, "%.1f", WeaponProfile.critChance(stack)),
                            String.format(Locale.ROOT, "%.0f", WeaponProfile.critDamage(stack)))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        // L'arme dit la PUISSANCE elementaire, jamais l'element : celui-ci
        // appartient au joueur et changerait sous l'infobulle.
        double power = WeaponProfile.elementPower(stack);
        if (power > 0.0) {
            event.getToolTip().add(Component.translatable("weapon.emeraldweapons.element",
                            String.format(Locale.ROOT, "%.0f", power))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        double pierce = WeaponProfile.resistPierce(stack);
        if (pierce > 0.0) {
            event.getToolTip().add(Component.translatable("weapon.emeraldweapons.pierce",
                            String.format(Locale.ROOT, "%.0f", pierce))
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
