package com.emerald.element;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * L'element d'une creature, et ses resistances -- FIXES, l'un comme l'autre.
 *
 * C'est ce qui distingue le bestiaire du joueur : le joueur CHOISIT son
 * element et peut en changer, la creature porte le sien et ne le quitte
 * jamais. Sans cette asymetrie il n'y aurait rien a preparer -- on ne choisit
 * pas contre quelque chose qui choisit aussi.
 *
 * LA TABLE EST EXPLICITE POUR CE QU'ON CONNAIT, et deduite des traits pour tout
 * le reste. Une liste seule manquerait tout ce que le modpack ajoute ; une
 * deduction seule laisserait la Lumiere sans representant, puisque aucune
 * creature vanilla n'en porte naturellement. Les deux ensemble couvrent le
 * bestiaire entier sans qu'on ait a le recenser.
 *
 * LES RESISTANCES SONT FIXES ET PROPRES A CHAQUE ELEMENT. Une creature de Feu
 * ne resiste pas seulement au Feu : elle a un profil complet, quatre chiffres,
 * qui dit ce qu'elle encaisse de chacun. C'est ce qui rend le choix d'element
 * interessant contre un bestiaire varie -- il n'y a pas UN bon element, il y en
 * a un par famille d'ennemis.
 */
public final class MobElement {

    /**
     * Les quatre resistances d'un profil, dans l'ordre de l'enumeration
     * (Eau, Feu, Lumiere, Obscur), en pour cent.
     *
     * Un profil resiste FORTEMENT a son propre element, moyennement aux deux
     * neutres, et PAS DU TOUT a son contraire. Le zero est la porte : c'est lui
     * qui recompense le joueur qui a prepare le bon element, et sans lui le
     * systeme entier ne serait qu'une taxe.
     */
    private record Profile(Element self, int eau, int feu, int lumiere, int obscur) {
        int against(Element element) {
            return switch (element) {
                case EAU -> eau;
                case FEU -> feu;
                case LUMIERE -> lumiere;
                case OBSCUR -> obscur;
                case NEUTRE -> 0;
            };
        }
    }

    private static final Profile OF_WATER = new Profile(Element.EAU, 55, 0, 20, 20);
    private static final Profile OF_FIRE = new Profile(Element.FEU, 0, 55, 20, 20);
    private static final Profile OF_LIGHT = new Profile(Element.LUMIERE, 20, 20, 55, 0);
    private static final Profile OF_DARK = new Profile(Element.OBSCUR, 20, 20, 0, 55);
    private static final Profile OF_NONE = new Profile(Element.NEUTRE, 0, 0, 0, 0);

    private MobElement() {
    }

    /** Le profil d'une creature. */
    private static Profile profile(LivingEntity entity) {
        return switch (Attunement.of(entity)) {
            case EAU -> OF_WATER;
            case FEU -> OF_FIRE;
            case LUMIERE -> OF_LIGHT;
            case OBSCUR -> OF_DARK;
            case NEUTRE -> OF_NONE;
        };
    }

    /**
     * L'element naturel d'une creature, deduit de ce qu'elle EST.
     *
     * Jamais de son nom : un mort-vivant brule au soleil quel que soit le mod
     * qui l'a ecrit, alors qu'une liste de noms serait perimee des le premier
     * mod ajoute au modpack.
     *
     * L'ordre des tests compte. Un squelette du Nether est mort-vivant ET
     * immunise au feu ; on teste donc le feu d'abord, parce que c'est ce qui le
     * distingue des autres morts-vivants et que le joueur le lira ainsi.
     *
     * LA LUMIERE N'A AUCUN REPRESENTANT NATUREL, et c'est voulu : elle est
     * reservee a ce que le mode place lui-meme -- gardiens de sanctuaire,
     * elites, boss. Rencontrer une creature de Lumiere doit vouloir dire
     * quelque chose.
     */
    public static Element natural(LivingEntity entity) {
        if (entity instanceof Player) {
            return Element.NEUTRE;
        }
        if (entity.fireImmune()) {
            return Element.FEU;
        }
        if (entity.getType().is(net.minecraft.tags.EntityTypeTags.AQUATIC)
                || entity.canBreatheUnderwater()) {
            return Element.EAU;
        }
        if (entity.getType().is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                || entity.isInvertedHealAndHarm()) {
            return Element.OBSCUR;
        }
        // Ce qui lance des sorts sans etre mort-vivant : sorcieres, evocateurs,
        // illusionnistes. Ils n'ont aucun trait commun exploitable, mais leur
        // classe de base en a une.
        if (entity instanceof net.minecraft.world.entity.monster.SpellcasterIllager
                || entity instanceof net.minecraft.world.entity.monster.Witch) {
            return Element.OBSCUR;
        }
        return Element.NEUTRE;
    }

    /** Ce que cette creature encaisse de cet element-la, en pour cent. */
    public static int resistance(LivingEntity entity, Element element) {
        return profile(entity).against(element);
    }
}
