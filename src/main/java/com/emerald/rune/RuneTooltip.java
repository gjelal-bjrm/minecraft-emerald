package com.emerald.rune;

import com.emerald.item.GearRarity;
import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/**
 * Ce que l'equipement dit de ses runes.
 *
 * Sans cette infobulle, la gravure serait invisible : les valeurs sont des
 * modificateurs d'attribut anonymes, et le joueur n'aurait aucun moyen de
 * savoir ce qu'il a mis sur quoi -- ni pourquoi une piece refuse une rune.
 *
 * On affiche donc TROIS choses, et la troisieme est celle qui evite les
 * questions : ce qui est grave, ce qui reste libre, et le RANG MAXIMUM que la
 * piece accepte. Ce plafond est la regle la moins intuitive du systeme ; la
 * laisser deviner serait la meilleure facon de la faire passer pour un bogue.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class RuneTooltip {

    private RuneTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof RuneItem) {
            return;                        // la rune se decrit elle-meme
        }
        boolean takesAny = false;
        for (RuneFamily family : RuneFamily.values()) {
            if (family.accepts(stack)) {
                takesAny = true;
                break;
            }
        }
        if (!takesAny) {
            return;
        }

        int ceiling = Runes.ceiling(stack);
        for (RuneFamily family : RuneFamily.values()) {
            if (!family.accepts(stack)) {
                continue;
            }
            RuneMark mark = Runes.on(stack, family);
            if (mark == null) {
                event.getToolTip().add(Component.translatable(
                                "rune.emeraldweapons.slot.empty",
                                Component.translatable("rune.emeraldweapons.slot."
                                        + family.name().toLowerCase(Locale.ROOT)))
                        .withStyle(ChatFormatting.DARK_GRAY));
                continue;
            }
            event.getToolTip().add(Component.literal("  ").append(mark.label()));
            for (RuneMark.Option option : mark.options()) {
                event.getToolTip().add(Component.literal("   ")
                        .append(option.grade().label())
                        .append(Component.literal(" "))
                        .append(option.stat().effect(option.value())
                                .copy().withStyle(ChatFormatting.BLUE)));
            }
        }

        // Le plafond, en clair, et seulement quand il mord : une piece
        // Phenomenale accepte deja tout, le lui rappeler serait du bruit.
        if (ceiling < GearRarity.values().length - 1) {
            event.getToolTip().add(Component.translatable(
                            "rune.emeraldweapons.ceiling",
                            GearRarity.values()[ceiling].label())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
