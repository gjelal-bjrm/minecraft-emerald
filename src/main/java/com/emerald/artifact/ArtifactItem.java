package com.emerald.artifact;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * L'objet artefact, tous artefacts confondus.
 *
 * Un seul objet plutot que vingt-quatre : l'artefact porte est un composant de
 * donnees, et le modele choisit sa texture par le predicat « variant ». Ajouter
 * un artefact ne demande donc qu'une entree d'enum, une texture et une ligne de
 * surcharge -- pas un nouvel enregistrement.
 */
public class ArtifactItem extends Item {

    public ArtifactItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        Artifact artifact = Artifacts.of(stack);
        return artifact == null
                ? Component.translatable(getDescriptionId())
                : Component.translatable(artifact.translationKey())
                        .withStyle(style -> style.withColor(artifact.color()));
    }

    /** Rappelle a quelle piece l'artefact se destine : la moitie du choix est la. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                java.util.List<Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        Artifact artifact = Artifacts.of(stack);
        if (artifact == null) {
            return;
        }
        tooltip.add(Component.translatable(artifact.descriptionKey()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("artifact.emeraldweapons.socket."
                        + artifact.socket().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Pile pretes a l'emploi, pour les tables de butin et l'onglet creatif. */
    public static ItemStack stack(Artifact artifact, Item item) {
        ItemStack stack = new ItemStack(item);
        Artifacts.set(stack, artifact);
        return stack;
    }
}
