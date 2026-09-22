package com.emerald.haven.quest;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Les six personnages des quetes de Haven (lot 3, cahier §86) : les heros de Jak 3 du plan
 * valide le 19 sept. (§71), Tess pour la boutique, et le Pecheur sur son bateau (§79.7).
 */
public enum HavenHero {
    TORN("torn", ChatFormatting.RED),
    TESS("tess", ChatFormatting.LIGHT_PURPLE),
    SIG("sig", ChatFormatting.GOLD),
    KEIRA("keira", ChatFormatting.AQUA),
    SAMOS("samos", ChatFormatting.GREEN),
    PECHEUR("pecheur", ChatFormatting.DARK_AQUA);

    public final String id;
    public final ChatFormatting color;

    HavenHero(String id, ChatFormatting color) {
        this.id = id;
        this.color = color;
    }

    /** Son nom (« Torn », « Le Pecheur »). */
    public Component displayName() {
        return Component.translatable("entity.emeraldweapons.haven_pnj." + this.id);
    }

    @Nullable
    public static HavenHero byId(String id) {
        for (HavenHero hero : values()) {
            if (hero.id.equals(id)) {
                return hero;
            }
        }
        return null;
    }
}
