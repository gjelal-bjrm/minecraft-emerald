package com.emerald.game;

import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Qui compose une vague, selon ce qui est installe.
 *
 * Les identifiants du modpack sont cites en TEXTE et resolus a l'execution :
 * citer les classes rendrait le mod incapable de demarrer sans elles. Une
 * faction absente disparait simplement du tirage.
 *
 * Factions visees, dans l'esprit du cahier :
 *   Cour Noyee et Legion Draugr .. L_Ender's Cataclysm
 *   Cercle Arcanique ............. Iron's Spellbooks
 *   Oublies ...................... The Undergarden
 *   Horde Gobeline ............... Twilight Forest
 *   Sculk ........................ Deeper Darker (reserve a l'arene finale)
 *
 * Du vanilla est melange a chaque palier : une vague entierement composee de
 * creatures etrangeres se lit mal, et perd le joueur qui ne connait pas le
 * modpack par coeur.
 */
public final class SiegeRoster {

    private SiegeRoster() {
    }

    public static List<String> forTier(int tier) {
        return switch (tier) {
            case 1 -> List.of(
                    "cataclysm:deepling", "cataclysm:draugr", "undergarden:rotling",
                    "twilightforest:kobold", "irons_spellbooks:cultist",
                    "minecraft:zombie", "minecraft:skeleton", "minecraft:pillager");
            case 2 -> List.of(
                    "cataclysm:deepling_brute", "cataclysm:deepling_angler",
                    "cataclysm:elite_draugr", "cataclysm:koboleton",
                    "irons_spellbooks:pyromancer", "irons_spellbooks:cryomancer",
                    "irons_spellbooks:priest", "undergarden:rotwalker",
                    "twilightforest:blockchain_goblin",
                    "minecraft:vindicator", "minecraft:witch", "minecraft:stray");
            default -> List.of(
                    "cataclysm:deepling_warlock", "cataclysm:royal_draugr",
                    "cataclysm:kobolediator", "cataclysm:coral_golem",
                    "irons_spellbooks:necromancer", "irons_spellbooks:archevoker",
                    "undergarden:forgotten_guardian", "twilightforest:armored_giant",
                    "minecraft:evoker", "minecraft:ravager", "minecraft:wither_skeleton");
        };
    }

    public static EntityType<?>[] vanillaFallback(int tier) {
        if (tier >= 3) {
            return new EntityType<?>[]{EntityType.PILLAGER, EntityType.VINDICATOR,
                    EntityType.EVOKER, EntityType.RAVAGER, EntityType.WITHER_SKELETON};
        }
        if (tier == 2) {
            return new EntityType<?>[]{EntityType.PILLAGER, EntityType.VINDICATOR,
                    EntityType.HUSK, EntityType.STRAY, EntityType.WITCH};
        }
        return new EntityType<?>[]{EntityType.ZOMBIE, EntityType.SKELETON,
                EntityType.SPIDER, EntityType.PILLAGER};
    }
}
