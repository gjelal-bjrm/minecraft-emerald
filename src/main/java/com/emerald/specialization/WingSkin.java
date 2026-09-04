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
    /**
     * LES PIERRES PRECIEUSES SONT AJUSTEES, ET ELLES SEULES.
     *
     * Le joueur les trouvait « trop elevees et trop serrees » en jeu. Verifie :
     * leur cadrage et la repartition de leur matiere sont IDENTIQUES aux
     * autres -- ce n'est donc ni un decalage ni un centrage. Ce qui differe est
     * la SILHOUETTE : deux branches ecartees, une resille doree fine et
     * beaucoup de petites pierres, la ou le Givre et les Prismatiques sont un
     * eventail plein. Une silhouette ouverte se lit mal quand on la reduit a un
     * bloc de large : les vides deviennent du bruit et les branches se
     * confondent.
     *
     * En attendant une repeinture (voir tools/prompts/ailes_pierres_precieuses.md),
     * on lui donne plus d'envergure et plus d'ecartement, ce qui separe les
     * deux branches et rend chaque pierre lisible. Et elle laisse tomber ses
     * pierres (voir WingGems), ce qui etait la demande.
     */
    PIERRES_PRECIEUSES("pierres_precieuses", true, 1.0F, 14.0F, 1.15F, true),
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

    /** Degres d'ecartement en plus, pour une silhouette qui a besoin d'air. */
    public final float spread;
    /** Facteur d'envergure : une aile fine se lit mieux un peu plus grande. */
    public final float scale;
    /** Vrai si elle laisse tomber des pierres (voir WingGems). */
    public final boolean gems;

    WingSkin(String id, boolean emissive, float tint) {
        this(id, emissive, tint, 0.0F, 1.0F, false);
    }

    WingSkin(String id, boolean emissive, float tint, float spread, float scale, boolean gems) {
        this.id = id;
        this.emissive = emissive;
        this.tint = tint;
        this.spread = spread;
        this.scale = scale;
        this.gems = gems;
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
