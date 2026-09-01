package com.emerald.rune;

import com.emerald.item.GearRarity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce qu'une piece porte comme runes, et ce qu'elle accepte.
 *
 * DEUX REGLES, ET ELLES FONT TOUT LE SYSTEME.
 *
 * 1. UNE SEULE RUNE PAR EMPLACEMENT. Pas d'accumulation : graver, c'est
 *    choisir. Le casque fait exception -- il porte une rune d'armure ET une
 *    rune secondaire -- parce qu'il tient chez nous le role de l'arme
 *    secondaire de NosTale ; ce sont bien deux emplacements distincts, un par
 *    famille, et non deux runes dans le meme.
 *
 * 2. LE RANG DE LA RUNE NE PEUT PAS DEPASSER CELUI DE LA PIECE. Une piece
 *    Phenomenale accepte tout, une piece Utile n'accepte que de l'Utile. C'est
 *    ce qui relie les deux systemes : monter une piece en rarete ne se contente
 *    plus d'ajouter des chiffres, cela OUVRE l'acces aux bonnes runes, ce qui
 *    est une bien meilleure recompense. Et cela donne une raison de garder une
 *    rune de haut rang qu'on ne peut pas encore poser.
 */
public final class Runes {

    private Runes() {
    }

    // -------------------------------------------------------------- la pile

    /** Les runes gravees sur une piece. Liste vide si elle n'en porte aucune. */
    public static List<RuneMark> on(ItemStack stack) {
        List<RuneMark> marks = stack.get(ModRuneComponents.RUNES.get());
        return marks == null ? List.of() : marks;
    }

    /** La rune que porte un OBJET rune. Nulle si la pile n'en est pas une. */
    public static RuneMark of(ItemStack stack) {
        return stack.get(ModRuneComponents.RUNE.get());
    }

    /** La rune gravee de cette famille, ou nulle. */
    public static RuneMark on(ItemStack stack, RuneFamily family) {
        for (RuneMark mark : on(stack)) {
            if (mark.family() == family) {
                return mark;
            }
        }
        return null;
    }

    /** Le rang de rune le plus eleve que cette piece accepte. */
    public static int ceiling(ItemStack stack) {
        return GearRarity.of(stack).rank();
    }

    /**
     * Pourquoi la gravure est refusee, ou null si elle passe.
     *
     * On rend la RAISON et non un simple non : l'etabli doit pouvoir dire au
     * joueur pourquoi rien ne se passe. Un refus muet devant trois regles
     * differentes est indiscernable d'une panne.
     */
    public static String refuse(ItemStack gear, RuneMark mark) {
        if (mark == null || gear.isEmpty()) {
            return "empty";
        }
        if (!mark.family().accepts(gear)) {
            return "family";
        }
        if (mark.rank() > ceiling(gear)) {
            return "rank";
        }
        return null;
    }

    public static boolean canEngrave(ItemStack gear, RuneMark mark) {
        return refuse(gear, mark) == null;
    }

    /**
     * Grave une rune, en REMPLACANT celle de sa famille.
     *
     * Le remplacement est silencieux et l'ancienne rune est perdue. C'est la
     * meme regle que pour les artefacts : on peut changer d'avis, mais cela
     * coute. Sans ce prix, il n'y aurait aucune raison de reflechir avant de
     * graver.
     */
    public static void engrave(ItemStack gear, RuneMark mark) {
        List<RuneMark> marks = new ArrayList<>();
        for (RuneMark had : on(gear)) {
            if (had.family() != mark.family()) {
                marks.add(had);
            }
        }
        marks.add(mark);
        gear.set(ModRuneComponents.RUNES.get(), List.copyOf(marks));
    }

    /** Efface toutes les runes d'une piece. */
    public static void clear(ItemStack gear) {
        gear.remove(ModRuneComponents.RUNES.get());
    }

    // ------------------------------------------------------------ la lecture

    /**
     * Ce qu'une rune donne au porteur, tout l'equipement confondu.
     *
     * On additionne l'arme en main ET les quatre pieces. Une rune ne compte que
     * sur la piece qui l'accepte, si bien qu'il n'y a aucun risque de la lire
     * deux fois. Chercher piece par piece plutot que de tenir un cache evite la
     * classe de bogues ou un total reste fige apres un changement d'equipement.
     */
    public static double total(LivingEntity entity, Rune stat) {
        double sum = 0.0;
        for (ItemStack stack : worn(entity)) {
            for (RuneMark mark : on(stack)) {
                sum += mark.value(stat);
            }
        }
        return sum;
    }

    /** L'arme en main et les quatre pieces portees. */
    public static List<ItemStack> worn(LivingEntity entity) {
        List<ItemStack> all = new ArrayList<>(5);
        all.add(entity.getMainHandItem());
        for (net.minecraft.world.entity.EquipmentSlot slot
                : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR) {
                all.add(entity.getItemBySlot(slot));
            }
        }
        return all;
    }
}
