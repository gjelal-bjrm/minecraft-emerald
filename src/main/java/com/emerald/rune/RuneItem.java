package com.emerald.rune;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * L'objet rune, toutes runes confondues.
 *
 * Un seul objet enregistre : la famille, le rang et les options sont des
 * composants de la pile. Meme choix que pour les artefacts, et pour la meme
 * raison -- des objets separes demanderaient autant de modeles et de
 * traductions sans rien apporter.
 *
 * L'infobulle liste les options UNE PAR LIGNE, avec leur grade devant. C'est le
 * grade qu'on lit en premier quand on ramasse : deux runes Legendaires ne se
 * comparent pas par leur nom, qui est le meme, mais par ce qu'elles portent.
 */
public class RuneItem extends Item {

    public RuneItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        RuneMark mark = Runes.of(stack);
        return mark == null ? Component.translatable(getDescriptionId()) : mark.label();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        RuneMark mark = Runes.of(stack);
        if (mark == null) {
            return;
        }
        for (RuneMark.Option option : mark.options()) {
            tooltip.add(Component.literal(" ")
                    .append(option.grade().label())
                    .append(Component.literal("  "))
                    .append(Component.translatable(option.stat().translationKey())
                            .withStyle(style -> style.withColor(option.stat().colour())))
                    .append(Component.literal("  "))
                    .append(option.stat().effect(option.value())
                            .copy().withStyle(ChatFormatting.BLUE)));
        }
        tooltip.add(Component.translatable("rune.emeraldweapons.family."
                        + mark.family().name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("rune.emeraldweapons.needs",
                        mark.rarity().label())
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Pile prete a l'emploi, pour les butins et l'onglet creatif. */
    public static ItemStack stack(RuneMark mark, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModRuneComponents.RUNE.get(), mark);
        return stack;
    }
}
