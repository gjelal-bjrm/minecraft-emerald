package com.emerald.element;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Le Cristal elementaire : ce avec quoi on accorde une arme.
 *
 * Un seul objet pour les quatre, comme pour les artefacts et les runes : ce
 * qu'il contient est un composant, et le modele choisit sa texture par le
 * predicat « variant ».
 *
 * IL TOMBE DES CREATURES DE SON ELEMENT, ce qui donne au systeme sa boucle :
 * pour accorder une arme contre l'Obscur, il faut du cristal de Lumiere, donc
 * aller chercher des creatures de Lumiere -- que l'on combat mal justement
 * parce qu'on n'est pas encore accorde. Le detour est le sel du systeme.
 */
public class ElementStoneItem extends Item {

    public ElementStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Element element = Attunement.of(stack);
        return element == Element.NEUTRE
                ? Component.translatable(getDescriptionId())
                : Component.translatable("item.emeraldweapons.element_stone.named",
                                element.label())
                        .withStyle(style -> style.withColor(element.colour()).withItalic(false));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        Element element = Attunement.of(stack);
        if (element == Element.NEUTRE) {
            return;
        }
        tooltip.add(Component.translatable("item.emeraldweapons.element_stone.desc",
                element.label(), element.opposite().label()).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Le clic droit change l'element du porteur.
     *
     * EN MAIN ET NON A L'ETABLI. Changer d'element n'est pas une fabrication
     * mais une decision tactique : on le fait devant l'ennemi qu'on vient de
     * voir, pas en rentrant a l'atelier. Un aller-retour de deux cents blocs
     * aurait suffi a ce que personne ne s'en serve.
     *
     * Le cristal se consomme, et un seul suffit : le prix est de l'avoir
     * trouve, pas d'en accumuler.
     */
    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(
            net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Element wanted = Attunement.of(stack);
        if (wanted == Element.NEUTRE || Attunement.of(player) == wanted) {
            return net.minecraft.world.InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            Attunement.set(player, wanted);
            player.displayClientMessage(Component.translatable(
                    "element.emeraldweapons.attuned", wanted.label()), false);
            level.playSound(null, player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
            stack.shrink(1);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Pile prete a l'emploi, pour les butins et l'onglet creatif. */
    public static ItemStack stack(Element element, Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        Attunement.set(stack, element);
        return stack;
    }
}
