package com.emerald.menu.bag;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Un sac vu par nos ecrans, COTE SERVEUR : ses cases, ce que chacune peut tenir, son tri.
 *
 * Deux sortes (voir Bags.of) : les sacs de Sophisticated Backpacks, lus dans leur
 * conteneur brut -- celui de leur propre ecran, sans les ameliorations qui detruisent
 * a l'entree --, et tout autre objet porte qui expose un conteneur modifiable (une
 * boite de Shulker dans l'inventaire, par exemple).
 *
 * Le client ne voit jamais un Bag : le contenu d'un sac vit dans le monde, et sa copie
 * cote client est vide ou perimee. L'ecran recoit les cases visibles par la
 * synchronisation normale des menus.
 */
public interface Bag {

    /** La pile du sac lui-meme, telle qu'elle est portee. */
    ItemStack stack();

    int size();

    /** Une COPIE de la case : nos ecrans ne modifient jamais une pile du sac en place. */
    ItemStack get(int slot);

    /** Ecrit la case telle quelle, sans passer par les ameliorations du sac. */
    void set(int slot, ItemStack stack);

    /** Combien de cet objet la case peut tenir (quatre piles dans le Sac d'Arcencium). */
    int limit(int slot, ItemStack stack);

    /** Le sac accepte-t-il cet objet dans cette case ? */
    boolean accepts(int slot, ItemStack stack);

    /** Range le sac. */
    void sort();

    /** Le multiplicateur de piles, pour que l'ecran devine les limites sans demander. */
    int multiplier();

    /** Un identifiant stable (l'UUID du contenu pour Sophisticated Backpacks), ou null. */
    @Nullable
    Object key();
}
