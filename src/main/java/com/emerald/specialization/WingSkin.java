package com.emerald.specialization;

import com.emerald.main.EmeraldWeaponsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * Les apparences des ailes de specialisation.
 *
 * Une apparence, c'est une texture -- une aile DROITE peinte, vue de dos,
 * racine en bas a gauche, fond transparent (voir tools/wings_import.py) --
 * et un mode de rendu : la plupart sont de la LUMIERE (cristal, aurore,
 * braise) et se rendent en emissif, plein feu ; les Obscures et le Papillon
 * sont de la matiere et prennent la lumiere du monde.
 *
 * Les bonus de chaque apparence (cahier, section 28) viendront ici quand la
 * mecanique sera ecrite ; pour l'instant, une apparence n'est qu'un aspect.
 */
public enum WingSkin implements StringRepresentable {
    PRISMATIQUES("prismatiques", true, 0.80F),
    RUBIS("rubis", true, 1.0F),
    AURORE("aurore", true, 0.95F),
    PIERRES_PRECIEUSES("pierres_precieuses", true, 1.0F),
    BRAISE("braise", true, 1.0F),
    TEMPETE("tempete", true, 0.95F),
    EMERAUDE("emeraude", true, 1.0F),
    OBSCURES("obscures", false, 1.0F),
    GIVRE("givre", true, 0.85F),
    PAPILLON("papillon", false, 1.0F);

    private final String id;
    /** Rendue en emissif (lumiere) plutot qu'eclairee par le monde (matiere). */
    public final boolean emissive;
    /**
     * Attenuation de la couleur en emissif : une aile presque blanche rendue
     * plein feu se lavait en une tache sous le shader ; on la retient un peu.
     */
    public final float tint;

    WingSkin(String id, boolean emissive, float tint) {
        this.id = id;
        this.emissive = emissive;
        this.tint = tint;
    }

    public String id() {
        return id;
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(EmeraldWeaponsMod.MODID, "textures/wings/" + id + ".png");
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static WingSkin byOrdinal(int ordinal) {
        WingSkin[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }

    /** L'apparence nommee, ou null. */
    public static WingSkin byId(String id) {
        for (WingSkin skin : values()) {
            if (skin.id.equalsIgnoreCase(id)) {
                return skin;
            }
        }
        return null;
    }
}
