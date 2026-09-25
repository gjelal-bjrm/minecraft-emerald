package com.emerald.jak.board;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * LE JET-BOARD DE JAK 3 (cahier §100) : l'objet qu'on achete chez Tess, porte dans SA case -- la
 * case Curios « jet_board », comme celles des artefacts, gardee a la mort (drop_rule ALWAYS_KEEP).
 * La touche du JET-Board le sort sous les pieds et le range (JetBoard.toggle) ; l'objet ne fait
 * que ses refus :
 *  - pile de 1, hors de l'onglet creatif, cache aux visionneuses de recettes ;
 *  - la touche Q ne le lache pas, ni sac ni boite de Shulker ne le prennent ;
 *  - une pile au sol disparait a sa premiere tique : JetBoardKeeper le rend a son proprietaire.
 * C'est l'achat, dans HavenProgress, qui fait le proprietaire : l'objet seul ne donne rien.
 */
public class JetBoardItem extends Item {

    public JetBoardItem(Properties properties) {
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
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.discard();
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        // Component.keybind : le nom de la touche se resout chez le client, sans charger la classe de la touche
        lines.add(Component.translatable("item.emeraldweapons.jet_board.tooltip",
                Component.keybind(JetBoard.KEY_NAME)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.emeraldweapons.jet_board.tooltip2").withStyle(ChatFormatting.DARK_AQUA));
    }
}
