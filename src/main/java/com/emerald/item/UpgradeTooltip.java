package com.emerald.item;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Ce que l'amelioration dit sur la piece.
 *
 * TROIS LIGNES, et il en faut trois. Le gain actuel, parce qu'un « +8 » dans le
 * nom ne dit pas ce qu'il vaut. Le cout du cran suivant, parce que l'echelle
 * des metaux est la regle la moins devinable du systeme -- personne ne trouvera
 * seul qu'il faut de la netherite pour un +9. Et les chances, parce qu'un pari
 * dont on ignore la cote n'est pas un pari, c'est une surprise.
 *
 * L'avertissement de RETROGRADATION n'apparait qu'a partir du septieme cran, la
 * ou il devient vrai. L'afficher plus tot inquieterait pour rien.
 */
@EventBusSubscriber(modid = EmeraldWeaponsMod.MODID)
public final class UpgradeTooltip {

    private UpgradeTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = Upgrade.of(stack);
        // On n'ecrit rien sur ce qui ne s'ameliore pas : une piece hors systeme
        // ne doit pas se voir proposer un cran qu'elle n'aura jamais.
        if (!upgradable(stack)) {
            return;
        }

        if (level > 0) {
            event.getToolTip().add(Component.translatable("upgrade.emeraldweapons.bonus",
                            (int) Math.round(Upgrade.bonus(level) * 100))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (level >= Upgrade.MAX) {
            event.getToolTip().add(Component.translatable("upgrade.emeraldweapons.maxed")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            return;
        }

        Upgrade.Cost cost = Upgrade.cost(level + 1);
        event.getToolTip().add(Component.translatable("upgrade.emeraldweapons.next",
                        level + 1, cost.amount(),
                        Component.translatable(cost.material().getDescriptionId()),
                        Upgrade.odds(level, com.emerald.client.WeatherClient.current()
                                == com.emerald.weather.Weather.HEURE_DOREE))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (Upgrade.risky(level)) {
            event.getToolTip().add(Component.translatable("upgrade.emeraldweapons.risk")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    /**
     * Vrai pour ce qui porte des degats ou de l'armure.
     *
     * On lit ce que la piece DECLARE, comme le fait deja RarityStats : c'est le
     * seul test qui vaille aussi pour les pieces des autres mods, qu'on n'a
     * aucune raison d'exclure du systeme.
     */
    private static boolean upgradable(ItemStack stack) {
        var declared = stack.getOrDefault(
                net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS,
                stack.getItem().getDefaultInstance().get(
                        net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS));
        if (declared == null) {
            return false;
        }
        for (var entry : declared.modifiers()) {
            if (entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes
                            .ATTACK_DAMAGE.unwrapKey().orElseThrow())
                    || entry.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes
                            .ARMOR.unwrapKey().orElseThrow())) {
                return true;
            }
        }
        return false;
    }
}
