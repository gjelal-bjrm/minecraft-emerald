package com.emerald.rune;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(EmeraldWeaponsMod.MODID);

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
                    // Le nom prend la couleur de son GRADE, non d'une teinte propre a
                    // l'option : c'est le grade qui dit ce que vaut la ligne, et une
                    // couleur par option ne renseignait sur rien.
                    .append(Component.translatable(option.stat().translationKey())
                            .withStyle(option.grade().colour()))
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

    /**
     * UNE RUNE SANS MARQUE SE REPARE dans les poches d'un joueur, tiree comme sur une
     * bete de 60 points de vie a la phase du moment. La Cache des poches de mine en a
     * laisse (15 sept., voir RuneDrops.guaranteed) ; un /give en donne aussi.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server && Runes.of(stack) == null) {
            stack.set(ModRuneComponents.RUNE.get(), RuneDrops.guaranteed(server, 60.0, server.random));
        }
    }

    /** Pile prete a l'emploi, pour les butins et l'onglet creatif ; vide sans marque. */
    public static ItemStack stack(RuneMark mark, Item item) {
        if (mark == null) {
            // sans marque, ni rang ni option : mieux vaut rien qu'une rune vide
            LOGGER.warn("rune demandee sans marque : pile vide", new IllegalArgumentException("marque absente"));
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(ModRuneComponents.RUNE.get(), mark);
        return stack;
    }
}
