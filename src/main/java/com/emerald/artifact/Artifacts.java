package com.emerald.artifact;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Acces au sertissage d'une pile, et recherche sur un porteur. */
public final class Artifacts {

    private Artifacts() {
    }

    @Nullable
    public static Artifact of(ItemStack stack) {
        return stack.get(ModDataComponents.ARTIFACT.get());
    }

    public static boolean has(ItemStack stack, Artifact artifact) {
        return of(stack) == artifact;
    }

    /** Sertit, sans verifier la compatibilite : c'est a l'appelant de le faire. */
    public static void set(ItemStack stack, @Nullable Artifact artifact) {
        if (artifact == null) {
            stack.remove(ModDataComponents.ARTIFACT.get());
        } else {
            stack.set(ModDataComponents.ARTIFACT.get(), artifact);
        }
    }

    /**
     * Vrai si l'entite porte cet artefact a l'emplacement qui lui correspond.
     *
     * On interroge l'emplacement attendu plutot que de parcourir l'equipement :
     * un plastron range dans le sac ne doit rien accorder.
     */
    public static boolean wearing(LivingEntity entity, Artifact artifact) {
        EquipmentSlot slot = switch (artifact.socket()) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHEST -> EquipmentSlot.CHEST;
            case LEGS -> EquipmentSlot.LEGS;
            case FEET -> EquipmentSlot.FEET;
            case SWORD, BOW, SCEPTER, GLAIVE -> EquipmentSlot.MAINHAND;
        };
        return has(entity.getItemBySlot(slot), artifact);
    }
}
