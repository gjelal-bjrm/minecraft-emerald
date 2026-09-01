package com.emerald.element;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * Les quatre elements, et ce qu'ils se font entre eux.
 *
 * DEUX COUPLES OPPOSES, et rien d'autre : l'Eau contre le Feu, la Lumiere
 * contre l'Obscur. Un element frappe fort son oppose, et son oppose le frappe
 * fort en retour -- l'opposition est SYMETRIQUE, ce qui evite le piege des
 * pierre-feuille-ciseaux ou l'on cherche toujours le bon cote du cycle.
 *
 * Entre les deux couples, rien : le Feu contre la Lumiere ne vaut ni plus ni
 * moins que le Feu contre l'Obscur. C'est ce qui rend le systeme lisible --
 * il n'y a qu'une question a se poser devant un ennemi, « suis-je son
 * contraire ? », et non un tableau de seize cases a retenir.
 *
 * Un element contre LUI-MEME est faible. Cela n'a pas ete demande mais decoule
 * de l'idee : si le Feu resiste a ce qui n'est pas l'Eau, il resiste d'abord au
 * Feu. Sans cette regle, une arme de feu serait le choix par defaut contre la
 * moitie du bestiaire.
 */
public enum Element implements StringRepresentable {

    /** L'absence d'element : ni bonus, ni malus, ni resistance. */
    NEUTRE("neutre", ChatFormatting.GRAY, 0xC8C8D4),
    EAU("eau", ChatFormatting.AQUA, 0x61C4FF),
    FEU("feu", ChatFormatting.RED, 0xFF7A3D),
    LUMIERE("lumiere", ChatFormatting.YELLOW, 0xFFE96B),
    OBSCUR("obscur", ChatFormatting.DARK_PURPLE, 0x9C6BFF);

    /** Ce que l'on inflige a son oppose. */
    public static final double ADVANTAGE = 1.60;
    /** Ce que l'on inflige a soi-meme. */
    public static final double MIRROR = 0.45;

    public static final Codec<Element> CODEC = StringRepresentable.fromEnum(Element::values);

    public static final StreamCodec<ByteBuf, Element> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], Element::ordinal);

    private final String id;
    private final ChatFormatting style;
    private final int colour;

    Element(String id, ChatFormatting style, int colour) {
        this.id = id;
        this.style = style;
        this.colour = colour;
    }

    /** L'element qui lui fait face, ou NEUTRE pour le neutre. */
    public Element opposite() {
        return switch (this) {
            case EAU -> FEU;
            case FEU -> EAU;
            case LUMIERE -> OBSCUR;
            case OBSCUR -> LUMIERE;
            case NEUTRE -> NEUTRE;
        };
    }

    /**
     * Le multiplicateur de cet element contre celui-la.
     *
     * Le NEUTRE ne participe a rien : une arme sans element n'inflige aucun
     * degat elementaire, et une creature sans element les subit tous a
     * l'identique. C'est deliberement le cas par defaut -- tant qu'on n'a pas
     * accorde son arme, le systeme entier reste invisible et ne complique rien.
     */
    public double against(Element defender) {
        if (this == NEUTRE || defender == NEUTRE) {
            return 1.0;
        }
        if (defender == this) {
            return MIRROR;
        }
        return defender == opposite() ? ADVANTAGE : 1.0;
    }

    public int colour() {
        return this.colour;
    }

    public ChatFormatting style() {
        return this.style;
    }

    public Component label() {
        return Component.translatable("element.emeraldweapons." + this.id)
                .withStyle(this.style);
    }

    /** L'element nomme, ou NEUTRE si le nom ne correspond a rien. */
    public static Element byName(String name) {
        for (Element element : values()) {
            if (element.id.equalsIgnoreCase(name)) {
                return element;
            }
        }
        return NEUTRE;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public String key() {
        return this.id;
    }

    public static Element of(String raw) {
        return byName(raw.toLowerCase(Locale.ROOT));
    }
}
