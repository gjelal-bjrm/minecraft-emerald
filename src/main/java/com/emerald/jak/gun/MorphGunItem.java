package com.emerald.jak.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Le Morph Gun de Jak 3 : UN objet, emeraldweapons:morph_gun, dont la forme,
 * les formes possedees et les quatre reserves d'eco vivent dans son composant
 * (MorphGunData).
 *
 * IL N'EXISTE QUE DANS HAVEN, LOBBY OUVERT. C'est MorphGunKeeper qui le donne et
 * le retire ; l'objet ne fait ici que les refus qui lui reviennent :
 *  - pile de 1, hors de l'onglet creatif, sans recette ni butin, cache aux
 *    visionneuses de recettes (etiquette c:hidden_from_recipe_viewers) ;
 *  - onDroppedByPlayer faux : la touche Q ne le lache pas (ServerPlayer.drop,
 *    :2051). Les jets depuis un ecran d'inventaire ne passent pas par la :
 *    MorphGunKeeper les rattrape sur ItemTossEvent ;
 *  - canFitInsideContainerItems faux : ni sac de cuir, ni boite de Shulker ;
 *  - inventoryTick, cote SERVEUR seulement (Inventory.tick tourne aussi chez le
 *    client) : une pile hors de la ville ou lobby ferme disparait ;
 *  - onEntityItemUpdate : une pile au sol disparait a sa premiere tique ;
 *  - shouldCauseReequipAnimation faux tant que la case et l'objet ne changent
 *    pas : changer d'arme ou debiter une reserve reecrit la pile, et l'arme
 *    replongerait sinon a chaque ecriture (IItemExtension.java:524).
 */
public class MorphGunItem extends Item {

    public MorphGunItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player && !MorphGunKeeper.allowed(player)) {
            MorphGunKeeper.discardOutside(player, stack);
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.discard();
        return true;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !newStack.is(oldStack.getItem());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        MorphGunData data = MorphGunData.of(stack);
        if (data == null) {
            return;
        }
        lines.add(Component.translatable("item.emeraldweapons.morph_gun.tooltip.form",
                Component.translatable(data.form().translationKey())).withStyle(ChatFormatting.GOLD));
        for (GunForm.Family family : GunForm.Family.values()) {
            lines.add(Component.translatable("item.emeraldweapons.morph_gun.tooltip.eco",
                    Component.translatable(family.translationKey()), data.eco(family), family.capacity)
                    .withColor(textColor(family)));
        }
        lines.add(Component.translatable("item.emeraldweapons.morph_gun.tooltip.keys").withStyle(ChatFormatting.GRAY));
    }

    /** La couleur de famille, eclaircie pour se lire sur le fond sombre d'une infobulle. */
    public static int textColor(GunForm.Family family) {
        return switch (family) {
            case RED -> 0xFF6A5A;
            case YELLOW -> 0xFFD84A;
            case BLUE -> 0x5CC8FF;
            case DARK -> 0xB48CFF;
        };
    }
}
